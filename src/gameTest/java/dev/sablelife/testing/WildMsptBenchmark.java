package dev.sablelife.testing;

import com.google.gson.GsonBuilder;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.compat.StructureRemovalApi;
import dev.sablelife.compat.photomancy.SpawnTemplateBridge;
import dev.sablelife.heart.FactionRegistry;
import dev.sablelife.ownership.BlueprintSpawnPlacement;
import dev.sablelife.ownership.WildEncounters;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;

/** Explicit, development-only, normal-20-TPS dedicated-server benchmark. Never included in the release JAR. */
@EventBusSubscriber(modid="sablelife_tests")
public final class WildMsptBenchmark {
    private enum Phase {
        SETUP(1), WARMUP(200), BASELINE_BEFORE(300), IDLE_64_RULES(300), SEARCH_WARMUP(200),
        FULL_CHECK_1_PER_SECOND(400), FULL_CHECK_8_PER_SECOND(400), SPAWN(1),
        STRUCTURE_WARMUP(200), STRUCTURE_STEADY(600), CLEANUP(1), SETTLE_AFTER(100), BASELINE_AFTER(300);
        final int ticks;Phase(int ticks){this.ticks=ticks;}
    }
    private record Sample(String phase,int serverTick,double fullMs,double vanillaMs,double operationMs) {}
    private static final List<Sample> samples=new ArrayList<>();
    private static final List<Map<String,Object>> summaries=new ArrayList<>();
    private static final Map<String,Object> metadata=new LinkedHashMap<>();
    private static Phase phase=Phase.SETUP;private static int phaseTick;private static long tickStart,phaseStart,gcCountStart,gcMsStart;
    private static boolean finished,failed;
    private static WildEncounters data,idleData;
    private static ServerLevel level;private static FakePlayer viewer;
    private static Object rule,key;private static Method validate,aboveGround,advance;
    private static Set<UUID> originalBodies;
    private static String file="valkyrie shuttle";
    private static final int RADIUS_CHUNKS=16;

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void pre(ServerTickEvent.Pre event){if(Boolean.getBoolean("sablelife.wildMspt")&&!finished)tickStart=System.nanoTime();}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void post(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("sablelife.wildMspt")||finished)return;
        var server=event.getServer();
        try{
            double operationMs=0;
            if(phase==Phase.SETUP){setup(server);next();return;}
            if(phaseTick==0){phaseStart=System.nanoTime();gcCountStart=gcCount();gcMsStart=gcMs();System.out.println("MSPT_PHASE_START: "+phase);}
            if(phase==Phase.IDLE_64_RULES&&phaseTick%20==0){long start=System.nanoTime();advance.invoke(idleData,server);operationMs=(System.nanoTime()-start)/1e6;}
            if((phase==Phase.SEARCH_WARMUP||phase==Phase.FULL_CHECK_1_PER_SECOND||phase==Phase.FULL_CHECK_8_PER_SECOND)&&phaseTick%20==0){
                long start=System.nanoTime();validate.invoke(null,level,key,rule);
                int attempts=phase==Phase.FULL_CHECK_1_PER_SECOND?1:8;
                for(int i=0;i<attempts;i++){
                    double angle=(phaseTick/20+i)*Math.PI/4;
                    int x=(int)Math.floor(Math.cos(angle)*128),z=(int)Math.floor(Math.sin(angle)*128);
                    if(aboveGround.invoke(null,level,rule,x,z)==null)throw new IllegalStateException("Prepared candidate failed full validation at "+x+","+z);
                }
                operationMs=(System.nanoTime()-start)/1e6;
            }
            if(phase==Phase.SPAWN){
                var enabled=(ModConfigSpec.BooleanValue)SableLifeServerConfig.SPEC.getValues().get("enableWildEncounters");
                long start=System.nanoTime();enabled.set(true);
                try{if(!data.attemptPreviewOrSpawn(viewer,file,false).success())throw new IllegalStateException("Full Valkyrie placement failed");}finally{enabled.set(false);}
                operationMs=(System.nanoTime()-start)/1e6;
                long count=SubLevelContainer.getContainer(level).getAllSubLevels().stream().filter(b->!originalBodies.contains(b.getUniqueId())).count();
                if(count!=22)throw new IllegalStateException("Unexpected placed part count: "+count);
                metadata.put("placedParts",count);System.out.println("MSPT_PLACED: parts="+count+" operationMs="+operationMs);
            }
            if(phase==Phase.CLEANUP){
                long start=System.nanoTime();for(var body:SubLevelContainer.getContainer(level).getAllSubLevels().stream().filter(b->!originalBodies.contains(b.getUniqueId())).toList())StructureRemovalApi.remove(level,(dev.ryanhcode.sable.sublevel.ServerSubLevel)body);
                operationMs=(System.nanoTime()-start)/1e6;
            }
            double fullMs=(System.nanoTime()-tickStart)/1e6;
            double vanillaMs=server.getTickTimesNanos()[Math.floorMod(server.getTickCount(),100)]/1e6;
            samples.add(new Sample(phase.name(),server.getTickCount(),fullMs,vanillaMs,operationMs));
            if(++phaseTick>=phase.ticks){summarize();if(phase==Phase.BASELINE_AFTER){report(server);finished=true;server.halt(false);}else next();}
        }catch(Throwable failure){
            failed=true;finished=true;System.out.println("MSPT_BENCHMARK_FAILED: "+failure);failure.printStackTrace();
            try{metadata.put("failure",failure.toString());report(server);}catch(Exception ignored){}server.halt(false);
        }
    }

    private static void setup(MinecraftServer server)throws Exception{
        level=server.overworld();server.tickRateManager().setTickRate(20);
        var rules=level.getGameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);rules.getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);level.setDayTime(6000);
        ((ModConfigSpec.BooleanValue)SableLifeServerConfig.SPEC.getValues().get("enableWildEncounters")).set(false);
        ((ModConfigSpec.IntValue)SableLifeServerConfig.SPEC.getValues().get("maximumNearbyWildEncounters")).set(64);
        for(int x=-RADIUS_CHUNKS;x<=RADIUS_CHUNKS;x++)for(int z=-RADIUS_CHUNKS;z<=RADIUS_CHUNKS;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
        var info=SpawnTemplateBridge.readWild(server,file);var box=SpawnTemplateBridge.geometry(info.template()).bounds(new BlueprintSpawnPlacement.Destination(level.dimension().location(),Vec3.ZERO,null));
        data=new WildEncounters();data.loadBlueprint(level,"",file,false);
        data.configure(level,file,32,0,10,false,false,FactionRegistry.WILDS,new WildEncounters.Conditions(false,true,false,Set.of(),0),1,false);
        var field=WildEncounters.class.getDeclaredField("rules");field.setAccessible(true);var entry=((Map<?,?>)field.get(data)).entrySet().iterator().next();key=entry.getKey();rule=entry.getValue();
        validate=Arrays.stream(WildEncounters.class.getDeclaredMethods()).filter(m->m.getName().equals("template")).findFirst().orElseThrow();validate.setAccessible(true);
        aboveGround=Arrays.stream(WildEncounters.class.getDeclaredMethods()).filter(m->m.getName().equals("aboveGround")&&m.getParameterCount()==4).findFirst().orElseThrow();aboveGround.setAccessible(true);
        advance=WildEncounters.class.getDeclaredMethod("advance",MinecraftServer.class);advance.setAccessible(true);
        var saved=data.save(new CompoundTag(),level.registryAccess());var row=saved.getList("Rules",10).getCompound(0);var list=new ListTag();
        for(int i=0;i<64;i++){var copy=row.copy();copy.putString("File","idle_rule_"+i);copy.putBoolean("Enabled",true);list.add(copy);}saved.put("Rules",list);idleData=WildEncounters.load(saved,level.registryAccess());
        viewer=TestActors.creative(level);viewer.setGameMode(GameType.SPECTATOR);viewer.setNoGravity(true);viewer.setPos(.5,80,.5);viewer.setYRot(0);viewer.setXRot(0);level.addNewPlayer(viewer);
        originalBodies=SubLevelContainer.getContainer(level).getAllSubLevels().stream().map(b->b.getUniqueId()).collect(java.util.stream.Collectors.toSet());
        metadata.put("blueprint",file);metadata.put("blocks",info.blocks());metadata.put("parts",info.parts());metadata.put("heartCores",info.life());metadata.put("bounds",box.toString());
        metadata.put("loadedChunks",(2*RADIUS_CHUNKS+1)*(2*RADIUS_CHUNKS+1));metadata.put("targetTps",20);metadata.put("heapMaxBytes",Runtime.getRuntime().maxMemory());metadata.put("logicalProcessors",Runtime.getRuntime().availableProcessors());
        metadata.put("java",System.getProperty("java.version"));metadata.put("os",System.getProperty("os.name")+" "+System.getProperty("os.arch"));
        metadata.put("method","Pre(HIGHEST) to Post(LOWEST), before benchmark reporting; vanilla tick time recorded separately because it excludes NeoForge post callbacks.");
        metadata.put("searchMethod","Cached template validation plus 1 or 8 complete fixed loaded candidate checks per 20 ticks, without placement; eight successful checks are a stress comparison, not normal early-stop automatic spawning.");
        metadata.put("limitations","Development dedicated server, original ship has no Heart Core; no Life AI workload, no full modpack/client rendering, one actual spawn, 1089 forced flat chunks shared by every phase.");
        System.out.println("MSPT_SETUP_COMPLETE: "+metadata);
    }

    private static void next(){phase=Phase.values()[phase.ordinal()+1];phaseTick=0;}
    private static long gcCount(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum();}
    private static long gcMs(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum();}
    private static Map<String,Object> stats(List<Double> raw){
        var result=new LinkedHashMap<String,Object>();if(raw.isEmpty())return result;var values=raw.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        result.put("n",values.length);result.put("meanMs",Arrays.stream(values).average().orElse(0));result.put("p50Ms",values[(int)Math.ceil(values.length*.5)-1]);result.put("p95Ms",values[(int)Math.ceil(values.length*.95)-1]);result.put("maxMs",values[values.length-1]);result.put("ticksOver50Ms",Arrays.stream(values).filter(v->v>50).count());return result;
    }
    private static void summarize(){
        var selected=samples.stream().filter(s->s.phase.equals(phase.name())).toList();var result=new LinkedHashMap<String,Object>();result.put("phase",phase.name());
        result.put("fullTick",stats(selected.stream().map(Sample::fullMs).toList()));result.put("vanillaTick",stats(selected.stream().map(Sample::vanillaMs).toList()));result.put("operations",stats(selected.stream().map(Sample::operationMs).filter(v->v>0).toList()));
        result.put("elapsedSeconds",(System.nanoTime()-phaseStart)/1e9);result.put("gcCollections",gcCount()-gcCountStart);result.put("gcTimeMs",gcMs()-gcMsStart);
        if(phase==Phase.STRUCTURE_STEADY)metadata.put("partsAfterSteady",SubLevelContainer.getContainer(level).getAllSubLevels().stream().filter(b->!originalBodies.contains(b.getUniqueId())).count());
        summaries.add(result);System.out.println("MSPT_PHASE_RESULT: "+result);
    }
    private static void report(MinecraftServer server)throws Exception{
        var directory=server.getServerDirectory().resolve("mspt-results");Files.createDirectories(directory);
        var result=new LinkedHashMap<String,Object>();result.put("success",!failed);result.put("metadata",metadata);result.put("phases",summaries);
        Files.writeString(directory.resolve("summary.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        var csv=new StringBuilder("phase,server_tick,full_ms,vanilla_ms,operation_ms\n");for(var s:samples)csv.append(String.format(Locale.ROOT,"%s,%d,%.6f,%.6f,%.6f%n",s.phase,s.serverTick,s.fullMs,s.vanillaMs,s.operationMs));Files.writeString(directory.resolve("ticks.csv"),csv);
        System.out.println("MSPT_BENCHMARK_COMPLETE: success="+!failed+" samples="+samples.size()+" directory="+directory);
    }
}
