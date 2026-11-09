package dev.sablelife.testing;

import dev.sablelife.client.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Real bilingual wrapping and color checks; menu-only, no player world. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class ManualColorsClientPreview {
    private static final Screen PARENT=new Screen(Component.literal("Manual colors preview")){};
    private static final String[] CHAPTERS=Boolean.getBoolean("sablelife.briefManualPreview")?new String[]{"core","wireless_channels","move_set"}:new String[]{"getting_started","wireless_channels","arrow"};
    private static int phase,ticks,chapterTabs;private static String language="";private static ManualScreen screen;
    private static java.util.concurrent.CompletableFuture<?> reload;
    private static java.lang.reflect.Field field(String key)throws Exception{var f=ManualScreen.class.getDeclaredField(key);f.setAccessible(true);return f;}
    private static Object call(Object target,String key)throws Exception{var m=target.getClass().getDeclaredMethod(key);m.setAccessible(true);return m.invoke(target);}
    private static void checkSpan(Component component,String token,int expected){
        var text=new StringBuilder();var styles=new ArrayList<Style>();
        component.getVisualOrderText().accept((index,style,code)->{text.appendCodePoint(code);for(int n=0;n<Character.charCount(code);n++)styles.add(style);return true;});
        int start=text.indexOf(token);if(start<0)throw new AssertionError("Missing token "+token);
        for(int i=start;i<start+token.length();i++)if(styles.get(i).getColor()==null||styles.get(i).getColor().getValue()!=expected)throw new AssertionError("Wrong semantic color: "+token);
    }
    private static void audit()throws Exception{
        if(!field("TABS").get(screen).equals(List.of("usage","example","troubleshooting")))throw new AssertionError("Manual does not have exactly the requested three tabs");
        var chapters=(List<?>)field("chapters").get(screen);var ids=new ArrayList<String>();
        for(var chapter:chapters)ids.add((String)call(chapter,"id"));
        if(!ids.subList(0,7).equals(List.of("getting_started","core","module_management","marking_system","preview_system","triggers","wireless_channels"))||ids.contains("retaliation")||ids.contains("death_unloading")||ids.contains("sound_timeline")||ids.contains("combat_compatibility"))throw new AssertionError("Manual order/removal differs");
        boolean modules=false;for(var chapter:chapters){boolean guide=((String)call(chapter,"module")).isEmpty();if(guide&&modules)throw new AssertionError("An appended guide is below modules");if(!guide)modules=true;}
        if(!ids.contains("hit_counter"))throw new AssertionError("Guide removal deleted the Hit counter module chapter");
        String lastGuide="";int brief=0,briefUsage=0;
        var paragraphs=ManualScreen.class.getDeclaredMethod("paragraphs");paragraphs.setAccessible(true);
        for(var chapter:chapters){
            String module=(String)call(chapter,"module");
            String id=(String)call(chapter,"id");
            if(module.isEmpty())lastGuide=id;
            var sections=(Map<?,?>)call(chapter,"sections");
            if(!id.equals("getting_started")){
                briefUsage++;field("selected").set(screen,chapter);field("tab").setInt(screen,0);
                var content=(List<?>)paragraphs.invoke(screen);
                if(content.size()!=1||((Component)content.getFirst()).getString().contains("\n"))throw new AssertionError("Usage is not one short paragraph: "+id);
                if(!sections.get("usage").equals(List.of("manual.sablelife."+id+".brief_usage")))throw new AssertionError("Verbose Usage references remain: "+id);
            }
            if(module.isEmpty())continue;
            if(ModuleCategory.of(module)==ModuleCategory.SIGNAL)continue;
            brief++;field("selected").set(screen,chapter);field("tab").setInt(screen,0);
            if(((List<?>)paragraphs.invoke(screen)).size()!=1)throw new AssertionError("Brief Usage still has appended parameter text: "+module);
            for(String tab:List.of("usage","example","troubleshooting")){
                var keys=(List<?>)sections.get(tab);
                if(keys.size()!=1||!keys.getFirst().equals("manual.sablelife."+module+".brief_"+tab))throw new AssertionError("Verbose tab references remain: "+module);
            }
        }
        if(brief!=37||briefUsage!=58||!lastGuide.equals("server_limits"))throw new AssertionError("Brief chapter count or last guide differs");

        for(var button:(List<?>)field("chapterButtons").get(screen))((net.minecraft.client.gui.components.Button)button).getMessage().getVisualOrderText().accept((i,s,c)->{if(s.getColor()==null||s.getColor().getValue()!=0xFFFFFF)throw new AssertionError("Chapter title is not white");return true;});
        var formatter=field("textStyle").get(screen);var format=formatter.getClass().getDeclaredMethod("format",String.class,boolean.class);format.setAccessible(true);
        String sample="Heart Core: Survival Heart Core, Redstone Core; Wireless Channel, Wireless Redstone, Arrow modules. 生命核心、生存生命核心、红石核心、无线频道、无线红石、箭矢发射。";
        var styled=(Component)format.invoke(formatter,sample,false);if(!sample.equals(styled.getString()))throw new AssertionError("Styling changed prose");
        checkSpan(styled,"Heart Core",0xEE939F);checkSpan(styled,"Survival Heart Core",0x9EDCC6);checkSpan(styled,"Redstone Core",0xEDB17C);
        checkSpan(styled,"Wireless Channel",0xAD75DF);checkSpan(styled,"Wireless Redstone",0xAD75DF);checkSpan(styled,"Arrow modules",0xE45C61);
        if(language.equals("zh_cn")){checkSpan(styled,"生命核心",0xEE939F);checkSpan(styled,"生存生命核心",0x9EDCC6);checkSpan(styled,"无线红石",0xAD75DF);checkSpan(styled,"箭矢发射",0xE45C61);}
        var heading=(Component)format.invoke(formatter,"Choose a core",true);heading.getVisualOrderText().accept((i,s,c)->{if(!s.isBold()||s.getColor()==null||s.getColor().getValue()!=0xFFFFFF)throw new AssertionError("Heading is not white");return true;});
        var ordinary=(Component)format.invoke(formatter,"narrow wirelessness",false);ordinary.getVisualOrderText().accept((i,s,c)->{if(s.getColor()!=null)throw new AssertionError("Partial word was highlighted");return true;});
        var method=ManualScreen.class.getDeclaredMethod("lines");method.setAccessible(true);int[] colors={0,0};
        for(var chapter:(List<?>)field("chapters").get(screen))for(int tab=0;tab<3;tab++){
            field("selected").set(screen,chapter);field("tab").setInt(screen,tab);
            for(var value:(List<?>)method.invoke(screen))((FormattedCharSequence)value).accept((i,s,c)->{if(s.getColor()!=null){if(s.getColor().getValue()==0xAD75DF)colors[0]++;if(s.getColor().getValue()==0xE45C61)colors[1]++;}return true;});chapterTabs++;
        }
        if(colors[0]==0||colors[1]==0)throw new AssertionError("Semantic colors disappeared from wrapped manual text");
        if(!net.minecraft.client.resources.language.I18n.get("dashboard.sablelife.wireless").equals(language.equals("en_us")?"Wireless Channel":"无线频道"))throw new AssertionError("Channel button label differs");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("sablelife.manualColorPreview"))return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null||reload!=null&&!reload.isDone())return;
        if(screen==null){if(mc.screen==null||mc.level!=null)return;String wanted=phase<3?"en_us":"zh_cn";
            if(!wanted.equals(language)){language=wanted;mc.getLanguageManager().setSelected(wanted);mc.options.languageCode=wanted;reload=mc.reloadResourcePacks();return;}
            mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.resizeDisplay();while(LifeUiStyle.density()!=1)LifeUiStyle.cycle(1);
            screen=new ManualScreen(PARENT,CHAPTERS[phase%3]);mc.setScreen(screen);ticks=0;
            if(phase%3==0){audit();screen=new ManualScreen(PARENT,CHAPTERS[phase%3]);mc.setScreen(screen);}
            field("tab").setInt(screen,0);screen.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());return;
        }
        if(++ticks==12){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(language+"-"+CHAPTERS[phase%3]+".png"));}
            System.out.println("MANUAL_COLORS_PASS "+language+" "+CHAPTERS[phase%3]);
            if(++phase==6){System.out.println("MANUAL_COLORS_COMPLETE chapterTabs="+chapterTabs+" screenshots=6");mc.stop();}else{screen=null;mc.setScreen(PARENT);}
        }
    }
}
