package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.combat.EntityHitbox;
import dev.sablelife.heart.*;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.ownership.LoadedCoreIndex;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

/** Numerical and lifecycle boundaries supplement the native projectile/skill fixtures. */
@GameTestHolder("sablelife_hitbox_tests") @PrefixGameTestTemplate(false)
public final class HitboxStabilityGameTests {
    private static HeartBlockEntity core(GameTestHelper h,int count,boolean survival){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(4,40,4));
        level.setBlockAndUpdate(p,(survival?SableLife.SURVIVAL_LIFE_CORE.get():SableLife.HEART.get()).defaultBlockState());level.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());var actor=TestActors.creative(level);core.configure(1000,100);
        if(survival){core.depositSurvivalTokens(actor,new ItemStack(Items.COPPER_INGOT,64));core.depositSurvivalTokens(actor,new ItemStack(Items.COPPER_INGOT,36));}
        core.tryActivate(actor);LoadedCoreIndex.observe(core);h.assertTrue(core.health().state()==HeartState.ALIVE&&core.health().current()==1000,"Fixture did not activate with full health");
        for(int i=0;i<count;i++){var m=core.modules().add("entity_hitbox");m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.UP)));}
        core.modules().damageBoxes.tick(core);return core;
    }
    private static void hit(HeartBlockEntity core,DamageSource source,float raw){for(var box:core.modules().damageBoxes.entities())box.hurt(source,raw);core.modules().damageBoxes.flush(core);}
    private static void hp(GameTestHelper h,HeartBlockEntity core,float expected){h.assertTrue(Float.isFinite(core.health().current())&&Math.abs(core.health().current()-expected)<.001,"Expected HP "+expected+", actual "+core.health().current());}
    private static void cadence(GameTestHelper h,int interval,float loss){
        var core=core(h,5,false);var source=h.getLevel().damageSources().mobAttack(EntityType.ZOMBIE.create(h.getLevel()));hit(core,source,14);
        for(int tick=interval;tick<=20;tick+=interval)h.runAfterDelay(tick,()->hit(core,source,14));
        h.runAfterDelay(22,()->{hp(h,core,1000-loss);System.out.println("HITBOX_CADENCE: intervalTicks="+interval+" receivers=5 observedTicks=21 lifeLoss="+(1000-core.health().current()));h.succeed();});
    }
    @GameTest(template="combat",batch="hitbox_cadence_1",timeoutTicks=45) public static void EveryTickContactHasThreeAcceptedHitsInTwentyOneTicks(GameTestHelper h){cadence(h,1,42);}
    @GameTest(template="combat",batch="hitbox_cadence_5",timeoutTicks=45) public static void FiveTickContactRespectsTheSameCoreWindow(GameTestHelper h){cadence(h,5,42);}
    @GameTest(template="combat",batch="hitbox_cadence_11",timeoutTicks=45) public static void ContactBeyondTheWindowIsNotLost(GameTestHelper h){cadence(h,11,28);}

    @GameTest(template="combat",batch="hitbox_invalid_amounts")
    public static void InvalidAmountsCannotCorruptHealthOrConsumeTheNextHit(GameTestHelper h){
        var core=core(h,5,false);var source=h.getLevel().damageSources().generic();
        for(float raw:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-1,0})for(var box:core.modules().damageBoxes.entities())h.assertTrue(!box.hurt(source,raw),"Invalid amount accepted: "+raw);
        core.modules().damageBoxes.flush(core);hp(h,core,1000);hit(core,source,14);hp(h,core,986);h.succeed();
    }
    @GameTest(template="combat",batch="hitbox_independent_types")
    public static void DifferentDamageTypesFromOneAttackerRemainIndependent(GameTestHelper h){
        var core=core(h,5,false);var attacker=EntityType.ZOMBIE.create(h.getLevel());hit(core,h.getLevel().damageSources().mobAttack(attacker),14);hit(core,h.getLevel().damageSources().indirectMagic(attacker,attacker),14);hp(h,core,972);h.succeed();
    }
    @GameTest(template="combat",batch="hitbox_canceled_retry")
    public static void CanceledDamageDoesNotStartAHurtWindow(GameTestHelper h){
        var core=core(h,5,false);var source=h.getLevel().damageSources().mobAttack(EntityType.ZOMBIE.create(h.getLevel()));
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> cancel=e->{if(e.getEntity() instanceof EntityHitbox box&&box.core()==core)e.setCanceled(true);};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);try{hit(core,source,20);hp(h,core,1000);}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);}
        hit(core,source,14);hp(h,core,986);h.succeed();
    }
    @GameTest(template="combat",batch="hitbox_suppressed_post",timeoutTicks=30)
    public static void SuppressedContactsEmitNoPhantomPostDamageEvents(GameTestHelper h){
        var core=core(h,5,false);var source=h.getLevel().damageSources().mobAttack(EntityType.ZOMBIE.create(h.getLevel()));int[] count={0};float[] actual={0};
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post> listener=e->{if(e.getEntity() instanceof EntityHitbox box&&box.core()==core){count[0]++;actual[0]+=e.getNewDamage();}};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try{hit(core,source,20);}catch(Throwable error){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);throw error;}
        h.runAfterDelay(1,()->{try{hit(core,source,20);hp(h,core,980);h.assertTrue(count[0]==1&&actual[0]==20,"Suppressed contact emitted a post event");}catch(Throwable error){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);throw error;}});
        // The receiver queue settles once at the end of a server tick. Put the stronger
        // contact on a distinct tick, rather than forcing two settlements in one tick.
        h.runAfterDelay(2,()->{try{hit(core,source,30);hp(h,core,970);h.assertTrue(count[0]==2&&actual[0]==30,"Post event included suppressed aliases or raw damage: "+count[0]+" / "+actual[0]);System.out.println("HITBOX_POST_EVENTS: rawSequence=20,20,30 ticks=0,1,2 receivers=5 postEvents="+count[0]+" actualLoss="+actual[0]);h.succeed();}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}});
    }
    @GameTest(template="combat",batch="hitbox_reactivation_window")
    public static void ReactivationClearsThePreviousHurtWindow(GameTestHelper h){
        var core=core(h,5,false);var source=h.getLevel().damageSources().mobAttack(EntityType.ZOMBIE.create(h.getLevel()));hit(core,source,14);hp(h,core,986);
        var old=core.modules().damageBoxes.entities();var actor=TestActors.creative(h.getLevel());core.resetForCreative(actor);h.assertTrue(old.stream().allMatch(EntityHitbox::isRemoved),"Release retained receivers");core.tryActivate(actor);core.modules().damageBoxes.tick(core);hp(h,core,1000);hit(core,source,14);hp(h,core,986);h.succeed();
    }
    @GameTest(template="combat",batch="hitbox_projectile_burst")
    public static void OneHundredIndependentProjectilesKeepTheirDamageAcrossEightOverlappingBoxes(GameTestHelper h){
        var core=core(h,8,false);var attacker=EntityType.ZOMBIE.create(h.getLevel());var boxes=core.modules().damageBoxes.entities();
        for(int i=0;i<100;i++){var arrow=new Arrow(h.getLevel(),0,0,0,new ItemStack(Items.ARROW),null);arrow.setOwner(attacker);for(var box:boxes)box.hurt(h.getLevel().damageSources().arrow(arrow,attacker),7);}
        core.modules().damageBoxes.flush(core);hp(h,core,300);System.out.println("HITBOX_BURST: distinctProjectiles=100 receivers=8 contactCalls=800 lifeLoss=700");h.succeed();
    }
    @GameTest(template="combat",batch="hitbox_survival_death")
    public static void EmptyEnergyDoesNotPreventIncomingDamageAndDeathReleasesSurvivalReceivers(GameTestHelper h){
        var core=core(h,5,true);h.assertTrue(core.survivalTokenBalance()==0,"Fixture has spare Energy");var old=core.modules().damageBoxes.entities();hit(core,h.getLevel().damageSources().generic(),1000);
        hp(h,core,0);h.assertTrue(core.health().state()==HeartState.DORMANT&&old.stream().allMatch(EntityHitbox::isRemoved)&&core.modules().damageBoxes.entities().isEmpty(),"Survival death did not release receivers");h.succeed();
    }
}
