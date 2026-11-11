package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.api.module.ModuleRegistry;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.HeartDashboardMenu;
import dev.sablelife.module.MarkingSessions;
import dev.sablelife.module.ModuleArena;
import dev.sablelife.module.VirtualModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("sablelife_addon_module_tests")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "sablelife_tests")
public final class AddonModuleGameTests {
    private static final String EXAMPLE_ID = "sablelife_example_addon:marker_chime";
    private static final ThreadLocal<List<PlayLevelSoundEvent.AtPosition>> CHIMES = new ThreadLocal<>();

    @SubscribeEvent
    public static void onSound(PlayLevelSoundEvent.AtPosition event) {
        var chimes = CHIMES.get();
        if (chimes != null && event.getSound() != null && event.getSound().value() == SoundEvents.NOTE_BLOCK_CHIME.value()) {
            chimes.add(event);
        }
    }

    @GameTest(template = "combat", batch = "addon_module_dashboard")
    public static void exampleAddonRunsThroughDashboardMarkAndSignal(GameTestHelper test) {
        boolean installed = ModList.get().isLoaded("sablelife_example_addon");
        test.assertTrue(installed == Boolean.getBoolean("sablelife.expectExampleAddon"), "Wrong example addon installation for this run");
        if (!installed) {
            test.assertTrue(ModuleRegistry.find(EXAMPLE_ID) == null, "Example registered without its JAR");
            test.succeed();
            return;
        }
        var core = core(test);
        var author = TestActors.creative(test.getLevel());
        author.setPos(ModuleArena.corePosition(core));
        var menu = new HeartDashboardMenu(1, author.getInventory(), core);
        test.assertTrue(core.modules().snapshot(core, true).getList("AvailableTypes", 8).stream()
                .anyMatch(type -> EXAMPLE_ID.equals(type.getAsString())), "Example missing from module picker snapshot");
        test.assertTrue(menu.action(author, core.modules().revision(), new UUID(0, 0), "add", EXAMPLE_ID, 0, ""),
                "Dashboard rejected the addon module");
        var module = core.modules().all().getFirst();
        UUID id = module.id();
        test.assertTrue(module.value("addon_volume") == 1, "Default volume missing");
        test.assertTrue(menu.action(author, core.modules().revision(), id, "edit", "addon_volume", 1.5, ""), "Volume edit rejected");
        test.assertTrue(!menu.action(author, core.modules().revision(), id, "edit", "addon_volume", Double.NaN, ""), "Accepted NaN");
        test.assertTrue(menu.action(author, core.modules().revision(), id, "edit", "trigger", 2, ""), "Internal trigger rejected");
        int channel = core.wireless().add();
        core.wireless().channel(channel).set(0, 1);
        core.wireless().channel(channel).set(6, 3);
        test.assertTrue(menu.action(author, core.modules().revision(), id, "edit", "channel", channel + 1, ""), "Channel edit rejected");
        test.assertTrue(menu.action(author, core.modules().revision(), id, "mark", "", 0, ""), "Marking session rejected");
        test.assertTrue(MarkingSessions.select(author, core.getBlockPos().east(), Direction.UP), "Point mark rejected");
        var saved = core.modules().save();
        core.modules().load(saved);
        module = core.modules().find(id);
        test.assertTrue(module.value("addon_volume") == 1.5 && module.anchors().size() == 1
                && module.value("trigger") == 2 && module.value("channel") == channel, "Saved row changed on reload");
        test.assertTrue(menu.action(author, core.modules().revision(), id, "copy", "", 0, ""), "Dashboard copy rejected");
        var copy = core.modules().all().getLast();
        test.assertTrue(!copy.id().equals(id) && copy.value("addon_volume") == 1.5 && copy.anchors().equals(module.anchors()),
                "Copy did not retain configured values and mark");
        test.assertTrue(menu.action(author, core.modules().revision(), copy.id(), "remove", "", 0, ""), "Copy could not be removed");
        var point = module.anchors().getFirst().world(core, false);

        var chimes = new ArrayList<PlayLevelSoundEvent.AtPosition>();
        CHIMES.set(chimes);
        try {
            core.modules().tick(core);
            test.assertTrue(chimes.isEmpty(), "Chime played without an internal signal");
            core.wireless().programOutputs("addon_test", Map.of(channel, 15));
            core.modules().tick(core);
            core.modules().tick(core);
            test.assertTrue(chimes.size() == 1 && chimes.getFirst().getPosition().distanceTo(point) < .001
                    && chimes.getFirst().getOriginalVolume() == 1.5f, "Chime did not play once with the saved mark and volume");
            core.wireless().programOutputs("addon_test", Map.of());
            core.modules().tick(core);
            core.wireless().programOutputs("addon_test", Map.of(channel, 15));
            core.modules().tick(core);
            test.assertTrue(chimes.size() == 2, "Chime did not rearm after the signal went low");
            core.wireless().programOutputs("addon_test", Map.of());
            core.modules().tick(core);
            test.assertTrue(menu.action(author, core.modules().revision(), id, "add", "move_set", 0, ""), "Move Set missing");
            UUID moveSet = core.modules().all().getLast().id();
            test.assertTrue(menu.action(author, core.modules().revision(), moveSet, "move_set", "add", 0, ""), "Move row rejected");
            test.assertTrue(menu.action(author, core.modules().revision(), moveSet, "move_set", "action_add", 0, "0|" + id),
                    "Move Set rejected the addon module");
            core.modules().tick(core);
            core.modules().tick(core);
            test.assertTrue(chimes.size() == 3, "Move Set did not trigger one chime");
            test.succeed();
        } finally {
            CHIMES.remove();
        }
    }

    @GameTest(template = "combat", batch = "addon_module_legacy")
    public static void missingAddonRowSurvivesAlongsideLegacyModule(GameTestHelper test) {
        var core = core(test);
        var unknown = new CompoundTag();
        unknown.putUUID("Id", UUID.randomUUID());
        unknown.putString("Type", "sablelife_missing_test:missing_module");
        unknown.putInt("Version", 1);
        unknown.putBoolean("Enabled", true);
        unknown.putString("AddonData", "retain me");
        var builtIn = new VirtualModule("wireless_redstone");
        var modules = new ListTag();
        modules.add(unknown);
        modules.add(builtIn.save());
        var saved = new CompoundTag();
        saved.put("Modules", modules);
        core.modules().load(saved);
        var missing = core.modules().find(unknown.getUUID("Id"));
        test.assertTrue(!missing.known() && !missing.enabled() && missing.save().equals(unknown), "Missing addon data was changed");
        test.assertTrue(core.modules().find(builtIn.id()).known(), "Old built-in type failed to load");
        test.succeed();
    }

    private static HeartBlockEntity core(GameTestHelper test) {
        var level = test.getLevel();
        var pos = test.absolutePos(new BlockPos(3, 6, 3));
        level.setBlockAndUpdate(pos, SableLife.REDSTONE_CORE.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos, pos.east()),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY(), pos.getZ()));
        return (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    }
}
