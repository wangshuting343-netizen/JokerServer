import java.util.*;
public class CacheTest {
 public static void main(String[] args){
  var c=new LeaderboardCache();
  c.accept(new CWire.Board("a",4,List.of()),100);
  c.accept(new CWire.Board("a",2,List.of()),200);
  if(c.get("a").sequence()!=4||c.lastSeen("a")!=100)throw new AssertionError("old datagram must not refresh");
  c.accept(new CWire.Board("a",4,List.of()),300);
  if(c.lastSeen("a")!=300)throw new AssertionError("equal refresh");
  c.accept(new CWire.Board("b",0,List.of()),400);
  if(c.get("b").sequence()!=0)throw new AssertionError("server restart sequence");
  System.out.println("PASS cache ordering, duplicate liveness, separate servers");
 }
}
