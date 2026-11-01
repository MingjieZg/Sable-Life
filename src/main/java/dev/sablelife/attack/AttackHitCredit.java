package dev.sablelife.attack;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.VirtualModule;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
/** Constant-time projectile ownership lookup; never scan the world for an attacker. */
public final class AttackHitCredit {
 public static final String MODULE="SableLifeAttackModule";
 public static void attach(Entity projectile,HeartBlockEntity heart,VirtualModule module){projectile.getPersistentData().putUUID("SableLifeAttackOwner",heart.creatureId());projectile.getPersistentData().putUUID(MODULE,module.id());}
 public static void record(Entity projectile,double loss){
  if(projectile==null||loss<=0||!(projectile.level() instanceof ServerLevel level))return;var tag=projectile.getPersistentData();
  if(!tag.hasUUID("SableLifeAttackOwner")||!tag.hasUUID(MODULE))return;var core=dev.sablelife.ownership.LoadedCoreIndex.find(level,tag.getUUID("SableLifeAttackOwner"));
  if(core!=null)core.modules().hitCounter.record(core,tag.getUUID(MODULE),loss);
 }
 private AttackHitCredit(){}
}
