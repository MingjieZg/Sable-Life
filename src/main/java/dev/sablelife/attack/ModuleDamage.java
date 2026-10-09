package dev.sablelife.attack;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Module attacks apply their own cadence rather than inheriting vanilla hurt immunity. */
public final class ModuleDamage {
    /** Physical module hits use ordinary armor/protection rules, unlike vanilla generic/magic. */
    public static DamageSource physical(net.minecraft.world.level.Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK));
    }
    /** Boss-authored blast damage stays configured even in Peaceful; armor and blast protection still apply. */
    public static DamageSource blast(net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity projectile) {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sablelife", "module_blast"));
        return new DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key), projectile, null);
    }
    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        int previous = target.invulnerableTime;
        target.invulnerableTime = 0;
        try {
            return target.hurt(source, amount);
        } finally {
            if (target.isAlive()) target.invulnerableTime = Math.max(previous, target.invulnerableTime);
        }
    }
    public static final String ATTACK_NAME="SableLifeAttackName";
    /** Keep native damage type, entities and source position; customize only death attribution. */
    public static DamageSource named(DamageSource source, dev.sablelife.heart.HeartBlockEntity heart){return named(source,heart.creatureName());}
    public static DamageSource named(DamageSource source,String raw){
        var killer=dev.sablelife.heart.CreatureNames.killer(raw);
        return new DamageSource(source.typeHolder(),source.getDirectEntity(),source.getEntity(),source.sourcePositionRaw()){
            @Override public net.minecraft.network.chat.Component getLocalizedDeathMessage(LivingEntity victim){return deathMessage(victim,killer);}
        };
    }
    public static net.minecraft.network.chat.Component deathMessage(LivingEntity victim,net.minecraft.network.chat.Component killer){
        return net.minecraft.network.chat.Component.translatable("death.attack.sablelife.creature",victim.getDisplayName(),killer.copy());
    }
    public static void nameProjectile(net.minecraft.world.entity.Entity projectile,dev.sablelife.heart.HeartBlockEntity heart){projectile.getPersistentData().putString(ATTACK_NAME,heart.creatureName());}
    private ModuleDamage() {}
}
