import java.io.*;
import java.net.*;
import java.util.*;
public class MulticastTest {
 public static void main(String[] args)throws Exception {
  var nic=Collections.list(NetworkInterface.getNetworkInterfaces()).stream().filter(n->{try{return n.isUp()&&n.supportsMulticast()&&Collections.list(n.getInetAddresses()).stream().anyMatch(a->a instanceof Inet4Address);}catch(Exception e){return false;}}).findFirst().orElseThrow();
  System.setProperty("bj.nic",nic.getName());System.setProperty("bj.udp","15559");
  try(MulticastSocket receiver=new MulticastSocket(null)) {
   receiver.setReuseAddress(true);receiver.bind(new InetSocketAddress(15559));receiver.setSoTimeout(5000);receiver.joinGroup(new InetSocketAddress("239.25.30.15",15559),nic);
   try(var publisher=new MulticastPublisher()) {
    publisher.update(new CWire.Board("server",7,List.of(new ScoreEntry("g","中文",17,3,"time"))));
    byte[] bytes=new byte[8192];DatagramPacket p=new DatagramPacket(bytes,bytes.length);receiver.receive(p);
    var in=new DataInputStream(new ByteArrayInputStream(bytes,0,p.getLength()));
    if(in.readInt()!=21)throw new AssertionError("message type");var b=CWire.readBoard(in);
    if(b.sequence()!=7||!b.entries().getFirst().name().equals("中文")||in.available()!=0)throw new AssertionError("datagram");
    receiver.receive(p); // scheduled refresh, not just immediate publication
   }
  }
  System.out.println("PASS real multicast: immediate and periodic, Unicode, binary roundtrip, resource close on "+nic.getName());
 }
}
