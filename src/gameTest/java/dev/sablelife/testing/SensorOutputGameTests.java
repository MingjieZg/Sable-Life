package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class SensorOutputGameTests {
 private static HeartBlockEntity fixture(GameTestHelper h,boolean control){var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,40,4));level.setBlockAndUpdate(pos,(control?SableLife.REDSTONE_CORE:SableLife.HEART).get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());}
 private static VirtualModule sensor(HeartBlockEntity core){var m=core.modules().add("sensor");m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.NORTH)));m.edit("trigger",0,"");m.edit("target",1,"");m.edit("lineOfSight",1,"");m.edit("sensorPower",9,"");return m;}
 private static net.minecraft.world.entity.animal.Cow target(GameTestHelper h,HeartBlockEntity core){var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter()).add(0,0,-4));h.getLevel().addFreshEntity(cow);return cow;}
 private static int internal(HeartBlockEntity c,VirtualModule m){int i=c.wireless().add();c.wireless().channel(i).set(0,1);c.wireless().channel(i).set(6,3);m.edit("sensorChannel",i+1,"");return i;}
 @GameTest(template="combat",batch="sensor_outputs",timeoutTicks=40)
 public static void MarkInternalAndNativeWirelessReceiverCoexistAndClear(GameTestHelper h){
  var c=fixture(h,false);var m=sensor(c);int output=internal(c,m);
  m.redBlue(false,new ItemStack(Items.EMERALD),h.getLevel().registryAccess());m.redBlue(true,new ItemStack(Items.DIAMOND),h.getLevel().registryAccess());
  int receiver=c.wireless().add();var rx=c.wireless().channel(receiver);rx.set(0,1);rx.set(6,1);rx.frequency(false,new ItemStack(Items.EMERALD));rx.frequency(true,new ItemStack(Items.DIAMOND));
  var cow=target(h,c);c.tryActivate(TestActors.creative(h.getLevel()));
  h.runAfterDelay(3,()->{c.wireless().tick(h.getLevel());c.modules().tick(c);var mark=m.anchors().getFirst();h.assertTrue(h.getLevel().getBestNeighborSignal(mark.position(mark.body(c)))==15&&c.wireless().output(output)==9&&c.wireless().input(receiver)==9,"Sensor mark/internal/external signal mismatch");cow.discard();});
  h.runAfterDelay(5,()->{c.modules().tick(c);h.assertTrue(h.getLevel().getBestNeighborSignal(c.getBlockPos().east())==0&&c.wireless().output(output)==0&&c.wireless().input(receiver)==0,"Target loss retained sensor output");m.edit("sensorInvert",1,"");c.modules().tick(c);h.assertTrue(h.getLevel().getBestNeighborSignal(c.getBlockPos().east())==15&&c.wireless().output(output)==9&&c.wireless().input(receiver)==9&&!c.modules().events().sensorDetected(m.id()),"Inverted absence did not power all outputs");var again=target(h,c);c.modules().tick(c);h.assertTrue(h.getLevel().getBestNeighborSignal(c.getBlockPos().east())==0&&c.wireless().output(output)==0&&c.wireless().input(receiver)==0&&c.modules().events().sensorDetected(m.id()),"Inverted detection did not clear outputs");m.edit("enabled",0,"");again.discard();c.modules().tick(c);h.assertTrue(c.wireless().output(output)==0&&h.getLevel().getBestNeighborSignal(c.getBlockPos().east())==0,"Disabled inverted sensor remained high");m.edit("enabled",1,"");m.edit("sensorInvert",0,"");again=target(h,c);c.modules().tick(c);h.assertTrue(c.wireless().input(receiver)==9,"Reentry failed to emit");c.modules().events().clear();h.assertTrue(c.wireless().output(output)==0&&c.wireless().input(receiver)==0,"Runtime clear leaked sensor transmitter");again.discard();c.forceDeath();h.succeed();});
 }
 @GameTest(template="combat",batch="sensor_signal_merge")
 public static void SharedInternalSourceDoesNotEraseOtherProducersAndControlCoreWorks(GameTestHelper h){
  var c=fixture(h,true);var m=sensor(c);int output=internal(c,m);var cow=target(h,c);
  c.wireless().programOutputs("test_other",Map.of(output,4));c.modules().tick(c);h.assertTrue(c.wireless().output(output)==9,"Control-core sensor did not emit");
  cow.discard();c.modules().tick(c);h.assertTrue(c.wireless().output(output)==4,"Sensor low erased another internal producer");
  c.wireless().master(false);c.modules().tick(c);h.assertTrue(c.wireless().output(output)==0&&h.getLevel().getBestNeighborSignal(c.getBlockPos().east())==0,"Control master off kept sensing");c.wireless().programOutputs("test_other",Map.of());h.succeed();
 }
 @GameTest(template="combat",batch="sensor_config")
 public static void SavedPresetFrequenciesAndDeletedChannelsKeepValidBindings(GameTestHelper h){
  var c=fixture(h,false);var m=sensor(c);c.wireless().add();int output=internal(c,m);m.redBlue(false,new ItemStack(Items.EMERALD),h.getLevel().registryAccess());
  h.assertTrue(m.edit("sensorPower",16,"")&&m.value("sensorPower")==15&&!m.edit("sensorChannel",Double.NaN,"")&&m.edit("sensorChannel",65536,"")&&m.value("sensorChannel")==65535,"Output bounds or nonfinite validation failed");m.edit("sensorPower",9,"");m.edit("sensorChannel",output+1,"");m.edit("sensorInvert",1,"");
  var saved=VirtualModule.load(m.save());var preset=ModulePreset.create(m.save());h.assertTrue(saved.value("sensorChannel")==output+1&&saved.value("sensorPower")==9&&saved.hasRedBlue()&&preset.hasRedBlue()&&saved.value("sensorInvert")==1,"Save/preset lost sensor output settings");
  c.wireless().remove(0);h.assertTrue(m.value("sensorChannel")==1,"Channel row removal redirected sensor");c.wireless().remove(0);h.assertTrue(m.value("sensorChannel")==0,"Deleted output did not unbind sensor");
  var menu=new HeartDashboardMenu(2,TestActors.creative(h.getLevel()).getInventory(),c);var player=TestActors.creative(h.getLevel());player.setPos(SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()));
  h.assertTrue(menu.action(player,c.modules().revision(),m.id(),"frequency","",0,""),"Sensor frequency editor rejected");player.closeContainer();h.succeed();
 }
 @GameTest(template="combat",batch="sensor_native_lamp",timeoutTicks=30)
 public static void SensorChargesMarkedNativeLampAndClearsWithoutAdjacentRelay(GameTestHelper h){
  var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,40,4));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.REDSTONE_LAMP.defaultBlockState());level.setBlockAndUpdate(pos.east(2),Blocks.REDSTONE_LAMP.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east(),pos.east(2)),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+2,pos.getY(),pos.getZ()));var c=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());var m=sensor(c);var mark=c.getBlockPos().east();
  var cow=target(h,c);c.tryActivate(TestActors.creative(level));
  h.runAfterDelay(3,()->{c.modules().tick(c);h.assertTrue(c.modules().events().sensorDetected(m.id()),"No target detected in lamp fixture");h.assertTrue(level.getBestNeighborSignal(mark)==15,"Native neighbor signal lost Mark charge");h.assertTrue(level.getBlockState(mark).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),"Marked lamp did not receive sensor charge");h.assertTrue(!level.getBlockState(mark.east()).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),"Weak Mark charge leaked to adjacent lamp");cow.discard();c.modules().tick(c);});
  h.runAfterDelay(10,()->{h.assertTrue(!level.getBlockState(mark).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT)&&level.getBestNeighborSignal(mark)==0,"Marked lamp remained powered after target loss");c.forceDeath();h.succeed();});
 }
 @GameTest(template="combat",batch="sensor_mark_faces",timeoutTicks=70)
 public static void MarkReceivesChargeOnAllSixSelectedFacesAndDeathClearsInverse(GameTestHelper h){
  var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,40,4));var positions=List.of(pos,pos.east(),pos.east(2),pos.east(3));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());level.setBlockAndUpdate(pos.east(2),Blocks.IRON_BLOCK.defaultBlockState());level.setBlockAndUpdate(pos.east(3),Blocks.REDSTONE_LAMP.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,positions,new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+3,pos.getY(),pos.getZ()));var c=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());var m=sensor(c);var mark=c.getBlockPos().east(3);m.edit("sensorInvert",1,"");m.edit("enabled",0,"");c.tryActivate(TestActors.creative(level));
  for(var face:Direction.values()){
   int start=1+face.ordinal()*8;
   h.runAfterDelay(start,()->{m.anchors(List.of(ModuleAnchor.create(c,mark,face)));m.edit("enabled",1,"");c.modules().tick(c);h.assertTrue(level.getBlockState(mark).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),"Selected face did not charge marked lamp: "+face);for(var side:Direction.values()){h.assertTrue(level.getSignal(mark.relative(side),side)==(side==face?15:0),"Charge leaked to wrong side: "+face+" / "+side);h.assertTrue(level.getDirectSignal(mark.relative(side),side)==0,"Charge became strong relay: "+side);}m.edit("enabled",0,"");c.modules().tick(c);});
   h.runAfterDelay(start+6,()->h.assertTrue(!level.getBlockState(mark).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT)&&level.getBestNeighborSignal(mark)==0,"Disabled inverse Mark stayed lit: "+face));
  }
  h.runAfterDelay(50,()->{m.edit("enabled",1,"");c.modules().tick(c);h.assertTrue(level.getBestNeighborSignal(mark)==15,"Inverse Mark did not resume");c.forceDeath();h.assertTrue(level.getBestNeighborSignal(mark)==0,"Death left inverse Mark charged");});
  h.runAfterDelay(57,()->{h.assertTrue(!level.getBlockState(mark).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),"Death left marked lamp lit");h.succeed();});
 }
}
