package dev.sablelife.mixin;
import dev.sablelife.compat.ironspells.IronSpellsBodyBridge;
import io.redspace.ironsspellbooks.spells.lightning.ShockwaveSpell;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ShockwaveSpell.class,remap=false)
public abstract class IronShockwaveMixin {
 @Inject(method="onCast",at=@At("HEAD"))
 private void sablelife$bodyTargets(Level level,int spellLevel,LivingEntity caster,CastSource source,MagicData data,CallbackInfo ci){IronSpellsBodyBridge.shockwave((ShockwaveSpell)(Object)this,level,spellLevel,caster);}
}
