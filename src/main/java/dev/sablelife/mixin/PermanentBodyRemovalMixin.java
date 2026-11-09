package dev.sablelife.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.sablelife.ownership.BodyOwnership;
import dev.sablelife.ownership.LifeResidueCleanup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(value = ServerSubLevelContainer.class, remap = false)
public abstract class PermanentBodyRemovalMixin {
    @Inject(method = "removeSubLevel(IILdev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason;)V", at = @At("HEAD"))
    private void sablelife$captureDeletedCores(int x, int z, SubLevelRemovalReason reason, CallbackInfo ci,
                                               @Share("sablelifeDeletedCores") LocalRef<List<HeartBlockEntity>> captured,
                                               @Share("sablelifeDeletedBody") LocalRef<UUID> deletedBody) {
        if (reason != SubLevelRemovalReason.REMOVED || BodyOwnership.MOVING.get() != 0) return;
        var container = (ServerSubLevelContainer) (Object) this;
        if (!(container.getSubLevel(x, z) instanceof ServerSubLevel body)) return;
        deletedBody.set(body.getUniqueId());
        var cores = new ArrayList<HeartBlockEntity>();
        for (var holder : body.getPlot().getLoadedChunks()) {
            for (var entity : holder.getChunk().getBlockEntities().values()) {
                if (entity instanceof HeartBlockEntity core && !core.isRemoved() && core.health().state() == HeartState.ALIVE)
                    cores.add(core);
            }
        }
        captured.set(cores);
    }

    @Inject(method = "removeSubLevel(IILdev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason;)V", at = @At("RETURN"))
    private void sablelife$cleanDeletedPlot(int x, int z, SubLevelRemovalReason reason, CallbackInfo ci,
                                            @Share("sablelifeDeletedCores") LocalRef<List<HeartBlockEntity>> captured,
                                            @Share("sablelifeDeletedBody") LocalRef<UUID> deletedBody) {
        var container = (ServerSubLevelContainer) (Object) this;
        if(deletedBody.get()!=null)dev.sablelife.ownership.WildEncounters.get(container.getLevel().getServer())
                .permanentlyRemoved(container.getLevel(),deletedBody.get());
        var cores = captured.get();
        if (cores == null) return;
        LifeResidueCleanup.permanentlyRemoved(container.getLevel(), x + container.getOrigin().x,
                z + container.getOrigin().y, container.getLogPlotSize(), cores);
    }
}
