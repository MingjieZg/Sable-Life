package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sablelife.ownership.BodyProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BiConsumer;

@Mixin(Explosion.class)
public abstract class BodyExplosionProtectionMixin {
    @WrapOperation(method="finalizeExplosion",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;onExplosionHit(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;Ljava/util/function/BiConsumer;)V"))
    private void sablelife$applyExplosionPolicy(BlockState state,Level level,BlockPos pos,Explosion explosion,
                                                 BiConsumer<ItemStack,BlockPos> drops,Operation<Void> original){
        if(level instanceof ServerLevel server)BodyProtection.explosionHit(server,pos,()->original.call(state,level,pos,explosion,drops));
        else original.call(state,level,pos,explosion,drops);
    }
}
