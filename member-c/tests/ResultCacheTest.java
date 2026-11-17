import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
public class ResultCacheTest {
 public static void main(String[] args){
  AtomicLong now=new AtomicLong(100);
  var cache=new ResultCache(now::get);
  var row=new ResultCache.Result("g","PENDING","winner",List.of("one","two"),List.of(3,1));
  cache.put(row,Set.of("session-a","session-b"));
  if(cache.get("g","outsider")!=null)throw new AssertionError("privacy");
  now.addAndGet(1_799_999);
  if(cache.get("g","session-a")==null)throw new AssertionError("30 minute retention");
  cache.put(new ResultCache.Result("g","SAVED","winner",row.names(),row.scores()),Set.of("session-a","session-b"));
  now.addAndGet(2);
  if(!cache.get("g","session-b").state().equals("SAVED"))throw new AssertionError("updated retention");
  now.addAndGet(1_800_001);
  if(cache.get("g","session-a")!=null)throw new AssertionError("expiry");
  System.out.println("PASS result cache: participant access, 30 minutes, state refresh, expiry");
 }
}
