package dev.sablelife.client;

import dev.sablelife.module.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Shares the dashboard menu and its per-viewer snapshots, with no extra world or signal simulation. */
public final class ModuleInspectorScreen extends AbstractContainerScreen<HeartDashboardMenu> {
 private final HeartDashboardScreen parent;private final UUID moduleId;
 private final List<Button> buttons=new ArrayList<>(),tabs=new ArrayList<>();
 private record Row(Component label,Component tooltip,UUID target,int move){}
 private List<Row> rows=List.of();private List<VirtualModule> modules=List.of();
 private CompoundTag data=new CompoundTag(),diagnostics=new CompoundTag();private int tab,page,slots,pitch;
 private EditBox group,channel;private Button applyGroup,clearGroup,inspect,previous,next;
 public ModuleInspectorScreen(HeartDashboardScreen parent){super(parent.getMenu(),net.minecraft.client.Minecraft.getInstance().player==null?new net.minecraft.world.entity.player.Inventory(null):net.minecraft.client.Minecraft.getInstance().player.getInventory(),tr("title"));this.parent=parent;moduleId=parent.selectedId();}
 private static Component tr(String key,Object... args){return Component.translatable("inspector.sablelife."+key,args);}
 private Button button(Component label,int x,int y,int w,Runnable action){return addRenderableWidget(LifeUiStyle.button(label,leftPos+x,topPos+y,w,18,action));}
 private VirtualModule find(UUID id){return modules.stream().filter(m->m.id().equals(id)).findFirst().orElse(null);}
 private Component name(UUID id){var m=find(id);return m==null?tr("missing"):Component.empty().append(m.name().isBlank()?Component.translatable("dashboard.sablelife.type_"+m.type()):Component.literal(m.name())).append(" #"+(modules.indexOf(m)+1));}
 @Override protected void init(){
  imageWidth=Math.max(300,Math.min(480,width-14));imageHeight=Math.max(200,height-14);super.init();buttons.clear();tabs.clear();
  button(Component.translatable("dashboard.sablelife.back"),imageWidth-64,8,54,this::onClose);
  int width=(imageWidth-26)/3;for(int i=0;i<3;i++){int n=i;tabs.add(button(tr(List.of("references","groups","channels").get(i)),10+i*(width+3),34,width,()->{tab=n;page=0;sync();if(tab==2)requestChannel();}));}
  group=addRenderableWidget(new EditBox(font,leftPos+10,topPos+65,imageWidth-140,18,tr("group_name")));group.setMaxLength(64);group.setHint(tr("group_name"));
  applyGroup=button(tr("assign"),imageWidth-124,65,54,()->assign(group.getValue()));clearGroup=button(tr("ungroup"),imageWidth-66,65,56,()->assign(""));
  channel=addRenderableWidget(new EditBox(font,leftPos+112,topPos+65,60,18,tr("channel")));channel.setMaxLength(5);channel.setFilter(s->s.matches("[0-9]{0,5}"));
  inspect=button(tr("inspect"),178,65,60,this::requestChannel);
  pitch=Math.max(20,(int)Math.ceil(26*LifeUiStyle.scale()));slots=Math.max(1,(imageHeight-167)/pitch);
  for(int i=0;i<slots;i++){int row=i;var b=button(Component.empty(),10,116+i*pitch,imageWidth-20,()->openRow(row));b.setHeight(pitch-3);buttons.add(b);}
  previous=button(Component.literal("‹"),10,imageHeight-31,28,()->{page=Math.max(0,page-1);sync();});next=button(Component.literal("›"),imageWidth-38,imageHeight-31,28,()->{page=Math.min(Math.max(0,(rows.size()-1)/slots),page+1);sync();});sync();
 }
 private void assign(String value){if(!ModuleFolders.valid(value)){group.setTextColor(0xFF7777);return;}parent.inspectAction("group",moduleId,0,value);group.setFocused(false);group.setTextColor(0xE0E0E0);}
 private void requestChannel(){try{int n=Integer.parseInt(channel.getValue());if(n<1||n>diagnostics.getInt("Count"))throw new NumberFormatException();parent.inspectAction("inspect",moduleId,n-1,"");channel.setTextColor(0xE0E0E0);channel.setFocused(false);}catch(NumberFormatException invalid){channel.setTextColor(0xFF7777);}}
 private void openRow(int i){int n=page*slots+i;if(n>=rows.size())return;var row=rows.get(n);if(tab==1){group.setValue(row.label.getString());assign(group.getValue());}else if(row.target!=null&&find(row.target)!=null)parent.jumpTo(row.target,row.move);}
 private void sync(){
  data=menu.snapshot();diagnostics=data.getCompound("ChannelDiagnostics");var loaded=new ArrayList<VirtualModule>();for(var item:data.getList("Modules",10))loaded.add(VirtualModule.load((CompoundTag)item));modules=List.copyOf(loaded);
  var current=find(moduleId);if(!group.isFocused())group.setValue(current==null?"":current.group());if(!channel.isFocused())channel.setValue(Integer.toString(diagnostics.getInt("Channel")+1));
  group.visible=applyGroup.visible=clearGroup.visible=tab==1;group.active=applyGroup.active=clearGroup.active=data.getBoolean("Editable")&&current!=null;group.setEditable(group.active);
  channel.visible=inspect.visible=tab==2;channel.active=inspect.active=diagnostics.getInt("Count")>0;channel.setEditable(channel.active);
  for(int i=0;i<tabs.size();i++)tabs.get(i).active=i!=tab;
  var result=new ArrayList<Row>();
  if(tab==0)for(var item:data.getCompound("References").getList("Rows",10)){
   var link=(CompoundTag)item;boolean incoming=link.getUUID("To").equals(moduleId);UUID target=link.getUUID(incoming?"From":"To");
   var relation=tr(incoming?"used_by":"uses").copy().withStyle(style->style.withColor(incoming?0xFFD36B:0x88CCFF).withBold(true));
   var label=Component.empty().append(relation).append(" ").append(name(target)).append(" · ").append(tr("link_"+link.getString("Kind")));
   if(link.getString("Kind").equals("move"))label.append(" ").append(link.getString("Detail").isBlank()?"#"+(link.getInt("Move")+1):link.getString("Detail"));
   else if(!link.getString("Detail").isBlank())label.append(" #"+link.getString("Detail"));
   result.add(new Row(label,label.copy().append("\n").append(tr(find(target)==null?"missing_help":"jump_help")),target,incoming?link.getInt("Move"):-1));
  }
  if(tab==1){var counts=new LinkedHashMap<String,Integer>();for(var m:modules)if(!m.group().isBlank())counts.merge(m.group(),1,Integer::sum);counts.forEach((name,count)->result.add(new Row(Component.literal(name),tr("group_count",count),null,-1)));}
  if(tab==2){
   if(!diagnostics.getBoolean("Master")||!diagnostics.getBoolean("Ready")||!diagnostics.getBoolean("Configured"))result.add(new Row(tr("inactive"),tr("inactive_help"),null,-1));
   var live=new HashMap<UUID,Integer>();for(var item:diagnostics.getList("Sources",10)){var row=(CompoundTag)item;live.put(row.getUUID("Module"),row.getInt("Power"));}
   for(var entry:live.entrySet()){var label=Component.empty().append(tr("live_source")).append(" ").append(name(entry.getKey())).append(" → "+entry.getValue());result.add(new Row(label,label.copy().append("\n").append(tr("source_help")),entry.getKey(),-1));}
   if(diagnostics.getInt("Input")>0){var label=tr("external_power",diagnostics.getInt("Input"));result.add(new Row(label,tr("external_help"),null,-1));}
   if(diagnostics.getBoolean("Latch")&&diagnostics.getInt("Power")>0){var label=tr("latched_power",diagnostics.getInt("Power"));result.add(new Row(label,tr("latch_help"),null,-1));}
   if(!diagnostics.getBoolean("Receiving")&&(diagnostics.getInt("Mode")==0||diagnostics.getInt("Mode")==2)){var label=tr("waveform_power",diagnostics.getInt("Power"));result.add(new Row(label,tr("waveform_help"),null,-1));}
   var seen=new HashSet<String>();for(var item:diagnostics.getList("Links",10)){var link=(CompoundTag)item;UUID id=link.getUUID("Module");boolean output=link.getBoolean("Output");String identity=id+":"+output+":"+link.getBoolean("External");if(!seen.add(identity)||output&&live.containsKey(id))continue;
    var label=Component.empty().append(tr(output?"configured_source":link.getBoolean("External")?"external_receiver":"receiver")).append(" ").append(name(id));result.add(new Row(label,label.copy().append("\n").append(tr(output?"configured_help":"jump_help")),id,-1));
   }
  }
  if(tab==0&&data.getCompound("References").getInt("Total")>512||tab==2&&diagnostics.getInt("LinkCount")>512)result.add(new Row(tr("truncated"),tr("truncated"),null,-1));
  rows=List.copyOf(result);page=Math.min(page,Math.max(0,(rows.size()-1)/slots));
  for(int i=0;i<buttons.size();i++){int n=page*slots+i;var b=buttons.get(i);b.visible=n<rows.size();if(b.visible){var row=rows.get(n);b.setMessage(row.label);b.setTooltip(Tooltip.create(row.tooltip));b.active=tab==1?data.getBoolean("Editable")&&current!=null:row.target!=null&&find(row.target)!=null;}}
  previous.active=page>0;next.active=(page+1)*slots<rows.size();
 }
 @Override protected void containerTick(){super.containerTick();sync();}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
  if(vertical!=0&&channel.visible&&channel.active&&channel.isMouseOver(x,y)){
   int current;try{current=Integer.parseInt(channel.getValue());}catch(NumberFormatException invalid){current=diagnostics.getInt("Channel")+1;}
   int next=Math.clamp(current+(int)Math.signum(vertical),1,diagnostics.getInt("Count"));
   channel.setValue(Integer.toString(next));requestChannel();channel.setFocused(true);return true;
  }
  return super.mouseScrolled(x,y,horizontal,vertical);
 }
 @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257||key==335){if(group.visible&&group.isFocused()){assign(group.getValue());return true;}if(channel.visible&&channel.isFocused()){requestChannel();return true;}}if(key!=256){for(var box:List.of(group,channel))if(box.visible&&box.active&&box.isFocused()){box.keyPressed(key,scan,modifiers);return true;}}return super.keyPressed(key,scan,modifiers);}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){g.fill(leftPos-2,topPos-2,leftPos+imageWidth+2,topPos+imageHeight+2,0xFF9B614E);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF242632);}
 @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,title,10,12,0xFFE6D8EA,false);
  if(tab==2){g.drawString(font,tr("channel"),10,70,0xFFD9C4ED,false);String info=tr("power",diagnostics.getInt("Power"),diagnostics.getInt("Input")).getString()+"  "+diagnostics.getString("Name");g.drawString(font,font.plainSubstrByWidth(info,imageWidth-20),10,92,0xFF88DDAD,false);}
  else{g.drawString(font,font.plainSubstrByWidth(name(moduleId).getString(),imageWidth-20),10,tab==1?91:69,0xFFD9C4ED,false);if(tab==0)g.drawString(font,tr("reference_count",data.getCompound("References").getInt("Incoming")),10,91,0xFF88DDAD,false);}
  if(rows.isEmpty())g.drawWordWrap(font,tr(tab==0?"no_references":tab==1?"group_help":"no_signals"),10,119,imageWidth-20,0xFFBDC5D4);
  g.drawString(font,(page+1)+" / "+Math.max(1,(rows.size()+slots-1)/slots),imageWidth/2-16,imageHeight-25,0xFFD9C4ED,false);
 }
}
