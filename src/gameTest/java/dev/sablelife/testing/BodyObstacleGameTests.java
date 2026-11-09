package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

@GameTestHolder("sablelife_rotation_obstacle_tests") @PrefixGameTestTemplate(false)
public final class BodyObstacleGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h,int index,double frameAngle,boolean solidContact,boolean emptyCollision){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(4+index*80,80,4));
        level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(p.north(),Blocks.IRON_BLOCK.defaultBlockState());
        var own=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.north()),new BoundingBox3i(p.getX(),p.getY(),p.getZ()-1,p.getX(),p.getY(),p.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(own.getPlot().getCenterBlock());
        var blocks=new ArrayList<BlockPos>();
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)if(Math.abs(x)==10||Math.abs(z)==10){var at=p.offset(x,0,z);level.setBlockAndUpdate(at,Blocks.IRON_BLOCK.defaultBlockState());blocks.add(at);}
        if(solidContact||emptyCollision){var at=p.west();level.setBlock(at,(solidContact?Blocks.STONE:Blocks.TORCH).defaultBlockState(),2);blocks.add(at);}
        var foreign=SubLevelAssemblyHelper.assembleBlocks(level,blocks.getFirst(),blocks,new BoundingBox3i(p.getX()-10,p.getY(),p.getZ()-10,p.getX()+10,p.getY(),p.getZ()+10));
        if(frameAngle!=0){
            var q=new Quaterniond().rotateY(Math.toRadians(frameAngle));var pivot=p.getCenter();var old=foreign.logicalPose().position();var relative=new Vec3(old.x()-pivot.x,old.y()-pivot.y,old.z()-pivot.z);var next=pivot.add(dev.sablelife.attack.ContactBox.rotate(q,relative));
            dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level).getPipeline().teleport(foreign,new Vector3d(next.x,next.y,next.z),q.mul(new Quaterniond(foreign.logicalPose().orientation())));
        }
        return core;
    }
    @GameTest(template="combat")
    public static void RemoteFrameEmptySpaceDoesNotBlockRotationOrTranslation(GameTestHelper h){
        var core=fixture(h,0,0,false,false);var bodies=LinearMovementGeometry.capture(core);var pivot=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());
        h.assertTrue(AutoFacingGeometry.clearTurn(h.getLevel(),bodies,pivot,new Quaterniond().rotateY(Math.PI/2),AutoFacingGeometry.radius(bodies,pivot)),"Remote frame's empty bounding box blocked rotation");
        h.assertTrue(LinearMovementGeometry.clear(h.getLevel(),bodies,Vec3.ZERO,new Vec3(2,0,0),true),"Remote frame's empty bounding box blocked Travel");h.succeed();
    }
    @GameTest(template="combat")
    public static void RotatedRemoteFrameStillLeavesItsInteriorEmpty(GameTestHelper h){
        var core=fixture(h,1,45,false,false);var bodies=LinearMovementGeometry.capture(core);var pivot=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());
        h.assertTrue(AutoFacingGeometry.clearTurn(h.getLevel(),bodies,pivot,new Quaterniond().rotateY(.2),AutoFacingGeometry.radius(bodies,pivot)),"Rotated remote frame's air blocked yaw");h.succeed();
    }
    @GameTest(template="combat")
    public static void NearbyRealForeignBodyVoxelStillStopsTheTurn(GameTestHelper h){
        var core=fixture(h,2,0,true,false);var bodies=LinearMovementGeometry.capture(core);var pivot=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());
        h.assertTrue(!AutoFacingGeometry.clearTurn(h.getLevel(),bodies,pivot,new Quaterniond().rotateY(Math.PI/2),AutoFacingGeometry.radius(bodies,pivot)),"Turn passed through real foreign stone");h.succeed();
    }
    @GameTest(template="combat")
    public static void ForeignDecorationWithoutCollisionDoesNotBecomeASolidBlock(GameTestHelper h){
        var core=fixture(h,3,0,false,true);var bodies=LinearMovementGeometry.capture(core);var pivot=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());
        h.assertTrue(AutoFacingGeometry.clearTurn(h.getLevel(),bodies,pivot,new Quaterniond().rotateY(Math.PI/2),AutoFacingGeometry.radius(bodies,pivot)),"Non-colliding foreign decoration blocked yaw");h.succeed();
    }
}
