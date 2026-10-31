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
public final class MobGrabGameTests {
    private static final class CountingPig extends Pig {
        int aiTicks;CountingPig(net.minecraft.server.level.ServerLevel level){super(EntityType.PIG,level);}
        @Override protected void customServerAiStep(){aiTicks++;super.customServerAiStep();}
    }
    private static CountingPig pig(GameTestHelper h,Vec3 at){var p=new CountingPig(h.getLevel());p.setPos(at);h.getLevel().addFreshEntity(p);return p;}
    private static PlayerFreeze.Source source(){return new PlayerFreeze.Source(UUID.randomUUID(),UUID.randomUUID());}
    @GameTest(template="combat") public static void SelectorPersistsAndExcludesMechanicalLives(GameTestHelper h){
        var m=new VirtualModule("grab");var animal=pig(h,h.absoluteVec(new Vec3(5,8,5)));var player=new SlashGuardGameTests.Defender(h.getLevel());
        h.assertTrue(m.value("grabTarget")==0&&ModuleGrab.matches(player,m)&&!ModuleGrab.matches(animal,m),"Old player-only default changed");m.edit("grabTarget",1,"");h.assertTrue(ModuleGrab.matches(animal,m)&&!ModuleGrab.matches(player,m),"Mobs-only filter failed");m.edit("grabTarget",2,"");h.assertTrue(ModuleGrab.matches(animal,m)&&ModuleGrab.matches(player,m)&&ModulePreset.create(m.save()).value("grabTarget")==2&&VirtualModule.load(m.save()).value("grabTarget")==2,"Mixed mode/save/preset failed");
        var c=SlashGuardGameTests.core(h);c.tryActivate(TestActors.creative(h.getLevel()));var proxy=ModuleTargets.query(h.getLevel(),UUID.randomUUID(),dev.sablelife.ownership.LoadedCoreIndex.bounds(c).inflate(1),e->e.getUUID().equals(c.creatureId())).stream().filter(e->e instanceof ModuleTargets.LifeTarget).findFirst().orElseThrow();
        h.assertTrue(!MobGrab.eligible(proxy)&&!ModuleGrab.matches(proxy,m)&&!ModuleGrab.matches(new net.minecraft.world.entity.decoration.ArmorStand(h.getLevel(),0,0,0),m),"Mechanical life/decorative entity admitted");animal.discard();h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=30) public static void AiAndTravelContinueWhilePositionStaysPinned(GameTestHelper h){
        var at=h.absoluteVec(new Vec3(5,8,5));var pig=pig(h,at);var source=source();h.assertTrue(MobGrab.grab(pig,source,20,m->at),"Mob grab rejected");pig.aiTicks=0;
        h.assertTrue(!pig.isNoAi()&&!pig.isNoGravity(),"Capture changed persistent flags");pig.setDeltaMovement(new Vec3(.3,.4,.2));pig.travel(new Vec3(1,0,1));h.assertTrue(pig.position().distanceTo(at)>.01,"Native travel was intercepted");MobGrab.post(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(pig));h.assertTrue(pig.position().distanceTo(at)<1e-8&&pig.getDeltaMovement().lengthSqr()==0,"Travel/knockback escaped");
        h.runAfterDelay(4,()->{h.assertTrue(pig.aiTicks>0&&MobGrab.held(pig),"Native AI stopped during hold");float hp=pig.getHealth();pig.hurt(h.getLevel().damageSources().magic(),1);h.assertTrue(pig.getHealth()<hp,"Held mob became invulnerable");MobGrab.release(h.getLevel().getServer(),source);});
        h.runAfterDelay(8,()->{h.assertTrue(!MobGrab.held(pig)&&pig.aiTicks>0&&!pig.isNoAi()&&!pig.isNoGravity(),"AI/gravity did not resume");pig.discard();h.succeed();});
    }
    @GameTest(template="combat",timeoutTicks=30) public static void MovingMobAttachmentExpiresAndKeepsExistingNoAi(GameTestHelper h){
        var at=h.absoluteVec(new Vec3(5,8,5));var pig=pig(h,at);pig.setNoAi(true);pig.setNoGravity(true);var target=new Vec3[]{at};h.assertTrue(MobGrab.grab(pig,source(),8,m->target[0]),"Mob grab rejected");
        h.runAfterDelay(2,()->target[0]=at.add(.5,1,0));h.runAfterDelay(5,()->{MobGrab.post(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(pig));h.assertTrue(MobGrab.held(pig)&&pig.position().distanceTo(target[0])<1e-8,"Mob did not follow moving point");});
        h.runAfterDelay(10,()->{h.assertTrue(!MobGrab.held(pig)&&pig.isNoAi()&&pig.isNoGravity(),"Expiry changed original flags");pig.discard();h.succeed();});
    }
    @GameTest(template="combat",timeoutTicks=30) public static void MobPathObstructionAndSourceLossRelease(GameTestHelper h){
        var at=h.absoluteVec(new Vec3(5.5,8,5.5));var pig=pig(h,at);pig.setNoGravity(true);var point=new Vec3[]{at};h.assertTrue(MobGrab.grab(pig,source(),25,m->point[0]),"Mob grab rejected");h.getLevel().setBlockAndUpdate(BlockPos.containing(at.add(1,0,0)),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        h.runAfterDelay(2,()->point[0]=at.add(3,0,0));h.runAfterDelay(5,()->{h.assertTrue(!MobGrab.held(pig)&&pig.position().distanceTo(at)<.2,"Mob tunneled through wall");point[0]=at;h.assertTrue(MobGrab.grab(pig,source(),20,m->point[0]),"Mob regrab failed");point[0]=null;});
        h.runAfterDelay(8,()->{h.assertTrue(!MobGrab.held(pig),"Lost source retained mob");pig.discard();h.succeed();});
    }
    @GameTest(template="combat") public static void ActualModuleCapturesMobAndClearsItWhenStopped(GameTestHelper h){
        var c=SlashGuardGameTests.core(h);var m=c.modules().add("grab");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("grabTarget",1,"");m.edit("grabWarning",0,"");c.tryActivate(TestActors.creative(h.getLevel()));var at=ModuleGrab.holdPoint(c,m);var animal=pig(h,at.subtract(0,.45,0));
        c.modules().tick(c);h.assertTrue(MobGrab.held(animal),"Actual module did not capture ordinary mob");m.edit("enabled",0,"");c.modules().tick(c);h.assertTrue(!MobGrab.held(animal)&&!animal.isNoAi(),"Disabled module retained mob");animal.discard();h.succeed();
    }
    @GameTest(template="combat") public static void OneMobCannotBeStolenAndDeathClearsControl(GameTestHelper h){var p=pig(h,h.absoluteVec(new Vec3(5,8,5)));var at=p.position();h.assertTrue(MobGrab.grab(p,source(),20,m->at)&&!MobGrab.grab(p,source(),20,m->at),"Grab ownership conflict");p.setHealth(0);h.assertTrue(!MobGrab.held(p),"Dead mob retained control");p.discard();h.succeed();}
}
