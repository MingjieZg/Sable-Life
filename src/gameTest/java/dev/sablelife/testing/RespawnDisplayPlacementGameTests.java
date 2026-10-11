package dev.sablelife.testing;
import dev.sablelife.ownership.RespawnDisplayPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("sablelife_respawn_tests") @PrefixGameTestTemplate(false)
public final class RespawnDisplayPlacementGameTests {
 @GameTest(template="combat",batch="display_ground") public static void HighAnchorDisplaysAboveGroundAndNotCeiling(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,15,3));l.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());var anchor=Vec3.atCenterOf(p.above(20));l.setBlockAndUpdate(p.above(40),Blocks.STONE.defaultBlockState());var actual=RespawnDisplayPlacement.locate(l,anchor);h.assertTrue(actual!=null&&actual.x==anchor.x&&actual.z==anchor.z&&actual.y==p.getY()+3.5,"Timer did not choose accessible surface below anchor");h.assertTrue(anchor.y==p.getY()+20.5,"Placement mutated original anchor");h.succeed();}
 @GameTest(template="combat",batch="display_slab") public static void SlabTopUsesItsActualCollisionHeight(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,15,3));l.setBlockAndUpdate(p,Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));var actual=RespawnDisplayPlacement.locate(l,Vec3.atCenterOf(p.above(20)));h.assertTrue(actual!=null&&actual.y==p.getY()+3,"Slab surface treated as a full cube");h.succeed();}
 @GameTest(template="combat",batch="display_void") public static void VoidKeepsAnchorFallbackAndMissingChunksStayUnloaded(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,15,3));for(int y=l.getMinBuildHeight();y<l.getMaxBuildHeight();y++)l.setBlockAndUpdate(new BlockPos(p.getX(),y,p.getZ()),Blocks.AIR.defaultBlockState());var anchor=Vec3.atCenterOf(p);h.assertTrue(RespawnDisplayPlacement.locate(l,anchor).equals(anchor.add(0,2.5,0)),"Void fallback sank the display");h.assertTrue(RespawnDisplayPlacement.locate(l,new Vec3(1000000,80,1000000))==null&&!l.hasChunk(1000000>>4,1000000>>4),"Display loaded missing chunks");h.succeed();}
}
