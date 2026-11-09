package dev.sablelife.testing;

import dev.sablelife.client.ManualScreen;
import dev.sablelife.client.LifeUiStyle;
import dev.sablelife.client.LanguagePickerScreen;
import dev.sablelife.module.ModuleFields;
import dev.sablelife.module.VirtualModule;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Isolated menu-only preview and interaction checks. No world is loaded. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class ManualClientPreview {
    private static int ticks,phase;
    private static ManualScreen screen;
    private static java.util.concurrent.CompletableFuture<?> reload;
    private static boolean baselineLanguage;
    private static final Screen parent=new Screen(Component.empty()){
        @Override protected void init(){var box=addRenderableWidget(new EditBox(font,10,10,160,18,Component.empty()));box.setValue("original");}
    };
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(!Boolean.getBoolean("sablelife.manualPreview"))return;
        var mc=Minecraft.getInstance();
        if(reload!=null&&!reload.isDone())return;
        if(screen==null) {
            if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;
            if(!baselineLanguage){baselineLanguage=true;mc.getLanguageManager().setSelected("en_us");mc.options.languageCode="en_us";reload=mc.reloadResourcePacks();return;}
            mc.options.guiScale().set(phase==0?2:3);mc.resizeDisplay();
            if(phase==0){validateLabels(mc);mc.setScreen(parent);var box=(EditBox)parent.children().getFirst();box.setValue("unsaved manual draft");parent.setFocused(box);box.setFocused(true);}
            screen=new ManualScreen(parent,phase==0?"core":"move_set");mc.setScreen(screen);
            clickTab(1);var buttons=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
            var language=buttons.stream().filter(btn->btn.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals("language_picker.sablelife.button")).findFirst().orElseThrow();
            var density=buttons.stream().filter(btn->btn.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals("manual.sablelife.density")).findFirst().orElseThrow();
            var back=buttons.stream().filter(btn->btn.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals("manual.sablelife.back")).findFirst().orElseThrow();
            if(!(language.getX()<density.getX()&&density.getX()<back.getX()))throw new AssertionError("Manual header button order incorrect");
            int old=LifeUiStyle.density();density.onPress();if(LifeUiStyle.density()==old)throw new AssertionError("Manual density control failed");LifeUiStyle.cycle(-1);screen.init(mc,screen.width,screen.height);
            language.onPress();if(!(mc.screen instanceof LanguagePickerScreen))throw new AssertionError("Manual language control failed");mc.screen.onClose();if(mc.screen!=screen)throw new AssertionError("Language Back lost manual navigation");
            ticks=0;return;
        }
        ticks++;
        if(ticks==20) {
            var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("manual-"+phase+".png"));}
        }
        if(ticks==35) {
            if(phase==0) {
                // English/ID search is still valid with the translated title resources.
                var box=(EditBox)screen.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();box.setValue("flame");
                if(screen.children().stream().filter(Button.class::isInstance).count()<10)throw new AssertionError("Search lost module results");
                screen.onClose();if(mc.screen!=parent)throw new AssertionError("Manual did not return to its parent");
                var draft=(EditBox)parent.children().getFirst();if(!draft.getValue().equals("unsaved manual draft")||!draft.isFocused())throw new AssertionError("Focused settings draft was lost");
                mc.getLanguageManager().setSelected("zh_cn");mc.options.languageCode="zh_cn";reload=mc.reloadResourcePacks();phase=1;screen=null;
            } else {
                var box=(EditBox)screen.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();box.setValue("Melee");
                boolean found=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).anyMatch(b->b.getMessage().getString().equals(net.minecraft.client.resources.language.I18n.get("dashboard.sablelife.type_melee")));
                if(!found)throw new AssertionError("English search failed in Chinese");
                screen.keyPressed(269,0,0);System.out.println("Manual preview interaction checks passed");mc.stop();
            }
        }
    }
    private static void clickTab(int tab) {
        var buttons=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();String key="manual.sablelife."+java.util.List.of("usage","settings","example","troubleshooting").get(tab);buttons.stream().filter(b->b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals(key)).findFirst().orElseThrow().onPress();
    }
    private static void validateLabels(Minecraft mc)throws Exception {
        ParticleTravelClientCheck.run(mc);
        try(var reader=mc.getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath("sablelife","lang/en_us.json"))) {
            var strings=JsonParser.parseReader(reader).getAsJsonObject();
            var missing=new java.util.LinkedHashSet<String>();
            for(String type:VirtualModule.AVAILABLE_TYPES)for(var f:ModuleFields.forType(type)) {
                String key="dashboard.sablelife."+f.key();
                if(f.key().endsWith("RgbSet"))continue; // Internal editor control is not a manual field.
                if(!strings.has(key))missing.add("field: "+key);
                if(f.choices().size()>1&&!f.choices().contains("library"))for(String option:f.choices())if(!strings.has("dashboard.sablelife.choice_"+option))missing.add("choice: "+option);
            }
            if(!missing.isEmpty())throw new AssertionError("Missing manual labels: "+String.join(", ",missing));
        }
    }
}
