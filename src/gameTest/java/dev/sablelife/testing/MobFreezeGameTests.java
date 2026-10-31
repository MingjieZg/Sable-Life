package dev.sablelife.testing;
import dev.sablelife.module.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_freeze_tests") @PrefixGameTestTemplate(false)
public final class MobFreezeGameTests {
    private static final class CounterPig extends Pig {int ai;CounterPig(net.minecraft.server.level.ServerLevel l){super(EntityType.PIG,l);}@Override protected void customServerAiStep(){ai++;super.customServerAiStep();}}
    private static CounterPig pig(GameTestHelper h,Vec3 at,boolean ai){var p=new CounterPig(h.getLevel());p.setPos(at);p.setNoAi(!ai);h.getLevel().addFreshEntity(p);return p;}
    private static PlayerFreeze.Source source(){return new PlayerFreeze.Source(UUID.randomUUID(),UUID.randomUUID());}
    @GameTest(template="combat") public static void SelectorAndTargetOriginRespectMobsAndExcludeLives(GameTestHelper h){
        var c=SlashGuardGameTests.core(h);var m=c.modules().add("freeze");var at=TargetCastOrigin.searchOrigin(c,m).add(7,5,0);var p=pig(h,at,false);var player=new SlashGuardGameTests.Defender(h.getLevel());
        h.assertTrue(m.value("freezeTarget")==0&&!ModuleFreeze.matches(p,m)&&ModuleFreeze.matches(player,m),"Old player default changed");m.edit("freezeTarget",1,"");m.edit("releasePosition",1,"");h.assertTrue(ModuleFreeze.matches(p,m)&&!ModuleFreeze.matches(player,m)&&TargetCastOrigin.points(c,m).getFirst().distanceTo(p.getBoundingBox().getCenter())<1e-8,"Mob-only target cast failed");m.edit("freezeTarget",2,"");h.assertTrue(VirtualModule.load(m.save()).value("freezeTarget")==2&&ModulePreset.create(m.save()).value("freezeTarget")==2,"Target filter lost in save/preset");
        c.tryActivate(TestActors.creative(h.getLevel()));var proxy=ModuleTargets.query(h.getLevel(),UUID.randomUUID(),dev.sablelife.ownership.LoadedCoreIndex.bounds(c).inflate(1),e->e.getUUID().equals(c.creatureId())).stream().filter(e->e instanceof ModuleTargets.LifeTarget).findFirst().orElseThrow();h.assertTrue(!ModuleFreeze.matches(proxy,m),"Mechanical life admitted");p.discard();h.succeed();
    }
    @GameTest(template="combat") public static void ActualCylinderAndRectangleFreezeMobVolumes(GameTestHelper h){
        var c=SlashGuardGameTests.core(h);var m=c.modules().add("freeze");m.edit("freezeTarget",1,"");m.edit("freezeRadius",2,"");m.edit("freezeHeight",2,"");var o=h.absoluteVec(new Vec3(5,8,5));var inside=pig(h,o,false);var far=pig(h,o.add(4,0,0),false);var high=pig(h,o.add(0,3,0),false);
        h.assertTrue(ModuleFreeze.capture(h.getLevel(),c,m,List.of(o))==1&&MobGrab.frozen(inside)&&!MobGrab.frozen(far)&&!MobGrab.frozen(high),"Cylinder radius/height ignored");MobGrab.release(h.getLevel().getServer(),new PlayerFreeze.Source(c.creatureId(),m.id()));m.edit("fieldShape",1,"");m.edit("fieldLength",8,"");m.edit("fieldWidth",1,"");var volume=ControlVolume.frame(m,o,new Vec3(1,0,0));
        h.assertTrue(ModuleFreeze.captureVolumes(h.getLevel(),c,m,List.of(volume))==2&&MobGrab.frozen(far),"Rotated rectangle query bounds lost a mob");MobGrab.release(h.getLevel().getServer(),new PlayerFreeze.Source(c.creatureId(),m.id()));inside.discard();far.discard();high.discard();h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=30) public static void FreezeOnlyPinsPositionWhileAiAndDamageContinue(GameTestHelper h){
        var at=h.absoluteVec(new Vec3(5,8,5));var p=pig(h,at,true);var a=source();var b=source();h.assertTrue(MobGrab.freeze(p,a,5)&&MobGrab.freeze(p,b,12)&&!MobGrab.held(p),"Fixed freeze became a grab");p.ai=0;MobGrab.release(h.getLevel().getServer(),a);
        h.runAfterDelay(4,()->{h.assertTrue(MobGrab.frozen(p)&&p.ai>0&&p.position().distanceTo(at)<1e-8&&!p.isNoAi()&&!p.isNoGravity(),"AI paused, position escaped or other source released");float hp=p.getHealth();p.hurt(h.getLevel().damageSources().magic(),1);h.assertTrue(p.getHealth()<hp,"Frozen mob became invulnerable");});
        h.runAfterDelay(15,()->{h.assertTrue(!MobGrab.frozen(p)&&p.position().y<at.y&&!p.isNoAi(),"Expiry did not restore movement");p.discard();h.succeed();});
    }
    @GameTest(template="combat",timeoutTicks=30) public static void GrabCanCarryFrozenMobThenLeavesItAtReleasePoint(GameTestHelper h){
        var at=h.absoluteVec(new Vec3(5,8,5));var p=pig(h,at,false);p.setNoGravity(true);var freeze=source();var grab=source();var point=new Vec3[]{at};MobGrab.freeze(p,freeze,25);h.assertTrue(MobGrab.grab(p,grab,20,m->point[0]),"Fixed freeze blocked grab");h.runAfterDelay(2,()->point[0]=at.add(.5,1,0));
        h.runAfterDelay(5,()->{MobGrab.post(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(p));h.assertTrue(p.position().distanceTo(point[0])<1e-8,"Frozen mob did not follow grab");MobGrab.release(h.getLevel().getServer(),grab);h.assertTrue(MobGrab.frozen(p)&&!MobGrab.held(p)&&p.position().distanceTo(point[0])<1e-8,"Release erased freeze or snapped back");MobGrab.release(h.getLevel().getServer(),freeze);h.assertTrue(!MobGrab.frozen(p)&&p.isNoAi()&&p.isNoGravity(),"Final release changed flags");p.discard();h.succeed();});
    }
    @GameTest(template="combat") public static void DisablingFreezeModuleReleasesOnlyItsMobSource(GameTestHelper h){
        var c=SlashGuardGameTests.core(h);var m=c.modules().add("freeze");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("startDistance",2,"");m.edit("freezeTarget",1,"");m.edit("freezeWarning",0,"");var point=ModuleFreeze.centers(c,m).getFirst();var p=pig(h,point.subtract(0,.45,0),false);c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);h.assertTrue(MobGrab.frozen(p),"Actual module did not freeze mob");var other=source();MobGrab.freeze(p,other,20);m.edit("enabled",0,"");c.modules().tick(c);h.assertTrue(MobGrab.frozen(p),"Module stop cleared another source");MobGrab.release(h.getLevel().getServer(),other);h.assertTrue(!MobGrab.frozen(p),"Disabled module retained source");p.discard();h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=30) public static void MobWarningSamplesPositionsOnlyAtActivation(GameTestHelper h){
        var c=SlashGuardGameTests.core(h);var m=c.modules().add("freeze");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("startDistance",3,"");m.edit("freezeTarget",1,"");m.edit("freezeRadius",1,"");m.edit("freezeWarning",7,"");var o=ModuleFreeze.centers(c,m).getFirst();var escape=pig(h,o,false);var enter=pig(h,o.add(4,0,0),false);escape.setNoGravity(true);enter.setNoGravity(true);c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);h.assertTrue(!MobGrab.frozen(escape),"Warning froze too early");
        h.runAfterDelay(2,()->{escape.setPos(o.add(4,0,0));enter.setPos(o);});h.runAfterDelay(10,()->{h.assertTrue(!MobGrab.frozen(escape)&&MobGrab.frozen(enter),"Mob escape/late entry failed");c.modules().clearRuntime();h.assertTrue(!MobGrab.frozen(enter),"Runtime clear retained freeze");escape.discard();enter.discard();h.succeed();});
    }
}
