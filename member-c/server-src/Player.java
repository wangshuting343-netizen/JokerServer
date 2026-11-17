import java.io.DataOutputStream;

public class Player {
    public final String sessionId=java.util.UUID.randomUUID().toString();
    public boolean connected=true;
    public int score;
    public String name;
    public int combo;
    public DataOutputStream out;
    public GameRoom room;
    public boolean hasUndone = false;
}
