package dev.sablelife.mixin;
import dev.sablelife.heart.LevelRewardOrb;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Direct-level rewards must never merge with another reward or vanilla XP. */
@Mixin(ExperienceOrb.class)
public abstract class LevelRewardOrbMixin {
 @Inject(method="canMerge(Lnet/minecraft/world/entity/ExperienceOrb;)Z",at=@At("HEAD"),cancellable=true)
 private void sablelife$separateLevels(ExperienceOrb other,CallbackInfoReturnable<Boolean> cir){if((Object)this instanceof LevelRewardOrb||other instanceof LevelRewardOrb)cir.setReturnValue(false);}
 @Inject(method="canMerge(Lnet/minecraft/world/entity/ExperienceOrb;II)Z",at=@At("HEAD"),cancellable=true)
 private static void sablelife$separateAward(ExperienceOrb orb,int id,int value,CallbackInfoReturnable<Boolean> cir){if(orb instanceof LevelRewardOrb)cir.setReturnValue(false);}
}
