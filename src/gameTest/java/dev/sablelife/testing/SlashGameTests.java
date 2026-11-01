package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.List;
@GameTestHolder("sablelife_blast_tests") @PrefixGameTestTemplate(false)
public final class SlashGameTests {
 private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,6,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}
 private static VirtualModule module(HeartBlockEntity core){var m=core.modules().add("slash");m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.UP)));m.edit("damage",3,"");m.edit("interval",1000,"");return m;}
 private static Vec3 start(HeartBlockEntity c,VirtualModule m){return m.anchors().getFirst().world(c,false).add(0,.55+m.value("startDistance"),0);}
 private static Pig pig(GameTestHelper h,Vec3 p){var e=EntityType.PIG.create(h.getLevel());e.setPos(p.x,p.y,p.z);e.setNoAi(true);e.setNoGravity(true);h.getLevel().addFreshEntity(e);return e;}
 @GameTest(template="combat",batch="slash_geometry") public static void FanOffsetsAndSweptRotationAreConsistent(GameTestHelper h){var m=new VirtualModule("slash");m.edit("slashCount",3,"");m.edit("slashFan",60,"");var f=ModuleSlash.frames(new Vec3(0,0,-1),m);h.assertTrue(f.size()==3&&f.get(1).direction().distanceTo(new Vec3(0,0,-1))<1e-8&&Math.abs(f.get(0).direction().dot(f.get(2).direction())-.5)<1e-8,"Fan not centered or wrong angle");m.edit("slashHorizontal",90,"");m.edit("slashCount",1,"");h.assertTrue(Math.abs(ModuleSlash.frames(new Vec3(0,0,-1),m).getFirst().direction().x)>.99,"Horizontal offset ignored");m.edit("slashHorizontal",0,"");m.edit("slashVertical",90,"");h.assertTrue(Math.abs(ModuleSlash.frames(new Vec3(0,0,-1),m).getFirst().direction().y)>.99,"Vertical offset ignored");var frame=ModuleSlash.frames(new Vec3(0,0,1),new VirtualModule("slash")).getFirst();h.assertTrue(ModuleSlash.intersects(Vec3.ZERO,new Vec3(0,0,10),frame,4,.5,new AABB(-.1,-.1,5,.1,.1,5.2))&&!ModuleSlash.intersects(Vec3.ZERO,new Vec3(0,0,10),frame,4,.5,new AABB(4,0,5,5,1,6)),"Sweep skipped fast target or hit outside blade");h.succeed();}
 @GameTest(template="combat",batch="slash_save") public static void CustomAppearanceAndAngleSettingsPersist(GameTestHelper h){var m=new VirtualModule("slash");m.edit("slashHorizontal",25,"");m.edit("slashVertical",-15,"");m.edit("slashRed",50,"");m.edit("slashCount",2.7,"");m.editParticle("add","minecraft:soul_fire_flame");m.editEffect("add","minecraft:slowness",0);var copy=VirtualModule.load(m.save());h.assertTrue(copy.value("slashHorizontal")==25&&copy.value("slashVertical")==-15&&copy.value("slashRed")==50&&copy.value("slashCount")==3&&copy.particles().size()==1&&copy.effects().size()==1,"Appearance/count/offset did not persist");h.assertTrue(MoveSet.TARGETS.contains(m.type())&&!RedstoneCorePolicy.allowed(m),"Attack whitelist wrong");h.succeed();}
 @GameTest(template="combat",batch="slash_fast",timeoutTicks=25) public static void FastBladeHitsOnceAcrossTickAndAppliesEffect(GameTestHelper h){var c=fixture(h);var m=module(c);m.edit("slashSpeed",80,"");m.edit("range",8,"");m.editEffect("add","minecraft:slowness",0);var target=pig(h,start(c,m).add(0,2,0));c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);h.runAfterDelay(5,()->{h.assertTrue(target.getHealth()==7&&target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Fast blade skipped, repeated hit or lost effects: "+target.getHealth());h.succeed();});}
 @GameTest(template="combat",batch="slash_wall",timeoutTicks=25) public static void WallStopsFlightAndDoesNotBreakBlocks(GameTestHelper h){var c=fixture(h);var m=module(c);m.edit("slashSpeed",80,"");var a=start(c,m);var wall=BlockPos.containing(a.add(0,2,0));h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());var target=pig(h,a.add(0,4,0));c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);h.runAfterDelay(5,()->{h.assertTrue(target.getHealth()==10&&h.getLevel().getBlockState(wall).is(Blocks.STONE),"Wall failed or was destroyed");h.succeed();});}
 @GameTest(template="combat",batch="slash_cancel",timeoutTicks=25) public static void LostMarkStopsReleasesButExistingBladeFinishes(GameTestHelper h){var c=fixture(h);var m=module(c);m.edit("slashSpeed",20,"");var target=pig(h,start(c,m).add(0,5,0));c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);m.clearMarks();h.runAfterDelay(10,()->{h.assertTrue(target.getHealth()==7,"Mark loss erased released blade or allowed another launch");h.succeed();});}
 @GameTest(template="combat",batch="slash_hurt",timeoutTicks=25) public static void HurtOnceLivesThroughFlightWithoutRepeating(GameTestHelper h){var c=fixture(h);var m=module(c);m.edit("trigger",5,"");m.edit("slashSpeed",20,"");m.edit("range",8,"");var target=pig(h,start(c,m).add(0,4,0));c.tryActivate(TestActors.creative(h.getLevel()));c.damage(1);h.runAfterDelay(15,()->{h.assertTrue(target.getHealth()==7&&!c.modules().hurtTriggers.powered(m.id()),"Hurt signal too short or volley repeated: "+target.getHealth());h.succeed();});}
 @GameTest(template="combat",batch="slash_overlap",timeoutTicks=30) public static void ReleaseIntervalAllowsOverlappingVolleys(GameTestHelper h){var c=fixture(h);var m=module(c);m.edit("slashSpeed",20,"");m.edit("range",8,"");m.edit("interval",3,"");m.edit("damage",1,"");var target=pig(h,start(c,m).add(0,4,0));c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);h.runAfterDelay(10,()->{h.assertTrue(target.getHealth()<=7,"Interval waited for earlier blades to vanish: "+target.getHealth());m.clearMarks();h.succeed();});}
 @GameTest(template="combat",batch="slash_signal_tail",timeoutTicks=30) public static void HurtDurationStopsNewReleasesButFlightTailContinues(GameTestHelper h){var c=fixture(h);var m=module(c);m.edit("trigger",5,"");m.edit("hurtMode",1,"");m.edit("hurtDuration",4,"");m.edit("slashSpeed",20,"");m.edit("range",8,"");m.edit("interval",2,"");m.edit("damage",1,"");var target=pig(h,start(c,m).add(0,5,0));c.tryActivate(TestActors.creative(h.getLevel()));c.damage(1);h.runAfterDelay(14,()->{h.assertTrue(target.getHealth()==8&&!c.modules().hurtTriggers.powered(m.id()),"Signal end erased flights or kept releasing: "+target.getHealth());h.succeed();});}

 @GameTest(template="combat",batch="slash_moving_audio",timeoutTicks=35)
 public static void FlightSoundTracksEachBladeAndStopsIndependently(GameTestHelper h){
  var c=fixture(h);var m=module(c);m.edit("slashCount",2,"");m.edit("slashFan",30,"");m.edit("slashSpeed",20,"");m.edit("range",10,"");
  var audio=m.soundTimeline("slashFlightSound");audio.edit("clear",null,0,"");h.assertTrue(audio.edit("add",null,0,"minecraft:entity.blaze.ambient"),"Sound row missing");m.soundTimeline("slashFlightSound",audio);
  var packets=new java.util.ArrayList<net.minecraft.network.protocol.common.custom.CustomPacketPayload>();var viewer=TestActors.creative(h.getLevel());viewer.setPos(start(c,m));
  viewer.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),viewer,net.minecraft.server.network.CommonListenerCookie.createInitial(viewer.getGameProfile(),false)){
   @Override public void send(net.minecraft.network.protocol.Packet<?> packet){if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket cp)packets.add(cp.payload());}
  };h.getLevel().players().add(viewer);c.tryActivate(viewer);c.modules().tick(c);
  h.runAfterDelay(4,()->{
   var sounds=packets.stream().filter(p->p instanceof ModuleNetworking.ModuleSound v&&!v.stop()&&!v.module().equals(m.id())).map(p->(ModuleNetworking.ModuleSound)p).toList();
   h.assertTrue(packets.stream().filter(p->p instanceof ModuleNetworking.ModuleSound v&&!v.stop()&&v.module().equals(m.id())).count()==1,"Volley replayed launch sound per blade");
   h.assertTrue(sounds.size()==2&&sounds.stream().map(ModuleNetworking.ModuleSound::module).distinct().count()==2&&sounds.stream().noneMatch(p->p.module().equals(m.id())),"Flying blades shared/restarted launch audio");
   var motion=packets.stream().filter(p->p instanceof SoundMotionPacket v&&!v.stop()).map(p->(SoundMotionPacket)p).toList();
   for(var sound:sounds)h.assertTrue(motion.stream().filter(p->p.source().equals(sound.module())).map(SoundMotionPacket::position).distinct().count()>=3,"Sound did not travel with blade");
   m.clearMarks();
  });
  h.runAfterDelay(16,()->{
   var starts=packets.stream().filter(p->p instanceof ModuleNetworking.ModuleSound v&&!v.stop()&&!v.module().equals(m.id())).map(p->((ModuleNetworking.ModuleSound)p).module()).toList();
   for(var id:starts)h.assertTrue(packets.stream().anyMatch(p->p instanceof SoundMotionPacket v&&v.stop()&&v.source().equals(id)),"Flight end did not stop independent sound");
   h.getLevel().players().remove(viewer);h.succeed();
  });
 }

}
