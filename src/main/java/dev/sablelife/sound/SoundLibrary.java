package dev.sablelife.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local authoring library; custom resources are packaged for the client at startup. */
public final class SoundLibrary {
    public record Entry(String id, String label) {}
    private static final List<Entry> entries = new java.util.concurrent.CopyOnWriteArrayList<>();
    public static Path folder() { return FMLPaths.CONFIGDIR.get().resolve("sablelife/sounds"); }
    public static Path packFolder() { return FMLPaths.CONFIGDIR.get().resolve("sablelife/generated_sound_pack"); }
    public static List<Entry> entries() { return List.copyOf(entries); }
    public static boolean contains(String id) { return entries.stream().anyMatch(e -> e.id.equals(id)); }
    public static String label(String id) { return entries.stream().filter(e -> e.id.equals(id)).map(Entry::label).findFirst().orElse(id); }
    public static void prepare() {
        prepare(folder(), packFolder());
    }
    public static void prepare(Path source, Path pack) {
        entries.clear(); entries.add(new Entry("", "None / 无"));
        for (String id : List.of("entity.ender_dragon.growl", "entity.ravager.roar", "entity.iron_golem.hurt", "entity.zombie.ambient", "entity.skeleton.hurt", "block.note_block.bass", "entity.warden.sonic_boom", "item.firecharge.use", "entity.firework_rocket.blast", "entity.generic.explode", "entity.arrow.shoot", "block.dispenser.launch", "entity.blaze.shoot", "music.dragon", "music_disc.pigstep", "music_disc.otherside"))
            entries.add(new Entry("minecraft:" + id, id));
        try {
            Files.createDirectories(source);
            var assets = pack.resolve("assets/sablelife");
            Files.createDirectories(assets.resolve("sounds/custom"));
            var definitions = new JsonObject();
            try (var files = Files.list(source)) {
                for (var file : files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                        .sorted().limit(128).toList()) {
                    if (Files.size(file) > 16 * 1024 * 1024 || Files.size(file) < 4) continue;
                    try (var stream = Files.newInputStream(file)) { if (!Arrays.equals(stream.readNBytes(4), new byte[]{'O','g','g','S'})) continue; }
                    String key = "custom/" + UUID.nameUUIDFromBytes(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
                    Files.copy(file, assets.resolve("sounds/" + key + ".ogg"), StandardCopyOption.REPLACE_EXISTING);
                    var sound = new JsonObject(); sound.addProperty("name", "sablelife:" + key); sound.addProperty("stream", true);
                    var sounds = new JsonArray(); sounds.add(sound); var definition = new JsonObject(); definition.add("sounds", sounds);
                    definitions.add(key, definition); entries.add(new Entry("sablelife:" + key, file.getFileName().toString()));
                }
            }
            Files.writeString(assets.resolve("sounds.json"), definitions.toString());
            Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":34,\"description\":\"Sable Life custom sounds\"}}");
        } catch (java.io.IOException error) { org.slf4j.LoggerFactory.getLogger("SableLife").warn("Cannot prepare custom sound folder", error); }
    }
    private SoundLibrary() {}
}
