package dev.sablelife.heart;

import dev.sablelife.SableLife;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

public final class SurvivalTopUpMenu extends AbstractContainerMenu {
    public static final int INPUTS=9, INVENTORY_Y=141;
    private final SimpleContainer inputs=new SimpleContainer(INPUTS), data=new SimpleContainer(1);
    private final HeartBlockEntity heart;
    private List<ItemStack> confirmed=List.of();
    private long confirmedValue, confirmationUntil;

    public SurvivalTopUpMenu(int id,Inventory inventory){this(id,inventory,null);}

    public SurvivalTopUpMenu(int id,Inventory inventory,HeartBlockEntity heart){
        super(SableLife.SURVIVAL_TOP_UP_MENU.get(),id);
        this.heart=heart;
        for(int i=0;i<INPUTS;i++)addSlot(new Slot(inputs,i,12+i*18,106){
            @Override public boolean mayPlace(ItemStack stack){
                return heart==null?catalogContains(stack):SurvivalTokens.depositValue(stack)>0;
            }
        });
        for(int row=0;row<3;row++)for(int column=0;column<9;column++)
            addSlot(new Slot(inventory,9+row*9+column,12+column*18,INVENTORY_Y+row*18));
        for(int column=0;column<9;column++)addSlot(new Slot(inventory,column,12+column*18,INVENTORY_Y+58));
        addSlot(new Slot(data,0,-10000,-10000){
            @Override public boolean isActive(){return false;}
            @Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean mayPickup(Player player){return false;}
        });
        refresh();
    }

    public CompoundTag snapshot(){return data.getItem(0).getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private boolean catalogContains(ItemStack stack){
        return snapshot().getList("Catalog",10).stream().anyMatch(entry->((CompoundTag)entry).getString("Id")
                .equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
    }
    private boolean confirmationMatches(long value){
        if(confirmed.isEmpty()||value!=confirmedValue||heart.getLevel().getGameTime()>confirmationUntil)return false;
        for(int i=0;i<INPUTS;i++)if(!ItemStack.matches(confirmed.get(i),inputs.getItem(i)))return false;
        return true;
    }
    private void refresh(){
        if(heart==null)return;
        var tag=new CompoundTag();
        long value=SurvivalTokens.inputValue(inputs);
        tag.putLong("Balance",heart.survivalTokenBalance());
        tag.putLong("Value",value);
        tag.putBoolean("Confirming",confirmationMatches(value));
        var catalog=new ListTag();
        SurvivalTokens.catalog().forEach((item,price)->{
            var row=new CompoundTag();
            row.putString("Id",BuiltInRegistries.ITEM.getKey(item).toString());
            row.putInt("Price",price);
            catalog.add(row);
        });
        tag.put("Catalog",catalog);
        var stack=new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        data.setItem(0,stack);
    }

    @Override public boolean stillValid(Player player){
        return heart==null||heart.canToggleBreaking(player)&&heart.editorInRange(player, 64);
    }

    @Override public boolean clickMenuButton(Player player,int id){
        if(heart==null||!stillValid(player))return false;
        if(id==1){heart.openDashboard(player);return true;}
        if(id!=0)return false;
        long value=SurvivalTokens.inputValue(inputs);
        if(value<=0||value>Long.MAX_VALUE-heart.survivalTokenBalance())return false;
        if(confirmationMatches(value)){
            if(!heart.depositSurvivalTokens(player,inputs))return false;
            confirmed=List.of();
        }else{
            var copy=new ArrayList<ItemStack>(INPUTS);
            for(int i=0;i<INPUTS;i++)copy.add(inputs.getItem(i).copy());
            confirmed=List.copyOf(copy);
            confirmedValue=value;
            confirmationUntil=heart.getLevel().getGameTime()+100;
        }
        broadcastChanges();
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=INPUTS+36||!stillValid(player))return ItemStack.EMPTY;
        var slot=slots.get(index);
        var stack=slot.getItem();
        if(stack.isEmpty())return ItemStack.EMPTY;
        var copy=stack.copy();
        if(index<INPUTS){
            if(!moveItemStackTo(stack,INPUTS,INPUTS+36,true))return ItemStack.EMPTY;
        }else if(!slots.getFirst().mayPlace(stack)||!moveItemStackTo(stack,0,INPUTS,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();
        slot.onTake(player,stack);
        return copy;
    }
    @Override public void broadcastChanges(){refresh();super.broadcastChanges();}
    @Override public void removed(Player player){
        super.removed(player);
        if(!player.level().isClientSide)clearContainer(player,inputs);
    }
}
