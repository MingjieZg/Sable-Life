package dev.sablelife.testing;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.sablelife.SableLife;
import dev.sablelife.attack.ModuleShot;
import dev.sablelife.diagnostics.CoreProfiler;
import dev.sablelife.diagnostics.CoreProfiler.CoreSample;
import dev.sablelife.diagnostics.CoreProfiler.Work;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.module.ModuleArena;
import dev.sablelife.module.ModuleHoming;
import dev.sablelife.module.VirtualModule;
import dev.sablelife.ownership.LoadedCoreIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sablelife_core_profiler_tests")
@PrefixGameTestTemplate(false)
public final class CoreProfilerGameTests {
    private static final String COMMAND = "sablelife profile ";

    @GameTest(template = "combat", batch = "profile_commands")
    public static void OperatorsCanRecordAndSortButOrdinaryPlayersCannot(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var server = level.getServer();
        var costly = fixture(h, SableLife.HEART.get(), 3);
        var busy = fixture(h, SableLife.HEART.get(), 9);
        var console = new CapturingOperator(level);
        var operator = console.source();
        var ordinary = operator.withPermission(0);
        var dispatcher = server.getCommands().getDispatcher();
        for (String command : List.of("start 60", "stop", "report cost", "report activity", "locate " + costly.creatureId())) {
            try {
                dispatcher.execute(COMMAND + command, ordinary);
                h.fail("A non-operator executed profiler command " + command);
            } catch (CommandSyntaxException expected) {
                h.assertTrue(!CoreProfiler.active(server), "A denied command changed the profiler state");
            }
        }
        h.assertTrue(dispatcher.execute(COMMAND + "start 60", operator) > 0 && CoreProfiler.active(server),
                "The operator could not start recording through the dispatcher");
        try {
            var showingActivity = CoreProfiler.begin(server);
            CoreProfiler.endCoreTick(busy, showingActivity);
            CoreProfiler.endWork(busy, Work.SENSOR_SCAN, CoreProfiler.begin(server), 7);
            var showingCost = CoreProfiler.begin(server);
            holdSpan();
            CoreProfiler.endCoreTick(costly, showingCost);
            CoreProfiler.endWork(costly, Work.SENSOR_SCAN, CoreProfiler.begin(server), 1);
            var current = CoreProfiler.report(server);
            h.assertTrue(current.ticks() == 1 && sample(current, costly).totalNanos() > sample(current, busy).totalNanos(),
                    "Controlled profile samples did not separate cost and activity");
            h.assertTrue(result(operator, "start 1") == 0 && CoreProfiler.active(server)
                            && sample(CoreProfiler.report(server), busy).work().get(Work.SENSOR_SCAN).count() == 7,
                    "A second start replaced the active recording");

            console.messages.clear();
            h.assertTrue(result(operator, "report cost") > 0
                            && console.order(costly.creatureId()) >= 0
                            && console.order(busy.creatureId()) > console.order(costly.creatureId())
                            && console.hasLink(ClickEvent.Action.RUN_COMMAND, "/" + COMMAND + "locate " + costly.creatureId()),
                    "Cost report did not put the costlier core first");
            console.messages.clear();
            h.assertTrue(result(operator, "report activity") > 0
                            && console.order(busy.creatureId()) >= 0
                            && console.order(costly.creatureId()) > console.order(busy.creatureId()),
                    "Activity report did not put the higher operation count first");
            h.assertTrue(result(operator, "stop") > 0 && !CoreProfiler.active(server) && CoreProfiler.begin(server) == 0,
                    "Stop did not disable timing");
            var completed = CoreProfiler.report(server);
            h.assertTrue(completed != null && completed.cores().equals(current.cores()) && result(operator, "stop") == 0,
                    "Stopping twice changed or lost the finished report");
            h.assertTrue(result(operator, "start") > 0 && CoreProfiler.report(server).cores().isEmpty(),
                    "A new recording retained previous samples");
        } finally {
            CoreProfiler.stop(server);
        }
        h.succeed();
    }

