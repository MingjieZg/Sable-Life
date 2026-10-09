package dev.sablelife.testing;
import dev.sablelife.module.*;
import dev.sablelife.client.HeartDashboardScreen;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
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
public final class SphereBlastClientPreview {
 private static Preview screen;private static int ticks,page;
 @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception{
  if(!Boolean.getBoolean("sablelife.signalTurnPreview")&&!Boolean.getBoolean("sablelife.accumPreview")&&!Boolean.getBoolean("sablelife.linearTargetPreview")&&!Boolean.getBoolean("sablelife.laserCutPreview")&&!Boolean.getBoolean("sablelife.particleRingPreview")&&!Boolean.getBoolean("sablelife.spherePreview")&&!Boolean.getBoolean("sablelife.facingPreview")&&!Boolean.getBoolean("sablelife.slashPreview"))return;var mc=Minecraft.getInstance();
  if(screen==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;mc.options.guiScale().set(2);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;}
  ticks++;if(ticks==30||ticks==60||ticks==90){page=Boolean.getBoolean("sablelife.signalTurnPreview")?(ticks==30?1:ticks==60?2:0):Boolean.getBoolean("sablelife.accumPreview")?(ticks==90?1:0):Boolean.getBoolean("sablelife.linearTargetPreview")?0:Boolean.getBoolean("sablelife.laserCutPreview")?(ticks==30?0:1):Boolean.getBoolean("sablelife.facingPreview")||Boolean.getBoolean("sablelife.particleRingPreview")?0:ticks==30?1:ticks==60?2:Boolean.getBoolean("sablelife.slashPreview")?3:2;if(ticks==60){mc.options.guiScale().set(3);mc.resizeDisplay();}screen.init(mc,screen.width,screen.height);}
  if(ticks==20||ticks==50||ticks==80||ticks==110){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("sphere-"+ticks+".png"));}}
  if(ticks==120){if(Boolean.getBoolean("sablelife.slashPreview")||Boolean.getBoolean("sablelife.spherePreview"))ParticleTravelClientCheck.run(mc);System.out.println("Attack dashboard pages rendered at GUI scale 2 and 3");mc.stop();}
 }
 private static final class Preview extends Screen{
  HeartDashboardScreen panel;Preview(){super(Component.empty());}
  @Override protected void init(){var modules=new HeartModules();var m=modules.add(Boolean.getBoolean("sablelife.signalTurnPreview")?"signal_turn":Boolean.getBoolean("sablelife.accumPreview")?"damage_accumulation":Boolean.getBoolean("sablelife.linearTargetPreview")?"linear_movement":Boolean.getBoolean("sablelife.laserCutPreview")?"laser":Boolean.getBoolean("sablelife.particleRingPreview")?"particles":Boolean.getBoolean("sablelife.facingPreview")?"auto_facing":Boolean.getBoolean("sablelife.slashPreview")?"slash":"sphere_blast");if(Boolean.getBoolean("sablelife.particleRingPreview")){m.edit("particlePattern",3,"");m.edit("particleRingHeight",8,"");m.edit("density",32,"");}if(Boolean.getBoolean("sablelife.laserCutPreview")){m.edit("laserBreakBlocks",1,"");m.edit("laserBreakMode",1,"");}if(Boolean.getBoolean("sablelife.linearTargetPreview")){m.edit("moveAim",1,"");m.edit("moveStopDistance",2,"");}if(Boolean.getBoolean("sablelife.accumPreview")){m.edit("accumInvert",1,"");m.edit("accumChannel",1,"");}var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",m.id());tag.getList("Modules",10).getCompound(0).putString("Status","valid");if(Boolean.getBoolean("sablelife.linearTargetPreview"))tag.getList("Modules",10).getCompound(0).putString("MovementPhase","no_target");var inv=new Inventory(null);var menu=new HeartDashboardMenu(1,inv);var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);panel=new HeartDashboardScreen(menu,inv,title);try{var f=HeartDashboardScreen.class.getDeclaredField("parameterPage");f.setAccessible(true);panel.init(minecraft,width,height);f.setInt(panel,page);var sync=HeartDashboardScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);sync.invoke(panel);}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}}
  @Override public void render(GuiGraphics g,int x,int y,float p){panel.render(g,-100,-100,p);}
 }
}
