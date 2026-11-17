import java.nio.file.*;
public class RepositoryTest {
 public static void main(String[] args) throws Exception {
  Path p=Files.createTempDirectory("bj-test").resolve("scores.db");
  try(GameRepository r=new GameRepository(p.toString())) {
   var a=new ScoreEntry("g1","O'Brien 中文",50,8,"2026-01-01T00:00:00Z");
   if(!r.save(a).equals("SAVED") || !r.save(a).equals("ALREADY_SAVED")) throw new AssertionError("idempotency");
   if(!r.save(new ScoreEntry("g1","other",99,8,a.time())).equals("CONFLICT")) throw new AssertionError("conflict");
   for(int i=0;i<15;i++) r.save(new ScoreEntry("x"+i,"n",i,1,"2026-01-02T00:00:00Z"));
   if(r.top10().size()!=10 || !r.top10().getFirst().name().equals(a.name())) throw new AssertionError("top10");
  }
  try(GameRepository r=new GameRepository(p.toString())) {if(r.top10().getFirst().score()!=50) throw new AssertionError("restart");}
  System.out.println("PASS repository: quote/unicode, duplicate, conflict, top10, restart");
 }
}
