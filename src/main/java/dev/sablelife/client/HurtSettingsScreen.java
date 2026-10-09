package dev.sablelife.client;

import dev.sablelife.module.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Uses the dashboard container and server revision checks, without opening another inventory. */
public final class HurtSettingsScreen extends AbstractContainerScreen<HeartDashboardMenu> {
    private final UUID id;
    private VirtualModule module;
    private boolean editable;
    private EditBox cooldown,duration;
    private Button mode,cooldownApply,durationApply,reset;
    public HurtSettingsScreen(HeartDashboardMenu menu,Inventory inventory,Component title,UUID id) { super(menu,inventory,title);this.id=id; }
    private Component tr(String key) { return Component.translatable("hurt.sablelife."+key); }
    private Button button(Component label,int x,int y,int width,Runnable action) { return addRenderableWidget(LifeUiStyle.button(label,leftPos+x,topPos+y,width,18,action)); }
    private void send(String operation,String key,double value) { PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,menu.snapshot().getInt("Revision"),id,operation,key,value,"")); }
    @Override protected void init() {
        imageWidth=Math.min(330,width-12);imageHeight=190;super.init();
        button(tr("back"),imageWidth-62,9,52,this::onClose);
        mode=button(Component.empty(),imageWidth/2,43,imageWidth/2-10,()->cycle(1));LifeUiStyle.reverse(mode,()->cycle(-1));
        cooldown=addRenderableWidget(new EditBox(font,leftPos+imageWidth/2,topPos+72,imageWidth/2-42,16,tr("cooldown")));
        duration=addRenderableWidget(new EditBox(font,leftPos+imageWidth/2,topPos+101,imageWidth/2-42,16,tr("duration")));
        for(var b:List.of(cooldown,duration)) { b.setMaxLength(7);b.setFilter(s->s.matches("[0-9]{0,3}(\\.[0-9]{0,2})?")); }
        cooldownApply=button(Component.literal("✓"),imageWidth-36,71,26,()->apply(cooldown,"hurtCooldown",0));
        durationApply=button(Component.literal("✓"),imageWidth-36,100,26,()->apply(duration,"hurtDuration",.05));
        reset=button(tr("reset"),10,133,imageWidth-20,()->send("hurt_reset","",0));sync();
    }
    private void cycle(int direction) { if(module!=null&&editable) send("edit","hurtMode",Math.floorMod((int)module.value("hurtMode")+direction,3)); }
    private void apply(EditBox box,String key,double min) {
        try { double value=Double.parseDouble(box.getValue()); if(!Double.isFinite(value)||value<min||value>600) {box.setTextColor(0xFF7777);return;}send("edit",key,value*20);box.setFocused(false); } catch(NumberFormatException ignored) {box.setTextColor(0xFF7777);}
    }
    private void sync() {
        var snapshot=menu.snapshot();editable=snapshot.getBoolean("Editable");module=null;
        for(var tag:snapshot.getList("Modules",10)) { var candidate=VirtualModule.load((net.minecraft.nbt.CompoundTag)tag);if(candidate.id().equals(id))module=candidate; }
        boolean valid=module!=null&&module.value("trigger")==5;
        mode.active=cooldownApply.active=cooldown.active=valid&&editable; cooldown.setEditable(valid&&editable);
        durationApply.active=valid&&editable&&module.value("hurtMode")==1;duration.setEditable(durationApply.active);duration.active=durationApply.active;
        reset.visible=valid&&module.value("hurtMode")==2;reset.active=reset.visible;
        if(valid) { mode.setMessage(tr("mode_"+(int)module.value("hurtMode")));if(!cooldown.isFocused())cooldown.setValue(String.format(Locale.ROOT,"%.2f",module.value("hurtCooldown")/20));if(!duration.isFocused())duration.setValue(String.format(Locale.ROOT,"%.2f",module.value("hurtDuration")/20)); }
        for(var b:List.of(mode,cooldownApply,durationApply,reset))b.setTooltip(Tooltip.create(tr("help")));
    }
    @Override protected void containerTick() {sync();}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(vertical!=0&&editable&&module!=null)for(var box:List.of(cooldown,duration))if(box.active&&box.isMouseOver(x,y)){
            boolean isDuration=box==duration;String key=isDuration?"hurtDuration":"hurtCooldown";double value;
            try{value=Double.parseDouble(box.getValue());}catch(NumberFormatException ignored){value=module.value(key)/20;}
            box.setValue(String.format(Locale.ROOT,"%.2f",Math.clamp(Math.round((value+Math.signum(vertical)*.1)*100)/100.,isDuration?.05:0,600)));apply(box,key,isDuration?.05:0);box.setFocused(true);return true;
        }return super.mouseScrolled(x,y,horizontal,vertical);
    }
    private void text(GuiGraphics g,Component text,int x,int y,int width,int color) {float scale=Math.min(LifeUiStyle.scale(),width/(float)Math.max(1,font.width(text)));g.pose().pushPose();g.pose().translate(leftPos+x,topPos+y,0);g.pose().scale(scale,scale,1);g.drawString(font,text,0,0,color,false);g.pose().popPose();}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        g.fill(leftPos-2,topPos-2,leftPos+imageWidth+2,topPos+imageHeight+2,0xFF92634D);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF242432);
        text(g,tr("title"),10,14,imageWidth-82,0xD9C4ED);text(g,tr("response"),10,48,imageWidth/2-18,0xD9C4ED);text(g,tr("cooldown"),10,76,imageWidth/2-18,0xD9C4ED);text(g,tr("duration"),10,105,imageWidth/2-18,0xD9C4ED);
        var runtime=menu.snapshot().getList("Modules",10);boolean active=false;int cd=0;
        for(var tag:runtime) {var t=(net.minecraft.nbt.CompoundTag)tag;if(t.hasUUID("Id")&&t.getUUID("Id").equals(id)){active=t.getCompound("HurtRuntime").getBoolean("Active");cd=t.getCompound("HurtRuntime").getInt("Cooldown");}}
        text(g,Component.translatable("hurt.sablelife.state",tr(active?"active":"waiting"),String.format(Locale.ROOT,"%.2f",cd/20.)),10,167,imageWidth-20,0x8FD5B5);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y) {}
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
    @Override public void onClose() {minecraft.setScreen(new HeartDashboardScreen(menu,minecraft.player.getInventory(),title));}
}
