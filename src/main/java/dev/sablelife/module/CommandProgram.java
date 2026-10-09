package dev.sablelife.module;

import net.minecraft.nbt.*;
import java.util.*;

/** Bounded command-block rows; execution state never travels with saved configurations. */
public final class CommandProgram {
    public static final int LIMIT=32, MAX_TEXT=2048;
    public record Row(UUID id, String command, int mode, boolean conditional, int interval, boolean enabled) {
        public Row { Objects.requireNonNull(id); command=command.trim(); mode=Math.clamp(mode,0,2); interval=Math.clamp(interval,1,12000); }
    }
    private final List<Row> rows=new ArrayList<>();
    public List<Row> rows(){return List.copyOf(rows);}
    public ListTag save(){var list=new ListTag();for(var r:rows){var t=new CompoundTag();t.putUUID("Id",r.id());t.putString("Command",r.command());t.putInt("Mode",r.mode());t.putBoolean("Conditional",r.conditional());t.putInt("Interval",r.interval());t.putBoolean("Enabled",r.enabled());list.add(t);}return list;}
    public void load(ListTag list){rows.clear();var ids=new HashSet<UUID>();for(int i=0;i<Math.min(LIMIT,list.size());i++){var t=list.getCompound(i);var command=t.getString("Command");if(!valid(command))continue;var id=t.hasUUID("Id")?t.getUUID("Id"):UUID.randomUUID();if(!ids.add(id))continue;rows.add(new Row(id,command,t.getInt("Mode"),t.getBoolean("Conditional"),t.getInt("Interval"),!t.contains("Enabled")||t.getBoolean("Enabled")));}}
    private static boolean valid(String s){return s.length()<=MAX_TEXT&&s.codePoints().noneMatch(Character::isISOControl);}
    public boolean edit(String key,int index,double value,String text){
        if(!Double.isFinite(value))return false;
        if(key.equals("add")){if(rows.size()>=LIMIT)return false;rows.add(new Row(UUID.randomUUID(),"",0,false,20,true));return true;}
        if(index<0||index>=rows.size())return false;var old=rows.get(index);
        switch(key){
            case "delete" -> rows.remove(index);
            case "up","down" -> {int next=index+(key.equals("up")?-1:1);if(next<0||next>=rows.size())return false;Collections.swap(rows,index,next);}
            case "copy" -> {if(rows.size()>=LIMIT)return false;rows.add(index+1,new Row(UUID.randomUUID(),old.command(),old.mode(),old.conditional(),old.interval(),old.enabled()));}
            case "command" -> {if(!valid(text))return false;rows.set(index,new Row(old.id(),text,old.mode(),old.conditional(),old.interval(),old.enabled()));}
            case "mode","conditional","interval","enabled" -> {
                if(key.equals("mode")&&(value<0||value>2||value!=Math.floor(value))||key.equals("interval")&&(value<.05||value>600))return false;
                rows.set(index,new Row(old.id(),old.command(),key.equals("mode")?(int)value:old.mode(),key.equals("conditional")?value!=0:old.conditional(),key.equals("interval")?(int)Math.round(value*20):old.interval(),key.equals("enabled")?value!=0:old.enabled()));
            }
            default -> {return false;}
        }return true;
    }
}
