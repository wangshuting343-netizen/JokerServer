import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

public class JokerServer {
    public static final int NAME = 0;
    public static final int COMMAND = 1;
    public static final int SETTING = 2;
    public static final int PUZZLE = 3;
    //more and more for different purposes
    public static final int PLAYER_INFO = 4;
    public static final int START_GAME = 5;
    public static final int UNDO = 6;
    public static final int UPLOAD_PUZZLE = 7;
    public static final int GET_TOP10 = 8;


    public static Object lockObject = new Object();


    //HashMap<Socket, DataOutputStream> clients = new HashMap<>();
    HashMap<Socket, Player> clients = new HashMap<>();


    final MulticastPublisher publisher = new MulticastPublisher();
    final SettlementService settlement = new SettlementService(System.getProperty("bj.db","data/battleJoker.db"),publisher);
    final ResultCache resultCache=new ResultCache();
    RoomManager roomManager = new RoomManager();

    public JokerServer(int tcpPort) throws IOException {
        Runtime.getRuntime().addShutdownHook(new Thread(settlement::close));
        ServerSocket srvSocket = new ServerSocket(tcpPort);

        while(true){
            Socket socket=srvSocket.accept();

            Player player = new Player();

            synchronized (lockObject){
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                player.out = out;
                //clients.put(socket, out);
                clients.put(socket, player);
            }

            Thread t = new Thread(()->{
                try {
                    serve(player, socket);
                }catch (IOException ex){
                    System.out.println("Disconnected..."+socket.getInetAddress());
                    synchronized (lockObject){
                        clients.remove(socket);
                    }
                    roomManager.removePlayer(player);
                }
            });
            t.start();
        }
    }

    public void serve(Player player, Socket socket) throws IOException{
        DataInputStream in = new DataInputStream(socket.getInputStream());
        GameRoom room = roomManager.assign(player);

        boolean success = false;
        synchronized (room.lock) {
            success = sendPuzzle(player, room.engine.isGameOver);
        }

        if(!success)
            throw new IOException("Unable to send the puzzle to the new player!");

        byte[] buffer = new byte[1024];

        while(true){

            int dataType = in.readInt();

            String payload=CWire.readString(in);
            byte[] content=payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int len=content.length;
            buffer=content;

            switch(dataType){
                case NAME:
                    handleName(player, payload);
                    break;
                case COMMAND:
                    handleCommand(player, payload);
                    break;
                case START_GAME:
                    handleStartGame(player);
                    break;
                case UNDO:
                    handleUndo(player);
                    break;
                case UPLOAD_PUZZLE:
                    handleUpload(player, in);
                    break;
                case GET_TOP10:
                    handleTop10(player);
                    break;
                case 25:
                    var cached=resultCache.get(payload,player.sessionId);
                    if(cached!=null)writeResult(player,cached);
                    else sendError(player,"Result unavailable or expired for this session");
                    break;
                default:
                    throw new IOException("Unknown data type");
            }

            System.out.println("Receive " + dataType + ": " + payload);
        }
    }

    public boolean sendPuzzle(Player player, boolean isGameOver){
        boolean success = true;

            try {
                //DataOutputStream out = clients.get(socket);
                //Player player = clients.get(socket);
                DataOutputStream out = player.out;
                synchronized(out){
                out.writeInt(PUZZLE);// the type of data I am going to send

                System.out.println("Send puzzle data");

                out.writeInt(player.room.engine.SIZE);// the width of the puzzle
                out.writeInt(player.room.engine.SIZE);// the height of the puzzle
                for (int i = 0; i < player.room.engine.SIZE * player.room.engine.SIZE; i++) {
                    out.writeInt(player.room.engine.board[i]);
                }

                out.writeBoolean(isGameOver);
                out.flush();//force data send

                System.out.println("Send Completed" + Arrays.toString(player.room.engine.board));
                }

            }catch (IOException ex){
                success = false;
            }

        return success;
    }

