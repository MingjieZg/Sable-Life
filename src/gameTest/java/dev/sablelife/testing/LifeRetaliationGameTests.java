package dev.sablelife.testing;
import java.util.*;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.combat.*;
import dev.sablelife.attack.*;
import dev.sablelife.ownership.LoadedCoreIndex;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("sablelife_retaliation_tests") @PrefixGameTestTemplate(false)
public final class LifeRetaliationGameTests {
    private static HeartBlockEntity core(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(4,40,4));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());c.configure(1000,100);c.tryActivate(TestActors.creative(l));LoadedCoreIndex.observe(c);return c;}
    private static VirtualModule receiver(HeartBlockEntity c){var m=c.modules().add("entity_hitbox");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos().east(),Direction.UP)));m.edit("hitboxOffsetY",4,"");c.modules().damageBoxes.tick(c);return m;}
    private static net.minecraft.world.entity.monster.Zombie zombie(GameTestHelper h,Vec3 p,boolean ai){var z=EntityType.ZOMBIE.create(h.getLevel());z.setNoGravity(true);z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);z.setNoAi(!ai);z.setPos(p);z.tickCount=20;h.getLevel().addFreshEntity(z);return z;}
    @GameTest(template="combat",timeoutTicks=80,batch="proactive_hitbox")
    public static void OptInAttractsHostileAndRetaliatingMobsWithoutChangingPassiveAnimals(GameTestHelper h){
        var c=core(h);var settings=receiver(c);var box=c.modules().damageBoxes.entities().getFirst();var level=h.getLevel();
        var hover=c.modules().add("hover_anchor");
        hover.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));
        hover.edit("hoverRotation",1,"");
        var point=box.position();
        var z=zombie(h,point.add(1,0,0),true);
        var wolf=EntityType.WOLF.create(level);
        var cow=EntityType.COW.create(level);
        wolf.setPos(point.add(0,0,1));
        cow.setPos(point.add(0,0,-4));
        for(var mob:List.of(wolf,cow)){mob.setNoGravity(true);mob.tickCount=20;level.addFreshEntity(mob);}
        h.runAfterDelay(12,()->{
            h.assertTrue(z.getTarget()==null&&wolf.getTarget()==null,"Disabled option attracted a mob");
            z.setPos(box.position().add(1,0,0));
            wolf.setPos(box.position().add(0,0,1));
            settings.edit("hitboxProvokeMobs",1,"");
            h.assertTrue(VirtualModule.load(settings.save()).value("hitboxProvokeMobs")==1,"Aggro opt-in did not persist");
        });
        h.runAfterDelay(45,()->{
            h.assertTrue(z.getTarget()==box&&wolf.getTarget()==box,"Receiver target missing, zombie="+z.getTarget()+", wolf="+wolf.getTarget());
            h.assertTrue(cow.getTarget()==null&&z.getLastHurtByMob()==null&&wolf.getLastHurtByMob()==null,"Acquisition used fake damage or affected passive animals");
            h.assertTrue(c.health().current()<1000,"Native attack did not damage core");
            settings.edit("hitboxProvokeMobs",0,"");
        });
        h.runAfterDelay(51,()->{
            h.assertTrue(z.getTarget()==null&&wolf.getTarget()==null,"Opting out left proactive targets assigned");
            z.discard();wolf.discard();cow.discard();h.succeed();
        });
    }
    @GameTest(template="combat",timeoutTicks=100,batch="retaliation_zombie")
    public static void RealZombieAiRetaliatesAndItsAttackDamagesCore(GameTestHelper h){var c=core(h);receiver(c);var box=c.modules().damageBoxes.entities().getFirst();var z=zombie(h,box.getBoundingBox().getCenter().add(1,-.5,0),true);var source=ModuleDamage.named(ModuleDamage.physical(h.getLevel()),c);h.assertTrue(ModuleDamage.hurt(z,source,1),"Initial Life attack failed");h.assertTrue(z.getLastHurtByMob()==box&&z.getTarget()==null,"Bridge did not supply normal memory, or forced a target");h.runAfterDelay(8,()->{h.assertTrue(z.getTarget()==box,"Zombie's own hurt-by AI did not select receiver");h.assertTrue(c.health().current()<1000,"Zombie did not actually hit receiver/core: "+c.health().current());z.discard();h.succeed();});}

    @GameTest(template="combat",timeoutTicks=140,batch="retaliation_whitelist_golem_on")
    public static void WhitelistedIronGolemBecomesTargetableAfterNativeRetaliation(GameTestHelper h) {
        whitelistedGolemRetaliation(h, true, dev.sablelife.heart.CoreRelations.MobGroup.NEUTRAL.id().toString());
    }

    @GameTest(template="combat",timeoutTicks=140,batch="retaliation_whitelist_golem_off")
    public static void WhitelistedIronGolemCanRetaliateWithTheTargetExceptionOff(GameTestHelper h) {
        whitelistedGolemRetaliation(h, false, "minecraft:iron_golem");
    }

    private static void whitelistedGolemRetaliation(GameTestHelper h, boolean allowRetaliation, String whitelistEntry) {
        var core = core(h);
        var hitbox = receiver(core);
        hitbox.edit("hitboxOffsetY", 0, "");
        hitbox.edit("hitboxOffsetX", 2, "");
        hitbox.edit("hitboxRetaliateAgainstWhitelisted", allowRetaliation ? 1 : 0, "");
        core.modules().damageBoxes.tick(core);
        var box = core.modules().damageBoxes.entities().getFirst();
        var level = h.getLevel();
        var hover = core.modules().add("hover_anchor");
        hover.anchors(List.of(ModuleAnchor.create(core, core.getBlockPos(), Direction.UP)));
        hover.edit("hoverRotation", 1, "");
        h.assertTrue(core.relations().excludeMob(whitelistEntry), "Could not whitelist iron golems");

        var blast = core.modules().add("sphere_blast");
        blast.anchors(List.of(ModuleAnchor.create(core, core.getBlockPos().east(), Direction.EAST)));
        blast.edit("enabled", 0, "");
        blast.edit("sphereDistance", 3, "");
        blast.edit("blastRadius", 2, "");
        blast.edit("damage", 6, "");
        blast.edit("chargeTime", 0, "");
        blast.edit("interval", 1200, "");
        var aim = new VirtualModule("gun");
        aim.edit("shotAim", 1, "");
        aim.edit("target", 2, "");
        aim.edit("targetRange", 8, "");

        var origin = box.position();
        var floor = BlockPos.containing(origin).below();
        for (var pos : BlockPos.betweenClosed(floor.offset(-4, 0, -9), floor.offset(10, 0, 9))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        var center = ModuleSphereBlast.center(core, blast);
        var struck = golem(h, new Vec3(center.x, floor.getY() + 1, center.z));
        var untouched = golem(h, new Vec3(origin.x, floor.getY() + 1, origin.z - 7));
        var bounds = box.getBoundingBox().inflate(8);
        h.runAfterDelay(8, () -> {
            h.assertTrue(struck.getTarget() == null && untouched.getTarget() == null && core.health().current() == 1000,
                    "An iron golem attacked before any Life damage");
            h.assertTrue(ModuleTargets.resolve(level, struck.getUUID(), core.creatureId()) == null
                            && ProjectileAim.directions(core, aim, box.getBoundingBox().getCenter()).isEmpty(),
                    "An aim module acquired a whitelisted iron golem before retaliation");
            blast.edit("enabled", 1, "");
        });
        h.succeedWhen(() -> {
            h.assertTrue(struck.getHealth() == 94 && struck.getLastHurtByMob() == box,
                    "Sphere Blast did not damage and anger the whitelisted golem: HP=" + struck.getHealth()
                            + ", golem=" + struck.position() + ", blast=" + ModuleSphereBlast.center(core, blast)
                            + ", attacker=" + struck.getLastHurtByMob());
            h.assertTrue(struck.getTarget() == box && core.health().current() < 1000,
                    "Iron golem AI did not damage the core: HP=" + core.health().current()
                            + ", golem=" + struck.position() + ", receiver=" + box.position() + ", target=" + struck.getTarget());
            var targets = ModuleTargets.query(level, core.creatureId(), bounds, mob -> ModuleTargets.matches(mob, aim));
            h.assertTrue(targets.contains(struck) == allowRetaliation
                            && ProjectileAim.directions(core, aim, box.getBoundingBox().getCenter()).size() == (allowRetaliation ? 1 : 0),
                    "Retaliating golem had the wrong target eligibility");
            h.assertTrue(untouched.getHealth() == 100 && untouched.getTarget() == null && !targets.contains(untouched),
                    "Another iron golem was damaged, angered, or made targetable");
            System.out.println("WHITELIST_GOLEM retaliation=" + allowRetaliation + " golemHp=" + struck.getHealth()
                    + " coreHp=" + core.health().current() + " eligible=" + targets.contains(struck));
            struck.discard();
            untouched.discard();
        });
    }

    private static net.minecraft.world.entity.animal.IronGolem golem(GameTestHelper h, Vec3 position) {
        var golem = EntityType.IRON_GOLEM.create(h.getLevel());
        golem.setPlayerCreated(true);
        golem.setPersistenceRequired();
        golem.setPos(position);
        h.assertTrue(h.getLevel().addFreshEntity(golem), "Could not spawn the iron golem");
        return golem;
    }

    @GameTest(template="combat",batch="retaliation_projectile")
    public static void TaggedNativeProjectileUsesCoreReceiverWithoutChangingSource(GameTestHelper h){var c=core(h);receiver(c);var box=c.modules().damageBoxes.entities().getFirst();var z=zombie(h,box.position().add(1,0,0),false);var arrow=new Arrow(EntityType.ARROW,h.getLevel());AttackHitCredit.attach(arrow,c,new VirtualModule("arrow"));ModuleDamage.nameProjectile(arrow,c);var source=h.getLevel().damageSources().arrow(arrow,null);z.hurt(source,1);h.assertTrue(z.getLastHurtByMob()==box&&source.getEntity()==null&&source.getDirectEntity()==arrow,"Native projectile retaliation missing or source changed");z.discard();h.succeed();}
    @GameTest(template="combat",batch="retaliation_gates")
    public static void CancelledZeroAndNoAngerDamageDoNotCreateRetaliation(GameTestHelper h){var c=core(h);receiver(c);var box=c.modules().damageBoxes.entities().getFirst();var z=zombie(h,box.position().add(1,0,0),false);var source=ModuleDamage.named(ModuleDamage.physical(h.getLevel()),c);java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> cancel=e->{if(e.getEntity()==z)e.setCanceled(true);};net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,cancel);try{ModuleDamage.hurt(z,source,1);}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);}h.assertTrue(z.getLastHurtByMob()==null,"Cancelled attack provoked retaliation");ModuleDamage.hurt(z,source,0);h.assertTrue(z.getLastHurtByMob()==null,"Zero damage provoked retaliation");ModuleDamage.hurt(z,ModuleDamage.named(new net.minecraft.world.damagesource.DamageSource(h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK_NO_AGGRO)),c),1);h.assertTrue(z.getLastHurtByMob()==null,"NO_ANGER attack provoked retaliation");z.discard();h.succeed();}
    @GameTest(template="combat",batch="retaliation_stability")
    public static void SeveralReceiversStayOneOpponentAndDisabledReceiversAreNotUsed(GameTestHelper h){var c=core(h);var first=receiver(c);var second=receiver(c);second.edit("hitboxOffsetX",6,"");c.modules().damageBoxes.tick(c);var boxes=c.modules().damageBoxes.entities();var z=zombie(h,boxes.getFirst().position().add(1,0,0),false);var source=ModuleDamage.named(ModuleDamage.physical(h.getLevel()),c);ModuleDamage.hurt(z,source,1);h.assertTrue(z.getLastHurtByMob()==boxes.getFirst(),"Nearest receiver not selected");z.setPos(boxes.getLast().position().add(1,0,0));z.tickCount++;ModuleDamage.hurt(z,source,1);h.assertTrue(z.getLastHurtByMob()==boxes.getFirst(),"Same core kept switching receiver targets");first.edit("enabled",0,"");c.modules().damageBoxes.tick(c);z.tickCount++;ModuleDamage.hurt(z,source,1);h.assertTrue(z.getLastHurtByMob()==boxes.getLast(),"Disabled receiver remained retaliation target");second.edit("enabled",0,"");c.modules().damageBoxes.tick(c);z.setLastHurtByMob(null);ModuleDamage.hurt(z,source,1);h.assertTrue(z.getLastHurtByMob()==null,"No active receivers fabricated a target");z.discard();h.succeed();}
    @GameTest(template="combat",timeoutTicks=60,batch="retaliation_passive")
    public static void PassiveAnimalDoesNotGainAttackAi(GameTestHelper h){var c=core(h);receiver(c);var box=c.modules().damageBoxes.entities().getFirst();var cow=EntityType.COW.create(h.getLevel());cow.setNoGravity(true);cow.setPos(box.position().add(1,0,0));cow.tickCount=20;h.getLevel().addFreshEntity(cow);ModuleDamage.hurt(cow,ModuleDamage.named(ModuleDamage.physical(h.getLevel()),c),1);h.runAfterDelay(4,()->{h.assertTrue(cow.getTarget()==null&&c.health().current()==1000,"Passive cow gained attacking behavior");cow.discard();h.succeed();});}
    @GameTest(template="combat",batch="retaliation_actor")
    public static void RealLivingAttackerIsNotReplacedByReceiver(GameTestHelper h){var c=core(h);receiver(c);var box=c.modules().damageBoxes.entities().getFirst();var z=zombie(h,box.position().add(1,0,0),false);var real=EntityType.IRON_GOLEM.create(h.getLevel());var source=ModuleDamage.named(h.getLevel().damageSources().mobAttack(real),c);z.hurt(source,1);h.assertTrue(z.getLastHurtByMob()==real,"Real living attacker was replaced");z.discard();h.succeed();}
}
