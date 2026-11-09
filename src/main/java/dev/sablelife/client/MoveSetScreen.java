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

/** Compact move library and parallel action schedule, using the dashboard revision lock. */
public final class MoveSetScreen extends AbstractContainerScreen<HeartDashboardMenu> {
    private final UUID tableId;
    private UUID selected;
    private int initialMove=-1;
    private CompoundTag snapshot=new CompoundTag(), runtime=new CompoundTag();
    private VirtualModule table;
    private List<VirtualModule> modules=List.of();
    private List<MoveSet.Move> moves=List.of();
    private int movePage, actionPage, rulePage, pickerPage, ticks, deleteUntil, actionDelete=-1;
    private int leftWidth, rightX, rightWidth, moveSlots, actionSlots, ruleSlots, timelineY;
    private boolean rules, picking;
    private String searchText="", pickerText="";
    private EditBox search, name, pickerSearch;
    private Button enabled, moveEnabled, addMove, copyMove, deleteMove, movePrev, moveNext, up, down, tabActions, tabRules, actionAdd, actionPrev, actionNext, statusHelp;
    private final List<Button> moveButtons=new ArrayList<>(), actionButtons=new ArrayList<>(), deletes=new ArrayList<>(), actionUps=new ArrayList<>(), actionDowns=new ArrayList<>(), pickerButtons=new ArrayList<>();
    private final List<EditBox> starts=new ArrayList<>(), durations=new ArrayList<>(), ruleInputs=new ArrayList<>();
    private final List<Button> ruleChoices=new ArrayList<>(), applyButtons=new ArrayList<>();
    private final List<ModuleFields.Field> ruleFields;
    private static List<ModuleFields.Field> rulesFor(HeartDashboardMenu menu){
        var rules=ModuleFields.moveRules();
        if(!menu.snapshot().getBoolean("ControlCore"))return rules;
        var fields=new ArrayList<>(rules);
        fields.removeIf(f->Set.of("phaseLower","phaseUpper").contains(f.key()));
        var keys=fields.stream().map(ModuleFields.Field::key).toList();
        Collections.swap(fields,keys.indexOf("target"),keys.indexOf("dispatchRepeat"));
        return List.copyOf(fields);
    }
    public MoveSetScreen(HeartDashboardMenu menu,Inventory inventory,Component title,UUID tableId){super(menu,inventory,title);this.tableId=tableId;this.ruleFields=rulesFor(menu);}
    public MoveSetScreen(HeartDashboardMenu menu,Inventory inventory,Component title,UUID tableId,int move){this(menu,inventory,title,tableId);initialMove=move;}
    private Component tr(String key){return Component.translatable("moveset.sablelife."+key);}
    private Component moduleName(VirtualModule m){return m.name().isBlank()?Component.translatable("dashboard.sablelife.type_"+m.type()):Component.literal(m.name());}
    private Component moveName(MoveSet.Move m){return m.config().name().isBlank()?Component.translatable("moveset.sablelife.move_number",moves.indexOf(m)+1):Component.literal(m.config().name());}
    private MoveSet.Move current(){return moves.stream().filter(m->m.config().id().equals(selected)).findFirst().orElse(null);}
    private List<MoveSet.Move> filtered(){return moves.stream().filter(m->moveName(m).getString().toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT))).toList();}
    private List<VirtualModule> candidates(){return modules.stream().filter(m->MoveSet.TARGETS.contains(m.type())&&(!snapshot.getBoolean("ControlCore")||dev.sablelife.heart.RedstoneCorePolicy.allowed(m))&&moduleName(m).getString().toLowerCase(Locale.ROOT).contains(pickerText.toLowerCase(Locale.ROOT))).toList();}
    private VirtualModule target(UUID id){return modules.stream().filter(m->m.id().equals(id)).findFirst().orElse(null);}
    private Button button(Component label,int x,int y,int w,int h,Runnable run){return addRenderableWidget(LifeUiStyle.button(label,leftPos+x,topPos+y,w,h,run));}
    private EditBox input(int x,int y,int w,int max,Component label){var b=addRenderableWidget(new EditBox(font,leftPos+x,topPos+y,w,16,label));b.setMaxLength(max);return b;}
    private void send(String key,double value,String extra){
        String text=key.equals("add")?"":Integer.toString(moves.indexOf(current()))+(extra.isEmpty()?"":"|"+extra);
        PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,snapshot.getInt("Revision"),tableId,"move_set",key,value,text));
    }
    private void select(MoveSet.Move m){selected=m.config().id();actionPage=0;deleteUntil=0;actionDelete=-1;rebuildWidgets();}
    @Override protected void init(){
        imageWidth=Math.min(540,width-12);imageHeight=Math.min(330,height-12);
        leftWidth=Math.max(110,imageWidth*26/100);rightX=leftWidth+18;rightWidth=imageWidth-rightX-10;
        moveSlots=Math.max(3,(imageHeight-116)/15);actionSlots=Math.max(2,Math.min(12,(imageHeight-150)/17));ruleSlots=Math.max(3,Math.min(8,(imageHeight-150)/20));timelineY=88+actionSlots*17;
        super.init();moveButtons.clear();actionButtons.clear();deletes.clear();actionUps.clear();actionDowns.clear();starts.clear();durations.clear();ruleInputs.clear();ruleChoices.clear();applyButtons.clear();pickerButtons.clear();
        button(tr("back"),imageWidth-65,7,55,18,this::onClose);
        enabled=button(Component.empty(),imageWidth-153,7,82,18,()->{if(table!=null)PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,snapshot.getInt("Revision"),tableId,"edit","enabled",table.enabled()?0:1,""));});
        search=input(9,33,leftWidth-8,40,tr("search"));search.setValue(searchText);search.setHint(tr("search"));search.setResponder(s->{searchText=s;movePage=0;});
        for(int i=0;i<moveSlots;i++){final int n=i;var b=button(Component.empty(),9,68+i*15,leftWidth-8,13,()->{var list=filtered();int at=movePage*moveSlots+n;if(at<list.size())select(list.get(at));});LifeUiStyle.bookmark(b,ModuleCategory.SIGNAL.color);moveButtons.add(b);}
        movePrev=button(Component.literal("‹"),9,imageHeight-43,21,17,()->movePage=Math.max(0,movePage-1));
        moveNext=button(Component.literal("›"),leftWidth-20,imageHeight-43,21,17,()->movePage++);
        int third=(leftWidth-12)/3;
        addMove=button(tr("add"),9,imageHeight-23,third,17,()->send("add",0,""));
        copyMove=button(tr("copy"),11+third,imageHeight-23,third,17,()->send("duplicate",0,""));
        deleteMove=button(tr("delete"),13+2*third,imageHeight-23,third,17,()->{if(deleteUntil>ticks){send("delete",0,"");deleteUntil=0;}else deleteUntil=ticks+60;});
        up=button(Component.literal("↑"),leftWidth-49,52,23,13,()->send("up",0,""));
        down=button(Component.literal("↓"),leftWidth-24,52,23,13,()->send("down",0,""));
        name=input(rightX,33,rightWidth-64,40,tr("name"));
        moveEnabled=button(Component.empty(),imageWidth-68,32,29,18,()->{var m=current();if(m!=null)send("enabled",m.config().enabled()?0:1,"");});
        applyButtons.add(button(Component.literal("✓"),imageWidth-35,32,25,18,()->{send("name",0,name.getValue());name.setFocused(false);}));
        tabActions=button(tr("actions"),rightX,54,rightWidth/2-2,18,()->{rules=false;picking=false;rebuildWidgets();});
        tabRules=button(tr("rules"),rightX+rightWidth/2+2,54,rightWidth/2-2,18,()->{rules=true;picking=false;rebuildWidgets();});
        statusHelp=button(Component.literal("?"),imageWidth-23,imageHeight-18,13,13,()->{});
        if(picking)initPicker();else if(rules)initRules();else initActions();sync();
    }
    private void initActions(){
        int mw=rightWidth-168;
        for(int i=0;i<actionSlots;i++){final int n=i;int y=87+i*17;
            actionButtons.add(button(Component.empty(),rightX,y,mw,15,()->{}));
            var start=input(rightX+mw+3,y,36,6,tr("start"));start.setHeight(14);start.setFilter(s->s.matches("[0-9]{0,3}(\\.[0-9]{0,2})?"));starts.add(start);
            applyButtons.add(button(Component.literal("✓"),rightX+mw+41,y,13,15,()->applyAction(n,false)));
            var duration=input(rightX+mw+57,y,36,6,tr("duration"));duration.setHeight(14);duration.setFilter(s->s.matches("[0-9]{0,3}(\\.[0-9]{0,2})?"));durations.add(duration);
            applyButtons.add(button(Component.literal("✓"),rightX+mw+95,y,13,15,()->applyAction(n,true)));
            actionUps.add(button(Component.literal("↑"),rightX+mw+111,y,17,15,()->send("action_up",0,Integer.toString(actionPage*actionSlots+n))));
            actionDowns.add(button(Component.literal("↓"),rightX+mw+130,y,17,15,()->send("action_down",0,Integer.toString(actionPage*actionSlots+n))));
            deletes.add(button(Component.literal("×"),rightX+mw+150,y,18,15,()->{int index=actionPage*actionSlots+n;if(actionDelete==index){send("action_delete",0,Integer.toString(index));actionDelete=-1;}else actionDelete=index;}));
        }
        actionAdd=button(tr("add_action"),rightX,timelineY,Math.max(80,rightWidth-90),18,()->{picking=true;pickerPage=0;rebuildWidgets();});actionAdd.setTooltip(Tooltip.create(tr("bind_help")));
        actionPrev=button(Component.literal("‹"),imageWidth-91,timelineY,23,18,()->{actionPage=Math.max(0,actionPage-1);rebuildWidgets();});
        actionNext=button(Component.literal("›"),imageWidth-33,timelineY,23,18,()->{actionPage++;rebuildWidgets();});
    }
    private void initPicker(){
        pickerSearch=input(rightX,81,rightWidth-55,40,tr("find_module"));pickerSearch.setValue(pickerText);pickerSearch.setHint(tr("find_module"));pickerSearch.setResponder(s->{pickerText=s;pickerPage=0;});
        button(tr("cancel"),imageWidth-60,80,50,18,()->{picking=false;rebuildWidgets();});int count=Math.max(2,(imageHeight-145)/23);
        for(int i=0;i<count;i++){final int n=i;pickerButtons.add(button(Component.empty(),rightX,104+i*23,rightWidth,21,()->{var list=candidates();int at=pickerPage*count+n;if(at<list.size()){send("action_add",0,list.get(at).id().toString());picking=false;rebuildWidgets();}}));}
        button(Component.literal("‹"),rightX,imageHeight-37,25,17,()->pickerPage=Math.max(0,pickerPage-1));
        button(Component.literal("›"),imageWidth-35,imageHeight-37,25,17,()->pickerPage=Math.min(Math.max(0,(candidates().size()-1)/count),pickerPage+1));
    }
    private void initRules(){
        for(int i=0;i<ruleSlots;i++){final int n=i;int y=91+i*20;var field=input(rightX+rightWidth/2,y,rightWidth/2-30,8,Component.empty());field.setFilter(s->s.matches("[0-9]{0,5}(\\.[0-9]{0,2})?"));ruleInputs.add(field);
            applyButtons.add(button(Component.literal("✓"),imageWidth-35,y-1,25,18,()->applyRule(n)));
            var choice=button(Component.empty(),rightX+rightWidth/2,y-1,rightWidth/2,18,()->{var m=current();int at=rulePage*ruleSlots+n;if(m!=null&&at<ruleFields.size()){var f=ruleFields.get(at);if(f.key().equals("dispatchWeight"))send("selection_mode",m.priority()?0:1,"");else send(f.key(),((int)m.config().value(f.key())+1)%f.choices().size(),"");}});
            LifeUiStyle.reverse(choice,()->{var m=current();int at=rulePage*ruleSlots+n;if(m!=null&&at<ruleFields.size()){var f=ruleFields.get(at);if(f.key().equals("dispatchWeight"))send("selection_mode",m.priority()?0:1,"");else send(f.key(),Math.floorMod((int)m.config().value(f.key())-1,f.choices().size()),"");}});ruleChoices.add(choice);
        }
        button(Component.literal("‹"),rightX,imageHeight-45,25,18,()->{rulePage=Math.max(0,rulePage-1);rebuildWidgets();});
        button(Component.literal("›"),imageWidth-35,imageHeight-45,25,18,()->{rulePage=Math.min((ruleFields.size()-1)/ruleSlots,rulePage+1);rebuildWidgets();});
    }
    private void applyAction(int n,boolean duration){var box=duration?durations.get(n):starts.get(n);try{double v=Double.parseDouble(box.getValue());if(!Double.isFinite(v)||v<(duration?.05:0)||v>(duration?600:599.95))throw new NumberFormatException();send(duration?"action_duration":"action_start",v,Integer.toString(actionPage*actionSlots+n));box.setFocused(false);box.setTextColor(0xFFFFFF);}catch(NumberFormatException e){box.setTextColor(0xFF7777);}}
    private void applyRule(int n){int at=rulePage*ruleSlots+n;if(at>=ruleFields.size())return;var f=ruleFields.get(at);if(current()==null||ModuleMoveSet.ruleLocked(table,f.key(),snapshot.getBoolean("ControlCore"))||f.key().equals("dispatchWeight")&&current().priority())return;var box=ruleInputs.get(n);try{double v=Double.parseDouble(box.getValue())*f.scale();if(!Double.isFinite(v)||v<f.min()||v>f.max())throw new NumberFormatException();send(f.key(),v,"");box.setFocused(false);box.setTextColor(0xFFFFFF);}catch(NumberFormatException e){box.setTextColor(0xFF7777);}}
    @Override protected void containerTick(){super.containerTick();ticks++;sync();}
    private void sync(){
        snapshot=menu.snapshot();var list=snapshot.getList("Modules",10);var loaded=new ArrayList<VirtualModule>();table=null;
        for(int i=0;i<list.size();i++){var tag=list.getCompound(i);var m=VirtualModule.load(tag);loaded.add(m);if(m.id().equals(tableId)){table=m;runtime=tag.getCompound("MoveSetRuntime");}}
        modules=List.copyOf(loaded);moves=table==null?List.of():table.moveSet().moves();if(initialMove>=0&&!moves.isEmpty()){var move=moves.get(Math.min(initialMove,moves.size()-1));selected=move.config().id();movePage=Math.min(initialMove,moves.size()-1)/Math.max(1,moveSlots);initialMove=-1;}if(current()==null)selected=moves.isEmpty()?null:moves.getFirst().config().id();
        boolean editable=snapshot.getBoolean("Editable")&&table!=null;var current=current();
        var details=current==null?null:problemDetails(current);
        statusHelp.visible=!picking&&details!=null;statusHelp.active=statusHelp.visible;
        if(details!=null){statusHelp.setTooltip(Tooltip.create(details));var label=moveStatus(current);float scale=Math.min(.8f,Math.max(.3f,(rightWidth-18)/(float)Math.max(1,font.width(label))));statusHelp.setX(leftPos+rightX+Math.min(rightWidth-13,(int)Math.ceil(font.width(label)*scale)+4));}
        enabled.active=editable;enabled.setMessage(tr(table!=null&&table.enabled()?"enabled":"disabled"));
        addMove.active=editable&&moves.size()<MoveSet.LIMIT;copyMove.active=addMove.active&&current!=null;deleteMove.active=editable&&current!=null;deleteMove.setMessage(tr(deleteUntil>ticks?"confirm":"delete"));up.active=down.active=editable&&current!=null;
        moveEnabled.active=editable&&current!=null;moveEnabled.setMessage(Component.literal(current!=null&&current.config().enabled()?"✓":"—"));moveEnabled.setTooltip(Tooltip.create(tr("toggle_move")));
        name.active=editable&&current!=null;if(!name.isFocused())name.setValue(current==null?"":current.config().name());name.setHint(current==null?tr("name"):moveName(current));for(var b:applyButtons)b.active=editable&&current!=null;
        var rows=filtered();movePage=Math.clamp(movePage,0,Math.max(0,(rows.size()-1)/moveSlots));movePrev.active=movePage>0;moveNext.active=(movePage+1)*moveSlots<rows.size();
        for(int i=0;i<moveButtons.size();i++){var b=moveButtons.get(i);int n=movePage*moveSlots+i;b.visible=n<rows.size();if(b.visible){var m=rows.get(n);b.setMessage(Component.literal(m.config().id().equals(selected)?"▶ ":"").append(moveName(m)).append(" · ").append(m.priority()?tr("priority_short"):Component.literal("W"+(int)m.config().value("dispatchWeight"))).append(" / "+seconds(m.config().value("dispatchCooldown"))+"s"));b.setTooltip(Tooltip.create(moveStatus(m)));}}
        tabActions.active=tabRules.active=current!=null;
        if(picking){var choices=candidates();for(int i=0;i<pickerButtons.size();i++){var b=pickerButtons.get(i);int n=pickerPage*pickerButtons.size()+i;b.visible=n<choices.size();b.active=editable&&current!=null;if(b.visible){var m=choices.get(n);b.setMessage(moduleName(m).copy().append(" #"+(modules.indexOf(m)+1)));LifeUiStyle.bookmark(b,ModuleCategory.of(m.type()).color);b.setTooltip(Tooltip.create(tr("bind_help")));}}return;}
        if(rules){for(int i=0;i<ruleSlots;i++){int n=rulePage*ruleSlots+i;var box=ruleInputs.get(i);var b=ruleChoices.get(i);boolean show=current!=null&&n<ruleFields.size();var f=show?ruleFields.get(n):null;box.visible=show&&!f.choice();b.visible=show&&(f.choice()||f.key().equals("dispatchWeight"));box.active=b.active=editable&&show&&!ModuleMoveSet.ruleLocked(table,f.key(),snapshot.getBoolean("ControlCore"));applyButtons.get(i+1).visible=box.visible;applyButtons.get(i+1).active=box.active;box.setEditable(box.active);if(!box.active)box.setFocused(false);
            if(show){
                var help=Tooltip.create(Component.translatable("moveset.sablelife.rule_help_"+f.key()));box.setTooltip(help);b.setTooltip(help);
                boolean probability=f.key().equals("dispatchWeight");
                b.setWidth(probability?62:f.key().equals("lineOfSight")?36:rightWidth/2);
                box.setX(leftPos+rightX+rightWidth/2+(probability?65:0));box.setWidth(rightWidth/2-30-(probability?65:0));
                if(probability){b.setMessage(tr(current.priority()?"priority":"weighted"));box.active=editable&&!current.priority();applyButtons.get(i+1).active=box.active;var modeHelp=Tooltip.create(tr("probability_help"));b.setTooltip(modeHelp);box.setTooltip(modeHelp);if(!box.active)box.setFocused(false);}
                if(f.choice())b.setMessage(Component.translatable("dashboard.sablelife.choice_"+f.choices().get((int)current.config().value(f.key()))));else if(!box.isFocused())box.setValue(String.format(Locale.ROOT,"%.2f",current.config().value(f.key())/f.scale()));}}return;}
        var actions=current==null?List.<MoveSet.Action>of():current.actions();actionPage=Math.clamp(actionPage,0,Math.max(0,(actions.size()-1)/actionSlots));actionAdd.active=editable&&current!=null&&actions.size()<MoveSet.ACTION_LIMIT;actionPrev.active=actionPage>0;actionNext.active=(actionPage+1)*actionSlots<actions.size();
        for(int i=0;i<actionSlots;i++){int n=actionPage*actionSlots+i;boolean show=n<actions.size();var b=actionButtons.get(i);b.visible=starts.get(i).visible=durations.get(i).visible=deletes.get(i).visible=actionUps.get(i).visible=actionDowns.get(i).visible=show;starts.get(i).active=durations.get(i).active=deletes.get(i).active=actionUps.get(i).active=actionDowns.get(i).active=editable&&show;actionUps.get(i).active=editable&&show&&n>0;actionDowns.get(i).active=editable&&show&&n+1<actions.size();applyButtons.get(1+i*2).visible=applyButtons.get(2+i*2).visible=show;deletes.get(i).setMessage(Component.literal(actionDelete==n?"?":"×"));
            if(show){var a=actions.get(n);var t=target(a.module());b.setMessage(t==null?tr("missing"):moduleName(t));LifeUiStyle.bookmark(b,t==null?0:ModuleCategory.of(t.type()).color);b.setTooltip(Tooltip.create(t==null?tr("missing"):moduleName(t).copy().append(" #"+(modules.indexOf(t)+1)).append(" — ").append(Component.translatable("dashboard.sablelife.trigger_"+(int)t.value("trigger")))));if(!starts.get(i).isFocused())starts.get(i).setValue(seconds(a.start()));if(!durations.get(i).isFocused())durations.get(i).setValue(seconds(a.duration()));}
        }
    }
    private String seconds(double ticks){return String.format(Locale.ROOT,"%.2f",ticks/20.);}
    private Component problemDetails(MoveSet.Move move){
        var details=Component.empty();boolean any=false;
        if(table!=null&&!table.enabled()){details.append(tr("table_disabled"));any=true;}
        if(!move.config().enabled()){if(any)details.append("\n");details.append(tr("move_disabled"));any=true;}
        if(move.actions().isEmpty()){if(any)details.append("\n");details.append(tr("empty"));any=true;}
        var rows=runtime.getList("Rows",10);
        for(int i=0;i<rows.size();i++){var row=rows.getCompound(i);if(!row.hasUUID("Id")||!row.getUUID("Id").equals(move.config().id()))continue;
            var problems=row.getList("Problems",10);
            for(int j=0;j<problems.size();j++){var issue=problems.getCompound(j);if(any)details.append("\n");any=true;
                var m=issue.hasUUID("Module")?target(issue.getUUID("Module")):null;
                details.append(Component.translatable("moveset.sablelife.problem_action",issue.getInt("Action"))).append(" · ");
                if(m!=null)details.append(moduleName(m)).append(" #"+(modules.indexOf(m)+1));else details.append(tr("missing"));
                String reason=issue.getString("Reason");details.append(" — ").append(reason.equals("trigger")?tr("trigger"):reason.equals("missing")?tr("problem_missing"):reason.equals("unsupported")?tr("problem_unsupported"):Component.translatable("dashboard.sablelife.status_"+reason));
            }
        }
        return any?details:null;
    }
    private Component moveStatus(MoveSet.Move move){
        if(table==null||!table.enabled())return tr("disabled");if(runtime.hasUUID("Current")&&runtime.getUUID("Current").equals(move.config().id()))return tr("running");
        var rows=runtime.getList("Rows",10);for(int i=0;i<rows.size();i++){var r=rows.getCompound(i);if(r.hasUUID("Id")&&r.getUUID("Id").equals(move.config().id())){if(!r.getString("Status").equals("ready"))return tr(r.getString("Status"));if(r.getInt("Cooldown")>0)return Component.translatable("moveset.sablelife.cooldown_left",seconds(r.getInt("Cooldown")));}}return tr("ready");
    }
    private void text(GuiGraphics g,Component t,int x,int y,int max,int color){float scale=Math.min(.8f,Math.max(.3f,max/(float)Math.max(1,font.width(t))));g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.drawString(font,t,0,0,color,false);g.pose().popPose();}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF252530);g.fill(leftPos+leftWidth+8,topPos+31,leftPos+leftWidth+10,topPos+imageHeight-8,0xFF9A79B6);
        text(g,tr("title"),leftPos+9,topPos+12,imageWidth-170,0xE5D5F1);text(g,tr("list_header"),leftPos+9,topPos+56,leftWidth-65,0xC8BAD5);var current=current();
        if(current==null){text(g,tr("empty_help"),leftPos+rightX,topPos+100,rightWidth,0xC8BAD5);return;}
        if(picking){text(g,tr("bind_help"),leftPos+rightX,topPos+imageHeight-15,rightWidth,0xC8BAD5);return;}
        if(rules){for(int i=0;i<ruleSlots;i++){int n=rulePage*ruleSlots+i;if(n<ruleFields.size()){
            var key=ruleFields.get(n).key();int y=topPos+95+i*20;
            text(g,key.equals("dispatchWeight")?tr("probability"):Component.translatable("moveset.sablelife.rule_label_"+key),leftPos+rightX,y,rightWidth/2-5,ModuleMoveSet.ruleLocked(table,key,snapshot.getBoolean("ControlCore"))?0x77717D:0xE1D5E8);
            if(key.equals("lineOfSight"))text(g,Component.translatable("dashboard.sablelife.line_of_sight_note"),leftPos+rightX+rightWidth/2+40,y,rightWidth/2-40,0xBDC5D4);
            else if(List.of("dispatchFront","phaseUpper","phaseLower").contains(key))text(g,Component.translatable("moveset.sablelife.note_"+key),leftPos+rightX,y+9,rightWidth/2-5,0xBDC5D4);
        }}text(g,Component.literal((rulePage+1)+" / "+((ruleFields.size()+ruleSlots-1)/ruleSlots)),leftPos+rightX+38,topPos+imageHeight-40,rightWidth-80,0xC8BAD5);
        }else{
            int mw=rightWidth-168;text(g,tr("module"),leftPos+rightX,topPos+77,mw,0xC8BAD5);text(g,tr("start"),leftPos+rightX+mw+3,topPos+77,51,0xC8BAD5);text(g,tr("duration"),leftPos+rightX+mw+57,topPos+77,51,0xC8BAD5);text(g,Component.literal((actionPage+1)+""),leftPos+imageWidth-59,topPos+timelineY+5,23,0xC8BAD5);
            int cx=leftPos+rightX,cy=topPos+timelineY+33,cw=rightWidth,ch=Math.max(18,imageHeight-timelineY-64),length=Math.max(20,current.length());g.fill(cx,cy,cx+cw,cy+ch,0xFF191923);
            for(int t=0;t<=4;t++){int x=cx+t*cw/4;g.fill(x,cy,x+1,cy+ch,0xFF464151);text(g,Component.literal(seconds(length*t/4.)+"s"),Math.min(x,cx+cw-27),cy-10,30,0xB4A8C0);}
            for(int i=0;i<actionSlots;i++){int n=actionPage*actionSlots+i;if(n>=current.actions().size())break;var a=current.actions().get(n);var target=target(a.module());int pitch=Math.max(3,(ch-5)/actionSlots),y=cy+3+i*pitch,x=cx+(int)(cw*a.start()/(double)length),end=cx+(int)(cw*a.end()/(double)length);g.fill(x,y,Math.max(x+2,end),y+Math.max(2,pitch-2),target==null?0xFF777777:ModuleCategory.of(target.type()).color);}
            if(runtime.hasUUID("Current")&&runtime.getUUID("Current").equals(selected)){int x=cx+(int)(cw*Math.min(1,runtime.getInt("Elapsed")/(double)length));g.fill(x,cy,x+1,cy+ch,0xFFFFFFFF);}
        }
        text(g,moveStatus(current),leftPos+rightX,topPos+imageHeight-15,statusHelp.visible?rightWidth-18:rightWidth,0x81DDAE);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);renderTooltip(g,x,y);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==257||key==335){if(name.isFocused()){send("name",0,name.getValue());name.setFocused(false);return true;}for(int i=0;i<starts.size();i++){if(starts.get(i).isFocused()){applyAction(i,false);return true;}if(durations.get(i).isFocused()){applyAction(i,true);return true;}}for(int i=0;i<ruleInputs.size();i++)if(ruleInputs.get(i).isFocused()){applyRule(i);return true;}}
        if(getFocused() instanceof EditBox box&&box.isFocused()&&key!=256)return box.keyPressed(key,scan,modifiers);return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(vertical==0)return super.mouseScrolled(x,y,horizontal,vertical);
        for(int i=0;i<starts.size();i++)for(boolean duration:new boolean[]{false,true}){var box=duration?durations.get(i):starts.get(i);if(box.visible&&box.active&&box.isMouseOver(x,y)){try{box.setValue(String.format(Locale.ROOT,"%.2f",Math.clamp(Double.parseDouble(box.getValue())+Math.signum(vertical)*.05,duration?.05:0,duration?600:599.95)));applyAction(i,duration);}catch(NumberFormatException ignored){}return true;}}
        for(int i=0;i<ruleInputs.size();i++){var box=ruleInputs.get(i);int n=rulePage*ruleSlots+i;if(n<ruleFields.size()&&box.visible&&box.active&&box.isMouseOver(x,y)){var f=ruleFields.get(n);try{box.setValue(String.format(Locale.ROOT,"%.2f",Math.clamp(Double.parseDouble(box.getValue())+Math.signum(vertical)*NumericWheelSteps.module("move_set",f.key(),f.scale(),f.min()),f.min()/f.scale(),f.max()/f.scale())));applyRule(i);}catch(NumberFormatException ignored){}return true;}}
        if(x<leftPos+leftWidth){movePage=Math.clamp(movePage+(vertical>0?-1:1),0,Math.max(0,(filtered().size()-1)/moveSlots));return true;}return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void onClose(){minecraft.setScreen(new HeartDashboardScreen(menu,minecraft.player.getInventory(),title));}
}
