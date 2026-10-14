package dev.sablelife.testing;
import dev.sablelife.client.*;
import dev.sablelife.heart.HeartWirelessMenu;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.nbt.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class WirelessPickerClientPreview {
 private static int ticks,index,originalDensity;private static Preview screen;private static final int[] baseline=new int[4];
 private static Object field(String name)throws Exception{for(Class<?> c=HeartWirelessScreen.class;c!=null;c=c.getSuperclass()){try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(screen.panel);}catch(NoSuchFieldException ignored){}}throw new NoSuchFieldException(name);}
 private static void check()throws Exception{int w=(int)field("imageWidth"),h=(int)field("imageHeight"),rows=(int)field("requestedRows"),pitch=(int)field("sidebarPitch");var back=(Button)field("back");var name=(EditBox)field("nameInput");var picker=(Button)field("picker");var mode=(Button)field("mode");var inputs=(java.util.List<EditBox>)field("inputs");
  if(w>screen.width||h>screen.height)throw new AssertionError("Wireless panel exceeds viewport");if(back.getY()+back.getHeight()>name.getY()||name.getY()+name.getHeight()>picker.getY())throw new AssertionError("Header controls overlap");if(inputs.getLast().getY()+inputs.getLast().getHeight()>mode.getY())throw new AssertionError("Last numeric input overlaps mode");if(34+rows*pitch>h-51)throw new AssertionError("Sidebar overlaps pagination");if(index%4==0)baseline[index/4]=rows;if(index%4==3&&rows<baseline[index/4])throw new AssertionError("50% density shows fewer rows");System.out.println("WIRELESS_LAYOUT_PASS scale="+(index/4+1)+" density="+(index%4)+" viewport="+screen.width+"x"+screen.height+" rows="+rows);
 }
 private static void layout(Minecraft mc){while(LifeUiStyle.density()!=index%4)LifeUiStyle.cycle();mc.options.guiScale().set(index/4+1);mc.resizeDisplay();screen.init(mc,screen.width,screen.height);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{if(!Boolean.getBoolean("sablelife.wirelessPickerPreview"))return;var mc=Minecraft.getInstance();if(screen==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;originalDensity=LifeUiStyle.density();screen=new Preview();mc.setScreen(screen);layout(mc);return;}if(++ticks==12){check();if(index%4==0||index%4==3){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("wireless-"+mc.getWindow().getWidth()+"-"+index+".png"));}}}if(ticks==16){ticks=0;if(++index<16)layout(mc);else{while(LifeUiStyle.density()!=originalDensity)LifeUiStyle.cycle();System.out.println("All 16 wireless scale/density combinations passed");mc.stop();}}}
 private static final class Preview extends Screen{HeartWirelessScreen panel;Preview(){super(Component.empty());}
  @Override protected void init(){var inv=new Inventory(null);var menu=new HeartWirelessMenu(1,inv);menu.setData(HeartWirelessMenu.COUNT,32);menu.setData(HeartWirelessMenu.EDITABLE,1);menu.setData(HeartWirelessMenu.MASTER,1);menu.setData(HeartWirelessMenu.SELECTED,9);menu.setData(0,1);menu.setData(2,20);menu.setData(3,20);menu.setData(4,15);menu.setData(6,index%5);panel=new HeartWirelessScreen(menu,inv,title);panel.init(minecraft,width,height);try{int rows=(int)field("requestedRows");int page=9/rows;menu.setData(HeartWirelessMenu.ROWS,rows);menu.setData(HeartWirelessMenu.PAGE,page);var tag=new CompoundTag();tag.putInt("Selected",9);tag.putString("SelectedName","Left arm attack");tag.putInt("Page",page);tag.putInt("Rows",rows);var names=new ListTag();for(int i=page*rows;i<Math.min(32,(page+1)*rows);i++)names.add(StringTag.valueOf(i==9?"Left arm attack":"Control "+(i+1)));tag.put("Names",names);var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(2).set(stack);for(int i=0;i<rows;i++){menu.setData(HeartWirelessMenu.ENABLED_ROWS+i,1);menu.setData(HeartWirelessMenu.OUTPUT+i,i%4==0?15:0);}}catch(Exception e){throw new RuntimeException(e);}if(menu.slots.size()!=3||menu.slots.stream().anyMatch(s->s.container==inv))throw new AssertionError("Wireless page exposes inventory");}
  @Override public void render(GuiGraphics g,int x,int y,float partial){panel.render(g,-100,-100,partial);}
 }
}
