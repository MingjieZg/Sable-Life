package dev.sablelife.testing;

import com.google.gson.GsonBuilder;
import dev.sablelife.ownership.WildWorkbenchProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

/** Explicit development-only stress workload. Measures both full tick and isolated operation time. */
@EventBusSubscriber(modid="sablelife_tests")
public final class WorkbenchBenchmark {
    private enum Phase { COLD_INDEX,SETTLE,BASELINE,QUERIES_8_PER_SECOND,QUERIES_512_PER_SECOND,QUERIES_512_PER_TICK,CHURN_200_CHANGES_PER_TICK,IDLE,UNLOAD }
    private static final List<Map<String,Object>> samples=new ArrayList<>(),results=new ArrayList<>();
    private static final Map<String,Object> metadata=new LinkedHashMap<>();
    private static final String STAGE=System.getProperty("sablelife.workbenchStage","benchmark");
    private static final int X=4096,Z=4096,R=12;
    private static ServerLevel level;private static MinecraftServer server;private static WildWorkbenchProtection index;
    private static boolean initialized,done;private static long start,readsBefore,probesBefore;private static int age,phaseTick;
    private static Phase phase=Phase.COLD_INDEX;
    private static Map<String,Long> phaseBefore;
    private static void require(boolean value,String text){if(!value)throw new IllegalStateException(text);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void pre(ServerTickEvent.Pre event){if(Boolean.getBoolean("sablelife.workbenchBenchmark")&&!done)start=System.nanoTime();}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void post(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("sablelife.workbenchBenchmark")||done)return;
        server=event.getServer();level=server.overworld();
        try{
            if(!initialized){initialized=true;setup();return;}age++;
            if(STAGE.equals("verify")){verify();return;}
            var before=index.statistics();long operationStart=System.nanoTime();int checks=0,blocked=0,clear=0;
            if(phase.name().startsWith("QUERIES")&&(phase==Phase.QUERIES_512_PER_TICK||phaseTick%20==0)){
                checks=phase==Phase.QUERIES_8_PER_SECOND?8:512;
                for(int i=0;i<checks;i++){
                    boolean protectedSide=i%2==0;var area=area(protectedSide?32:112,(i%8-4)*8);
                    var reason=WildWorkbenchProtection.check(level,area);
                    if(protectedSide){require(reason.equals("workbench"),"Protected query escaped: "+reason);blocked++;}
                    else{require(reason.isEmpty(),"Cached safe query failed: "+reason);clear++;}
                }
            }
            if(phase==Phase.CHURN_200_CHANGES_PER_TICK){
                for(int i=0;i<100;i++){
                    var p=new BlockPos(X+96+i%10,-55,Z+i/10);
                    level.setBlock(p,Blocks.CRAFTING_TABLE.defaultBlockState(),18);
                    level.setBlock(p,Blocks.AIR.defaultBlockState(),18);
                }
            }
            double operation=(System.nanoTime()-operationStart)/1e6;var after=index.statistics();
            long reads=after.get("blockReads")-readsBefore,probes=after.get("sectionProbes")-probesBefore;
            require(reads<=WildWorkbenchProtection.BLOCK_READS_PER_TICK&&probes<=WildWorkbenchProtection.SECTION_PROBES_PER_TICK,"Index exceeded per-server-tick work budget");
            var sample=new LinkedHashMap<String,Object>();sample.put("phase",phase.name());sample.put("tick",server.getTickCount());
            sample.put("tickMs",(System.nanoTime()-start)/1e6);sample.put("operationMs",operation);sample.put("checks",checks);sample.put("blocked",blocked);sample.put("clear",clear);sample.put("blockReads",reads);sample.put("sectionProbes",probes);samples.add(sample);
            readsBefore=after.get("blockReads");probesBefore=after.get("sectionProbes");phaseTick++;
            boolean next=switch(phase){case COLD_INDEX->after.get("pending")==0;case SETTLE,QUERIES_512_PER_TICK->phaseTick>=100;case QUERIES_512_PER_SECOND->phaseTick>=300;case UNLOAD->phaseTick>=100;default->phaseTick>=200;};
            if(phase==Phase.COLD_INDEX)require(phaseTick<2000,"Initial indexing did not drain");
            if(next){
                summarize(after);
                if(phase==Phase.UNLOAD){require(level.getChunkSource().getChunkNow(X>>4,Z>>4)==null,"Original area did not unload");finish(true,"");return;}
                phase=Phase.values()[phase.ordinal()+1];phaseTick=0;phaseBefore=after;
                if(phase==Phase.UNLOAD){force(false);}
            }
        }catch(Throwable failure){failure.printStackTrace();try{finish(false,failure.toString());}catch(Exception ignored){server.halt(false);}}
    }
    private static AABB area(int dx,int dz){return new AABB(X+dx,100,Z+dz,X+dx+8,104,Z+dz+8);}
    private static void setup(){
        server.tickRateManager().setTickRate(20);var rules=level.getGameRules();
        rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,server);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);rules.getRule(GameRules.RULE_RANDOMTICKING).set(0,server);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);
        index=WildWorkbenchProtection.get(level);
        if(STAGE.equals("benchmark")){
            force(true);int tables=0;
            for(int cx=-R;cx<0;cx++)for(int cz=-R;cz<=R;cz++){level.setBlock(new BlockPos(X+(cx<<4)+8,-55,Z+(cz<<4)+8),Blocks.CRAFTING_TABLE.defaultBlockState(),18);tables++;}
            metadata.put("craftingTables",tables);metadata.put("forcedChunks",(R*2+1)*(R*2+1));
        }
        metadata.put("stage",STAGE);metadata.put("java",System.getProperty("java.version"));metadata.put("processors",Runtime.getRuntime().availableProcessors());metadata.put("maxHeapBytes",Runtime.getRuntime().maxMemory());
        metadata.put("method","20 TPS dedicated development server, Pre HIGHEST to Post LOWEST includes the production index callback. 512 queries/second simulates 64 rules x 8 candidates; 512/tick is artificial overload. Mixed clear/protected queries. Churn time includes vanilla block changes.");
        phaseBefore=index.statistics();readsBefore=phaseBefore.get("blockReads");probesBefore=phaseBefore.get("sectionProbes");
        System.out.println("WORKBENCH_BENCHMARK_SETUP: "+metadata);
    }
    private static void force(boolean on){for(int cx=(X>>4)-R;cx<=(X>>4)+R;cx++)for(int cz=(Z>>4)-R;cz<=(Z>>4)+R;cz++){level.setChunkForced(cx,cz,on);if(on)level.getChunk(cx,cz);}}
    private static Map<String,Object> stats(List<Double> source){
        var values=source.stream().mapToDouble(Double::doubleValue).sorted().toArray();if(values.length==0)return Map.of();
        return Map.of("n",values.length,"meanMs",Arrays.stream(values).average().orElse(0),"p95Ms",values[(int)Math.ceil(values.length*.95)-1],"maxMs",values[values.length-1],"over50ms",Arrays.stream(values).filter(v->v>50).count());
    }
    private static void summarize(Map<String,Long> after){
        var selected=samples.stream().filter(s->s.get("phase").equals(phase.name())).toList();var row=new LinkedHashMap<String,Object>();row.put("phase",phase.name());
        row.put("tick",stats(selected.stream().map(s->((Number)s.get("tickMs")).doubleValue()).toList()));
        row.put("operation",stats(selected.stream().filter(s->((Number)s.get("checks")).intValue()>0||phase==Phase.CHURN_200_CHANGES_PER_TICK).map(s->((Number)s.get("operationMs")).doubleValue()).toList()));
        long reads=after.get("blockReads")-phaseBefore.get("blockReads"),probes=after.get("sectionProbes")-phaseBefore.get("sectionProbes");row.put("blockReads",reads);row.put("sectionProbes",probes);row.put("index",after);
        if(phase!=Phase.COLD_INDEX)require(reads==0&&probes==0,"Warm workload restarted scanning in "+phase);
        results.add(row);System.out.println("WORKBENCH_PHASE: "+row);
    }
    private static void verify()throws Exception{
        if(age<20)return;
        require(level.getChunkSource().getChunkNow(X>>4,Z>>4)==null,"Reboot unexpectedly loaded test area");
        require(WildWorkbenchProtection.check(level,area(32,0)).equals("workbench"),"Unloaded saved base lost protection after real reboot");
        require(WildWorkbenchProtection.check(level,area(112,0)).isEmpty(),"Unloaded saved clear terrain was forgotten after reboot");
        require(index.statistics().get("blockReads")==0,"Unloaded query scanned blocks");
        metadata.put("unloadedProtectionAndClearCacheSurvivedRestart",true);finish(true,"");
    }
    private static void finish(boolean success,String failure)throws Exception{
        done=true;var root=server.getServerDirectory().resolve("workbench-results");Files.createDirectories(root);
        var report=new LinkedHashMap<String,Object>();report.put("success",success);report.put("failure",failure);report.put("metadata",metadata);report.put("phases",results);report.put("finalIndex",index.statistics());
        var gson=new GsonBuilder().setPrettyPrinting().create();Files.writeString(root.resolve(STAGE+".json"),gson.toJson(report));Files.writeString(root.resolve(STAGE+"-samples.json"),gson.toJson(samples));
        System.out.println("WORKBENCH_BENCHMARK_COMPLETE: "+success+" "+failure);server.halt(false);
    }
}
