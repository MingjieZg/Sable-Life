package dev.sablelife.mixin;

import dev.sablelife.combat.HitboxQueryBounds;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySectionStorage.class)
public abstract class HitboxSectionStorageMixin {
    @Unique private final HitboxQueryBounds sablelife$hitboxBounds=new HitboxQueryBounds();

    @Inject(method="getOrCreateSection",at=@At("RETURN"))
    private void sablelife$bindSections(long key,CallbackInfoReturnable<EntitySection<?>> result){
        ((HitboxQueryBounds.Section)result.getReturnValue()).sablelife$queryBounds(sablelife$hitboxBounds);
    }

    @ModifyVariable(method="forEachAccessibleNonEmptySection",at=@At("HEAD"),argsOnly=true)
    private AABB sablelife$includeLargeReceivers(AABB query){return sablelife$hitboxBounds.sectionsFor(query);}
}
