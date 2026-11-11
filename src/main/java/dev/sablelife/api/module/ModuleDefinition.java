package dev.sablelife.api.module;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Defines a module that can be added through the Sable Life core dashboard. */
public record ModuleDefinition(ResourceLocation id, String nameKey, String descriptionKey,
        Category category, Set<Core> cores, MarkMode markMode, List<NumberOption> options,
        Supplier<? extends ModuleBehavior> behaviorFactory) {

    public ModuleDefinition {
        Objects.requireNonNull(id);
        if (id.toString().length() > 64 || id.getNamespace().equals("sablelife")) {
            throw new IllegalArgumentException("Use an addon module ID of at most 64 characters in your own namespace: " + id);
        }
        requireTranslationKey(nameKey);
        requireTranslationKey(descriptionKey);
        Objects.requireNonNull(category);
        Objects.requireNonNull(markMode);
        Objects.requireNonNull(behaviorFactory);
        cores = Set.copyOf(cores);
        if (cores.isEmpty()) throw new IllegalArgumentException("Select at least one supported core");
        options = List.copyOf(options);
        var keys = new HashSet<String>();
        for (var option : options) {
            if (!keys.add(option.key())) throw new IllegalArgumentException("Duplicate module option: " + option.key());
        }
    }

    private static void requireTranslationKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("A translation key is required");
    }

    public enum Category { SIGNAL, WEAPON, FUNCTION }
    public enum Core { HEART, REDSTONE, SURVIVAL }
    public enum MarkMode { NONE, POINT, AREA }

    /** Values are in the units shown in the dashboard. All bounds must be finite. */
    public record NumberOption(String key, String nameKey, double minimum, double maximum, double initial) {
        public NumberOption {
            // Dashboard actions allow 64 characters, including the internal "addon_" prefix.
            if (key == null || !key.matches("[a-z][a-z0-9_]{0,57}")) {
                throw new IllegalArgumentException("Invalid module option key: " + key);
            }
            requireTranslationKey(nameKey);
            if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || !Double.isFinite(initial)
                    || minimum > initial || initial > maximum) {
                throw new IllegalArgumentException("Invalid bounds for module option: " + key);
            }
        }
    }
}
