package dev.sablelife.mixin;

import dev.sablelife.compat.epicfight.EpicFightBodyBridge;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo @Mixin(targets="yesman.epicfight.api.collider.OBBCollider",remap=false)
public abstract class EpicFightColliderMixin {
    @Shadow(remap=false) protected Vec3[] rotatedVertices;
    @Inject(method="transform",at=@At("TAIL"))
    private void sablelife$sample(@Coerce Object matrix,CallbackInfo ci) {EpicFightBodyBridge.sample(((EpicFightColliderOriginAccess)this).sablelife$worldCenter(),rotatedVertices);}
}
