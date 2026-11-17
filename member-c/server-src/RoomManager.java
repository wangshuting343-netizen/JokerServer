import java.util.ArrayList;
import java.util.List;

public class RoomManager {
    private final List<GameRoom> rooms = new ArrayList<>();
    private int nextRoomId = 1;
    public synchronized GameRoom assign(Player p){
        for(GameRoom r: rooms){
            if(!r.started && r.players.size()<4){
                r.players.add(p);
                if(r.players.size()==4){
                    r.started=true;
                }
                p.room = r;
                return r;
            }
        }
        GameRoom r = new GameRoom(nextRoomId);
        nextRoomId++;
        r.host = p;
        r.players.add(p);
        p.room = r;
        rooms.add(r);
        return r;
    }
    public synchronized void removePlayer(Player p){
        if(p.room==null)
            return;
        GameRoom r = p.room;
        synchronized(r.lock){
        if(r.started){p.connected=false;
            if(r.players.stream().noneMatch(x->x.connected)){rooms.remove(r);r.engine.isGameOver=true;}
            else if(r.players.get(r.currentPlayer)==p){do{r.currentPlayer=(r.currentPlayer+1)%r.players.size();}while(!r.players.get(r.currentPlayer).connected);r.moveLeft=4;r.lastBoard=null;}
            return;
        }
        r.players.remove(p);
        if(r.players.size()==0){
            rooms.remove(r);
        } else if (r.host==p) {
            r.host = r.players.get(0);
        }
        p.room = null;
        }
    }
}
