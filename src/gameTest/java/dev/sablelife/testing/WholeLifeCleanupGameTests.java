package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.ownership.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_whole_cleanup_tests") @PrefixGameTestTemplate(false)
public final class WholeLifeCleanupGameTests {
 private static ServerSubLevel body(GameTestHelper h,int offset,int size,boolean heart){
  var l=h.getLevel();for(int x=-4;x<=8;x++)for(int z=-4;z<=4;z++)l.setChunkForced(x,z,true);var p=h.absolutePos(new BlockPos(5+offset,35,5));var positions=new ArrayList<BlockPos>();
  for(int x=0;x<size;x++)for(int y=0;y<size;y++)for(int z=0;z<size;z++){var pos=p.offset(x,y,z);positions.add(pos);l.setBlockAndUpdate(pos,heart&&x==0&&y==0&&z==0?SableLife.HEART.get().defaultBlockState():Blocks.IRON_BLOCK.defaultBlockState());}
  return SubLevelAssemblyHelper.assembleBlocks(l,p,positions,new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+size-1,p.getY()+size-1,p.getZ()+size-1));
 }
 private static void bind(GameTestHelper h,ServerSubLevel body,UUID owner){
  var data=BodyOwnership.get(h.getLevel());for(var holder:body.getPlot().getLoadedChunks())for(var section:holder.getChunk().getSections())if(!section.hasOnlyAir()){
   int index=Arrays.asList(holder.getChunk().getSections()).indexOf(section);int bottom=holder.getChunk().getSectionYFromSectionIndex(index)<<4;
   for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)if(!section.getBlockState(x,y,z).isAir())data.bind(new BlockPos(holder.getChunk().getPos().getMinBlockX()+x,bottom+y,holder.getChunk().getPos().getMinBlockZ()+z),owner);
  }
 }
 @GameTest(template="combat",batch="whole_large",timeoutTicks=200) public static void LargeDisconnectedPartsAllDisappearInOneTickAfterDelay(GameTestHelper h){
  var main=body(h,0,32,true);var a=body(h,36,2,false);var b=body(h,42,2,false);var sentinel=body(h,48,1,false);var all=List.of(main,a,b);
  var core=(HeartBlockEntity)h.getLevel().getBlockEntity(main.getPlot().getCenterBlock());h.assertTrue(core.configure(100,10)&&core.activateForRespawn(),"Core fixture failed");for(var part:all)bind(h,part,core.creatureId());core.forceDeath();
  var checked=new boolean[]{false};h.onEachTick(()->{if(checked[0])return;long removed=all.stream().filter(ServerSubLevel::isRemoved).count();h.assertTrue(removed==0||removed==all.size(),"Whole life visibly lost only some parts on a tick");if(removed==0)return;checked[0]=true;h.assertTrue(!sentinel.isRemoved(),"Nearby unrelated body deleted");h.assertTrue(!BodyOwnership.get(h.getLevel()).hasOwned(core.creatureId()),"Whole cleanup retained ownership");h.succeed();});
 }
 @GameTest(template="combat",batch="whole_mixed",timeoutTicks=100) public static void MixedOwnershipFallsBackAndKeepsForeignBlock(GameTestHelper h){
  var main=body(h,0,2,true);var core=(HeartBlockEntity)h.getLevel().getBlockEntity(main.getPlot().getCenterBlock());h.assertTrue(core.configure(100,0)&&core.activateForRespawn(),"Core fixture failed");
  var foreign=core.getBlockPos().east();var data=BodyOwnership.get(h.getLevel());var stranger=UUID.randomUUID();data.forget(foreign);data.bind(foreign,stranger);h.assertTrue(stranger.equals(data.owner(foreign)),"Mixed fixture did not create foreign ownership");core.forceDeath();
  h.succeedWhen(()->{h.assertTrue(LifeLifecycle.get(h.getLevel().getServer()).finished(core.creatureId()),"Waiting for fallback cleanup");h.assertTrue(!main.isRemoved()&&h.getLevel().getBlockState(foreign).is(Blocks.IRON_BLOCK)&&stranger.equals(data.owner(foreign)),"Foreign block or mixed plot deleted: removed="+main.isRemoved()+" state="+h.getLevel().getBlockState(foreign)+" owner="+data.owner(foreign)+" expected="+stranger);});
 }
 @GameTest(template="combat",batch="whole_wait_reload",timeoutTicks=150) public static void UnloadedOwnershipWaitsAcrossSavedDataReloadWithoutPartialRemoval(GameTestHelper h){
  var main=body(h,0,2,true);var limb=body(h,5,2,false);var core=(HeartBlockEntity)h.getLevel().getBlockEntity(main.getPlot().getCenterBlock());h.assertTrue(core.configure(100,0)&&core.activateForRespawn(),"Core fixture failed");bind(h,limb,core.creatureId());
  var unknown=new BlockPos(1000000,80,1000000);BodyOwnership.get(h.getLevel()).bind(unknown,core.creatureId());core.forceDeath();
  h.runAfterDelay(4,()->{
   h.assertTrue(!main.isRemoved()&&!limb.isRemoved()&&!h.getLevel().hasChunkAt(unknown),"Waiting job partially deleted life or forced unknown chunks");
   var saved=LifeLifecycle.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess());h.getLevel().getServer().overworld().getDataStorage().set("sablelife_lifecycle",LifeLifecycle.load(saved,h.getLevel().registryAccess()));
   h.runAfterDelay(4,()->{h.assertTrue(!main.isRemoved()&&!limb.isRemoved(),"Reload bypassed unloaded validation");BodyOwnership.get(h.getLevel()).forget(unknown);});
   h.succeedWhen(()->{h.assertTrue(main.isRemoved()&&limb.isRemoved(),"Loaded ownership did not complete whole cleanup");h.assertTrue(!h.getLevel().hasChunkAt(unknown),"Retry forced unknown chunk");});
  });
 }
}
