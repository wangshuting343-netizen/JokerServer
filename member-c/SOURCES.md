# 来源比较记录
A: https://github.com/wangshuting343-netizen/JokerServer @ b7d772848f6ea89a3db04987a9adba749bd153fe
主服务器：server-src（A 最新源码的整合副本）；JokerServer 目录保留原始 checkout。
客户端：group ZIP 的 src 和 images；保留原 UI/FXML，替换客户端本地数据库路径。
section1 与 section2：逐文件比较后保留为参考，不覆盖 A 新增房间和回合；具体差异见本文件后部。
team-contract v2：参考规则和类型，未声称已实现完整 v2。实际线路见 README。


## section1 vs group BattleJoker.java
```diff
--- section1/BattleJoker.java

+++ group/BattleJoker.java

@@ -10,8 +10,10 @@

     public static Client client;

-    public static void endProgram() {
-        /// Stop the other threads if it has
+    public static void exit(){
         client.disconnect();
-        System.exit(0);
+        ///close all other connections if you have.
+        ///stop all threads if you have.
+        ///close all open files if you have.
+
     }

@@ -21,9 +23,10 @@

             GetNameDialog dialog = new GetNameDialog();
             GameWindow win = new GameWindow(primaryStage);
+
             String username = dialog.getPlayername();
-            win.setName(username);

             client.send(GameEngine.NAME, username);

+            win.setName(dialog.getPlayername());
             Database.connect();
         } catch (Exception ex) {
@@ -43,11 +46,10 @@

         System.setErr(new FilteredStream(System.err));  // All JavaFX'es version warnings will not be displayed

-
-        client = new Client("127.0.0.1", 15556);
         try {
+            client = new Client("127.0.0.1", 12345);
             client.connect();
             launch();
-        } catch (IOException ex) {
-            System.out.println("Unable to connect server! Try it later...");
+        }catch (IOException ex){
+            System.out.println("Unable to connect the server. Try it again later~");
         }
     }
```

## section1 vs group Client.java
```diff
--- section1/Client.java

+++ group/Client.java

@@ -13,18 +13,21 @@

     private DataOutputStream out;

-    public Client(String ipAddress, int tcpPort) {
-        this.ipAddress = ipAddress;
-        this.tcpPort = tcpPort;
+    public Client(String ipAddress, int tcpPort){
+        this.ipAddress=ipAddress;
+        this.tcpPort=tcpPort;
+
     }

-    public void disconnect() {
+    public void disconnect(){
         try {
+            in.close();
+            out.close();
             socket.close();
-            socket = null;
-            in = null;
-            out = null;
-        } catch (IOException ex) {
-            System.out.println("Disconnect!");
+        }catch (IOException ex){
+            System.out.println("Bye bye~");
         }
+        in = null;
+        out = null;
+        socket = null;
     }

@@ -39,6 +42,7 @@

                     receive();
                 }
-            } catch (IOException ex) {
-                System.out.println("Disconnected. Stop the receiver thread!");
+            }catch (IOException ex){
+                System.out.println("Disconnected!");
+                System.exit(-1);
             }
         });
@@ -46,48 +50,55 @@

     }

-    public void send(int dataType, String data) throws IOException {
+    public void send(int dataType, String message) throws IOException {
+        int len = message.length();
+
         out.writeInt(dataType);
-        out.writeInt(data.length());
-        out.write(data.getBytes(), 0, data.length());
+
+        out.writeInt(len);
+        out.write(message.getBytes(), 0, len);
         out.flush();
     }

-    public void receive() throws IOException {
+    public void receive() throws IOException{
         int dataType = in.readInt();
-
-        System.out.println("Receive data type: " + dataType);
-
-        switch (dataType) {
+        switch(dataType){
             case GameEngine.PUZZLE:
-                handleReceivePuzzle();
+                handleReceivePuzzleData();
                 break;
-            case GameEngine.GAMEOVER:
-                GameEngine.getInstance().gameOver = true;
+            case GameEngine.PLAYER_INFO:
+                handleReceivePlayerInfo();
                 break;
-
         }
     }
+    public void handleReceivePlayerInfo() throws IOException {
+        int len = in.readInt();
+        byte[] nameBytes = new byte[len];
+        in.readFully(nameBytes);
+        String name = new String(nameBytes);
+        int score = in.readInt();
+        System.out.println("Score of somebody!"+name+" "+score);
+    }

-    public void handleReceivePuzzle() throws IOException {
-        int rows = in.readInt();
-        int cols = in.readInt();
+    public void handleReceivePuzzleData() throws IOException{
+        int width = in.readInt();
+        int height = in.readInt();
+        int[] puzzle = new int[width * height];

-        int[] puzzle = new int[rows * cols];
-
-        for (int i=0; i<rows*cols; i++) {
-            puzzle[i] = in.readInt();
+        for(int i=0; i<width*height;i++){
+            puzzle[i]=in.readInt();
         }

+        boolean isGameOver = in.readBoolean();
+        System.out.println("Received: " + Arrays.toString(puzzle));
         GameEngine engine = GameEngine.getInstance();
-        engine.setBoard(puzzle);

-        System.out.println(Arrays.toString(puzzle));
-
+        engine.board = puzzle;
+        engine.gameOver = isGameOver;
     }

-    public static void main(String[] args) throws IOException {
-        Client client = new Client("127.0.0.1", 15556);
-        client.connect();
-        client.send(0, "Hello!!");
-    }
+//    public static void main(String[] args) throws IOException{
+//        Client client = new Client("127.0.0.1", 12345);
+//        client.connect();
+//        client.send("I am here!!!");
+//    }
 }
```

