package dev.sablelife.testing;

import dev.sablelife.module.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class LaserSweepGameTests {
    @GameTest(template="combat", batch="laser_sweep_save")
    public static void SettingsSurviveReloadAndOldSavesKeepAutoSpeed(GameTestHelper h) {
        var m=new VirtualModule("laser");
        h.assertTrue(!LaserSweep.visible(m,"sweepSpeed")&&!LaserSweep.visible(m,"verticalSweepSpeed"),"Speed shown for zero angle");
        m.edit("sweepAngle",90,"");m.edit("sweepSpeed",30,"");m.edit("verticalSweepAngle",-60,"");m.edit("verticalSweepSpeed",60,"");m.edit("laserParticleSpacing",.5,"");
        var restored=VirtualModule.load(m.save());
        h.assertTrue(restored.value("sweepSpeed")==30&&restored.value("verticalSweepSpeed")==60&&restored.value("laserParticleSpacing")==.5,"Laser settings lost on reload");
        h.assertTrue(LaserSweep.visible(restored,"sweepSpeed")&&LaserSweep.visible(restored,"verticalSweepSpeed"),"Configured speeds hidden");
        restored.edit("laserAim",1,"");h.assertTrue(!LaserSweep.visible(restored,"sweepSpeed")&&!LaserSweep.visible(restored,"verticalSweepSpeed"),"Sweep speeds shown during target tracking");
        var old=m.save();var settings=old.getCompound("Settings");settings.remove("sweepSpeed");settings.remove("verticalSweepSpeed");settings.remove("laserParticleSpacing");
        restored=VirtualModule.load(old);
        h.assertTrue(restored.value("sweepSpeed")==0&&restored.value("verticalSweepSpeed")==0&&restored.value("laserParticleSpacing")==4,"Old settings did not gain compatible defaults");
        h.succeed();
    }

    @GameTest(template="combat", batch="laser_sweep_axes")
    public static void IndependentAxesRetainSignedAnglesAndMarkedOrientation(GameTestHelper h) {
        var forward=new Vec3(0,0,-1);var pose=new org.joml.Quaterniond();
        var a=ModuleLaser.sweep(forward,pose,90,90,.5,1);
        h.assertTrue(Math.abs(a.x)<1e-8&&a.y>.7,"Vertical rate changed horizontal progression");
        var b=ModuleLaser.sweep(forward,pose,90,-90,1,.5);
        h.assertTrue(Math.abs(b.x)>.7&&Math.abs(b.y)<1e-8,"Horizontal rate changed vertical progression");
        for(double p:new double[]{0,.2,.7,1})h.assertTrue(ModuleLaser.sweep(forward,pose,70,-80,p).distanceTo(ModuleLaser.sweep(forward,pose,70,-80,p,p))<1e-9,"Legacy combined sweep changed");
        var rotated=new org.joml.Quaterniond().rotateZ(Math.PI/2);
        var c=ModuleLaser.sweep(forward,rotated,0,90,.5,1);
        h.assertTrue(Math.abs(c.x)>.7&&Math.abs(c.y)<1e-8&&Math.abs(c.length()-1)<1e-8,"Sweep ignored marked body axes");
        h.succeed();
    }

    @GameTest(template="combat", batch="laser_visual_transition")
    public static void SmoothingRejectsChangedBeamIdentityAndCoordinateSpace(GameTestHelper h) {
        var id=UUID.randomUUID();var body=UUID.randomUUID();
        var a=new LaserVisual(id,body,Vec3.ZERO,Vec3.ZERO,.5f,0xFF0000,List.of(new LaserVisual.End(new Vec3(10,0,0),true)));
        var b=new LaserVisual(id,body,Vec3.ZERO,Vec3.ZERO,.5f,0xFF0000,List.of(new LaserVisual.End(new Vec3(9,1,0),true)));
        h.assertTrue(b.interpolatesFrom(a),"Consecutive local frames cannot interpolate");
        h.assertTrue(!new LaserVisual(id,body,Vec3.ZERO,Vec3.ZERO,.5f,0xFF0000,List.of(new LaserVisual.End(new Vec3(9,1,0),false))).interpolatesFrom(a),"Clipped world-space endpoint blended with local endpoint");
        h.assertTrue(!new LaserVisual(id,UUID.randomUUID(),Vec3.ZERO,Vec3.ZERO,.5f,0,List.of(new LaserVisual.End(Vec3.ZERO,true))).interpolatesFrom(a),"Different bodies blended");
        h.assertTrue(!LaserVisual.stop(id).interpolatesFrom(a)&&!b.interpolatesFrom(null),"Stop or initial packet retained old beam");h.succeed();
    }
}
