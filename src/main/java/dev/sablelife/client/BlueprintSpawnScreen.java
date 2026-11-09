package dev.sablelife.client;

import java.util.*;
import dev.sablelife.module.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-local file selection and metadata only. Never uploads or downloads template payloads. */
public final class BlueprintSpawnScreen extends Screen {
    private final Screen parent;private final HeartDashboardMenu menu;private final UUID module;
    private int x,y,w,h,capacity,page;private String draft="",signature="";private boolean requested;
    public BlueprintSpawnScreen(Screen parent,HeartDashboardMenu menu,UUID module){super(tr("title"));this.parent=parent;this.menu=menu;this.module=module;}
    private static Component tr(String key,Object... args){return Component.translatable("blueprint_spawn.sablelife."+key,args);}
    private void send(String key,String text){PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,menu.snapshot().getInt("Revision"),module,"blueprint_spawn",key,0,text));}
    private CompoundTag data(){for(var value:menu.snapshot().getList("Modules",10)){var t=(CompoundTag)value;if(t.hasUUID("Id")&&t.getUUID("Id").equals(module))return t.getCompound("BlueprintSpawn");}return new CompoundTag();}
    private List<String> files(){return menu.snapshot().getList("BlueprintFiles",8).stream().map(t->t.getAsString()).toList();}
    private Button button(Component text,int px,int py,int width,Runnable run){return addRenderableWidget(LifeUiStyle.button(text,px,py,width,20,run));}
    @Override protected void init(){w=Math.min(480,width-16);h=Math.min(330,height-16);x=(width-w)/2;y=(height-h)/2;capacity=Math.max(1,(h-176)/24);page=Math.clamp(page,0,Math.max(0,(files().size()-1)/capacity));boolean editable=menu.snapshot().getBoolean("Editable")&&menu.snapshot().getBoolean("RespawnAllowed")&&data().getBoolean("Available");
        button(Component.translatable("dashboard.sablelife.back"),x+w-70,y+8,60,this::onClose);
        var input=addRenderableWidget(new EditBox(font,x+10,y+35,w-160,20,tr("name")));input.setMaxLength(80);input.setHint(tr("name"));input.setValue(draft);input.setResponder(s->draft=s);input.active=editable;
        button(tr("load"),x+w-144,y+35,64,()->send("load",draft)).active=editable;
        button(tr("clear"),x+w-74,y+35,64,()->send("clear","")).active=editable&&!data().getString("Name").isEmpty();
        var files=files();for(int i=0;i<capacity;i++){int at=page*capacity+i;if(at>=files.size())break;String name=files.get(at);button(Component.literal(name),x+10,y+119+i*24,w-20,()->{draft=name;send("load",name);}).active=editable;}
        button(Component.literal("<"),x+10,y+h-49,28,()->{page--;rebuildWidgets();}).active=page>0;
        button(Component.literal(">"),x+w-38,y+h-49,28,()->{page++;rebuildWidgets();}).active=(page+1)*capacity<files.size();
        signature=data().toString()+files+menu.snapshot().getBoolean("Editable");if(!requested){requested=true;send("list","");}
    }
    @Override public void tick(){if(minecraft.player!=null&&minecraft.player.containerMenu!=menu){minecraft.setScreen(null);return;}String now=data().toString()+files()+menu.snapshot().getBoolean("Editable");if(!now.equals(signature))rebuildWidgets();}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);g.fill(x-2,y-2,x+w+2,y+h+2,0xFF9B614E);g.fill(x,y,x+w,y+h,0xFF242632);var d=data();g.drawString(font,title,x+10,y+14,0xD9C4ED,false);g.drawString(font,font.plainSubstrByWidth(tr("selected",d.getString("Name")).getString(),w-20),x+10,y+64,0xD9C4ED,false);g.drawString(font,tr("cores",d.getInt("Life"),d.getInt("Redstone"),d.getInt("Blocks")),x+10,y+80,0xAEB8C7,false);g.drawString(font,font.plainSubstrByWidth(tr("state",tr("phase_"+(d.getString("Phase").isEmpty()?"empty":d.getString("Phase"))),d.getInt("Count")).getString(),w-20),x+10,y+97,0x79D3AE,false);g.drawString(font,font.plainSubstrByWidth(tr("position",d.getString("Position")).getString(),w-20),x+10,y+h-65,0xAEB8C7,false);g.drawCenteredString(font,(page+1)+" / "+Math.max(1,(files().size()+capacity-1)/capacity),x+w/2,y+h-43,0xD9C4ED);g.drawString(font,font.plainSubstrByWidth(tr(d.getBoolean("Available")?"directory":"requires").getString(),w-20),x+10,y+h-20,0xAEB8C7,false);for(var widget:renderables)widget.render(g,mx,my,partial);}
}
