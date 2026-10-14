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
public final class DamageAccumulationGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,9,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());c.configure(10000,60);return c;}
    private static VirtualModule configured(HeartBlockEntity c){var m=c.modules().add("damage_accumulation");int n=c.wireless().add();c.wireless().channel(n).set(0,1);c.wireless().channel(n).set(6,3);m.edit("accumChannel",n+1,"");return m;}
    private static net.minecraft.nbt.CompoundTag state(HeartBlockEntity c,VirtualModule m){return c.modules().damageAccumulation.snapshot(m,c);}
    @GameTest(template="combat",batch="damage_accum_clock")
    public static void ThresholdLatchesOnePulseIgnoresSpamAndRearmsOnlyAfterOutput(GameTestHelper h){
        var c=new DamageAccumulator();c.add(30,100);c.add(40,100);h.assertTrue(c.accumulated()==70&&!c.tick(0,true,3),"Partial damage emitted");c.add(50,100);c.add(10000,100);
        h.assertTrue(c.pending()==1&&c.accumulated()==100&&c.tick(1,true,3),"Threshold queued more than one pulse or retained excess");c.add(500,100);
        h.assertTrue(c.tick(1,true,3)&&c.tick(2,true,3)&&c.tick(3,true,3)&&c.accumulated()==100,"Pulse duration or same-tick idempotence failed");
        h.assertTrue(!c.tick(4,true,3)&&c.accumulated()==0&&c.pending()==0,"Pulse did not clear/rearm");c.add(1000,100);h.assertTrue(c.pending()==1&&c.tick(5,true,1)&&!c.tick(6,true,1),"Large hit queued multiple pulses");
        c.add(60,100);c.tick(7,false,3);c.add(Double.NaN,100);h.assertTrue(c.accumulated()==60&&!c.tick(8,true,3),"Pause lost partial count");h.succeed();
    }
    @GameTest(template="combat",batch="damage_accum_actual")
    public static void OnlyActualHealthLossCountsAndImmunityHealingAndDisabledHitsDoNot(GameTestHelper h){
        var c=fixture(h);var m=configured(c);var shield=c.modules().add("armor");shield.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));ArmorTestSupport.set(c,shield,-50);c.tryActivate(TestActors.creative(h.getLevel()));
        c.damageAt(c.getBlockPos(),100);h.assertTrue(state(c,m).getDouble("Accumulated")==50,"Pre-armor damage was counted");c.restoreFullHealth();h.assertTrue(state(c,m).getDouble("Accumulated")==50,"Healing subtracted cumulative damage");
        ArmorTestSupport.set(c,shield,-100);c.damageAt(c.getBlockPos(),100);c.damage(Float.NaN);h.assertTrue(state(c,m).getDouble("Accumulated")==50,"Immune or invalid damage counted");
        m.edit("enabled",0,"");c.damage(20);h.assertTrue(state(c,m).getDouble("Accumulated")==50,"Disabled accumulation counted a hit");m.edit("enabled",1,"");c.damage(60);
        h.assertTrue(state(c,m).getLong("Pending")==1&&state(c,m).getDouble("Accumulated")==100,"Threshold did not latch once");c.modules().tick(c);float before=c.health().current();c.damage(1000);
        h.assertTrue(c.health().current()==before-1000&&state(c,m).getLong("Pending")==0&&state(c,m).getDouble("Accumulated")==100,"Output window accumulated damage or prevented ordinary HP loss");h.succeed();
    }
    @GameTest(template="combat",batch="damage_accum_defense")
    public static void AdaptiveReductionIsAppliedBeforeCountingActualHpLoss(GameTestHelper h){
        var c=fixture(h);var m=configured(c);m.edit("accumThreshold",1000,"");c.editCore(TestActors.creative(h.getLevel()),"defenseEnabled",1,"");c.tryActivate(TestActors.creative(h.getLevel()));c.damage(200);
        h.assertTrue(c.health().current()==9900&&state(c,m).getDouble("Accumulated")==100,"Adaptive pre-reduction damage counted");h.succeed();
    }
    @GameTest(template="combat",batch="damage_accum_reverse",timeoutTicks=35)
    public static void ReversedInternalSignalCancelsMoveSetThenRestartsAfterInterruption(GameTestHelper h){
        var c=fixture(h);var m=configured(c);m.edit("accumInvert",1,"");m.edit("accumPulse",3,"");var table=c.modules().add("move_set");table.edit("trigger",2,"");table.edit("channel",1,"");var action=c.modules().add("wireless_redstone");
        table.moveSet().edit("add",0,"",c.modules());table.moveSet().edit("action_add",0,"0|"+action.id(),c.modules());table.moveSet().edit("action_duration",10,"0|0",c.modules());var target=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.setPos(ModuleArena.corePosition(c).add(0,0,4));h.getLevel().addFreshEntity(target);c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);
        h.runAfterDelay(1,()->c.modules().tick(c));h.runAfterDelay(3,()->c.modules().tick(c));h.runAfterDelay(5,()->c.modules().tick(c));h.runAfterDelay(6,()->c.modules().tick(c));
        h.runAfterDelay(2,()->{c.modules().tick(c);h.assertTrue(c.wireless().output(0)==15&&HeartModules.triggered(action,c),"Reversed idle output did not run Move set");c.damage(150);c.modules().tick(c);c.damage(200);});
        h.runAfterDelay(4,()->{c.modules().tick(c);h.assertTrue(c.wireless().output(0)==0&&!HeartModules.triggered(action,c)&&state(c,m).getDouble("Accumulated")==100,"Threshold interruption failed to cancel scheduling or ignored-hit latch");});
        h.runAfterDelay(7,()->{c.modules().tick(c);h.assertTrue(c.wireless().output(0)==15&&HeartModules.triggered(action,c)&&state(c,m).getDouble("Accumulated")==0,"Reversed interruption failed to reset and restart Move set");c.forceDeath();h.assertTrue(c.wireless().output(0)==0&&state(c,m).getDouble("Accumulated")==0,"Death left reverse output or accumulated damage");target.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="damage_accum_external",timeoutTicks=35)
    public static void InternalAndExternalFrequencyOutputCoexistWithOrdinaryWirelessModules(GameTestHelper h){
        var c=fixture(h);var m=configured(c);m.edit("accumPower",12,"");m.redBlue(false,new ItemStack(Items.EMERALD),h.getLevel().registryAccess());m.redBlue(true,new ItemStack(Items.DIAMOND),h.getLevel().registryAccess());
        var ordinary=c.modules().add("wireless_redstone");ordinary.edit("trigger",0,"");ordinary.redBlue(false,new ItemStack(Items.REDSTONE),h.getLevel().registryAccess());ordinary.redBlue(true,new ItemStack(Items.IRON_INGOT),h.getLevel().registryAccess());
        var p=h.absolutePos(new BlockPos(5,2,5));var level=h.getLevel();level.setBlockAndUpdate(p.below(),Blocks.IRON_BLOCK.defaultBlockState());level.setBlockAndUpdate(p,com.simibubi.create.AllBlocks.REDSTONE_LINK.getDefaultState().setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.RECEIVER,true).setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.FACING,Direction.UP));
        var receiver=(com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity)level.getBlockEntity(p);receiver.loadWithComponents(new net.minecraft.nbt.CompoundTag(),level.registryAccess());receiver.tick();var link=receiver.getBehaviour(com.simibubi.create.content.redstone.link.LinkBehaviour.TYPE);link.setFrequency(true,new ItemStack(Items.EMERALD));link.setFrequency(false,new ItemStack(Items.DIAMOND));var p2=p.east(3);level.setBlockAndUpdate(p2.below(),Blocks.IRON_BLOCK.defaultBlockState());level.setBlockAndUpdate(p2,level.getBlockState(p));
        var receiver2=(com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity)level.getBlockEntity(p2);receiver2.loadWithComponents(new net.minecraft.nbt.CompoundTag(),level.registryAccess());receiver2.tick();var link2=receiver2.getBehaviour(com.simibubi.create.content.redstone.link.LinkBehaviour.TYPE);link2.setFrequency(true,new ItemStack(Items.REDSTONE));link2.setFrequency(false,new ItemStack(Items.IRON_INGOT));
        c.tryActivate(TestActors.creative(level));c.damage(100);c.modules().tick(c);
        h.runAfterDelay(5,()->{c.modules().tick(c);h.assertTrue(c.wireless().output(0)==12&&receiver.getReceivedSignal()==12&&receiver2.getReceivedSignal()==15,"Internal or red/blue external pulse missing");c.modules().damageAccumulation.reset(m.id());});
        h.runAfterDelay(7,()->{c.modules().tick(c);h.assertTrue(c.wireless().output(0)==0&&receiver.getReceivedSignal()==0&&receiver2.getReceivedSignal()==15,"Reset left stale output or erased ordinary wireless");m.edit("accumInvert",1,"");c.modules().tick(c);h.assertTrue(receiver.getReceivedSignal()==12,"Reversed external idle did not output");m.edit("enabled",0,"");c.modules().tick(c);h.assertTrue(receiver.getReceivedSignal()==0&&receiver2.getReceivedSignal()==15,"Disabled inverse output stayed high or erased another source");h.succeed();});
    }
    @GameTest(template="combat",batch="damage_accum_config")
    public static void ConfigPresetChannelRemappingAndLifecycleDoNotCopyRuntimeDamage(GameTestHelper h){
        var c=fixture(h);var m=configured(c);int extra=c.wireless().add();m.edit("accumChannel",extra+1,"");m.edit("accumThreshold",234,"");m.edit("accumInvert",1,"");m.redBlue(false,new ItemStack(Items.EMERALD),h.getLevel().registryAccess());c.tryActivate(TestActors.creative(h.getLevel()));c.damage(100);
        var preset=ModulePreset.create(m.save());h.assertTrue(preset.value("accumThreshold")==234&&preset.value("accumInvert")==1&&preset.redBlue(false,h.getLevel().registryAccess()).is(Items.EMERALD)&&preset.coreModule(),"Preset lost settings/frequency or requested a mark");
        c.wireless().remove(0);h.assertTrue(m.value("accumChannel")==1,"Deleting earlier channel redirected output");c.wireless().remove(0);h.assertTrue(m.value("accumChannel")==0,"Deleted output channel remained bound");
        m.edit("accumThreshold",300,"");c.modules().tick(c);h.assertTrue(state(c,m).getDouble("Accumulated")==0,"Threshold edit kept stale counter");c.damage(100);c.modules().clearRuntime();h.assertTrue(state(c,m).getDouble("Accumulated")==0&&!RedstoneCorePolicy.TYPES.contains("damage_accumulation"),"Runtime copied/reset failed or redstone core gained health-only module");h.succeed();
    }
}
