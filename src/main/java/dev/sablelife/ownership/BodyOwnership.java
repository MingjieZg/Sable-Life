package dev.sablelife.ownership;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-dimension block ownership survives core removal, chunk unloads and Sable assembly transfers. */
public final class BodyOwnership extends SavedData {
    public static final ThreadLocal<Integer> MOVING = ThreadLocal.withInitial(() -> 0);
    private final Map<Long, UUID> owners = new HashMap<>();
    private final Map<Long, UUID> parts = new HashMap<>();
    private final Map<Long,java.util.Set<Long>> byChunk=new HashMap<>();
    private final Map<UUID, java.util.Set<Long>> byOwner = new HashMap<>(), byPart = new HashMap<>();
    private static final class Cursor {
        final java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
        final java.util.Set<Long> queued = new java.util.HashSet<>();
        void add(long position) { if (queued.add(position)) queue.addLast(position); }
        long next() { long position=queue.removeFirst();queued.remove(position);return position; }
    }
    private final Map<UUID,Cursor> ownerCursors=new HashMap<>(), partCursors=new HashMap<>();
    private static void addIndex(Map<UUID,java.util.Set<Long>> index,UUID id,long position) { index.computeIfAbsent(id,k->new java.util.HashSet<>()).add(position); }
    private static void removeIndex(Map<UUID,java.util.Set<Long>> index,UUID id,long position) { if(id==null)return;var positions=index.get(id);if(positions!=null&&positions.remove(position)&&positions.isEmpty())index.remove(id); }
    private final java.util.Set<Long> removedBlocks = new java.util.HashSet<>();
    /** Keep ownership through vanilla drop callbacks, then retire confirmed empty coordinates. */
    public void noteRemoved(BlockPos pos) {
        if (owner(pos) != null && removedBlocks.add(pos.asLong())) setDirty();
    }
    public void flushRemoved(ServerLevel level) {
        for (var key : java.util.List.copyOf(removedBlocks)) {
            var pos = BlockPos.of(key);
            // An emptied Sable plot can already be gone; the removal was observed while loaded.
            if (!level.hasChunkAt(pos) || level.getBlockState(pos).isAir()) forget(pos);
            else { removedBlocks.remove(key); setDirty(); }
        }
    }
    private static long chunkKey(BlockPos pos){return net.minecraft.world.level.ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4);}
    java.util.Set<Long> trackedChunks(){return byChunk.keySet();}
    java.util.Set<Long> chunkRecords(long chunk){return byChunk.getOrDefault(chunk,java.util.Set.of());}
    boolean matchesCleanup(BlockPos pos,UUID id,boolean part){return id.equals(part?parts.get(pos.asLong()):owners.get(pos.asLong()));}
    java.util.Set<Long> cleanupPositions(UUID id,boolean part){return (part?byPart:byOwner).getOrDefault(id,java.util.Set.of());}
    void finishCleanup(UUID id,boolean part){if(cleanupPositions(id,part).isEmpty())(part?partCursors:ownerCursors).remove(id);}
    public UUID part(BlockPos pos) { return parts.get(pos.asLong()); }
    /** Registered bodies, including reconstructed parts whose actor connection is not ready yet. */
    public java.util.Set<UUID> bodyIds(ServerLevel level,UUID creature){
        var result=new java.util.HashSet<UUID>();
        for(var key:byOwner.getOrDefault(creature,java.util.Set.of())){
            var body=Sable.HELPER.getContaining(level,BlockPos.of(key));
            if(body!=null&&!body.isRemoved())result.add(body.getUniqueId());
        }
        return result;
    }
    /** The value flags bodies claimed by a limb-loot job. */
    Map<UUID, Boolean> limbBodies(ServerLevel level, UUID creature) {
        var result = new HashMap<UUID, Boolean>();
        for (long position : byOwner.getOrDefault(creature, java.util.Set.of())) {
            var pos = BlockPos.of(position);
            var body = Sable.HELPER.getContaining(level, pos);
            if (body == null || body.isRemoved()) continue;
            result.merge(body.getUniqueId(), parts.containsKey(position), Boolean::logicalOr);
        }
        return result;
    }
    public boolean hasPart(UUID id) { return byPart.containsKey(id); }
    public boolean hasOwnedWithin(ServerLevel level,UUID owner,java.util.Set<UUID> bodies){
        for(var position:byOwner.getOrDefault(owner,java.util.Set.of())){var body=Sable.HELPER.getContaining(level,BlockPos.of(position));if(body!=null&&bodies.contains(body.getUniqueId()))return true;}return false;
    }
    public void bindPart(BlockPos pos, UUID id) {
        if (id == null || owner(pos) == null) return;
        long position=pos.asLong();var previous=parts.put(position,id);
        if (!id.equals(previous)) { removeIndex(byPart,previous,position);addIndex(byPart,id,position);var cursor=partCursors.get(id);if(cursor!=null)cursor.add(position);setDirty(); }
    }
    /** Preserve the creature identity; add a separate cleanup group for this disconnected fragment. */
    public boolean claimPart(ServerLevel level, BlockPos marker, UUID creature, UUID part) {
        var root = Sable.HELPER.getContaining(level, marker);
        if (root == null) return false;
        var connected = java.util.Set.copyOf(SubLevelHelper.getConnectedChain(root));
        var positions = byOwner.getOrDefault(creature,java.util.Set.of()).stream().filter(position -> connected.contains(Sable.HELPER.getContaining(level,BlockPos.of(position)))).toList();
        if (positions.isEmpty() || positions.stream().anyMatch(parts::containsKey)) return false;
        positions.forEach(position -> bindPart(BlockPos.of(position),part)); return true;
    }
    public static BodyOwnership get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(BodyOwnership::new, BodyOwnership::load), "sablelife_bodies");
    }
    public static boolean protects(Level level, BlockPos pos) {
        return level instanceof ServerLevel server && get(server).owner(pos) != null;
    }
    public UUID owner(BlockPos pos) { return owners.get(pos.asLong()); }
    public boolean hasOwned(UUID creature) { return byOwner.containsKey(creature); }
    public java.util.List<HeartBlockEntity> loadedHearts(ServerLevel level) {
        return LoadedCoreIndex.all(level).stream().filter(heart -> heart.creatureId().equals(owner(heart.getBlockPos()))).toList();
    }

    public boolean hasLoadedHeart(ServerLevel level, UUID creature) {
        return loadedHeart(level, creature) != null;
    }
    public HeartBlockEntity loadedHeart(ServerLevel level, UUID creature) {
        var heart = LoadedCoreIndex.find(level, creature);
        return heart != null && creature.equals(owner(heart.getBlockPos())) ? heart : null;
    }

    public void bind(BlockPos pos, UUID creature) {
        if (removedBlocks.remove(pos.asLong())) setDirty();
        if (owners.putIfAbsent(pos.asLong(), creature) == null) { addIndex(byOwner,creature,pos.asLong());byChunk.computeIfAbsent(chunkKey(pos),k->new java.util.HashSet<>()).add(pos.asLong());var cursor=ownerCursors.get(creature);if(cursor!=null)cursor.add(pos.asLong());setDirty(); }
    }
    public void forget(BlockPos pos) {
        if (removedBlocks.remove(pos.asLong())) setDirty();
        var part=parts.remove(pos.asLong());if(part!=null){removeIndex(byPart,part,pos.asLong());setDirty();}
        var records=byChunk.get(chunkKey(pos));if(records!=null){records.remove(pos.asLong());if(records.isEmpty())byChunk.remove(chunkKey(pos));}
        var owner=owners.remove(pos.asLong());if(owner!=null){removeIndex(byOwner,owner,pos.asLong());setDirty();}
    }
    public void release(UUID creature) {
        for(var position:java.util.List.copyOf(byOwner.getOrDefault(creature,java.util.Set.of())))forget(BlockPos.of(position));
        ownerCursors.remove(creature);
    }
    public void cleanLoaded(ServerLevel level, UUID creature, int budget) {
        clean(level, creature, budget, false);
    }
    public void cleanLoaded(ServerLevel level, UUID creature, int budget, dev.sablelife.heart.DeathPresentation effect) {
        clean(level,creature,budget,false,effect);
    }
    public void cleanPart(ServerLevel level, UUID part, int budget) { clean(level, part, budget, true); }
    private void clean(ServerLevel level, UUID creature, int budget, boolean part) {
        clean(level,creature,budget,part,null);
    }
    private void clean(ServerLevel level, UUID creature, int budget, boolean part, dev.sablelife.heart.DeathPresentation effect) {
        cleanBudgeted(level,creature,budget,Math.max(1024,budget),part,effect);
    }
    public record CleanupResult(int removed,int visited) {}
    /** Queue only this owner's records. Unloaded positions rotate without scanning unrelated creatures. */
    public CleanupResult cleanBudgeted(ServerLevel level,UUID id,int budget,int visits,boolean part,dev.sablelife.heart.DeathPresentation effect) {return cleanBudgeted(level,id,budget,visits,part,effect,false);}
    /** Whole-body deletion is atomic and may consume the remaining block budget in one operation. */
    public CleanupResult cleanBudgeted(ServerLevel level,UUID id,int budget,int visits,boolean part,dev.sablelife.heart.DeathPresentation effect,boolean wholeBodies) {
        var index=part?byPart:byOwner;var cursors=part?partCursors:ownerCursors;
        var positions=index.get(id);if(positions==null){cursors.remove(id);return new CleanupResult(0,0);}
        if(budget<=0||visits<=0)return new CleanupResult(0,0);
        var cursor=cursors.computeIfAbsent(id,key->{var c=new Cursor();positions.forEach(c::add);return c;});
        var attemptedBodies=new java.util.HashSet<UUID>();
        int examined=0,popped=0,removed=0,smoke=16,limit=Math.min(visits,cursor.queue.size());
        while(popped<limit&&examined<visits&&removed<budget&&!cursor.queue.isEmpty()) {
            long position=cursor.next();examined++;popped++;var pos=BlockPos.of(position);
            if(!id.equals(part?parts.get(position):owners.get(position)))continue;
            if(!level.hasChunkAt(pos)){cursor.add(position);continue;}
            if(level.getBlockState(pos).isAir()){forget(pos);removed++;continue;}
            if(wholeBodies&&Sable.HELPER.getContaining(level,pos) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body&&attemptedBodies.add(body.getUniqueId())) {
                var result=SableBodyDeletion.attempt(level,this,body,id,part,Math.max(0,visits-examined),effect);examined+=result.visits();
                if(result.removed()>0){removed+=result.removed();continue;}
                if(examined>=visits){cursor.add(position);break;}
            }
            var point=dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,pos.getCenter());boolean erased=eraseOwnedBlock(level,pos);
            if(erased){
                if(smoke-->0){if(effect!=null)effect.emit(level,point);else dev.sablelife.module.ModuleParticles.send(level,net.minecraft.core.particles.ParticleTypes.POOF,point.x,point.y,point.z,6,.35,.35,.35,.04);}
                forget(pos);removed++;
            }else if(id.equals(part?parts.get(position):owners.get(position)))cursor.add(position);
        }
        if(!index.containsKey(id))cursors.remove(id);
        return new CleanupResult(removed,examined);
    }
    public boolean breakMarkedBlock(ServerLevel level, BlockPos pos, UUID creature, dev.ryanhcode.sable.sublevel.SubLevel body) {
        if (body.isRemoved() || !level.hasChunkAt(pos) || Sable.HELPER.getContaining(level, pos) != body
                || !creature.equals(owner(pos)) || part(pos) != null) return false;
        var state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof dev.sablelife.heart.HeartBlock || !eraseOwnedBlock(level, pos)) return false;
        forget(pos);
        return true;
    }
    static boolean eraseOwnedBlock(ServerLevel level,BlockPos pos) {
        BodyProtection.CLEANING.set(BodyProtection.CLEANING.get()+1);
        try {
            dev.sablelife.compat.BodyDecorationCleanup.discardForBlock(level,pos);
            dev.sablelife.compat.MountedContraptionCleanup.discardForDeath(level.getBlockEntity(pos));
            return level.removeBlock(pos,false);
        } finally {
            BodyProtection.CLEANING.set(BodyProtection.CLEANING.get()-1);
        }
    }
    /** Release only the selected orphan fragment, never the distant active parent. */
    public boolean releaseFragment(ServerLevel level, BlockPos clicked) {
        UUID creature = owner(clicked);
        if (creature == null) return false;
        var affectedParts = new java.util.HashSet<UUID>();
        var root = Sable.HELPER.getContaining(level, clicked);
        if (root == null) { if(part(clicked)!=null)affectedParts.add(part(clicked));forget(clicked);cancelReleasedParts(level,affectedParts);return true; }
        var connected = java.util.Set.copyOf(SubLevelHelper.getConnectedChain(root));
        var positions=byOwner.getOrDefault(creature,java.util.Set.of()).stream()
                .filter(position->connected.contains(Sable.HELPER.getContaining(level,BlockPos.of(position)))).toList();
        for(var position:positions){var pos=BlockPos.of(position);if(part(pos)!=null)affectedParts.add(part(pos));forget(pos);}
        boolean changed=!positions.isEmpty();
        cancelReleasedParts(level, affectedParts);
        return changed;
    }
    private void cancelReleasedParts(ServerLevel level, java.util.Set<UUID> ids) {
        for (var id : ids) {
            boolean remains = false;
            for (var dimension : level.getServer().getAllLevels()) remains |= get(dimension).hasPart(id);
            if (!remains) LifeLifecycle.get(level.getServer()).cancel(id);
        }
    }
    public void capture(HeartBlockEntity heart) {
        LoadedCoreIndex.observe(heart);
        var level = (ServerLevel) heart.getLevel();
        var root = Sable.HELPER.getContaining(level, heart.getBlockPos());
        if (root == null) { bind(heart.getBlockPos(), heart.creatureId()); return; }
        for (var body : SubLevelHelper.getConnectedChain(root)) {
            for (var holder : body.getPlot().getLoadedChunks()) {
                var chunk = holder.getChunk();
                var sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    var section = sections[i];
                    if (section.hasOnlyAir()) continue;
                    int bottom = chunk.getSectionYFromSectionIndex(i) << 4;
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        if (!section.getBlockState(x, y, z).isAir())
                            bind(new BlockPos(chunk.getPos().getMinBlockX() + x, bottom + y, chunk.getPos().getMinBlockZ() + z), heart.creatureId());
                    }
                }
            }
        }
    }
    public static BodyOwnership load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new BodyOwnership();
        var entries = tag.getList("Blocks", 10);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            if (entry.hasUUID("Owner")) {
                long position=entry.getLong("Pos");var owner=entry.getUUID("Owner");data.owners.put(position,owner);addIndex(data.byOwner,owner,position);data.byChunk.computeIfAbsent(chunkKey(BlockPos.of(position)),k->new java.util.HashSet<>()).add(position);
                if (entry.hasUUID("Part")) { var part=entry.getUUID("Part");data.parts.put(position,part);addIndex(data.byPart,part,position); }
                if (entry.getBoolean("Removed")) data.removedBlocks.add(entry.getLong("Pos"));
            }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        owners.forEach((pos, owner) -> {
            var entry = new CompoundTag();
            entry.putLong("Pos", pos);
            entry.putUUID("Owner", owner);
            if (parts.containsKey(pos)) entry.putUUID("Part", parts.get(pos));
            if (removedBlocks.contains(pos)) entry.putBoolean("Removed", true);
            entries.add(entry);
        });
        tag.put("Blocks", entries);
        return tag;
    }
}

