package battlejoker.codec;

import battlejoker.contract.Contract;
import battlejoker.contract.Contract.Ack;
import battlejoker.contract.Contract.BinaryCodec;
import battlejoker.contract.Contract.CommandRequest;
import battlejoker.contract.Contract.Direction;
import battlejoker.contract.Contract.Empty;
import battlejoker.contract.Contract.EndReason;
import battlejoker.contract.Contract.ErrorBody;
import battlejoker.contract.Contract.ErrorCode;
import battlejoker.contract.Contract.Heartbeat;
import battlejoker.contract.Contract.Joined;
import battlejoker.contract.Contract.Leaderboard;
import battlejoker.contract.Contract.LegacyPlayerInfo;
import battlejoker.contract.Contract.Message;
import battlejoker.contract.Contract.NameRequest;
import battlejoker.contract.Contract.Persistence;
import battlejoker.contract.Contract.PlayerResult;
import battlejoker.contract.Contract.PlayerView;
import battlejoker.contract.Contract.Pong;
import battlejoker.contract.Contract.PuzzleData;
import battlejoker.contract.Contract.PuzzleFile;
import battlejoker.contract.Contract.ResultRequest;
import battlejoker.contract.Contract.RoomState;
import battlejoker.contract.Contract.RoomStatus;
import battlejoker.contract.Contract.RoundResult;
import battlejoker.contract.Contract.ScoreEntry;
import battlejoker.contract.Contract.Type;
import battlejoker.contract.Contract.UploadRequest;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared v2 binary codec: message number first, then the fields of the record
 * declared in Contract.java, in declaration order.
 *
 * <p>Only this class knows the wire layout, so client and server cannot drift apart.</p>
 */
public final class BinaryCodecImpl implements BinaryCodec {

    private static final int MAX_FRAME = Contract.MAX_FRAME_BYTES;
    private static final int MAX_STRING = Contract.MAX_STRING_BYTES;
    private static final int BOARD_SIZE = 16;
    private static final int MAX_PLAYERS = Contract.MAX_PLAYERS;
    private static final int MAX_LEADERBOARD = 10;

    @Override
    public Message<?> read(DataInputStream input) throws IOException {
        DataInputStream in = new DataInputStream(new CountingInputStream(input, MAX_FRAME));
        Type type = typeOf(in.readInt());
        Object body = switch (type) {
            case NAME -> new NameRequest(readString(in, true));
            case COMMAND -> new CommandRequest(readEnum(in, Direction.class, false));
            case SETTING -> throw new IOException("SETTING is reserved and must not be sent");
            case PUZZLE -> readPuzzleData(in);
            case PLAYER_INFO -> new LegacyPlayerInfo(in.readInt());
            case START_GAME, UNDO, GET_ROOM_STATE, GET_TOP10, LEAVE_ROOM -> new Empty();
            case UPLOAD_PUZZLE -> new UploadRequest(readPuzzleFile(in));
            case GET_RESULT -> new ResultRequest(readString(in, true));
            case HEARTBEAT -> new Heartbeat(in.readLong());
            case JOINED -> new Joined(readString(in, true), readString(in, true),
                    readString(in, true), readString(in, true), in.readInt());
            case ACK -> new Ack(typeOf(in.readInt()), in.readBoolean(), in.readLong());
            case ROOM_STATE -> readRoomState(in);
            case ROUND_RESULT -> readRoundResult(in);
            case TOP10, TOP10_UPDATED -> readLeaderboard(in);
            case PONG -> new Pong(in.readLong());
            case ERROR -> new ErrorBody(typeOf(in.readInt()),
                    readEnum(in, ErrorCode.class, false), readString(in, false), in.readBoolean());
        };
        if (!expectedBody(type, body)) {
            throw new IOException("Message number " + codeOf(type) + " carries the wrong body type");
        }
        return new Message<>(type, body);
    }

