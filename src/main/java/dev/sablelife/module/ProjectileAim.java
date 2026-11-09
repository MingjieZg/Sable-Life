package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import java.util.*;

/** Each release samples current target positions once. No homing or movement prediction. */
public final class ProjectileAim {
    public static List<Vec3> directions(HeartBlockEntity heart,VirtualModule m,Vec3 origin){
        if(m.value("shotAim")==0)return List.of(ModuleCombat.direction(heart,m));
        var level=(ServerLevel)heart.getLevel();double range=m.value("targetRange");
        return ModuleTargets.query(level,heart.creatureId(),new AABB(origin,origin).inflate(range),e->ModuleTargets.matches(e,m)).stream()
            .filter(e->{double distance=SableBodyProbe.toWorld(level,e.getBoundingBox().getCenter()).distanceToSqr(origin);return distance>1e-8&&distance<=range*range&&ModuleTargets.visible(level,heart.creatureId(),origin,e);})
            .sorted(ModuleTargets.comparator(heart,e->SableBodyProbe.toWorld(level,e.getBoundingBox().getCenter()).distanceToSqr(origin)))
            .limit(m.value("targetMultiple")==1?64:1)
            .map(e->SableBodyProbe.toWorld(level,e.getBoundingBox().getCenter()).subtract(origin).normalize()).toList();
    }
    private ProjectileAim(){}
}
