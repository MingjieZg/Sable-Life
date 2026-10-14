package dev.sablelife.module;
import net.minecraft.nbt.*;import java.util.*;
public final class HealthPhaseRules {
 public static final int LIMIT=64;public static final List<String> FIELDS=List.of("phaseUpper","phaseLower","phaseChannel","phasePower","phaseMode","phasePulse");
 public static final class Row {
  private final UUID id;private boolean enabled=true;private int revision;private final CompoundTag values=new CompoundTag();
  Row(UUID id){this.id=id;values.putDouble("phaseUpper",100);values.putDouble("phaseLower",70);values.putDouble("phaseChannel",0);values.putDouble("phasePower",15);values.putDouble("phaseMode",0);values.putDouble("phasePulse",20);}
  public UUID id(){return id;}public boolean enabled(){return enabled;}public int revision(){return revision;}public double value(String key){return values.getDouble(key);}
  public boolean edit(String key,double value){if(!Double.isFinite(value))return false;if(key.equals("enabled")){if(value!=0&&value!=1)return false;enabled=value==1;revision++;return true;}if(!FIELDS.contains(key))return false;double min=key.equals("phasePower")||key.equals("phasePulse")?1:0,max=switch(key){case "phaseChannel"->65535;case "phasePower"->15;case "phaseMode"->1;case "phasePulse"->12000;default->100;};if(value<min||value>max)return false;if(!key.equals("phaseUpper")&&!key.equals("phaseLower")&&value!=Math.rint(value))return false;if(value!=value(key)){values.putDouble(key,value);revision++;}return true;}
  public CompoundTag save(){var t=new CompoundTag();t.putUUID("Id",id);t.putBoolean("Enabled",enabled);t.put("Values",values.copy());return t;}
 }
 private final List<Row> rows=new ArrayList<>();public List<Row> rows(){return List.copyOf(rows);}public Row find(UUID id){return rows.stream().filter(r->r.id.equals(id)).findFirst().orElse(null);}public Row add(){if(rows.size()>=LIMIT)return null;var r=new Row(UUID.randomUUID());rows.add(r);return r;}public boolean remove(UUID id){return rows.removeIf(r->r.id.equals(id));}public boolean move(UUID id,int delta){var r=find(id);int i=rows.indexOf(r),n=i+delta;if(i<0||n<0||n>=rows.size())return false;Collections.swap(rows,i,n);return true;}
 public boolean channelDeleted(int index){boolean changed=false;for(var r:rows){int c=(int)r.value("phaseChannel");if(c>index){r.edit("phaseChannel",c==index+1?0:c-1);changed=true;}}return changed;}public void unbind(){rows.forEach(r->r.edit("phaseChannel",0));}
 public ListTag save(){var l=new ListTag();rows.forEach(r->l.add(r.save()));return l;}public void load(ListTag list){rows.clear();var ids=new HashSet<UUID>();for(var t:list){if(rows.size()>=LIMIT)break;var n=(CompoundTag)t;if(!n.hasUUID("Id")||!ids.add(n.getUUID("Id")))continue;var r=new Row(n.getUUID("Id"));boolean valid=true;for(var k:FIELDS)if(n.getCompound("Values").contains(k)&&!r.edit(k,n.getCompound("Values").getDouble(k)))valid=false;if(valid){r.enabled=n.getBoolean("Enabled");rows.add(r);}}}
}
