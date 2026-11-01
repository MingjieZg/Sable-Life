package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class CoreReleaseGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,8,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}
    @GameTest(template="combat",batch="settings_release_alive")
    public static void SettingsReleaseWorksWhileLiveAndPreservesConfigurationAndBody(GameTestHelper h){
        var c=fixture(h);var p=TestActors.creative(h.getLevel());p.setPos(ModuleArena.corePosition(c));c.configure(1500,60);c.editCore(p,"name",0,"Release test");var m=c.modules().add("particles");m.edit("name",0,"Remember me");m.edit("density",24,"");var moduleId=m.id();var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),c.getBlockPos());var bodyId=body.getUniqueId();c.tryActivate(p);c.damage(100);var creature=c.creatureId();var menu=new HeartSettingsMenu(1,p.getInventory(),c,3);
        h.assertTrue(!menu.snapshot().getBoolean("Editable")&&menu.snapshot().getBoolean("ReleaseAllowed"),"Live release was tied to dormant-only editing");
        h.assertTrue(menu.action(p,"release",0,"")&&c.health().state()==HeartState.DORMANT&&c.health().current()==1500&&!c.creatureId().equals(creature),"Settings Release did not reset life/runtime identity");
        h.assertTrue(c.creatureName().equals("Release test")&&c.modules().find(moduleId).value("density")==24&&c.modules().find(moduleId).name().equals("Remember me"),"Release lost authored settings");
        h.assertTrue(!body.isRemoved()&&body.getUniqueId().equals(bodyId)&&h.getLevel().getBlockState(c.getBlockPos().east()).is(Blocks.IRON_BLOCK),"Release dismantled the machine");h.succeed();
    }
    @GameTest(template="combat",batch="settings_release_permission")
    public static void SettingsReleaseRejectsSurvivalAndCanCancelDeathCountdown(GameTestHelper h){
        var c=fixture(h);var p=TestActors.creative(h.getLevel());p.setPos(ModuleArena.corePosition(c));c.tryActivate(p);var menu=new HeartSettingsMenu(1,p.getInventory(),c);var id=c.creatureId();p.setGameMode(GameType.SURVIVAL);
        h.assertTrue(!menu.action(p,"release",0,"")&&c.health().state()==HeartState.ALIVE&&c.creatureId().equals(id),"Survival actor released a living creature");p.setGameMode(GameType.CREATIVE);c.forceDeath();h.assertTrue(c.health().state()==HeartState.DYING&&menu.action(p,"release",0,"")&&c.health().state()==HeartState.DORMANT,"Release could not cancel death countdown");h.succeed();
    }
    @GameTest(template="combat",batch="dashboard_release_shared")
    public static void DashboardUsesTheSameReleaseAction(GameTestHelper h){
        var c=fixture(h);var p=TestActors.creative(h.getLevel());p.setPos(ModuleArena.corePosition(c));c.tryActivate(p);var menu=new HeartDashboardMenu(1,p.getInventory(),c);
        h.assertTrue(menu.action(p,c.modules().revision(),new UUID(0,0),"release","",0,"")&&c.health().state()==HeartState.DORMANT,"Dashboard release changed while adding settings entry point");h.succeed();
    }
}
