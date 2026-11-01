package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class SensorHealthPhaseGameTests {
 private record Circuit(HeartBlockEntity core,UUID sensor,UUID phase,UUID particle){}
 private static Circuit circuit(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(4,40,4));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());for(int i=0;i<2;i++){c.wireless().add();c.wireless().channel(i).set(0,1);c.wireless().channel(i).set(6,3);}var phase=c.modules().add("health_phase");phase.edit("trigger",2,"");phase.edit("channel",1,"");phase.edit("phaseChannel",2,"");phase.edit("phaseLower",70,"");phase.edit("phaseUpper",100,"");phase.edit("phaseMode",0,"");var sensor=c.modules().add("sensor");sensor.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos().east(),Direction.NORTH)));sensor.edit("trigger",0,"");sensor.edit("target",1,"");sensor.edit("lineOfSight",1,"");sensor.edit("sensorChannel",1,"");var particle=c.modules().add("particles");particle.edit("particlePattern",1,"");particle.anchors(sensor.anchors());particle.edit("trigger",2,"");particle.edit("channel",2,"");c.tryActivate(TestActors.creative(l));return new Circuit(c,sensor.id(),phase.id(),particle.id());}
 private static net.minecraft.world.entity.animal.Cow target(GameTestHelper h,HeartBlockEntity c){var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()).add(0,0,-4));h.getLevel().addFreshEntity(cow);return cow;}
 private static void check(GameTestHelper h,Circuit x,boolean powered){var c=x.core();var snapshot=c.modules().snapshot(c,false).getList("Modules",10);var phase=c.modules().find(x.phase());h.assertTrue(HeartModules.triggered(phase,c)==powered,"Phase input differs from sensor channel; expected="+powered+" master="+c.wireless().master()+" ch1="+c.wireless().output(0)+" state="+c.health().state()+" hp="+c.health().current()+" detected="+c.modules().events().sensorDetected(x.sensor()));h.assertTrue(c.wireless().output(1)==(powered?15:0),"Phase Hold output remained waiting: "+snapshot);h.assertTrue(HeartModules.triggered(c.modules().find(x.particle()),c)==powered,"Particle did not follow phase output");h.assertTrue(snapshot.getCompound(c.modules().all().indexOf(phase)).getBoolean("PhaseActive")==powered,"Phase UI active state differs from output");}
 @GameTest(template="combat",batch="sensor_phase_hold",timeoutTicks=85)
 public static void SensorChannelOneDrivesHoldPhaseChannelTwoAcrossReloadAndMasterPause(GameTestHelper h){var x=circuit(h);var c=x.core();var cow=target(h,c);var targets=new ArrayList<net.minecraft.world.entity.animal.Cow>();targets.add(cow);
  // Keep targets in sensor range while native Sable physics moves the assembled fixture.
  for(int i=1;i<60;i++)h.runAfterDelay(i,()->{for(var t:targets)if(t.isAlive())t.setPos(SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()).add(0,0,-4));});
  h.runAfterDelay(3,()->check(h,x,true));
  h.runAfterDelay(10,()->{check(h,x,true);c.modules().load(c.modules().save());});
  h.runAfterDelay(14,()->{check(h,x,true);c.modules().reorder(x.phase(),1);});
  h.runAfterDelay(18,()->{check(h,x,true);c.wireless().master(false);});
  h.runAfterDelay(22,()->{check(h,x,false);c.wireless().master(true);});
  h.runAfterDelay(27,()->{check(h,x,true);cow.discard();});
  h.runAfterDelay(32,()->{check(h,x,false);targets.add(target(h,c));});
  h.runAfterDelay(37,()->{check(h,x,true);c.modules().find(x.phase()).edit("phaseMode",1,"");c.modules().find(x.phase()).edit("phasePulse",1,"");});
  h.runAfterDelay(42,()->{c.modules().find(x.phase()).edit("phaseMode",0,"");});
  h.runAfterDelay(47,()->{check(h,x,true);c.modules().clearRuntime();});
  h.runAfterDelay(52,()->{check(h,x,true);targets.forEach(net.minecraft.world.entity.Entity::discard);c.forceDeath();h.succeed();});
 }
}
