package dev.sablelife.testing;
import dev.sablelife.client.HeartDashboardScreen;
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
/** Real dashboard, isolated snapshots, no player save access. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class EntityHitboxClientPreview {
    private static HeartDashboardMenu currentMenu;private static HeartDashboardScreen panel;private static int index,ticks;private static boolean started;
    private static final class Host extends Screen {Host(){super(Component.empty());}@Override protected void init(){if(panel!=null)panel.init(minecraft,width,height);}@Override public void render(GuiGraphics g,int x,int y,float p){if(panel!=null)panel.render(g,-100,-100,p);}}
    private static int minScale(){return Math.min(index/2+1,4);}
    private static void build(){var mc=Minecraft.getInstance();mc.options.guiScale().set(minScale());mc.resizeDisplay();String lang=index%2==0?"en_us":"zh_cn";mc.getLanguageManager().setSelected(lang);mc.options.languageCode=lang;mc.reloadResourcePacks().thenRun(()->mc.execute(()->{
        var inv=new Inventory(null);var modules=new HeartModules();var m=modules.add("entity_hitbox");m.edit("hitboxDamageMode",1,"");m.edit("hitboxRetaliateAgainstWhitelisted",index==4||index==5?1:0,"");var tag=modules.save();tag.putBoolean("HybridDamage",true);tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putString("LifeState","ALIVE");tag.putUUID("Selected",m.id());var data=tag.getList("Modules",10).getCompound(0);data.putString("Status","valid");data.putBoolean("ReceiverActive",true);data.putBoolean("TriggerPowered",true);var menu=new HeartDashboardMenu(1,inv);var item=new ItemStack(Items.PAPER);item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));if(index==8)item=ItemStack.EMPTY;else if(index==9){var empty=new CompoundTag();empty.putBoolean("Ready",true);empty.putBoolean("Editable",true);item.set(DataComponents.CUSTOM_DATA,CustomData.of(empty));}menu.getSlot(0).set(item);currentMenu=menu;panel=new HeartDashboardScreen(menu,inv,Component.empty());mc.setScreen(new Host());ticks=0;
    }));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{if(!Boolean.getBoolean("sablelife.entityHitboxPreview"))return;var mc=Minecraft.getInstance();if(!started){if(mc.screen==null||mc.getOverlay()!=null)return;started=true;build();return;}if(panel==null||mc.getOverlay()!=null||!(mc.screen instanceof Host))return;
        if(index==10&&ticks==5){var empty=new CompoundTag();empty.putBoolean("Ready",true);empty.putBoolean("Editable",true);var item=new ItemStack(Items.PAPER);item.set(DataComponents.CUSTOM_DATA,CustomData.of(empty));currentMenu.getSlot(0).set(item);}
        if(++ticks==14){for(var child:panel.children())if(child instanceof AbstractWidget w&&w.visible){if(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>panel.width||w.getY()+w.getHeight()>panel.height)throw new AssertionError("Receiver widget outside viewport: "+index);if(w.getMessage().getString().contains("dashboard.sablelife"))throw new AssertionError("Untranslated receiver widget");}if(index==4||index==5){for(String prefix:java.util.List.of("dashboard.sablelife.","dashboard.sablelife.property_help.")){String key=prefix+"hitboxRetaliateAgainstWhitelisted";if(Component.translatable(key).getString().equals(key))throw new AssertionError("Missing "+key);}var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("entity-hitbox-"+index+".png"));}}}
        if(ticks==18){panel=null;if(++index==11){System.out.println("ENTITY_HITBOX_UI_PASS: 8 receiver layouts plus pending snapshot, empty core, and last-module removal");mc.stop();}else build();}
    }
}
