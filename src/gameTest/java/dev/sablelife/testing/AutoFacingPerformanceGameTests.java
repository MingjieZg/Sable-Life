package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_facing_perf_tests") @PrefixGameTestTemplate(false)
public final class AutoFacingPerformanceGameTests {
 @GameTest(template="combat",batch="facing_mspt",timeoutTicks=1100)
 public static void CompareZeroAndFractionalOffsetsAcrossThirtyTwoActiveCores(GameTestHelper h){
  var cores=new ArrayList<HeartBlockEntity>();var bodies=new ArrayList<ServerSubLevel>();var poses=new ArrayList<org.joml.Vector3d>();var cows=new ArrayList<net.minecraft.world.entity.animal.Cow>();var centers=new ArrayList<Vec3>();long started=System.nanoTime();
  for(int i=0;i<32;i++){
   var p=h.absolutePos(new BlockPos(3+i*5,40,3));h.getLevel().setChunkForced(p.getX()>>4,p.getZ()>>4,true);h.getLevel().getChunkAt(p);
   var c=ContactSensorGameTests.core(h,SableLife.HEART.get(),3+i*5);var m=c.modules().add("auto_facing");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH)));m.edit("target",1,"");m.edit("facingRange",16,"");m.edit("facingVertical",1,"");m.edit("facingCollision",0,"");ContactSensorGameTests.activate(h,c);cores.add(c);
   var body=(ServerSubLevel)dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),c.getBlockPos());bodies.add(body);poses.add(new org.joml.Vector3d(body.logicalPose().position()));var center=SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter());centers.add(center);
   var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(center.add(4,2,-6));h.getLevel().addFreshEntity(cow);cows.add(cow);
  }
  boolean offsets=ModuleFields.forType("auto_facing").stream().anyMatch(f->f.key().equals("facingOffsetLeft"));System.out.printf(Locale.ROOT,"FACING_SETUP_MS %.6f cores=32 native_assembly=true%n",(System.nanoTime()-started)/1e6);var times=new ArrayList<Double>();
  h.onEachTick(()->{
   int tick=(int)h.getTick(),phase=tick/240,within=tick%240;if(phase>=4){h.succeed();return;}
   if(within==1){times.clear();for(var c:cores){var m=c.modules().all().getFirst();m.edit("facingOffsetLeft",phase==1||phase==2?.375:0,"");m.edit("facingOffsetUp",phase==2?-.625:0,"");c.modules().changed();}}
   if(within>=120){var server=h.getLevel().getServer();times.add(server.getTickTimesNanos()[Math.floorMod(server.getTickCount()-1,100)]/1e6);}
   var physics=SubLevelPhysicsSystem.get(h.getLevel()).getPipeline();for(int i=0;i<32;i++){physics.resetVelocity(bodies.get(i));physics.teleport(bodies.get(i),poses.get(i),new org.joml.Quaterniond(bodies.get(i).logicalPose().orientation()));cows.get(i).setPos(centers.get(i).add(4+Math.sin(tick*.025),2+Math.cos(tick*.02),-6));}
   if(within!=239)return;var sorted=new ArrayList<>(times);sorted.sort(Double::compare);System.out.printf(Locale.ROOT,"FACING_PERF phase=%d offsets_supported=%s cores=32 targets=32 samples=%d mean=%.6f p95=%.6f max=%.6f%n",phase,offsets,times.size(),times.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),sorted.get((int)Math.ceil(sorted.size()*.95)-1),sorted.getLast());
  });
 }
}
