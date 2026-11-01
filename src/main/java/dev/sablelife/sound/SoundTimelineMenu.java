package dev.sablelife.sound;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import java.util.*;

/** One validated editor shared by module events and core effects. */
public final class SoundTimelineMenu extends AbstractContainerMenu {
    private final HeartBlockEntity heart;private final UUID moduleId;
    private final String key,context;private final Player viewer;private final SimpleContainer data=new SimpleContainer(1);
    public SoundTimelineMenu(int id,Inventory inv){this(id,inv,null,null,"","");}
    public SoundTimelineMenu(int id,Inventory inv,HeartBlockEntity heart,UUID moduleId,String key,String context){
        super(SableLife.SOUND_TIMELINE_MENU.get(),id);this.heart=heart;this.moduleId=moduleId;this.key=key;this.context=context;viewer=inv.player;
        addSlot(new Slot(data,0,-10000,-10000){@Override public boolean mayPlace(ItemStack s){return false;}@Override public boolean mayPickup(Player p){return false;}@Override public boolean isActive(){return false;}});refresh();
    }
    public static boolean open(Player player,HeartBlockEntity heart,UUID moduleId,String key,String context){
        if(!heart.canAccessEditor(player))return false;
        if(heart.isControlCore()&&(!context.equals("module")||!dev.sablelife.heart.RedstoneCorePolicy.audioKeyAllowed(heart.modules().find(moduleId),key)))return false;
        var m=moduleId==null?null:heart.modules().find(moduleId);
        if(context.equals("module")?(m==null||!m.soundKey(key)):!Set.of("death","vanish").contains(context)||!key.equals("deathSound"))return false;
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,p)->new SoundTimelineMenu(id,inv,heart,moduleId,key,context),Component.translatable("sound_timeline.sablelife.title")));return true;
    }
    private VirtualModule module(){return heart==null||moduleId==null?null:heart.modules().find(moduleId);}
    private boolean editable(Player p){return heart!=null&&(context.equals("module")?heart.canEdit(p):heart.canToggleBreaking(p));}
    private SoundTimeline timeline(){
        if(context.equals("module"))return module().soundTimeline(key);
        return (context.equals("vanish")?heart.removalPresentation():heart.deathPresentation()).soundTimeline(key);
    }
    private void store(SoundTimeline t){
        if(context.equals("module")){module().soundTimeline(key,t);heart.modules().changed();}
        else (context.equals("vanish")?heart.removalPresentation():heart.deathPresentation()).soundTimeline(key,t);
        heart.setChanged();
    }
    public CompoundTag snapshot(){return data.getItem(0).getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private void refresh(){if(heart==null||context.equals("module")&&module()==null)return;
        var tag=timeline().save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",editable(viewer));tag.putString("Key",key);tag.putString("Context",context);tag.putBoolean("Music",key.equals("bgm"));
        var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));data.setItem(0,stack);
    }
    public boolean action(Player p,int revision,String operation,UUID row,double value,String sound){
        if(!stillValid(p)||!editable(p))return false;var timeline=timeline();if(revision!=timeline.revision()&&!operation.equals("add")){refresh();sendAllDataToRemote();return false;}
        boolean accepted=timeline.edit(operation,row,value,sound);if(accepted)store(timeline);refresh();sendAllDataToRemote();return accepted;
    }
    @Override public void broadcastChanges(){refresh();super.broadcastChanges();}
    @Override public boolean stillValid(Player p){
        if(heart==null)return true;
        var entity=heart;
        return heart.canAccessEditor(p)&&(! (heart!=null&&heart.isControlCore())||context.equals("module")&&dev.sablelife.heart.RedstoneCorePolicy.audioKeyAllowed(module(),key))&&!entity.isRemoved()&&p.level()==entity.getLevel()&&entity.getLevel().getBlockEntity(entity.getBlockPos())==entity
            &&(moduleId==null||module()!=null)&&dev.sablelife.compat.sable.SableBodyProbe.toWorld(p.level(),p.getEyePosition()).distanceToSqr(dev.sablelife.compat.sable.SableBodyProbe.toWorld(p.level(),entity.getBlockPos().getCenter()))<=4096;
    }
    @Override public boolean clickMenuButton(Player p,int button){if(button!=0||!stillValid(p))return false;
        if(context.equals("module")){DashboardSelection.set(heart,p.getUUID(),moduleId);heart.openDashboard(p);}else heart.openSettings(p,context.equals("vanish")?3:2);return true;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player p){}
    @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
}
