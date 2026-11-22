package dev.sablelife.testing;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.CommandProgram;
import dev.sablelife.module.DashboardSelection;
import dev.sablelife.module.HeartDashboardMenu;
import dev.sablelife.module.ModuleNetworking;
import dev.sablelife.module.ProjectileItemMenu;
import dev.sablelife.module.SequenceMenu;
import dev.sablelife.module.VirtualModule;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("sablelife_editor_revision_tests")
@PrefixGameTestTemplate(false)
public final class EditorRevisionGameTests {
    private static HeartBlockEntity core(GameTestHelper test) {
        var pos = test.absolutePos(new BlockPos(3, 14, 3));
        var level = test.getLevel();
        level.setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos, pos.east()),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY(), pos.getZ()));
        return (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    }

    private static Editor editor(GameTestHelper test, HeartBlockEntity core, boolean operator, GameType mode) {
        return new Editor(test, core, operator, mode);
    }

    private static final class Editor extends FakePlayer {
        private final boolean operator;
        private final List<Component> messages = new ArrayList<>();
        private int nextMenu;

        Editor(GameTestHelper test, HeartBlockEntity core, boolean operator, GameType mode) {
            super(test.getLevel(), new GameProfile(UUID.randomUUID(), "FixtureEditor"));
            this.operator = operator;
            setGameMode(mode);
            setPos(SableBodyProbe.toWorld(test.getLevel(), core.getBlockPos().getCenter()));
        }

        @Override public boolean canUseGameMasterBlocks() { return operator; }
        @Override public void displayClientMessage(Component text, boolean overlay) { messages.add(text); }
        @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,
                java.util.function.Consumer<RegistryFriendlyByteBuf> extraData) {
            if (provider == null) return java.util.OptionalInt.empty();
            closeContainer();
            containerMenu = provider.createMenu(++nextMenu, getInventory(), this);
            return java.util.OptionalInt.of(nextMenu);
        }

        boolean sawLocalizedFeedback(String key) {
            return messages.stream().anyMatch(message ->
                    message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                            && translated.getKey().equals(key) && translated.getFallback() != null);
        }
    }

    private static ModuleNetworking.Action roundTrip(GameTestHelper test, ModuleNetworking.Action action) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), test.getLevel().registryAccess());
        try {
            ModuleNetworking.Action.CODEC.encode(buffer, action);
            var actual = ModuleNetworking.Action.CODEC.decode(buffer);
            test.assertTrue(buffer.readableBytes() == 0 && action.equals(actual), "Action codec changed the request");
            return actual;
        } finally {
            buffer.release();
        }
    }

    private static int sequenceRevision(SequenceMenu menu) {
        return menu.snapshot().getInt("SequenceRevision");
    }

    private static boolean sequenceEdit(GameTestHelper test, SequenceMenu menu, ServerPlayer player,
                                        VirtualModule module, int revision, String key, double value, int index) {
        var action = roundTrip(test, new ModuleNetworking.Action(menu.containerId, revision, module.id(),
                "seq_edit", key, value, Integer.toString(index)));
        return menu.action(player, action.revision(), action.module(), action.key(), action.value(), action.text());
    }

    @GameTest(template = "combat", batch = "editor_command_protocol")
    public static void commandEditsRemainCompatibleAndRejectOldRevision(GameTestHelper test) {
        var core = core(test);
        var module = core.modules().add("command");
        var first = editor(test, core, true, GameType.CREATIVE);
        var second = editor(test, core, true, GameType.CREATIVE);
        var firstMenu = new HeartDashboardMenu(1, first.getInventory(), core);
        var secondMenu = new HeartDashboardMenu(2, second.getInventory(), core);
        test.assertTrue(firstMenu.action(first, core.modules().revision(), module.id(), "commands", "add", 0, "0|"), "Cannot add a command row");
        test.assertTrue(firstMenu.action(first, core.modules().revision(), module.id(), "commands", "add", 0, "0|"), "Cannot add a second command row");
        var unaffected = module.commands().rows().getLast().id();
        int oldRevision = firstMenu.snapshot().getInt("Revision");
        test.assertTrue(secondMenu.action(second, core.modules().revision(), module.id(), "commands", "delete", 0, "0|"), "Second editor could not remove the first row");
        var action = roundTrip(test, new ModuleNetworking.Action(firstMenu.containerId, oldRevision, module.id(),
                "commands", "command", 0, "0|say Old draft"));
        test.assertTrue(!firstMenu.action(first, action.revision(), action.module(), action.operation(), action.key(), action.value(), action.text()),
                "Stale command index targeted the remaining row");
        String command = "say " + "a".repeat(CommandProgram.MAX_TEXT - "say |".length()) + "|";
        var fresh = roundTrip(test, new ModuleNetworking.Action(firstMenu.containerId, core.modules().revision(),
                module.id(), "commands", "command", 0, "0|" + command));
        test.assertTrue(firstMenu.action(first, fresh.revision(), fresh.module(), fresh.operation(), fresh.key(), fresh.value(), fresh.text())
                && module.commands().rows().getFirst().id().equals(unaffected)
                && module.commands().rows().getFirst().command().equals(command), "Fresh command, pipe text, or row UUID changed");
        test.succeed();
    }

    @GameTest(template = "combat", batch = "editor_sequence_normal")
    public static void sequenceNormalEditsTravelThroughExistingActionAndSave(GameTestHelper test) {
        var core = core(test);
        var module = core.modules().add("sequence");
        var author = editor(test, core, false, GameType.CREATIVE);
        var menu = new SequenceMenu(1, author.getInventory(), core, module);
        test.assertTrue(sequenceEdit(test, menu, author, module, sequenceRevision(menu), "add", 0, 0), "Fresh sequence add was refused");
        test.assertTrue(sequenceEdit(test, menu, author, module, sequenceRevision(menu), "channel", 5, 0), "Fresh channel edit was refused");
        test.assertTrue(sequenceEdit(test, menu, author, module, sequenceRevision(menu), "time", 1.5, 0), "Fresh duration edit was refused");
        var loaded = VirtualModule.load(module.save());
        test.assertTrue(loaded.sequence().getFirst().channel() == 5 && loaded.sequence().getFirst().ticks() == 30,
                "Sequence save format or current edit behavior changed");
        test.assertTrue(menu.action(author, module.id(), "power", 6, "0")
                && module.sequence().getFirst().power() == 6, "Trusted Java sequence editor no longer accepts current rows");
        test.succeed();
    }

    @GameTest(template = "combat", batch = "editor_sequence_concurrent")
    public static void staleSequenceMutationCannotWriteToShiftedIndex(GameTestHelper test) {
        var core = core(test);
        var module = core.modules().add("sequence");
        module.editSequence("add", 0, 0);
        module.editSequence("add", 0, 0);
        module.editSequence("time", 1, 3);
        var first = editor(test, core, false, GameType.CREATIVE);
        var second = editor(test, core, false, GameType.CREATIVE);
        var firstMenu = new SequenceMenu(1, first.getInventory(), core, module);
        var secondMenu = new SequenceMenu(2, second.getInventory(), core, module);
        int oldRevision = sequenceRevision(firstMenu);
        test.assertTrue(sequenceEdit(test, secondMenu, second, module, sequenceRevision(secondMenu), "delete", 0, 0),
                "Second editor could not delete the original row");
        test.assertTrue(!sequenceEdit(test, firstMenu, first, module, oldRevision, "time", 7, 0)
                && module.sequence().getFirst().ticks() == 60, "Stale sequence duration wrote to the replacement row");
        test.assertTrue(first.sawLocalizedFeedback("sequence.sablelife.editor_stale"), "Concurrent edit did not explain the retry");
        test.assertTrue(sequenceEdit(test, firstMenu, first, module, sequenceRevision(firstMenu), "time", 4, 0)
                && module.sequence().getFirst().ticks() == 80, "Resync did not restore normal edits");
        test.succeed();
    }

    @GameTest(template = "combat", batch = "editor_sequence_legacy")
    public static void oldSequenceRequestsCanReadButCannotMutate(GameTestHelper test) {
        var core = core(test);
        core.wireless().add();
        var module = core.modules().add("sequence");
        module.editSequence("add", 0, 0);
        var author = editor(test, core, false, GameType.CREATIVE);
        var menu = new SequenceMenu(1, author.getInventory(), core, module);
        test.assertTrue(menu.snapshot().getCompound("Module").hasUUID("Id"), "Old viewer cannot read sequence");
        test.assertTrue(sequenceEdit(test, menu, author, module, 0, "wire_select", 1, 0), "Read-only wireless navigation no longer works");
        test.assertTrue(!sequenceEdit(test, menu, author, module, 0, "time", 7, 0)
                && module.sequence().getFirst().ticks() == 20, "Unversioned sequence edit was accepted");
        test.assertTrue(author.sawLocalizedFeedback("sequence.sablelife.editor_update_required"),
                "Old client did not receive an update message with a fallback");
        test.succeed();
    }

    @GameTest(template = "combat", batch = "editor_display_recovery")
    public static void oversizeSavedItemCanBeClearedThroughAuthorizedNormalMenu(GameTestHelper test) {
        var core = core(test);
        var module = core.modules().add("item_display");
        var item = Items.PAPER.getDefaultInstance();
        var custom = new CompoundTag();
        custom.putByteArray("model_data", new byte[1_700_000]);
        item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(custom));
        module.projectileItem(item, test.getLevel().registryAccess());
        var original = module.save().getCompound("ProjectileItem");
        var author = editor(test, core, false, GameType.CREATIVE);
        DashboardSelection.set(core, author.getUUID(), module.id());
        var menu = new HeartDashboardMenu(1, author.getInventory(), core);
        var snapshot = menu.snapshot();
        var displayRow = snapshot.getList("Modules", 10).getCompound(0);
        test.assertTrue(snapshot.getBoolean("Editable") && snapshot.sizeInBytes() < 100_000
                && displayRow.getBoolean(VirtualModule.DISPLAY_MODEL_OMITTED) && !displayRow.contains("ProjectileItem"),
                "Dashboard kept the oversized item payload or disabled recovery");
        test.assertTrue(module.save().getCompound("ProjectileItem").equals(original),
                "Dashboard altered the stored item while building its snapshot");
        var spectator = editor(test, core, false, GameType.SPECTATOR);
        test.assertTrue(!menu.action(spectator, core.modules().revision(), module.id(), "projectile", "", 0, ""),
                "Read-only spectator could open the editing menu");
        test.assertTrue(menu.action(author, core.modules().revision(), module.id(), "projectile", "", 0, "")
                && author.containerMenu instanceof ProjectileItemMenu, "Authorized author cannot open the existing item chooser");
        var picker = (ProjectileItemMenu) author.containerMenu;
        var sample = picker.getSlot(0).getItem();
        var sentData = sample.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        test.assertTrue(sentData.copyTag().sizeInBytes() < 100_000, "Recovery chooser would send the oversized world item");
        test.assertTrue(picker.clickMenuButton(author, 1) && module.save().getCompound("ProjectileItem").isEmpty(),
                "Normal clear button could not recover the module");
        test.succeed();
    }
}
