package dev.sablelife.compat.ironspells;
import dev.sablelife.api.BodyCombatApi;
import dev.sablelife.combat.BodyExplosionDamage;
import dev.sablelife.compat.sable.SableBodyProbe;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.magic_missile.MagicMissileProjectile;
import io.redspace.ironsspellbooks.entity.spells.magic_arrow.MagicArrowProjectile;
import io.redspace.ironsspellbooks.entity.spells.fire_arrow.FireArrowProjectile;
import io.redspace.ironsspellbooks.entity.spells.fireball.*;
import io.redspace.ironsspellbooks.spells.lightning.ShockwaveSpell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;
/** Optional typed bridge; raw damage already includes the spell's caster/level calculation. */
public final class IronSpellsBodyBridge {
 private static final ConcurrentHashMap<Class<?>,ToDoubleFunction<AbstractMagicProjectile>> DIRECT=new ConcurrentHashMap<>();
 static{DIRECT.put(MagicMissileProjectile.class,AbstractMagicProjectile::getDamage);DIRECT.put(MagicArrowProjectile.class,AbstractMagicProjectile::getDamage);DIRECT.put(SmallMagicFireball.class,AbstractMagicProjectile::getDamage);}
 /** Experimental addon opt-in: the class must retain the base onHit pipeline and direct-damage semantics. */
 public static synchronized <T extends AbstractMagicProjectile> boolean registerDirectProjectile(Class<T> type,ToDoubleFunction<? super T> damage){
  if(type==null||damage==null||!AbstractMagicProjectile.class.isAssignableFrom(type)||java.lang.reflect.Modifier.isAbstract(type.getModifiers())||DIRECT.size()>=128&&!DIRECT.containsKey(type))return false;DIRECT.put(type,p->damage.applyAsDouble(type.cast(p)));return true;
 }
 public static HitResult worldHit(AbstractMagicProjectile shot,HitResult hit){
  if(hit instanceof BlockHitResult block&&hit.getType()==HitResult.Type.BLOCK){
   var point=SableBodyProbe.toWorld(shot.level(),hit.getLocation());
   if(!point.equals(hit.getLocation()))return new BlockHitResult(point,block.getDirection(),block.getBlockPos(),block.isInside());
  }return hit;
 }
 public static void direct(AbstractMagicProjectile shot,HitResult hit){
  if(!(shot.level() instanceof ServerLevel)||shot.isRemoved()||!(hit instanceof BlockHitResult block)||hit.getType()!=HitResult.Type.BLOCK)return;
  for(Class<?> type=shot.getClass();type!=null&&AbstractMagicProjectile.class.isAssignableFrom(type);type=type.getSuperclass()){
   var profile=DIRECT.get(type);if(profile==null)continue;double damage=profile.applyAsDouble(shot);if(Double.isFinite(damage)&&damage>0){
    if(shot instanceof MagicArrowProjectile){
     var core=SableBodyProbe.findActivatedHeart(shot.level(),block.getBlockPos());
     if(core!=null&&!core.isControlCore()&&core.health().state()==dev.sablelife.heart.HeartState.ALIVE){
      var tried=shot.getPersistentData().getCompound("SableLifeIronArrowAttempts");String id=core.creatureId().toString();
      if(tried.getBoolean(id)||tried.size()>=64)return;tried.putBoolean(id,true);shot.getPersistentData().put("SableLifeIronArrowAttempts",tried);
     }
    }
    BodyCombatApi.projectile(shot,block,(float)Math.min(damage,dev.sablelife.heart.HeartHealth.MAX_CONFIGURED_HEALTH));
   }return;
  }
 }
 public static void blast(AbstractMagicProjectile shot,HitResult hit){
  if(!(shot.level() instanceof ServerLevel level)||shot.isRemoved()||hit==null||hit.getType()==HitResult.Type.MISS)return;
  if(!shot.getPersistentData().getBoolean("SableLifeIronBlastHandled")){
   shot.getPersistentData().putBoolean("SableLifeIronBlastHandled",true);
   java.util.UUID direct=null;
   if(shot instanceof FireArrowProjectile&&hit instanceof BlockHitResult block){
    var core=SableBodyProbe.findActivatedHeart(level,block.getBlockPos());if(core!=null){direct=core.creatureId();BodyCombatApi.projectile(shot,block,shot.getDamage());}
   }
   var excluded=direct==null?java.util.Set.<java.util.UUID>of():java.util.Set.of(direct);
   var center=SableBodyProbe.toWorld(level,hit.getLocation());double radius=shot.getExplosionRadius();float damage=shot.getDamage()*(shot instanceof FireArrowProjectile?.5F:1);
   // Match the original explosion's raised visibility origin, not TNT damage or destruction settings.
   var eye=io.redspace.ironsspellbooks.api.util.Utils.raycastForBlock(level,shot.position(),shot.position().add(0,2,0),net.minecraft.world.level.ClipContext.Fluid.NONE).getLocation();
   if(Float.isFinite(damage)&&damage>0&&Double.isFinite(radius)&&radius>0)BodyCombatApi.sphere(shot,center,eye,radius,distance->damage*Math.max(0,1-distance*distance/(radius*radius)),excluded);
  }
 }
 public static void shockwave(ShockwaveSpell spell,net.minecraft.world.level.Level level,int spellLevel,LivingEntity caster){
  if(!(level instanceof ServerLevel)||caster==null||caster.isRemoved()||!caster.isAlive())return;
  float damage=spell.getDamage(spellLevel,caster);double radius=spell.getRadius(spellLevel,caster);
  if(Float.isFinite(damage)&&damage>0)BodyCombatApi.sphere(caster,caster.position(),caster.getEyePosition(),radius,distance->damage);
 }
 private IronSpellsBodyBridge(){}
}
