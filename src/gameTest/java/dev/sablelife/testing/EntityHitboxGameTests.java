package dev.sablelife.testing;

import java.util.*;
import dev.sablelife.SableLife;
import dev.sablelife.combat.*;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.ownership.LoadedCoreIndex;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sablelife_hitbox_tests") @PrefixGameTestTemplate(false)
public final class EntityHitboxGameTests {
    private static HeartBlockEntity core(GameTestHelper h){return core(h,0);}
    private static HeartBlockEntity core(GameTestHelper h,int offset){var l=h.getLevel();var p=h.absolutePos(new BlockPos(4+offset,40,4));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());c.configure(1000,100);c.tryActivate(TestActors.creative(l));LoadedCoreIndex.observe(c);return c;}
    private static VirtualModule module(HeartBlockEntity c){var m=c.modules().add("entity_hitbox");m.edit("hitboxDamageMode",0,"");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos().east(),Direction.UP)));return m;}
    private static List<EntityHitbox> boxes(HeartBlockEntity c){c.modules().damageBoxes.tick(c);return c.modules().damageBoxes.entities();}
    private static void hp(GameTestHelper h,HeartBlockEntity c,float expected){h.assertTrue(Math.abs(c.health().current()-expected)<.001,"Expected HP "+expected+", got "+c.health().current());}
    @GameTest(template="combat",batch="hitbox_basic")
    public static void ReceiverSwitchesDamageModeAndCleansOnDisableAndRelease(GameTestHelper h){var c=core(h);var m=module(c);var b=boxes(c).getFirst();h.assertTrue(EntityHitboxes.mode(c),"No entity mode");h.assertTrue(!c.damageAt(c.getBlockPos(),10),"Structure damage duplicated receiver path");b.hurt(h.getLevel().damageSources().generic(),20);hp(h,c,1000);c.modules().damageBoxes.flush(c);hp(h,c,980);m.edit("enabled",0,"");boxes(c);h.assertTrue(b.isRemoved()&&!EntityHitboxes.mode(c),"Disabled receiver retained mode/entity");h.assertTrue(c.damageAt(c.getBlockPos(),10),"Structure mode did not recover");m.edit("enabled",1,"");var next=boxes(c).getFirst();c.resetForCreative(TestActors.creative(h.getLevel()));h.assertTrue(next.isRemoved(),"Release leaked receiver");h.succeed();}
    @GameTest(template="combat",batch="hitbox_transform")
    public static void SizeOffsetsFollowRotatedMarkedPartAndRemainAxisAligned(GameTestHelper h){var c=core(h);var m=module(c);m.edit("hitboxWidth",2,"");m.edit("hitboxHeight",3,"");m.edit("hitboxDepth",4,"");m.edit("hitboxOffsetX",3,"");var b=boxes(c).getFirst();var a=m.anchors().getFirst();var body=a.body(c);body.logicalPose().orientation().rotateY(Math.PI/2);b.tick();var expected=body.logicalPose().transformPosition(a.position(body).getCenter().add(3,0,0));h.assertTrue(b.getBoundingBox().getCenter().distanceTo(expected)<1e-6,"Receiver lost part transform");var box=b.getBoundingBox();h.assertTrue(Math.abs(box.getXsize()-2)<1e-6&&Math.abs(box.getYsize()-3)<1e-6&&Math.abs(box.getZsize()-4)<1e-6,"Size not retained/world-axis-aligned");h.succeed();}
    @GameTest(template="combat",batch="hitbox_targets")
    public static void ProxiesNeverAppearAsOrdinaryOrDuplicateLifeTargets(GameTestHelper h){var c=core(h);module(c);module(c);var bs=boxes(c);var bounds=bs.getFirst().getBoundingBox().inflate(8);h.assertTrue(!ModuleTargets.matches(bs.getFirst(),1)&&!ModuleTargets.matches(bs.getFirst(),2),"Proxy matched mobs/all");var targets=ModuleTargets.query(h.getLevel(),UUID.randomUUID(),bounds,e->ModuleTargets.matches(e,2));h.assertTrue(targets.stream().noneMatch(e->e instanceof EntityHitbox)&&targets.stream().filter(e->e instanceof ModuleTargets.LifeTarget).count()==1,"Target count includes proxies");h.assertTrue(ModuleTargets.resolve(h.getLevel(),bs.getFirst().getUUID(),UUID.randomUUID())==null,"Proxy resolved as ordinary target");h.succeed();}
    @GameTest(template="combat",batch="hitbox_dedup")
    public static void DifferentAttackersRemainIndependentWhileOverlapsDoNotMultiplyDamage(GameTestHelper h){var c=core(h);module(c);module(c);var bs=boxes(c);var a=EntityType.ZOMBIE.create(h.getLevel());var b=EntityType.ZOMBIE.create(h.getLevel());var sa=h.getLevel().damageSources().mobAttack(a);var sb=h.getLevel().damageSources().mobAttack(b);for(var box:bs){box.hurt(sa,10);box.hurt(sb,15);}c.modules().damageBoxes.flush(c);hp(h,c,975);h.succeed();}
    @GameTest(template="combat",batch="hitbox_armor")
    public static void ReceiverUsesMarkedPartArmorExactlyOnceAndAggregatesAreaMaximum(GameTestHelper h){var c=core(h);var part=module(c);var head=module(c);head.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));var armor=c.modules().add("armor");armor.anchors(part.anchors());var row=armor.armorRules().add();row.edit("channel",c.wireless().add()+1);row.edit("when",1);row.edit("modifier",-50);var bs=boxes(c);bs.getFirst().hurt(h.getLevel().damageSources().generic(),20);c.modules().damageBoxes.flush(c);hp(h,c,990);var source=h.getLevel().damageSources().explosion(null,null);bs.getFirst().hurt(source,20);bs.getLast().hurt(source,40);c.modules().damageBoxes.flush(c);hp(h,c,970);h.succeed();}
    @GameTest(template="combat",batch="hitbox_projectile")
    public static void OneProjectileOnlyDamagesOneReceiverAcrossTicks(GameTestHelper h){var c=core(h);module(c);module(c);var bs=boxes(c);var arrow=new Arrow(h.getLevel(),0,0,0,new ItemStack(Items.ARROW),null);var source=h.getLevel().damageSources().arrow(arrow,null);for(var box:bs)box.hurt(source,12);c.modules().damageBoxes.flush(c);hp(h,c,988);for(var box:bs)box.hurt(source,12);c.modules().damageBoxes.flush(c);hp(h,c,988);h.succeed();}
    @GameTest(template="combat",batch="hitbox_lifecycle")
    public static void LostMarksDeathAndUnloadRemoveReceiversWithoutSavingThem(GameTestHelper h){var c=core(h);var m=module(c);var b=boxes(c).getFirst();h.assertTrue(!b.save(new net.minecraft.nbt.CompoundTag()),"Receiver persisted into chunk");m.clearMarks();b.tick();h.assertTrue(b.isRemoved()&&!EntityHitboxes.mode(c),"Missing Mark leaked receiver/mode");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos().east(),Direction.UP)));b=boxes(c).getFirst();c.onChunkUnloaded();h.assertTrue(b.isRemoved(),"Unload leaked receiver");LoadedCoreIndex.observe(c);b=boxes(c).getFirst();c.forceDeath();h.assertTrue(b.isRemoved(),"Death leaked receiver");h.succeed();}
    @GameTest(template="combat",batch="hitbox_cap")
    public static void ReceiverCapAndSettingsPersistence(GameTestHelper h){var c=core(h);for(int i=0;i<12;i++)module(c);h.assertTrue(boxes(c).size()==8,"Receiver cap exceeded");var m=c.modules().all().getFirst();m.edit("hitboxWidth",5,"");var copy=VirtualModule.load(m.save());h.assertTrue(copy.value("hitboxWidth")==5&&copy.anchors().equals(m.anchors()),"Saved receiver settings changed");h.assertTrue(!ModuleAvailability.enabled(true,"entity_hitbox"),"Redstone Core gained Life damage receiver");var killed=c.modules().damageBoxes.entities().getFirst();killed.setHealth(0);boxes(c);h.assertTrue(killed.isRemoved()&&c.modules().damageBoxes.entities().size()==8,"Externally killed receiver left an unhurtable mode");h.succeed();}
    @GameTest(template="combat",timeoutTicks=100,batch="hitbox_real_arrow")
    public static void RealVanillaArrowHitsLivingReceiver(GameTestHelper h){var c=core(h);var m=module(c);m.edit("hitboxOffsetY",4,"");var box=boxes(c).getFirst();var center=box.getBoundingBox().getCenter();var arrow=new Arrow(h.getLevel(),center.x-3,center.y,center.z,new ItemStack(Items.ARROW),null);arrow.setNoGravity(true);arrow.setDeltaMovement(2,0,0);h.getLevel().addFreshEntity(arrow);h.runAfterDelay(8,()->{h.assertTrue(c.health().current()<1000,"Real arrow missed/did not transfer damage");arrow.discard();h.succeed();});}
    @GameTest(template="combat",batch="hitbox_real_explosion")
    public static void VanillaExplosionDamagesOverlappingReceiversOnlyOnce(GameTestHelper h){var c=core(h);var m=module(c);m.edit("hitboxOffsetY",4,"");var n=module(c);n.edit("hitboxOffsetY",4,"");var bs=boxes(c);var center=bs.getFirst().getBoundingBox().getCenter();h.getLevel().explode(null,center.x+2,center.y,center.z,2,Level.ExplosionInteraction.NONE);c.modules().damageBoxes.flush(c);h.assertTrue(c.health().current()<1000&&c.health().current()>=970,"Explosion missing or duplicated: "+c.health().current());h.succeed();}
    @GameTest(template="combat",batch="hitbox_factions")
    public static void SelfAndFriendlyFirePoliciesStillApply(GameTestHelper h){var c=core(h);var other=core(h,8);module(c);var b=boxes(c).getFirst();var arrow=new Arrow(h.getLevel(),0,0,0,new ItemStack(Items.ARROW),null);var source=h.getLevel().damageSources().arrow(arrow,null);arrow.getPersistentData().putUUID("SableLifeAttackOwner",c.creatureId());h.assertTrue(!b.hurt(source,10),"Self hit accepted");arrow.getPersistentData().putUUID("SableLifeAttackOwner",other.creatureId());var author=TestActors.creative(h.getLevel());var registry=FactionRegistry.get(h.getLevel().getServer());var faction=registry.create(author,"H"+UUID.randomUUID().toString().substring(0,8));c.relations().faction(faction);other.relations().faction(faction);h.assertTrue(!b.hurt(source,10),"Friendly fire policy bypassed");registry.toggle(author,faction);h.assertTrue(b.hurt(source,10),"Enabled friendly fire rejected");c.modules().damageBoxes.flush(c);hp(h,c,990);c.editCore(author,"lifeAttacks",0,"");var another=new Arrow(h.getLevel(),0,0,0,new ItemStack(Items.ARROW),null);another.getPersistentData().putUUID("SableLifeAttackOwner",other.creatureId());h.assertTrue(!b.hurt(h.getLevel().damageSources().arrow(another,null),10),"Core life-attack switch ignored");h.succeed();}
    @GameTest(template="combat",batch="hitbox_cancellation")
    public static void CancelledIncomingEntityDamageNeverReachesCore(GameTestHelper h){var c=core(h);module(c);var b=boxes(c).getFirst();java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> cancel=e->{if(e.getEntity()==b)e.setCanceled(true);};net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,cancel);try{h.assertTrue(!b.hurt(h.getLevel().damageSources().generic(),20),"Cancelled entity damage accepted");}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);}c.modules().damageBoxes.flush(c);hp(h,c,1000);h.succeed();}
    @GameTest(template="combat",batch="hitbox_gating")
    public static void ImmunePartConsumesShotAndSignalLossRestoresStructureMode(GameTestHelper h){var c=core(h);var m=module(c);var armor=c.modules().add("armor");armor.anchors(m.anchors());var row=armor.armorRules().add();row.edit("channel",c.wireless().add()+1);row.edit("when",1);row.edit("modifier",-100);var b=boxes(c).getFirst();var arrow=new Arrow(h.getLevel(),0,0,0,new ItemStack(Items.ARROW),null);var source=h.getLevel().damageSources().arrow(arrow,null);b.hurt(source,20);c.modules().damageBoxes.flush(c);hp(h,c,1000);armor.edit("enabled",0,"");b.hurt(source,20);c.modules().damageBoxes.flush(c);hp(h,c,1000);m.edit("trigger",2,"");m.edit("channel",0,"");boxes(c);h.assertTrue(b.isRemoved()&&!EntityHitboxes.mode(c),"Unpowered receiver retained entity mode");h.assertTrue(c.damageAt(c.getBlockPos(),10),"Unpowered receiver blocked structure damage");h.succeed();}

    @GameTest(template="combat",batch="hitbox_attack_identity")
    public static void MeleeUsesFirstReceiverButProjectileExplosionUsesMaximum(GameTestHelper h){var c=core(h);module(c);module(c);var bs=boxes(c);var zombie=EntityType.ZOMBIE.create(h.getLevel());var melee=h.getLevel().damageSources().mobAttack(zombie);bs.getFirst().hurt(melee,5);bs.getLast().hurt(melee,30);c.modules().damageBoxes.flush(c);hp(h,c,995);var arrow=new Arrow(h.getLevel(),0,0,0,new ItemStack(Items.ARROW),null);bs.getFirst().hurt(h.getLevel().damageSources().arrow(arrow,null),12);c.modules().damageBoxes.flush(c);hp(h,c,983);var explosion=h.getLevel().damageSources().explosion(arrow,null);bs.getFirst().hurt(explosion,20);bs.getLast().hurt(explosion,40);c.modules().damageBoxes.flush(c);hp(h,c,943);h.succeed();}

    private static VirtualModule mixed(HeartBlockEntity c){var m=module(c);m.edit("hitboxDamageMode",1,"");return m;}
    private static Arrow arrow(GameTestHelper h){return new Arrow(h.getLevel(),0,0,0,new ItemStack(Items.ARROW),null);}
    private static BlockHitResult block(HeartBlockEntity c){return new BlockHitResult(c.getBlockPos().east().getCenter(),Direction.UP,c.getBlockPos().east(),false);}
    @GameTest(template="combat",batch="hybrid_mode")
    public static void HybridIsOptionalAndSharesCoreHealthWithStructure(GameTestHelper h){
        var c=core(h);var m=module(c);var b=boxes(c).getFirst();h.assertTrue(m.value("hitboxDamageMode")==0&&!c.damageAt(c.getBlockPos(),10),"Existing mode changed");
        m.edit("hitboxDamageMode",1,"");h.assertTrue(EntityHitboxes.hybrid(c)&&c.damageAt(c.getBlockPos(),10),"Hybrid structure route missing");b.hurt(h.getLevel().damageSources().generic(),20);c.modules().damageBoxes.flush(c);hp(h,c,970);
        var copy=VirtualModule.load(m.save());h.assertTrue(copy.value("hitboxDamageMode")==1,"Hybrid setting lost on save");
        m.edit("hitboxDamageMode",0,"");h.assertTrue(!c.damageAt(c.getBlockPos(),10),"Entity-only mode did not restore exclusivity");h.succeed();
    }
    @GameTest(template="combat",batch="hybrid_projectiles")
    public static void BodyThenReceiverSameArrowChargesOnlyOnce(GameTestHelper h){
        var c=core(h);mixed(c);var b=boxes(c).getFirst();var a=arrow(h);h.assertTrue(dev.sablelife.api.BodyCombatApi.projectile(a,block(c),12),"Body shot rejected");
        b.hurt(h.getLevel().damageSources().arrow(a,null),40);c.modules().damageBoxes.flush(c);hp(h,c,988);
        h.assertTrue(!dev.sablelife.api.BodyCombatApi.projectile(a,block(c),12),"Spent body projectile hit twice");h.succeed();
    }
    @GameTest(template="combat",batch="hybrid_projectiles")
    public static void PendingReceiverThenBodyUsesRealBlockContactOnce(GameTestHelper h){
        var c=core(h);mixed(c);module(c);var bs=boxes(c);var a=arrow(h);for(var b:bs)b.hurt(h.getLevel().damageSources().arrow(a,null),40);
        h.assertTrue(dev.sablelife.api.BodyCombatApi.projectile(a,block(c),12),"Queued receiver swallowed physical contact");c.modules().damageBoxes.flush(c);hp(h,c,988);h.succeed();
    }
    @GameTest(template="combat",batch="hybrid_projectiles")
    public static void SettledReceiverThenBodyDoesNotChargeAgain(GameTestHelper h){
        var c=core(h);mixed(c);var b=boxes(c).getFirst();var a=arrow(h);b.hurt(h.getLevel().damageSources().arrow(a,null),12);c.modules().damageBoxes.flush(c);
        h.assertTrue(!dev.sablelife.api.BodyCombatApi.projectile(a,block(c),40),"Settled receiver duplicated body hit");hp(h,c,988);h.succeed();
    }
    @GameTest(template="combat",batch="hybrid_native_arrow")
    public static void NativeArrowImpactAndEntityAliasDoNotMultiplyDamage(GameTestHelper h){
        var c=core(h);mixed(c);var b=boxes(c).getFirst();var a=arrow(h);a.setDeltaMovement(2,0,0);a.setBaseDamage(3);
        b.hurt(h.getLevel().damageSources().arrow(a,null),18);BodyCombat.onProjectileImpact(new net.neoforged.neoforge.event.entity.ProjectileImpactEvent(a,block(c)));c.modules().damageBoxes.flush(c);hp(h,c,994);h.succeed();
    }
    @GameTest(template="combat",batch="hybrid_immune")
    public static void ImmuneBodyContactConsumesShotAndCannotFallThroughToReceiver(GameTestHelper h){
        var c=core(h);var m=mixed(c);var armor=c.modules().add("armor");armor.anchors(m.anchors());var row=armor.armorRules().add();row.edit("channel",c.wireless().add()+1);row.edit("when",1);row.edit("modifier",-100);
        var b=boxes(c).getFirst();var a=arrow(h);b.hurt(h.getLevel().damageSources().arrow(a,null),30);
        h.assertTrue(!dev.sablelife.api.BodyCombatApi.projectile(a,block(c),30),"Immune body reported health loss");armor.edit("enabled",0,"");b.hurt(h.getLevel().damageSources().arrow(a,null),30);c.modules().damageBoxes.flush(c);hp(h,c,1000);
        h.assertTrue(!dev.sablelife.api.BodyCombatApi.projectile(a,block(c),30),"Immune first contact did not consume projectile");h.succeed();
    }
    @GameTest(template="combat",batch="hybrid_melee")
    public static void DifferentPlayersRemainIndependentAcrossBothRoutes(GameTestHelper h){
        var c=core(h);mixed(c);var b=boxes(c).getFirst();var a=TestActors.creative(h.getLevel());var other=TestActors.creative(h.getLevel());
        h.assertTrue(BodyDamageBridge.melee(a,c,block(c).getBlockPos(),block(c).getLocation(),10),"Player body melee rejected");
        b.hurt(h.getLevel().damageSources().playerAttack(a),40);b.hurt(h.getLevel().damageSources().playerAttack(other),15);c.modules().damageBoxes.flush(c);hp(h,c,975);h.succeed();
    }
    @GameTest(template="combat",batch="hybrid_melee")
    public static void PendingEntityMeleeIsReplacedButSettledMeleeIsNotChargedTwice(GameTestHelper h){
        var c=core(h);mixed(c);var b=boxes(c).getFirst();var a=TestActors.creative(h.getLevel());var source=h.getLevel().damageSources().playerAttack(a);
        b.hurt(source,40);h.assertTrue(BodyDamageBridge.melee(a,c,block(c).getBlockPos(),block(c).getLocation(),10),"Body melee lost before settlement");c.modules().damageBoxes.flush(c);hp(h,c,990);
        var other=TestActors.creative(h.getLevel());b.hurt(h.getLevel().damageSources().playerAttack(other),15);c.modules().damageBoxes.flush(c);
        h.assertTrue(!BodyDamageBridge.melee(other,c,block(c).getBlockPos(),block(c).getLocation(),50),"Body melee duplicated settled entity melee");hp(h,c,975);h.succeed();
    }
    @GameTest(template="combat",batch="hybrid_continuous",timeoutTicks=30)
    public static void ContinuousBodyApiKeepsActualDamageAndItsOwnCadence(GameTestHelper h){
        var c=core(h);mixed(c);var b=boxes(c).getFirst();var source=EntityType.AREA_EFFECT_CLOUD.create(h.getLevel());var ds=new net.minecraft.world.damagesource.DamageSource(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(net.minecraft.world.damagesource.DamageTypes.MAGIC),source);
        b.hurt(ds,40);h.assertTrue(dev.sablelife.api.BodyCombatApi.contact(source,block(c),7)==7,"Contact did not return actual HP loss");h.assertTrue(dev.sablelife.api.BodyCombatApi.contact(source,block(c),3)==3,"Contact caller cadence was collapsed");c.modules().damageBoxes.flush(c);hp(h,c,990);
        h.runAfterDelay(1,()->{h.assertTrue(dev.sablelife.api.BodyCombatApi.contact(source,block(c),5)==5,"Body claim survived its tick");b.hurt(ds,40);c.modules().damageBoxes.flush(c);hp(h,c,985);h.succeed();});
    }
    @GameTest(template="combat",batch="hybrid_explosion")
    public static void BodyAndReceiverExplosionUseMaximumOnceAndSeparateBlastsStayIndependent(GameTestHelper h){
        var c=core(h);mixed(c);var b=boxes(c).getFirst();var source=h.getLevel().damageSources().explosion(null,null);
        b.hurt(source,30);c.modules().damageBoxes.bodyExplosion(c,source,block(c).getBlockPos(),20);c.modules().damageBoxes.flush(c);hp(h,c,970);
        c.modules().damageBoxes.bodyExplosion(c,source,block(c).getBlockPos(),50);b.hurt(source,50);c.modules().damageBoxes.flush(c);hp(h,c,970);
        var second=h.getLevel().damageSources().explosion(null,null);c.modules().damageBoxes.bodyExplosion(c,second,block(c).getBlockPos(),20);b.hurt(second,10);c.modules().damageBoxes.flush(c);hp(h,c,950);h.succeed();
    }
    @GameTest(template="combat",batch="hybrid_native_explosion")
    public static void RealVanillaExplosionDoesNotSumBodyAndReceiverDamage(GameTestHelper h){
        var c=core(h);var center=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().east().getCenter()).add(2,1,0);
        h.getLevel().explode(null,center.x,center.y,center.z,2,Level.ExplosionInteraction.NONE);float body=1000-c.health().current();h.assertTrue(body>0,"Explosion fixture missed physical structure");c.restoreFullHealth();mixed(c);module(c);boxes(c);
        final float[] entity={0};java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre> capture=e->{if(e.getEntity() instanceof EntityHitbox box&&box.core()==c)entity[0]=Math.max(entity[0],e.getNewDamage());};net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(capture);
        try{h.getLevel().explode(null,center.x,center.y,center.z,2,Level.ExplosionInteraction.NONE);}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(capture);}
        c.modules().damageBoxes.flush(c);hp(h,c,1000-Math.max(body,entity[0]));h.succeed();
    }
    @GameTest(template="combat",batch="hybrid_lifecycle")
    public static void HybridChoiceUsesOnlyLiveSelectedReceiversAndCleansOnRelease(GameTestHelper h){
        var c=core(h);var old=module(c);var hybrid=mixed(c);boxes(c);h.assertTrue(EntityHitboxes.hybrid(c),"Mixed receiver did not open structure path");hybrid.edit("enabled",0,"");boxes(c);h.assertTrue(!EntityHitboxes.hybrid(c)&&!c.damageAt(c.getBlockPos(),10),"Disabled hybrid still opened structure path");
        old.edit("enabled",0,"");boxes(c);h.assertTrue(!EntityHitboxes.mode(c)&&c.damageAt(c.getBlockPos(),10),"No receivers did not recover structure-only path");hybrid.edit("enabled",1,"");boxes(c);var a=arrow(h);c.modules().damageBoxes.entities().getFirst().hurt(h.getLevel().damageSources().arrow(a,null),20);c.resetForCreative(TestActors.creative(h.getLevel()));c.modules().damageBoxes.flush(c);h.assertTrue(c.modules().damageBoxes.entities().isEmpty(),"Release leaked hybrid entities");h.succeed();
    }

    @GameTest(template="combat",batch="hybrid_post_event")
    public static void PostDamageEventReportsOneActualPartAdjustedHealthLoss(GameTestHelper h){
        var c=core(h);var m=mixed(c);module(c);var armor=c.modules().add("armor");armor.anchors(m.anchors());var row=armor.armorRules().add();row.edit("channel",c.wireless().add()+1);row.edit("when",1);row.edit("modifier",-50);var bs=boxes(c);
        final int[] count={0};final float[] reported={0};java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post> capture=e->{if(e.getEntity() instanceof EntityHitbox box&&box.core()==c){count[0]++;reported[0]+=e.getNewDamage();}};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(capture);try{var a=arrow(h);for(var b:bs)b.hurt(h.getLevel().damageSources().arrow(a,null),20);h.assertTrue(count[0]==0,"Post event ran before core settlement");c.modules().damageBoxes.flush(c);hp(h,c,990);h.assertTrue(count[0]==1&&reported[0]==10,"Post event reported raw/duplicate damage: "+reported[0]);}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(capture);}h.succeed();
    }
    @GameTest(template="combat",batch="hybrid_terrain_visual")
    public static void TerrainOnlySpellExplosionNeverAddsReceiverDamage(GameTestHelper h){
        var c=core(h);mixed(c);var b=boxes(c).getFirst();var center=b.getBoundingBox().getCenter();var explosion=new net.minecraft.world.level.Explosion(h.getLevel(),null,center.x+2,center.y,center.z,2,false,net.minecraft.world.level.Explosion.BlockInteraction.KEEP);BodyExplosionDamage.terrainOnly(explosion);explosion.explode();c.modules().damageBoxes.flush(c);hp(h,c,1000);h.succeed();
    }
    @GameTest(template="combat",batch="hybrid_post_event")
    public static void BodyWinsWithoutPhantomReceiverPostHitEffects(GameTestHelper h){
        var c=core(h);mixed(c);var b=boxes(c).getFirst();final int[] count={0};java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post> capture=e->{if(e.getEntity()==b)count[0]++;};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(capture);try{var a=arrow(h);b.hurt(h.getLevel().damageSources().arrow(a,null),40);dev.sablelife.api.BodyCombatApi.projectile(a,block(c),10);c.modules().damageBoxes.flush(c);hp(h,c,990);h.assertTrue(count[0]==0,"Suppressed receiver alias emitted damage-post effects");}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(capture);}h.succeed();
    }
    @GameTest(template="combat",batch="hybrid_mode_change")
    public static void ChangingRoutingMidTickDoesNotEraseAcceptedAttackIdentity(GameTestHelper h){
        var c=core(h);var m=mixed(c);var b=boxes(c).getFirst();var p=TestActors.creative(h.getLevel());BodyDamageBridge.melee(p,c,block(c).getBlockPos(),block(c).getLocation(),10);m.edit("hitboxDamageMode",0,"");b.hurt(h.getLevel().damageSources().playerAttack(p),40);c.modules().damageBoxes.flush(c);hp(h,c,990);
        var other=TestActors.creative(h.getLevel());b.hurt(h.getLevel().damageSources().playerAttack(other),15);c.modules().damageBoxes.flush(c);m.edit("hitboxDamageMode",1,"");h.assertTrue(!BodyDamageBridge.melee(other,c,block(c).getBlockPos(),block(c).getLocation(),50),"New hybrid mode forgot already settled receiver hit");hp(h,c,975);h.succeed();
    }

    @GameTest(template="combat",batch="hybrid_same_owner_shots")
    public static void SeparateProjectilesFromOnePlayerDoNotShareCrossRouteClaim(GameTestHelper h){
        var c=core(h);mixed(c);var b=boxes(c).getFirst();var player=TestActors.creative(h.getLevel());
        for(int i=0;i<2;i++){var a=arrow(h);a.setOwner(player);b.hurt(h.getLevel().damageSources().arrow(a,player),20);h.assertTrue(dev.sablelife.api.BodyCombatApi.projectile(a,block(c),10),"Another projectile from the same owner was suppressed");}
        c.modules().damageBoxes.flush(c);hp(h,c,980);h.succeed();
    }

    @GameTest(template="combat",batch="hybrid_custom_explosion",timeoutTicks=30)
    public static void NativeExplosionWithUntaggedMagicSourceStillUsesOneContribution(GameTestHelper h){
        var c=core(h);var center=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().east().getCenter()).add(2,1,0);
        h.getLevel().explode(null,center.x,center.y,center.z,2,Level.ExplosionInteraction.NONE);float body=1000-c.health().current();c.restoreFullHealth();mixed(c);var b=boxes(c).getFirst();var source=h.getLevel().damageSources().magic();
        final float[] entity={0};java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre> capture=e->{if(e.getEntity()==b)entity[0]=Math.max(entity[0],e.getNewDamage());};net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(capture);
        try{var blast=new net.minecraft.world.level.Explosion(h.getLevel(),null,source,null,center.x,center.y,center.z,2,false,net.minecraft.world.level.Explosion.BlockInteraction.KEEP,net.minecraft.core.particles.ParticleTypes.EXPLOSION,net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE);blast.explode();}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(capture);}
        c.modules().damageBoxes.flush(c);hp(h,c,1000-Math.max(body,entity[0]));
        h.runAfterDelay(1,()->{h.assertTrue(!BodyExplosionDamage.nativeExplosionSource(source,h.getLevel().getGameTime()),"Cached magic source remained classified as an explosion in later ticks");h.succeed();});
    }

    @GameTest(template="combat",batch="hybrid_blast_and_direct")
    public static void UntaggedBlastDoesNotConsumeTheSeparateDirectProjectileHit(GameTestHelper h){
        var c=core(h);mixed(c);boxes(c);var a=arrow(h);var source=new net.minecraft.world.damagesource.DamageSource(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(net.minecraft.world.damagesource.DamageTypes.MAGIC),a);
        c.modules().damageBoxes.bodyExplosion(c,source,block(c).getBlockPos(),20);c.modules().damageBoxes.flush(c);hp(h,c,980);
        h.assertTrue(dev.sablelife.api.BodyCombatApi.projectile(a,block(c),10),"An untagged blast consumed a distinct projectile-direct component");hp(h,c,970);h.succeed();
    }

    @GameTest(template="combat",batch="hitbox_default_hybrid")
    public static void NewHitboxDefaultsHybridAndSavedEntityOnlyRemains(GameTestHelper h){var m=new VirtualModule("entity_hitbox");h.assertTrue(m.value("hitboxDamageMode")==1,"New receiver is not hybrid");m.edit("hitboxDamageMode",0,"");h.assertTrue(VirtualModule.load(m.save()).value("hitboxDamageMode")==0,"Saved entity-only mode was overwritten");h.succeed();}
    @GameTest(template="combat",batch="hitbox_release_tool")
    public static void EnclosingReceiverCanReleaseCoreWithoutBlockAccess(GameTestHelper h){
        var c=core(h);var m=module(c);m.edit("hitboxWidth",16,"");m.edit("hitboxHeight",16,"");m.edit("hitboxDepth",16,"");var box=boxes(c).getFirst();var other=core(h,20);var player=TestActors.creative(h.getLevel());player.setPos(box.getBoundingBox().getCenter());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(SableLife.CREATIVE_RELEASE_TOOL.get()));var old=c.creatureId();var otherId=other.creatureId();
        h.assertTrue(box.interactAt(player,Vec3.ZERO,net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction(),"Enclosing receiver blocked release tool");h.assertTrue(c.health().state()==HeartState.DORMANT&&!c.creatureId().equals(old),"Receiver did not release its core");h.assertTrue(box.isRemoved()&&c.modules().damageBoxes.entities().isEmpty(),"Release left receiver behind");h.assertTrue(c.modules().find(m.id())!=null&&h.getLevel().getBlockEntity(c.getBlockPos())==c,"Release erased authoring data or core");h.assertTrue(other.health().state()==HeartState.ALIVE&&other.creatureId().equals(otherId),"Receiver release affected unrelated core");h.assertTrue(player.getMainHandItem().getCount()==1,"Release consumed tool");h.succeed();
    }
    @GameTest(template="combat",batch="hitbox_release_permissions")
    public static void ReceiverReleaseRejectsSurvivalAndRemoteAndAcceptsOffhand(GameTestHelper h){
        var c=core(h);module(c);var box=boxes(c).getFirst();var player=TestActors.creative(h.getLevel());player.setPos(box.position());var hand=net.minecraft.world.InteractionHand.OFF_HAND;player.setItemInHand(hand,new ItemStack(SableLife.CREATIVE_RELEASE_TOOL.get()));player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        h.assertTrue(!box.interactAt(player,Vec3.ZERO,hand).consumesAction()&&c.health().state()==HeartState.ALIVE,"Survival released receiver");player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.setPos(box.position().add(100,0,0));h.assertTrue(!box.interactAt(player,Vec3.ZERO,hand).consumesAction()&&c.health().state()==HeartState.ALIVE,"Remote release bypassed reach");player.setPos(box.position());player.setItemInHand(hand,ItemStack.EMPTY);h.assertTrue(!box.interactAt(player,Vec3.ZERO,hand).consumesAction()&&c.health().state()==HeartState.ALIVE,"Empty hand released receiver");player.setItemInHand(hand,new ItemStack(SableLife.CREATIVE_RELEASE_TOOL.get()));h.assertTrue(player.interactOn(box,hand).consumesAction()&&c.health().state()==HeartState.DORMANT,"Generic offhand interaction failed release");h.succeed();
    }
}
