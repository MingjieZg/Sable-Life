package dev.sablelife.module;

import java.util.Map;
import java.util.HashMap;

/** Independent producers share channels without erasing each other's signals. */
public final class ProgramSignals {
    private final Map<String, Map<Integer, Integer>> sources = new HashMap<>();
    public void set(String source, Map<Integer, Integer> values) {
        if (values.isEmpty()) sources.remove(source); else sources.put(source, Map.copyOf(values));
    }
    public Map<Integer, Integer> combined() {
        var result = new HashMap<Integer, Integer>();
        sources.values().forEach(values -> values.forEach((channel, power) -> result.merge(channel, power, Math::max)));
        return Map.copyOf(result);
    }
    public void clear() { sources.clear(); }
}
