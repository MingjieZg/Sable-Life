package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.client.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Matrix4f;
import java.util.*;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class DistortionTrailGameTests {
 private static TrailMotion.Pose pose(double x){return new TrailMotion.Pose(new Vec3(x,0,0),new Quaterniond());}
 private static DistortionTrailPacket packet(UUID id,int life){return new DistortionTrailPacket(ResourceLocation.withDefaultNamespace("overworld"),id,List.of(new DistortionTrailPacket.Sample(new Vec3(0,0,-10),0,0,0,1)),4,1,2,life,.8f,64);}
 @GameTest(template="combat",batch="trail_motion")
 public static void MotionRequiresMovementInterpolatesRotationAndBoundsTeleport(GameTestHelper h){
  var motion=new TrailMotion();var half=new Vec3(.5,.5,3);h.assertTrue(motion.advance(pose(0),half,.5,.1).isEmpty(),"First baseline emitted");h.assertTrue(motion.advance(pose(0),half,.5,.1).isEmpty(),"Static mark emitted");h.assertTrue(motion.advance(pose(.001),half,.5,.1).isEmpty(),"Below threshold emitted");
  var samples=motion.advance(new TrailMotion.Pose(new Vec3(.001,0,0),new Quaterniond().rotateY(Math.PI/2)),half,.5,.1);h.assertTrue(samples.size()==8&&samples.getFirst().rotation().angle()<Math.PI/2,"Pure rotation did not interpolate/bound births");
  h.assertTrue(motion.advance(pose(100),half,0,.1).isEmpty(),"Teleport connected distant poses");motion.clear();motion.advance(pose(0),half,0,.35);int births=0;for(int i=1;i<=20;i++)births+=motion.advance(pose(i*.05),half,.5,.35).size();h.assertTrue(births==40,"Small moving ticks lost ribbon endpoints: "+births);h.succeed();
 }
 @GameTest(template="combat",batch="trail_packet")
 public static void SettingsCodecAndImportPreserveVisualDimensions(GameTestHelper h){
  var m=new VirtualModule("distortion_trail");h.assertTrue(m.known()&&!m.area()&&MoveSet.TARGETS.contains(m.type())&&RedstoneCorePolicy.allowed(m),"New module missing authoring/dispatch/control support");
  h.assertTrue(m.edit("trailLength",12,"")&&m.edit("trailLife",20,"")&&m.edit("trailWidth",65,"")&&m.value("trailWidth")==64&&!m.edit("trailDistortion",Double.NaN,""),"Invalid settings accepted");var loaded=VirtualModule.load(m.save());h.assertTrue(loaded.value("trailLength")==12&&loaded.value("trailLife")==20,"Save lost dimensions/lifetime");
  var p=packet(UUID.randomUUID(),10);var b=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());try{DistortionTrailPacket.CODEC.encode(b,p);h.assertTrue(DistortionTrailPacket.CODEC.decode(b).equals(p),"Packet changed frozen sample");}finally{b.release();}
  h.assertTrue(p.valid()&&!new DistortionTrailPacket(p.dimension(),p.module(),List.of(new DistortionTrailPacket.Sample(Vec3.ZERO,0,0,0,0)),1,1,1,10,1,64).valid(),"Invalid quaternion accepted");h.succeed();
 }
 @GameTest(template="combat",batch="trail_client_state")
 public static void OldPosesStayFrozenFadeAndExpireUnderLimits(GameTestHelper h){
  var state=new DistortionTrailState();var id=UUID.randomUUID();for(int i=0;i<200;i++)state.accept(packet(id,60),0);h.assertTrue(state.births().size()==128,"Per module cap lost");for(int i=0;i<200;i++)state.accept(packet(UUID.randomUUID(),60),0);h.assertTrue(state.births().size()==256,"Global cap lost");var first=state.births().getFirst();state.tick(20);h.assertTrue(first.sample().center().equals(new Vec3(0,0,-10))&&DistortionTrailState.fade(20,60)<DistortionTrailState.fade(1,60),"Old pose moved or failed to fade");state.tick(60);h.assertTrue(state.births().isEmpty(),"Stopped signal left permanent trail");state.accept(packet(id,10),61);state.clear();h.assertTrue(state.births().isEmpty(),"World clear retained trail");h.succeed();
 }
 @GameTest(template="combat",batch="trail_projection")
 public static void AllDimensionsAndRotationAffectProjectedVolume(GameTestHelper h){
  var p=packet(UUID.randomUUID(),10);var view=new Matrix4f();var projection=new Matrix4f().perspective((float)Math.PI/2,1,.05f,200);var sample=p.samples().getFirst();var a=DistortionTrailMath.project(sample,new Vec3(2,.5,1),Vec3.ZERO,view,projection,1);var q=new Quaterniond().rotateZ(Math.PI/2);var rotated=new DistortionTrailPacket.Sample(sample.center(),(float)q.x,(float)q.y,(float)q.z,(float)q.w);var b=DistortionTrailMath.project(rotated,new Vec3(2,.5,1),Vec3.ZERO,view,projection,1);h.assertTrue(a!=null&&b!=null&&Math.abs(a[2]-b[3])<.02&&Math.abs(a[3]-b[2])<.02,"Oriented width/height not projected");h.assertTrue(DistortionTrailMath.project(new DistortionTrailPacket.Sample(new Vec3(0,0,10),0,0,0,1),new Vec3(1,1,1),Vec3.ZERO,view,projection,1)==null,"Behind camera trail projected");h.succeed();
 }
 @GameTest(template="combat",batch="trail_native",timeoutTicks=45)
 public static void NativePinnedPartRotationEmitsThenDisableStopsWithoutDamage(GameTestHelper h){
  var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,40,3));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.north(),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.north()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ()-1,pos.getX(),pos.getY(),pos.getZ()));var c=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());var a=ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH);var hover=c.modules().add("hover_anchor");hover.anchors(List.of(a));var m=c.modules().add("distortion_trail");m.anchors(List.of(a));m.edit("trailSpacing",.1,"");
  var packets=new ArrayList<DistortionTrailPacket>();var farPackets=new ArrayList<DistortionTrailPacket>();var near=viewer(h,a.world(c,false),m.id(),packets);var far=viewer(h,a.world(c,false).add(500,0,0),m.id(),farPackets);c.tryActivate(near);c.modules().tick(c);h.assertTrue(packets.isEmpty(),"Activation emitted without motion");
  SubLevelPhysicsSystem.get(level).getPipeline().addLinearAndAngularVelocity((ServerSubLevel)body,new Vector3d(),new Vector3d(0,3,0));int[] count={0};
  h.runAfterDelay(8,()->{h.assertTrue(!packets.isEmpty()&&packets.stream().allMatch(DistortionTrailPacket::valid)&&farPackets.isEmpty(),"Native part swing missing or broadcast out of range");m.edit("enabled",0,"");c.modules().tick(c);count[0]=packets.size();});
  h.runAfterDelay(11,()->{h.assertTrue(packets.size()==count[0]&&c.health().current()==100,"Disabled trail emitted or damaged core");m.edit("trigger",5,"");m.edit("enabled",1,"");c.modules().hurtTriggers.hurt(c);c.modules().tick(c);});
  h.runAfterDelay(15,()->h.assertTrue(packets.size()>count[0]&&c.modules().hurtTriggers.powered(m.id()),"Hurt Once expired before motion could emit"));
  h.runAfterDelay(25,()->{h.assertTrue(!c.modules().hurtTriggers.powered(m.id()),"Hurt Once window did not end");level.players().remove(near);level.players().remove(far);c.forceDeath();c.modules().tick(c);h.succeed();});
 }
 private static ServerPlayer viewer(GameTestHelper h,Vec3 pos,UUID module,List<DistortionTrailPacket> packets){var p=TestActors.creative(h.getLevel());p.setPos(pos);p.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false)){@Override public void send(Packet<?> packet){if(packet instanceof ClientboundCustomPayloadPacket cp&&cp.payload() instanceof DistortionTrailPacket trail&&trail.module().equals(module))packets.add(trail);}};h.getLevel().players().add(p);return p;}
}
