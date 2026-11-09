package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.attack.ContactBox;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;
@GameTestHolder("sablelife_facing_tests") @PrefixGameTestTemplate(false)
public final class GyroscopeGameTests {
 private static HeartBlockEntity fixture(GameTestHelper h,boolean control){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));l.setBlockAndUpdate(p,(control?SableLife.REDSTONE_CORE.get():SableLife.HEART.get()).defaultBlockState());l.setBlockAndUpdate(p.north(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.north()),new BoundingBox3i(p.getX(),p.getY(),p.getZ()-1,p.getX(),p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}
 private static VirtualModule gyro(HeartBlockEntity c){var m=c.modules().add("gyroscope");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH)));return m;}
 private static CompoundTag state(HeartBlockEntity c,VirtualModule m){return c.modules().snapshot(c,true).getList("Modules",10).getCompound(c.modules().all().indexOf(m));}
 private static void orient(HeartBlockEntity c,Quaterniond rotation){var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)c.modules().all().getFirst().anchors().getFirst().body(c);var system=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(c.getLevel());system.getPipeline().resetVelocity(body);system.getPipeline().teleport(body,new Vector3d(body.logicalPose().position()),rotation);}
 @GameTest(template="combat",batch="gyro_solid_preview")
 public static void SolidPreviewKeepsWorldAndCalibratedAxesWithoutParticles(GameTestHelper h)throws Exception{
  var c=fixture(h,false);var m=gyro(c);m.gyroDown(new Vec3(.5,-Math.sqrt(.75),0));
  var player=TestActors.creative(h.getLevel());var outlines=new EnumMap<MarkOutline.Kind,MarkOutline>(MarkOutline.Kind.class);int[] particles={0};
  player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)){
   @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
    if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket p&&p.payload() instanceof MarkOutline outline)outlines.put(outline.kind(),outline);
    if(packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket)particles[0]++;
   }
  };
  var draw=MarkingSessions.class.getDeclaredMethod("previewModule",net.minecraft.server.level.ServerPlayer.class,HeartBlockEntity.class,VirtualModule.class);draw.setAccessible(true);draw.invoke(null,player,c,m);
  var center=m.anchors().getFirst().world(c,false);var world=outlines.get(MarkOutline.Kind.ALIGN_DOWN);var body=outlines.get(MarkOutline.Kind.DETAIL);var cone=outlines.get(MarkOutline.Kind.VOLUME);
  h.assertTrue(particles[0]==0&&outlines.size()==4,"Gyroscope preview still emitted particles or missed its marked block");
  h.assertTrue(world.color()==0x33CCFF&&body.color()==0xFF9933&&cone.color()==0x33FF66,"Preview colors changed");
  h.assertTrue(world.endpoints().size()==10&&world.endpoints().get(1).distanceTo(center.add(0,-2.6,0))<1E-7,"World-down arrow did not stay vertical");
  h.assertTrue(body.endpoints().size()==10&&body.endpoints().get(1).distanceTo(center.add(ModuleGyroscope.down(c,m).scale(2.2)))<1E-7,"Calibrated arrow lost direction");
  h.assertTrue(cone.endpoints().size()==104,"Tilt boundary is not a connected ring with guides");
  for(int i=0;i<96;i++){
   var offset=cone.endpoints().get(i).subtract(center);
   h.assertTrue(Math.abs(offset.length()-2)<1E-6&&Math.abs(ModuleGyroscope.tilt(offset)-m.value("gyroTilt"))<1E-6,"Tilt boundary changed meaning");
  }
  m.edit("enabled",0,"");outlines.clear();draw.invoke(null,player,c,m);h.assertTrue(outlines.size()==4,"Disabled module lost its preview");
  for(double angle:new double[]{0,90,180}){
   m.edit("gyroTilt",angle,"");outlines.clear();draw.invoke(null,player,c,m);
   h.assertTrue(!outlines.get(MarkOutline.Kind.VOLUME).endpoints().isEmpty()&&outlines.values().stream().flatMap(p->p.endpoints().stream()).allMatch(p->Double.isFinite(p.lengthSqr())),"Extreme tilt produced invalid or invisible geometry");
  }
  m.gyroDown(null);outlines.clear();draw.invoke(null,player,c,m);
  h.assertTrue(outlines.get(MarkOutline.Kind.DETAIL).endpoints().isEmpty(),"Uncalibrated preview retained an old axis");
  m.clearMarks();outlines.clear();draw.invoke(null,player,c,m);h.assertTrue(outlines.isEmpty()&&particles[0]==0,"Missing mark still drew a preview");h.succeed();
 }
 @GameTest(template="combat",batch="gyro_calibration",timeoutTicks=35)
 public static void SlopedPostureCalibrationPersistsThroughReleaseReloadAndNeedsNoDownFacingMark(GameTestHelper h){var c=fixture(h,false);var m=gyro(c);orient(c,new Quaterniond().rotateZ(Math.toRadians(30)));
  h.runAfterDelay(3,()->{var author=TestActors.creative(h.getLevel());author.setPos(SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()));var menu=new HeartDashboardMenu(2,author.getInventory(),c);author.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);h.assertTrue(!menu.action(author,c.modules().revision(),m.id(),"gyro_calibrate","",0,""),"Survival calibrated life core");author.setGameMode(net.minecraft.world.level.GameType.CREATIVE);h.assertTrue(!menu.action(author,c.modules().revision()-1,m.id(),"gyro_calibrate","",0,""),"Stale calibration edit accepted");h.assertTrue(menu.action(author,c.modules().revision(),m.id(),"gyro_calibrate","",0,""),"Server calibration rejected reference part");var down=m.gyroDown();h.assertTrue(Math.abs(down.x)>.1&&ModuleGyroscope.tilt(ModuleGyroscope.down(c,m))<.01,"Sloped posture was forced to its marked face");c.tryActivate(author);h.assertTrue(c.health().state()==HeartState.ALIVE&&c.resetForCreative(author)&&m.gyroDown().distanceTo(down)<1e-8,"Release erased calibration");var saved=c.modules().save();c.modules().load(saved);var loaded=c.modules().find(m.id());h.assertTrue(loaded.gyroDown().distanceTo(down)<1e-8&&ModuleGyroscope.tilt(ModuleGyroscope.down(c,loaded))<.01,"Reload erased local gravity direction");h.assertTrue(ModulePreset.create(loaded.save()).gyroDown()==null&&!c.copyConfiguration().getList("Modules",10).getCompound(0).contains("GyroDown"),"Portable configuration retained another structure calibration");var old=loaded.save();old.getCompound("GyroDown").putDouble("X",Double.NaN);h.assertTrue(VirtualModule.load(old).gyroDown()==null,"Nonfinite calibration loaded");c.tryActivate(author);h.assertTrue(c.health().state()==HeartState.ALIVE,"Reactivation failed");c.resetForCreative(author);author.closeContainer();h.succeed();});
 }
 @GameTest(template="combat",batch="gyro_native_recovery",timeoutTicks=45)
 public static void ActualRecoveryClaimsRotationAndStopsOnMasterAndDeath(GameTestHelper h){var c=fixture(h,false);var m=gyro(c);h.assertTrue(ModuleGyroscope.calibrate(c,m),"Upright calibration failed");m.edit("gyroCollision",0,"");m.edit("enabled",0,"");c.tryActivate(TestActors.creative(h.getLevel()));orient(c,new Quaterniond().rotateZ(Math.toRadians(40)));
  h.runAfterDelay(3,()->{h.assertTrue(ModuleGyroscope.tilt(ModuleGyroscope.down(c,m))>25,"Native tilt fixture did not tip");int ch=c.wireless().add();c.wireless().channel(ch).set(0,1);c.wireless().channel(ch).set(6,3);c.wireless().programOutputs("gyro_test",Map.of(ch,15));var turn=c.modules().add("signal_turn");turn.anchors(m.anchors());turn.edit("turnLeftChannel",ch+1,"");turn.edit("turnCollision",0,"");var auto=c.modules().add("auto_facing");auto.anchors(m.anchors());m.edit("enabled",1,"");});
  h.runAfterDelay(5,()->{var rows=c.modules().snapshot(c,true).getList("Modules",10);h.assertTrue(rows.getCompound(0).getString("GyroState").equals("recovering")&&rows.getCompound(1).getString("TurnState").equals("busy")&&rows.getCompound(2).getString("FacingState").equals("busy"),"Rotation controllers fought the recovering gyro");});
  h.runAfterDelay(11,()->{h.assertTrue(ModuleGyroscope.tilt(ModuleGyroscope.down(c,m))<20,"Native machine did not right itself");c.wireless().master(false);});
  h.runAfterDelay(13,()->{h.assertTrue(state(c,m).getString("GyroState").equals("idle"),"Master Off kept gyro active");m.edit("gyroTilt",0,"");c.wireless().master(true);});
  h.runAfterDelay(23,()->{h.assertTrue(ModuleGyroscope.tilt(ModuleGyroscope.down(c,m))<1,"Full-level recovery stopped at threshold");c.forceDeath();c.modules().tick(c);h.assertTrue(state(c,m).getString("GyroState").equals("idle"),"Death kept gyro correcting");h.succeed();});
 }
 @GameTest(template="combat",batch="gyro_collision_budget")
 public static void CollisionBudgetAndMovementOwnershipPauseWithoutErasingCalibration(GameTestHelper h){var c=fixture(h,false);var m=gyro(c);m.gyroDown(ContactBox.rotate(new Quaterniond().rotateZ(Math.toRadians(40)),ModuleGyroscope.DOWN));m.edit("gyroSpeed",720,"");c.tryActivate(TestActors.creative(h.getLevel()));var pos=BlockPos.containing(SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()));for(var p:BlockPos.betweenClosed(pos.offset(-2,-1,-2),pos.offset(2,-1,2)))h.getLevel().setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());var runtime=new ModuleGyroscope();h.assertTrue(runtime.tick(c,false)&&runtime.state(m.id()).equals("blocked"),"Gyro rotated through floor");runtime.clear();h.assertTrue(!runtime.tick(c,true)&&runtime.state(m.id()).equals("busy")&&m.gyroDown()!=null,"Linear movement did not retain priority/calibration");runtime.clear();var budget=CollisionWorkBudget.begin(h.getLevel());for(int step=1<<30;step>0;step>>=1)while(budget.spend(step)){}h.assertTrue(runtime.tick(c,false)&&runtime.state(m.id()).equals("budget"),"Exhausted sweep forced rotation");budget.refund(dev.sablelife.SableLifeServerConfig.collisionChecksPerTick());c.resetForCreative(TestActors.creative(h.getLevel()));h.succeed();}
 @GameTest(template="combat",batch="gyro_axes")
 public static void WorldDownIgnoresYawAndMinimalCorrectionPreservesRelativePartPoses(GameTestHelper h){var c=fixture(h,true);var m=gyro(c);m.gyroDown(ModuleGyroscope.DOWN);var yaw=new Quaterniond().rotateY(1.1);h.assertTrue(ModuleGyroscope.tilt(ContactBox.rotate(yaw,m.gyroDown()))<1e-6,"Yaw counted as tilt");var tipped=ContactBox.rotate(new Quaterniond().rotateY(1.1).rotateZ(.7),ModuleGyroscope.DOWN);var turn=AutoFacingGeometry.turn(tipped,ModuleGyroscope.DOWN,true,5);h.assertTrue(Math.abs(ModuleGyroscope.tilt(tipped)-ModuleGyroscope.tilt(ContactBox.rotate(turn,tipped))-5)<1e-6,"Correction ignored rate limit");var bodies=LinearMovementGeometry.capture(c);var first=bodies.getFirst();var second=new LinearMovementGeometry.Body(first.body(),new Vector3d(first.position()).add(2,1,0),new Quaterniond(first.rotation()).rotateX(.3),first.box().move(2,1,0));var before=new Quaterniond(first.rotation()).invert().mul(second.rotation());var rotated=AutoFacingGeometry.rotated(List.of(first,second),SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()),turn);var after=new Quaterniond(rotated.getFirst().rotation()).invert().mul(rotated.getLast().rotation());h.assertTrue(Math.abs(before.dot(after))>1-1e-8&&Math.abs(rotated.getFirst().position().distance(rotated.getLast().position())-Math.sqrt(5))<1e-8,"Correction changed relative part pose");h.assertTrue(!m.gyroDown(new Vec3(0,0,0))&&!m.gyroDown(new Vec3(Double.NaN,1,0)),"Invalid local direction accepted");var clone=VirtualModule.load(m.save());clone.replaceStoredAnchor("Anchors",0,new ModuleAnchor(UUID.randomUUID(),BlockPos.ZERO,Direction.DOWN));h.assertTrue(clone.gyroDown()==null,"New reference body retained stale calibration");h.succeed();}
 @GameTest(template="combat",batch="gyro_limit_hold",timeoutTicks=35)
 public static void InternalTriggerAndInsideLimitRecoveryDoNotForceAnAlreadyAcceptablePosture(GameTestHelper h){var c=fixture(h,true);var m=gyro(c);ModuleGyroscope.calibrate(c,m);m.edit("gyroReturn",1,"");m.edit("gyroCollision",0,"");m.edit("enabled",0,"");int channel=c.wireless().add();c.wireless().channel(channel).set(0,1);c.wireless().channel(channel).set(6,3);m.edit("trigger",2,"");m.edit("channel",channel+1,"");orient(c,new Quaterniond().rotateZ(Math.toRadians(40)));
  h.runAfterDelay(3,()->m.edit("enabled",1,""));
  h.runAfterDelay(4,()->{h.assertTrue(ModuleGyroscope.tilt(ModuleGyroscope.down(c,m))>35&&state(c,m).getString("GyroState").equals("idle"),"Unpowered channel still corrected posture");c.wireless().programOutputs("gyro_limit_test",Map.of(channel,15));});
  h.runAfterDelay(12,()->{double tilt=ModuleGyroscope.tilt(ModuleGyroscope.down(c,m));h.assertTrue(tilt>15&&tilt<18&&state(c,m).getString("GyroState").equals("stable"),"Inside-limit mode forced full level or stopped outside hysteresis: "+tilt);m.edit("enabled",0,"");});
  h.runAfterDelay(15,()->m.edit("enabled",1,""));
  h.runAfterDelay(18,()->{h.assertTrue(ModuleGyroscope.tilt(ModuleGyroscope.down(c,m))>15&&state(c,m).getString("GyroState").equals("stable"),"Re-enabling forced an acceptable posture");m.clearMarks();h.assertTrue(m.gyroDown()==null&&!HeartModules.triggered(m,c),"Clear mark retained calibration or triggered rotation");h.succeed();});
 }
}
