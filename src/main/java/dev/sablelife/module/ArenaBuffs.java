package dev.sablelife.module;
import net.minecraft.nbt.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
/** Bounded persistent groups. Short-lived refresh effects never remove other effect sources. */
public final class ArenaBuffs {
 public static final int LIMIT=16,EFFECT_LIMIT=16,DURATION=40,REFRESH=20;
 public static final class Group {
  private final UUID id;private final VirtualModule editor;private int source,channel;private boolean enabled=true;
  private Group(UUID id){this.id=id;var tag=new CompoundTag();tag.putUUID("Id",id);tag.putString("Type","melee");editor=VirtualModule.load(tag);}
  public UUID id(){return id;}public VirtualModule editor(){return editor;}public int source(){return source;}public int channel(){return channel;}public boolean enabled(){return enabled;}
  public boolean powered(dev.sablelife.heart.HeartBlockEntity heart){return enabled&&(source==0||channel>0&&(source==1?heart.wireless().output(channel-1):heart.wireless().input(channel-1))>0);}
  public boolean edit(String key,double value){
   if(!Double.isFinite(value)||value!=Math.rint(value))return false;
   switch(key){case "enabled"->{if(value<0||value>1)return false;enabled=value==1;}case "source"->{if(value<0||value>2)return false;source=(int)value;}case "channel"->{if(value<0||value>65535)return false;channel=(int)value;}default->{return false;}}return true;
  }
  public boolean effect(String key,String id,double value){
   if(!Double.isFinite(value)||!persistent(id))return false;
   if(key.equals("add")){
    if(editor.effects().size()>=EFFECT_LIMIT||!editor.editEffect(key,id,0))return false;
    editor.editEffect("duration",id,2);return true;
   }
   if(key.equals("level")&&(value!=Math.rint(value)||value<1||value>5))return false;
   return (key.equals("remove")||key.equals("level"))&&editor.editEffect(key,id,value);
  }
  private CompoundTag save(){var tag=new CompoundTag();tag.putUUID("Id",id);tag.putBoolean("Enabled",enabled);tag.putInt("Source",source);tag.putInt("Channel",channel);tag.put("Effects",ModuleEffects.save(editor.effects()));return tag;}
 }
 private final List<Group> groups=new ArrayList<>();
 public List<Group> groups(){return List.copyOf(groups);}public Group find(UUID id){return groups.stream().filter(g->g.id.equals(id)).findFirst().orElse(null);}
 public Group add(){if(groups.size()>=LIMIT)return null;var g=new Group(UUID.randomUUID());groups.add(g);return g;}
 public boolean remove(UUID id){return groups.removeIf(g->g.id.equals(id));}
 public boolean move(UUID id,int delta){var g=find(id);int at=groups.indexOf(g),next=at+delta;if(g==null||Math.abs(delta)!=1||next<0||next>=groups.size())return false;Collections.swap(groups,at,next);return true;}
 public static boolean persistent(String id){var key=ResourceLocation.tryParse(id);return ModuleEffects.valid(id)&&key!=null&&!BuiltInRegistries.MOB_EFFECT.get(key).isInstantenous();}
 public ListTag save(){var tag=new ListTag();groups.forEach(g->tag.add(g.save()));return tag;}
 public void load(ListTag tag){groups.clear();var seen=new HashSet<UUID>();for(int i=0;i<Math.min(tag.size(),LIMIT);i++){
   var row=tag.getCompound(i);var id=row.hasUUID("Id")?row.getUUID("Id"):UUID.randomUUID();if(!seen.add(id))continue;var g=new Group(id);
   g.enabled=!row.contains("Enabled")||row.getBoolean("Enabled");g.source=Math.clamp(row.getInt("Source"),0,2);g.channel=Math.clamp(row.getInt("Channel"),0,65535);
   var effects=row.getList("Effects",10);for(int e=0;e<Math.min(effects.size(),EFFECT_LIMIT);e++){var value=effects.getCompound(e);String effect=value.getString("Id");if(g.effect("add",effect,0))g.effect("level",effect,Math.clamp(value.getInt("Level"),1,5));}groups.add(g);
  }}
 public void unbind(){groups.forEach(g->g.channel=0);}
 public boolean channelDeleted(int index){boolean changed=false;for(var g:groups)if(g.channel>index){g.channel=g.channel==index+1?0:g.channel-1;changed=true;}return changed;}
}
