package dev.sablelife.testing;

import dev.sablelife.client.MoveSetScreen;
import dev.sablelife.module.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Developer-only, no world opened: render the real panel at two GUI sizes into screenshots. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class MoveSetClientPreview {
    private static int ticks;
    private static Preview screen;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if(!Boolean.getBoolean("sablelife.panelPreview"))return;
        var mc=Minecraft.getInstance();
        if(screen==null){if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
            if(!dev.sablelife.client.FlameJetParticle.spriteReady())throw new IllegalStateException("Flame jet sprite failed to load");
            org.slf4j.LoggerFactory.getLogger("SableLifeClientTest").info("Flame jet sprite loaded from particle atlas: minecraft:flame");
            mc.options.guiScale().set(2);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;}
        ticks++;
        if(ticks==20||ticks==45||ticks==70||ticks==110||ticks==135){
            var path=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(path);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path.resolve("move-set-"+ticks+".png"));}
        }
        if(ticks==25||ticks==115)screen.tab(true);
        if(ticks==50){screen.tab(false);screen.picker();}
        if(ticks==90){mc.options.guiScale().set(3);mc.resizeDisplay();screen.tab(false);}
        if(ticks==150)mc.stop();
    }
    private static final class Preview extends Screen {
        MoveSetScreen panel;
        Preview(){super(Component.literal("Move set preview"));}
        @Override protected void init(){
            var modules=new HeartModules();
            var arrow=modules.add("arrow");arrow.edit("name",0,"Arrow battery");
            var flame=modules.add("flame");flame.edit("name",0,"Flame sweep");
            var gun=modules.add("gun");gun.edit("name",0,"Long named cannon / upper deck");
            var table=modules.add("move_set");var data=table.moveSet();
            for(int i=0;i<12;i++){data.edit("add",0,"",modules);data.edit("name",0,i+"|"+(i==0?"Combined volley":"Attack pattern "+(i+1)),modules);}
            data.edit("action_add",0,"0|"+arrow.id(),modules);data.edit("action_add",0,"0|"+flame.id(),modules);data.edit("action_add",0,"0|"+gun.id(),modules);
            data.edit("action_start",1,"0|1",modules);data.edit("action_start",3,"0|2",modules);data.edit("action_duration",1.5,"0|2",modules);
            var inventory=new Inventory(null);var menu=new HeartDashboardMenu(1,inventory);
            var tag=modules.save();tag.putBoolean("Editable",true);tag.putBoolean("Ready",true);
            var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);
            panel=new MoveSetScreen(menu,inventory,title,table.id());panel.init(minecraft,width,height);
        }
        void tab(boolean rules)throws Exception{var f=MoveSetScreen.class.getDeclaredField(rules?"tabRules":"tabActions");f.setAccessible(true);((net.minecraft.client.gui.components.Button)f.get(panel)).onPress();}
        void picker()throws Exception{var f=MoveSetScreen.class.getDeclaredField("actionAdd");f.setAccessible(true);((net.minecraft.client.gui.components.Button)f.get(panel)).onPress();}
        @Override public void render(GuiGraphics graphics,int x,int y,float partial){panel.render(graphics,-100,-100,partial);}
    }
}

