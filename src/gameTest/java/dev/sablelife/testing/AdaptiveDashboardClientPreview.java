package dev.sablelife.testing;
import dev.sablelife.client.*;
import dev.sablelife.module.*;
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
public final class AdaptiveDashboardClientPreview {
 private static Preview screen;private static int ticks,index,originalDensity;
 private static int number(String field)throws Exception{for(Class<?> type=HeartDashboardScreen.class;type!=null;type=type.getSuperclass()){try{var f=type.getDeclaredField(field);f.setAccessible(true);return f.getInt(screen.panel);}catch(NoSuchFieldException ignored){}}throw new NoSuchFieldException(field);}
 private static void check()throws Exception{
  int h=number("imageHeight"),w=number("imageWidth"),slots=number("sidebarSlots"),pitch=number("sidebarPitch"),fields=number("parameterSlots"),row=number("rowPitch");
  if(h>screen.height||w>screen.width)throw new AssertionError("Panel exceeds the viewport");
  if(47+(slots+1)*pitch<=h-44)throw new AssertionError("Sidebar left room for another row but paginated early");
  if(63+(fields+1)*row<=h-99)throw new AssertionError("Property panel left room for another row");
  if(slots>=9&&number("page")!=0)throw new AssertionError("Nine modules still spilled onto second page despite enough room");
  System.out.println("Adaptive dashboard scale="+Minecraft.getInstance().options.guiScale().get()+" density="+LifeUiStyle.density()+" viewport="+screen.width+"x"+screen.height+" rows="+slots+" fields="+fields);
 }
 private static void layout(Minecraft mc){int scale=index/4+1,density=index%4;while(LifeUiStyle.density()!=density)LifeUiStyle.cycle();mc.options.guiScale().set(scale);mc.resizeDisplay();screen.init(mc,screen.width,screen.height);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!Boolean.getBoolean("sablelife.adaptiveDashboardPreview"))return;var mc=Minecraft.getInstance();if(screen==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;originalDensity=LifeUiStyle.density();screen=new Preview();mc.setScreen(screen);layout(mc);return;}
  if(++ticks==12){check();if(index%4==0){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(folder.resolve("adaptive-"+index+".png"));}}}
  if(ticks==15){ticks=0;if(++index<16)layout(mc);else{while(LifeUiStyle.density()!=originalDensity)LifeUiStyle.cycle();System.out.println("All 16 scale/density combinations passed");mc.stop();}}
 }
 private static final class Preview extends Screen {
  HeartDashboardScreen panel;Preview(){super(Component.empty());}
  @Override protected void init(){var modules=new HeartModules();for(int i=0;i<9;i++)modules.add(i==0?"sound":"item_display");var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",modules.all().get(8).id());for(var item:tag.getList("Modules",10))((net.minecraft.nbt.CompoundTag)item).putString("Status","valid");var inv=new Inventory(null);var menu=new HeartDashboardMenu(1,inv);var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);panel=new HeartDashboardScreen(menu,inv,title);panel.init(minecraft,width,height);}
  @Override public void render(GuiGraphics g,int x,int y,float partial){panel.render(g,-100,-100,partial);}
 }
}
