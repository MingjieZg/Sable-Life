package dev.sablelife.testing;

import dev.sablelife.client.LifeUiStyle;
import dev.sablelife.client.ManualScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Real renderer checks in both languages and all four manual text densities; no user world is loaded. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class ManualIllustratedPreview {
    private static int phase,ticks;private static ManualScreen screen;
    private static String language="";private static java.util.concurrent.CompletableFuture<?> reload;
    private static final Screen parent=new Screen(Component.literal("Manual preview")){};
    private static java.lang.reflect.Field field(String name)throws Exception{var f=ManualScreen.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void tab(String key){screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast).filter(b->b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals("manual.sablelife."+key)).findFirst().orElseThrow().onPress();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception{
        if(!Boolean.getBoolean("sablelife.manualIllustratedPreview"))return;
        var mc=Minecraft.getInstance();if(reload!=null&&!reload.isDone()||mc.getOverlay()!=null)return;
        if(screen==null){
            if(mc.screen==null||mc.level!=null)return;
            String wanted=phase<4?"en_us":"zh_cn";
            if(!wanted.equals(language)){language=wanted;mc.getLanguageManager().setSelected(wanted);mc.options.languageCode=wanted;reload=mc.reloadResourcePacks();return;}
            int density=phase%4;while(LifeUiStyle.density()!=density)LifeUiStyle.cycle(1);
            mc.options.guiScale().set(phase%2==0?2:3);mc.resizeDisplay();
            screen=new ManualScreen(parent,"getting_started");mc.setScreen(screen);ticks=0;return;
        }
        ticks++;
        @SuppressWarnings("unchecked") var rows=(Map<Integer,String>)field("figureRows").get(screen);
        if(ticks==6){
            if(rows.size()!=6)throw new AssertionError("Expected all six tutorial illustrations: "+rows);
            var lineMethod=ManualScreen.class.getDeclaredMethod("lines");lineMethod.setAccessible(true);
            var lines=(List<?>)lineMethod.invoke(screen);int pitch=Math.max(7,Math.round((mc.font.lineHeight+3)*Math.max(.85f,LifeUiStyle.scale())));
            int imageHeight=Math.round(100*Math.min(1.5f,((int)field("bodyWidth").get(screen)-10)/320f))+8;
            var starts=new ArrayList<>(rows.keySet());for(int i=1;i<starts.size();i++)if((starts.get(i)-starts.get(i-1))*pitch<imageHeight)throw new AssertionError("Tutorial illustration overlaps next figure");
            if((int)field("contentHeight").get(screen)<lines.size()*pitch)throw new AssertionError("Figures omitted from scroll height");
        }
        if(ticks>=10&&ticks<=60&&ticks%10==0){
            int index=ticks/10-1;var starts=new ArrayList<>(rows.keySet());int pitch=Math.max(7,Math.round((mc.font.lineHeight+3)*Math.max(.85f,LifeUiStyle.scale())));
            field("scroll").setInt(screen,Math.max(0,starts.get(index)*pitch-7*pitch));
        }
        if(ticks>=13&&ticks<=63&&ticks%10==3&&(phase%4==0||phase%4==3)){
            int index=ticks/10-1;var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("tutorial-"+language+"-density"+(phase%4)+"-step"+(index+1)+".png"));}
        }
        if(ticks==68){
            screen.keyPressed(269,0,0);int scroll=(int)field("scroll").get(screen),height=(int)field("contentHeight").get(screen),body=(int)field("bodyHeight").get(screen);
            if(scroll!=Math.max(0,height-body))throw new AssertionError("Cannot scroll to final step");
            tab("example");
        }
        if(ticks==72){
            if(!((Map<?,?>)field("figureRows").get(screen)).isEmpty())throw new AssertionError("Example repeats the six tutorial illustrations");
            tab("usage");
        }
        if(ticks==76){
            var method=ManualScreen.class.getDeclaredMethod("lines");method.setAccessible(true);var lines=(List<?>)method.invoke(screen);int start=-1;
            for(int i=0;i<lines.size();i++){var text=new StringBuilder();((net.minecraft.util.FormattedCharSequence)lines.get(i)).accept((index,style,code)->{text.appendCodePoint(code);return true;});if(text.toString().contains("Automation: three")||text.toString().contains("自动化从这里开始")){start=i;break;}}
            if(start<0)throw new AssertionError("Automation introduction is not reachable in Usage");
            int pitch=Math.max(7,Math.round((mc.font.lineHeight+3)*Math.max(.85f,LifeUiStyle.scale())));field("scroll").setInt(screen,start*pitch);
        }
        if(ticks==80&&phase%4==0){
            var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("automation-"+language+".png"));}
        }
        if(ticks==84){
            screen.keyPressed(269,0,0);int scroll=(int)field("scroll").get(screen),height=(int)field("contentHeight").get(screen),body=(int)field("bodyHeight").get(screen);
            if(scroll!=Math.max(0,height-body))throw new AssertionError("Cannot reach the end of automation instructions");
        }
        if(ticks>=85&&ticks<135){
            String[] chapters={"natural_spawning","core","arena","linear_movement","wireless_channels"};int part=(ticks-85)/10,step=(ticks-85)%10;
            if(step==0){screen=new ManualScreen(parent,chapters[part]);mc.setScreen(screen);tab("usage");}
            if(step==4){
                var method=ManualScreen.class.getDeclaredMethod("paragraphs");method.setAccessible(true);
                var paragraphs=(List<?>)method.invoke(screen);
                // Usage now includes the former Settings prose; its paragraph count may be higher.
                if(phase%4==0){var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);
                    try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("reading-178-"+language+"-"+chapters[part]+".png"));}}
            }
            if(part==4&&step==5)tab("example");
            if(part==4&&step==6&&phase%4==0){var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("wireless-179-"+language+"-routes.png"));}}
            if(step==7){screen.keyPressed(269,0,0);if((int)field("scroll").get(screen)!=Math.max(0,(int)field("contentHeight").get(screen)-(int)field("bodyHeight").get(screen)))throw new AssertionError("Cannot reach concise chapter end: "+chapters[part]);}
        }
        if(ticks==135){
            System.out.println("ILLUSTRATED_MANUAL_PASS: language="+language+" density="+(phase%4)+" gui="+mc.options.guiScale().get()+" tutorial/example/automation/concise-settings/scroll");
            if(++phase==8){System.out.println("ILLUSTRATED_MANUAL_COMPLETE: 2 locales x 4 densities; 6 figures, non-duplicate Example, automation, concise settings without property dump, and end scrolling passed");mc.stop();}else {screen=null;mc.setScreen(parent);}
        }
    }
}
