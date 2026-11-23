package dev.sablelife.module;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablelife.compat.sablecollision.EntityPassThroughMixinPlugin;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The core owns the trigger. A marked body remains in its normal Sable physics scene. */
final class ModuleEntityPassThrough {
    private ServerLevel level;
    private Set<ServerSubLevel> selected = Set.of();

    static boolean wholeBody(VirtualModule module) {
        return module.value(VirtualModule.PASS_THROUGH_WHOLE_BODY) == 1;
    }

    void tick(HeartBlockEntity heart, List<VirtualModule> modules) {
        if (modules.isEmpty() || !heart.operational() || !(heart.getLevel() instanceof ServerLevel server)
                || !EntityPassThroughMixinPlugin.supported()) {
            clear();
            return;
        }
        if (level != null && level != server) clear();

        Set<ServerSubLevel> marked = null;
        for (var module : modules) {
            if (!HeartModules.triggered(module, heart)) continue;
            if (wholeBody(module)) {
                var root = Sable.HELPER.getContaining(server, heart.getBlockPos());
                if (root == null || root.isRemoved()) break;
                var connected = new HashSet<ServerSubLevel>();
                for (var body : SubLevelHelper.getConnectedChain(root)) {
                    if (body instanceof ServerSubLevel part && !part.isRemoved()) connected.add(part);
                }
                marked = connected;
                break;
            }
            if (module.anchors().getFirst().body(heart) instanceof ServerSubLevel body && !body.isRemoved()) {
                if (marked == null) marked = new HashSet<>();
                marked.add(body);
            }
        }
        var next = marked == null ? Set.<ServerSubLevel>of() : marked;
        if (!selected.equals(next)) {
            EntityPassThroughState.replace(server, selected, next);
            selected = next;
            level = next.isEmpty() ? null : server;
        }
    }

    void clear() {
        if (level == null) return;
        EntityPassThroughState.replace(level, selected, Set.of());
        level = null;
        selected = Set.of();
    }
}
