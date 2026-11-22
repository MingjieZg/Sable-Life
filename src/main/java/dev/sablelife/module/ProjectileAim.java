package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import java.util.*;

/** Each release samples current target positions once. No homing or movement prediction. */
public final class ProjectileAim {
    public static List<Vec3> directions(HeartBlockEntity heart,VirtualModule m,Vec3 origin){
        return directions(heart, m, origin, true);
    }

    public static List<Vec3> previewDirections(HeartBlockEntity heart, VirtualModule m, Vec3 origin) {
        return directions(heart, m, origin, false);
    }

    private static List<Vec3> directions(HeartBlockEntity heart, VirtualModule m, Vec3 origin, boolean remember) {
        if(m.value("shotAim")==0)return List.of(ModuleCombat.direction(heart,m));
        var level=(ServerLevel)heart.getLevel();double range=m.value("targetRange");
        var aim = ModuleTargets.attackAim(heart, m);
        java.util.function.ToDoubleFunction<net.minecraft.world.entity.LivingEntity> distance = target ->
                ModuleTargets.attackPoint(level, heart, m, target, aim).distanceToSqr(origin);
        java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> eligible = e -> {
            if (!ModuleTargets.matches(e, m)) return false;
            double squared = distance.applyAsDouble(e);
            return squared > 1e-8 && squared <= range * range;
        };
        var bounds = new AABB(origin, origin).inflate(range);
        var order = ModuleTargets.comparator(heart, distance);
        java.util.function.Function<net.minecraft.world.entity.LivingEntity, Vec3> direction = target -> {
            if (!ModuleTargets.attackVisible(level, heart, m, origin, target, aim)) return null;
            var delta = ModuleTargets.attackPoint(level, heart, m, target, aim).subtract(origin);
            return delta.lengthSqr() > 1e-8 && delta.lengthSqr() <= range * range ? delta.normalize() : null;
        };
        boolean multiple = m.value("targetMultiple") == 1;
        var selected = ModuleTargets.attackResults(level, heart, m, bounds, eligible, order, direction, aim, multiple, remember);
        return selected.limit(multiple ? 64 : 1).toList();
    }
    private ProjectileAim(){}
}
