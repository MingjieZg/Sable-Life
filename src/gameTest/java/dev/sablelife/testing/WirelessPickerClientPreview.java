package dev.sablelife.testing;

import dev.sablelife.client.HeartWirelessScreen;
import dev.sablelife.client.ModuleFrequencyScreen;
import dev.sablelife.heart.HeartWirelessMenu;
import dev.sablelife.module.ModuleFrequencyMenu;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class WirelessPickerClientPreview {
    private static int ticks;
    private static Preview screen;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(!Boolean.getBoolean("sablelife.wirelessPickerPreview"))return;
        var mc=Minecraft.getInstance();
        if(screen==null){if(mc.screen==null||mc.getOverlay()!=null)return;mc.options.guiScale().set(2);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;}
        ticks++;
        if(ticks==35){screen.picker=true;screen.init(mc,screen.width,screen.height);}
        if(ticks==65){mc.options.guiScale().set(3);mc.resizeDisplay();}
        if(ticks==25||ticks==55||ticks==85){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("wireless-picker-"+ticks+".png"));}}
        if(ticks==95){System.out.println("Wireless main page has no hidden player inventory; separate frequency picker layouts passed");mc.stop();}
    }
    private static final class Preview extends Screen {
        boolean picker;Screen panel;
        Preview(){super(Component.empty());}
        @Override protected void init(){
            var inventory=new Inventory(null);inventory.setItem(0,new ItemStack(Items.EMERALD,23));
            if(!picker){
                var menu=new HeartWirelessMenu(1,inventory);menu.setData(HeartWirelessMenu.COUNT,1);menu.setData(HeartWirelessMenu.EDITABLE,1);menu.setData(HeartWirelessMenu.MASTER,1);menu.setData(2,20);menu.setData(3,20);menu.setData(4,15);
                if(menu.slots.size()!=2||menu.slots.stream().anyMatch(s->s.container==inventory))throw new AssertionError("Wireless page exposes hidden inventory");
                panel=new HeartWirelessScreen(menu,inventory,title);
            }else{
                var menu=new ModuleFrequencyMenu(1,inventory);menu.setData(0,1);
                if(menu.slots.size()!=38)throw new AssertionError("Picker has no inventory");
                panel=new ModuleFrequencyScreen(menu,inventory,title);
            }
            panel.init(minecraft,width,height);
        }
        @Override public void render(GuiGraphics g,int x,int y,float partial){panel.render(g,-100,-100,partial);}
    }
}
