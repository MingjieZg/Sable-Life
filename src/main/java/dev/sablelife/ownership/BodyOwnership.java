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
    public UUID part(BlockPos pos) { return parts.get(pos.asLong()); }
    public boolean hasPart(UUID id) { return parts.containsValue(id); }
    public boolean hasOwnedWithin(ServerLevel level,UUID owner,java.util.Set<UUID> bodies){
        for(var entry:owners.entrySet())if(owner.equals(entry.getValue())){var body=Sable.HELPER.getContaining(level,BlockPos.of(entry.getKey()));if(body!=null&&bodies.contains(body.getUniqueId()))return true;}return false;
    }
    public void bindPart(BlockPos pos, UUID id) { if (id != null && owner(pos) != null) { parts.put(pos.asLong(), id); setDirty(); } }
    /** Preserve the creature identity; add a separate cleanup group for this disconnected fragment. */
    public boolean claimPart(ServerLevel level, BlockPos marker, UUID creature, UUID part) {
        var root = Sable.HELPER.getContaining(level, marker);
        if (root == null) return false;
        var connected = SubLevelHelper.getConnectedChain(root);
        var positions = owners.entrySet().stream().filter(e -> creature.equals(e.getValue())
                && connected.contains(Sable.HELPER.getContaining(level, BlockPos.of(e.getKey())))).map(Map.Entry::getKey).toList();
        if (positions.isEmpty() || positions.stream().anyMatch(parts::containsKey)) return false;
        positions.forEach(pos -> parts.put(pos, part)); setDirty(); return true;
    }
    public static BodyOwnership get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(BodyOwnership::new, BodyOwnership::load), "sablelife_bodies");
    }
    public static boolean protects(Level level, BlockPos pos) {
        return level instanceof ServerLevel server && get(server).owner(pos) != null;
    }
    public UUID owner(BlockPos pos) { return owners.get(pos.asLong()); }
    public boolean hasOwned(UUID creature) { return owners.containsValue(creature); }
    public java.util.List<HeartBlockEntity> loadedHearts(ServerLevel level) {
        var result=new java.util.ArrayList<HeartBlockEntity>(); var seen=new java.util.HashSet<UUID>();
        for(var entry:owners.entrySet()) {
            var pos=BlockPos.of(entry.getKey());
            if(level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof HeartBlockEntity heart && entry.getValue().equals(heart.creatureId()) && seen.add(heart.creatureId())) result.add(heart);
        }
        return result;
    }
    public boolean hasLoadedHeart(ServerLevel level, UUID creature) {
        return loadedHeart(level, creature) != null;
    }
    public HeartBlockEntity loadedHeart(ServerLevel level, UUID creature) {
        for (var entry : owners.entrySet()) {
            if (!creature.equals(entry.getValue())) continue;
            var pos = BlockPos.of(entry.getKey());
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof HeartBlockEntity heart
                    && creature.equals(heart.creatureId())) return heart;
        }
        return null;
    }
    public void bind(BlockPos pos, UUID creature) {
        if (removedBlocks.remove(pos.asLong())) setDirty();
        if (owners.putIfAbsent(pos.asLong(), creature) == null) setDirty();
    }
    public void forget(BlockPos pos) {
        if (removedBlocks.remove(pos.asLong())) setDirty();
        if (parts.remove(pos.asLong()) != null) setDirty();
        if (owners.remove(pos.asLong()) != null) setDirty();
    }
    public void release(UUID creature) {
        removedBlocks.removeIf(pos -> creature.equals(owners.get(pos)));
        parts.keySet().removeIf(pos -> creature.equals(owners.get(pos)));
        if (owners.values().removeIf(creature::equals)) setDirty();
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
        int smokeBudget = 16;
        for (var entry : java.util.List.copyOf(owners.entrySet())) {
            if (budget == 0) return;
            if (!creature.equals(part ? parts.get(entry.getKey()) : entry.getValue())) continue;
            var pos = BlockPos.of(entry.getKey());
            if (!level.hasChunkAt(pos)) continue;
            // Keep ownership present during onRemove so inventories cannot scatter ordinary materials.
            if (level.getBlockState(pos).isAir()) { forget(pos); budget--; continue; }
            var point = dev.sablelife.compat.sable.SableBodyProbe.toWorld(level, pos.getCenter());
            boolean removed;
            BodyProtection.CLEANING.set(BodyProtection.CLEANING.get() + 1);
            try { removed = level.removeBlock(pos, false); }
            finally { BodyProtection.CLEANING.set(BodyProtection.CLEANING.get() - 1); }
            if (removed) {
                if (smokeBudget-- > 0) {
                    if(effect!=null)effect.emit(level,point);
                    else dev.sablelife.module.ModuleParticles.send(level, net.minecraft.core.particles.ParticleTypes.POOF,
                        point.x, point.y, point.z, 6, 0.35, 0.35, 0.35, 0.04);
                }
                forget(pos); budget--;
            }
        }
    }
    /** Release only the selected orphan fragment, never the distant active parent. */
    public boolean releaseFragment(ServerLevel level, BlockPos clicked) {
        UUID creature = owner(clicked);
        if (creature == null) return false;
        var affectedParts = new java.util.HashSet<>(parts.values());
        var root = Sable.HELPER.getContaining(level, clicked);
        if (root == null) { forget(clicked); cancelReleasedParts(level, affectedParts); return true; }
        var connected = SubLevelHelper.getConnectedChain(root);
        boolean changed = owners.entrySet().removeIf(entry -> creature.equals(entry.getValue())
                && connected.contains(Sable.HELPER.getContaining(level, BlockPos.of(entry.getKey()))));
        if (changed) setDirty();
        parts.keySet().removeIf(pos -> !owners.containsKey(pos));
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
                data.owners.put(entry.getLong("Pos"), entry.getUUID("Owner"));
                if (entry.hasUUID("Part")) data.parts.put(entry.getLong("Pos"), entry.getUUID("Part"));
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

