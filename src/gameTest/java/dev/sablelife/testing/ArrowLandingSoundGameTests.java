package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.attack.*;
import dev.sablelife.module.*;
import dev.sablelife.sound.SoundTimeline;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import java.util.*;
@GameTestHolder("sablelife_sound_tests") @PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="sablelife_tests")
public final class ArrowLandingSoundGameTests {
 private static final ThreadLocal<int[]> nativeHits=new ThreadLocal<>();
 @SubscribeEvent public static void sound(PlayLevelSoundEvent event){var count=nativeHits.get();if(count!=null&&event.getSound()!=null&&event.getSound().value()==SoundEvents.ARROW_HIT)count[0]++;}
 private static ModuleArrow arrow(GameTestHelper h,VirtualModule module,Vec3 start){var a=new ModuleArrow(SableLife.MODULE_ARROW.get(),h.getLevel());a.setPos(start);a.configure(module);a.setDeltaMovement(1,0,0);a.setBaseDamage(3);return a;}
 private static void block(ModuleArrow a,BlockHitResult hit)throws Exception{var m=ModuleArrow.class.getDeclaredMethod("onHitBlock",BlockHitResult.class);m.setAccessible(true);m.invoke(a,hit);}
 @GameTest(template="combat",batch="arrow_landing_mix")
 public static void LandingMixReplacesNativeSoundAndFreezesOnLaunchAndReload(GameTestHelper h)throws Exception{
  var l=h.getLevel();var pos=h.absolutePos(new BlockPos(4,6,4));l.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());var point=pos.getCenter();var hit=new BlockHitResult(point,Direction.WEST,pos,false);int[] count={0};nativeHits.set(count);
  var player=TestActors.creative(l);player.setPos(point);var packets=new ArrayList<ModuleNetworking.ModuleSound>();player.connection=new ServerGamePacketListenerImpl(l.getServer(),new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(player.getGameProfile(),false)){@Override public void send(Packet<?> p){if(p instanceof ClientboundCustomPayloadPacket payload&&payload.payload() instanceof ModuleNetworking.ModuleSound sound)packets.add(sound);}};l.players().add(player);
  try{
   var module=new VirtualModule("arrow");var nativeArrow=arrow(h,module,point.add(-1,0,0));block(nativeArrow,hit);h.assertTrue(count[0]==1&&packets.isEmpty()&&!nativeArrow.saveWithoutId(new CompoundTag()).contains("ModuleLandingSound"),"Default landing no longer preserves vanilla sound");
   var mix=module.soundTimeline("arrowLandSound");mix.edit("clear",null,0,"");mix.edit("add",null,0,"minecraft:block.note_block.bass");var row=mix.rows().getFirst();mix.edit("start",row.id(),.4,"");mix.edit("duration",row.id(),.3,"");mix.edit("fade",row.id(),.1,"");mix.edit("radius",row.id(),40,"");mix.edit("add",null,0,"minecraft:block.note_block.harp");module.soundTimeline("arrowLandSound",mix);var shot=arrow(h,module,point.add(-1,0,0));mix.edit("clear",null,0,"");module.soundTimeline("arrowLandSound",mix);
   var saved=shot.saveWithoutId(new CompoundTag());h.assertTrue(saved.getCompound("ModuleLandingSound").getList("Rows",10).size()==2,"Editor changed an already fired arrow");var loaded=new ModuleArrow(SableLife.MODULE_ARROW.get(),l);loaded.load(saved);block(loaded,hit);
   h.assertTrue(count[0]==1&&packets.size()==2&&packets.getFirst().sound().equals("minecraft:block.note_block.bass")&&packets.getFirst().start()==8&&packets.getFirst().duration()==6&&packets.getFirst().fade()==2&&packets.getFirst().position().distanceTo(point)<.001,"Custom landing doubled native audio or lost mix data/location");h.assertTrue(loaded.saveWithoutId(new CompoundTag()).getBoolean("inGround"),"Sound edit changed embedding");
   var silent=arrow(h,module,point.add(-1,0,0));block(silent,hit);h.assertTrue(count[0]==1&&packets.size()==2,"Empty mix did not silence landing");
   packets.clear();var livingHit=arrow(h,VirtualModule.load(shotModule(saved).save()),point.add(-1,0,0));var cow=EntityType.COW.create(l);cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20);cow.setHealth(20);cow.setPos(point);var method=ModuleArrow.class.getDeclaredMethod("onHitEntity",EntityHitResult.class);method.setAccessible(true);method.invoke(livingHit,new EntityHitResult(cow));h.assertTrue(count[0]>1&&packets.isEmpty()&&cow.getHealth()<20,"Landing mix replaced entity-hit sound/damage");cow.discard();h.succeed();
  }finally{nativeHits.remove();l.players().remove(player);}
 }
 private static VirtualModule shotModule(CompoundTag shot){var m=new VirtualModule("arrow");m.soundTimeline("arrowLandSound",SoundTimeline.load(shot.getCompound("ModuleLandingSound")));return m;}
 @GameTest(template="combat",batch="arrow_landing_blast")
 public static void ExplosiveAcceptedBlockImpactPlaysOneLandingMix(GameTestHelper h){var l=h.getLevel();var pos=h.absolutePos(new BlockPos(4,6,4));l.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());var point=pos.getCenter();var module=new VirtualModule("arrow");module.edit("explode",1,"");module.edit("blastDamage",0,"");var mix=new SoundTimeline();mix.edit("add",null,0,"minecraft:block.note_block.bass");module.soundTimeline("arrowLandSound",mix);var shot=arrow(h,module,point.add(-1,0,0));ModuleBlast.configure(shot,module);
  var player=TestActors.creative(l);player.setPos(point);var packets=new ArrayList<ModuleNetworking.ModuleSound>();player.connection=new ServerGamePacketListenerImpl(l.getServer(),new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(player.getGameProfile(),false)){@Override public void send(Packet<?> p){if(p instanceof ClientboundCustomPayloadPacket payload&&payload.payload() instanceof ModuleNetworking.ModuleSound sound)packets.add(sound);}};l.players().add(player);
  try{var impact=new net.neoforged.neoforge.event.entity.ProjectileImpactEvent(shot,new BlockHitResult(point,Direction.WEST,pos,false));ModuleBlast.impact(impact);ModuleBlast.impact(impact);h.assertTrue(shot.isRemoved()&&impact.isCanceled()&&packets.size()==1&&packets.getFirst().sound().equals("minecraft:block.note_block.bass"),"Explosive block impact missed/doubled landing mix");h.succeed();}finally{l.players().remove(player);}
 }
}
