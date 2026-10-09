package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("sablelife_cleanup_regression_tests") @PrefixGameTestTemplate(false)
public final class RemovalSafetyGameTests {
    @GameTest(template="combat",batch="live_body_split")
    public static void LiveBodiesStillPerformTheirNativeSplit(GameTestHelper h)throws Exception {
        var body=splitFixture(h);var container=SubLevelContainer.getContainer(h.getLevel());int count=container.getAllSubLevels().size();
        var split=body.getHeatMapManager().getClass().getDeclaredMethod("split");split.setAccessible(true);split.invoke(body.getHeatMapManager());
        h.assertTrue(!body.isRemoved()&&container.getAllSubLevels().size()>count,"Removal guard disabled legitimate live-body splitting");h.succeed();
    }
    @GameTest(template="combat",batch="removal_between_split_batches")
    public static void RemovalFromASplitListenerStopsBeforeAssembly(GameTestHelper h)throws Exception {
        var body=splitFixture(h);var container=SubLevelContainer.getContainer(h.getLevel());int count=container.getAllSubLevels().size();
        dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager.SplitListener listener=(level,bounds,blocks)->body.markRemoved();
        dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager.addSplitListener(listener);
        try {
            var split=body.getHeatMapManager().getClass().getDeclaredMethod("split");split.setAccessible(true);split.invoke(body.getHeatMapManager());
            h.assertTrue(body.isRemoved()&&container.getAllSubLevels().size()==count,"Split listener removal still started an assembly");
        } finally {
            var field=body.getHeatMapManager().getClass().getDeclaredField("LISTENERS");field.setAccessible(true);((java.util.Collection<?>)field.get(null)).remove(listener);
            container.removeSubLevel(body,SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }
    private static dev.ryanhcode.sable.sublevel.ServerSubLevel splitFixture(GameTestHelper h)throws Exception {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));for(int i=0;i<3;i++)level.setBlockAndUpdate(p.east(i),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east(),p.east(2)),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+2,p.getY(),p.getZ()));
        var manager=body.getHeatMapManager();var splitField=manager.getClass().getDeclaredField("subLevelSplits");splitField.setAccessible(true);var splits=(it.unimi.dsi.fastutil.longs.Long2IntMap)splitField.get(manager);splits.clear();
        var indexField=manager.getClass().getDeclaredField("splitIndexMap");indexField.setAccessible(true);var indexes=(it.unimi.dsi.fastutil.ints.IntList)indexField.get(manager);indexes.clear();indexes.add(0);indexes.add(1);indexes.add(2);
        splits.put(body.getPlot().getCenterBlock().east().asLong(),1);splits.put(body.getPlot().getCenterBlock().east(2).asLong(),2);
        var countField=manager.getClass().getDeclaredField("solidCount");countField.setAccessible(true);countField.setInt(manager,3);return body;
    }
    @GameTest(template="combat",batch="atomic_copycat_death",timeoutTicks=80)
    public static void LargeCopycatBodyDisappearsThroughOfficialRemovalWithoutMaterialsOrDuplicateRewards(GameTestHelper h)throws Exception {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));var blocks=new java.util.ArrayList<BlockPos>();
        boolean addon=net.neoforged.fml.ModList.get().isLoaded("copycats");
        var copycat=addon?net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("copycats:copycat_block")):com.simibubi.create.AllBlocks.COPYCAT_PANEL.get();
        h.assertTrue(copycat!=Blocks.AIR,"Copycats fixture block missing");
        for(int x=0;x<16;x++)for(int y=0;y<4;y++)for(int z=0;z<16;z++) {
            var pos=p.offset(x,y,z);blocks.add(pos);if(pos.equals(p)){level.setBlockAndUpdate(pos,dev.sablelife.SableLife.HEART.get().defaultBlockState());continue;}level.setBlockAndUpdate(pos,copycat.defaultBlockState());
            var entity=level.getBlockEntity(pos);
            entity.getClass().getMethod("setMaterial",net.minecraft.world.level.block.state.BlockState.class).invoke(entity,Blocks.IRON_BLOCK.defaultBlockState());
            entity.getClass().getMethod("setConsumedItem",net.minecraft.world.item.ItemStack.class).invoke(entity,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_BLOCK));
        }
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,blocks,new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+15,p.getY()+3,p.getZ()+15));
        var core=(dev.sablelife.heart.HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());var player=TestActors.creative(level);
        core.configure(1000,0);core.setDeathTemplate(player,0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD));core.tryActivate(player);
        var point=dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,core.getBlockPos().getCenter());var id=core.creatureId();
        var existingItems=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(point,point).inflate(20)).stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
        core.forceDeath();
        h.runAfterDelay(8,()->{
            h.assertTrue(body.isRemoved()&&SubLevelContainer.getContainer(level).getSubLevel(body.getUniqueId())==null,"Large body still registered instead of atomic removal");
            h.assertTrue(!dev.sablelife.ownership.BodyOwnership.get(level).hasOwned(id),"Deleted plot left ownership records");
            var items=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(point,point).inflate(20));
            h.assertTrue(items.stream().filter(e->e.getItem().is(net.minecraft.world.item.Items.EMERALD)).mapToInt(e->e.getItem().getCount()).sum()==1,"Custom reward missing or duplicated");
            h.assertTrue(items.stream().filter(e->!existingItems.contains(e.getUUID())).noneMatch(e->e.getItem().is(net.minecraft.world.item.Items.IRON_BLOCK)||e.getItem().getItem()==copycat.asItem()),"Copycat materials escaped bulk removal");
        });
        h.runAfterDelay(20,()->{
            h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(point,point).inflate(20)).stream().filter(e->e.getItem().is(net.minecraft.world.item.Items.EMERALD)).mapToInt(e->e.getItem().getCount()).sum()==1,"Later ticks duplicated reward");h.succeed();
        });
    }
    @GameTest(template="combat",batch="mixed_body_cleanup")
    public static void MixedOwnershipAndUnownedContentAreNeverDeletedAsAWhole(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));for(int i=0;i<3;i++)level.setBlockAndUpdate(p.east(i),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east(),p.east(2)),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+2,p.getY(),p.getZ()));
        var start=body.getPlot().getCenterBlock();var data=dev.sablelife.ownership.BodyOwnership.get(level);var own=java.util.UUID.randomUUID();var other=java.util.UUID.randomUUID();data.bind(start,own);data.bind(start.east(),other);
        data.cleanBudgeted(level,own,256,16384,false,null,true);
        h.assertTrue(SubLevelContainer.getContainer(level).getSubLevel(body.getUniqueId())==body&&!body.isRemoved(),"Mixed body was deleted");
        h.assertTrue(level.getBlockState(start).isAir()&&level.getBlockState(start.east()).is(Blocks.IRON_BLOCK)&&level.getBlockState(start.east(2)).is(Blocks.IRON_BLOCK)&&other.equals(data.owner(start.east())),"Cleanup erased foreign/unowned blocks");h.succeed();
    }
    @GameTest(template="combat",batch="part_body_cleanup")
    public static void PartCleanupCannotDeleteOtherPartsInTheSamePlot(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));for(int i=0;i<3;i++)level.setBlockAndUpdate(p.east(i),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east(),p.east(2)),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+2,p.getY(),p.getZ()));
        var start=body.getPlot().getCenterBlock();var data=dev.sablelife.ownership.BodyOwnership.get(level);var own=java.util.UUID.randomUUID();var part=java.util.UUID.randomUUID();for(int i=0;i<3;i++)data.bind(start.east(i),own);data.bindPart(start.east(),part);
        data.cleanBudgeted(level,part,256,16384,true,null,true);
        h.assertTrue(!body.isRemoved()&&!data.hasPart(part)&&data.hasOwned(own)&&level.getBlockState(start).is(Blocks.IRON_BLOCK)&&level.getBlockState(start.east(2)).is(Blocks.IRON_BLOCK),"Part cleanup erased parent content");h.succeed();
    }
    @GameTest(template="combat",batch="chunk_index_restore")
    public static void ReloadedOwnershipCanUseAtomicPlotRemoval(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));level.setBlockAndUpdate(p,Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));
        var pos=body.getPlot().getCenterBlock();var original=dev.sablelife.ownership.BodyOwnership.get(level);var id=java.util.UUID.randomUUID();original.bind(pos,id);
        var restored=dev.sablelife.ownership.BodyOwnership.load(original.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess());restored.cleanBudgeted(level,id,256,16384,false,null,true);
        h.assertTrue(body.isRemoved()&&!restored.hasOwned(id),"Reloaded chunk index did not permit plot removal");original.release(id);h.succeed();
    }
    @GameTest(template="combat",batch="removed_body_pending_split")
    public static void RemovedBodyNeverProcessesPendingSplitAssembly(GameTestHelper h)throws Exception {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));
        for(int i=0;i<3;i++)level.setBlockAndUpdate(p.east(i),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east(),p.east(2)),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+2,p.getY(),p.getZ()));
        var manager=body.getHeatMapManager();var splitField=manager.getClass().getDeclaredField("subLevelSplits");splitField.setAccessible(true);
        var splits=(it.unimi.dsi.fastutil.longs.Long2IntMap)splitField.get(manager);splits.clear();
        var indexField=manager.getClass().getDeclaredField("splitIndexMap");indexField.setAccessible(true);var indexes=(it.unimi.dsi.fastutil.ints.IntList)indexField.get(manager);indexes.clear();indexes.add(0);indexes.add(1);indexes.add(2);
        splits.put(body.getPlot().getCenterBlock().east().asLong(),1);splits.put(body.getPlot().getCenterBlock().east(2).asLong(),2);
        var countField=manager.getClass().getDeclaredField("solidCount");countField.setAccessible(true);countField.setInt(manager,3);
        body.markRemoved();var split=manager.getClass().getDeclaredMethod("split");split.setAccessible(true);boolean reproduced=false;
        try {split.invoke(manager);}catch(java.lang.reflect.InvocationTargetException error) {
            var cause=error.getCause();if(cause instanceof RuntimeException&&cause.getMessage()!=null&&cause.getMessage().contains("already removed sub-level"))reproduced=true;else throw error;
        }finally {SubLevelContainer.getContainer(level).removeSubLevel(body,SubLevelRemovalReason.REMOVED);}
        h.assertTrue(reproduced==Boolean.getBoolean("sablelife.expectCleanupCrash"),reproduced?"Removed body still enters split assembly":"Baseline did not reproduce the reported native exception");
        if(reproduced)System.out.println("Confirmed native baseline: Sub-level assembly attempted inside plot of already removed sub-level");h.succeed();
    }
}
