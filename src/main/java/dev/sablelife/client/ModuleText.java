package dev.sablelife.client;

import dev.sablelife.api.module.ModuleRegistry;
import dev.sablelife.module.DatapackModules;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class ModuleText {
    static String nameKey(String type) {
        var addon = ModuleRegistry.find(type);
        return addon != null ? addon.nameKey() : "dashboard.sablelife.type_" + type;
    }

    static Component name(String type) {
        var datapack = DatapackModules.clientPresentation(type);
        if (datapack != null) return literal(datapack.name());
        String key = nameKey(type);
        return type.indexOf(':') < 0 ? Component.translatable(key) : Component.translatableWithFallback(key, type);
    }

    static Component description(String type) {
        var datapack = DatapackModules.clientPresentation(type);
        if (datapack != null) return literal(datapack.description());
        var addon = ModuleRegistry.find(type);
        if (addon != null) return Component.translatableWithFallback(addon.descriptionKey(), type);
        return Component.translatable(type.indexOf(':') < 0 ? "dashboard.sablelife.description_" + type
                : "dashboard.sablelife.status_unknown");
    }

    static Component literal(DatapackModules.Text text) {
        return Component.literal(text.forLocale(Minecraft.getInstance().getLanguageManager().getSelected()));
    }

    private ModuleText() {}
}
