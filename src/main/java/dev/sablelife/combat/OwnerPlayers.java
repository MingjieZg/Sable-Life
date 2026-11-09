package dev.sablelife.combat;
import dev.sablelife.attack.*;
import dev.sablelife.ownership.LoadedCoreIndex;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import java.util.UUID;
/** Owner is a per-core follow relation, never an attacking player attribution. */
public final class OwnerPlayers {
    public static boolean protectedPlayer(Entity target,UUID coreId,String launchOwner){
        if(!(target instanceof Player p)||!(target.level() instanceof ServerLevel level))return false;
        if(launchOwner!=null&&!launchOwner.isEmpty()&&launchOwner.equalsIgnoreCase(p.getGameProfile().getName()))return true;
        var core=coreId==null?null:LoadedCoreIndex.find(level,coreId);return core!=null&&core.relations().isOwner(p);
    }
    public static boolean protectedFromProjectile(Entity target,Entity projectile){var t=projectile.getPersistentData();return protectedPlayer(target,t.hasUUID("SableLifeAttackOwner")?t.getUUID("SableLifeAttackOwner"):null,t.getString(ModuleDamage.ATTACK_PLAYER_OWNER));}
    public static boolean protectedFrom(Entity target,DamageSource source){
        UUID core=source instanceof CreatureAttackSource attack?attack.creatureOwner():null;var direct=source.getDirectEntity();
        if(core==null&&direct!=null&&direct.getPersistentData().hasUUID("SableLifeAttackOwner"))core=direct.getPersistentData().getUUID("SableLifeAttackOwner");
        return protectedPlayer(target,core,ModuleDamage.playerOwner(source));
    }
    public static void incoming(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event){if(protectedFrom(event.getEntity(),event.getSource()))event.setCanceled(true);}
    private OwnerPlayers(){}
}
