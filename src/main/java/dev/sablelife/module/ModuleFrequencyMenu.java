package dev.sablelife.module;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Red/blue ghost samples, using the same item-copy interaction as the wireless terminal. */
public final class ModuleFrequencyMenu extends AbstractContainerMenu {
    private final HeartBlockEntity heart;
    private final VirtualModule module;
    private final SimpleContainer samples=new SimpleContainer(2);
    private final ContainerData data;
    public ModuleFrequencyMenu(int id,Inventory inventory) { this(id,inventory,null,null); }
    public ModuleFrequencyMenu(int id,Inventory inventory,HeartBlockEntity heart,VirtualModule module) {
        super(SableLife.MODULE_FREQUENCY_MENU.get(),id);this.heart=heart;this.module=module;
        data=heart==null ? new SimpleContainerData(1) : new ContainerData() {
            public int getCount(){return 1;} public void set(int i,int v){} public int get(int i){return heart.canEdit(inventory.player)?1:0;}
        };addDataSlots(data);
        for(int i=0;i<2;i++) addSlot(new Slot(samples,i,55+i*54,40){
            @Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean mayPickup(Player player){return false;}
        });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,10+col*18,99+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,10+col*18,157));refresh();
    }
    public boolean editable(){return data.get(0)!=0;}
    private void refresh(){if(heart!=null)for(int i=0;i<2;i++)samples.setItem(i,module.redBlue(i==1,heart.getLevel().registryAccess()));}
    @Override public void broadcastChanges(){refresh();super.broadcastChanges();}
    @Override public boolean stillValid(Player player){return heart==null || heart.canAccessEditor(player)&&!heart.isRemoved()&&player.level()==heart.getLevel()
        &&heart.getLevel().getBlockEntity(heart.getBlockPos())==heart&&heart.modules().find(module.id())==module
        &&SableBodyProbe.toWorld(player.level(),player.getEyePosition()).distanceToSqr(SableBodyProbe.toWorld(player.level(),heart.getBlockPos().getCenter()))<=64;}
    private void set(boolean blue,ItemStack stack){module.redBlue(blue,stack,heart.getLevel().registryAccess());heart.modules().changed();heart.setChanged();refresh();sendAllDataToRemote();}
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(!stillValid(player))return;
        if(slot>=0&&slot<2){if(heart!=null&&heart.canEdit(player)&&type==ClickType.PICKUP&&(button==0||button==1))set(slot==1,getCarried());return;}
        super.clicked(slot,button,type,player);
    }
    @Override public boolean clickMenuButton(Player player,int button){
        if(heart==null||!stillValid(player))return false;
        if(button==0){DashboardSelection.set(heart,player.getUUID(),module.id());heart.openDashboard(player);return true;}
        if(button==1&&heart.canEdit(player)){set(false,ItemStack.EMPTY);set(true,ItemStack.EMPTY);return true;}return false;
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean canDragTo(Slot slot){return slot.index>=2;}
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return slot.index>=2;}
}
