package dev.sablelife.testing.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
/** Test-only: vanilla chooses +/-15M coordinates, beyond the precision of float body physics. */
@Mixin(GameTestServer.class)
public abstract class GameTestLocalOriginMixin {
    @ModifyArg(method="startTests",at=@At(value="INVOKE",target="Lnet/minecraft/gametest/framework/StructureGridSpawner;<init>(Lnet/minecraft/core/BlockPos;IZ)V"),index=0)
    private BlockPos sablelife$localOrigin(BlockPos original){return Boolean.getBoolean("sablelife.localGameTests")?new BlockPos(0,-59,0):original;}
}
