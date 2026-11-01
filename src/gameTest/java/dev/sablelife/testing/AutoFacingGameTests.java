package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.attack.ContactBox;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.joml.Quaterniond;
import java.util.*;

@GameTestHolder("sablelife_facing_tests")
@PrefixGameTestTemplate(false)
public final class AutoFacingGameTests {
    @GameTest(template="combat",batch="facing_collision_off",timeoutTicks=35)
    public static void CollisionOffForcesPitchEvenWhenGroundBlocksTurn(GameTestHelper h){
        var core=fixture(h);for(var pos:BlockPos.betweenClosed(h.absolutePos(new BlockPos(0,5,0)),h.absolutePos(new BlockPos(9,5,9))))h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        var m=core.modules().add("auto_facing");m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.NORTH)));m.edit("target",1,"");m.edit("facingVertical",1,"");h.assertTrue(m.value("facingCollision")==1,"Old/new default no longer safe");m.edit("facingCollision",0,"");h.assertTrue(VirtualModule.load(m.save()).value("facingCollision")==0,"Collision toggle lost on save");
        var pivot=SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(pivot.add(6,8,0));h.getLevel().addFreshEntity(cow);core.tryActivate(TestActors.creative(h.getLevel()));
        h.runAfterDelay(12,()->{var front=ContactBox.rotate(new Quaterniond(m.anchors().getFirst().body(core).logicalPose().orientation()),new Vec3(0,0,-1));h.assertTrue(front.y>.2,"Off retained pitch collision veto: "+front);cow.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="facing_sight_life")
    public static void LifeCoreFacingSkipsOccludedTargetsAndItsOwnBody(GameTestHelper h){sightCheck(h,false);}
    @GameTest(template="combat",batch="facing_sight_control")
    public static void RedstoneCoreFacingSkipsOccludedTargetsAndItsOwnBody(GameTestHelper h){sightCheck(h,true);}
    private static void sightCheck(GameTestHelper h,boolean control){
        var heart=fixture(h,control);var module=heart.modules().add("auto_facing");module.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos(),Direction.NORTH)));module.edit("target",1,"");
        var level=h.getLevel();var pivot=SableBodyProbe.toWorld(level,heart.getBlockPos().getCenter());
        var near=EntityType.COW.create(level);near.setNoAi(true);near.setNoGravity(true);near.setPos(pivot.add(2,0,0));level.addFreshEntity(near);
        var far=EntityType.COW.create(level);far.setNoAi(true);far.setNoGravity(true);far.setPos(pivot.add(0,0,-2.5));level.addFreshEntity(far);
        var wall=BlockPos.containing(pivot).east(1);for(int y=0;y<3;y++)for(int z=-1;z<=1;z++)level.setBlockAndUpdate(wall.offset(0,y,z),Blocks.STONE.defaultBlockState());
        h.assertTrue(module.value("lineOfSight")==0&&ModuleAutoFacing.selectTarget(level,heart,module,pivot)==near,"Default Off did not retain through-wall selection");
        module.edit("lineOfSight",1,"");h.assertTrue(ModuleAutoFacing.selectTarget(level,heart,module,pivot)==far,"Closer hidden target won, or own north block blocked visible target");
        far.discard();h.assertTrue(ModuleAutoFacing.selectTarget(level,heart,module,pivot)==null,"All hidden targets still selected");
        if(!control)heart.health().activate();heart.modules().tick(heart);var row=heart.modules().snapshot(heart,true).getList("Modules",10).getCompound(0);h.assertTrue(row.getString("FacingState").equals("no_target"),"Runtime rotated toward an occluded target");
        for(int y=0;y<3;y++)for(int z=-1;z<=1;z++)level.setBlockAndUpdate(wall.offset(0,y,z),Blocks.AIR.defaultBlockState());
        h.assertTrue(ModuleAutoFacing.selectTarget(level,heart,module,pivot)==near,"Removing obstruction did not restore selection");
        var saved=module.save();h.assertTrue(VirtualModule.load(saved).value("lineOfSight")==1,"Sight option failed to save");saved.getCompound("Settings").remove("lineOfSight");h.assertTrue(VirtualModule.load(saved).value("lineOfSight")==0,"Old module did not default to Off");near.discard();h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=45)
    public static void GroundBlockedPitchDoesNotStopHorizontalTracking(GameTestHelper h){
        var heart=fixture(h);for(var pos:BlockPos.betweenClosed(h.absolutePos(new BlockPos(0,5,0)),h.absolutePos(new BlockPos(9,5,9))))h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        var module=heart.modules().add("auto_facing");module.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos(),Direction.NORTH)));module.edit("target",1,"");module.edit("facingRange",10,"");module.edit("facingVertical",1,"");
        var pivot=SableBodyProbe.toWorld(h.getLevel(),heart.getBlockPos().getCenter());var desired=new Vec3(6,7.7,0);var bodies=LinearMovementGeometry.capture(heart);double radius=AutoFacingGeometry.radius(bodies,pivot);
        h.assertTrue(!AutoFacingGeometry.clearTurn(h.getLevel(),bodies,pivot,AutoFacingGeometry.turn(new Vec3(0,0,-1),desired,true,9),radius),"Fixture did not block combined pitch");
        h.assertTrue(AutoFacingGeometry.clearTurn(h.getLevel(),bodies,pivot,AutoFacingGeometry.turn(new Vec3(0,0,-1),desired,false,9),radius),"Fixture incorrectly blocked yaw");
        var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(pivot.x+6,pivot.y+7,pivot.z);h.getLevel().addFreshEntity(cow);
        var author=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"GroundFacingAuthor"));author.setGameMode(net.minecraft.world.level.GameType.CREATIVE);heart.tryActivate(author);
        h.runAfterDelay(18,()->{var anchor=module.anchors().getFirst();var front=ContactBox.rotate(new Quaterniond(anchor.body(heart).logicalPose().orientation()),new Vec3(0,0,-1));
            h.assertTrue(front.x>.9,"Blocked pitch prevented yaw tracking");var state=heart.modules().snapshot(heart,true).getList("Modules",10).getCompound(0).getString("FacingState");h.assertTrue(state.equals("vertical_blocked"),"Partial-block state missing");cow.discard();h.succeed();});
    }
    @GameTest(template="combat")
    public static void AlignmentReverseSwapsOppositeFacesWithoutChangingStrength(GameTestHelper h){
        var heart=fixture(h);var module=new VirtualModule("navigation");var anchor=ModuleAnchor.create(heart,heart.getBlockPos(),Direction.NORTH);module.anchors(List.of(anchor));module.edit("target",1,"");module.edit("lineOfSight",1,"");
        h.assertTrue(module.value("navigationInvert")==0,"Legacy alignment defaults changed");var center=anchor.world(heart,false);var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(center.x-4,center.y+2,center.z-6);h.getLevel().addFreshEntity(cow);
        var control=new TargetNavigation();var normal=new HashMap<ModuleSignals.Face,Integer>();control.tick(h.getLevel(),heart,module,normal);module.edit("navigationInvert",1,"");var reversed=new HashMap<ModuleSignals.Face,Integer>();control.tick(h.getLevel(),heart,module,reversed);
        var pos=anchor.position(anchor.body(heart));h.assertTrue(normal.get(new ModuleSignals.Face(pos,Direction.WEST))>0&&normal.get(new ModuleSignals.Face(pos,Direction.EAST))==0,"Fixture did not request a left turn");
        for(var face:Direction.values())h.assertTrue(normal.get(new ModuleSignals.Face(pos,face)).equals(reversed.get(new ModuleSignals.Face(pos,face.getOpposite()))),"Reverse changed strength or did not swap "+face);
        h.assertTrue(VirtualModule.load(module.save()).value("navigationInvert")==1,"Reverse setting failed to save");
        cow.discard();var empty=new HashMap<ModuleSignals.Face,Integer>();control.tick(h.getLevel(),heart,module,empty);h.assertTrue(empty.values().stream().allMatch(p->p==0),"No target inversion produced a signal");h.succeed();
    }
    @GameTest(template="combat")
    public static void FacingGeometryHandlesAxesSpeedAndVerticalFront(GameTestHelper h){
        for(var face:Direction.values())if(face.getAxis().isHorizontal()){
            var front=Vec3.atLowerCornerOf(face.getNormal());var target=new Vec3(1,0,2).normalize();
            h.assertTrue(ContactBox.rotate(AutoFacingGeometry.turn(front,target,false,180),front).distanceTo(target)<1e-8,"Horizontal front failed "+face);
        }
        var front=new Vec3(0,0,-1);var turn=AutoFacingGeometry.turn(front,new Vec3(1,50,0),false,9);
        h.assertTrue(Math.abs(Math.toDegrees(2*Math.acos(Math.abs(turn.w)))-9)<1e-7&&Math.abs(ContactBox.rotate(turn,front).y)<1e-8,"Speed cap or horizontal tracking failed");
        var up=new Vec3(0,1,0);var target=new Vec3(1,2,3).normalize();h.assertTrue(ContactBox.rotate(AutoFacingGeometry.turn(up,target,true,180),up).distanceTo(target)<1e-8,"Vertical front failed");
        h.assertTrue(Math.abs(AutoFacingGeometry.turn(up,Vec3.ZERO,true,180).w-1)<1e-8,"Degenerate target rotated body");
        var bounds=AutoFacingGeometry.rotatedBox(new net.minecraft.world.phys.AABB(-1,-2,-3,1,2,3),new Vec3(5,6,7),new Quaterniond().rotateY(Math.PI/2));
        h.assertTrue(Math.abs(bounds.getYsize()-4)<1e-8&&Math.abs(bounds.getXsize()-6)<1e-8&&Math.abs(bounds.getZsize()-2)<1e-8,"Rotated collision bounds failed");h.succeed();
    }
    private static HeartBlockEntity fixture(GameTestHelper h){
        return fixture(h,false);
    }
    private static HeartBlockEntity fixture(GameTestHelper h,boolean control){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,6,3));level.setBlockAndUpdate(pos,(control?SableLife.REDSTONE_CORE.get():SableLife.HEART.get()).defaultBlockState());level.setBlockAndUpdate(pos.north(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.north()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ()-1,pos.getX(),pos.getY(),pos.getZ()));return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    @GameTest(template="combat",timeoutTicks=45)
    public static void ActiveMachineTurnsMarkedFaceTowardConfiguredMob(GameTestHelper h){
        var heart=fixture(h);var module=heart.modules().add("auto_facing");module.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos(),Direction.NORTH)));module.edit("target",1,"");module.edit("facingRange",16,"");module.edit("facingSpeed",720,"");
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"FacingAuthor"));player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var pivot=SableBodyProbe.toWorld(h.getLevel(),heart.getBlockPos().getCenter());var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(pivot.x+6,pivot.y,pivot.z);h.getLevel().addFreshEntity(cow);
        heart.tryActivate(player);h.assertTrue(heart.health().state()==HeartState.ALIVE,"Activation failed");
        h.runAfterDelay(15,()->{
            var anchor=module.anchors().getFirst();var front=ContactBox.rotate(new Quaterniond(anchor.body(heart).logicalPose().orientation()),new Vec3(0,0,-1));
            var now=SableBodyProbe.toWorld(h.getLevel(),heart.getBlockPos().getCenter());var direction=cow.getBoundingBox().getCenter().subtract(now);direction=new Vec3(direction.x,0,direction.z).normalize();
            h.assertTrue(front.dot(direction)>.98,"Marked face did not turn toward mob target");
            var saved=VirtualModule.load(module.save());h.assertTrue(saved.type().equals("auto_facing")&&saved.value("target")==1&&saved.anchors().equals(module.anchors()),"Facing configuration/mark failed to save");
            heart.resetForCreative(player);heart.modules().tick(heart);h.assertTrue(!HeartModules.triggered(module,heart),"Dormant controller remained active");cow.discard();h.succeed();
        });
    }
    @GameTest(template="combat")
    public static void TargetTypeCreativeOptionAndTerrainGuardRemainEffective(GameTestHelper h){
        var heart=fixture(h);var module=new VirtualModule("auto_facing");var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"FacingTarget"));player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        h.assertTrue(ModuleAutoFacing.eligible(player,module),"Creative player option did not work");module.edit("target",1,"");h.assertTrue(!ModuleAutoFacing.eligible(player,module),"Mobs target accepted a player");module.edit("target",0,"");module.edit("facingCreative",0,"");h.assertTrue(!ModuleAutoFacing.eligible(player,module),"Disabled creative option ignored");
        var bodies=LinearMovementGeometry.capture(heart);var pivot=SableBodyProbe.toWorld(h.getLevel(),heart.getBlockPos().getCenter());var rotated=AutoFacingGeometry.rotated(bodies,pivot,new Quaterniond().rotateY(Math.PI/2));
        var box=rotated.getFirst().box();var obstruction=BlockPos.containing(box.getCenter());h.getLevel().setBlockAndUpdate(obstruction,Blocks.STONE.defaultBlockState());
        h.assertTrue(!LinearMovementGeometry.clear(h.getLevel(),rotated,Vec3.ZERO,Vec3.ZERO,false),"Rotation ignored terrain obstacle");h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=30)
    public static void CompletedTeleportIsNotOverwrittenByFacingInTheSameTick(GameTestHelper h){
        var heart=fixture(h);var facing=heart.modules().add("auto_facing");var movement=heart.modules().add("linear_movement");
        var anchor=ModuleAnchor.create(heart,heart.getBlockPos(),Direction.NORTH);facing.anchors(List.of(anchor));movement.anchors(List.of(anchor));facing.edit("target",1,"");
        movement.edit("trigger",0,"");movement.edit("chargeTime",0,"");movement.edit("moveDistance",3,"");movement.edit("direction",6,"");
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"FacingMovementAuthor"));player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var start=SableBodyProbe.toWorld(h.getLevel(),heart.getBlockPos().getCenter());var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(start.x,start.y,start.z+6);h.getLevel().addFreshEntity(cow);
        heart.tryActivate(player);heart.modules().tick(heart);
        var tags=heart.modules().snapshot(heart,true).getList("Modules",10);h.assertTrue(tags.getCompound(0).getString("FacingState").equals("busy"),"Facing ran during the completing movement tick");
        h.runAfterDelay(5,()->{var end=SableBodyProbe.toWorld(h.getLevel(),heart.getBlockPos().getCenter());h.assertTrue(Math.abs(end.x-start.x-3)<.1,"Facing overwrote teleport position");cow.discard();h.succeed();});
    }
}
