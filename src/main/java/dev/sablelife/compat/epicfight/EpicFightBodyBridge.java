package dev.sablelife.compat.epicfight;

import dev.sablelife.attack.AttackVolume;
import dev.sablelife.combat.*;
import dev.sablelife.heart.HeartHealth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.*;
import yesman.epicfight.api.animation.property.AnimationProperty.AttackPhaseProperty;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.utils.math.ValueModifier;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import java.util.*;

/** Native animation samples, independent creature IDs per phase; no proxy Entity is created. */
public final class EpicFightBodyBridge {
    private static final class Swing {final Set<UUID> attempted=new HashSet<>();int hits;}
    public static final class Frame {
        final ServerPlayerPatch patch;final AttackAnimation animation;final AttackAnimation.Phase phase;final Swing swing;
        final List<AttackVolume> samples=new ArrayList<>();final Frame previous;
        Frame(ServerPlayerPatch patch,AttackAnimation animation,AttackAnimation.Phase phase,Frame previous){this.patch=patch;this.animation=animation;this.phase=phase;this.previous=previous;swing=patch==null?null:SWINGS.computeIfAbsent(patch,k->new Swing());}
    }
    private static final Map<Object,Swing> SWINGS=new WeakHashMap<>();
    private static final ThreadLocal<Frame> CURRENT=new ThreadLocal<>();
    public static boolean combatMode(ServerPlayer player){var patch=EpicFightCapabilities.getServerPlayerPatch(player);return patch!=null&&patch.isEpicFightMode();}
    public static Frame begin(Object patch,Object animation,Object phase) {
        if(!(patch instanceof ServerPlayerPatch player)||!player.isEpicFightMode()||player.getOriginal().isSpectator()) {
            if(CURRENT.get()==null)return null;
            var suspended=new Frame(null,null,null,CURRENT.get());CURRENT.set(suspended);return suspended;
        }
        var frame=new Frame(player,(AttackAnimation)animation,(AttackAnimation.Phase)phase,CURRENT.get());CURRENT.set(frame);return frame;
    }
    public static void close(Frame frame){if(frame!=null){if(frame.previous==null)CURRENT.remove();else CURRENT.set(frame.previous);}}
    public static void reset(Object patch){if(patch instanceof ServerPlayerPatch)SWINGS.remove(patch);}
    public static int remaining(Object patch,int nativeMaximum){var frame=CURRENT.get();return frame!=null&&frame.patch==patch?Math.max(0,nativeMaximum-frame.swing.hits):nativeMaximum;}
    /** Epic Fight stores X/Z reflected world coordinates and four centrally symmetric OBB vertices. */
    public static void sample(Vec3 center,Vec3[] vertices) {
        var frame=CURRENT.get();if(frame==null||frame.patch==null||frame.samples.size()>=128||center==null||vertices==null||vertices.length!=4)return;
        for(var vertex:vertices)if(vertex==null||!Double.isFinite(vertex.lengthSqr()))return;
        var x=world(vertices[1].subtract(vertices[2]).scale(.5));var y=world(vertices[0].add(vertices[2]).scale(.5));var z=world(vertices[1].subtract(vertices[0]).scale(.5));
        var volume=new AttackVolume(world(center),x,y,z);if(volume.valid())frame.samples.add(volume);
    }
    private static Vec3 world(Vec3 epic){return new Vec3(-epic.x,epic.y,-epic.z);}
    public static void finish(Frame frame) {
        if(frame==null||frame.patch==null||frame.samples.isEmpty())return;
        var patch=frame.patch;var player=patch.getOriginal();var phase=frame.phase;var hand=phase.effectiveHand(patch);
        int maximum=phase.getProperty(AttackPhaseProperty.MAX_STRIKES_MODIFIER).map(v->(int)ValueModifier.calculator().attach(v).getResult(patch.getMaxStrikes(hand))).orElse(patch.getMaxStrikes(hand));
        int left=Math.min(64,maximum)-patch.getCurrentlyActuallyHitEntities().size()-frame.swing.hits;if(left<=0)return;
        // Use the current weapon attributes and native phase multiplier, with full-strength EF cadence.
        // Target-dependent extra damage, stun/knockback and entity-only events require a real LivingEntity target.
        if(hand!=net.minecraft.world.InteractionHand.MAIN_HAND)return;
        var source=patch.getDamageSource(frame.animation.getAccessor(),hand);
        phase.getProperty(AttackPhaseProperty.DAMAGE_MODIFIER).ifPresent(source::attachDamageModifier);
        float base=(float)player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        base=EnchantmentHelper.modifyDamage(player.serverLevel(),player.getItemInHand(hand),player,source,base);
        float amount=source.calculateDamageAgainst(player,null,patch.getModifiedBaseDamage(base));
        if(!Float.isFinite(amount)||amount<=0)return;amount=Math.min(amount,HeartHealth.MAX_CONFIGURED_HEALTH);
        for(var hit:BodyAttackVolumes.query(player.serverLevel(),dev.sablelife.compat.sable.SableBodyProbe.toWorld(player.level(),player.getEyePosition()),frame.samples)) {
            if(left<=0||frame.swing.attempted.size()>=64)break;
            if(!frame.swing.attempted.add(hit.heart().creatureId()))continue;
            float before=hit.heart().health().current();
            if(BodyDamageBridge.melee(player,hit.heart(),hit.block(),hit.point(),amount)) {
                frame.swing.hits++;left--;patch.setLastAttackSuccess(true);
                chargeWeapon(patch,source,Math.max(0,before-hit.heart().health().current()));
            }
        }
    }
    /** Mirrors ServerPlayerPatch's DELIVER_DAMAGE_POST listener for the pinned API.
     * A body is not a LivingEntity: publishing a fake/null target event breaks third-party listeners.
     * Native skill consumption handles caps, stacks and the owner's client synchronization.
     */
    public static void chargeWeapon(ServerPlayerPatch patch,yesman.epicfight.world.damagesource.EpicFightDamageSource source,float damage) {
        if(!source.shouldChargeWeapon()||!Float.isFinite(damage)||damage<=0)return;
        var container=patch.getSkill(yesman.epicfight.skill.SkillSlots.WEAPON_INNATE);
        var activeItem=patch.getOriginal().getItemInHand(patch.getPrimaryHand());
        if(!container.isFull()&&!container.isActivated()
                &&container.hasSkill(EpicFightCapabilities.getItemStackCapability(activeItem).getInnateSkill(patch,activeItem))) {
            float value=container.getResource()+damage;
            if(value>0)container.getSkill().setConsumptionSynchronize(container,value);
        }
    }
    private EpicFightBodyBridge() {}
}
