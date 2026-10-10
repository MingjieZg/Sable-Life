package dev.sablelife.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.sablelife.compat.ironspells.IronContinuousBodyBridge;
import io.redspace.ironsspellbooks.spells.blood.RayOfSiphoningSpell;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=RayOfSiphoningSpell.class,remap=false)
public abstract class IronSiphoningMixin {
 @Shadow private float getTickDamage(int spellLevel,LivingEntity caster){throw new AssertionError();}
 @ModifyExpressionValue(method="onCast",at=@At(value="INVOKE",target="Lio/redspace/ironsspellbooks/api/util/RaycastBuilder;build()Lnet/minecraft/world/phys/HitResult;"))
 private HitResult sablelife$bodyRay(HitResult hit,Level level,int spellLevel,LivingEntity caster,CastSource source,MagicData data){return IronContinuousBodyBridge.siphon(hit,caster,getTickDamage(spellLevel,caster));}
}
