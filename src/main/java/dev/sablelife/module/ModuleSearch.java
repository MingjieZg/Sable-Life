package dev.sablelife.module;
import java.util.Locale;
/** Sidebar search is presentation-only; module indices and references never change. */
public final class ModuleSearch {
 public static boolean matches(VirtualModule module,String localizedType,int number,String query){
  var text=query.strip().toLowerCase(Locale.ROOT);if(text.isEmpty())return true;
  if(text.startsWith("#"))return text.equals("#"+number);
  var haystack=(module.name()+" "+module.type()+" "+localizedType+" #"+number).toLowerCase(Locale.ROOT);
  for(var word:text.split("\\s+"))if(!haystack.contains(word))return false;return true;
 }
 private ModuleSearch(){}
}
