package dev.sablelife.testing;
import dev.sablelife.client.*;
import dev.sablelife.heart.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
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
public final class TerrainSettingsClientPreview {
 private static Preview screen;private static int ticks,index;
 private static Object get(Object object,String key)throws Exception{var f=object.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(object);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!Boolean.getBoolean("sablelife.terrainSettingsPreview"))return;var mc=Minecraft.getInstance();if(screen==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;mc.options.guiScale().set(1);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;}
  if(++ticks==18){check();var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("terrain-settings-"+index+".png"));}}
  if(ticks==23){ticks=0;if(++index==12){System.out.println("All 12 terrain settings/protection/block picker GUI checks passed");mc.stop();}else{mc.options.guiScale().set(index/3+1);mc.resizeDisplay();screen.reinit();}}
 }
 private static void check()throws Exception{
  for(var child:screen.active.children())if(child instanceof AbstractWidget w&&w.visible&&(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>screen.width||w.getY()+w.getHeight()>screen.height))throw new AssertionError("Terrain setting outside viewport");
  if(index%3==0){var toggles=(java.util.Map<String,Button>)get(screen.core,"toggles");if(toggles.size()!=4||!toggles.containsKey("crushEnabled"))throw new AssertionError("Missing terrain toggles");var fields=(java.util.List<EditBox>)get(screen.core,"inputs");if(fields.size()!=1||!fields.getFirst().active||NumericWheelSteps.core("crushDelay")!=.05)throw new AssertionError("Delay editor/step missing");fields.getFirst().setFocused(true);fields.getFirst().setValue("0.25");var sync=HeartSettingsScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);sync.invoke(screen.core);if(!fields.getFirst().isFocused()||!fields.getFirst().getValue().equals("0.25"))throw new AssertionError("Delay draft lost");fields.getFirst().setFocused(false);}
  else if(index%3==1){var input=(EditBox)get(screen.active,"input");var sync=CollisionProtectionScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);input.setValue("minecraft:stone");sync.invoke(screen.active);if(!((Button)get(screen.active,"add")).active)throw new AssertionError("Valid block rejected");input.setValue("#minecraft:planks");sync.invoke(screen.active);if(!((Button)get(screen.active,"add")).active)throw new AssertionError("Tag rejected");input.setValue("missing:bad_block");sync.invoke(screen.active);if(((Button)get(screen.active,"add")).active)throw new AssertionError("Unknown block accepted");input.setValue("");int cols=(int)get(screen.active,"columns"),rows=(int)get(screen.active,"rows"),pitch=(int)get(screen.active,"pitch"),gridY=(int)get(screen.active,"gridY"),height=(int)get(screen.active,"h");if(cols<9||rows*pitch+gridY>height-80)throw new AssertionError("Protection icon grid wastes space or overlaps buttons");var icon=CollisionProtectionScreen.class.getDeclaredMethod("icon",String.class);icon.setAccessible(true);if(((ItemStack)icon.invoke(screen.active,"minecraft:stone")).getItem()!=Items.STONE||((ItemStack)icon.invoke(screen.active,"#minecraft:planks")).getItem()!=Items.BOOK||((ItemStack)icon.invoke(screen.active,"missing:removed_block")).getItem()!=Items.BARRIER)throw new AssertionError("Block/tag/missing icon identity lost");var hit=CollisionProtectionScreen.class.getDeclaredMethod("indexAt",double.class,double.class);hit.setAccessible(true);if((int)hit.invoke(screen.active,(int)get(screen.active,"x")+14.,(int)get(screen.active,"y")+gridY+4.)!=0)throw new AssertionError("Icon hover points to wrong saved ID");}
  else{
   var picker=screen.active;int px=(int)get(picker,"x"),py=(int)get(picker,"y"),pitch=(int)get(picker,"pitch"),inventoryY=(int)get(picker,"inventoryY");
   var samples=(java.util.List<ItemStack>)get(picker,"samples");var inv=(Inventory)get(picker,"inventory");
   picker.mouseClicked(px+13,py+inventoryY+1,0);picker.mouseClicked(px+13,py+29,0);picker.mouseClicked(px+13+pitch,py+29,0);
   var pending=ProtectionBlockPickerScreen.class.getDeclaredMethod("pending");pending.setAccessible(true);
   if(samples.get(0).getItem()!=Items.DIAMOND_BLOCK||((java.util.List<?>)pending.invoke(picker)).size()!=1||inv.getItem(9).getCount()!=32)throw new AssertionError("Block picker copy/dedup consumed inventory or failed");
   picker.mouseClicked(px+13+pitch,py+29,1);picker.mouseClicked(px+13+pitch,py+inventoryY+1,0);picker.mouseClicked(px+13+pitch,py+29,0);
   if(!samples.get(1).isEmpty()&&samples.get(1).getItem()==Items.STICK)throw new AssertionError("Non-block item accepted");
  }
  System.out.println("Terrain settings GUI "+index+" passed");
 }
 private static final class Preview extends Screen {
  HeartSettingsScreen core;Screen active;Preview(){super(Component.empty());}void reinit(){init();}
  @Override protected void init(){var inv=new Inventory(null);inv.setItem(9,new ItemStack(Items.DIAMOND_BLOCK,32));inv.setItem(10,new ItemStack(Items.STICK,12));var menu=new HeartSettingsMenu(1,inv);var tag=new CompoundTag();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putBoolean("LiveControls",true);tag.putInt("InitialPage",5);tag.putString("State","DORMANT");tag.putBoolean("crushEnabled",true);tag.putBoolean("crushFloor",true);tag.putBoolean("crushDebris",true);tag.putDouble("crushDelay",0);var config=new TerrainBreakSettings();for(var block:net.minecraft.core.registries.BuiltInRegistries.BLOCK){config.edit("crushProtectAdd",0,net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString());if(config.entries().size()>=32)break;}tag.put("Crush",config.save());var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);core=new HeartSettingsScreen(menu,inv,title);
   try{var page=HeartSettingsScreen.class.getDeclaredField("page");page.setAccessible(true);page.setInt(core,5);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}core.init(minecraft,width,height);active=index%3==0?core:index%3==1?new CollisionProtectionScreen(core,menu):new ProtectionBlockPickerScreen(core,menu,inv);if(active!=core)active.init(minecraft,width,height);
  }
  @Override public void render(GuiGraphics g,int x,int y,float p){try{if(index%3==1)active.render(g,(int)get(active,"x")+15,(int)get(active,"y")+(int)get(active,"gridY")+5,p);else active.render(g,-100,-100,p);}catch(Exception e){throw new IllegalStateException(e);}}
 }
}
