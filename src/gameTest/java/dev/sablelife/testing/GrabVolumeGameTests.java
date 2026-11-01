package dev.sablelife.testing;
import dev.sablelife.module.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.*;
import net.minecraft.core.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_freeze_tests") @PrefixGameTestTemplate(false)
public final class GrabVolumeGameTests {
    private static SlashGuardGameTests.Defender player(GameTestHelper h,Vec3 point){var p=new SlashGuardGameTests.Defender(h.getLevel());p.setPos(point);p.setNoGravity(true);h.getLevel().addNewPlayer(p);return p;}
    private static PlayerFreeze.Source source(){return new PlayerFreeze.Source(UUID.randomUUID(),UUID.randomUUID());}
    @GameTest(template="combat") public static void CircleHeightAndRectanglePreviewMatchCapture(GameTestHelper h){
        var m=new VirtualModule("freeze");m.edit("freezeRadius",2,"");m.edit("freezeHeight",4,"");var circle=ControlVolume.frame(m,Vec3.ZERO,new Vec3(0,0,1));
        h.assertTrue(circle.intersects(new AABB(1.8,-.2,-.1,2.2,.2,.1))&&!circle.intersects(new AABB(2.2,0,0,2.4,1,.2))&&!circle.intersects(new AABB(0,2.1,0,.2,3,.2)),"Circle height/radius mismatch");
        for(var p:circle.outline())h.assertTrue(circle.intersects(new AABB(p,p).inflate(.0001)),"Preview outside cylinder capture");
        m.edit("fieldShape",1,"");m.edit("fieldLength",8,"");m.edit("fieldWidth",2,"");m.edit("fieldAngle",45,"");var rectangle=ControlVolume.frame(m,Vec3.ZERO,new Vec3(0,0,1));
        for(var p:rectangle.outline())h.assertTrue(rectangle.intersects(new AABB(p,p).inflate(.0001)),"Preview outside rectangle capture");
        var inside=rectangle.forward().scale(3);var outside=rectangle.right().scale(1.5);h.assertTrue(rectangle.intersects(new AABB(inside,inside).inflate(.05))&&!rectangle.intersects(new AABB(outside,outside).inflate(.05)),"Rotated length/width ignored");
        m.edit("fieldPlacement",1,"");var ahead=ControlVolume.frame(m,Vec3.ZERO,new Vec3(0,0,1));h.assertTrue(ahead.center().distanceTo(ahead.forward().scale(4))<1e-8,"Ahead placement not applied");h.succeed();
    }
    @GameTest(template="combat") public static void MarkStartDistanceAndGrabSettingsPersist(GameTestHelper h){
        var c=SlashGuardGameTests.core(h);var m=c.modules().add("freeze");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("startDistance",3,"");m.edit("direction",0,"");var mark=m.anchors().getFirst();var direction=ModuleCombat.direction(c,m);var expected=mark.world(c,true).add(direction.scale(3));h.assertTrue(ModuleFreeze.centers(c,m).getFirst().distanceTo(expected)<1e-8,"Start distance ignored");
        var grab=c.modules().add("grab");grab.anchors(m.anchors());grab.edit("grabOffsetY",2,"");h.assertTrue(grab.known()&&MoveSet.TARGETS.contains("grab")&&VirtualModule.AVAILABLE_TYPES.contains("grab"),"Grab scheduling unavailable");var copy=VirtualModule.load(grab.save());h.assertTrue(copy.value("grabOffsetY")==2&&copy.particles().size()==1&&ModulePreset.create(grab.save()).anchors().isEmpty(),"Grab preset/save corrupted");h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=30) public static void DynamicAttachmentMovesAndPreservesOtherFreeze(GameTestHelper h){
        var origin=h.absoluteVec(new Vec3(5,8,5));var p=player(h,origin);var freeze=source();var grab=source();var point=new Vec3[]{origin};PlayerFreeze.apply(p,freeze,25);h.assertTrue(PlayerFreeze.grab(p,grab,20,v->point[0]),"Safe attachment rejected");
        h.runAfterDelay(2,()->point[0]=origin.add(.5,1,0));h.runAfterDelay(5,()->{PlayerFreeze.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));var moved=PlayerFreeze.constrain(p,new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(0,0,0,0,0,false));h.assertTrue(new Vec3(moved.getX(0),moved.getY(0),moved.getZ(0)).distanceTo(point[0])<1e-8&&PlayerFreeze.attached(p),"Attached constraint did not follow (FakePlayer teleport is a no-op)");PlayerFreeze.release(h.getLevel().getServer(),grab);h.assertTrue(PlayerFreeze.frozen(p)&&!PlayerFreeze.attached(p),"Grab release erased another freeze");var packet=PlayerFreeze.constrain(p,new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(0,0,0,42,12,false));h.assertTrue(Math.abs(packet.getY(0)-point[0].y)<1e-8,"Freeze snapped back to original position after release");PlayerFreeze.release(h.getLevel().getServer(),freeze);p.discard();h.succeed();});
    }
    @GameTest(template="combat",timeoutTicks=30) public static void AttachmentReleasesBeforeWallAndDoesNotTunnel(GameTestHelper h){
        var origin=h.absoluteVec(new Vec3(5.5,8,5.5));var p=player(h,origin);var target=new Vec3[]{origin};var source=source();h.assertTrue(PlayerFreeze.grab(p,source,25,v->target[0]),"Initial attachment failed");
        h.getLevel().setBlockAndUpdate(BlockPos.containing(origin.add(1,0,0)),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        h.runAfterDelay(2,()->target[0]=origin.add(3,0,0));h.runAfterDelay(5,()->{h.assertTrue(!PlayerFreeze.frozen(p)&&p.position().distanceTo(origin)<.1,"Grab tunneled through a wall or kept control");p.discard();h.succeed();});
    }
    @GameTest(template="combat",timeoutTicks=30) public static void InvalidAttachmentAndLargeJumpRelease(GameTestHelper h){
        var origin=h.absoluteVec(new Vec3(5,8,5));var p=player(h,origin);var point=new Vec3[]{origin};h.assertTrue(PlayerFreeze.grab(p,source(),20,v->point[0]),"Initial attachment failed");h.assertTrue(!PlayerFreeze.grab(p,source(),20,v->origin),"Second grab stole held player");
        h.runAfterDelay(2,()->point[0]=origin.add(20,0,0));h.runAfterDelay(5,()->{h.assertTrue(!PlayerFreeze.frozen(p),"Large pose jump retained attachment");point[0]=origin;h.assertTrue(PlayerFreeze.grab(p,source(),20,v->point[0]),"Re-grab after release failed");point[0]=null;});
        h.runAfterDelay(8,()->{h.assertTrue(!PlayerFreeze.frozen(p),"Missing source retained player");p.discard();h.succeed();});
    }
    @GameTest(template="combat",timeoutTicks=30) public static void MarkedBodyRotationMovesHeldPoint(GameTestHelper h){
        var c=SlashGuardGameTests.core(h);var m=c.modules().add("grab");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("grabOffsetX",.5,"");var before=ModuleGrab.holdPoint(c,m);var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)m.anchors().getFirst().body(c);var position=new org.joml.Vector3d(body.logicalPose().position());var rot=new org.joml.Quaterniond().rotationY(Math.PI/2);
        dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(h.getLevel()).getPipeline().teleport(body,new org.joml.Vector3d(position).add(.5,0,0),rot);
        h.runAfterDelay(4,()->{var mark=m.anchors().getFirst();var local=mark.position(body).getCenter().add(.5,0,2.05);var expected=body.logicalPose().transformPosition(local);h.assertTrue(ModuleGrab.holdPoint(c,m).distanceTo(expected)<1e-8&&ModuleGrab.holdPoint(c,m).distanceTo(before)>.5,"Hold point did not inherit body pose");h.succeed();});
    }
    @GameTest(template="combat",timeoutTicks=30) public static void ActualGrabCapturesNearestAndStopsWhenDisabled(GameTestHelper h){
        var c=SlashGuardGameTests.core(h);var m=c.modules().add("grab");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("grabRadius",2,"");
        c.tryActivate(TestActors.creative(h.getLevel()));var point=ModuleGrab.holdPoint(c,m);var near=player(h,point.subtract(0,.9,0));var far=player(h,point.add(1.5,-.9,0));
        h.assertTrue(ModuleGrab.capture(h.getLevel(),c,m)==1&&PlayerFreeze.attached(near)&&!PlayerFreeze.attached(far),"Grab did not select exactly the nearest player");
        m.edit("enabled",0,"");h.runAfterDelay(2,()->{h.assertTrue(!PlayerFreeze.frozen(near),"Disabled source still attached");near.discard();far.discard();h.succeed();});
    }
}
