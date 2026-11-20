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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

@GameTestHolder("sablelife_terrain_melee_perf_tests") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="sablelife_tests")
public final class TerrainMeleePerformanceGameTests {
 record Item(HeartBlockEntity core,ServerSubLevel body,Vector3d position){}
 static List<BlockPos> decorations=new ArrayList<>();static List<Item> items=new ArrayList<>();static List<Double> samples=new ArrayList<>(),burst=new ArrayList<>();static boolean active;static int age;static long begin;static double setup,generation,placeSum,placeMax;static GameTestHelper helper;
 @GameTest(template="combat",batch="terrain_melee_perf",timeoutTicks=500)
 public static void ThirtyTwoBlueprintMeleeCreatures(GameTestHelper h){var level=h.getLevel();long start=System.nanoTime();var p=h.absolutePos(new BlockPos(4,80,4));level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());var seed=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));var source=(HeartBlockEntity)level.getBlockEntity(seed.getPlot().getCenterBlock());if(Boolean.getBoolean("sablelife.coreTerrainPerfOn"))source.terrainSettings().edit("crushEnabled",1,"");var melee=source.modules().add("melee");var mark=ModuleAnchor.create(source,source.getBlockPos(),Direction.EAST);melee.anchors(List.of(mark,mark));melee.edit("startDistance",3,"");for(var k:List.of("meleeLength","meleeWidth","meleeHeight"))melee.edit(k,2,"");boolean on=Boolean.getBoolean("sablelife.meleeTerrainPerfOn");if(on)melee.edit("meleeBreakBlocks",1,"");if(Boolean.getBoolean("sablelife.meleeEffectsPerfOn"))for(String key:List.of("meleeDebris","meleeDrops"))if(ModuleFields.find("melee",key)!=null)melee.edit(key,1,"");var template=dev.sablelife.compat.photomancy.SpawnTemplateBridge.inspect(dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(level,Vec3.ZERO,List.of(seed.getUniqueId())).save()).template();
  for(int i=0;i<32;i++){var at=h.absoluteVec(new Vec3(8+i%8*12,80,20+i/8*12));var cell=BlockPos.containing(at);level.setChunkForced(cell.getX()>>4,cell.getZ()>>4,true);level.getChunkAt(cell);long place=System.nanoTime();var build=dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.createSpawn(template,new CompoundTag(),at,new Quaterniond());h.assertTrue(build.step(level).equals("DONE"),"Native blueprint placement failed");double ms=(System.nanoTime()-place)/1e6;placeSum+=ms;placeMax=Math.max(placeMax,ms);var core=build.heart(level);core.tryActivate(TestActors.creative(level));h.assertTrue(core.operational(),"Core not active");var body=(ServerSubLevel)dev.ryanhcode.sable.Sable.HELPER.getContaining(level,core.getBlockPos());items.add(new Item(core,body,new Vector3d(body.logicalPose().position())));}
  setup=(System.nanoTime()-start)/1e6;helper=h;active=true;
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST)public static void pre(ServerTickEvent.Pre e){begin=System.nanoTime();if(active&&age==100){var level=helper.getLevel();for(var item:items){if(Boolean.getBoolean("sablelife.coreTerrainPerfOn")){var p=BlockPos.containing(ModuleArena.corePosition(item.core));level.setBlock(p,Blocks.TALL_GRASS.defaultBlockState(),2);decorations.add(p);}else{var module=item.core.modules().all().getFirst();var center=BlockPos.containing(MeleeGeometry.frame(item.core,module).center());for(var p:BlockPos.betweenClosed(center.offset(-1,-1,-1),center.offset(1,1,1)))level.setBlock(p,Blocks.DIRT.defaultBlockState(),2);}}}}
 @SubscribeEvent(priority=EventPriority.LOWEST)public static void post(ServerTickEvent.Post e){if(!active)return;double ms=(System.nanoTime()-begin)/1e6;if(age==0)generation=ms;if(age>=100&&age<120)burst.add(ms);var pipeline=SubLevelPhysicsSystem.get(helper.getLevel()).getPipeline();for(var item:items){pipeline.resetVelocity(item.body);pipeline.teleport(item.body,item.position,new Quaterniond());}if(++age>200&&age<=400)samples.add(ms);if(age!=400)return;var sorted=samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();System.out.printf(Locale.ROOT,"TERRAIN_MELEE_PERF {\"cores\":32,\"meleeModules\":32,\"onRequested\":%s,\"onSupported\":%s,\"samples\":200,\"setupMs\":%.6f,\"generationTickWindowMs\":%.6f,\"placementMeanMs\":%.6f,\"placementMaxMs\":%.6f,\"meanMspt\":%.6f,\"p95Mspt\":%.6f,\"maxMspt\":%.6f,\"burstSamples\":20,\"burstMeanMspt\":%.6f,\"burstMaxMspt\":%.6f}%n",Boolean.getBoolean("sablelife.meleeTerrainPerfOn"),ModuleFields.find("melee","meleeBreakBlocks")!=null,setup,generation,placeSum/32,placeMax,Arrays.stream(sorted).average().orElseThrow(),sorted[189],sorted[199],burst.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),burst.stream().mapToDouble(Double::doubleValue).max().orElseThrow());System.out.println("TERRAIN_DECOR_REMAINING "+decorations.stream().filter(p->!helper.getLevel().getBlockState(p).isAir()).count());active=false;for(var item:items)item.core.resetForCreative(TestActors.creative(helper.getLevel()));helper.succeed();}
}
