package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.joml.Vector3d;
import java.util.*;

@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class GravityTravelGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h,int height){var l=h.getLevel();var p=h.absolutePos(new BlockPos(4,height,4));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}
    private static VirtualModule move(HeartBlockEntity core){var m=core.modules().add("linear_movement");m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.EAST)));m.edit("trigger",0,"");m.edit("linearMode",1,"");m.edit("moveGravity",1,"");m.edit("chargeTime",0,"");m.edit("interval",1000,"");m.edit("travelSpeed",4,"");m.edit("moveDistance",8,"");core.tryActivate(TestActors.creative((net.minecraft.server.level.ServerLevel)core.getLevel()));return m;}
    private static String phase(HeartBlockEntity c){return c.modules().snapshot(c,true).getList("Modules",10).getCompound(0).getString("MovementPhase");}
    private static void floor(GameTestHelper h){for(var p:BlockPos.betweenClosed(-2,4,-2,20,4,12))h.getLevel().setBlockAndUpdate(h.absolutePos(p),Blocks.STONE.defaultBlockState());}
    @GameTest(template="combat",batch="gravity_settings")
    public static void DefaultsPersistenceHorizontalPlanAndVelocityDelta(GameTestHelper h){
        var c=fixture(h,12);var m=move(c);m.edit("moveVertical",45,"");var plan=ModuleLinearMovement.plan(c,m);
        h.assertTrue(Math.abs(plan.delta().y)<1e-9&&Math.abs(plan.delta().length()-8)<1e-6,"Gravity plan still locked a sloping route");
        var delta=GravityTravel.driveDelta(new Vector3d(1,-3,2),new Vec3(1,0,0),4);h.assertTrue(delta.equals(new Vector3d(3,0,-2)),"Drive cancelled vertical velocity");
        m.edit("moveStep",1,"");m.edit("moveStepHeight",1.5,"");m.edit("moveStepCooldown",18,"");var loaded=VirtualModule.load(m.save());h.assertTrue(loaded.value("moveGravity")==1&&loaded.value("moveStep")==1&&loaded.value("moveStepHeight")==1.5&&loaded.value("moveStepCooldown")==18,"Gravity/step settings did not persist");
        var saved=m.save();saved.getCompound("Settings").remove("moveGravity");saved.getCompound("Settings").remove("moveStep");var old=VirtualModule.load(saved);h.assertTrue(old.value("moveGravity")==0&&old.value("moveStep")==0,"Old Travel unexpectedly changed");
        m.edit("linearMode",0,"");h.assertTrue(!GravityTravel.enabled(m),"Teleport enabled physics drive");h.succeed();
    }
    @GameTest(template="combat",batch="gravity_fall",timeoutTicks=35)
    public static void HorizontalTravelFallsAndRetainsAngularMotion(GameTestHelper h){
        var c=fixture(h,20);var m=move(c);var body=LinearMovementGeometry.capture(c).getFirst().body();var origin=new Vector3d(body.logicalPose().position());var rotation=new org.joml.Quaterniond(body.logicalPose().orientation());
        dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(h.getLevel()).getPipeline().addLinearAndAngularVelocity(body,new Vector3d(0,-1,0),new Vector3d(0,0,.4));c.modules().tick(c);
        h.runAfterDelay(10,()->{var pos=body.logicalPose().position();h.assertTrue(pos.x()>origin.x+.5&&pos.y()<origin.y-.2,"Travel failed to move/fall: "+new Vector3d(pos).sub(origin));h.assertTrue(Math.abs(body.logicalPose().orientation().dot(rotation))<.99999,"Travel erased rotation");m.edit("enabled",0,"");c.modules().tick(c);});h.runAfterDelay(12,()->{h.assertTrue(phase(c).equals("cancelled"),"Trigger cancellation did not stop drive: "+phase(c));h.succeed();});
    }
    @GameTest(template="combat",batch="gravity_arrival",timeoutTicks=45)
    public static void CompletionStopsHorizontalDriveButKeepsFalling(GameTestHelper h){var c=fixture(h,25);var m=move(c);m.edit("moveDistance",1,"");var body=LinearMovementGeometry.capture(c).getFirst().body();var start=new Vector3d(body.logicalPose().position());c.modules().tick(c);h.runAfterDelay(25,()->{var p=body.logicalPose().position();h.assertTrue(phase(c).equals("arrived")&&Math.abs(p.x()-start.x-1)<.3,"Gravity Travel did not complete actual horizontal displacement: "+phase(c)+" / "+new Vector3d(p).sub(start));h.assertTrue(p.y()<start.y-.3&&Math.abs(body.latestLinearVelocity.x)<.3,"Completion held height or retained horizontal drive");m.edit("enabled",0,"");h.succeed();});}
    @GameTest(template="combat",batch="gravity_probe")
    public static void StepProbesRequireGroundAndObstacleWithinConfiguredHeight(GameTestHelper h){floor(h);var c=fixture(h,5);var m=move(c);var body=LinearMovementGeometry.capture(c).getFirst();var wall=h.absolutePos(new BlockPos(6,5,4));h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
        h.assertTrue(GravityTravel.obstacle(h.getLevel(),body,new Vec3(1,0,0),4,1,CollisionWorkBudget.begin(h.getLevel()))>.8,"One-block obstacle not detected");
        h.assertTrue(GravityTravel.obstacle(h.getLevel(),body,new Vec3(1,0,0),4,.5,CollisionWorkBudget.begin(h.getLevel()))==0,"Over-height obstacle accepted");
        var airborne=new LinearMovementGeometry.Body(body.body(),body.position(),body.rotation(),body.box().move(0,3,0));h.assertTrue(GravityTravel.obstacle(h.getLevel(),airborne,new Vec3(1,0,0),4,1,CollisionWorkBudget.begin(h.getLevel()))<0,"Airborne part allowed to rearm step");m.edit("enabled",0,"");h.succeed();}
    @GameTest(template="combat",batch="gravity_part_impulse",timeoutTicks=20)
    public static void WholeStepRaisesAllPartsBySameAmount(GameTestHelper h){
        floor(h);var c=fixture(h,5);var m=move(c);m.edit("moveStep",1,"");m.edit("enabled",0,"");h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(6,5,4)),Blocks.STONE.defaultBlockState());
        var p=h.absolutePos(new BlockPos(11,8,4));h.getLevel().setBlockAndUpdate(p,Blocks.IRON_BLOCK.defaultBlockState());
        var other=SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));other.updateBoundingBox();var bounds=other.boundingBox();
        var untouched=new LinearMovementGeometry.Body(other,new Vector3d(other.logicalPose().position()),new org.joml.Quaterniond(other.logicalPose().orientation()),LinearMovementGeometry.contactBounds(new AABB(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ())));
        var foot=LinearMovementGeometry.capture(c).getFirst();var session=new GravityTravel.Session();double footY=foot.body().logicalPose().position().y,otherY=other.logicalPose().position().y;
        h.assertTrue(GravityTravel.drive(h.getLevel(),c,m,List.of(foot,untouched),new Vec3(1,0,0),4,session),"Part drive rejected loaded scene");
        double rise=foot.body().logicalPose().position().y-footY,otherRise=other.logicalPose().position().y-otherY;
        h.assertTrue(Math.abs(rise-1./6)<1e-6&&Math.abs(rise-otherRise)<1e-6,"Whole step did not raise every part equally: "+rise+" / "+otherRise);
        h.runAfterDelay(1,()->{h.assertTrue(foot.body().logicalPose().position().y>footY&&other.logicalPose().position().y>otherY,"Synchronized lift did not reach native physics");session.clear();h.succeed();});
    }
    @GameTest(template="combat",batch="gravity_wall",timeoutTicks=145)
    public static void HoldingAgainstWallCannotContinuouslyClimb(GameTestHelper h){floor(h);for(var p:BlockPos.betweenClosed(6,5,2,6,10,7))h.getLevel().setBlockAndUpdate(h.absolutePos(p),Blocks.STONE.defaultBlockState());var c=fixture(h,5);var m=move(c);m.edit("moveDistance",2,"");m.edit("moveStep",1,"");m.edit("moveStepCooldown",4,"");var body=LinearMovementGeometry.capture(c).getFirst().body();double start=body.logicalPose().position().y;double[] peak={start};c.modules().tick(c);for(int i=1;i<=115;i++)h.runAfterDelay(i,()->{peak[0]=Math.max(peak[0],body.logicalPose().position().y());c.modules().tick(c);});h.runAfterDelay(120,()->{h.assertTrue(peak[0]<start+2.5,"Repeated contact climbed the wall: "+(peak[0]-start));h.assertTrue(phase(c).equals("blocked"),"Blocked gravity Travel did not time out: "+phase(c));m.edit("enabled",0,"");h.succeed();});}
    @GameTest(template="combat",batch="route_step_velocity")
    public static void StepDoesNotRestoreStaleFallingVelocityAfterRouteAlignment(GameTestHelper h){
        floor(h);var c=fixture(h,5);var m=move(c);m.edit("moveGravity",0,"");m.edit("moveStep",1,"");m.edit("enabled",0,"");h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(6,5,4)),Blocks.STONE.defaultBlockState());
        var bodies=LinearMovementGeometry.capture(c);var body=bodies.getFirst().body();var pipeline=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(h.getLevel()).getPipeline();
        pipeline.addLinearAndAngularVelocity(body,new Vector3d(0,-2,0),new Vector3d());body.latestLinearVelocity.y=-2;
        LinearMovementGeometry.move(h.getLevel(),bodies,Vec3.ZERO);
        h.assertTrue(GravityTravel.step(h.getLevel(),m,bodies,new Vec3(1,0,0),4,new GravityTravel.Session()).lift()>0,"Step did not activate after route alignment");
        h.assertTrue(Math.abs(pipeline.getLinearVelocity(body,new Vector3d()).y)<1e-6,"Step converted stale falling speed into an upward impulse");h.succeed();
    }
    @GameTest(template="combat",batch="route_rear_step",timeoutTicks=40)
    public static void HeldHeightRearPartCanStepAgainAfterProgressAndCooldown(GameTestHelper h){
        floor(h);var c=fixture(h,5);var m=move(c);m.edit("moveGravity",0,"");m.edit("moveStep",1,"");m.edit("moveStepHeight",1.2,"");m.edit("enabled",0,"");
        h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(6,5,4)),Blocks.STONE.defaultBlockState());
        var session=new GravityTravel.Session();var direction=new Vec3(1,0,0);
        for(int i=0;i<6;i++)h.assertTrue(GravityTravel.step(h.getLevel(),m,LinearMovementGeometry.capture(c),direction,4,session).lift()>0,"First lift stopped early");
        var raised=LinearMovementGeometry.capture(c).getFirst();
        // A second low part after the front has crossed: the held route leaves a gap to the floor.
        var rear=new LinearMovementGeometry.Body(raised.body(),raised.position(),raised.rotation(),raised.box().move(-2,-.4,0));
        h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(4,6,4)),Blocks.STONE.defaultBlockState());
        h.assertTrue(GravityTravel.obstacle(h.getLevel(),rear,direction,4,1.2,CollisionWorkBudget.begin(h.getLevel()))<0,"Rear fixture unexpectedly grounded");
        h.assertTrue(GravityTravel.step(h.getLevel(),m,List.of(rear),direction,4,session).lift()==0,"Repeated stationary contact climbed without progress");
        var advanced=new LinearMovementGeometry.Body(rear.body(),new Vector3d(rear.position()).add(.5,0,0),rear.rotation(),rear.box().move(.5,0,0));
        h.assertTrue(GravityTravel.step(h.getLevel(),m,List.of(advanced),direction,4,session).lift()==0,"Rear step ignored cooldown");
        h.runAfterDelay(15,()->{
            h.assertTrue(GravityTravel.step(h.getLevel(),m,List.of(rear),direction,4,session).lift()==0,"Cooldown alone rearmed a stationary wall contact");
            h.assertTrue(GravityTravel.step(h.getLevel(),m,List.of(advanced),direction,4,session).lift()>0,"Rear contact could not resume stepping on held-height route");h.succeed();
        });
    }
    @GameTest(template="combat",batch="route_step_wall",timeoutTicks=145)
    public static void RouteStepWaitsForLaterContactsButStillTimesOutAtWall(GameTestHelper h){
        floor(h);for(var p:BlockPos.betweenClosed(6,5,-2,12,25,12))h.getLevel().setBlockAndUpdate(h.absolutePos(p),Blocks.STONE.defaultBlockState());
        var c=fixture(h,5);var m=move(c);m.edit("moveGravity",0,"");m.edit("moveDistance",2,"");m.edit("moveStep",1,"");m.edit("moveStepCooldown",4,"");c.modules().tick(c);var contact=LinearMovementGeometry.capture(c);var stationary=LinearMovementGeometry.forcedDestination(contact,contact,new Vec3(.1,0,0));
        // Keep the fixture at the obstruction: solver pushback must not turn this timeout check into a new free route.
        for(int i=1;i<=120;i++)h.runAfterDelay(i,()->{LinearMovementGeometry.move(h.getLevel(),stationary,Vec3.ZERO);c.modules().tick(c);});
        h.runAfterDelay(10,()->{h.assertTrue(phase(c).equals("travelling"),"Route cancelled instead of waiting for a later step: "+phase(c));});
        h.runAfterDelay(125,()->{h.assertTrue(phase(c).equals("blocked"),"Route step waited forever at a wall: "+phase(c)+" bodies="+LinearMovementGeometry.capture(c));m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="gravity_step",timeoutTicks=160)
    public static void ActualStepCrossesPlatformThenFallsBackToGround(GameTestHelper h){crossPlatform(h,true,false);}
    @GameTest(template="combat",batch="route_step",timeoutTicks=160)
    public static void OrdinaryTravelStepsWithoutEnablingGravity(GameTestHelper h){crossPlatform(h,false,false);}
    @GameTest(template="combat",batch="forced_route_step",timeoutTicks=160)
    public static void ForcedTravelRetainsWholeStepHeightWithoutGravity(GameTestHelper h){crossPlatform(h,false,true);}
    private static void crossPlatform(GameTestHelper h,boolean gravity,boolean force){floor(h);for(var p:BlockPos.betweenClosed(6,5,3,8,5,5))h.getLevel().setBlockAndUpdate(h.absolutePos(p),Blocks.STONE.defaultBlockState());var c=fixture(h,5);var m=move(c);m.edit("moveGravity",gravity?1:0,"");m.edit("moveForce",force?1:0,"");m.edit("moveDistance",5.5,"");m.edit("moveStep",1,"");m.edit("moveStepHeight",1.2,"");var gyro=c.modules().add("gyroscope");gyro.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.DOWN)));gyro.edit("trigger",0,"");gyro.edit("gyroTilt",10,"");gyro.edit("gyroSpeed",180,"");gyro.edit("gyroCollision",0,"");ModuleGyroscope.calibrate(c,gyro);var body=LinearMovementGeometry.capture(c).getFirst().body();var start=new Vector3d(body.logicalPose().position());double[] peak={start.y};c.modules().tick(c);
        for(int i=1;i<=125;i++){int at=i;h.runAfterDelay(i,()->{peak[0]=Math.max(peak[0],body.logicalPose().position().y());c.modules().tick(c);});}
        h.runAfterDelay(130,()->{var p=body.logicalPose().position();h.assertTrue(peak[0]>start.y+.5,"Contact step never lifted body: peak="+peak[0]+" start="+start+" end="+p+" phase="+phase(c)+" status="+HeartModules.status(m,c));h.assertTrue(p.x()>start.x+5&&p.y()<start.y+.3&&phase(c).equals("arrived"),"Step failed to cross and settle: "+new Vector3d(p).sub(start)+" / "+phase(c));h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(6,5,4))).is(Blocks.STONE),"Step altered terrain");m.edit("enabled",0,"");h.succeed();});}
}
