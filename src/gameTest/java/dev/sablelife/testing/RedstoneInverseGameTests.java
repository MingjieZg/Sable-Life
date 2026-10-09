package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class RedstoneInverseGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h,boolean control){var l=h.getLevel();var p=h.absolutePos(new BlockPos(control?7:3,9,3));l.setBlockAndUpdate(p,(control?SableLife.REDSTONE_CORE:SableLife.HEART).get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());if(!control)c.tryActivate(TestActors.creative(l));return c;}
    private static VirtualModule output(HeartBlockEntity c){var m=c.modules().add("wireless_redstone");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos().east(),Direction.UP)));m.edit("trigger",2,"");int n=c.wireless().add();var channel=c.wireless().channel(n);channel.set(0,1);channel.set(6,3);m.edit("channel",n+1,"");m.edit("redstonePower",9,"");return m;}
    private static int power(GameTestHelper h,HeartBlockEntity c,VirtualModule m){var a=m.anchors().getFirst();var p=a.position(a.body(c));return h.getLevel().getSignal(m.value("redstoneFaces")==1?p.above():p,Direction.UP);}
    @GameTest(template="combat",batch="wireless_reverse_hold")
    public static void HoldInversionWorksForBothCoreTypesAndClearsOnDisableMasterAndInvalidMark(GameTestHelper h){
        for(boolean control:List.of(false,true)){
            var c=fixture(h,control);var m=output(c);c.modules().tick(c);h.assertTrue(power(h,c,m)==0,"Legacy default started inverted");m.edit("redstoneInvert",1,"");c.modules().tick(c);h.assertTrue(power(h,c,m)==9,"Idle hold did not output configured strength");
            c.wireless().programOutputs("reverse_test",Map.of(0,15));c.modules().tick(c);h.assertTrue(power(h,c,m)==0,"Triggered inverted hold did not go low");c.wireless().programOutputs("reverse_test",Map.of());m.edit("redstoneFaces",1,"");c.modules().tick(c);h.assertTrue(power(h,c,m)==9,"Selected-face output failed inversion");
            m.edit("enabled",0,"");c.modules().tick(c);h.assertTrue(power(h,c,m)==0,"Disabled reverse module stayed high");m.edit("enabled",1,"");c.modules().tick(c);h.assertTrue(power(h,c,m)==9,"Re-enabled inverse module did not restore idle output");c.wireless().master(false);c.modules().tick(c);h.assertTrue(power(h,c,m)==0,"Master Off left reverse output high");c.wireless().master(true);c.modules().tick(c);m.invalidate();c.modules().tick(c);h.assertTrue(power(h,c,m)==0,"Invalid mark stayed high after inversion");c.modules().clearRuntime();
        }h.succeed();
    }
    @GameTest(template="combat",batch="wireless_reverse_pulse",timeoutTicks=20)
    public static void InvertedPulseDisconnectsForFullDurationThenRestoresWhileInputIsHeld(GameTestHelper h){
        var c=fixture(h,false);var m=output(c);m.edit("redstoneInvert",1,"");m.edit("redstoneMode",1,"");m.edit("redstonePulse",3,"");c.modules().tick(c);h.assertTrue(power(h,c,m)==9,"Pulse idle was not high");c.wireless().programOutputs("reverse_test",Map.of(0,15));c.modules().tick(c);h.assertTrue(power(h,c,m)==0,"Pulse rising edge did not disconnect");
        h.runAfterDelay(1,()->{c.modules().tick(c);h.assertTrue(power(h,c,m)==0,"Pulse interruption ended early");});
        h.runAfterDelay(2,()->{c.modules().tick(c);h.assertTrue(power(h,c,m)==0,"Pulse interruption duration was shortened");});
        h.runAfterDelay(3,()->{c.modules().tick(c);h.assertTrue(power(h,c,m)==9,"Held input restarted reverse pulse or failed to restore");m.edit("enabled",0,"");c.modules().tick(c);h.assertTrue(power(h,c,m)==0,"Disabled inverse pulse stayed high");c.forceDeath();h.succeed();});
    }
    @GameTest(template="combat",batch="wireless_reverse_external",timeoutTicks=25)
    public static void InvertedExternalFrequencyFollowsHoldAndDeathClearsItsIdleOutput(GameTestHelper h){
        var c=fixture(h,false);var m=output(c);m.edit("redstoneInvert",1,"");m.redBlue(false,new ItemStack(Items.GOLD_INGOT),h.getLevel().registryAccess());m.redBlue(true,new ItemStack(Items.LAPIS_LAZULI),h.getLevel().registryAccess());
        var p=h.absolutePos(new BlockPos(5,6,6));var l=h.getLevel();l.setBlockAndUpdate(p.below(),Blocks.IRON_BLOCK.defaultBlockState());l.setBlockAndUpdate(p,com.simibubi.create.AllBlocks.REDSTONE_LINK.getDefaultState().setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.RECEIVER,true).setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.FACING,Direction.UP));
        var receiver=(com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity)l.getBlockEntity(p);receiver.loadWithComponents(new net.minecraft.nbt.CompoundTag(),l.registryAccess());receiver.tick();var link=receiver.getBehaviour(com.simibubi.create.content.redstone.link.LinkBehaviour.TYPE);link.setFrequency(true,new ItemStack(Items.GOLD_INGOT));link.setFrequency(false,new ItemStack(Items.LAPIS_LAZULI));c.modules().tick(c);
        h.runAfterDelay(5,()->{c.modules().tick(c);h.assertTrue(receiver.getReceivedSignal()==9&&power(h,c,m)==9,"External and marked idle outputs diverged");c.wireless().programOutputs("reverse_test",Map.of(0,15));c.modules().tick(c);h.assertTrue(receiver.getReceivedSignal()==0&&power(h,c,m)==0,"External reverse hold did not disconnect");c.wireless().programOutputs("reverse_test",Map.of());c.modules().tick(c);h.assertTrue(receiver.getReceivedSignal()==9,"Reverse external output failed to resume");c.forceDeath();h.assertTrue(receiver.getReceivedSignal()==0,"Dead core kept inverted idle signal");h.succeed();});
    }
    @GameTest(template="combat",batch="wireless_reverse_save")
    public static void InverseSettingPersistsInWorldsAndPresetsAndLegacyDefaultsOff(GameTestHelper h){
        var m=new VirtualModule("wireless_redstone");h.assertTrue(m.value("redstoneInvert")==0,"New module inversion default changed");m.edit("redstoneInvert",1,"");h.assertTrue(VirtualModule.load(m.save()).value("redstoneInvert")==1&&ModulePreset.create(m.save()).value("redstoneInvert")==1,"Inversion failed save or preset round trip");var old=m.save();old.getCompound("Settings").remove("redstoneInvert");h.assertTrue(VirtualModule.load(old).value("redstoneInvert")==0,"Legacy module became inverted");h.succeed();
    }
}
