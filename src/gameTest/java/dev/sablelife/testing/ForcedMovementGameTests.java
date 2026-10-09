package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class ForcedMovementGameTests {
 private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,12,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}
 private static VirtualModule move(HeartBlockEntity c){var m=c.modules().add("linear_movement");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH)));m.edit("trigger",0,"");m.edit("chargeTime",0,"");m.edit("moveDistance",6,"");m.edit("interval",1000,"");c.tryActivate(TestActors.creative((net.minecraft.server.level.ServerLevel)c.getLevel()));return m;}
 private static void wall(GameTestHelper h,Vec3 origin){var p=BlockPos.containing(origin.add(0,0,-2));for(int x=-2;x<=3;x++)for(int y=-4;y<=4;y++)h.getLevel().setBlockAndUpdate(p.offset(x,y,0),Blocks.STONE.defaultBlockState());}
 private static String phase(HeartBlockEntity c){return c.modules().snapshot(c,true).getList("Modules",10).getCompound(0).getString("MovementPhase");}
 @GameTest(template="combat",batch="force_teleport",timeoutTicks=25) public static void DefaultTeleportStopsAtWallButForcedTeleportCrossesWithoutBreakingIt(GameTestHelper h){
  var c=fixture(h);var m=move(c);var origin=ModuleArena.corePosition(c);wall(h,origin);c.modules().tick(c);h.assertTrue(phase(c).equals("blocked")&&m.value("moveForce")==0,"Default teleport stopped respecting the wall");m.edit("moveForce",1,"");c.modules().clearRuntime();c.modules().tick(c);h.assertTrue(phase(c).equals("arrived"),"Force teleport did not bypass path obstruction");h.runAfterDelay(4,()->{var end=ModuleArena.corePosition(c);h.assertTrue(Math.abs(end.z-origin.z+6)<.15,"Forced body did not reach the far side");h.assertTrue(h.getLevel().getBlockState(BlockPos.containing(origin.add(0,0,-2))).is(Blocks.STONE),"Force teleport destroyed terrain");h.succeed();});
 }
 @GameTest(template="combat",batch="force_travel",timeoutTicks=40) public static void ForcedTravelKeepsPlannedProgressDespiteWallContact(GameTestHelper h){
  var c=fixture(h);var m=move(c);m.edit("linearMode",1,"");m.edit("travelSpeed",10,"");m.edit("moveForce",1,"");var origin=ModuleArena.corePosition(c);wall(h,origin);c.modules().tick(c);
  for(int i=1;i<=14;i++)h.runAfterDelay(i,()->c.modules().tick(c));h.runAfterDelay(16,()->{var end=ModuleArena.corePosition(c);h.assertTrue(phase(c).equals("arrived")&&Math.abs(end.z-origin.z+6)<.2,"Forced Travel stalled or lost planned progress: "+phase(c)+" / "+(end.z-origin.z));h.assertTrue(h.getLevel().getBlockState(BlockPos.containing(origin.add(0,0,-2))).is(Blocks.STONE),"Force Travel destroyed terrain");h.succeed();});
 }
 @GameTest(template="combat",batch="force_geometry") public static void PlannedDestinationIgnoresPushbackButPreservesWorldBoundsAndSavedDefaults(GameTestHelper h){
  var c=fixture(h);var m=move(c);var start=LinearMovementGeometry.capture(c);var displaced=start.stream().map(b->new LinearMovementGeometry.Body(b.body(),new org.joml.Vector3d(b.position()).add(0,0,10),b.rotation(),b.box().move(0,0,10))).toList();var planned=LinearMovementGeometry.forcedDestination(start,displaced,new Vec3(0,0,-2));h.assertTrue(Math.abs(planned.getFirst().position().z-start.getFirst().position().z+2)<1e-6,"Pushback changed the next planned translation");h.assertTrue(!LinearMovementGeometry.loadedDestination(h.getLevel(),LinearMovementGeometry.forcedDestination(start,start,new Vec3(100000000,0,0))),"Force mode bypassed world border");m.edit("moveForce",1,"");var tag=m.save();h.assertTrue(VirtualModule.load(tag).value("moveForce")==1,"Force flag did not persist");tag.getCompound("Settings").remove("moveForce");h.assertTrue(VirtualModule.load(tag).value("moveForce")==0,"Legacy module unexpectedly forced movement");h.succeed();
 }
}
