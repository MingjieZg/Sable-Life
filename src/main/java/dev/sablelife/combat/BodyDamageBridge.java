package dev.sablelife.combat;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.Explosion;
import java.lang.reflect.*;
import java.util.*;
/** Explicit hit-to-core transfer: no living entities or proxy hitboxes are spawned. */
public final class BodyDamageBridge {
 private static final String HITS="SableLifeHitCreatures";
 public interface BlastProfile{double sablelife$blastRange();float sablelife$blastDamage(double distance);}
 public static boolean hit(Entity source,BlockHitResult hit,float amount){
  if(!(source.level() instanceof ServerLevel level)||source.isRemoved()||!Float.isFinite(amount)||amount<=0)return false;var core=SableBodyProbe.findActivatedHeart(level,hit.getBlockPos());if(core==null||core.isControlCore()||core.health().state()!=HeartState.ALIVE)return false;
  var data=source.getPersistentData();if(data.hasUUID("SableLifeAttackOwner")&&!core.acceptsLifeAttack(data.getUUID("SableLifeAttackOwner")))return false;
  var hits=data.getCompound(HITS);var key=core.creatureId().toString();if(hits.getBoolean(key)||hits.getAllKeys().size()>=64)return false;float before=core.health().current();if(!core.damageAt(hit.getBlockPos(),Math.min(amount,HeartHealth.MAX_CONFIGURED_HEALTH)))return false;
  hits.putBoolean(key,true);data.put(HITS,hits);BodyCombat.feedback(level,hit,core,before-core.health().current(),source instanceof Projectile p&&p.getOwner() instanceof net.minecraft.world.entity.player.Player player?player:null,false);return true;
 }
 public static boolean melee(net.minecraft.server.level.ServerPlayer player,HeartBlockEntity core,net.minecraft.core.BlockPos block,Vec3 point,float amount) {
  var level=player.serverLevel();
  if(player.isSpectator()||core.getLevel()!=level||core.isRemoved()||core.isControlCore()||!Float.isFinite(amount)||amount<=0
      ||!core.creatureId().equals(dev.sablelife.ownership.BodyOwnership.get(level).owner(block)))return false;
  float before=core.health().current();if(!core.damageAt(block,Math.min(amount,HeartHealth.MAX_CONFIGURED_HEALTH)))return false;
  BodyCombat.feedback(level,new BlockHitResult(point,net.minecraft.core.Direction.UP,block,false),core,before-core.health().current(),player,false);return true;
 }
 private static final ClassValue<Optional<Method>> DAMAGE=new ClassValue<>(){protected Optional<Method> computeValue(Class<?> type){for(String key:List.of("getDamage","getBaseDamage"))for(Class<?>[] args:List.of(new Class<?>[]{Vec3.class},new Class<?>[]{}))try{var method=type.getMethod(key,args);if(Number.class.isAssignableFrom(box(method.getReturnType())))return Optional.of(method);}catch(NoSuchMethodException ignored){}return Optional.empty();}};
 private static Class<?> box(Class<?> type){return type==float.class?Float.class:type==double.class?Double.class:type==int.class?Integer.class:type;}
 public static float projectileDamage(Entity source,Vec3 point){try{var method=DAMAGE.get(source.getClass()).orElse(null);if(method==null)return 0;return ((Number)(method.getParameterCount()==0?method.invoke(source):method.invoke(source,point))).floatValue();}catch(ReflectiveOperationException e){return 0;}}
 public static void projectile(Entity source,BlockHitResult hit){hit(source,hit,projectileDamage(source,SableBodyProbe.toWorld(source.level(),hit.getLocation())));}
 private static final ClassValue<Optional<Field>> CANNON_DAMAGE=new ClassValue<>(){protected Optional<Field> computeValue(Class<?> type){for(var c=type;c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField("damage");f.setAccessible(true);return Optional.of(f);}catch(NoSuchFieldException ignored){}return Optional.empty();}};
 public static void cannon(Entity source,BlockHitResult hit){try{var field=CANNON_DAMAGE.get(source.getClass()).orElse(null);if(field!=null)hit(source,hit,((Number)field.get(source)).floatValue());}catch(IllegalAccessException ignored){}}
 public static double explosionRange(Explosion e){if(e instanceof BlastProfile profile)return profile.sablelife$blastRange();try{return ((Number)e.getClass().getMethod("getEntityRadius").invoke(e)).doubleValue()*2;}catch(ReflectiveOperationException ignored){return e.radius()*2;}}
 public static float explosionDamage(Explosion e,double distance,double range){if(e instanceof BlastProfile profile)return profile.sablelife$blastDamage(distance);double impact=Math.max(0,1-distance/range);return (float)((impact*impact+impact)*.5*7*range+1);}
 private BodyDamageBridge(){}
}
