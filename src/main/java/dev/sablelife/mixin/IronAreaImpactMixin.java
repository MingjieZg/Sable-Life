package dev.sablelife.mixin;
import dev.sablelife.compat.ironspells.*;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.WitherSkullProjectile;
import io.redspace.ironsspellbooks.entity.spells.comet.Comet;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value={WitherSkullProjectile.class,Comet.class,io.redspace.ironsspellbooks.entity.spells.creeper_head.CreeperHeadProjectile.class},remap=false)
public abstract class IronAreaImpactMixin {
 @ModifyVariable(method="onHit",at=@At("HEAD"),argsOnly=true)
 private HitResult sablelife$worldImpact(HitResult hit){return IronSpellsBodyBridge.worldHit((AbstractMagicProjectile)(Object)this,hit);}
 @Inject(method="onHit",at=@At("HEAD"))
 private void sablelife$bodyImpact(HitResult hit,CallbackInfo ci){IronAreaBodyBridge.impact((AbstractMagicProjectile)(Object)this,hit);}
}
