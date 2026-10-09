package dev.sablelife.heart;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Parse author-entered legacy color codes into isolated component styles. */
public final class CreatureNames {
    public static Component formatted(String raw) {
        var result=Component.empty();var text=new StringBuilder();Style style=Style.EMPTY;
        for(int i=0;i<raw.length();i++){
            char c=raw.charAt(i);
            if((c=='&'||c=='§')&&i+1<raw.length()){
                Style next=null;int consumed=1;char code=Character.toLowerCase(raw.charAt(i+1));
                if(code=='#'&&i+7<raw.length()&&raw.substring(i+2,i+8).matches("[0-9a-fA-F]{6}")){
                    next=Style.EMPTY.withColor(Integer.parseInt(raw.substring(i+2,i+8),16));consumed=7;
                }else{
                    var format=ChatFormatting.getByCode(code);
                    if(format!=null)next=format==ChatFormatting.RESET?Style.EMPTY:format.isColor()?Style.EMPTY.applyFormat(format):style.applyFormat(format);
                }
                if(next!=null){if(!text.isEmpty()){result.append(Component.literal(text.toString()).setStyle(style));text.setLength(0);}style=next;i+=consumed;continue;}
            }
            text.append(c);
        }
        if(!text.isEmpty())result.append(Component.literal(text.toString()).setStyle(style));return result;
    }
    public static Component killer(String raw){var name=formatted(raw);return name.getString().isBlank()?Component.literal("block"):name;}
    private CreatureNames(){}
}
