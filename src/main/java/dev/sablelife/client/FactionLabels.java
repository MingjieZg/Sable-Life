package dev.sablelife.client;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public final class FactionLabels {
    public static Component visibility(CompoundTag row){
        boolean privateFaction=row.getBoolean("Private");
        return Component.translatable("relations.sablelife."+(privateFaction?"private_badge":"public_badge"))
                .withStyle(privateFaction?ChatFormatting.GOLD:ChatFormatting.GREEN);
    }
    private FactionLabels(){}
}
