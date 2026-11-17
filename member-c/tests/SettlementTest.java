import java.nio.file.*;
import java.util.concurrent.*;
public class SettlementTest {
 public static void main(String[] args)throws Exception {
  Path parent=Files.createTempDirectory("bj-retry").resolve("unavailable");
  var failed=new CountDownLatch(1);var saved=new CountDownLatch(1);
  try(var service=new SettlementService(parent.resolve("scores.db").toString(),new MulticastPublisher())) {
   service.save(new ScoreEntry("retry","中文",31,7,"2026-01-01T00:00:00Z"),s->{if(s.startsWith("FAILED"))failed.countDown();if(s.equals("SAVED"))saved.countDown();});
   if(!failed.await(5,TimeUnit.SECONDS))throw new AssertionError("must report real failure");
   if(!service.top().entries().isEmpty())throw new AssertionError("failed entry leaked");
   Files.createDirectory(parent);
   if(!saved.await(8,TimeUnit.SECONDS))throw new AssertionError("must recover");
   if(service.top().entries().size()!=1)throw new AssertionError("saved leaderboard");
  }
  System.out.println("PASS settlement: failure, no false scores, recovery retry");
 }
}
