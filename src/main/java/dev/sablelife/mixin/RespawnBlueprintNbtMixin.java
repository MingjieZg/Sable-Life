package dev.sablelife.mixin;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.survival.BlueprintNbtPolicies;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.*;
import dev.sablelife.compat.photomancy.PhotomancyRespawnBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Identity-scoped full NBT for our server-recorded template, never for uploads or other tools. */
@Mixin(value=BlueprintNbtPolicies.class,remap=false)
public abstract class RespawnBlueprintNbtMixin {
 @Inject(method="resolve",at=@At("RETURN"),cancellable=true)
 private static void sablelife$trustedCapture(SableBlueprint blueprint,SableBlueprint.SubLevelData part,SableBlueprint.BlockData block,BlockState state,CompoundTag raw,CallbackInfoReturnable<BlueprintNbtLoadDecision> ci){
  if(raw!=null&&PhotomancyRespawnBridge.trusted(blueprint)&&ci.getReturnValue().mode()!=BlueprintNbtLoadMode.DENY)ci.setReturnValue(new BlueprintNbtLoadDecision(BlueprintNbtLoadMode.FULL,raw.copy()));
 }
}
