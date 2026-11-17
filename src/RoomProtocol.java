import battlejoker.codec.BinaryCodecImpl;
import battlejoker.contract.Contract;

import java.io.DataInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The whole V3 protocol lives here, so {@link JokerServer} itself only needs three
 * small edits. All game rules stay in {@link ServerEngine}, untouched.
 *
 * <p>Threading: every room mutation happens inside {@code room.lock}; every socket
 * write goes through {@link #send} which locks that player's output stream.</p>
 */
public class RoomProtocol {

    private final JokerServer server;
    private final RoomManager roomManager;
    private final BinaryCodecImpl codec = new BinaryCodecImpl();

    public RoomProtocol(JokerServer server, RoomManager roomManager) {
        this.server = server;
        this.roomManager = roomManager;
    }

    // ------------------------------------------------------------------ entry points

    /** Sends the legacy 77-byte PUZZLE frame that every client expects first. */
    public boolean sendInitialPuzzle(Player player) {
        synchronized (player.out) {
            boolean sent = server.sendPuzzle(player, player.room.engine.isGameOver);
            player.synced = sent;      // only now may broadcasts reach this socket
            return sent;
        }
    }

    /** Reads messages until the socket dies; throws IOException to end the session. */
    public void handle(Player player, DataInputStream in) throws IOException {
        while (true) {
            Contract.Message<?> message = codec.read(in);
            switch (message.type()) {
                case NAME -> handleName(player, (Contract.NameRequest) message.body());
                case COMMAND -> handleCommand(player, (Contract.CommandRequest) message.body());
                case START_GAME -> handleStartGame(player);
                case UNDO -> handleUndo(player);
                case UPLOAD_PUZZLE -> handleUpload(player, (Contract.UploadRequest) message.body());
                case GET_ROOM_STATE -> send(player, new Contract.Message<>(
                        Contract.Type.ROOM_STATE, toRoomState(player.room)));
                case GET_TOP10 -> handleTop10(player);
                case GET_RESULT -> handleResult(player, (Contract.ResultRequest) message.body());
                case LEAVE_ROOM -> handleLeave(player);
                case HEARTBEAT -> send(player, new Contract.Message<>(
                        Contract.Type.PONG, new Contract.Pong(System.currentTimeMillis())));
                default -> System.out.println("Unsupported message " + message.type());
            }
        }
    }

    /** Called from the catch block in JokerServer when a socket dies. */
    public void onDisconnected(Player player) {
        GameRoom room = player.room;
        if (room == null) {
            return;
        }
        synchronized (room.lock) {
            player.connected = false;
            if (room.started) {
                // A3: keep the score, mark the player as forfeited, skip their turns.
                player.forfeited = true;
                skipPast(room, player);
            } else {
                roomManager.removePlayer(player);
            }

            if (room.players.isEmpty()) {
                return;
            }
            if (allDisconnected(room)) {
                for (Player p : new ArrayList<>(room.players)) {
                    roomManager.removePlayer(p);          // nobody left: close the room
                }
                return;
            }
            fixHost(room, player);
            room.revision++;
            broadcast(room);
        }
    }

    // ------------------------------------------------------------------ sending

    private void send(Player player, Contract.Message<?> message) {
        if (player.out == null) {
            return;
        }
        try {
            synchronized (player.out) {
                codec.write(player.out, message);
                player.out.flush();
            }
        } catch (IOException ex) {
            player.connected = false;
        }
    }

    private void sendAck(Player player, Contract.Type action, boolean changed, long revision) {
        send(player, new Contract.Message<>(Contract.Type.ACK,
                new Contract.Ack(action, changed, revision)));
    }

    private void sendError(Player player, Contract.Type action, Contract.ErrorCode code,
                           String text, boolean retryable) {
        send(player, new Contract.Message<>(Contract.Type.ERROR,
                new Contract.ErrorBody(action, code, text, retryable)));
    }

    /** revision+1, then send the snapshot to everyone in the room. */
    private long broadcast(GameRoom room) {
        Contract.Message<?> message = new Contract.Message<>(
                Contract.Type.ROOM_STATE, toRoomState(room));
        for (Player p : new ArrayList<>(room.players)) {
            if (p.connected && p.synced) {
                send(p, message);
            }
        }
        return room.revision;
    }

    private void broadcastAndBump(GameRoom room) {
        room.revision++;
        broadcast(room);
    }

    // ------------------------------------------------------------------ snapshot

    private Contract.RoomState toRoomState(GameRoom room) {
        List<Contract.PlayerView> views = new ArrayList<>();
        for (Player p : room.players) {
            if (p.name == null) {
                continue;               // connected but has not sent NAME yet: not in the room list
            }
            views.add(new Contract.PlayerView(p.playerId, p.name,
                    room.players.indexOf(p) + 1, p.connected, p.forfeited,
                    p.score, p.combo, p.moves, p.undoUsed,
                    room.undoOwner == p && !p.undoUsed));
        }

        List<Integer> board = new ArrayList<>(room.engine.board.length);
        for (int v : room.engine.board) {
            board.add(v);
        }

        Player current = current(room);
        Player host = room.host;
        if (host == null || !room.players.contains(host)) {
            host = firstConnected(room);
        }
        if (host == null) {
            host = firstNamed(room);
        }
        Contract.RoomStatus status = !room.started ? Contract.RoomStatus.WAITING
                : room.engine.isGameOver ? Contract.RoomStatus.ENDED
                : Contract.RoomStatus.PLAYING;

        Contract.EndReason reason = null;
        if (status == Contract.RoomStatus.ENDED) {
            reason = hasJoker(room) ? Contract.EndReason.JOKER : Contract.EndReason.NO_MOVES;
        }

        return new Contract.RoomState(String.valueOf(room.roomId), room.gameId, room.revision,
                status, host == null ? "" : host.playerId, views,
                board, room.engine.level, current == null ? null : current.playerId,
                room.turnNumber, room.moveLeft, reason);
    }

    private static Player current(GameRoom room) {
        if (room.players.isEmpty()) {
            return null;
        }
        Player player = room.players.get(Math.min(room.currentPlayer, room.players.size() - 1));
        return player.name == null ? null : player;    // an unnamed connection never owns a turn
    }

    /** Moves the turn index forward, skipping unnamed and disconnected players. */
    private static void advanceTurn(GameRoom room) {
        int size = room.players.size();
        for (int i = 1; i <= size; i++) {
            int index = (room.currentPlayer + i) % size;
            Player p = room.players.get(index);
            if (p.name != null && p.connected) {
                room.currentPlayer = index;
                return;
            }
        }
    }

    private static boolean hasJoker(GameRoom room) {
        for (int v : room.engine.board) {
            if (v >= 14) {
                return true;
            }
        }
        return false;
    }

    private static Player firstConnected(GameRoom room) {
        for (Player p : room.players) {
            if (p.connected && p.name != null) {   // a half-open connection cannot host
                return p;
            }
        }
        return null;
    }

    private static Player firstNamed(GameRoom room) {
        for (Player p : room.players) {
            if (p.name != null) {
                return p;
            }
        }
        return null;
    }

    /** Re-elects the host whenever the current one left or was never a member. */
    private static void fixHost(GameRoom room, Player leaving) {
        if (room.host == null || room.host == leaving || !room.players.contains(room.host)) {
            room.host = firstConnected(room);
        }
    }

    private static boolean allDisconnected(GameRoom room) {
        for (Player p : room.players) {
            if (p.connected) {
                return false;
            }
        }
        return true;
    }

    /** Hands the turn to the next connected player after {@code leaving}. */
    private static void skipPast(GameRoom room, Player leaving) {
        if (current(room) != leaving) {
            return;
        }
        int before = room.currentPlayer;
        advanceTurn(room);
        if (room.currentPlayer != before) {
            room.moveLeft = Contract.MOVES_PER_TURN;
            room.turnNumber++;
        }
    }

    // ------------------------------------------------------------------ handlers

    public void handleName(Player player, Contract.NameRequest request) {
        String name = request.name();
        System.out.println("The player wants to set their name to: [" + name + "]");
        if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > 32) {
            sendError(player, Contract.Type.NAME, Contract.ErrorCode.INVALID_NAME,
                    "Name must be 1-32 characters", false);
            return;
        }
        GameRoom room = player.room;
        synchronized (room.lock) {
            if (player.name != null) {
                sendError(player, Contract.Type.NAME, Contract.ErrorCode.ALREADY_JOINED,
                        "You already joined", false);
                return;
            }
            for (Player p : room.players) {
                if (p != player && p.connected && name.equals(p.name)) {
                    sendError(player, Contract.Type.NAME, Contract.ErrorCode.NAME_TAKEN,
                            "Name already used in this room", false);
                    return;
                }
            }
            player.name = name;
            player.connected = true;
            if (room.host == null) {
                room.host = player;               // only when the previous host is gone
            }

            send(player, new Contract.Message<>(Contract.Type.JOINED, new Contract.Joined(
                    player.playerId, String.valueOf(room.roomId), room.gameId,
                    JokerServer.SERVER_ID, 10)));
            broadcastAndBump(room);
        }
    }

    public void handleCommand(Player player, Contract.CommandRequest request) {
        System.out.println("The player wants to run the command: " + request.direction());
        GameRoom room = player.room;
        if (room == null) {
            sendError(player, Contract.Type.COMMAND, Contract.ErrorCode.NOT_JOINED,
                    "You are not in a room", false);
            return;
        }
        synchronized (room.lock) {
            if (!room.started) {
                sendError(player, Contract.Type.COMMAND, Contract.ErrorCode.GAME_NOT_STARTED,
                        "The game has not started", false);
                return;
            }
            if (room.engine.isGameOver) {
                sendError(player, Contract.Type.COMMAND, Contract.ErrorCode.GAME_ENDED,
                        "The game is over", false);
                return;
            }
            Player current = current(room);
            if (current != player || !player.connected) {
                sendError(player, Contract.Type.COMMAND, Contract.ErrorCode.NOT_YOUR_TURN,
                        "It is not your turn", true);
                return;
            }

            room.undoBoard = room.engine.board.clone();
            room.undoLevel = room.engine.level;
            room.undoOwner = player;

            boolean changed = room.engine.processCommand(player, request.direction().name());
            if (!changed) {
                room.undoOwner = null;
                sendAck(player, Contract.Type.COMMAND, false, room.revision);
                return;                         // board unchanged: nothing to broadcast
            }

            player.moves++;
            room.moveLeft--;
            if (room.moveLeft <= 0) {
                advanceTurn(room);
                room.moveLeft = Contract.MOVES_PER_TURN;
                room.turnNumber++;
            }

            broadcastAndBump(room);
            sendAck(player, Contract.Type.COMMAND, true, room.revision);

            // the old PUZZLE frame is byte-compatible, keep sending it for old clients
            for (Player p : new ArrayList<>(room.players)) {
                if (p.connected && p.synced) {
                    synchronized (p.out) {
                        server.sendPuzzle(p, room.engine.isGameOver);
                    }
                }
            }

            if (room.engine.isGameOver) {
                endGame(room);
            }
        }
    }

    public void handleStartGame(Player player) {
        GameRoom room = player.room;
        if (room == null) {
            return;
        }
        synchronized (room.lock) {
            if (room.host != player) {
                sendError(player, Contract.Type.START_GAME, Contract.ErrorCode.NOT_HOST,
                        "Only the host can start the game", false);
                return;
            }
            if (room.started) {
                sendError(player, Contract.Type.START_GAME,
                        Contract.ErrorCode.GAME_ALREADY_STARTED, "Game already started", false);
                return;
            }
            room.started = true;
            room.gameId = UUID.randomUUID().toString();
            room.currentPlayer = 0;
            room.moveLeft = Contract.MOVES_PER_TURN;
            room.turnNumber = 1;
            for (Player p : room.players) {
                p.undoUsed = false;
                p.moves = 0;
                p.forfeited = false;
            }
            System.out.println("Room " + room.roomId + " started by " + player.name);

            broadcastAndBump(room);
            sendAck(player, Contract.Type.START_GAME, true, room.revision);
        }
    }

    public void handleUndo(Player player) {
        GameRoom room = player.room;
        if (room == null) {
            return;
        }
        synchronized (room.lock) {
            if (player.undoUsed) {
                sendError(player, Contract.Type.UNDO, Contract.ErrorCode.UNDO_USED,
                        "You already used undo this game", false);
                return;
            }
            if (room.undoOwner != player || room.undoBoard == null) {
                sendError(player, Contract.Type.UNDO, Contract.ErrorCode.UNDO_UNAVAILABLE,
                        "Undo is not available now", true);
                return;
            }
            System.arraycopy(room.undoBoard, 0, room.engine.board, 0, room.undoBoard.length);
            room.engine.level = room.undoLevel;
            room.engine.isGameOver = false;
            player.undoUsed = true;
            room.undoOwner = null;
            room.moveLeft++;                    // that move does not count

            broadcastAndBump(room);
            sendAck(player, Contract.Type.UNDO, true, room.revision);
        }
    }

    public void handleUpload(Player player, Contract.UploadRequest request) {
        GameRoom room = player.room;
        if (room == null) {
            return;
        }
        Contract.PuzzleFile puzzle = request.puzzle();
        synchronized (room.lock) {
            String problem = checkPuzzle(puzzle);
            if (problem != null) {
                sendError(player, Contract.Type.UPLOAD_PUZZLE,
                        Contract.ErrorCode.INVALID_PUZZLE, problem, false);
                return;
            }
            for (int i = 0; i < room.engine.board.length; i++) {
                room.engine.board[i] = puzzle.board().get(i);
            }
            room.engine.level = puzzle.level();
            room.engine.isGameOver = false;
            room.undoOwner = null;

            if (room.started) {
                room.moveLeft--;
                if (room.moveLeft <= 0) {
                    advanceTurn(room);
                    room.moveLeft = Contract.MOVES_PER_TURN;
                    room.turnNumber++;
                }
            }

            broadcastAndBump(room);
            sendAck(player, Contract.Type.UPLOAD_PUZZLE, true, room.revision);
        }
    }

    private static String checkPuzzle(Contract.PuzzleFile puzzle) {
        if (puzzle == null) {
            return "Puzzle is empty";
        }
        if (puzzle.version() != 2) {
            return "Unsupported version";
        }
        if (puzzle.size() != 4) {
            return "Size must be 4";
        }
        if (puzzle.board() == null || puzzle.board().size() != 16) {
            return "Need 16 cards";
        }
        int highest = 0;
        for (int v : puzzle.board()) {
            if (v < 0 || v > 13) {
                return "Card values must be 0..13";
            }
            highest = Math.max(highest, v);
        }
        if (highest == 0) {
            return "Puzzle must contain at least one card";
        }
        if (puzzle.level() != highest) {
            return "Level must equal the highest card";
        }
        return null;
    }

    public void handleLeave(Player player) {
        GameRoom room = player.room;
        if (room == null) {
            return;
        }
        synchronized (room.lock) {
            sendAck(player, Contract.Type.LEAVE_ROOM, true, room.revision);
            player.connected = false;
            if (room.started) {
                player.forfeited = true;
            }
            roomManager.removePlayer(player);
            if (room.players.isEmpty()) {
                return;
            }
            fixHost(room, player);
            if (room.currentPlayer >= room.players.size()) {
                room.currentPlayer = 0;
            }
            room.revision++;
            broadcast(room);
        }
    }

    /** Temporary until C's repository is connected: an empty board. */
    public void handleTop10(Player player) {
        send(player, new Contract.Message<>(Contract.Type.TOP10,
                new Contract.Leaderboard(JokerServer.SERVER_ID, System.currentTimeMillis(),
                        Instant.now().toString(), List.of())));
    }

    /** Temporary until C's SettlementService is connected. */
    public void handleResult(Player player, Contract.ResultRequest request) {
        sendError(player, Contract.Type.GET_RESULT, Contract.ErrorCode.RESULT_NOT_FOUND,
                "No settled result yet", true);
    }

    /** Broadcasts ROUND_RESULT as PENDING; C switches it to SAVED later. */
    private void endGame(GameRoom room) {
        room.started = false;
        Contract.EndReason reason = hasJoker(room)
                ? Contract.EndReason.JOKER : Contract.EndReason.NO_MOVES;

        List<Contract.PlayerResult> results = new ArrayList<>();
        List<Player> sorted = new ArrayList<>(room.players);
        sorted.removeIf(p -> p.name == null);   // connected but never named: not a participant
        sorted.sort((a, b) -> Integer.compare(b.score, a.score));
        for (int i = 0; i < sorted.size(); i++) {
            Player p = sorted.get(i);
            results.add(new Contract.PlayerResult(p.playerId, p.name, p.score, p.moves,
                    room.engine.level, p.forfeited, i + 1));
        }
        String winnerId = sorted.isEmpty() ? null : sorted.get(0).playerId;

        room.revision++;
        Contract.RoundResult result = new Contract.RoundResult(room.gameId,
                String.valueOf(room.roomId), room.revision,
                Instant.now().toString(), reason, winnerId, results,
                Contract.Persistence.PENDING);

        Contract.Message<?> message = new Contract.Message<>(
                Contract.Type.ROUND_RESULT, result);
        for (Player p : new ArrayList<>(room.players)) {
            if (p.connected && p.synced) {
                send(p, message);
            }
        }

        // forfeited players have been recorded; drop them so the room can host a new game
        for (Player p : new ArrayList<>(room.players)) {
            if (!p.connected) {
                roomManager.removePlayer(p);
            }
        }
        fixHost(room, null);
        broadcast(room);
        System.out.println("Room " + room.roomId + " ended: " + reason);
    }
}
