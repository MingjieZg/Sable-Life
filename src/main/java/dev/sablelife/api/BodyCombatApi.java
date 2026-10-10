package dev.sablelife.api;
import dev.sablelife.combat.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.*;
import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.*;
import java.util.function.DoubleUnaryOperator;
/** Experimental server API. Targets are real owned body voxels; no proxy Entity is constructed. */
public final class BodyCombatApi {
 public static boolean projectile(Projectile source,BlockHitResult hit,float damage){
  return source!=null&&hit!=null&&hit.getType()==HitResult.Type.BLOCK&&Double.isFinite(hit.getLocation().lengthSqr())&&BodyDamageBridge.hit(source,hit,damage);
 }
 /** One invocation is one attack window. Persistent callers must retain their own original cadence. */
 public static int sphere(Entity source,Vec3 center,Vec3 visibilityOrigin,double radius,DoubleUnaryOperator damageAtDistance){
  return sphere(source,center,visibilityOrigin,radius,damageAtDistance,java.util.Set.of());
 }
 public static int sphere(Entity source,Vec3 center,Vec3 visibilityOrigin,double radius,DoubleUnaryOperator damageAtDistance,java.util.Set<java.util.UUID> excluded){
  if(source==null||center==null||visibilityOrigin==null||source.isRemoved()||!(source.level() instanceof ServerLevel level)||damageAtDistance==null)return 0;
  center=SableBodyProbe.toWorld(level,center);visibilityOrigin=SableBodyProbe.toWorld(level,visibilityOrigin);int hits=0;
  for(var hit:BodySphereQuery.query(level,center,visibilityOrigin,radius)){
   if(excluded!=null&&excluded.contains(hit.core().creatureId()))continue;
   double damage=damageAtDistance.applyAsDouble(hit.distance());if(!Double.isFinite(damage)||damage<=0)continue;
   var core=hit.core();if(core.getLevel()!=level||core.isRemoved()||core.isControlCore()||!core.creatureId().equals(BodyOwnership.get(level).owner(hit.block())))continue;
   var data=source.getPersistentData();if(data.hasUUID("SableLifeAttackOwner")&&!core.acceptsLifeAttack(data.getUUID("SableLifeAttackOwner")))continue;
   if(core.damageAt(hit.block(),(float)Math.min(damage,HeartHealth.MAX_CONFIGURED_HEALTH)))hits++;
  }return hits;
 }
 private BodyCombatApi(){}
}