## section1 vs group Database.java
内容相同（忽略换行符）

## section1 vs group GameEngine.java
```diff
--- section1/GameEngine.java

+++ group/GameEngine.java

@@ -7,10 +7,9 @@

     public static final int NAME = 0;
     public static final int COMMAND = 1;
-    public static final int MESSAGE = 2;
-    public static final int SETTING = 3;
-    public static final int PUZZLE = 4;
-    public static final int GAMEOVER = 5;
-
-    // you can add more constants
+    public static final int SETTING = 2;
+    public static final int PUZZLE = 3;
+    //more and more for different purposes
+    public static final int PLAYER_INFO=4;
+    public static final int START_GAME = 5;

     public static final int LIMIT = 14;
@@ -20,5 +19,5 @@


     private static GameEngine instance;
-    public boolean gameOver;
+    boolean gameOver;

     private String playerName;
@@ -42,12 +41,8 @@

     }

-    public static GameEngine getInstance() {    // singleton
+    public static GameEngine getInstance() {//Singleton
         if (instance == null)
             instance = new GameEngine();
         return instance;
-    }
-
-    public void setBoard(int[] board) {
-        this.board = board;
     }

@@ -119,7 +114,7 @@

         try {
             BattleJoker.client.send(COMMAND, "Down");
-        } catch (IOException ex) {
-            System.out.println("Unable to send action to the server...");
-            BattleJoker.endProgram();
+        }catch (IOException ex){
+            System.out.println("Unable to send the command to the server");
+            BattleJoker.exit();
         }

@@ -133,12 +128,11 @@

     private void moveUp() {
         try {
-            BattleJoker.client.send(COMMAND, "Up");
-        } catch (IOException ex) {
-            System.out.println("Unable to send action to the server...");
-            BattleJoker.endProgram();
-        }
-
-        for (int i = 0; i < SIZE; i++)
-            moveMerge(-SIZE, i, SIZE * (SIZE - 1) + i);
+            BattleJoker.client.send(COMMAND,"Up");
+        }catch (IOException ex){
+            System.out.println("Unable to send the command to the server");
+            BattleJoker.exit();
+        }
+//        for (int i = 0; i < SIZE; i++)
+//            moveMerge(-SIZE, i, SIZE * (SIZE - 1) + i);
     }

@@ -148,8 +142,8 @@

     private void moveRight() {
         try {
-            BattleJoker.client.send(COMMAND, "Right");
-        } catch (IOException ex) {
-            System.out.println("Unable to send action to the server...");
-            BattleJoker.endProgram();
+            BattleJoker.client.send(COMMAND,"Right");
+        }catch (IOException ex){
+            System.out.println("Unable to send the command to the server");
+            BattleJoker.exit();
         }

@@ -163,8 +157,8 @@

     private void moveLeft() {
         try {
-            BattleJoker.client.send(COMMAND, "Left");
-        } catch (IOException ex) {
-            System.out.println("Unable to send action to the server...");
-            BattleJoker.endProgram();
+            BattleJoker.client.send(COMMAND,"Left");
+        }catch (IOException ex){
+            System.out.println("Unable to send the command to the server");
+            BattleJoker.exit();
         }

```

