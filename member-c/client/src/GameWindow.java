import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.scene.control.Button;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class GameWindow {
    @FXML
    MenuBar menuBar;

    @FXML
    Label nameLabel;

    @FXML
    Label scoreLabel;

    @FXML
    Label levelLabel;

    @FXML
    Label comboLabel;

    @FXML
    Label moveCountLabel;

    @FXML
    Pane boardPane;

    @FXML
    Canvas canvas;

    @FXML
    Button startButton;

    Stage stage;
    AnimationTimer animationTimer;

    final String imagePath = "images/";
    final String[] symbols = {"bg", "A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "Joker"};
    final Image[] images = new Image[symbols.length];
    final ServerDisplayState gameEngine = ServerDisplayState.getInstance();

    public GameWindow(Stage stage) throws IOException {
        loadImages();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("mainUI.fxml"));
        loader.setController(this);
        Parent root = loader.load();
        Scene scene = new Scene(root);

        this.stage = stage;

        stage.setScene(scene);
        stage.setTitle("Battle Joker");
        stage.setMinWidth(scene.getWidth());
        stage.setMinHeight(scene.getHeight());

        stage.widthProperty().addListener(w -> onWidthChangedWindow(((ReadOnlyDoubleProperty) w).getValue()));
        stage.heightProperty().addListener(h -> onHeightChangedWindow(((ReadOnlyDoubleProperty) h).getValue()));
        stage.setOnCloseRequest(event -> quit());

        javafx.scene.control.Menu extra=new javafx.scene.control.Menu("Server scores");
        javafx.scene.control.MenuItem history=new javafx.scene.control.MenuItem("Historical top ten");
        history.setOnAction(e->{try{new ScoreboardWindow();}catch(IOException ex){ClientViews.error(ex.getMessage());}});
        extra.getItems().add(history);menuBar.getMenus().add(extra);
        stage.show();
        initCanvas();
        gameStart();
    }

    private void gameStart() {
        animationTimer.start();
    }

    private void loadImages() throws IOException {
        for (int i = 0; i < symbols.length; i++)
            images[i] = new Image(Files.newInputStream(Paths.get(imagePath + symbols[i] + ".png")));
    }

    private void initCanvas() {
        canvas.setOnKeyPressed(event -> {
//            gameEngine.moveMerge(event.getCode().toString());
//            scoreLabel.setText("Score: " + gameEngine.getScore());
//            levelLabel.setText("Level: " + gameEngine.getLevel());
//            comboLabel.setText("Combo: " + gameEngine.getCombo());
//            moveCountLabel.setText("# of Moves: " + gameEngine.getMoveCount());

            String keyPressed = event.getCode().toString();
            try {
                BattleJoker.client.send(ServerDisplayState.COMMAND, keyPressed);
            }catch (IOException ex){
                System.out.println("Disconnected!");
                ClientViews.error("Cannot send move: disconnected from server.");
                ///You should show a popup window about the problem instead.
            }
        });

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                render();
                if (gameEngine.isGameOver()) {
                    System.out.println("Game Over!");
                    animationTimer.stop();

                    Platform.runLater(() -> {
                        try {
                            BattleJoker.client.send(8, "");
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });

                }
            }
        };
        canvas.requestFocus();
    }

    private void render() {

        double w = canvas.getWidth();
        double h = canvas.getHeight();

        double sceneSize = Math.min(w, h);
        double blockSize = sceneSize / ServerDisplayState.SIZE;
        double padding = blockSize * .05;
        double startX = (w - sceneSize) / 2;
        double startY = (h - sceneSize) / 2;
        double cardSize = blockSize - (padding * 2);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        double y = startY;
        int v;

        // Draw the background and cards from left to right, and top to bottom.
        for (int i = 0; i < ServerDisplayState.SIZE; i++) {
            double x = startX;
            for (int j = 0; j < ServerDisplayState.SIZE; j++) {
                gc.drawImage(images[0], x, y, blockSize, blockSize);  // Draw the background

                v = gameEngine.getValue(i, j);

                if (v > 0)  // if a card is in the place, draw it
                    gc.drawImage(images[v], x + padding, y + padding, cardSize, cardSize);

                x += blockSize;
            }
            y += blockSize;
        }
    }

    void onWidthChangedWindow(double w) {
        double width = w - boardPane.getBoundsInParent().getMinX();
        boardPane.setMinWidth(width);
        canvas.setWidth(width);
        render();
    }

    void onHeightChangedWindow(double h) {
        double height = h - boardPane.getBoundsInParent().getMinY() - menuBar.getHeight();
        boardPane.setMinHeight(height);
        canvas.setHeight(height);
        render();
    }

    void quit() {
        System.out.println("Bye bye");
        stage.close();
        System.exit(0);
    }

    public void setName(String name) {
        nameLabel.setText(name);
        gameEngine.setPlayerName(name);
        startButton.setVisible(true);
        startButton.setManaged(true);
    }
    @FXML
    void onStartGame(){
        try{
            BattleJoker.client.send(ServerDisplayState.START_GAME, "");
        } catch (IOException ex){
            System.out.println("Fail to send START_GAME");
        }
        canvas.requestFocus();
        System.out.println(">>> 点击按钮后，焦点是否在 Canvas 上: " + canvas.isFocused());
    }

}
