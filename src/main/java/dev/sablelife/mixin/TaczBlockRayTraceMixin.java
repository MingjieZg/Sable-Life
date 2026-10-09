package dev.sablelife.mixin;
import dev.sablelife.combat.BodyProjectileClip;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo @Mixin(targets="com.tacz.guns.util.block.BlockRayTrace",remap=false)
public abstract class TaczBlockRayTraceMixin {
    @Inject(method="rayTraceBlocks(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",at=@At("RETURN"),cancellable=true)
    private static void sablelife$includeMovingBlocks(Level level,ClipContext context,CallbackInfoReturnable<BlockHitResult> cir){cir.setReturnValue(BodyProjectileClip.merge(level,context,cir.getReturnValue()));}
}
