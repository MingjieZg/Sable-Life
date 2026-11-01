package dev.sablelife.module;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.*;
/** One refresh per server second, including repeated tick calls; no effect-removal or player retention. */
public final class ArenaBuffRuntime {
 private long next=Long.MIN_VALUE,last=Long.MIN_VALUE;
 public void clear(){next=last=Long.MIN_VALUE;}
 public void tick(HeartBlockEntity heart){
  if(!(heart.getLevel() instanceof ServerLevel level)||!heart.operational()){clear();return;}
  long now=level.getGameTime();if(now<last)clear();if(now<next)return;last=now;next=now+ArenaBuffs.REFRESH;
  for(var module:heart.modules().all()){
   if(!module.type().equals("arena")||module.value("arenaBuffEnabled")!=1||!HeartModules.triggered(module,heart))continue;
   var effects=new LinkedHashMap<String,ModuleEffects.Entry>();
   for(var group:module.arenaBuffs().groups())if(group.powered(heart))for(var effect:group.editor().effects())
    effects.merge(effect.id(),effect,(a,b)->a.level()>=b.level()?a:b);
   if(effects.isEmpty())continue;var center=ModuleArena.corePosition(heart);double range=module.value("warningRange");
   for(var player:level.players()){
    if(!player.isAlive()||player.isCreative()||player.isSpectator()||ModuleArena.horizontalDistance(player.position(),center)>range)continue;
    for(var entry:effects.values()){
     var effect=entry.instance();if(effect==null||effect.getEffect().value().isInstantenous())continue;
     var current=player.getEffect(effect.getEffect());
     if(current!=null&&current.getAmplifier()>=effect.getAmplifier()&&(current.isInfiniteDuration()||current.getDuration()>=ArenaBuffs.DURATION))continue;
     player.addEffect(new MobEffectInstance(effect.getEffect(),ArenaBuffs.DURATION,effect.getAmplifier()));
    }
   }
  }
 }
}
