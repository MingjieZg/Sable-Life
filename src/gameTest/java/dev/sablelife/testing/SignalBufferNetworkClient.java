package dev.sablelife.testing;
import dev.sablelife.client.*;
import dev.sablelife.module.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class SignalBufferNetworkClient {
 static boolean started,joined;static int ticks,phase,age,stage,lastContainer=-1;static boolean second;
 static Object field(Object o,String key)throws Exception{var f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
 static void shot(String name)throws Exception{var mc=Minecraft.getInstance();var p=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(p);try(var i=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){i.writeToFile(p.resolve(name+".png"));}}
 static int bufferRow(HeartDashboardScreen s)throws Exception{var fields=(List<ModuleFields.Field>)field(s,"parameters");int slots=(int)field(s,"parameterSlots");for(int i=0;i<fields.size();i++)if(fields.get(i).key().equals("redstoneBufferTime")){int target=i/slots;var p=s.getClass().getDeclaredField("parameterPage");p.setAccessible(true);if(p.getInt(s)!=target){p.setInt(s,target);age=0;return -1;}return i%slots;}throw new AssertionError("Buffer row missing");}
 static Button button(String key){return Minecraft.getInstance().screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals(key)).findFirst().orElseThrow();}
 @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception{if(!Boolean.getBoolean("sablelife.signalBufferNetworkClient"))return;var mc=Minecraft.getInstance();second=System.getProperty("sablelife.networkRole","A").equals("B");if(++ticks>3400)throw new AssertionError("Buffer client timeout phase="+phase);
  if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){started=true;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());System.out.println("SIGNAL_BUFFER_CLIENT_ORIGIN "+dev.sablelife.SableLife.class.getProtectionDomain().getCodeSource().getLocation());String a="127.0.0.1:"+Integer.getInteger("sablelife.releaseNetworkPort",25591);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(a),new ServerData("Buffer test",a,ServerData.Type.OTHER),false,null);return;}
  if(mc.player!=null)joined=true;if(joined&&mc.player==null){System.out.println("SIGNAL_BUFFER_CLIENT_COMPLETE phase="+phase);mc.stop();return;}if(mc.player==null||mc.getOverlay()!=null)return;age++;mc.getToasts().clear();for(var key:List.of("xpos","ypos")){try{var f=mc.mouseHandler.getClass().getDeclaredField(key);f.setAccessible(true);f.setDouble(mc.mouseHandler,5);}catch(NoSuchFieldException ignored){}}
  if(mc.player.containerMenu instanceof HeartDashboardMenu menu&&mc.screen instanceof HeartDashboardScreen s){var m=VirtualModule.load(menu.snapshot().getList("Modules",10).getCompound(0));if(lastContainer!=menu.containerId){lastContainer=menu.containerId;stage=m.value("redstoneStrengthMode")==2?2:m.value("redstoneMode")==1?1:0;phase=1;age=0;}if(phase==9||age<12)return;int row=bufferRow(s);if(row<0)return;var box=((List<EditBox>)field(s,"fields")).get(row);if(!box.active||!box.visible)throw new AssertionError("Buffer input disabled in stage "+stage);
   if(phase==1){if(second){if(m.value("redstoneBufferTime")!=6)return;phase=4;age=0;}else{box.setValue("0.25");((List<Button>)field(s,"applyButtons")).get(row).onPress();phase=2;age=0;}}
   else if(phase==2){if(m.value("redstoneBufferTime")!=5)return;s.mouseScrolled(box.getX()+2,box.getY()+2,0,1);phase=3;age=0;}
   else if(phase==3){if(m.value("redstoneBufferTime")!=6)return;phase=4;age=0;}
   else if(phase==4){shot("buffer-stage"+stage+"-density0");s.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().endsWith("%")).map(c->(Button)c).findFirst().orElseThrow().onPress();phase=5;age=0;}
   else if(phase==5){shot("buffer-stage"+stage+"-density1");if(stage==0){mc.setScreen(new ManualScreen(s,"wireless_redstone"));phase=6;age=0;}else{mc.player.connection.sendCommand("sablelife_buffer_ack "+stage);phase=9;}}
  }else if(phase==6&&age>12&&mc.screen instanceof ManualScreen){shot("buffer-manual-usage");button("manual.sablelife.example").onPress();phase=7;age=0;}
  else if(phase==7&&age>12&&mc.screen instanceof ManualScreen){shot("buffer-manual-example");mc.screen.onClose();mc.player.connection.sendCommand("sablelife_buffer_ack "+stage);phase=9;age=0;}
 }
}
