package dev.sablelife.testing;
import dev.sablelife.client.HeartSettingsScreen;
import dev.sablelife.heart.HeartSettingsMenu;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
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
public final class DefenseClientPreview {
    private static int ticks;private static Preview screen;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{if(!Boolean.getBoolean("sablelife.defensePreview"))return;var mc=Minecraft.getInstance();if(screen==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;mc.options.guiScale().set(2);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;}ticks++;if(ticks==25||ticks==60){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("defense-"+ticks+".png"));}}if(ticks==35){mc.options.guiScale().set(3);mc.resizeDisplay();}if(ticks==80)mc.stop();}
    private static final class Preview extends Screen {
        HeartSettingsScreen panel;Preview(){super(Component.literal("Defense settings preview"));}
        @Override protected void init(){var inv=new Inventory(null);var menu=new HeartSettingsMenu(1,inv);var tag=new CompoundTag();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putBoolean("LiveControls",true);tag.putBoolean("defenseEnabled",true);tag.putDouble("defenseThreshold",100);tag.putDouble("defenseMaximum",80);tag.putDouble("defenseWindow",1);tag.putDouble("DefenseDps",250);tag.putDouble("DefenseReduction",60);tag.putString("State","ALIVE");var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);panel=new HeartSettingsScreen(menu,inv,title);try{var page=HeartSettingsScreen.class.getDeclaredField("page");page.setAccessible(true);page.setInt(panel,4);}catch(Exception e){throw new IllegalStateException(e);}panel.init(minecraft,width,height);}
        @Override public void render(GuiGraphics g,int x,int y,float partial){panel.render(g,-100,-100,partial);}
    }
}
