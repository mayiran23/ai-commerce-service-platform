import java.util.*;
public class T {
  @SuppressWarnings("unchecked")
  public static void main(String[] a){
    List<Map<String,Long>> rows = new ArrayList<>();
    Map<String,Object> m = new HashMap<>();
    m.put("status","RECEIVED");
    m.put("n",325L);
    rows.add((Map<String,Long>)(Map<?,?>)m);
    for(Map<String,Long> row: rows){
      try {
        String s = String.valueOf(row.get("status"));
        System.out.println("第36行 String.valueOf(...) = " + s + "   <- 没炸");
      } catch (Exception e) { System.out.println("第36行 炸了: " + e.getClass().getSimpleName()); }
      try {
        Long bad = row.get("status");
        System.out.println("若写成 Long bad = row.get(\"status\") -> " + bad);
      } catch (Exception e) { System.out.println("若写成 Long bad = row.get(\"status\") -> 炸了: " + e.getClass().getSimpleName()); }
      Long n = row.get("n");
      System.out.println("第37行 Long n = row.get(\"n\") = " + n + " (" + n.getClass().getSimpleName() + ")");
    }
  }
}
