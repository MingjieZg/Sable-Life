package dev.sablelife.testing;
import dev.sablelife.module.*;
import dev.sablelife.client.HeartDashboardScreen;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
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
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class RgbClientPreview {
 private static final String[] types={"mortar","ground_strike","laser","quake","pull","pull","slash"};private static int ticks,index;private static Preview screen;
 @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception{if(!Boolean.getBoolean("sablelife.rgbPreview"))return;var mc=Minecraft.getInstance();if(screen==null){if(mc.screen==null||mc.getOverlay()!=null)return;mc.options.guiScale().set(2);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;}ticks++;if(ticks%30==0&&index<6){index++;if(index==3){mc.options.guiScale().set(3);mc.resizeDisplay();}screen.init(mc,screen.width,screen.height);}if(ticks%30==20){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var im=Screenshot.takeScreenshot(mc.getMainRenderTarget())){im.writeToFile(folder.resolve("rgb-"+index+".png"));}}if(ticks==210){System.out.println("All seven RGB controls: preset disabled, three channels grouped, collapse restores preset; GUI scales 2/3 passed");mc.stop();}}
 private static final class Preview extends Screen{HeartDashboardScreen panel;Preview(){super(Component.empty());}
 @Override protected void init(){var modules=new HeartModules();var m=modules.add(types[index]);var key=ModuleColors.keys(m.type()).get(index==5?1:0);m.edit(key+"Rgb",1,"");m.edit(key+"Red",80,"");m.edit(key+"Green",40,"");m.edit(key+"Blue",200,"");var inv=new Inventory(null);var menu=new HeartDashboardMenu(1,inv);set(menu,modules,m);panel=new HeartDashboardScreen(menu,inv,title);panel.init(minecraft,width,height);try{var params=HeartDashboardScreen.class.getDeclaredField("parameters");params.setAccessible(true);var list=(java.util.List<ModuleFields.Field>)params.get(panel);int row=0;for(;row<list.size();row++)if(list.get(row).key().equals(key))break;var slots=HeartDashboardScreen.class.getDeclaredField("parameterSlots");slots.setAccessible(true);int count=slots.getInt(panel);var page=HeartDashboardScreen.class.getDeclaredField("parameterPage");page.setAccessible(true);page.setInt(panel,row/count);var sync=HeartDashboardScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);sync.invoke(panel);var choices=HeartDashboardScreen.class.getDeclaredField("choices");choices.setAccessible(true);var button=(net.minecraft.client.gui.components.Button)((java.util.List<?>)choices.get(panel)).get(row%count);if(button.active)throw new AssertionError("Preset not disabled");var rgbs=HeartDashboardScreen.class.getDeclaredField("rgbButtons");rgbs.setAccessible(true);var rgb=(net.minecraft.client.gui.components.Button)((java.util.List<?>)rgbs.get(panel)).get(row%count);if(!rgb.visible||rgb.getX()+rgb.getWidth()>=button.getX())throw new AssertionError("RGB button overlap");m.edit(key+"Rgb",0,"");set(menu,modules,m);sync.invoke(panel);var collapsed=(java.util.List<ModuleFields.Field>)params.get(panel);int collapsedRow=0;for(;collapsedRow<collapsed.size();collapsedRow++)if(collapsed.get(collapsedRow).key().equals(key))break;page.setInt(panel,collapsedRow/count);sync.invoke(panel);var preset=(net.minecraft.client.gui.components.Button)((java.util.List<?>)choices.get(panel)).get(collapsedRow%count);if(!preset.active)throw new AssertionError("Collapse did not restore preset");m.edit(key+"Rgb",1,"");set(menu,modules,m);page.setInt(panel,row/count);sync.invoke(panel);}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}}
 private void set(HeartDashboardMenu menu,HeartModules modules,VirtualModule m){var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",m.id());tag.getList("Modules",10).getCompound(0).putString("Status","valid");var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);}
 @Override public void render(GuiGraphics g,int x,int y,float p){panel.render(g,-100,-100,p);}}
}
