package dev.sablelife.attack;

import dev.sablelife.SableLife;
import dev.sablelife.ownership.LoadedCoreIndex;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.*;
import java.util.UUID;

/** Only authoritative Sable attack attribution may emit events on its own core. */
@EventBusSubscriber(modid=SableLife.MOD_ID)
public final class CombatEventHooks {
 public static UUID module(DamageSource source){if(source instanceof CreatureAttackSource c&&c.creatureModule()!=null)return c.creatureModule();var e=source.getDirectEntity();return e!=null&&e.getPersistentData().hasUUID(AttackHitCredit.MODULE)?e.getPersistentData().getUUID(AttackHitCredit.MODULE):null;}
 public static void emit(ServerLevel level,DamageSource source,int kind,int filter,UUID target){
  UUID owner=source instanceof CreatureAttackSource c?c.creatureOwner():null;var e=source.getDirectEntity();if(owner==null&&e!=null&&e.getPersistentData().hasUUID("SableLifeAttackOwner"))owner=e.getPersistentData().getUUID("SableLifeAttackOwner");
  var heart=owner==null?null:LoadedCoreIndex.find(level,owner);if(heart!=null)heart.modules().combatEvents.emit(heart,kind,filter,module(source),target);
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void died(LivingDeathEvent event){
  if(event.isCanceled()||!(event.getEntity().level() instanceof ServerLevel level))return;var victim=event.getEntity();
  if(victim instanceof net.minecraft.world.entity.player.Player p&&(p.isCreative()||p.isSpectator()))return;
  emit(level,event.getSource(),0,victim instanceof net.minecraft.world.entity.player.Player?0:1,victim.getUUID());
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void shield(LivingShieldBlockEvent event){
  if(event.isCanceled()||!event.getBlocked()||event.getBlockedDamage()<=0||!(event.getEntity().level() instanceof ServerLevel level))return;
  if(event.getDamageSource() instanceof BodyMeleeSource body)body.blocked(1);
  else emit(level,event.getDamageSource(),2,1,event.getEntity().getUUID());
 }
 public static void blocked(LivingEntity victim,BodyMeleeSource source){if(source.blockKind()>0&&victim.level() instanceof ServerLevel level)emit(level,source,2,source.blockKind(),victim.getUUID());}
 public static void killed(dev.sablelife.heart.HeartBlockEntity attacker,UUID module,dev.sablelife.heart.HeartBlockEntity victim){if(victim.health().state()!=dev.sablelife.heart.HeartState.ALIVE)attacker.modules().combatEvents.emit(attacker,0,2,module,victim.creatureId());}
 public static void killed(net.minecraft.world.entity.Entity projectile,dev.sablelife.heart.HeartBlockEntity victim){if(!(projectile.level() instanceof ServerLevel level)||victim.health().state()==dev.sablelife.heart.HeartState.ALIVE)return;var tag=projectile.getPersistentData();if(!tag.hasUUID("SableLifeAttackOwner"))return;var attacker=LoadedCoreIndex.find(level,tag.getUUID("SableLifeAttackOwner"));if(attacker!=null)killed(attacker,tag.hasUUID(AttackHitCredit.MODULE)?tag.getUUID(AttackHitCredit.MODULE):null,victim);}
 private CombatEventHooks(){}
}
