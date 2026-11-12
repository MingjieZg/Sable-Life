package dev.sablelife.testing;

import dev.sablelife.client.*;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
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
 private static int livePhase, liveTicks;
 private static volatile BlockPos liveCore;
 private static Object get(Object object,Class<?> type,String key)throws Exception{var f=type.getDeclaredField(key);f.setAccessible(true);return f.get(object);}
 private static void set(Object object,String key,Object value)throws Exception{var f=ModuleInspectorScreen.class.getDeclaredField(key);f.setAccessible(true);f.set(object,value);}
 private static void sync()throws Exception{var m=ModuleInspectorScreen.class.getDeclaredMethod("sync");m.setAccessible(true);m.invoke(inspector);}
 private static void capture(String name)throws Exception{var mc=Minecraft.getInstance();var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}}
 private static void build(){
  var modules=new HeartModules();var gun=modules.add("gun");gun.edit("name",0,"Main cannon");gun.group("Phase 2");var table=modules.add("move_set");tableId=table.id();table.edit("name",0,"Phase 2 moves");table.group("Phase 2");
  for(int i=0;i<18;i++){table.moveSet().edit("add",0,"",modules);table.moveSet().edit("action_add",0,i+"|"+gun.id(),modules);}
  var relay=modules.add("wireless_redstone");relay.group("Signals");relay.edit("redstoneChannel",1,"");relay.edit("redstonePower",12,"");var consumer=modules.add("flame");consumer.edit("trigger",2,"");consumer.edit("channel",1,"");
  for(int i=0;i<28;i++){var m=modules.add("particles");m.group("Phase "+(i%3+1));m.edit("name",0,"Particle "+i);}
  var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putBoolean("GroupTools",true);tag.putBoolean("GroupMemberEditing",true);tag.putInt("ModuleLimit",128);tag.putUUID("Selected",gun.id());tag.put("References",ModuleConnections.references(ModuleConnections.links(modules.all()),gun.id()));
  for (var row : tag.getList("Modules", 10)) ((CompoundTag) row).putString("Status", "unmarked");
  var types=new ListTag();for(String type:VirtualModule.AVAILABLE_TYPES)types.add(StringTag.valueOf(type));tag.put("AvailableTypes",types);
  var diag=new CompoundTag();diag.putInt("Channel",0);diag.putInt("Count",3);diag.putInt("Power",12);diag.putInt("Mode",3);diag.putString("Name","Phase 2 control");diag.putBoolean("Master",true);diag.putBoolean("Ready",true);diag.putBoolean("Configured",true);
  var sources=new ListTag();var source=new CompoundTag();source.putUUID("Module",relay.id());source.putInt("Power",12);sources.add(source);diag.put("Sources",sources);var links=new ListTag();for(var e:ModuleConnections.endpoints(modules.all()))if(e.channel()==0){var row=new CompoundTag();row.putUUID("Module",e.module());row.putBoolean("Output",e.output());row.putBoolean("External",e.external());links.add(row);}diag.put("Links",links);tag.put("ChannelDiagnostics",diag);
  inventory=new Inventory(null);menu=new HeartDashboardMenu(1,inventory);var item=new ItemStack(Items.PAPER);item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(item);dashboard=new HeartDashboardScreen(menu,inventory,Component.empty());show(dashboard);
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!Boolean.getBoolean("sablelife.managementPreview"))return;var mc=Minecraft.getInstance();if(dashboard==null){if(mc.screen==null||mc.getOverlay()!=null)return;if(!loading){loading=true;mc.options.languageCode="zh_cn";mc.getLanguageManager().setSelected("zh_cn");mc.reloadResourcePacks();return;}build();return;}
  if (layout >= 29) { testLiveConfirm(mc); return; }
  if(mc.getOverlay()!=null||++tick%4!=0)return;
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
   case 21->{show(dashboard);selectGroupModules();}
   case 22->{capture("module-multiselect-zh");openGroupMove();}
   case 23->{capture("module-move-group-zh");startInlineGroup();}
   case 24->{capture("module-group-members-zh");cancelInlineGroup();mc.options.languageCode="en_us";mc.getLanguageManager().setSelected("en_us");mc.reloadResourcePacks();}
   case 25->{show(dashboard);selectGroupModules();}
   case 26->{capture("module-multiselect-en");openGroupMove();}
   case 27->{capture("module-move-group-en");startInlineGroup();}
   case 28->{
    capture("module-group-members-en");
    cancelInlineGroup();
    System.out.println("MANAGEMENT_UI_PASS: 16 layout combinations, both locales, multiselect and inline membership editing");
    var settings=new LevelSettings("Group membership test",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
    mc.createWorldOpenFlows().createFreshLevel("group-members-"+System.currentTimeMillis(),settings,new WorldOptions(1234,false,false),
            access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
   }
  }
 }

 private static void testLiveConfirm(Minecraft mc)throws Exception{
  if (++liveTicks > 1200) throw new AssertionError("Group membership client test stalled at phase "+livePhase);
  var server=mc.getSingleplayerServer();
  if(mc.player==null || mc.gameMode==null || server==null || mc.screen!=null && livePhase<2) return;
  if(livePhase==0){
   livePhase=1;
   server.execute(()->{
    var player=server.getPlayerList().getPlayers().getFirst();
    var level=player.serverLevel();
    var pos=player.blockPosition().east(2);
    level.setBlockAndUpdate(pos,dev.sablelife.SableLife.HEART.get().defaultBlockState());
    var core=(dev.sablelife.heart.HeartBlockEntity)level.getBlockEntity(pos);
    core.modules().add("gun").group("Test group");
    core.modules().add("sound").group("Test group");
    core.modules().add("particles");
    core.setChanged();
    liveCore=pos;
   });
  }else if(livePhase==1 && liveCore!=null && mc.level.getBlockState(liveCore).is(dev.sablelife.SableLife.HEART.get())){
   mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(liveCore.getCenter(),Direction.UP,liveCore,false));
   livePhase=2;
  }else if(livePhase==2 && mc.screen instanceof HeartDashboardScreen live && live.getMenu().snapshot().getBoolean("GroupMemberEditing")){
   dashboard=live;
   syncDashboard();
   var rows=dashboardRows();
   click(rows.getFirst(),1);
   click(rows.get(1),0);
   click(rows.get(3),0);
   var confirm=dashboardButton("confirmGroupMembers");
   if(!confirm.active)throw new AssertionError("Live dashboard did not stage membership edits");
   click(confirm,0);
   if(confirm.visible)throw new AssertionError("Confirm did not close the membership editor");
   livePhase=3;
  }else if(livePhase==3 && mc.screen instanceof HeartDashboardScreen live){
   var modules=live.getMenu().snapshot().getList("Modules",10);
   if(modules.size()==3 && modules.getCompound(0).getString("Group").isEmpty()
           && modules.getCompound(1).getString("Group").equals("Test group")
           && modules.getCompound(2).getString("Group").equals("Test group")){
    livePhase=4;
    System.out.println("GROUP_MEMBERS_LIVE_PASS: core right-click, uncheck, check, confirm, and server sync");
    mc.stop();
   }
  }
 }

 private static void click(Button button,int mouseButton){
  if(!dashboard.mouseClicked(button.getX()+3,button.getY()+3,mouseButton))
   throw new AssertionError("Dashboard click was not handled: "+button.getMessage().getString());
 }

 private static Button dashboardButton(String name)throws Exception{return (Button)get(dashboard,HeartDashboardScreen.class,name);}
 private static List<Button> dashboardRows()throws Exception{return (List<Button>)get(dashboard,HeartDashboardScreen.class,"rows");}
 private static void selectGroupModules()throws Exception{
  if(!dashboardButton("add").visible || dashboardButton("moveToGroup").visible)
   throw new AssertionError("Group actions occupy the normal module footer");
  var click=HeartDashboardScreen.class.getDeclaredMethod("clickSidebar",int.class,boolean.class,boolean.class);
  click.setAccessible(true);
  click.invoke(dashboard,1,true,false);
  click.invoke(dashboard,2,false,true);
  var move=dashboardButton("moveToGroup");
  if(!move.visible||!move.active||!move.getMessage().getString().contains("2")||dashboardButton("add").visible)
   throw new AssertionError("Batch footer state incorrect");
  var search=(EditBox)get(dashboard,HeartDashboardScreen.class,"moduleSearch");
  search.setValue("Signal");
  if(((Set<?>)get(dashboard,HeartDashboardScreen.class,"groupSelection")).size()!=2)throw new AssertionError("Search dropped batch selection");
  search.setValue("");
 }

 private static void openGroupMove()throws Exception{
  dashboardButton("moveToGroup").onPress();
  var screen=Minecraft.getInstance().screen;
  if(screen==null||!screen.getTitle().getString().contains("2")||screen.children().stream().noneMatch(widget->widget instanceof Button b&&b.getMessage().getString().equals("Phase 2")))
   throw new AssertionError("Group picker missing selection count or existing groups");
  show(screen);
 }

 private static void startInlineGroup()throws Exception{
  show(dashboard);
  dashboard.keyPressed(256,0,0);
  var header=dashboardRows().getFirst();
  var confirm=dashboardButton("confirmGroupMembers");
  int right=header.getX()+header.getWidth();
  if(!dashboard.mouseClicked(header.getX()+3,header.getY()+3,1)
          || !(Minecraft.getInstance().screen instanceof Host host && host.panel == dashboard))
   throw new AssertionError("Group selection did not stay in the dashboard");
  if(!confirm.visible || confirm.active || confirm.getY()!=header.getY() || confirm.getHeight()!=header.getHeight()
          || confirm.getX()!=header.getX()+header.getWidth()+3 || confirm.getX()+confirm.getWidth()!=right)
   throw new AssertionError("Confirm does not align with the group header");
  var member=dashboardRows().get(1);
  if(!member.active || !member.getMessage().getString().startsWith("[✓]"))
   throw new AssertionError("Existing group member is not editable and checked");
  if(!dashboard.mouseClicked(member.getX()+3,member.getY()+3,0) || !confirm.active
          || !member.getMessage().getString().startsWith("[ ]"))
   throw new AssertionError("Unchecking an existing member did not stage removal");
  dashboard.mouseClicked(member.getX()+3,member.getY()+3,0);
  if(confirm.active || !member.getMessage().getString().startsWith("[✓]"))
   throw new AssertionError("Rechecking a member did not cancel its pending removal");
  dashboard.mouseClicked(member.getX()+3,member.getY()+3,0);
  var search=(EditBox)get(dashboard,HeartDashboardScreen.class,"moduleSearch");
  search.setValue("Signals");
  syncDashboard();
  if(!header.getMessage().getString().contains("Phase 2"))throw new AssertionError("Search hid the destination group");
  var candidate=dashboardRows().get(2);
  if(!candidate.visible || !candidate.active || !candidate.getMessage().getString().startsWith("[ ]"))
   throw new AssertionError("Existing module did not get a checkbox");
  if(!dashboard.mouseClicked(candidate.getX()+3,candidate.getY()+3,0) || !confirm.active
          || !candidate.getMessage().getString().startsWith("[✓]"))
   throw new AssertionError("Click did not check the module or activate Confirm");
  search.setValue("");
  syncDashboard();
  var next=dashboard.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals("›"))
          .map(w->(Button)w).filter(b->b.getX()+b.getWidth()==right).findFirst().orElseThrow();
  next.onPress();
  if((int)get(dashboard,HeartDashboardScreen.class,"page")!=1 || !confirm.visible || !confirm.active)
   throw new AssertionError("Pagination lost the pending selection or Confirm");
  search.setValue("Main cannon");
  syncDashboard();
  if (!dashboardRows().get(1).getMessage().getString().startsWith("[ ]")
          || ((Map<?,?>)get(dashboard,HeartDashboardScreen.class,"groupMembershipEdits")).size()!=2)
   throw new AssertionError("Search and pagination did not retain both membership edits");
  search.setValue("");
  syncDashboard();
 }

 private static void cancelInlineGroup()throws Exception{
  if(!dashboard.keyPressed(256,0,0) || dashboardButton("confirmGroupMembers").visible
          || !dashboardButton("add").visible)
   throw new AssertionError("Cancel did not restore the ordinary dashboard");
  ((EditBox)get(dashboard,HeartDashboardScreen.class,"moduleSearch")).setValue("");
  syncDashboard();
  if (((Map<?,?>)get(dashboard,HeartDashboardScreen.class,"groupMembershipEdits")).size()!=0)
   throw new AssertionError("Cancel retained pending membership edits");
 }

 private static void syncDashboard()throws Exception{
  var sync=HeartDashboardScreen.class.getDeclaredMethod("sync");
  sync.setAccessible(true);
  sync.invoke(dashboard);
 }
}
