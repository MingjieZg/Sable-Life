package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import dev.sablelife.heart.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;
import com.simibubi.create.content.redstone.link.*;
import net.createmod.catnip.data.Couple;
import java.util.*;
@GameTestHolder("sablelife_redstone_preview_tests") @PrefixGameTestTemplate(false)
public final class FollowRedstoneGameTests {
 private static HeartBlockEntity core(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.north(),Blocks.IRON_BLOCK.defaultBlockState());l.setBlockAndUpdate(p.north(2),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.north(),p.north(2)),new BoundingBox3i(p.getX(),p.getY(),p.getZ()-2,p.getX(),p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());var hover=c.modules().add("hover_anchor");hover.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));for(int i=0;i<3;i++){int n=c.wireless().add();c.wireless().channel(n).set(0,1);c.wireless().channel(n).set(6,3);}return c;}
 private static VirtualModule out(HeartBlockEntity c){var m=c.modules().add("wireless_redstone");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos().north(2),Direction.EAST)));m.edit("redstoneFaces",1,"");m.edit("redstoneStrengthMode",1,"");m.edit("redstoneChannel",2,"");m.edit("trigger",2,"");m.edit("channel",1,"");return m;}
 private static final class Link implements IRedstoneLinkable {
  final BlockPos pos;final boolean receiver;final Couple<RedstoneLinkNetworkHandler.Frequency> key;int tx,rx;
  Link(BlockPos pos,boolean receiver,ItemStack red,ItemStack blue){this.pos=pos;this.receiver=receiver;key=Couple.create(RedstoneLinkNetworkHandler.Frequency.of(red),RedstoneLinkNetworkHandler.Frequency.of(blue));}
  public int getTransmittedStrength(){return tx;}public void setReceivedStrength(int value){rx=value;}public boolean isListening(){return receiver;}public boolean isAlive(){return true;}public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey(){return key;}public BlockPos getLocation(){return pos;}
 }
 private static int face(HeartBlockEntity c,VirtualModule m){var a=m.anchors().getFirst();return ModuleSignals.signal((ServerLevel)c.getLevel(),a.position(a.body(c)).relative(a.face()),a.face());}
 @GameTest(template="combat",batch="follow_three_outputs")
 public static void InternalInputFollowsAllThreeDestinationsAndFixedDefaultStillUsesConfiguredPower(GameTestHelper h){
  var c=core(h);var m=out(c);var red=new ItemStack(Items.NETHER_STAR);var blue=new ItemStack(Items.EMERALD);m.redBlue(false,red,h.getLevel().registryAccess());m.redBlue(true,blue,h.getLevel().registryAccess());var link=new Link(c.getBlockPos(),true,red,blue);com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(h.getLevel(),link);c.tryActivate(TestActors.creative(h.getLevel()));
  try{for(int value:new int[]{7,2,15,0,11}){c.wireless().programOutputs("test_input",Map.of(0,value));c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==value&&face(c,m)==value&&link.rx==value,"Analog input not preserved at all destinations: "+value+" -> "+c.wireless().output(1)+","+face(c,m)+","+link.rx);}
   m.edit("redstoneInvert",1,"");c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==4&&face(c,m)==4&&link.rx==4,"Analog inverse did not use15-input");
   m.edit("redstoneStrengthMode",0,"");m.edit("redstoneInvert",0,"");m.edit("redstonePower",9,"");c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==9&&face(c,m)==9&&link.rx==9,"Fixed mode changed behavior");m.edit("enabled",0,"");c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==0&&link.rx==0&&face(c,m)==0,"Disabled follow relay retained output");h.succeed();
  }finally{com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(h.getLevel(),link);c.modules().events().clear();}
 }
 @GameTest(template="combat",batch="follow_pulse",timeoutTicks=30)
 public static void FollowPulseCapturesEdgeStrengthUntilDeadlineAndDoesNotRestartWhileHeld(GameTestHelper h){
  var c=core(h);var m=out(c);m.edit("redstoneMode",1,"");m.edit("redstonePulse",4,"");c.tryActivate(TestActors.creative(h.getLevel()));c.wireless().programOutputs("test_input",Map.of(0,6));c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==6,"Pulse did not sample edge strength");c.wireless().programOutputs("test_input",Map.of());c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==6,"Pulse dropped with input");
  h.runAfterDelay(5,()->{h.assertTrue(c.wireless().output(1)==0,"Pulse outlived deadline");c.wireless().programOutputs("test_input",Map.of(0,3));c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==3,"New rising edge did not resample");});
  h.runAfterDelay(10,()->{c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==0,"Held input restarted pulse");c.wireless().master(false);h.assertTrue(c.wireless().output(1)==0,"Master off retained pulse");h.succeed();});
 }
 @GameTest(template="combat",batch="follow_anchor")
 public static void AnchorPowerPreservesVanillaStrengthAndExcludesAdjacentCoreHeartbeat(GameTestHelper h){
  var c=core(h);var m=out(c);m.edit("trigger",1,"");m.edit("redstoneFaces",0,"");c.tryActivate(TestActors.creative(h.getLevel()));var a=m.anchors().getFirst();var pos=a.position(a.body(c));var wire=pos.above();
  for(int strength:new int[]{4,11,0}){h.getLevel().setBlock(wire,Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState().setValue(WeightedPressurePlateBlock.POWER,strength),2);c.modules().events().tick(c);h.assertTrue(ModuleRedstone.inputStrength(c,m)==strength&&c.wireless().output(1)==strength,"Anchor analog strength changed: "+strength+" -> "+ModuleRedstone.inputStrength(c,m));}
  h.getLevel().setBlock(wire,Blocks.AIR.defaultBlockState(),2);m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos().north(),Direction.WEST)));h.assertTrue(ModuleRedstone.inputStrength(c,m)==0&&!dev.sablelife.attack.ModulePower.hasControlSignal(h.getLevel(),c.getBlockPos().north()),"Adjacent core heartbeat self-powered analog control");c.modules().events().clear();h.succeed();
 }
 @GameTest(template="combat",batch="follow_external",timeoutTicks=30)
 public static void ExternalReceiverPassesActualNetworkStrength(GameTestHelper h){
  var c=core(h);var m=out(c);m.edit("trigger",3,"");m.edit("channel",3,"");var red=new ItemStack(Items.PRISMARINE_SHARD);var blue=new ItemStack(Items.QUARTZ);var channel=c.wireless().channel(2);channel.set(6,1);channel.frequency(false,red);channel.frequency(true,blue);var source=new Link(c.getBlockPos(),false,red,blue);source.tx=5;c.tryActivate(TestActors.creative(h.getLevel()));com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(h.getLevel(),source);c.wireless().tick(h.getLevel());
  h.runAfterDelay(5,()->{com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(h.getLevel(),source);c.modules().events().tick(c);h.assertTrue(c.wireless().input(2)==5&&c.wireless().output(1)==5&&face(c,m)==5,"External RX was converted to fixed strength");source.tx=12;com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(h.getLevel(),source);c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==12,"External RX changes did not propagate");source.tx=0;com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(h.getLevel(),source);c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==0,"External RX off retained output");com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(h.getLevel(),source);h.succeed();});
 }
 @GameTest(template="combat",batch="follow_settings")
 public static void DefaultsSaveAndBinaryTriggersUseConfiguredStrength(GameTestHelper h){
  h.assertTrue(new VirtualModule("wireless_redstone").value("redstoneStrengthMode")==0,"Old configuration unexpectedly follows input");var c=core(h);var m=out(c);m.edit("redstonePower",8,"");for(int trigger:new int[]{0,4,5}){m.edit("trigger",trigger,"");h.assertTrue(!ModuleRedstone.followsInput(m)&&ModuleRedstone.inputStrength(c,m)==8,"Binary trigger did not use configured strength");}
  h.assertTrue(VirtualModule.load(m.save()).value("redstoneStrengthMode")==1&&ModulePreset.create(m.save()).value("redstoneStrengthMode")==1,"Follow mode did not persist");m.edit("trigger",0,"");c.tryActivate(TestActors.creative(h.getLevel()));c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==8,"Always fallback did not emit configured strength");c.forceDeath();h.assertTrue(c.wireless().output(1)==0,"Death retained follow output");h.succeed();
 }

 @GameTest(template="combat",batch="follow_moveset_fallback",timeoutTicks=30)
 public static void MoveSetWindowUsesConfiguredPowerInFollowMode(GameTestHelper h){
  var c=core(h);var m=out(c);m.edit("redstonePower",8,"");var table=c.modules().add("move_set");table.moveSet().edit("add",0,"",c.modules());table.moveSet().edit("action_add",0,"0|"+m.id(),c.modules());table.moveSet().edit("action_duration",.2,"0|0",c.modules());var config=table.moveSet().get(0).config();config.edit("dispatchBusy",100,"");config.edit("dispatchRest",100,"");config.edit("dispatchCooldown",100,"");config.edit("target",1,"");config.edit("dispatchFar",32,"");var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(m.anchors().getFirst().world(c,false).add(3,0,0));h.getLevel().addFreshEntity(cow);c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);h.assertTrue(m.value("trigger")==4&&c.wireless().output(1)==8,"Move Set did not use configured strength in Follow mode");h.runAfterDelay(7,()->{h.assertTrue(c.wireless().output(1)==0,"Move Set output outlived action window");cow.discard();h.succeed();});
 }
 @GameTest(template="combat",batch="follow_hurt_fallback",timeoutTicks=25)
 public static void HurtTriggerUsesConfiguredPowerInFollowMode(GameTestHelper h){
  var c=core(h);var m=out(c);m.edit("trigger",5,"");m.edit("redstonePower",9,"");c.tryActivate(TestActors.creative(h.getLevel()));c.damage(1);c.modules().tick(c);h.assertTrue(c.wireless().output(1)==9,"Hurt trigger did not use configured strength");h.runAfterDelay(4,()->{h.assertTrue(c.wireless().output(1)==0,"Hurt window left output");h.succeed();});
 }

 @GameTest(template="combat",batch="ramp_outputs",timeoutTicks=30)
 public static void RampHoldsEndpointClearsAllOutputsAndRestarts(GameTestHelper h){
  var c=core(h);var m=out(c);m.edit("redstoneStrengthMode",2,"");m.edit("redstoneRampTime",6,"");
  var red=new ItemStack(Items.DIAMOND);var blue=new ItemStack(Items.AMETHYST_SHARD);m.redBlue(false,red,h.getLevel().registryAccess());m.redBlue(true,blue,h.getLevel().registryAccess());var link=new Link(c.getBlockPos(),true,red,blue);com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(h.getLevel(),link);
  c.tryActivate(TestActors.creative(h.getLevel()));c.wireless().programOutputs("test_input",Map.of(0,15));c.modules().events().tick(c);long began=h.getLevel().getGameTime();
  h.assertTrue(c.wireless().output(1)==0&&face(c,m)==0&&link.rx==0,"Ramp did not start at zero");
  h.runAfterDelay(3,()->{c.modules().events().tick(c);int expected=(int)Math.round(15*Math.min(1,(h.getLevel().getGameTime()-began)/6.));h.assertTrue(c.wireless().output(1)==expected&&face(c,m)==expected&&link.rx==expected,"Ramp destinations diverged at midpoint");});
  h.runAfterDelay(9,()->{try{c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==15&&face(c,m)==15&&link.rx==15,"Held ramp did not hold endpoint");c.wireless().programOutputs("test_input",Map.of());c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==0&&face(c,m)==0&&link.rx==0,"Stopped ramp retained output");c.wireless().programOutputs("test_input",Map.of(0,15));c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==0,"Restart resumed old progress");c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==0,"Same-tick publication advanced ramp");h.succeed();}finally{com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(h.getLevel(),link);c.modules().events().clear();}});
 }
 @GameTest(template="combat",batch="ramp_settings",timeoutTicks=30)
 public static void DescendingRampIgnoresInvertAndPulseAndPersistsValidatedEndpoints(GameTestHelper h){
  var fresh=new VirtualModule("wireless_redstone");h.assertTrue(fresh.value("redstoneFaces")==1,"New module should default to selected surface");
  var c=core(h);var m=out(c);m.edit("redstoneStrengthMode",2,"");h.assertTrue(m.edit("redstoneRampRange",0,"15|3"),"Endpoint pair rejected");
  for(String invalid:List.of("-1|4","4|16","3.5|4","NaN|4","3|4|5","3|")){h.assertTrue(!m.edit("redstoneRampRange",0,invalid)&&m.value("redstoneRampStart")==15&&m.value("redstoneRampEnd")==3,"Malformed range partially changed configuration: "+invalid);}
  m.edit("redstoneRampTime",6,"");m.edit("redstoneMode",1,"");m.edit("redstonePulse",120,"");m.edit("redstoneInvert",1,"");
  var saved=VirtualModule.load(m.save());var preset=ModulePreset.create(m.save());h.assertTrue(saved.value("redstoneRampStart")==15&&preset.value("redstoneRampEnd")==3&&saved.value("redstoneStrengthMode")==2,"Ramp settings lost on save/preset");
  c.tryActivate(TestActors.creative(h.getLevel()));c.wireless().programOutputs("test_input",Map.of(0,15));c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==15,"Descending ramp incorrectly inverted");
  h.runAfterDelay(8,()->{c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==3,"Descending endpoint lost to pulse/inversion");c.wireless().programOutputs("test_input",Map.of());c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==0,"Pulse/inversion kept stopped ramp alive");c.wireless().programOutputs("test_input",Map.of(0,15));c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==15,"Descending ramp failed to restart");c.wireless().master(false);h.assertTrue(c.wireless().output(1)==0,"Master off retained ramp");h.succeed();});
 }
 @GameTest(template="combat",batch="ramp_moveset",timeoutTicks=30)
 public static void MoveSetEndsRampImmediatelyBeforeRampDeadline(GameTestHelper h){
  var c=core(h);var m=out(c);m.edit("redstoneStrengthMode",2,"");m.edit("redstoneRampStart",3,"");m.edit("redstoneRampTime",60,"");m.edit("redstoneMode",1,"");m.edit("redstonePulse",120,"");m.edit("redstoneInvert",1,"");
  var table=c.modules().add("move_set");table.moveSet().edit("add",0,"",c.modules());table.moveSet().edit("action_add",0,"0|"+m.id(),c.modules());table.moveSet().edit("action_duration",.2,"0|0",c.modules());var config=table.moveSet().get(0).config();config.edit("dispatchBusy",100,"");config.edit("dispatchRest",100,"");config.edit("dispatchCooldown",100,"");config.edit("target",1,"");config.edit("dispatchFar",32,"");
  var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(m.anchors().getFirst().world(c,false).add(3,0,0));h.getLevel().addFreshEntity(cow);c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);h.assertTrue(m.value("trigger")==4&&c.wireless().output(1)==3,"Move Set ramp did not start at configured endpoint");
  h.runAfterDelay(7,()->{c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==0&&face(c,m)==0,"Ramp outlived shortened Move Set action");cow.discard();h.succeed();});
 }
}
