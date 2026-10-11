package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.ownership.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.decoration.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_decoration_cleanup_tests") @PrefixGameTestTemplate(false)
public final class DecorationCleanupGameTests {
 @GameTest(template="combat",batch="decorations_whole",timeoutTicks=100) public static void PaintingAndFramesClearWithoutRemovingWorldPainting(GameTestHelper h){
  var level=h.getLevel();for(int x=-2;x<3;x++)for(int z=-2;z<3;z++)level.setChunkForced(x,z,true);
  var p=h.absolutePos(new BlockPos(5,35,5));var positions=new ArrayList<BlockPos>();for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){var q=p.offset(x,y,z);positions.add(q);level.setBlockAndUpdate(q,q.equals(p)?SableLife.HEART.get().defaultBlockState():Blocks.STONE.defaultBlockState());}
  var body=SubLevelAssemblyHelper.assembleBlocks(level,p,positions,new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY()+1,p.getZ()+1));var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
  var anchor=core.getBlockPos();var painting=Painting.create(level,anchor.east(2),Direction.EAST).orElseThrow();h.assertTrue(level.addFreshEntity(painting),"Painting fixture failed");
  var frame=new ItemFrame(level,anchor.south(2),Direction.SOUTH);var glow=new GlowItemFrame(level,anchor.north(),Direction.NORTH);h.assertTrue(level.addFreshEntity(frame)&&level.addFreshEntity(glow),"Frame fixture failed");
  var support=h.absolutePos(new BlockPos(14,35,5));level.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());var outsider=Painting.create(level,support.east(),Direction.EAST).orElseThrow();level.addFreshEntity(outsider);
  h.assertTrue(core.configure(100,5)&&core.activateForRespawn(),"Core fixture failed");core.forceDeath();
  h.succeedWhen(()->{h.assertTrue(LifeLifecycle.get(level.getServer()).finished(core.creatureId()),"Waiting for native deletion");h.assertTrue(painting.isRemoved()&&frame.isRemoved()&&glow.isRemoved(),"Owned hanging decorations were kicked into the sky");h.assertTrue(!outsider.isRemoved(),"Unrelated world painting was removed");});
 }
 @GameTest(template="combat",batch="decorations_fallback") public static void BudgetedOwnedSupportClearsDecorationWithoutDroppingIt(GameTestHelper h){
  var level=h.getLevel();var support=h.absolutePos(new BlockPos(5,35,5));level.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());var painting=Painting.create(level,support.east(),Direction.EAST).orElseThrow();level.addFreshEntity(painting);
  var owner=UUID.randomUUID();var part=UUID.randomUUID();var data=BodyOwnership.get(level);data.bind(support,owner);data.bindPart(support,part);h.assertTrue(LifeLifecycle.get(level.getServer()).schedulePart(level,part,owner,"test",support.getCenter(),0,net.minecraft.resources.ResourceLocation.withDefaultNamespace("empty"),0),"Death fixture failed");
  data.cleanPart(level,part,1);h.assertTrue(painting.isRemoved(),"Fallback support cleanup left its painting");h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(support).inflate(3)).isEmpty(),"Decoration cleanup dropped an item");h.succeed();
 }
}
