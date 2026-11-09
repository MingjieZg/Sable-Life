package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import net.minecraft.client.gui.*;
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
public final class SignalRuleScreen extends AbstractContainerScreen<SignalRuleMenu> {
 private final List<Button> list=new ArrayList<>(),choices=new ArrayList<>(),apply=new ArrayList<>();private final List<EditBox> fields=new ArrayList<>();
 private List<SignalRules.Row> rules=List.of();private net.minecraft.nbt.ListTag last;private UUID selected;private int page,parameterPage,rows,column,slots,rowHeight,labelWidth;private Button add,remove,up,down,enabled,prev,next,parameterPrev,parameterNext;
 public SignalRuleScreen(SignalRuleMenu menu,Inventory inv,Component title){super(menu,inv,title);}
 @SubscribeEvent public static void register(RegisterMenuScreensEvent event){event.register(SableLife.SIGNAL_RULE_MENU.get(),SignalRuleScreen::new);}
 private Component tr(String k){return Component.translatable("signal_rules.sablelife."+k);}private Component field(String k){return Component.translatable("dashboard.sablelife."+k);}
 private Component label(String k){return List.of("eventChannel","eventCooldown","eventPulse","signalInput").contains(k)?tr("label_"+k):field(k);}
 private String type(){return menu.snapshot().getString("Type");}private SignalRules.Row current(){return rules.stream().filter(r->r.id().equals(selected)).findFirst().orElse(null);}
 private Button button(Component text,int x,int y,int w,Runnable run){return addRenderableWidget(LifeUiStyle.button(text,leftPos+x,topPos+y,w,18,run));}
 private void send(String k,double v){PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,menu.snapshot().getInt("Revision"),selected==null?new UUID(0,0):selected,"signal_rule_edit",k,v,""));}
 @Override protected void init(){imageWidth=Math.max(300,Math.min(480,width-12));imageHeight=Math.max(200,Math.min(400,height-12));super.init();list.clear();choices.clear();fields.clear();apply.clear();column=(int)(imageWidth*.40);rows=Math.max(2,(imageHeight-112)/22);int count=SignalRules.fields(type()).size();slots=Math.min(count,Math.max(1,(imageHeight-84)/18));if(slots<count)slots=Math.max(1,(imageHeight-108)/18);rowHeight=Math.min(24,(imageHeight-(slots>=count?84:108))/slots);labelWidth=Math.clamp((int)((imageWidth-column-18)*.43),64,110);
  remove=button(tr("delete"),imageWidth-125,8,55,()->send("delete",0));button(tr("back"),imageWidth-65,8,55,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0));
  for(int i=0;i<rows;i++){int n=i;list.add(button(Component.empty(),10,40+i*22,column-22,()->{int at=page*rows+n;if(at<rules.size()){selected=rules.get(at).id();parameterPage=0;fields.forEach(f->f.setFocused(false));sync();}}));}
  prev=button(Component.literal("<"),10,imageHeight-62,24,()->{page--;sync();});next=button(Component.literal(">"),column-36,imageHeight-62,24,()->{page++;sync();});add=button(tr("add"),10,imageHeight-38,column-70,()->send("add",0));up=button(Component.literal("↑"),column-56,imageHeight-38,20,()->send("up",0));down=button(Component.literal("↓"),column-33,imageHeight-38,20,()->send("down",0));
  enabled=button(Component.empty(),column+8,34,imageWidth-column-18,()->{var r=current();if(r!=null)send("enabled",r.enabled()?0:1);});
  for(int i=0;i<slots;i++){int n=i,y=60+i*rowHeight,w=imageWidth-column-18-labelWidth;var box=addRenderableWidget(new EditBox(font,leftPos+column+8+labelWidth,topPos+y,w-34,18,Component.empty()));box.setMaxLength(12);fields.add(box);apply.add(button(Component.literal("✓"),imageWidth-36,y,26,()->apply(n)));choices.add(button(Component.empty(),column+8+labelWidth,y,w,()->cycle(n)));}
  parameterPrev=button(Component.literal("<"),column+8,imageHeight-42,24,()->{parameterPage--;fields.forEach(f->f.setFocused(false));sync();});parameterNext=button(Component.literal(">"),imageWidth-34,imageHeight-42,24,()->{parameterPage++;fields.forEach(f->f.setFocused(false));sync();});sync();
 }
 private ModuleFields.Field parameter(int i){var defs=SignalRules.fields(type());int at=parameterPage*slots+i;return at<defs.size()?defs.get(at):null;}
 private List<String> options(ModuleFields.Field f,SignalRules.Row r){if(f.key().equals("eventKind"))return List.of("event_kill","event_block","event_death");if(!f.key().equals("eventFilter"))return f.choices();return switch((int)r.value("eventKind")){case 2->List.of("event_block_any","event_block_shield","event_block_guard");default->List.of("event_target_player","event_target_mob","event_target_life","event_target_all");};}
 private void cycle(int i){cycle(i,1);}
 private void cycle(int i,int direction){var f=parameter(i);var r=current();if(f==null||r==null)return;if(f.key().equals("eventSource")){
   var modules=new ArrayList<VirtualModule>();for(var v:menu.snapshot().getList("Modules",10))modules.add(VirtualModule.load((net.minecraft.nbt.CompoundTag)v));
   minecraft.setScreen(new EventSourcePickerScreen(this,modules,r.source(),index->send("eventSource",index)));return;
  }int value=f.key().equals("eventKind")?(r.value("eventKind")==3?2:r.value("eventKind")==2?1:0):(int)r.value(f.key());int nextValue=Math.floorMod(value+direction,options(f,r).size());send(f.key(),f.key().equals("eventKind")?(nextValue==2?3:nextValue==1?2:0):nextValue);fields.forEach(b->b.setFocused(false));}
 private void apply(int i){var f=parameter(i);if(f==null)return;try{double value=Double.parseDouble(fields.get(i).getValue());if(Double.isFinite(value)){send(f.key(),Math.round(value*f.scale()));fields.get(i).setFocused(false);}}catch(NumberFormatException ignored){fields.get(i).setTextColor(0xFF7777);}}
 private Component sourceLabel(SignalRules.Row row){if(row.source()==null)return field("signal_source_all");int i=0;for(var v:menu.snapshot().getList("Modules",10)){i++;var tag=(net.minecraft.nbt.CompoundTag)v;if(tag.hasUUID("Id")&&row.source().equals(tag.getUUID("Id")))return Component.literal((tag.getString("Name").isBlank()?field("type_"+tag.getString("Type")).getString():tag.getString("Name"))+" #"+i);}return tr("source_missing");}
 private void sync(){var data=menu.snapshot();var saved=data.getList("Groups",10);int before=rules.size();if(!saved.equals(last)){var collection=new SignalRules(type());collection.load(saved);rules=collection.rows();if(last!=null&&rules.size()>before){selected=rules.getLast().id();page=(rules.size()-1)/rows;parameterPage=0;}last=saved.copy();}
  if(current()==null)selected=rules.isEmpty()?null:rules.getFirst().id();page=Math.clamp(page,0,Math.max(0,(rules.size()-1)/rows));parameterPage=Math.clamp(parameterPage,0,Math.max(0,(SignalRules.fields(type()).size()-1)/slots));
  for(int i=0;i<list.size();i++){int at=page*rows+i;var b=list.get(i);b.visible=at<rules.size();if(b.visible){var r=rules.get(at);String text=type().equals("signal_logic")?"CH "+(int)r.value("signalInput")+" · "+field(r.value("signalCondition")==0?"signal_powered":"signal_unpowered").getString():field(SignalRules.fields(type()).getFirst().choices().get((int)r.value("eventKind"))).getString()+" → "+(int)r.value("eventChannel");var label=Component.literal((r.id().equals(selected)?"> ":"")+(at+1)+". "+text);b.setMessage(Component.literal(font.plainSubstrByWidth(label.getString(),b.getWidth()-8)));b.setTooltip(Tooltip.create(label.copy().append(" · ").append(tr(r.enabled()?"enabled":"disabled"))));}}
  var r=current();boolean editable=data.getBoolean("Ready")&&data.getBoolean("Editable"),has=r!=null;add.active=editable&&rules.size()<SignalRules.limit(type());remove.active=enabled.active=editable&&has;enabled.setMessage(tr(has&&r.enabled()?"enabled":"disabled"));up.active=editable&&has&&rules.indexOf(r)>0;down.active=editable&&has&&rules.indexOf(r)+1<rules.size();prev.active=page>0;next.active=(page+1)*rows<rules.size();parameterPrev.active=parameterPage>0;parameterNext.active=(parameterPage+1)*slots<SignalRules.fields(type()).size();parameterPrev.visible=parameterNext.visible=SignalRules.fields(type()).size()>slots;
  for(int i=0;i<slots;i++){var f=parameter(i);var box=fields.get(i);var b=choices.get(i);var a=apply.get(i);boolean choice=f!=null&&f.choice();box.visible=a.visible=f!=null&&!choice;b.visible=choice;boolean usable=editable&&has&&f!=null&&!(r.value("eventKind")==3&&List.of("eventFilter","eventSource","eventCount","eventCooldown").contains(f.key()));box.active=a.active=b.active=usable;box.setEditable(usable);
   if(f==null)continue;var help=Tooltip.create(field(f.key()).copy().append(" · ").append(tr(type().equals("signal_logic")?"logic_help":has&&r.value("eventKind")==3?"death_help":"event_help")));box.setTooltip(help);b.setTooltip(help);
   if(choice){Component label=!has?Component.empty():f.key().equals("eventSource")?sourceLabel(r):field(options(f,r).get(Math.clamp(f.key().equals("eventKind")?(r.value("eventKind")==3?2:r.value("eventKind")==2?1:0):(int)r.value(f.key()),0,options(f,r).size()-1)));b.setMessage(Component.literal(font.plainSubstrByWidth(label.getString(),b.getWidth()-10)));}
   else if(!box.isFocused())box.setValue(has?String.format(Locale.ROOT,f.scale()==20?"%.2f":"%.0f",r.value(f.key())/f.scale()):"");
  }
 }
 @Override protected void containerTick(){super.containerTick();sync();}
 @Override public boolean mouseClicked(double x,double y,int button){if(button==1)for(int i=0;i<choices.size();i++){var f=parameter(i);var b=choices.get(i);if(f!=null&&f.key().equals("eventKind")&&b.visible&&b.active&&b.isMouseOver(x,y)){b.playDownSound(minecraft.getSoundManager());cycle(i,-1);return true;}}return super.mouseClicked(x,y,button);}
 @Override public boolean keyPressed(int k,int scan,int mods){for(int i=0;i<fields.size();i++)if(fields.get(i).visible&&fields.get(i).isFocused()&&k!=256){if(k==257||k==335){apply(i);return true;}return fields.get(i).keyPressed(k,scan,mods);}return super.keyPressed(k,scan,mods);}
 @Override public boolean mouseScrolled(double x,double y,double h,double v){for(int i=0;i<fields.size();i++){var box=fields.get(i);var f=parameter(i);if(v!=0&&f!=null&&box.visible&&box.active&&box.isMouseOver(x,y)){double value;try{value=Double.parseDouble(box.getValue());}catch(NumberFormatException e){value=f.initial()/f.scale();}double step=f.scale()==20?.05:1;box.setValue(String.format(Locale.ROOT,"%.2f",Math.clamp(value+Math.signum(v)*step,f.min()/f.scale(),f.max()/f.scale())));apply(i);return true;}}return super.mouseScrolled(x,y,h,v);}
 @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(leftPos-2,topPos-2,leftPos+imageWidth+2,topPos+imageHeight+2,0xFF9B614E);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF242632);g.fill(leftPos+column-2,topPos+32,leftPos+column,topPos+imageHeight-7,0xFFAA79C8);}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){g.drawString(font,tr(type().equals("signal_logic")?"logic_title":"event_title"),10,13,0xE6D8EA,false);g.drawCenteredString(font,(page+1)+" / "+Math.max(1,(rules.size()+rows-1)/rows),column/2,imageHeight-57,0xD9C4ED);
  for(int i=0;i<slots;i++){var f=parameter(i);if(f!=null)g.drawString(font,font.plainSubstrByWidth(label(f.key()).getString(),labelWidth-6),column+8,60+i*rowHeight+5,0xD9C4ED,false);}
  if(SignalRules.fields(type()).size()>slots)g.drawCenteredString(font,(parameterPage+1)+" / "+Math.max(1,(SignalRules.fields(type()).size()+slots-1)/slots),column+(imageWidth-column)/2,imageHeight-37,0xD9C4ED);
  var runtime=menu.snapshot().getCompound("Runtime");boolean active=runtime.getBoolean("Active");int progress=0;if(!type().equals("signal_logic")&&current()!=null)for(var v:runtime.getList("Rows",10)){var row=(net.minecraft.nbt.CompoundTag)v;if(row.hasUUID("Id")&&row.getUUID("Id").equals(current().id())){active=row.getBoolean("Active");progress=row.getInt("Count");}}
  g.drawString(font,font.plainSubstrByWidth((!type().equals("signal_logic")&&current()!=null?tr("progress").getString()+" "+progress+"/"+(int)current().value("eventCount")+" · ":"")+tr(active?"active":"waiting").getString(),imageWidth-column-18),column+8,imageHeight-16,active?0x79D3AE:0xBDA5D0,false);
 }
 @Override public void render(GuiGraphics g,int x,int y,float p){sync();renderBackground(g,x,y,p);super.render(g,x,y,p);renderTooltip(g,x,y);}
}
