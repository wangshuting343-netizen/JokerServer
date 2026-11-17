import java.io.DataOutputStream;

public class Player {
    public int score;
    public String name;
    public int combo;
    public DataOutputStream out;
    public GameRoom room;

    // ---- added for v3 (pure additions, nothing removed)
    public String playerId;
    public int moves;
    public boolean connected = true;
    public boolean forfeited;
    public boolean undoUsed;
    /** True once the initial PUZZLE frame has been written; before that the client cannot parse frames. */
    public boolean synced;
}
