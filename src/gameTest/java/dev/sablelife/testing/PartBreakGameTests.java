package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.combat.BodyCombat;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.sablelife.module.HeartDashboardMenu;
import dev.sablelife.module.HeartModules;
import dev.sablelife.module.MarkingSessions;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.module.ModuleMoveSet;
import dev.sablelife.module.ModuleNetworking;
import dev.sablelife.module.VirtualModule;
import dev.sablelife.sound.SoundTimelineMenu;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sablelife_hitbox_tests")
@PrefixGameTestTemplate(false)
public final class PartBreakGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h, boolean survival) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(4, 40, 4));
        var coreBlock = survival ? SableLife.SURVIVAL_LIFE_CORE.get() : SableLife.HEART.get();
        level.setBlockAndUpdate(origin, coreBlock.defaultBlockState());
        for (int x = 1; x <= 3; x++) level.setBlockAndUpdate(origin.east(x), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, origin.east(), origin.east(2), origin.east(3)),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 3, origin.getY(), origin.getZ()));
        var core = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        core.configure(100, 100);
        return core;
    }

    private static void activate(GameTestHelper h, HeartBlockEntity core) {
        var actor = TestActors.creative(h.getLevel());
        if (core.isSurvivalCore()) core.depositSurvivalTokens(actor, new ItemStack(Items.COPPER_INGOT, 10));
        core.tryActivate(actor);
        h.assertTrue(core.health().state() == HeartState.ALIVE, "Fixture could not activate");
    }

    private static ModuleAnchor mark(HeartBlockEntity core, BlockPos pos) {
        return ModuleAnchor.create(core, pos, Direction.UP);
    }

    @GameTest(template = "combat", batch = "part_break_dashboard")
    public static void DashboardWatchedPartPacketAllowsSingleAndRangeCutsAfterDamage(GameTestHelper h) {
        dashboardCut(h, false);
    }

    @GameTest(template = "combat", batch = "part_break_survival_dashboard")
    public static void SurvivalDashboardWatchedPartPacketPreservesMarksAndAllowsRangeCuts(GameTestHelper h) {
        dashboardCut(h, true);
    }

    private static void dashboardCut(GameTestHelper h, boolean range) {
        var core = fixture(h, range);
        var pos = core.getBlockPos();
        var player = TestActors.creative(h.getLevel());
        player.setPos(SableBodyProbe.toWorld(h.getLevel(), pos.getCenter()).add(0, 1, 0));
        var menu = new HeartDashboardMenu(1, player.getInventory(), core);
        var cut = core.modules().add("part_break");
        int revision = core.modules().revision();
        h.assertTrue(dashboardAction(menu, player, new ModuleNetworking.Action(1, revision, cut.id(), "edit",
                "partBreakMode", 1, "")), "Could not select the part damage mode from the dashboard");
        h.assertTrue(dashboardAction(menu, player, new ModuleNetworking.Action(1, core.modules().revision(), cut.id(), "edit",
                "partBreakThreshold", 15, "")), "Could not enter the damage threshold");
        if (range) h.assertTrue(dashboardAction(menu, player, new ModuleNetworking.Action(1, core.modules().revision(), cut.id(),
                "edit", "partBreakShape", 1, "")), "Could not switch to a two-point break area");
        var watch = ModuleNetworking.Action.mark(1, core.modules().revision(), cut.id(), true);
        h.assertTrue(!dashboardAction(menu, player, ModuleNetworking.Action.mark(1, revision, cut.id(), true)),
                "A stale dashboard could start marking");
        var watchedPos = pos.east();
        var breakPos = pos.east(3);
        h.assertTrue(dashboardAction(menu, player, watch) && MarkingSessions.select(player, watchedPos, Direction.UP)
                && cut.watchedPart().equals(mark(core, watchedPos)) && cut.anchors().isEmpty(),
                "The watched part should take one click independently of the break shape");
        h.assertTrue(dashboardAction(menu, player, ModuleNetworking.Action.mark(1, core.modules().revision(), cut.id(), false))
                && MarkingSessions.select(player, breakPos, Direction.UP), "Could not mark the block to break");
        if (range) {
            h.assertTrue(cut.anchors().isEmpty() && MarkingSessions.select(player, pos.east(2), Direction.UP),
                    "The break range did not wait for a second corner");
        }
        h.assertTrue(HeartModules.status(cut, core).equals("valid") && cut.watchedPart().equals(mark(core, watchedPos)),
                "Marking the blocks to break lost the watched part");
        var reloaded = VirtualModule.load(cut.save());
        h.assertTrue(reloaded.anchors().equals(cut.anchors()) && reloaded.watchedPart().equals(cut.watchedPart()),
                "Saving and reading the module lost one of its selections");

        var accumulation = core.modules().add("damage_accumulation");
        core.wireless().add();
        core.wireless().channel(0).set(0, 1);
        core.wireless().channel(0).set(6, 3);
        accumulation.edit("accumChannel", 1, "");
        accumulation.edit("accumThreshold", 15, "");
        h.assertTrue(dashboardAction(menu, player, new ModuleNetworking.Action(1, core.modules().revision(),
                accumulation.id(), "edit", "accumScope", 1, "")), "Could not select marked-part damage accumulation");
        h.assertTrue(dashboardAction(menu, player, ModuleNetworking.Action.mark(1, core.modules().revision(), accumulation.id(), true))
                && MarkingSessions.select(player, watchedPos, Direction.UP)
                && HeartModules.status(accumulation, core).equals("valid"), "The cumulative-damage module could not mark its watched part");

        configureBreakSound(h, core, cut);
        activate(h, core);
        var soundPoint = SableBodyProbe.toWorld(h.getLevel(), breakPos.getCenter().lerp(
                (range ? pos.east(2) : breakPos).getCenter(), 0.5));
        captureSounds(h, soundPoint, sounds -> {
            core.damage(3);
            impact(h, watchedPos, 10);
            core.modules().tick(core);
            h.assertTrue(core.health().current() == 87 && !h.getLevel().getBlockState(breakPos).isAir() && sounds.isEmpty(),
                    "An arrow hit below the threshold broke the block or played sound");
            h.assertTrue(core.modules().damageAccumulation.snapshot(accumulation, core).getDouble("Accumulated") == 10
                    && core.wireless().output(0) == 0, "The signal counter included unattributed damage or fired early");
            impact(h, watchedPos, 5);
            core.modules().tick(core);
            assertBreakSound(h, sounds, soundPoint, cut.id());
        });
        h.assertTrue(core.health().current() == 82 && h.getLevel().getBlockState(breakPos).isAir()
                && h.getLevel().getBlockState(pos.east(2)).isAir() == range
                && h.getLevel().getBlockState(watchedPos).is(Blocks.IRON_BLOCK), "Threshold did not cut the requested blocks");
        h.runAfterDelay(1, () -> {
            core.modules().tick(core);
            h.assertTrue(core.wireless().output(0) == 15, "The marked-part counter did not emit a threshold signal");
            h.succeed();
        });
    }

    private static boolean dashboardAction(HeartDashboardMenu menu, Player player, ModuleNetworking.Action request) {
        var packet = roundTrip(player, request);
        return menu.action(player, packet.revision(), packet.module(), packet.operation(), packet.key(), packet.value(), packet.text());
    }

    private static ModuleNetworking.Action roundTrip(Player player, ModuleNetworking.Action request) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.level().registryAccess());
        try {
            ModuleNetworking.Action.CODEC.encode(buffer, request);
            return ModuleNetworking.Action.CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void configureBreakSound(GameTestHelper h, HeartBlockEntity core, VirtualModule cut) {
        var player = TestActors.creative(h.getLevel());
        player.setPos(SableBodyProbe.toWorld(h.getLevel(), core.getBlockPos().getCenter()));
        var dashboard = new HeartDashboardMenu(1, player.getInventory(), core);
        String key = "partBreakSound";
        h.assertTrue(!VirtualModule.load(cut.save()).hasSound(key), "An old Part Break module should have no default sound");
        h.assertTrue(dashboardAction(dashboard, player, new ModuleNetworking.Action(1, core.modules().revision(),
                cut.id(), "sound_editor", key, 0, "")), "The dashboard did not open the break sound editor");
        var editor = new SoundTimelineMenu(2, player.getInventory(), core, cut.id(), key, "module");
        var request = roundTrip(player, new ModuleNetworking.Action(2, editor.snapshot().getInt("Revision"), new UUID(0, 0),
                "sound_timeline", "add", 0, "minecraft:block.note_block.bass"));
        h.assertTrue(editor.action(player, request.revision(), request.key(), request.module(), request.value(), request.text()),
                "The break sound editor did not save the selected sound");
        h.assertTrue(VirtualModule.load(cut.save()).soundTimeline(key).rows().equals(cut.soundTimeline(key).rows()),
                "The break sound was lost when the module was saved");
    }

    private static void captureSounds(GameTestHelper h, Vec3 point, Consumer<List<ModuleNetworking.ModuleSound>> test) {
        var level = h.getLevel();
        var player = TestActors.creative(level);
        player.setGameMode(GameType.SPECTATOR);
        player.setPos(point);
        var sounds = new ArrayList<ModuleNetworking.ModuleSound>();
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND),
                player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket payload
                        && payload.payload() instanceof ModuleNetworking.ModuleSound sound && !sound.stop()
                        && sound.sound().equals("minecraft:block.note_block.bass")) sounds.add(sound);
            }
        };
        level.players().add(player);
        try {
            test.accept(sounds);
        } finally {
            level.players().remove(player);
        }
    }

    private static void assertBreakSound(GameTestHelper h, List<ModuleNetworking.ModuleSound> sounds, Vec3 point, UUID module) {
        h.assertTrue(sounds.size() == 1, "A successful cut should start exactly one mix");
        var sound = sounds.getFirst();
        h.assertTrue(sound.sound().equals("minecraft:block.note_block.bass") && sound.start() == 0
                && sound.position().distanceToSqr(point) < 0.000001 && !sound.module().equals(module),
                "The sound was delayed, misplaced, or tied to the invalidated module");
    }

    private static void impact(GameTestHelper h, BlockPos pos, double damage) {
        var arrow = new Arrow(h.getLevel(), 0, 0, 0, new ItemStack(Items.ARROW), null);
        arrow.setBaseDamage(damage);
        arrow.setDeltaMovement(0, -1, 0);
        BodyCombat.onProjectileImpact(new ProjectileImpactEvent(arrow, new BlockHitResult(pos.getCenter(), Direction.UP, pos, false)));
    }

    @GameTest(template = "combat", batch = "part_break_trigger")
    public static void WirelessCutInvalidatesOnlyTheMoveWithTheDestroyedMark(GameTestHelper h) {
        var core = fixture(h, false);
        var pos = core.getBlockPos();
        var cut = core.modules().add("part_break");
        cut.edit("partBreakShape", 1, "");
        cut.edit("trigger", 2, "");
        cut.edit("channel", 1, "");
        var player = TestActors.creative(h.getLevel());
        h.assertTrue(MarkingSessions.start(player, core, cut)
                && MarkingSessions.select(player, pos.east(2), Direction.UP)
                && MarkingSessions.select(player, pos.east(), Direction.UP), "Could not mark the range in reverse order");
        var affected = core.modules().add("arrow");
        affected.anchors(List.of(mark(core, pos.east())));
        var other = core.modules().add("arrow");
        other.anchors(List.of(mark(core, pos.east(3))));
        var table = core.modules().add("move_set");
        table.edit("ignoreConditions", 1, "");
        var moves = table.moveSet();
        for (int i = 0; i < 2; i++) moves.edit("add", 0, "", core.modules());
        moves.edit("action_add", 0, "0|" + affected.id(), core.modules());
        moves.edit("action_add", 0, "1|" + other.id(), core.modules());
        core.wireless().add();
        var channel = core.wireless().channel(0);
        channel.set(0, 1);
        channel.set(6, 3);
        configureBreakSound(h, core, cut);
        activate(h, core);
        var soundPoint = SableBodyProbe.toWorld(h.getLevel(), pos.east().getCenter().lerp(pos.east(2).getCenter(), 0.5));
        captureSounds(h, soundPoint, sounds -> {
            core.modules().tick(core);
            h.assertTrue(ModuleMoveSet.status(moves.get(0), core).equals("ready")
                            && !h.getLevel().getBlockState(pos.east()).isAir() && sounds.isEmpty(),
                    "Unpowered cut removed a block, played sound, or blocked the move");
            core.wireless().programOutputs("part_break_test", Map.of(0, 15));
            core.modules().tick(core);
            core.modules().partBreak.tick(core);
            assertBreakSound(h, sounds, soundPoint, cut.id());
        });
        h.assertTrue(h.getLevel().getBlockState(pos.east()).isAir() && h.getLevel().getBlockState(pos.east(2)).isAir(), "Powered cut did not erase both blocks");
        h.assertTrue(ModuleMoveSet.status(moves.get(0), core).equals("unavailable")
                && ModuleMoveSet.status(moves.get(1), core).equals("ready")
                && !HeartModules.triggered(affected, core), "Move Set retained a broken action or blocked an intact action");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "part_break_damage")
    public static void SurvivalThresholdUsesOnlyAttributedHealthLossAndSavesBothMarks(GameTestHelper h) {
        var core = fixture(h, true);
        var pos = core.getBlockPos();
        var cut = core.modules().add("part_break");
        cut.edit("partBreakMode", 1, "");
        cut.edit("partBreakThreshold", 15, "");
        cut.edit("trigger", 2, "");
        cut.anchors(List.of(mark(core, pos.east())));
        var player = TestActors.creative(h.getLevel());
        h.assertTrue(MarkingSessions.start(player, core, cut, true)
                && MarkingSessions.select(player, pos.east(3), Direction.UP), "Could not mark the watched part");
        var reload = VirtualModule.load(cut.save());
        h.assertTrue(reload.anchors().equals(cut.anchors()) && reload.watchedPart().equals(cut.watchedPart())
                && reload.value("partBreakThreshold") == 15 && reload.listensToPart(), "Part Break settings did not survive save/load");
        var whole = core.modules().add("damage_accumulation");
        whole.edit("accumThreshold", 1000, "");
        var scoped = core.modules().add("damage_accumulation");
        scoped.edit("accumScope", 1, "");
        scoped.edit("accumThreshold", 1000, "");
        scoped.watchedPart(cut.watchedPart());
        var old = whole.save();
        old.getCompound("Settings").remove("accumScope");
        h.assertTrue(!VirtualModule.load(old).listensToPart(), "Legacy Damage Accumulation lost whole-creature scope");
        activate(h, core);
        core.damage(20);
        core.damageAt(pos.east(2), 10);
        core.modules().partBreak.tick(core);
        h.assertTrue(!h.getLevel().getBlockState(pos.east()).isAir(), "Unattributed loss triggered the cut");
        core.damageAt(pos.east(3), 5);
        core.modules().partBreak.tick(core);
        h.assertTrue(h.getLevel().getBlockState(pos.east()).isAir(), "Part damage failed to cut at the threshold with no wireless input");
        var accumulation = core.modules().damageAccumulation;
        h.assertTrue(accumulation.snapshot(whole, core).getDouble("Accumulated") == 35
                && accumulation.snapshot(scoped, core).getDouble("Accumulated") == 15, "Whole and part counters received the wrong losses");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "part_break_detach")
    public static void MoveBecomesUnavailableWhenItsMarkTransfersToDetachedPart(GameTestHelper h) {
        var core = fixture(h, false);
        var pos = core.getBlockPos().east(3);
        var attack = core.modules().add("arrow");
        attack.anchors(List.of(mark(core, pos)));
        var table = core.modules().add("move_set");
        table.edit("ignoreConditions", 1, "");
        table.moveSet().edit("add", 0, "", core.modules());
        table.moveSet().edit("action_add", 0, "0|" + attack.id(), core.modules());
        var move = table.moveSet().get(0);
        h.assertTrue(ModuleMoveSet.status(move, core).equals("ready"), "Fixture move was not initially eligible");
        var detached = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), pos, List.of(pos),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ()));
        h.assertTrue(!h.getLevel().getBlockState(detached.getPlot().getCenterBlock()).isAir()
                && HeartModules.status(attack, core).equals("disconnected")
                && ModuleMoveSet.status(move, core).equals("unavailable"), "A detached intact marker remained eligible");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "part_break_hybrid")
    public static void OverlappingHybridReceiversCountTheSettledHitOnce(GameTestHelper h) {
        var core = fixture(h, false);
        var pos = core.getBlockPos();
        var cut = core.modules().add("part_break");
        cut.edit("partBreakMode", 1, "");
        cut.edit("partBreakThreshold", 15, "");
        cut.anchors(List.of(mark(core, pos.east())));
        cut.watchedPart(mark(core, pos.east(3)));
        for (int i = 0; i < 2; i++) {
            var receiver = core.modules().add("entity_hitbox");
            receiver.edit("hitboxDamageMode", 1, "");
            receiver.anchors(List.of(mark(core, pos.east(3))));
        }
        activate(h, core);
        var boxes = core.modules().damageBoxes;
        boxes.tick(core);
        h.assertTrue(boxes.entities().size() == 2, "Fixture did not spawn two receivers");
        var attacker = TestActors.creative(h.getLevel());
        var source = h.getLevel().damageSources().playerAttack(attacker);
        for (var box : boxes.entities()) box.hurt(source, 10);
        boxes.bodyDamage(core, attacker, pos.east(2), 10, false, false);
        boxes.flush(core);
        core.modules().partBreak.tick(core);
        h.assertTrue(core.health().current() == 90 && !h.getLevel().getBlockState(pos.east()).isAir(), "One Hybrid attack was counted more than once");
        var second = h.getLevel().damageSources().playerAttack(TestActors.creative(h.getLevel()));
        boxes.entities().getFirst().hurt(second, 5);
        boxes.flush(core);
        core.modules().partBreak.tick(core);
        h.assertTrue(core.health().current() == 85 && h.getLevel().getBlockState(pos.east()).isAir(), "Receiver-only damage did not count toward its marked part");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "part_break_swivel")
    public static void BreakingANativeSwivelDisconnectsTheWatchedArmAndItsMove(GameTestHelper h) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("simulated")) {
            h.succeed();
            return;
        }
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(4, 40, 4));
        var joint = origin.east();
        var tip = joint.above();
        var bearing = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("simulated", "swivel_bearing"));
        level.setBlockAndUpdate(origin, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(joint, bearing.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        level.setBlockAndUpdate(tip, Blocks.IRON_BLOCK.defaultBlockState());
        var root = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, joint, tip),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), tip.getX(), tip.getY(), tip.getZ()));
        var pos = root.getPlot().getCenterBlock();
        var core = (HeartBlockEntity) level.getBlockEntity(pos);
        var controller = level.getBlockEntity(pos.east());
        controller.getClass().getMethod("assemble").invoke(controller);
        var arm = ((BlockEntitySubLevelActor) controller).sable$getConnectionDependencies().iterator().next();
        h.assertTrue(SubLevelHelper.getConnectedChain(root).contains(arm), "The native swivel did not connect its assembled arm");
        var armBlock = arm.getPlot().getCenterBlock();
        if (!level.getBlockState(armBlock).is(Blocks.IRON_BLOCK)) armBlock = armBlock.above();
        h.assertTrue(level.getBlockState(armBlock).is(Blocks.IRON_BLOCK), "Could not locate the arm block above the swivel plate");

        var cut = core.modules().add("part_break");
        cut.edit("partBreakMode", 1, "");
        cut.edit("partBreakThreshold", 10, "");
        cut.anchors(List.of(mark(core, pos.east())));
        cut.watchedPart(mark(core, armBlock));
        var attack = core.modules().add("arrow");
        attack.anchors(List.of(mark(core, armBlock)));
        var table = core.modules().add("move_set");
        table.moveSet().edit("add", 0, "", core.modules());
        table.moveSet().edit("action_add", 0, "0|" + attack.id(), core.modules());
        var move = table.moveSet().get(0);
        activate(h, core);
        h.assertTrue(ModuleMoveSet.status(move, core).equals("ready"), "Attached arm move was unavailable");
        core.damageAt(pos, 15);
        core.modules().partBreak.tick(core);
        h.assertTrue(!level.getBlockState(pos.east()).isAir(), "Body damage incorrectly counted toward the arm");
        core.damageAt(armBlock, 10);
        core.modules().partBreak.tick(core);
        h.assertTrue(level.getBlockState(pos.east()).isAir() && level.getBlockState(armBlock).is(Blocks.IRON_BLOCK)
                && !SubLevelHelper.getConnectedChain(root).contains(arm)
                && ModuleMoveSet.status(move, core).equals("unavailable"), "The cut did not leave an intact detached arm with an unavailable move");
        System.out.println("PART_BREAK_NATIVE_SWIVEL: passed");
        h.succeed();
    }
}
