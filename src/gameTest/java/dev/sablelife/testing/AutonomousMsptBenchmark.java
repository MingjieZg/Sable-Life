package dev.sablelife.testing;

import com.google.gson.GsonBuilder;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.compat.photomancy.SpawnTemplateBridge;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.ownership.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.*;

/** Opt-in dedicated-server experiment. Not included in the distributable mod. */
@EventBusSubscriber(modid="sablelife_tests")
public final class AutonomousMsptBenchmark {
    private record Phase(String name,int ticks,int population) {}
    private record Tick(String phase,int tick,double ms,double nativeMs,double operationMs) {}
    private static final List<Phase> phases=new ArrayList<>();
    static { phases.add(new Phase("WARMUP",200,0));phases.add(new Phase("BASELINE",300,0));
        for(String entry:System.getProperty("sablelife.autonomousPopulations","2,8,16,32,64").split(",")){int n=Integer.parseInt(entry);if(n<2||n>64||n%2!=0)throw new IllegalArgumentException("even population 2..64");phases.add(new Phase("FILL_"+n,n*2+20,n));phases.add(new Phase("COMBAT_"+n,n==64?1200:600,n));phases.add(new Phase("CLEAN_"+n,1200,0));}
        phases.add(new Phase("BASELINE_AFTER",300,0)); }
    private static int phaseIndex=-1,phaseTick,spawned;private static long start,phaseStart,gcStart,gcMsStart;
    private static boolean done,failed;private static ServerLevel level;private static FakePlayer viewer;
    private static WildEncounters wild;private static final String[] names={"core","wheelcog"};private static final UUID[] factions=new UUID[2];
    private static final double[] forward=new double[2];
    private static final List<Tick> ticks=new ArrayList<>();private static final List<Map<String,Object>> results=new ArrayList<>(),activity=new ArrayList<>();
    private static final Map<String,Object> metadata=new LinkedHashMap<>();private static final Map<UUID,Vec3> positions=new HashMap<>();
    private static final Map<UUID,Double> health=new HashMap<>();private static final Map<UUID,Long> moveTicks=new HashMap<>();
    private static final Map<String,Integer> joinedEntities=new TreeMap<>();private static final Set<UUID> movers=new HashSet<>(),actors=new HashSet<>();
    private static double damage,totalTravel;private static int changes;

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void pre(ServerTickEvent.Pre e){if(Boolean.getBoolean("sablelife.autonomousMspt")&&!done)start=System.nanoTime();}
    @SubscribeEvent public static void entity(EntityJoinLevelEvent e){if(Boolean.getBoolean("sablelife.autonomousMspt")&&!done&&e.getLevel()==level)joinedEntities.merge(e.getEntity().getType().toString(),1,Integer::sum);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void post(ServerTickEvent.Post e){
        if(!Boolean.getBoolean("sablelife.autonomousMspt")||done)return;
        var server=e.getServer();try{
            if(phaseIndex<0){setup(server);next();return;}
            var p=phases.get(phaseIndex);double operation=0;
            if(phaseTick==0){phaseStart=System.nanoTime();gcStart=gcCount();gcMsStart=gcMs();joinedEntities.clear();movers.clear();actors.clear();positions.clear();health.clear();moveTicks.clear();damage=totalTravel=0;changes=0;System.out.println("AUTO_PHASE_START: "+p.name);}
            if(p.name.startsWith("FILL")&&phaseTick%2==0&&spawned<p.population){long t=System.nanoTime();spawn();operation=(System.nanoTime()-t)/1e6;}
            if(p.name.startsWith("CLEAN")&&phaseTick==0){long t=System.nanoTime();wild.clearCurrent(level,names[0]);wild.clearCurrent(level,names[1]);spawned=0;operation=(System.nanoTime()-t)/1e6;}
            double ms=(System.nanoTime()-start)/1e6;
            ticks.add(new Tick(p.name,server.getTickCount(),ms,server.getTickTimesNanos()[Math.floorMod(server.getTickCount(),100)]/1e6,operation));
            // Sampling/reporting excluded from the measured window, but included in elapsed wall time.
            if(phaseTick%20==0)observe(p,server);
            boolean cleanDone=p.name.startsWith("CLEAN")&&phaseTick>=100&&wild.snapshot(level,names[0]).getInt("ActiveCount")==0&&wild.snapshot(level,names[1]).getInt("ActiveCount")==0&&SubLevelContainer.getContainer(level).getAllSubLevels().isEmpty();
            if(++phaseTick>=p.ticks||cleanDone){if(p.name.startsWith("CLEAN")&&!cleanDone)throw new IllegalStateException("Normal encounter cleanup did not finish within 1200 ticks");summarize(p);if(phaseIndex+1==phases.size()){report(server);done=true;server.halt(false);}else next();}
        }catch(Throwable t){failed=true;done=true;t.printStackTrace();metadata.put("failure",t.toString());try{report(server);}catch(Exception ignored){}server.halt(false);}
    }
    private static void setup(MinecraftServer server)throws Exception{
        level=server.overworld();server.tickRateManager().setTickRate(20);
        var r=level.getGameRules();r.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);r.getRule(GameRules.RULE_RANDOMTICKING).set(0,server);r.getRule(GameRules.RULE_DAYLIGHT).set(false,server);r.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);level.setDayTime(6000);
        ((ModConfigSpec.BooleanValue)SableLifeServerConfig.SPEC.getValues().get("enableWildEncounters")).set(true);
        ((ModConfigSpec.IntValue)SableLifeServerConfig.SPEC.getValues().get("maximumNearbyWildEncounters")).set(64);
        for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
        viewer=TestActors.creative(level);viewer.setGameMode(GameType.SPECTATOR);viewer.setNoGravity(true);viewer.setPos(0,100,0);viewer.setYRot(0);level.addNewPlayer(viewer);
        var registry=FactionRegistry.get(server);wild=WildEncounters.get(server);var templates=new ArrayList<Map<String,Object>>();
        for(int i=0;i<2;i++){
            factions[i]=registry.create(viewer,"Benchmark "+names[i]);if(factions[i]==null)throw new IllegalStateException("faction creation failed");
            var info=SpawnTemplateBridge.readWild(server,names[i]);if(!info.canActivate())throw new IllegalStateException("Template must contain exactly one Heart Core");
            var box=SpawnTemplateBridge.geometry(info.template()).bounds(new BlueprintSpawnPlacement.Destination(level.dimension().location(),Vec3.ZERO,null));forward[i]=Math.max(24,Math.ceil(Math.hypot(box.getXsize(),box.getZsize())/2+8));
            wild.loadBlueprint(level,"",names[i],true);wild.configure(level,names[i],0,0,10,false,false,factions[i],new WildEncounters.Conditions(false,true,false,Set.of(),0),32,false);
            templates.add(Map.of("name",names[i],"blocks",info.blocks(),"parts",info.parts(),"bounds",box.toString(),"faction",factions[i].toString()));
        }
        if(registry.allied(factions[0],factions[1])||!registry.damageAllowed(factions[0],factions[1]))throw new IllegalStateException("Opposing factions must be hostile");
        metadata.put("templates",templates);metadata.put("java",System.getProperty("java.version"));metadata.put("heapMaxBytes",Runtime.getRuntime().maxMemory());metadata.put("logicalProcessors",Runtime.getRuntime().availableProcessors());metadata.put("forcedChunks",1089);
        if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.parse("cataclysm:the_incinerator")))throw new IllegalStateException("Original blueprint requires Cataclysm item registry");
        metadata.put("method","Normal 20 TPS. Highest Pre to Lowest Post including production Life and natural encounter ticks. Diagnostic sampling excluded, elapsed time includes sampling. Original blueprint HP, modules and actions unchanged; only assigned opposing factions. Manual Test Spawn path fills two disabled automatic rules at one creature per 2 ticks; production tracking remains enabled. No refill during combat; actual alive population sampled each second. No connected rendering client or full modpack.");
        System.out.println("AUTO_SETUP: "+metadata);
    }
    private static void spawn()throws Exception{
        int kind=spawned%2,pair=spawned/2;double x=(pair%4-1.5)*52+kind*24,z=(pair/4-3.5)*36;
        viewer.setPos(x,100,z-forward[kind]);viewer.setYRot(0);
        var attempt=wild.attemptPreviewOrSpawn(viewer,names[kind],false);if(!attempt.success())throw new IllegalStateException("spawn "+spawned+" "+names[kind]+": "+attempt.reason());spawned++;
        System.out.println("AUTO_SPAWN: "+spawned+" "+names[kind]);
    }
    private static void observe(Phase p,MinecraftServer server)throws Exception{
        int alive=0,operational=0,core=0,wheel=0,poweredAttacks=0,valid=0,invalid=0;double hp=0;
        for(var h:LoadedCoreIndex.all(level)){
            if(h.health().state()!=HeartState.ALIVE)continue;alive++;if(h.operational())operational++;if(factions[0].equals(h.relations().faction()))core++;if(factions[1].equals(h.relations().faction()))wheel++;
            var pos=SableBodyProbe.toWorld(level,h.getBlockPos().getCenter());var old=positions.put(h.creatureId(),pos);if(old!=null){double d=old.distanceTo(pos);if(d>.02){movers.add(h.creatureId());totalTravel+=d;}}
            double current=h.health().current();hp+=current;var previous=health.put(h.creatureId(),current);if(previous!=null)damage+=Math.max(0,previous-current);
            for(var m:h.modules().all()){
                if(HeartModules.status(m,h).equals("valid"))valid++;else invalid++;
                long activation=h.modules().moveActivation(m.id());var last=moveTicks.put(m.id(),activation);if(activation!=Long.MIN_VALUE&&(last==null||last!=activation)){changes++;actors.add(h.creatureId());}
                if(Set.of("melee","arrow","gun","mortar","quake","slash","laser","homing","ground_strike","flame","sphere_blast","pulse","surface_field").contains(m.type())&&HeartModules.triggered(m,h))poweredAttacks++;
            }
        }
        var row=new LinkedHashMap<String,Object>();row.put("phase",p.name);row.put("tick",server.getTickCount());row.put("alive",alive);row.put("operational",operational);row.put("coreAlive",core);row.put("wheelcogAlive",wheel);row.put("hp",hp);row.put("poweredAttackModules",poweredAttacks);row.put("validModules",valid);row.put("otherModuleStatuses",invalid);row.put("tracked",wild.snapshot(level,names[0]).getInt("ActiveCount")+wild.snapshot(level,names[1]).getInt("ActiveCount"));row.put("parts",SubLevelContainer.getContainer(level).getAllSubLevels().size());row.put("entities",java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).count());row.put("heapUsedBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());activity.add(row);
        if(phaseTick%200==0)System.out.println("AUTO_ACTIVITY: "+row);
        if(p.name.startsWith("COMBAT")&&phaseTick==0)for(int k=0;k<2;k++){final int kind=k;var h=LoadedCoreIndex.all(level).stream().filter(a->factions[kind].equals(a.relations().faction())).findFirst();if(h.isPresent()){var dir=server.getServerDirectory().resolve("autonomous-results");Files.createDirectories(dir);Files.writeString(dir.resolve(p.name+"-"+names[k]+"-runtime.snbt"),h.get().modules().snapshot(h.get(),true).toString());}}
    }
    private static void next(){phaseIndex++;phaseTick=0;}
    private static long gcCount(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum();}
    private static long gcMs(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum();}
    private static Map<String,Object> stats(List<Double> raw){var out=new LinkedHashMap<String,Object>();if(raw.isEmpty())return out;var a=raw.stream().mapToDouble(Double::doubleValue).sorted().toArray();out.put("n",a.length);out.put("meanMs",Arrays.stream(a).average().orElse(0));out.put("p50Ms",a[(int)Math.ceil(a.length*.5)-1]);out.put("p95Ms",a[(int)Math.ceil(a.length*.95)-1]);out.put("p99Ms",a[(int)Math.ceil(a.length*.99)-1]);out.put("maxMs",a[a.length-1]);out.put("over50Ms",Arrays.stream(a).filter(v->v>50).count());return out;}
    private static void summarize(Phase p){var selected=ticks.stream().filter(t->t.phase.equals(p.name)).toList();var out=new LinkedHashMap<String,Object>();out.put("phase",p.name);out.put("fullTick",stats(selected.stream().map(Tick::ms).toList()));out.put("nativeTick",stats(selected.stream().map(Tick::nativeMs).toList()));out.put("spawnCleanup",stats(selected.stream().map(Tick::operationMs).filter(v->v>0).toList()));out.put("elapsedSeconds",(System.nanoTime()-phaseStart)/1e9);out.put("gcCount",gcCount()-gcStart);out.put("gcMs",gcMs()-gcMsStart);out.put("uniqueMoved",movers.size());out.put("totalSampledTravel",totalTravel);out.put("uniqueActed",actors.size());out.put("moveActivationsObserved",changes);out.put("sampledHealthLost",damage);out.put("entitiesJoined",new TreeMap<>(joinedEntities));var pop=activity.stream().filter(a->p.name.equals(a.get("phase"))).mapToInt(a->(int)a.get("alive")).summaryStatistics();out.put("aliveMin",pop.getMin());out.put("aliveMax",pop.getMax());out.put("aliveMean",pop.getAverage());results.add(out);System.out.println("AUTO_RESULT: "+out);}
    private static void report(MinecraftServer server)throws Exception{var dir=server.getServerDirectory().resolve("autonomous-results");Files.createDirectories(dir);var gson=new GsonBuilder().setPrettyPrinting().create();Files.writeString(dir.resolve("summary.json"),gson.toJson(Map.of("success",!failed,"metadata",metadata,"phases",results,"activity",activity)));var csv=new StringBuilder("phase,tick,full_ms,native_ms,operation_ms\n");for(var t:ticks)csv.append(String.format(Locale.ROOT,"%s,%d,%.6f,%.6f,%.6f%n",t.phase,t.tick,t.ms,t.nativeMs,t.operationMs));Files.writeString(dir.resolve("ticks.csv"),csv);System.out.println("AUTO_COMPLETE: success="+!failed+" directory="+dir);}
}
