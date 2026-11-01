package dev.sablelife.testing;
import dev.sablelife.client.*;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
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
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class FreezeDashboardPreview {
    private static Preview screen;private static int ticks,index;
    private static Object get(String key)throws Exception{var f=HeartDashboardScreen.class.getDeclaredField(key);f.setAccessible(true);return f.get(screen.panel);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception{
        if(!Boolean.getBoolean("sablelife.freezeDashboardPreview"))return;var mc=Minecraft.getInstance();
        if(screen==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;mc.options.guiScale().set(2);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;}
        if(++ticks==18){check();var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("freeze-panel-"+index+".png"));}}
        if(ticks==23){ticks=0;if(++index==14){System.out.println("All 14 control-volume/grab dashboard mode/scale and typed-input checks passed");mc.stop();}else{mc.options.guiScale().set(2+(index<8?index/4:(index-8)/3));mc.resizeDisplay();screen.init();}}
    }
    private static void check()throws Exception{
        DashboardChoiceCheck.run();for(var child:screen.panel.children())if(child instanceof AbstractWidget w&&w.visible&&(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>screen.width||w.getY()+w.getHeight()>screen.height))throw new AssertionError("Control widget outside viewport");
        var fields=(java.util.List<ModuleFields.Field>)get("parameters");var m=((java.util.List<VirtualModule>)get("modules")).getFirst();
        if(m.type().equals("freeze")&&fields.stream().anyMatch(f->f.key().equals("targetMultiple"))!=TargetCastOrigin.targeted(m))throw new AssertionError("Freeze Multiple target visibility incorrect");
        var expected=ModuleFields.forType(m.type()).stream().filter(f->SurfaceFieldSettings.visible(m,f.key())&&!java.util.List.of("warningSoundVolume","warningSoundRadius").contains(f.key())).map(ModuleFields.Field::key).toList();
        if(!fields.stream().map(ModuleFields.Field::key).toList().equals(expected))throw new AssertionError("Control parameters missing");
        for(var f:fields)if(net.minecraft.client.resources.language.I18n.get("dashboard.sablelife."+f.key()).startsWith("dashboard."))throw new AssertionError("Untranslated field");
        var page=HeartDashboardScreen.class.getDeclaredField("parameterPage");page.setAccessible(true);var sync=HeartDashboardScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);int slots=(int)get("parameterSlots");
        for(int p=0;p*slots<fields.size();p++){
            page.setInt(screen.panel,p);sync.invoke(screen.panel);var choices=(java.util.List<Button>)get("choices");
            for(int at=0;at<choices.size();at++){var choice=choices.get(at);if(choice.visible&&choice.getMessage().getString().contains("dashboard.sablelife"))throw new AssertionError("Untranslated option");int n=p*slots+at;
                if(n<fields.size()&&fields.get(n).key().equals("targetMultiple")&&(!choice.active||!choice.visible||choice.getWidth()!=18||!choice.getMessage().getString().equals(m.value("targetMultiple")==1?"✓":"")))throw new AssertionError("Freeze Multiple target checkbox disabled or incorrect");}
            for(var input:(java.util.List<EditBox>)get("fields"))if(input.visible&&input.active){input.setFocused(true);input.setValue("1.25");sync.invoke(screen.panel);if(!input.getValue().equals("1.25")||!input.isFocused())throw new AssertionError("Control draft lost");input.setFocused(false);}
        }
        page.setInt(screen.panel,0);sync.invoke(screen.panel);
    }
    private static final class Preview extends Screen{
        HeartDashboardScreen panel;Preview(){super(Component.empty());}
        @Override protected void init(){var modules=new HeartModules();var m=modules.add(index>=8?"grab":"freeze");m.edit("releasePosition",index%2,"");m.edit("freezeTarget",index%3,"");m.edit("targetMultiple",index%4/2,"");m.edit("fieldShape",index%4/2,"");m.edit("fieldPlacement",index%2,"");if(index>=8)m.edit("grabTarget",(index-8)%3,"");
            var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",m.id());tag.getList("Modules",10).getCompound(0).putString("Status","valid");var inv=new Inventory(null);var menu=new HeartDashboardMenu(1,inv);var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);panel=new HeartDashboardScreen(menu,inv,title);panel.init(minecraft,width,height);}
        @Override public void render(GuiGraphics g,int x,int y,float p){panel.render(g,-100,-100,p);}
    }
}
