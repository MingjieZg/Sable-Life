package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.joml.Vector3d;
import java.util.*;
@GameTestHolder("sablelife_facing_tests") @PrefixGameTestTemplate(false)
public final class TravelTurningGameTests {
 @GameTest(template="combat",batch="travel_turn_route",timeoutTicks=65) public static void RouteFollowsSignalHeadingAndFinishesCurvedDistance(GameTestHelper h){run(h,false,false,false,0);}
 @GameTest(template="combat",batch="travel_turn_force",timeoutTicks=65) public static void ForcedBackwardsRouteKeepsHeadingOffsetAndDistance(GameTestHelper h){run(h,false,true,false,180);}
 @GameTest(template="combat",batch="travel_turn_strafe",timeoutTicks=65) public static void StrafingRouteTurnsWithBodyWithoutLosingLocalOffset(GameTestHelper h){run(h,false,false,false,90);}
 @GameTest(template="combat",batch="travel_turn_gravity",timeoutTicks=65) public static void GravityDrivePreservesSpeedAndTurnsWithoutSidewaysSliding(GameTestHelper h){run(h,true,false,false,0);}
 @GameTest(template="combat",batch="travel_face_route",timeoutTicks=65) public static void AutoFacingTurnsOrdinaryTravelTowardTarget(GameTestHelper h){run(h,false,false,true,0);}
 @GameTest(template="combat",batch="travel_face_gravity",timeoutTicks=65) public static void AutoFacingTurnsGravityTravelAndPreservesBraking(GameTestHelper h){run(h,true,false,true,0);}
 @GameTest(template="combat",batch="travel_face_force_target",timeoutTicks=70)
 public static void ForcedTargetPositionStillArrivesAtRecordedGoalWhileTurning(GameTestHelper h){run(h,false,true,true,0,true);}
 private static void run(GameTestHelper h,boolean gravity,boolean force,boolean auto,double offset){run(h,gravity,force,auto,offset,false);}
 private static Vec3 point(HeartBlockEntity c){return SableBodyProbe.toWorld(c.getLevel(),c.getBlockPos().getCenter());}
 private static String state(HeartBlockEntity c,VirtualModule m,String key){return c.modules().snapshot(c,true).getList("Modules",10).getCompound(c.modules().all().indexOf(m)).getString(key);}
 private static void run(GameTestHelper h,boolean gravity,boolean force,boolean auto,double offset,boolean targeted){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(6,40,6));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.north(),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.north()),new BoundingBox3i(p.getX(),p.getY(),p.getZ()-1,p.getX(),p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());var anchor=ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH);
  var m=c.modules().add("linear_movement");m.anchors(List.of(anchor));m.edit("trigger",0,"");m.edit("linearMode",1,"");m.edit("chargeTime",0,"");m.edit("moveDistance",6,"");m.edit("travelSpeed",4,"");m.edit("moveHorizontal",offset,"");m.edit("moveGravity",gravity?1:0,"");m.edit("moveForce",force?1:0,"");m.edit("interval",1000,"");var initial=LinearMovementGeometry.direction(c,m);
  var rotation=c.modules().add(auto?"auto_facing":"signal_turn");rotation.anchors(List.of(anchor));net.minecraft.world.entity.Mob target=null;
  if(auto){rotation.edit("target",1,"");rotation.edit("facingSpeed",90,"");rotation.edit("facingCollision",0,"");target=EntityType.COW.create(l);target.setNoAi(true);target.setNoGravity(true);target.setPos(point(c).add(-8,0,0));l.addFreshEntity(target);}else{rotation.edit("turnSpeed",90,"");rotation.edit("turnCollision",0,"");rotation.edit("turnLeftChannel",1,"");int channel=c.wireless().add();c.wireless().channel(channel).set(0,1);c.wireless().channel(channel).set(6,3);c.wireless().programOutputs("steering",Map.of(0,15));}
  if(targeted){m.edit("moveAim",1,"");m.edit("target",1,"");m.edit("moveStopDistance",2,"");}
  var goal=targeted?ModuleLinearMovement.plan(c,m).goal():null;
  var start=point(c);var previous=new Vec3[]{start};double[] distance={0};c.tryActivate(TestActors.creative(l));if(!auto)c.wireless().programOutputs("steering",Map.of(0,15));c.modules().tick(c);
  h.assertTrue(!state(c,rotation,auto?"FacingState":"TurnState").equals("busy"),"Travel still blocks turn controller");
  distance[0]+=GravityTravel.horizontal(point(c).subtract(previous[0])).length();previous[0]=point(c);
  for(int i=1;i<=38;i++)h.runAfterDelay(i,()->{var now=point(c);distance[0]+=GravityTravel.horizontal(now.subtract(previous[0])).length();previous[0]=now;});
  h.runAfterDelay(8,()->{
   var direction=GravityTravel.horizontal(LinearMovementGeometry.direction(c,m)).normalize();h.assertTrue(targeted||direction.dot(initial)<.96,"Move direction stayed frozen despite turning");
   if(gravity){var velocity=SubLevelPhysicsSystem.get(l).getPipeline().getLinearVelocity((dev.ryanhcode.sable.sublevel.ServerSubLevel)body,new Vector3d());var horizontal=new Vec3(velocity.x,0,velocity.z);h.assertTrue(Math.abs(horizontal.length()-4)<.3&&horizontal.normalize().dot(direction)>.98,"Turn erased/doubled drive or retained old heading: "+velocity+" direction="+direction);}
  });
  var victim=target;
  h.runAfterDelay(39,()->{
   h.assertTrue(state(c,m,"MovementPhase").equals("arrived"),"Curved travel did not finish: "+state(c,m,"MovementPhase")+" arc="+distance[0]);
   h.assertTrue(targeted||distance[0]>5.6&&distance[0]<6.6,"Distance used net displacement instead of travelled arc: "+distance[0]);
   var displacement=GravityTravel.horizontal(point(c).subtract(start));h.assertTrue(targeted||displacement.length()<5.9,"Steering did not bend the route: "+displacement);
   if(gravity){var velocity=SubLevelPhysicsSystem.get(l).getPipeline().getLinearVelocity((dev.ryanhcode.sable.sublevel.ServerSubLevel)body,new Vector3d());h.assertTrue(Math.hypot(velocity.x,velocity.z)<.2,"Turning restored stale velocity after travel braking: "+velocity);}
   if(targeted)h.assertTrue(m.anchors().getFirst().world(c,true).subtract(new Vec3(goal.x,m.anchors().getFirst().world(c,true).y,goal.z)).length()<.7,"Forced target route rotated its recorded goal: "+m.anchors().getFirst().world(c,true)+" goal="+goal);
   if(victim!=null)victim.discard();m.edit("enabled",0,"");rotation.edit("enabled",0,"");c.forceDeath();c.modules().tick(c);h.succeed();
  });
 }
}
