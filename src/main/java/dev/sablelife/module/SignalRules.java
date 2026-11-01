package dev.sablelife.module;

import net.minecraft.nbt.*;
import java.util.*;

/** Bounded authoring rows shared by logic inputs and combat-event outputs. No runtime is serialized. */
public final class SignalRules {
 public static boolean supported(String type){return type.equals("signal_logic")||type.equals("combat_event");}
 public static int limit(String type){return type.equals("signal_logic")?16:64;}
 private static ModuleFields.Field n(String key,int min,int max,int value,int scale){return new ModuleFields.Field(key,min,max,value,scale,List.of());}
 private static ModuleFields.Field c(String key,String... values){return new ModuleFields.Field(key,0,values.length-1,0,1,List.of(values));}
 public static List<ModuleFields.Field> fields(String type){return type.equals("signal_logic")?List.of(n("signalInput",0,65535,0,1),c("signalCondition","signal_powered","signal_unpowered")):List.of(c("eventKind","event_kill","event_part","event_block"),c("eventFilter","event_filter_default","event_filter_second","event_filter_third","event_filter_all"),c("eventSource","signal_source_all"),n("eventCount",1,9999,1,1),n("eventChannel",0,65535,0,1),n("eventPower",1,15,15,1),n("eventPulse",1,12000,10,20),n("eventCooldown",0,12000,20,20));}
 public static final class Row {
  private final UUID id;private boolean enabled=true;private int revision;private final String type;private final CompoundTag values=new CompoundTag();private UUID source;
  Row(UUID id,String type){this.id=id;this.type=type;for(var f:fields(type))values.putDouble(f.key(),f.initial());}
  public UUID id(){return id;}public boolean enabled(){return enabled;}public int revision(){return revision;}public UUID source(){return source;}
  public void source(UUID id){if(!Objects.equals(source,id)){source=id;revision++;}}
  public double value(String key){return values.getDouble(key);}
  public boolean edit(String key,double v){if(!Double.isFinite(v))return false;if(key.equals("enabled")){if(v!=0&&v!=1)return false;if(enabled!=(v==1)){enabled=v==1;revision++;}return true;}
   var f=fields(type).stream().filter(x->x.key().equals(key)).findFirst().orElse(null);if(f==null||key.equals("eventSource")||v<f.min()||v>f.max()||v!=Math.rint(v))return false;
   if(key.equals("eventKind")&&v==1)return false;
   if(key.equals("eventFilter")&&v>(value("eventKind")==2?2:3))return false;
   if(value(key)!=v){values.putDouble(key,v);revision++;if(key.equals("eventKind")){values.putDouble("eventFilter",0);}}return true;
  }
  public CompoundTag save(){var t=new CompoundTag();t.putUUID("Id",id);t.putBoolean("Enabled",enabled);t.put("Values",values.copy());if(source!=null)t.putUUID("Source",source);return t;}
 }
 private final List<Row> rows=new ArrayList<>();private final String type;
 public SignalRules(String type){this.type=type;}
 public List<Row> rows(){return List.copyOf(rows);}public Row find(UUID id){return rows.stream().filter(r->r.id.equals(id)).findFirst().orElse(null);}
 public Row add(){if(rows.size()>=limit(type))return null;var r=new Row(UUID.randomUUID(),type);rows.add(r);return r;}
 public boolean remove(UUID id){return rows.removeIf(r->r.id.equals(id));}
 public boolean move(UUID id,int delta){if(Math.abs(delta)!=1)return false;int i=rows.indexOf(find(id)),j=i+delta;if(i<0||j<0||j>=rows.size())return false;Collections.swap(rows,i,j);return true;}
 public boolean channelDeleted(int index){boolean changed=false;String k=type.equals("signal_logic")?"signalInput":"eventChannel";for(var r:rows){int c=(int)r.value(k);if(c>index){r.edit(k,c==index+1?0:c-1);changed=true;}}return changed;}
 public void unbind(){for(var r:rows){r.edit(type.equals("signal_logic")?"signalInput":"eventChannel",0);r.source(null);}}
 public ListTag save(){var t=new ListTag();rows.forEach(r->t.add(r.save()));return t;}
 public void load(ListTag saved){rows.clear();var seen=new HashSet<UUID>();for(var v:saved){if(rows.size()>=limit(type))break;var t=(CompoundTag)v;if(!t.hasUUID("Id")||!seen.add(t.getUUID("Id")))continue;if(type.equals("combat_event")&&t.getCompound("Values").getDouble("eventKind")==1)continue;var r=new Row(t.getUUID("Id"),type);boolean valid=true;for(var f:fields(type))if(!f.key().equals("eventSource")&&t.getCompound("Values").contains(f.key())&&!r.edit(f.key(),t.getCompound("Values").getDouble(f.key())))valid=false;if(valid){r.enabled=t.getBoolean("Enabled");r.source=t.hasUUID("Source")?t.getUUID("Source"):null;rows.add(r);}}}
}
