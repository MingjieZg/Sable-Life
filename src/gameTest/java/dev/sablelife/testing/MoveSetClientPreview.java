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
            var diagnosticRows=new net.minecraft.nbt.ListTag();var row=new net.minecraft.nbt.CompoundTag();row.putUUID("Id",data.get(0).config().id());row.putString("Status","unavailable");var issues=new net.minecraft.nbt.ListTag();
            var refs=java.util.List.of(arrow,flame,gun);var reasons=java.util.List.of("disabled","unmarked","trigger");
            for(int i=0;i<3;i++){var issue=new net.minecraft.nbt.CompoundTag();issue.putUUID("Module",refs.get(i).id());issue.putInt("Action",i+1);issue.putString("Reason",reasons.get(i));issues.add(issue);}row.put("Problems",issues);diagnosticRows.add(row);var runtime=new net.minecraft.nbt.CompoundTag();runtime.put("Rows",diagnosticRows);tag.getList("Modules",10).getCompound(3).put("MoveSetRuntime",runtime);
            var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);
            panel=new MoveSetScreen(menu,inventory,title,table.id());panel.init(minecraft,width,height);
            try{
                var details=MoveSetScreen.class.getDeclaredMethod("problemDetails",MoveSet.Move.class);details.setAccessible(true);var text=((Component)details.invoke(panel,data.get(0))).getString();
                for(String expected:java.util.List.of("Arrow battery #1","Flame sweep #2","Long named cannon / upper deck #3","Disabled","Not marked","Trigger: move set"))if(!text.contains(expected))throw new AssertionError("Missing diagnostic text: "+expected+" / "+text);
                var help=MoveSetScreen.class.getDeclaredField("statusHelp");help.setAccessible(true);if(!((net.minecraft.client.gui.components.Button)help.get(panel)).visible)throw new AssertionError("Missing question mark");
                row.put("Problems",new net.minecraft.nbt.ListTag());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);var sync=MoveSetScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);sync.invoke(panel);
                if(((net.minecraft.client.gui.components.Button)help.get(panel)).visible)throw new AssertionError("Stale question mark after repair");
                row.put("Problems",issues);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);sync.invoke(panel);System.out.println("Move diagnostics tooltip identity/reasons and repair refresh passed");
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        void tab(boolean rules)throws Exception{var f=MoveSetScreen.class.getDeclaredField(rules?"tabRules":"tabActions");f.setAccessible(true);((net.minecraft.client.gui.components.Button)f.get(panel)).onPress();}
        void picker()throws Exception{var f=MoveSetScreen.class.getDeclaredField("actionAdd");f.setAccessible(true);((net.minecraft.client.gui.components.Button)f.get(panel)).onPress();}
        @Override public void render(GuiGraphics graphics,int x,int y,float partial){try{var f=MoveSetScreen.class.getDeclaredField("statusHelp");f.setAccessible(true);var help=(net.minecraft.client.gui.components.Button)f.get(panel);panel.render(graphics,help.getX()+2,help.getY()+2,partial);if(help.visible){var current=MoveSetScreen.class.getDeclaredMethod("current");current.setAccessible(true);var details=MoveSetScreen.class.getDeclaredMethod("problemDetails",MoveSet.Move.class);details.setAccessible(true);graphics.renderTooltip(minecraft.font,java.util.Arrays.stream(((Component)details.invoke(panel,current.invoke(panel))).getString().split("\n")).<Component>map(Component::literal).toList(),java.util.Optional.empty(),help.getX()+2,help.getY()+2);}}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    }
}

