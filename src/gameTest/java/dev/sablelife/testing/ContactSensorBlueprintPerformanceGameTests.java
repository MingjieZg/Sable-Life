package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

@GameTestHolder("sablelife_contact_blueprint_perf_tests") @PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="sablelife_tests")
public final class ContactSensorBlueprintPerformanceGameTests {
    private record Item(HeartBlockEntity core,ServerSubLevel body,Vector3d position){}
    private static final List<Item> items=new ArrayList<>();private static final List<Double> samples=new ArrayList<>();
    private static boolean active;private static int age;private static long begin;private static double setup,placeMax,placeSum,generationTick;private static GameTestHelper helper;
    @GameTest(template="combat",batch="contact_blueprint_perf",timeoutTicks=500)
    public static void ThirtyTwoBlueprintPlacementsAndLiveContactQueries(GameTestHelper h){
        var level=h.getLevel();long start=System.nanoTime();var pos=h.absolutePos(new BlockPos(4,45,4));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());
        var seed=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ()));var source=(HeartBlockEntity)level.getBlockEntity(seed.getPlot().getCenterBlock());
        if(VirtualModule.supportsType("contact_sensor")){
            int channel=source.wireless().add();source.wireless().channel(channel).set(6,3);source.wireless().channel(channel).set(0,1);
            for(int n=0;n<8;n++){var m=source.modules().add("contact_sensor");m.anchors(List.of(ModuleAnchor.create(source,source.getBlockPos(),Direction.SOUTH)));m.edit("sensorChannel",1,"");m.edit("startDistance",3,"");for(String key:List.of("contactSizeX","contactSizeY","contactSizeZ"))m.edit(key,2,"");m.edit("contactDetectStructures",1,"");m.edit("contactDetectMobs",1,"");m.edit("contactDetectProjectiles",1,"");}
        }
        var template=dev.sablelife.compat.photomancy.SpawnTemplateBridge.inspect(dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(level,ModuleArena.corePosition(source),List.of(seed.getUniqueId())).save()).template();
        for(int i=0;i<32;i++){
            var center=h.absoluteVec(new Vec3(5+i%8*6,45,20+i/8*6));var block=BlockPos.containing(center);level.setChunkForced(block.getX()>>4,block.getZ()>>4,true);level.getChunkAt(block);long place=System.nanoTime();
            var build=dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.createSpawn(template,new CompoundTag(),center,new Quaterniond());h.assertTrue(build.step(level).equals("DONE"),"Benchmark native blueprint failed");double ms=(System.nanoTime()-place)/1e6;placeMax=Math.max(placeMax,ms);placeSum+=ms;
            var core=build.heart(level);core.tryActivate(TestActors.creative(level));h.assertTrue(core.operational(),"Benchmark blueprint activation failed");var body=(ServerSubLevel)dev.ryanhcode.sable.Sable.HELPER.getContaining(level,core.getBlockPos());items.add(new Item(core,body,new Vector3d(body.logicalPose().position())));
        }
        setup=(System.nanoTime()-start)/1e6;helper=h;active=true;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void pre(ServerTickEvent.Pre e){begin=System.nanoTime();}
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void post(ServerTickEvent.Post e){
        if(!active)return;double ms=(System.nanoTime()-begin)/1e6;if(age==0)generationTick=ms;var pipeline=SubLevelPhysicsSystem.get(helper.getLevel()).getPipeline();
        for(var item:items){pipeline.resetVelocity(item.body);pipeline.teleport(item.body,item.position,new Quaterniond());}
        if(++age>100&&age<=400)samples.add(ms);
        if(age!=400)return;var sorted=samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        System.out.printf(Locale.ROOT,"CONTACT_BLUEPRINT_PERF {\"cores\":32,\"sensors\":%d,\"samples\":%d,\"setupMs\":%.6f,\"generationTickWindowMs\":%.6f,\"placementMeanMs\":%.6f,\"placementMaxMs\":%.6f,\"meanMspt\":%.6f,\"p95Mspt\":%.6f,\"maxMspt\":%.6f}%n",VirtualModule.supportsType("contact_sensor")?256:0,sorted.length,setup,generationTick,placeSum/32,placeMax,Arrays.stream(sorted).average().orElseThrow(),sorted[284],sorted[299]);
        active=false;for(var item:items)item.core.resetForCreative(TestActors.creative(helper.getLevel()));helper.succeed();
    }
}
