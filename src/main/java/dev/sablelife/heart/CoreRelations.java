package dev.sablelife.heart;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import dev.sablelife.ownership.LoadedCoreIndex;

/** Player exclusions use case-insensitive login names; faction identities use UUIDs. Exclusions affect acquisition only. */
public final class CoreRelations {
    public static final int LIMIT=256;
    private final Map<String,String> excluded=new LinkedHashMap<>();
    private UUID faction;
    private int priority;
    private String ownerName="";
    public String ownerName(){return ownerName;}
    public boolean ownerName(String value){if(value==null)return false;value=value.strip();if(!value.isEmpty()&&!value.matches("[A-Za-z0-9_]{1,16}"))return false;ownerName=value;return true;}
    public boolean isOwner(LivingEntity target){return target instanceof Player p&&!ownerName.isEmpty()&&ownerName.equalsIgnoreCase(p.getGameProfile().getName());}
    public int priority(){return priority;}
    public void priority(int value){priority=Math.clamp(value,0,3);}
    public UUID faction(){return faction;}
    public void faction(UUID id){faction=id;}
    private static String key(String name){return name.strip().toLowerCase(Locale.ROOT);}
    public boolean excludes(String name){return name!=null&&excluded.containsKey(key(name));}
    public boolean exclude(String name){if(name==null)return false;name=name.strip();if(!name.matches("[A-Za-z0-9_]{1,16}"))return false;String key=key(name);if(!excluded.containsKey(key)&&excluded.size()>=LIMIT)return false;excluded.put(key,name);return true;}
    public boolean remove(String name){return name!=null&&excluded.remove(key(name))!=null;}
    public CompoundTag save(){var t=new CompoundTag();t.putInt("TargetPriority",priority);t.putString("OwnerName",ownerName);if(faction!=null)t.putUUID("Faction",faction);var list=new ListTag();excluded.values().forEach(name->list.add(StringTag.valueOf(name)));t.put("ExcludedNames",list);return t;}
    public void load(CompoundTag t){excluded.clear();ownerName="";ownerName(t.getString("OwnerName"));priority(t.getInt("TargetPriority"));faction=t.hasUUID("Faction")?t.getUUID("Faction"):null;for(var v:t.getList("ExcludedNames",8))exclude(v.getAsString());for(var v:t.getList("Excluded",10))exclude(((CompoundTag)v).getString("Name"));}
    public static boolean allies(HeartBlockEntity a,HeartBlockEntity b){return a!=null&&b!=null&&a.getLevel() instanceof ServerLevel level&&FactionRegistry.get(level.getServer()).allied(a.relations().faction,b.relations().faction);}
    public static boolean targetAllowed(ServerLevel level,UUID owner,LivingEntity target){var core=LoadedCoreIndex.find(level,owner);if(core==null)return true;if(target instanceof Player player)return !core.relations().isOwner(player)&&!core.relations().excludes(player.getGameProfile().getName());if(target instanceof dev.sablelife.module.ModuleTargets.LifeTarget life)return !allies(core,life.core());return true;}
    public static boolean damageAllowed(HeartBlockEntity victim,UUID attacker){if(!(victim.getLevel() instanceof ServerLevel level))return true;var source=LoadedCoreIndex.find(level,attacker);return source==null||FactionRegistry.get(level.getServer()).damageAllowed(source.relations().faction,victim.relations().faction);}
}
