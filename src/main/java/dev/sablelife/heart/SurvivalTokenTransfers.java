package dev.sablelife.heart;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** An item carries a one-use claim against the world that issued it. */
final class SurvivalTokenTransfers extends SavedData {
    private final Map<UUID, Long> unclaimed = new HashMap<>();

    static SurvivalTokenTransfers get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(SurvivalTokenTransfers::new, SurvivalTokenTransfers::load), "sablelife_token_transfers");
    }

    UUID issue(long balance) {
        UUID id = UUID.randomUUID();
        unclaimed.put(id, balance);
        setDirty();
        return id;
    }

    long redeem(UUID id) {
        Long balance = unclaimed.remove(id);
        if (balance == null) return 0;
        setDirty();
        return balance;
    }

    private static SurvivalTokenTransfers load(CompoundTag tag, HolderLookup.Provider registries) {
        var transfers = new SurvivalTokenTransfers();
        for (var entry : tag.getList("Claims", 10)) {
            var claim = (CompoundTag) entry;
            if (claim.hasUUID("Id") && claim.getLong("Balance") > 0)
                transfers.unclaimed.put(claim.getUUID("Id"), claim.getLong("Balance"));
        }
        return transfers;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        unclaimed.forEach((id, balance) -> {
            var claim = new CompoundTag();
            claim.putUUID("Id", id);
            claim.putLong("Balance", balance);
            entries.add(claim);
        });
        tag.put("Claims", entries);
        return tag;
    }
}