## section1 vs group GameWindow.java
```diff
--- section1/GameWindow.java

+++ group/GameWindow.java

@@ -13,4 +13,5 @@

 import javafx.scene.layout.Pane;
 import javafx.stage.Stage;
+import javafx.scene.control.Button;

 import java.io.IOException;
@@ -42,4 +43,7 @@

     @FXML
     Canvas canvas;
+
+    @FXML
+    Button startButton;

     Stage stage;
@@ -72,5 +76,4 @@

         stage.show();
         initCanvas();
-
         gameStart();
     }
@@ -92,10 +95,12 @@

 //            comboLabel.setText("Combo: " + gameEngine.getCombo());
 //            moveCountLabel.setText("# of Moves: " + gameEngine.getMoveCount());
-            System.out.println(event.getCode().toString());
+
+            String keyPressed = event.getCode().toString();
             try {
-                BattleJoker.client.send(GameEngine.COMMAND, event.getCode().toString());
-            } catch (IOException ex) {
-                System.out.println("Disconnected");
+                BattleJoker.client.send(GameEngine.COMMAND, keyPressed);
+            }catch (IOException ex){
+                System.out.println("Disconnected!");
                 System.exit(-1);
+                ///You should show a popup window about the problem instead.
             }
         });
@@ -181,4 +186,17 @@

         nameLabel.setText(name);
         gameEngine.setPlayerName(name);
-    }
+        startButton.setVisible(true);
+        startButton.setManaged(true);
+    }
+    @FXML
+    void onStartGame(){
+        try{
+            BattleJoker.client.send(GameEngine.START_GAME, "");
+        } catch (IOException ex){
+            System.out.println("Fail to send START_GAME");
+        }
+        canvas.requestFocus();
+        System.out.println(">>> 点击按钮后，焦点是否在 Canvas 上: " + canvas.isFocused());
+    }
+
 }
```

## section1 vs group GetNameDialog.java
内容相同（忽略换行符）

## section1 vs group ScoreboardTester.java
内容相同（忽略换行符）

## section1 vs group ScoreboardWindow.java
内容相同（忽略换行符）

## section2 vs group BattleJoker.java
```diff
--- section2/BattleJoker.java

+++ group/BattleJoker.java

@@ -10,9 +10,10 @@

     public static Client client;

-    public static void exit() {
+    public static void exit(){
         client.disconnect();
-        /// close all other connections if you have.
-        /// stop all threads if you have.
-        /// close all open files if you have.
+        ///close all other connections if you have.
+        ///stop all threads if you have.
+        ///close all open files if you have.
+
     }

@@ -27,5 +28,5 @@

             client.send(GameEngine.NAME, username);

-            win.setName(username);
+            win.setName(dialog.getPlayername());
             Database.connect();
         } catch (Exception ex) {
@@ -49,6 +50,6 @@

             client.connect();
             launch();
-        } catch (IOException ex) {
-            System.out.println("Unable to connect the server. Try it again later~~");
+        }catch (IOException ex){
+            System.out.println("Unable to connect the server. Try it again later~");
         }
     }
```

