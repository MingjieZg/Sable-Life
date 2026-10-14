package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.joml.Vector3d;
import java.util.*;
@GameTestHolder("sablelife_facing_tests") @PrefixGameTestTemplate(false)
public final class HoverAnchorGameTests {
 private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.north(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.north()),new BoundingBox3i(p.getX(),p.getY(),p.getZ()-1,p.getX(),p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}
 private static VirtualModule anchor(HeartBlockEntity c){var m=c.modules().add("hover_anchor");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH)));return m;}
 private static boolean active(HeartBlockEntity c,VirtualModule m){return c.modules().snapshot(c,true).getList("Modules",10).getCompound(c.modules().all().indexOf(m)).getBoolean("HoverActive");}
 @GameTest(template="combat",batch="hover_native",timeoutTicks=70)
 public static void PinKeepsBlockCentreButAllowsRotationAndReleasesGravity(GameTestHelper h){
  var c=fixture(h);var m=anchor(c);var pivot=m.anchors().getFirst().world(c,false);var body=(ServerSubLevel)m.anchors().getFirst().body(c);var orientation=new org.joml.Quaterniond(body.logicalPose().orientation());c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);
  h.assertTrue(active(c,m),"Native pin not created");SubLevelPhysicsSystem.get(h.getLevel()).getPipeline().addLinearAndAngularVelocity(body,new Vector3d(1,0,0),new Vector3d(0,0,1));
  h.runAfterDelay(18,()->{h.assertTrue(pivot.distanceTo(m.anchors().getFirst().world(c,false))<.2,"Pinned block drifted");h.assertTrue(Math.abs(orientation.dot(body.logicalPose().orientation()))<.999,"Angular axes locked");m.edit("enabled",0,"");c.modules().tick(c);h.assertTrue(!active(c,m),"Disabled pin retained");});
  h.runAfterDelay(32,()->{var now=m.anchors().getFirst().world(c,false);h.assertTrue(now.y<pivot.y-.3,"Released pin did not fall");m.edit("enabled",1,"");c.modules().tick(c);var fresh=m.anchors().getFirst().world(c,false);h.runAfterDelay(8,()->{h.assertTrue(fresh.distanceTo(m.anchors().getFirst().world(c,false))<.25,"Reactivation reused old world anchor");c.forceDeath();c.modules().tick(c);h.assertTrue(!active(c,m),"Death retained pin");h.succeed();});});
 }
 @GameTest(template="combat",batch="hover_lifecycle",timeoutTicks=30)
 public static void MasterClearReloadAndDeletedMarksReleaseNativeJoint(GameTestHelper h){
  var c=fixture(h);var m=anchor(c);c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);h.assertTrue(active(c,m),"No initial pin");c.wireless().master(false);c.modules().tick(c);h.assertTrue(!active(c,m),"Master off retained joint");c.wireless().master(true);c.modules().tick(c);h.assertTrue(active(c,m),"Master on did not repin");var saved=c.modules().save();c.modules().load(saved);var loaded=c.modules().find(m.id());h.assertTrue(!active(c,loaded)&&loaded.anchors().size()==1,"Reload saved runtime world joint or lost mark");c.modules().tick(c);h.assertTrue(active(c,loaded),"Reload failed to pin current position");loaded.clearMarks();c.modules().tick(c);h.assertTrue(!active(c,loaded),"Lost mark kept pin");c.modules().clearRuntime();h.succeed();
 }
 @GameTest(template="combat",batch="hover_ownership",timeoutTicks=35)
 public static void FirstAnchorOwnsTranslationAndUnloadClearsIt(GameTestHelper h){
  var c=fixture(h);var first=anchor(c);var second=anchor(c);second.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos().north(),Direction.DOWN)));
  var linear=c.modules().add("linear_movement");linear.anchors(first.anchors());linear.edit("chargeTime",0,"");var gyro=c.modules().add("gyroscope");gyro.anchors(first.anchors());gyro.gyroDown(new net.minecraft.world.phys.Vec3(1,0,0));
  c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);h.assertTrue(active(c,first)&&!active(c,second),"More than one anchor claimed the body");var rows=c.modules().snapshot(c,true).getList("Modules",10);h.assertTrue(rows.getCompound(2).getString("MovementPhase").equals("idle")&&rows.getCompound(3).getString("GyroState").equals("busy"),"Pose controller fought world joint");
  first.edit("enabled",0,"");c.modules().tick(c);h.assertTrue(active(c,second)&&!active(c,first),"Next pin did not take over");c.onChunkUnloaded();h.assertTrue(!active(c,second),"Unloading retained native handle");h.succeed();
 }
 @GameTest(template="combat",batch="hover_world_y",timeoutTicks=55)
 public static void HeightOnlyKeepsWorldHeightWhileMovingHorizontallyAndTurning(GameTestHelper h){
  var c=fixture(h);var m=anchor(c);m.edit("hoverX",0,"");m.edit("hoverZ",0,"");var a=m.anchors().getFirst();var p=a.world(c,false);var body=(ServerSubLevel)a.body(c);c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);SubLevelPhysicsSystem.get(h.getLevel()).getPipeline().addLinearAndAngularVelocity(body,new Vector3d(2,0,0),new Vector3d(0,0,2));
  h.runAfterDelay(20,()->{var now=a.world(c,false);h.assertTrue(Math.abs(now.y-p.y)<.2,"Y-only constraint rotated out of world vertical");h.assertTrue(Math.abs(now.x-p.x)>.3,"Y-only constraint blocked horizontal travel");c.forceDeath();c.modules().tick(c);h.succeed();});
 }
 @GameTest(template="combat",batch="hover_full_lock",timeoutTicks=55)
 public static void FullLockHoldsActivationPostureAndXZOnlyAllowsFalling(GameTestHelper h){
  var c=fixture(h);var m=anchor(c);m.edit("hoverRotation",1,"");var a=m.anchors().getFirst();var p=a.world(c,false);var body=(ServerSubLevel)a.body(c);var physics=SubLevelPhysicsSystem.get(h.getLevel()).getPipeline();physics.teleport(body,new Vector3d(body.logicalPose().position()),new org.joml.Quaterniond().rotateZ(.4));
  h.runAfterDelay(2,()->{var q=new org.joml.Quaterniond(body.logicalPose().orientation());c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);var pin=a.world(c,false);physics.addLinearAndAngularVelocity(body,new Vector3d(2,1,1),new Vector3d(2,1,3));h.runAfterDelay(15,()->{h.assertTrue(pin.distanceTo(a.world(c,false))<.2&&Math.abs(q.dot(body.logicalPose().orientation()))>.999,"Full lock changed activation position/posture");m.edit("enabled",0,"");c.modules().tick(c);m.edit("hoverRotation",0,"");m.edit("hoverY",0,"");m.edit("enabled",1,"");c.modules().tick(c);var start=a.world(c,false);h.runAfterDelay(12,()->{var now=a.world(c,false);h.assertTrue(now.y<start.y-.2&&Math.abs(now.x-start.x)<.2&&Math.abs(now.z-start.z)<.2,"XZ-only did not allow vertical fall");c.forceDeath();c.modules().tick(c);h.succeed();});});});
 }
 @GameTest(template="combat",batch="hover_elastic",timeoutTicks=100)
 public static void ElasticAxisMovesWithinLimitAndReturnsThenAllOffReleases(GameTestHelper h){
  var c=fixture(h);var m=anchor(c);m.edit("hoverX",0,"");m.edit("hoverY",0,"");m.edit("hoverMode",1,"");m.edit("hoverStrength",80,"");m.edit("hoverDamping",8,"");m.edit("hoverOffset",.6,"");var a=m.anchors().getFirst();var body=(ServerSubLevel)a.body(c);var start=a.world(c,false);c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);SubLevelPhysicsSystem.get(h.getLevel()).getPipeline().addLinearAndAngularVelocity(body,new Vector3d(0,0,5),new Vector3d());
  h.runAfterDelay(3,()->{double offset=Math.abs(a.world(c,false).z-start.z);h.assertTrue(offset>.02&&offset<.8,"Elastic axis was hard locked or exceeded limit: "+offset);});
  h.runAfterDelay(25,()->{double offset=Math.abs(a.world(c,false).z-start.z);h.assertTrue(offset<.15,"Elastic axis did not return: "+offset);m.edit("hoverZ",0,"");m.edit("hoverDamping",0,"");c.modules().tick(c);h.assertTrue(!active(c,m),"All Off retained joint");var saved=VirtualModule.load(m.save());h.assertTrue(saved.value("hoverMode")==1&&saved.value("hoverStrength")==80&&saved.value("hoverOffset")==.6&&!saved.anchors().isEmpty(),"Anchor settings not saved");c.forceDeath();h.succeed();});
 }

 @GameTest(template="combat",batch="hover_damping",timeoutTicks=60)
 public static void DampingReducesFreeAngularVelocityWithoutLockingRotation(GameTestHelper h){
  var c=fixture(h);var m=anchor(c);var a=m.anchors().getFirst();var body=(ServerSubLevel)a.body(c);var physics=SubLevelPhysicsSystem.get(h.getLevel()).getPipeline();c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);physics.addLinearAndAngularVelocity(body,new Vector3d(),new Vector3d(0,2,0));
  h.runAfterDelay(8,()->{double free=body.latestAngularVelocity.length();h.assertTrue(free>.5,"Undamped pin suppressed spin");m.edit("hoverDamping",30,"");c.modules().tick(c);physics.resetVelocity(body);physics.addLinearAndAngularVelocity(body,new Vector3d(),new Vector3d(0,2,0));h.runAfterDelay(8,()->{h.assertTrue(body.latestAngularVelocity.length()<free*.5,"Damping failed to reduce spin");c.forceDeath();c.modules().tick(c);h.succeed();});});
 }

}
