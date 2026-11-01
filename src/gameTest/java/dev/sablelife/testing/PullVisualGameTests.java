package dev.sablelife.testing;
import dev.sablelife.module.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sablelife_particle_tests") @PrefixGameTestTemplate(false)
public final class PullVisualGameTests {
    @GameTest(template="combat")
    public static void InflowStartsAtFullSphereAndContinuesAcrossRepeatedBursts(GameTestHelper h){
        var m=new VirtualModule("pull");var center=new Vec3(-100,7,12);var point=center.add(1,2,0);
        for(double radius:new double[]{1,8,32}){
            m.edit("pullRadius",radius,"");var packets=PullVisual.particles(m,center,point,60,0);var next=PullVisual.particles(m,center,point,56,4);
            h.assertTrue(packets.size()==PullVisual.STREAM_POINTS&&next.size()==packets.size(),"Inflow stopped after the first burst");
            double min=1,max=-1;
            for(var p:packets){h.assertTrue(Math.abs(p.from().distanceTo(center)-radius)<1e-7&&p.to().equals(point),"Inflow did not start at configured spherical radius or target pull point");min=Math.min(min,(p.from().y-center.y)/radius);max=Math.max(max,(p.from().y-center.y)/radius);h.assertTrue(p.life()>p.ticks()&&p.ticks()<=24,"Default inflow expired early or had unbounded travel");}
            h.assertTrue(min<-.9&&max>.9&&!packets.getFirst().from().equals(next.getFirst().from()),"Inflow is a flat ring or repeats fixed angular spokes");
        }
        var boundary=java.util.stream.IntStream.range(0,PullVisual.BOUNDARY_POINTS).mapToObj(i->PullVisual.shell(center,8,i,PullVisual.BOUNDARY_POINTS,0)).toList();
        h.assertTrue(boundary.stream().allMatch(p->Math.abs(p.distanceTo(center)-8)<1e-7)&&boundary.stream().noneMatch(p->Math.abs(p.y-center.y)<1e-7),"Boundary reverted to intersecting great circles");h.succeed();
    }
    @GameTest(template="combat")
    public static void WarningCutoffAndSelectedRgbStylesArePreserved(GameTestHelper h){
        var m=new VirtualModule("pull");m.edit("pullWarningColorRgb",1,"");m.edit("pullWarningColorRed",20,"");m.edit("pullWarningColorGreen",220,"");m.edit("pullWarningColorBlue",60,"");
        var fallback=PullVisual.particles(m,Vec3.ZERO,Vec3.ZERO,5,0).getFirst();h.assertTrue(fallback.id().equals("minecraft:dust")&&fallback.color()==0x14DC3C&&fallback.life()==5,"Fallback ignored warning color or warning cutoff");
        m.editParticle("add","minecraft:effect");m.editParticle("color","minecraft:effect",0x32CD32);m.editParticle("size","minecraft:effect",2);m.editParticle("duration","minecraft:effect",.1);
        var custom=PullVisual.particles(m,Vec3.ZERO,Vec3.ZERO,30,8).getFirst();h.assertTrue(custom.id().equals("minecraft:effect")&&custom.color()==0x32CD32&&custom.size()==2&&custom.life()==2,"Selected style lost RGB/size/lifetime");
        h.assertTrue(PullVisual.particles(m,Vec3.ZERO,Vec3.ZERO,0,0).isEmpty(),"Expired warning still emitted particles");h.succeed();
    }
}
