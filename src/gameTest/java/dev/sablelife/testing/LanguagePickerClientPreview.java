package dev.sablelife.testing;

import dev.sablelife.client.LanguagePickerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real resource reload and settings save, in a dedicated no-world preview directory. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class LanguagePickerClientPreview {
    private static int phase,ticks;
    private static java.util.concurrent.CompletableFuture<?> baseline;
    private static final Screen parent=new Screen(Component.empty()){
        @Override protected void init(){var box=addRenderableWidget(new EditBox(font,10,10,160,18,Component.empty()));box.setValue("original");}
    };
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("sablelife.languagePreview"))return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null||baseline!=null&&!baseline.isDone())return;
        if(phase==0){if(mc.screen==null||mc.level!=null)return;mc.options.guiScale().set(2);mc.resizeDisplay();mc.getLanguageManager().setSelected("en_us");mc.options.languageCode="en_us";baseline=mc.reloadResourcePacks();phase=1;return;}
        if(phase==1){mc.setScreen(parent);var draft=(EditBox)parent.children().getFirst();draft.setValue("unsaved draft");parent.setFocused(draft);draft.setFocused(true);mc.setScreen(new LanguagePickerScreen(parent));phase=2;ticks=0;return;}
        if(phase==2||phase==4){if(!(mc.screen instanceof LanguagePickerScreen))return;if(++ticks==15){var path=mc.gameDirectory.toPath().resolve("screenshots/language-"+phase+".png");java.nio.file.Files.createDirectories(path.getParent());try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}String target=phase==2?"简体中文":"English";mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(target)).findFirst().orElseThrow().onPress();phase++;}return;}
        if((phase==3||phase==5)&&mc.screen==parent){String code=phase==3?"zh_cn":"en_us";if(!mc.options.languageCode.equals(code)||!mc.getLanguageManager().getSelected().equals(code))throw new AssertionError("Language did not switch");String expected=phase==3?"自定义动作抽选":"Move set";if(!net.minecraft.client.resources.language.I18n.get("dashboard.sablelife.type_move_set").equals(expected))throw new AssertionError("Module translations did not reload");var draft=(EditBox)parent.children().getFirst();if(!draft.getValue().equals("unsaved draft")||!draft.isFocused())throw new AssertionError("Editor draft lost during language switch");if(phase==3){mc.setScreen(new LanguagePickerScreen(parent));phase=4;ticks=0;}else{System.out.println("Live English/Chinese resource switching, settings persistence and focused draft retention passed");mc.stop();}}
    }
}
