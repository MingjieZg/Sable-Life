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
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class ShockwaveGameTests {
 @GameTest(template="combat",batch="shockwave_reverse")
 public static void ReversePersistsAndContractsFromMaximumRadius(GameTestHelper h){
  var c=fixture(h);var m=module(c);h.assertTrue(m.value("shockReverse")==0&&!ModuleShockwave.packet(c,m).reverse(),"Existing waves changed direction");
  m.edit("shockReverse",1,"");var p=ModuleShockwave.packet(c,m);h.assertTrue(p.valid()&&p.reverse()&&VirtualModule.load(m.save()).value("shockReverse")==1&&ModulePreset.create(m.save()).value("shockReverse")==1,"Reverse lost in packet/save/preset");
  var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{ShockwavePacket.CODEC.encode(buf,p);h.assertTrue(ShockwavePacket.CODEC.decode(buf).equals(p),"Reverse lost on wire");}finally{buf.release();}
  h.assertTrue(ShockwaveMath.radius(0,20,30,true)==30&&ShockwaveMath.radius(5,20,30,true)==22.5&&ShockwaveMath.radius(20,20,30,true)==0&&ShockwaveMath.radius(5,100,30,true)==28.5,"Reverse direction, endpoints or duration is wrong");
  for(int age=0;age<20;age++)h.assertTrue(ShockwaveMath.radius(age,20,30,true)>ShockwaveMath.radius(age+1,20,30,true),"Reverse radius did not shrink continuously");h.succeed();
 }
 private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.north(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.north()),new BoundingBox3i(p.getX(),p.getY(),p.getZ()-1,p.getX(),p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());var hover=c.modules().add("hover_anchor");hover.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));return c;}
 private static VirtualModule module(HeartBlockEntity c){var m=c.modules().add("visual_shockwave");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH)));return m;}
 @GameTest(template="combat",batch="shock_repeat_zoom",timeoutTicks=60)
 public static void RepeatZoomHoldsAcrossLongIntervalsAndReleasesWithSignalAndMaster(GameTestHelper h){
  var c=fixture(h);var m=module(c);m.edit("shockMode",1,"");m.edit("shockInterval",100,"");m.edit("shockZoomTime",2,"");var point=m.anchors().getFirst().world(c,false);var zooms=new ArrayList<ShockwaveZoomPacket>();var waves=new ArrayList<ShockwavePacket>();var p=TestActors.creative(h.getLevel());p.setPos(point);
  p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false)){@Override public void send(Packet<?> packet){if(packet instanceof ClientboundCustomPayloadPacket cp){if(cp.payload() instanceof ShockwaveZoomPacket z&&z.module().equals(m.id()))zooms.add(z);if(cp.payload() instanceof ShockwavePacket w&&w.point().distanceTo(point)<1)waves.add(w);}}};h.getLevel().players().add(p);c.tryActivate(p);c.modules().tick(c);
  h.assertTrue(!zooms.isEmpty()&&zooms.getFirst().active()&&waves.size()==1&&waves.getFirst().zoom()==0,"Repeat zoom not separated from wave births");
  h.runAfterDelay(15,()->{h.assertTrue(zooms.size()>=2&&zooms.stream().allMatch(ShockwaveZoomPacket::active)&&waves.size()==1,"Long repeat interval started recovery or stopped renewing hold");m.edit("enabled",0,"");});
  h.runAfterDelay(18,()->{h.assertTrue(!zooms.getLast().active(),"Signal end failed to release zoom");m.edit("enabled",1,"");});
  h.runAfterDelay(21,()->{h.assertTrue(zooms.getLast().active(),"New signal failed to resume zoom");c.wireless().master(false);});
  h.runAfterDelay(24,()->{h.assertTrue(!zooms.getLast().active(),"Master Off left zoom held");h.getLevel().players().remove(p);h.succeed();});
 }
 private static ServerPlayer viewer(GameTestHelper h,Vec3 position,List<ShockwavePacket> packets,List<Long> times,Vec3 source){var l=h.getLevel();var player=TestActors.creative(l);player.setPos(position);player.connection=new ServerGamePacketListenerImpl(l.getServer(),new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(player.getGameProfile(),false)){@Override public void send(Packet<?> packet){if(packet instanceof ClientboundCustomPayloadPacket c&&c.payload() instanceof ShockwavePacket p&&p.point().distanceTo(source)<1){packets.add(p);times.add(l.getGameTime());}}};l.players().add(player);return player;}
 @GameTest(template="combat",batch="shock_once",timeoutTicks=35)
 public static void SignalEdgesSendOneWorldBirthOnlyToNearbyPlayersAndNeverDamage(GameTestHelper h){var c=fixture(h);var m=module(c);var point=m.anchors().getFirst().world(c,false);var packets=new ArrayList<ShockwavePacket>();var farPackets=new ArrayList<ShockwavePacket>();var near=viewer(h,point.add(2,0,0),packets,new ArrayList<>(),point);var far=viewer(h,point.add(500,0,0),farPackets,new ArrayList<>(),point);c.tryActivate(near);c.modules().tick(c);h.assertTrue(packets.size()==1&&packets.getFirst().valid()&&packets.getFirst().point().distanceTo(point)<.01&&farPackets.isEmpty(),"Wrong birth position, range or initial cadence");h.runAfterDelay(3,()->{h.assertTrue(packets.size()==1,"Held input spawned waves repeatedly");m.edit("enabled",0,"");});h.runAfterDelay(5,()->m.edit("enabled",1,""));h.runAfterDelay(8,()->{h.assertTrue(packets.size()==2&&c.health().current()==c.health().maximum(),"Rising edge did not rearm or effect damaged life");c.resetForCreative(near);h.getLevel().players().remove(near);h.getLevel().players().remove(far);h.succeed();});}
 @GameTest(template="combat",batch="shock_repeat",timeoutTicks=35)
 public static void RepeatCadenceStopsWithMasterAndCanPreviewOneAuthorOnly(GameTestHelper h){var c=fixture(h);var m=module(c);m.edit("shockMode",1,"");m.edit("shockInterval",3,"");var point=m.anchors().getFirst().world(c,false);var packets=new ArrayList<ShockwavePacket>();var times=new ArrayList<Long>();var player=viewer(h,point.add(2,0,0),packets,times,point);c.tryActivate(player);c.modules().tick(c);h.runAfterDelay(8,()->{h.assertTrue(packets.size()>=3,"Repeating shock did not emit");for(int i=1;i<times.size();i++)h.assertTrue(times.get(i)-times.get(i-1)>=3,"Repeating shock ignored interval");c.wireless().master(false);});h.runAfterDelay(10,()->{int before=packets.size();c.modules().tick(c);h.assertTrue(packets.size()==before,"Master off emitted");c.resetForCreative(player);h.assertTrue(ModuleShockwave.preview(player,c,m)&&packets.size()==before+1,"Dormant author preview failed");m.clearMarks();h.assertTrue(!ModuleShockwave.preview(player,c,m),"Missing mark preview accepted");h.getLevel().players().remove(player);h.succeed();});}
 @GameTest(template="combat",batch="shock_packet_geometry")
 public static void CodecBoundsProjectionAndCameraEnvelopeRejectBehindAndRecover(GameTestHelper h){var c=fixture(h);var m=module(c);var p=ModuleShockwave.packet(c,m);var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{ShockwavePacket.CODEC.encode(buf,p);h.assertTrue(ShockwavePacket.CODEC.decode(buf).equals(p),"Shockwave packet roundtrip changed settings");}finally{buf.release();}m.edit("shockZoom",-100,"");var wide=ModuleShockwave.packet(c,m);h.assertTrue(wide.valid()&&wide.zoom()==-100&&VirtualModule.load(m.save()).value("shockZoom")==-100,"Expanded negative zoom was rejected or not saved");var wideBuf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{ShockwavePacket.CODEC.encode(wideBuf,wide);h.assertTrue(ShockwavePacket.CODEC.decode(wideBuf).equals(wide),"Expanded zoom packet roundtrip failed");}finally{wideBuf.release();}m.edit("shockZoom",100,"");h.assertTrue(m.value("shockZoom")==30&&ModuleShockwave.packet(c,m).valid()&&VirtualModule.load(m.save()).value("shockZoom")==30,"Positive zoom was not clamped back to 30");h.assertTrue(ShockwaveMath.radius(0,100,30)==0&&ShockwaveMath.radius(5,100,30)==1.5&&ShockwaveMath.radius(5,20,30)==7.5&&ShockwaveMath.radius(100,100,30)==30&&ShockwaveMath.waveStrength(5,100)==1&&ShockwaveMath.waveStrength(100,100)==0,"Wave did not start at centre, slow with duration or stay visible early");var outside=new ShockwavePacket(p.dimension(),p.point(),p.radius(),p.expansion(),p.width(),p.distortion(),p.blur(),p.shake(),p.shakeTicks(),-101,p.zoomTicks(),p.range());h.assertTrue(!outside.valid(),"Out-of-range zoom accepted");m.edit("shockZoom",4,"");var bad=new ShockwavePacket(p.dimension(),new Vec3(Double.NaN,0,0),p.radius(),p.expansion(),p.width(),p.distortion(),p.blur(),p.shake(),p.shakeTicks(),p.zoom(),p.zoomTicks(),p.range());h.assertTrue(!bad.valid(),"Nonfinite wave accepted");var proj=new Matrix4f().perspective((float)Math.PI/2,16f/9,.05f,200);var screen=ShockwaveMath.project(new Vec3(-3,2,-10),Vec3.ZERO,new Matrix4f(),proj);h.assertTrue(screen!=null&&screen.x()<.5&&screen.y()>.5&&ShockwaveMath.project(new Vec3(0,0,10),Vec3.ZERO,new Matrix4f(),proj)==null,"World projection lost source or put rear wave in screen centre");h.assertTrue(ShockwaveMath.envelope(0,10)==0&&ShockwaveMath.envelope(10,10)==0&&ShockwaveMath.envelope(11,10)==0&&ShockwaveMath.envelope(2,10)>.5&&ShockwaveMath.attenuation(64,64)==0,"Camera effects did not fade/recover");var saved=VirtualModule.load(m.save());h.assertTrue(saved.value("shockExpansion")==9&&saved.value("shockZoom")==4&&saved.anchors().size()==1&&ModulePreset.create(m.save()).anchors().isEmpty(),"Defaults/save/preset failed");h.succeed();}
 @GameTest(template="combat",batch="shock_moveset",timeoutTicks=40)
 public static void MoveSetActionWindowFiresOnceAndNextMoveRearms(GameTestHelper h){var c=fixture(h);var m=module(c);var table=c.modules().add("move_set");table.moveSet().edit("add",0,"",c.modules());h.assertTrue(table.moveSet().edit("action_add",0,"0|"+m.id(),c.modules()),"Move Set cannot target shockwave");table.moveSet().edit("action_duration",.1,"0|0",c.modules());var cfg=table.moveSet().get(0).config();cfg.edit("dispatchBusy",2,"");cfg.edit("dispatchRest",1,"");cfg.edit("dispatchCooldown",0,"");cfg.edit("target",1,"");cfg.edit("dispatchFar",16,"");var point=m.anchors().getFirst().world(c,false);var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setPos(point.add(2,0,0));h.getLevel().addFreshEntity(cow);var packets=new ArrayList<ShockwavePacket>();var player=viewer(h,point.add(0,0,2),packets,new ArrayList<>(),point);c.tryActivate(player);c.modules().tick(c);h.assertTrue(packets.size()==1,"Move did not emit");h.runAfterDelay(1,()->h.assertTrue(packets.size()==1,"Same action window repeated"));h.runAfterDelay(4,()->{h.assertTrue(packets.size()==2,"Next move did not rearm");c.resetForCreative(player);cow.discard();h.getLevel().players().remove(player);h.succeed();});}
 @GameTest(template="combat",batch="shock_contact",timeoutTicks=45)
 public static void ContactWaveUsesCollisionPointRearmsAfterSeparationAndHonorsSignalCooldown(GameTestHelper h){
  var c=fixture(h);var m=module(c);m.edit("shockContact",1,"");m.edit("contactDistance",.5,"");m.edit("contactCooldown",6,"");m.edit("trigger",1,"");
  var point=m.anchors().getFirst().world(c,false);var packets=new ArrayList<ShockwavePacket>();var player=viewer(h,point.add(2,0,0),packets,new ArrayList<>(),point);c.tryActivate(player);
  var ground=BlockPos.containing(ModuleEvents.contactStart(c,m).add(0,-.25,0));h.getLevel().setBlockAndUpdate(ground,Blocks.STONE.defaultBlockState());
  h.assertTrue(ModuleEvents.contact(h.getLevel(),c,m),"Contact fixture did not touch external ground");c.modules().tick(c);h.assertTrue(packets.isEmpty(),"Contact ignored inactive trigger");
  h.runAfterDelay(2,()->m.edit("trigger",0,""));
  h.runAfterDelay(4,()->{h.assertTrue(packets.size()==1,"Initial contact missing or held contact repeated");var hit=ModuleEvents.contactHit(h.getLevel(),c,m);h.assertTrue(hit!=null&&packets.getFirst().point().distanceTo(hit.getLocation())<.05,"Wave did not start at actual collision point");h.getLevel().setBlockAndUpdate(ground,Blocks.AIR.defaultBlockState());});
  h.runAfterDelay(6,()->h.getLevel().setBlockAndUpdate(ground,Blocks.STONE.defaultBlockState()));
  h.runAfterDelay(7,()->{h.assertTrue(packets.size()==1,"Contact cooldown ignored");h.getLevel().setBlockAndUpdate(ground,Blocks.AIR.defaultBlockState());});
  h.runAfterDelay(10,()->h.getLevel().setBlockAndUpdate(ground,Blocks.STONE.defaultBlockState()));
  h.runAfterDelay(12,()->{h.assertTrue(packets.size()==2,"Separated contact did not rearm");m.edit("trigger",1,"");h.getLevel().setBlockAndUpdate(ground,Blocks.AIR.defaultBlockState());});
  h.runAfterDelay(16,()->h.getLevel().setBlockAndUpdate(ground,Blocks.STONE.defaultBlockState()));
  h.runAfterDelay(18,()->{h.assertTrue(packets.size()==2,"Disabled signal emitted on contact");m.edit("trigger",0,"");});
  h.runAfterDelay(20,()->{h.assertTrue(packets.size()==3,"Reenabled gate did not arm contact");var saved=VirtualModule.load(m.save());h.assertTrue(saved.value("shockContact")==1&&saved.value("contactCooldown")==6&&saved.value("contactDistance")==.5,"Contact settings not saved");c.resetForCreative(player);h.getLevel().players().remove(player);h.succeed();});
 }

 @GameTest(template="combat",batch="shock_marker_preview")
 public static void PreviewShowsMarkedBlockAndContactProbeWithoutRepeatingWave(GameTestHelper h)throws Exception{
  var c=fixture(h);var m=module(c);m.edit("shockContact",1,"");var player=TestActors.creative(h.getLevel());player.setPos(m.anchors().getFirst().world(c,false));int[] counts={0,0};
  player.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(player.getGameProfile(),false)){@Override public void send(Packet<?> packet){if(packet instanceof ClientboundCustomPayloadPacket cp&&cp.payload() instanceof ShockwavePacket)counts[0]++;if(packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket)counts[1]++;}};
  MarkingSessions.showPreview(player,c,m);h.assertTrue(counts[0]==1&&counts[1]>0,"Preview did not show both one wave and marked block");int before=counts[1];
  var draw=MarkingSessions.class.getDeclaredMethod("previewModule",ServerPlayer.class,HeartBlockEntity.class,VirtualModule.class);draw.setAccessible(true);draw.invoke(null,player,c,m);h.assertTrue(counts[0]==1&&counts[1]>before,"Marker refresh repeated the wave or omitted geometry");int contactCount=counts[1]-before;
  m.edit("shockContact",0,"");before=counts[1];draw.invoke(null,player,c,m);h.assertTrue(counts[1]>before&&counts[1]-before<contactCount,"Normal marker missing or contact probe not conditional");m.clearMarks();before=counts[1];draw.invoke(null,player,c,m);h.assertTrue(counts[1]==before&&counts[0]==1,"Cleared mark still drew a preview");h.succeed();
 }

}
