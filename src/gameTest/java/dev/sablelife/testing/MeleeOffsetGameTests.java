package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.attack.ContactBox;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.joml.Quaterniond;
import java.util.*;
@GameTestHolder("sablelife_melee_tests")
@PrefixGameTestTemplate(false)
public final class MeleeOffsetGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(4,6,4));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}
    private static VirtualModule melee(HeartBlockEntity heart){var m=heart.modules().add("melee");var a=ModuleAnchor.create(heart,heart.getBlockPos().east(),Direction.EAST);m.anchors(List.of(a,a));m.edit("damage",3,"");m.edit("knockback",0,"");m.edit("interval",3,"");return m;}
    private static net.minecraft.world.entity.animal.Cow cow(GameTestHelper h,Vec3 point){var c=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());c.setNoAi(true);c.setNoGravity(true);c.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);c.setHealth(100);c.setPos(point.add(0,-.5,0));h.getLevel().getChunk(BlockPos.containing(point));h.getLevel().addFreshEntity(c);return c;}
    @GameTest(template="combat",batch="melee_extension_geometry")
    public static void ExtensionsPreserveRearFaceAndWorkForEveryMarkedDirection(GameTestHelper h){
        var heart=fixture(h);var m=melee(heart);
        for(var face:Direction.values()){
            var a=ModuleAnchor.create(heart,heart.getBlockPos().east(),face);m.anchors(List.of(a,a));
            for(String key:List.of("meleeLength","meleeWidth","meleeHeight","startDistance","meleeHorizontal","meleeVertical"))m.edit(key,0,"");
            var base=MeleeGeometry.frame(heart,m);m.edit("meleeLength",8,"");m.edit("meleeWidth",2,"");m.edit("meleeHeight",4,"");m.edit("startDistance",3,"");m.edit("meleeHorizontal",35,"");m.edit("meleeVertical",20,"");
            var box=MeleeGeometry.frame(heart,m);var forward=Vec3.atLowerCornerOf(face.getNormal());
            var rotated=ContactBox.rotate(box.rotation(),forward);
            h.assertTrue(box.center().distanceTo(base.center().add(rotated.scale(7)))<1e-6,"Length/offset center incorrect for "+face);
            double baseAlong=Math.abs(forward.x)*base.half().x+Math.abs(forward.y)*base.half().y+Math.abs(forward.z)*base.half().z;
            double along=Math.abs(forward.x)*box.half().x+Math.abs(forward.y)*box.half().y+Math.abs(forward.z)*box.half().z;
            h.assertTrue(Math.abs(along-baseAlong-4)<1e-6,"Length grew along wrong axis for "+face);
            h.assertTrue(box.center().subtract(rotated.scale(along)).distanceTo(base.center().add(rotated.scale(3-baseAlong)))<1e-6,"Extension moved rear face for "+face);
            h.assertTrue(Math.abs(box.half().x+box.half().y+box.half().z-8.5)<1e-6,"Width/height full dimensions incorrect");
        }
        var loaded=VirtualModule.load(m.save());h.assertTrue(loaded.value("meleeLength")==8&&loaded.value("meleeWidth")==2&&loaded.value("meleeHeight")==4,"Extensions did not persist");
        var legacy=m.save();for(String key:List.of("meleeLength","meleeWidth","meleeHeight"))legacy.getCompound("Settings").remove(key);
        var old=VirtualModule.load(legacy);h.assertTrue(old.value("meleeLength")==0&&old.value("meleeWidth")==0&&old.value("meleeHeight")==0,"Old marks gained extensions");h.succeed();
    }
    @GameTest(template="combat",batch="melee_extension_damage")
    public static void ExtendedBladeDealsDamageAtTipButNotBehindHilt(GameTestHelper h){
        var heart=fixture(h);var m=melee(heart);m.edit("meleeLength",8,"");m.edit("meleeHitLimit",1,"");heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));
        var box=MeleeGeometry.frame(heart,m);var forward=ContactBox.rotate(box.rotation(),new Vec3(1,0,0));
        var tip=cow(h,box.center().add(forward.scale(3.8)));var rear=cow(h,box.center().subtract(forward.scale(5.5)));
        heart.modules().tick(heart);h.assertTrue(tip.getHealth()==97,"Extended tip did not damage target: "+tip.getHealth());h.assertTrue(rear.getHealth()==100,"Length extension leaked behind hilt");
        m.edit("enabled",0,"");tip.discard();rear.discard();h.succeed();
    }
    @GameTest(template="combat",batch="melee_config")
    public static void OffsetAnglesKeepSizeAndDefaultsAndPersist(GameTestHelper h){var heart=fixture(h);var m=melee(heart);var original=MeleeGeometry.frame(heart,m);h.assertTrue(m.value("meleeHitLimit")==0&&m.value("meleeHorizontal")==0&&m.value("meleeVertical")==0,"Legacy defaults changed");
        var anchors=m.anchors();m.edit("startDistance",3,"");m.edit("meleeHorizontal",90,"");var shifted=MeleeGeometry.frame(heart,m);var forward=Vec3.atLowerCornerOf(anchors.getFirst().face().getNormal());var localTurn=ContactBox.rotate(MeleeGeometry.angles(forward,90,0),forward);var expected=ContactBox.rotate(original.bodyRotation(),localTurn).scale(3);
        h.assertTrue(shifted.center().subtract(original.center()).distanceTo(expected)<1e-6&&shifted.half().equals(original.half())&&m.anchors().equals(anchors),"Offset changed marks/size or ignored angles");h.assertTrue(ModuleCombat.meleeCenter(heart,m).distanceTo(shifted.center())<1e-6,"Preview center differed from collision center");
        m.edit("meleeVertical",30,"");m.edit("meleeHitLimit",3,"");var loaded=VirtualModule.load(m.save());h.assertTrue(loaded.value("startDistance")==3&&loaded.value("meleeHorizontal")==90&&loaded.value("meleeVertical")==30&&loaded.value("meleeHitLimit")==3,"Melee settings failed to reload");
        var legacy=m.save();for(String key:List.of("meleeHorizontal","meleeVertical","meleeHitLimit"))legacy.getCompound("Settings").remove(key);var old=VirtualModule.load(legacy);h.assertTrue(old.value("meleeHorizontal")==0&&old.value("meleeVertical")==0&&old.value("meleeHitLimit")==0,"Missing new fields did not retain legacy behavior");h.succeed();}
    @GameTest(template="combat",batch="melee_angles")
    public static void FaceRelativeAnglesWorkForAllSixFaces(GameTestHelper h){for(var face:Direction.values()){var forward=Vec3.atLowerCornerOf(face.getNormal());var right=forward.cross(new Vec3(0,1,0));if(right.lengthSqr()<.001)right=new Vec3(1,0,0);right=right.normalize();var up=right.cross(forward).normalize();h.assertTrue(ContactBox.rotate(MeleeGeometry.angles(forward,90,0),forward).distanceTo(right)<1e-7,"Horizontal sign wrong for "+face);h.assertTrue(ContactBox.rotate(MeleeGeometry.angles(forward,0,90),forward).distanceTo(up)<1e-7,"Vertical sign wrong for "+face);}h.succeed();}
    @GameTest(template="combat",batch="melee_cap",timeoutTicks=35)
    public static void CapIsPerTargetPerWindowAndStillRespectsInterval(GameTestHelper h){
        var heart=fixture(h);var m=melee(heart);m.edit("trigger",1,"");m.edit("meleeHitLimit",2,"");
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));
        // Place power after ownership capture, beside rather than above the target's eye position.
        var power=heart.getBlockPos().east(2);h.getLevel().setBlockAndUpdate(power,Blocks.REDSTONE_BLOCK.defaultBlockState());
        var center=ModuleCombat.meleeCenter(heart,m);var a=cow(h,center.add(.15,0,0));var b=cow(h,center.add(-.15,0,0));
        h.assertTrue(HeartModules.triggered(m,heart),"Melee power fixture was not triggered: "+heart.health().state()+" / "+HeartModules.status(m,heart)+" / "+h.getLevel().getBlockState(power));
        var geometry=MeleeGeometry.frame(heart,m);h.assertTrue(ContactBox.intersects(geometry.center(),geometry.half(),geometry.rotation(),a.getBoundingBox()),"Target drifted outside its marked box before first hit");
        h.assertTrue(ModuleTargets.query(h.getLevel(),heart.creatureId(),new AABB(geometry.center(),geometry.center()).inflate(2)).stream().anyMatch(e->e.getUUID().equals(a.getUUID())),"Fresh target was not visible to entity query");
        heart.modules().tick(heart);h.assertTrue(a.getHealth()==97&&b.getHealth()==97,"First independent hits failed: "+a.getHealth()+" / "+b.getHealth());
        heart.modules().tick(heart);h.assertTrue(a.getHealth()==97&&b.getHealth()==97,"Count cap bypassed interval");
        h.runAfterDelay(3,()->{var point=ModuleCombat.meleeCenter(heart,m);a.setPos(point.add(.15,-.5,0));b.setPos(point.add(-.15,-.5,0));heart.modules().tick(heart);h.assertTrue(a.getHealth()==94&&b.getHealth()==94,"Second hits were not independent: "+a.getHealth()+" / "+b.getHealth());});
        h.runAfterDelay(6,()->{
            heart.modules().tick(heart);h.assertTrue(a.getHealth()==94&&b.getHealth()==94,"Third hit exceeded cap");
            var point=ModuleCombat.meleeCenter(heart,m);a.setPos(point.add(5,-.5,0));heart.modules().tick(heart);a.setPos(point.add(.15,-.5,0));b.setPos(point.add(-.15,-.5,0));heart.modules().tick(heart);h.assertTrue(a.getHealth()==94,"Leaving/reentering reset cap");
            h.getLevel().removeBlock(power,false);heart.modules().tick(heart);h.assertTrue(!HeartModules.triggered(m,heart),"Power fixture never switched off");
            h.getLevel().setBlockAndUpdate(power,Blocks.REDSTONE_BLOCK.defaultBlockState());heart.modules().tick(heart);
            h.assertTrue(a.getHealth()==91&&b.getHealth()==91,"Next activation failed to reset counts: "+a.getHealth()+" / "+b.getHealth());m.edit("enabled",0,"");a.discard();b.discard();h.succeed();
        });
    }
    @GameTest(template="combat",batch="melee_unlimited",timeoutTicks=25)
    public static void ZeroMeansUnlimitedInsteadOfZeroHits(GameTestHelper h){var heart=fixture(h);var m=melee(heart);var c=cow(h,ModuleCombat.meleeCenter(heart,m));heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));heart.modules().tick(heart);h.assertTrue(c.getHealth()==97,"Zero cap prevented first hit");h.runAfterDelay(3,()->{heart.modules().tick(heart);h.assertTrue(c.getHealth()==94,"Zero cap limited second hit");});h.runAfterDelay(6,()->{heart.modules().tick(heart);h.assertTrue(c.getHealth()==91,"Zero cap limited repeated hits");m.edit("enabled",0,"");c.discard();h.succeed();});}
    @GameTest(template="combat",batch="melee_arc_math")
    public static void RotationSweepIncludesArcAndRejectsChord(GameTestHelper h){var half=new Vec3(.5,.5,.5);var before=new MeleeGeometry.Frame(Vec3.ZERO,new Quaterniond(),new Vec3(7,0,0),new Quaterniond(),half,"same");var after=new MeleeGeometry.Frame(Vec3.ZERO,new Quaterniond().rotationY(Math.PI/3),new Vec3(7,0,0),new Quaterniond(),half,"same");var actual=ContactBox.rotate(new Quaterniond().rotationY(Math.PI/6),new Vec3(7,0,0));var arcBox=new AABB(actual,actual).inflate(.1);var chord=before.center().lerp(after.center(),.5);var chordBox=new AABB(chord,chord).inflate(.1);boolean arc=false,wrong=false;int steps=MeleeGeometry.samples(before,after);for(int i=0;i<=steps;i++){var point=MeleeGeometry.sample(before,after,i/(double)steps);arc|=ContactBox.intersects(point.center(),half,point.rotation(),arcBox);wrong|=ContactBox.intersects(point.center(),half,point.rotation(),chordBox);}h.assertTrue(arc&&!wrong,"Sweep still followed chord instead of actual rotating frame");h.succeed();}
    @GameTest(template="combat",batch="melee_move_set",timeoutTicks=30)
    public static void MoveSetWindowsResetTargetHitCounts(GameTestHelper h){var heart=fixture(h);var m=melee(heart);m.edit("trigger",4,"");m.edit("meleeHitLimit",1,"");m.edit("interval",1,"");var target=cow(h,ModuleCombat.meleeCenter(heart,m));var table=heart.modules().add("move_set");table.moveSet().edit("add",0,"",heart.modules());table.moveSet().edit("action_add",0,"0|"+m.id(),heart.modules());table.moveSet().edit("action_duration",.15,"0|0",heart.modules());var config=table.moveSet().get(0).config();config.edit("target",1,"");config.edit("dispatchBusy",1,"");config.edit("dispatchRest",2,"");config.edit("dispatchCooldown",0,"");config.edit("dispatchRepeat",1,"");heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));heart.modules().tick(heart);h.assertTrue(target.getHealth()==97,"Move did not start melee");h.runAfterDelay(2,()->{heart.modules().tick(heart);h.assertTrue(target.getHealth()==97,"One move hit same target more than once");});h.runAfterDelay(3,()->{heart.modules().tick(heart);h.assertTrue(!HeartModules.triggered(m,heart),"Move never ended its activation window");});h.runAfterDelay(5,()->{heart.modules().tick(heart);h.assertTrue(target.getHealth()==94,"Second move did not reset hit count");m.edit("enabled",0,"");table.edit("enabled",0,"");target.discard();h.succeed();});}
    @GameTest(template="combat",batch="melee_live_arc",timeoutTicks=25)
    public static void ActualRotatingOffsetBoxHitsArcAndNotChord(GameTestHelper h){
        var heart=fixture(h);var m=melee(heart);m.edit("startDistance",12,"");var before=MeleeGeometry.frame(heart,m);
        var rotation=new Quaterniond().rotationY(Math.PI/3);var predicted=new MeleeGeometry.Frame(before.position(),rotation,before.localCenter(),before.angleRotation(),before.half(),before.signature());
        var arc=MeleeGeometry.sample(before,predicted,.5).center();var chord=before.center().lerp(predicted.center(),.5);
        var actualTarget=cow(h,arc);var wrongTarget=cow(h,chord);var runtime=new ModuleCombat();
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));runtime.tick(heart);h.assertTrue(actualTarget.getHealth()==100&&wrongTarget.getHealth()==100,"Rotation test started inside attack endpoint");
        h.runAfterDelay(1,()->{
            var body=m.anchors().getFirst().body(heart);
            dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of((dev.ryanhcode.sable.sublevel.ServerSubLevel)body).teleport(new org.joml.Vector3d(before.position().x,before.position().y,before.position().z),rotation);
            runtime.tick(heart);
            h.assertTrue(actualTarget.getHealth()==97&&wrongTarget.getHealth()==100,"Actual offset sweep did not follow limb arc: "+actualTarget.getHealth()+" / "+wrongTarget.getHealth());
            m.edit("enabled",0,"");actualTarget.discard();wrongTarget.discard();h.succeed();
        });
    }

}
