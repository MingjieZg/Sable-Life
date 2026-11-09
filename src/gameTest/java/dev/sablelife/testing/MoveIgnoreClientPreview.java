package dev.sablelife.testing;

import java.util.*;
import dev.sablelife.client.*;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Exercises the outer checkbox, locked selection checks and editable timing pages, without a player world. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class MoveIgnoreClientPreview {
    private static Screen panel;private static int ticks,index;private static boolean started;
    private static final class Host extends Screen {Host(){super(Component.empty());}@Override protected void init(){panel.init(minecraft,width,height);}@Override public void render(GuiGraphics g,int x,int y,float p){panel.render(g,-100,-100,p);}}
    private static Object get(String name)throws Exception{var f=panel.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(panel);}
    private static void set(String name,Object value)throws Exception{var f=panel.getClass().getDeclaredField(name);f.setAccessible(true);f.set(panel,value);}
    private static void sync()throws Exception{var m=panel.getClass().getDeclaredMethod("sync");m.setAccessible(true);m.invoke(panel);}
    private static void build()throws Exception{var mc=Minecraft.getInstance();mc.options.guiScale().set(index/4+1);mc.resizeDisplay();var modules=new HeartModules();var output=modules.add("wireless_redstone");var table=modules.add("move_set");table.moveSet().edit("add",0,"",modules);table.moveSet().edit("action_add",0,"0|"+output.id(),modules);table.edit("ignoreConditions",index%4==1?0:1,"");if(index%4==3)table.moveSet().edit("selection_mode",1,"0",modules);var data=modules.save();data.putBoolean("Ready",true);data.putBoolean("Editable",true);data.putUUID("Selected",table.id());var inv=new Inventory(null);var menu=new HeartDashboardMenu(1,inv);var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(data));menu.getSlot(0).set(paper);
        panel=index%4==0?new HeartDashboardScreen(menu,inv,Component.empty()):new MoveSetScreen(menu,inv,Component.empty(),table.id());mc.setScreen(new Host());if(index%4!=0){set("rules",true);panel.init(mc,panel.width,panel.height);}
    }
    private static void check()throws Exception{if(index%4==0){var fields=(List<ModuleFields.Field>)get("parameters");if(fields.stream().noneMatch(f->f.key().equals("ignoreConditions")))throw new AssertionError("Outer properties omitted checkbox");return;}
        var fields=(List<ModuleFields.Field>)get("ruleFields");int slots=(int)get("ruleSlots");for(int page=0;page*slots<fields.size();page++){set("rulePage",page);sync();var inputs=(List<EditBox>)get("ruleInputs");var choices=(List<Button>)get("ruleChoices");var apply=(List<Button>)get("applyButtons");for(int i=0;i<slots&&page*slots+i<fields.size();i++){var f=fields.get(page*slots+i);boolean weight=f.key().equals("dispatchWeight");boolean editable=index%4==1||List.of("dispatchWeight","dispatchCooldown","dispatchBusy","dispatchRest","dispatchRepeat").contains(f.key());boolean inputEditable=editable&&!(weight&&index%4==3);if(inputs.get(i).visible&&inputs.get(i).active!=inputEditable)throw new AssertionError("Incorrect condition input lock: "+f.key());if(choices.get(i).visible&&choices.get(i).active!=editable)throw new AssertionError("Incorrect condition choice lock: "+f.key());if(apply.get(i+1).visible&&apply.get(i+1).active!=inputEditable)throw new AssertionError("Locked condition apply still editable: "+f.key());}}
        set("rulePage",0);sync();for(var child:panel.children())if(child instanceof AbstractWidget w&&w.visible&&(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>panel.width||w.getY()+w.getHeight()>panel.height))throw new AssertionError("Move UI outside viewport");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{if(!Boolean.getBoolean("sablelife.moveIgnorePreview"))return;var mc=Minecraft.getInstance();if(!started){if(mc.screen==null||mc.getOverlay()!=null)return;started=true;build();return;}if(++ticks==14){check();if(index<4){var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("move-ignore-"+index+".png"));}}}if(ticks==18){ticks=0;if(++index==16){System.out.println("MOVE_IGNORE_UI_PASS: outer checkbox, all condition pages, priority/weight and 4 GUI scales");mc.stop();}else build();}}
}
