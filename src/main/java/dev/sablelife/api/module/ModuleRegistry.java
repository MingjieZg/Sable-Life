package dev.sablelife.api.module;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;

/** Register definitions from the addon mod constructor on both physical sides. */
public final class ModuleRegistry {
    private static final ConcurrentSkipListMap<String, ModuleDefinition> DEFINITIONS = new ConcurrentSkipListMap<>();
    private static final Collection<ModuleDefinition> VALUES = Collections.unmodifiableCollection(DEFINITIONS.values());

    public static void register(ModuleDefinition definition) {
        Objects.requireNonNull(definition);
        if (definition.id().getPath().startsWith("datapack/")) {
            throw new IllegalArgumentException("Module paths starting with datapack/ are reserved: " + definition.id());
        }
        if (DEFINITIONS.putIfAbsent(definition.id().toString(), definition) != null) {
            throw new IllegalArgumentException("Module already registered: " + definition.id());
        }
    }

    public static ModuleDefinition find(String id) {
        return id.indexOf(':') < 0 ? null : DEFINITIONS.get(id);
    }

    public static Collection<ModuleDefinition> definitions() {
        return VALUES;
    }

    private ModuleRegistry() {}
}
