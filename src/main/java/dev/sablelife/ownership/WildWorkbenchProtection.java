package dev.sablelife.ownership;

import dev.sablelife.SableLifeServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/** Fixed-world crafting tables protect horizontal blueprint footprints, at every height.
 * Initial indexing is queued once per chunk load, then maintained by actual block changes.
 * Saved column counts also protect unloaded chunks; missing knowledge rejects, never loads. */
@EventBusSubscriber(modid="sablelife")
public final class WildWorkbenchProtection extends SavedData {
    public static final int SECTION_PROBES_PER_TICK=64, BLOCK_READS_PER_TICK=8192, MAX_RECORDS=32768;
    private static final Map<ServerLevel,WildWorkbenchProtection> worlds=new IdentityHashMap<>();
    private static final class Columns {
        int[] counts;
        void add(int column,int delta){
            if(counts==null){if(delta<=0)return;counts=new int[256];}
            counts[column]=Math.max(0,counts[column]+delta);
        }
    }
    private static final class Scan {
        final LevelChunk chunk;final Columns columns=new Columns();int cursor;
        Scan(LevelChunk chunk){this.chunk=chunk;}
        void changed(BlockPos pos,int delta){
            int offset=chunk.getSectionIndex(pos.getY())*4096+((pos.getY()&15)<<8)+((pos.getZ()&15)<<4)+(pos.getX()&15);
            if(offset<cursor)columns.add(((pos.getZ()&15)<<4)+(pos.getX()&15),delta);
        }
    }
    private final LinkedHashMap<Long,Columns> records=new LinkedHashMap<>(256,.75f,true);
    private final Map<Long,LevelChunk> ready=new HashMap<>();
    private final LinkedHashMap<Long,Scan> pending=new LinkedHashMap<>();
    private long sectionProbes,blockReads,queries,columnChecks,updates,completed;

