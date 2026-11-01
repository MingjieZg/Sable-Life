package dev.sablelife.heart;

import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Portable core configuration; contacts and delay clocks are deliberately excluded. */
public final class TerrainBreakSettings {
 public static final int LIMIT=128;
 public static final List<String> SURFACE_PRESET=List.of("grass_block","dirt","coarse_dirt","rooted_dirt","podzol","mycelium","dirt_path","farmland","gravel","sand","red_sand","clay","stone","granite","diorite","andesite","deepslate","tuff","snow","snow_block").stream().map(id->"minecraft:"+id).toList();
 private boolean enabled,floor=true,debris=true,drops;private int delay;
 private final List<String> entries=new ArrayList<>();private final Set<ResourceLocation> blocks=new HashSet<>();private final List<TagKey<Block>> tags=new ArrayList<>();
 public boolean enabled(){return enabled;}public boolean floor(){return floor;}public boolean debris(){return debris;}public boolean drops(){return drops;}public int delay(){return delay;}public List<String> entries(){return List.copyOf(entries);}
 public boolean protects(BlockState state){return blocks.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))||tags.stream().anyMatch(state::is);}
 public boolean edit(String key,double value,String text){
  if(!Double.isFinite(value))return false;
  switch(key){
   case "crushEnabled","crushFloor","crushDebris","crushDrops"->{if(value!=0&&value!=1)return false;switch(key){case "crushEnabled"->enabled=value==1;case "crushFloor"->floor=value==1;case "crushDebris"->debris=value==1;default->drops=value==1;}}
   case "crushDelay"->{if(value<0||value>10)return false;delay=(int)Math.round(value*20);}
   case "crushProtectAdd"->{return add(text);}
   case "crushProtectPreset"->{boolean changed=false;for(String id:SURFACE_PRESET)changed|=add(id);return changed;}
   case "crushProtectRemove"->{if(value!=Math.rint(value)||value<0||value>=entries.size())return false;if(!entries.get((int)value).equals(text))return false;entries.remove((int)value);rebuild();}
   default->{return false;}
  }return true;
 }
 private boolean add(String text){if(text==null||text.length()>80||entries.size()>=LIMIT)return false;String s=text.trim();boolean tag=s.startsWith("#");var id=ResourceLocation.tryParse(tag?s.substring(1):s);if(id==null||!tag&&!BuiltInRegistries.BLOCK.containsKey(id))return false;s=(tag?"#":"")+id;if(s.length()>80)return false;if(entries.contains(s))return false;entries.add(s);rebuild();return true;}
 private void rebuild(){blocks.clear();tags.clear();for(String s:entries){boolean tag=s.startsWith("#");var id=ResourceLocation.tryParse(tag?s.substring(1):s);if(id==null)continue;if(tag)tags.add(TagKey.create(Registries.BLOCK,id));else blocks.add(id);}}
 public CompoundTag save(){var t=new CompoundTag();t.putBoolean("Enabled",enabled);t.putBoolean("Floor",floor);t.putBoolean("Debris",debris);t.putBoolean("Drops",drops);t.putInt("Delay",delay);var list=new ListTag();entries.forEach(s->list.add(StringTag.valueOf(s)));t.put("Protected",list);return t;}
 public void load(CompoundTag t){enabled=t.getBoolean("Enabled");floor=!t.contains("Floor")||t.getBoolean("Floor");debris=!t.contains("Debris")||t.getBoolean("Debris");drops=t.getBoolean("Drops");delay=Math.clamp(t.getInt("Delay"),0,200);entries.clear();for(var s:t.getList("Protected",8)){if(entries.size()>=LIMIT)break;add(s.getAsString());}rebuild();}
}
