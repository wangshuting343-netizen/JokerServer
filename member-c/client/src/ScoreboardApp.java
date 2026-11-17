import javafx.application.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.stage.Stage;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.net.*;
import java.io.*;
import java.util.*;
// Run an independent multicast leaderboard without accessing the database.
public class ScoreboardApp extends Application {
 private MulticastSocket socket;private Timeline refresh;private volatile boolean closed;
 private final LeaderboardCache cache=new LeaderboardCache();
 private final Map<String,Long> times=new java.util.concurrent.ConcurrentHashMap<>();
 // Receive packets in the background and refresh the selected server every second.
 public void start(Stage stage)throws Exception {
  ComboBox<String> servers=new ComboBox<>();TextArea text=new TextArea();text.setEditable(false);Label status=new Label("Waiting for multicast");
  stage.setScene(new Scene(new VBox(8,servers,status,text),740,400));stage.setTitle("Independent server leaderboard");stage.show();
  int port=Integer.getInteger("bj.udp",15557);InetAddress group=InetAddress.getByName(System.getProperty("bj.group","239.25.30.15"));
  String nic=System.getProperty("bj.nic");NetworkInterface net=nic==null?Collections.list(NetworkInterface.getNetworkInterfaces()).stream().filter(n->{try{return n.isUp()&&n.supportsMulticast()&&Collections.list(n.getInetAddresses()).stream().anyMatch(a->a instanceof Inet4Address);}catch(Exception e){return false;}}).findFirst().orElseThrow():NetworkInterface.getByName(nic);
  socket=new MulticastSocket(null);socket.setReuseAddress(true);socket.bind(new InetSocketAddress(port));socket.joinGroup(new InetSocketAddress(group,port),net);
  Thread receiver=new Thread(()->{while(!closed){try{byte[] bytes=new byte[8193];DatagramPacket p=new DatagramPacket(bytes,bytes.length);socket.receive(p);if(p.getLength()>8192)continue;DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes,0,p.getLength()));if(in.readInt()!=CWire.TOP)continue;var b=CWire.readBoard(in);if(in.available()!=0)continue;cache.accept(b,System.nanoTime());}catch(IOException e){if(!closed)System.err.println(e);}}},"scoreboard-receive");receiver.setDaemon(true);receiver.start();
  refresh=new Timeline(new KeyFrame(Duration.seconds(1),e->{for(String id:cache.servers())if(!servers.getItems().contains(id))servers.getItems().add(id);if(servers.getValue()==null&&!servers.getItems().isEmpty())servers.setValue(servers.getItems().getFirst());String id=servers.getValue();if(id!=null){text.setText(ClientViews.format(cache.get(id)));status.setText(System.nanoTime()-cache.lastSeen(id)>6_000_000_000L?"Possibly disconnected — retained last scores":"Live · "+id);}}));refresh.setCycleCount(Timeline.INDEFINITE);refresh.play();
 }
 // Stop UI refresh and unblock the receiver by closing its socket.
 public void stop(){closed=true;if(refresh!=null)refresh.stop();if(socket!=null)socket.close();}
 public static void main(String[] args){launch(args);}
}
