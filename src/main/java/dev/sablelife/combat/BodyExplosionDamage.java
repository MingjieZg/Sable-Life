package dev.sablelife.combat;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.*;
import dev.sablelife.ownership.BodyOwnership;
import dev.sablelife.attack.ContactBox;
import dev.sablelife.mixin.ExplosionDamageAccess;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.joml.Quaterniond;
import java.util.*;
import java.util.function.DoubleUnaryOperator;
/** Actual collision voxels, never the empty space in a creature's enclosing box. Event-only work. */
public final class BodyExplosionDamage {
 private record Hit(BlockPos block,double distance){}
 private static final Map<Explosion,Boolean> TERRAIN_ONLY=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Map<net.minecraft.world.damagesource.DamageSource,Long> TERRAIN_SOURCES=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Map<net.minecraft.world.damagesource.DamageSource,Long> NATIVE_SOURCES=Collections.synchronizedMap(new WeakHashMap<>());
 /** Identity-scoped suppression: keep vanilla terrain/ordinary-entity behavior, skip duplicate core damage. */
 public static void terrainOnly(Explosion explosion){if(explosion!=null)TERRAIN_ONLY.put(explosion,true);}
 public static boolean routedTerrainSource(net.minecraft.world.damagesource.DamageSource source,long tick){return Objects.equals(TERRAIN_SOURCES.get(source),tick);}
 public static boolean nativeExplosionSource(net.minecraft.world.damagesource.DamageSource source,long tick){return Objects.equals(NATIVE_SOURCES.get(source),tick);}
 private static final Map<Explosion,Set<UUID>> SEEN=Collections.synchronizedMap(new WeakHashMap<>());
 public static void detonate(ExplosionEvent.Detonate event){
  if(!(event.getLevel() instanceof ServerLevel level))return;var explosion=event.getExplosion();var damageSource=((ExplosionDamageAccess)explosion).sablelife$damageSource();NATIVE_SOURCES.put(damageSource,level.getGameTime());if(TERRAIN_ONLY.containsKey(explosion)){TERRAIN_SOURCES.put(damageSource,level.getGameTime());return;}var source=explosion.getDirectSourceEntity();
  // Our own modules already apply their configured damage. Their terrain/VFX explosion must not duplicate it.
  if(source!=null&&source.getPersistentData().hasUUID("SableLifeAttackOwner"))return;
  var calculator=((ExplosionDamageAccess)explosion).sablelife$calculator();if(calculator.getClass().getName().startsWith("dev.sablelife."))return;
  double range=BodyDamageBridge.explosionRange(explosion);
  damageBodies(level,explosion,damageSource,range,distance->BodyDamageBridge.explosionDamage(explosion,distance,range));
 }
 public static void externalDetonation(ServerLevel level, Explosion explosion, net.minecraft.world.damagesource.DamageSource source, double range, float amount, boolean falloff) {
  if(source==null||!Float.isFinite(amount)||amount<=0)return;
  NATIVE_SOURCES.put(source,level.getGameTime());
  damageBodies(level,explosion,source,range,distance->falloff?amount*Math.max(0,1-distance/range):amount);
 }
 private static void damageBodies(ServerLevel level, Explosion explosion, net.minecraft.world.damagesource.DamageSource source, double range, DoubleUnaryOperator damageAtDistance) {
  var center=SableBodyProbe.toWorld(level,explosion.center());if(!Double.isFinite(range)||range<=0||!Double.isFinite(center.lengthSqr()))return;
  var seen=SEEN.computeIfAbsent(explosion,e->new HashSet<>());
  for(var core:dev.sablelife.ownership.LoadedCoreIndex.query(level,new AABB(center,center).inflate(range))){
   if(core.isControlCore()||EntityHitboxes.blocksStructure(core)||core.health().state()!=HeartState.ALIVE||seen.contains(core.creatureId()))continue;var hit=nearestVisible(level,core,center,range);if(hit==null)continue;seen.add(core.creatureId());float amount=(float)damageAtDistance.applyAsDouble(hit.distance);if(Float.isFinite(amount)&&amount>0){if(EntityHitboxes.hybrid(core))core.modules().damageBoxes.bodyExplosion(core,source,hit.block,Math.min(amount,HeartHealth.MAX_CONFIGURED_HEALTH));else core.damageAt(hit.block,Math.min(amount,HeartHealth.MAX_CONFIGURED_HEALTH));}
  }
 }
 private static Hit nearestVisible(ServerLevel level,HeartBlockEntity core,Vec3 center,double range){
  var root=Sable.HELPER.getContaining(level,core.getBlockPos());if(root==null)return null;var ownership=BodyOwnership.get(level);Hit best=null;int remaining=32768;
  for(var body:SubLevelHelper.getConnectedChain(root)){
   if(body.isRemoved())continue;var bounds=body.getPlot().getBoundingBox();if(bounds==null)continue;var pivot=body.getPlot().getCenterBlock().getCenter();var worldPivot=body.logicalPose().transformPosition(pivot);var local=pivot.add(ContactBox.rotate(new Quaterniond(body.logicalPose().orientation()).conjugate(),center.subtract(worldPivot)));
   int minX=(int)Math.max(bounds.minX(),Math.floor(local.x-range)),maxX=(int)Math.min(bounds.maxX(),Math.floor(local.x+range));int minY=(int)Math.max(bounds.minY(),Math.floor(local.y-range)),maxY=(int)Math.min(bounds.maxY(),Math.floor(local.y+range));int minZ=(int)Math.max(bounds.minZ(),Math.floor(local.z-range)),maxZ=(int)Math.min(bounds.maxZ(),Math.floor(local.z+range));
   for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++){
    if(--remaining<0)return best;var pos=new BlockPos(x,y,z);if(!core.creatureId().equals(ownership.owner(pos))||ownership.part(pos)!=null||!level.hasChunkAt(pos))continue;var state=level.getBlockState(pos);if(state.isAir())continue;
    for(var box:state.getCollisionShape(level,pos).toAabbs()){
     var nearest=new Vec3(Math.clamp(local.x,x+box.minX,x+box.maxX),Math.clamp(local.y,y+box.minY,y+box.maxY),Math.clamp(local.z,z+box.minZ,z+box.maxZ));double distance=nearest.distanceTo(local);if(distance>range||best!=null&&distance>=best.distance)continue;
     var inside=nearest.lerp(box.getCenter().add(x,y,z),.001);var point=body.logicalPose().transformPosition(inside);var ray=level.clip(new ClipContext(center,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null));
     if(ray.getType()==HitResult.Type.MISS||ray.getBlockPos().equals(pos)||core.creatureId().equals(ownership.owner(ray.getBlockPos()))){var actual=ray.getType()==HitResult.Type.BLOCK?ray.getBlockPos():pos;if(ownership.part(actual)==null)best=new Hit(actual,distance);}
    }
   }
  }return best;
 }
 private BodyExplosionDamage(){}
}
