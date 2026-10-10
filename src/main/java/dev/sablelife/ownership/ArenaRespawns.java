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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;

/** World-owned templates and resumable jobs survive deletion of the original core. */
public final class ArenaRespawns extends SavedData {
 public interface Build {
  AABB bounds();String step(ServerLevel level);CompoundTag save();float fraction();HeartBlockEntity heart(ServerLevel level);UUID module(UUID original);Set<UUID> bodies();void restoreApprovals(HeartBlockEntity heart);void captureOwnership(HeartBlockEntity heart);
 }
 private static final int LIMIT=32;
 private final LinkedHashMap<UUID,Entry> entries=new LinkedHashMap<>();
 private int turn;
 private static final class Entry {
  UUID id,creature,module,templateModule,display;
  ResourceLocation dimension;Vec3 center;CompoundTag template,progress=new CompoundTag();
  int delay,remaining;String phase="ready";Set<UUID> oldBodies=new HashSet<>(),newBodies=new HashSet<>();
  transient Build build;transient int shown=-1;
 }
 public static boolean available(){return ModList.get().getModContainerById("sable_schematic_api").map(m->m.getModInfo().getVersion().toString().equals("1.0.1")).orElse(false);}
 public static ArenaRespawns get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ArenaRespawns::new,ArenaRespawns::load),"sablelife_arena_respawns");}
 private Entry find(HeartBlockEntity heart,UUID module){return entries.values().stream().filter(e->e.creature.equals(heart.creatureId())&&e.module.equals(module)).findFirst().orElse(null);}
 public int count(){return entries.size();}
 public static boolean record(ServerPlayer player,HeartBlockEntity heart,VirtualModule module){
  if(!available()||!player.isCreative()||!player.canUseGameMasterBlocks()||!heart.canAccessEditor(player)||heart.isControlCore()||module==null||!module.type().equals("arena")||module.arenaCenter()==null||!module.arenaDimension().equals(player.level().dimension().location().toString())||heart.health().state()==HeartState.DYING||heart.health().state()==HeartState.DEAD)return false;
  var data=get(player.server);var existing=data.find(heart,module.id());
  if(existing==null&&(data.entries.size()>=LIMIT||data.entries.values().stream().anyMatch(e->e.creature.equals(heart.creatureId()))))return false;
  try{
   var template=PhotomancyRespawnBridge.capture(heart,module.arenaCenter());
   long total=template.sizeInBytes();for(var old:data.entries.values())if(old!=existing)total+=old.template.sizeInBytes();
   ArenaRespawnLimits.check("world_templates",total,ArenaRespawnLimits.WORLD_TEMPLATE_BYTES);
   var e=new Entry();e.id=existing==null?UUID.randomUUID():existing.id;e.creature=heart.creatureId();e.module=module.id();e.templateModule=module.id();e.dimension=player.level().dimension().location();e.center=module.arenaCenter();e.template=template;e.delay=(int)module.value("arenaRespawnDelay");
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
  if(!available()||!(heart.getLevel() instanceof ServerLevel level))return;
  var data=get(level.getServer());
  for(var e:data.entries.values())if(e.creature.equals(heart.creatureId())&&e.phase.equals("ready")){
   var module=heart.modules().find(e.module);
   if(module==null||!module.enabled()||module.value("arenaRespawnEnabled")!=1||module.arenaCenter()==null||!module.arenaDimension().equals(e.dimension.toString()))continue;
   e.center=module.arenaCenter();e.delay=(int)module.value("arenaRespawnDelay");e.remaining=e.delay;e.phase="countdown";
   var root=dev.ryanhcode.sable.Sable.HELPER.getContaining(level,heart.getBlockPos());
   e.oldBodies.addAll(BodyOwnership.get(level).bodyIds(level,heart.creatureId()));
   if(root!=null)for(var body:dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(root))e.oldBodies.add(body.getUniqueId());
   data.setDirty();
  }
 }
 public static void release(HeartBlockEntity heart){if(!(heart.getLevel() instanceof ServerLevel level))return;var data=get(level.getServer());for(var e:List.copyOf(data.entries.values()))if(e.creature.equals(heart.creatureId()))data.remove(level.getServer(),e);}
 private static boolean loaded(ServerLevel level,AABB bounds){
  if(!Double.isFinite(bounds.getSize())||bounds.getXsize()>512||bounds.getZsize()>512||bounds.minY<level.getMinBuildHeight()||bounds.maxY>level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(bounds))return false;
  int x0=((int)Math.floor(bounds.minX))>>4,x1=((int)Math.floor(bounds.maxX))>>4,z0=((int)Math.floor(bounds.minZ))>>4,z1=((int)Math.floor(bounds.maxZ))>>4;
  if((long)(x1-x0+1)*(z1-z0+1)>256)return false;
  for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!level.hasChunk(x,z))return false;return true;
 }
 public static void beforeTick(ServerTickEvent.Pre event){var server=event.getServer();for(var e:get(server).entries.values())if(!e.newBodies.isEmpty()){
  var level=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,e.dimension));if(level!=null)ArenaBuildTicks.hold(level,e.id,e.newBodies);
 }}
 public static void tick(ServerTickEvent.Post event){get(event.getServer()).advance(event.getServer());}
 public void advance(MinecraftServer server){
  int buildSlots=1;var list=new ArrayList<>(entries.values());if(list.isEmpty())return;
  Collections.rotate(list,Math.floorMod(turn++,list.size()));
  for(var e:list){
   var level=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,e.dimension));if(level==null)continue;
   if(e.phase.equals("ready")){if(e.display!=null)hide(level,e);continue;}
   if(!available()){display(level,e,"missing",0);continue;}
   try{
    if(e.build==null)e.build=PhotomancyRespawnBridge.create(e.template,e.progress,e.center);
    if(!loaded(level,e.build.bounds())){hide(level,e);continue;}
    if(e.phase.equals("failed")){display(level,e,"failed",0);continue;}
    if(e.remaining>0){e.remaining--;setDirty();display(level,e,"countdown",1-e.remaining/(float)Math.max(1,e.delay));continue;}
    if(!LifeLifecycle.get(server).finished(e.creature)){display(level,e,"cleanup",0);continue;}
    var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
    if(container==null||e.oldBodies.stream().anyMatch(id->container.getSubLevel(id)!=null)){display(level,e,"cleanup",0);continue;}
    if(buildSlots--<=0)continue;
    e.phase="building";var status=e.build.step(level);e.progress=e.build.save();e.newBodies=e.build.bodies();setDirty();
    display(level,e,"building",e.build.fraction());
    if(status.equals("FAILED")||status.equals("WAITING_FOR_MATERIALS")){e.phase="failed";setDirty();continue;}
    if(status.equals("DONE")){
     dev.sablelife.compat.sable.SableBodyProbe.invalidate(level);
     var heart=e.build.heart(level);var moduleId=e.build.module(e.templateModule);
     var module=heart==null||moduleId==null?null:heart.modules().find(moduleId);
     if(module==null){e.phase="failed";setDirty();continue;}
     e.build.restoreApprovals(heart);
     if(!heart.activateForRespawn()){e.phase="failed";setDirty();continue;}
     e.build.captureOwnership(heart);
     module.arenaCenter(e.center,e.dimension.toString());
     e.creature=heart.creatureId();e.module=moduleId;e.phase="ready";e.progress=new CompoundTag();e.build=null;e.oldBodies.clear();e.newBodies.clear();hide(level,e);heart.modules().changed();heart.setChanged();setDirty();
    }
   }catch(RuntimeException failure){if(e.build!=null){e.progress=e.build.save();e.newBodies=e.build.bodies();}e.phase="failed";setDirty();com.mojang.logging.LogUtils.getLogger().error("Arena respawn {} stopped without retrying partial placement",e.id,failure);}
  }
 }
 private void hide(ServerLevel level,Entry e){if(e.display!=null){var entity=level.getEntity(e.display);if(entity!=null){entity.discard();e.display=null;setDirty();}}e.shown=-1;}
 private void display(ServerLevel level,Entry e,String phase,float fraction){
  if(!level.hasChunkAt(BlockPos.containing(e.center)))return;
  int seconds=(e.remaining+19)/20,filled=Math.clamp((int)(fraction*20),0,20),key=Objects.hash(phase,seconds,filled);
  if(e.shown==key&&e.display!=null&&level.getEntity(e.display)!=null)return;
  Display.TextDisplay text=e.display==null?null:level.getEntity(e.display) instanceof Display.TextDisplay t?t:null;
  if(text==null){text=EntityType.TEXT_DISPLAY.create(level);if(text==null)return;if(e.display!=null)text.setUUID(e.display);text.setPos(e.center.add(0,2.5,0));text.setNoGravity(true);text.addTag("sablelife_arena_respawn");text.getPersistentData().putUUID("SableLifeArenaRecord",e.id);if(!level.addFreshEntity(text))return;e.display=text.getUUID();setDirty();}
  var label=Component.translatable("arena_respawn.sablelife."+phase,seconds).append("\n").append(Component.literal("■".repeat(filled)+"□".repeat(20-filled)).withStyle(net.minecraft.ChatFormatting.YELLOW));
  var access=(dev.sablelife.mixin.RespawnTextDisplayAccess)(Object)text;access.sablelife$text(label);access.sablelife$background(0xAA101018);access.sablelife$flags((byte)3);access.sablelife$lineWidth(256);((dev.sablelife.mixin.RespawnDisplayAccess)(Object)text).sablelife$billboard(Display.BillboardConstraints.CENTER);e.shown=key;
 }
 private void remove(MinecraftServer server,Entry e){
  var level=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,e.dimension));if(level!=null)hide(level,e);
  if(level!=null){ArenaBuildTicks.release(level,e.newBodies);removeDisplays(level,e.id);var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);if(container!=null)for(var id:e.newBodies)if(container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body&&!body.isRemoved())dev.sablelife.compat.StructureRemovalApi.remove(level,body);}
  entries.remove(e.id);setDirty();
 }
 public static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){
  if(event.getLevel() instanceof ServerLevel level&&event.getEntity() instanceof Display.TextDisplay text&&text.getPersistentData().hasUUID("SableLifeArenaRecord")){
   var entry=get(level.getServer()).entries.get(text.getPersistentData().getUUID("SableLifeArenaRecord"));if(entry==null||entry.phase.equals("ready"))event.setCanceled(true);
  }
 }
 private static void removeDisplays(ServerLevel level,UUID record){var matches=new ArrayList<Display.TextDisplay>();for(var entity:level.getAllEntities())if(entity instanceof Display.TextDisplay text&&text.getPersistentData().hasUUID("SableLifeArenaRecord")&&text.getPersistentData().getUUID("SableLifeArenaRecord").equals(record))matches.add(text);matches.forEach(Entity::discard);}
 private boolean cancelOrphan(ServerLevel level,Vec3 point){Display.TextDisplay nearest=null;double distance=64*64;for(var entity:level.getAllEntities())if(entity instanceof Display.TextDisplay text&&text.getPersistentData().hasUUID("SableLifeArenaRecord")&&!entries.containsKey(text.getPersistentData().getUUID("SableLifeArenaRecord"))&&text.position().distanceToSqr(point)<distance){nearest=text;distance=text.position().distanceToSqr(point);}if(nearest==null)return false;removeDisplays(level,nearest.getPersistentData().getUUID("SableLifeArenaRecord"));return true;}
 public static void commands(RegisterCommandsEvent event){event.getDispatcher().register(net.minecraft.commands.Commands.literal("sablelife_respawn").requires(s->s.hasPermission(2)).then(net.minecraft.commands.Commands.literal("cancel").executes(ctx->{var source=ctx.getSource();var data=get(source.getServer());var e=data.entries.values().stream().filter(v->v.dimension.equals(source.getLevel().dimension().location())&&v.center.distanceToSqr(source.getPosition())<64*64).min(Comparator.comparingDouble(v->v.center.distanceToSqr(source.getPosition()))).orElse(null);if(e==null){if(!data.cancelOrphan(source.getLevel(),source.getPosition()))return 0;}else data.remove(source.getServer(),e);source.sendSuccess(()->Component.translatable("arena_respawn.sablelife.cancelled"),false);return 1;})));}
 @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){var list=new ListTag();for(var e:entries.values()){
  var t=new CompoundTag();t.putUUID("Id",e.id);t.putUUID("Creature",e.creature);t.putUUID("Module",e.module);t.putUUID("TemplateModule",e.templateModule);t.putString("Dimension",e.dimension.toString());t.putDouble("X",e.center.x);t.putDouble("Y",e.center.y);t.putDouble("Z",e.center.z);t.put("Template",e.template);t.put("Progress",e.progress);t.putInt("Delay",e.delay);t.putInt("Remaining",e.remaining);t.putString("Phase",e.phase);if(e.display!=null)t.putUUID("Display",e.display);var bodies=new ListTag();for(var id:e.oldBodies){var body=new CompoundTag();body.putUUID("Id",id);bodies.add(body);}t.put("OldBodies",bodies);var created=new ListTag();for(var id:e.newBodies){var body=new CompoundTag();body.putUUID("Id",id);created.add(body);}t.put("NewBodies",created);list.add(t);
 }tag.put("Entries",list);return tag;}
 public static ArenaRespawns load(CompoundTag tag,HolderLookup.Provider lookup){var data=new ArenaRespawns();long totalTemplateBytes=0;for(var item:tag.getList("Entries",10)){
  if(data.entries.size()>=LIMIT)break;var t=(CompoundTag)item;if(!t.hasUUID("Id")||!t.hasUUID("Creature")||!t.hasUUID("Module")||!t.hasUUID("TemplateModule"))continue;
  var dim=ResourceLocation.tryParse(t.getString("Dimension"));var center=new Vec3(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"));var template=t.getCompound("Template");
  if(dim==null||!Double.isFinite(center.lengthSqr())||Math.abs(center.x)>30000000||Math.abs(center.z)>30000000||template.isEmpty()||template.sizeInBytes()>ArenaRespawnLimits.TEMPLATE_BYTES)continue;
  if(totalTemplateBytes+template.sizeInBytes()>ArenaRespawnLimits.WORLD_TEMPLATE_BYTES||data.entries.containsKey(t.getUUID("Id")))continue;
  totalTemplateBytes+=template.sizeInBytes();
  var e=new Entry();e.id=t.getUUID("Id");e.creature=t.getUUID("Creature");e.module=t.getUUID("Module");e.templateModule=t.getUUID("TemplateModule");e.dimension=dim;e.center=center;e.template=template;e.progress=t.getCompound("Progress");e.delay=Math.clamp(t.getInt("Delay"),20,72000);e.remaining=Math.clamp(t.getInt("Remaining"),0,e.delay);e.phase=Set.of("ready","countdown","building","failed").contains(t.getString("Phase"))?t.getString("Phase"):"failed";if(t.hasUUID("Display"))e.display=t.getUUID("Display");for(var b:t.getList("OldBodies",10)){if(e.oldBodies.size()>=ArenaRespawnLimits.PARTS)break;var v=(CompoundTag)b;if(v.hasUUID("Id"))e.oldBodies.add(v.getUUID("Id"));}for(var b:t.getList("NewBodies",10)){if(e.newBodies.size()>=ArenaRespawnLimits.PARTS)break;var v=(CompoundTag)b;if(v.hasUUID("Id"))e.newBodies.add(v.getUUID("Id"));}data.entries.put(e.id,e);
 }return data;}
}
