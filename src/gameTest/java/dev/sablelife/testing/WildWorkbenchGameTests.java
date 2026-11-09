package dev.sablelife.testing;

import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.ownership.WildWorkbenchProtection;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sablelife_workbench_tests") @PrefixGameTestTemplate(false)
public final class WildWorkbenchGameTests {
    @GameTest(template="combat",batch="workbench_protection",timeoutTicks=500)
    public static void WorkbenchIndexProtectsWholeFootprintsAndTracksEveryHeightWithoutRescanning(GameTestHelper h)throws Exception{
        var level=h.getLevel();int x=8192,z=8192,y=-55;
        h.assertTrue(SableLifeServerConfig.wildWorkbenchProtectionRadius()==64,"Default protection is not 64");
        for(int cx=(x>>4)-6;cx<=(x>>4)+6;cx++)for(int cz=(z>>4)-6;cz<=(z>>4)+6;cz++){level.setChunkForced(cx,cz,true);level.getChunk(cx,cz);}
        var footprint=new AABB(x,0,z,x+1,1,z+1);var index=WildWorkbenchProtection.get(level);
        h.assertTrue(WildWorkbenchProtection.check(level,footprint).equals("workbench_pending"),"Cold unknown terrain was declared safe");
        var table=new BlockPos(x+64,y,z);level.setBlockAndUpdate(table,Blocks.CRAFTING_TABLE.defaultBlockState());
        var source=new BlockPos(x+20,-40,z+20);level.setBlockAndUpdate(source,Blocks.IRON_BLOCK.defaultBlockState());
        var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level,source,java.util.List.of(source),
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(source.getX(),source.getY(),source.getZ(),source.getX(),source.getY(),source.getZ()));
        var pose=body.logicalPose().position();var template=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(level,new net.minecraft.world.phys.Vec3(pose.x(),pose.y(),pose.z()),java.util.List.of(body.getUniqueId())).save();
        var name="workbench_guard";var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(level.getServer(),name);
        java.nio.file.Files.createDirectories(path.getParent());net.minecraft.nbt.NbtIo.writeCompressed(template,path);
        var rules=new dev.sablelife.ownership.WildEncounters();rules.loadBlueprint(level,"",name,false);
        rules.configure(level,name,0,0,10,false,false,dev.sablelife.heart.FactionRegistry.WILDS,
                new dev.sablelife.ownership.WildEncounters.Conditions(true,true,false,java.util.Set.of(),0,true));
        var actor=TestActors.creative(level);actor.setYRot(0);actor.setXRot(0);actor.setPos(x+.5,-57,z-23.5);
        h.succeedWhen(()->{
            h.assertTrue(index.statistics().get("pending")==0,"Initial chunk indexing is still queued");
            h.assertTrue(WildWorkbenchProtection.check(level,footprint).equals("workbench"),"64-block table radius failed");
            try{var attempt=rules.attemptPreviewOrSpawn(actor,name,false);h.assertTrue(!attempt.success()&&attempt.reason().equals("placement_workbench"),"Test spawn / ignore bounds / clear obstacles bypassed protection: "+attempt.reason());}
            catch(java.io.IOException failure){throw new RuntimeException(failure);}
            h.assertTrue(WildWorkbenchProtection.check(level,footprint.move(0,250,0)).equals("workbench"),"Flying blueprint bypassed base protection");
            h.assertTrue(WildWorkbenchProtection.check(level,footprint.move(-2,0,0)).isEmpty(),"Outside radius was blocked");
            // Blueprint center is far away; its edge, rather than its center, enters the protected radius.
            h.assertTrue(WildWorkbenchProtection.check(level,new AABB(x-90,200,z,x+1,201,z+1)).equals("workbench"),"Large blueprint edge bypassed protection");
            var stacked=table.above(20);level.setBlockAndUpdate(stacked,Blocks.CRAFTING_TABLE.defaultBlockState());
            level.setBlockAndUpdate(table,Blocks.AIR.defaultBlockState());
            h.assertTrue(WildWorkbenchProtection.check(level,footprint).equals("workbench"),"Removing one of two vertical tables removed both");
            level.setBlockAndUpdate(stacked,Blocks.AIR.defaultBlockState());
            h.assertTrue(WildWorkbenchProtection.check(level,footprint).isEmpty(),"Last removed table left stale protection");
            long reads=index.statistics().get("blockReads"),probes=index.statistics().get("sectionProbes");
            for(int i=0;i<10000;i++)h.assertTrue(WildWorkbenchProtection.check(level,footprint).isEmpty(),"Cached query changed result");
            h.assertTrue(index.statistics().get("blockReads")==reads&&index.statistics().get("sectionProbes")==probes,"Repeated checks rescanned terrain");
            level.setBlockAndUpdate(table,Blocks.CRAFTING_TABLE.defaultBlockState());
            h.assertTrue(WildWorkbenchProtection.check(level,footprint).equals("workbench"),"Direct placement failed to update index");
            var saved=index.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
            var restored=WildWorkbenchProtection.load(saved,level.registryAccess());
            h.assertTrue(restored.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()).equals(saved),"Saved column index changed on reload");
            level.setBlockAndUpdate(table,Blocks.AIR.defaultBlockState());
            var radius=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)SableLifeServerConfig.SPEC.getValues().get("wildWorkbenchProtectionRadius");
            radius.set(0);try{h.assertTrue(WildWorkbenchProtection.check(level,footprint.move(20000,0,20000)).isEmpty(),"Disabled protection blocked unknown area");}finally{radius.set(64);}
            try{var attempt=rules.attemptPreviewOrSpawn(actor,name,false);h.assertTrue(attempt.success(),"Removing the last table did not allow actual spawning: "+attempt.reason());}
            catch(java.io.IOException failure){throw new RuntimeException(failure);}
        });
    }
}
