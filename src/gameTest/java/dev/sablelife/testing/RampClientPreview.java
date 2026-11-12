package dev.sablelife.testing;

import dev.sablelife.client.*;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Captures the actual dashboard, without modifying a player world or installation. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class RampClientPreview {
 private static final List<String> LANGUAGES = List.of("zh_cn", "en_us");
 private static java.util.concurrent.CompletableFuture<?> reload;
 private static Preview screen;
 private static int languageIndex, ticks;
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!Boolean.getBoolean("sablelife.rampPreview"))return;var mc=Minecraft.getInstance();
  var language = LANGUAGES.get(languageIndex);
  if(screen==null){
   if(mc.screen==null||mc.getOverlay()!=null||reload!=null&&!reload.isDone())return;
   if(!mc.options.languageCode.equals(language)){
    mc.options.languageCode=language;
    mc.getLanguageManager().setSelected(language);
    reload=mc.reloadResourcePacks();
    return;
   }
   while(LifeUiStyle.density()!=2)LifeUiStyle.cycle();mc.options.guiScale().set(2);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;
  }
  if(++ticks==15){check();var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(dir.resolve("wireless-redstone-ramp-aligned-"+language+".png"));}}
  if(ticks==20){
   System.out.println("Ramp dashboard layout, focus and disabled-control checks passed for " + language);
   if(languageIndex+1==LANGUAGES.size()){mc.stop();return;}
   languageIndex++;
   ticks=0;
   screen=null;
  }
 }
 private static Object member(String key)throws Exception{var f=HeartDashboardScreen.class.getDeclaredField(key);f.setAccessible(true);return f.get(screen.panel);}
 private static void check()throws Exception{
  var keys=((List<ModuleFields.Field>)member("parameters")).stream().map(ModuleFields.Field::key).toList();
  var boxes=(List<EditBox>)member("fields");var end=(EditBox)member("rampEnd");var start=boxes.get(keys.indexOf("redstoneRampStart"));
  if(!end.visible||!end.active||start.getY()!=end.getY()||start.getX()+start.getWidth()>=end.getX())throw new AssertionError("Ramp endpoints overlap or are not adjacent/editable");
  var choices=(List<Button>)member("choices");for(String key:List.of("redstoneInvert","redstoneMode"))if(choices.get(keys.indexOf(key)).active)throw new AssertionError("Ramp exposed ignored control: "+key);
  if(keys.contains("redstonePower")||keys.contains("redstoneRampEnd"))throw new AssertionError("Duplicate endpoint/fixed-power row");
  start.setValue("4");end.setValue("12");end.setFocused(true);var sync=HeartDashboardScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);sync.invoke(screen.panel);
  if(!end.isFocused()||!start.getValue().equals("4")||!end.getValue().equals("12"))throw new AssertionError("Synchronization discarded endpoint edits");
  start.setValue("0");end.setValue("15");end.setFocused(false);
 }
 private static final class Preview extends Screen{
  HeartDashboardScreen panel;Preview(){super(Component.empty());}
  @Override protected void init(){var modules=new HeartModules();var m=modules.add("wireless_redstone");m.edit("redstoneStrengthMode",2,"");var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",m.id());tag.getList("Modules",10).getCompound(0).putString("Status","valid");var inv=new Inventory(null);var menu=new HeartDashboardMenu(1,inv);var item=new ItemStack(Items.PAPER);item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(item);panel=new HeartDashboardScreen(menu,inv,title);panel.init(minecraft,width,height);}
  @Override public void render(GuiGraphics g,int x,int y,float partial){panel.render(g,-100,-100,partial);}
 }
}