    @GameTest(template = "combat", batch = "profile_native_operations")
    public static void PhysicalCoreTicksCountActualOperationsForAllCoreKinds(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var ordinary = fixture(h, SableLife.HEART.get(), 3);
        var survival = fixture(h, SableLife.SURVIVAL_LIFE_CORE.get(), 12);
        var redstone = fixture(h, SableLife.REDSTONE_CORE.get(), 21);
        var cores = List.of(ordinary, survival, redstone);
        var targets = new ArrayList<Pig>();
        var actor = TestActors.creative(level);
        for (var core : cores) {
            configureWork(h, core);
            if (core.isSurvivalCore()) {
                h.assertTrue(core.depositSurvivalTokens(actor, new ItemStack(Items.COPPER_INGOT, 20)), "Could not fund Survival workloads");
            }
            if (!core.isControlCore()) {
                actor.setPos(ModuleArena.corePosition(core));
                core.tryActivate(actor);
            }
            h.assertTrue(core.operational(), "Profiler fixture core was not operational");
            if (!core.isControlCore()) targets.add(homingTarget(h, core));
        }

        h.assertTrue(CoreProfiler.start(server, 20), "Could not open the native operation recording");
        try {
            for (var core : cores) {
                tick(core);
                assertWork(h, sample(CoreProfiler.report(server), core), core.isControlCore(), 1);
                h.assertTrue(leverPowered(core), "A counted block use did not flip its lever");
                h.assertTrue(shotCount(core) == (core.isControlCore() ? 0 : 2),
                        "Homing deployment must count one salvo containing two actual shots");
            }
            for (var core : cores) {
                var sensor = module(core, "sensor");
                sensor.edit("enabled", 0, "");
                core.wireless().master(false);
                tick(core);
                assertWork(h, sample(CoreProfiler.report(server), core), core.isControlCore(), 1);
                sensor.edit("enabled", 1, "");
                core.wireless().master(true);
                tick(core);
                assertWork(h, sample(CoreProfiler.report(server), core), core.isControlCore(), 2);
                h.assertTrue(!leverPowered(core), "A second successful block use did not flip its lever back");
            }
            h.assertTrue(CoreProfiler.report(server).ticks() == 1, "Multiple core ticks in one server tick inflated the session tick count");
        } finally {
            CoreProfiler.stop(server);
            for (var core : cores) core.modules().clearRuntime();
            targets.forEach(Pig::discard);
        }
        h.succeed();
    }

