package dev.sablelife.testing;

import dev.sablelife.client.*;
import dev.sablelife.module.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class ContactSensorNetworkClient {
    private static boolean started,joined;private static int ticks,phase,age,page,density;
    private static ModuleAnchor savedMark;
    private static boolean wheelChecked;
    private static net.minecraft.client.gui.components.EditBox pulseBox;
    private static Object field(Object o,String name)throws Exception {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void shot(String name)throws Exception {var mc=Minecraft.getInstance();var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(name+".png"));}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(!Boolean.getBoolean("sablelife.contactNetworkClient"))return;
        var mc=Minecraft.getInstance();boolean second=System.getProperty("sablelife.networkRole","A").equals("B");
        if(++ticks>2500)throw new AssertionError("Contact client timeout phase="+phase+" screen="+mc.screen);
        if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){started=true;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());System.out.println("CONTACT_CLIENT_ORIGIN "+dev.sablelife.SableLife.class.getProtectionDomain().getCodeSource().getLocation());String a="127.0.0.1:"+Integer.getInteger("sablelife.releaseNetworkPort",25591);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(a),new ServerData("Contact test",a,ServerData.Type.OTHER),false,null);return;}
        if(mc.player!=null)joined=true;
        if(joined&&mc.player==null){System.out.println("CONTACT_CLIENT_COMPLETE phase="+phase);mc.stop();return;}
        if(mc.player==null||mc.getOverlay()!=null)return;age++;
        mc.getToasts().clear();
        if(age%200==0)System.out.println("CONTACT_CLIENT_PHASE "+phase+" screen="+mc.screen);
        if(phase==0&&mc.screen instanceof HeartDashboardScreen screen&&age>20){
            var menu=(HeartDashboardMenu)mc.player.containerMenu;var tag=menu.snapshot();var m=VirtualModule.load(tag.getList("Modules",10).getCompound(0));
            if(!m.type().equals("contact_sensor"))throw new AssertionError("Wrong module");
            savedMark=m.anchors().getFirst();
            if(second){net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,-1,m.id(),"edit","contactDetectPlayers",1,""));}
            else {var buttons=(List<Button>)field(screen,"choices");if(buttons.getFirst().getWidth()>25||!buttons.getFirst().getMessage().getString().equals("✓"))throw new AssertionError("Contact types are not checkboxes");buttons.getFirst().onPress();}
            phase=1;age=0;
        } else if(phase==1&&age>15&&mc.screen instanceof HeartDashboardScreen){phase=2;age=0;}
        else if(phase==2&&age>10&&mc.screen instanceof HeartDashboardScreen screen){
            if(!second&&!wheelChecked&&density==0){
                var parameters=(List<ModuleFields.Field>)field(screen,"parameters");int first=(int)field(screen,"parameterPage")*(int)field(screen,"parameterSlots");
                for(int i=first;i<Math.min(parameters.size(),first+(int)field(screen,"parameterSlots"));i++)if(parameters.get(i).key().equals("contactPulse")){
                    pulseBox=((List<net.minecraft.client.gui.components.EditBox>)field(screen,"fields")).get(i-first);
                    if(!screen.mouseScrolled(pulseBox.getX()+5,pulseBox.getY()+5,0,1))throw new AssertionError("Pulse wheel ignored");phase=20;age=0;return;
                }
            }
            shot("contact-density"+density+"-page"+page);var next=(Button)field(screen,"nextParameters");
            if(next.visible&&next.active){next.onPress();page++;age=0;}
            else if(density==0){var button=screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().endsWith("%")).map(c->(Button)c).findFirst().orElseThrow();button.onPress();density++;page=0;var first=mc.screen.getClass().getDeclaredField("parameterPage");first.setAccessible(true);first.setInt(mc.screen,0);age=0;}
            else {var preview=(Button)field(screen,"preview");preview.onPress();phase=3;age=0;}
        } else if(phase==3&&age>15){shot("contact-preview-world");var body=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(mc.level).getSubLevel(savedMark.bodyId());var pos=savedMark.position(body);mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),net.minecraft.core.Direction.SOUTH,pos,false));phase=13;age=0;}
        else if(phase==13&&age>15&&mc.screen instanceof HeartDashboardScreen){shot("contact-preview-return");mc.setScreen(new ManualScreen(mc.screen,"contact_sensor"));phase=4;age=0;}
        else if(phase==4&&age>15){shot("contact-manual-usage");for(var child:mc.screen.children())if(child instanceof Button b&&(b.getMessage().getString().equalsIgnoreCase("Example")||b.getMessage().getString().equals("示例"))){b.onPress();break;}phase=5;age=0;}
        else if(phase==5&&age>15){shot("contact-manual-example");mc.screen.onClose();mc.player.connection.sendCommand("sablelife_contact_test_ack");phase=6;}
        else if(phase==20&&age>15&&mc.screen instanceof HeartDashboardScreen screen){
            var m=VirtualModule.load(((HeartDashboardMenu)mc.player.containerMenu).snapshot().getList("Modules",10).getCompound(0));if(m.value("contactPulse")!=5)throw new AssertionError("Pulse wheel did not add exactly 0.05 seconds: "+m.value("contactPulse"));
            screen.mouseScrolled(pulseBox.getX()+5,pulseBox.getY()+5,0,-1);phase=21;age=0;
        }else if(phase==21&&age>15){var m=VirtualModule.load(((HeartDashboardMenu)mc.player.containerMenu).snapshot().getList("Modules",10).getCompound(0));if(m.value("contactPulse")!=4)throw new AssertionError("Pulse reverse wheel failed");wheelChecked=true;phase=2;age=0;System.out.println("CONTACT_WHEEL_PASS 0.05_seconds_both_directions");}
    }
}
