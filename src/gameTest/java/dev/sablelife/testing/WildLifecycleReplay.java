package dev.sablelife.testing;

import com.google.gson.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablelife.*;
import dev.sablelife.combat.EntityHitbox;
import dev.sablelife.compat.StructureRemovalApi;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.ownership.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

/** Four real dedicated-server boots. Explicit opt-in; no production source or player world edits. */
@EventBusSubscriber(modid="sablelife_tests")
public final class WildLifecycleReplay {
    private static final String STAGE=System.getProperty("sablelife.wildLifecycleStage","prepare");
    private static final String[] FILES={"life_absence","structure_absence","life_lifetime","structure_lifetime","life_manual","structure_manual"};
    private static final Set<Long> forced=new HashSet<>();
    private static final List<Map<String,Object>> evidence=new ArrayList<>();
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static MinecraftServer server;private static ServerLevel level;private static WildEncounters wild;private static FakePlayer actor;
    private static boolean initialized,done,left,scanStarted;private static int tick,fullAt=-1,leftAt;private static long waitUntil;
    private static JsonObject manifest;private static final Map<UUID,Vec3> firstPositions=new HashMap<>();
    private static Path directory(){return server.getServerDirectory().resolve("lifecycle-results");}
    private static void require(boolean okay,String message){if(!okay)throw new IllegalStateException(message);}
    private static void record(String event,Object detail){var row=new LinkedHashMap<String,Object>();row.put("event",event);row.put("serverTick",server.getTickCount());row.put("gameTime",level.getGameTime());row.put("detail",detail);evidence.add(row);System.out.println("WILD_LIFECYCLE: "+event+" "+GSON.toJson(detail));}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("sablelife.wildLifecycle")||done)return;
        server=event.getServer();level=server.overworld();
        try{
            if(!initialized){initialized=true;setup();return;}
            tick++;
            switch(STAGE){case "prepare"->prepare();case "unloaded"->unloaded();case "cleanup"->cleanup();case "verify"->verify();default->throw new IllegalArgumentException(STAGE);}
            if(tick>3000)throw new IllegalStateException("Stage timed out: "+STAGE);
        }catch(Throwable failure){failure.printStackTrace();try{finish(false,failure.toString());}catch(Exception write){write.printStackTrace();server.halt(false);}}
    }
    private static void setup()throws Exception{
        Files.createDirectories(directory());wild=WildEncounters.get(server);server.tickRateManager().setTickRate(20);
        var rules=level.getGameRules();rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,server);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);rules.getRule(GameRules.RULE_RANDOMTICKING).set(0,server);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(6000);
        ((ModConfigSpec.BooleanValue)SableLifeServerConfig.SPEC.getValues().get("enableWildEncounters")).set(true);
        ((ModConfigSpec.IntValue)SableLifeServerConfig.SPEC.getValues().get("maximumNearbyWildEncounters")).set(64);
        record("boot",Map.of("stage",STAGE,"method","Actual server process; normal 20 TPS; controlled exported blueprints; fake server-level player for presence, no rendering/network client"));
        if(STAGE.equals("prepare")){
            require(wild.configuredFiles(level).isEmpty(),"Prepare requires a new isolated world");
            forceArea(4096,4096,6,true);
            actor=TestActors.creative(level);actor.setNoGravity(true);actor.setPos(4096.5,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,4096,4096)+2,4096.5);level.addNewPlayer(actor);
            var templates=new CompoundTag[]{template(true),template(false)};
            for(int i=0;i<FILES.length;i++){
                var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,FILES[i]);Files.createDirectories(path.getParent());NbtIo.writeCompressed(templates[i%2],path);
                wild.loadBlueprint(level,"",FILES[i],i%2==0);configure(i,true);
            }
            manifest=new JsonObject();manifest.addProperty("stage",STAGE);
        }else{
            manifest=JsonParser.parseString(Files.readString(directory().resolve("manifest.json"))).getAsJsonObject();
            require(wild.configuredFiles(level).size()==6,"Saved rules did not survive process restart");
            require(count()==(STAGE.equals("verify")?0:6),"Saved current count did not survive restart: "+count());
            require(Arrays.stream(FILES).noneMatch(f->wild.snapshot(level,f).getBoolean("Enabled")),"Disabled automatic rules were not preserved");
            if(STAGE.equals("cleanup")){
                require(wild.snapshot(level,FILES[4]).getBoolean("Clearing")&&wild.snapshot(level,FILES[5]).getBoolean("Clearing"),"Unloaded clear requests did not survive restart");
                require(row(FILES[0]).getList("Encounters",10).getCompound(0).getBoolean("AbsenceDue"),"Creature absence deadline lost on restart");
                require(row(FILES[1]).getList("Encounters",10).getCompound(0).getBoolean("AbsenceDue"),"Structure absence deadline lost on restart");
            }
            record("saved_state_restored",state());
        }
    }
    private static void configure(int index,boolean enabled)throws Exception{
        wild.configure(level,FILES[index],0,index/2==1?60:0,10,enabled,false,FactionRegistry.WILDS,
                new WildEncounters.Conditions(true,false,false,Set.of(),index/2==0?30:0),1,false,new WildEncounters.Density(1,8,24,48));
    }
    private static CompoundTag template(boolean life)throws Exception{
        var p=new BlockPos(life?4140:4160,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,4140,4096)+4,4096);
        level.setBlockAndUpdate(p,(life?SableLife.HEART.get():Blocks.IRON_BLOCK).defaultBlockState());level.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=(ServerSubLevel)SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));
        if(life){
            var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.configure(100,40);
            var movement=core.modules().add("linear_movement");movement.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.EAST)));movement.edit("trigger",0,"");movement.edit("linearMode",1,"");movement.edit("moveAim",0,"");movement.edit("moveGravity",1,"");movement.edit("moveDistance",4,"");movement.edit("travelSpeed",1,"");movement.edit("chargeTime",0,"");movement.edit("interval",40,"");
            var box=core.modules().add("entity_hitbox");box.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.UP)));core.modules().changed();
        }
        var pos=body.logicalPose().position();var tag=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(level,new Vec3(pos.x(),pos.y(),pos.z()),List.of(body.getUniqueId())).save();
        StructureRemovalApi.remove(level,body);return tag;
    }
    private static CompoundTag row(String file){return wild.save(new CompoundTag(),level.registryAccess()).getList("Rules",10).stream().map(CompoundTag.class::cast).filter(t->t.getString("File").equals(file)).findFirst().orElseThrow();}
    private static int count(){return Arrays.stream(FILES).mapToInt(f->wild.snapshot(level,f).getInt("ActiveCount")).sum();}
    private static Map<String,Object> state(){var out=new LinkedHashMap<String,Object>();out.put("tracked",count());out.put("loadedParts",SubLevelContainer.getContainer(level).getAllSubLevels().size());out.put("loadedCores",LoadedCoreIndex.all(level).size());out.put("pendingCleanup",LifeLifecycle.get(server).pendingCount());out.put("receivers",java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(e->e instanceof EntityHitbox).count());return out;}
    private static void prepare()throws Exception{
        if(fullAt<0){
            if(tick%20==0)record("automatic_spawn_progress",state());
            if(count()!=6){require(tick<400,"Automatic spawning did not fill the six rules");return;}
            fullAt=tick;for(int i=0;i<6;i++)configure(i,false);
            for(var core:LoadedCoreIndex.all(level))firstPositions.put(core.creatureId(),SableBodyProbe.toWorld(level,core.getBlockPos().getCenter()));
            record("automatic_spawns_complete",state());return;
        }
        if(!left&&tick>=fullAt+160){
            double moved=0;for(var core:LoadedCoreIndex.all(level)){
                var first=firstPositions.get(core.creatureId());var current=SableBodyProbe.toWorld(level,core.getBlockPos().getCenter());if(first!=null)moved+=Math.hypot(current.x-first.x,current.z-first.z);
                Files.writeString(directory().resolve("activity-"+core.creatureId()+".snbt"),core.modules().snapshot(core,true).toString());
            }
            require(moved>1,"Life fixtures did not autonomously move");record("autonomous_activity",Map.of("totalDisplacement",moved,"state",state()));
            captureManifest();actor.setPos(12000,100,12000);level.getChunkSource().move(actor);left=true;leftAt=tick;
            forceArea(4096,4096,6,false);record("player_left_and_chunk_tickets_removed",state());
        }
        if(left&&tick%20==0){
            if(allUnloaded()){
                require(count()==6,"Tracking lost during unload");require(LoadedCoreIndex.all(level).isEmpty(),"Core index retained unloaded cores");
                require((long)state().get("receivers")==0,"Entity receivers survived unload");
                record("actual_unload_confirmed",state());captureManifest();finish(true,"");
            }else if(tick-leftAt>1000)throw new IllegalStateException("World chunks or Sable bodies did not unload naturally: "+state());
        }
    }
    private static void captureManifest()throws Exception{
        var cases=new JsonArray();var container=SubLevelContainer.getContainer(level);
        for(int i=0;i<6;i++){
            var encounter=row(FILES[i]).getList("Encounters",10).getCompound(0);var id=encounter.getUUID("Id");var one=new JsonObject();one.addProperty("file",FILES[i]);one.addProperty("life",i%2==0);one.addProperty("id",id.toString());one.addProperty("expires",encounter.getLong("Expires"));
            var p=BlockPos.of(encounter.getLong("LastPosition"));one.add("position",GSON.toJsonTree(new int[]{p.getX(),p.getY(),p.getZ()}));
            var ids=new ArrayList<UUID>();if(i%2==0)ids.addAll(BodyOwnership.get(level).bodyIds(level,id));else for(var n:encounter.getList("Bodies",11))ids.add(NbtUtils.loadUUID(n));
            // After unload, retain the previously captured part/plot identities.
            if(ids.isEmpty()&&manifest.has("cases")){
                for(var old:manifest.getAsJsonArray("cases"))if(old.getAsJsonObject().get("file").getAsString().equals(FILES[i])){one.add("bodies",old.getAsJsonObject().get("bodies"));one.add("plotBlocks",old.getAsJsonObject().get("plotBlocks"));}
            }else {
                one.add("bodies",GSON.toJsonTree(ids.stream().map(UUID::toString).toList()));var blocks=new JsonArray();
                for(var bodyId:ids){var body=container.getSubLevel(bodyId);if(body!=null){var c=body.getPlot().getCenterBlock();blocks.add(GSON.toJsonTree(new int[]{c.getX(),c.getY(),c.getZ()}));blocks.add(GSON.toJsonTree(new int[]{c.getX()+1,c.getY(),c.getZ()}));}}
                if(blocks.isEmpty()&&manifest.has("cases"))for(var old:manifest.getAsJsonArray("cases"))if(old.getAsJsonObject().get("file").getAsString().equals(FILES[i]))blocks=old.getAsJsonObject().getAsJsonArray("plotBlocks");
                one.add("plotBlocks",blocks);
            }
            require(one.has("bodies")&&!one.getAsJsonArray("bodies").isEmpty(),"Cannot capture body identity for "+FILES[i]);cases.add(one);
        }
        manifest.add("cases",cases);manifest.addProperty("savedTime",level.getGameTime());Files.writeString(directory().resolve("manifest.json"),GSON.toJson(manifest));
    }
    private static boolean allUnloaded(){var container=SubLevelContainer.getContainer(level);for(var c:manifest.getAsJsonArray("cases")){
        var one=c.getAsJsonObject();var p=position(one);if(level.getChunkSource().hasChunk(p.getX()>>4,p.getZ()>>4))return false;
        for(var id:one.getAsJsonArray("bodies"))if(container.getSubLevel(UUID.fromString(id.getAsString()))!=null)return false;
    }return true;}
    private static void unloaded()throws Exception{
        if(tick==20){require(allUnloaded(),"Boot unexpectedly loaded test bodies");require(count()==6,"Tracked objects lost before deadlines");record("restart_unloaded_tracking",state());}
        long expiry=0;for(var c:manifest.getAsJsonArray("cases"))expiry=Math.max(expiry,c.getAsJsonObject().get("expires").getAsLong());
        boolean due=row(FILES[0]).getList("Encounters",10).getCompound(0).getBoolean("AbsenceDue")&&row(FILES[1]).getList("Encounters",10).getCompound(0).getBoolean("AbsenceDue");
        if(level.getGameTime()>expiry+20&&due){
            require(allUnloaded()&&count()==6,"Expiry discarded unloaded tracked objects");require(wild.clearCurrent(level,FILES[4])&&wild.clearCurrent(level,FILES[5]),"Cannot queue unloaded manual cleanup");
            record("deadlines_and_manual_requests_persisted_while_unloaded",state());finish(true,"");
        }
    }
    private static BlockPos position(JsonObject c){var a=c.getAsJsonArray("position");return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private static void reload(){for(var c:manifest.getAsJsonArray("cases")){var p=position(c.getAsJsonObject());forceArea(p.getX(),p.getZ(),2,true);}record("original_world_chunks_reloaded",state());}
    private static boolean empty(){return count()==0&&SubLevelContainer.getContainer(level).getAllSubLevels().isEmpty()&&LoadedCoreIndex.all(level).isEmpty()&&LifeLifecycle.get(server).pendingCount()==0&&(long)state().get("receivers")==0;}
    private static void cleanup()throws Exception{
        if(tick==20){require(allUnloaded(),"Cleanup must begin from real unloaded state");reload();}
        if(!scanStarted&&tick>60&&empty()){
            audit();server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"sablelife cleanup scan all");record("read_only_residue_scan_started",state());scanStarted=true;waitUntil=tick+100;
        }
        if(scanStarted&&tick>=waitUntil){record("cleanup_complete",state());finish(true,"");}
    }
    private static void verify()throws Exception{if(tick==20)reload();if(tick==200){require(empty(),"Cleanup result did not survive a further reboot: "+state());audit();record("clean_state_survived_reboot",state());finish(true,"");}}
    private static void audit(){
        var container=(dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer)SubLevelContainer.getContainer(level);
        require(empty(),"Cleanup queues or live objects remain");
        for(var c:manifest.getAsJsonArray("cases")){
            var one=c.getAsJsonObject();for(var id:one.getAsJsonArray("bodies")){var uuid=UUID.fromString(id.getAsString());require(container.getSubLevel(uuid)==null&&container.getHoldingChunkMap().getHoldingSubLevel(uuid)==null,"Native body/holding residue: "+uuid);}
            if(one.get("life").getAsBoolean())require(!BodyOwnership.get(level).hasOwned(UUID.fromString(one.get("id").getAsString())),"Life ownership residue");
            for(var b:one.getAsJsonArray("plotBlocks")){var a=b.getAsJsonArray();var p=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());require(level.getBlockState(p).isAir(),"Old plot block remains: "+p);}
            require(!wild.snapshot(level,one.get("file").getAsString()).getBoolean("Clearing"),"Manual cleanup flag remained set");
        }
        long rewards=java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(e->e instanceof ItemEntity||e instanceof ExperienceOrb).count();require(rewards==0,"Cleanup emitted item/XP entities");
        actor=actor==null?TestActors.creative(level):actor;
        for(var c:manifest.getAsJsonArray("cases")){
            var old=position(c.getAsJsonObject());var p=new BlockPos(old.getX(),level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,old.getX(),old.getZ()),old.getZ());actor.setPos(p.getX()+.5,p.getY()+2,p.getZ()+.5);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS));
            var use=new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(p.below().getCenter().add(0,.5,0),Direction.UP,p.below(),false));
            var placed=actor.getMainHandItem().useOn(use);
            boolean atExpected=level.getBlockState(p).is(Blocks.GLASS);
            if(!atExpected&&level.getBlockState(p.below()).is(Blocks.GLASS)) {
                record("placement_replaced_natural_support",Map.of("position",p.toString(),"result",placed.toString()));
                // A replaceable natural surface (for example water or tall grass) is itself
                // the placement destination. This still proves native item placement works.
                atExpected=true;
            }
            require(placed.consumesAction()&&atExpected,"Cannot build at former encounter location: "+p+", result="+placed+", actual="+level.getBlockState(p)+", below="+level.getBlockState(p.below()));
            require(actor.gameMode.destroyBlock(p)&&level.getBlockState(p).isAir(),"Cannot remove newly placed block: "+p);
        }
        record("audit_no_bodies_ownership_receivers_rewards_or_plot_blocks_and_building_works",state());
    }
    private static void forceArea(int x,int z,int radius,boolean on){for(int cx=(x>>4)-radius;cx<=(x>>4)+radius;cx++)for(int cz=(z>>4)-radius;cz<=(z>>4)+radius;cz++){var key=ChunkPos.asLong(cx,cz);if(on){level.setChunkForced(cx,cz,true);level.getChunk(cx,cz);forced.add(key);}else {level.setChunkForced(cx,cz,false);forced.remove(key);}}}
    private static void finish(boolean success,String failure)throws Exception{if(done)return;done=true;Files.writeString(directory().resolve(STAGE+".json"),GSON.toJson(Map.of("success",success,"failure",failure,"stage",STAGE,"evidence",evidence,"finalState",state())));Files.writeString(directory().resolve(STAGE+"-wild.snbt"),wild.save(new CompoundTag(),level.registryAccess()).toString());System.out.println("WILD_LIFECYCLE_COMPLETE: "+STAGE+" success="+success+" "+failure);server.halt(false);}
}
