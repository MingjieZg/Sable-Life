package dev.sablelife.client;
import dev.sablelife.SableLife;
import dev.sablelife.module.FreezeStatePacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT)
public final class FreezeClient {
    private static net.minecraft.client.multiplayer.ClientLevel world;private static long until;private static net.minecraft.world.phys.Vec3 anchor;
    public static void receive(FreezeStatePacket p){var mc=Minecraft.getInstance();if(mc.level==null||p.ticks()<0||p.ticks()>1200||!Double.isFinite(p.anchor().x+p.anchor().y+p.anchor().z))return;world=mc.level;until=world.getGameTime()+p.ticks();anchor=p.anchor();}
    public static boolean active(){var mc=Minecraft.getInstance();return world!=null&&mc.level==world&&mc.player!=null&&mc.player.isAlive()&&!mc.player.isSpectator()&&!mc.player.isCreative()&&world.getGameTime()<until;}
    @SubscribeEvent public static void input(MovementInputUpdateEvent e){if(active()){var in=e.getInput();in.forwardImpulse=in.leftImpulse=0;in.up=in.down=in.left=in.right=in.jumping=in.shiftKeyDown=false;}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){var mc=Minecraft.getInstance();if(mc.level!=world||mc.player==null){world=null;anchor=null;return;}if(!active())return;var p=mc.player;p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);p.setPos(anchor);p.fallDistance=0;}
    private FreezeClient(){}
}
