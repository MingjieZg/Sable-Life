package dev.sablelife.mixin;
import net.minecraft.world.entity.Display;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Display.TextDisplay.class)
public interface RespawnTextDisplayAccess {
 @Invoker("setText") void sablelife$text(Component text);
 @Invoker("setBackgroundColor") void sablelife$background(int color);
 @Invoker("setFlags") void sablelife$flags(byte flags);
 @Invoker("setLineWidth") void sablelife$lineWidth(int width);
}
