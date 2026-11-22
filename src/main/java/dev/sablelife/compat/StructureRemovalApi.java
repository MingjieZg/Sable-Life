package dev.sablelife.compat;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;

/** Same native path as Photomancy; verified Toolgun coordinator also cleans its own rope/control state. */
public final class StructureRemovalApi {
    private static final class Toolgun {
        static final Method coordinator = lookup();
        private static final String COORDINATOR = "com.enxv.aeronauticsstructuretool.blueprint.lifecycle.SubLevelRemovalCoordinator";

        private static Method lookup() {
            var version = OptionalMod.version("create_aeronautics_toolgun");
            if (!ModVersion.atLeast(version, "0.3.6")) return null;
            try {
                var loader = StructureRemovalApi.class.getClassLoader();
                var coordinatorClass = Class.forName(COORDINATOR, false, loader);
                var method = coordinatorClass.getMethod("remove", ServerLevel.class, ServerSubLevelContainer.class, Collection.class);
                if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class || !method.canAccess(null)) {
                    throw new NoSuchMethodException("Expected an accessible static void remove method");
                }
                Class.forName(COORDINATOR, true, loader);
                return method;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                LogUtils.getLogger().warn("Toolgun {} structure removal API is unavailable. Using native Sable removal.", version, failure);
                return null;
            }
        }
    }
    public static void remove(ServerLevel level, ServerSubLevel body) {
        removeAll(level, List.of(body));
    }

    /** Validate and detach every mounted payload before removing any member of the group. */
    public static void removeAll(ServerLevel level, Collection<ServerSubLevel> bodies) {
        var requested = List.copyOf(bodies);
        if (requested.isEmpty()) return;
        var container = SubLevelContainer.getContainer(level);
        for (var body : requested) {
            if (container == null || container.getSubLevel(body.getUniqueId()) != body) {
                throw new IllegalStateException("Structure is no longer registered: " + body.getUniqueId());
            }
        }
        for (var body : requested) BodyDecorationCleanup.discardForBody(level, body);
        // Only controllers in this exact plot, with persisted ownership and an active death/part job, qualify.
        for (var body : requested) {
            for (var holder : body.getPlot().getLoadedChunks()) {
                for (var controller : List.copyOf(holder.getChunk().getBlockEntities().values())) {
                    if (Sable.HELPER.getContaining(level, controller.getBlockPos()) == body) {
                        MountedContraptionCleanup.discardForDeath(controller);
                    }
                }
            }
        }
        removePrepared(level, container, requested, Toolgun.coordinator);
    }

    private static void removePrepared(ServerLevel level, ServerSubLevelContainer container, List<ServerSubLevel> requested, Method coordinator) {
        boolean usedToolgun = false;
        if (coordinator != null) {
            try {
                coordinator.invoke(null, level, container, requested);
                usedToolgun = true;
            } catch (IllegalAccessException failure) {
                LogUtils.getLogger().warn("Toolgun structure removal is inaccessible. Using native Sable removal.", failure);
            } catch (InvocationTargetException failure) {
                // Toolgun may already have changed part of the group. Let cleanup inspect and retry remaining work.
                if (failure.getCause() instanceof RuntimeException cause) throw cause;
                throw new IllegalStateException("Toolgun structure removal failed", failure.getCause());
            }
        }
        if (!usedToolgun) {
            for (var body : requested) {
                if (container.getSubLevel(body.getUniqueId()) == body) {
                    container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                }
            }
        }
        for (var body : requested) {
            if (!permanentlyRemoved(container, body)) {
                String adapter = usedToolgun ? "Toolgun" : "Sable";
                throw new IllegalStateException(adapter + " did not permanently remove structure " + body.getUniqueId());
            }
        }
    }

    public static boolean permanentlyRemoved(ServerSubLevelContainer container, ServerSubLevel body) {
        if (container == null || !body.isRemoved() || container.getSubLevel(body.getUniqueId()) != null) return false;
        // Unloading also marks a body removed and unregisters it, but reserves its plot for reloading.
        var plot = body.getPlot().plotPos;
        var origin = container.getOrigin();
        return !container.getOccupancy().get(container.getIndex(plot.x - origin.x, plot.z - origin.y));
    }

    /** Confirm an absent body's removal using its previously observed native plot. */
    public static boolean permanentlyRemoved(ServerSubLevelContainer container, java.util.UUID body, BlockPos plotCenter) {
        if (container == null || plotCenter == null || !container.inBounds(plotCenter) || container.getSubLevel(body) != null)
            return false;
        var holding = container.getHoldingChunkMap();
        if (holding == null || holding.getHoldingSubLevel(body) != null) return false;
        int shift = 4 + container.getLogPlotSize();
        var origin = container.getOrigin();
        int x = (plotCenter.getX() >> shift) - origin.x;
        int z = (plotCenter.getZ() >> shift) - origin.y;
        var resident = container.getSubLevel(x, z);
        // A reserved plot without a loaded body can still hold the structure on disk.
        return resident != null && !resident.getUniqueId().equals(body)
                || !container.getOccupancy().get(container.getIndex(x, z));
    }

    private StructureRemovalApi() {}
}
