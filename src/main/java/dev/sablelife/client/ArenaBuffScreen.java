package dev.sablelife.client;
import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
@EventBusSubscriber(modid=SableLife.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ArenaBuffScreen extends AbstractContainerScreen<ArenaBuffMenu> {
 private final List<Button> groupRows=new ArrayList<>();private List<ArenaBuffs.Group> groups=List.of();
 private net.minecraft.nbt.ListTag lastConfig;private UUID selected;private int page,rows,column;private EditBox channel;private Button add,remove,up,down,enabled,source,effects,apply,prev,next;
 public ArenaBuffScreen(ArenaBuffMenu menu,Inventory inv,Component title){super(menu,inv,title);}
 @SubscribeEvent public static void register(RegisterMenuScreensEvent event){event.register(SableLife.ARENA_BUFF_MENU.get(),ArenaBuffScreen::new);}
 private Component tr(String k){return Component.translatable("arena_buffs.sablelife."+k);}
 private Button button(Component label,int x,int y,int w,Runnable action){return addRenderableWidget(LifeUiStyle.button(label,leftPos+x,topPos+y,w,18,action));}
 private ArenaBuffs.Group current(){return groups.stream().filter(g->g.id().equals(selected)).findFirst().orElse(null);}
 private void send(String key,double value){PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,menu.snapshot().getInt("Revision"),selected==null?new UUID(0,0):selected,"arena_buff_edit",key,value,""));}
 @Override protected void init(){imageWidth=Math.max(280,Math.min(410,width-12));imageHeight=Math.max(168,Math.min(300,height-12));super.init();groupRows.clear();column=imageWidth/2;rows=Math.max(2,(imageHeight-112)/22);
  button(tr("back"),imageWidth-65,8,55,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0));
  for(int i=0;i<rows;i++){final int row=i;groupRows.add(button(Component.empty(),10,40+i*22,column-22,()->{int at=page*rows+row;if(at<groups.size()){selected=groups.get(at).id();channel.setFocused(false);sync();}}));}
  prev=button(Component.literal("<"),10,imageHeight-62,24,()->{page--;sync();});next=button(Component.literal(">"),column-36,imageHeight-62,24,()->{page++;sync();});
  add=button(tr("add"),10,imageHeight-38,column-70,()->send("add",0));up=button(Component.literal("↑"),column-56,imageHeight-38,20,()->send("up",0));down=button(Component.literal("↓"),column-33,imageHeight-38,20,()->send("down",0));
  boolean compact=imageHeight<220;int first=compact?34:52;
  enabled=button(Component.empty(),column+8,first,imageWidth-column-18,()->{var g=current();if(g!=null)send("enabled",g.enabled()?0:1);});
  source=button(Component.empty(),column+8,first+(compact?20:26),imageWidth-column-18,()->cycle(1));LifeUiStyle.reverse(source,()->cycle(-1));
  channel=addRenderableWidget(new EditBox(font,leftPos+column+8,topPos+first+(compact?54:70),imageWidth-column-52,18,tr("channel")));channel.setMaxLength(5);channel.setFilter(s->s.matches("[0-9]{0,5}"));
  apply=button(Component.literal("✓"),imageWidth-36,first+(compact?54:70),26,this::applyChannel);
  effects=button(Component.empty(),column+8,first+(compact?76:99),imageWidth-column-18,()->send("effects",0));
  remove=button(tr("delete"),column+8,imageHeight-38,imageWidth-column-18,()->send("delete",0));
  for(var b:List.of(enabled,source,apply,effects))b.setTooltip(Tooltip.create(tr("help")));sync();
 }
 private void cycle(int delta){var g=current();if(g!=null)send("source",Math.floorMod(g.source()+delta,3));}
 private void applyChannel(){try{int n=Integer.parseInt(channel.getValue());if(n>=0&&n<=65535){send("channel",n);channel.setFocused(false);}}catch(NumberFormatException e){channel.setTextColor(0xFF7777);}}
 private void sync(){var data=menu.snapshot();var before=groups;var saved=data.getList("Groups",10);boolean hadConfig=lastConfig!=null,changed=!saved.equals(lastConfig);
  if(changed){var config=new ArenaBuffs();config.load(saved);groups=config.groups();lastConfig=saved.copy();}
  if(selected==null&&data.hasUUID("Selected"))selected=data.getUUID("Selected");
  if(hadConfig&&changed&&groups.size()>before.size()){selected=groups.getLast().id();page=(groups.size()-1)/rows;}
  if(current()==null)selected=groups.isEmpty()?null:groups.getFirst().id();
  page=Math.clamp(page,0,Math.max(0,(groups.size()-1)/rows));
  for(int i=0;i<rows;i++){var button=groupRows.get(i);int at=page*rows+i;button.visible=at<groups.size();if(button.visible)button.setMessage(Component.literal(groups.get(at).id().equals(selected)?"> ":"").append(Component.translatable("arena_buffs.sablelife.group",at+1)));}
  var g=current();boolean editable=data.getBoolean("Ready")&&data.getBoolean("Editable"),has=g!=null;
  add.active=editable&&groups.size()<ArenaBuffs.LIMIT;remove.active=enabled.active=source.active=editable&&has;
  up.active=editable&&has&&groups.indexOf(g)>0;down.active=editable&&has&&groups.indexOf(g)+1<groups.size();
  effects.active=data.getBoolean("Ready")&&has;apply.active=channel.active=editable&&has&&g.source()!=0;channel.setEditable(channel.active);
  prev.active=page>0;next.active=(page+1)*rows<groups.size();
  enabled.setMessage(tr(has&&g.enabled()?"enabled":"disabled"));source.setMessage(tr("source_"+(has?g.source():0)));
  effects.setMessage(Component.translatable("arena_buffs.sablelife.effects",has?g.editor().effects().size():0));
  if(!channel.isFocused())channel.setValue(has?Integer.toString(g.channel()):"0");
 }
 @Override protected void containerTick(){super.containerTick();sync();}
 @Override public boolean keyPressed(int key,int scan,int mods){if(key==257&&channel.isFocused()){applyChannel();return true;}if(key!=256&&channel.isFocused())return channel.keyPressed(key,scan,mods);return super.keyPressed(key,scan,mods);}
 @Override public boolean mouseScrolled(double x,double y,double h,double v){if(v!=0&&channel.active&&channel.isMouseOver(x,y)){int n;try{n=Integer.parseInt(channel.getValue());}catch(NumberFormatException e){n=0;}channel.setValue(Integer.toString(Math.clamp(n+(int)Math.signum(v),0,65535)));applyChannel();return true;}return super.mouseScrolled(x,y,h,v);}
 @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(leftPos-2,topPos-2,leftPos+imageWidth+2,topPos+imageHeight+2,0xFF19151D);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF9B614E);g.fill(leftPos+3,topPos+3,leftPos+imageWidth-3,topPos+imageHeight-3,0xFF242632);g.fill(leftPos+column-2,topPos+32,leftPos+column,topPos+imageHeight-7,0xFFAA79C8);}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){g.drawString(font,tr("title"),10,13,0xE6D8EA,false);g.drawString(font,tr("channel"),column+8,channel.getY()-topPos-14,0xD9C4ED,false);g.drawCenteredString(font,(page+1)+" / "+Math.max(1,(groups.size()+rows-1)/rows),column/2,imageHeight-57,0xD9C4ED);if(imageHeight>=220){var hint=tr("note");float scale=Math.min(1,(imageWidth-column-18)/(float)Math.max(1,font.width(hint)));g.pose().pushPose();g.pose().translate(column+8,173,0);g.pose().scale(scale,scale,1);g.drawString(font,hint,0,0,0xBDA5D0,false);g.pose().popPose();}}
 @Override public void render(GuiGraphics g,int x,int y,float partial){sync();renderBackground(g,x,y,partial);super.render(g,x,y,partial);renderTooltip(g,x,y);}
}
