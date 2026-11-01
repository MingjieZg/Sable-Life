package dev.sablelife.mixin;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class ArenaBuildBlockEntityTickMixin {
 @Shadow @Final private BlockEntity blockEntity;
 @Inject(method="tick",at=@At("HEAD"),cancellable=true)
 private void sablelife$deferPartialBlueprint(CallbackInfo ci){if(dev.sablelife.ownership.ArenaBuildTicks.blocked(blockEntity))ci.cancel();}
}
