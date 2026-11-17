import java.net.*;
import java.io.*;
import java.util.concurrent.*;
// Broadcast the committed leaderboard through configurable IPv4 multicast.
public class MulticastPublisher implements AutoCloseable {
 private final MulticastSocket socket; private final InetAddress group; private final int port;
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
 private volatile CWire.Board board;
 // Configure the multicast socket and schedule periodic publication.
 public MulticastPublisher() throws IOException {
  group=InetAddress.getByName(System.getProperty("bj.group","239.25.30.15"));port=Integer.getInteger("bj.udp",15557);
  if(!group.isMulticastAddress())throw new IOException("Not multicast");socket=new MulticastSocket();socket.setTimeToLive(1);
  String nic=System.getProperty("bj.nic");if(nic!=null)socket.setNetworkInterface(NetworkInterface.getByName(nic));
  timer.scheduleAtFixedRate(this::send,0,2,TimeUnit.SECONDS);
 }
 // Publish a refreshed leaderboard immediately as well as periodically.
 public void update(CWire.Board b){board=b;timer.execute(this::send);}
 // Encode one bounded binary leaderboard per UDP datagram.
 private void send(){var b=board;if(b==null)return;try {var bytes=new ByteArrayOutputStream();CWire.writeBoard(new DataOutputStream(bytes),b);byte[] data=bytes.toByteArray();if(data.length>8192)throw new IOException("Oversize leaderboard");socket.send(new DatagramPacket(data,data.length,group,port));}catch(IOException e){System.err.println("Multicast: "+e);}}
 // Stop the publisher thread and close its socket.
 public void close(){timer.shutdownNow();socket.close();}
}
