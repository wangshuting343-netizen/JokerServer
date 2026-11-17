public class EngineTest {
 public static void main(String[] args){
  ServerEngine e=new ServerEngine();
  e.board=new int[]{1,1,2,3,4,5,6,7,8,9,10,11,12,13,1,2};
  if(e.checkGameOver())throw new AssertionError("horizontal merge must remain playable");
  e.board=new int[]{13,13,0,0,0,0,0,0,0,0,0,0,0,0,0,0}; e.level=13;
  e.processCommand(new Player(),"LEFT");
  if(!e.isGameOver || java.util.Arrays.stream(e.board).filter(v->v>0).count()!=1)throw new AssertionError("Joker stops before spawn");
  System.out.println("PASS engine terminal rules");
 }
}
