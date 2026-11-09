package dev.sablelife.client;

import dev.sablelife.module.VirtualModule;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Read-only semantic highlights; localized prose stays editable without embedded formatting codes. */
final class ManualTextStyle {
    static final int HEADING=0xFFFFFF, HEART=0xEE939F, SURVIVAL=0x9EDCC6, REDSTONE=0xEDB17C;
    private final Map<String,Integer> colors=new HashMap<>();
    private final Pattern terms;
    ManualTextStyle(Function<String,String> localized,Function<String,String> english){
        for(var language:List.of(localized,english)){
            add(language.apply("block.sablelife.heart"),HEART);
            add(language.apply("block.sablelife.survival_heart_core"),SURVIVAL);
            add(language.apply("block.sablelife.redstone_core"),REDSTONE);
            for(String type:VirtualModule.AVAILABLE_TYPES)add(language.apply("dashboard.sablelife.type_"+type),ModuleCategory.of(type).color);
            add(language.apply("dashboard.sablelife.wireless"),ModuleCategory.SIGNAL.color);
            add(language.apply("manual.sablelife.chapter.wireless_channels"),ModuleCategory.SIGNAL.color);
        }
        for(String text:List.of("Wireless Channel","Wireless Channels","无线频道"))add(text,ModuleCategory.SIGNAL.color);
        for(String text:List.of("Arrow module","Arrow modules","Arrow launcher","箭矢模块"))add(text,ModuleCategory.WEAPON.color);
        add("Energy",ModuleCategory.FUNCTION.color);
        var names=new ArrayList<>(colors.keySet());names.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Function.identity()));
        terms=Pattern.compile("(?<![A-Za-z0-9_])(?:"+String.join("|",names.stream().map(Pattern::quote).toList())+")(?![A-Za-z0-9_])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    }
    private void add(String name,int color){if(name!=null&&!name.isBlank()&&!name.contains(".sablelife."))colors.put(name.toLowerCase(Locale.ROOT),color&0xFFFFFF);}
    static boolean heading(String[] lines,int index){
        String line=lines[index].strip();
        return index+1<lines.length&&!line.isEmpty()&&line.length()<=72&&!line.matches("^\\d+[.)、].*")
                &&!line.matches(".*[.!?。！？：:]$")&&(index==0||lines[index-1].isBlank());
    }
    Component format(String text,boolean heading){
        Style base=heading?Style.EMPTY.withColor(HEADING).withBold(true):Style.EMPTY;
        int colon=text.indexOf(':');if(colon<0)colon=text.indexOf('：');
        int labelEnd=!heading&&colon>0&&colon<48&&!text.startsWith("http")?colon+1:0;
        MutableComponent result=Component.empty();int offset=0;var matcher=terms.matcher(text);
        while(matcher.find()){
            append(result,text,offset,matcher.start(),base,labelEnd);
            Style style=base.withColor(colors.get(matcher.group().toLowerCase(Locale.ROOT)));
            if(matcher.start()<labelEnd)style=style.withBold(true);
            result.append(Component.literal(matcher.group()).withStyle(style));offset=matcher.end();
        }
        append(result,text,offset,text.length(),base,labelEnd);return result;
    }
    private static void append(MutableComponent result,String text,int start,int end,Style base,int labelEnd){
        if(start>=end)return;
        if(start<labelEnd){int split=Math.min(end,labelEnd);result.append(Component.literal(text.substring(start,split)).withStyle(base.withColor(HEADING).withBold(true)));start=split;}
        if(start<end)result.append(Component.literal(text.substring(start,end)).withStyle(base));
    }
}
