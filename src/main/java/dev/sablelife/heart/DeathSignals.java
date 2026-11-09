package dev.sablelife.heart;

import java.util.*;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import dev.sablelife.module.*;

/** Detached, saved external TX pulses. Never keeps a dying core's attack/movement runtimes alive. */
public final class DeathSignals extends SavedData {
    public record Pulse(UUID id,int channel,ItemStack first,ItemStack second,int power,int ticks){}
    private static final class Job implements IRedstoneLinkable {
        UUID id;ResourceLocation dimension;BlockPos pos;ItemStack first,second;int power,remaining;transient ServerLevel level;transient boolean registered;
        @Override public int getTransmittedStrength(){return remaining>0?power:0;}
        @Override public void setReceivedStrength(int value){}
        @Override public boolean isListening(){return false;}
        @Override public boolean isAlive(){return remaining>0&&level!=null;}
        @Override public Couple<Frequency> getNetworkKey(){return Couple.create(Frequency.of(first),Frequency.of(second));}
        @Override public BlockPos getLocation(){return pos;}
    }
    private final Map<UUID,Job> jobs=new LinkedHashMap<>();
    public static DeathSignals get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(DeathSignals::new,DeathSignals::load),"sablelife_death_signals");}
    /** Sample triggers while the core is still alive, before the lethal damage transition. */
    public static List<Pulse> prepare(HeartBlockEntity heart){var result=new ArrayList<Pulse>();if(!heart.wireless().master()||heart.isControlCore())return result;for(var m:heart.modules().all())if(m.type().equals("combat_event")&&HeartModules.triggered(m,heart))for(var row:m.signalRules().rows()){
        int channel=(int)row.value("eventChannel")-1;if(!row.enabled()||row.value("eventKind")!=3||channel<0||channel>=heart.wireless().size())continue;var c=heart.wireless().channel(channel);
        if(c.configured()&&c.programmed()&&c.transmitting())result.add(new Pulse(row.id(),channel,c.first(),c.second(),(int)row.value("eventPower"),(int)row.value("eventPulse")));
    }return result;}
    public static void enqueue(HeartBlockEntity heart,List<Pulse> pulses){if(heart.health().state()!=HeartState.DYING||!(heart.getLevel() instanceof ServerLevel level))return;var data=get(level.getServer());var pos=BlockPos.containing(dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,heart.getBlockPos().getCenter()));for(var pulse:pulses){var j=new Job();j.id=UUID.randomUUID();j.dimension=level.dimension().location();j.pos=pos;j.first=pulse.first.copy();j.second=pulse.second.copy();j.power=pulse.power;j.remaining=pulse.ticks;data.jobs.put(j.id,j);data.register(level.getServer(),j);}if(!pulses.isEmpty())data.setDirty();}
    private void register(MinecraftServer server,Job job){job.level=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,job.dimension));if(job.level!=null&&!job.registered){Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(job.level,job);job.registered=true;}}
    public static void tick(ServerTickEvent.Post event){var data=get(event.getServer());for(var j:List.copyOf(data.jobs.values())){data.register(event.getServer(),j);if(j.level==null)continue;if(--j.remaining<=0){if(j.registered){Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(j.level,j);Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(j.level,j);}data.jobs.remove(j.id);}else if(j.remaining%5==0)Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(j.level,j);data.setDirty();}}
    public int size(){return jobs.size();}
    @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider registries){var rows=new ListTag();for(var j:jobs.values()){var r=new CompoundTag();r.putUUID("Id",j.id);r.putString("Dimension",j.dimension.toString());r.putLong("Pos",j.pos.asLong());if(!j.first.isEmpty())r.put("First",j.first.save(registries));if(!j.second.isEmpty())r.put("Second",j.second.save(registries));r.putInt("Power",j.power);r.putInt("Remaining",j.remaining);rows.add(r);}t.put("Pulses",rows);return t;}
    private static DeathSignals load(CompoundTag t,HolderLookup.Provider registries){var data=new DeathSignals();for(var v:t.getList("Pulses",10)){var r=(CompoundTag)v;var dim=ResourceLocation.tryParse(r.getString("Dimension"));if(!r.hasUUID("Id")||dim==null||r.getInt("Remaining")<=0)continue;var j=new Job();j.id=r.getUUID("Id");j.dimension=dim;j.pos=BlockPos.of(r.getLong("Pos"));j.first=ItemStack.parseOptional(registries,r.getCompound("First"));j.second=ItemStack.parseOptional(registries,r.getCompound("Second"));j.power=Math.clamp(r.getInt("Power"),1,15);j.remaining=Math.clamp(r.getInt("Remaining"),1,12000);data.jobs.put(j.id,j);}return data;}
}