    @Override
    public void write(DataOutputStream output, Message<?> message) throws IOException {
        DataOutputStream out = new DataOutputStream(new CountingOutputStream(output, MAX_FRAME));
        Type type = message.type();
        Object body = message.body();
        out.writeInt(codeOf(type));
        switch (type) {
            case NAME -> writeString(out, ((NameRequest) body).name());
            case COMMAND -> writeEnum(out, ((CommandRequest) body).direction());
            case SETTING -> throw new IOException("SETTING is reserved and must not be sent");
            case PUZZLE -> writePuzzleData(out, (PuzzleData) body);
            case PLAYER_INFO -> out.writeInt(((LegacyPlayerInfo) body).score());
            case START_GAME, UNDO, GET_ROOM_STATE, GET_TOP10, LEAVE_ROOM -> {
                // empty body: nothing follows the message number
            }
            case UPLOAD_PUZZLE -> writePuzzleFile(out, ((UploadRequest) body).puzzle());
            case GET_RESULT -> writeString(out, ((ResultRequest) body).gameId());
            case HEARTBEAT -> out.writeLong(((Heartbeat) body).clientTimeMillis());
            case JOINED -> {
                Joined joined = (Joined) body;
                writeString(out, joined.playerId());
                writeString(out, joined.roomId());
                writeString(out, joined.gameId());
                writeString(out, joined.serverId());
                out.writeInt(joined.heartbeatSeconds());
            }
            case ACK -> {
                Ack ack = (Ack) body;
                out.writeInt(codeOf(ack.action()));
                out.writeBoolean(ack.changed());
                out.writeLong(ack.revision());
            }
            case ROOM_STATE -> writeRoomState(out, (RoomState) body);
            case ROUND_RESULT -> writeRoundResult(out, (RoundResult) body);
            case TOP10, TOP10_UPDATED -> writeLeaderboard(out, (Leaderboard) body);
            case PONG -> out.writeLong(((Pong) body).serverTimeMillis());
            case ERROR -> {
                ErrorBody error = (ErrorBody) body;
                out.writeInt(codeOf(error.action()));
                writeEnum(out, error.code());
                writeString(out, error.message());
                out.writeBoolean(error.retryable());
            }
        }
        out.flush();
    }

    // ------------------------------------------------------------------ numbers

    public static int codeOf(Type type) {
        return switch (type) {
            case NAME -> 0;
            case COMMAND -> 1;
            case SETTING -> 2;
            case PUZZLE -> 3;
            case PLAYER_INFO -> 4;
            case START_GAME -> 5;
            case UNDO -> 6;
            case UPLOAD_PUZZLE -> 7;
            case GET_ROOM_STATE -> 8;
            case GET_TOP10 -> 9;
            case GET_RESULT -> 10;
            case LEAVE_ROOM -> 11;
            case HEARTBEAT -> 12;
            case JOINED -> 13;
            case ACK -> 14;
            case ROOM_STATE -> 15;
            case ROUND_RESULT -> 16;
            case TOP10 -> 17;
            case TOP10_UPDATED -> 18;
            case PONG -> 19;
            case ERROR -> 20;
        };
    }

    public static Type typeOf(int code) throws IOException {
        return switch (code) {
            case 0 -> Type.NAME;
            case 1 -> Type.COMMAND;
            case 2 -> Type.SETTING;
            case 3 -> Type.PUZZLE;
            case 4 -> Type.PLAYER_INFO;
            case 5 -> Type.START_GAME;
            case 6 -> Type.UNDO;
            case 7 -> Type.UPLOAD_PUZZLE;
            case 8 -> Type.GET_ROOM_STATE;
            case 9 -> Type.GET_TOP10;
            case 10 -> Type.GET_RESULT;
            case 11 -> Type.LEAVE_ROOM;
            case 12 -> Type.HEARTBEAT;
            case 13 -> Type.JOINED;
            case 14 -> Type.ACK;
            case 15 -> Type.ROOM_STATE;
            case 16 -> Type.ROUND_RESULT;
            case 17 -> Type.TOP10;
            case 18 -> Type.TOP10_UPDATED;
            case 19 -> Type.PONG;
            case 20 -> Type.ERROR;
            default -> throw new IOException("Unknown message number " + code);
        };
    }

    private static boolean expectedBody(Type type, Object body) {
        return switch (type) {
            case NAME -> body instanceof NameRequest;
            case COMMAND -> body instanceof CommandRequest;
            case PUZZLE -> body instanceof PuzzleData;
            case PLAYER_INFO -> body instanceof LegacyPlayerInfo;
            case START_GAME, UNDO, GET_ROOM_STATE, GET_TOP10, LEAVE_ROOM -> body instanceof Empty;
            case UPLOAD_PUZZLE -> body instanceof UploadRequest;
            case GET_RESULT -> body instanceof ResultRequest;
            case HEARTBEAT -> body instanceof Heartbeat;
            case JOINED -> body instanceof Joined;
            case ACK -> body instanceof Ack;
            case ROOM_STATE -> body instanceof RoomState;
            case ROUND_RESULT -> body instanceof RoundResult;
            case TOP10, TOP10_UPDATED -> body instanceof Leaderboard;
            case PONG -> body instanceof Pong;
            case ERROR -> body instanceof ErrorBody;
            case SETTING -> false;
        };
    }

    // ------------------------------------------------------------------ strings

