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
public final class SignalRuleClientPreview {
 private static Preview screen;private static int ticks,index;
 private static Object get(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!Boolean.getBoolean("sablelife.signalRulePreview"))return;var mc=Minecraft.getInstance();if(screen==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;mc.options.guiScale().set(1);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;}
  if(++ticks==18){check();var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("signal-rules-"+index+".png"));}}
  if(ticks==23){ticks=0;if(++index==8){System.out.println("All 8 compact signal logic/event single-page GUI scale checks passed");mc.stop();}else{mc.options.guiScale().set(index/2+1);mc.resizeDisplay();screen.reinit();}}
 }
 private static void check()throws Exception{
  var panel=screen.panel;var sync=SignalRuleScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);var page=SignalRuleScreen.class.getDeclaredField("parameterPage");page.setAccessible(true);int slots=(int)get(panel,"slots");if(slots<SignalRules.fields(screen.type).size()||((Button)get(panel,"parameterNext")).visible||((Button)get(panel,"parameterPrev")).visible)throw new AssertionError("Rule settings still require parameter pagination");var fields=(java.util.List<EditBox>)get(panel,"fields");
  for(int p=0;p*slots<SignalRules.fields(screen.type).size();p++){page.setInt(panel,p);sync.invoke(panel);for(var element:panel.children())if(element instanceof AbstractWidget w&&w.visible&&(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>screen.width||w.getY()+w.getHeight()>screen.height))throw new AssertionError("Rule widget outside viewport");
   for(int i=0;i<slots&&p*slots+i<SignalRules.fields(screen.type).size();i++){var f=SignalRules.fields(screen.type).get(p*slots+i);if(net.minecraft.client.resources.language.I18n.get("dashboard.sablelife."+f.key()).startsWith("dashboard."))throw new AssertionError("Untranslated field");if(f.choice()){var b=((java.util.List<Button>)get(panel,"choices")).get(i);if(!b.visible||b.getMessage().getString().contains("dashboard.sablelife"))throw new AssertionError("Rule choice missing/ untranslated");}else{var box=fields.get(i);if(!box.visible||!box.active)throw new AssertionError("Numeric rule field not editable");box.setFocused(true);box.setValue("12");sync.invoke(panel);if(!box.isFocused()||!box.getValue().equals("12"))throw new AssertionError("Rule draft lost during snapshot sync");box.setFocused(false);}}}
  page.setInt(panel,0);sync.invoke(panel);System.out.println("Signal rule GUI "+index+" passed: "+screen.type+" "+screen.width+"x"+screen.height);
 }
 private static final class Preview extends Screen {
  SignalRuleScreen panel;String type;Preview(){super(Component.empty());}void reinit(){init();}
  @Override protected void init(){type=index%2==0?"signal_logic":"combat_event";var inv=new Inventory(null);var menu=new SignalRuleMenu(1,inv);panel=new SignalRuleScreen(menu,inv,title);panel.init(minecraft,width,height);
   var config=new SignalRules(type);var tag=new net.minecraft.nbt.CompoundTag();tag.putString("Type",type);tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.put("Groups",config.save());var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(paper);panel.init(minecraft,width,height);
   for(int i=0;i<(type.equals("signal_logic")?16:32);i++){var r=config.add();if(type.equals("signal_logic")){r.edit("signalInput",i+1);r.edit("signalCondition",i%2);}else{r.edit("eventKind",(i+index/2)%2==0?0:2);r.edit("eventChannel",i+1);}}
   tag.put("Groups",config.save());paper.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));panel.init(minecraft,width,height);
  }
  @Override public void render(GuiGraphics g,int x,int y,float p){panel.render(g,-100,-100,p);}
 }
}
