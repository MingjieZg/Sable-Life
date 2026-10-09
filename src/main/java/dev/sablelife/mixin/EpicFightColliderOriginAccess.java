package dev.sablelife.mixin;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo @Mixin(targets="yesman.epicfight.api.collider.Collider",remap=false)
public interface EpicFightColliderOriginAccess {
    @Accessor(value="worldCenter",remap=false) Vec3 sablelife$worldCenter();
}