    static void writeString(DataOutputStream out, String value) throws IOException {
        if (value == null) {
            out.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING) {
            throw new IOException("String is longer than " + MAX_STRING + " UTF-8 bytes");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    static String readString(DataInputStream in, boolean required) throws IOException {
        int length = in.readInt();
        if (length == -1) {
            if (required) {
                throw new IOException("A required string was null");
            }
            return null;
        }
        if (length < 0 || length > MAX_STRING) {
            throw new IOException("Illegal string length " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return decodeUtf8(bytes);
    }

    private static String decodeUtf8(byte[] bytes) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new IOException("Malformed UTF-8 string", ex);
        }
    }

    private static <E extends Enum<E>> void writeEnum(DataOutputStream out, E value) throws IOException {
        writeString(out, value == null ? null : value.name());
    }

    private static <E extends Enum<E>> E readEnum(DataInputStream in, Class<E> type, boolean nullable)
            throws IOException {
        String name = readString(in, !nullable);
        if (name == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Unknown " + type.getSimpleName() + " value " + name);
        }
    }

    private static List<Integer> readBoard(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count != BOARD_SIZE) {
            throw new IOException("Board must hold " + BOARD_SIZE + " cards, not " + count);
        }
        List<Integer> board = new ArrayList<>(BOARD_SIZE);
        for (int i = 0; i < BOARD_SIZE; i++) {
            board.add(in.readInt());
        }
        return List.copyOf(board);
    }

    private static void writeBoard(DataOutputStream out, List<Integer> board) throws IOException {
        if (board == null || board.size() != BOARD_SIZE) {
            throw new IOException("Board must hold " + BOARD_SIZE + " cards");
        }
        out.writeInt(BOARD_SIZE);
        for (int value : board) {
            out.writeInt(value);
        }
    }

    // ------------------------------------------------------------------ records

    private static PuzzleData readPuzzleData(DataInputStream in) throws IOException {
        int width = in.readInt();
        int height = in.readInt();
        if (width <= 0 || height <= 0 || width * height != BOARD_SIZE) {
            throw new IOException("Legacy puzzle must be 4x4");
        }
        List<Integer> board = new ArrayList<>(BOARD_SIZE);
        for (int i = 0; i < width * height; i++) {
            board.add(in.readInt());
        }
        return new PuzzleData(width, height, List.copyOf(board), in.readBoolean());
    }

    private static void writePuzzleData(DataOutputStream out, PuzzleData puzzle) throws IOException {
        out.writeInt(puzzle.width());
        out.writeInt(puzzle.height());
        if (puzzle.board() == null || puzzle.board().size() != puzzle.width() * puzzle.height()) {
            throw new IOException("Legacy puzzle board does not match " + puzzle.width() + "x" + puzzle.height());
        }
        for (int value : puzzle.board()) {
            out.writeInt(value);
        }
        out.writeBoolean(puzzle.gameOver());
    }

    private static PuzzleFile readPuzzleFile(DataInputStream in) throws IOException {
        int version = in.readInt();
        int size = in.readInt();
        if (size != 4) {
            throw new IOException("Puzzle size must be 4, not " + size);
        }
        List<Integer> board = readBoard(in);
        int level = in.readInt();
        return new PuzzleFile(version, size, board, level);
    }

    private static void writePuzzleFile(DataOutputStream out, PuzzleFile puzzle) throws IOException {
        out.writeInt(puzzle.version());
        out.writeInt(puzzle.size());
        writeBoard(out, puzzle.board());
        out.writeInt(puzzle.level());
    }

    private static RoomState readRoomState(DataInputStream in) throws IOException {
        String roomId = readString(in, true);
        String gameId = readString(in, true);
        long revision = in.readLong();
        RoomStatus status = readEnum(in, RoomStatus.class, false);
        String hostId = readString(in, true);
        int count = in.readInt();
        if (count < 0 || count > MAX_PLAYERS) {
            throw new IOException("Illegal player count " + count);
        }
        List<PlayerView> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(new PlayerView(readString(in, true), readString(in, true), in.readInt(),
                    in.readBoolean(), in.readBoolean(), in.readInt(), in.readInt(), in.readInt(),
                    in.readBoolean(), in.readBoolean()));
        }
        List<Integer> board = readBoard(in);
        int level = in.readInt();
        String currentPlayerId = readString(in, false);
        long turnNumber = in.readLong();
        int movesRemaining = in.readInt();
        EndReason endReason = readEnum(in, EndReason.class, true);
        return new RoomState(roomId, gameId, revision, status, hostId, List.copyOf(players),
                board, level, currentPlayerId, turnNumber, movesRemaining, endReason);
    }

