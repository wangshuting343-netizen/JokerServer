import java.util.Arrays;
import java.util.Random;

public class ServerEngine {
    final int SIZE = 4;
    int[] board = new int[SIZE * SIZE];
    int level = 1;
    //private int score;
    //private int combo;
    private int totalMoveCount;
    private int numOfTilesMoved;
    public boolean isGameOver;

    Random random = new Random();

    public ServerEngine(){
        nextRound();
    }

    public boolean processCommand(Player player, String dir){
        if(isGameOver)return false;
        player.combo=0;
        int[] oldBoard = Arrays.copyOf(board, board.length);
        switch(dir){
            case "UP":
                for (int i = 0; i < SIZE; i++)
                    moveMerge(player, -SIZE, i, SIZE * (SIZE - 1) + i);
                break;
            case "DOWN":
                for (int i = 0; i < SIZE; i++)
                    moveMerge(player, SIZE, SIZE * (SIZE - 1) + i, i);
                break;
            case "LEFT":
                for (int i = 0; i <= SIZE * (SIZE - 1); i += SIZE)
                    moveMerge(player, -1, i, SIZE - 1 + i);
                break;
            case "RIGHT":
                for (int i = 0; i <= SIZE * (SIZE - 1); i += SIZE)
                    moveMerge(player, 1, SIZE - 1 + i, i);
                break;


        }

        boolean changed = !Arrays.equals(oldBoard,board);
        if(changed){
            player.score+=(player.combo/5)*2;
            if(java.util.Arrays.stream(board).anyMatch(v->v>=14)){isGameOver=true;return true;}
            if (nextRound() == false){
                isGameOver = true;
            }
        }

        return changed;
    }

    private void moveMerge(Player player, int d, int s, int l) {
        int v, j;
        for (int i = s - d; i != l - d; i -= d) {
            j = i;
            if (board[j] <= 0) continue;
            v = board[j];
            board[j] = 0;
            while (j + d != s && board[j + d] == 0)
                j += d;

            if (board[j + d] == 0) {
                j += d;
                board[j] = v;
            } else {
                while (j != s && board[j + d] == v) {
                    j += d;
                    board[j] = 0;
                    v++;
                    player.score++;
                    player.combo++;
                }
                board[j] = v;
                if (v > level) level = v;
            }
            if (i != j)
                numOfTilesMoved++;

        }
    }

    private boolean isFull() {
        for (int v : board)
            if (v == 0) return false;
        return true;
    }

    private boolean nextRound() {
        if (isFull()) return false;
        int i;

        // randomly find an empty place
        do {
            i = random.nextInt(SIZE * SIZE);
        } while (board[i] > 0);

        // randomly generate a card based on the existing level, and assign it to the select place
        board[i] = random.nextInt(level) / 4 + 1;
        return true;
    }

    public boolean checkGameOver(){
        for(int b: board){
            if(b==14){
                isGameOver = true;
                return true;
            }
        }
        if (!isFull()){
            isGameOver = false;
            return false;
        } else{
            for(int i=0; i<SIZE; i++){
                for(int j=0; j<SIZE-1; j++){
                    if(board[i*SIZE+j]==board[i*SIZE+j+1]){
                        isGameOver = false;
                        return false;
                    }
                }
            }
            for(int i=0; i<SIZE-1; i++){
                for(int j=0; j<SIZE; j++){
                    if(board[i*SIZE+j]==board[(i+1)*SIZE+j]){
                        isGameOver = false;
                        return false;
                    }
                }
            }
        }
        isGameOver = true;
        return true;
    }
}
