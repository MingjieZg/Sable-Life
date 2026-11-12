package dev.sablelife.testing;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.DatapackModuleCatalog;
import dev.sablelife.module.DatapackModules;
import dev.sablelife.module.ModuleTypes;
import dev.sablelife.module.VirtualModule;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

final class DatapackFullCatalogScenario {
    static final boolean ENABLED = Boolean.getBoolean("sablelife.datapackFullCatalog");
    private static final int MODULES = 64;
    private static final int OPTIONS = 8;
    private static final String PREFIX = "sablelife_capacity:datapack/";
    private static final Path MODULE_DIRECTORY = Path.of("capacity/data/sablelife_capacity/sablelife/modules");
    private static final String RELOADED_NAME = "新".repeat(64);
    static final String LAST_MODULE = type(MODULES - 1);
    static final String LAST_PROPERTY = "addon_" + optionKey(OPTIONS - 1);
    private static long reloadStarted;
    private static CompoundTag saved;

    static int verifyClient(RegistryAccess registries, String role, int observed) {
        if (observed == 2) return observed;
        var tail = DatapackModules.clientPresentation(LAST_MODULE);
        if (tail == null) return observed;
        boolean reloaded = tail.name().chinese().equals(RELOADED_NAME);
        if (reloaded != (observed == 1)) return observed;

        var entries = new ArrayList<DatapackModules.Presentation>(MODULES);
        for (int module = 0; module < MODULES; module++) {
            var entry = DatapackModules.clientPresentation(type(module));
            check(entry != null && entry.options().size() == OPTIONS, "Incomplete module " + module);
            check(entry.name().english().equals(text(module, 0, false, 64))
                            && entry.name().chinese().equals(reloaded && module == MODULES - 1
                                    ? RELOADED_NAME : text(module, 0, true, 64)), "Wrong localized module name " + module);
            check(entry.description().english().equals(text(module, 1, false, 192))
                            && entry.description().chinese().equals(text(module, 1, true, 192)), "Wrong module description " + module);
            for (int option = 0; option < OPTIONS; option++) {
                var label = entry.optionLabel(optionKey(option));
                check(label != null && label.english().equals(text(module, option + 2, false, 64))
                                && label.chinese().equals(text(module, option + 2, true, 64)), "Wrong option label " + option);
            }
            entries.add(entry);
        }
        check(DatapackModules.clientPresentation(PREFIX + "a_invalid") == null
                        && DatapackModules.clientPresentation(PREFIX + "z_excess") == null, "Catalog limit was not enforced");

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            DatapackModuleCatalog.CODEC.encode(buffer, new DatapackModuleCatalog(entries));
            System.out.printf("DATAPACK_CAPACITY %s %s: 64 modules, 512 options, %d uncompressed catalog bytes%n",
                    role, reloaded ? "reload" : "login", buffer.readableBytes());
        } finally {
            buffer.release();
        }
        DatapackAlarmScenario.observe(role, observed + 1);
        return observed + 1;
    }

    static boolean verifyReload(MinecraftServer server, HeartBlockEntity core) {
        var sample = core.modules().all().stream().filter(module -> module.type().equals(LAST_MODULE)).findFirst().orElse(null);
        if (reloadStarted == 0) {
            if (!DatapackAlarmScenario.observed(server, 1) || sample == null || sample.value(LAST_PROPERTY) != 1.75) return false;
            for (int module = 0; module < MODULES; module++) {
                var definition = ModuleTypes.find(type(module));
                check(definition != null && definition.options().size() == OPTIONS, "Server rejected module " + module);
            }
            check(ModuleTypes.find(PREFIX + "a_invalid") == null && ModuleTypes.find(PREFIX + "z_excess") == null,
                    "Server accepted a definition outside the limits");
            saved = sample.save();
            Path file = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(MODULE_DIRECTORY)
                    .resolve(fileStem(MODULES - 1) + ".json");
            try {
                String json = Files.readString(file);
                String name = text(MODULES - 1, 0, true, 64);
                check(json.contains(name), "Reload fixture did not contain the expected name");
                Files.writeString(file, json.replace(name, RELOADED_NAME));
            } catch (IOException failure) {
                throw new IllegalStateException("Could not update full catalog fixture", failure);
            }
            reloadStarted = System.nanoTime();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4), "reload");
            return false;
        }
        if (!DatapackAlarmScenario.observed(server, 2)) return false;
        var restored = VirtualModule.load(saved);
        check(sample != null && sample.save().equals(saved) && restored.known()
                        && restored.value(LAST_PROPERTY) == 1.75 && restored.save().equals(saved),
                "Reload changed the saved eighth option");
        System.out.printf("DATAPACK_CAPACITY reload acknowledged by both clients in %d ms%n",
                (System.nanoTime() - reloadStarted) / 1_000_000);
        System.out.println("DATAPACK_CAPACITY login and reload PASS");
        return true;
    }

    private static String type(int index) {
        return PREFIX + fileStem(index);
    }

    private static String fileStem(int index) {
        String prefix = String.format(Locale.ROOT, "m%02d", index);
        return prefix + "x".repeat(64 - PREFIX.length() - prefix.length());
    }

    private static String optionKey(int index) {
        return "p" + index + "x".repeat(56);
    }

    private static String text(int module, int field, boolean chinese, int length) {
        return String.valueOf((char) (0x4e00 + module * 20 + field * 2 + (chinese ? 1 : 0))).repeat(length);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private DatapackFullCatalogScenario() {}
}
