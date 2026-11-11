package dev.sablelife.client;

import java.util.*;
import dev.sablelife.heart.HeartSettingsMenu;
import dev.sablelife.heart.CoreRelations;
import dev.sablelife.module.ModuleNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-owned whitelist and faction editor; membership never changes optimistically. */
public final class CoreRelationsScreen extends Screen {
    private final Screen parent;
    private final HeartSettingsMenu menu;
    private boolean factions,mobs;
    private int page,x,y,w,h,capacity,noteHeight;
    private String draft="",playerDraft="",signature="";
    private long deleteUntil;
    private UUID deleteTarget;
    private Button deleteButton;
    private EditBox playerInput,factionInput;
    private EditBox mobSearch;
    private String mobQuery="",leftMob="",rightMob="";
    private int leftMobPage,rightMobPage,mobRows,paneWidth;
    private List<String> mobCatalog=List.of(),availableMobs=List.of(),excludedMobs=List.of();
    private final List<Button> availableButtons=new ArrayList<>(),excludedButtons=new ArrayList<>();
    private Button addMob,removeMob,leftMobPrev,leftMobNext,rightMobPrev,rightMobNext;
    private final List<Button> selectedButtons=new ArrayList<>();
    public CoreRelationsScreen(Screen parent,HeartSettingsMenu menu){super(tr("title"));this.parent=parent;this.menu=menu;}
    private static Component tr(String key,Object... args){return Component.translatable("relations.sablelife."+key,args);}
    private void send(String key,String text){PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,0,new UUID(0,0),"core","relation:"+key,0,text));}
    private Button button(Component label,int px,int py,int width,int height,Runnable action){return addRenderableWidget(LifeUiStyle.button(label,px,py,width,height,action));}
    private List<CompoundTag> rows(){
        var s=menu.snapshot();
        if(factions){var result=new ArrayList<CompoundTag>();var none=new CompoundTag();none.putString("Name",tr("none").getString());result.add(none);for(var row:s.getList("Factions",10))result.add((CompoundTag)row);return result;}
        var all=new LinkedHashMap<String,CompoundTag>();
        for(var t:s.getCompound("Relations").getList("ExcludedNames",8)){String name=t.getAsString();var r=new CompoundTag();r.putString("Name",name);all.put(name.toLowerCase(Locale.ROOT),r);}
        return List.copyOf(all.values());
    }
    private UUID membership(){var r=menu.snapshot().getCompound("Relations");if(!r.hasUUID("Faction"))return null;var id=r.getUUID("Faction");return dev.sablelife.heart.FactionRegistry.WILDS.equals(id)||menu.snapshot().getList("Factions",10).stream().map(t->(CompoundTag)t).anyMatch(t->t.hasUUID("Id")&&id.equals(t.getUUID("Id")))?id:null;}
    private String signature(){return menu.snapshot().getCompound("Relations").toString()+menu.snapshot().getList("Factions",10)+menu.snapshot().getString("RelationStatus")+menu.snapshot().getBoolean("LiveControls");}
    private void tab(int value){factions=value==2;mobs=value==1;page=0;deleteUntil=0;deleteTarget=null;rebuildWidgets();}
    private void deleteSelected(UUID id){
        if(id==null)return;
        if(id.equals(deleteTarget)&&deleteUntil>net.minecraft.Util.getMillis()){send("delete",id.toString());deleteUntil=0;deleteTarget=null;}
        else{deleteTarget=id;deleteUntil=net.minecraft.Util.getMillis()+3000;}
        rebuildWidgets();
    }
    @Override protected void init(){
        w=Math.min(620,width-16);h=Math.min(370,height-16);x=(width-w)/2;y=(height-h)/2;selectedButtons.clear();deleteButton=null;playerInput=null;factionInput=null;mobSearch=null;
        noteHeight=Math.max(2,font.split(tr(mobs?"mob_note":factions?"faction_note":"player_note"),w-20).size())*font.lineHeight+4;
        int rowHeight=Math.max(12,Math.round(18*LifeUiStyle.scale())),stride=rowHeight+2,gridRows=Math.max(1,(h-noteHeight-119)/stride),columns=factions?1:5;
        capacity=gridRows*columns;var rows=rows();page=Math.clamp(page,0,Math.max(0,(rows.size()-1)/capacity));
        button(Component.translatable("dashboard.sablelife.back"),x+w-70,y+8,60,18,this::onClose);
        int tabWidth=(w-32)/3;
        for(int i=0;i<3;i++){int index=i;var b=button(tr(List.of("players","mobs","factions").get(i)),x+10+i*(tabWidth+6),y+34,tabWidth,18,()->tab(index));if(i==(factions?2:mobs?1:0))selectedButtons.add(b);}
        if(mobs){initMobs(rowHeight);signature=signature();return;}
        var own=membership();
        if(factions){
            var input=addRenderableWidget(new EditBox(font,x+10,y+60,w-150,18,tr("name")));factionInput=input;input.setMaxLength(40);input.setValue(draft);input.setHint(tr("name"));input.setResponder(s->draft=s);
            button(tr("create"),x+w-134,y+60,60,18,()->send("create",draft));
            deleteButton=button(tr(deleteUntil>net.minecraft.Util.getMillis()&&Objects.equals(own,deleteTarget)?"confirm_delete":"delete"),x+w-70,y+60,60,18,()->deleteSelected(own));
            deleteButton.active=own!=null&&rows.stream().anyMatch(r->r.hasUUID("Id")&&own.equals(r.getUUID("Id"))&&r.getBoolean("Manage"));deleteButton.setTooltip(Tooltip.create(tr("delete_help")));
        }else{
            var input=addRenderableWidget(new EditBox(font,x+10,y+60,w-90,18,tr("player_name")));playerInput=input;input.setMaxLength(16);input.setValue(playerDraft);input.setHint(tr("player_name"));input.setResponder(s->playerDraft=s);
            button(tr("exclude_add"),x+w-74,y+60,64,18,()->send("excludeName",playerDraft));
        }
        int cellWidth=(w-20-(columns-1)*3)/columns;
        for(int i=0;i<capacity;i++){
            int at=page*capacity+i;if(at>=rows.size())break;var r=rows.get(at);int px=x+10+(i/gridRows)*(cellWidth+3),py=y+87+(i%gridRows)*stride;
            if(factions){
                UUID id=r.hasUUID("Id")?r.getUUID("Id"):null;
                String creator=r.getString("CreatorName");
                var attribution=tr("created_by",creator.isEmpty()?tr("unknown_creator"):Component.literal(creator));
                var joined=id!=null&&id.equals(own)?tr("joined").copy().withStyle(net.minecraft.ChatFormatting.GREEN).append(" "):Component.empty();
                var visibility=id==null?Component.empty():FactionLabels.visibility(r);
                var invitation=r.getBoolean("Invited")?tr("invitation_badge").copy().withStyle(net.minecraft.ChatFormatting.AQUA):Component.empty();
                var fullLabel=Component.literal(r.getString("Name")).append(visibility).append(invitation).append(id==null?Component.empty():Component.literal("    ").append(attribution));
                int available=w-220;String suffix=font.plainSubstrByWidth(attribution.getString(),available/3);
                int nameWidth=Math.max(12,available-font.width(joined)-font.width(suffix)-font.width("    ")-font.width(visibility)-font.width(invitation));
                var label=id==null?Component.literal(r.getString("Name")):Component.empty().append(joined)
                        .append(font.plainSubstrByWidth(r.getString("Name"),nameWidth)).append("    ")
                        .append(Component.literal(suffix).withStyle(net.minecraft.ChatFormatting.GRAY));
                if(id!=null)label.append(visibility).append(invitation);
                var b=button(label,x+10,py,w-210,rowHeight,()->{deleteUntil=0;deleteTarget=null;send(id==null?"leave":"join",id==null?"":id.toString());});
                b.active=id==null||r.getBoolean("CanJoin");
                if(Objects.equals(own,id))selectedButtons.add(b);
                b.setTooltip(Tooltip.create(fullLabel.copy().append("\n").append(tr("join_help")).append(id==null?"":"\n"+id)));
                if(id!=null){button(tr("details"),x+w-195,py,72,rowHeight,()->{send("select",id.toString());minecraft.setScreen(new FactionDetailsScreen(this,menu,id));});var ff=button(tr(r.getBoolean("FriendlyFire")?"ff_on":"ff_off"),x+w-117,py,107,rowHeight,()->send("friendlyFire",id.toString()));ff.active=r.getBoolean("Manage");}
            }else{
                String name=r.getString("Name");var label=button(Component.literal(name),px,py,cellWidth-16,rowHeight,()->{});label.setTooltip(Tooltip.create(Component.literal(name).append("\n").append(tr("exclude_help"))));
                var remove=button(Component.literal("X").withStyle(net.minecraft.ChatFormatting.RED),px+cellWidth-15,py,15,rowHeight,()->send("removeName",name));remove.setTooltip(Tooltip.create(tr("remove_player",name)));
            }
        }
        button(Component.literal("<"),x+10,y+h-noteHeight-24,30,18,()->{page--;rebuildWidgets();}).active=page>0;
        button(Component.literal(">"),x+w-40,y+h-noteHeight-24,30,18,()->{page++;rebuildWidgets();}).active=(page+1)*capacity<rows.size();
        if(!factions&&rows.isEmpty()){var empty=button(tr("empty_players"),x+10,y+87,w-20,rowHeight,()->{});empty.active=false;}
        signature=signature();
    }
    private Component mobName(String id){var location=ResourceLocation.tryParse(id);return location!=null&&BuiltInRegistries.ENTITY_TYPE.containsKey(location)?BuiltInRegistries.ENTITY_TYPE.get(location).getDescription():Component.literal(id);}
    private void initMobs(int rowHeight){
        availableButtons.clear();excludedButtons.clear();paneWidth=(w-52)/2;mobRows=Math.max(1,(h-noteHeight-119)/(rowHeight+2));
        if(mobCatalog.isEmpty())mobCatalog=CoreRelations.mobTypes().stream().map(Object::toString).sorted(Comparator.<String,String>comparing(id->mobName(id).getString().toLowerCase(Locale.ROOT)).thenComparing(id->id)).toList();
        mobSearch=addRenderableWidget(new EditBox(font,x+10,y+60,paneWidth,18,tr("mob_search")));mobSearch.setMaxLength(128);mobSearch.setHint(tr("mob_search"));mobSearch.setValue(mobQuery);
        mobSearch.setResponder(s->{mobQuery=s;leftMobPage=0;syncMobs();});
        int right=x+42+paneWidth;
        for(int i=0;i<mobRows;i++){
            int index=i,py=y+87+i*(rowHeight+2);
            availableButtons.add(button(Component.empty(),x+10,py,paneWidth,rowHeight,()->{int at=leftMobPage*mobRows+index;if(at<availableMobs.size())leftMob=availableMobs.get(at);syncMobs();}));
            excludedButtons.add(button(Component.empty(),right,py,paneWidth,rowHeight,()->{int at=rightMobPage*mobRows+index;if(at<excludedMobs.size())rightMob=excludedMobs.get(at);syncMobs();}));
        }
        int middle=x+14+paneWidth,center=y+87+Math.max(0,(mobRows*(rowHeight+2)-40)/2);
        addMob=button(Component.literal(">>"),middle,center,24,18,()->send("excludeMob",leftMob));addMob.setTooltip(Tooltip.create(tr("mob_add_help")));
        removeMob=button(Component.literal("<<"),middle,center+22,24,18,()->send("removeMob",rightMob));removeMob.setTooltip(Tooltip.create(tr("mob_remove_help")));
        int bottom=y+h-noteHeight-24;
        leftMobPrev=button(Component.literal("<"),x+10,bottom,26,18,()->{leftMobPage--;syncMobs();});
        leftMobNext=button(Component.literal(">"),x+10+paneWidth-26,bottom,26,18,()->{leftMobPage++;syncMobs();});
        rightMobPrev=button(Component.literal("<"),right,bottom,26,18,()->{rightMobPage--;syncMobs();});
        rightMobNext=button(Component.literal(">"),right+paneWidth-26,bottom,26,18,()->{rightMobPage++;syncMobs();});
        syncMobs();
    }
    private void syncMobs(){
        if(!mobs||addMob==null)return;
        excludedMobs=menu.snapshot().getCompound("Relations").getList("ExcludedMobs",8).stream().map(Tag::getAsString).distinct().toList();
        var excluded=new HashSet<>(excludedMobs);String query=mobQuery.strip().toLowerCase(Locale.ROOT);
        availableMobs=mobCatalog.stream().filter(id->!excluded.contains(id)&&(id.toLowerCase(Locale.ROOT).contains(query)||mobName(id).getString().toLowerCase(Locale.ROOT).contains(query))).toList();
        leftMobPage=Math.clamp(leftMobPage,0,Math.max(0,(availableMobs.size()-1)/mobRows));rightMobPage=Math.clamp(rightMobPage,0,Math.max(0,(excludedMobs.size()-1)/mobRows));
        selectedButtons.removeAll(availableButtons);selectedButtons.removeAll(excludedButtons);
        for(int side=0;side<2;side++){
            var ids=side==0?availableMobs:excludedMobs;var buttons=side==0?availableButtons:excludedButtons;int page=side==0?leftMobPage:rightMobPage;
            for(int i=0;i<buttons.size();i++){
                var b=buttons.get(i);int at=page*mobRows+i;b.visible=at<ids.size();if(!b.visible)continue;
                String id=ids.get(at);var name=mobName(id);b.setMessage(Component.literal(font.plainSubstrByWidth(name.getString(),paneWidth-10)));
                b.setTooltip(Tooltip.create(name.copy().append("\n").append(Component.literal(id).withStyle(net.minecraft.ChatFormatting.GRAY)).append("\n").append(tr(side==0?"mob_add_help":"mob_remove_help"))));
                if(id.equals(side==0?leftMob:rightMob))selectedButtons.add(b);
            }
        }
        boolean editable=menu.snapshot().getBoolean("LiveControls");addMob.active=editable&&excludedMobs.size()<CoreRelations.LIMIT&&availableMobs.contains(leftMob);removeMob.active=editable&&excludedMobs.contains(rightMob);
        leftMobPrev.active=leftMobPage>0;leftMobNext.active=(leftMobPage+1)*mobRows<availableMobs.size();rightMobPrev.active=rightMobPage>0;rightMobNext.active=(rightMobPage+1)*mobRows<excludedMobs.size();
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(mobs&&button==1&&menu.snapshot().getBoolean("LiveControls")){
            for(int i=0;i<mobRows;i++){
                if(availableButtons.get(i).visible&&availableButtons.get(i).isMouseOver(mx,my)){send("excludeMob",availableMobs.get(leftMobPage*mobRows+i));return true;}
                if(excludedButtons.get(i).visible&&excludedButtons.get(i).isMouseOver(mx,my)){send("removeMob",excludedMobs.get(rightMobPage*mobRows+i));return true;}
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public void tick(){
        if(minecraft.player!=null&&minecraft.player.containerMenu!=menu){minecraft.setScreen(null);return;}
        if(!signature.equals(signature()))rebuildWidgets();
        if(deleteUntil!=0&&deleteUntil<=net.minecraft.Util.getMillis()){deleteUntil=0;deleteTarget=null;if(deleteButton!=null)deleteButton.setMessage(tr("delete"));}
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(mobs&&mobSearch!=null&&mobSearch.isFocused()){
            if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER||key==org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER){if(addMob.active)send("excludeMob",leftMob);return true;}
            return key==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?super.keyPressed(key,scan,modifiers):mobSearch.keyPressed(key,scan,modifiers)||minecraft.options.keyInventory.matches(key,scan);
        }
        if(factions&&factionInput!=null&&factionInput.isFocused()&&(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER||key==org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)){
            send("create",draft);return true;
        }
        if(!factions&&playerInput!=null&&playerInput.isFocused()&&(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER||key==org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)){
            if(!playerDraft.isBlank())send("excludeName",playerDraft);
            return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        super.render(g,mx,my,partial);g.fill(x-2,y-2,x+w+2,y+h+2,0xFF9B614E);g.fill(x,y,x+w,y+h,0xFF242632);
        var titleText=font.plainSubstrByWidth(title.getString(),w-90);g.drawString(font,titleText,x+10,y+14,0xD9C4ED,false);
        if(mobs){
            int bottom=y+h-noteHeight-27,right=x+42+paneWidth;
            for(int px:new int[]{x+8,right-2}){int color=0xFF675C79;g.fill(px,y+57,px+paneWidth+4,y+58,color);g.fill(px,bottom,px+paneWidth+4,bottom+1,color);g.fill(px,y+57,px+1,bottom,color);g.fill(px+paneWidth+3,y+57,px+paneWidth+4,bottom,color);}
            g.drawString(font,tr("excluded_mobs"),right,y+65,0xD9C4ED,false);
            int py=y+h-noteHeight-19;g.drawCenteredString(font,(leftMobPage+1)+" / "+Math.max(1,(availableMobs.size()+mobRows-1)/mobRows),x+10+paneWidth/2,py,0xD9C4ED);g.drawCenteredString(font,(rightMobPage+1)+" / "+Math.max(1,(excludedMobs.size()+mobRows-1)/mobRows),right+paneWidth/2,py,0xD9C4ED);
            if(availableMobs.isEmpty())g.drawWordWrap(font,tr("no_mobs"),x+14,y+91,paneWidth-8,0xAEB8C7);
            if(excludedMobs.isEmpty())g.drawWordWrap(font,tr("empty_mobs"),right+4,y+91,paneWidth-8,0xAEB8C7);
        }else g.drawCenteredString(font,(page+1)+" / "+Math.max(1,(rows().size()+capacity-1)/capacity),x+w/2,y+h-noteHeight-19,0xD9C4ED);
        String status=menu.snapshot().getString("RelationStatus");var note=tr(!status.isEmpty()?status:mobs?"mob_note":factions?"faction_note":"player_note",menu.snapshot().getInt("FactionCreationLimit"));
        int noteColor=status.startsWith("faction_")||status.equals("denied")||status.equals("invalid_name")||status.equals("invalid_mob")?0xEF8D8D:status.equals("updated")||status.equals("player_added")||status.equals("mob_added")?0x89DFA8:0xAEB8C7;
        g.drawWordWrap(font,note,x+10,y+h-noteHeight,w-20,noteColor);
        for(var widget:renderables)widget.render(g,mx,my,partial);
        for(var b:selectedButtons){int bx=b.getX(),by=b.getY(),bw=b.getWidth(),bh=b.getHeight(),color=0xFFD9C4ED;g.fill(bx,by,bx+bw,by+1,color);g.fill(bx,by+bh-1,bx+bw,by+bh,color);g.fill(bx,by,bx+1,by+bh,color);g.fill(bx+bw-1,by,bx+bw,by+bh,color);}
    }
}
