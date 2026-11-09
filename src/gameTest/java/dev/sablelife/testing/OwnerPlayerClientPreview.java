package dev.sablelife.testing;
import dev.sablelife.client.*;
import dev.sablelife.heart.HeartSettingsMenu;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class OwnerPlayerClientPreview {
    private static Screen panel;private static int index,ticks;private static boolean started;
    private static final class Host extends Screen {Host(){super(Component.empty());}@Override protected void init(){if(panel!=null)panel.init(minecraft,width,height);}@Override public void render(GuiGraphics g,int x,int y,float p){if(panel!=null)panel.render(g,-100,-100,p);}}
    private static ItemStack snapshot(CompoundTag data){var item=new ItemStack(Items.PAPER);item.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return item;}
    private static void open(){try{var mc=Minecraft.getInstance();var inv=new Inventory(null);if(index%2==0){var data=new CompoundTag();data.putBoolean("Ready",true);data.putBoolean("LiveControls",true);data.putBoolean("Editable",true);data.putString("ownerName","TestOwner");data.putString("State","ALIVE");data.putInt("InitialPage",1);var menu=new HeartSettingsMenu(1,inv);menu.getSlot(0).set(snapshot(data));panel=new HeartSettingsScreen(menu,inv,Component.empty());var field=HeartSettingsScreen.class.getDeclaredField("page");field.setAccessible(true);field.setInt(panel,1);}else{var modules=new HeartModules();var m=modules.add("sensor");m.edit("target",4,"");var data=modules.save();data.putBoolean("Ready",true);data.putBoolean("Editable",true);data.putUUID("Selected",m.id());data.getList("Modules",10).getCompound(0).putString("Status","valid");var menu=new HeartDashboardMenu(1,inv);menu.getSlot(0).set(snapshot(data));panel=new HeartDashboardScreen(menu,inv,Component.empty());}mc.setScreen(new Host());ticks=0;}catch(Exception e){throw new RuntimeException(e);}}
    private static void build(){var mc=Minecraft.getInstance();mc.options.guiScale().set(index/4+1);mc.resizeDisplay();var language=index%4<2?"en_us":"zh_cn";mc.getLanguageManager().setSelected(language);mc.options.languageCode=language;mc.reloadResourcePacks().thenRun(()->mc.execute(OwnerPlayerClientPreview::open));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{if(!Boolean.getBoolean("sablelife.ownerPlayerPreview"))return;var mc=Minecraft.getInstance();if(!started){if(mc.screen==null||mc.getOverlay()!=null)return;started=true;build();return;}if(panel==null||mc.getOverlay()!=null||!(mc.screen instanceof Host))return;
        if(++ticks==14){for(var child:panel.children())if(child instanceof AbstractWidget w&&w.visible){if(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>panel.width||w.getY()+w.getHeight()>panel.height)throw new AssertionError("Owner UI outside viewport: "+index);if(w.getMessage().getString().contains("sablelife."))throw new AssertionError("Untranslated Owner UI: "+w.getMessage().getString());}if(index%2==0&&!panel.children().stream().anyMatch(w->w instanceof EditBox b&&b.getValue().equals("TestOwner")&&b.active))throw new AssertionError("Live Owner input missing");if(index>=8&&index<=11){var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("owner-"+index+".png"));}}}
        if(ticks==18){panel=null;if(++index==16){System.out.println("OWNER_UI_PASS: core settings and Owner sensor, 2 languages x 4 GUI scales");mc.stop();}else build();}
    }
}
