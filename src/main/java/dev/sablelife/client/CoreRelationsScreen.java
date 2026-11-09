package dev.sablelife.client;

import java.util.*;
import dev.sablelife.heart.HeartSettingsMenu;
import dev.sablelife.module.ModuleNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-owned whitelist and faction editor; membership never changes optimistically. */
public final class CoreRelationsScreen extends Screen {
    private final Screen parent;
    private final HeartSettingsMenu menu;
    private boolean factions;
    private int page,x,y,w,h,capacity,noteHeight;
    private String draft="",playerDraft="",signature="";
    private long deleteUntil;
    private UUID deleteTarget;
    private Button deleteButton;
    private EditBox playerInput,factionInput;
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
    private String signature(){return menu.snapshot().getCompound("Relations").toString()+menu.snapshot().getList("Factions",10)+menu.snapshot().getString("RelationStatus");}
    private void tab(boolean value){factions=value;page=0;deleteUntil=0;deleteTarget=null;rebuildWidgets();}
    private void deleteSelected(UUID id){
        if(id==null)return;
        if(id.equals(deleteTarget)&&deleteUntil>net.minecraft.Util.getMillis()){send("delete",id.toString());deleteUntil=0;deleteTarget=null;}
        else{deleteTarget=id;deleteUntil=net.minecraft.Util.getMillis()+3000;}
        rebuildWidgets();
    }
    @Override protected void init(){
        w=Math.min(620,width-16);h=Math.min(370,height-16);x=(width-w)/2;y=(height-h)/2;selectedButtons.clear();deleteButton=null;playerInput=null;factionInput=null;
        noteHeight=Math.max(2,font.split(tr(factions?"faction_note":"player_note"),w-20).size())*font.lineHeight+4;
        int rowHeight=Math.max(12,Math.round(18*LifeUiStyle.scale())),stride=rowHeight+2,gridRows=Math.max(1,(h-noteHeight-119)/stride),columns=factions?1:5;
        capacity=gridRows*columns;var rows=rows();page=Math.clamp(page,0,Math.max(0,(rows.size()-1)/capacity));
        button(Component.translatable("dashboard.sablelife.back"),x+w-70,y+8,60,18,this::onClose);
        button(tr("players"),x+10,y+34,(w-26)/2,18,()->tab(false));
        button(tr("factions"),x+16+(w-26)/2,y+34,(w-26)/2,18,()->tab(true));
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
    @Override public void tick(){
        if(minecraft.player!=null&&minecraft.player.containerMenu!=menu){minecraft.setScreen(null);return;}
        if(!signature.equals(signature()))rebuildWidgets();
        if(deleteUntil!=0&&deleteUntil<=net.minecraft.Util.getMillis()){deleteUntil=0;deleteTarget=null;if(deleteButton!=null)deleteButton.setMessage(tr("delete"));}
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
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
        g.drawCenteredString(font,(page+1)+" / "+Math.max(1,(rows().size()+capacity-1)/capacity),x+w/2,y+h-noteHeight-19,0xD9C4ED);
        String status=menu.snapshot().getString("RelationStatus");var note=tr(!status.isEmpty()?status:factions?"faction_note":"player_note",menu.snapshot().getInt("FactionCreationLimit"));
        int noteColor=status.startsWith("faction_")||status.equals("denied")||status.equals("invalid_name")?0xEF8D8D:status.equals("updated")||status.equals("player_added")?0x89DFA8:0xAEB8C7;
        g.drawWordWrap(font,note,x+10,y+h-noteHeight,w-20,noteColor);
        for(var widget:renderables)widget.render(g,mx,my,partial);
        for(var b:selectedButtons){int bx=b.getX(),by=b.getY(),bw=b.getWidth(),bh=b.getHeight(),color=0xFFD9C4ED;g.fill(bx,by,bx+bw,by+1,color);g.fill(bx,by+bh-1,bx+bw,by+bh,color);g.fill(bx,by,bx+1,by+bh,color);g.fill(bx+bw-1,by,bx+bw,by+bh,color);}
    }
}
