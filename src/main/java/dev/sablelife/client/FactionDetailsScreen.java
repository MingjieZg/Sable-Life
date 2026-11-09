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

/** Inspect one server-owned faction. Only snapshots determine permissions and membership. */
public final class FactionDetailsScreen extends Screen {
    private final Screen parent;
    private final HeartSettingsMenu menu;
    private final UUID id;
    private int x,y,w,h,tab,page,capacity=1;
    private String draft="",signature="",armed="";
    private long armedUntil;
    private EditBox input;
    public FactionDetailsScreen(Screen parent,HeartSettingsMenu menu,UUID id){super(tr("details"));this.parent=parent;this.menu=menu;this.id=id;}
    private static Component tr(String key,Object... args){return Component.translatable("relations.sablelife."+key,args);}
    private CompoundTag detail(){var t=menu.snapshot().getCompound("FactionDetail");return t.hasUUID("Id")&&id.equals(t.getUUID("Id"))?t:new CompoundTag();}
    private void send(String action,String value){PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,0,new UUID(0,0),"core","relation:"+action,0,value));}
    private void own(String action){send(action,id.toString());}
    private void pair(String action,UUID other){send(action,id+"|"+other);}
    private Button button(Component label,int px,int py,int width,Runnable action){return addRenderableWidget(LifeUiStyle.button(label,px,py,width,18,action));}
    private void confirm(String key,Runnable action){if(armed.equals(key)&&armedUntil>net.minecraft.Util.getMillis()){armed="";armedUntil=0;action.run();}else{armed=key;armedUntil=net.minecraft.Util.getMillis()+3000;}rebuildWidgets();}
    private Component confirmation(String key,String fallback){return tr(armed.equals(key)&&armedUntil>net.minecraft.Util.getMillis()?"confirm_action":fallback);}
    private void switchTab(int value){tab=value;page=0;armed="";rebuildWidgets();}
    private List<CompoundTag> rows(){var d=detail();var result=new ArrayList<CompoundTag>();if(tab==1){for(var v:d.getList("Members",10))result.add((CompoundTag)v);for(var v:d.getList("Invitations",10)){var r=((CompoundTag)v).copy();r.putBoolean("Pending",true);result.add(r);}}else if(tab==2)for(var v:d.getList("Alliances",10))result.add((CompoundTag)v);return result;}
    @Override protected void init(){
        w=Math.min(620,width-16);h=Math.min(370,height-16);x=(width-w)/2;y=(height-h)/2;input=null;
        var d=detail();boolean ready=d.hasUUID("Id"),manage=d.getBoolean("Manage"),member=d.getBoolean("Member"),invited=d.getBoolean("Invited");
        button(Component.translatable("dashboard.sablelife.back"),x+w-70,y+8,60,this::onClose);
        int tw=(w-28)/3;for(int i=0;i<3;i++){final int at=i;var b=button(tr(new String[]{"overview","members","alliances"}[i]),x+10+i*(tw+4),y+35,tw,()->switchTab(at));if(tab==i)LifeUiStyle.bookmark(b,0xFFD9C4ED);}
        if(tab==0&&ready){
            var privacy=button(tr(d.getBoolean("Private")?"private_mode":"public_mode"),x+10,y+65,(w-26)/2,()->own("privacy"));privacy.active=manage;privacy.setTooltip(Tooltip.create(tr("privacy_help")));
            if(invited){button(tr("accept_invite"),x+10,y+111,(w-26)/2,()->own("accept"));button(tr("decline_invite"),x+16+(w-26)/2,y+111,(w-26)/2,()->own("decline"));}
            {boolean owner=d.hasUUID("Owner")&&minecraft.player!=null&&d.getUUID("Owner").equals(minecraft.player.getUUID());var join=button(tr(member?"leave_player":"join_player"),x+16+(w-26)/2,y+65,(w-26)/2,()->{if(member)confirm("leave",()->own("leavePlayer"));else own("joinPlayer");});if(member)join.setMessage(confirmation("leave","leave_player"));join.active=member?!owner:d.getBoolean("CanJoin");join.setTooltip(Tooltip.create(tr("membership_help")));}
            var ff=button(tr(d.getBoolean("FriendlyFire")?"ff_on":"ff_off"),x+10,y+88,(w-26)/2,()->own("friendlyFire"));ff.active=manage;
            var aff=button(tr(d.getBoolean("AlliedFire")?"allied_fire_on":"allied_fire_off"),x+16+(w-26)/2,y+88,(w-26)/2,()->own("alliedFire"));aff.active=manage;aff.setTooltip(Tooltip.create(tr("allied_fire_help")));
            if(manage){input=addRenderableWidget(new EditBox(font,x+10,y+111,w-100,18,tr("invite_name")));input.setMaxLength(16);input.setValue(draft);input.setHint(tr("invite_name"));input.setResponder(s->draft=s);button(tr("invite"),x+w-84,y+111,74,this::invite);}
        }
        var rows=rows();int stride=20;capacity=Math.max(1,(h-145)/stride);page=Math.clamp(page,0,Math.max(0,(rows.size()-1)/capacity));
        if(tab!=0)for(int i=0;i<capacity&&page*capacity+i<rows.size();i++){
            var row=rows.get(page*capacity+i);UUID other=row.getUUID("Id");int py=y+65+i*stride;boolean pending=row.getBoolean("Pending"),allied=row.getBoolean("Allied"),incoming=row.getBoolean("Incoming"),outgoing=row.getBoolean("Outgoing");
            String suffix=tab==1?(pending?"pending_invite":"member_label"):allied?"allied_label":incoming?"incoming_label":outgoing?"outgoing_label":"unrelated_label";
            var label=Component.literal(row.getString("Name")).append("  ").append(tr(suffix));
            var name=button(label,x+10,py,w-(tab==1?110:210),()->{});name.active=false;name.setTooltip(Tooltip.create(label));
            if(tab==1){boolean owner=d.hasUUID("Owner")&&d.getUUID("Owner").equals(other);var remove=button(confirmation(other.toString(),pending?"revoke":"remove_member"),x+w-94,py,84,()->confirm(other.toString(),()->pair(pending?"revoke":"removeMember",other)));remove.active=manage&&!owner;}
            else{
                String action=allied?"allied_label":incoming?"accept_alliance":outgoing?"outgoing_label":"request_alliance";
                var b=button(tr(action),x+w-194,py,110,()->pair(incoming?"acceptAlliance":"requestAlliance",other));b.active=manage&&!allied&&!outgoing;
                if(allied)b.setTooltip(Tooltip.create(tr(row.getBoolean("DamageEnabled")?"allied_damage_on":"allied_damage_off")));
                var cancel=button(confirmation(other.toString(),allied?"end_alliance":"cancel_request"),x+w-80,py,70,()->confirm(other.toString(),()->pair("endAlliance",other)));cancel.active=manage&&(allied||incoming||outgoing);
                if(incoming&&!allied){LifeUiStyle.fill(b,0xFF24583D);LifeUiStyle.fill(cancel,0xFF7A3038);}
            }
        }
        if(tab!=0){button(Component.literal("<"),x+10,y+h-65,30,()->{page--;rebuildWidgets();}).active=page>0;button(Component.literal(">"),x+w-40,y+h-65,30,()->{page++;rebuildWidgets();}).active=(page+1)*capacity<rows.size();}
        signature=detail().toString()+menu.snapshot().getString("RelationStatus");
    }
    private void invite(){if(!draft.isBlank())send("invite",id+"|"+draft);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(input!=null&&input.isFocused()&&(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER||key==org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)){invite();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void tick(){if(minecraft.player!=null&&minecraft.player.containerMenu!=menu){minecraft.setScreen(null);return;}if(!signature.equals(detail().toString()+menu.snapshot().getString("RelationStatus")))rebuildWidgets();if(armedUntil!=0&&armedUntil<=net.minecraft.Util.getMillis()){armed="";armedUntil=0;rebuildWidgets();}}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        super.render(g,mx,my,partial);g.fill(x-2,y-2,x+w+2,y+h+2,0xFF9B614E);g.fill(x,y,x+w,y+h,0xFF242632);
        g.drawString(font,font.plainSubstrByWidth(detail().getString("Name"),w-90),x+10,y+14,0xD9C4ED,false);
        String status=menu.snapshot().getString("RelationStatus");
        if(tab==0)g.drawWordWrap(font,tr("membership_summary"),x+10,y+140,w-20,0xAEB8C7);
        else g.drawCenteredString(font,(page+1)+" / "+Math.max(1,(rows().size()+capacity-1)/capacity),x+w/2,y+h-60,0xD9C4ED);
        g.drawWordWrap(font,tr(status.isEmpty()?tab==2?"alliance_help":tab==1?"members_help":"membership_note":status),x+10,y+h-40,w-20,0xAEB8C7);
        for(var widget:renderables)widget.render(g,mx,my,partial);
    }
}
