package dev.sablelife.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.sablelife.compat.ironspells.IronSpellsBodyBridge;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** These helpers return a world-space position; the storage BlockPos identity stays intact. */
@Mixin(value=Utils.class,remap=false)
public abstract class IronUtilsWorldRayMixin {
 @ModifyExpressionValue(method="raycastForBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",remap=true))
 private static BlockHitResult sablelife$worldRay(BlockHitResult hit,Level level,Vec3 start,Vec3 end,ClipContext.Fluid fluid){return IronSpellsBodyBridge.worldBlockHit(level,hit);}
 @ModifyExpressionValue(method="moveToRelativeGroundLevel(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;II)Lnet/minecraft/world/phys/Vec3;",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",remap=true))
 private static BlockHitResult sablelife$worldGround(BlockHitResult hit,Level level,Vec3 start,int up,int down){return IronSpellsBodyBridge.worldBlockHit(level,hit);}
}
