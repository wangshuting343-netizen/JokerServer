import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
// Encode C extensions without changing the existing integer message protocol.
public class CWire {
 public static final int TOP=21, RESULT=22, STATUS=23, ERROR=24;
 public static String readString(DataInputStream in) throws IOException {int n=in.readInt();if(n<0||n>4096)throw new IOException("String length");byte[] b=new byte[n];in.readFully(b);return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString();}
 public static void str(DataOutputStream out,String s)throws IOException {byte[] b=s.getBytes(StandardCharsets.UTF_8);if(b.length>4096)throw new IOException("String length");out.writeInt(b.length);out.write(b);}
 public record Board(String server,long sequence,List<ScoreEntry> entries) {}
 public static void writeBoard(DataOutputStream o,Board b)throws IOException {o.writeInt(TOP);str(o,b.server());o.writeLong(b.sequence());o.writeInt(b.entries().size());for(var e:b.entries()){str(o,e.gameId());str(o,e.name());o.writeInt(e.score());o.writeInt(e.level());str(o,e.time());}}
 public static Board readBoard(DataInputStream i)throws IOException {String s=readString(i);long seq=i.readLong();int n=i.readInt();if(n<0||n>10)throw new IOException("Entry count");var rows=new ArrayList<ScoreEntry>();for(int j=0;j<n;j++)rows.add(new ScoreEntry(readString(i),readString(i),i.readInt(),i.readInt(),readString(i)));return new Board(s,seq,List.copyOf(rows));}
}
