package dev.sablelife.module;

import java.util.*;
import net.minecraft.nbt.*;

/** Authoring links use stable IDs. They never change execution order or signal state. */
public final class ModuleConnections {
 public record Link(UUID from,UUID to,String kind,String detail,int move){}
 public record Endpoint(UUID module,int channel,boolean external,boolean output,String detail){}
 public static List<Link> links(List<VirtualModule> modules){
  var result=new ArrayList<Link>();
  for(var m:modules){
   for(int i=0;i<m.moveSet().moves().size();i++){var move=m.moveSet().get(i);for(var action:move.actions())result.add(new Link(m.id(),action.module(),"move",move.config().name(),i));}
   add(result,m,m.summonAttackId(),"summon");add(result,m,m.impactFieldId(),"impact");add(result,m,m.hitSourceId(),"hit");
   int row=0;for(var rule:m.signalRules().rows()){row++;if(rule.source()!=null)result.add(new Link(m.id(),rule.source(),"event",Integer.toString(row),-1));}
  }return List.copyOf(result);
 }
 private static void add(List<Link> links,VirtualModule m,UUID target,String kind){if(target!=null)links.add(new Link(m.id(),target,kind,"",-1));}
 public static List<Endpoint> endpoints(List<VirtualModule> modules){
  var result=new ArrayList<Endpoint>();for(var m:modules){
   int trigger=(int)m.value("trigger");if(trigger==2||trigger==3)endpoint(result,m,(int)m.value("channel")+1,trigger==3,false,"trigger");
   String key=switch(m.type()){case "wireless_redstone"->"redstoneChannel";case "sensor"->"sensorChannel";case "signal_logic"->"logicChannel";case "hit_counter"->"hitChannel";case "damage_accumulation"->"accumChannel";default->"";};
   if(!key.isEmpty())endpoint(result,m,(int)m.value(key),false,true,key);
   if(m.type().equals("health_phase"))for(var r:m.healthPhaseRules().rows())endpoint(result,m,(int)r.value("phaseChannel"),false,true,"phase");
   if(m.type().equals("navigation"))for(String d:TargetNavigation.DIRECTIONS)endpoint(result,m,(int)m.value("navigation"+d+"Channel"),false,true,"navigation"+d+"Channel");
   if(m.type().equals("sequence")){for(var step:m.sequence())if(!step.waiting())endpoint(result,m,step.channel(),false,true,"sequence");endpoint(result,m,(int)m.value("resetChannel"),m.value("resetSource")==0,false,"reset");}
   if(m.type().equals("signal_turn"))for(String d:ModuleSignalTurn.DIRECTIONS)endpoint(result,m,(int)m.value(d+"Channel"),m.value(d+"Source")==1,false,d+"Channel");
   if(m.type().equals("signal_logic"))for(var r:m.signalRules().rows())endpoint(result,m,(int)r.value("signalInput"),false,false,"condition");
   if(m.type().equals("combat_event"))for(var r:m.signalRules().rows())endpoint(result,m,(int)r.value("eventChannel"),false,true,"event");
   if(m.type().equals("armor"))for(var r:m.armorRules().rows())endpoint(result,m,r.channel(),r.source()==1,false,"armor");
  }return result.stream().distinct().toList();
 }
 private static void endpoint(List<Endpoint> rows,VirtualModule m,int channel,boolean external,boolean output,String detail){if(channel>0&&channel<=65535)rows.add(new Endpoint(m.id(),channel-1,external,output,detail));}
 public static CompoundTag references(List<Link> links,UUID selected){
  var tag=new CompoundTag();if(selected!=null)tag.putUUID("Selected",selected);var rows=new ListTag();int incoming=0,total=0;
  for(var link:links)if(link.from.equals(selected)||link.to.equals(selected)){
   total++;if(link.to.equals(selected))incoming++;if(rows.size()>=512)continue;
   var row=new CompoundTag();row.putUUID("From",link.from);row.putUUID("To",link.to);row.putString("Kind",link.kind);row.putString("Detail",link.detail);row.putInt("Move",link.move);rows.add(row);
  }tag.put("Rows",rows);tag.putInt("Incoming",incoming);tag.putInt("Total",total);return tag;
 }
 private ModuleConnections(){}
}
