package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class CoreCommandGameTests {
    @GameTest(template="combat",batch="command_catalog_cleanup")
    public static void RemovedAuthoringCommandsLeaveAdministratorRecoveryAvailable(GameTestHelper h){
        var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();var root=dispatcher.getRoot().getChild("sablelife");
        h.assertTrue(root!=null&&root.getChild("options")==null&&root.getChild("configure")==null,"Legacy authoring commands are still registered");
        h.assertTrue(root.getChild("inspect")!=null&&root.getChild("damage")!=null,"Administrator diagnostics were removed");
        var cleanup=root.getChild("cleanup");
        h.assertTrue(cleanup!=null&&cleanup.getChild("scan").getChild("all")!=null&&cleanup.getChild("apply").getChild("radius")!=null,"Residue recovery command disappeared");
        h.assertTrue(dispatcher.getRoot().getChild("sablelife_respawn").getChild("cancel")!=null,"Orphan respawn recovery disappeared");
        var source=h.getLevel().getServer().createCommandSourceStack();
        h.assertTrue(root.canUse(source.withPermission(2))&&!root.canUse(source.withPermission(0)),"Diagnostic root lost its operator restriction");
        h.succeed();
    }

    @GameTest(template="combat",batch="command_diagnostic_runtime")
    public static void RetainedInspectAndDamageWorkWithoutGrantingNonOperatorsAccess(GameTestHelper h)throws Exception{
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,8,4));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.tryActivate(TestActors.creative(level));
        var target=core.getBlockPos();String coordinates=target.getX()+" "+target.getY()+" "+target.getZ();
        var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2).withSuppressedOutput();var dispatcher=level.getServer().getCommands().getDispatcher();
        h.assertTrue(dispatcher.execute("sablelife inspect "+coordinates,source)==1,"Inspect no longer executes");
        float before=core.health().current();
        h.assertTrue(dispatcher.execute("sablelife damage "+coordinates+" 2",source)==1&&core.health().current()==before-2,"Damage diagnostic failed");
        boolean rejected=false;
        try{dispatcher.execute("sablelife damage "+coordinates+" 2",source.withPermission(0));}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){rejected=true;}
        h.assertTrue(rejected&&core.health().current()==before-2,"Non-operator diagnostic command changed health");
        h.succeed();
    }
}
