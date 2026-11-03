package dev.sablelife.module;
import java.util.*;
/** Presentation grouping preserves IDs and the original executable module order. */
public final class ModuleFolders {
 public record Row(String group,VirtualModule module,int count){public boolean header(){return module==null;}}
 public static boolean valid(String name){return name!=null&&name.codePointCount(0,name.length())<=32&&name.codePoints().noneMatch(Character::isISOControl);}
 public static List<Row> rows(List<VirtualModule> matching,Set<String> collapsed,boolean searching){
  var groups=new LinkedHashMap<String,List<VirtualModule>>();for(var m:matching)groups.computeIfAbsent(m.group(),k->new ArrayList<>()).add(m);
  var result=new ArrayList<Row>();for(var entry:groups.entrySet()){
   if(!entry.getKey().isEmpty())result.add(new Row(entry.getKey(),null,entry.getValue().size()));
   if(searching||entry.getKey().isEmpty()||!collapsed.contains(entry.getKey()))for(var m:entry.getValue())result.add(new Row(entry.getKey(),m,0));
  }return List.copyOf(result);
 }
 private ModuleFolders(){}
}
