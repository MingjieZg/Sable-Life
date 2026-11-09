package dev.sablelife.item;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.CoreRelease;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/** Survival recovery tool restricted to the Survival Heart Core. */
public final class SurvivalReleaseToolItem extends Item {
    public SurvivalReleaseToolItem(Properties properties){super(properties);}

    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return release(context);}
    @Override public InteractionResult useOn(UseOnContext context){return release(context);}

    private InteractionResult release(UseOnContext context){
        var player=context.getPlayer();
        var level=context.getLevel();
        var pos=context.getClickedPos();
        if(player==null||player.isSpectator())return InteractionResult.FAIL;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(!player.canInteractWithBlock(pos,1)||!level.mayInteract(player,pos))return InteractionResult.FAIL;
        var cores=SableBodyProbe.findConnectedHearts(level,pos);
        if(cores.isEmpty()&&level.getBlockEntity(pos) instanceof HeartBlockEntity core)cores=List.of(core);
        var survival=cores.stream().filter(HeartBlockEntity::isSurvivalCore).toList();
        if(survival.size()!=1||!CoreRelease.releaseSurvival(survival.getFirst(),player)){
            player.displayClientMessage(Component.translatable("message.sablelife.release_survival_only"),true);
            return InteractionResult.FAIL;
        }
        return InteractionResult.CONSUME;
    }

    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        tooltip.add(Component.translatable("tooltip.sablelife.release_survival_only").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.sablelife.release_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sablelife.release_survival_preserve").withStyle(ChatFormatting.GRAY));
    }
}