## section2 vs group Client.java
```diff
--- section2/Client.java

+++ group/Client.java

@@ -3,5 +3,4 @@

 import java.io.IOException;
 import java.net.Socket;
-import java.nio.charset.StandardCharsets;
 import java.util.Arrays;

@@ -14,15 +13,16 @@

     private DataOutputStream out;

-    public Client(String ipAddress, int tcpPort) {
-        this.ipAddress = ipAddress;
-        this.tcpPort = tcpPort;
+    public Client(String ipAddress, int tcpPort){
+        this.ipAddress=ipAddress;
+        this.tcpPort=tcpPort;
+
     }

-    public void disconnect() {
+    public void disconnect(){
         try {
             in.close();
             out.close();
             socket.close();
-        } catch (IOException ex) {
+        }catch (IOException ex){
             System.out.println("Bye bye~");
         }
@@ -42,5 +42,5 @@

                     receive();
                 }
-            } catch(IOException ex) {
+            }catch (IOException ex){
                 System.out.println("Disconnected!");
                 System.exit(-1);
@@ -60,7 +60,7 @@

     }

-    public void receive() throws IOException {
+    public void receive() throws IOException{
         int dataType = in.readInt();
-        switch (dataType) {
+        switch(dataType){
             case GameEngine.PUZZLE:
                 handleReceivePuzzleData();
@@ -71,41 +71,34 @@

         }
     }
-
     public void handleReceivePlayerInfo() throws IOException {
+        int len = in.readInt();
+        byte[] nameBytes = new byte[len];
+        in.readFully(nameBytes);
+        String name = new String(nameBytes);
         int score = in.readInt();
-        System.out.println("Score of somebody!" + score);
+        System.out.println("Score of somebody!"+name+" "+score);
     }

-    public void handleReceivePuzzleData() throws IOException {
+    public void handleReceivePuzzleData() throws IOException{
         int width = in.readInt();
         int height = in.readInt();
         int[] puzzle = new int[width * height];

-        for (int i=0; i<width*height; i++) {
-            puzzle[i] = in.readInt();
+        for(int i=0; i<width*height;i++){
+            puzzle[i]=in.readInt();
         }

         boolean isGameOver = in.readBoolean();
-
         System.out.println("Received: " + Arrays.toString(puzzle));
-
         GameEngine engine = GameEngine.getInstance();

         engine.board = puzzle;
         engine.gameOver = isGameOver;
-
     }

-//    public static void main(String[] args) throws IOException {
+//    public static void main(String[] args) throws IOException{
 //        Client client = new Client("127.0.0.1", 12345);
 //        client.connect();
 //        client.send("I am here!!!");
 //    }
-
 }
-
-
-
-
-
-
```

## section2 vs group Database.java
内容相同（忽略换行符）

## section2 vs group GameEngine.java
```diff
--- section2/GameEngine.java

+++ group/GameEngine.java

@@ -9,16 +9,15 @@

     public static final int SETTING = 2;
     public static final int PUZZLE = 3;
-    public static final int PLAYER_INFO = 4;
-    // more and more for different purposes
+    //more and more for different purposes
+    public static final int PLAYER_INFO=4;
+    public static final int START_GAME = 5;

     public static final int LIMIT = 14;
     public static final int SIZE = 4;
-
     int[] board = new int[SIZE * SIZE];
-
     Random random = new Random(0);

     private static GameEngine instance;
-    public boolean gameOver;
+    boolean gameOver;

     private String playerName;
@@ -42,5 +41,5 @@

     }

-    public static GameEngine getInstance() { // Singleton
+    public static GameEngine getInstance() {//Singleton
         if (instance == null)
             instance = new GameEngine();
@@ -115,6 +114,6 @@

         try {
             BattleJoker.client.send(COMMAND, "Down");
-        } catch (IOException ex) {
-            System.out.println("Unable to send the command to the server~!");
+        }catch (IOException ex){
+            System.out.println("Unable to send the command to the server");
             BattleJoker.exit();
         }
@@ -129,12 +128,11 @@

     private void moveUp() {
         try {
-            BattleJoker.client.send(COMMAND, "Up");
-        } catch (IOException ex) {
-            System.out.println("Unable to send the command to the server~!");
-            BattleJoker.exit();
-        }
-
-        for (int i = 0; i < SIZE; i++)
-            moveMerge(-SIZE, i, SIZE * (SIZE - 1) + i);
+            BattleJoker.client.send(COMMAND,"Up");
+        }catch (IOException ex){
+            System.out.println("Unable to send the command to the server");
+            BattleJoker.exit();
+        }
+//        for (int i = 0; i < SIZE; i++)
+//            moveMerge(-SIZE, i, SIZE * (SIZE - 1) + i);
     }

@@ -144,7 +142,7 @@

     private void moveRight() {
         try {
-            BattleJoker.client.send(COMMAND, "Right");
-        } catch (IOException ex) {
-            System.out.println("Unable to send the command to the server~!");
+            BattleJoker.client.send(COMMAND,"Right");
+        }catch (IOException ex){
+            System.out.println("Unable to send the command to the server");
             BattleJoker.exit();
         }
@@ -159,7 +157,7 @@

     private void moveLeft() {
         try {
-            BattleJoker.client.send(COMMAND, "Left");
-        } catch (IOException ex) {
-            System.out.println("Unable to send the command to the server~!");
+            BattleJoker.client.send(COMMAND,"Left");
+        }catch (IOException ex){
+            System.out.println("Unable to send the command to the server");
             BattleJoker.exit();
         }
```

