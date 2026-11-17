import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameRoom {
    public int roomId;
    public ServerEngine engine = new ServerEngine();
    public List<Player> players = new ArrayList<>();
    public Player host;
    public boolean started = false;
    public final Object lock = new Object();
    public int currentPlayer = 0;
    public int moveLeft = 4;

    //added for v3 (pure additions, nothing removed)
    public String gameId = UUID.randomUUID().toString();
    public long revision = 0;
    public long turnNumber = 0;
    public int[] undoBoard;
    public int undoLevel;
    public Player undoOwner;

    public GameRoom(int roomId){
        this.roomId = roomId;
    }
}
