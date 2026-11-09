package dev.sablelife.mixin;

import dev.sablelife.combat.*;
import net.minecraft.world.level.entity.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(EntitySection.class)
public abstract class HitboxEntitySectionMixin implements HitboxQueryBounds.Section {
    @Unique private HitboxQueryBounds sablelife$hitboxBounds;
    @Override public void sablelife$queryBounds(HitboxQueryBounds bounds){sablelife$hitboxBounds=bounds;}

    @Inject(method="add",at=@At("RETURN"))
    private void sablelife$receiverAdded(EntityAccess entity,CallbackInfo result){
        if(entity instanceof EntityHitbox receiver)receiver.queryBounds(sablelife$hitboxBounds);
    }

    @Inject(method="remove",at=@At("RETURN"))
    private void sablelife$receiverRemoved(EntityAccess entity,CallbackInfoReturnable<Boolean> result){
        if(result.getReturnValueZ()&&entity instanceof EntityHitbox receiver)receiver.clearQueryBounds(sablelife$hitboxBounds);
    }
}
