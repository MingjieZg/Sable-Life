package dev.sablelife.client;

import dev.sablelife.heart.*;
import dev.sablelife.module.ModuleNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Local, non-consuming block samples. Confirmation sends only validated block IDs to the existing core menu. */
public class ProtectionBlockPickerScreen extends Screen {
 private final Screen parent;private final HeartSettingsMenu menu;private final Inventory inventory;
 private final List<ItemStack> samples=new ArrayList<>(Collections.nCopies(27,ItemStack.EMPTY));
 private ItemStack carried=ItemStack.EMPTY;private int x,y,w,h,pitch,inventoryY;private Button confirm;
 public ProtectionBlockPickerScreen(Screen parent,HeartSettingsMenu menu,Inventory inventory){super(tr("crushProtected_inventory"));this.parent=parent;this.menu=menu;this.inventory=inventory;}
 private static Component tr(String key){return Component.translatable("core.sablelife."+key);}
 private boolean editable(){var data=menu.snapshot();return data.getBoolean("Ready")&&data.getBoolean("LiveControls");}
 private List<String> pending(){return samples.stream().filter(s->!s.isEmpty()&&s.getItem() instanceof BlockItem).map(s->BuiltInRegistries.BLOCK.getKey(((BlockItem)s.getItem()).getBlock()).toString()).distinct().toList();}
 protected void commit(List<String> ids){for(String id:ids)PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,0,new UUID(0,0),"core","crushProtectAdd",0,id));}
 @Override protected void init(){pitch=Math.clamp((height-110)/7,8,20);w=9*pitch+24;h=7*pitch+94;x=(width-w)/2;y=(height-h)/2;inventoryY=28+3*pitch+20;
  confirm=addRenderableWidget(LifeUiStyle.button(tr("crushProtected_confirm"),x+10,y+h-25,(w-26)/2,18,()->{if(editable()){commit(pending());onClose();}}));
  addRenderableWidget(LifeUiStyle.button(Component.translatable("dashboard.sablelife.back"),x+16+(w-26)/2,y+h-25,(w-26)/2,18,this::onClose));
 }
 @Override public boolean mouseClicked(double mx,double my,int button){if(button!=0&&button!=1)return super.mouseClicked(mx,my,button);if(!editable())return super.mouseClicked(mx,my,button);
  for(int i=0;i<27;i++)if(over(mx,my,i%9,i/9,28)){if(button==1)samples.set(i,ItemStack.EMPTY);else if(carried.getItem() instanceof BlockItem)samples.set(i,carried.copyWithCount(1));return true;}
  for(int i=0;i<36;i++)if(over(mx,my,i%9,i<27?i/9:3,inventoryY)){var item=inventory.getItem(i<27?i+9:i-27);if(!item.isEmpty()&&item.getItem() instanceof BlockItem){if(hasShiftDown()){for(int n=0;n<27;n++)if(samples.get(n).isEmpty()){samples.set(n,item.copyWithCount(1));break;}}else carried=item.copyWithCount(1);}return true;}
  return super.mouseClicked(mx,my,button);
 }
 private boolean over(double mx,double my,int col,int row,int start){return mx>=x+12+col*pitch&&mx<x+12+(col+1)*pitch&&my>=y+start+row*pitch&&my<y+start+(row+1)*pitch;}
 private void cell(GuiGraphics g,ItemStack item,int col,int row,int start){int px=x+12+col*pitch,py=y+start+row*pitch;g.fill(px-1,py-1,px+pitch-1,py+pitch-1,0xFF82728F);g.fill(px,py,px+pitch-2,py+pitch-2,0xFF4B4557);if(!item.isEmpty()){g.pose().pushPose();g.pose().translate(px,py,0);float scale=(pitch-2)/16F;g.pose().scale(scale,scale,1);g.renderItem(item,0,0);g.pose().popPose();}}
 @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);confirm.active=editable()&&!pending().isEmpty();g.fill(x-2,y-2,x+w+2,y+h+2,0xFF9B614E);g.fill(x,y,x+w,y+h,0xFF242632);g.drawString(font,title,x+10,y+8,0xD9C4ED,false);g.drawString(font,Component.translatable("container.inventory"),x+12,y+inventoryY-13,0xD9C4ED,false);
  for(int i=0;i<27;i++)cell(g,samples.get(i),i%9,i/9,28);for(int i=0;i<36;i++)cell(g,inventory.getItem(i<27?i+9:i-27),i%9,i<27?i/9:3,inventoryY);for(var widget:renderables)widget.render(g,mx,my,partial);
  if(!carried.isEmpty())g.renderItem(carried,mx+8,my+8);
  for(int i=0;i<27;i++)if(over(mx,my,i%9,i/9,28))g.renderTooltip(font,tr("crushProtected_picker_help"),mx,my);
  for(int i=0;i<36;i++)if(over(mx,my,i%9,i<27?i/9:3,inventoryY)){var item=inventory.getItem(i<27?i+9:i-27);if(!item.isEmpty())g.renderTooltip(font,item,mx,my);}
 }
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
