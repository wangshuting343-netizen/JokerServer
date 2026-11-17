import javafx.application.*;
import javafx.stage.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.net.*;
import java.io.*;
import java.util.*;
// Exercise real multicast reception and the visible stale-data warning on the JavaFX thread.
public class ScoreboardUiTest extends Application {
 private ScoreboardApp app;private Stage stage;
 private void after(double seconds,Runnable action){PauseTransition p=new PauseTransition(Duration.seconds(seconds));p.setOnFinished(e->{try{action.run();}catch(Throwable ex){ex.printStackTrace();app.stop();System.exit(1);}});p.play();}
 private String status(){return ((Label)((VBox)stage.getScene().getRoot()).getChildren().get(1)).getText();}
 private String text(){return ((TextArea)((VBox)stage.getScene().getRoot()).getChildren().get(2)).getText();}
 public void start(Stage stage)throws Exception{
  this.stage=stage;System.setProperty("bj.udp","15561");
  NetworkInterface net=Collections.list(NetworkInterface.getNetworkInterfaces()).stream().filter(n->{try{return n.isUp()&&n.supportsMulticast()&&Collections.list(n.getInetAddresses()).stream().anyMatch(a->a instanceof Inet4Address);}catch(Exception ex){return false;}}).findFirst().orElseThrow();
  System.setProperty("bj.nic",net.getName());app=new ScoreboardApp();app.start(stage);
  try(MulticastSocket sender=new MulticastSocket()){
   sender.setNetworkInterface(net);var bytes=new ByteArrayOutputStream();CWire.writeBoard(new DataOutputStream(bytes),new CWire.Board("ui-test",9,List.of(new ScoreEntry("g","test winner",73,4,"time"))));
   byte[] b=bytes.toByteArray();sender.send(new DatagramPacket(b,b.length,InetAddress.getByName("239.25.30.15"),15561));
  }
  after(2,()->{if(!status().startsWith("Live")||!text().contains("73"))throw new AssertionError("live leaderboard");
   after(6,()->{if(!status().startsWith("Possibly disconnected")||!text().contains("73"))throw new AssertionError("timeout must retain scores");app.stop();stage.hide();System.out.println("PASS JavaFX multicast display, six-second timeout, retained scores, shutdown");Platform.exit();});
  });
 }
 public static void main(String[] args){launch(args);}
}
