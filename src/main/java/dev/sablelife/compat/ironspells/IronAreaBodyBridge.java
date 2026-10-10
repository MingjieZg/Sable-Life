package dev.sablelife.compat.ironspells;

import dev.sablelife.api.BodyCombatApi;
import dev.sablelife.attack.AttackVolume;
import dev.sablelife.combat.BodySphereQuery;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.*;
import org.joml.Quaterniond;
import java.util.*;

public final class IronAreaBodyBridge {
 public static AttackVolume volume(AABB box){return AttackVolume.box(box.getCenter(),new Vec3(box.getXsize(),box.getYsize(),box.getZsize()).scale(.5),new Quaterniond());}
 public static boolean box(Entity source,AABB box,Vec3 eye,float damage,Set<UUID> seen){
  if(!(source.level() instanceof ServerLevel level)||source.isRemoved()||!Float.isFinite(damage)||damage<=0)return false;
  boolean applied=false;
  for(var hit:BodySphereQuery.queryVolumes(level,eye,List.of(volume(box)))){
   if(seen.size()>=64)break;if(!seen.add(hit.core().creatureId()))continue;
   if(BodyCombatApi.contact(source,new BlockHitResult(hit.point(),Direction.UP,hit.block(),false),damage)>0)applied=true;
  }return applied;
 }
 public static void shadowContact(LivingEntity caster,BlockHitResult hit,float damage,Set<UUID> seen){
  if(!(caster.level() instanceof ServerLevel level)||!caster.isAlive()||hit.getType()!=HitResult.Type.BLOCK)return;
  var core=SableBodyProbe.findActivatedHeart(level,hit.getBlockPos());if(core==null||core.isControlCore())return;
  var eye=caster.getEyePosition();if(hit.getLocation().distanceToSqr(eye)>12*12)return;
  var forward=caster.getForward();var candidates=caster.getHitbox().expandTowards(hit.getLocation().subtract(eye)).inflate(2);
  boolean closer=level.getEntities(caster,candidates,e->!e.isSpectator()&&(e instanceof LivingEntity||e instanceof Projectile)&&e.getBoundingBox().getCenter().subtract(caster.getBoundingBox().getCenter()).normalize().dot(forward)>=.85)
    .stream().anyMatch(e->e.distanceToSqr(caster)<hit.getLocation().distanceToSqr(caster.position())&&e.distanceToSqr(caster)<12*12);
  if(!closer)box(caster,AABB.ofSize(hit.getLocation(),2.5,3.5,2.5).move(forward.scale(1.25)),eye,damage,seen);
 }
 public static boolean area(io.redspace.ironsspellbooks.entity.spells.AoeEntity area,AABB bounds){
  if(!(area.level() instanceof ServerLevel level)||area.isRemoved()||!Float.isFinite(area.getDamage())||area.getDamage()<=0)return false;
  // Healing and freeze-only fields can also have a positive damage field; never guess from that value.
  if(!(area instanceof io.redspace.ironsspellbooks.entity.spells.magma_ball.FireField||area instanceof io.redspace.ironsspellbooks.entity.spells.poison_cloud.PoisonCloud||area instanceof io.redspace.ironsspellbooks.entity.spells.sunbeam.SunbeamEntity||area instanceof io.redspace.ironsspellbooks.entity.spells.EarthquakeAoe))return false;
  var shape=volume(bounds);var hits=area.isCircular()?BodySphereQuery.queryRegion(level,area.position(),area.position(),area.getRadius(),shape):BodySphereQuery.queryVolumes(level,area.position(),List.of(shape));
  boolean applied=false;
  for(var hit:hits){BodyCombatApi.contact(area,new BlockHitResult(hit.point(),Direction.UP,hit.block(),false),area.getDamage());applied=true;}
  return applied; // native applyEffect is void: an eligible immune contact still counts for onPostHit

 }
 public static void impact(io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile shot,HitResult hit){
  if(!(shot.level() instanceof ServerLevel level)||shot.isRemoved()||hit.getType()==HitResult.Type.MISS||shot.getPersistentData().getBoolean("SableLifeIronAreaImpact"))return;
  if(!Float.isFinite(shot.getDamage())||shot.getDamage()<=0||!Float.isFinite(shot.getExplosionRadius())||shot.getExplosionRadius()<=0)return;
  shot.getPersistentData().putBoolean("SableLifeIronAreaImpact",true);
  var center=SableBodyProbe.toWorld(level,hit.getLocation());boolean skull=shot instanceof io.redspace.ironsspellbooks.entity.spells.WitherSkullProjectile;
  UUID direct=null;
  if(skull&&hit instanceof BlockHitResult block){var core=SableBodyProbe.findActivatedHeart(level,block.getBlockPos());if(core!=null&&!core.isControlCore()){direct=core.creatureId();BodyCombatApi.projectile(shot,block,shot.getDamage());}}
  for(var contact:BodySphereQuery.query(level,center,shot.position(),shot.getExplosionRadius())){
   if(contact.core().creatureId().equals(direct))continue;
   float damage=shot.getDamage();
   if(skull){
    var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(level,contact.block());double width=body==null?1:body.logicalPose().transformPosition(contact.block().getCenter().add(1,0,0)).distanceTo(body.logicalPose().transformPosition(contact.block().getCenter()));
    double r=shot.getExplosionRadius()+width;damage*=(float)Math.max(0,1-contact.distance()*contact.distance()/(r*r));
   }
   if(shot instanceof io.redspace.ironsspellbooks.entity.spells.creeper_head.CreeperHeadProjectile){double radius=shot.getExplosionRadius();damage*=(float)Math.max(0,1-contact.distance()*contact.distance()/(radius*radius));}
   BodyCombatApi.contact(shot,new BlockHitResult(contact.point(),Direction.UP,contact.block(),false),damage);
  }
 }
 public static boolean iceContact(io.redspace.ironsspellbooks.entity.spells.ice_block.IceBlockProjectile shot){
  if(!(shot.level() instanceof ServerLevel level)||shot.isRemoved())return false;
  var hits=BodySphereQuery.queryVolumes(level,shot.position(),List.of(volume(shot.getBoundingBox().inflate(.35))));if(hits.isEmpty())return false;
  var hit=hits.getFirst();BodyCombatApi.projectile(shot,new BlockHitResult(hit.point(),Direction.UP,hit.block(),false),shot.getDamage()/2);shot.impactParticles(shot.getX(),shot.getY(),shot.getZ());shot.discard();return true;
 }
 public static void iceBurst(io.redspace.ironsspellbooks.entity.spells.ice_block.IceBlockProjectile shot){
  if(shot.isRemoved()||shot.getPersistentData().getBoolean("SableLifeIceBurst"))return;shot.getPersistentData().putBoolean("SableLifeIceBurst",true);
  BodyCombatApi.sphere(shot,shot.position(),shot.position(),3.5,d->shot.getDamage()*Math.max(0,1-Math.pow(d/3.5,3)));
 }
 private IronAreaBodyBridge(){}
}
