package dev.sablelife.mixin;
import dev.sablelife.compat.ironspells.IronSpellsBodyBridge;
import dev.sablelife.combat.BodyExplosionDamage;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.fire_arrow.FireArrowProjectile;
import io.redspace.ironsspellbooks.entity.spells.fireball.MagicFireball;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;
@Mixin(value={MagicFireball.class,FireArrowProjectile.class},remap=false)
public abstract class IronSpellBlastMixin {
 @ModifyVariable(method="onHit",at=@At("HEAD"),argsOnly=true)
 private HitResult sablelife$worldImpact(HitResult hit){return IronSpellsBodyBridge.worldHit((AbstractMagicProjectile)(Object)this,hit);}
 @Inject(method="onHit",at=@At("HEAD"))
 private void sablelife$spellBlast(HitResult hit,CallbackInfo ci){IronSpellsBodyBridge.blast((AbstractMagicProjectile)(Object)this,hit);}
 @Inject(method="onHit",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Explosion;explode()V",remap=true))
 private void sablelife$terrainOnly(HitResult hit,CallbackInfo ci,@Local Explosion explosion){BodyExplosionDamage.terrainOnly(explosion);}
}