    private static void writeRoomState(DataOutputStream out, RoomState state) throws IOException {
        writeString(out, state.roomId());
        writeString(out, state.gameId());
        out.writeLong(state.revision());
        writeEnum(out, state.status());
        writeString(out, state.hostId());
        List<PlayerView> players = state.players() == null ? List.of() : state.players();
        if (players.size() > MAX_PLAYERS) {
            throw new IOException("Too many players in one room");
        }
        out.writeInt(players.size());
        for (PlayerView player : players) {
            writeString(out, player.playerId());
            writeString(out, player.name());
            out.writeInt(player.joinOrder());
            out.writeBoolean(player.connected());
            out.writeBoolean(player.forfeited());
            out.writeInt(player.score());
            out.writeInt(player.combo());
            out.writeInt(player.moves());
            out.writeBoolean(player.undoUsed());
            out.writeBoolean(player.canUndo());
        }
        writeBoard(out, state.board());
        out.writeInt(state.level());
        writeString(out, state.currentPlayerId());
        out.writeLong(state.turnNumber());
        out.writeInt(state.movesRemaining());
        writeEnum(out, state.endReason());
    }

    private static RoundResult readRoundResult(DataInputStream in) throws IOException {
        String gameId = readString(in, true);
        String roomId = readString(in, true);
        long revision = in.readLong();
        String endedAt = readString(in, true);
        EndReason reason = readEnum(in, EndReason.class, false);
        String winnerId = readString(in, false);
        int count = in.readInt();
        if (count < 0 || count > MAX_PLAYERS) {
            throw new IOException("Illegal result player count " + count);
        }
        List<PlayerResult> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(new PlayerResult(readString(in, true), readString(in, true), in.readInt(),
                    in.readInt(), in.readInt(), in.readBoolean(), in.readInt()));
        }
        Persistence persistence = readEnum(in, Persistence.class, false);
        return new RoundResult(gameId, roomId, revision, endedAt, reason, winnerId,
                List.copyOf(players), persistence);
    }

    private static void writeRoundResult(DataOutputStream out, RoundResult result) throws IOException {
        writeString(out, result.gameId());
        writeString(out, result.roomId());
        out.writeLong(result.revision());
        writeString(out, result.endedAt());
        writeEnum(out, result.reason());
        writeString(out, result.winnerId());
        List<PlayerResult> players = result.players() == null ? List.of() : result.players();
        if (players.size() > MAX_PLAYERS) {
            throw new IOException("Too many results in one round");
        }
        out.writeInt(players.size());
        for (PlayerResult player : players) {
            writeString(out, player.playerId());
            writeString(out, player.name());
            out.writeInt(player.score());
            out.writeInt(player.moves());
            out.writeInt(player.level());
            out.writeBoolean(player.forfeited());
            out.writeInt(player.rank());
        }
        writeEnum(out, result.persistence());
    }

    private static Leaderboard readLeaderboard(DataInputStream in) throws IOException {
        String serverId = readString(in, true);
        long sequence = in.readLong();
        String generatedAt = readString(in, true);
        int count = in.readInt();
        if (count < 0 || count > MAX_LEADERBOARD) {
            throw new IOException("Illegal leaderboard size " + count);
        }
        List<ScoreEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new ScoreEntry(readString(in, true), readString(in, true),
                    in.readInt(), in.readInt(), readString(in, true)));
        }
        return new Leaderboard(serverId, sequence, generatedAt, List.copyOf(entries));
    }

    private static void writeLeaderboard(DataOutputStream out, Leaderboard leaderboard) throws IOException {
        writeString(out, leaderboard.serverId());
        out.writeLong(leaderboard.sequence());
        writeString(out, leaderboard.generatedAt());
        List<ScoreEntry> entries = leaderboard.entries() == null ? List.of() : leaderboard.entries();
        if (entries.size() > MAX_LEADERBOARD) {
            throw new IOException("Leaderboard holds at most " + MAX_LEADERBOARD + " entries");
        }
        out.writeInt(entries.size());
        for (ScoreEntry entry : entries) {
            writeString(out, entry.gameId());
            writeString(out, entry.name());
            out.writeInt(entry.score());
            out.writeInt(entry.level());
            writeString(out, entry.endedAt());
        }
    }

    // ------------------------------------------------------------------ byte limits

    private static final class CountingInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        CountingInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                add(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                add(read);
            }
            return read;
        }

        private void add(long amount) throws IOException {
            count += amount;
            if (count > limit) {
                throw new IOException("Message is larger than " + limit + " bytes");
            }
        }
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private final long limit;
        private long count;

        CountingOutputStream(OutputStream out, long limit) {
            super(out);
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            out.write(value);
            add(1);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            out.write(buffer, offset, length);
            add(length);
        }

        private void add(long amount) throws IOException {
            count += amount;
            if (count > limit) {
                throw new IOException("Message is larger than " + limit + " bytes");
            }
        }
    }
}
