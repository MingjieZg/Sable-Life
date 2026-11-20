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
@GameTestHolder("sablelife_signal_buffer_perf_tests") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="sablelife_tests")
public final class SignalBufferPerformanceGameTests {
 record Item(HeartBlockEntity core,ServerSubLevel body,Vector3d position){}
 static List<Item> items=new ArrayList<>();static List<Double> samples=new ArrayList<>();static boolean active;static int age;static long began;static double setup,generation,placeSum,placeMax;static GameTestHelper helper;
 @GameTest(template="combat",batch="signal_buffer_perf",timeoutTicks=500)
 public static void ThirtyTwoBlueprintCoresWith256RedstoneModules(GameTestHelper h){var l=h.getLevel();long start=System.nanoTime();var p=h.absolutePos(new BlockPos(4,80,4));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());var seed=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));var source=(HeartBlockEntity)l.getBlockEntity(seed.getPlot().getCenterBlock());for(int i=0;i<2;i++){source.wireless().add();source.wireless().channel(i).set(6,3);}for(int i=0;i<8;i++){var m=source.modules().add("wireless_redstone");m.anchors(List.of(ModuleAnchor.create(source,source.getBlockPos(),Direction.EAST)));m.edit("redstoneStrengthMode",1,"");m.edit("trigger",2,"");m.edit("channel",1,"");m.edit("redstoneChannel",2,"");m.edit("redstoneBufferTime",Integer.getInteger("sablelife.signalBufferPerfTicks",0),"");}var template=dev.sablelife.compat.photomancy.SpawnTemplateBridge.inspect(dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(l,Vec3.ZERO,List.of(seed.getUniqueId())).save()).template();for(int i=0;i<32;i++){var at=h.absoluteVec(new Vec3(8+i%8*12,80,20+i/8*12));var cell=BlockPos.containing(at);l.setChunkForced(cell.getX()>>4,cell.getZ()>>4,true);l.getChunkAt(cell);long placed=System.nanoTime();var build=dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.createSpawn(template,new CompoundTag(),at,new Quaterniond());h.assertTrue(build.step(l).equals("DONE"),"Native blueprint failed");double ms=(System.nanoTime()-placed)/1e6;placeSum+=ms;placeMax=Math.max(placeMax,ms);var c=build.heart(l);c.tryActivate(TestActors.creative(l));h.assertTrue(c.operational(),"Perf core inactive");var body=(ServerSubLevel)dev.ryanhcode.sable.Sable.HELPER.getContaining(l,c.getBlockPos());items.add(new Item(c,body,new Vector3d(body.logicalPose().position())));}setup=(System.nanoTime()-start)/1e6;helper=h;active=true;}
 @SubscribeEvent(priority=EventPriority.HIGHEST)public static void pre(ServerTickEvent.Pre e){began=System.nanoTime();if(active)for(var item:items)item.core.wireless().programOutputs("perf_input",age%20<10?Map.of(0,7):Map.of());}
 @SubscribeEvent(priority=EventPriority.LOWEST)public static void post(ServerTickEvent.Post e){if(!active)return;double ms=(System.nanoTime()-began)/1e6;if(age==0)generation=ms;var pipeline=SubLevelPhysicsSystem.get(helper.getLevel()).getPipeline();for(var item:items){pipeline.resetVelocity(item.body);pipeline.teleport(item.body,item.position,new Quaterniond());}if(++age>200&&age<=400)samples.add(ms);if(age!=400)return;var sorted=samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();System.out.printf(Locale.ROOT,"SIGNAL_BUFFER_PERF {\"cores\":32,\"redstoneModules\":256,\"bufferTicks\":%d,\"supported\":%s,\"samples\":200,\"setupMs\":%.6f,\"generationTickWindowMs\":%.6f,\"placementMeanMs\":%.6f,\"placementMaxMs\":%.6f,\"meanMspt\":%.6f,\"p95Mspt\":%.6f,\"maxMspt\":%.6f}%n",Integer.getInteger("sablelife.signalBufferPerfTicks",0),ModuleFields.find("wireless_redstone","redstoneBufferTime")!=null,setup,generation,placeSum/32,placeMax,Arrays.stream(sorted).average().orElseThrow(),sorted[189],sorted[199]);active=false;for(var item:items)item.core.resetForCreative(TestActors.creative(helper.getLevel()));helper.succeed();}
}
