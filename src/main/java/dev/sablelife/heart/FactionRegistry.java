package dev.sablelife.heart;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned player membership and direct, bilateral faction relationships. */
@net.neoforged.fml.common.EventBusSubscriber(modid="sablelife")
public final class FactionRegistry extends SavedData {
    public static final UUID WILDS = UUID.fromString("79a3db0b-383b-4cff-8091-56cf35776e4a");
    private static final int PLAYER_LIMIT=128;
    private static final class Faction {
        final UUID id,owner;
        final String name;
        String creatorName;
        boolean friendlyFire,privateFaction,alliedFire;
        final Map<UUID,String> members=new LinkedHashMap<>(),invitations=new LinkedHashMap<>();
        final Set<UUID> allies=new LinkedHashSet<>(),requests=new LinkedHashSet<>();
        Faction(UUID id,UUID owner,String name,String creatorName){this.id=id;this.owner=owner;this.name=name;this.creatorName=creatorName;members.put(owner,creatorName);}
    }
    private final Map<UUID,Faction> factions=new LinkedHashMap<>();
    private final Map<UUID,String> players=new LinkedHashMap<>();
    public void observe(ServerPlayer player){String name=player.getGameProfile().getName();if(!name.equals(players.put(player.getUUID(),name)))setDirty();}
    @net.neoforged.bus.api.SubscribeEvent
    public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer player)get(player.server).observe(player);}
    public static FactionRegistry get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(FactionRegistry::new,FactionRegistry::load),"sablelife_factions");}
    public boolean contains(UUID id){return id!=null&&factions.containsKey(id);}
    public boolean known(UUID id){return WILDS.equals(id)||contains(id);}
    public boolean friendlyFire(UUID id){var f=factions.get(id);return f!=null&&f.friendlyFire;}
    public boolean allied(UUID a,UUID b){return known(a)&&known(b)&&(a.equals(b)||contains(a)&&factions.get(a).allies.contains(b)&&factions.get(b).allies.contains(a));}
    public boolean damageAllowed(UUID source,UUID victim){
        if(!allied(source,victim))return true;
        if(source.equals(victim))return friendlyFire(victim);
        return factions.get(source).alliedFire&&factions.get(victim).alliedFire;
    }
    private boolean manages(ServerPlayer player,Faction f){return f!=null&&(f.owner.equals(player.getUUID())||player.hasPermissions(2));}
    public boolean canAssign(ServerPlayer player,UUID id){var f=factions.get(id);return f!=null&&(!f.privateFaction||f.members.containsKey(player.getUUID())||manages(player,f));}
    /** The authoritative reason is shared by menu feedback and all creation callers. */
    public String creationError(ServerPlayer player,String text){
        String name=text.strip();
        if(name.isEmpty())return "faction_name_required";
        if(name.equalsIgnoreCase("Wilds"))return "faction_name_reserved";
        if(name.codePointCount(0,name.length())>40||name.codePoints().anyMatch(Character::isISOControl))return "faction_name_invalid";
        if(factions.values().stream().anyMatch(f->f.name.equalsIgnoreCase(name)))return "faction_name_duplicate";
        if(factions.size()>=256)return "faction_server_limit";
        int limit=dev.sablelife.SableLifeServerConfig.maximumFactionsPerPlayer();
        if(limit>0&&!player.hasPermissions(2)&&factions.values().stream().filter(f->f.owner.equals(player.getUUID())).count()>=limit)return "faction_player_limit";
        return "";
    }
    public UUID create(ServerPlayer player,String text){
        if(!creationError(player,text).isEmpty())return null;
        String name=text.strip();
        var id=UUID.randomUUID();factions.put(id,new Faction(id,player.getUUID(),name,player.getGameProfile().getName()));setDirty();return id;
    }
    public boolean toggle(ServerPlayer player,UUID id){var f=factions.get(id);if(!manages(player,f))return false;f.friendlyFire=!f.friendlyFire;setDirty();return true;}
    public boolean privacy(ServerPlayer player,UUID id){var f=factions.get(id);if(!manages(player,f))return false;f.privateFaction=!f.privateFaction;setDirty();return true;}
    public boolean alliedFire(ServerPlayer player,UUID id){var f=factions.get(id);if(!manages(player,f))return false;f.alliedFire=!f.alliedFire;setDirty();return true;}
    public boolean joinPlayer(ServerPlayer player,UUID id){
        var f=factions.get(id);if(f==null||!canAssign(player,id))return false;
        if(f.members.containsKey(player.getUUID()))return true;
        if(f.members.size()>=PLAYER_LIMIT)return false;
        f.members.put(player.getUUID(),player.getGameProfile().getName());f.invitations.remove(player.getUUID());setDirty();return true;
    }
    /** Invitations only resolve profiles already known to this server; no blocking online lookup. */
    public boolean invite(ServerPlayer player,UUID id,String name){
        var f=factions.get(id);if(!manages(player,f)||!name.matches("[A-Za-z0-9_]{1,16}"))return false;
        var online=player.server.getPlayerList().getPlayerByName(name);
        com.mojang.authlib.GameProfile profile=online==null?null:online.getGameProfile();
        if(profile==null)for(var entry:players.entrySet())if(entry.getValue().equalsIgnoreCase(name)){profile=new com.mojang.authlib.GameProfile(entry.getKey(),entry.getValue());break;}
        if(profile==null||f.members.containsKey(profile.getId())||f.invitations.size()>=PLAYER_LIMIT)return false;
        f.invitations.put(profile.getId(),profile.getName());setDirty();return true;
    }
    public boolean accept(ServerPlayer player,UUID id){
        var f=factions.get(id);if(f==null||!f.invitations.containsKey(player.getUUID())||f.members.size()>=PLAYER_LIMIT)return false;
        f.invitations.remove(player.getUUID());f.members.put(player.getUUID(),player.getGameProfile().getName());setDirty();return true;
    }
    public boolean decline(ServerPlayer player,UUID id){var f=factions.get(id);if(f==null||f.invitations.remove(player.getUUID())==null)return false;setDirty();return true;}
    public boolean removeMember(ServerPlayer player,UUID id,UUID target){
        var f=factions.get(id);if(f==null||f.owner.equals(target)||!player.getUUID().equals(target)&&!manages(player,f))return false;
        if(f.members.remove(target)==null)return false;setDirty();return true;
    }
    public boolean revoke(ServerPlayer player,UUID id,UUID target){var f=factions.get(id);if(!manages(player,f)||f.invitations.remove(target)==null)return false;setDirty();return true;}
    public boolean requestAlliance(ServerPlayer player,UUID source,UUID target){
        var a=factions.get(source);var b=factions.get(target);if(source.equals(target)||!manages(player,a)||b==null||allied(source,target)||a.requests.contains(target))return false;
        a.requests.add(target);setDirty();return true;
    }
    public boolean acceptAlliance(ServerPlayer player,UUID receiving,UUID requesting){
        var a=factions.get(receiving);var b=factions.get(requesting);if(!manages(player,a)||b==null||!b.requests.remove(receiving))return false;
        a.requests.remove(requesting);a.allies.add(requesting);b.allies.add(receiving);setDirty();return true;
    }
    /** Either manager can decline, cancel or end their own bilateral relationship. */
    public boolean endAlliance(ServerPlayer player,UUID own,UUID other){
        var a=factions.get(own);var b=factions.get(other);if(!manages(player,a)||b==null)return false;
        boolean changed=a.allies.remove(other)|b.allies.remove(own)|a.requests.remove(other)|b.requests.remove(own);
        if(changed)setDirty();return changed;
    }
    public boolean delete(ServerPlayer player,UUID id){
        if(!manages(player,factions.get(id)))return false;
        factions.remove(id);for(var f:factions.values()){f.allies.remove(id);f.requests.remove(id);}setDirty();
        for(var level:player.server.getAllLevels())for(var core:dev.sablelife.ownership.LoadedCoreIndex.all(level))if(id.equals(core.relations().faction())){core.relations().faction(null);core.setChanged();}
        return true;
    }
    private CompoundTag row(Faction f){var r=new CompoundTag();r.putUUID("Id",f.id);r.putUUID("Owner",f.owner);r.putString("Name",f.name);r.putString("CreatorName",f.creatorName);r.putBoolean("FriendlyFire",f.friendlyFire);r.putBoolean("Private",f.privateFaction);r.putBoolean("AlliedFire",f.alliedFire);return r;}
    public ListTag snapshot(ServerPlayer player){
        for(var online:player.server.getPlayerList().getPlayers())observe(online);
        observe(player);var rows=new ListTag();for(var f:factions.values()){
            if(f.members.containsKey(player.getUUID())&&!player.getGameProfile().getName().equals(f.members.get(player.getUUID()))){f.members.put(player.getUUID(),player.getGameProfile().getName());if(f.owner.equals(player.getUUID()))f.creatorName=player.getGameProfile().getName();setDirty();}
            if(f.creatorName.isEmpty()&&player.server.getProfileCache()!=null){var cached=player.server.getProfileCache().get(f.owner);if(cached.isPresent()){f.creatorName=cached.get().getName();f.members.put(f.owner,f.creatorName);setDirty();}}
            var r=row(f);r.putBoolean("Manage",manages(player,f));r.putBoolean("Member",f.members.containsKey(player.getUUID()));r.putBoolean("Invited",f.invitations.containsKey(player.getUUID()));r.putBoolean("CanJoin",canAssign(player,f.id));rows.add(r);
        }return rows;
    }
    /** Only the currently inspected faction carries member lists in menu snapshots. */
    public CompoundTag detail(ServerPlayer player,UUID id){
        var f=factions.get(id);if(f==null)return new CompoundTag();var r=row(f);boolean manage=manages(player,f);r.putBoolean("Manage",manage);r.putBoolean("Member",f.members.containsKey(player.getUUID()));r.putBoolean("Invited",f.invitations.containsKey(player.getUUID()));r.putBoolean("CanJoin",canAssign(player,id));
        if(manage||f.members.containsKey(player.getUUID()))r.put("Members",people(f.members));
        if(manage)r.put("Invitations",people(f.invitations));
        var ties=new ListTag();for(var other:factions.values())if(!other.id.equals(id)){var t=new CompoundTag();t.putUUID("Id",other.id);t.putString("Name",other.name);t.putBoolean("Allied",allied(id,other.id));t.putBoolean("Incoming",other.requests.contains(id));t.putBoolean("Outgoing",f.requests.contains(other.id));t.putBoolean("DamageEnabled",f.alliedFire&&other.alliedFire);ties.add(t);}r.put("Alliances",ties);return r;
    }
    private static ListTag people(Map<UUID,String> values){var rows=new ListTag();values.forEach((id,name)->{var t=new CompoundTag();t.putUUID("Id",id);t.putString("Name",name);rows.add(t);});return rows;}
    private static ListTag ids(Set<UUID> values){var rows=new ListTag();values.forEach(id->rows.add(net.minecraft.nbt.NbtUtils.createUUID(id)));return rows;}
    @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider registries){var rows=new ListTag();for(var f:factions.values()){var r=row(f);r.put("Members",people(f.members));r.put("Invitations",people(f.invitations));r.put("Allies",ids(f.allies));r.put("Requests",ids(f.requests));rows.add(r);}t.put("Factions",rows);t.put("KnownPlayers",people(players));return t;}
    private static void loadPeople(CompoundTag t,String key,Map<UUID,String> values){for(var v:t.getList(key,10)){if(values.size()>=PLAYER_LIMIT)break;var r=(CompoundTag)v;if(r.hasUUID("Id"))values.put(r.getUUID("Id"),r.getString("Name"));}}
    private static void loadIds(CompoundTag t,String key,Set<UUID> values){for(var v:t.getList(key,11)){if(values.size()>=256)break;try{values.add(NbtUtils.loadUUID(v));}catch(IllegalArgumentException ignored){}}}
    public static FactionRegistry load(CompoundTag t,HolderLookup.Provider registries){
        var result=new FactionRegistry();for(var v:t.getList("KnownPlayers",10)){var r=(CompoundTag)v;if(r.hasUUID("Id"))result.players.put(r.getUUID("Id"),r.getString("Name"));}for(var v:t.getList("Factions",10)){var r=(CompoundTag)v;if(result.factions.size()>=256)break;if(!r.hasUUID("Id")||!r.hasUUID("Owner")||WILDS.equals(r.getUUID("Id")))continue;
            var f=new Faction(r.getUUID("Id"),r.getUUID("Owner"),r.getString("Name"),r.getString("CreatorName"));f.friendlyFire=r.getBoolean("FriendlyFire");f.privateFaction=r.getBoolean("Private");f.alliedFire=r.getBoolean("AlliedFire");loadPeople(r,"Members",f.members);loadPeople(r,"Invitations",f.invitations);f.members.keySet().forEach(f.invitations::remove);loadIds(r,"Allies",f.allies);loadIds(r,"Requests",f.requests);result.factions.put(f.id,f);
        }
        for(var f:result.factions.values()){f.allies.removeIf(id->id.equals(f.id)||!result.contains(id)||!result.factions.get(id).allies.contains(f.id));f.requests.removeIf(id->id.equals(f.id)||!result.contains(id)||f.allies.contains(id));}
        return result;
    }
}
