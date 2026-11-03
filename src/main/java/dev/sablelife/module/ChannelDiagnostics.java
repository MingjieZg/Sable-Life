package dev.sablelife.module;
import java.util.*;
import net.minecraft.nbt.*;
import dev.sablelife.heart.HeartBlockEntity;
/** Records the publication result, rather than guessing output from trigger/configuration. */
public final class ChannelDiagnostics {
 public static void record(Map<UUID,Map<Integer,Integer>> rows,UUID module,int channel,int power){if(channel>=0&&power>0)rows.computeIfAbsent(module,k->new HashMap<>()).merge(channel,power,Math::max);}
 public static CompoundTag snapshot(HeartBlockEntity heart,int channel,List<ModuleConnections.Endpoint> endpoints){
  var tag=new CompoundTag();var wireless=heart.wireless();tag.putInt("Channel",channel);tag.putInt("Count",wireless.size());tag.putString("Name",wireless.channel(channel).name());tag.putInt("Power",wireless.output(channel));tag.putInt("Input",wireless.input(channel));tag.putBoolean("Master",wireless.master());tag.putBoolean("Ready",heart.operational());tag.putBoolean("Configured",wireless.channel(channel).configured());
  tag.putBoolean("Receiving",wireless.channel(channel).receiving());tag.putBoolean("Latch",wireless.channel(channel).value(7)!=0);tag.putInt("Mode",wireless.channel(channel).value(6));
  var sources=new ListTag();wireless.moduleContributions(channel).forEach((id,power)->{var row=new CompoundTag();row.putUUID("Module",id);row.putInt("Power",power);sources.add(row);});tag.put("Sources",sources);
  var links=new ListTag();int total=0;for(var endpoint:endpoints)if(endpoint.channel()==channel){total++;if(links.size()>=512)continue;var row=new CompoundTag();row.putUUID("Module",endpoint.module());row.putBoolean("External",endpoint.detail().equals("condition")?wireless.channel(channel).receiving():endpoint.external());row.putBoolean("Output",endpoint.output());row.putString("Detail",endpoint.detail());links.add(row);}tag.put("Links",links);tag.putInt("LinkCount",total);return tag;
 }
 private ChannelDiagnostics(){}
}
