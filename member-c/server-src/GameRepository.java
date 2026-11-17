import java.sql.*;
import java.util.*;
// Store winner records on the server without discarding legacy scores.
public class GameRepository implements AutoCloseable {
 private final Connection db;
 // Initialize the SQLite schema and import legacy records with stable IDs.
 public GameRepository(String path) throws SQLException {
  db=DriverManager.getConnection("jdbc:sqlite:"+path);
  try(var s=db.createStatement()) {
   s.execute("PRAGMA busy_timeout=3000");
   s.execute("CREATE TABLE IF NOT EXISTS winners(game_id TEXT PRIMARY KEY,name TEXT NOT NULL,score INTEGER NOT NULL,level INTEGER NOT NULL,ended_at TEXT NOT NULL)");
   // Preserve original table. Stable IDs make importing the same database repeatable.
   try(var tables=db.getMetaData().getTables(null,null,"scores",null)) {
    if(tables.next()) s.executeUpdate("INSERT OR IGNORE INTO winners SELECT 'legacy:'||rowid,name,score,level,time FROM scores");
   }
  }
 }
 // Save one winner transactionally and distinguish duplicates from conflicts.
 public synchronized String save(ScoreEntry e) throws SQLException {
  db.setAutoCommit(false);
  try {
   try(var q=db.prepareStatement("SELECT name,score,level,ended_at FROM winners WHERE game_id=?")) {
    q.setString(1,e.gameId());
    try(var rs=q.executeQuery()) {if(rs.next()) {boolean same=Objects.equals(rs.getString(1),e.name())&&rs.getInt(2)==e.score()&&rs.getInt(3)==e.level()&&Objects.equals(rs.getString(4),e.time()); db.commit(); return same?"ALREADY_SAVED":"CONFLICT";}}
   }
   try(var q=db.prepareStatement("INSERT INTO winners VALUES(?,?,?,?,?)")) {
    q.setString(1,e.gameId());q.setString(2,e.name());q.setInt(3,e.score());q.setInt(4,e.level());q.setString(5,e.time());q.executeUpdate();
   }
   db.commit();return "SAVED";
  } catch(SQLException ex) {db.rollback();throw ex;} finally {db.setAutoCommit(true);}
 }
 // Read the ten highest scores using deterministic tie ordering.
 public synchronized List<ScoreEntry> top10() throws SQLException {
  var list=new ArrayList<ScoreEntry>();
  try(var s=db.createStatement();var r=s.executeQuery("SELECT * FROM winners ORDER BY score DESC,ended_at ASC,game_id ASC LIMIT 10")) {while(r.next()) list.add(new ScoreEntry(r.getString(1),r.getString(2),r.getInt(3),r.getInt(4),r.getString(5)));}
  return List.copyOf(list);
 }
 // Release the database connection after pending work has stopped.
 public synchronized void close() throws SQLException {db.close();}
}
