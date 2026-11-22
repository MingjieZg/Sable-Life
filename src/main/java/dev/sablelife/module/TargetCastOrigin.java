package dev.sablelife.module;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Function;

/** Target origins are sampled once per warning/charge, never an always-following aura. */
public final class TargetCastOrigin {
    public static boolean targeted(VirtualModule module) {
        return module.emitter() == null && switch (module.type()) {
            case "sphere_blast", "pull", "freeze", "pulse" -> module.value("releasePosition") == 1;
            default -> false;
        };
    }

    public static Vec3 searchOrigin(HeartBlockEntity core, VirtualModule module) {
        return !module.anchors().isEmpty() && module.anchors().getFirst().status(core).equals("valid")
                ? module.anchors().getFirst().world(core, false)
                : SableBodyProbe.toWorld(core.getLevel(), core.getBlockPos().getCenter());
    }

    static boolean targetInRange(ServerLevel level, VirtualModule module, Vec3 origin, LivingEntity target) {
        double range = module.value("castTargetRange");
        return matches(module, target) && distance(level, origin, target) <= range * range;
    }

    private static boolean matches(VirtualModule module, LivingEntity target) {
        return module.type().equals("freeze") ? ModuleFreeze.matches(target, module) : ModuleTargets.matches(target, module);
    }

    private static double distance(ServerLevel level, Vec3 origin, LivingEntity target) {
        return SableBodyProbe.toWorld(level, target.getBoundingBox().getCenter()).distanceToSqr(origin);
    }

    public static List<Vec3> points(HeartBlockEntity core, VirtualModule module) {
        return points(core, module, ModuleTargets.attackAim(core, module), false);
    }

    static List<Vec3> castPoints(HeartBlockEntity core, VirtualModule module, ModuleTargets.AttackAim aim) {
        return points(core, module, aim, true);
    }

    private static List<Vec3> points(HeartBlockEntity core, VirtualModule module, ModuleTargets.AttackAim aim, boolean remember) {
        if (!(core.getLevel() instanceof ServerLevel level)) return List.of();
        var origin = searchOrigin(core, module);
        double range = module.value("castTargetRange");
        boolean multiple = module.value("targetMultiple") == 1;
        var bounds = new AABB(origin, origin).inflate(range);
        java.util.function.Predicate<LivingEntity> eligible = target -> matches(module, target)
                && ModuleTargets.attackPoint(level, core, module, target, aim).distanceToSqr(origin) <= range * range;
        var order = ModuleTargets.comparator(core, target -> distance(level, origin, target));
        Function<LivingEntity, Vec3> point = target -> {
            if (!ModuleTargets.attackVisible(level, core, module, origin, target, aim)) return null;
            var result = ModuleTargets.attackPoint(level, core, module, target, aim);
            return result.distanceToSqr(origin) <= range * range && level.hasChunkAt(BlockPos.containing(result)) ? result : null;
        };
        var results = ModuleTargets.attackResults(level, core, module, bounds, eligible, order, point, aim, multiple, remember);
        return results.limit(multiple ? 64 : 1).toList();
    }

    public static Vec3 point(HeartBlockEntity core, VirtualModule module) {
        var points = points(core, module);
        return points.isEmpty() ? null : points.getFirst();
    }

    static boolean releaseAllowed(ServerLevel level, HeartBlockEntity core, VirtualModule module, ModuleTargets.AttackAim aim) {
        if (aim == null || !aim.followsMove()) return true;
        var target = ModuleTargets.resolve(level, aim.id(), core.creatureId());
        var origin = searchOrigin(core, module);
        double range = module.value("castTargetRange");
        return target != null && matches(module, target)
                && ModuleTargets.attackPoint(level, core, module, target, aim).distanceToSqr(origin) <= range * range
                && ModuleTargets.attackVisible(level, core, module, origin, target, aim);
    }

    private TargetCastOrigin() {}
}
