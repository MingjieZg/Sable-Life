package dev.sablelife.testing;
import dev.sablelife.client.HeartSettingsScreen;
import dev.sablelife.heart.HeartSettingsMenu;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class TargetPriorityClientPreview {
 private static HeartSettingsScreen panel;private static int index,ticks;private static boolean started;
 private static final class Host extends Screen {Host(){super(Component.empty());}@Override protected void init(){panel.init(minecraft,width,height);}@Override public void render(GuiGraphics g,int x,int y,float p){panel.render(g,-100,-100,p);}}
 private static void build()throws Exception{var mc=Minecraft.getInstance();mc.options.guiScale().set(index/4+1);mc.resizeDisplay();var inv=new Inventory(null);var menu=new HeartSettingsMenu(1,inv);var data=new CompoundTag();data.putBoolean("Ready",true);data.putBoolean("Editable",true);data.putBoolean("LiveControls",true);data.putInt("targetPriority",index%4);data.putString("State","ALIVE");var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(data));menu.getSlot(0).set(paper);panel=new HeartSettingsScreen(menu,inv,Component.empty());var page=HeartSettingsScreen.class.getDeclaredField("page");page.setAccessible(true);page.setInt(panel,1);mc.setScreen(new Host());}
 @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception{if(!Boolean.getBoolean("sablelife.targetPriorityPreview"))return;var mc=Minecraft.getInstance();if(!started){if(mc.screen==null||mc.getOverlay()!=null)return;started=true;build();return;}if(++ticks==14){var field=HeartSettingsScreen.class.getDeclaredField("targetPriority");field.setAccessible(true);var b=(Button)field.get(panel);if(b==null||!b.active||b.getX()<0||b.getY()<0||b.getX()+b.getWidth()>panel.width||b.getY()+b.getHeight()>panel.height||b.getMessage().getString().contains("sablelife"))throw new AssertionError("Priority selector missing, untranslated or out of bounds");if(index<4){var path=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(path);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path.resolve("priority-"+index+".png"));}}}if(ticks==18){ticks=0;if(++index==16){System.out.println("TARGET_PRIORITY_UI_PASS: all 4 priorities at 4 GUI scales");mc.stop();}else build();}}
}
