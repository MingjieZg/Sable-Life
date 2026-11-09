package dev.sablelife.ownership;

import java.util.*;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.photomancy.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Operator-approved templates and detached one-shot jobs; no template NBT is sent in menu snapshots. */
public final class BlueprintSpawns extends SavedData {
    private static final int LIMIT=32;
    private static final class Entry {
        UUID module,owner;String name="",phase="ready",error="";CompoundTag template=new CompoundTag(),progress=new CompoundTag();
        int life,redstone,blocks,count,remaining;long next;boolean armed=true,death,activate,detached;
        ResourceLocation dimension;Vec3 position;org.joml.Quaterniond rotation;transient ArenaRespawns.Build build;transient BlueprintSpawnPlacement.Geometry geometry;
    }
    private final Map<UUID,Entry> entries=new LinkedHashMap<>();
    public static boolean available(){return PhotomancyCompatibility.available();}
    public static BlueprintSpawns get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(BlueprintSpawns::new,BlueprintSpawns::load),"sablelife_blueprint_spawns");}
    /** Loaded authoring data proves absence; unloaded cores are never treated as deleted. */
    public static void reconcile(HeartBlockEntity heart){
        if(!(heart.getLevel() instanceof ServerLevel level))return;
        var data=get(level.getServer());
        for(var e:List.copyOf(data.entries.values()))if(e.owner.equals(heart.creatureId())){
            var module=heart.modules().find(e.module);
            if(module!=null&&module.type().equals("blueprint_spawn"))continue;
            if(List.of("queued","building").contains(e.phase)){
                if(!e.detached){e.detached=true;data.setDirty();}
            }else {data.entries.remove(e.module);data.setDirty();}
        }
    }
    static boolean referencesDeath(MinecraftServer server,UUID owner){
        return get(server).entries.values().stream().anyMatch(e->e.owner.equals(owner)&&List.of("queued","building").contains(e.phase));
    }
    static void retireIdleOwner(MinecraftServer server,UUID owner){
        var data=get(server);
        if(data.entries.values().removeIf(e->e.owner.equals(owner)&&!List.of("queued","building").contains(e.phase)))data.setDirty();
    }
    private static BlueprintSpawnPlacement.Destination destination(HeartBlockEntity heart,VirtualModule module,Entry entry){
        if(entry!=null&&List.of("queued","building").contains(entry.phase)&&entry.position!=null)return new BlueprintSpawnPlacement.Destination(entry.dimension,entry.position,entry.rotation);
        return currentDestination(heart,module,entry);
    }
    private static BlueprintSpawnPlacement.Destination currentDestination(HeartBlockEntity heart,VirtualModule module,Entry entry){
        var point=BlueprintSpawnPlacement.resolve(heart,module);
        if(point==null||entry==null||module.value("spawnAnchor")!=0||!available())return point;
        if(entry.geometry==null)entry.geometry=SpawnTemplateBridge.geometry(entry.template);
        var bounds=entry.geometry.bounds(point);var base=new Vec3(bounds.getCenter().x,bounds.minY,bounds.getCenter().z);
        return new BlueprintSpawnPlacement.Destination(point.dimension(),point.position().add(point.position().subtract(base)),point.rotation());
    }
    public static CompoundTag snapshot(HeartBlockEntity heart,VirtualModule module){
        var t=new CompoundTag();t.putBoolean("Available",available());Entry e=null;
        if(heart.getLevel() instanceof ServerLevel l){e=get(l.getServer()).entries.get(module.id());if(e!=null&&!e.owner.equals(heart.creatureId()))e=null;}
        var point=destination(heart,module,e);
        if(point!=null)t.putString("Position",String.format(Locale.ROOT,"%.1f, %.1f, %.1f",point.position().x,point.position().y,point.position().z));
        if(e!=null){t.putString("Name",e.name);t.putString("Phase",e.phase);t.putString("Error",e.error);t.putInt("Life",e.life);t.putInt("Redstone",e.redstone);t.putInt("Blocks",e.blocks);t.putInt("Count",e.count);t.putBoolean("CanActivate",e.life==1&&e.redstone==0);}
        t.putBoolean("CanPreview",available()&&e!=null&&point!=null);return t;
    }
    public static AABB previewBounds(HeartBlockEntity heart,VirtualModule module){
        if(!available()||!(heart.getLevel() instanceof ServerLevel level))return null;
        var e=get(level.getServer()).entries.get(module.id());if(e==null||!e.owner.equals(heart.creatureId()))return null;
        var point=destination(heart,module,e);if(point==null||!point.dimension().equals(level.dimension().location()))return null;
        if(e.geometry==null)e.geometry=SpawnTemplateBridge.geometry(e.template);
        return e.geometry.bounds(point);
    }
    public static void preview(ServerPlayer player,HeartBlockEntity heart,VirtualModule module){
        preview(player,heart,module,false);
    }
    public static void preview(ServerPlayer player,HeartBlockEntity heart,VirtualModule module,boolean sendModels){
        if(!player.canUseGameMasterBlocks()||!ModuleAvailability.enabled(heart,module))return;
        var box=previewBounds(heart,module);if(box==null)return;
        var e=get(player.server).entries.get(module.id());var point=destination(heart,module,e);
        var forward=point.rotation()==null?e.geometry.fixedForward():dev.sablelife.attack.ContactBox.rotate(point.rotation(),new Vec3(0,0,1));
        UUID projection=e.blocks<=dev.sablelife.SableLifeServerConfig.maximumBlueprintProjectionBlocks()?module.id():null;
        if(projection!=null&&sendModels)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,SpawnTemplateBridge.projection(e.template,projection,point.rotation()!=null,Vec3.ZERO));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ModuleNetworking.BlueprintPreview(point.dimension(),
                new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ),point.position(),forward,12,projection,
                point.rotation()==null?new org.joml.Quaternionf():new org.joml.Quaternionf(point.rotation())));
    }
    /** Release cancels jobs, but keeps the approved template attached to this module. */
    public static void resetForRelease(HeartBlockEntity heart,UUID nextOwner){
        if(!(heart.getLevel() instanceof ServerLevel level))return;var data=get(level.getServer());
        for(var e:List.copyOf(data.entries.values()))if(e.owner.equals(heart.creatureId())){
            if(e.build!=null){var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);for(var id:e.build.bodies())if(container!=null&&container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body&&!body.isRemoved())dev.sablelife.compat.StructureRemovalApi.remove(level,body);}
            var module=heart.modules().find(e.module);if(module==null||!module.type().equals("blueprint_spawn")){data.entries.remove(e.module);continue;}
            e.owner=nextOwner;e.phase="ready";e.error="";e.build=null;e.progress=new CompoundTag();e.position=null;e.rotation=null;e.remaining=0;e.count=0;e.next=0;e.armed=true;e.death=false;e.detached=false;
        }data.setDirty();
    }
    public static void release(HeartBlockEntity heart){if(heart.getLevel() instanceof ServerLevel l){var data=get(l.getServer());if(data.entries.values().removeIf(e->e.owner.equals(heart.creatureId())))data.setDirty();}}
    public static List<String> files(ServerPlayer player){return available()&&player.canUseGameMasterBlocks()?SpawnTemplateBridge.files(player.server):List.of();}
    public static boolean select(ServerPlayer player,HeartBlockEntity heart,VirtualModule module,String name){if(!available()||!player.canUseGameMasterBlocks()||!heart.canEdit(player)||module==null||!module.type().equals("blueprint_spawn")||!ModuleAvailability.enabled(heart,module))return false;try{return install(heart,module,name,SpawnTemplateBridge.read(player.server,name));}catch(java.io.IOException|RuntimeException ex){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("blueprint_spawn.sablelife.load_failed",ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage()),false);return false;}}
    public static boolean install(HeartBlockEntity heart,VirtualModule module,String name,SpawnTemplateBridge.Info info){reconcile(heart);var data=get(((ServerLevel)heart.getLevel()).getServer());var old=data.entries.get(module.id());if(old!=null&&List.of("queued","building").contains(old.phase))return false;if(old==null&&data.entries.size()>=LIMIT)throw new IllegalStateException("limit_records");long bytes=info.template().sizeInBytes();for(var e:data.entries.values())if(e!=old)bytes+=e.template.sizeInBytes();ArenaRespawnLimits.check("world_templates",bytes,dev.sablelife.SableLifeServerConfig.arenaWorldTemplateBytes());var e=new Entry();e.owner=heart.creatureId();e.module=module.id();e.name=name;e.template=info.template().copy();e.life=info.life();e.redstone=info.redstone();e.blocks=info.blocks();data.entries.put(e.module,e);if(!info.canActivate())module.edit("spawnActivate",0,"");data.setDirty();heart.modules().changed();heart.setChanged();return true;}
    public static boolean clear(ServerPlayer player,HeartBlockEntity heart,VirtualModule module){if(!player.canUseGameMasterBlocks()||!heart.canEdit(player))return false;var data=get(player.server);var e=data.entries.get(module.id());if(e==null||!e.owner.equals(heart.creatureId())||List.of("queued","building").contains(e.phase))return false;data.entries.remove(module.id());data.setDirty();return true;}
    private boolean queue(HeartBlockEntity heart,VirtualModule m,boolean death){var e=entries.get(m.id());var point=currentDestination(heart,m,e);if(e==null||!e.owner.equals(heart.creatureId())||!List.of("ready","done").contains(e.phase)||e.count>=m.value("spawnLimit")||heart.getLevel().getGameTime()<e.next||point==null)return false;if(m.value("spawnActivate")==1&&(e.life!=1||e.redstone!=0)){e.error="activation";setDirty();return false;}e.position=point.position();e.dimension=point.dimension();e.rotation=point.rotation()==null?null:new org.joml.Quaterniond(point.rotation());e.remaining=(int)m.value("spawnDelay");e.activate=m.value("spawnActivate")==1;e.death=death;e.phase="queued";e.error="";e.progress=new CompoundTag();e.next=heart.getLevel().getGameTime()+(long)m.value("spawnCooldown");setDirty();return true;}
    public static void tickCore(HeartBlockEntity heart){if(!(heart.getLevel() instanceof ServerLevel l))return;var data=get(l.getServer());for(var m:heart.modules().all())if(m.type().equals("blueprint_spawn")){var e=data.entries.get(m.id());if(e==null||!e.owner.equals(heart.creatureId()))continue;if(m.value("spawnEvent")!=0){e.armed=true;continue;}boolean powered=HeartModules.triggered(m,heart)&&heart.wireless().master();if(!powered){if(!e.armed){e.armed=true;data.setDirty();}}else if(e.armed){e.armed=false;data.queue(heart,m,false);data.setDirty();}}}
    public static void death(HeartBlockEntity heart){if(!available()||!(heart.getLevel() instanceof ServerLevel l)||!heart.wireless().master())return;var data=get(l.getServer());for(var m:heart.modules().all())if(m.type().equals("blueprint_spawn")&&m.value("spawnEvent")==1&&m.enabled()&&ModuleAvailability.enabled(heart,m))data.queue(heart,m,true);if(data.entries.values().removeIf(e->e.owner.equals(heart.creatureId())&&List.of("ready","done").contains(e.phase)))data.setDirty();}
    public static void deathTrigger(HeartBlockEntity heart,List<dev.sablelife.heart.DeathSignals.Pulse> pulses){
        if(pulses.isEmpty()||heart.health().state()!=dev.sablelife.heart.HeartState.DYING
                ||!available()||!(heart.getLevel() instanceof ServerLevel level))return;
        var data=get(level.getServer());
        for(var module:heart.modules().all()){
            if(module.type().equals("blueprint_spawn")&&module.value("spawnEvent")==0&&module.value("trigger")==2
                    &&HeartModules.status(module,heart).equals("valid")
                    &&pulses.stream().anyMatch(pulse->pulse.channel()==(int)module.value("channel")))
                data.queue(heart,module,true);
        }
    }
    private static boolean loaded(ServerLevel level,AABB box){if(!Double.isFinite(box.getSize())||box.getXsize()>512||box.getZsize()>512||box.minY<level.getMinBuildHeight()||box.maxY>level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(box))return false;int x0=((int)Math.floor(box.minX))>>4,x1=((int)Math.floor(box.maxX))>>4,z0=((int)Math.floor(box.minZ))>>4,z1=((int)Math.floor(box.maxZ))>>4;if((long)(x1-x0+1)*(z1-z0+1)>256)return false;for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!level.hasChunk(x,z))return false;return true;}
    public static void tick(ServerTickEvent.Post event){get(event.getServer()).advance(event.getServer());}
    public void advance(MinecraftServer server){if(!available()||!ModuleAvailability.enabled(false,"blueprint_spawn"))return;int builds=1;for(var e:List.copyOf(entries.values())){if(!List.of("queued","building").contains(e.phase))continue;var level=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,e.dimension));if(level==null)continue;try{if(e.build==null)e.build=PhotomancyRespawnBridge.createSpawn(e.template,e.progress,e.position,e.rotation);if(!loaded(level,e.build.bounds()))continue;if(e.remaining>0){e.remaining--;setDirty();continue;}if(e.death&&!LifeLifecycle.get(server).finished(e.owner))continue;if(builds--<=0)continue;e.phase="building";e.progress=e.build.save();setDirty();String status=e.build.step(level);e.progress=e.build.save();setDirty();if(!status.equals("DONE"))continue;dev.sablelife.compat.sable.SableBodyProbe.invalidate(level);if(e.activate){var core=e.build.heart(level);if(core==null||!core.activateForRespawn())throw new IllegalStateException("activation");e.build.captureOwnership(core);}e.count++;e.phase="done";e.build=null;e.progress=new CompoundTag();setDirty();if(e.detached||e.death||LifeLifecycle.get(server).isDying(e.owner)||LifeLifecycle.get(server).finished(e.owner))entries.remove(e.module);}catch(RuntimeException failure){if(e.build!=null){e.progress=e.build.save();var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);for(var id:e.build.bodies())try{if(container!=null&&container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body)dev.sablelife.compat.StructureRemovalApi.remove(level,body);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}}e.phase="failed";e.error=failure.getMessage()==null?"failed":failure.getMessage();setDirty();com.mojang.logging.LogUtils.getLogger().error("Blueprint generation {} failed without automatic retry",e.module,failure);}}}
    @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider registries){var list=new ListTag();for(var e:entries.values()){var r=new CompoundTag();r.putUUID("Module",e.module);r.putUUID("Owner",e.owner);r.putString("Name",e.name);r.putString("Phase",e.phase);r.putString("Error",e.error);r.put("Template",e.template);r.put("Progress",e.progress);r.putInt("Life",e.life);r.putInt("Redstone",e.redstone);r.putInt("Blocks",e.blocks);r.putInt("Count",e.count);r.putInt("Remaining",e.remaining);r.putLong("Next",e.next);r.putBoolean("Armed",e.armed);r.putBoolean("Death",e.death);r.putBoolean("Detached",e.detached);r.putBoolean("Activate",e.activate);if(e.position!=null){r.putString("Dimension",e.dimension.toString());r.putDouble("X",e.position.x);r.putDouble("Y",e.position.y);r.putDouble("Z",e.position.z);if(e.rotation!=null){var rotation=new ListTag();for(double v:new double[]{e.rotation.x,e.rotation.y,e.rotation.z,e.rotation.w})rotation.add(DoubleTag.valueOf(v));r.put("Rotation",rotation);}}list.add(r);}t.put("Entries",list);return t;}
    public static BlueprintSpawns load(CompoundTag t,HolderLookup.Provider registries){var data=new BlueprintSpawns();long bytes=0;for(var v:t.getList("Entries",10)){var r=(CompoundTag)v;if(data.entries.size()>=LIMIT||!r.hasUUID("Module")||!r.hasUUID("Owner"))continue;var template=r.getCompound("Template");bytes+=template.sizeInBytes();if(bytes>dev.sablelife.SableLifeServerConfig.arenaWorldTemplateBytes())break;var e=new Entry();e.module=r.getUUID("Module");e.owner=r.getUUID("Owner");e.name=r.getString("Name");e.phase=r.getString("Phase");e.error=r.getString("Error");e.template=template.copy();e.progress=r.getCompound("Progress").copy();e.life=r.getInt("Life");e.redstone=r.getInt("Redstone");e.blocks=r.getInt("Blocks");e.count=Math.max(0,r.getInt("Count"));e.remaining=Math.clamp(r.getInt("Remaining"),0,72000);e.next=r.getLong("Next");e.armed=r.getBoolean("Armed");e.death=r.getBoolean("Death");e.detached=r.getBoolean("Detached");e.activate=r.getBoolean("Activate");if(r.contains("Rotation")){var q=r.getList("Rotation",6);if(q.size()!=4)continue;e.rotation=new org.joml.Quaterniond(q.getDouble(0),q.getDouble(1),q.getDouble(2),q.getDouble(3));if(!Double.isFinite(e.rotation.lengthSquared())||e.rotation.lengthSquared()<1e-12)continue;e.rotation.normalize();}if(r.contains("Dimension")){e.dimension=ResourceLocation.tryParse(r.getString("Dimension"));e.position=new Vec3(r.getDouble("X"),r.getDouble("Y"),r.getDouble("Z"));if(e.dimension==null||!Double.isFinite(e.position.lengthSqr()))continue;}if(List.of("queued","building").contains(e.phase)&&e.position==null)continue;if(e.phase.equals("building")){e.phase="failed";e.error="interrupted";}data.entries.put(e.module,e);}return data;}
}
