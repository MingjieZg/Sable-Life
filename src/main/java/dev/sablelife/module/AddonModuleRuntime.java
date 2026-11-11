package dev.sablelife.module;

import com.mojang.logging.LogUtils;
import dev.sablelife.api.module.ModuleBehavior;
import dev.sablelife.api.module.ModuleContext;
import dev.sablelife.api.module.ModuleDefinition.MarkMode;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class AddonModuleRuntime {
    private record Context(ServerLevel level, VirtualModule module, UUID creatureId,
            Vec3 corePosition, List<Mark> marks) implements ModuleContext {
        @Override public UUID moduleId() { return module.id(); }

        @Override public double option(String key) {
            String field = "addon_" + key;
            if (ModuleFields.find(module.type(), field) == null) {
                throw new IllegalArgumentException("Unknown module option: " + key);
            }
            return module.value(field);
        }
    }

    private record Active(ModuleBehavior behavior, Context context) {}

    private final HashMap<UUID, Active> active = new HashMap<>();
    private final HashSet<UUID> failed = new HashSet<>();
    private List<VirtualModule> candidates;
    private DatapackModules.Catalog catalog;
    private int revision;
    private int generation;

    void tick(HeartBlockEntity heart, List<VirtualModule> modules, int moduleRevision) {
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        var currentCatalog = DatapackModules.current();
        if (candidates == null || revision != moduleRevision || catalog != currentCatalog) {
            candidates = modules.stream().filter(module -> ModuleTypes.find(module.type()) != null).toList();
            revision = moduleRevision;
            catalog = currentCatalog;
        }
        int currentGeneration = generation;
        Set<UUID> powered = null;
        for (var module : candidates) {
            var definition = ModuleTypes.find(module.type());
            if (!HeartModules.triggered(module, heart)) continue;
            if (powered == null) powered = new HashSet<>();
            powered.add(module.id());
            if (failed.contains(module.id())) continue;
            List<ModuleContext.Mark> marks = definition.markMode() == MarkMode.NONE ? List.of() : module.anchors().stream().map(anchor -> {
                var point = anchor.world(heart, false);
                return new ModuleContext.Mark(point, anchor.world(heart, true).subtract(point).normalize());
            }).toList();
            var context = new Context(level, module, heart.creatureId(),
                    SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter()), marks);
            try {
                var instance = active.get(module.id());
                if (instance == null) {
                    var behavior = Objects.requireNonNull(definition.behaviorFactory().get(), "Addon module behavior");
                    instance = new Active(behavior, context);
                    active.put(module.id(), instance);
                    behavior.onStart(context);
                } else {
                    instance = new Active(instance.behavior(), context);
                    active.put(module.id(), instance);
                }
                if (generation == currentGeneration) instance.behavior().onTick(context);
            } catch (RuntimeException exception) {
                LogUtils.getLogger().error("Addon module {} failed for {}", module.type(), module.id(), exception);
                if (generation == currentGeneration) failed.add(module.id());
                stop(active.remove(module.id()));
            }
            if (generation != currentGeneration) return;
        }
        if (powered == null) {
            if (!active.isEmpty() || !failed.isEmpty()) stopAll();
            return;
        }
        for (var id : List.copyOf(active.keySet())) {
            if (!powered.contains(id)) stop(active.remove(id));
        }
        failed.retainAll(powered);
    }

    void clear() {
        generation++;
        candidates = null;
        stopAll();
    }

    private void stopAll() {
        var previous = List.copyOf(active.values());
        active.clear();
        failed.clear();
        previous.forEach(AddonModuleRuntime::stop);
    }

    private static void stop(Active instance) {
        if (instance == null) return;
        try {
            instance.behavior().onStop(instance.context());
        } catch (RuntimeException exception) {
            LogUtils.getLogger().error("Stopping addon module {} failed", instance.context().moduleId(), exception);
        }
    }
}
