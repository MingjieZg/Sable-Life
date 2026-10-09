package dev.sablelife.heart;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.module.MarkingSessions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;

/** One release action shared by every editor entry point. Retains the machine and authoring configuration. */
public final class CoreRelease {
    public static boolean release(HeartBlockEntity heart,Player player){
        if(!player.isCreative()||heart.isControlCore()||!(heart.getLevel() instanceof ServerLevel level)||player.level()!=level||heart.isRemoved()||level.getBlockEntity(heart.getBlockPos())!=heart)return false;
        var hearts=SableBodyProbe.findConnectedHearts(level,heart.getBlockPos());if(hearts.isEmpty())hearts=java.util.List.of(heart);
        MarkingSessions.cancel(player);int reset=0;for(var core:hearts)if(core.resetForCreative(player))reset++;
        if(reset==0)return false;
        var point=SableBodyProbe.toWorld(level,heart.getBlockPos().getCenter());level.playSound(null,point.x,point.y,point.z,net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.BLOCKS,.7f,1.2f);
        player.closeContainer();player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.sablelife.released",reset),true);return true;
    }
    private CoreRelease(){}
}
