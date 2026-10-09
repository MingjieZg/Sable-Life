package dev.sablelife.module;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import java.util.*;
/** Target origins are sampled once per warning/charge, never an always-following aura. */
public final class TargetCastOrigin {
 public static boolean targeted(VirtualModule m){return (m.type().equals("sphere_blast")||m.type().equals("pull"))&&m.value("releasePosition")==1;}
 public static Vec3 searchOrigin(HeartBlockEntity core,VirtualModule m){return !m.anchors().isEmpty()&&m.anchors().getFirst().status(core).equals("valid")?m.anchors().getFirst().world(core,false):SableBodyProbe.toWorld(core.getLevel(),core.getBlockPos().getCenter());}
 public static Vec3 point(HeartBlockEntity core,VirtualModule m){if(!(core.getLevel() instanceof ServerLevel level))return null;var origin=searchOrigin(core,m);double range=m.value("castTargetRange");return ModuleTargets.query(level,core.creatureId(),new AABB(origin,origin).inflate(range),e->ModuleTargets.matches(e,m)).stream().filter(e->SableBodyProbe.toWorld(level,e.getBoundingBox().getCenter()).distanceToSqr(origin)<=range*range).filter(e->!m.type().equals("pull")||m.value("lineOfSight")==1||ModuleTargets.visible(level,core.creatureId(),origin,e)).min(Comparator.comparingDouble(e->SableBodyProbe.toWorld(level,e.getBoundingBox().getCenter()).distanceToSqr(origin))).map(e->SableBodyProbe.toWorld(level,e.getBoundingBox().getCenter())).orElse(null);}
 private TargetCastOrigin(){}
}
