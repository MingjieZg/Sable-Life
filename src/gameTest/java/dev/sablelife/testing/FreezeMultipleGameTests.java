package dev.sablelife.testing;
import dev.sablelife.module.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_freeze_tests") @PrefixGameTestTemplate(false)
public final class FreezeMultipleGameTests {
    @GameTest(template="combat",timeoutTicks=35) public static void MultipleOriginsFreezeBothMobsWithOneWarningClock(GameTestHelper h){
        var c=SlashGuardGameTests.core(h);var m=c.modules().add("freeze");m.edit("freezeTarget",1,"");m.edit("releasePosition",1,"");m.edit("freezeRadius",.75,"");m.edit("freezeWarning",5,"");m.edit("freezeDuration",10,"");m.edit("interval",400,"");var o=TargetCastOrigin.searchOrigin(c,m);
        var a=EntityType.PIG.create(h.getLevel());a.setPos(o.add(5,4,0));a.setNoAi(true);a.setNoGravity(true);h.getLevel().addFreshEntity(a);var b=EntityType.PIG.create(h.getLevel());b.setPos(o.add(-6,4,0));b.setNoAi(true);b.setNoGravity(true);h.getLevel().addFreshEntity(b);
        h.assertTrue(m.value("targetMultiple")==0&&TargetCastOrigin.points(c,m).size()==1,"Single-target default changed");m.edit("targetMultiple",1,"");h.assertTrue(TargetCastOrigin.points(c,m).size()==2&&VirtualModule.load(m.save()).value("targetMultiple")==1&&ModulePreset.create(m.save()).value("targetMultiple")==1,"Multiple origins or save/preset failed");c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);
        h.assertTrue(!MobGrab.frozen(a)&&!MobGrab.frozen(b),"Multiple casts bypassed warning");h.runAfterDelay(7,()->h.assertTrue(MobGrab.frozen(a)&&MobGrab.frozen(b),"Both selected centers did not activate together"));
        h.runAfterDelay(19,()->{h.assertTrue(!MobGrab.frozen(a)&&!MobGrab.frozen(b),"Multiple cast duplicated duration or ignored shared cooldown");c.modules().clearRuntime();a.discard();b.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="freeze_overlap_isolation") public static void OverlappingRangesCaptureEachVictimOnceAndMarkHidesCheckbox(GameTestHelper h){
        var c=SlashGuardGameTests.core(h);var m=c.modules().add("freeze");m.edit("freezeTarget",1,"");m.edit("releasePosition",1,"");m.edit("targetMultiple",1,"");var o=TargetCastOrigin.searchOrigin(c,m).add(7,5,0);var a=EntityType.PIG.create(h.getLevel());a.setPos(o);a.setNoAi(true);h.getLevel().addFreshEntity(a);var b=EntityType.PIG.create(h.getLevel());b.setPos(o.add(1,0,0));b.setNoAi(true);h.getLevel().addFreshEntity(b);
        h.assertTrue(ModuleFreeze.volumes(c,m).size()==2&&ModuleFreeze.captureVolumes(h.getLevel(),c,m,ModuleFreeze.volumes(c,m))==2,"Overlapping casts duplicated victims");h.assertTrue(SurfaceFieldSettings.visible(m,"targetMultiple"),"Target checkbox hidden");m.edit("releasePosition",0,"");h.assertTrue(!SurfaceFieldSettings.visible(m,"targetMultiple")&&m.value("targetMultiple")==1,"Mark mode exposed/reset multiple checkbox");MobGrab.release(h.getLevel().getServer(),new PlayerFreeze.Source(c.creatureId(),m.id()));a.discard();b.discard();h.succeed();
    }
}
