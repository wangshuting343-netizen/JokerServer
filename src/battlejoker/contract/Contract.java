package battlejoker.contract;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Shared declarations only. Business, transport and database implementations belong to A/B/C. */
public final class Contract {
    private Contract() {}
    public static final int VERSION = 2;
    public static final int NAME = 0, COMMAND = 1, SETTING = 2, PUZZLE = 3, PLAYER_INFO = 4;
    public static final int START_GAME = 5, UNDO = 6, UPLOAD_PUZZLE = 7, GET_ROOM_STATE = 8;
    public static final int GET_TOP10 = 9, GET_RESULT = 10, LEAVE_ROOM = 11, HEARTBEAT = 12;
    public static final int JOINED = 13, ACK = 14, ROOM_STATE = 15, ROUND_RESULT = 16;
    public static final int TOP10 = 17, TOP10_UPDATED = 18, PONG = 19, ERROR = 20;
    public static final int PUZZLE_FILE_MAGIC = 0x424A5032;
    public static final int MAX_STRING_BYTES = 4096;
    public static final int DEFAULT_TCP_PORT = 12345;
    public static final String DEFAULT_MULTICAST_GROUP = "239.25.30.15";
    public static final int DEFAULT_MULTICAST_PORT = 15557;
    public static final int MAX_FRAME_BYTES = 65536;
    public static final int MAX_PUZZLE_FILE_BYTES = 16384;
    public static final int MAX_DATAGRAM_BYTES = 8192;
    public static final int MOVES_PER_TURN = 4;
    public static final int MAX_PLAYERS = 4;

    /** Wire codes are Contract integer constants; never use ordinal(). */
    public enum Type {
        NAME, COMMAND, SETTING, PUZZLE, PLAYER_INFO, START_GAME, UNDO,
        UPLOAD_PUZZLE, GET_ROOM_STATE, GET_TOP10, GET_RESULT, LEAVE_ROOM,
        HEARTBEAT, JOINED, ACK, ROOM_STATE, ROUND_RESULT, TOP10,
        TOP10_UPDATED, PONG, ERROR
    }
    public enum Direction { UP, DOWN, LEFT, RIGHT }
    public enum RoomStatus { WAITING, PLAYING, ENDED }
    public enum EndReason { JOKER, NO_MOVES, ABANDONED }
    public enum Persistence { PENDING, SAVED, FAILED, NOT_APPLICABLE }
    public enum SaveStatus { SAVED, ALREADY_SAVED, CONFLICT }
    public enum ErrorCode {
        INVALID_NAME, NAME_TAKEN, NOT_JOINED, ALREADY_JOINED, NOT_HOST, NOT_YOUR_TURN,
        GAME_NOT_STARTED, GAME_ALREADY_STARTED, GAME_ENDED, INVALID_DIRECTION,
        INVALID_PUZZLE, UNDO_USED, UNDO_UNAVAILABLE, RESULT_NOT_READY,
        RESULT_NOT_FOUND, INTERNAL_ERROR, PERSISTENCE_UNAVAILABLE
    }

    /** Internal wrapper, not an extra wire envelope. */
    public record Message<T>(Type type, T body) {}
    public record Empty() {}
    public record NameRequest(String name) {}
    public record CommandRequest(Direction direction) {}
    public record UploadRequest(PuzzleFile puzzle) {}
    public record ResultRequest(String gameId) {}
    public record Heartbeat(long clientTimeMillis) {}
    public record Pong(long serverTimeMillis) {}
    /** Original section2 code 3: no list count, exactly width*height ints. */
    public record PuzzleData(int width, int height, List<Integer> board, boolean gameOver) {}
    public record LegacyPlayerInfo(int score) {}
    public record Joined(String playerId, String roomId, String gameId,
                         String serverId, int heartbeatSeconds) {}
    public record Ack(Type action, boolean changed, long revision) {}
    public record ErrorBody(Type action, ErrorCode code, String message, boolean retryable) {}

    /** Lists must be immutable snapshots created by the producer. */
    public record PuzzleFile(int version, int size,
                             List<Integer> board, int level) {}
    public record PlayerView(String playerId, String name, int joinOrder,
                             boolean connected, boolean forfeited, int score,
                             int combo, int moves, boolean undoUsed, boolean canUndo) {}
    public record RoomState(String roomId, String gameId, long revision,
                            RoomStatus status, String hostId, List<PlayerView> players,
                            List<Integer> board, int level, String currentPlayerId,
                            long turnNumber, int movesRemaining, EndReason endReason) {}
    public record PlayerResult(String playerId, String name, int score, int moves,
                               int level, boolean forfeited, int rank) {}
    public record RoundResult(String gameId, String roomId, long revision,
                              String endedAt, EndReason reason, String winnerId,
                              List<PlayerResult> players, Persistence persistence) {}
    public record ScoreEntry(String gameId, String name, int score,
                             int level, String endedAt) {}
    public record Leaderboard(String serverId, long sequence, String generatedAt,
                              List<ScoreEntry> entries) {}

    /** Internal server types; never accept Session fields from client messages. */
    public record Session(String connectionId, String playerId, String roomId) {}
    public record Delivery(String connectionId, Message<?> message) {}
    /** deliveries are ordered; send them before closing listed connections. */
    public record Dispatch(List<Delivery> deliveries, List<String> closeConnections) {}

    /** A owns this implementation. It must validate body by Type, including fixed wire field order. */
    public interface BinaryCodec {
        Message<?> read(DataInputStream input) throws IOException;
        void write(DataOutputStream output, Message<?> message) throws IOException;
    }

    /** A owns sessions and rooms. Queries from callbacks are marshalled to the server event queue. */
    public interface RoomService {
        Dispatch handle(String connectionId, Message<?> request);
        Dispatch disconnected(String connectionId);
        Session session(String connectionId);
        Dispatch settlementUpdated(RoundResult result);
        Dispatch leaderboardUpdated(Leaderboard leaderboard);
    }

    /** B owns implementation; callbacks occur on background threads, not the JavaFX thread. */
    public interface ClientTransport extends AutoCloseable {
        void connect(String host, int port, ClientListener listener) throws IOException;
        void send(Message<?> request) throws IOException;
        boolean isConnected();
        @Override void close();
    }
    public interface ClientListener {
        void onMessage(Message<?> message);
        void onClosed(String reason);
    }

    /** C owns implementation; only its database worker calls this synchronous repository. */
    public interface GameRepository extends AutoCloseable {
        void initialize() throws SQLException;
        SaveStatus saveWinner(ScoreEntry winner) throws SQLException;
        List<ScoreEntry> top10() throws SQLException;
        @Override void close() throws SQLException;
    }

    /** C serializes DB work, owns leaderboard sequence, and retries failed settlements by gameId. */
    public interface SettlementService extends AutoCloseable {
        void start(Top10Listener top10Listener, SettlementListener settlementListener)
            throws Exception;
        CompletableFuture<RoundResult> settle(RoundResult frozenResult, ScoreEntry winner);
        CompletableFuture<Leaderboard> top10();
        @Override void close();
    }
    public interface Top10Listener {
        void onUpdated(Leaderboard leaderboard);
    }
    /** Includes eventual successful retry; A posts callback onto its event queue. */
    public interface SettlementListener {
        void onUpdated(RoundResult result);
    }

    /** C owns publisher and standalone subscriber. Payload is int TOP10_UPDATED + binary Leaderboard record. */
    public interface MulticastPublisher extends AutoCloseable {
        void start(String group, int port, String networkInterfaceName) throws IOException;
        void update(Leaderboard leaderboard);
        @Override void close();
    }
}
