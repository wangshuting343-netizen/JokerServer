import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.sql.SQLException;

public class BattleJoker extends Application {
    public static Client client;

    public static void exit(){
        client.disconnect();
        ///close all other connections if you have.
        ///stop all threads if you have.
        ///close all open files if you have.

    }

    @Override
    public void start(Stage primaryStage) {
        try {
            GetNameDialog dialog = new GetNameDialog();
            GameWindow win = new GameWindow(primaryStage);

            String username = dialog.getPlayername();

            client.send(GameEngine.NAME, username);

            win.setName(dialog.getPlayername());

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void stop() {
        try {
            client.disconnect();
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) {
        System.setErr(new FilteredStream(System.err));  // All JavaFX'es version warnings will not be displayed

        try {
            client = new Client(System.getProperty("bj.host","127.0.0.1"), Integer.getInteger("bj.tcp",12345));
            client.connect();
            launch();
        }catch (IOException ex){
            System.out.println("Unable to connect the server. Try it again later~");
        }
    }

}

class FilteredStream extends PrintStream {

    public FilteredStream(OutputStream out) {
        super(out);
    }

    @Override
    public void println(String x) {
        if (x != null && !x.contains("SLF4J: "))
            super.println(x);
    }

    @Override
    public void print(String x) {
        if (x!= null && !x.contains("WARNING: Loading FXML document with JavaFX API of version 18"))
            super.print(x);
    }
}
