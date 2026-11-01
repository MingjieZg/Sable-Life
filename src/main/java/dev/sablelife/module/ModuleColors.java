package dev.sablelife.module;
import java.util.*;
import org.joml.Vector3f;
/** Independent preset/custom state for each existing color control. */
public final class ModuleColors {
 public static List<String> keys(String type){return switch(type){case "visual_shockwave"->List.of("filterColor");case "mortar"->List.of("mortarWarningColor");case "ground_strike"->List.of("strikeColor");case "laser"->List.of("beamColor");case "quake"->List.of("quakeColor");case "pull"->List.of("pullWarningColor","pullCenterColor");case "slash"->List.of("slashColor");default->List.of();};}
 public static boolean color(String type,String key){return keys(type).contains(key);}
 public static String parent(String type,String key){for(var color:keys(type))for(var suffix:List.of("Red","Green","Blue","Rgb","RgbSet"))if(key.equals(color+suffix))return color;return null;}
 public static Vector3f preset(String type,int index){var colors=new Vector3f[]{new Vector3f(1,type.equals("mortar")?.08F:.1F,type.equals("mortar")?.08F:.1F),new Vector3f(1,type.equals("pull")?.65F:type.equals("quake")?.6F:.5F,.1F),new Vector3f(.2F,.5F,1),new Vector3f(.7F,.2F,1),new Vector3f(.2F,1,type.equals("pull")?.35F:.4F),new Vector3f(1,1,1),new Vector3f(1,.9F,.1F),new Vector3f(.2F,.8F,1)};return colors[Math.clamp(index,0,colors.length-1)];}
 public static Vector3f vector(VirtualModule m,String key){return m.value(key+"Rgb")==1?new Vector3f((float)m.value(key+"Red")/255,(float)m.value(key+"Green")/255,(float)m.value(key+"Blue")/255):preset(m.type(),(int)m.value(key));}
 public static int packed(VirtualModule m,String key){if(m.value(key+"Rgb")!=1)return (int)m.value(key);return 0x1000000|((int)m.value(key+"Red")<<16)|((int)m.value(key+"Green")<<8)|(int)m.value(key+"Blue");}
 public static Vector3f packedVector(int color){return (color&0x1000000)!=0?new Vector3f(((color>>16)&255)/255F,((color>>8)&255)/255F,(color&255)/255F):preset("mortar",color);}
 public static List<ModuleFields.Field> fields(String key,Vector3f initial){var fields=new ArrayList<ModuleFields.Field>();fields.add(new ModuleFields.Field(key+"Rgb",0,1,0,1,List.of("off","on")));String[] suffix={"Red","Green","Blue"};float[] values={initial.x,initial.y,initial.z};for(int i=0;i<3;i++)fields.add(new ModuleFields.Field(key+suffix[i],0,255,Math.round(values[i]*255),1,List.of()));fields.add(new ModuleFields.Field(key+"RgbSet",0,1,0,1,List.of("off","on")));return fields;}
 public static boolean visible(VirtualModule m,String key){return parent(m.type(),key)==null;}
 public static List<ModuleFields.Field> display(VirtualModule m,List<ModuleFields.Field> source,int slots){return source.stream().filter(f->visible(m,f.key())).toList();}
 private ModuleColors(){}
}