## section2 vs group GameWindow.java
```diff
--- section2/GameWindow.java

+++ group/GameWindow.java

@@ -13,4 +13,5 @@

 import javafx.scene.layout.Pane;
 import javafx.stage.Stage;
+import javafx.scene.control.Button;

 import java.io.IOException;
@@ -42,4 +43,7 @@

     @FXML
     Canvas canvas;
+
+    @FXML
+    Button startButton;

     Stage stage;
@@ -72,5 +76,4 @@

         stage.show();
         initCanvas();
-
         gameStart();
     }
@@ -96,10 +99,9 @@

             try {
                 BattleJoker.client.send(GameEngine.COMMAND, keyPressed);
-            } catch (IOException ex) {
+            }catch (IOException ex){
                 System.out.println("Disconnected!");
                 System.exit(-1);
-                /// You should show a popup window about the problem instead.
+                ///You should show a popup window about the problem instead.
             }
-
         });

@@ -184,4 +186,17 @@

         nameLabel.setText(name);
         gameEngine.setPlayerName(name);
-    }
+        startButton.setVisible(true);
+        startButton.setManaged(true);
+    }
+    @FXML
+    void onStartGame(){
+        try{
+            BattleJoker.client.send(GameEngine.START_GAME, "");
+        } catch (IOException ex){
+            System.out.println("Fail to send START_GAME");
+        }
+        canvas.requestFocus();
+        System.out.println(">>> 点击按钮后，焦点是否在 Canvas 上: " + canvas.isFocused());
+    }
+
 }
```

## section2 vs group GetNameDialog.java
内容相同（忽略换行符）

## section2 vs group ScoreboardTester.java
内容相同（忽略换行符）

## section2 vs group ScoreboardWindow.java
内容相同（忽略换行符）

## A GameRoom.java vs local section2

A 新增文件。

## A JokerServer.java vs local section2

