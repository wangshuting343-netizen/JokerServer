import javafx.application.*;
import javafx.stage.*;
import javafx.scene.control.*;
import javafx.animation.*;
import javafx.util.Duration;
import java.util.*;
// Verify that repeated persistence updates reuse a result window and history refresh reuses its window.
public class ViewsTest extends Application {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static long count(String title){return Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getTitle().startsWith(title)).count();}
 private static String content(String title){return Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getTitle().startsWith(title)).map(w->((TextArea)w.getScene().getRoot()).getText()).findFirst().orElseThrow();}
 private void later(Runnable action){Platform.runLater(()->{try{action.run();}catch(Throwable e){e.printStackTrace();System.exit(1);}});}
 public void start(Stage unused){
  Platform.setImplicitExit(false);
  ClientViews.result("test-game","PENDING\none: 3\ntwo: 1");
  later(()->{
   check(count("Round results")==1,"initial result");
   ClientViews.result("test-game","FAILED\none: 3\ntwo: 1");
   later(()->{
    check(count("Round results")==1&&content("Round results").contains("FAILED"),"failure updates same window");
    ClientViews.result("test-game","SAVED\none: 3\ntwo: 1");
    var b=new CWire.Board("s",1,List.of(new ScoreEntry("g","winner",3,2,"time")));
    ClientViews.history(b);ClientViews.history(b);
    later(()->{
     check(count("Round results")==1&&content("Round results").contains("SAVED"),"saved update");
     check(count("Server historical")==1,"history refresh must not open duplicate windows");
     check(!content("Server historical").contains("two:"),"round and history separated");
     ClientViews.error("Disconnected from server");
     later(()->{check(Window.getWindows().stream().anyMatch(w->w.getScene().getRoot() instanceof DialogPane),"disconnect alert");
      for(Window w:List.copyOf(Window.getWindows()))w.hide();
      System.out.println("PASS JavaFX result state updates, single history window, separation, disconnect alert");Platform.exit();});
    });
   });
  });
 }
 public static void main(String[] args){launch(args);}
}
