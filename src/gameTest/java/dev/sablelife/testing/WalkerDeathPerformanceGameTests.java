package dev.sablelife.testing;

import net.minecraft.gametest.framework.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_walker_death_perf_tests") @PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="sablelife_tests")
public final class WalkerDeathPerformanceGameTests {
    static long begin;static int age;static GameTestHelper helper;static WalkerDeathCleanupGameTests.Rig rig;
    static final List<Double> live=new ArrayList<>(),death=new ArrayList<>();
    @GameTest(template="combat",batch="walker_death_perf",timeoutTicks=420)
    public static void LiveWalkerAndDeathWindow(GameTestHelper h)throws Exception{
        WalkerDeathCleanupGameTests.start(h,"death_performance",false,0,r->{rig=r;helper=h;});
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void pre(ServerTickEvent.Pre e){begin=System.nanoTime();if(rig!=null&&age==250)WalkerDeathCleanupGameTests.hit(helper.getLevel(),rig.hull().getBlockPos(),2000);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void post(ServerTickEvent.Post e){
        if(rig==null)return;double ms=(System.nanoTime()-begin)/1e6;
        if(age>=50&&age<250)live.add(ms);if(age>=250)death.add(ms);
        ++age;
        if(age<290)return;
        var report=new com.google.gson.JsonObject();report.addProperty("cores",2);report.addProperty("legSegments",rig.legs().size());report.add("live",stats(live));report.add("deathWindow",stats(death));report.addProperty("legsRemaining",rig.legs().stream().filter(b->!b.isRemoved()).count());
        System.out.println("WALKER_DEATH_PERF "+report);WalkerDeathCleanupGameTests.finish(helper,rig);rig=null;
    }
    static com.google.gson.JsonObject stats(List<Double> samples){var a=samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();var out=new com.google.gson.JsonObject();out.addProperty("samples",a.length);out.addProperty("meanMspt",Arrays.stream(a).average().orElseThrow());out.addProperty("p95Mspt",a[(int)Math.ceil(a.length*.95)-1]);out.addProperty("maxMspt",a[a.length-1]);return out;}
}
