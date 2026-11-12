package dev.sablelife.ownership;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks detached Sable bodies without taking ownership away from limb loot or death cleanup. */
public final class DetachedLimbCleanup {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final int MAX_SECONDS = 600;
    private static final int MAX_PENDING = 256;
    private static final int SCAN_INTERVAL = 20;
    private final Map<UUID, Long> deadlines = new LinkedHashMap<>();
    private boolean enabled;
    private int delay = 3;

    public DetachedLimbCleanup() { this(false); }

    public DetachedLimbCleanup(boolean enabled) { this.enabled = enabled; }

    public boolean enabled() { return enabled; }
    public int delay() { return delay; }

    public boolean edit(String key, double value) {
        if (key.equals("limbCleanup") && (value == 0 || value == 1)) {
            enabled = value == 1;
            if (!enabled) clear();
            return true;
        }
        if (key.equals("limbCleanupDelay") && value == Math.rint(value) && value >= 1 && value <= MAX_SECONDS) {
            delay = (int) value;
            return true;
        }
        return false;
    }

    public void clear() { deadlines.clear(); }

    public void tick(HeartBlockEntity heart, ServerLevel level) {
        long now = level.getGameTime();
        if (!enabled || heart.health().state() != HeartState.ALIVE
                || Math.floorMod(now, SCAN_INTERVAL) != Math.floorMod(heart.creatureId().hashCode(), SCAN_INTERVAL)) return;
        var root = Sable.HELPER.getContaining(level, heart.getBlockPos());
        var container = SubLevelContainer.getContainer(level);
        if (root == null || root.isRemoved() || container == null) return;

        var ownership = BodyOwnership.get(level);
        var bodies = ownership.limbBodies(level, heart.creatureId());
        var eligible = new LinkedHashMap<UUID, ServerSubLevel>();
        var checked = new HashMap<UUID, Boolean>();
        for (var id : bodies.keySet()) {
            if (!(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved()) continue;
            if (!checked.containsKey(id)) {
                var chain = SubLevelHelper.getConnectedChain(body);
                boolean detached = !chain.contains(root) && chain.stream().allMatch(member ->
                        !member.isRemoved() && Boolean.FALSE.equals(bodies.get(member.getUniqueId())))
                        && SableBodyProbe.findConnectedHearts(level, body.getPlot().getCenterBlock()).isEmpty();
                for (var member : chain) checked.put(member.getUniqueId(), detached);
            }
            if (Boolean.TRUE.equals(checked.get(id))) eligible.put(id, body);
        }

        boolean changed = deadlines.keySet().retainAll(eligible.keySet());
        for (var id : eligible.keySet()) {
            if (deadlines.size() >= MAX_PENDING) break;
            if (deadlines.putIfAbsent(id, now + delay * 20L) == null) changed = true;
        }
        int budget = 16384;
        for (var iterator = deadlines.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (entry.getValue() > now) continue;
            var body = eligible.get(entry.getKey());
            SableBodyDeletion.Result result;
            try {
                result = SableBodyDeletion.attempt(level, ownership, body, heart.creatureId(), false, budget, null);
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not clean detached Sable body {}", entry.getKey(), failure);
                entry.setValue(now + 100);
                changed = true;
                continue;
            }
            budget -= result.visits();
            if (result.removed() > 0) {
                iterator.remove();
                changed = true;
                break;
            }
            if (budget <= 0) {
                // A candidate that cannot be removed must not starve later bodies.
                var id = entry.getKey();
                long due = entry.getValue();
                iterator.remove();
                deadlines.put(id, due);
                changed = true;
                break;
            }
        }
        if (changed) heart.setChanged();
    }

    public CompoundTag saveSettings() {
        var tag = new CompoundTag();
        tag.putBoolean("Enabled", enabled);
        tag.putInt("Delay", delay);
        return tag;
    }

    public CompoundTag save() {
        var tag = saveSettings();
        var rows = new ListTag();
        deadlines.forEach((id, due) -> {
            var row = new CompoundTag();
            row.putUUID("Body", id);
            row.putLong("Due", due);
            rows.add(row);
        });
        tag.put("Pending", rows);
        return tag;
    }

    public void loadSettings(CompoundTag tag) {
        clear();
        // Older saves and blueprints without a cleanup setting used the disabled default.
        enabled = tag.getBoolean("Enabled");
        delay = tag.contains("Delay") ? Math.clamp(tag.getInt("Delay"), 1, MAX_SECONDS) : 3;
    }

    public void load(CompoundTag tag) {
        loadSettings(tag);
        if (!enabled) return;
        for (var value : tag.getList("Pending", 10)) {
            if (deadlines.size() >= MAX_PENDING) break;
            var row = (CompoundTag) value;
            if (row.hasUUID("Body")) deadlines.put(row.getUUID("Body"), row.getLong("Due"));
        }
    }
}
