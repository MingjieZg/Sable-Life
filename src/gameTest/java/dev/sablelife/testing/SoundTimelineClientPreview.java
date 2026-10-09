package dev.sablelife.testing;
import dev.sablelife.client.SoundTimelineScreen;
import dev.sablelife.sound.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class SoundTimelineClientPreview {
    private static int ticks;private static Preview screen;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{if(!Boolean.getBoolean("sablelife.soundPreview"))return;var mc=Minecraft.getInstance();
        if(screen==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;mc.options.guiScale().set(2);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;}
        ticks++;if(ticks==25||ticks==60){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("sound-timeline-"+ticks+".png"));}}
        if(ticks==35){mc.options.guiScale().set(3);mc.resizeDisplay();}if(ticks==40)verifyDashboardReturn(mc);if(ticks==80)mc.stop();
    }
    private static void verifyDashboardReturn(Minecraft mc)throws Exception {
        var field=dev.sablelife.client.HeartDashboardScreen.class.getDeclaredField("parameterPage");field.setAccessible(true);
        for(String type:java.util.List.of("sound","melee","particles")) {
            var module=new dev.sablelife.module.VirtualModule(type);if(type.equals("particles"))module.edit("particlePattern",4,"");
            var tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",module.id());
            var modules=new net.minecraft.nbt.ListTag();var saved=module.save();saved.putString("Status","valid");modules.add(saved);tag.put("Modules",modules);
            var inv=new Inventory(null);var menu=new dev.sablelife.module.HeartDashboardMenu(1,inv);var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);
            var panel=new dev.sablelife.client.HeartDashboardScreen(menu,inv,Component.empty());panel.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());field.setInt(panel,1);panel.removed();
            var returned=new dev.sablelife.client.HeartDashboardScreen(menu,inv,Component.empty());returned.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            if(field.getInt(returned)!=1)throw new AssertionError(type+" editor return lost property page");
        }
        System.out.println("Sound, effect and particle property-page return checks passed");
    }
    private static final class Preview extends Screen {
        SoundTimelineScreen panel;Preview(){super(Component.literal("Sound timeline preview"));}
        @Override protected void init(){var inv=new Inventory(null);var menu=new SoundTimelineMenu(1,inv);var mix=SoundTimeline.legacy("chargeSound","minecraft:block.respawn_anchor.charge",1,1,32);
            for(int i=0;i<10;i++){mix.edit("add",null,0,i%2==0?"minecraft:entity.firework_rocket.blast":"minecraft:block.note_block.bass");var r=mix.rows().getLast();mix.edit("start",r.id(),i*.35,"");if(i%2==0)mix.edit("duration",r.id(),.8,"");}
            var tag=mix.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);panel=new SoundTimelineScreen(menu,inv,title);panel.init(minecraft,width,height);}
        @Override public void render(GuiGraphics g,int x,int y,float partial){panel.render(g,-100,-100,partial);}
    }
}
