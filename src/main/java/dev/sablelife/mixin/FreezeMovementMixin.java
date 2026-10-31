package dev.sablelife.mixin;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class FreezeMovementMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int aboveGroundTickCount;
    @Inject(method="tick",at=@At("HEAD"))
    private void sablelife$allowFrozenSuspension(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if(dev.sablelife.module.PlayerFreeze.frozen(player)){clientIsFloating=false;aboveGroundTickCount=0;}
    }
    @ModifyVariable(method="handleMovePlayer",at=@At("HEAD"),argsOnly=true)
    private ServerboundMovePlayerPacket sablelife$freeze(ServerboundMovePlayerPacket packet){
        // The network dispatcher can enter once on Netty before rescheduling on the server thread.
        return player.server.isSameThread()?dev.sablelife.module.PlayerFreeze.constrain(player,packet):packet;
    }
}
