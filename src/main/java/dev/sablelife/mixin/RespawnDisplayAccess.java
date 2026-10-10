package dev.sablelife.mixin;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Display.class)
public interface RespawnDisplayAccess {
 @Invoker("setBillboardConstraints") void sablelife$billboard(Display.BillboardConstraints constraints);
}
