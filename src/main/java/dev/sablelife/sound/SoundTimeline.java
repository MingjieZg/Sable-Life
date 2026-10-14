package dev.sablelife.sound;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Event-relative sound layers. Zero duration means natural completion (BGM keeps its native loop). */
public final class SoundTimeline {
    public static final int LIMIT=32, MAX_TICKS=12000;
    public record Row(UUID id,String sound,int start,float volume,float pitch,float radius,int duration,int fade,boolean enabled) {
        public Row(UUID id,String sound,int start,float volume,float pitch,float radius,int duration,boolean enabled) { this(id,sound,start,volume,pitch,radius,duration,0,enabled); }
        public Row { fade=Math.clamp(fade,0,MAX_TICKS); start=Math.clamp(start,0,MAX_TICKS); duration=Math.clamp(duration,0,MAX_TICKS);
            volume=finite(volume,0,4,1); pitch=finite(pitch,.5F,2,1); radius=finite(radius,1,256,32); }
        private static float finite(float v,float min,float max,float fallback){return Float.isFinite(v)?Math.clamp(v,min,max):fallback;}
    }
    private final List<Row> rows=new ArrayList<>(); private int revision; private boolean authored;
    public boolean authored(){return authored;}
    public List<Row> rows(){return List.copyOf(rows);} public int revision(){return revision;}
    public boolean audible(){return rows.stream().anyMatch(r->r.enabled&&r.volume>0);}
    public static boolean valid(String sound){var id=ResourceLocation.tryParse(sound);return sound.length()<=256&&id!=null&&(BuiltInRegistries.SOUND_EVENT.containsKey(id)||SoundLibrary.contains(sound));}
    public static SoundTimeline legacy(String key,String sound,double volume,double pitch,double radius){
        var t=new SoundTimeline();if(!sound.isEmpty()&&ResourceLocation.tryParse(sound)!=null)t.rows.add(new Row(UUID.nameUUIDFromBytes((key+":"+sound).getBytes(java.nio.charset.StandardCharsets.UTF_8)),sound,0,(float)volume,(float)pitch,(float)radius,0,true));return t;
    }
    public boolean edit(String operation,UUID id,double value,String sound){
        if(!Double.isFinite(value))return false;
        int index=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).id.equals(id)){index=i;break;}
        if(operation.equals("add")){if(rows.size()>=LIMIT||!valid(sound))return false;rows.add(new Row(UUID.randomUUID(),sound,0,1,1,32,0,true));authored=true;revision++;return true;}
        if(operation.equals("clear")){rows.clear();authored=true;revision++;return true;}
        if(index<0)return false;var r=rows.get(index);
        switch(operation){
            case "delete" -> rows.remove(index);
            case "copy" -> {if(rows.size()>=LIMIT)return false;rows.add(index+1,new Row(UUID.randomUUID(),r.sound,r.start,r.volume,r.pitch,r.radius,r.duration,r.fade,r.enabled));}
            case "up","down" -> {int next=index+(operation.equals("up")?-1:1);if(next<0||next>=rows.size())return false;Collections.swap(rows,index,next);}
            case "enabled" -> rows.set(index,new Row(r.id,r.sound,r.start,r.volume,r.pitch,r.radius,r.duration,r.fade,value!=0));
            case "start","volume","pitch","radius","duration","fade" -> {
                double min=operation.equals("pitch")?.5:operation.equals("radius")?1:0;
                double max=operation.equals("start")||operation.equals("duration")||operation.equals("fade")?600:operation.equals("radius")?256:operation.equals("pitch")?2:4;
                if(value<min||value>max)return false;
                rows.set(index,new Row(r.id,r.sound,operation.equals("start")?(int)Math.round(value*20):r.start,operation.equals("volume")?(float)value:r.volume,
                    operation.equals("pitch")?(float)value:r.pitch,operation.equals("radius")?(float)value:r.radius,operation.equals("duration")?(int)Math.round(value*20):r.duration,operation.equals("fade")?(int)Math.round(value*20):r.fade,r.enabled));
            }
            default -> {return false;}
        }
        authored=true;revision++;return true;
    }
    public CompoundTag save(){var tag=new CompoundTag();tag.putInt("Revision",revision);tag.putBoolean("Authored",authored);var list=new ListTag();for(var r:rows){var row=new CompoundTag();row.putUUID("Id",r.id);row.putString("Sound",r.sound);row.putInt("Start",r.start);row.putFloat("Volume",r.volume);row.putFloat("Pitch",r.pitch);row.putFloat("Radius",r.radius);row.putInt("Duration",r.duration);row.putInt("Fade",r.fade);row.putBoolean("Enabled",r.enabled);list.add(row);}tag.put("Rows",list);return tag;}
    public static SoundTimeline load(CompoundTag tag){var t=new SoundTimeline();t.authored=tag.getBoolean("Authored");t.revision=Math.max(0,tag.getInt("Revision"));var list=tag.getList("Rows",10);var ids=new HashSet<UUID>();for(int i=0;i<Math.min(LIMIT,list.size());i++){var r=list.getCompound(i);String sound=r.getString("Sound");if(sound.isEmpty()||sound.length()>256||ResourceLocation.tryParse(sound)==null)continue;UUID id=r.hasUUID("Id")?r.getUUID("Id"):UUID.randomUUID();if(!ids.add(id))continue;t.rows.add(new Row(id,sound,r.getInt("Start"),r.getFloat("Volume"),r.getFloat("Pitch"),r.contains("Radius")?r.getFloat("Radius"):32,r.getInt("Duration"),r.getInt("Fade"),r.getBoolean("Enabled")));}return t;}
}
