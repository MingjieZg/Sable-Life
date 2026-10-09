package dev.sablelife.mixin;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Explosion.class)
public interface ExplosionDamageAccess {@Accessor("damageCalculator") ExplosionDamageCalculator sablelife$calculator();}
