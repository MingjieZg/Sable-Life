package dev.sablelife.module;

import java.util.List;
import java.util.UUID;

/** Pure weighted draw. A lone available move may repeat so the boss never deadlocks. */
public final class MoveChoice {
    public record Candidate(UUID id, int weight, boolean avoidRepeat) {}
    public static UUID choose(List<Candidate> candidates, UUID previous, double random) {
        boolean alternative = candidates.stream().anyMatch(c -> !c.id().equals(previous));
        var pool = candidates.stream().filter(c -> c.weight() > 0
                && !(alternative && c.avoidRepeat() && c.id().equals(previous))).toList();
        int total = pool.stream().mapToInt(Candidate::weight).sum();
        if (total == 0) return null;
        double draw = Math.clamp(random, 0, Math.nextDown(1.0)) * total;
        for (var c : pool) { draw -= c.weight(); if (draw < 0) return c.id(); }
        return pool.getLast().id();
    }
    private MoveChoice() {}
}
