// Hold server snapshots without invoking the original local game logic.
public class ServerDisplayState {
 public static final int NAME=0,COMMAND=1,PUZZLE=3,PLAYER_INFO=4,START_GAME=5,SIZE=4;
 private static final ServerDisplayState INSTANCE=new ServerDisplayState();
 volatile int[] board=new int[16];volatile boolean gameOver;
 public static ServerDisplayState getInstance(){return INSTANCE;}
 public int getValue(int r,int c){return board[r*4+c];}
 public boolean isGameOver(){return gameOver;}
 public void setPlayerName(String n){}
}
