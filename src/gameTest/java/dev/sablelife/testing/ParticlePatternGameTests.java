package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.sablelife.attack.ContactBox;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;

@GameTestHolder("sablelife_particle_tests")
@PrefixGameTestTemplate(false)
public final class ParticlePatternGameTests {
    @GameTest(template="combat")
    public static void RingHeightBuildsHollowCylinderAndKeepsLegacyZeroHeight(GameTestHelper h){
        var m=new VirtualModule("particles");m.edit("particlePattern",3,"");m.edit("particleDistribution",1,"");var forward=new Vec3(0,1,0);var random=RandomSource.create(7);
        h.assertTrue(m.value("particleRingHeight")==0&&ParticlePattern.visibleField(m,"particleRingHeight"),"Ring height default or visibility wrong");
        for(int i=0;i<32;i++){var p=ParticlePattern.sample(m,Vec3.ZERO,forward,i,32,random);h.assertTrue(p.y==0&&Math.abs(p.length()-3)<1e-8,"Legacy ring changed");}
        m.edit("particleRingHeight",8,"");double min=100,max=-100;
        for(int distribution:new int[]{0,1}){m.edit("particleDistribution",distribution,"");for(int i=0;i<256;i++){var p=ParticlePattern.sample(m,Vec3.ZERO,forward,i,256,random);h.assertTrue(p.y>=0&&p.y<=8&&Math.abs(Math.hypot(p.x,p.z)-3)<1e-8,"Cylinder filled interior or exceeded height");min=Math.min(min,p.y);max=Math.max(max,p.y);}}
        h.assertTrue(min<.2&&max>7.8,"Cylinder axial distribution did not span height");var saved=VirtualModule.load(m.save());h.assertTrue(saved.value("particleRingHeight")==8,"Cylinder height lost on save");m.edit("particlePattern",4,"");h.assertTrue(!ParticlePattern.visibleField(m,"particleRingHeight"),"Height visible on non-ring pattern");h.succeed();
    }
    @GameTest(template="combat")
    public static void CylinderPropagationPreservesSectionsAndRadialSpeed(GameTestHelper h){
        var m=new VirtualModule("particles");m.edit("particlePattern",3,"");m.edit("particleRingHeight",8,"");m.edit("particleDistribution",1,"");m.edit("particleSpeed",.25,"");var style=new ModuleParticles.Entry("minecraft:flame",0,1);var center=new Vec3(3,4,5);var rotation=new org.joml.Quaterniond().rotateZ(Math.PI/2);var forward=new Vec3(0,1,0);
        for(int i=0;i<32;i++){m.edit("particlePropagation",1,"");var outward=ParticlePattern.travel(m,style,center,rotation,forward,i,32,RandomSource.create(7));m.edit("particlePropagation",2,"");var inward=ParticlePattern.travel(m,style,center,rotation,forward,i,32,RandomSource.create(7));h.assertTrue(outward.from().distanceTo(inward.to())<1e-8&&outward.to().distanceTo(inward.from())<1e-8&&Math.abs(outward.from().distanceTo(outward.to())-3)<1e-8&&outward.ticks()==12,"Radial reverse/speed changed with axial height");h.assertTrue(outward.from().distanceTo(center)>0,"Cylinder section origin collapsed to one point");}
        var frame=new ParticlePattern.Frame(center,rotation,Vec3.ZERO,forward);h.assertTrue(Math.abs(ParticlePattern.sweepRadius(m,frame)-Math.hypot(3,8))<1e-8,"Trail bounds ignored height");h.succeed();
    }
    @GameTest(template="combat")
    public static void OutwardAndReverseTravelRespectLengthRadiusAndSavedDefaults(GameTestHelper h){
        var m=new VirtualModule("particles");var style=new ModuleParticles.Entry("minecraft:flame",0,2);
        var center=new Vec3(3,4,5);var rotation=new org.joml.Quaterniond().rotateZ(Math.PI/2);var random=RandomSource.create(7);
        h.assertTrue(m.value("particlePropagation")==0,"Old placement default changed");
        m.edit("particlePattern",2,"");m.edit("particleLineMode",1,"");m.edit("particleSpeed",.25,"");
        var line=ParticlePattern.travel(m,style,center,rotation,new Vec3(1,0,0),0,4,random);
        h.assertTrue(line.from().equals(center)&&line.to().distanceTo(center.add(0,8,0))<1e-8&&line.ticks()==32&&line.life()==33,"8-block line travel was truncated");
        for(int shape:new int[]{2,3,4})for(int mode:new int[]{1,2}) {
            m.edit("particlePattern",shape,"");m.edit("particlePropagation",mode,"");m.edit("particleRadius",3,"");
            var packet=ParticlePattern.travel(m,style,center,rotation,new Vec3(1,0,0),1,4,random);
            h.assertTrue(Math.abs(packet.from().distanceTo(packet.to())-(shape==2?8:3))<1e-8,"Path did not reach configured length/radius");
            h.assertTrue((mode==1?packet.from():packet.to()).equals(center),"Reverse did not end at the center");
        }
        var saved=VirtualModule.load(m.save());h.assertTrue(saved.value("particlePropagation")==2,"Propagation failed to save");
        var shortLife=ParticlePattern.travel(m,new ModuleParticles.Entry("minecraft:flame",5,1),center,rotation,new Vec3(1,0,0),0,4,random);h.assertTrue(shortLife.life()==5,"Custom cutoff was ignored");
        h.succeed();
    }
    private static HeartBlockEntity fixture(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,6,4));
        level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    @GameTest(template="combat",timeoutTicks=30)
    public static void AreaAndPointMarkSessionsRemainIndependentAndPersist(GameTestHelper h) {
        var heart=fixture(h);var m=heart.modules().add("particles");var player=FakePlayerFactory.getMinecraft(h.getLevel());player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        h.assertTrue(m.area()&&m.value("trail")==1&&m.value("interval")==2,"Legacy particle defaults changed");
        h.assertTrue(MarkingSessions.start(player,heart,m)&&MarkingSessions.select(player,heart.getBlockPos(),Direction.UP)&&m.anchors().isEmpty(),"Area mark completed with one point");
        h.assertTrue(MarkingSessions.select(player,heart.getBlockPos().east(),Direction.EAST)&&m.anchors().size()==2,"Area did not complete after two points");
        var area=m.anchors();var legacy=m.save();legacy.remove("ParticleAreaAnchors");legacy.remove("ParticlePointAnchors");legacy.getCompound("Settings").remove("particlePattern");
        var migrated=VirtualModule.load(legacy);h.assertTrue(migrated.area()&&migrated.anchors().equals(area),"Old area marks were not preserved");
        m.edit("particlePattern",4,"");h.assertTrue(!m.area()&&HeartModules.status(m,heart).equals("unmarked"),"Sphere reused area marks");
        h.assertTrue(MarkingSessions.start(player,heart,m)&&MarkingSessions.select(player,heart.getBlockPos().east(),Direction.EAST)&&m.anchors().size()==1,"Point mark required a second click");
        var point=m.anchors();h.assertTrue(HeartModules.status(m,heart).equals("valid"),"Single-point sphere was not executable");
        m.invalidate();h.assertTrue(VirtualModule.load(m.save()).invalidated(),"Loading a particle pattern erased saved destruction state");
        m.edit("particlePattern",4,"");
        var restored=VirtualModule.load(m.save());h.assertTrue(restored.anchors().equals(point),"Point mark was not saved");
        restored.edit("particlePattern",0,"");h.assertTrue(restored.anchors().equals(area),"Inactive area marks were lost");
        restored.clearMarks();restored.edit("particlePattern",3,"");h.assertTrue(restored.anchors().equals(point),"Clearing Area erased Point");
        restored.clearMarks();restored.edit("particlePattern",0,"");h.assertTrue(restored.anchors().isEmpty(),"Clear did not reset the current mode");
        heart.tryActivate(player);m.editParticle("add","minecraft:flame");heart.modules().tick(heart);
        m.edit("particlePattern",1,"");heart.modules().tick(heart);m.edit("particlePattern",0,"");heart.modules().tick(heart);
        h.assertTrue(m.anchors().equals(area),"Runtime changed stored area selection");h.succeed();
    }
    @GameTest(template="combat")
    public static void SphereRandomDistributionFillsVolumeWithoutEscapingRadius(GameTestHelper h) {
        var m=new VirtualModule("particles");m.edit("particlePattern",4,"");m.edit("particleRadius",3,"");var random=RandomSource.create(4937);
        double cubes=0;int count=10000;boolean inner=false,outer=false;
        for(int i=0;i<count;i++) {
            var p=ParticlePattern.sample(m,Vec3.ZERO,new Vec3(0,0,-1),i,count,random);double ratio=p.length()/3;
            h.assertTrue(ratio<=1.0000001,"Sphere particle escaped its radius");cubes+=ratio*ratio*ratio;inner|=ratio<.3;outer|=ratio>.9;
        }
        h.assertTrue(inner&&outer&&Math.abs(cubes/count-.5)<.02,"Sphere interior clustered rather than filling volume uniformly");
        m.edit("particleFill",1,"");m.edit("particleDistribution",1,"");
        for(int i=0;i<64;i++)h.assertTrue(Math.abs(ParticlePattern.sample(m,Vec3.ZERO,new Vec3(0,1,0),i,64,random).length()-3)<1e-7,"Surface mode generated an interior particle");h.succeed();
    }
    @GameTest(template="combat")
    public static void RingAndLineRespectAllMarkedFaceDirections(GameTestHelper h) {
        var m=new VirtualModule("particles");m.edit("particleDistribution",1,"");m.edit("particleRadius",2,"");m.edit("particleLength",8,"");var random=RandomSource.create(8);
        for(var face:Direction.values()) {
            var forward=Vec3.atLowerCornerOf(face.getNormal());m.edit("particlePattern",3,"");
            for(int i=0;i<16;i++) {var point=ParticlePattern.sample(m,Vec3.ZERO,forward,i,16,random);h.assertTrue(Math.abs(point.length()-2)<1e-7&&Math.abs(point.dot(forward))<1e-7,"Ring plane or radius was incorrect for "+face);}
            m.edit("particlePattern",2,"");h.assertTrue(ParticlePattern.sample(m,Vec3.ZERO,forward,0,9,random).lengthSqr()==0&&ParticlePattern.sample(m,Vec3.ZERO,forward,8,9,random).distanceTo(forward.scale(8))<1e-7,"Line endpoints were incorrect");
        }
        m.edit("particleLineMode",1,"");m.edit("particleSpeed",.5,"");
        var style=ParticlePattern.emissionStyle(m,new ModuleParticles.Entry("minecraft:flame",0,2));h.assertTrue(style.ticks()==16&&style.size()==2,"Spray range did not cap lifetime or preserve size");
        var q=new org.joml.Quaterniond().rotateZ(Math.PI/2);var velocity=ParticlePattern.velocity(m,q,new Vec3(1,0,0),random);
        h.assertTrue(velocity.distanceTo(new Vec3(0,.5,0))<1e-7,"Spray direction did not follow structure rotation");h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=30)
    public static void OffsetMovesBothAreaAndPointGeometryFromTheirOwnMarks(GameTestHelper h) {
        var heart=fixture(h);var m=heart.modules().add("particles");m.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos(),Direction.UP),ModuleAnchor.create(heart,heart.getBlockPos().east(),Direction.EAST)));
        for(int shape:new int[]{0,3}) {
            m.edit("particlePattern",shape,"");if(shape!=0)m.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos().east(),Direction.EAST)));
            m.edit("startDistance",0,"");var base=ParticlePattern.frame(heart,m);m.edit("startDistance",3,"");var moved=ParticlePattern.frame(heart,m);
            var delta=ContactBox.rotate(base.rotation(),base.forward()).scale(3);
            h.assertTrue(moved.center().distanceTo(base.center().add(delta))<1e-7&&moved.half().equals(base.half()),"Offset changed shape size or ignored direction");
            var saved=VirtualModule.load(m.save());h.assertTrue(saved.value("startDistance")==3&&saved.value("particlePattern")==shape,"Pattern/offset settings failed to reload");
        }
        h.succeed();
    }
    @GameTest(template="combat",batch="particle_expand")
    public static void AreaAndPointExpandForwardFromOriginalBoundsForAllFaces(GameTestHelper h){
        var c=fixture(h);
        for(var face:Direction.values())for(int shape:new int[]{0,1}){
            var m=new VirtualModule("particles");m.edit("particlePattern",shape,"");var a=ModuleAnchor.create(c,c.getBlockPos(),face);m.anchors(shape==0?List.of(a,ModuleAnchor.create(c,c.getBlockPos().east(),face)):List.of(a));
            var base=ParticlePattern.frame(c,m);h.assertTrue(shape==0||base.half().equals(Vec3.ZERO),"Point default no longer a point");m.edit("particleExpandLength",8,"");var grown=ParticlePattern.frame(c,m);var forward=ContactBox.rotate(base.rotation(),base.forward());
            h.assertTrue(grown.center().distanceTo(base.center().add(forward.scale(4)))<1e-7&&Math.abs(grown.half().subtract(base.half()).length()-4)<1e-7,"Length did not extend one-sided from original mark: "+face+"/"+shape);
            m.edit("particleExpandWidth",4,"");m.edit("particleExpandHeight",2,"");var frame=ParticlePattern.frame(c,m);
            h.assertTrue(frame.center().distanceTo(grown.center())<1e-7&&frame.half().x>0&&frame.half().y>0&&frame.half().z>0,"Width/height shifted center or collapsed dimensions");
            boolean outside=false;for(int distribution:new int[]{0,1}){m.edit("particleDistribution",distribution,"");for(int n=0;n<128;n++){var point=ParticlePattern.sample(m,frame.half(),frame.forward(),n,128,RandomSource.create(n));h.assertTrue(Math.abs(point.x)<=frame.half().x&&Math.abs(point.y)<=frame.half().y&&Math.abs(point.z)<=frame.half().z,"Expanded particle escaped bounds");if(point.length()>base.half().length())outside=true;}}
            h.assertTrue(outside&&ParticlePattern.sweepRadius(m,frame)>=frame.half().length(),"Expanded region/trail still used old bounds");
            var loaded=VirtualModule.load(m.save());h.assertTrue(loaded.value("particleExpandLength")==8&&loaded.value("particleExpandWidth")==4&&loaded.value("particleExpandHeight")==2&&ParticlePattern.frame(c,loaded).half().equals(frame.half()),"Expanded geometry lost on reload");
        }h.succeed();
    }
    @GameTest(template="combat",batch="particle_expand_visibility")
    public static void PointDefaultsAndOtherPatternsRemainUnchanged(GameTestHelper h){
        var m=new VirtualModule("particles");m.edit("particlePattern",1,"");h.assertTrue(ParticlePattern.sample(m,Vec3.ZERO,new Vec3(0,0,1),0,1,RandomSource.create(1)).equals(Vec3.ZERO)&&!ParticlePattern.visibleField(m,"particleDistribution"),"Zero-expansion point changed");
        m.edit("particleExpandWidth",5,"");h.assertTrue(ParticlePattern.visibleField(m,"particleDistribution"),"Expanded point cannot select distribution");
        for(int shape=0;shape<5;shape++){m.edit("particlePattern",shape,"");h.assertTrue(ParticlePattern.visibleField(m,"particleExpandLength")== (shape<=1),"Expansion controls leaked into other pattern");}h.succeed();
    }

}
