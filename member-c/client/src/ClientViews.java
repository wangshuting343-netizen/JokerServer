import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import java.util.*;
// Display server results and historical scores on the JavaFX application thread.
public class ClientViews {
 private static final Map<String,TextArea> results=new HashMap<>();
 private static Stage historyStage;
 private static TextArea historyArea;
 // Update the existing result window when persistence changes for the same game.
 public static void result(String id,String text){Platform.runLater(()->{TextArea area=results.get(id);if(area==null){area=new TextArea();results.put(id,area);Stage s=new Stage();s.setTitle("Round results "+id);s.setScene(new Scene(area,520,320));s.setOnHidden(e->results.remove(id));s.show();}area.setEditable(false);area.setText(text);});}
 // Display historical winners separately from the current round results.
 public static void history(CWire.Board b){Platform.runLater(()->{
  if(historyStage==null){historyStage=new Stage();historyStage.setTitle("Server historical top ten");historyArea=new TextArea();historyArea.setEditable(false);historyStage.setScene(new Scene(historyArea,650,350));}
  historyArea.setText(format(b));historyStage.show();
 });}
 public static String format(CWire.Board b){StringBuilder s=new StringBuilder();int rank=0;for(var e:b.entries())s.append(++rank).append(" | ").append(e.name()).append(" | ").append(e.score()).append(" | level ").append(e.level()).append(" | ").append(e.time()).append("\n");return s.toString();}
 public static void error(String e){Platform.runLater(()->new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,e).show());}
}
