package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_contact_perf_tests") @PrefixGameTestTemplate(false)
public final class ContactSensorPerformanceGameTests {
    @GameTest(template="combat",batch="contact_mspt",timeoutTicks=1000)
    public static void CompareContactSensorsDisabledAndActive(GameTestHelper h) {
        boolean supported=VirtualModule.supportsType("contact_sensor")&&!Boolean.getBoolean("sablelife.contactPerfEmpty");
        var cores=new ArrayList<HeartBlockEntity>();var bodies=new ArrayList<ServerSubLevel>();var poses=new ArrayList<org.joml.Vector3d>();
        long setup=System.nanoTime();
        for(int i=0;i<32;i++){
            var loaded=h.absolutePos(new net.minecraft.core.BlockPos(3+i*5,40,3));h.getLevel().setChunkForced(loaded.getX()>>4,loaded.getZ()>>4,true);h.getLevel().getChunkAt(loaded);
            var c=ContactSensorGameTests.core(h,SableLife.HEART.get(),3+i*5);cores.add(c);
            if(supported)for(int n=0;n<8;n++){
                var m=ContactSensorGameTests.sensor(c);m.edit("contactDetectStructures",1,"");m.edit("contactDetectMobs",1,"");m.edit("contactDetectPlayers",1,"");m.edit("contactDetectProjectiles",1,"");m.edit("enabled",0,"");
            }
            ContactSensorGameTests.activate(h,c);var body=(ServerSubLevel)dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),c.getBlockPos());bodies.add(body);poses.add(new org.joml.Vector3d(body.logicalPose().position()));
            var center=ModuleArena.corePosition(c).add(0,0,3.5);var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(center);h.getLevel().addFreshEntity(cow);
            for(int n=0;n<4;n++){var a=new Arrow(h.getLevel(),center.x+.9+n*.01,center.y+.5,center.z,new ItemStack(Items.ARROW),null);a.setNoGravity(true);a.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);h.getLevel().addFreshEntity(a);}
        }
        System.out.printf(Locale.ROOT,"CONTACT_SETUP_MS %.6f cores=32 native_assembly=true%n",(System.nanoTime()-setup)/1e6);
        var times=new ArrayList<Double>();boolean[] phases={false,true,true,false};
        h.onEachTick(()->{
            int tick=(int)h.getTick(),phase=tick/220,within=tick%220;
            if(phase>=4){h.succeed();return;}
            if(within==1){times.clear();if(supported)for(var c:cores){for(var m:c.modules().all())m.edit("enabled",phases[phase]?1:0,"");c.modules().changed();}}
            if(within>=100){var server=h.getLevel().getServer();times.add(server.getTickTimesNanos()[Math.floorMod(server.getTickCount()-1,100)]/1e6);}
            var pipeline=SubLevelPhysicsSystem.get(h.getLevel()).getPipeline();
            if(tick>=4)for(int i=0;i<bodies.size();i++){pipeline.resetVelocity(bodies.get(i));pipeline.teleport(bodies.get(i),poses.get(i),new org.joml.Quaterniond());}
            if(within!=219)return;
            var sorted=new ArrayList<>(times);sorted.sort(Double::compare);
            if(supported&&phases[phase])h.assertTrue(cores.stream().allMatch(c->c.wireless().output(0)==15),"Benchmark sensors were not producing contact output");
            System.out.printf(Locale.ROOT,"CONTACT_PERF phase=%d supported=%s active=%s cores=32 sensors=%d mobs=32 projectiles=128 samples=%d mean=%.6f p95=%.6f max=%.6f%n",phase,supported,phases[phase],supported?256:0,times.size(),times.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),sorted.get((int)Math.ceil(sorted.size()*.95)-1),sorted.getLast());
        });
    }
}
