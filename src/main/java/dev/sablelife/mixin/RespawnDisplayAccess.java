package dev.sablelife.mixin;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Display.class)
public interface RespawnDisplayAccess {
 @Invoker("setTransformation") void sablelife$transform(com.mojang.math.Transformation transform);
 @Invoker("setTransformationInterpolationDelay") void sablelife$interpolation(int delay);
 @Invoker("setBillboardConstraints") void sablelife$billboard(Display.BillboardConstraints constraints);
}
