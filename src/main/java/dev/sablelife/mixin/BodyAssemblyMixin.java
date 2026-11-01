package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import java.util.HashMap;
import java.util.UUID;

@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class BodyAssemblyMixin {
    @WrapMethod(method = "moveBlocks")
    private static void sableLife$transfer(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                           Iterable<BlockPos> blocks, Operation<Void> original) {
        var source = BodyOwnership.get(level);
        var anchors = dev.sablelife.module.AnchorTransfers.capture(level, blocks);
        var destination = BodyOwnership.get(transform.getLevel());
        var moving = new HashMap<BlockPos, UUID>();
        var movingParts = new HashMap<BlockPos, UUID>();
        for (var pos : blocks) {
            var owner = source.owner(pos);
            if (owner != null) moving.put(pos.immutable(), owner);
            if (source.part(pos) != null) movingParts.put(pos.immutable(), source.part(pos));
        }
        // Protect destination before neighbour callbacks; keep source protected until all removals finish.
        // Sable reuses storage plots: an unowned incoming block must clear the previous occupant too.
        for (var pos : blocks) destination.forget(transform.apply(pos));
        moving.forEach((pos, owner) -> destination.bind(transform.apply(pos), owner));
        movingParts.forEach((pos, part) -> destination.bindPart(transform.apply(pos), part));
        BodyOwnership.MOVING.set(BodyOwnership.MOVING.get() + 1);
        try {
            original.call(level, transform, blocks);
        } finally {
            BodyOwnership.MOVING.set(BodyOwnership.MOVING.get() - 1);
        }
        moving.forEach((pos, owner) -> {
            if (level.getBlockState(pos).isAir()) source.forget(pos);
        });
        anchors.finish(transform);
        dev.sablelife.compat.sable.SableBodyProbe.invalidate(level);
        dev.sablelife.compat.sable.SableBodyProbe.invalidate(transform.getLevel());
    }
}
