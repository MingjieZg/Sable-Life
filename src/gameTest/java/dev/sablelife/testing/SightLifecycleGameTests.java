package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.HeartModules;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.module.ModuleCombat;
import dev.sablelife.module.ProjectileAim;
import dev.sablelife.module.VirtualModule;
import dev.sablelife.sense.SensorGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("sablelife_sight_lifecycle_tests")
@PrefixGameTestTemplate(false)
public final class SightLifecycleGameTests {
    @GameTest(template = "combat", timeoutTicks = 45)
    public static void LegacyMoveKeepsTheActiveWeaponTargetOutsideTheEntryCone(GameTestHelper helper) {
        var scene = setup(helper);
        scene.gun().edit("lineOfSight", 0, "");
        editMove(helper, scene, "dispatchBusy", 30, "0");
        scene.activate(helper);
        helper.assertTrue(ProjectileAim.directions(scene.heart(), scene.gun(), scene.muzzle()).size() == 1, "Missing initial weapon lock");
        helper.runAfterDelay(1, () -> scene.target().setPos(scene.target().position().add(-6, 0, 7)));
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(HeartModules.triggered(scene.gun(), scene.heart()), "Weapon action unexpectedly stopped");
            helper.assertTrue(scene.target().getUUID().equals(scene.heart().modules().moveTarget(scene.gun().id())), "Action lost the assigned target");
            var directions = ProjectileAim.directions(scene.heart(), scene.gun(), scene.muzzle());
            helper.assertTrue(directions.size() == 1 && directions.getFirst().z > .8, "Entry cone revoked an active move's weapon target");
            scene.finish(helper);
        });
    }

    @GameTest(template = "combat", timeoutTicks = 65)
    public static void LongerSuccessorCannotReviveAnExpiredSourceLock(GameTestHelper helper) {
        var scene = setup(helper);
        editMove(helper, scene, "lineOfSight", 1, "0");
        editMove(helper, scene, "sightMemory", 12, "0");
        editMove(helper, scene, "selection_mode", 1, "0");
        editMove(helper, scene, "dispatchCooldown", 200, "0");
        editMove(helper, scene, "dispatchBusy", 2, "0");
        editMove(helper, scene, "dispatchRest", 25, "0");
        editMove(helper, scene, "action_duration", .1, "0|0");
        addMove(helper, scene, 1);
        editMove(helper, scene, "lineOfSight", 1, "1");
        editMove(helper, scene, "sightMemory", 100, "1");
        scene.activate(helper);
        var first = scene.heart().modules().moveAim(scene.gun().id());
        helper.assertTrue(first != null && first.visible() && first.id().equals(scene.target().getUUID()), "Missing first move's visible target");
        var wall = BlockPos.containing(scene.muzzle()).east(4);
        helper.runAfterDelay(1, () -> wall(helper, wall, true));
        helper.runAfterDelay(22, () -> {
            var center = SableBodyProbe.toWorld(helper.getLevel(), scene.heart().getBlockPos().getCenter());
            helper.assertTrue(!SensorGeometry.visible(helper.getLevel(), scene.heart().creatureId(), center, scene.target().getBoundingBox().getCenter()), "Target was uncovered");
            helper.assertTrue(scene.heart().modules().moveAim(scene.gun().id()) == null, "First action was still running instead of resting");
        });
        helper.runAfterDelay(34, () -> {
            helper.assertTrue(scene.heart().modules().moveAim(scene.gun().id()) == null, "Successor revived an expired target");
            scene.finish(helper);
        });
    }

    @GameTest(template = "combat", timeoutTicks = 30)
    public static void PhysicalSensorWithMemoryUsesItsOwnTriggerWhenWirelessIsOff(GameTestHelper helper) {
        var scene = setup(helper);
        var sensor = sensor(scene);
        scene.activate(helper);
        scene.heart().wireless().master(false);
        helper.assertTrue(HeartModules.triggered(sensor, scene.heart()), "Test requires an independently triggered sensor");
        scene.heart().modules().events().tick(scene.heart());
        helper.assertTrue(scene.heart().modules().events().sensorDetected(sensor.id()), "Sensor with zero memory failed to detect");
        sensor.edit("sightMemory", 20, "");
        scene.heart().modules().events().tick(scene.heart());
        helper.assertTrue(scene.heart().modules().events().sensorDetected(sensor.id()), "Memory stopped a physical sensor from detecting a visible target");
        scene.finish(helper);
    }

    @GameTest(template = "combat", timeoutTicks = 30)
    public static void LinkedFacingCannotRecallAnObservationItNeverSaw(GameTestHelper helper) {
        var scene = setup(helper);
        var heart = scene.heart();
        var pivot = SableBodyProbe.toWorld(helper.getLevel(), heart.getBlockPos().getCenter());
        var sensor = sensor(scene);
        sensor.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos().south(), Direction.EAST)));
        sensor.edit("sightMemory", 20, "");
        var facing = heart.modules().add("auto_facing");
        facing.anchors(scene.gun().anchors());
        facing.edit("facingSource", 1, "");
        facing.edit("lineOfSight", 1, "");
        facing.facingSensor(sensor.id());
        var cover = BlockPos.containing(pivot).east();
        for (int y = -1; y <= 3; y++)
            helper.getLevel().setBlockAndUpdate(cover.above(y), Blocks.STONE.defaultBlockState());

        scene.activate(helper);
        var first = heart.modules().events().sensorAim(heart, sensor.id());
        helper.assertTrue(first != null && first.visible() && first.id().equals(scene.target().getUUID()), "Sensor failed to see around the first wall");
        helper.assertTrue(facingState(heart, facing).equals("no_target"), "Facing could see through the first wall");
        helper.runAfterDelay(1, () -> {
            for (int y = -1; y <= 3; y++)
                helper.getLevel().setBlockAndUpdate(cover.south().above(y), Blocks.STONE.defaultBlockState());
        });
        helper.runAfterDelay(3, () -> {
            var remembered = heart.modules().events().sensorAim(heart, sensor.id());
            helper.assertTrue(remembered != null && !remembered.visible(), "Sensor did not retain its initially visible target");
            helper.assertTrue(facingState(heart, facing).equals("no_target"), "Facing began tracking only when the sensor also lost sight");
            scene.finish(helper);
        });
    }

    @GameTest(template = "combat", timeoutTicks = 30)
    public static void HiddenMovementDoesNotLeakThroughTheSensorRangeCheck(GameTestHelper helper) {
        var scene = setup(helper);
        var sensor = sensor(scene);
        sensor.edit("radius", 9, "");
        sensor.edit("sightMemory", 20, "");
        scene.activate(helper);
        var events = scene.heart().modules().events();
        var first = events.sensorAim(scene.heart(), sensor.id());
        helper.assertTrue(first != null && first.visible(), "Missing initial observation");
        var cover = BlockPos.containing(scene.muzzle()).east(4);
        helper.runAfterDelay(1, () -> {
            wall(helper, cover, true);
            scene.target().setPos(scene.target().position().add(7, 0, 0));
        });
        helper.runAfterDelay(4, () -> {
            var retained = events.sensorAim(scene.heart(), sensor.id());
            helper.assertTrue(retained != null && !retained.visible() && first.point().equals(retained.point()),
                    "Hidden movement changed the range outcome or the remembered position");
            wall(helper, cover, false);
        });
        helper.runAfterDelay(7, () -> {
            helper.assertTrue(events.sensorAim(scene.heart(), sensor.id()) == null, "A visibly out-of-range target stayed locked");
            scene.finish(helper);
        });
    }

    @GameTest(template = "combat", batch = "move_sensor_order", timeoutTicks = 35)
    public static void SensorProducerRunsFirstWithoutChangingAuthoredActionPriority(GameTestHelper helper) {
        var scene = setup(helper);
        var heart = scene.heart();
        var sensor = sensor(scene);
        link(scene.table(), sensor);
        editMove(helper, scene, "dispatchRest", 1, "0");
        editMove(helper, scene, "dispatchCooldown", 0, "0");
        var producer = heart.modules().add("move_set");
        producer.edit("ignoreConditions", 1, "");
        addProducer(helper, heart, producer, sensor);
        addAction(helper, heart, producer, scene.gun());

        scene.activate(helper);
        helper.assertTrue(heart.modules().events().sensorDetected(sensor.id()), "The producer did not activate the sensor");
        var target = heart.modules().moveTarget(scene.gun().id());
        helper.assertTrue(scene.target().getUUID().equals(target),
                "A linked move authored first did not take priority after seeing the later producer's sensor in the same tick");

        helper.assertTrue(heart.modules().reorder(producer.id(), -1) && heart.modules().reorder(producer.id(), -1),
                "Failed to move the producer before the consumer");
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(heart.modules().events().sensorDetected(sensor.id()) && HeartModules.triggered(scene.gun(), heart),
                    "Moving the producer first disabled either output");
            helper.assertTrue(heart.modules().moveTarget(scene.gun().id()) == null,
                    "The same-tick winner did not reflect the new authored order");
            scene.finish(helper);
        });
    }

    @GameTest(template = "combat", batch = "move_sensor_cycle", timeoutTicks = 30)
    public static void UnpoweredMoveSetSensorCycleStaysIdle(GameTestHelper helper) {
        var scene = setup(helper);
        var first = sensor(scene);
        var second = sensor(scene);
        var next = scene.heart().modules().add("move_set");
        link(scene.table(), first);
        link(next, second);
        editMove(helper, scene, "action_delete", 0, "0|0");
        addAction(helper, scene.heart(), scene.table(), second);
        addProducer(helper, scene.heart(), next, first);

        scene.activate(helper);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!HeartModules.triggered(first, scene.heart()) && !HeartModules.triggered(second, scene.heart()),
                    "An unpowered Move Set sensor cycle started itself");
            scene.finish(helper);
        });
    }

    @GameTest(template = "combat", batch = "move_observer_cold", timeoutTicks = 30)
    public static void MoveControlledFacingAndAlignmentCannotRecallWhatTheyNeverSaw(GameTestHelper helper) {
        var scene = setup(helper);
        var heart = scene.heart();
        var sensor = sensor(scene);
        sensor.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos().south(), Direction.EAST)));
        var movement = movement(helper, scene, sensor);
        var pivot = SableBodyProbe.toWorld(helper.getLevel(), heart.getBlockPos().getCenter());
        var cover = BlockPos.containing(pivot).east();
        narrowCover(helper, cover);

        scene.activate(helper);
        var initial = heart.modules().events().sensorAim(heart, sensor.id());
        helper.assertTrue(initial != null && initial.visible(), "The remote sensor could not see around the first wall");
        helper.assertTrue(!movement.aligned(heart) && !movement.facing(heart), "A movement module saw through its own wall");
        helper.runAfterDelay(1, () -> narrowCover(helper, cover.south()));
        helper.runAfterDelay(4, () -> {
            var hidden = heart.modules().events().sensorAim(heart, sensor.id());
            helper.assertTrue(hidden != null && !hidden.visible(), "The source did not retain its initial observation");
            helper.assertTrue(!movement.aligned(heart) && !movement.facing(heart),
                    "A movement module followed a position it had never seen when its Move Set target became hidden");
            scene.finish(helper);
        });
    }

    @GameTest(template = "combat", batch = "move_observer_warm", timeoutTicks = 35)
    public static void MoveControlledFacingAndAlignmentRetainObservedPositionsUntilSourceExpires(GameTestHelper helper) {
        var scene = setup(helper);
        var heart = scene.heart();
        var sensor = sensor(scene);
        var movement = movement(helper, scene, sensor);
        sensor.edit("sightMemory", 10, "");

        scene.activate(helper);
        helper.assertTrue(movement.aligned(heart) && movement.facing(heart), "Both movement modules must observe the uncovered target first");
        var pivot = SableBodyProbe.toWorld(helper.getLevel(), heart.getBlockPos().getCenter());
        helper.runAfterDelay(1, () -> wall(helper, BlockPos.containing(pivot).east(3), true));
        helper.runAfterDelay(4, () -> {
            var hidden = heart.modules().events().sensorAim(heart, sensor.id());
            helper.assertTrue(hidden != null && !hidden.visible(), "The source did not retain the now-hidden target");
            helper.assertTrue(movement.aligned(heart) && movement.facing(heart),
                    "A movement module lost its observation before the source expired");
        });
        helper.runAfterDelay(16, () -> {
            helper.assertTrue(heart.modules().events().sensorAim(heart, sensor.id()) == null, "The source failed to expire");
            helper.assertTrue(!movement.aligned(heart) && !movement.facing(heart), "A movement module extended the source's retention time");
            scene.finish(helper);
        });
    }

    @GameTest(template = "combat", batch = "alignment_inner_radius_legacy_move", timeoutTicks = 35)
    public static void LegacyMoveAlignmentRejectsItsInheritedTargetInsideTheHole(GameTestHelper helper) {
        moveAlignmentInnerRadius(helper, false);
    }

    @GameTest(template = "combat", batch = "alignment_inner_radius_observed_move", timeoutTicks = 35)
    public static void ObservedMoveAlignmentRejectsItsInheritedTargetInsideTheHole(GameTestHelper helper) {
        moveAlignmentInnerRadius(helper, true);
    }

    @GameTest(template = "combat", batch = "alignment_priority_move", timeoutTicks = 24)
    public static void PriorityOnlyAlignmentKeepsItsAssignedMoveTargetWithoutFallback(GameTestHelper helper) {
        var scene = setup(helper);
        var heart = scene.heart();
        var alignment = heart.modules().add("navigation");
        var mark = ModuleAnchor.create(heart, heart.getBlockPos(), Direction.EAST);
        alignment.anchors(List.of(mark));
        alignment.edit("target", 1, "");
        alignment.edit("lineOfSight", 1, "");
        alignment.edit("radius", 9, "");
        alignment.edit("priorityOnly", 1, "");
        editMove(helper, scene, "action_delete", 0, "0|0");
        addAction(helper, heart, scene.table(), alignment);
        editMove(helper, scene, "dispatchNear", 0, "0");
        editMove(helper, scene, "dispatchBusy", 30, "0");
        editMove(helper, scene, "action_duration", 1, "0|0");
        editMove(helper, scene, "lineOfSight", 1, "0");
        editMove(helper, scene, "sightMemory", 24, "0");
        var fallback = EntityType.PIG.create(helper.getLevel());
        fallback.setCustomName(Component.literal("Listed mob outside the move cone"));
        fallback.setNoAi(true);
        fallback.setNoGravity(true);
        fallback.setPos(mark.world(heart, false).add(-6, -fallback.getBbHeight() / 2, 0));
        helper.getLevel().addFreshEntity(fallback);
        var rules = heart.relations();
        var events = heart.modules().events();
        var navigation = events.navigation();
        try {
            rules.prioritizeMob("minecraft:pig");
            scene.activate(helper);
            var aim = heart.modules().moveAim(alignment.id());
            helper.assertTrue(HeartModules.triggered(alignment, heart) && aim != null && aim.visible()
                            && aim.id().equals(scene.target().getUUID()) && heart.modules().moveAction(alignment.id()) != null,
                    "The Move Set did not dispatch the expected observed target inside its cone");
            helper.assertTrue(navigation.name(alignment.id()).isEmpty()
                            && navigation.left(alignment.id()) == 0 && navigation.right(alignment.id()) == 0,
                    "Priority-only alignment followed an unlisted inherited target or an independent listed candidate");
            rules.prioritizeMob("minecraft:cow");
            events.tick(heart);
            helper.assertTrue(navigation.name(alignment.id()).equals(scene.target().getName().getString()),
                    "Listing the assigned type did not restore alignment to the same Move Set target");
            helper.succeed();
        } finally {
            alignment.edit("enabled", 0, "");
            scene.gun().edit("enabled", 0, "");
            scene.target().discard();
            fallback.discard();
        }
    }

    private static void moveAlignmentInnerRadius(GameTestHelper helper, boolean observed) {
        var scene = setup(helper);
        var heart = scene.heart();
        var alignment = heart.modules().add("navigation");
        var mark = ModuleAnchor.create(heart, heart.getBlockPos(), Direction.EAST);
        alignment.anchors(List.of(mark));
        alignment.edit("target", 1, "");
        alignment.edit("lineOfSight", 1, "");
        alignment.edit("radius", 9, "");
        alignment.edit("innerRadius", 4, "");
        editMove(helper, scene, "action_delete", 0, "0|0");
        addAction(helper, heart, scene.table(), alignment);
        editMove(helper, scene, "dispatchNear", 0, "0");
        editMove(helper, scene, "dispatchBusy", 30, "0");
        editMove(helper, scene, "action_duration", 1, "0|0");
        if (observed) {
            editMove(helper, scene, "lineOfSight", 1, "0");
            editMove(helper, scene, "sightMemory", 24, "0");
        }
        var fallback = EntityType.COW.create(helper.getLevel());
        fallback.setNoAi(true);
        fallback.setNoGravity(true);
        fallback.setCustomName(Component.literal("Independent ring candidate"));
        fallback.setPos(mark.world(heart, false).add(-6, -fallback.getBbHeight() / 2, 0));
        helper.getLevel().addFreshEntity(fallback);
        Runnable cleanup = () -> {
            alignment.edit("enabled", 0, "");
            scene.gun().edit("enabled", 0, "");
            scene.target().discard();
            fallback.discard();
        };
        var runtime = heart.modules().events().navigation();
        checkAlignmentMove(cleanup, () -> {
            scene.activate(helper);
            helper.assertTrue(HeartModules.triggered(alignment, heart)
                            && scene.target().getUUID().equals(heart.modules().moveTarget(alignment.id()))
                            && ((heart.modules().moveAction(alignment.id()) != null) == observed)
                            && runtime.name(alignment.id()).equals(scene.target().getName().getString()),
                    "Move Set did not start with the expected inherited target and observation path");
            scene.target().setPos(mark.world(heart, false).add(2, -scene.target().getBbHeight() / 2, 0));
        });
        helper.runAfterDelay(2, () -> checkAlignmentMove(cleanup, () -> {
            var aim = heart.modules().moveAim(alignment.id());
            helper.assertTrue(aim != null && aim.visible() && aim.id().equals(scene.target().getUUID())
                            && aim.point().distanceToSqr(mark.world(heart, false)) < 16,
                    "Move Set stopped supplying the intended visible target before alignment checked the hole");
            helper.assertTrue(runtime.name(alignment.id()).isEmpty()
                            && runtime.left(alignment.id()) == 0 && runtime.right(alignment.id()) == 0,
                    "Alignment tracked its inherited target in the hole or switched to its independent candidate");
            scene.target().setPos(mark.world(heart, false).add(6, -scene.target().getBbHeight() / 2, 0));
        }));
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(runtime.name(alignment.id()).equals(scene.target().getName().getString()),
                        "Alignment did not resume the same inherited target after it returned to the ring");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    private static void checkAlignmentMove(Runnable cleanup, Runnable check) {
        try {
            check.run();
        } catch (RuntimeException | AssertionError failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static void link(VirtualModule table, VirtualModule sensor) {
        table.edit("facingSource", 1, "");
        table.facingSensor(sensor.id());
    }

    private static void addProducer(GameTestHelper helper, HeartBlockEntity heart, VirtualModule table, VirtualModule sensor) {
        var moves = table.moveSet();
        helper.assertTrue(moves.edit("add", 0, "", heart.modules()), "Could not add a producer move");
        moves.get(0).config().edit("dispatchRest", 1, "");
        moves.get(0).config().edit("dispatchCooldown", 0, "");
        addAction(helper, heart, table, sensor);
    }

    private static void addAction(GameTestHelper helper, HeartBlockEntity heart, VirtualModule table, VirtualModule module) {
        helper.assertTrue(table.moveSet().edit("action_add", 0, "0|" + module.id(), heart.modules()), "Could not add " + module.type() + " to the move");
    }

    private static Movement movement(GameTestHelper helper, Scene scene, VirtualModule sensor) {
        var heart = scene.heart();
        var mark = ModuleAnchor.create(heart, heart.getBlockPos(), Direction.EAST);
        var alignment = heart.modules().add("navigation");
        alignment.anchors(List.of(mark));
        alignment.edit("target", 1, "");
        alignment.edit("lineOfSight", 0, "");
        var facing = heart.modules().add("auto_facing");
        facing.anchors(List.of(mark));
        facing.edit("target", 1, "");
        facing.edit("lineOfSight", 1, "");
        sensor.edit("sightMemory", 20, "");
        link(scene.table(), sensor);
        editMove(helper, scene, "action_delete", 0, "0|0");
        editMove(helper, scene, "lineOfSight", 1, "0");
        addAction(helper, heart, scene.table(), alignment);
        addAction(helper, heart, scene.table(), facing);
        return new Movement(alignment, facing);
    }

    private static void narrowCover(GameTestHelper helper, BlockPos cover) {
        for (int y = -1; y <= 4; y++) helper.getLevel().setBlockAndUpdate(cover.above(y), Blocks.STONE.defaultBlockState());
    }

    private record Movement(VirtualModule alignment, VirtualModule facingModule) {
        boolean aligned(HeartBlockEntity heart) { return !heart.modules().events().navigation().name(alignment.id()).isEmpty(); }
        boolean facing(HeartBlockEntity heart) { return facingState(heart, facingModule).equals("tracking"); }
    }

    private static VirtualModule sensor(Scene scene) {
        var sensor = scene.heart().modules().add("sensor");
        sensor.anchors(scene.gun().anchors());
        sensor.edit("trigger", 0, "");
        sensor.edit("target", 1, "");
        sensor.edit("lineOfSight", 0, "");
        return sensor;
    }

    private static String facingState(HeartBlockEntity heart, VirtualModule facing) {
        var snapshot = heart.modules().snapshot(heart, false).getList("Modules", 10);
        for (int i = 0; i < snapshot.size(); i++) {
            var row = snapshot.getCompound(i);
            if (row.hasUUID("Id") && row.getUUID("Id").equals(facing.id())) return row.getString("FacingState");
        }
        return "";
    }

    private static void wall(GameTestHelper helper, BlockPos position, boolean present) {
        var state = present ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        for (var block : BlockPos.betweenClosed(position.offset(0, -2, -4), position.offset(0, 2, 4)))
            helper.getLevel().setBlockAndUpdate(block, state);
    }

    private static Scene setup(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(8, 16, 8));
        level.setBlockAndUpdate(position, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(position.south(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, position, List.of(position, position.south()),
                new BoundingBox3i(position.getX(), position.getY(), position.getZ(), position.getX(), position.getY(), position.getZ() + 1));
        var heart = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        var gun = heart.modules().add("gun");
        gun.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), Direction.UP)));
        gun.edit("shotAim", 1, "");
        gun.edit("target", 1, "");
        gun.edit("targetRange", 12, "");
        gun.edit("interval", 200, "");
        var table = heart.modules().add("move_set");
        var target = EntityType.COW.create(level);
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setPos(ModuleCombat.nozzle(heart, gun).add(7, -target.getBbHeight() / 2, 0));
        level.addFreshEntity(target);
        var scene = new Scene(heart, gun, table, target);
        addMove(helper, scene, 0);
        return scene;
    }

    private static void addMove(GameTestHelper helper, Scene scene, int index) {
        var key = Integer.toString(index);
        editMove(helper, scene, "add", 0, "");
        editMove(helper, scene, "action_add", 0, key + "|" + scene.gun().id());
        editMove(helper, scene, "dispatchFront", 3, key);
        editMove(helper, scene, "dispatchCone", 60, key);
        editMove(helper, scene, "target", 1, key);
    }

    private static void editMove(GameTestHelper helper, Scene scene, String key, double value, String text) {
        helper.assertTrue(scene.table().moveSet().edit(key, value, text, scene.heart().modules()), "Move edit rejected: " + key);
    }

    private record Scene(HeartBlockEntity heart, VirtualModule gun, VirtualModule table, LivingEntity target) {
        net.minecraft.world.phys.Vec3 muzzle() { return ModuleCombat.nozzle(heart, gun); }

        void activate(GameTestHelper helper) {
            heart.tryActivate(TestActors.creative(helper.getLevel()));
            heart.modules().tick(heart);
        }

        void finish(GameTestHelper helper) {
            gun.edit("enabled", 0, "");
            target.discard();
            helper.succeed();
        }
    }
}
