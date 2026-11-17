import java.nio.file.*;
public class OriginalBugTest {
 public static void main(String[] args)throws Exception {
  Files.createDirectories(Path.of("data"));
  Database.connect();
  try(var s=Database.conn.createStatement()){s.execute("CREATE TABLE IF NOT EXISTS scores(name TEXT,score INTEGER,level INTEGER,time TEXT)");}
  try{Database.putScore("O'Brien",10,2);throw new AssertionError("Original unexpectedly accepts quote");}
  catch(java.sql.SQLException expected){System.out.println("REPRODUCED original Database.putScore quote failure: "+expected.getMessage());}
  Database.disconnect();
 }
}
