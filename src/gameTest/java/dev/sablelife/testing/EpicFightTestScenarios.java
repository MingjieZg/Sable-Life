package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.attack.AttackVolume;
import dev.sablelife.compat.epicfight.*;
import dev.sablelife.heart.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import yesman.epicfight.api.animation.types.*;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import java.util.*;

public final class EpicFightTestScenarios {
    public static void NativeSwordAnimationHitsBodyOnlyOnceAndNextPhaseCanHitAgain(GameTestHelper h)throws Exception {
        nativeWeapon(h,Items.DIAMOND_SWORD,Animations.SWORD_AUTO1);
    }
    public static void NativeAxeAnimationHitsBodyWithPhaseLimits(GameTestHelper h)throws Exception {
        nativeWeapon(h,Items.DIAMOND_AXE,Animations.AXE_AUTO1);
    }
    public static void NativeTridentAnimationHitsBodyWithPhaseLimits(GameTestHelper h)throws Exception {
        nativeWeapon(h,Items.TRIDENT,Animations.SPEAR_TWOHAND_AUTO1);
    }
    public static void LivingTargetsAndLifeBodiesShareTheNativeStrikeLimit(GameTestHelper h)throws Exception {
        nativeWeapon(h,Items.DIAMOND_SWORD,Animations.SWORD_AUTO1,true);
    }
    private static final class GuardPlayer extends net.neoforged.neoforge.common.util.FakePlayer {
        GuardPlayer(net.minecraft.server.level.ServerLevel level){super(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"GuardTester"));
            try{var f=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");f.setAccessible(true);f.setInt(this,0);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
        @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}
    }
    public static void ActualMeleeModuleBlocksWithoutEveryTickStaminaDrain(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,40,3));var blocks=new ArrayList<BlockPos>();
        for(int i=0;i<4;i++){var p=pos.east(i);blocks.add(p);level.setBlockAndUpdate(p,i==0?SableLife.HEART.get().defaultBlockState():Blocks.IRON_BLOCK.defaultBlockState());}
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,blocks,new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+3,pos.getY(),pos.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());var module=core.modules().add("melee");
        module.anchors(List.of(dev.sablelife.module.ModuleAnchor.create(core,core.getBlockPos().east(),Direction.EAST),dev.sablelife.module.ModuleAnchor.create(core,core.getBlockPos().east(3),Direction.EAST)));
        module.edit("interval",4,"");module.edit("knockback",0,"");core.tryActivate(TestActors.creative(level));
        var victim=new GuardPlayer(level);victim.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);victim.tickCount=100;
        var center=dev.sablelife.module.MeleeGeometry.frame(core,module).center();victim.setPos(center.add(0,-.4,0));victim.setYRot(90);victim.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));
        level.addNewPlayer(victim);var patch=EpicFightCapabilities.getServerPlayerPatch(victim);patch.toEpicFightMode(false);
        var cap=EpicFightCapabilities.getItemStackCapability(victim.getMainHandItem());patch.updateHeldItem(EpicFightCapabilities.getItemStackCapability(ItemStack.EMPTY),cap,ItemStack.EMPTY,victim.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);
        var guard=yesman.epicfight.registry.entries.EpicFightSkills.GUARD.get();patch.getSkill(yesman.epicfight.skill.SkillSlots.GUARD).setSkill(guard,true);patch.setStamina(patch.getMaxStamina());patch.startSkillHolding(guard);
        var runtime=new dev.sablelife.module.ModuleCombat();float before=patch.getStamina();runtime.tick(core);float after=patch.getStamina();
        h.assertTrue(victim.getHealth()==20&&after<before,"Actual module failed to guard");runtime.tick(core);
        h.assertTrue(patch.getStamina()==after,"Same tick drained stamina again");
        h.runAfterDelay(1,()->{runtime.tick(core);h.assertTrue(patch.getStamina()==after&&victim.getHealth()==20,"Blocked contact ignored interval");
            module.edit("enabled",0,"");patch.resetHolding();level.removePlayerImmediately(victim,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);h.succeed();});
    }
    public static void PositionBasedMeleeUsesNativeGuardParryAndRearRules(GameTestHelper h) {
        var player=new GuardPlayer(h.getLevel());player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(h.absolutePos(new BlockPos(5,40,5)).getCenter());player.setYRot(0);player.tickCount=100;
        var item=new ItemStack(Items.DIAMOND_SWORD);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,item);
        var patch=EpicFightCapabilities.getServerPlayerPatch(player);patch.toEpicFightMode(false);
        var cap=EpicFightCapabilities.getItemStackCapability(item);
        patch.updateHeldItem(EpicFightCapabilities.getItemStackCapability(ItemStack.EMPTY),cap,ItemStack.EMPTY,item,net.minecraft.world.InteractionHand.MAIN_HAND);
        var slot=patch.getSkill(yesman.epicfight.skill.SkillSlots.GUARD);
        for(var guard:List.of(yesman.epicfight.registry.entries.EpicFightSkills.GUARD.get(),yesman.epicfight.registry.entries.EpicFightSkills.PARRYING.get(),yesman.epicfight.registry.entries.EpicFightSkills.IMPACT_GUARD.get())) {
            patch.resetHolding();slot.setSkill(guard,true);patch.setStamina(patch.getMaxStamina());
            h.assertTrue(patch.startSkillHolding(guard),"Failed to hold native guard");
            if(guard instanceof yesman.epicfight.skill.guard.ParryingSkill)slot.getDataManager().setData(yesman.epicfight.registry.entries.EpicFightSkillDataKeys.LAST_ACTIVE,player.tickCount);
            var origin=player.position().add(0,0,2);var source=new dev.sablelife.attack.BodyMeleeSource(h.getLevel(),origin,"Machine");
            var event=new yesman.epicfight.api.event.types.entity.TakeDamageEvent.Income(patch,source,6);
            float stamina=patch.getStamina();
            yesman.epicfight.api.event.EpicFightEventHooks.Entity.TAKE_DAMAGE_INCOME.postWithListener(event,patch.getEventListener());
            h.assertTrue(event.isCanceled()&&source.wasBlocked()&&event.getResult()==yesman.epicfight.api.utils.AttackResult.ResultType.BLOCKED,"Native guard failed against position-only melee: "+guard.getClass().getSimpleName());
            h.assertTrue(patch.getStamina()<stamina,"Guard did not consume native stamina");
            if(guard instanceof yesman.epicfight.skill.guard.ParryingSkill)h.assertTrue(event.isParried(),"Native parry window ignored");
            var rear=new dev.sablelife.attack.BodyMeleeSource(h.getLevel(),player.position().add(0,0,-2),"Machine");
            var rearEvent=new yesman.epicfight.api.event.types.entity.TakeDamageEvent.Income(patch,rear,6);
            yesman.epicfight.api.event.EpicFightEventHooks.Entity.TAKE_DAMAGE_INCOME.postWithListener(rearEvent,patch.getEventListener());
            h.assertTrue(!rearEvent.isCanceled()&&!rear.wasBlocked(),"Guard blocked a rear attack");
            float hp=player.getHealth();var actual=new dev.sablelife.attack.BodyMeleeSource(h.getLevel(),origin,"Machine");
            dev.sablelife.attack.ModuleDamage.hurt(player,actual,6);
            h.assertTrue(player.getHealth()==hp&&actual.wasBlocked(),"Actual hurt pipeline bypassed guard: HP="+player.getHealth()+" original="+hp+" blocked="+actual.wasBlocked()+" holding="+patch.isHoldingAny());
            patch.resetHolding();var off=new dev.sablelife.attack.BodyMeleeSource(h.getLevel(),origin,"Machine");
            var offEvent=new yesman.epicfight.api.event.types.entity.TakeDamageEvent.Income(patch,off,6);
            yesman.epicfight.api.event.EpicFightEventHooks.Entity.TAKE_DAMAGE_INCOME.postWithListener(offEvent,patch.getEventListener());
            h.assertTrue(!offEvent.isCanceled(),"Released guard remained active");
        }
        h.succeed();
    }
    public static void NativeChargingRulesMatchRealPostHitAndRespectGuards(GameTestHelper h) {
        var player=TestActors.creative(h.getLevel());var item=new ItemStack(Items.DIAMOND_SWORD);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,item);
        var patch=EpicFightCapabilities.getServerPlayerPatch(player);patch.toEpicFightMode(false);
        var skill=patch.getSkill(yesman.epicfight.skill.SkillSlots.WEAPON_INNATE);
        var innate=EpicFightCapabilities.getItemStackCapability(item).getInnateSkill(patch,item);
        h.assertTrue(innate!=null,"Native sword skill missing");skill.setSkill(innate,true);skill.setMaxResource(1000);
        var source=patch.getDamageSource(Animations.SWORD_AUTO1,net.minecraft.world.InteractionHand.MAIN_HAND);source.setChargeWeapon(true);
        var target=net.minecraft.world.entity.EntityType.PIG.create(h.getLevel());
        skill.setResource(3);
        yesman.epicfight.api.event.EpicFightEventHooks.Entity.DELIVER_DAMAGE_POST.postWithListener(
            new yesman.epicfight.api.event.types.entity.DealDamageEvent.Post(patch,target,source,7),patch.getEventListener());
        float nativeValue=skill.getResource();h.assertTrue(nativeValue==10,"Native reference event did not add damage");
        skill.setResource(3);EpicFightBodyBridge.chargeWeapon(patch,source,7);
        h.assertTrue(skill.getResource()==nativeValue,"Bridge differs from native post-hit resource");
        source.setChargeWeapon(false);EpicFightBodyBridge.chargeWeapon(patch,source,7);
        h.assertTrue(skill.getResource()==nativeValue,"Noncharging attack gained resource");source.setChargeWeapon(true);
        EpicFightBodyBridge.chargeWeapon(patch,source,0);EpicFightBodyBridge.chargeWeapon(patch,source,Float.NaN);
        h.assertTrue(skill.getResource()==nativeValue,"Invalid/zero damage gained resource");
        skill.setDuration(1);h.assertTrue(skill.isActivated(),"Active skill fixture failed");EpicFightBodyBridge.chargeWeapon(patch,source,7);
        h.assertTrue(skill.getResource()==nativeValue,"Activated skill charged");skill.deactivate();skill.setStack(Integer.MAX_VALUE);
        h.assertTrue(skill.isFull(),"Full skill fixture failed");EpicFightBodyBridge.chargeWeapon(patch,source,7);
        h.assertTrue(skill.getResource()==nativeValue,"Full skill charged");
        skill.setStack(0);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_AXE));
        EpicFightBodyBridge.chargeWeapon(patch,source,7);h.assertTrue(skill.getResource()==nativeValue,"Mismatched weapon skill charged");
        h.succeed();
    }
    private static void nativeWeapon(GameTestHelper h,Item weapon,Object rawAccessor)throws Exception {nativeWeapon(h,weapon,rawAccessor,false);}
    private static void nativeWeapon(GameTestHelper h,Item weapon,Object rawAccessor,boolean livingTarget)throws Exception {
        h.assertTrue(EpicFightCompatibility.supported(),"Verified Epic Fight release was not loaded");
        var player=TestActors.creative(h.getLevel());player.setPos(h.absolutePos(new BlockPos(6,40,6)).getCenter());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(weapon));player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
        var patch=EpicFightCapabilities.getServerPlayerPatch(player);h.assertTrue(patch!=null,"No native Epic Fight player patch");
        var accessor=(yesman.epicfight.api.animation.AnimationManager.AnimationAccessor<? extends AttackAnimation>)rawAccessor;
        patch.toEpicFightMode(false);patch.getAnimator().playAnimationInstantly(accessor);patch.preTick();
        var animation=accessor.get();var phase=animation.phases[0];
        var innate=EpicFightCapabilities.getItemStackCapability(player.getMainHandItem()).getInnateSkill(patch,player.getMainHandItem());
        var skill=patch.getSkill(yesman.epicfight.skill.SkillSlots.WEAPON_INNATE);
        if(!livingTarget) {
            h.assertTrue(innate!=null,"Native weapon has no innate skill");skill.setSkill(innate,true);
            skill.setMaxResource(1000);skill.setResource(0);
        }
        float from=phase.preDelay,to=phase.contact;
        var frame=EpicFightBodyBridge.begin(patch,animation,phase);List<AttackVolume> samples;
        try {
            phase.getCollidingEntities(patch,animation,from,to,animation.getPlaySpeed(patch,animation));
            var field=frame.getClass().getDeclaredField("samples");field.setAccessible(true);samples=List.copyOf((List<AttackVolume>)field.get(frame));
        } finally {EpicFightBodyBridge.close(frame);}
        h.assertTrue(!samples.isEmpty(),"Real animation did not capture native collider samples");
        var pos=BlockPos.containing(samples.get(samples.size()/2).center());var level=h.getLevel();
        level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.configure(1000,60);core.tryActivate(player);
        h.runAfterDelay(4,()->{
        var access=(dev.sablelife.mixin.EpicFightAttackAccess)animation;
        var before=core.health().current();patch.removeHurtEntities();
        var click=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(player,core.getBlockPos().east(),Direction.UP,net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START);
        dev.sablelife.combat.BodyCombat.onLeftClickBlock(click);
        h.assertTrue(core.health().current()==before,"Epic combat mode also applied the vanilla block-hit damage");
        long entityCount=java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).count();
        var eye=dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,player.getEyePosition());
        var direct=dev.sablelife.combat.BodyAttackVolumes.query(level,eye,samples);
        var center=dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,core.getBlockPos().getCenter());
        var cube=AttackVolume.box(center,new net.minecraft.world.phys.Vec3(.5,.5,.5),new org.joml.Quaterniond());
        h.assertTrue(!direct.isEmpty(),"Native samples fixture: chosen="+samples.get(samples.size()/2).center()+" body="+center+" alive="+core.health().state()+" candidates="+dev.sablelife.ownership.LoadedCoreIndex.query(level,samples.get(samples.size()/2).bounds()).size()+" overlaps="+samples.stream().anyMatch(v->v.intersects(cube))+" visible="+dev.sablelife.sense.SensorGeometry.visible(level,null,eye,center,core.creatureId()));
        if(livingTarget) {
            var pig=net.minecraft.world.entity.EntityType.PIG.create(level);var contact=samples.get(samples.size()/2).center();
            var point=contact.add(eye.subtract(contact).normalize().scale(.8));pig.setPos(point.x,point.y-.45,point.z);pig.setNoAi(true);pig.setNoGravity(true);level.addFreshEntity(pig);
            access.sablelife$hurtColliding(patch,from,to,animation.getState(patch,from),animation.getState(patch,to),phase);
            h.assertTrue(pig.getHealth()<pig.getMaxHealth(),"Native living target was not hit in shared-limit fixture");
            h.assertTrue(core.health().current()==before,"One-strike phase hit both the living target and life body");h.succeed();return;
        }
        access.sablelife$hurtColliding(patch,from,to,animation.getState(patch,from),animation.getState(patch,to),phase);
        h.assertTrue(core.health().current()<before,"Native sword failed to hurt the owned body");var after=core.health().current();
        h.assertTrue(Math.abs(skill.getResource()-(before-after))<.001,"Native animation hit failed to charge weapon by damage: "+skill.getResource());
        float charged=skill.getResource();
        access.sablelife$hurtColliding(patch,from,to,animation.getState(patch,from),animation.getState(patch,to),phase);
        h.assertTrue(core.health().current()==after,"One phase hit multiple voxels/ticks repeatedly");
        h.assertTrue(skill.getResource()==charged,"Repeated phase charged the weapon again");
        patch.removeHurtEntities();access.sablelife$hurtColliding(patch,from,to,animation.getState(patch,from),animation.getState(patch,to),phase);
        h.assertTrue(core.health().current()<after,"Native phase reset did not permit the next strike");
        var oldModifier=phase.getProperty(yesman.epicfight.api.animation.property.AnimationProperty.AttackPhaseProperty.DAMAGE_MODIFIER);
        try {
            phase.addProperty(yesman.epicfight.api.animation.property.AnimationProperty.AttackPhaseProperty.DAMAGE_MODIFIER,yesman.epicfight.api.utils.math.ValueModifier.multiplier(2));
            patch.removeHurtEntities();float previous=core.health().current();
            access.sablelife$hurtColliding(patch,from,to,animation.getState(patch,from),animation.getState(patch,to),phase);
            h.assertTrue(Math.abs(previous-core.health().current()-20)<.001,"Native phase multiplier was not applied");
        } finally {
            phase.removeProperty(yesman.epicfight.api.animation.property.AnimationProperty.AttackPhaseProperty.DAMAGE_MODIFIER);
            oldModifier.ifPresent(v->phase.addProperty(yesman.epicfight.api.animation.property.AnimationProperty.AttackPhaseProperty.DAMAGE_MODIFIER,v));
        }
        var armor=core.modules().add("armor");armor.anchors(List.of(dev.sablelife.module.ModuleAnchor.create(core,core.getBlockPos(),Direction.UP)));
        armor.edit("damageModifier",-50,"");patch.removeHurtEntities();float hp=core.health().current(),resource=skill.getResource();
        access.sablelife$hurtColliding(patch,from,to,animation.getState(patch,from),animation.getState(patch,to),phase);
        h.assertTrue(Math.abs(hp-core.health().current()-5)<.001&&Math.abs(skill.getResource()-resource-5)<.001,"Reduced damage charged raw damage");
        armor.edit("damageModifier",-100,"");patch.removeHurtEntities();hp=core.health().current();resource=skill.getResource();
        access.sablelife$hurtColliding(patch,from,to,animation.getState(patch,from),animation.getState(patch,to),phase);
        h.assertTrue(core.health().current()==hp&&skill.getResource()==resource,"Immune body charged weapon");
        h.assertTrue(java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).count()==entityCount,"Bridge spawned proxy entities");
        h.succeed();
        });
    }
}
