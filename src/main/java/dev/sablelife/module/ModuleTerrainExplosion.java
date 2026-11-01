package dev.sablelife.module;

import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;

/** Identity for terrain-only blasts that have no projectile entity. Damage is applied by the module. */
public final class ModuleTerrainExplosion extends ExplosionDamageCalculator {
    private final UUID owner;
    public ModuleTerrainExplosion(UUID owner) { this.owner=owner; }
    public UUID owner() { return owner; }
    @Override public boolean shouldDamageEntity(Explosion explosion,Entity entity) { return false; }
    @Override public float getKnockbackMultiplier(Entity entity) { return 0; }
}
