package dev.sablelife.combat;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartHealth;
import dev.sablelife.heart.HeartState;
import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.common.util.TriState;

import java.util.Map;
import java.util.WeakHashMap;

/** Damage is resolved only on the server, from an actual block hit to the currently connected core. */
public final class BodyCombat {
    private static final String ARROW_HITS = "SableLifeHitCreatures";
    // Weak player keys avoid retaining disconnected players or old integrated servers.
    private static final Map<ServerPlayer, Long> LAST_MELEE_TICK = new WeakHashMap<>();

    private BodyCombat() {}

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.isCanceled() || event.getEntity().isSpectator()) return;
        if (event.getEntity().isCreative() && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof dev.sablelife.heart.HeartBlock) {
            if (!event.getEntity().isShiftKeyDown()) event.setCanceled(true);
            return;
        }
        if (isMiningTool(event.getItemStack())) return;
        var heart = SableBodyProbe.findActivatedHeart(event.getLevel(), event.getPos());
        if (heart == null) return;

        // Cancel on both sides: a punch/sword hit should not instantly erase a body block in creative.
        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;

        player.connection.send(new ClientboundBlockUpdatePacket(event.getPos(), event.getLevel().getBlockState(event.getPos())));
        if (heart.health().state() != HeartState.ALIVE) return;
        BlockHitResult hit = validateMeleeHit(player, event.getPos());
        if (hit == null) return;

        long now = player.level().getGameTime();
        Long previous = LAST_MELEE_TICK.get(player);
        if (previous != null && now >= previous && now - previous < 4) return;
        float strength = player.getAttackStrengthScale(0.5F);
        float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float enchantedDamage = EnchantmentHelper.modifyDamage((ServerLevel) player.level(), player.getWeaponItem(), player,
                player.level().damageSources().playerAttack(player), baseDamage);
        float enchantmentBonus = enchantedDamage - baseDamage;
        float damage = baseDamage * (0.2F + strength * strength * 0.8F);
        boolean critical = isCriticalHit(player, strength);
        if (critical) damage *= 1.5F;
        damage += enchantmentBonus * strength;
        if (!Float.isFinite(damage) || damage <= 0) return;
        float before = heart.health().current();
        if (!heart.damageAt(hit.getBlockPos(), Math.min(damage, HeartHealth.MAX_CONFIGURED_HEALTH))) return;
        LAST_MELEE_TICK.put(player, now);
        player.resetAttackStrengthTicker();
        feedback((ServerLevel) player.level(), hit, heart, before - heart.health().current(), player, critical);
    }

    private static boolean isMiningTool(ItemStack stack) {
        // Keep deliberate tool mining available; the ownership/no-drop subsystem is a separate milestone.
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES) || stack.is(ItemTags.AXES);
    }

    /** Mirrors vanilla's full-strength airborne attack conditions for a body block target. */
    private static boolean isCriticalHit(ServerPlayer player, float strength) {
        return strength > 0.9F
                && player.fallDistance > 0.0F
                && !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                && !player.isPassenger()
                && !player.isSprinting();
    }

    /** Sable's placement bypass is author-only; survival players cannot grow a living body. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getEntity().isSpectator()
                || event.getEntity().isCreative() && event.getEntity().hasInfiniteMaterials()
                || !(event.getItemStack().getItem() instanceof BlockItem)) return;
        var hearts = SableBodyProbe.findConnectedHearts(event.getLevel(), event.getPos());
        if (BodyOwnership.protects(event.getLevel(), event.getPos())
                || hearts.size() == 1 && hearts.getFirst().health().state() != HeartState.DORMANT) {
            event.setUseItem(TriState.FALSE);
            event.setCancellationResult(net.minecraft.world.InteractionResult.PASS);
            event.setCanceled(true);
        }
    }

    private static BlockHitResult validateMeleeHit(ServerPlayer player, BlockPos expectedBlock) {
        if (!player.canInteractWithBlock(expectedBlock, 1.0) || !player.level().mayInteract(player, expectedBlock)) return null;
        Vec3 eye = SableBodyProbe.toWorld(player.level(), player.getEyePosition());
        Vec3 center = SableBodyProbe.toWorld(player.level(), Vec3.atCenterOf(expectedBlock));
        var ray = player.level().clip(new ClipContext(eye, center, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (ray.getType() == HitResult.Type.BLOCK && ray.getBlockPos().equals(expectedBlock)) return ray;
        // A non-full block's center may be empty; use the actual sight line as a second shape-aware check.
        var sight = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(player.blockInteractionRange() + 1)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return sight.getType() == HitResult.Type.BLOCK && sight.getBlockPos().equals(expectedBlock) ? sight : null;
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if(!event.isCanceled()&&!(event.getProjectile() instanceof AbstractArrow)&&event.getRayTraceResult() instanceof BlockHitResult external){BodyDamageBridge.projectile(event.getProjectile(),external);return;}
        if (event.isCanceled() || !(event.getProjectile() instanceof AbstractArrow arrow)
                || !(arrow.level() instanceof ServerLevel level) || arrow.isRemoved()
                || !(event.getRayTraceResult() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        var heart = SableBodyProbe.findActivatedHeart(level, hit.getBlockPos());
        if (heart == null || heart.health().state() != HeartState.ALIVE) return;
        var attribution = arrow.getPersistentData();
        if (attribution.hasUUID("SableLifeAttackOwner") && !heart.acceptsLifeAttack(attribution.getUUID("SableLifeAttackOwner"))) return;
        CompoundTag hits = arrow.getPersistentData().getCompound(ARROW_HITS);
        String creature = heart.creatureId().toString();
        // Persist on the arrow so a stuck/reloaded/released arrow cannot damage the same creature again.
        if (hits.getBoolean(creature) || hits.getAllKeys().size() >= 64) return;
        // Vanilla arrows scale with their flight speed; a thrown trident's entity damage is a fixed 8 HP.
        double rawDamage = arrow instanceof ThrownTrident
                ? 8.0
                : arrow.getDeltaMovement().length() * arrow.getBaseDamage();
        if (!Double.isFinite(rawDamage) || rawDamage <= 0) return;
        int damage = Mth.ceil(Math.min(rawDamage, HeartHealth.MAX_CONFIGURED_HEALTH));
        if (arrow.isCritArrow()) damage += arrow.getRandom().nextInt(damage / 2 + 2);
        float before = heart.health().current();
        if (!heart.damageAt(hit.getBlockPos(), Math.min(damage, HeartHealth.MAX_CONFIGURED_HEALTH))) return;
        hits.putBoolean(creature, true);
        arrow.getPersistentData().put(ARROW_HITS, hits);
        feedback(level, hit, heart, before - heart.health().current(), arrow.getOwner() instanceof Player player ? player : null, false);
        // Leave the impact uncancelled so Sable can embed the arrow and apply its physical impulse normally.
    }

    static void feedback(ServerLevel level, BlockHitResult hit, HeartBlockEntity heart, float damage, Player attacker, boolean critical) {
        Vec3 worldHit = SableBodyProbe.toWorld(level, hit.getLocation());
        dev.sablelife.module.ModuleParticles.send(level, ParticleTypes.DAMAGE_INDICATOR, worldHit.x, worldHit.y, worldHit.z, 4, 0.15, 0.15, 0.15, 0.05);
        if (critical) {
            dev.sablelife.module.ModuleParticles.send(level, ParticleTypes.CRIT, worldHit.x, worldHit.y, worldHit.z, 8, 0.25, 0.25, 0.25, 0.15);
        }
        level.playSound(null, worldHit.x, worldHit.y, worldHit.z,
                critical ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.BLOCKS, 0.7F, critical ? 1.0F : 0.8F);
        if (attacker != null) {
            attacker.displayClientMessage(Component.translatable("message.sablelife.hit", rounded(damage),
                    rounded(heart.health().current()), rounded(heart.health().maximum())), true);
        }
    }

    private static String rounded(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}

