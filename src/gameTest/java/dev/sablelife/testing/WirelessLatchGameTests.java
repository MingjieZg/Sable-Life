package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class WirelessLatchGameTests {
 private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,30,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());for(int i=0;i<4;i++){int index=c.wireless().add();c.wireless().channel(index).set(0,1);c.wireless().channel(index).set(6,3);}c.wireless().channel(0).set(7,1);c.wireless().channel(0).set(8,2);return c;}
 private static net.minecraft.server.level.ServerPlayer activate(GameTestHelper h,HeartBlockEntity c){var p=TestActors.creative(h.getLevel());p.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()));c.tryActivate(p);return p;}
 @GameTest(template="combat",batch="latch_hold_reset")
 public static void PulseHoldsAcrossSourceRemovalAndResetWinsUntilNewEdge(GameTestHelper h){
  var c=fixture(h);activate(h,c);var w=c.wireless();var particle=c.modules().add("particles");particle.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP),ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));particle.edit("trigger",2,"");particle.edit("channel",1,"");
  w.programOutputs("transition",Map.of(0,9));h.assertTrue(w.output(0)==9&&HeartModules.triggered(particle,c),"Pulse did not latch particle trigger");w.programOutputs("transition",Map.of());h.assertTrue(w.output(0)==9&&HeartModules.triggered(particle,c),"Move gap cleared latch");
  w.programOutputs("transition",Map.of(0,12));w.programOutputs("reset",Map.of(1,15));h.assertTrue(w.output(0)==0&&!HeartModules.triggered(particle,c),"Reset lost simultaneous set");w.programOutputs("reset",Map.of());h.assertTrue(w.output(0)==0,"Held set reasserted after reset");
  w.programOutputs("other",Map.of(2,7));h.assertTrue(w.output(0)==0&&w.output(2)==7,"Independent source reasserted latch or disappeared");w.programOutputs("transition",Map.of());w.programOutputs("transition",Map.of(0,6));h.assertTrue(w.output(0)==6,"Fresh edge did not rearm");w.programOutputs("transition",Map.of(0,15));h.assertTrue(w.output(0)==6,"Held signal changed captured strength");h.succeed();
 }
 @GameTest(template="combat",batch="latch_lifecycle")
 public static void MasterDisableReloadDeathAndReleaseClearOnlyRuntime(GameTestHelper h){
  var c=fixture(h);var player=activate(h,c);var w=c.wireless();w.programOutputs("test",Map.of(0,15));w.programOutputs("test",Map.of());var tag=w.save(h.getLevel().registryAccess());
  w.load(tag,h.getLevel().registryAccess());h.assertTrue(w.output(0)==0&&w.channel(0).value(7)==1&&w.channel(0).value(8)==2,"Reload restored stale latch or lost configuration");w.programOutputs("test",Map.of(0,15));w.master(false);w.master(true);h.assertTrue(w.output(0)==0,"Master off retained latch");
  w.programOutputs("test",Map.of(0,15));w.channel(0).set(0,0);w.channel(0).set(0,1);h.assertTrue(w.output(0)==0,"Channel disable retained latch");w.programOutputs("test",Map.of(0,15));c.damage(1000000);h.assertTrue(w.output(0)==0,"Death retained latch");c.resetForCreative(player);c.tryActivate(player);h.assertTrue(w.output(0)==0,"Reactivation restored dead latch");w.programOutputs("test",Map.of(0,15));c.resetForCreative(player);h.assertTrue(w.output(0)==0&&w.channel(0).value(7)==1,"Release retained runtime or lost settings");h.succeed();
 }
 @GameTest(template="combat",batch="latch_menu_delete")
 public static void MenuValidatesResetAndDeletionRemapsReferences(GameTestHelper h){
  var c=fixture(h);var p=TestActors.creative(h.getLevel());p.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()));var menu=new HeartWirelessMenu(1,p.getInventory(),c);
  h.assertTrue(menu.clickMenuButton(p,15<<16|1)&&menu.setting(0,7)==1&&menu.clickMenuButton(p,16<<16|4)&&menu.setting(0,8)==4,"Latch menu did not sync");h.assertTrue(!menu.clickMenuButton(p,16<<16|1)&&!menu.clickMenuButton(p,16<<16|5),"Self or missing reset accepted");
  var saved=c.wireless().channel(0).save(h.getLevel().registryAccess());var copy=WirelessChannel.load(saved,h.getLevel().registryAccess());h.assertTrue(copy.value(7)==1&&copy.value(8)==4,"Channel settings not saved");saved.remove("Latch");saved.remove("LatchReset");copy=WirelessChannel.load(saved,h.getLevel().registryAccess());h.assertTrue(copy.value(7)==0&&copy.value(8)==0,"Legacy channel gained latch");
  c.wireless().remove(1);h.assertTrue(c.wireless().channel(0).value(8)==3,"Reset did not follow channel deletion");c.wireless().remove(2);h.assertTrue(c.wireless().channel(0).value(8)==0,"Deleted reset redirected to another channel");
  c.wireless().channel(0).set(6,1);h.assertTrue(!menu.clickMenuButton(p,15<<16|1),"Receive mode allowed latch edit");h.succeed();
 }
 @GameTest(template="combat",batch="latch_sequence",timeoutTicks=45)
 public static void SequenceEndLeavesParticleGateHeldUntilAnotherSequenceResets(GameTestHelper h){
  var c=fixture(h);var sequence=c.modules().add("sequence");sequence.edit("trigger",0,"");sequence.editSequence("add",0,0);sequence.editSequence("time",0,.1);activate(h,c);
  h.runAfterDelay(8,()->{h.assertTrue(c.wireless().output(0)==15,"Finished sequence lost latched output");c.wireless().programOutputs("reset-test",Map.of(1,15));h.assertTrue(c.wireless().output(0)==0,"Reset did not clear ended sequence");h.succeed();});
 }
 @GameTest(template="combat",batch="latch_external",timeoutTicks=45)
 public static void LatchedExternalWaveformSurvivesPulseAndStopsOnResetAndUnload(GameTestHelper h){
  var c=fixture(h);var w=c.wireless();var ch=w.channel(0);ch.set(6,2);ch.set(2,2);ch.set(3,200);ch.set(5,0);ch.set(4,9);ch.frequency(false,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD));ch.frequency(true,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PRISMARINE_SHARD));
  var pos=h.absolutePos(new BlockPos(6,30,5));var l=h.getLevel();l.setBlockAndUpdate(pos.below(),Blocks.IRON_BLOCK.defaultBlockState());l.setBlockAndUpdate(pos,com.simibubi.create.AllBlocks.REDSTONE_LINK.getDefaultState().setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.RECEIVER,true).setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.FACING,Direction.UP));
  var receiver=(com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity)l.getBlockEntity(pos);receiver.loadWithComponents(new net.minecraft.nbt.CompoundTag(),l.registryAccess());receiver.tick();var link=receiver.getBehaviour(com.simibubi.create.content.redstone.link.LinkBehaviour.TYPE);link.setFrequency(true,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD));link.setFrequency(false,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PRISMARINE_SHARD));activate(h,c);
  h.runAfterDelay(6,()->{h.assertTrue(w.output(0)==9&&receiver.getReceivedSignal()==9,"External latch did not survive waveform end");w.programOutputs("reset",Map.of(1,15));h.assertTrue(w.output(0)==0&&receiver.getReceivedSignal()==0,"External receiver stayed on after reset");});
  h.runAfterDelay(9,()->{w.programOutputs("reset",Map.of());h.assertTrue(w.output(0)==0,"Ended waveform re-latched");c.onChunkUnloaded();h.assertTrue(receiver.getReceivedSignal()==0,"Unload left external output");h.succeed();});
 }

}
