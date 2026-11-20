package dev.sablelife.testing;
import dev.sablelife.client.*;
import dev.sablelife.heart.HeartSettingsMenu;
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
public final class TerrainMeleeNetworkClient {
 static boolean started,joined,toggled;static int ticks,phase,age,page,density;
 static Object field(Object o,String key)throws Exception{var f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
 static void shot(String name)throws Exception{var mc=Minecraft.getInstance();var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve(name+".png"));}}
 static Button button(String key){return Minecraft.getInstance().screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals(key)).findFirst().orElseThrow();}
 static void clickField(HeartDashboardScreen s,String key)throws Exception{var fields=(List<ModuleFields.Field>)field(s,"parameters");int at=java.util.stream.IntStream.range(0,fields.size()).filter(i->fields.get(i).key().equals(key)).findFirst().orElseThrow();var page=HeartDashboardScreen.class.getDeclaredField("parameterPage");page.setAccessible(true);int slots=(int)field(s,"parameterSlots");page.setInt(s,at/slots);var sync=HeartDashboardScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);sync.invoke(s);var button=((List<Button>)field(s,"choices")).get(at%slots);if(!button.active||!button.visible)throw new AssertionError("Field unavailable: "+key);button.onPress();}
 @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception{
  if(!Boolean.getBoolean("sablelife.terrainMeleeNetworkClient"))return;var mc=Minecraft.getInstance();boolean second=System.getProperty("sablelife.networkRole","A").equals("B");
  if(++ticks>3300)throw new AssertionError("Melee client timeout phase="+phase+" screen="+mc.screen);
  if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){started=true;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());System.out.println("TERRAIN_MELEE_CLIENT_ORIGIN "+dev.sablelife.SableLife.class.getProtectionDomain().getCodeSource().getLocation());String a="127.0.0.1:"+Integer.getInteger("sablelife.releaseNetworkPort",25591);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(a),new ServerData("Melee test",a,ServerData.Type.OTHER),false,null);return;}
  if(mc.player!=null)joined=true;if(joined&&mc.player==null){System.out.println("TERRAIN_MELEE_CLIENT_COMPLETE phase="+phase);mc.stop();return;}if(mc.player==null||mc.getOverlay()!=null)return;age++;mc.getToasts().clear();if(mc.screen!=null)org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),5,5);
  if(phase==0&&mc.screen instanceof HeartDashboardScreen&&age>20){var menu=(HeartDashboardMenu)mc.player.containerMenu;var m=VirtualModule.load(menu.snapshot().getList("Modules",10).getCompound(0));if(!m.type().equals("melee"))throw new AssertionError("Wrong module");if(second)net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,-1,m.id(),"edit","meleeBreakBlocks",0,""));phase=2;age=0;}
  else if(phase==2&&age>12&&mc.screen instanceof HeartDashboardScreen s){
   if(!second&&!toggled){var params=(List<ModuleFields.Field>)field(s,"parameters");int first=(int)field(s,"parameterPage")*(int)field(s,"parameterSlots");for(int i=first;i<Math.min(params.size(),first+(int)field(s,"parameterSlots"));i++)if(params.get(i).key().equals("meleeBreakBlocks")){var choice=((List<Button>)field(s,"choices")).get(i-first);if(!choice.active||!choice.visible)throw new AssertionError("Terrain switch unavailable");choice.onPress();phase=8;age=0;return;}}
   shot("melee-density"+density+"-page"+page);var next=(Button)field(s,"nextParameters");if(next.visible&&next.active){next.onPress();page++;age=0;}else if(density==0){s.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().endsWith("%")).map(c->(Button)c).findFirst().orElseThrow().onPress();density++;page=0;var f=mc.screen.getClass().getDeclaredField("parameterPage");f.setAccessible(true);f.setInt(mc.screen,0);age=0;}else{if(!second&&!toggled)throw new AssertionError("Terrain switch not found");if(!second){clickField(s,"meleeDrops");phase=20;}else phase=23;age=0;}
  }else if(phase==8&&age>15&&mc.screen instanceof HeartDashboardScreen){var m=VirtualModule.load(((HeartDashboardMenu)mc.player.containerMenu).snapshot().getList("Modules",10).getCompound(0));if(m.value("meleeBreakBlocks")!=1)throw new AssertionError("Real toggle did not arrive");toggled=true;phase=2;age=0;}
  else if(phase==20&&age>15&&mc.screen instanceof HeartDashboardScreen s){var m=VirtualModule.load(((HeartDashboardMenu)mc.player.containerMenu).snapshot().getList("Modules",10).getCompound(0));if(m.value("meleeDrops")!=1)return;clickField(s,"meleeDebris");phase=21;age=0;}
  else if((phase==21||phase==23)&&age>15&&mc.screen instanceof HeartDashboardScreen s){var m=VirtualModule.load(((HeartDashboardMenu)mc.player.containerMenu).snapshot().getList("Modules",10).getCompound(0));if(m.value("meleeDrops")!=1||m.value("meleeDebris")!=1)return;shot("melee-breaking-options");clickField(s,"meleeProtected");phase=40;age=0;}
  else if(phase==40&&age>12&&mc.screen instanceof CollisionProtectionScreen){shot("melee-protection-empty");if(!second)((Button)field(mc.screen,"preset")).onPress();phase=41;age=0;}
  else if(phase==41&&age>30&&mc.screen instanceof CollisionProtectionScreen){var row=((HeartDashboardMenu)mc.player.containerMenu).snapshot().getList("Modules",10).getCompound(0);if(!row.getCompound("MeleeProtection").getList("Protected",8).stream().map(net.minecraft.nbt.Tag::getAsString).toList().contains("#minecraft:flowers"))return;phase=42;age=0;}
  else if(phase==42&&age>8&&mc.screen instanceof CollisionProtectionScreen){shot("melee-protection-filled");if(!second){((Button)field(mc.screen,"inventory")).onPress();phase=43;}else phase=45;age=0;}
  else if(phase==43&&age>12&&mc.screen instanceof ProtectionBlockPickerScreen s){int x=(int)field(s,"x"),y=(int)field(s,"y"),pitch=(int)field(s,"pitch"),iy=(int)field(s,"inventoryY");for(int i=0;i<2;i++){s.mouseClicked(x+13+i*pitch,y+iy+1,0);s.mouseClicked(x+13+i*pitch,y+29,0);}shot("melee-inventory-samples");((Button)field(s,"confirm")).onPress();phase=45;age=0;}
  else if(phase==45&&age>20&&mc.screen instanceof CollisionProtectionScreen){var list=((HeartDashboardMenu)mc.player.containerMenu).snapshot().getList("Modules",10).getCompound(0).getCompound("MeleeProtection").getList("Protected",8).stream().map(net.minecraft.nbt.Tag::getAsString).toList();if(!list.contains("minecraft:diamond_block")||!list.contains("minecraft:emerald_block"))return;if(!second&&(mc.player.getInventory().getItem(9).getCount()!=12||mc.player.getInventory().getItem(10).getCount()!=13))throw new AssertionError("Inventory samples consumed items");shot("melee-inventory-confirmed");mc.screen.onClose();mc.setScreen(new ManualScreen(mc.screen,"melee"));phase=3;age=0;}

  else if(phase==3&&age>12){shot("melee-manual-usage");button("manual.sablelife.example").onPress();phase=4;age=0;}
  else if(phase==4&&age>12){shot("melee-manual-example");mc.screen.onClose();((Button)field(mc.screen,"settingsNav")).onPress();phase=5;age=0;}
  else if(phase==5&&age>12&&mc.screen instanceof HeartSettingsScreen s){int p=(int)field(s,"page");if(p<5){s.children().stream().filter(c->c instanceof Button b&&b.active&&b.getMessage().getString().equals(">")).map(c->(Button)c).findFirst().orElseThrow().onPress();age=0;}else{shot("core-terrain");button("core.sablelife.crushProtected_open").onPress();phase=6;age=0;}}
  else if(phase==6&&age>12&&mc.screen instanceof CollisionProtectionScreen){shot("preset-before");if(!second)((Button)field(mc.screen,"preset")).onPress();phase=7;age=0;}
  else if(phase==7&&age>20&&mc.screen instanceof CollisionProtectionScreen){var menu=(HeartSettingsMenu)mc.player.containerMenu;var entries=menu.snapshot().getCompound("Crush").getList("Protected",8).stream().map(net.minecraft.nbt.Tag::getAsString).toList();if(!entries.contains("#minecraft:flowers")||!entries.contains("minecraft:oak_planks"))return;shot("preset-after");mc.player.connection.sendCommand("sablelife_terrain_test_ack");phase=9;}
 }
}
