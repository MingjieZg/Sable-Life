package dev.sablelife.heart;

import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class CorePlacementRules {
    public static void placed(BlockEvent.EntityPlaceEvent event){
        if(!(event.getLevel() instanceof Level level)||level.isClientSide||!(level.getBlockState(event.getPos()).getBlock() instanceof HeartBlock))return;
        if(SableBodyProbe.findConnectedHearts(level,event.getPos()).size()>1){event.setCanceled(true);if(event.getEntity() instanceof Player player)player.displayClientMessage(Component.translatable("control.sablelife.duplicate"),true);}
    }
    private CorePlacementRules(){}
}
