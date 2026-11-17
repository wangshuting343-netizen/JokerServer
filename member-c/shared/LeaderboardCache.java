import java.util.*;
// Keep independent leaderboard snapshots and receipt times for each server.
public class LeaderboardCache {
 private final Map<String,CWire.Board> boards=new HashMap<>();private final Map<String,Long> times=new HashMap<>();
 // Ignore older sequences while allowing duplicate packets to refresh liveness.
 public synchronized void accept(CWire.Board b,long now){var old=boards.get(b.server());if(old==null||b.sequence()>=old.sequence()){boards.put(b.server(),b);times.put(b.server(),now);}}
 public synchronized CWire.Board get(String id){return boards.get(id);}
 public synchronized long lastSeen(String id){return times.getOrDefault(id,0L);}
 public synchronized Set<String> servers(){return Set.copyOf(boards.keySet());}
}
