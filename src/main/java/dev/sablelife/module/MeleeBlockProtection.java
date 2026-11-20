package dev.sablelife.module;

import dev.sablelife.heart.TerrainBreakSettings;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Per-module protection only: no collision switches, clocks or core-list references. */
public final class MeleeBlockProtection {
    public static final int LIMIT=128;
    private final List<String> entries=new ArrayList<>();
    private final Set<ResourceLocation> blocks=new HashSet<>();
    private final List<TagKey<Block>> tags=new ArrayList<>();
    public List<String> entries(){return List.copyOf(entries);}
    public boolean protects(BlockState state){return blocks.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))||tags.stream().anyMatch(state::is);}
    public boolean edit(String key,double value,String text){
        if(!Double.isFinite(value))return false;
        return switch(key){
            case "crushProtectAdd"->add(text,false);
            case "crushProtectPreset"->{boolean changed=false;for(var s:TerrainBreakSettings.SURFACE_PRESET)changed|=add(s,false);yield changed;}
            case "crushProtectRemove"->{
                if(value!=Math.rint(value)||value<0||value>=entries.size()||!entries.get((int)value).equals(text))yield false;
                entries.remove((int)value);rebuild();yield true;
            }
            default->false;
        };
    }
    private boolean add(String text,boolean loading){
        if(text==null||text.length()>80||entries.size()>=LIMIT)return false;
        var s=text.trim();boolean tag=s.startsWith("#");var id=ResourceLocation.tryParse(tag?s.substring(1):s);
        // A temporarily absent decoration mod must not erase a saved protection entry.
        if(id==null||!loading&&!tag&&!BuiltInRegistries.BLOCK.containsKey(id))return false;
        s=(tag?"#":"")+id;if(s.length()>80||entries.contains(s))return false;
        entries.add(s);rebuild();return true;
    }
    private void rebuild(){blocks.clear();tags.clear();for(var s:entries){boolean tag=s.startsWith("#");var id=ResourceLocation.parse(tag?s.substring(1):s);if(tag)tags.add(TagKey.create(Registries.BLOCK,id));else blocks.add(id);}}
    public CompoundTag save(){var t=new CompoundTag();var list=new ListTag();entries.forEach(s->list.add(StringTag.valueOf(s)));t.put("Protected",list);return t;}
    public void load(CompoundTag t){entries.clear();for(var s:t.getList("Protected",8))add(s.getAsString(),true);rebuild();}
}
