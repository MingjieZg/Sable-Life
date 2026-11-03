package dev.sablelife.testing;

import dev.sablelife.client.*;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Uses the real screens in an isolated client; never opens or edits a player world. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class ManagementClientPreview {
 private static final class Host extends Screen {final Screen panel;Host(Screen panel){super(Component.empty());this.panel=panel;}@Override protected void init(){panel.init(minecraft,width,height);}@Override public void render(GuiGraphics g,int x,int y,float partial){panel.render(g,-100,-100,partial);}}
 private static void show(Screen panel){Minecraft.getInstance().setScreen(new Host(panel));}
 private static boolean loading;private static int tick,layout;private static HeartDashboardScreen dashboard;private static ModuleInspectorScreen inspector;private static HeartDashboardMenu menu;private static UUID tableId;private static Inventory inventory;
 private static Object get(Object object,Class<?> type,String key)throws Exception{var f=type.getDeclaredField(key);f.setAccessible(true);return f.get(object);}
 private static void set(Object object,String key,Object value)throws Exception{var f=ModuleInspectorScreen.class.getDeclaredField(key);f.setAccessible(true);f.set(object,value);}
 private static void sync()throws Exception{var m=ModuleInspectorScreen.class.getDeclaredMethod("sync");m.setAccessible(true);m.invoke(inspector);}
 private static void capture(String name)throws Exception{var mc=Minecraft.getInstance();var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}}
 private static void build(){
  var modules=new HeartModules();var gun=modules.add("gun");gun.edit("name",0,"Main cannon");gun.group("Phase 2");var table=modules.add("move_set");tableId=table.id();table.edit("name",0,"Phase 2 moves");table.group("Phase 2");
  for(int i=0;i<18;i++){table.moveSet().edit("add",0,"",modules);table.moveSet().edit("action_add",0,i+"|"+gun.id(),modules);}
  var relay=modules.add("wireless_redstone");relay.group("Signals");relay.edit("redstoneChannel",1,"");relay.edit("redstonePower",12,"");var consumer=modules.add("flame");consumer.edit("trigger",2,"");consumer.edit("channel",1,"");
  for(int i=0;i<28;i++){var m=modules.add("particles");m.group("Phase "+(i%3+1));m.edit("name",0,"Particle "+i);}
  var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",gun.id());tag.put("References",ModuleConnections.references(ModuleConnections.links(modules.all()),gun.id()));
  var diag=new CompoundTag();diag.putInt("Channel",0);diag.putInt("Count",3);diag.putInt("Power",12);diag.putInt("Mode",3);diag.putString("Name","Phase 2 control");diag.putBoolean("Master",true);diag.putBoolean("Ready",true);diag.putBoolean("Configured",true);
  var sources=new ListTag();var source=new CompoundTag();source.putUUID("Module",relay.id());source.putInt("Power",12);sources.add(source);diag.put("Sources",sources);var links=new ListTag();for(var e:ModuleConnections.endpoints(modules.all()))if(e.channel()==0){var row=new CompoundTag();row.putUUID("Module",e.module());row.putBoolean("Output",e.output());row.putBoolean("External",e.external());links.add(row);}diag.put("Links",links);tag.put("ChannelDiagnostics",diag);
  inventory=new Inventory(null);menu=new HeartDashboardMenu(1,inventory);var item=new ItemStack(Items.PAPER);item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(item);dashboard=new HeartDashboardScreen(menu,inventory,Component.empty());show(dashboard);
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!Boolean.getBoolean("sablelife.managementPreview"))return;var mc=Minecraft.getInstance();if(dashboard==null){if(mc.screen==null||mc.getOverlay()!=null)return;if(!loading){loading=true;mc.options.languageCode="zh_cn";mc.getLanguageManager().setSelected("zh_cn");mc.reloadResourcePacks();return;}build();return;}
  if(++tick%4!=0)return;
  if(layout<16){mc.options.guiScale().set(layout/4+1);while(LifeUiStyle.density()!=layout%4)LifeUiStyle.cycle();mc.resizeDisplay();show(dashboard);
   var rows=(List<ModuleFolders.Row>)get(dashboard,HeartDashboardScreen.class,"folderRows");if(rows.stream().noneMatch(ModuleFolders.Row::header))throw new AssertionError("Dashboard omitted folder headers");
   var search=(EditBox)get(dashboard,HeartDashboardScreen.class,"moduleSearch");var control=dashboard.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals("…")).map(w->(Button)w).findFirst().orElseThrow();if(search.getX()+search.getWidth()>=control.getX())throw new AssertionError("Search overlaps manager button");
   inspector=new ModuleInspectorScreen(dashboard);show(inspector);for(int tab=0;tab<3;tab++){set(inspector,"tab",tab);sync();var buttons=(List<Button>)get(inspector,ModuleInspectorScreen.class,"buttons");for(var b:buttons)if(b.visible&&(b.getX()<0||b.getY()<0||b.getX()+b.getWidth()>inspector.width||b.getY()+b.getHeight()>inspector.height))throw new AssertionError("Manager rows outside screen at layout"+layout);}
   layout++;return;
  }
  switch(layout++){
   case 16->{mc.options.guiScale().set(2);while(LifeUiStyle.density()!=2)LifeUiStyle.cycle();mc.resizeDisplay();show(dashboard);}
   case 17->{capture("module-folders");inspector=new ModuleInspectorScreen(dashboard);show(inspector);}
   case 18->{capture("module-references");set(inspector,"tab",1);sync();}
   case 19->{capture("module-groups");var input=(EditBox)get(inspector,ModuleInspectorScreen.class,"group");input.setFocused(true);input.setValue("Unsaved input");sync();if(!input.getValue().equals("Unsaved input"))throw new AssertionError("Refresh discarded group editing");inspector.keyPressed(69,0,0);if(!(mc.screen instanceof Host host&&host.panel==inspector))throw new AssertionError("Inventory hotkey closed focused editor");set(inspector,"tab",2);sync();}
   case 20->{capture("module-channels");var move=new MoveSetScreen(menu,inventory,Component.empty(),tableId,17);show(move);if((int)get(move,MoveSetScreen.class,"movePage")==0)throw new AssertionError("Exact move jump did not reveal move page");}
   case 21->{System.out.println("MANAGEMENT_UI_PASS:16 layout combinations, all tabs, focus retention, exact move page");mc.stop();}
  }
 }
}
