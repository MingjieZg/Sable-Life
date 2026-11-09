package dev.sablelife.testing;
import dev.sablelife.module.*;
import dev.sablelife.heart.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class TimedDamageGameTests {
 @GameTest(template="combat") public static void FullWindowFailureInversionEqualityAndSingleRearm(GameTestHelper h){
  var w=new TimedDamageWindow();h.assertTrue(!w.tick(0,true,5,200,false,false,2),"Early failure");w.add(199);h.assertTrue(!w.tick(4,true,5,200,false,false,2)&&w.tick(5,true,5,200,false,false,2),"Deadline failure missing");h.assertTrue(w.tick(6,true,5,200,false,false,2)&&!w.tick(7,true,5,200,false,false,2)&&!w.tick(100,true,5,200,false,false,2),"Pulse/once repeated");
  w.tick(101,false,5,200,false,false,2);w.tick(102,true,5,200,false,false,2);w.add(200);h.assertTrue(!w.tick(107,true,5,200,false,false,2)&&w.met(),"Exact equality failed requirement");w.clear();w.tick(0,true,5,200,false,true,2);w.add(200);h.assertTrue(!w.tick(4,true,5,200,false,true,2)&&w.tick(5,true,5,200,false,true,2),"Inverted success emitted early or not at deadline");h.succeed();
 }
 @GameTest(template="combat") public static void RepeatCancelsAndBoundaryDamageBelongsToNextWindow(GameTestHelper h){
  var w=new TimedDamageWindow();w.tick(0,true,3,20,true,true,1);w.add(20);h.assertTrue(w.tick(3,true,3,20,true,true,1)&&w.running()&&w.damage()==0,"Repeat did not settle/start new window");w.add(20);h.assertTrue(!w.tick(4,true,3,20,true,true,1)&&w.tick(6,true,3,20,true,true,1),"Boundary damage did not count toward next window");w.tick(7,false,3,20,true,true,1);h.assertTrue(!w.completed()&&w.damage()==0&&!w.running(),"Cancel retained result or count");h.assertTrue(!w.tick(9,true,3,20,true,false,1)&&!w.tick(11,true,3,20,true,false,1)&&w.tick(12,true,3,20,true,false,1),"Cancel became immediate failure or failed to rearm");h.succeed();
 }
 private static HeartBlockEntity core(GameTestHelper h){var c=SlashGuardGameTests.core(h);c.configure(10000,60);int n=c.wireless().add();c.wireless().channel(n).set(0,1);c.wireless().channel(n).set(6,3);return c;}
 private static VirtualModule timed(HeartBlockEntity c){var m=c.modules().add("damage_accumulation");m.edit("accumMode",1,"");m.edit("accumWindow",6,"");m.edit("accumRequired",200,"");m.edit("accumPulse",2,"");m.edit("accumChannel",1,"");return m;}
 @GameTest(template="combat",timeoutTicks=35) public static void ActualHpLossSettlesAtDeadlineAndCancellingDoesNotPunish(GameTestHelper h){
  var c=core(h);var m=timed(c);c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);c.damage(100);
  h.runAfterDelay(3,()->{c.modules().tick(c);h.assertTrue(c.wireless().output(0)==0&&c.modules().damageAccumulation.snapshot(m,c).getDouble("Accumulated")==100,"Early output or wrong actual loss");});
  h.runAfterDelay(7,()->{c.modules().tick(c);h.assertTrue(c.wireless().output(0)==15,"Real received-damage failure did not output");});
  h.runAfterDelay(10,()->{c.modules().tick(c);h.assertTrue(c.wireless().output(0)==0,"Pulse stayed high");c.modules().damageAccumulation.reset(m.id());c.modules().tick(c);});
  h.runAfterDelay(12,()->{m.edit("enabled",0,"");c.modules().tick(c);});
  h.runAfterDelay(18,()->{c.modules().tick(c);var state=c.modules().damageAccumulation.snapshot(m,c);h.assertTrue(c.wireless().output(0)==0&&!state.getBoolean("Completed")&&state.getDouble("Accumulated")==0,"Cancelled window punished or kept progress");h.succeed();});
 }
 @GameTest(template="combat",timeoutTicks=40) public static void TimedInvertedExternalOnlyOutputAndCumulativeCounterRemainIndependent(GameTestHelper h){
  var c=core(h);var m=timed(c);m.edit("accumChannel",0,"");m.edit("accumWindow",8,"");m.edit("accumPulse",8,"");m.edit("accumInvert",1,"");m.edit("accumPower",12,"");m.redBlue(false,new ItemStack(Items.HEART_OF_THE_SEA),h.getLevel().registryAccess());m.redBlue(true,new ItemStack(Items.ECHO_SHARD),h.getLevel().registryAccess());
  var cumulative=c.modules().add("damage_accumulation");cumulative.edit("accumThreshold",1000,"");cumulative.edit("accumChannel",1,"");
  var p=h.absolutePos(new net.minecraft.core.BlockPos(5,2,5));var level=h.getLevel();level.setBlockAndUpdate(p.below(),net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState());level.setBlockAndUpdate(p,com.simibubi.create.AllBlocks.REDSTONE_LINK.getDefaultState().setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.RECEIVER,true).setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.FACING,net.minecraft.core.Direction.UP));
  var receiver=(com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity)level.getBlockEntity(p);receiver.loadWithComponents(new net.minecraft.nbt.CompoundTag(),level.registryAccess());receiver.tick();var link=receiver.getBehaviour(com.simibubi.create.content.redstone.link.LinkBehaviour.TYPE);link.setFrequency(true,new ItemStack(Items.HEART_OF_THE_SEA));link.setFrequency(false,new ItemStack(Items.ECHO_SHARD));
  c.tryActivate(TestActors.creative(level));c.modules().tick(c);c.damage(200);
  h.runAfterDelay(4,()->{c.modules().tick(c);h.assertTrue(receiver.getReceivedSignal()==0,"Inverted timed output fired before deadline");});
  h.runAfterDelay(11,()->{c.modules().tick(c);h.assertTrue(receiver.getReceivedSignal()==12&&c.wireless().output(0)==0,"External-only success output missing or leaked internally");h.assertTrue(c.modules().damageAccumulation.snapshot(cumulative,c).getDouble("Accumulated")==200,"Timed mode changed independent cumulative counter");m.edit("enabled",0,"");c.modules().tick(c);h.assertTrue(receiver.getReceivedSignal()==0,"Disabled external pulse stayed high");h.succeed();});
 }
 @GameTest(template="combat") public static void ModePanelsKeepIndependentConfigAndClearRuntimeOnSwitch(GameTestHelper h){
  var c=core(h);var m=timed(c);m.edit("accumThreshold",333,"");m.redBlue(false,new ItemStack(Items.DIAMOND),h.getLevel().registryAccess());c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);c.damage(50);h.assertTrue(c.modules().damageAccumulation.snapshot(m,c).getDouble("Accumulated")==50,"Timed progress absent");m.edit("accumMode",0,"");c.modules().damageAccumulation.tick(c,new HashMap<>());h.assertTrue(c.modules().damageAccumulation.snapshot(m,c).getDouble("Accumulated")==0&&m.value("accumRequired")==200&&m.value("accumThreshold")==333,"Mode switch mixed progress/config");h.assertTrue(SurfaceFieldSettings.visible(m,"accumThreshold")&&!SurfaceFieldSettings.visible(m,"accumWindow"),"Cumulative fields incorrect");m.edit("accumMode",1,"");h.assertTrue(!SurfaceFieldSettings.visible(m,"accumThreshold")&&SurfaceFieldSettings.visible(m,"accumRequired"),"Timed fields incorrect");var preset=ModulePreset.create(m.save());h.assertTrue(preset.value("accumMode")==1&&preset.value("accumRequired")==200&&preset.hasRedBlue(),"Preset lost mode/frequency");var old=m.save();old.getCompound("Settings").remove("accumMode");h.assertTrue(VirtualModule.load(old).value("accumMode")==0,"Legacy mode changed");h.succeed();
 }
}
