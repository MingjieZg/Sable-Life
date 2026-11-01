package dev.sablelife.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.Resource;
import java.nio.file.*;
import java.util.*;

/** Minecraft caches are hashed/packed: export a browsable vanilla-only sound tree on demand. */
public final class VanillaSoundFolder {
    private static final java.util.concurrent.atomic.AtomicBoolean busy = new java.util.concurrent.atomic.AtomicBoolean();
    public static Path folder() { return dev.sablelife.sound.SoundLibrary.folder().resolveSibling("minecraft_sounds"); }
    public static List<ResourceLocation> sounds() {
        var manager = Minecraft.getInstance().getResourceManager();
        return manager.listResources("sounds", id -> id.getNamespace().equals("minecraft") && id.getPath().endsWith(".ogg")).keySet().stream()
                .filter(id -> manager.getResourceStack(id).stream().anyMatch(resource -> resource.source() instanceof VanillaPackResources))
                .sorted(Comparator.comparing(ResourceLocation::getPath)).toList();
    }
    /** Import a specific vanilla OGG variant, rather than its potentially randomized sound event. */
    public static String importSound(ResourceLocation id) throws java.io.IOException {
        if (!sounds().contains(id)) throw new java.io.IOException("Not a vanilla OGG resource");
        var resource = Minecraft.getInstance().getResourceManager().getResourceStack(id).stream()
                .filter(r -> r.source() instanceof VanillaPackResources).findFirst().orElseThrow(() -> new java.io.IOException("Sound not found"));
        byte[] bytes;
        try (var input = resource.open()) { bytes = input.readNBytes(16 * 1024 * 1024 + 1); }
        if (bytes.length < 4 || bytes.length > 16 * 1024 * 1024 || bytes[0] != 'O' || bytes[1] != 'g' || bytes[2] != 'g' || bytes[3] != 'S')
            throw new java.io.IOException("Invalid or oversized OGG");
        String filename = "mc_" + id.getPath().substring(7, id.getPath().length() - 4).replace('/', '_') + "_"
                + UUID.nameUUIDFromBytes(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".ogg";
        var root = dev.sablelife.sound.SoundLibrary.folder(); Files.createDirectories(root);
        var target = root.resolve(filename);
        if (!Files.exists(target)) {
            try (var files = Files.list(root)) {
                if (files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg")).count() >= 128)
                    throw new java.io.IOException("Custom sound library is full (128)");
            }
            var temporary = Files.createTempFile(root, ".mc-import-", ".tmp");
            try { Files.write(temporary, bytes); Files.move(temporary, target); }
            finally { Files.deleteIfExists(temporary); }
        }
        dev.sablelife.sound.SoundLibrary.prepare();
        String key = "sablelife:custom/" + UUID.nameUUIDFromBytes(filename.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!dev.sablelife.sound.SoundLibrary.contains(key)) throw new java.io.IOException("Imported sound could not be registered");
        return key;
    }
    public static void open() {
        if (!busy.compareAndSet(false, true)) return;
        var manager = Minecraft.getInstance().getResourceManager();
        var resources = new LinkedHashMap<ResourceLocation, Resource>();
        for (var id : manager.listResources("sounds", id -> id.getNamespace().equals("minecraft") && id.getPath().endsWith(".ogg")).keySet()) {
            for (var resource : manager.getResourceStack(id)) if (resource.source() instanceof VanillaPackResources) { resources.put(id, resource); break; }
        }
        var definitions = ResourceLocation.withDefaultNamespace("sounds.json");
        for (var resource : manager.getResourceStack(definitions)) if (resource.source() instanceof VanillaPackResources) { resources.put(definitions, resource); break; }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                var root = folder().toAbsolutePath().normalize(); Files.createDirectories(root);
                for (var entry : resources.entrySet()) {
                    String relative = entry.getKey().getPath(); if (relative.startsWith("sounds/")) relative = relative.substring(7);
                    var target = root.resolve(relative).normalize(); if (!target.startsWith(root) || Files.exists(target)) continue;
                    Files.createDirectories(target.getParent());
                    var temporary = Files.createTempFile(target.getParent(), ".sound-", ".tmp");
                    try { try (var input = entry.getValue().open()) { Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING); } Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
                    finally { Files.deleteIfExists(temporary); }
                }
                Minecraft.getInstance().execute(() -> net.minecraft.Util.getPlatform().openPath(root));
            } catch (java.io.IOException error) {
                com.mojang.logging.LogUtils.getLogger().warn("Cannot prepare vanilla sound directory", error);
                Minecraft.getInstance().execute(() -> { if(Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.translatable("dashboard.sablelife.vanilla_folder_error"), false); });
            } finally { busy.set(false); }
        }, net.minecraft.Util.ioPool());
    }
}
