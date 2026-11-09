package dev.sablelife.testing;

import com.google.gson.GsonBuilder;
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
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** Opt-in isolated dedicated-server experiment. Never shipped in the production JAR. */
@EventBusSubscriber(modid="sablelife_tests")
public final class HitboxMsptBenchmark {
    private record Phase(String name,int ticks,int receivers,int size,int populated,boolean moving,boolean aggro){}
    private record Sample(String phase,int tick,double fullMs,double nativeMs,double operationMs){}
    private static final List<Phase> ALL_PHASES=List.of(
            new Phase("WARMUP",200,0,1,0,false,false),new Phase("BASELINE_EMPTY",300,0,1,0,false,false),
            new Phase("FILL_MANUAL_64",150,0,1,0,false,false),new Phase("CORES_64_NO_BOXES",300,0,1,0,false,false),
            new Phase("CORES_8_BOXES_8_SIZE_16",300,8,16,8,false,false),new Phase("CORES_32_BOXES_8_SIZE_16",300,8,16,32,false,false),
            new Phase("CORES_64_BOXES_8_SIZE_1",300,8,1,64,false,false),new Phase("CORES_64_BOXES_8_SIZE_16",300,8,16,64,false,false),
            new Phase("CORES_64_BOXES_32_SIZE_1",300,32,1,64,false,false),new Phase("CORES_64_BOXES_32_SIZE_16",600,32,16,64,false,false),
            new Phase("QUERIES_64_PER_SECOND",300,32,16,64,false,false),
            new Phase("MOVING_64_BOXES_32_SIZE_16",400,32,16,64,true,false),
            new Phase("ZOMBIES_128_AGGRO_OFF",300,32,16,64,false,false),new Phase("ZOMBIES_128_AGGRO_ON",600,32,16,64,false,true),
            new Phase("ARROWS_64_PER_SECOND",400,32,16,64,false,false),new Phase("CLEAN_MANUAL",1200,0,1,0,false,false),
            new Phase("BASELINE_AFTER_MANUAL",200,0,1,0,false,false),
            new Phase("AUTO_DEFAULT_NEARBY_4",400,8,16,0,false,false),new Phase("AUTO_FILL_GLOBAL_64",2400,8,16,0,false,false),
            new Phase("AUTO_CAP_64_STEADY",400,8,16,64,false,false),new Phase("CLEAN_AUTOMATIC",1200,0,1,0,false,false),
            new Phase("BASELINE_AFTER_ALL",300,0,1,0,false,false));
    private static final List<Phase> PHASES=System.getProperty("sablelife.hitboxProfile","full").equals("ai")?
            ALL_PHASES.stream().filter(p->Set.of("WARMUP","BASELINE_EMPTY","FILL_MANUAL_64","CORES_64_BOXES_32_SIZE_16","ZOMBIES_128_AGGRO_OFF","ZOMBIES_128_AGGRO_ON","CLEAN_MANUAL","BASELINE_AFTER_ALL").contains(p.name)).toList():ALL_PHASES;
    private static final int X=4096,Z=4096,R=12;
    private static final String MANUAL="hitbox_benchmark_manual";
    private static final List<String> files=new ArrayList<>();
    private static final List<Sample> samples=new ArrayList<>();
    private static final List<Map<String,Object>> summaries=new ArrayList<>(),activity=new ArrayList<>();
    private static final Map<String,Object> metadata=new LinkedHashMap<>();
    private static final List<Zombie> mobs=new ArrayList<>();
    private static final List<Arrow> arrows=new ArrayList<>();
    private static MinecraftServer server;private static ServerLevel level;private static FakePlayer player;private static WildEncounters wild;
    private static int phaseIndex=-1,age,settle,spawned,queryCount,arrowsCreated;private static long tickStart,phaseStart,gcStart,gcMsStart,heapStart;
    private static boolean done,failed;private static double healthStart;private static final Map<UUID,Vec3> startPositions=new HashMap<>();
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void integer(String key,int value){((ModConfigSpec.IntValue)SableLifeServerConfig.SPEC.getValues().get(key)).set(value);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void pre(ServerTickEvent.Pre event){if(Boolean.getBoolean("sablelife.hitboxMspt")&&!done)tickStart=System.nanoTime();}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void post(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("sablelife.hitboxMspt")||done)return;server=event.getServer();
        try{
            if(phaseIndex<0){setup();next();return;}
            var p=PHASES.get(phaseIndex);double operation=0;
            if(settle>0){settle--;if(settle==0)begin(p);return;}
            if(age==0&&phaseStart==0)begin(p);
            long op=System.nanoTime();
            if(p.name.equals("FILL_MANUAL_64")&&age%2==0&&spawned<64)spawnManual();
            if(p.name.equals("ARROWS_64_PER_SECOND")){
                arrows.removeIf(a->{if(a.isRemoved())return true;if(a.tickCount>80){a.discard();return true;}return false;});
                if(age%20==0)for(var core:cores()){
                    var boxes=core.modules().damageBoxes.entities();if(boxes.isEmpty())continue;
                    var center=boxes.getFirst().getBoundingBox().getCenter();var start=center.add(0,0,12);
                    var arrow=new Arrow(level,start.x,start.y,start.z,new ItemStack(Items.ARROW),new ItemStack(Items.BOW));
                    arrow.setNoGravity(true);arrow.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;arrow.setBaseDamage(2);arrow.shoot(0,0,-1,2,0);
                    if(level.addFreshEntity(arrow)){arrows.add(arrow);arrowsCreated++;}
                }
            }
            if(p.name.equals("QUERIES_64_PER_SECOND")&&age%20==0){
                for(var core:cores()){
                    var center=SableBodyProbe.toWorld(level,core.getBlockPos().getCenter());
                    level.getEntitiesOfClass(EntityHitbox.class,new AABB(center,center).inflate(8));queryCount++;
                }
            }
            operation=(System.nanoTime()-op)/1e6;
            double full=(System.nanoTime()-tickStart)/1e6;
            samples.add(new Sample(p.name,server.getTickCount(),full,server.getTickTimesNanos()[Math.floorMod(server.getTickCount(),100)]/1e6,operation));
            if(age%20==0)observe(p);
            boolean early=false;
            if(p.name.startsWith("CLEAN_")){
                early=age>=100&&tracked()==0&&cores().isEmpty()&&SubLevelContainer.getContainer(level).getAllSubLevels().isEmpty()&&receiverCount()==0&&LifeLifecycle.get(server).pendingCount()==0;
                if(age+1==p.ticks)require(early,"Cleanup timed out: "+state());
            }
            if(p.name.equals("AUTO_DEFAULT_NEARBY_4")&&age>=220){require(tracked()==4,"Default nearby cap did not stop at four: "+tracked());}
            if(p.name.equals("AUTO_FILL_GLOBAL_64"))early=tracked()==64&&receiverCount()==512;
            if(++age>=p.ticks||early){
                if(p.name.equals("FILL_MANUAL_64"))require(tracked()==64&&cores().size()==64,"Manual fill did not make 64 active cores");
                if(p.name.equals("AUTO_FILL_GLOBAL_64"))require(early,"Automatic generation did not fill 64: "+state());
                if(p.name.equals("AUTO_CAP_64_STEADY"))require(tracked()==64&&receiverCount()==512,"Global cap or receiver population drifted");
                if(p.name.startsWith("ZOMBIES_"))require(mobs.size()==128&&mobs.stream().allMatch(m->m.isAlive()&&!m.isRemoved()&&!m.isNoAi()),"Zombie workload lost live AI actors during sampling");
                if(p.name.equals("ZOMBIES_128_AGGRO_ON"))require(mobs.stream().anyMatch(m->m.getTarget() instanceof EntityHitbox)&&hp()<healthStart,"Aggro workload did not acquire receivers and deal damage");
                summarize(p);if(phaseIndex+1==PHASES.size()){report(true,"");done=true;server.halt(false);}else next();
            }
        }catch(Throwable failure){failure.printStackTrace();failed=true;done=true;try{report(false,failure.toString());}catch(Exception ignored){}server.halt(false);}
    }
    private static void setup()throws Exception{
        level=server.overworld();server.tickRateManager().setTickRate(20);var rules=level.getGameRules();
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,server);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);rules.getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);level.setDayTime(18000);
        ((ModConfigSpec.BooleanValue)SableLifeServerConfig.SPEC.getValues().get("enableWildEncounters")).set(true);
        integer("maximumEntityHitboxesPerCreature",32);integer("maximumWildEncounters",64);integer("maximumNearbyWildEncounters",64);integer("wildWorkbenchProtectionRadius",0);
        for(int x=-R;x<=R;x++)for(int z=-R;z<=R;z++){level.setChunkForced((X>>4)+x,(Z>>4)+z,true);level.getChunk((X>>4)+x,(Z>>4)+z);}
        player=TestActors.creative(level);player.setGameMode(GameType.SPECTATOR);player.setNoGravity(true);player.setPos(X+.5,0,Z+.5);player.setYRot(0);level.addNewPlayer(player);
        wild=WildEncounters.get(server);require(wild.configuredFiles(level).isEmpty(),"Benchmark requires a new isolated world");
        writeTemplate(MANUAL,0);wild.loadBlueprint(level,"",MANUAL,true);configure(MANUAL,false,64);
        metadata.put("java",System.getProperty("java.version"));metadata.put("processors",Runtime.getRuntime().availableProcessors());metadata.put("heapMaxBytes",Runtime.getRuntime().maxMemory());
        metadata.put("forcedChunks",(2*R+1)*(2*R+1));metadata.put("coreBlocks",2);metadata.put("targetTps",20);metadata.put("maximumSizePerAxis",16);metadata.put("configuredReceiverCap",32);
        metadata.put("difficulty",level.getDifficulty().toString());metadata.put("profile",System.getProperty("sablelife.hitboxProfile","full"));
        metadata.put("method","Highest ServerTick Pre to lowest Post, normal 20 TPS. Diagnostic observation/reporting excluded from measured window, included in wall time. 64 two-block real Sable cores, same-faction Wilds, no outgoing attack modules. Manual fill uses production Test spawn every two ticks. Final 64-rule stage uses unmodified production automatic scheduler, 10s per-rule success cooldown, 1s retry, 8 candidates, 24-96 block range. Workbench protection disabled in isolated config. No client renderer or real network connection.");
        metadata.put("overlap","All receivers on one core share the same mark and overlap completely; cores initially 20 blocks apart. Size 1 and 16 profiles use identical 64 core structures. AI and arrows have separately labeled phases.");
        System.out.println("HITBOX_SETUP: "+metadata);
    }
    private static void writeTemplate(String file,int count)throws Exception{
        var pos=new BlockPos(X+160,0,Z+160);level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=(ServerSubLevel)SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.configure(1000000,0);
        for(int i=0;i<32;i++)box(core,i,count,16,false);
        var move=core.modules().add("linear_movement");move.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.EAST)));move.edit("enabled",0,"");move.edit("trigger",0,"");move.edit("linearMode",1,"");move.edit("moveAim",0,"");move.edit("moveGravity",1,"");move.edit("moveDistance",4,"");move.edit("travelSpeed",2,"");move.edit("chargeTime",0,"");move.edit("interval",0,"");
        var position=body.logicalPose().position();var tag=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(level,new Vec3(position.x(),position.y(),position.z()),List.of(body.getUniqueId())).save();
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,file);Files.createDirectories(path.getParent());NbtIo.writeCompressed(tag,path);StructureRemovalApi.remove(level,body);
    }
    private static void box(HeartBlockEntity core,int index,int count,int size,boolean aggro){
        var all=core.modules().all().stream().filter(m->m.type().equals("entity_hitbox")).toList();
        var m=index<all.size()?all.get(index):core.modules().add("entity_hitbox");m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.UP)));
        m.edit("enabled",index<count?1:0,"");m.edit("trigger",0,"");m.edit("hitboxWidth",size,"");m.edit("hitboxHeight",size,"");m.edit("hitboxDepth",size,"");m.edit("hitboxProvokeMobs",aggro?1:0,"");
    }
    private static void configure(String file,boolean enabled,int max)throws Exception{
        wild.configure(level,file,0,0,10,enabled,false,FactionRegistry.WILDS,new WildEncounters.Conditions(true,false,false,Set.of(),0),max,false,new WildEncounters.Density(1,8,24,96));
    }
    private static void spawnManual()throws Exception{
        double x=X+(spawned%8-3.5)*20,z=Z+(spawned/8-3.5)*20;player.setPos(x,0,z-24);player.setYRot(0);
        var attempt=wild.attemptPreviewOrSpawn(player,MANUAL,false);require(attempt.success(),"Manual spawn "+spawned+": "+attempt.reason());spawned++;
    }
    private static List<HeartBlockEntity> cores(){return LoadedCoreIndex.all(level).stream().filter(c->c.health().state()==HeartState.ALIVE).sorted(Comparator.comparing(c->c.creatureId().toString())).toList();}
    private static long receiverCount(){long count=0;for(var e:level.getAllEntities())if(e instanceof EntityHitbox&&!e.isRemoved())count++;return count;}
    private static int tracked(){return wild.configuredFiles(level).stream().mapToInt(f->wild.snapshot(level,f).getInt("ActiveCount")).sum();}
    private static double hp(){return cores().stream().mapToDouble(c->c.health().current()).sum();}
    private static Map<String,Object> state(){return Map.of("tracked",tracked(),"cores",cores().size(),"receivers",receiverCount(),"parts",SubLevelContainer.getContainer(level).getAllSubLevels().size(),"pendingDeaths",LifeLifecycle.get(server).pendingCount());}
    private static void next()throws Exception{
        phaseIndex++;age=0;phaseStart=0;queryCount=0;arrowsCreated=0;var p=PHASES.get(phaseIndex);long start=System.nanoTime();
        if(p.name.startsWith("CORES_")||p.name.startsWith("MOVING_")||p.name.startsWith("ZOMBIES_")||p.name.equals("ARROWS_64_PER_SECOND")){
            int n=0;for(var core:cores()){
                for(int i=0;i<32;i++)box(core,i,n<p.populated?p.receivers:0,p.size,p.aggro);
                for(var m:core.modules().all())if(m.type().equals("linear_movement"))m.edit("enabled",p.moving?1:0,"");
                core.modules().changed();n++;
            }
            if(p.name.equals("ZOMBIES_128_AGGRO_OFF"))for(var core:cores())for(int i=0;i<2;i++){
                var zombie=EntityType.ZOMBIE.create(level);var center=SableBodyProbe.toWorld(level,core.getBlockPos().getCenter());
                zombie.setPos(center.add(0,0,i==0?12:-12));zombie.setPersistenceRequired();zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.CARVED_PUMPKIN));level.addFreshEntity(zombie);mobs.add(zombie);
            }
            if(p.name.equals("ARROWS_64_PER_SECOND")){mobs.forEach(Entity::discard);mobs.clear();}
            settle=100;
        }
        if(p.name.startsWith("CLEAN_")){
            mobs.forEach(Entity::discard);mobs.clear();arrows.forEach(Entity::discard);arrows.clear();
            for(var file:wild.configuredFiles(level)){configure(file,false,64);wild.clearCurrent(level,file);}
            player.setGameMode(GameType.SPECTATOR);
        }
        if(p.name.equals("AUTO_DEFAULT_NEARBY_4")){
            require(tracked()==0&&receiverCount()==0,"Manual cleanup left receivers");require(wild.removeRule(level,MANUAL),"Manual rule did not release its slot");
            writeTemplate("hitbox_benchmark_auto_source",8);var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,"hitbox_benchmark_auto_source");
            for(int i=0;i<64;i++){String file="hitbox_auto_"+i;files.add(file);Files.copy(path,dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,file));wild.loadBlueprint(level,"",file,true);configure(file,true,64);}
            integer("maximumEntityHitboxesPerCreature",8);integer("maximumNearbyWildEncounters",4);player.setPos(X+.5,-58,Z+.5);player.setGameMode(GameType.CREATIVE);
        }
        if(p.name.equals("AUTO_FILL_GLOBAL_64"))integer("maximumNearbyWildEncounters",64);
        metadata.put("transitionMs_"+p.name,(System.nanoTime()-start)/1e6);System.out.println("HITBOX_PHASE_START: "+p.name+" settle="+settle+" "+state());
    }
    private static void begin(Phase p){
        phaseStart=System.nanoTime();gcStart=gcCount();gcMsStart=gcMs();heapStart=usedHeap();healthStart=hp();startPositions.clear();
        for(var core:cores())startPositions.put(core.creatureId(),SableBodyProbe.toWorld(level,core.getBlockPos().getCenter()));
        if(p.name.startsWith("CORES_")||p.name.startsWith("MOVING_")||p.name.startsWith("ZOMBIES_")||p.name.equals("ARROWS_64_PER_SECOND"))require(receiverCount()==(long)p.populated*p.receivers,"Wrong receiver count at sample start: "+p.name+" "+state());
        if(p.name.startsWith("ZOMBIES_"))require(mobs.size()==128&&mobs.stream().allMatch(m->m.isAlive()&&!m.isRemoved()&&!m.isNoAi()),"Zombie workload is not 128 live AI mobs");
    }
    private static void observe(Phase p){
        double travel=0;for(var core:cores()){var start=startPositions.get(core.creatureId());if(start!=null){var now=SableBodyProbe.toWorld(level,core.getBlockPos().getCenter());travel+=Math.hypot(now.x-start.x,now.z-start.z);}}
        long targeted=mobs.stream().filter(m->m.getTarget() instanceof EntityHitbox).count();
        var row=new LinkedHashMap<String,Object>();row.put("phase",p.name);row.put("tick",server.getTickCount());row.putAll(state());row.put("heapBytes",usedHeap());row.put("zombies",mobs.size());row.put("liveZombies",mobs.stream().filter(m->m.isAlive()&&!m.isRemoved()).count());row.put("zombiesTargetingHitbox",targeted);row.put("travel",travel);row.put("healthLoss",Math.max(0,healthStart-hp()));row.put("liveArrows",arrows.size());activity.add(row);
        if(age%100==0)System.out.println("HITBOX_ACTIVITY: "+row);
    }
    private static long gcCount(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum();}
    private static long gcMs(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum();}
    private static long usedHeap(){return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();}
    private static Map<String,Object> stats(List<Double> raw){
        var x=raw.stream().mapToDouble(Double::doubleValue).sorted().toArray();if(x.length==0)return Map.of();
        return Map.of("n",x.length,"meanMs",Arrays.stream(x).average().orElse(0),"p50Ms",x[(int)Math.ceil(x.length*.5)-1],"p95Ms",x[(int)Math.ceil(x.length*.95)-1],"p99Ms",x[(int)Math.ceil(x.length*.99)-1],"maxMs",x[x.length-1],"ticksOver50Ms",Arrays.stream(x).filter(v->v>50).count());
    }
    private static void summarize(Phase p){
        var ticks=samples.stream().filter(t->t.phase.equals(p.name)).toList();var out=new LinkedHashMap<String,Object>();out.put("phase",p.name);out.put("fullTick",stats(ticks.stream().map(Sample::fullMs).toList()));out.put("nativeTick",stats(ticks.stream().map(Sample::nativeMs).toList()));
        out.put("operations",stats(ticks.stream().map(Sample::operationMs).toList()));out.put("elapsedSeconds",(System.nanoTime()-phaseStart)/1e9);out.put("gcCount",gcCount()-gcStart);out.put("gcMs",gcMs()-gcMsStart);out.put("heapStart",heapStart);out.put("heapEnd",usedHeap());out.put("queries",queryCount);out.put("arrowsCreated",arrowsCreated);out.putAll(state());out.put("healthLoss",Math.max(0,healthStart-hp()));summaries.add(out);System.out.println("HITBOX_RESULT: "+out);
    }
    private static void report(boolean success,String failure)throws Exception{
        var dir=server.getServerDirectory().resolve("hitbox-results");Files.createDirectories(dir);var data=new LinkedHashMap<String,Object>();data.put("success",success);data.put("failure",failure);data.put("metadata",metadata);data.put("phases",summaries);data.put("activity",activity);data.put("finalState",state());
        Files.writeString(dir.resolve("summary.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));
        var csv=new StringBuilder("phase,server_tick,full_ms,native_ms,operation_ms\n");for(var t:samples)csv.append(String.format(Locale.ROOT,"%s,%d,%.6f,%.6f,%.6f%n",t.phase,t.tick,t.fullMs,t.nativeMs,t.operationMs));Files.writeString(dir.resolve("ticks.csv"),csv);
        System.out.println("HITBOX_BENCHMARK_COMPLETE: success="+success+" "+dir+" "+failure);
    }
}
