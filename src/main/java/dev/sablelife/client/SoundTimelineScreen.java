package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.sound.*;
import dev.sablelife.module.ModuleNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Library on the left, editable layered events and an honest start/cutoff timeline on the right. */
@EventBusSubscriber(modid=SableLife.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SoundTimelineScreen extends AbstractContainerScreen<SoundTimelineMenu> {
    @SubscribeEvent public static void register(RegisterMenuScreensEvent e){e.register(SableLife.SOUND_TIMELINE_MENU.get(),SoundTimelineScreen::new);}
    private final UUID previewId=UUID.randomUUID();private UUID selected;
    private String query="",librarySelection="";private int libraryPage,rowPage,leftWidth,rightX,rightWidth,librarySlots,rowSlots,ticks;
    private int previewStarted=-1;private boolean loading;
    private final SoundDoubleClick doubleClick=new SoundDoubleClick();
    private boolean rowsSeen;private final Set<UUID> observedRows=new HashSet<>();
    private SoundTimeline timeline=new SoundTimeline();private List<SoundLibrary.Entry> library=List.of();
    private final List<Button> libraryButtons=new ArrayList<>(),rowButtons=new ArrayList<>();
    private final List<EditBox> values=new ArrayList<>();private final List<Button> applies=new ArrayList<>();
    private final String[] properties={"start","pitch","volume","radius","duration"};
    private EditBox search;private Button add,copy,delete,enable,up,down,libraryPrev,libraryNext,rowPrev,rowNext;
    private int timelineY(){return imageHeight-(imageHeight<280?34:52);}
    private int controlsY(){return timelineY()-82;}
    private int propertyY(){return controlsY()+29;}
    public SoundTimelineScreen(SoundTimelineMenu menu,Inventory inv,Component title){super(menu,inv,title);}
    private static Component tr(String key){return Component.translatable("sound_timeline.sablelife."+key);}
    private Button button(Component label,int x,int y,int w,int h,Runnable run){return addRenderableWidget(LifeUiStyle.button(label,leftPos+x,topPos+y,w,h,run));}
    private List<SoundLibrary.Entry> filtered(){return library.stream().filter(e->(e.label()+" "+e.id()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();}
    private SoundTimeline.Row current(){return timeline.rows().stream().filter(r->r.id().equals(selected)).findFirst().orElse(null);}
    private void send(String key,double value,String sound){PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,menu.snapshot().getInt("Revision"),selected==null?new UUID(0,0):selected,"sound_timeline",key,value,sound));}
    private void refreshLibrary(){var all=new TreeMap<String,String>();for(var id:BuiltInRegistries.SOUND_EVENT.keySet())all.put(id.toString(),id.toString());for(var e:SoundLibrary.entries())if(!e.id().isEmpty())all.put(e.id(),e.label());library=all.entrySet().stream().map(e->new SoundLibrary.Entry(e.getKey(),e.getValue())).toList();}
    @Override protected void init(){
        doubleClick.clear();
        imageWidth=Math.min(550,width-12);imageHeight=Math.min(340,height-12);leftWidth=Math.max(90,imageWidth*28/100);rightX=leftWidth+17;rightWidth=imageWidth-rightX-10;
        librarySlots=Math.max(2,(imageHeight-114)/18);rowSlots=Math.max(1,(controlsY()-62)/16);
        super.init();libraryButtons.clear();rowButtons.clear();values.clear();applies.clear();refreshLibrary();
        button(Component.literal("MC"),8,9,24,18,()->minecraft.setScreen(new VanillaSoundBrowser(this,id->{librarySelection=id;send("add",0,id);refreshLibrary();}))).setTooltip(Tooltip.create(tr("mc_import")));
        button(Component.literal("OGG"),36,9,28,18,()->net.minecraft.Util.getPlatform().openPath(SoundLibrary.folder())).setTooltip(Tooltip.create(tr("ogg_import")));
        button(tr("reload"),68,9,30,18,this::reload);
        button(tr("back"),imageWidth-61,9,51,18,()->{stopPreview();if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0);});
        search=addRenderableWidget(new EditBox(font,leftPos+8,topPos+36,leftWidth,17,tr("search")));search.setMaxLength(100);search.setHint(tr("search"));search.setValue(query);search.setResponder(s->{doubleClick.clear();query=s;libraryPage=0;sync();});
        for(int i=0;i<librarySlots;i++){final int row=i;var b=button(Component.empty(),8,60+i*18,leftWidth,16,()->{var list=filtered();int index=libraryPage*librarySlots+row;if(index<list.size()){librarySelection=list.get(index).id();sync();}});LifeUiStyle.reverse(b,()->{var list=filtered();int index=libraryPage*librarySlots+row;if(index<list.size()){librarySelection=list.get(index).id();send("add",0,librarySelection);}});libraryButtons.add(b);}
        libraryPrev=button(Component.literal("<"),8,imageHeight-48,23,16,()->{libraryPage--;sync();});libraryNext=button(Component.literal(">"),8+leftWidth-23,imageHeight-48,23,16,()->{libraryPage++;sync();});
        add=button(tr("add"),8,imageHeight-27,leftWidth/2-2,17,()->send("add",0,librarySelection));button(tr("listen"),12+leftWidth/2,imageHeight-27,leftWidth/2-4,17,this::listenLibrary);
        button(tr("play"),rightX,36,55,17,()->preview(timeline.rows()));button(tr("listen"),rightX+59,36,44,17,this::listenSelected);button(tr("stop"),rightX+107,36,38,17,this::stopPreview);
        rowPrev=button(Component.literal("<"),imageWidth-63,36,23,17,()->{rowPage--;sync();});rowNext=button(Component.literal(">"),imageWidth-36,36,23,17,()->{rowPage++;sync();});
        for(int i=0;i<rowSlots;i++){final int row=i;rowButtons.add(button(Component.empty(),rightX,60+i*16,rightWidth,14,()->{int index=rowPage*rowSlots+row;if(index<timeline.rows().size()){selected=timeline.rows().get(index).id();values.forEach(v->v.setFocused(false));sync();}}));}
        int controlsY=controlsY();
        enable=button(Component.empty(),rightX,controlsY,52,17,()->{var r=current();if(r!=null)send("enabled",r.enabled()?0:1,"");});
        copy=button(tr("copy"),rightX+56,controlsY,40,17,()->send("copy",0,""));delete=button(tr("delete"),rightX+100,controlsY,40,17,()->send("delete",0,""));up=button(Component.literal("↑"),rightX+144,controlsY,20,17,()->send("up",0,""));down=button(Component.literal("↓"),rightX+168,controlsY,20,17,()->send("down",0,""));
        int column=Math.max(38,rightWidth/3);int propertyY=propertyY();
        for(int i=0;i<properties.length;i++){final int index=i;int x=rightX+(i%3)*column,y=propertyY+(i/3)*26;var box=addRenderableWidget(new EditBox(font,leftPos+x,topPos+y,column-25,16,tr(properties[i])));box.setMaxLength(8);box.setFilter(s->s.matches("[0-9]{0,5}(\\.[0-9]{0,2})?"));box.setTooltip(Tooltip.create(tr(i==4?"duration_help":"property_help").copy().append("\n").append(tr(i<=2?"wheel_tenth":"wheel_one"))));values.add(box);applies.add(button(Component.literal("✓"),x+column-23,y,20,16,()->apply(index)));}
        sync();
    }
    private void reload(){if(loading)return;loading=true;SoundLibrary.prepare();minecraft.reloadResourcePacks().whenComplete((v,error)->minecraft.execute(()->{loading=false;if(minecraft.screen==this){refreshLibrary();sync();}}));}
    private void apply(int index){var r=current();if(r==null||!values.get(index).active)return;try{double v=Double.parseDouble(values.get(index).getValue());if(!Double.isFinite(v)||v<SoundTimelineInputs.minimum(index)||v>SoundTimelineInputs.maximum(index))throw new NumberFormatException();send(properties[index],v,"");values.get(index).setFocused(false);values.get(index).setTextColor(0xE0E0E0);}catch(NumberFormatException e){values.get(index).setTextColor(0xFF7777);}}
    private double currentValue(int index){var r=current();if(r==null)return SoundTimelineInputs.minimum(index);return switch(index){case 0->r.start()/20.;case 1->r.pitch();case 2->r.volume();case 3->r.radius();default->r.duration()/20.;};}
    private void stopPreview(){ModuleSoundClient.receive(new ModuleNetworking.ModuleSound(previewId,"",true,net.minecraft.world.phys.Vec3.ZERO,0,1,0,0,0,true));previewStarted=-1;}
    private void preview(List<SoundTimeline.Row> rows){stopPreview();if(minecraft.player==null)return;previewStarted=ticks;for(var row:rows)if(row.enabled()&&row.volume()>0)ModuleSoundClient.receive(new ModuleNetworking.ModuleSound(previewId,row.sound(),false,minecraft.player.position(),row.volume(),row.pitch(),row.radius(),row.start(),row.duration(),true));}
    private void listenLibrary(){if(!librarySelection.isEmpty())preview(List.of(new SoundTimeline.Row(UUID.randomUUID(),librarySelection,0,1,1,32,0,true)));}
    private void listenSelected(){var r=current();if(r!=null)preview(List.of(new SoundTimeline.Row(r.id(),r.sound(),0,r.volume(),r.pitch(),r.radius(),r.duration(),true)));}
    @Override public boolean mouseClicked(double x,double y,int button){
        String item=null;boolean fromLibrary=false;
        if(button==0){
            var matches=filtered();
            for(int i=0;i<libraryButtons.size();i++)if(libraryButtons.get(i).isMouseOver(x,y)&&libraryButtons.get(i).active){int at=libraryPage*librarySlots+i;if(at<matches.size()){item="library:"+matches.get(at).id();fromLibrary=true;}break;}
            if(item==null)for(int i=0;i<rowButtons.size();i++)if(rowButtons.get(i).isMouseOver(x,y)&&rowButtons.get(i).active){int at=rowPage*rowSlots+i;if(at<timeline.rows().size())item="selected:"+timeline.rows().get(at).id();break;}
        }
        boolean handled=super.mouseClicked(x,y,button);
        if(item!=null&&handled){if(doubleClick.click(item,net.minecraft.Util.getMillis())){if(fromLibrary)listenLibrary();else listenSelected();}}
        else doubleClick.clear();
        return handled;
    }
    private void sync(){var data=menu.snapshot();if(data.getBoolean("Ready")){
            var next=SoundTimeline.load(data);
            if(rowsSeen){var added=next.rows().stream().filter(r->!observedRows.contains(r.id())).toList();if(!added.isEmpty()){selected=added.getLast().id();rowPage=next.rows().indexOf(added.getLast())/Math.max(1,rowSlots);values.forEach(b->b.setFocused(false));}}
            observedRows.clear();next.rows().forEach(r->observedRows.add(r.id()));rowsSeen=true;timeline=next;
        }if(current()==null&&!timeline.rows().isEmpty())selected=timeline.rows().getFirst().id();boolean editable=data.getBoolean("Editable");
        libraryPage=Math.clamp(libraryPage,0,Math.max(0,(filtered().size()-1)/Math.max(1,librarySlots)));rowPage=Math.clamp(rowPage,0,Math.max(0,(timeline.rows().size()-1)/Math.max(1,rowSlots)));
        var filtered=filtered();for(int i=0;i<libraryButtons.size();i++){var b=libraryButtons.get(i);int index=libraryPage*librarySlots+i;b.visible=index<filtered.size();b.active=!loading;if(b.visible){var e=filtered.get(index);b.setMessage(Component.literal((e.id().equals(librarySelection)?"▶ ":"")+e.label()));b.setTooltip(Tooltip.create(Component.literal(e.id()).append("\n").append(tr("library_help"))));}}
        for(int i=0;i<rowButtons.size();i++){var b=rowButtons.get(i);int index=rowPage*rowSlots+i;b.visible=index<timeline.rows().size();if(b.visible){var r=timeline.rows().get(index);b.setMessage(Component.literal((r.id().equals(selected)?"▶ ":"")+(index+1)+". "+SoundLibrary.label(r.sound())+String.format(Locale.ROOT," · %.2fs",r.start()/20.)));b.setTooltip(Tooltip.create(Component.literal(r.sound()).append("\n").append(tr("row_help")).append("\n").append(tr(r.duration()==0?"natural":"cutoff"))));LifeUiStyle.bookmark(b,r.enabled()?0xB47DDD:0x666666);}}
        if(add==null)return;add.active=editable&&!librarySelection.isEmpty()&&timeline.rows().size()<SoundTimeline.LIMIT;var r=current();copy.active=editable&&r!=null&&timeline.rows().size()<SoundTimeline.LIMIT;delete.active=enable.active=editable&&r!=null;up.active=editable&&r!=null&&timeline.rows().indexOf(r)>0;down.active=editable&&r!=null&&timeline.rows().indexOf(r)<timeline.rows().size()-1;enable.setMessage(tr(r!=null&&r.enabled()?"enabled":"disabled"));
        libraryPrev.active=libraryPage>0;libraryNext.active=(libraryPage+1)*librarySlots<filtered.size();rowPrev.active=rowPage>0;rowNext.active=(rowPage+1)*rowSlots<timeline.rows().size();
        for(int i=0;i<values.size();i++){var b=values.get(i);b.active=applies.get(i).active=editable&&r!=null;b.setEditable(b.active);if(!b.isFocused())b.setValue(r==null?"":String.format(Locale.ROOT,"%.2f",switch(i){case 0->r.start()/20.;case 1->r.pitch();case 2->r.volume();case 3->(double)r.radius();default->r.duration()/20.;}));}
    }
    @Override protected void containerTick(){super.containerTick();ticks++;sync();}
    @Override public boolean keyPressed(int key,int scan,int mods){doubleClick.clear();if(key==257||key==335){for(int i=0;i<values.size();i++)if(values.get(i).isFocused()){apply(i);return true;}}if(search.isFocused()&&key!=256)return search.keyPressed(key,scan,mods);for(var b:values)if(b.active&&b.isFocused()&&key!=256)return b.keyPressed(key,scan,mods);return super.keyPressed(key,scan,mods);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(vertical!=0){doubleClick.clear();for(int i=0;i<values.size();i++){var box=values.get(i);if(!box.isMouseOver(x,y))continue;if(!box.active)return true;double value;try{value=Double.parseDouble(box.getValue());}catch(NumberFormatException ignored){value=currentValue(i);}box.setValue(String.format(Locale.ROOT,"%.2f",SoundTimelineInputs.scroll(i,value,currentValue(i),vertical)));apply(i);box.setFocused(true);return true;}
            if(x>=leftPos+8&&x<=leftPos+8+leftWidth&&y>=topPos+60&&y<topPos+imageHeight-48)libraryPage-=(int)Math.signum(vertical);
            else if(x>=leftPos+rightX&&x<=leftPos+rightX+rightWidth&&y>=topPos+60&&y<topPos+controlsY())rowPage-=(int)Math.signum(vertical);else return super.mouseScrolled(x,y,horizontal,vertical);sync();return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public void removed(){stopPreview();super.removed();}
    @Override public void render(GuiGraphics g,int x,int y,float partial){sync();renderBackground(g,x,y,partial);super.render(g,x,y,partial);renderTooltip(g,x,y);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF242632);int divider=(8+leftWidth+rightX)/2;g.fill(leftPos+divider,topPos+36,leftPos+divider+2,topPos+imageHeight-10,0xFF9673AE);}
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){g.drawString(font,Component.empty().append(tr("selected")).append(" ("+timeline.rows().size()+")"),rightX,12,0xE6D8EA,false);int column=Math.max(38,rightWidth/3),propertyY=propertyY();for(int i=0;i<properties.length;i++)g.drawString(font,tr(properties[i]),rightX+i%3*column,propertyY+i/3*26-10,0xD9C4ED,false);
        int y=timelineY();double max=Math.max(2,timeline.rows().stream().mapToDouble(r->(r.start()+(r.duration()>0?r.duration():40))/20.).max().orElse(2));
        for(int i=0;i<=4;i++){int x=rightX+(int)(rightWidth*i/4.);g.drawString(font,String.format(Locale.ROOT,"%.1fs",max*i/4),Math.min(x,imageWidth-35),y-10,0xBDC5D4,false);g.fill(x,y,x+1,imageHeight-10,0xFF505060);}
        int lanes=Math.max(1,rowSlots);for(int i=0;i<lanes;i++){int index=rowPage*rowSlots+i;if(index>=timeline.rows().size())break;var r=timeline.rows().get(index);int x=rightX+(int)(rightWidth*r.start()/20./max),end=rightX+(int)(rightWidth*(r.start()+(r.duration()>0?r.duration():20))/20./max);int lane=y+i*Math.max(3,(imageHeight-y-14)/lanes);int color=r.id().equals(selected)?0xFFDBBAFF:r.enabled()?0xFF9974B5:0xFF55555F;if(r.duration()>0)g.fill(x,lane,Math.max(x+2,end),lane+3,color);else{g.fill(x,lane,x+2,lane+4,color);for(int tail=x+3;tail<Math.min(end,imageWidth-10);tail+=4)g.fill(tail,lane,tail+2,lane+2,color);}}
        if(previewStarted>=0){double time=(ticks-previewStarted)/20.;if(time<=max){int x=rightX+(int)(rightWidth*time/max);g.fill(x,y-2,x+1,imageHeight-10,0xFFFFFF55);}}
        g.drawCenteredString(font,(libraryPage+1)+" / "+Math.max(1,(filtered().size()+librarySlots-1)/librarySlots),8+leftWidth/2,imageHeight-43,0xBDC5D4);
    }
}