    public void handleCommand(Player player, String command){
        System.out.println("The player wants to run the command: " + command);
        if(player.room==null)
            return;
        synchronized (player.room.lock) {
            if(player.room.engine.isGameOver)return;
            if(!player.room.started){
                System.out.println("Room: "+player.room.roomId + " not started");
                return;
            }
            Player currentPlayer = player.room.players.get(player.room.currentPlayer);

            if(currentPlayer != player){
                System.out.println("It is not your turn!");
                return;
            }
            int[] boardBeforeMove = Arrays.copyOf(player.room.engine.board, player.room.engine.board.length);
            boolean changed = player.room.engine.processCommand(player,command);
            if(!changed){
                System.out.println("Not changed!");
                if(player.room.engine.checkGameOver()){
                    System.out.println("Game Over!");
                    settleGame(player.room);
                }
                return;
            }
            if(player.room.engine.checkGameOver()){
                System.out.println("Game Over!");
                settleGame(player.room);
            }
            player.room.lastBoard = boardBeforeMove;
//            player.room.engine.processCommand(player, command);//process the command and update the data in board
            currentPlayer.room.moveLeft--;
            if(currentPlayer.room.moveLeft==0){
                do{player.room.currentPlayer = (player.room.currentPlayer+1)%player.room.players.size();}while(!player.room.players.get(player.room.currentPlayer).connected);
                player.room.moveLeft=4;
                Player nextPlayer = player.room.players.get(player.room.currentPlayer);
                System.out.println(nextPlayer.name+ " It is your turn!");
            }
            System.out.println(Arrays.toString(player.room.engine.board));

            ///send the updated puzzle data to other players in the same room
            for (Player p: player.room.players){
                if(sendPuzzle(p, player.room.engine.isGameOver)) {
                    sendPlayerInfo(player, p);
                }
            }
        }
    }
    public void sendPlayerInfo(Player player, Player otherPlayer){
        try {
            //Player otherPlayer = clients.get(socket);
            synchronized(otherPlayer.out){
            otherPlayer.out.writeInt(PLAYER_INFO);
            /// send the player name too!
            byte[] nameBytes = player.name ==null? new byte[0]: player.name.getBytes();
            otherPlayer.out.writeInt(nameBytes.length);
            otherPlayer.out.write(nameBytes);
            otherPlayer.out.writeInt(player.score);
            otherPlayer.out.flush();
            }
        } catch (IOException ex){
            System.out.println("Cannot send player info, but i don't remove the player info from the map");
        }
    }

    public void handleName(Player player, String name){
        System.out.println("The player wants to set their name to: [" +name+"]");
        synchronized (lockObject) {
            player.name = name;
            sendPlayerInfo(player, player);
        }
    }

    public static void main(String[] args) throws IOException{
        new JokerServer(Integer.getInteger("bj.tcp",12345));
    }

