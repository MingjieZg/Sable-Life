package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import java.util.*;

/** Session memory per viewer/core; no item or world data is changed by browsing. */
public final class DashboardSelection {
    private static final Map<HeartBlockEntity, Map<UUID, UUID>> selected = new WeakHashMap<>();
    public static UUID get(HeartBlockEntity heart, UUID player) { return selected.getOrDefault(heart, Map.of()).get(player); }
    public static void set(HeartBlockEntity heart, UUID player, UUID module) {
        if (heart.modules().find(module) != null) selected.computeIfAbsent(heart, ignored -> new HashMap<>()).put(player, module);
    }
    private DashboardSelection() {}
}
