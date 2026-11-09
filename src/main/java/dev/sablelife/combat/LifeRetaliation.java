package dev.sablelife.combat;

import dev.sablelife.attack.CreatureAttackSource;
import dev.sablelife.ownership.LoadedCoreIndex;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import java.util.*;

/** Supply normal retaliation memory after actual damage; never add AI goals or force a target. */
public final class LifeRetaliation {
    public static void damaged(LivingDamageEvent.Post event){
        if(!(event.getEntity() instanceof Mob victim)||victim instanceof EntityHitbox||!victim.isAlive()
                ||!(victim.level() instanceof ServerLevel level)||!Float.isFinite(event.getNewDamage())||event.getNewDamage()<=0)return;
        var source=event.getSource();
        // Preserve genuine entity attackers; this bridge only supplies the missing living attacker.
        if(source.getEntity() instanceof LivingEntity||source.is(net.minecraft.tags.DamageTypeTags.NO_ANGER))return;
        UUID id=source instanceof CreatureAttackSource attack?attack.creatureOwner():null;
        var direct=source.getDirectEntity();if(id==null&&direct!=null&&direct.getPersistentData().hasUUID("SableLifeAttackOwner"))id=direct.getPersistentData().getUUID("SableLifeAttackOwner");
        var core=id==null?null:LoadedCoreIndex.find(level,id);if(core==null)return;
        var receivers=core.modules().damageBoxes.entities().stream().filter(e->core.modules().damageBoxes.active(core,e.moduleId())!=null).toList();
        if(receivers.isEmpty())return;
        EntityHitbox receiver=victim.getLastHurtByMob() instanceof EntityHitbox previous&&receivers.contains(previous)?previous:
                receivers.stream().min(Comparator.comparingDouble(e->distance(victim,e))).orElseThrow();
        victim.setLastHurtByMob(receiver);
    }
    static double distance(Mob victim,EntityHitbox receiver){var point=victim.getBoundingBox().getCenter();return point.distanceToSqr(HitboxMobGeometry.closest(receiver,point));}
    private LifeRetaliation(){}
}
