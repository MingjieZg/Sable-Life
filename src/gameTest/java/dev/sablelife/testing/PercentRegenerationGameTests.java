package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class PercentRegenerationGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h,int x){var l=h.getLevel();var p=h.absolutePos(new BlockPos(x,8,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}
    private static void ticks(HeartBlockEntity c,int n){for(int i=0;i<n;i++)c.tickRegeneration();}
    @GameTest(template="combat",batch="regen_percent_math")
    public static void PercentageUsesMaximumHealthAndAddsToFixedHealingOncePerSecond(GameTestHelper h){
        var c=fixture(h,3);var p=TestActors.creative(h.getLevel());c.configure(2000,60);c.editCore(p,"regen",10,"");c.editCore(p,"regenPercent",2,"");c.editCore(p,"regenDelay",0,"");c.tryActivate(p);c.damage(400);ticks(c,19);h.assertTrue(c.health().current()==1600,"Percentage regeneration ran before one second");ticks(c,1);h.assertTrue(c.health().current()==1650,"Percentage did not use max HP or add fixed 10 HP");
        c.resetForCreative(p);c.configure(3000,60);c.tryActivate(p);c.damage(600);ticks(c,20);h.assertTrue(c.health().current()==2470,"Percentage did not follow changed max health");h.succeed();
    }
    @GameTest(template="combat",batch="regen_percent_delay")
    public static void PercentageSharesDamageDelayCapsAtFullHealthAndCannotReviveDeath(GameTestHelper h){
        var c=fixture(h,3);var p=TestActors.creative(h.getLevel());c.configure(1000,60);c.editCore(p,"regenPercent",100,"");c.editCore(p,"regenDelay",.2,"");c.tryActivate(p);c.damage(300);ticks(c,23);h.assertTrue(c.health().current()==700,"Shared quiet delay shortened");ticks(c,1);h.assertTrue(c.health().current()==1000,"Percentage exceeded/missed maximum HP");c.damage(50);ticks(c,10);c.damage(50);ticks(c,23);h.assertTrue(c.health().current()==900,"A new hit failed to reset healing delay/progress");ticks(c,1);h.assertTrue(c.health().current()==1000,"Second regen cycle failed");c.forceDeath();ticks(c,40);h.assertTrue(c.health().state()==HeartState.DYING&&c.health().current()==0,"Percent regeneration revived a dying creature");h.succeed();
    }
    @GameTest(template="combat",batch="regen_percent_save")
    public static void PercentDefaultValidationWorldAndCopyPreserveSettingsWithoutHistory(GameTestHelper h){
        var c=fixture(h,2);var target=fixture(h,6);var p=TestActors.creative(h.getLevel());h.assertTrue(c.regenPercentPerSecond()==0&&c.editCore(p,"regenPercent",2.5,"")&&!c.editCore(p,"regenPercent",100.01,"")&&!c.editCore(p,"regenPercent",-1,"")&&!c.editCore(p,"regenPercent",Double.NaN,""),"Percent default or limits incorrect");
        var saved=c.saveWithoutMetadata(h.getLevel().registryAccess());var loaded=new HeartBlockEntity(c.getBlockPos(),c.getBlockState());loaded.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(loaded.regenPercentPerSecond()==2.5&&target.pasteConfiguration(p,c.copyConfiguration())&&target.regenPercentPerSecond()==2.5,"World/copy lost percentage");saved.remove("RegenPercentPerSecond");loaded.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(loaded.regenPercentPerSecond()==0,"Legacy world unexpectedly enabled percentage healing");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);h.assertTrue(!c.editCore(p,"regenPercent",10,""),"Survival actor changed percentage healing");h.succeed();
    }
}
