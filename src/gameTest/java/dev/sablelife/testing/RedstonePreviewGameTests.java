package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import dev.sablelife.heart.*;
import dev.sablelife.client.ShockwaveMath;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;
import org.joml.Matrix4f;
import io.netty.buffer.Unpooled;
import java.util.*;
@GameTestHolder("sablelife_redstone_preview_tests") @PrefixGameTestTemplate(false)
public final class RedstonePreviewGameTests {
 private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.north(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.north()),new BoundingBox3i(p.getX(),p.getY(),p.getZ()-1,p.getX(),p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());var hover=c.modules().add("hover_anchor");hover.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));return c;}
 @GameTest(template="combat",batch="redstone_disabled_preview")
 public static void CopiedDisabledRedstoneRetainsFacePreviewAfterRemarkAndMissingMarksDrawNothing(GameTestHelper h)throws Exception{
  var c=fixture(h);var m=c.modules().add("wireless_redstone");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));m.edit("enabled",0,"");var p=TestActors.creative(h.getLevel());p.setPos(m.anchors().getFirst().world(c,false));int[] dust={0},other={0};
  p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false)){@Override public void send(Packet<?> packet){if(packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket cp){if(cp.getParticle() instanceof net.minecraft.core.particles.DustParticleOptions)dust[0]++;else other[0]++;}}};
  var draw=MarkingSessions.class.getDeclaredMethod("previewModule",ServerPlayer.class,HeartBlockEntity.class,VirtualModule.class);draw.setAccessible(true);
  draw.invoke(null,p,c,m);h.assertTrue(dust[0]==216&&other[0]==0,"Disabled six-face preview became generic direction line");
  m.edit("enabled",1,"");dust[0]=0;draw.invoke(null,p,c,m);h.assertTrue(dust[0]==216&&other[0]==0,"Enabled preview differs");
  m.edit("enabled",0,"");var data=c.copyConfiguration();h.assertTrue(c.pasteConfiguration(p,data),"Copier paste failed");var copied=c.modules().all().stream().filter(x->x.type().equals("wireless_redstone")).findFirst().orElseThrow();
  h.assertTrue(!copied.enabled()&&copied.anchors().isEmpty(),"Copy changed enabled state or retained source mark");dust[0]=0;draw.invoke(null,p,c,copied);h.assertTrue(dust[0]==0&&other[0]==0,"Unmarked copied module drew or crashed");
  copied.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));copied.edit("redstoneFaces",1,"");draw.invoke(null,p,c,copied);h.assertTrue(dust[0]==36&&other[0]==0,"Remarked copied module did not show selected face");
  copied.clearMarks();copied.edit("enabled",1,"");dust[0]=0;draw.invoke(null,p,c,copied);h.assertTrue(dust[0]==0&&other[0]==0,"Enabled unmarked redstone drew or crashed");h.succeed();
 }

 @GameTest(template="combat",batch="alignment_face_diagnostic")
 public static void AlignmentLeftCorrectionDecreasesTowardFrontOnActualBlockFaces(GameTestHelper h){
  var c=fixture(h);var m=c.modules().add("navigation");var anchor=ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH);m.anchors(List.of(anchor));m.edit("target",1,"");m.edit("lineOfSight",1,"");m.edit("deadZone",0,"");c.tryActivate(TestActors.creative(h.getLevel()));var center=anchor.world(c,false);var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);h.getLevel().addFreshEntity(cow);var control=new TargetNavigation();int previous=16;
  try{for(int angle:new int[]{90,60,30,10,0}){
   double rad=Math.toRadians(angle);cow.setPos(center.add(-8*Math.sin(rad),-cow.getBbHeight()/2.,-8*Math.cos(rad)));var signals=new HashMap<ModuleSignals.Face,Integer>();control.tick(h.getLevel(),c,m,signals);ModuleSignals.updateFaces(c,signals);
   int left=h.getLevel().getSignal(c.getBlockPos(),Direction.EAST),front=h.getLevel().getSignal(c.getBlockPos(),Direction.SOUTH);
   h.assertTrue(left<=previous&&left==control.left(m.id())&&control.right(m.id())==0,"Actual left face is not monotonic at "+angle+": "+left);previous=left;
   System.out.println("Alignment angle="+angle+" left="+left+" front="+front+" leftNeighbor="+h.getLevel().getBestNeighborSignal(c.getBlockPos().west()));
   if(angle==0)h.assertTrue(left==0&&front==15,"Aligned left correction/front bearing were confused");
  }h.succeed();}finally{cow.discard();ModuleSignals.clear(c);}
 }

 private static VirtualModule navigation(HeartBlockEntity c){var m=c.modules().add("navigation");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH)));m.edit("target",1,"");m.edit("lineOfSight",1,"");m.edit("deadZone",0,"");m.edit("pitchDeadZone",0,"");for(int i=0;i<TargetNavigation.DIRECTIONS.size();i++)m.edit("navigation"+TargetNavigation.DIRECTIONS.get(i)+"Channel",i+1,"");return m;}
 private static void channels(HeartBlockEntity c){for(int i=0;i<6;i++){int n=c.wireless().add();c.wireless().channel(n).set(0,1);c.wireless().channel(n).set(6,3);}}
 @GameTest(template="combat",batch="alignment_channels")
 public static void DirectionChannelsPreserveAnalogStrengthReverseAndFeedSignalTurning(GameTestHelper h){
  var c=fixture(h);channels(c);var m=navigation(c);var turn=c.modules().add("signal_turn");turn.anchors(m.anchors());for(int i=0;i<4;i++)turn.edit(ModuleSignalTurn.DIRECTIONS.get(i)+"Channel",i+1,"");var player=TestActors.creative(h.getLevel());c.tryActivate(player);var center=m.anchors().getFirst().world(c,false);var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);h.getLevel().addFreshEntity(cow);int previous=16;
  try{for(int angle:new int[]{90,60,30,10,0}){double rad=Math.toRadians(angle);cow.setPos(center.add(-8*Math.sin(rad),-cow.getBbHeight()/2.,-8*Math.cos(rad)));c.modules().events().tick(c);int power=c.wireless().output(0);h.assertTrue(power<=previous&&power==c.modules().events().navigation().left(m.id())&&ModuleSignalTurn.input(c,turn,"turnLeft")==power,"Analog left strength changed at "+angle);previous=power;if(angle==30)h.assertTrue(power>=4&&power<=6,"Intermediate signal lost analog strength: "+power);}
   h.assertTrue(c.wireless().output(0)==0&&c.wireless().output(4)==15,"Aligned correction/bearing mixed up");
   cow.setPos(center.add(-6,4,-6));c.modules().events().tick(c);int left=c.wireless().output(0),up=c.wireless().output(2),forward=c.wireless().output(4);h.assertTrue(left>0&&up>0&&ModuleSignalTurn.input(c,turn,"turnUp")==up,"Vertical analog channel missing");m.edit("navigationInvert",1,"");c.modules().events().tick(c);h.assertTrue(c.wireless().output(0)==0&&c.wireless().output(1)==left&&c.wireless().output(2)==0&&c.wireless().output(3)==up&&c.wireless().output(4)==0&&c.wireless().output(5)==forward,"Reverse did not swap all channel directions");
   cow.discard();c.modules().events().tick(c);for(int n=0;n<6;n++)h.assertTrue(c.wireless().output(n)==0,"Target loss retained direction output");h.succeed();
  }finally{cow.discard();c.modules().events().clear();}
 }
 @GameTest(template="combat",batch="alignment_channel_cleanup")
 public static void SharedChannelsKeepOtherSourcesAndGateOffMasterAndResetClearTheirOwnOutput(GameTestHelper h){
  var c=fixture(h);channels(c);var a=navigation(c);var b=navigation(c);b.edit("navigationLeftChannel",2,"");var p=TestActors.creative(h.getLevel());c.tryActivate(p);var center=a.anchors().getFirst().world(c,false);var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(center.add(-6,0,-6));h.getLevel().addFreshEntity(cow);
  try{c.modules().events().tick(c);h.assertTrue(c.wireless().output(0)>0&&c.wireless().output(1)>0,"Two modules did not publish independently");c.wireless().programOutputs("other",Map.of(0,3));a.edit("enabled",0,"");c.modules().events().tick(c);h.assertTrue(c.wireless().output(0)==3&&c.wireless().output(1)>0,"Disabling one source cleared others");b.edit("trigger",2,"");b.edit("channel",6,"");c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)==0,"Trigger end left output");b.edit("trigger",0,"");c.modules().events().tick(c);c.wireless().master(false);h.assertTrue(c.wireless().output(0)==0&&c.wireless().output(1)==0,"Master off retained signal");c.wireless().master(true);c.modules().events().tick(c);h.assertTrue(c.wireless().output(1)>0,"Master on did not resume");c.resetForCreative(p);for(int n=0;n<6;n++)h.assertTrue(c.wireless().output(n)==0,"Life release left output");h.succeed();}finally{cow.discard();c.modules().events().clear();}
 }
 @GameTest(template="combat",batch="alignment_channel_settings")
 public static void DirectionChannelDefaultsSavePresetIntegerStepsAndDeletedReferencesStaySafe(GameTestHelper h){
  var c=fixture(h);var m=navigation(c);for(String direction:TargetNavigation.DIRECTIONS)h.assertTrue(new VirtualModule("navigation").value("navigation"+direction+"Channel")==0,"Old config unexpectedly bound a channel");
  for(var saved:List.of(VirtualModule.load(m.save()),ModulePreset.create(m.save())))for(int n=0;n<6;n++)h.assertTrue(saved.value("navigation"+TargetNavigation.DIRECTIONS.get(n)+"Channel")==n+1,"Save/preset lost direction channel");
  channels(c);c.wireless().remove(1);h.assertTrue(m.value("navigationLeftChannel")==1&&m.value("navigationRightChannel")==0&&m.value("navigationUpChannel")==2&&m.value("navigationBackwardChannel")==5,"Channel deletion redirected reference");m.edit("navigationLeftChannel",2.7,"");h.assertTrue(m.value("navigationLeftChannel")==3&&dev.sablelife.client.NumericWheelSteps.module("navigation","navigationLeftChannel",1,0)==1,"Direction channel accepts fraction or wrong wheel step");h.succeed();
 }

 @GameTest(template="combat",batch="alignment_turn_integration",timeoutTicks=35)
 public static void AlignmentChannelsActuallySteerTheBodyTowardTheTarget(GameTestHelper h){
  var c=fixture(h);c.modules().all().getFirst().edit("enabled",0,"");channels(c);var m=navigation(c);m.edit("verticalTracking",0,"");var turn=c.modules().add("signal_turn");turn.anchors(m.anchors());turn.edit("turnLeftChannel",1,"");turn.edit("turnRightChannel",2,"");turn.edit("turnCollision",0,"");var center=m.anchors().getFirst().world(c,false);var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(center.add(-8,0,-8));h.getLevel().addFreshEntity(cow);c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);int initial=c.wireless().output(0);h.assertTrue(initial>0,"Alignment did not seed steering channel");
  h.runAfterDelay(15,()->{var front=TargetNavigation.worldFront(c,m);h.assertTrue(front.x<-.15&&c.wireless().output(0)<initial&&ModuleSignalTurn.input(c,turn,"turnLeft")==c.wireless().output(0),"Actual body did not steer or correction did not reduce: "+front+" / "+initial+" -> "+c.wireless().output(0));cow.discard();c.modules().events().clear();h.succeed();});
 }
}
