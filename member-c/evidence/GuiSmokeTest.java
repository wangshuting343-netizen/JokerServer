import javafx.application.*;
import javafx.stage.*;
import javafx.animation.*;
import javafx.util.Duration;
public class GuiSmokeTest extends Application {
 public void start(Stage stage){try{
  new GameWindow(stage);
  ScoreboardApp scoreboard=new ScoreboardApp();Stage board=new Stage();scoreboard.start(board);
  PauseTransition done=new PauseTransition(Duration.seconds(2));done.setOnFinished(e->{scoreboard.stop();stage.close();board.close();System.out.println("PASS JavaFX game FXML/images and independent scoreboard startup/shutdown");Platform.exit();});done.play();
 }catch(Exception ex){ex.printStackTrace();Platform.exit();System.exit(1);}}
 public static void main(String[] args){launch(args);}
}
