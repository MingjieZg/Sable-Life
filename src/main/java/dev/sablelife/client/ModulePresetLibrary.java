package dev.sablelife.client;

import com.google.gson.*;
import dev.sablelife.module.ModulePreset;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.*;

/** Personal presets live in the client config, independently of a world or server. */
public final class ModulePresetLibrary {
    public record Entry(String name,CompoundTag module){}
    private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("config/sablelife/module-presets.json");}
    private static List<Entry> read()throws java.io.IOException{
        try{
            if(!Files.exists(file()))return List.of();
            if(Files.size(file())>16*1024*1024)throw new java.io.IOException("Preset library too large");
            var list=new ArrayList<Entry>();
            for(var value:JsonParser.parseString(Files.readString(file())).getAsJsonArray()){
                if(list.size()>=128)break;var row=value.getAsJsonObject();var text=row.get("module").getAsString();
                if(!dev.sablelife.module.AuthoringLimits.presetText(text))continue;
                var module=ModulePreset.portable(TagParser.parseTag(text));
                if(dev.sablelife.module.VirtualModule.load(module).known())list.add(new Entry(row.get("name").getAsString(),module));
            }return List.copyOf(list);
        }catch(Exception failure){throw new java.io.IOException("Could not read module presets",failure);}
    }
    public static List<Entry> load(){try{return read();}catch(java.io.IOException e){com.mojang.logging.LogUtils.getLogger().warn("Could not read module presets",e);return List.of();}}
    public static void delete(String name)throws java.io.IOException{var entries=new ArrayList<>(read());if(entries.removeIf(e->e.name.equals(name)))write(entries);}
    public static void moveRelative(String name,String neighbor)throws java.io.IOException{
        var entries=new ArrayList<>(read());int from=-1,to=-1;
        for(int i=0;i<entries.size();i++){if(entries.get(i).name.equals(name))from=i;if(entries.get(i).name.equals(neighbor))to=i;}
        if(from<0||to<0||from==to)return;var entry=entries.remove(from);entries.add(to,entry);write(entries);
    }
    public static void save(String name,CompoundTag module)throws java.io.IOException{
        var entries=new ArrayList<>(read());if(entries.size()>=128)throw new java.io.IOException("Preset limit reached");
        String base=name.strip();if(base.isEmpty()||base.length()>40)throw new java.io.IOException("Invalid preset name");
        String unique=base;int suffix=2;var names=entries.stream().map(Entry::name).toList();while(names.contains(unique))unique=base+" ("+suffix+++")";
        var portable=ModulePreset.portable(module);if(!dev.sablelife.module.AuthoringLimits.compound(portable,dev.sablelife.module.AuthoringLimits.MAX_PRESET_BYTES)||portable.toString().length()>ModulePreset.MAX_TEXT)throw new java.io.IOException("Preset too large");
        entries.add(new Entry(unique,portable));write(entries);
    }
    private static void write(List<Entry> entries)throws java.io.IOException{
        var json=new JsonArray();
        for(var entry:entries){var row=new JsonObject();row.addProperty("name",entry.name);row.addProperty("module",entry.module.toString());json.add(row);}
        Files.createDirectories(file().getParent());var temp=file().resolveSibling("module-presets.tmp");var text=new GsonBuilder().setPrettyPrinting().create().toJson(json);if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16*1024*1024)throw new java.io.IOException("Preset library too large");Files.writeString(temp,text);
        try{Files.move(temp,file(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file(),StandardCopyOption.REPLACE_EXISTING);}
    }
    private ModulePresetLibrary(){}
}
