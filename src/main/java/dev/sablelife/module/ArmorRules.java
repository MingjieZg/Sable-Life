package dev.sablelife.module;
import net.minecraft.nbt.*;
import java.util.*;
/** Bounded per-part rules. Channel zero is unbound, never an unconditional shield. */
public final class ArmorRules {
 public static final int LIMIT=64;
 public static final class Row {
  private final UUID id;private boolean enabled=true;private int source,when,channel=1;private double modifier;
  Row(UUID id){this.id=id;}public UUID id(){return id;}public boolean enabled(){return enabled;}public int source(){return source;}public int when(){return when;}public int channel(){return channel;}public double modifier(){return modifier;}
  public boolean edit(String key,double value){if(!Double.isFinite(value))return false;switch(key){case "enabled"->{if(value!=0&&value!=1)return false;enabled=value==1;}case "when"->{if(value!=0&&value!=1)return false;when=(int)value;}case "source"->{if(value!=0&&value!=1)return false;source=(int)value;}case "channel"->{if(value!=Math.rint(value)||value<0||value>65535)return false;channel=(int)value;}case "modifier"->{if(value< -100||value>1000)return false;modifier=value;}default->{return false;}}return true;}
 }
 private final List<Row> rows=new ArrayList<>();public List<Row> rows(){return List.copyOf(rows);}public Row find(UUID id){return rows.stream().filter(r->r.id.equals(id)).findFirst().orElse(null);}
 public Row add(){if(rows.size()>=LIMIT)return null;var r=new Row(UUID.randomUUID());rows.add(r);return r;}public boolean remove(UUID id){return rows.removeIf(r->r.id.equals(id));}
 public boolean move(UUID id,int delta){var r=find(id);int i=rows.indexOf(r),n=i+delta;if(i<0||n<0||n>=rows.size())return false;Collections.swap(rows,i,n);return true;}
 public boolean channelDeleted(int index){boolean changed=false;for(var r:rows)if(r.channel>index){r.channel=r.channel==index+1?0:r.channel-1;changed=true;}return changed;}
 public void unbind(){rows.forEach(r->r.channel=0);}
 public ListTag save(){var list=new ListTag();for(var r:rows){var t=new CompoundTag();t.putUUID("Id",r.id);t.putBoolean("Enabled",r.enabled);t.putInt("Source",r.source);t.putInt("When",r.when);t.putInt("Channel",r.channel);t.putDouble("Modifier",r.modifier);list.add(t);}return list;}
 public void load(ListTag list){rows.clear();var ids=new HashSet<UUID>();for(var item:list){if(rows.size()>=LIMIT)break;var t=(CompoundTag)item;if(!t.hasUUID("Id")||!ids.add(t.getUUID("Id")))continue;var r=new Row(t.getUUID("Id"));if(!r.edit("when",t.getInt("When"))||!r.edit("source",t.getInt("Source"))||!r.edit("channel",t.getInt("Channel"))||!r.edit("modifier",t.getDouble("Modifier")))continue;r.enabled=t.getBoolean("Enabled");rows.add(r);}}
}
