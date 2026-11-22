package dev.sablelife.ownership;

import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.photomancy.PhotomancyRespawnBridge;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;

/** World-owned templates and resumable jobs survive deletion of the original core. */
public final class ArenaRespawns extends SavedData {
 public interface Build {
  AABB bounds();String step(ServerLevel level);CompoundTag save();float fraction();HeartBlockEntity heart(ServerLevel level);UUID module(UUID original);Set<UUID> bodies();void restoreApprovals(HeartBlockEntity heart);void captureOwnership(HeartBlockEntity heart);
  default void restoreBlueprintSpawns(HeartBlockEntity heart, CompoundTag references, boolean summonsOnly) {}
 }
 private static final int LIMIT=32;
 private static final int SUMMON_LIMIT=16, ABANDONED_TICKS=600;
 static final int NEXT_STAGE=1, SUMMON=2;
 private final LinkedHashMap<UUID,Entry> entries=new LinkedHashMap<>();
 private final Map<UUID,Entry> stages=new HashMap<>();
 private int turn;
 private static final class Entry {
  UUID id,creature,module,templateModule,display;
  ResourceLocation dimension;Vec3 center;CompoundTag template,progress=new CompoundTag();
  int delay,remaining;String phase="ready",name="";Set<UUID> oldBodies=new HashSet<>(),newBodies=new HashSet<>();
  transient Build build;transient int shown=-1;
  UUID stageJob, stage, respawningCore;
  long stageDeadline, lastNearby, battleDeadline;
  Integer battleLimit;
  CompoundTag battleTitles;
  int shownBattleTitles;
  int summonsUsed;
  boolean defeatPending, stageDefeated, abandoning;
  Set<UUID> summons=new HashSet<>();
  VirtualModule boundary;
  CompoundTag blueprintReferences=new CompoundTag();
  String respawnAnnouncement;
  String defeatAnnouncement="";
 }
 public static boolean available(){return dev.sablelife.compat.photomancy.PhotomancyCompatibility.available();}
 public static ArenaRespawns get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ArenaRespawns::new,ArenaRespawns::load),"sablelife_arena_respawns");}
 public static void reconcile(HeartBlockEntity heart){
  if(!(heart.getLevel() instanceof ServerLevel level))return;var data=get(level.getServer());
  for(var e:List.copyOf(data.entries.values()))if(e.creature.equals(heart.creatureId())&&e.phase.equals("ready")){
   var module=heart.modules().find(e.module);
   if(module==null||!module.type().equals("arena"))data.remove(level.getServer(),e);
  }
 }
 static boolean referencesDeath(MinecraftServer server,UUID owner){return get(server).entries.values().stream().anyMatch(e->e.creature.equals(owner)&&!e.phase.equals("ready")||owner.equals(e.stage)||e.summons.contains(owner));}
 static void retireIdleOwner(MinecraftServer server,UUID owner){var data=get(server);for(var e:List.copyOf(data.entries.values()))if(e.creature.equals(owner)&&e.phase.equals("ready"))data.remove(server,e);}
 private Entry find(HeartBlockEntity heart,UUID module){return entries.values().stream().filter(e->e.creature.equals(heart.creatureId())&&e.module.equals(module)).findFirst().orElse(null);}
 public int count(){return entries.size();}
 private static boolean enabledBoundary(HeartBlockEntity heart, VirtualModule arena, Entry entry) {
  return arena!=null && ModuleAvailability.enabled(heart,arena) && arena.enabled()
          && arena.value("arenaRespawnEnabled")==1 && arena.arenaCenter()!=null
          && arena.arenaDimension().equals(entry.dimension.toString());
 }
 private static int battleLimit(VirtualModule arena) {
  return arena.value("arenaRespawnBattleTimer")==1 ? (int)arena.value("arenaRespawnBattleLimit") : 0;
 }
 public static void playerHit(ServerLevel level, HeartBlockEntity heart, ServerPlayer player) {
  if(player.level()!=level||player.isSpectator()||!available())return;
  var data=get(level.getServer());
  var stage=data.stages.get(heart.creatureId());
  if(stage!=null){
   if(!stage.abandoning&&!stage.stageDefeated)data.startBattle(level,stage);
   return;
  }
  for(var e:data.entries.values()) {
   if(!e.creature.equals(heart.creatureId())||!e.phase.equals("ready")||e.battleDeadline!=0)continue;
   var arena=heart.modules().find(e.module);
   if(!enabledBoundary(heart,arena,e))continue;
   e.battleLimit=battleLimit(arena);
   if(e.battleLimit>0){e.center=arena.arenaCenter();e.name=heart.creatureName();captureBattleTitles(e,arena);}
   data.startBattle(level,e);
   return;
  }
 }
 private void startBattle(ServerLevel level, Entry e) {
  if(e.battleDeadline!=0||e.battleLimit==null||e.battleLimit<=0)return;
  e.battleDeadline=level.getGameTime()+e.battleLimit;
  e.shownBattleTitles=0;
  setDirty();
  sendBattleReminder(level,e,ArenaBattleTitle.START,e.battleLimit);
 }
 private static void captureBattleTitles(Entry e, VirtualModule arena) {
  var titles=new CompoundTag();
  titles.putBoolean("Enabled",arena.value(ArenaBattleTitle.ENABLED_KEY)==1);
  titles.putDouble("Range",arena.value("warningRange"));
  titles.putBoolean("Chat",arena.value(ArenaBattleTitle.DISPLAY_KEY)==ArenaBattleTitle.CHAT_DISPLAY);
  for(var title:ArenaBattleTitle.values()){
   if(arena.hasText(title.key()))titles.putString(title.key(),arena.text(title.key()));
  }
  e.battleTitles=titles;
 }
 private static void restoreBattleTitles(Entry e, VirtualModule arena) {
  if(e.battleTitles==null)return;
  arena.edit(ArenaBattleTitle.ENABLED_KEY,e.battleTitles.getBoolean("Enabled")?1:0,"");
  arena.edit(ArenaBattleTitle.DISPLAY_KEY,e.battleTitles.getBoolean("Chat")?ArenaBattleTitle.CHAT_DISPLAY:0,"");
  for(var title:ArenaBattleTitle.values()){
   if(e.battleTitles.contains(title.key(),Tag.TAG_STRING))arena.edit(title.key(),0,e.battleTitles.getString(title.key()));
  }
 }
 private void sendBattleReminder(ServerLevel level, Entry e, ArenaBattleTitle title, int remaining) {
  var titles=e.battleTitles;
  if(titles==null||!titles.getBoolean("Enabled")||(e.shownBattleTitles&title.bit())!=0)return;
  e.shownBattleTitles|=title.bit();
  setDirty();
  String custom=titles.contains(title.key(),Tag.TAG_STRING)?titles.getString(title.key()):null;
  if(custom!=null&&custom.isBlank())return;
  double radius=titles.getDouble("Range");
  var message=title.message(custom,e.name,remaining);
  boolean chat=titles.getBoolean("Chat");
  for(var player:level.players()){
   if(player.connection==null||ModuleArena.horizontalDistance(player.position(),e.center)>radius)continue;
   if(chat){
    player.sendSystemMessage(message,false);
   }else{
    player.connection.send(new ClientboundSetTitlesAnimationPacket(5,40,10));
    player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
    player.connection.send(new ClientboundSetTitleTextPacket(message));
   }
  }
 }
 private static void setBoundary(Entry entry, VirtualModule arena) {
  var boundary = new VirtualModule("arena");
  for (String key : List.of("arenaRadius","warningRange","countdown","arenaDeath","arenaPlayerDeathCountdown","arenaForceReturn","arenaReturn"))
   boundary.edit(key, arena.value(key), "");
  boundary.arenaCenter(arena.arenaCenter(), entry.dimension.toString());
  entry.boundary = boundary;
 }
 public static VirtualModule inheritedBoundary(HeartBlockEntity heart) {
  if (!(heart.getLevel() instanceof ServerLevel level) || !ModuleAvailability.enabled(false,"arena")) return null;
  var entry = get(level.getServer()).stages.get(heart.creatureId());
  return entry == null || entry.abandoning || !entry.dimension.equals(level.dimension().location()) ? null : entry.boundary;
 }
 static UUID reserveBlueprint(HeartBlockEntity heart, VirtualModule spawn, int role, boolean death, BlueprintSpawnPlacement.Destination point) {
  if (!(heart.getLevel() instanceof ServerLevel level)) return null;
  var data=get(level.getServer());
  var id=heart.creatureId();
  var entry=data.stages.get(id);
  if(entry!=null && (role!=SUMMON || death || entry.abandoning || entry.stageDefeated || heart.health().state()!=HeartState.ALIVE))return null;
  if(entry==null){
   entry=data.entries.values().stream().filter(e->id.equals(e.creature)
           && (e.phase.equals("ready") || role==NEXT_STAGE && e.phase.equals("countdown"))).findFirst().orElse(null);
   if(entry==null || !enabledBoundary(heart, heart.modules().find(entry.module),entry))return null;
   if (role==NEXT_STAGE && (!death || entry.stageJob!=null || entry.stage!=null) || role==SUMMON && (death || heart.health().state()!=HeartState.ALIVE)) return null;
   setBoundary(entry,heart.modules().find(entry.module));
  }
  if (entry.boundary==null || !entry.dimension.equals(point.dimension())
          || ModuleArena.horizontalDistance(entry.boundary.arenaCenter(),point.position())>entry.boundary.value("arenaRadius"))return null;
  if (role==NEXT_STAGE) {
   entry.stageJob=spawn.id();
   entry.stageDeadline=level.getGameTime()+heart.health().remainingDeathTicks()+(long)spawn.value("spawnDelay")+ABANDONED_TICKS;
  } else {
   if(entry.summonsUsed>=SUMMON_LIMIT)return null;
   entry.summonsUsed++;
  }
  data.setDirty();
  return entry.id;
 }
 static boolean acceptsBlueprint(ServerLevel level, UUID arena, UUID job, int role) {
  var entry=get(level.getServer()).entries.get(arena);
  if(entry==null || !entry.dimension.equals(level.dimension().location()) || entry.abandoning
          || entry.stageDefeated || entry.phase.equals("resetting")
          || entry.battleDeadline>0&&level.getGameTime()>=entry.battleDeadline
          || !ModuleAvailability.enabled(false,"arena")) return false;
  return role==NEXT_STAGE ? job.equals(entry.stageJob) && entry.stage==null
          : entry.phase.equals("ready") || entry.stageJob!=null || entry.stage!=null;
 }
 static void blueprintPlaced(ServerLevel level, UUID arena, UUID job, int role, HeartBlockEntity heart) {
  var data=get(level.getServer());
  var entry=data.entries.get(arena);
  if (role==NEXT_STAGE && job.equals(entry.stageJob)) {
   entry.stageJob=null;
   entry.stage=heart.creatureId();
   entry.defeatPending=false;
   entry.lastNearby=level.getGameTime();
   data.stages.put(entry.stage,entry);
  } else entry.summons.add(heart.creatureId());
  LoadedCoreIndex.observe(heart);
  data.setDirty();
 }
 private static void announce(MinecraftServer server, String message) {
  if(!message.isBlank())server.getPlayerList().broadcastSystemMessage(CreatureNames.formatted(message),false);
 }
 public static boolean record(ServerPlayer player,HeartBlockEntity heart,VirtualModule module){
  if(!dev.sablelife.module.ModuleAvailability.enabled(false,"arena")||!available()||!player.isCreative()||!player.canUseGameMasterBlocks()||!heart.canAccessEditor(player)||heart.isControlCore()||module==null||!module.type().equals("arena")||module.arenaCenter()==null||!module.arenaDimension().equals(player.level().dimension().location().toString())||heart.health().state()==HeartState.DYING||heart.health().state()==HeartState.DEAD)return false;
  reconcile(heart);var data=get(player.server);var existing=data.find(heart,module.id());
  if(data.stages.containsKey(heart.creatureId())||data.entries.values().stream().anyMatch(e->e.summons.contains(heart.creatureId())))return false;
  if(existing==null&&(data.entries.size()>=LIMIT||data.entries.values().stream().anyMatch(e->e.creature.equals(heart.creatureId()))))return false;
  try{
   var template=PhotomancyRespawnBridge.capture(heart,module.arenaCenter());
   long total=template.sizeInBytes();for(var old:data.entries.values())if(old!=existing)total+=old.template.sizeInBytes();
   ArenaRespawnLimits.check("world_templates",total,dev.sablelife.SableLifeServerConfig.arenaWorldTemplateBytes());
   var e=new Entry();e.id=existing==null?UUID.randomUUID():existing.id;e.creature=heart.creatureId();e.module=module.id();e.templateModule=module.id();e.dimension=player.level().dimension().location();e.center=module.arenaCenter();e.template=template;e.delay=Math.clamp((int)module.value("arenaRespawnDelay"),100,72000);
   e.name=heart.creatureName();e.blueprintReferences=BlueprintSpawns.references(heart);
   if(existing!=null)data.remove(player.server,existing);
   data.entries.put(e.id,e);data.setDirty();heart.modules().changed();heart.setChanged();return true;
  }catch(ArenaRespawnLimits.Exceeded failure){player.displayClientMessage(Component.translatable("arena_respawn.sablelife.record_limit",Component.translatable("arena_respawn.sablelife.limit_"+failure.kind),failure.actual,failure.maximum),false);com.mojang.logging.LogUtils.getLogger().warn("Arena capture {} rejected: {} {} > {}",heart.creatureId(),failure.kind,failure.actual,failure.maximum);return false;
  }catch(IllegalArgumentException failure){player.displayClientMessage(Component.translatable("arena_respawn.sablelife.record_failed",Component.translatable("arena_respawn.sablelife.error_"+failure.getMessage())),false);return false;}
 }
 public static boolean clear(ServerPlayer player,HeartBlockEntity heart,VirtualModule module){
  if(!player.isCreative()||!player.canUseGameMasterBlocks()||!heart.canAccessEditor(player)||module==null)return false;
  var data=get(player.server);var e=data.find(heart,module.id());if(e==null)return false;data.remove(player.server,e);heart.modules().changed();heart.setChanged();return true;
 }
 public static CompoundTag snapshot(HeartBlockEntity heart,UUID module){
  var tag=new CompoundTag();tag.putBoolean("Available",available());
  if(heart.getLevel() instanceof ServerLevel level){var e=get(level.getServer()).find(heart,module);if(e!=null){tag.putBoolean("Recorded",true);tag.putString("Phase",e.phase);}}return tag;
 }
 public static void death(HeartBlockEntity heart){
  if(!(heart.getLevel() instanceof ServerLevel level))return;
  var data=get(level.getServer());
  boolean canRespawn=available();
  var stage=data.stages.get(heart.creatureId());
  if(stage!=null){
   if(!stage.abandoning&&!stage.phase.equals("resetting")){
    stage.stageDefeated=true;stage.defeatPending=true;stage.battleDeadline=0;data.setDirty();
   }
   return;
  }
  for(var e:data.entries.values()){
   if(e.summons.contains(heart.creatureId()))return;
   if(canRespawn&&heart.creatureId().equals(e.respawningCore)&&e.phase.equals("activating")){
    var module=heart.modules().find(e.module);
    if(module!=null) data.finishRespawn(level,e,heart,module);
   }
   if(!canRespawn||!e.creature.equals(heart.creatureId())||!e.phase.equals("ready"))continue;
   var module=heart.modules().find(e.module);
   if(!enabledBoundary(heart,module,e))continue;
   e.center=module.arenaCenter();e.name=heart.creatureName();e.delay=Math.clamp((int)module.value("arenaRespawnDelay"),100,72000);e.remaining=e.delay;e.phase="countdown";
   setBoundary(e,module);
   e.respawnAnnouncement=module.text("arenaRespawnAnnouncement");
   e.defeatAnnouncement=module.text("arenaDefeatAnnouncement");
   if(e.battleDeadline==0)e.battleLimit=battleLimit(module);
   captureBattleTitles(e,module);
   e.defeatPending=true;
   if(e.module.equals(e.templateModule)&&e.blueprintReferences.isEmpty())e.blueprintReferences=BlueprintSpawns.references(heart);
   var root=dev.ryanhcode.sable.Sable.HELPER.getContaining(level,heart.getBlockPos());
   e.oldBodies.addAll(BodyOwnership.get(level).bodyIds(level,heart.creatureId()));
   if(root!=null)for(var body:dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(root))e.oldBodies.add(body.getUniqueId());
   data.setDirty();
   return;
  }
  for(var module:heart.modules().all())if(module.type().equals("arena")&&module.enabled()&&ModuleAvailability.enabled(heart,module)
          &&module.arenaCenter()!=null&&module.arenaDimension().equals(level.dimension().location().toString())
          &&!module.text("arenaDefeatAnnouncement").isBlank()){
   announce(level.getServer(),module.text("arenaDefeatAnnouncement"));
   break;
  }
 }
 /** Keep recorded authoring data, cancel the previous life's detached job. */
 public static void resetForRelease(HeartBlockEntity heart,UUID nextCreature){
  if(!(heart.getLevel() instanceof ServerLevel level))return;var data=get(level.getServer());
  for(var e:List.copyOf(data.entries.values()))if(e.creature.equals(heart.creatureId())){
   data.remove(level.getServer(),e);var module=heart.modules().find(e.module);if(module==null||!module.type().equals("arena"))continue;
   e.creature=nextCreature;e.phase="ready";e.remaining=0;e.build=null;e.progress=new CompoundTag();e.oldBodies.clear();e.newBodies.clear();e.display=null;e.shown=-1;data.entries.put(e.id,e);
  }data.setDirty();
 }
 public static void release(HeartBlockEntity heart){release(heart,false);}
 public static void releaseAfterNaturalRemoval(HeartBlockEntity heart){release(heart,true);}
 private static void release(HeartBlockEntity heart, boolean naturalRemoval) {
  if(!(heart.getLevel() instanceof ServerLevel level))return;
  var data=get(level.getServer());
  for(var e:List.copyOf(data.entries.values()))
   if(e.creature.equals(heart.creatureId())&&(!naturalRemoval||!e.phase.equals("resetting")))data.remove(level.getServer(),e);
 }
 private static boolean loaded(ServerLevel level,AABB bounds){
  if(!Double.isFinite(bounds.getSize())||bounds.getXsize()>512||bounds.getZsize()>512||bounds.minY<level.getMinBuildHeight()||bounds.maxY>level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(bounds))return false;
  int x0=((int)Math.floor(bounds.minX))>>4,x1=((int)Math.floor(bounds.maxX))>>4,z0=((int)Math.floor(bounds.minZ))>>4,z1=((int)Math.floor(bounds.maxZ))>>4;
  if((long)(x1-x0+1)*(z1-z0+1)>256)return false;
  for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!level.hasChunk(x,z))return false;return true;
 }
 public static void beforeTick(ServerTickEvent.Pre event){var server=event.getServer();for(var e:get(server).entries.values())if(!e.newBodies.isEmpty()&&!e.phase.equals("activating")){
  var level=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,e.dimension));if(level!=null)ArenaBuildTicks.hold(level,e.id,e.newBodies);
 }}
 public static void tick(ServerTickEvent.Post event){get(event.getServer()).advance(event.getServer());}
 private static boolean quietRemoval(ServerLevel level, UUID owner, Vec3 center) {
  var lifecycle=LifeLifecycle.get(level.getServer());
  boolean owned=BodyOwnership.get(level).hasOwned(owner);
  if(lifecycle.finished(owner)||!owned&&!lifecycle.isDying(owner))return true;
  var heart=LoadedCoreIndex.find(level,owner);
  if(heart!=null)heart.expireWildEncounter();
  else if(owned)lifecycle.scheduleNaturalRemoval(level,owner,center);
  lifecycle.expediteNaturalRemoval(owner);
  return false;
 }
 private void beginBattleReset(ServerLevel level, Entry e) {
  e.phase="resetting";
  e.battleDeadline=0;
  e.remaining=0;
  e.defeatPending=false;
  e.oldBodies.addAll(BodyOwnership.get(level).bodyIds(level,e.creature));
  var heart=LoadedCoreIndex.find(level,e.creature);
  var arena=heart==null?null:heart.modules().find(e.module);
  if(arena!=null){
   e.respawnAnnouncement=arena.text("arenaRespawnAnnouncement");
   e.defeatAnnouncement=arena.text("arenaDefeatAnnouncement");
   captureBattleTitles(e,arena);
  }
  setDirty();
 }
 private boolean battleResetCleaned(ServerLevel level, Entry e) {
  var server=level.getServer();
  BlueprintSpawns.cancelArenaBlueprints(server,e.id,null);
  e.stageJob=null;
  if(e.stage!=null&&quietRemoval(level,e.stage,e.center)){
   stages.remove(e.stage);
   e.stage=null;
   setDirty();
  }
  if(e.summons.removeIf(id->quietRemoval(level,id,e.center)))setDirty();
  return quietRemoval(level,e.creature,e.center)&&e.stage==null&&e.summons.isEmpty();
 }
 private boolean waitForEncounter(ServerLevel level, Entry e) {
  var server=level.getServer();
  var lifecycle=LifeLifecycle.get(server);
  if(e.stageJob!=null){
   e.defeatPending=false;
   if(level.getGameTime()<e.stageDeadline && BlueprintSpawns.pendingArenaBlueprint(server,e.stageJob))return true;
   BlueprintSpawns.cancelArenaBlueprints(server,e.id,e.stageJob);
   e.stageJob=null;
   setDirty();
  }
  if(e.stage!=null){
   if(e.stageDefeated){
    if(e.defeatPending){e.defeatPending=false;setDirty();announce(server,e.defeatAnnouncement);}
    if(!lifecycle.finished(e.stage))return true;
   }else{
    var heart=LoadedCoreIndex.find(level,e.stage);
    if(!e.abandoning && heart!=null && heart.health().state()==HeartState.DORMANT && !heart.awaitingBlueprintActivation())e.abandoning=true;
    if(!e.abandoning && !BodyOwnership.get(level).hasOwned(e.stage) && !lifecycle.isDying(e.stage))e.abandoning=true;
    if(!e.abandoning && level.getGameTime()%20==0){
     boolean nearby=level.players().stream().anyMatch(player->player.isAlive()&&!player.isCreative()&&!player.isSpectator()
             && ModuleArena.horizontalDistance(player.position(),e.center)<=e.boundary.value("warningRange"));
     if(nearby){e.lastNearby=level.getGameTime();setDirty();}
     else if(level.getGameTime()-e.lastNearby>=ABANDONED_TICKS)e.abandoning=true;
    }
    if(!e.abandoning)return true;
    setDirty();
    BlueprintSpawns.cancelArenaBlueprints(server,e.id,null);
    if(!quietRemoval(level,e.stage,e.center))return true;
   }
   stages.remove(e.stage);
   e.stage=null;
   setDirty();
  }
  if(e.defeatPending){e.defeatPending=false;setDirty();announce(server,e.defeatAnnouncement);}
  if(!e.summons.isEmpty()){
   BlueprintSpawns.cancelArenaBlueprints(server,e.id,null);
   if(e.summons.removeIf(id->quietRemoval(level,id,e.center)))setDirty();
   if(!e.summons.isEmpty())return true;
  }
  return false;
 }
 private void keepSummonsInBounds(ServerLevel level, Entry entry) {
  if(entry.summons.isEmpty()||entry.boundary==null||level.getGameTime()%20!=0)return;
  var life=LifeLifecycle.get(level.getServer());
  if(entry.summons.removeIf(id->life.finished(id)||!life.isDying(id)&&!BodyOwnership.get(level).hasOwned(id)))setDirty();
  for(var id:entry.summons){
   var heart=LoadedCoreIndex.find(level,id);
   if(heart==null)continue;
   if(heart.health().state()==HeartState.DORMANT&&!heart.awaitingBlueprintActivation()){
    quietRemoval(level,id,entry.center);
   }else if(heart.health().state()==HeartState.ALIVE
            && level.hasChunkAt(BlockPos.containing(entry.boundary.arenaCenter()))
            && ModuleArena.horizontalDistance(ModuleArena.corePosition(heart),entry.boundary.arenaCenter())>entry.boundary.value("arenaRadius")){
    ModuleArena.returnToCenter(heart,entry.boundary.arenaCenter());
   }
  }
 }
 private void finishRespawn(ServerLevel level, Entry e, HeartBlockEntity heart, VirtualModule module) {
  e.creature=heart.creatureId();e.module=module.id();e.phase="ready";e.progress=new CompoundTag();e.build=null;e.oldBodies.clear();e.newBodies.clear();
  e.respawningCore=null;e.summonsUsed=0;e.stageDefeated=false;e.abandoning=false;e.defeatPending=false;
  e.battleDeadline=0;
  hide(level,e);heart.modules().changed();heart.setChanged();setDirty();
  announce(level.getServer(),module.text("arenaRespawnAnnouncement"));
 }
 public void advance(MinecraftServer server){
  int buildSlots=1;var list=new ArrayList<>(entries.values());if(list.isEmpty())return;
  Collections.rotate(list,Math.floorMod(turn++,list.size()));
  for(var e:list){
   if (e.phase.equals("failed") && (!e.newBodies.isEmpty() || !e.progress.isEmpty())) rollback(server, e);
   var level=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,e.dimension));if(level==null)continue;
   if(!dev.sablelife.module.ModuleAvailability.enabled(false,"arena")){hide(level,e);continue;}
   try{
    boolean inBattle=e.battleDeadline>0;
    boolean ready=e.phase.equals("ready");
    if(ready&&!inBattle){if(e.display!=null)hide(level,e);keepSummonsInBounds(level,e);continue;}
    if(!available()){display(level,e,"missing",0);continue;}
    if(inBattle&&level.getGameTime()>=e.battleDeadline
            &&(ready||e.stageJob!=null||e.stage!=null&&!e.stageDefeated)){
     beginBattleReset(level,e);
     ready=false;
    }
    if(ready){displayBattle(level,e);keepSummonsInBounds(level,e);continue;}
    if(e.battleDeadline>0&&e.stageJob==null&&e.stage==null){e.battleDeadline=0;setDirty();}
    boolean resetting=e.phase.equals("resetting");
    if(resetting&&!battleResetCleaned(level,e)){display(level,e,"resetting",0);continue;}
    if(e.phase.equals("countdown") && waitForEncounter(level,e)){
     if(e.battleDeadline>0)displayBattle(level,e);else hide(level,e);
     if(!e.abandoning)keepSummonsInBounds(level,e);
     continue;
    }
    if(e.battleDeadline>0){e.battleDeadline=0;setDirty();}
    if(e.phase.equals("activating")){
     var core=LoadedCoreIndex.find(level,e.respawningCore);
     if(core==null){
      if(!BodyOwnership.get(level).hasOwned(e.respawningCore)){e.phase="failed";setDirty();}
      continue;
     }
     var module=core.modules().find(e.module);
     if(module!=null && core.health().state()==HeartState.ALIVE){finishRespawn(level,e,core,module);continue;}
     if(!core.awaitingBlueprintActivation()){
      if (e.newBodies.isEmpty() && e.progress.isEmpty()) quietRemoval(level,core.creatureId(),e.center);
      else rollback(server, e);
      e.phase="failed";setDirty();
     }
     continue;
    }
    if(e.build==null)e.build=PhotomancyRespawnBridge.create(e.template,e.progress,e.center);
    if(!loaded(level,e.build.bounds())){hide(level,e);continue;}
    if(e.phase.equals("failed")){display(level,e,"failed",0);continue;}
    if(e.remaining>0){e.remaining--;setDirty();display(level,e,"countdown",1-e.remaining/(float)Math.max(1,e.delay));continue;}
    if(e.phase.equals("countdown")&&!LifeLifecycle.get(server).finished(e.creature)){display(level,e,"cleanup",0);continue;}
    var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
    if(container==null||e.oldBodies.stream().anyMatch(id->container.getSubLevel(id)!=null)){display(level,e,"cleanup",0);continue;}
    if(buildSlots--<=0)continue;
    e.phase="building";display(level,e,"building",0);var status=e.build.step(level);e.progress=e.build.save();e.newBodies=e.build.bodies();setDirty();
    display(level,e,"building",e.build.fraction());
    if(status.equals("FAILED")||status.equals("WAITING_FOR_MATERIALS")){rollback(server,e);e.phase="failed";setDirty();continue;}
    if(status.equals("DONE")){
     dev.sablelife.compat.sable.SableBodyProbe.invalidate(level);
     var heart=e.build.heart(level);var moduleId=e.build.module(e.templateModule);
     var module=heart==null||moduleId==null?null:heart.modules().find(moduleId);
     if(module==null){rollback(server,e);e.phase="failed";setDirty();continue;}
     e.build.restoreApprovals(heart);
     e.build.restoreBlueprintSpawns(heart,e.blueprintReferences,false);
     module.arenaCenter(e.center,e.dimension.toString());
     if(e.respawnAnnouncement!=null){
      module.edit("arenaRespawnAnnouncement",0,e.respawnAnnouncement);
      module.edit("arenaDefeatAnnouncement",0,e.defeatAnnouncement);
     }
     if(e.battleLimit!=null){
      module.edit("arenaRespawnBattleTimer",e.battleLimit>0?1:0,"");
      if(e.battleLimit>0){
       module.edit("arenaRespawnBattleLimit",e.battleLimit,"");
       module.edit("arenaRespawnEnabled",1,"");
      }
     }
     restoreBattleTitles(e,module);
     e.respawningCore=heart.creatureId();
     e.build.captureOwnership(heart);
     if(!heart.activateAfterBlueprintPlacement(e.build.bodies().size())){rollback(server,e);e.phase="failed";setDirty();continue;}
     if(heart.awaitingBlueprintActivation()){
      e.respawningCore=heart.creatureId();e.module=moduleId;e.phase="activating";setDirty();
     }else finishRespawn(level,e,heart,module);
    }
   }catch(RuntimeException failure){
    rollback(server, e);
    e.phase="failed";setDirty();
    com.mojang.logging.LogUtils.getLogger().error("Arena respawn {} stopped without retrying partial placement",e.id,failure);
   }
  }
 }
 private void hide(ServerLevel level,Entry e){if(e.display!=null){var entity=level.getEntity(e.display);if(entity!=null){entity.discard();e.display=null;setDirty();}}e.shown=-1;}
 private void display(ServerLevel level,Entry e,String phase,float fraction){
  display(level,e,phase,fraction,e.remaining);
 }
 private void displayBattle(ServerLevel level,Entry e) {
  int ticks=(int)Math.clamp(e.battleDeadline-level.getGameTime(),0,ModuleFields.ARENA_BATTLE_MAX_TICKS);
  if(e.battleTitles!=null&&e.battleTitles.getBoolean("Enabled")){
   var title=ArenaBattleTitle.due(ticks,e.battleLimit);
   if(title!=null)sendBattleReminder(level,e,title,ticks);
  }
  display(level,e,"battle",1-ticks/(float)e.battleLimit,ticks);
 }
 private void display(ServerLevel level,Entry e,String phase,float fraction,int ticks){
  if(!level.hasChunkAt(BlockPos.containing(e.center)))return;
  int seconds=(ticks+19)/20,filled=Math.clamp((int)(fraction*20),0,20),key=Objects.hash(phase,seconds,filled);
  if(e.shown==key&&e.display!=null&&level.getEntity(e.display)!=null)return;
  var displayPoint=RespawnDisplayPlacement.locate(level,e.center);if(displayPoint==null)return;
  Display.TextDisplay text=e.display==null?null:level.getEntity(e.display) instanceof Display.TextDisplay t?t:null;
  if(text==null){text=EntityType.TEXT_DISPLAY.create(level);if(text==null)return;if(e.display!=null)text.setUUID(e.display);text.setPos(displayPoint);text.setNoGravity(true);text.addTag("sablelife_arena_respawn");text.getPersistentData().putUUID("SableLifeArenaRecord",e.id);if(!level.addFreshEntity(text))return;e.display=text.getUUID();setDirty();}
  if(!text.position().equals(displayPoint))text.setPos(displayPoint);
  var label=Component.translatable("arena_respawn.sablelife."+phase,seconds).append("\n").append(Component.literal("\u25A0".repeat(filled)+"\u25A1".repeat(20-filled)).withStyle(net.minecraft.ChatFormatting.YELLOW));
  // Keep a translatable arena root so the existing shader label path recognizes it.
  var name=CreatureNames.formatted(e.name);if(name.getString().isBlank())name=Component.translatable("entity.sablelife.creature");
  label=Component.translatable("arena_respawn.sablelife.named",name,label);
  var access=(dev.sablelife.mixin.RespawnTextDisplayAccess)(Object)text;access.sablelife$text(label);access.sablelife$background(0xAA101018);access.sablelife$flags((byte)3);access.sablelife$lineWidth(256);((dev.sablelife.mixin.RespawnDisplayAccess)(Object)text).sablelife$billboard(Display.BillboardConstraints.CENTER);e.shown=key;
 }
 private void rollback(MinecraftServer server, Entry entry) {
  var build = entry.build;
  var bodies = new HashSet<>(entry.newBodies);
  if (build != null) bodies.addAll(build.bodies());
  BlueprintCleanup.request(server, UUID.randomUUID(), entry.dimension, entry.respawningCore,
          bodies, build == null ? entry.progress : build.save());
  entry.newBodies = new HashSet<>();
  entry.progress = new CompoundTag();
  entry.build = null;
  entry.respawningCore = null;
  setDirty();
 }
 private void remove(MinecraftServer server,Entry e){
  var level=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,e.dimension));if(level!=null)hide(level,e);
  BlueprintSpawns.cancelArenaBlueprints(server,e.id,null);
  if (!e.newBodies.isEmpty() || !e.progress.isEmpty() || e.build != null) rollback(server, e);
  if(level!=null){
   if(e.stage!=null)quietRemoval(level,e.stage,e.center);
   for(var summon:e.summons)quietRemoval(level,summon,e.center);
   if(e.respawningCore!=null)quietRemoval(level,e.respawningCore,e.center);
  }
  if(e.stage!=null)stages.remove(e.stage);
  e.stage=null;e.stageJob=null;e.respawningCore=null;e.summons.clear();e.summonsUsed=0;
  e.stageDefeated=false;e.abandoning=false;e.defeatPending=false;
  e.battleDeadline=0;e.battleLimit=null;
  if(level!=null)removeDisplays(level,e.id);
  entries.remove(e.id);setDirty();
 }
 public static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){
  if(event.getLevel() instanceof ServerLevel level&&event.getEntity() instanceof Display.TextDisplay text&&text.getPersistentData().hasUUID("SableLifeArenaRecord")){
   var entry=get(level.getServer()).entries.get(text.getPersistentData().getUUID("SableLifeArenaRecord"));if(entry==null||entry.phase.equals("ready")&&entry.battleDeadline==0)event.setCanceled(true);
  }
 }
 private static void removeDisplays(ServerLevel level,UUID record){var matches=new ArrayList<Display.TextDisplay>();for(var entity:level.getAllEntities())if(entity instanceof Display.TextDisplay text&&text.getPersistentData().hasUUID("SableLifeArenaRecord")&&text.getPersistentData().getUUID("SableLifeArenaRecord").equals(record))matches.add(text);matches.forEach(Entity::discard);}
 private boolean cancelOrphan(ServerLevel level,Vec3 point){Display.TextDisplay nearest=null;double distance=64*64;for(var entity:level.getAllEntities())if(entity instanceof Display.TextDisplay text&&text.getPersistentData().hasUUID("SableLifeArenaRecord")&&!entries.containsKey(text.getPersistentData().getUUID("SableLifeArenaRecord"))&&text.position().distanceToSqr(point)<distance){nearest=text;distance=text.position().distanceToSqr(point);}if(nearest==null)return false;removeDisplays(level,nearest.getPersistentData().getUUID("SableLifeArenaRecord"));return true;}
 private static double cancelDistance(ServerLevel level,Entry e,Vec3 point){
  double distance=e.center.distanceToSqr(point);
  var entity=e.display==null?null:level.getEntity(e.display);
  if(entity instanceof Display.TextDisplay text&&text.getPersistentData().hasUUID("SableLifeArenaRecord")&&text.getPersistentData().getUUID("SableLifeArenaRecord").equals(e.id))distance=Math.min(distance,text.position().distanceToSqr(point));
  return distance;
 }
 public static void commands(RegisterCommandsEvent event){event.getDispatcher().register(net.minecraft.commands.Commands.literal("sablelife_respawn").requires(s->s.hasPermission(2)).then(net.minecraft.commands.Commands.literal("cancel").executes(ctx->{var source=ctx.getSource();var data=get(source.getServer());var e=data.entries.values().stream().filter(v->v.dimension.equals(source.getLevel().dimension().location())&&cancelDistance(source.getLevel(),v,source.getPosition())<64*64).min(Comparator.comparingDouble(v->cancelDistance(source.getLevel(),v,source.getPosition()))).orElse(null);if(e==null){if(!data.cancelOrphan(source.getLevel(),source.getPosition()))return 0;}else data.remove(source.getServer(),e);source.sendSuccess(()->Component.translatable("arena_respawn.sablelife.cancelled"),false);return 1;})));}
 private static void saveEncounter(Entry e, CompoundTag tag) {
  if(e.stageJob!=null)tag.putUUID("StageJob",e.stageJob);
  if(e.stage!=null)tag.putUUID("Stage",e.stage);
  if(e.respawningCore!=null)tag.putUUID("RespawningCore",e.respawningCore);
  tag.putLong("StageDeadline",e.stageDeadline);
  tag.putLong("LastNearby",e.lastNearby);
  if(e.battleLimit!=null)tag.putInt("BattleLimit",e.battleLimit);
  if(e.battleDeadline>0)tag.putLong("BattleDeadline",e.battleDeadline);
  if(e.battleTitles!=null)tag.put("BattleTitles",e.battleTitles);
  if(e.battleDeadline>0&&e.shownBattleTitles!=0)tag.putInt("ShownBattleTitles",e.shownBattleTitles);
  tag.putInt("SummonsUsed",e.summonsUsed);
  tag.putBoolean("DefeatPending",e.defeatPending);
  tag.putBoolean("StageDefeated",e.stageDefeated);
  tag.putBoolean("Abandoning",e.abandoning);
  if(e.boundary!=null)tag.put("StageBoundary",e.boundary.save());
  if(e.respawnAnnouncement!=null)tag.putString("RespawnAnnouncement",e.respawnAnnouncement);
  tag.putString("DefeatAnnouncement",e.defeatAnnouncement);
  tag.put("BlueprintReferences",e.blueprintReferences);
  var summons=new ListTag();
  for(var id:e.summons)summons.add(NbtUtils.createUUID(id));
  tag.put("Summons",summons);
 }
 private void loadEncounter(Entry e, CompoundTag tag) {
  if(tag.hasUUID("StageJob"))e.stageJob=tag.getUUID("StageJob");
  if(tag.hasUUID("Stage")){e.stage=tag.getUUID("Stage");stages.put(e.stage,e);}
  if(tag.hasUUID("RespawningCore"))e.respawningCore=tag.getUUID("RespawningCore");
  if(e.phase.equals("activating")&&e.respawningCore==null)e.phase="failed";
  e.stageDeadline=tag.getLong("StageDeadline");
  e.lastNearby=tag.getLong("LastNearby");
  if(tag.contains("BattleLimit",Tag.TAG_INT)){
   int limit=tag.getInt("BattleLimit");
   e.battleLimit=limit<=0?0:Math.clamp(limit,ModuleFields.ARENA_BATTLE_MIN_TICKS,ModuleFields.ARENA_BATTLE_MAX_TICKS);
   if(e.battleLimit>0&&(e.phase.equals("ready")||e.phase.equals("countdown")))e.battleDeadline=Math.max(0,tag.getLong("BattleDeadline"));
  }
  if(tag.contains("BattleTitles",Tag.TAG_COMPOUND))e.battleTitles=tag.getCompound("BattleTitles");
  e.shownBattleTitles=tag.getInt("ShownBattleTitles");
  e.summonsUsed=Math.clamp(tag.getInt("SummonsUsed"),0,SUMMON_LIMIT);
  e.defeatPending=tag.getBoolean("DefeatPending");
  e.stageDefeated=tag.getBoolean("StageDefeated");
  e.abandoning=tag.getBoolean("Abandoning");
  if(tag.contains("StageBoundary",10)){
   var boundary=VirtualModule.load(tag.getCompound("StageBoundary"));
   if(boundary.type().equals("arena")&&boundary.arenaCenter()!=null)e.boundary=boundary;
  }
  if(e.stage!=null&&e.boundary==null)e.abandoning=true;
  if(tag.contains("RespawnAnnouncement",8))e.respawnAnnouncement=readAnnouncement(tag,"RespawnAnnouncement");
  e.defeatAnnouncement=readAnnouncement(tag,"DefeatAnnouncement");
  e.blueprintReferences=tag.getCompound("BlueprintReferences");
  for(var value:tag.getList("Summons",11)){
   if(e.summons.size()>=SUMMON_LIMIT)break;
   if(((IntArrayTag)value).size()==4)e.summons.add(NbtUtils.loadUUID(value));
  }
 }
 private static String readAnnouncement(CompoundTag tag,String key){
  String message=tag.getString(key);
  return message.length()<=ModuleFields.ARENA_ANNOUNCEMENT_LENGTH&&message.codePoints().noneMatch(Character::isISOControl)?message:"";
 }
 @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){var list=new ListTag();for(var e:entries.values()){
  var t=new CompoundTag();t.putUUID("Id",e.id);t.putUUID("Creature",e.creature);t.putUUID("Module",e.module);t.putUUID("TemplateModule",e.templateModule);t.putString("CreatureName",e.name);t.putString("Dimension",e.dimension.toString());t.putDouble("X",e.center.x);t.putDouble("Y",e.center.y);t.putDouble("Z",e.center.z);t.put("Template",e.template);t.put("Progress",e.progress);t.putInt("Delay",e.delay);t.putInt("Remaining",e.remaining);t.putString("Phase",e.phase);if(e.display!=null)t.putUUID("Display",e.display);var bodies=new ListTag();for(var id:e.oldBodies){var body=new CompoundTag();body.putUUID("Id",id);bodies.add(body);}t.put("OldBodies",bodies);var created=new ListTag();for(var id:e.newBodies){var body=new CompoundTag();body.putUUID("Id",id);created.add(body);}t.put("NewBodies",created);list.add(t);
  saveEncounter(e,t);
 }tag.put("Entries",list);return tag;}
 public static ArenaRespawns load(CompoundTag tag, HolderLookup.Provider lookup) {
  var data = new ArenaRespawns();
  long totalTemplateBytes = 0;
  int rejectedTemplates = 0;
  var phases = Set.of("ready", "countdown", "resetting", "building", "activating", "failed");
  for (var item : tag.getList("Entries", Tag.TAG_COMPOUND)) {
   if (data.entries.size() >= LIMIT) break;
   var row = (CompoundTag) item;
   if (!row.hasUUID("Id") || !row.hasUUID("Creature") || !row.hasUUID("Module") || !row.hasUUID("TemplateModule")) continue;
   var id = row.getUUID("Id");
   if (data.entries.containsKey(id)) continue;
   var dimension = ResourceLocation.tryParse(row.getString("Dimension"));
   var center = new Vec3(row.getDouble("X"), row.getDouble("Y"), row.getDouble("Z"));
   var template = row.getCompound("Template");
   if (dimension == null || !Double.isFinite(center.lengthSqr())
       || Math.abs(center.x) > 30000000 || Math.abs(center.z) > 30000000 || template.isEmpty()) continue;
   int templateBytes = template.sizeInBytes();
   if (!ArenaRespawnLimits.fitsSavedArenaTemplate(totalTemplateBytes, templateBytes)) {
    rejectedTemplates++;
    continue;
   }

   var entry = new Entry();
   entry.id = id;
   entry.creature = row.getUUID("Creature");
   entry.module = row.getUUID("Module");
   entry.templateModule = row.getUUID("TemplateModule");
   entry.name = row.getString("CreatureName");
   if (entry.name.codePointCount(0, entry.name.length()) > 64 || entry.name.codePoints().anyMatch(Character::isISOControl)) entry.name = "";
   entry.dimension = dimension;
   entry.center = center;
   entry.template = template;
   entry.progress = row.getCompound("Progress");
   entry.delay = Math.clamp(row.getInt("Delay"), 100, 72000);
   entry.remaining = Math.clamp(row.getInt("Remaining"), 0, entry.delay);
   var phase = row.getString("Phase");
   entry.phase = phases.contains(phase) ? phase : "failed";
   if (row.hasUUID("Display")) entry.display = row.getUUID("Display");
   for (var body : row.getList("OldBodies", Tag.TAG_COMPOUND)) {
    if (entry.oldBodies.size() >= ArenaRespawnLimits.PARTS) break;
    var bodyTag = (CompoundTag) body;
    if (bodyTag.hasUUID("Id")) entry.oldBodies.add(bodyTag.getUUID("Id"));
   }
   for (var body : row.getList("NewBodies", Tag.TAG_COMPOUND)) {
    if (entry.newBodies.size() >= ArenaRespawnLimits.PARTS) break;
    var bodyTag = (CompoundTag) body;
    if (bodyTag.hasUUID("Id")) entry.newBodies.add(bodyTag.getUUID("Id"));
   }
   data.loadEncounter(entry, row);
   data.entries.put(entry.id, entry);
   totalTemplateBytes += templateBytes;
  }
  ArenaRespawnLimits.warnSkippedTemplates("sablelife_arena_respawns", rejectedTemplates);
  return data;
 }
}