    @GameTest(template = "combat", batch = "profile_locate", timeoutTicks = 85)
    public static void LocateUsesWorldPoseAndFallsBackAfterNativeUnload(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var core = fixture(h, SableLife.HEART.get(), 3);
        var body = Sable.HELPER.getContaining(level, core.getBlockPos());
        var dim = server.getLevel(Level.NETHER);
        h.assertTrue(dim != null, "The cross-dimensional profiler fixture requires the Nether");
        var remotePos = new BlockPos(12, 85, 13);
        var previous = dim.getBlockState(remotePos);
        dim.setBlockAndUpdate(remotePos, SableLife.REDSTONE_CORE.get().defaultBlockState());
        var remote = (HeartBlockEntity) dim.getBlockEntity(remotePos);
        LoadedCoreIndex.observe(remote);
        var console = new CapturingOperator(level);
        var source = console.source();
        int overworldForced = level.getForcedChunks().size(), netherForced = dim.getForcedChunks().size();
        h.assertTrue(CoreProfiler.start(server, 20), "Could not open the pose recording");
        var first = ModuleArena.corePosition(core);
        var begun = CoreProfiler.begin(server);
        holdSpan();
        CoreProfiler.endCoreTick(core, begun);
        h.assertTrue(!first.equals(core.getBlockPos().getCenter()), "Fixture stored its world position at its plot coordinates");

        h.runAfterDelay(2, () -> {
            try {
                body.logicalPose().position().add(7, 2, -4);
                var moved = ModuleArena.corePosition(core);
                h.assertTrue(moved.distanceTo(first) > 5, "Native Sable pose did not move");
                tick(core);
                var sample = sample(CoreProfiler.report(server), core);
                h.assertTrue(sample.peak().position().distanceTo(first) < 1e-6 && sample.last().position().distanceTo(moved) < 1e-6
                                && sample.last().gameTime() > sample.peak().gameTime(),
                        "Profiler did not retain distinct world-space peak and last positions");
                var current = CoreProfiler.locate(server, core.creatureId());
                h.assertTrue(current != null && current.current() && current.location().equals(sample.last()),
                        "Locate did not return the moved loaded body");
                var remoteLocation = CoreProfiler.locate(server, remote.creatureId());
                h.assertTrue(remoteLocation != null && remoteLocation.current()
                                && remoteLocation.location().dimension().equals(dim.dimension().location())
                                && remoteLocation.location().position().equals(remotePos.getCenter()),
                        "Locate missed a loaded core in another dimension");
                h.assertTrue(result(source, "locate " + remote.creatureId()) > 0
                                && console.hasTeleportTo(dim.dimension().location().toString()),
                        "Cross-dimensional command did not suggest an explicit dimension teleport");

                h.assertTrue(CoreProfiler.stop(server), "Could not freeze the report before unload");
                SubLevelContainer.getContainer(level).removeSubLevel(body, SubLevelRemovalReason.UNLOADED);
                var last = CoreProfiler.locate(server, core.creatureId());
                h.assertTrue(last != null && !last.current() && last.location().equals(sample.last()),
                        "An unloaded core lost its last sampled world position");
                console.messages.clear();
                h.assertTrue(result(source, "locate " + core.creatureId()) > 0
                                && console.hasTeleportTo(level.dimension().location().toString()),
                        "The locate command did not use the stored world position");
                h.assertTrue(SubLevelContainer.getContainer(level).getSubLevel(body.getUniqueId()) == null
                                && level.getForcedChunks().size() == overworldForced && dim.getForcedChunks().size() == netherForced,
                        "Looking up a position reloaded the Sable body or added forced chunks");
                h.assertTrue(CoreProfiler.locate(server, UUID.randomUUID()) == null,
                        "An unknown identifier unexpectedly acquired a location");
            } finally {
                CoreProfiler.stop(server);
                dim.setBlockAndUpdate(remotePos, previous);
            }
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "profile_timeout", timeoutTicks = 20000)
    public static void RecordingStopsAtItsWallClockDeadline(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var source = new CapturingOperator(level).source();
        h.assertTrue(result(source, "start 1") > 0 && CoreProfiler.active(server), "Could not start a one-second recording");
        h.succeedWhen(() -> {
            h.assertTrue(!CoreProfiler.active(server), "Recording has not reached its wall-clock deadline yet");
            var finished = CoreProfiler.report(server);
            h.assertTrue(finished != null && finished.elapsedNanos() >= TimeUnit.SECONDS.toNanos(1) && finished.ticks() > 0
                            && CoreProfiler.begin(server) == 0 && result(source, "stop") == 0,
                    "Automatic stop did not keep a completed one-second report or disable timing");
        });
    }

    private static HeartBlockEntity fixture(GameTestHelper h, Block block, int x) {
        var level = h.getLevel();
        var at = h.absolutePos(new BlockPos(x, 40, 4));
        var support = at.east();
        var lever = support.above();
        level.setBlockAndUpdate(at, block.defaultBlockState());
        level.setBlockAndUpdate(support, Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(lever, Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR));
        var body = SubLevelAssemblyHelper.assembleBlocks(level, at, List.of(at, support, lever),
                new BoundingBox3i(at.getX(), at.getY(), at.getZ(), support.getX(), lever.getY(), lever.getZ()));
        return (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    }

    private static void configureWork(GameTestHelper h, HeartBlockEntity core) {
        var sensor = marked(core, "sensor", Direction.NORTH);
        sensor.edit("target", 1, "");
        sensor.edit("lineOfSight", 0, "");
        for (int height : List.of(1, 4)) {
            var interaction = marked(core, "block_interaction", Direction.EAST);
            interaction.edit("interactionSpace", 1, "");
            interaction.edit("startDistance", 1, "");
            interaction.edit("interactionSize", 1, "");
            interaction.edit("interactionOffsetY", height, "");
        }
        var relay = marked(core, "wireless_redstone", Direction.UP);
        var table = core.modules().add("move_set");
        table.edit("ignoreConditions", 1, "");
        h.assertTrue(table.moveSet().edit("add", 0, "", core.modules())
                        && table.moveSet().edit("action_add", 0, "0|" + relay.id(), core.modules()), "Could not configure the Move Set");
        if (!core.isControlCore()) {
            var homing = marked(core, "homing", Direction.NORTH);
            homing.edit("target", 1, "");
            homing.edit("targetRange", 8, "");
            homing.edit("lineOfSight", 0, "");
            homing.edit("homingCount", 2, "");
            homing.edit("homingFan", 0, "");
        }
    }

    private static VirtualModule marked(HeartBlockEntity core, String type, Direction face) {
        var module = core.modules().add(type);
        module.anchors(List.of(ModuleAnchor.create(core, core.getBlockPos(), face)));
        return module;
    }

    private static Pig homingTarget(GameTestHelper h, HeartBlockEntity core) {
        var homing = module(core, "homing");
        var point = ModuleHoming.formation(core, homing).getFirst().add(0, -.45, -2);
        var level = h.getLevel();
        var target = EntityType.PIG.create(level);
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setPos(point);
        level.getChunkAt(target.blockPosition());
        h.assertTrue(level.addFreshEntity(target), "Could not spawn the Homing target");
        return target;
    }

    private static VirtualModule module(HeartBlockEntity core, String type) {
        return core.modules().all().stream().filter(module -> module.type().equals(type)).findFirst().orElseThrow();
    }

    private static boolean leverPowered(HeartBlockEntity core) {
        return core.getLevel().getBlockState(core.getBlockPos().east().above()).getValue(LeverBlock.POWERED);
    }

    private static int shotCount(HeartBlockEntity core) {
        int count = 0;
        for (var entity : ((ServerLevel) core.getLevel()).getAllEntities()) {
            if (entity instanceof ModuleShot shot && !shot.isRemoved() && core.creatureId().equals(shot.attackOwner())) count++;
        }
        return count;
    }

    private static void tick(HeartBlockEntity core) {
        HeartBlockEntity.serverTick(core.getLevel(), core.getBlockPos(), core.getBlockState(), core);
    }

    private static CoreSample sample(CoreProfiler.Report report, HeartBlockEntity core) {
        return report.cores().stream().filter(row -> row.id().equals(core.creatureId())).findFirst().orElseThrow();
    }

    private static void assertWork(GameTestHelper h, CoreSample sample, boolean redstone, int rounds) {
        h.assertTrue(sample.totalNanos() > 0 && sample.peakNanos() == sample.totalNanos(),
                "Missing or incorrectly split native same-tick cost");
        for (Work work : Work.values()) {
            long expected = switch (work) {
                case BLOCK_SCAN -> 2L * rounds;
                case HOMING_PREFLIGHT, HOMING_DEPLOY -> redstone ? 0 : rounds;
                default -> rounds;
            };
            var measured = sample.work().get(work);
            long count = measured == null ? 0 : measured.count();
            h.assertTrue(count == expected, work + " counted " + count + " instead of " + expected + " for " + sample.id());
        }
    }

    private static void holdSpan() {
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(20);
        while (until - System.nanoTime() > 0) LockSupport.parkNanos(until - System.nanoTime());
    }

    private static int result(CommandSourceStack source, String command) {
        try {
            return source.getServer().getCommands().getDispatcher().execute(COMMAND + command, source);
        } catch (CommandSyntaxException error) {
            throw new AssertionError("Profiler command failed to parse: " + command, error);
        }
    }

    private static final class CapturingOperator extends FakePlayer {
        private final List<Component> messages = new ArrayList<>();

        CapturingOperator(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "ProfilerOperator"));
        }

        CommandSourceStack source() {
            var level = serverLevel();
            var name = getGameProfile().getName();
            return new CommandSourceStack(this, Vec3.ZERO, Vec2.ZERO, level, 2, name,
                    Component.literal(name), level.getServer(), this);
        }

        int order(UUID core) {
            for (int index = 0; index < messages.size(); index++) {
                if (contains(messages.get(index), core)) return index;
            }
            return -1;
        }

        private boolean contains(Component message, UUID core) {
            if (message.getString().contains(core.toString()) || message.getString().contains(core.toString().substring(0, 8))) return true;
            var click = message.getStyle().getClickEvent();
            if (click != null && click.getValue().endsWith(core.toString())) return true;
            return message.getSiblings().stream().anyMatch(sibling -> contains(sibling, core));
        }

        boolean hasTeleportTo(String dimension) {
            return hasLink(ClickEvent.Action.SUGGEST_COMMAND, "/execute in " + dimension + " run tp @s ");
        }

        boolean hasLink(ClickEvent.Action action, String commandPrefix) {
            return messages.stream().anyMatch(message -> hasLink(message, action, commandPrefix));
        }

        private boolean hasLink(Component message, ClickEvent.Action action, String commandPrefix) {
            var click = message.getStyle().getClickEvent();
            return click != null && click.getAction() == action && click.getValue().startsWith(commandPrefix)
                    || message.getSiblings().stream().anyMatch(sibling -> hasLink(sibling, action, commandPrefix));
        }

        @Override public void sendSystemMessage(Component message) { messages.add(message); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }
}
