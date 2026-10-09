package dev.sablelife.testing;
import dev.sablelife.client.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class ColorPickerClientPreview {
    private static Screen parent;
    private static ColorPickerScreen picker;
    private static int ticks,saves,color;
    private static boolean custom;
    private static void open(Minecraft mc,int initial){picker=new ColorPickerScreen(parent,Component.literal("Laser color"),initial,0xFF0000,(mode,rgb)->{saves++;custom=mode;color=rgb;});mc.setScreen(picker);}
    private static Button button(String key){return picker.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getContents() instanceof TranslatableContents t&&t.getKey().equals("color_picker.sablelife."+key)).findFirst().orElseThrow();}
    private static int integer(String key)throws Exception{var f=ColorPickerScreen.class.getDeclaredField(key);f.setAccessible(true);return f.getInt(picker);}
    private static ColorPickerModel model()throws Exception{var f=ColorPickerScreen.class.getDeclaredField("model");f.setAccessible(true);return (ColorPickerModel)f.get(picker);}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("sablelife.colorPickerPreview"))return;var mc=Minecraft.getInstance();if(parent==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;mc.options.guiScale().set(2);mc.resizeDisplay();parent=new Screen(Component.empty()){};open(mc,0x804040);return;}
        ticks++;
        if(ticks==15){int sx=integer("sx"),sy=integer("sy"),size=integer("size"),hx=integer("hx");picker.mouseClicked(sx+size*.75,sy+size*.25,0);picker.mouseDragged(sx+size*.5,sy+size*.4,0,0,0);picker.mouseReleased(sx,sy,0);picker.mouseClicked(hx+4,sy+size*.65,0);picker.mouseReleased(hx,sy,0);check(saves==0,"Dragging sent premature saves");}
        if(ticks==30){button("precise").onPress();var boxes=picker.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();check(boxes.size()==3,"Precise RGB fields absent");boxes.get(0).setValue("12");boxes.get(1).setValue("34");boxes.get(2).setValue("56");check(model().rgb()==0x0C2238,"Numbers did not synchronize color model");}
        if(ticks==42){button("confirm").onPress();check(saves==1&&custom&&color==0x0C2238&&mc.screen==parent,"Confirm did not save once/return to parent");open(mc,0x123456);picker.mouseClicked(integer("sx")+5,integer("sy")+5,0);picker.onClose();check(saves==1&&mc.screen==parent,"Cancel changed saved color");}
        if(ticks==60){mc.options.guiScale().set(3);mc.resizeDisplay();open(mc,0x123456);button("precise").onPress();var boxes=picker.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();boxes.get(1).setValue("256");check(!button("confirm").active,"Invalid RGB left Confirm active");button("confirm").onPress();check(saves==1,"Invalid RGB partially saved");boxes.get(1).setValue("52");}
        if(ticks==85){button("presets").onPress();check(saves==2&&!custom&&color==0x123456&&mc.screen==parent,"Use presets did not preserve custom RGB/return");}
        if(ticks==20||ticks==35||ticks==75){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("color-picker-"+ticks+".png"));}}
        if(ticks==95){System.out.println("Color picker drag, RGB sync, atomic confirm, cancel, presets and GUI scales 2/3 passed");mc.stop();}
    }
}
