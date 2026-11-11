package dev.sablelife.testing;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.HeartModules;
import dev.sablelife.module.ModuleTypes;
import dev.sablelife.module.VirtualModule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

final class DatapackAlarmScenario {
    static final boolean ENABLED = System.getProperty("sablelife.datapackAlarmFixture") != null;
    static final String ALARM = "sablelife_example:datapack/alarm";
    static final String CHECKPOINT = "sablelife_example:datapack/checkpoint";
    private static final String PACK = "sablelife_alarm";
    private static final Path ALARM_FILE = Path.of("data/sablelife_example/sablelife/modules/alarm.json");

    private static Path alarm;
    private static String definition;
    private static CompoundTag saved;
    private static UUID builtIn;
    private static int phase;
    private static int waitUntil;
    private static Vec3 soundPosition;
    private static float soundVolume;

    static void recordSound(Vec3 position, float volume) {
        soundPosition = position;
        soundVolume = volume;
    }

    static void observe(String role, int state) {
        try {
            Files.createFile(observation(role, state));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not record the client's datapack observation", failure);
        }
    }

    private static Path observation(String role, int state) {
        return Path.of(System.getProperty("sablelife.datapackAlarmObservations"), role + "-" + state);
    }

    private static boolean observed(MinecraftServer server, int state) {
        return server.isDedicatedServer()
                ? Files.exists(observation("author", state)) && Files.exists(observation("observer", state))
                : Files.exists(observation("singleplayer", state));
    }

    static boolean prepare(MinecraftServer server) {
        try {
            if (alarm == null) {
                check(ModuleTypes.find(ALARM) == null, "Alarm is available without the datapack");
                Path source = Path.of(System.getProperty("sablelife.datapackAlarmFixture"));
                Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK);
                alarm = pack.resolve(ALARM_FILE);
                Files.createDirectories(alarm.getParent());
                Files.copy(source.resolve("pack.mcmeta"), pack.resolve("pack.mcmeta"));
                definition = Files.readString(source.resolve(ALARM_FILE));
                Files.writeString(alarm, definition);
                server.getPackRepository().reload();
                command(server, "datapack enable \"file/" + PACK + "\"");
            }
            return ModuleTypes.find(ALARM) != null;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not prepare alarm datapack", failure);
        }
    }

    static boolean verifyReload(MinecraftServer server, HeartBlockEntity core, int chimes, int tick) {
        try {
            return advance(server, core, chimes, tick);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not update alarm datapack", failure);
        }
    }

    private static boolean advance(MinecraftServer server, HeartBlockEntity core, int chimes, int tick) throws IOException {
        VirtualModule module = core.modules().all().stream().filter(row -> row.type().equals(ALARM)).findFirst().orElseThrow();
        switch (phase) {
            case 0 -> {
                check(chimes == 1, "Initial activation did not play one alarm");
                checkSound(core, module);
                saved = module.save();
                var wireless = core.modules().add("wireless_redstone");
                check(wireless != null && wireless.edit("trigger", 0, "") && wireless.edit("redstoneChannel", 1, "")
                                && wireless.edit("redstonePower", 9, ""),
                        "Could not prepare built-in output");
                builtIn = wireless.id();
                Files.writeString(alarm.resolveSibling("broken_json.json"), "{\"format\": ");
                Files.writeString(alarm.resolveSibling("broken_action.json"), definition.replace("play_sound", "unknown_action"));
                Files.writeString(alarm.resolveSibling("checkpoint.json"), definition.replace("\"Alarm\"", "\"Checkpoint\""));
                command(server, "reload");
                phase++;
            }
            case 1 -> {
                if (ModuleTypes.find(CHECKPOINT) == null) return false;
                check(ModuleTypes.find("sablelife_example:datapack/broken_json") == null
                                && ModuleTypes.find("sablelife_example:datapack/broken_action") == null,
                        "Bad definition was registered");
                check(module.known() && chimes == 1, "Valid alarm restarted or stopped after unrelated bad definitions");
                checkBuiltIn(core);
                core.wireless().programOutputs("addon_network_test", Map.of());
                waitUntil = tick + 10;
                phase++;
            }
            case 2 -> {
                if (tick < waitUntil) return false;
                core.wireless().programOutputs("addon_network_test", Map.of(1, 15));
                waitUntil = tick + 30;
                phase++;
            }
            case 3 -> {
                if (tick < waitUntil || !observed(server, 1)) return false;
                check(chimes == 2, "Valid alarm did not rearm after invalid definitions were isolated");
                System.out.println("DATAPACK_ALARM invalid definitions isolated");
                Files.delete(alarm);
                command(server, "reload");
                phase++;
            }
            case 4 -> {
                if (ModuleTypes.find(ALARM) != null) return false;
                check(!module.known() && "unknown".equals(HeartModules.status(module, core)), "Missing alarm was still operational");
                check(module.save().equals(saved) && VirtualModule.load(saved).save().equals(saved), "Missing alarm lost saved data");
                checkBuiltIn(core);
                waitUntil = tick + 40;
                phase++;
            }
            case 5 -> {
                if (tick < waitUntil || !observed(server, 2)) return false;
                check(chimes == 2, "Missing alarm played a sound");
                System.out.println("DATAPACK_ALARM removed definition preserved row");
                core.wireless().programOutputs("addon_network_test", Map.of());
                Files.writeString(alarm, definition);
                command(server, "reload");
                phase++;
            }
            case 6 -> {
                if (ModuleTypes.find(ALARM) == null) return false;
                check(chimes == 2 && module.save().equals(saved) && module.value("addon_volume") == 1.75
                                && module.anchors().size() == 1 && "valid".equals(HeartModules.status(module, core)),
                        "Restored alarm did not retain the original settings and mark");
                checkBuiltIn(core);
                core.wireless().programOutputs("addon_network_test", Map.of(1, 15));
                waitUntil = tick + 35;
                phase++;
            }
            case 7 -> {
                if (tick < waitUntil || !observed(server, 3)) return false;
                check(chimes == 3, "Restored alarm did not play exactly once");
                checkSound(core, module);
                System.out.println("DATAPACK_ALARM reload and restoration PASS");
                return true;
            }
        }
        return false;
    }

    private static void checkBuiltIn(HeartBlockEntity core) {
        var wireless = core.modules().find(builtIn);
        check(wireless != null && HeartModules.triggered(wireless, core) && core.wireless().output(0) == 9,
                "Built-in redstone output changed");
    }

    private static void checkSound(HeartBlockEntity core, VirtualModule module) {
        check(soundVolume == 1.75f && soundPosition.distanceTo(module.anchors().getFirst().world(core, false)) < .05,
                "Alarm sound did not use the configured volume and marked position");
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4), command);
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }

    private DatapackAlarmScenario() {}
}
