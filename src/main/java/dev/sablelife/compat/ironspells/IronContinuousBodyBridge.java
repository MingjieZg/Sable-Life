package dev.sablelife.compat.ironspells;

import dev.sablelife.api.BodyCombatApi;
import dev.sablelife.attack.AttackVolume;
import dev.sablelife.combat.BodySphereQuery;
import io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile;
import io.redspace.ironsspellbooks.entity.spells.blood_slash.BloodSlashProjectile;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import org.joml.Quaterniond;
import java.util.*;

/** Native attack windows, never an unconditional per-tick contact damage loop. */
public final class IronContinuousBodyBridge {
    private static final Map<Entity, Map<UUID, Long>> WINDOWS = new WeakHashMap<>();
    private static boolean window(Entity source, UUID victim) {
        var now = source.level().getGameTime();
        var times = WINDOWS.computeIfAbsent(source, ignored -> new HashMap<>());
        times.entrySet().removeIf(e -> e.getValue() != now);
        if (times.containsKey(victim) || times.size() >= 64) return false;
        times.put(victim, now); return true;
    }
    private static AttackVolume volume(AABB box) {
        return AttackVolume.box(box.getCenter(), new Vec3(box.getXsize(), box.getYsize(), box.getZsize()).scale(.5), new Quaterniond());
    }
    public static boolean bloodSlash(BloodSlashProjectile shot) {
        if (!(shot.level() instanceof ServerLevel level) || shot.isRemoved() || !Float.isFinite(shot.getDamage()) || shot.getDamage() <= 0) return false;
        var hits = BodySphereQuery.queryVolumes(level, shot.position(), List.of(volume(shot.getBoundingBox())));
        if (hits.isEmpty()) return false;
        var hit = hits.getFirst();
        bloodDamage(shot, new BlockHitResult(hit.point(), Direction.UP, hit.block(), false));
        shot.discard(); return true; // immunity is still a physical body contact
    }
    public static void bloodBlock(BloodSlashProjectile shot, BlockHitResult hit) {
        var core = dev.sablelife.compat.sable.SableBodyProbe.findActivatedHeart(shot.level(), hit.getBlockPos());
        if (core != null && !core.isControlCore() && core.health().state() == dev.sablelife.heart.HeartState.ALIVE)
            bloodDamage(shot, hit);
    }
    private static void bloodDamage(BloodSlashProjectile shot, BlockHitResult hit) {
        var core = dev.sablelife.compat.sable.SableBodyProbe.findActivatedHeart(shot.level(), hit.getBlockPos());
        if (core == null) return;
        float before = core.health().current();
        if (BodyCombatApi.projectile(shot, hit, shot.getDamage()) && shot.getOwner() instanceof LivingEntity owner && owner.isAlive()) {
            float rate = io.redspace.ironsspellbooks.api.registry.SpellRegistry.BLOOD_SLASH_SPELL.get().getDamageSource(shot, owner).getLifestealPercent();
            if (Float.isFinite(rate) && rate > 0) owner.heal(Math.max(0, before - core.health().current()) * rate);
        }
    }
    public static void cone(AbstractConeProjectile shot, float damage) {
        if (!(shot.level() instanceof ServerLevel level) || shot.isRemoved() || !(shot.getOwner() instanceof LivingEntity owner) || !owner.isAlive() || !Float.isFinite(damage) || damage <= 0) return;
        if (!(shot instanceof io.redspace.ironsspellbooks.entity.spells.fire_breath.FireBreathProjectile || shot instanceof io.redspace.ironsspellbooks.entity.spells.cone_of_cold.ConeOfColdProjectile || shot instanceof io.redspace.ironsspellbooks.entity.spells.poison_breath.PoisonBreathProjectile)) return;
        var volumes = Arrays.stream(shot.getParts()).map(p -> volume(p.getBoundingBox())).toList();
        for (var hit : BodySphereQuery.queryVolumes(level, owner.getEyePosition(), volumes))
            if (window(shot, hit.core().creatureId())) BodyCombatApi.contact(shot, new BlockHitResult(hit.point(), Direction.UP, hit.block(), false), damage);
    }
    public static HitResult siphon(HitResult hit, LivingEntity caster, float damage) {
        if (!(caster.level() instanceof ServerLevel) || !caster.isAlive() || !(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return hit;
        var core = dev.sablelife.compat.sable.SableBodyProbe.findActivatedHeart(caster.level(), block.getBlockPos());
        if (core == null || core.isControlCore() || !window(caster, core.creatureId())) return hit;
        float actual = BodyCombatApi.contact(caster, block, damage);
        if (actual > 0) {
            caster.heal(actual); // native spell lifesteal is 100% of actual damage, not configured damage
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster,
                    new io.redspace.ironsspellbooks.network.particles.BloodSiphonParticlesPacket(block.getLocation(), caster.position().add(0, caster.getBbHeight()/2, 0)));
        }
        return hit;
    }
    private IronContinuousBodyBridge() {}
}
