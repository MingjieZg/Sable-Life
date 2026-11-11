package dev.sablelife.module;

import dev.sablelife.api.module.ModuleDefinition;
import dev.sablelife.api.module.ModuleRegistry;

import java.util.stream.Stream;

/** Definitions visible to the logical side making the lookup. */
public final class ModuleTypes {
    public static ModuleDefinition find(String type) {
        if (!DatapackModules.isDatapackType(type)) return ModuleRegistry.find(type);
        var entry = DatapackModules.entry(type);
        return entry == null ? null : entry.definition();
    }

    static Stream<String> extensionIds() {
        return Stream.concat(ModuleRegistry.definitions().stream().map(definition -> definition.id().toString()),
                DatapackModules.current().entries().keySet().stream());
    }

    static boolean hasExtensions() {
        return !ModuleRegistry.definitions().isEmpty() || !DatapackModules.current().entries().isEmpty();
    }

    private ModuleTypes() {}
}