```diff
---

+++

@@ -13,26 +13,33 @@

     public static final int SETTING = 2;
     public static final int PUZZLE = 3;
+    //more and more for different purposes
     public static final int PLAYER_INFO = 4;
-    // more and more for different purposes
+    public static final int START_GAME = 5;
+    public static final int UNDO = 6;
+    public static final int UPLOAD_PUZZLE = 7;
+    public static final int GET_TOP10 = 8;
+

     public static Object lockObject = new Object();


-//    HashMap<Socket, DataOutputStream> clients = new HashMap<>();
+    //HashMap<Socket, DataOutputStream> clients = new HashMap<>();
     HashMap<Socket, Player> clients = new HashMap<>();

-    ServerEngine engine = new ServerEngine();
+
+    RoomManager roomManager = new RoomManager();

     public JokerServer(int tcpPort) throws IOException {
         ServerSocket srvSocket = new ServerSocket(tcpPort);

-        while(true) {
-            Socket socket = srvSocket.accept();
+        while(true){
+            Socket socket=srvSocket.accept();

             Player player = new Player();
-            synchronized (lockObject) {
+
+            synchronized (lockObject){
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 player.out = out;
-//                clients.put(socket, out);
+                //clients.put(socket, out);
                 clients.put(socket, player);
             }
@@ -41,9 +48,10 @@

                 try {
                     serve(player, socket);
-                } catch (IOException ex) {
-                    System.out.println("Disconnected... " + socket.getInetAddress());
-                    synchronized (lockObject) {
+                }catch (IOException ex){
+                    System.out.println("Disconnected..."+socket.getInetAddress());
+                    synchronized (lockObject){
                         clients.remove(socket);
                     }
+                    roomManager.removePlayer(player);
                 }
             });
@@ -52,19 +60,19 @@

     }

-    public void serve(Player player, Socket socket) throws IOException {
+    public void serve(Player player, Socket socket) throws IOException{
         DataInputStream in = new DataInputStream(socket.getInputStream());
+        GameRoom room = roomManager.assign(player);

         boolean success = false;
-
-        synchronized (lockObject) {
-            success = sendPuzzle(player, engine.isGameOver);
-        }
-
-        if (!success)
+        synchronized (room.lock) {
+            success = sendPuzzle(player, room.engine.isGameOver);
+        }
+
+        if(!success)
             throw new IOException("Unable to send the puzzle to the new player!");

         byte[] buffer = new byte[1024];

-        while(true) {
+        while(true){

             int dataType = in.readInt();
@@ -73,10 +81,22 @@

             in.read(buffer, 0, len);

-            switch(dataType) {
+            switch(dataType){
                 case NAME:
-                    handleName(new String(buffer, 0, len));
+                    handleName(player, new String(buffer, 0, len));
                     break;
                 case COMMAND:
-                    handleCommand(player, new String(buffer, 0, len));
+                    handleCommand(player, new String(buffer,0,len));
+                    break;
+                case START_GAME:
+                    handleStartGame(player);
+                    break;
+                case UNDO:
+                    handleUndo(player);
+                    break;
+                case UPLOAD_PUZZLE:
+                    handleUpload(player, in);
+                    break;
+                case GET_TOP10:
+                    handleTop10(player);
                     break;
                 default:
@@ -84,35 +104,31 @@

             }

-            System.out.println("Receive " + dataType + " : " + new String(buffer, 0, len));
-        }
-    }
-
-    public boolean sendPuzzle(Player player, boolean isGameOver) {
+            System.out.println("Receive " + dataType + ": " + new String(buffer, 0, len));
+        }
+    }
+
+    public boolean sendPuzzle(Player player, boolean isGameOver){
         boolean success = true;

             try {
-//                DataOutputStream out = clients.get(socket);
-//                Player player  = clients.get(socket);
+                //DataOutputStream out = clients.get(socket);
+                //Player player = clients.get(socket);
                 DataOutputStream out = player.out;
-
-                out.writeInt(PUZZLE); // the type of the data I am going to send
+                out.writeInt(PUZZLE);// the type of data I am going to send

                 System.out.println("Send puzzle data");

-                out.writeInt(engine.SIZE);     // the width of the puzzle
-                out.writeInt(engine.SIZE);     // the height of the puzzle
-                for (int i = 0; i < engine.SIZE * engine.SIZE; i++) {
-                    out.writeInt(engine.board[i]);
-                }
-
-                out.writeBoolean(isGameOver);   // send the game status too!
-
-                out.flush();        // force data send
-
-                System.out.println("Send completed" + Arrays.toString(engine.board));
-
-
-
-            } catch (IOException ex) {
+                out.writeInt(player.room.engine.SIZE);// the width of the puzzle
+                out.writeInt(player.room.engine.SIZE);// the height of the puzzle
+                for (int i = 0; i < player.room.engine.SIZE * player.room.engine.SIZE; i++) {
+                    out.writeInt(player.room.engine.board[i]);
+                }
+
+                out.writeBoolean(isGameOver);
+                out.flush();//force data send
+
+                System.out.println("Send Completed" + Arrays.toString(player.room.engine.board));
+
+            }catch (IOException ex){
                 success = false;
             }
@@ -121,46 +137,176 @@

     }

-    public void handleCommand(Player player, String command) {
+    public void handleCommand(Player player, String command){
         System.out.println("The player wants to run the command: " + command);
-
-        synchronized (lockObject) {
-            engine.processCommand(player, command);     // process the command and update the data in board
-
-            System.out.println(Arrays.toString(engine.board));
-
-            /// send the updated puzzle data to clients
-            Iterator<Socket> iterator = clients.keySet().iterator();
-
-            while (iterator.hasNext()) {
-                Socket socket = iterator.next();
-                Player anotherPlayer = clients.get(socket);
-                if (sendPuzzle(anotherPlayer, engine.isGameOver) == false)    // fail to send data through this socket
-                    clients.remove(socket);
-                else {
-                    sendPlayerInfo(player, anotherPlayer);
-                }
-            }
-        }
-    }
-
-    public void sendPlayerInfo(Player player, Player otherPlayer)  {
+        if(player.room==null)
+            return;
+        synchronized (player.room.lock) {
+            if(!player.room.started){
+                System.out.println("Room: "+player.room.roomId + " not started");
+                return;
+            }
+            Player currentPlayer = player.room.players.get(player.room.currentPlayer);
+
+            if(currentPlayer != player){
+                System.out.println("It is not your turn!");
+                return;
+            }
+            int[] boardBeforeMove = Arrays.copyOf(player.room.engine.board, player.room.engine.board.length);
+            boolean changed = player.room.engine.processCommand(player,command);
+            if(!changed){
+                System.out.println("Not changed!");
+                if(player.room.engine.checkGameOver()){
+                    System.out.println("Game Over!");
+                    settleGame(player.room);
+                }
+                return;
+            }
+            if(player.room.engine.checkGameOver()){
+                System.out.println("Game Over!");
+                settleGame(player.room);
+            }
+            player.room.lastBoard = boardBeforeMove;
+//            player.room.engine.processCommand(player, command);//process the command and update the data in board
+            currentPlayer.room.moveLeft--;
+            if(currentPlayer.room.moveLeft==0){
+                player.room.currentPlayer = (player.room.currentPlayer+1)%player.room.players.size();
+                player.room.moveLeft=4;
+                Player nextPlayer = player.room.players.get(player.room.currentPlayer);
+                System.out.println(nextPlayer.name+ " It is your turn!");
+            }
+            System.out.println(Arrays.toString(player.room.engine.board));
+
+            ///send the updated puzzle data to other players in the same room
+            for (Player p: player.room.players){
+                if(sendPuzzle(p, player.room.engine.isGameOver)) {
+                    sendPlayerInfo(player, p);
+                }
+            }
+        }
+    }
+    public void sendPlayerInfo(Player player, Player otherPlayer){
         try {
-//            Player otherPlayer = clients.get(socket);
+            //Player otherPlayer = clients.get(socket);
             otherPlayer.out.writeInt(PLAYER_INFO);
-            /// you should send the player name too!
+            /// send the player name too!
+            byte[] nameBytes = player.name ==null? new byte[0]: player.name.getBytes();
+            otherPlayer.out.writeInt(nameBytes.length);
+            otherPlayer.out.write(nameBytes);
             otherPlayer.out.writeInt(player.score);
             otherPlayer.out.flush();
-        } catch (IOException ex) {
-            System.out.println("Cannot send player info, but I don't remove the player info from the map");
-        }
-    }
-
-    public void handleName(String name) {
-        System.out.println("The player wants to set their name to " + name);
-    }
-
-    public static void main(String[] args) throws IOException {
+        } catch (IOException ex){
+            System.out.println("Cannot send player info, but i don't remove the player info from the map");
+        }
+    }
+
+    public void handleName(Player player, String name){
+        System.out.println("The player wants to set their name to: [" +name+"]");
+        synchronized (lockObject) {
+            player.name = name;
+            sendPlayerInfo(player, player);
+        }
+    }
+
+    public static void main(String[] args) throws IOException{
         new JokerServer(12345);
     }

+    public void handleStartGame(Player player){
+        if(player.room==null)
+            return;
+        synchronized (player.room.lock){
+            if(player.room.host!=player){
+                System.out.println("Only host can start the game!");
+                return;
+            }
+            if(player.room.started){
+                System.out.println("Game already started!");
+                return;
+            }
+            player.room.started=true;
+            System.out.println("Room " + player.room.roomId + " started by " + player.name);
+        }
+    }
+    public void handleUndo(Player player) {
+        if (player.room == null || !player.room.started)
+            return;
+        synchronized (player.room.lock) {
+            Player currentPlayer = player.room.players.get(player.room.currentPlayer);
+            if (!(player == currentPlayer)) {
+                System.out.println("It is not your turn!");
+                return;
+            }
+            if (player.hasUndone) {
+                System.out.println("You have already used your undo!");
+                return;
+            }
+            if (player.room.lastBoard == null) {
+                System.out.println("No action to undo!");
+                return;
+            }
+            player.room.engine.board = Arrays.copyOf(player.room.lastBoard, player.room.lastBoard.length);
+            player.hasUndone = true;
+            player.room.moveLeft++;
+            player.room.lastBoard = null;
+            System.out.println(player.name + " has used undo this turn!");
+
+            for (Player p : player.room.players) {
+                if (sendPuzzle(p, player.room.engine.isGameOver)) {
+                    sendPlayerInfo(player, p);
+                }
+            }
+
+        }
+
+    }
+    public void handleUpload(Player player, DataInputStream in){
+        if(player.room==null || !player.room.started){
+            return;
+        }
+        synchronized (player.room.lock){
+            try{
+                int [] newBoard = new int[16];
+                for(int i=0; i<16; i++){
+                    newBoard[i] = in.readInt();
+                }
+                Player currentPlayer = player.room.players.get(player.room.currentPlayer);
+                if(currentPlayer!=player){
+                    System.out.println("It is not your turn!");
+                    return;
+                }
+                player.room.engine.board = newBoard;
+                player.room.moveLeft--;
+                if(player.room.moveLeft == 0){
+                    player.room.currentPlayer = (player.room.currentPlayer+1)%player.room.players.size();
+                    player.room.moveLeft=4;
+                    System.out.println(player.room.players.get(player.room.currentPlayer).name+" it's your turn!");
+                    player.room.lastBoard = null;
+                }
+                for(Player p: player.room.players){
+                    if(sendPuzzle(p, player.room.engine.isGameOver)) {
+                        sendPlayerInfo(player, p);
+                    }
+                }
+            } catch (IOException e) {
+                System.out.println("Fail to upload the board!");
+            }
+        }
+    }
+    public void settleGame(GameRoom room){
+        if(room.isRecordSaved){
+            return;
+        }
+        room.isRecordSaved = true;
+        Player winner = room.players.get(0);
+        for(Player p: room.players){
+            if(p.score>winner.score){
+                winner = p;
+            }
+        }
+        System.out.println("Winner: "+winner.name + " Score: "+winner.score + " Level: "+ room.engine.level);
+    }
+    public void handleTop10(Player player){
+        //别忘了替换，这里需要data.base.getScores()，和排行榜！！！！！！！！！！！！！
+
+    }
 }
```

