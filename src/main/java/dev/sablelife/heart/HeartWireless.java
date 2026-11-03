package dev.sablelife.heart;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;

/** Registers real Create transmitters; Sable's Create mixin projects plot coordinates for range checks. */
public final class HeartWireless {
    public static final int CHANNELS = 65535;
    private final HeartBlockEntity heart;
    private final java.util.List<WirelessChannel> channels = new java.util.ArrayList<>();
    private final java.util.Map<Integer, Transmitter> transmitters = new java.util.HashMap<>();
    private final java.util.Map<Integer, Receiver> receivers = new java.util.HashMap<>();
    private boolean master = true;
    private boolean legacyChannels;
    private long elapsed, lastTick = Long.MIN_VALUE;
    private java.util.List<Integer> configuredChannels;
    private final dev.sablelife.module.ProgramSignals programSources = new dev.sablelife.module.ProgramSignals();
    private java.util.Map<Integer, Integer> programOutputs = java.util.Map.of();
    public record ModuleOutput(net.minecraft.world.item.ItemStack red, net.minecraft.world.item.ItemStack blue, int power) {}
    private final java.util.Map<java.util.UUID,Transmitter> moduleTransmitters=new java.util.HashMap<>();
    private static final class Latch { int held; boolean armed=true;
        void sample(int raw,boolean reset){if(reset){held=0;armed=raw==0;return;}if(raw==0)armed=true;else if(armed){held=raw;armed=false;}}
    }
    private final java.util.Map<Integer,Latch> latches=new java.util.HashMap<>();
    private java.util.List<Integer> latchChannels;
    private int powerAt(int index,long time){var c=channels.get(index);if(c.value(7)!=0&&!c.receiving())return c.configured()?latches.containsKey(index)?latches.get(index).held:0:0;return rawPowerAt(index,time);}
    private int rawPowerAt(int index, long time) {
        var channel = channels.get(index);
        if (channel.programmed()) return channel.configured() ? programOutputs.getOrDefault(index, 0) : 0;
        return channel.powerAt(time);
    }
    /** Snapshot reset inputs before sampling: no recursive channel evaluation or order-dependent reset chains. */
    private void refreshLatches(long time){
        if(!master||!heart.operational()){latches.clear();return;}
        if(latchChannels==null)latchChannels=java.util.stream.IntStream.range(0,size()).filter(i->channels.get(i).configured()&&!channels.get(i).receiving()&&channels.get(i).value(7)!=0).boxed().toList();
        var resets=new boolean[latchChannels.size()];
        for(int n=0;n<resets.length;n++){int reset=channels.get(latchChannels.get(n)).value(8)-1;resets[n]=reset>=0&&reset<size()&&(channels.get(reset).receiving()?input(reset):powerAt(reset,time))>0;}
        for(int n=0;n<resets.length;n++){int i=latchChannels.get(n);latches.computeIfAbsent(i,k->new Latch()).sample(rawPowerAt(i,time),resets[n]);}
    }
    private final java.util.Map<String,java.util.Map<java.util.UUID,java.util.Map<Integer,Integer>>> contributionSources=new java.util.HashMap<>();
    public java.util.Map<java.util.UUID,Integer> moduleContributions(int channel){
        var result=new java.util.LinkedHashMap<java.util.UUID,Integer>();if(!master||!heart.operational())return result;
        contributionSources.values().forEach(rows->rows.forEach((id,values)->{int power=values.getOrDefault(channel,0);if(power>0)result.merge(id,power,Math::max);}));return java.util.Map.copyOf(result);
    }
    public void programOutputs(String source,java.util.Map<Integer,Integer> outputs,java.util.Map<java.util.UUID,java.util.Map<Integer,Integer>> details){
        programOutputs(source,outputs);var accepted=new java.util.HashMap<java.util.UUID,java.util.Map<Integer,Integer>>();
        if(master&&heart.operational())details.forEach((id,values)->{var valid=new java.util.HashMap<Integer,Integer>();values.forEach((c,p)->{if(c>=0&&c<size()&&channels.get(c).programmed()&&channels.get(c).configured()&&p>0&&outputs.getOrDefault(c,0)>=p)valid.put(c,Math.clamp(p,0,15));});if(!valid.isEmpty())accepted.put(id,java.util.Map.copyOf(valid));});
        if(!accepted.isEmpty())contributionSources.put(source,java.util.Map.copyOf(accepted));
    }
    public void programOutputs(String source, java.util.Map<Integer, Integer> outputs) {
        contributionSources.remove(source);
        var next = new java.util.HashMap<Integer, Integer>();
        if (master && heart.operational()) outputs.forEach((index, value) -> {
            if (index >= 0 && index < size() && channels.get(index).programmed() && channels.get(index).configured()) next.put(index, Math.clamp(value, 0, 15));
        });
        programSources.set(source, next);
        programOutputs = programSources.combined();
        refreshLatches(Math.max(0,elapsed-1));
        for (var entry : transmitters.entrySet()) {
            var transmitter = entry.getValue(); int power = powerAt(entry.getKey(), Math.max(0, elapsed - 1));
            if (transmitter.power != power) { transmitter.power = power; Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(transmitter.level, transmitter); }
        }
    }
    /** Independent module frequencies: no sequencer channel or waveform is involved. */
    public void moduleFrequencies(java.util.Map<java.util.UUID,ModuleOutput> outputs) {
        if(!(heart.getLevel() instanceof ServerLevel level)) return;
        var wanted=master && heart.operational() ? outputs : java.util.Map.<java.util.UUID,ModuleOutput>of();
        var iterator=moduleTransmitters.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next(); var value=wanted.get(entry.getKey());
            if(value==null || !entry.getValue().key.equals(Couple.create(Frequency.of(value.red()),Frequency.of(value.blue())))) {
                removeModuleTransmitter(entry.getValue()); iterator.remove();
            }
        }
        wanted.forEach((id,value)->{
            if(value.red().isEmpty()&&value.blue().isEmpty()) return;
            var transmitter=moduleTransmitters.get(id); int power=Math.clamp(value.power(),0,15);
            if(transmitter==null) {
                transmitter=new Transmitter(value.red(),value.blue(),level); transmitter.power=power;
                moduleTransmitters.put(id,transmitter); Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level,transmitter);
            } else if(transmitter.power!=power || level.getGameTime()%5==0) {
                transmitter.power=power; Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level,transmitter);
            }
        });
    }
    public void clearModuleFrequencies(java.util.Set<java.util.UUID> ids){
        for(var id:ids){var transmitter=moduleTransmitters.remove(id);if(transmitter!=null)removeModuleTransmitter(transmitter);}
    }
    private void removeModuleTransmitter(Transmitter transmitter) {
        transmitter.power=0;
        if(transmitter.level.getServer().isRunning()) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(transmitter.level,transmitter);
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(transmitter.level,transmitter);
        }
    }
    public HeartWireless(HeartBlockEntity heart) {
        this.heart = heart;
    }
    public int size() { return channels.size(); }
    public WirelessChannel channel(int index) { return index >= 0 && index < channels.size() ? channels.get(index) : new WirelessChannel(); }
    public int limit() { return dev.sablelife.SableLifeServerConfig.maximumWirelessChannels(); }
    public int add() { if (channels.size() >= limit()) return -1; var channel=new WirelessChannel();channel.onChange(this::configurationChanged);channels.add(channel);configuredChannels=null;latchChannels=null; heart.setChanged(); return channels.size() - 1; }
    public boolean remove(int index) {
        if (index < 0 || index >= channels.size()) return false;
        stop(); channels.remove(index);
        for(var c:channels){int reset=c.value(8);if(reset==index+1)c.set(8,0);else if(reset>index+1)c.set(8,reset-1);}
        configuredChannels=null;latchChannels=null;
        boolean changed = false;
        for (var module : heart.modules().all()) changed |= module.wirelessChannelDeleted(index);
        if (changed) heart.modules().changed();
        heart.setChanged(); return true;
    }
    public void finishMigration() {
        if (!legacyChannels) return;
        int keep = 0;
        for (int i = 0; i < channels.size(); i++) {
            var channel = channels.get(i);
            if (!channel.name().isEmpty() || !channel.first().isEmpty() || !channel.second().isEmpty() || channel.value(0) != 0
                    || channel.value(1) != 0 || channel.value(2) != 20 || channel.value(3) != 20
                    || channel.value(4) != 15 || channel.value(5) != 1 || channel.value(7)!=0 || channel.value(8)!=0 || channel.receiving()) keep = i + 1;
        }
        for (var module : heart.modules().all()) if (module.value("trigger") >= 2)
            keep = Math.max(keep, Math.min(channels.size(), (int) module.value("channel") + 1));
        while (channels.size() > keep) channels.removeLast();
        configuredChannels=null;latchChannels=null;legacyChannels = false;
    }
    public boolean master() { return master; }
    public void master(boolean value) { master = value; if (!value) { stop(); heart.modules().hurtTriggers.clear();heart.modules().signalLogic.clear();heart.modules().combatEvents.clear(); } }
    public long elapsed() { return elapsed; }
    public int output(int index) {
        if (!master || !heart.operational() || index < 0 || index >= size()) return 0;
        return powerAt(index, Math.max(0, elapsed - 1));
    }
    public int input(int index) {
        var receiver = receivers.get(index);
        return receiver == null || !master || !heart.operational() ? 0 : receiver.power;
    }
    public void start() { stop(); elapsed = 0; lastTick = Long.MIN_VALUE; }
    public void tick(ServerLevel level) {
        if (!heart.operational() || heart.isRemoved()) { stop(); return; }
        if (lastTick == level.getGameTime()) return;
        lastTick = level.getGameTime();
        if (configuredChannels == null) configuredChannels = java.util.stream.IntStream.range(0, channels.size())
                .filter(i -> channels.get(i).configured()).boxed().toList();
        refreshLatches(elapsed);
        for (int i : configuredChannels) {
            if (!master || !channels.get(i).configured()) continue;
            if (channels.get(i).receiving()) {
                if (!receivers.containsKey(i)) {
                    receivers.put(i, new Receiver(channels.get(i), level));
                    Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, receivers.get(i));
                } else if (level.getGameTime() % 5 == 0) Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, receivers.get(i));
                continue;
            }
            if (!channels.get(i).transmitting()) continue;
            if (!transmitters.containsKey(i)) {
                transmitters.put(i, new Transmitter(channels.get(i), level));
                transmitters.get(i).power = powerAt(i, elapsed);
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, transmitters.get(i));
            } else {
                int value = powerAt(i, elapsed);
                if (transmitters.get(i).power != value || level.getGameTime() % 5 == 0) {
                    transmitters.get(i).power = value;
                    Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, transmitters.get(i));
                }
            }
        }
        if (elapsed < Long.MAX_VALUE - 1) elapsed++;
        heart.setChanged();
    }
    public void configurationChanged() { configuredChannels=null;latchChannels=null; stop(); }
    public void stop() {
        latches.clear();
        contributionSources.clear();programSources.clear();
        programOutputs = java.util.Map.of();
        moduleTransmitters.values().forEach(this::removeModuleTransmitter); moduleTransmitters.clear();
        for (var receiver : receivers.values()) {
            receiver.power = 0;
            if (receiver.level.getServer().isRunning()) Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(receiver.level, receiver);
        }
        receivers.clear();
        for (var transmitter : transmitters.values()) {
            transmitter.power = 0;
            // Create clears all world networks during shutdown before some block entities unload.
            if (transmitter.level.getServer().isRunning()) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(transmitter.level, transmitter);
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(transmitter.level, transmitter);
            }
        }
        transmitters.clear();
    }
    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag(); var list = new ListTag();
        for (var channel : channels) list.add(channel.save(registries));
        tag.putBoolean("DynamicChannels", true); tag.put("Channels", list); tag.putBoolean("Master", master); tag.putLong("Elapsed", elapsed);
        return tag;
    }
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        stop(); master = !tag.contains("Master") || tag.getBoolean("Master");
        elapsed = Math.max(0, tag.getLong("Elapsed")); lastTick = Long.MIN_VALUE;
        var list = tag.getList("Channels", 10);
        legacyChannels = !tag.getBoolean("DynamicChannels");
        channels.clear();
        configuredChannels=null;latchChannels=null;
        // Old saves retain all 16 numbers, including disabled slots referenced by modules.
        int count = tag.getBoolean("DynamicChannels") ? list.size() : list.isEmpty() ? 0 : Math.max(16, list.size());
        for (int i = 0; i < Math.min(CHANNELS, count); i++) { var channel=i < list.size() ? WirelessChannel.load(list.getCompound(i), registries) : new WirelessChannel(); channel.onChange(this::configurationChanged);channels.add(channel); }
    }
    private final class Transmitter implements IRedstoneLinkable {
        private final ServerLevel level;
        private final Couple<Frequency> key;
        private int power;
        private Transmitter(WirelessChannel channel, ServerLevel level) {
            this(channel.first(),channel.second(),level);
        }
        private Transmitter(net.minecraft.world.item.ItemStack red,net.minecraft.world.item.ItemStack blue,ServerLevel level) { this.level=level; key=Couple.create(Frequency.of(red),Frequency.of(blue)); }
        @Override public int getTransmittedStrength() { return power; }
        @Override public void setReceivedStrength(int value) {}
        @Override public boolean isListening() { return false; }
        @Override public boolean isAlive() { return !heart.isRemoved() && heart.operational(); }
        @Override public Couple<Frequency> getNetworkKey() { return key; }
        @Override public BlockPos getLocation() { return heart.getBlockPos(); }
    }
    private final class Receiver implements IRedstoneLinkable {
        private final ServerLevel level;
        private final Couple<Frequency> key;
        private int power;
        private Receiver(WirelessChannel channel, ServerLevel level) { this.level = level; key = Couple.create(Frequency.of(channel.first()), Frequency.of(channel.second())); }
        @Override public int getTransmittedStrength() { return 0; }
        @Override public void setReceivedStrength(int value) { power = Math.clamp(value, 0, 15); }
        @Override public boolean isListening() { return true; }
        @Override public boolean isAlive() { return !heart.isRemoved() && heart.operational(); }
        @Override public Couple<Frequency> getNetworkKey() { return key; }
        @Override public BlockPos getLocation() { return heart.getBlockPos(); }
    }
}