    public static WildWorkbenchProtection get(ServerLevel level){
        return worlds.computeIfAbsent(level,l->l.getDataStorage().computeIfAbsent(
                new Factory<>(WildWorkbenchProtection::new,WildWorkbenchProtection::load),"sablelife_wild_workbenches"));
    }
    private void enqueue(LevelChunk chunk){
        long key=chunk.getPos().toLong();
        if(ready.get(key)==chunk||pending.containsKey(key)&&pending.get(key).chunk==chunk)return;
        ready.remove(key);if(records.remove(key)!=null)setDirty();pending.put(key,new Scan(chunk));
    }
    @SubscribeEvent public static void loaded(ChunkEvent.Load event){
        if(event.getLevel() instanceof ServerLevel level&&event.getChunk() instanceof LevelChunk chunk
                &&SableLifeServerConfig.wildEncountersEnabled()&&SableLifeServerConfig.wildWorkbenchProtectionRadius()>0){
            if(level.getServer().isSameThread())get(level).enqueue(chunk);
            else level.getServer().execute(()->{if(level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)==chunk)get(level).enqueue(chunk);});
        }
    }
    @SubscribeEvent public static void unloaded(ChunkEvent.Unload event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        var data=worlds.get(level);if(data==null)return;
        long key=event.getChunk().getPos().toLong();
        if(data.ready.get(key)==event.getChunk())data.ready.remove(key);
        var scan=data.pending.get(key);if(scan!=null&&scan.chunk==event.getChunk())data.pending.remove(key);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){worlds.keySet().removeIf(l->l.getServer()==event.getServer());}
    @SubscribeEvent public static void unloadedLevel(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)worlds.remove(level);}

    /** Called only when a crafting table actually changes, including commands, explosions and pistons. */
    public static void changed(LevelChunk chunk,BlockPos pos,boolean wasTable,boolean isTable){
        if(wasTable==isTable||!(chunk.getLevel() instanceof ServerLevel level))return;
        var data=worlds.get(level);
        if(data==null){if(!SableLifeServerConfig.wildEncountersEnabled()||SableLifeServerConfig.wildWorkbenchProtectionRadius()==0)return;data=get(level);}
        long key=chunk.getPos().toLong();int delta=isTable?1:-1;data.updates++;
        var columns=data.records.get(key);
        if(columns!=null){columns.add(((pos.getZ()&15)<<4)+(pos.getX()&15),delta);data.setDirty();}
        var scan=data.pending.get(key);
        if(scan!=null&&scan.chunk==chunk)scan.changed(pos,delta);
        else if(data.ready.get(key)!=chunk)data.enqueue(chunk);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!SableLifeServerConfig.wildEncountersEnabled()||SableLifeServerConfig.wildWorkbenchProtectionRadius()==0)return;
        int[] budget={SECTION_PROBES_PER_TICK,BLOCK_READS_PER_TICK};
        var levels=event.getServer().getAllLevels();
        for(var level:levels){var data=worlds.get(level);if(data!=null)data.advance(budget);}
    }
    private void advance(int[] budget){
        while(!pending.isEmpty()&&budget[0]>0&&budget[1]>0){
            var first=pending.entrySet().iterator().next();long key=first.getKey();var scan=first.getValue();var sections=scan.chunk.getSections();
            while(scan.cursor<sections.length*4096&&budget[0]>0&&budget[1]>0){
                int section=scan.cursor/4096,local=scan.cursor&4095;
                if(local==0){
                    budget[0]--;sectionProbes++;
                    if(!sections[section].maybeHas(s->s.is(Blocks.CRAFTING_TABLE))){scan.cursor+=4096;continue;}
                }
                if(sections[section].getBlockState(local&15,local>>8,(local>>4)&15).is(Blocks.CRAFTING_TABLE))scan.columns.add(local&255,1);
                scan.cursor++;budget[1]--;blockReads++;
            }
            if(scan.cursor<sections.length*4096)return;
            records.put(key,scan.columns);ready.put(key,scan.chunk);pending.remove(key);completed++;setDirty();
            while(records.size()>MAX_RECORDS){var oldest=records.entrySet().iterator();long evicted=oldest.next().getKey();oldest.remove();ready.remove(evicted);}
        }
    }

    /** Empty = allowed, otherwise a localized placement failure suffix. No block scans or chunk loads. */
    public static String check(ServerLevel level,AABB footprint){
        int radius=SableLifeServerConfig.wildWorkbenchProtectionRadius();if(radius==0)return "";
        var data=get(level);data.queries++;boolean unknown=false;
        int minX=(int)Math.floor(footprint.minX-radius)>>4,maxX=(int)Math.floor(footprint.maxX+radius)>>4;
        int minZ=(int)Math.floor(footprint.minZ-radius)>>4,maxZ=(int)Math.floor(footprint.maxZ+radius)>>4;
        for(int cx=minX;cx<=maxX;cx++)for(int cz=minZ;cz<=maxZ;cz++){
            double chunkDx=Math.max(0,Math.max(footprint.minX-((cx<<4)+16),(cx<<4)-footprint.maxX));
            double chunkDz=Math.max(0,Math.max(footprint.minZ-((cz<<4)+16),(cz<<4)-footprint.maxZ));
            if(chunkDx*chunkDx+chunkDz*chunkDz>(double)radius*radius)continue;
            long key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);var columns=data.records.get(key);
            var chunk=level.getChunkSource().getChunkNow(cx,cz);
            if(chunk!=null&&(columns==null||data.ready.get(key)!=chunk)){data.enqueue(chunk);unknown=true;}
            if(columns==null){unknown=true;continue;}
            if(columns.counts==null)continue;
            for(int column=0;column<256;column++)if(columns.counts[column]>0){
                data.columnChecks++;double x=(cx<<4)+(column&15)+.5,z=(cz<<4)+(column>>4)+.5;
                double dx=Math.max(0,Math.max(footprint.minX-x,x-footprint.maxX));
                double dz=Math.max(0,Math.max(footprint.minZ-z,z-footprint.maxZ));
                if(dx*dx+dz*dz<=(double)radius*radius)return "workbench";
            }
        }
        return unknown?"workbench_pending":"";
    }

    public Map<String,Long> statistics(){return Map.of("records",(long)records.size(),"pending",(long)pending.size(),"ready",(long)ready.size(),
            "sectionProbes",sectionProbes,"blockReads",blockReads,"queries",queries,"columnChecks",columnChecks,"updates",updates,"completed",completed);}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var rows=new ListTag();for(var entry:records.entrySet()){
            var row=new CompoundTag();row.putLong("Chunk",entry.getKey());if(entry.getValue().counts!=null)row.putIntArray("Columns",entry.getValue().counts);rows.add(row);
        }tag.put("Chunks",rows);return tag;
    }
    public static WildWorkbenchProtection load(CompoundTag tag,HolderLookup.Provider registries){
        var data=new WildWorkbenchProtection();for(var value:tag.getList("Chunks",10)){
            if(data.records.size()>=MAX_RECORDS)break;var row=(CompoundTag)value;var columns=new Columns();var counts=row.getIntArray("Columns");
            if(counts.length==256){columns.counts=counts;for(int i=0;i<counts.length;i++)counts[i]=Math.clamp(counts[i],0,65536);}
            data.records.put(row.getLong("Chunk"),columns);
        }return data;
    }
}
