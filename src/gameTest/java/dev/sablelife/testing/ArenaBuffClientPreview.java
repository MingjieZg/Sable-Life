package dev.sablelife.testing;
import dev.sablelife.client.*;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class ArenaBuffClientPreview {
 private static int ticks,mode;private static Preview preview;
 @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception{
  if(!Boolean.getBoolean("sablelife.arenaBuffPreview"))return;var mc=Minecraft.getInstance();
  if(preview==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;mc.options.guiScale().set(1);mc.resizeDisplay();preview=new Preview();mc.setScreen(preview);return;}
  ticks++;
  if(ticks==35||ticks==70||ticks==105){mc.options.guiScale().set(ticks/35+1);mc.resizeDisplay();}
  if(ticks==132){mode=1;preview.init(mc,preview.width,preview.height);}
  if(ticks==25||ticks==60||ticks==95||ticks==120||ticks==150){var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("arena-buffs-"+ticks+".png"));}}
  if(ticks==165){System.out.println("Arena buff pagination, GUI 1-4 layout and fixed-duration effect selection checks passed");mc.stop();}
 }
 private static final class Preview extends Screen {
  private Screen panel;Preview(){super(Component.empty());}
  @Override protected void init(){try{
   var inv=new Inventory(null);var config=new ArenaBuffs();for(int i=0;i<16;i++){var g=config.add();g.edit("source",i%3);g.edit("channel",i+1);g.effect("add","minecraft:speed",0);}
   var tag=new CompoundTag();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("ModuleId",java.util.UUID.randomUUID());tag.put("Groups",config.save());tag.putInt("Channels",16);
   if(mode==0){var menu=new ArenaBuffMenu(1,inv);var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(paper);var screen=new ArenaBuffScreen(menu,inv,title);panel=screen;screen.init(minecraft,width,height);
    var pf=ArenaBuffScreen.class.getDeclaredField("page");pf.setAccessible(true);var nf=ArenaBuffScreen.class.getDeclaredField("next");nf.setAccessible(true);var next=(net.minecraft.client.gui.components.Button)nf.get(screen);
    if(!next.active||pf.getInt(screen)!=0)throw new AssertionError("Group editor did not start on first page");next.onPress();screen.render(new GuiGraphics(minecraft,minecraft.renderBuffers().bufferSource()),-100,-100,0);if(pf.getInt(screen)!=1)throw new AssertionError("Pagination snapped back to selection");
    var prev=ArenaBuffScreen.class.getDeclaredField("prev");prev.setAccessible(true);((net.minecraft.client.gui.components.Button)prev.get(screen)).onPress();
    for(var widget:screen.children())if(widget instanceof net.minecraft.client.gui.components.Button b&&b.visible&&(b.getY()<0||b.getY()+b.getHeight()>height))throw new AssertionError("Button clipped at GUI scale");
   } else {var menu=new EffectSelectionMenu(1,inv);var data=new CompoundTag();data.put("Module",config.groups().getFirst().editor().save());data.putBoolean("Editable",true);data.putBoolean("ArenaBuffMode",true);var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(data));menu.getSlot(0).set(paper);var screen=new EffectSelectionScreen(menu,inv,title);panel=screen;screen.init(minecraft,width,height);
    var duration=EffectSelectionScreen.class.getDeclaredField("duration");duration.setAccessible(true);if(((net.minecraft.client.gui.components.EditBox)duration.get(screen)).visible)throw new AssertionError("Buff duration was editable");
    var filter=EffectSelectionScreen.class.getDeclaredMethod("filtered");filter.setAccessible(true);for(var id:(java.util.List<String>)filter.invoke(screen))if(!ArenaBuffs.persistent(id))throw new AssertionError("Instant effect offered as buff");
   }
  }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}}
  @Override public void render(GuiGraphics g,int x,int y,float partial){panel.render(g,-100,-100,partial);}
 }
}
