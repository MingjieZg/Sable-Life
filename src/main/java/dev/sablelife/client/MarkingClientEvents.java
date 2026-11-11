package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.ModuleNetworking;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

@EventBusSubscriber(modid = SableLife.MOD_ID, value = Dist.CLIENT)
public final class MarkingClientEvents {
    @SubscribeEvent public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(!HeartDashboardScreen.marking||!event.isUseItem()||mc.player==null||!mc.player.isShiftKeyDown()||mc.hitResult==null||mc.hitResult.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)return;
        event.setCanceled(true);event.setSwingHand(false);
        if(event.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND)PacketDistributor.sendToServer(new ModuleNetworking.Action(-1,0,new UUID(0,0),"clear_mark","",0,""));
    }
    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (event.getKey() == 256 && event.getAction() == 1 && HeartDashboardScreen.marking) {
            PacketDistributor.sendToServer(new ModuleNetworking.Action(-1, 0, new UUID(0, 0), "cancel", "", 0, ""));
            HeartDashboardScreen.marking = false;
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        HeartDashboardScreen.marking = false;
        DashboardPageMemory.clear();
        FlameJetParticle.resetBudget();
        dev.sablelife.module.DatapackModules.disconnectClient();
    }
}
