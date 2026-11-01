package dev.sablelife.client;
import dev.sablelife.heart.*;
import dev.sablelife.module.ModuleNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** One validated block ID/tag per operation; no arbitrary NBT or oversized text is sent. */
public final class CollisionProtectionScreen extends Screen {
 private final Screen parent;private final HeartSettingsMenu menu;private EditBox input;private Button add,prev,next,preset,inventory;private final Map<String,net.minecraft.world.item.ItemStack> icons=new HashMap<>();private int x,y,w,h,rows,columns,pitch,gridY,page;private String draft="";
 public CollisionProtectionScreen(Screen parent,HeartSettingsMenu menu){super(tr("crushProtected"));this.parent=parent;this.menu=menu;}
 private static Component tr(String key){return Component.translatable("core.sablelife."+key);}
 private Button button(Component label,int x,int y,int w,Runnable run){return addRenderableWidget(LifeUiStyle.button(label,x,y,w,20,run));}
 private void send(String key,double value,String text){PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,0,new UUID(0,0),"core",key,value,text));}
 @Override protected void init(){w=Math.min(440,width-16);h=Math.min(320,height-16);x=(width-w)/2;y=(height-h)/2;pitch=h<220?18:24;gridY=h<220?58:70;columns=Math.max(1,(w-20)/pitch);rows=Math.max(1,(h-90-gridY)/pitch);icons.clear();button(Component.translatable("dashboard.sablelife.back"),x+w-68,y+8,60,this::onClose);
  input=addRenderableWidget(new EditBox(font,x+10,y+(h<220?32:38),w-85,20,tr("crushProtected_hint")));input.setHint(tr("crushProtected_hint"));input.setMaxLength(80);input.setValue(draft);input.setResponder(s->draft=s);
  add=button(tr("crushProtected_add"),x+w-69,y+(h<220?32:38),59,()->{if(valid()){send("crushProtectAdd",0,input.getValue().trim());input.setValue("");}else input.setTextColor(0xFF7777);});

  prev=button(Component.literal("<"),x+10,y+h-53,28,()->{page--;sync();});next=button(Component.literal(">"),x+w-38,y+h-53,28,()->{page++;sync();});sync();
  preset=button(tr("crushProtected_preset"),x+10,y+h-80,(w-26)/2,()->send("crushProtectPreset",0,""));preset.setTooltip(Tooltip.create(tr("crushProtected_preset_help")));
  inventory=button(tr("crushProtected_inventory"),x+16+(w-26)/2,y+h-80,(w-26)/2,()->{if(minecraft.player!=null)minecraft.setScreen(new ProtectionBlockPickerScreen(this,menu,minecraft.player.getInventory()));});sync();
 }
 private List<String> saved(){return menu.snapshot().getCompound("Crush").getList("Protected",8).stream().map(net.minecraft.nbt.Tag::getAsString).toList();}
 private boolean valid(){String s=input.getValue().trim();boolean tag=s.startsWith("#");var id=net.minecraft.resources.ResourceLocation.tryParse(tag?s.substring(1):s);return id!=null&&(tag||net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id));}
 private void sync(){var list=saved();boolean editable=menu.snapshot().getBoolean("Ready")&&menu.snapshot().getBoolean("LiveControls");page=Math.clamp(page,0,Math.max(0,(list.size()-1)/(rows*columns)));input.active=editable;input.setEditable(editable);add.active=editable&&list.size()<TerrainBreakSettings.LIMIT&&valid();input.setTextColor(valid()||input.getValue().isEmpty()?0xE0E0E0:0xFF7777);

  prev.active=page>0;next.active=(page+1)*rows*columns<list.size();
  if(preset!=null)preset.active=editable&&list.size()<TerrainBreakSettings.LIMIT&&!list.containsAll(TerrainBreakSettings.SURFACE_PRESET);if(inventory!=null)inventory.active=editable&&list.size()<TerrainBreakSettings.LIMIT;
 }
 private int indexAt(double mx,double my){int col=(int)Math.floor((mx-x-10)/pitch),row=(int)Math.floor((my-y-gridY)/pitch);if(col<0||col>=columns||row<0||row>=rows)return -1;int at=page*rows*columns+row*columns+col;return at<saved().size()?at:-1;}
 private net.minecraft.world.item.ItemStack icon(String entry){return icons.computeIfAbsent(entry,key->{if(key.startsWith("#"))return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK);var id=net.minecraft.resources.ResourceLocation.tryParse(key);var item=id==null?net.minecraft.world.item.Items.AIR:net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id).asItem();return new net.minecraft.world.item.ItemStack(item==net.minecraft.world.item.Items.AIR?net.minecraft.world.item.Items.BARRIER:item);});}
 @Override public boolean mouseClicked(double mx,double my,int button){int at=indexAt(mx,my);if(at>=0&&(button==0||button==1)){if(button==1&&menu.snapshot().getBoolean("Ready")&&menu.snapshot().getBoolean("LiveControls"))send("crushProtectRemove",at,saved().get(at));return true;}return super.mouseClicked(mx,my,button);}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
 @Override public boolean keyPressed(int key,int scan,int mods){if(key==257&&add.active){add.onPress();return true;}if(key!=256&&input.isFocused())return input.keyPressed(key,scan,mods);return super.keyPressed(key,scan,mods);}
 @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);sync();g.fill(x-2,y-2,x+w+2,y+h+2,0xFF9B614E);g.fill(x,y,x+w,y+h,0xFF242632);g.drawString(font,title,x+10,y+13,0xFFD9C4ED,false);g.drawCenteredString(font,(page+1)+" / "+Math.max(1,(saved().size()+rows*columns-1)/(rows*columns)),x+w/2,y+h-48,0xFFD9C4ED);g.drawString(font,font.plainSubstrByWidth(tr("crushProtected_help").getString(),w-20),x+10,y+h-22,0xBDA5D0,false);var list=saved();int hoveredIndex=indexAt(mx,my);for(int i=0;i<rows*columns;i++){int at=page*rows*columns+i;if(at>=list.size())break;int px=x+10+(i%columns)*pitch,py=y+gridY+(i/columns)*pitch;boolean hovered=at==hoveredIndex;g.fill(px,py,px+pitch-2,py+pitch-2,hovered?0xFFD9C4ED:0xFF82728F);g.fill(px+1,py+1,px+pitch-3,py+pitch-3,0xFF4B4557);int inset=(pitch-16)/2;g.renderItem(icon(list.get(at)),px+inset,py+inset);if(list.get(at).startsWith("#"))g.drawString(font,"#",px+1,py+1,0xFFFFD970,true);}
  for(var widget:renderables)widget.render(g,mx,my,partial);int hovered=indexAt(mx,my);if(hovered>=0){String entry=list.get(hovered);var id=net.minecraft.resources.ResourceLocation.tryParse(entry);Component name=entry.startsWith("#")?tr("crushProtected_tag"):id!=null&&net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id)?net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id).getName():Component.literal(entry);g.renderComponentTooltip(font,List.of(name,Component.literal(entry),tr("crushProtected_remove_help")),mx,my);}}
}
