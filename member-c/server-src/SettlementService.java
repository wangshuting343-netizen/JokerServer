import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
// Serialize persistence work and publish only committed leaderboard entries.
public class SettlementService implements AutoCloseable {
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
 private final String path,server=UUID.randomUUID().toString();private GameRepository repo;private long seq;
 private final Map<String,ScoreEntry> jobs=new HashMap<>();
 private volatile CWire.Board board=new CWire.Board(server,0,List.of());
 private final MulticastPublisher publisher;
 // Start the database worker and load the initial historical leaderboard.
 public SettlementService(String path,MulticastPublisher p){this.path=path;publisher=p;worker.execute(()->{try{initialize();}catch(Exception e){System.err.println("Database unavailable: "+e);}});}
 // Open the repository when available and refresh the committed snapshot.
 private void initialize()throws Exception {if(repo==null)repo=new GameRepository(path);board=new CWire.Board(server,seq,repo.top10());publisher.update(board);}
 // Expose the latest immutable leaderboard snapshot.
 public CWire.Board top(){return board;}
 // Report database query failures instead of returning a false success.
 public void query(Consumer<CWire.Board> ok,Consumer<Exception> fail){worker.execute(()->{try{initialize();ok.accept(board);}catch(Exception e){fail.accept(e);}});}
 // Reject conflicting game IDs before scheduling persistence.
 public void save(ScoreEntry e,Consumer<String> callback){worker.execute(()->{var old=jobs.putIfAbsent(e.gameId(),e);if(old!=null&&!old.equals(e)){callback.accept("CONFLICT");return;}attempt(e,callback,0);});}
 // Retry storage failures with bounded delays and report each save state.
 private void attempt(ScoreEntry e,Consumer<String> callback,int attempt){try {initialize();String result=repo.save(e);if(result.equals("CONFLICT")){callback.accept("CONFLICT");return;}if(result.equals("SAVED"))seq++;board=new CWire.Board(server,seq,repo.top10());publisher.update(board);callback.accept("SAVED");}catch(Exception ex){callback.accept("FAILED: "+ex.getMessage());long[] delays={1,2,5,10,30};worker.schedule(()->attempt(e,callback,attempt+1),delays[Math.min(attempt,4)],TimeUnit.SECONDS);}}
 // Stop retries and release the repository and multicast publisher.
 public void close(){worker.shutdownNow();try{worker.awaitTermination(5,TimeUnit.SECONDS);if(repo!=null)repo.close();}catch(Exception e){System.err.println(e);}publisher.close();}
}
