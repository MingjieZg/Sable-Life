package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = "sablelife_tests")
public final class AddonModuleNetworkServer {
    private static final boolean RUN = Boolean.getBoolean("sablelife.addonNetworkServer");
    private static final boolean INTEGRATED = "singleplayer".equals(System.getProperty("sablelife.addonNetworkRole"));
    private static final String MODULE = DatapackAlarmScenario.ENABLED ? DatapackAlarmScenario.ALARM
            : "sablelife_example_addon:marker_chime";
    private static final BlockPos SITE = new BlockPos(8, 100, 8);
    private static BlockPos plotCore;
    private static int ticks;
    private static int opened;
    private static int powered;
    private static int chimes;
    private static boolean dashboardsOpened;
    private static boolean done;

    @SubscribeEvent
    public static void sound(PlayLevelSoundEvent.AtPosition event) {
        if (RUN && !event.getLevel().isClientSide() && event.getSound() != null
                && event.getSound().value() == SoundEvents.NOTE_BLOCK_CHIME.value()) {
            chimes++;
            if (DatapackAlarmScenario.ENABLED) {
                DatapackAlarmScenario.recordSound(event.getPosition(), event.getOriginalVolume());
            }
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!RUN || done) return;
        var server = event.getServer();
        var level = server.overworld();
        try {
            if (++ticks > 2400) throw new IllegalStateException("Network timeout: opened=" + opened + " powered=" + powered);
            var players = server.getPlayerList().getPlayers();
            if (players.size() != (INTEGRATED ? 1 : 2)) return;
            if (plotCore == null) {
                if (DatapackAlarmScenario.ENABLED && !DatapackAlarmScenario.prepare(server)) return;
                level.setChunkForced(0, 0, true);
                for (var floor : BlockPos.betweenClosed(SITE.offset(-3, -1, -3), SITE.offset(3, -1, 3))) {
                    level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                }
                level.setBlockAndUpdate(SITE, SableLife.REDSTONE_CORE.get().defaultBlockState());
                level.setBlockAndUpdate(SITE.east(), Blocks.IRON_BLOCK.defaultBlockState());
                var body = SubLevelAssemblyHelper.assembleBlocks(level, SITE, List.of(SITE, SITE.east()),
                        new BoundingBox3i(SITE.getX(), SITE.getY(), SITE.getZ(), SITE.getX() + 1, SITE.getY(), SITE.getZ()));
                plotCore = body.getPlot().getCenterBlock();
                opened = -ticks;
                return;
            }
            if (!(level.getBlockEntity(plotCore) instanceof HeartBlockEntity core) || !core.operational()) return;
            if (opened < 0 && ticks + opened > 60) {
                for (int i = 0; i < 2; i++) {
                    int channel = core.wireless().add();
                    core.wireless().channel(channel).set(0, 1);
                    core.wireless().channel(channel).set(6, 3);
                }
                var point = dev.sablelife.module.ModuleArena.corePosition(core);
                for (var player : players) {
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.setNoGravity(true);
                    player.connection.teleport(point.x + 4, point.y - 1.62, point.z, 90, 0);
                }
                opened = ticks;
                return;
            }
            if (!dashboardsOpened && opened > 0 && ticks - opened >= 30) {
                players.forEach(core::openDashboard);
                dashboardsOpened = true;
                System.out.println("ADDON_MODULE_NETWORK opened " + players.stream().map(player ->
                        player.containerMenu.getClass().getSimpleName() + " at " + player.position()).toList());
            }
            if (DatapackFullCatalogScenario.ENABLED) {
                if (DatapackFullCatalogScenario.verifyReload(server, core)) finish(server);
                return;
            }
            if (powered == 0) {
                var module = core.modules().all().stream().filter(row -> row.type().equals(MODULE))
                        .findFirst().orElse(null);
                if (module == null || module.anchors().size() != 1 || module.value("trigger") != 2
                        || module.value("channel") != 1 || module.value("addon_volume") != 1.75) return;
                if (chimes != 0) throw new IllegalStateException("Chime fired before the configured trigger");
                core.wireless().programOutputs("addon_network_test", Map.of(1, 15));
                System.out.println("ADDON_MODULE_NETWORK marked and powered " + module.id());
                powered = ticks;
                return;
            }
            if (ticks - powered >= 60) {
                if (DatapackAlarmScenario.ENABLED) {
                    if (!DatapackAlarmScenario.verifyReload(server, core, chimes, ticks)) return;
                } else if (chimes != 1) throw new IllegalStateException("Expected one server chime, got " + chimes);
                finish(server);
            }
        } catch (RuntimeException failure) {
            done = true;
            failure.printStackTrace();
            System.out.println("ADDON_MODULE_NETWORK_SERVER FAIL");
            server.halt(false);
        }
    }

    private static void finish(MinecraftServer server) {
        done = true;
        System.out.println("ADDON_MODULE_NETWORK_SERVER PASS");
        server.halt(false);
    }
}
