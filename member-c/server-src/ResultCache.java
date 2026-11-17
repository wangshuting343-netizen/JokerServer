import java.util.*;
import java.util.function.LongSupplier;
// Retain immutable round results independently of room membership for thirty minutes.
public final class ResultCache {
 public record Result(String gameId,String state,String winner,List<String> names,List<Integer> scores){
  public Result {names=List.copyOf(names);scores=List.copyOf(scores);}
 }
 private record Entry(Result result,Set<String> participants,long expires){}
 private final Map<String,Entry> entries=new HashMap<>();
 private final LongSupplier clock;
 public ResultCache(){this(System::currentTimeMillis);}
 public ResultCache(LongSupplier clock){this.clock=clock;}
 // Refresh retention after every persistence update without granting access to other sessions.
 public synchronized void put(Result result,Set<String> participants){purge();entries.put(result.gameId(),new Entry(result,Set.copyOf(participants),clock.getAsLong()+1_800_000));}
 public synchronized Result get(String gameId,String session){purge();var entry=entries.get(gameId);return entry!=null&&entry.participants().contains(session)?entry.result():null;}
 // Remove expired snapshots during access and periodic server maintenance.
 public synchronized void purge(){long now=clock.getAsLong();entries.values().removeIf(e->e.expires()<=now);}
}
