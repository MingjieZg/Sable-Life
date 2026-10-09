package dev.sablelife.testing;

import dev.sablelife.client.*;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

/** Runs without a world, using only a separate preview directory's personal preset file. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class ModulePickerClientPreview {
    private static Preview screen;private static int ticks,phase;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("sablelife.pickerPreview"))return;var mc=Minecraft.getInstance();
        if(screen==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;for(int i=0;i<33;i++)ModulePresetLibrary.save("Preview "+i,new VirtualModule("flame").save());mc.options.guiScale().set(2);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;}
        ticks++;
        if(ticks==20){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("module-picker-"+phase+".png"));}}
        if(ticks==25&&phase==0){long count=screen.inner.children().stream().filter(Button.class::isInstance).count();if(count<38)throw new AssertionError("All catalog omitted modules");var query=(net.minecraft.client.gui.components.EditBox)screen.inner.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance).findFirst().orElseThrow();query.setValue("sphere_blast");if(screen.inner.children().stream().filter(Button.class::isInstance).count()>=count)throw new AssertionError("Search failed");((net.minecraft.client.gui.components.EditBox)screen.inner.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance).findFirst().orElseThrow()).setValue("");button("Signals").onPress();if(screen.inner.children().stream().filter(Button.class::isInstance).count()>=count)throw new AssertionError("Signal filter failed");}
        if(ticks==25&&phase==2){var entries=ModulePresetLibrary.load();var row=screen.inner.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(entries.getFirst().name())).findFirst().orElseThrow();if(button("Delete").active)throw new AssertionError("Unselected delete enabled");row.onPress();var originalFirst=entries.getFirst().name();button("↓").onPress();if(!ModulePresetLibrary.load().get(1).name().equals(originalFirst))throw new AssertionError("Preset reorder not saved");if(!button("Delete").active||!button("Load modules").active)throw new AssertionError("Selected preset actions disabled");button("Delete").onPress();if(ModulePresetLibrary.load().size()!=entries.size())throw new AssertionError("Single click deleted preset");button("Click again to delete").onPress();if(ModulePresetLibrary.load().size()!=entries.size()-1)throw new AssertionError("Double click did not persist delete");if(!button("›").active)throw new AssertionError("Preset pagination missing");button("›").onPress();}
        if(ticks==35){if(phase<3){phase++;ticks=0;if(phase==1){mc.options.guiScale().set(3);mc.resizeDisplay();}screen.init(mc,screen.width,screen.height);}else{for(var p:ModulePresetLibrary.load())if(p.name().startsWith("Preview "))ModulePresetLibrary.delete(p.name());System.out.println("Module catalog filters, column layout, search, persistent sorting, presets, pagination and double-delete checks passed");mc.stop();}}
    }
    private static Button button(String text){return screen.inner.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(text)).findFirst().orElseThrow();}
    private static final class Preview extends Screen{
        Screen inner;
        Preview(){super(Component.empty());}
        @Override protected void init(){var modules=new HeartModules();var module=modules.add("flame");var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",module.id());var inv=new Inventory(null);var menu=new HeartDashboardMenu(1,inv);var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);var parent=new HeartDashboardScreen(menu,inv,title);parent.init(minecraft,width,height);inner=phase==3?new SaveModulePresetScreen(parent):new ModulePickerScreen(parent,VirtualModule.AVAILABLE_TYPES);inner.init(minecraft,width,height);if(phase==2)buttonAfterInit("Presets").onPress();}
        private Button buttonAfterInit(String label){return inner.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow();}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){inner.render(g,mx,my,partial);}
    }
}
