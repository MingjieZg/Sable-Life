package dev.sablelife.testing;

import dev.sablelife.module.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class ReleaseNetworkClient {
    private static boolean started,joined;private static int ticks,phase,menuTicks,initialRevision,previousMenu=-1;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("sablelife.releaseNetworkClient"))return;var mc=Minecraft.getInstance();
        if(++ticks>2400){System.out.println("RELEASE_NETWORK_CLIENT_FAIL timeout "+phase);mc.stop();return;}
        if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
            started=true;String address="127.0.0.1:25591";
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            System.out.println("RELEASE_NETWORK_CLIENT_ORIGIN: "+dev.sablelife.SableLife.class.getProtectionDomain().getCodeSource().getLocation());
            ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Local release test",address,ServerData.Type.OTHER),false,null);return;
        }
        if(mc.player!=null)joined=true;
        if(joined&&mc.player==null){System.out.println("RELEASE_NETWORK_CLIENT_COMPLETE phase="+phase);mc.stop();return;}
        if(mc.player==null||!(mc.player.containerMenu instanceof HeartDashboardMenu menu))return;
        var data=menu.snapshot();if(!data.getBoolean("Ready"))return;
        boolean second=System.getProperty("sablelife.networkRole","A").equals("B");
        if(phase==2&&data.getBoolean("SurvivalCore")&&data.getString("LifeState").equals("DORMANT")){
            System.out.println("RELEASE_NETWORK_CLIENT_OBSERVED_SHARED_RELEASE");phase=3;
        }
        if(phase==0){
            if(menuTicks++==0)initialRevision=data.getInt("Revision");
            if(menuTicks==(second?100:10)){
                PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,initialRevision,new UUID(0,0),"add","particles",0,""));
                System.out.println("RELEASE_NETWORK_CLIENT_EDIT role="+(second?"B":"A")+" revision="+initialRevision);previousMenu=menu.containerId;phase=1;
            }
        }else if(menu.containerId!=previousMenu&&(phase==1&&!data.getBoolean("SurvivalCore")||phase==2&&data.getBoolean("SurvivalCore"))&&data.getString("LifeState").equals("ALIVE")){
            PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,data.getInt("Revision"),new UUID(0,0),"release","",0,""));
            System.out.println("RELEASE_NETWORK_CLIENT_RELEASE survival="+data.getBoolean("SurvivalCore"));previousMenu=menu.containerId;phase++;
        }
    }
}
