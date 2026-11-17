import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.*;
public class MigrationTest {
 public static void main(String[] args)throws Exception {
  Path p=Files.createTempDirectory("bj-migrate").resolve("old.db");
  try(var c=DriverManager.getConnection("jdbc:sqlite:"+p);var s=c.createStatement()){
   s.execute("CREATE TABLE scores(name TEXT,score INTEGER,level INTEGER,time TEXT)");
   s.execute("INSERT INTO scores VALUES('old',42,9,'2025-01-01 00:00:00')");
  }
  for(int i=0;i<2;i++)try(var r=new GameRepository(p.toString())){if(r.top10().size()!=1||r.top10().getFirst().score()!=42)throw new AssertionError("migration idempotence");}
  try(var r=new GameRepository(p.toString())){
   var pool=Executors.newFixedThreadPool(2);
   try{var a=pool.submit(()->r.save(new ScoreEntry("room-a","a",53,5,"2026-01-01")));var b=pool.submit(()->r.save(new ScoreEntry("room-b","b",52,5,"2026-01-01")));a.get();b.get();if(r.top10().size()!=3)throw new AssertionError("concurrent rooms lost");}finally{pool.shutdownNow();}
  }
  try(var c=DriverManager.getConnection("jdbc:sqlite:"+p);var s=c.createStatement();var rs=s.executeQuery("SELECT count(*) FROM scores")){if(rs.getInt(1)!=1)throw new AssertionError("legacy source destroyed");}
  System.out.println("PASS legacy import repeatability, source preserved, concurrent rooms");
 }
}
