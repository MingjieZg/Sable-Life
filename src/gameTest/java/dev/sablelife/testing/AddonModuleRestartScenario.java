package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.module.ModuleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Three separate dedicated server boots against the same isolated world. */
@EventBusSubscriber(modid = "sablelife_tests")
public final class AddonModuleRestartScenario {
    private static final String TYPE = DatapackAlarmScenario.ENABLED ? DatapackAlarmScenario.ALARM
            : "sablelife_example_addon:marker_chime";
    private static final BlockPos SITE = new BlockPos(8, 100, 8);
    private static final String STAGE = System.getProperty("sablelife.moduleAddonRestart", "");
    private static BlockPos plotCore;
    private static CompoundTag expected;
    private static int ticks;
    private static int chimes;
    private static boolean finished;

    @SubscribeEvent
    public static void sound(PlayLevelSoundEvent.AtPosition event) {
        if (!STAGE.isEmpty() && !finished && event.getSound() != null
                && event.getSound().value() == SoundEvents.NOTE_BLOCK_CHIME.value()) chimes++;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (STAGE.isEmpty() || finished) return;
        var server = event.getServer();
        try {
            run(server);
        } catch (Exception failure) {
            failure.printStackTrace();
            finish(server, false);
        }
    }

    private static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        Path evidence = server.getWorldPath(LevelResource.ROOT).resolve("addon-module-fixture.nbt");
        if (++ticks > 1200) throw new IllegalStateException("Timed out loading addon module fixture at " + plotCore);
        if (ticks == 1) {
            check((ModuleTypes.find(TYPE) != null) == !STAGE.equals("missing"), "Wrong addon installation");
            level.setChunkForced(0, 0, true);
            if (STAGE.equals("create")) {
                for (var block : BlockPos.betweenClosed(SITE.offset(-3, -1, -3), SITE.offset(3, -1, 3))) {
                    level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
                }
                level.setBlockAndUpdate(SITE, SableLife.REDSTONE_CORE.get().defaultBlockState());
                level.setBlockAndUpdate(SITE.east(), Blocks.IRON_BLOCK.defaultBlockState());
                var body = SubLevelAssemblyHelper.assembleBlocks(level, SITE, List.of(SITE, SITE.east()),
                        new BoundingBox3i(SITE.getX(), SITE.getY(), SITE.getZ(), SITE.getX() + 1, SITE.getY(), SITE.getZ()));
                plotCore = body.getPlot().getCenterBlock();
            } else {
                expected = NbtIo.readCompressed(evidence, NbtAccounter.unlimitedHeap());
                plotCore = BlockPos.of(expected.getLong("PlotCore"));
            }
        }
        if (ticks < 40 || !level.hasChunkAt(plotCore) || !(level.getBlockEntity(plotCore) instanceof HeartBlockEntity core)
                || !core.operational()) return;

        if (STAGE.equals("create")) {
            var builtIn = core.modules().add("wireless_redstone");
            var module = core.modules().add(TYPE);
            int channel = core.wireless().add();
            core.wireless().channel(channel).set(0, 1);
            core.wireless().channel(channel).set(6, 3);
            check(module.edit("trigger", 2, "") && module.edit("channel", channel + 1, "")
                    && module.edit("addon_volume", 1.75, ""), "Could not configure fixture");
            module.anchors(List.of(ModuleAnchor.create(core, plotCore.east(), Direction.UP)));
            expected = new CompoundTag();
            expected.putLong("PlotCore", plotCore.asLong());
            expected.putUUID("BuiltIn", builtIn.id());
            expected.put("Module", module.save());
            NbtIo.writeCompressed(expected, evidence);
        } else {
            var raw = expected.getCompound("Module");
            var builtIn = core.modules().find(expected.getUUID("BuiltIn"));
            var module = core.modules().find(raw.getUUID("Id"));
            check(builtIn != null && builtIn.known() && builtIn.type().equals("wireless_redstone"), "Built-in module lost on restart");
            check(module != null && module.type().equals(TYPE), "Saved addon row lost on restart");
            if (STAGE.equals("missing")) {
                check(!module.known() && (DatapackAlarmScenario.ENABLED || !module.enabled())
                        && module.save().equals(raw), "Missing addon row was changed");
                check(chimes == 0, "Missing addon emitted a chime");
            } else {
                check(module.known() && module.enabled() && module.value("addon_volume") == 1.75
                        && module.anchors().size() == 1 && module.value("trigger") == 2, "Addon settings did not restore");
                core.wireless().programOutputs("addon_restart", Map.of((int) module.value("channel"), 15));
                core.modules().tick(core);
                core.modules().tick(core);
                check(chimes == 1, "Restored addon did not emit exactly one chime");
            }
        }
        core.setChanged();
        finish(server, true);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void finish(MinecraftServer server, boolean passed) {
        finished = true;
        System.out.println("ADDON_MODULE_RESTART " + STAGE + (passed ? " PASS" : " FAIL"));
        server.halt(false);
    }
}
