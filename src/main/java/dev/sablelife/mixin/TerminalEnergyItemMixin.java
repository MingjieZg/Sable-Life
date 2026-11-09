package dev.sablelife.mixin;

import dev.sablelife.heart.SurvivalLifeCoreBlock;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class TerminalEnergyItemMixin {
    @Inject(method={"onBelowWorld","kill"},at=@At("RETURN"))
    private void sablelife$terminalItem(CallbackInfo ci){
        SurvivalLifeCoreBlock.retireDestroyedClaim((Entity)(Object)this);
    }
}
