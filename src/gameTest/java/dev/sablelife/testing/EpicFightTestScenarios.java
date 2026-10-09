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
