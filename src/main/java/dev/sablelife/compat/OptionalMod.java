package dev.sablelife.compat;

import java.io.IOException;
import java.nio.file.Files;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.locating.IModFile;

/** Read optional dependency metadata and class files without loading the mod's classes. */
public final class OptionalMod {
    public static String version(String id) {
        var list = FMLLoader.getLoadingModList();
        if (list == null) return "";
        return list.getMods().stream().filter(mod -> mod.getModId().equals(id))
                .findFirst().map(mod -> mod.getVersion().toString()).orElse("");
    }

    public static byte[] classBytes(String id, String name) {
        var list = FMLLoader.getLoadingModList();
        var info = list == null ? null : list.getModFileById(id);
        return info == null ? null : readClass(info.getFile(), name);
    }

    public static byte[] classBytes(String name) {
        var list = FMLLoader.getLoadingModList();
        if (list != null) {
            for (var mod : list.getMods()) {
                var bytes = readClass(mod.getOwningFile().getFile(), name);
                if (bytes != null) return bytes;
            }
        }
        try (var stream = OptionalMod.class.getResourceAsStream("/" + name + ".class")) {
            return stream == null ? null : stream.readAllBytes();
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private static byte[] readClass(IModFile file, String name) {
        try {
            var path = file.findResource(name + ".class");
            return Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private OptionalMod() {}
}
