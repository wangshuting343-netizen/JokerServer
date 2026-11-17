import java.util.ArrayList;
import java.util.List;

public class GameRoom {
    public final String gameId=java.util.UUID.randomUUID().toString();
    public String resultTime;
    public String persistenceState,winnerName;
    public java.util.List<String> resultNames;
    public java.util.List<Integer> resultScores;
    public int roomId;
    public ServerEngine engine = new ServerEngine();
    public List<Player> players = new ArrayList<>();
    public Player host;
    public boolean started = false;
    public final Object lock = new Object();
    public int currentPlayer = 0;
    public int moveLeft = 4;
    public int[] lastBoard = null;
    public boolean isRecordSaved = false;

    public GameRoom(int roomId){
        this.roomId = roomId;
    }
}
