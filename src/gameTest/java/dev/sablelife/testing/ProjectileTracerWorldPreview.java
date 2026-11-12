package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.attack.ModuleShot;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.VirtualModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Draw a live bullet's flight, then check that the client removes the stopped tracer. */
@EventBusSubscriber(modid = "sablelife_tests", value = Dist.CLIENT)
public final class ProjectileTracerWorldPreview {
    private static final BlockPos CORE = new BlockPos(-7, 82, 0);
    private static boolean started;
    private static boolean building;
    private static volatile boolean ready;
    private static int ticks;
    private static int mostPixels;
    private static boolean saved;
    private static final boolean BENCHMARK = Boolean.getBoolean("sablelife.projectileTracerBenchmark");
    private static final List<Double> particleMs = new ArrayList<>();
    private static final List<Double> tracerMs = new ArrayList<>();
    private static int benchmarkTick;
    private static volatile boolean benchmarkDone;
    private static long tickStart;
    private static VirtualModule particleModule;
    private static VirtualModule tracerModule;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("sablelife.projectileTracerWorld")) return;
        var mc = Minecraft.getInstance();
        if (!started) {
            if (mc.getOverlay() != null || mc.screen == null) return;
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.hideGui = true;
            if (Boolean.getBoolean("sablelife.patternShaders")) {
                var iris = Class.forName("net.irisshaders.iris.Iris");
                var config = iris.getMethod("getIrisConfig").invoke(null);
                config.getClass().getMethod("setShaderPackName", String.class).invoke(config, "ComplementaryReimagined_r5.8.1.zip");
                config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
                config.getClass().getMethod("save").invoke(config);
                iris.getMethod("reload").invoke(null);
            }
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("tracer-" + System.currentTimeMillis(),
                    new LevelSettings("Projectile tracer", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1234, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            return;
        }
        var server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null || mc.getOverlay() != null) return;
        if (!building) {
            building = true;
            server.execute(() -> {
                var world = server.overworld();
                world.setDayTime(6000);
                for (int x = -9; x <= 9; x++) for (int z = -3; z <= 4; z++)
                    world.setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 81; y <= 84; y++) for (int z = -1; z <= 1; z++)
                    world.setBlockAndUpdate(new BlockPos(4, y, z), Blocks.GRAY_CONCRETE.defaultBlockState());
                for (int x = -4; x <= 3; x++) for (int z = 2; z <= 3; z++)
                    world.setBlockAndUpdate(new BlockPos(x, 81, z), Blocks.WATER.defaultBlockState());
                world.setBlockAndUpdate(CORE, SableLife.HEART.get().defaultBlockState());
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(world, -1, 81.2, 11.5, 180, 0);
                ready = true;
            });
            return;
        }
        if (!ready) return;
        if (BENCHMARK) {
            if (benchmarkDone) mc.stop();
            return;
        }
        ticks++;
        if (ticks >= 45 && ticks <= 90 && ticks % 6 == 0) server.execute(() -> {
            var world = server.overworld();
            var core = (HeartBlockEntity) world.getBlockEntity(CORE);
            var module = new VirtualModule("gun");
            module.edit("projectileTrailStyle", 1, "");
            module.edit("projectileTrailColorRgb", 1, "");
            module.edit("projectileTrailColorRed", 255, "");
            module.edit("projectileTrailColorGreen", 0, "");
            module.edit("projectileTrailColorBlue", 200, "");
            module.edit("projectileTrailLength", 8, "");
            module.edit("projectileTrailWidth", .2, "");
            world.addFreshEntity(new ModuleShot(world, new Vec3(-6, 82.5, .5), new Vec3(1.35, 0, 0), module, core));
        });
        if (ticks >= 52 && ticks <= 105) {
            try (var picture = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                int pixels = coloredPixels(picture);
                if (pixels > mostPixels) {
                    mostPixels = pixels;
                    var path = mc.gameDirectory.toPath().resolve("screenshots/tracer.png");
                    Files.createDirectories(path.getParent());
                    picture.writeToFile(path);
                    saved = true;
                }
            }
        }
        if (ticks == 125) {
            try (var picture = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                int remaining = coloredPixels(picture);
                System.out.println("PROJECTILE_TRACER_WORLD success=" + (saved && mostPixels > remaining + 30)
                        + " flightPixels=" + mostPixels + " expiredPixels=" + remaining);
            }
            mc.stop();
        }
    }

    private static int coloredPixels(com.mojang.blaze3d.platform.NativeImage picture) {
        int count = 0;
        for (int y = picture.getHeight() * 3 / 8; y < picture.getHeight() * 5 / 8; y++) {
            for (int x = picture.getWidth() / 4; x < picture.getWidth() * 3 / 4; x++) {
                int pixel = picture.getPixelRGBA(x, y);
                int red = pixel & 255, green = pixel >> 8 & 255, blue = pixel >> 16 & 255;
                if (red > 150 && red > green * 1.45 && blue > green * 1.3 && blue > 85) count++;
            }
        }
        return count;
    }

    @SubscribeEvent
    public static void beforeTick(ServerTickEvent.Pre event) {
        if (BENCHMARK && ready && !benchmarkDone) tickStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void benchmark(ServerTickEvent.Post event) {
        if (!BENCHMARK || !ready || benchmarkDone || tickStart == 0) return;
        double ms = (System.nanoTime() - tickStart) / 1_000_000.;
        var world = event.getServer().overworld();
        if (particleModule == null) {
            particleModule = new VirtualModule("gun");
            particleModule.edit("range", 18, "");
            particleModule.editParticle("add", "minecraft:flame");
            tracerModule = VirtualModule.load(particleModule.save());
            tracerModule.edit("projectileTrailStyle", 1, "");
        }
        int sample = benchmarkTick % 120;
        int phase = benchmarkTick / 120;
        boolean tracer = phase == 1 || phase == 2;
        // Shots last 30 ticks, so all shots from the prior phase expire before sampling.
        if (sample >= 40) (tracer ? tracerMs : particleMs).add(ms);
        if (sample % 4 == 0) {
            var core = (HeartBlockEntity) world.getBlockEntity(CORE);
            for (int i = 0; i < 8; i++) {
                world.addFreshEntity(new ModuleShot(world, new Vec3(-6, 83 + i * .18, 5.5),
                        new Vec3(.6, 0, 0), tracer ? tracerModule : particleModule, core));
            }
        }
        if (++benchmarkTick == 480) {
            System.out.println("PROJECTILE_TRACER_MSPT particles " + statistics(particleMs)
                    + " | tracer " + statistics(tracerMs));
            benchmarkDone = true;
        }
    }

    private static String statistics(List<Double> samples) {
        var sorted = samples.stream().sorted().toList();
        return String.format(java.util.Locale.ROOT, "n=%d median=%.2f p95=%.2f", sorted.size(),
                sorted.get(sorted.size() / 2), sorted.get((int) Math.ceil(sorted.size() * .95) - 1));
    }

    private ProjectileTracerWorldPreview() {}
}
