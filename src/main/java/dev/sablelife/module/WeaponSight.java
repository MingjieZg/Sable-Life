package dev.sablelife.module;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Keeps the last successful selection between casts. Only live sight can authorize a new cast. */
public final class WeaponSight {
    private record Lock(VirtualModule module, SightMemory memory) {}
    private final Map<UUID, Lock> locks = new HashMap<>();
    private int revision = Integer.MIN_VALUE;

    void clear() { locks.clear(); }

    public UUID target(HeartBlockEntity heart, VirtualModule module) {
        if (revision != heart.modules().revision() || SightMemory.ticks(module) == 0) return null;
        var lock = locks.get(module.id());
        return lock == null ? null : lock.memory().id();
    }

    private void refreshRevision(HeartBlockEntity heart) {
        int current = heart.modules().revision();
        if (revision != current) {
            locks.clear();
            revision = current;
        }
    }

    void tick(HeartBlockEntity heart) {
        if (locks.isEmpty() || !(heart.getLevel() instanceof ServerLevel level)) return;
        refreshRevision(heart);
        if (heart.modules().commandTarget().weaponTarget(level) != null) {
            locks.clear();
            return;
        }
        locks.values().removeIf(lock -> {
            var module = lock.module();
            if (!SightMemory.configurable(module) || SightMemory.ticks(module) == 0 || !HeartModules.triggered(module, heart)
                    || module.value("trigger") == 4 && heart.modules().moveTarget(module.id()) != null) return true;
            var target = ModuleTargets.resolve(level, lock.memory().id(), heart.creatureId());
            return target == null || !refresh(level, heart, module, lock.memory(), target);
        });
    }

    private boolean refresh(ServerLevel level, HeartBlockEntity heart, VirtualModule module, SightMemory memory, LivingEntity target) {
        if (target.isRemoved()) return false;
        Vec3 origin = switch (module.type()) {
            case "ground_strike" -> ModuleCombat.baseOrigin(heart, module);
            case "quake" -> ModuleQuake.searchOrigin(heart, module);
            case "grab" -> ModuleGrab.holdPoint(heart, module);
            case "freeze", "pull", "pulse", "sphere_blast" -> TargetCastOrigin.searchOrigin(heart, module);
            case "homing" -> ModuleCombat.origin(heart, module, true);
            default -> ModuleCombat.nozzle(heart, module);
        };
        if (origin == null || !eligible(module, target)) return false;
        var rangeOrigin = module.type().equals("quake") ? ModuleGroundStrike.floor(level, heart, origin) : origin;
        if (rangeOrigin == null) return false;
        var point = rangePoint(level, module, target);
        boolean liveInRange = module.type().equals("ground_strike") ? ModuleGroundStrike.targetInRange(level, module, origin, target)
                : inRange(module, rangeOrigin, point);
        var last = memory.lastSeen();
        boolean rememberedInRange = last != null && inRange(module, rangeOrigin, last.point());
        if (!liveInRange && !rememberedInRange) return false;
        var center = SableBodyProbe.toWorld(level, target.getBoundingBox().getCenter());
        if (SightMemory.canRecheck(origin, center) && (module.type().equals("laser")
                ? ModuleLaser.canSee(level, heart, module, origin, target)
                : ModuleTargets.visible(level, heart.creatureId(), origin, target))) {
            if (!liveInRange) return false;
            memory.see(target.getUUID(), point);
            return true;
        }
        return rememberedInRange && memory.keeping(level.getGameTime(), SightMemory.ticks(module));
    }

    private static boolean eligible(VirtualModule module, LivingEntity target) {
        return switch (module.type()) {
            case "freeze" -> ModuleFreeze.matches(target, module);
            case "grab" -> ModuleGrab.matches(target, module);
            default -> ModuleTargets.matches(target, module);
        };
    }

    private static Vec3 rangePoint(ServerLevel level, VirtualModule module, LivingEntity target) {
        var point = switch (module.type()) {
            case "ground_strike", "quake", "mortar" -> target.position();
            default -> target.getBoundingBox().getCenter();
        };
        return SableBodyProbe.toWorld(level, point);
    }

    private static boolean inRange(VirtualModule module, Vec3 origin, Vec3 point) {
        if (module.type().equals("ground_strike")) {
            double range = module.value("strikeRange");
            double distance = ModuleArena.horizontalDistance(point, origin);
            return Math.abs(point.y - origin.y) <= range && distance >= module.value("strikeStart") && distance <= range;
        }
        double range = module.value(switch (module.type()) {
            case "quake" -> "quakeDetectRange";
            case "freeze", "pull", "pulse", "sphere_blast" -> "castTargetRange";
            case "grab" -> "grabRadius";
            case "laser" -> "beamLength";
            default -> "targetRange";
        });
        double distance = point.distanceToSqr(origin);
        return distance <= range * range && (distance > 1e-8 || !directShot(module));
    }

    private static boolean directShot(VirtualModule module) {
        return switch (module.type()) {
            case "arrow", "gun", "slash" -> true;
            default -> false;
        };
    }

    public <T> Stream<T> results(ServerLevel level, HeartBlockEntity heart, VirtualModule module, AABB bounds,
                                 Predicate<LivingEntity> eligible, Comparator<LivingEntity> order,
                                 Function<LivingEntity, T> result, UUID inherited, boolean multiple) {
        if (inherited != null || module.emitter() != null || SightMemory.ticks(module) == 0 || !SightMemory.configurable(module))
            return ModuleTargets.aimedResults(level, heart, bounds, eligible, order, result, inherited, multiple);
        refreshRevision(heart);
        var lock = locks.get(module.id());
        var authored = lock == null ? heart.modules().find(module.id()) : lock.module();
        if (authored == null || !authored.type().equals(module.type()) || !SightMemory.configurable(authored) || SightMemory.ticks(authored) == 0)
            return ModuleTargets.aimedResults(level, heart, bounds, eligible, order, result, inherited, multiple);
        if (lock != null) {
            var target = ModuleTargets.resolve(level, lock.memory().id(), heart.creatureId());
            if (target != null && refresh(level, heart, module, lock.memory(), target)) {
                if (lock.memory().obscured()) return Stream.empty();
                if (bounds.intersects(target.getBoundingBox()) && eligible.test(target)) {
                    T first = result.apply(target);
                    if (first != null) return ModuleTargets.aimedResults(level, heart, bounds, eligible, order,
                            candidate -> candidate.getUUID().equals(target.getUUID()) ? first : result.apply(candidate),
                            target.getUUID(), multiple);
                }
            }
            locks.remove(module.id());
        }
        return ModuleTargets.aimedResults(level, heart, bounds, eligible, order, target -> {
            var selected = result.apply(target);
            if (selected != null && !locks.containsKey(module.id())) {
                var memory = new SightMemory();
                memory.see(target.getUUID(), rangePoint(level, module, target));
                locks.put(module.id(), new Lock(authored, memory));
            }
            return selected;
        }, null, multiple);
    }

    public List<LivingEntity> targets(ServerLevel level, HeartBlockEntity heart, VirtualModule module, AABB bounds,
                                      Predicate<LivingEntity> eligible, Comparator<LivingEntity> order,
                                      Predicate<LivingEntity> visible, int limit, UUID inherited) {
        return results(level, heart, module, bounds, eligible, order, target -> visible.test(target) ? target : null,
                inherited, limit > 1).limit(limit).toList();
    }
}
