import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

public class Client {
    private String ipAddress;
    private int tcpPort;

    private volatile boolean closing;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    public Client(String ipAddress, int tcpPort){
        this.ipAddress=ipAddress;
        this.tcpPort=tcpPort;

    }

    public void disconnect(){
        closing=true;
        try {if(socket!=null)socket.close();}catch(IOException ignored){}
    }

    public void connect() throws IOException {
        closing=false;
        socket = new Socket(ipAddress, tcpPort);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        Thread receiveThread = new Thread(()->{
            try {
                while (true) {
                    receive();
                }
            }catch (IOException ex){
                System.out.println("Disconnected!");
                if(!closing)ClientViews.error("Disconnected from server. Saved results remain visible; reconnect to continue.");
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    public synchronized void send(int dataType, String message) throws IOException {
        byte[] bytes=message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int len = bytes.length;

        out.writeInt(dataType);

        out.writeInt(len);
        out.write(bytes, 0, len);
        out.flush();
    }

    public void receive() throws IOException{
        int dataType = in.readInt();
        switch(dataType){
            case CWire.TOP: var board=CWire.readBoard(in); ClientViews.history(board); break;
            case CWire.RESULT:
                String id=CWire.readString(in),state=CWire.readString(in),winner=CWire.readString(in);
                int n=in.readInt();if(n<0||n>4)throw new IOException("Results count");
                StringBuilder text=new StringBuilder("Winner: "+winner+"\nPersistence: "+state+"\n");
                for(int j=0;j<n;j++)text.append(CWire.readString(in)).append(": ").append(in.readInt()).append("\n");
                ClientViews.result(id,text.toString());break;
            case CWire.ERROR: ClientViews.error(CWire.readString(in));break;

            case ServerDisplayState.PUZZLE:
                handleReceivePuzzleData();
                break;
            case ServerDisplayState.PLAYER_INFO:
                handleReceivePlayerInfo();
                break;
        }
    }
    public void handleReceivePlayerInfo() throws IOException {
        int len = in.readInt();
        byte[] nameBytes = new byte[len];
        in.readFully(nameBytes);
        String name = new String(nameBytes);
        int score = in.readInt();
        System.out.println("Score of somebody!"+name+" "+score);
    }

    public void handleReceivePuzzleData() throws IOException{
        int width = in.readInt();
        int height = in.readInt();
        int[] puzzle = new int[width * height];

        for(int i=0; i<width*height;i++){
            puzzle[i]=in.readInt();
        }

        boolean isGameOver = in.readBoolean();
        System.out.println("Received: " + Arrays.toString(puzzle));
        ServerDisplayState engine = ServerDisplayState.getInstance();

        engine.board = puzzle;
        engine.gameOver = isGameOver;
    }

//    public static void main(String[] args) throws IOException{
//        Client client = new Client("127.0.0.1", 12345);
//        client.connect();
//        client.send("I am here!!!");
//    }
}
