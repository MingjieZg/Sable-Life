package dev.sablelife.attack;

import dev.sablelife.SableLife;
import dev.sablelife.ownership.LoadedCoreIndex;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Confirmed player deaths only; totems and canceled deaths do not reward a boss. */
@EventBusSubscriber(modid=SableLife.MOD_ID)
public final class CreatureKillHealing {
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void died(LivingDeathEvent event){
        if(event.isCanceled())return;
        if(!(event.getEntity() instanceof ServerPlayer player)||player.isCreative()||player.isSpectator())return;
        var source=event.getSource();
        java.util.UUID owner=source instanceof CreatureAttackSource credited?credited.creatureOwner():null;
        var projectile=source.getDirectEntity();
        if(owner==null&&projectile!=null&&projectile.getPersistentData().hasUUID("SableLifeAttackOwner"))owner=projectile.getPersistentData().getUUID("SableLifeAttackOwner");
        if(owner==null)return;
        var heart=LoadedCoreIndex.find(player.serverLevel(),owner);
        if(heart!=null)heart.healFromPlayerKill();
    }
    private CreatureKillHealing(){}
}