## A Player.java vs local section2

```diff
---

+++

@@ -6,3 +6,5 @@

     public int combo;
     public DataOutputStream out;
+    public GameRoom room;
+    public boolean hasUndone = false;
 }
```

## A RoomManager.java vs local section2

A 新增文件。

## A ServerEngine.java vs local section2

```diff
---

+++

@@ -1,2 +1,3 @@

+import java.util.Arrays;
 import java.util.Random;

@@ -5,19 +6,19 @@

     int[] board = new int[SIZE * SIZE];
     int level = 1;
-//    private int score;
-//    private int combo;
+    //private int score;
+    //private int combo;
     private int totalMoveCount;
     private int numOfTilesMoved;
-
     public boolean isGameOver;

     Random random = new Random(0);

-    public ServerEngine() {
+    public ServerEngine(){
         nextRound();
     }

-    public void processCommand(Player player, String dir) {
-        switch(dir) {
+    public boolean processCommand(Player player, String dir){
+        int[] oldBoard = Arrays.copyOf(board, board.length);
+        switch(dir){
             case "UP":
                 for (int i = 0; i < SIZE; i++)
@@ -36,9 +37,16 @@

                     moveMerge(player, 1, SIZE - 1 + i, i);
                 break;
+
+
         }

-        if (nextRound() == false) {
-            isGameOver = true;
+        boolean changed = !Arrays.equals(oldBoard,board);
+        if(changed){
+            if (nextRound() == false){
+                isGameOver = true;
+            }
         }
+
+        return changed;
     }

@@ -92,3 +100,35 @@

         return true;
     }
+
+    public boolean checkGameOver(){
+        for(int b: board){
+            if(b==14){
+                isGameOver = true;
+                return true;
+            }
+        }
+        if (!isFull()){
+            isGameOver = false;
+            return false;
+        } else{
+            for(int i=0; i<SIZE; i++){
+                for(int j=0; j<SIZE-1; j++){
+                    if(board[i*SIZE+j]==board[i+SIZE+j+1]){
+                        isGameOver = false;
+                        return false;
+                    }
+                }
+            }
+            for(int i=0; i<SIZE-1; i++){
+                for(int j=0; j<SIZE; j++){
+                    if(board[i*SIZE+j]==board[(i+1)*SIZE+j]){
+                        isGameOver = false;
+                        return false;
+                    }
+                }
+            }
+        }
+        isGameOver = true;
+        return true;
+    }
 }
```
