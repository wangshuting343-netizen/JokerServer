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


    public static Object lockObject = new Object();


    //HashMap<Socket, DataOutputStream> clients = new HashMap<>();
    HashMap<Socket, Player> clients = new HashMap<>();


    RoomManager roomManager = new RoomManager();

    // ---- added for v3: all new protocol code lives in RoomProtocol
    public static final String SERVER_ID = java.util.UUID.randomUUID().toString();
    private final RoomProtocol protocol = new RoomProtocol(this, roomManager);

    public JokerServer(int tcpPort) throws IOException {
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
                    protocol.onDisconnected(player);   // v3: forfeit / host transfer / broadcast
                }
            });
            t.start();
        }
    }

    public void serve(Player player, Socket socket) throws IOException{
        DataInputStream in = new DataInputStream(socket.getInputStream());
        roomManager.assign(player);
        player.playerId = java.util.UUID.randomUUID().toString();   // v3

        if (!protocol.sendInitialPuzzle(player))
            throw new IOException("Unable to send the puzzle to the new player!");

        protocol.handle(player, in);      // v3: reads v2 frames until the socket dies
    }

    public boolean sendPuzzle(Player player, boolean isGameOver){
        boolean success = true;

            try {
                //DataOutputStream out = clients.get(socket);
                //Player player = clients.get(socket);
                DataOutputStream out = player.out;
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
            if(!player.room.started){
                System.out.println("Room: "+player.room.roomId + " not started");
                return;
            }
            Player currentPlayer = player.room.players.get(player.room.currentPlayer);

            if(currentPlayer != player){
                System.out.println("It is not your turn!");
                return;
            }
            boolean changed = player.room.engine.processCommand(player,command);
            if(!changed){
                System.out.println("Not changed!");
                return;
            }
//            player.room.engine.processCommand(player, command);//process the command and update the data in board
            currentPlayer.room.moveLeft--;
            if(currentPlayer.room.moveLeft==0){
                player.room.currentPlayer = (player.room.currentPlayer+1)%player.room.players.size();
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
            otherPlayer.out.writeInt(PLAYER_INFO);
            /// send the player name too!
            byte[] nameBytes = player.name ==null? new byte[0]: player.name.getBytes();
            otherPlayer.out.writeInt(nameBytes.length);
            otherPlayer.out.write(nameBytes);
            otherPlayer.out.writeInt(player.score);
            otherPlayer.out.flush();
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
        new JokerServer(12345);
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
}
