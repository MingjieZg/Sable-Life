package dev.sablelife.testing;
import dev.sablelife.client.*;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class ItemDisplayClientPreview {
    private static int ticks,phase;private static Preview screen;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception{
        if(!Boolean.getBoolean("sablelife.itemDisplayPreview"))return;var mc=Minecraft.getInstance();
        if(screen==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;mc.options.guiScale().set(2);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;}
        ticks++;if(ticks==20){var path=mc.gameDirectory.toPath().resolve("screenshots/item-display-"+phase+".png");java.nio.file.Files.createDirectories(path.getParent());try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}}
        if(ticks==30){if(phase<2){phase++;ticks=0;if(phase==2){mc.options.guiScale().set(3);mc.resizeDisplay();}screen.init(mc,screen.width,screen.height);}else{System.out.println("Item display handheld sword/block model and two-scale dashboard rendering passed");mc.stop();}}
    }
    private static final class Preview extends Screen{
        private HeartDashboardScreen dashboard;Preview(){super(Component.empty());}
        @Override protected void init(){if(phase==0)return;var modules=new HeartModules();var m=modules.add("item_display");var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",m.id());tag.getList("Modules",10).getCompound(0).putString("Status","valid");var inv=new Inventory(null);var menu=new HeartDashboardMenu(1,inv);var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);dashboard=new HeartDashboardScreen(menu,inv,title);dashboard.init(minecraft,width,height);}
        @Override public void render(GuiGraphics g,int mx,int my,float dt){
            if(phase>0){dashboard.render(g,-100,-100,dt);return;}
            g.fill(0,0,width,height,0xFF242632);g.drawCenteredString(font,"Handheld item model / size and rotation",width/2,25,0xFFE6D8EA);
            var items=new ItemStack[]{new ItemStack(Items.DIAMOND_SWORD),new ItemStack(Items.IRON_AXE),new ItemStack(Items.OAK_LOG)};
            for(int i=0;i<items.length;i++){g.pose().pushPose();g.pose().translate(width*(i+1)/4.,height/2.,150);g.pose().scale(80,-80,80);g.pose().mulPose(new org.joml.Quaternionf().rotationXYZ(.15F,-.5F,i==0?.8F:0));ProjectileItemRenderer.render(minecraft.getItemRenderer(),items[i],null,i,1,g.pose(),minecraft.renderBuffers().bufferSource(),15728880);minecraft.renderBuffers().bufferSource().endBatch();g.pose().popPose();g.drawCenteredString(font,items[i].getHoverName(),width*(i+1)/4,height-45,0xFFE6D8EA);}
        }
    }
}
