package dev.sablelife.testing;
import dev.sablelife.client.*;
import dev.sablelife.module.*;
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
public final class AdaptiveDashboardClientPreview {
 private static Preview screen;private static int ticks,index,originalDensity;
 private static int number(String field)throws Exception{for(Class<?> type=HeartDashboardScreen.class;type!=null;type=type.getSuperclass()){try{var f=type.getDeclaredField(field);f.setAccessible(true);return f.getInt(screen.panel);}catch(NoSuchFieldException ignored){}}throw new NoSuchFieldException(field);}
 private static void check()throws Exception{
  int h=number("imageHeight"),w=number("imageWidth"),slots=number("sidebarSlots"),pitch=number("sidebarPitch"),fields=number("parameterSlots"),row=number("rowPitch");
  if(h>screen.height||w>screen.width)throw new AssertionError("Panel exceeds the viewport");
  if(47+(slots+1)*pitch<=h-44)throw new AssertionError("Sidebar left room for another row but paginated early");
  if(63+(fields+1)*row<=h-99)throw new AssertionError("Property panel left room for another row");
  if(slots>=9&&number("page")!=0)throw new AssertionError("Nine modules still spilled onto second page despite enough room");
  System.out.println("Adaptive dashboard scale="+Minecraft.getInstance().options.guiScale().get()+" density="+LifeUiStyle.density()+" viewport="+screen.width+"x"+screen.height+" rows="+slots+" fields="+fields);
 }
 private static void layout(Minecraft mc){int scale=index/4+1,density=index%4;while(LifeUiStyle.density()!=density)LifeUiStyle.cycle();mc.options.guiScale().set(scale);mc.resizeDisplay();screen.init(mc,screen.width,screen.height);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!Boolean.getBoolean("sablelife.adaptiveDashboardPreview"))return;var mc=Minecraft.getInstance();if(screen==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;originalDensity=LifeUiStyle.density();screen=new Preview();mc.setScreen(screen);layout(mc);return;}
  if(++ticks==12){check();if(index%4==0||Boolean.getBoolean("sablelife.healthModulesPreview")||Boolean.getBoolean("sablelife.hoverAnchorPreview")&&index%4==3||Boolean.getBoolean("sablelife.gyroPreview")&&index%4==3){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(folder.resolve((Boolean.getBoolean("sablelife.healthModulesPreview")?"health-modules-":Boolean.getBoolean("sablelife.hoverAnchorPreview")?"hover-anchor-":Boolean.getBoolean("sablelife.internalRedstonePreview")?"internal-redstone-":Boolean.getBoolean("sablelife.pulseVisualPreview")?"pulse-adaptive-":Boolean.getBoolean("sablelife.gyroPreview")?"gyro-adaptive-":"adaptive-")+index+".png"));}}}
  if(ticks==15){ticks=0;if(++index<16)layout(mc);else{while(LifeUiStyle.density()!=originalDensity)LifeUiStyle.cycle();if(Boolean.getBoolean("sablelife.pulseVisualPreview"))ParticleTravelClientCheck.run(mc);System.out.println("All 16 scale/density combinations passed");mc.stop();}}
 }
 private static final class Preview extends Screen {
  HeartDashboardScreen panel;Preview(){super(Component.empty());}
  @Override protected void init(){var modules=new HeartModules();for(int i=0;i<9;i++)modules.add(Boolean.getBoolean("sablelife.healthModulesPreview")&&i==8?(index%4<2?"hit_counter":"health_adjustment"):Boolean.getBoolean("sablelife.healthModulesPreview")&&i==0?"melee":Boolean.getBoolean("sablelife.hoverAnchorPreview")&&i==8?"hover_anchor":Boolean.getBoolean("sablelife.internalRedstonePreview")&&i==8?"wireless_redstone":Boolean.getBoolean("sablelife.pulseVisualPreview")&&i==8?"pulse":Boolean.getBoolean("sablelife.gyroPreview")&&i==8?"gyroscope":i==0?"sound":"item_display");if(Boolean.getBoolean("sablelife.gyroPreview"))modules.all().get(8).gyroDown(new net.minecraft.world.phys.Vec3(0,-1,0));if(Boolean.getBoolean("sablelife.pulseVisualPreview")){modules.all().get(8).edit("pulseVisual",1,"");modules.all().get(8).edit("pulseExpandTime",10,"");}if(Boolean.getBoolean("sablelife.healthModulesPreview")&&index%4<2)modules.all().get(8).hitSource(modules.all().getFirst().id(),1);if(Boolean.getBoolean("sablelife.hoverAnchorPreview"))modules.all().get(8).edit("hoverMode",1,"");if(Boolean.getBoolean("sablelife.internalRedstonePreview"))modules.all().get(8).edit("redstoneChannel",10,"");var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",modules.all().get(8).id());for(var item:tag.getList("Modules",10))((net.minecraft.nbt.CompoundTag)item).putString("Status","valid");if(Boolean.getBoolean("sablelife.gyroPreview")){var data=tag.getList("Modules",10).getCompound(8);data.putBoolean("GyroCalibrated",true);data.putBoolean("TriggerPowered",true);data.putDouble("GyroTilt",25.4);data.putString("GyroState","recovering");}var inv=new Inventory(null);var menu=new HeartDashboardMenu(1,inv);
   // Open-screen arrives before the slot snapshot: init must tolerate no selected module.
   var loading=new HeartDashboardScreen(menu,inv,title);loading.init(minecraft,width,height);
   var emptyTag=new HeartModules().save();emptyTag.putBoolean("Ready",true);emptyTag.putBoolean("Editable",true);
   var emptyStack=new ItemStack(Items.PAPER);emptyStack.set(DataComponents.CUSTOM_DATA,CustomData.of(emptyTag));menu.getSlot(0).set(emptyStack);
   var empty=new HeartDashboardScreen(menu,inv,title);empty.init(minecraft,width,height);
   System.out.println("Dashboard loading and empty snapshot init passed");
   var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);panel=new HeartDashboardScreen(menu,inv,title);panel.init(minecraft,width,height);if(Boolean.getBoolean("sablelife.pulseVisualPreview")||Boolean.getBoolean("sablelife.hoverAnchorPreview")&&index%4==3||Boolean.getBoolean("sablelife.healthModulesPreview")&&index%4==1)try{var f=HeartDashboardScreen.class.getDeclaredField("parameterPage");f.setAccessible(true);f.setInt(panel,1);var sync=HeartDashboardScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);sync.invoke(panel);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
  @Override public void render(GuiGraphics g,int x,int y,float partial){panel.render(g,-100,-100,partial);}
 }
}