    public void handleStartGame(Player player){
        if(player.room==null)
            return;
        synchronized (player.room.lock){
            if(player.room.host!=player){
                System.out.println("Only host can start the game!");
                return;
            }
            if(player.room.started){
                System.out.println("Game already started!");
                return;
            }
            player.room.started=true;
            System.out.println("Room " + player.room.roomId + " started by " + player.name);
        }
    }
    public void handleUndo(Player player) {
        if (player.room == null || !player.room.started || player.room.engine.isGameOver)
            return;
        synchronized (player.room.lock) {
            Player currentPlayer = player.room.players.get(player.room.currentPlayer);
            if (!(player == currentPlayer)) {
                System.out.println("It is not your turn!");
                return;
            }
            if (player.hasUndone) {
                System.out.println("You have already used your undo!");
                return;
            }
            if (player.room.lastBoard == null) {
                System.out.println("No action to undo!");
                return;
            }
            player.room.engine.board = Arrays.copyOf(player.room.lastBoard, player.room.lastBoard.length);
            player.hasUndone = true;
            player.room.moveLeft++;
            player.room.lastBoard = null;
            System.out.println(player.name + " has used undo this turn!");

            for (Player p : player.room.players) {
                if (sendPuzzle(p, player.room.engine.isGameOver)) {
                    sendPlayerInfo(player, p);
                }
            }

        }

    }
    public void handleUpload(Player player, DataInputStream in){
        int[] newBoard=new int[16];
        try{for(int i=0;i<16;i++)newBoard[i]=in.readInt();}catch(IOException e){throw new java.io.UncheckedIOException(e);}
        if(player.room==null || !player.room.started || player.room.engine.isGameOver)return;
        synchronized(player.room.lock){
            try{
                if(java.util.Arrays.stream(newBoard).anyMatch(v->v<0||v>13) || java.util.Arrays.stream(newBoard).allMatch(v->v==0))return;
                Player currentPlayer = player.room.players.get(player.room.currentPlayer);
                if(currentPlayer!=player){
                    System.out.println("It is not your turn!");
                    return;
                }
                player.room.engine.board = newBoard;
                player.room.engine.level=java.util.Arrays.stream(newBoard).max().orElse(1);
                player.room.lastBoard=null;
                player.room.moveLeft--;
                if(player.room.moveLeft == 0){
                    do{player.room.currentPlayer = (player.room.currentPlayer+1)%player.room.players.size();}while(!player.room.players.get(player.room.currentPlayer).connected);
                    player.room.moveLeft=4;
                    System.out.println(player.room.players.get(player.room.currentPlayer).name+" it's your turn!");
                    player.room.lastBoard = null;
                }
                for(Player p: player.room.players){
                    if(sendPuzzle(p, player.room.engine.isGameOver)) {
                        sendPlayerInfo(player, p);
                    }
                }
            } catch (RuntimeException e) {
                System.out.println("Fail to upload the board!");
            }
        }
    }
    public void settleGame(GameRoom room){
        if(room.isRecordSaved)return;
        room.isRecordSaved=true;
        room.engine.isGameOver=true;
        room.resultTime=java.time.Instant.now().toString();
        Player winner=room.players.stream().filter(p->p.connected).max(java.util.Comparator.comparingInt(p->p.score)).orElse(null);
        if(winner==null)return;
        room.resultNames=room.players.stream().map(p->(p.name==null?"":p.name)+(p.connected?"":" (forfeited)")).toList();
        room.resultScores=room.players.stream().map(p->p.score).toList();
        sendResult(room,"PENDING",winner.name);
        ScoreEntry entry=new ScoreEntry(room.gameId,winner.name,winner.score,room.engine.level,room.resultTime);
        settlement.save(entry,state->{
            sendResult(room,state,winner.name);
            if(state.equals("SAVED")){
                java.util.List<Player> targets;
                synchronized(lockObject){targets=java.util.List.copyOf(clients.values());}
                for(Player target:targets)synchronized(target.out){try{CWire.writeBoard(target.out,settlement.top());target.out.flush();}catch(IOException ex){System.err.println(ex);}}
            }
        });
    }
    private void sendResult(GameRoom room,String state,String winner){
        synchronized(room.lock){
            room.persistenceState=state;room.winnerName=winner;
            var snapshot=new ResultCache.Result(room.gameId,state,winner,room.resultNames,room.resultScores);
            resultCache.put(snapshot,room.players.stream().map(p->p.sessionId).collect(java.util.stream.Collectors.toSet()));
            for(Player player:room.players)if(player.connected)writeResult(player,snapshot);
        }
    }
    // Send a cached snapshot only to its authorized requester or current room participant.
    private void writeResult(Player player,ResultCache.Result result){
        synchronized(player.out){try{
            player.out.writeInt(CWire.RESULT);CWire.str(player.out,result.gameId());CWire.str(player.out,result.state());CWire.str(player.out,result.winner());
            player.out.writeInt(result.names().size());
            for(int i=0;i<result.names().size();i++){CWire.str(player.out,result.names().get(i));player.out.writeInt(result.scores().get(i));}
            player.out.flush();
        }catch(IOException ex){System.err.println(ex);}}
    }
    private void sendError(Player p,String text){synchronized(p.out){try{p.out.writeInt(CWire.ERROR);CWire.str(p.out,text);p.out.flush();}catch(IOException e){System.err.println(e);}}}
    public void handleTop10(Player player){
        settlement.query(board->{synchronized(player.out){try{CWire.writeBoard(player.out,board);player.out.flush();}catch(IOException e){System.err.println(e);}}},error->{synchronized(player.out){try{player.out.writeInt(CWire.ERROR);CWire.str(player.out,"History unavailable: "+error.getMessage());player.out.flush();}catch(IOException e){System.err.println(e);}}});
    }
}
