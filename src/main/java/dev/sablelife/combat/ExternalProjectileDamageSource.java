package dev.sablelife.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

/** A native damage source with the external projectile restored as the direct entity. */
public final class ExternalProjectileDamageSource extends DamageSource {
    public interface ImpactBonus {
        float sablelife$impactBonus();
    }

    public ExternalProjectileDamageSource(DamageSource original, Entity projectile) {
        super(original.typeHolder(), projectile, original.getEntity());
    }

    static boolean identifiesProjectile(DamageSource source) {
        return source instanceof ExternalProjectileDamageSource || source.getDirectEntity() instanceof Projectile;
    }

    public static float withImpactBonus(Entity projectile, float damage) {
        return projectile instanceof ImpactBonus bonus ? damage + bonus.sablelife$impactBonus() : damage;
    }
}
