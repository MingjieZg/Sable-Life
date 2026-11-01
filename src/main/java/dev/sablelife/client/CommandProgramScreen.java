package dev.sablelife.client;

import dev.sablelife.module.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Two-pane command list with the same dashboard revision and authoring lock as Move set. */
public final class CommandProgramScreen extends AbstractContainerScreen<HeartDashboardMenu> {
    private final UUID moduleId;
    private UUID selected;
    private CompoundTag snapshot=new CompoundTag(),runtime=new CompoundTag();
    private List<CommandProgram.Row> rows=List.of();
    private int page,slots,leftWidth,rightX,rightWidth,ticks,deleteUntil;
    private EditBox command,interval;
    private CommandSuggestions suggestions;
    private boolean completing(){return command!=null&&command.active&&command.isFocused();}
    private int suggestionOffset(){return command.getY()+command.getHeight()+2-72;}
    private Button mode,conditional,rowEnabled,save,intervalSave,add,copy,delete,up,down,prev,next;
    private final List<Button> entries=new ArrayList<>();
    public CommandProgramScreen(HeartDashboardMenu menu,Inventory inventory,Component title,UUID id){super(menu,inventory,title);moduleId=id;}
    private Component tr(String key){return Component.translatable("commands.sablelife."+key);}
    private CommandProgram.Row current(){return rows.stream().filter(r->r.id().equals(selected)).findFirst().orElse(null);}
    private Button button(Component label,int x,int y,int w,Runnable action){return addRenderableWidget(LifeUiStyle.button(label,leftPos+x,topPos+y,w,18,action));}
    private void send(String key,double value,String text){int index=current()==null?-1:rows.indexOf(current());PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,snapshot.getInt("Revision"),moduleId,"commands",key,value,index+"|"+text));}
    @Override protected void init(){
        imageWidth=Math.min(540,width-12);imageHeight=Math.min(300,height-12);leftWidth=imageWidth*29/100;rightX=leftWidth+18;rightWidth=imageWidth-rightX-10;slots=Math.max(3,(imageHeight-107)/17);super.init();entries.clear();
        button(tr("back"),imageWidth-65,8,55,this::onClose);
        up=button(Component.literal("↑"),leftWidth-41,34,20,()->send("up",0,""));down=button(Component.literal("↓"),leftWidth-18,34,20,()->send("down",0,""));
        for(int i=0;i<slots;i++){final int n=i;var b=button(Component.empty(),9,58+i*17,leftWidth-8,()->{int index=page*slots+n;if(index<rows.size()){selected=rows.get(index).id();command.setFocused(false);interval.setFocused(false);deleteUntil=0;sync();}});b.setHeight(15);entries.add(b);}
        prev=button(Component.literal("‹"),9,imageHeight-45,22,()->page=Math.max(0,page-1));next=button(Component.literal("›"),leftWidth-21,imageHeight-45,22,()->page++);
        int third=(leftWidth-12)/3;add=button(tr("add"),9,imageHeight-23,third,()->send("add",0,""));copy=button(tr("copy"),11+third,imageHeight-23,third,()->send("copy",0,""));delete=button(tr("delete"),13+third*2,imageHeight-23,third,()->{if(deleteUntil>ticks){send("delete",0,"");deleteUntil=0;}else deleteUntil=ticks+60;});
        rowEnabled=button(Component.empty(),rightX,35,rightWidth,()->{var r=current();if(r!=null)send("enabled",r.enabled()?0:1,"");});
        mode=button(Component.empty(),rightX+rightWidth/2,65,rightWidth/2,()->cycle(1));LifeUiStyle.reverse(mode,()->cycle(-1));
        conditional=button(Component.empty(),rightX+rightWidth/2,94,rightWidth/2,()->{var r=current();if(r!=null)send("conditional",r.conditional()?0:1,"");});LifeUiStyle.reverse(conditional,()->{var r=current();if(r!=null)send("conditional",r.conditional()?0:1,"");});conditional.setTooltip(Tooltip.create(tr("conditional_help")));
        interval=addRenderableWidget(new EditBox(font,leftPos+rightX+rightWidth/2,topPos+123,rightWidth/2-30,16,tr("interval")));interval.setMaxLength(6);interval.setFilter(s->s.matches("[0-9]{0,3}(\\.[0-9]{0,2})?"));intervalSave=button(Component.literal("✓"),imageWidth-35,122,25,this::applyInterval);
        command=addRenderableWidget(new EditBox(font,leftPos+rightX,topPos+167,rightWidth,18,tr("command")));command.setMaxLength(CommandProgram.MAX_TEXT);
        suggestions=new CommandSuggestions(minecraft,this,command,font,true,true,0,Math.max(1,Math.min(7,(height-command.getY()-command.getHeight()-4)/12)),false,Integer.MIN_VALUE);
        suggestions.setAllowHiding(false);
        command.setResponder(value->{suggestions.setAllowSuggestions(completing());suggestions.updateCommandInfo();});
        command.setTooltip(Tooltip.create(tr("completion_help")));
        save=button(tr("save"),rightX,190,rightWidth,()->{send("command",0,command.getValue());command.setFocused(false);});save.setTooltip(Tooltip.create(tr("origin_help")));sync();
    }
    private void cycle(int direction){var r=current();if(r!=null)send("mode",Math.floorMod(r.mode()+direction,3),"");}
    private void applyInterval(){try{double seconds=Double.parseDouble(interval.getValue());if(seconds<.05||seconds>600)throw new NumberFormatException();send("interval",seconds,"");interval.setTextColor(0xFFFFFF);interval.setFocused(false);}catch(NumberFormatException e){interval.setTextColor(0xFF7777);}}
    private void sync(){
        snapshot=menu.snapshot();rows=List.of();runtime=new CompoundTag();var modules=snapshot.getList("Modules",10);
        for(int i=0;i<modules.size();i++){var tag=modules.getCompound(i);if(tag.hasUUID("Id")&&tag.getUUID("Id").equals(moduleId)){rows=VirtualModule.load(tag).commands().rows();runtime=tag.getCompound("CommandRuntime");break;}}
        if(current()==null)selected=rows.isEmpty()?null:rows.getFirst().id();var r=current();boolean edit=snapshot.getBoolean("Editable")&&snapshot.getBoolean("CommandEditable"),has=edit&&r!=null;
        add.active=edit&&rows.size()<CommandProgram.LIMIT;copy.active=has&&rows.size()<CommandProgram.LIMIT;delete.active=has;delete.setMessage(tr(deleteUntil>ticks?"confirm":"delete"));up.active=has&&rows.indexOf(r)>0;down.active=has&&rows.indexOf(r)+1<rows.size();
        rowEnabled.active=mode.active=conditional.active=save.active=command.active=has;interval.active=intervalSave.active=has&&r.mode()==1;
        rowEnabled.setMessage(tr(r!=null&&r.enabled()?"enabled":"disabled"));mode.setMessage(tr("mode_"+(r==null?0:r.mode())));mode.setTooltip(Tooltip.create(tr("mode_help_"+(r==null?0:r.mode()))));conditional.setMessage(tr(r!=null&&r.conditional()?"conditional":"unconditional"));conditional.setTooltip(Tooltip.create(tr(r!=null&&r.conditional()?"conditional_help":"unconditional_help")));
        if(!command.isFocused()){String value=r==null?"":r.command();if(!command.getValue().equals(value))command.setValue(value);}if(!interval.isFocused())interval.setValue(String.format(Locale.ROOT,"%.2f",r==null?1:r.interval()/20.));
        page=Math.clamp(page,0,Math.max(0,(rows.size()-1)/slots));prev.active=page>0;next.active=(page+1)*slots<rows.size();
        for(int i=0;i<entries.size();i++){var b=entries.get(i);int n=page*slots+i;b.visible=n<rows.size();if(b.visible){var row=rows.get(n);String snippet=row.command().isBlank()?tr("empty").getString():row.command();if(snippet.length()>35)snippet=snippet.substring(0,32)+"…";b.setMessage(Component.literal((row.id().equals(selected)?"▶ ":"")+(n+1)+". "+snippet));LifeUiStyle.bookmark(b,switch(row.mode()){case 0->0xFFE8A653;case 1->0xFFAD75DF;default->0xFF72C78B;});b.setTooltip(Tooltip.create(tr("mode_"+row.mode()).copy().append(" — ").append(Component.literal(row.command()))));}}
    }
    @Override protected void containerTick(){super.containerTick();ticks++;sync();}
    private void text(GuiGraphics g,Component t,int x,int y,int max,int color){float scale=Math.min(.8f,Math.max(.3f,max/(float)Math.max(1,font.width(t))));g.pose().pushPose();g.pose().translate(leftPos+x,topPos+y,0);g.pose().scale(scale,scale,1);g.drawString(font,t,0,0,color,false);g.pose().popPose();}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF252530);g.fill(leftPos+leftWidth+8,topPos+33,leftPos+leftWidth+10,topPos+imageHeight-8,0xFF9A79B6);
        text(g,tr("title"),9,13,imageWidth-85,0xE5D5F1);text(g,tr("list"),9,40,leftWidth-50,0xC8BAD5);
        text(g,tr("type"),rightX,70,rightWidth/2-5,0xE1D5E8);text(g,tr("condition"),rightX,99,rightWidth/2-5,0xE1D5E8);text(g,tr("interval"),rightX,127,rightWidth/2-5,0xE1D5E8);text(g,tr("command"),rightX,153,rightWidth,0xE1D5E8);
        var selectedRow=current();
        text(g,tr(selectedRow!=null&&selectedRow.conditional()?"conditional_note":"unconditional_note"),rightX,114,rightWidth,0xBDC5D4);
        text(g,tr("origin_help"),rightX,218,rightWidth,0xBDC5D4);
        if(!snapshot.getBoolean("CommandEditable"))text(g,tr("permission"),rightX,imageHeight-30,rightWidth,0xFF9999);
        else if(!snapshot.getBoolean("CommandAllowed"))text(g,tr("server_disabled"),rightX,imageHeight-30,rightWidth,0xFF9999);
        else {var r=current();var results=runtime.getList("Rows",10);Component state=tr("waiting");if(r!=null)for(int i=0;i<results.size();i++){var t=results.getCompound(i);if(t.hasUUID("Id")&&t.getUUID("Id").equals(r.id()))state=Component.translatable("commands.sablelife.result",t.getInt("Success"));}text(g,state,rightX,imageHeight-30,rightWidth,0x81DDAE);}
        text(g,Component.literal((page+1)+" / "+Math.max(1,(rows.size()+slots-1)/slots)),38,imageHeight-39,leftWidth-60,0xC8BAD5);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);renderTooltip(g,x,y);
        if(completing()){g.pose().pushPose();g.pose().translate(0,suggestionOffset(),200);suggestions.render(g,x,y-suggestionOffset());g.pose().popPose();}}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(completing()&&suggestions.keyPressed(key,scan,modifiers))return true;if(key==257||key==335){if(command.isFocused()&&command.active){send("command",0,command.getValue());command.setFocused(false);return true;}if(interval.isFocused()&&interval.active){applyInterval();return true;}}if(getFocused() instanceof EditBox b&&b.isFocused()&&key!=256)return b.keyPressed(key,scan,modifiers);return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(vertical!=0&&interval.active&&interval.isMouseOver(x,y)){double value;try{value=Double.parseDouble(interval.getValue());}catch(NumberFormatException ignored){value=current()==null?1:current().interval()/20.;}interval.setValue(String.format(Locale.ROOT,"%.2f",Math.clamp(Math.round((value+Math.signum(vertical)*.1)*100)/100.,.05,600)));applyInterval();interval.setFocused(true);return true;}if(completing()&&suggestions.mouseScrolled(vertical))return true;if(vertical!=0&&x<leftPos+leftWidth){page=Math.clamp(page+(vertical>0?-1:1),0,Math.max(0,(rows.size()-1)/slots));return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(completing()&&suggestions.mouseClicked(x,y-suggestionOffset(),button))return true;
        boolean handled=super.mouseClicked(x,y,button);
        suggestions.setAllowSuggestions(completing());
        if(completing())suggestions.updateCommandInfo();
        return handled;
    }
    @Override public void resize(net.minecraft.client.Minecraft mc,int w,int h){
        String draft=command.getValue();boolean focused=completing();super.resize(mc,w,h);
        if(focused){setFocused(command);command.setFocused(true);command.setValue(draft);}
    }
    @Override public void onClose(){minecraft.setScreen(new HeartDashboardScreen(menu,minecraft.player.getInventory(),title));}
}
