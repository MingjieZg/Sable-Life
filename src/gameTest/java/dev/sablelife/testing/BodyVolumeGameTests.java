package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.attack.AttackVolume;
import dev.sablelife.combat.BodyAttackVolumes;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.joml.Quaterniond;
import java.util.List;

@GameTestHolder("sablelife_audit_tests") @PrefixGameTestTemplate(false)
public final class BodyVolumeGameTests {
    @GameTest(template="combat",batch="attack_volume_diagonal")
    public static void ThinDiagonalWeaponRejectsEmptyCornersOfItsBounds(GameTestHelper h) {
        var weapon=new AttackVolume(Vec3.ZERO,new Vec3(2,0,2),new Vec3(0,.1,0),new Vec3(.05,0,-.05));
        var corner=AttackVolume.box(new Vec3(1.5,0,-1.5),new Vec3(.1,.1,.1),new Quaterniond());
        h.assertTrue(weapon.bounds().intersects(corner.bounds())&&!weapon.intersects(corner)&&!corner.intersects(weapon),"Thin weapon falsely hit enclosing-box corner");
        h.assertTrue(weapon.intersects(AttackVolume.box(new Vec3(1.5,0,1.5),new Vec3(.1,.1,.1),new Quaterniond())),"Thin weapon missed its actual center line");h.succeed();
    }
    @GameTest(template="combat",batch="attack_volume_rotation")
    public static void RotatedVoxelAndInvalidSamplesHaveExplicitBoundaries(GameTestHelper h) {
        var body=AttackVolume.box(Vec3.ZERO,new Vec3(1,.5,.2),new Quaterniond().rotationY(Math.PI/2));
        h.assertTrue(body.intersects(AttackVolume.box(new Vec3(0,0,.8),new Vec3(.1,.1,.1),new Quaterniond()))&&!body.intersects(AttackVolume.box(new Vec3(.8,0,0),new Vec3(.1,.1,.1),new Quaterniond())),"Rotated body axes were ignored");
        h.assertTrue(!new AttackVolume(new Vec3(Double.NaN,0,0),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)).valid()&&!AttackVolume.box(Vec3.ZERO,new Vec3(129,1,1),new Quaterniond()).valid(),"Unbounded server samples accepted");h.succeed();
    }

    @GameTest(template="combat",batch="body_attack_gap")
    public static void AttackVolumesRejectBodyGapsAndEarlierTerrainWalls(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));var end=p.east(4);
        level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(end,Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,end),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),end.getX(),p.getY(),p.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.tryActivate(TestActors.creative(level));
        h.runAfterDelay(4,()->{
        var center=SableBodyProbe.toWorld(level,core.getBlockPos().getCenter());var eye=center.add(0,0,-4);
        var gap=AttackVolume.box(center.add(2,0,0),new Vec3(.4,.4,.4),new Quaterniond());
        h.assertTrue(BodyAttackVolumes.query(level,eye,List.of(gap)).isEmpty(),"Enclosing bounds falsely hit an empty body gap");
        var hit=AttackVolume.box(center,new Vec3(.4,.4,.4),new Quaterniond());
        h.assertTrue(BodyAttackVolumes.query(level,eye,List.of(hit)).size()==1,"Real owned voxel was not selected");
        var wall=BlockPos.containing(center.add(0,0,-2));level.setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
        h.assertTrue(BodyAttackVolumes.query(level,eye,List.of(hit)).isEmpty(),"Body attack went through earlier terrain");h.succeed();
        });
    }
}
