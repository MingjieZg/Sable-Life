package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.sablelife.ownership.LoadedCoreIndex;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder("sablelife_sensor_facing_tests")
@PrefixGameTestTemplate(false)
public final class SensorFacingGameTests {
    @GameTest(template = "combat", batch = "sensor_facing_life")
    public static void OrdinaryCoreFollowsFarSensorTargetInsteadOfNearRearMob(GameTestHelper h) {
        sensorFollow(h, SableLife.HEART.get());
    }

    @GameTest(template = "combat", batch = "sensor_facing_control")
    public static void RedstoneCoreFollowsFarSensorTargetInsteadOfNearRearMob(GameTestHelper h) {
        sensorFollow(h, SableLife.REDSTONE_CORE.get());
    }

    @GameTest(template = "combat", batch = "sensor_facing_survival")
    public static void SurvivalCoreFollowsFarSensorTargetInsteadOfNearRearMob(GameTestHelper h) {
        sensorFollow(h, SableLife.SURVIVAL_LIFE_CORE.get());
    }

    private static void sensorFollow(GameTestHelper h, Block block) {
        var heart = fixture(h, block);
        var facing = heart.modules().add("auto_facing");
        facing.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), Direction.NORTH)));
        facing.edit("trigger", 0, "");
        facing.edit("facingSource", 1, "");
        facing.edit("facingRange", 1, "");
        var front = sensor(heart, Direction.NORTH);
        var rear = sensor(heart, Direction.SOUTH);
        facing.facingSensor(front.id());
        var author = TestActors.creative(h.getLevel());
        author.setPos(SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter()));
        if (heart.isSurvivalCore()) heart.depositSurvivalTokens(author,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 10));
        heart.tryActivate(author);
        h.assertTrue(heart.operational(), "Core did not activate");
        var pivot = SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter());
        var far = EntityType.COW.create(h.getLevel());
        far.setNoAi(true); far.setNoGravity(true); far.setPos(pivot.add(0, -far.getBbHeight() / 2, -20)); h.getLevel().addFreshEntity(far);
        var near = EntityType.COW.create(h.getLevel());
        near.setNoAi(true); near.setNoGravity(true); near.setPos(pivot.add(0, -near.getBbHeight() / 2, 3)); h.getLevel().addFreshEntity(near);
        try {
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == far,
                    "Auto facing ignored its sensor or applied its independent type/range");
            facing.facingSensor(rear.id());
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == near, "Wrong sensor was followed");
            facing.facingSensor(front.id());
            front.edit("sensorInvert", 1, ""); heart.modules().changed();
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == far, "Inversion discarded a detected target");
            front.edit("enabled", 0, "");
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == null, "Disabled sensor gave a target");
            front.edit("enabled", 1, ""); front.edit("trigger", 2, ""); heart.modules().changed();
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == null, "Untriggered sensor gave a target");
            front.edit("trigger", 0, ""); far.discard(); heart.modules().changed();
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == null, "Lost target fell back to the rear mob");
            heart.modules().remove(front.id());
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == null, "Deleted sensor redirected the binding");
            facing.facingSensor(UUID.randomUUID());
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == null, "Foreign sensor ID was accepted");
            h.succeed();
        } finally { far.discard(); near.discard(); }
    }

    @GameTest(template = "combat", batch = "sensor_facing_binding")
    public static void BindingUsesCodecAndSurvivesRenameReorderSaveCopyAndPreset(GameTestHelper h) {
        var heart = fixture(h, SableLife.SURVIVAL_LIFE_CORE.get());
        var player = TestActors.creative(h.getLevel());
        player.setPos(SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter()));
        var facing = heart.modules().add("auto_facing");
        var sensor = sensor(heart, Direction.NORTH);
        facing.anchors(sensor.anchors());
        var unrelated = heart.modules().add("auto_facing");
        var menu = new HeartDashboardMenu(1, player.getInventory(), heart);
        var request = new ModuleNetworking.Action(1, heart.modules().revision(), facing.id(), "edit", "facingSensor", 2, "");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ModuleNetworking.Action.CODEC.encode(buffer, request);
            var decoded = ModuleNetworking.Action.CODEC.decode(buffer);
            h.assertTrue(decoded.equals(request) && menu.action(player, decoded.revision(), decoded.module(), decoded.operation(), decoded.key(),
                    decoded.value(), decoded.text()), "Encoded binding request failed");
        } finally { buffer.release(); }
        h.assertTrue(sensor.id().equals(facing.facingSensorId()), "Binding used a different sensor");
        h.assertTrue(!menu.action(player, request.revision(), facing.id(), "edit", "facingSensor", 0, ""), "Repeated stale request changed binding");
        for (double value : new double[]{-1, .5, 4, Double.NaN, Double.POSITIVE_INFINITY})
            h.assertTrue(!menu.action(player, heart.modules().revision(), facing.id(), "edit", "facingSensor", value, ""), "Invalid index accepted");
        h.assertTrue(!menu.action(player, heart.modules().revision(), facing.id(), "edit", "facingSensor", 3, ""), "Non-sensor was accepted");
        h.assertTrue(!menu.action(player, heart.modules().revision(), unrelated.id(), "edit", "facingSensor", 2, "unexpected"), "Unexpected text accepted");
        sensor.edit("name", 0, "Front lookout"); heart.modules().reorder(sensor.id(), -1);
        facing.edit("facingSource", 1, "");
        var saved = VirtualModule.load(facing.save());
        h.assertTrue(sensor.id().equals(saved.facingSensorId()) && saved.value("facingSource") == 1, "Binding changed on reload/reorder");
        var preview = InspectionSnapshot.capture(UUID.randomUUID(), heart, false).rows().stream()
                .filter(row -> row.id().equals(facing.id())).findFirst().orElseThrow();
        h.assertTrue(preview.pieces().stream().noneMatch(piece -> piece.shape() == InspectionSnapshot.Shape.RING),
                "Sensor mode preview retained the unused self-targeting range");
        h.assertTrue(sensor.id().equals(heart.modules().copy(facing.id()).facingSensorId()), "Local duplication lost sensor binding");
        var preset = ModulePreset.create(facing.save());
        h.assertTrue(preset.facingSensorId() == null && preset.value("facingSource") == 1, "Portable preset retained foreign sensor ID");
        var old = facing.save(); old.getCompound("Settings").remove("facingSource"); old.getCompound("Settings").remove("FacingSensorId");
        var legacy = VirtualModule.load(old);
        h.assertTrue(legacy.value("facingSource") == 0 && legacy.facingSensorId() == null, "Old modules changed targeting defaults");
        var configuration = heart.copyConfiguration();
        h.assertTrue(heart.pasteConfiguration(player, configuration), "Configuration paste failed");
        var copiedSensor = heart.modules().all().stream().filter(m -> m.type().equals("sensor")).findFirst().orElseThrow();
        h.assertTrue(!copiedSensor.id().equals(sensor.id()) && heart.modules().all().stream().filter(m -> m.type().equals("auto_facing")
                && m.value("facingSource") == 1).allMatch(m -> copiedSensor.id().equals(m.facingSensorId())), "Configuration copy did not remap sensor binding");
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var permitted = new HeartDashboardMenu(2, player.getInventory(), heart);
        var copiedFacing = heart.modules().all().stream().filter(m -> m.type().equals("auto_facing")).findFirst().orElseThrow();
        h.assertTrue(permitted.action(player, heart.modules().revision(), copiedFacing.id(), "edit", "facingSensor", 1, ""), "Survival player could not bind a Survival Core sensor");
        h.succeed();
    }

    private static VirtualModule sensor(HeartBlockEntity heart, Direction direction) {
        var sensor = heart.modules().add("sensor");
        sensor.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), direction)));
        sensor.edit("trigger", 0, ""); sensor.edit("target", 1, ""); sensor.edit("shape", 1, "");
        sensor.edit("radius", 64, ""); sensor.edit("frontWidth", 4, ""); sensor.edit("lineOfSight", 1, "");
        return sensor;
    }

    @GameTest(template = "combat", batch = "sensor_facing_blueprint")
    public static void BlueprintRemapsBoundSensorAndOrdinaryCoreRejectsSurvivalEdits(GameTestHelper h) {
        var heart = fixture(h, SableLife.HEART.get());
        var sensor = sensor(heart, Direction.NORTH);
        var facing = heart.modules().add("auto_facing");
        facing.anchors(sensor.anchors()); facing.edit("facingSource", 1, ""); facing.facingSensor(sensor.id());
        var player = TestActors.creative(h.getLevel());
        var center = SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter());
        player.setPos(center);
        var menu = new HeartDashboardMenu(1, player.getInventory(), heart);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        h.assertTrue(!menu.action(player, heart.modules().revision(), facing.id(), "edit", "facingSensor", 0, ""),
                "Survival player edited an ordinary creative core");
        h.assertTrue(sensor.id().equals(facing.facingSensorId()), "Rejected request changed the binding");
        var template = dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.capture(heart, center);
        var build = dev.sablelife.compat.photomancy.PhotomancyRespawnBridge.createSpawn(template,
                new net.minecraft.nbt.CompoundTag(), center.add(0, 10, 40), new org.joml.Quaterniond());
        h.assertTrue(build.step(h.getLevel()).equals("DONE"), "Blueprint placement failed");
        var copy = build.heart(h.getLevel());
        h.assertTrue(copy != null, "Placed blueprint core missing");
        var copiedSensor = copy.modules().find(build.module(sensor.id()));
        var copiedFacing = copy.modules().find(build.module(facing.id()));
        h.assertTrue(copiedSensor != null && copiedFacing != null && !copiedSensor.id().equals(sensor.id())
                && copiedSensor.id().equals(copiedFacing.facingSensorId()), "Blueprint retained the original core's sensor ID");
        h.assertTrue(copiedFacing.anchors().getFirst().body(copy) != null, "Blueprint lost facing mark");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "sensor_facing_sight")
    public static void SensorSightBlocksHiddenTargetsAndRestoresAfterWallRemoval(GameTestHelper h) {
        var heart = fixture(h, SableLife.HEART.get());
        var sensor = sensor(heart, Direction.NORTH);
        sensor.edit("lineOfSight", 0, "");
        var facing = heart.modules().add("auto_facing");
        facing.anchors(sensor.anchors()); facing.edit("facingSource", 1, ""); facing.facingSensor(sensor.id());
        heart.tryActivate(TestActors.creative(h.getLevel()));
        var pivot = SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter());
        var cow = EntityType.COW.create(h.getLevel()); cow.setNoAi(true); cow.setNoGravity(true);
        cow.setPos(pivot.add(0, -cow.getBbHeight() / 2, -12)); h.getLevel().addFreshEntity(cow);
        var wall = BlockPos.containing(pivot).north(6);
        try {
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == cow, "Visible sensor target missing");
            for (var pos : BlockPos.betweenClosed(wall.offset(-2, -1, 0), wall.offset(2, 2, 0)))
                h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            heart.modules().changed();
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == null, "Sensor selected target through wall");
            for (var pos : BlockPos.betweenClosed(wall.offset(-2, -1, 0), wall.offset(2, 2, 0)))
                h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            heart.modules().changed();
            h.assertTrue(ModuleAutoFacing.selectTarget(h.getLevel(), heart, facing, pivot) == cow, "Removing wall did not restore target");
            h.succeed();
        } finally { cow.discard(); }
    }

    @GameTest(template = "combat", batch = "sight_memory", timeoutTicks = 45)
    public static void SensorAndAlignmentRememberOnlyTheLastVisiblePoint(GameTestHelper h) {
        var heart = fixture(h, SableLife.HEART.get());
        var sensor = sensor(heart, Direction.NORTH);
        sensor.edit("radius", 12, "");
        sensor.edit("lineOfSight", 0, "");
        sensor.edit(SightMemory.FIELD, 6, "");
        var alignment = heart.modules().add("navigation");
        alignment.anchors(sensor.anchors());
        alignment.edit("trigger", 0, "");
        alignment.edit("target", 1, "");
        alignment.edit("lineOfSight", 0, "");
        alignment.edit("facingSource", 1, "");
        alignment.facingSensor(sensor.id());
        heart.tryActivate(TestActors.creative(h.getLevel()));
        var pivot = SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter());
        var cow = EntityType.COW.create(h.getLevel());
        cow.setNoAi(true); cow.setNoGravity(true);
        cow.setPos(pivot.add(0, -cow.getBbHeight() / 2, -8)); h.getLevel().addFreshEntity(cow);
        var wall = BlockPos.containing(pivot).north(4);
        var initial = heart.modules().events().sensorAim(heart, sensor.id());
        h.assertTrue(initial != null && initial.visible(), "Initial visible target missing");
        // GameTest callbacks can install the wall before the first block-entity tick following assembly.
        heart.modules().events().tick(heart);
        h.assertTrue(!heart.modules().events().navigation().name(alignment.id()).isEmpty(),
                "Alignment did not observe the target before the wall was placed");
        h.runAfterDelay(1, () -> {
            for (var pos : BlockPos.betweenClosed(wall.offset(-3, -1, 0), wall.offset(3, 2, 0)))
                h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            cow.setPos(cow.position().add(1, 0, -2));
        });
        h.runAfterDelay(3, () -> {
            var memory = heart.modules().events().sensorAim(heart, sensor.id());
            h.assertTrue(memory != null && !memory.visible() && memory.point().equals(initial.point()),
                    "Sensor followed live movement behind the wall");
            h.assertTrue(heart.modules().events().sensorDetected(sensor.id()), "Sensor lost its signal during the grace period");
            h.assertTrue(!heart.modules().events().navigation().name(alignment.id()).isEmpty(),
                    "Alignment did not use the sensor's memory when its own delay was zero");
        });
        h.runAfterDelay(10, () -> {
            h.assertTrue(heart.modules().events().sensorAim(heart, sensor.id()) == null, "Sensor memory failed to expire");
            h.assertTrue(heart.modules().events().navigation().name(alignment.id()).isEmpty(), "Alignment extended its source's timer");
            sensor.edit("lineOfSight", 1, ""); heart.modules().changed();
            var throughWall = heart.modules().events().sensorAim(heart, sensor.id());
            h.assertTrue(throughWall != null && throughWall.visible(), "LOS Off did not allow current detection");
            sensor.edit("lineOfSight", 0, ""); heart.modules().changed();
            h.assertTrue(sensor.value(SightMemory.FIELD) == 6 && heart.modules().events().sensorAim(heart, sensor.id()) == null,
                    "Re-enabling sight reused a lock acquired with it Off");
            cow.discard();
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "sensor_player_height")
    public static void SensorSeesOverCoverAtPlayerChestHeight(GameTestHelper h) {
        var heart = fixture(h, SableLife.HEART.get());
        var sensor = sensor(heart, Direction.NORTH);
        sensor.edit("shape", 0, "");
        sensor.edit("target", 0, "");
        sensor.edit("lineOfSight", 0, "");
        heart.tryActivate(TestActors.creative(h.getLevel()));
        var center = SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter());
        var player = new SlashGuardGameTests.Defender(h.getLevel());
        player.setNoGravity(true);
        player.setPos(center.x, Math.floor(center.y) - .02, center.z - 12);
        h.getLevel().addNewPlayer(player);
        var halfHeightCover = BlockPos.containing(center).north(10);
        try {
            for (int x = -1; x <= 1; x++)
                h.getLevel().setBlockAndUpdate(halfHeightCover.offset(x, 0, 0), Blocks.STONE.defaultBlockState());
            h.assertTrue(!ModuleTargets.visible(h.getLevel(), heart.creatureId(), center.add(0, 0, -.8), player),
                    "The test cover did not block the old center ray");
            var selected = heart.modules().events().sensorAim(heart, sensor.id());
            h.assertTrue(selected != null && selected.id().equals(player.getUUID()), "Sensor missed the player's exposed chest");
            var laser = heart.modules().add("laser");
            laser.anchors(sensor.anchors());
            laser.edit("laserAim", 1, "");
            laser.edit("target", 0, "");
            laser.edit("lineOfSight", 1, "");
            laser.edit("chargeTime", 0, "");
            var runtime = new ModuleLaser();
            runtime.tick(heart);
            h.assertTrue(runtime.aimPoint(laser.id()).distanceTo(ModuleTargets.upperChest(player)) < .0001,
                    "Tracking laser did not aim at the player's upper chest");
            h.assertTrue(player.getHealth() < player.getMaxHealth(), "The exposed chest was not hit");
            runtime.clear();
            laser.edit("enabled", 0, "");
            for (int x = -1; x <= 1; x++)
                h.getLevel().setBlockAndUpdate(halfHeightCover.offset(x, 1, 0), Blocks.STONE.defaultBlockState());
            heart.modules().changed();
            h.assertTrue(heart.modules().events().sensorAim(heart, sensor.id()) == null, "Sensor saw a fully covered player");
            h.succeed();
        } finally { player.discard(); }
    }

    @GameTest(template = "combat", batch = "alignment_inner_radius_settings")
    public static void AlignmentInnerRadiusDefaultsRoundsAndPersists(GameTestHelper h) {
        var alignment = new VirtualModule("navigation");
        alignment.edit("radius", 12, "");
        var oldTag = alignment.save();
        oldTag.getCompound("Settings").remove("innerRadius");
        alignment = VirtualModule.load(oldTag);
        h.assertTrue(alignment.value("innerRadius") == 0 && alignment.value("radius") == 12,
                "Legacy Target Alignment did not keep its original search radius with an empty exclusion zone");
        h.assertTrue(alignment.edit("innerRadius", 2.6, "") && alignment.value("innerRadius") == 3,
                "Target Alignment did not accept the new field in whole blocks");
        alignment.edit("innerRadius", -1, "");
        h.assertTrue(alignment.value("innerRadius") == 0, "The exclusion zone accepted a negative radius");
        alignment.edit("radius", 64, "");
        alignment.edit("innerRadius", 50, "");
        h.assertTrue(alignment.value("innerRadius") == 31, "The exclusion radius exceeded its upper limit");
        alignment.edit("radius", 5, "");
        h.assertTrue(alignment.value("innerRadius") == 4, "Reducing the outer radius did not clamp the inner radius");
        alignment.edit("innerRadius", 31, "");
        alignment = VirtualModule.load(alignment.save());
        h.assertTrue(alignment.value("innerRadius") == 4 && alignment.value("radius") == 5,
                "Saved Target Alignment lost its clamped detection ring");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "alignment_inner_radius_edges")
    public static void AlignmentIncludesBothRadiusEdgesAndRejectsTheHole(GameTestHelper h) {
        var heart = fixture(h, SableLife.HEART.get());
        var alignment = alignment(heart, 0);
        var mark = alignment.anchors().getFirst();
        heart.tryActivate(TestActors.creative(h.getLevel()));
        h.assertTrue(HeartModules.triggered(alignment, heart), "Fixture alignment was not powered");
        var center = mark.world(heart, false);
        var inside = namedCow(h, "Inside hole", center.add(0, 0, 1.25));
        var target = namedCow(h, "On radial edge", center.add(0, 0, 3));
        var outside = namedCow(h, "Beyond exterior edge", center.add(0, 0, 9));
        try {
            var events = heart.modules().events();
            var navigation = events.navigation();
            double innerDistance = SableBodyProbe.toWorld(h.getLevel(), target.getBoundingBox().getCenter())
                    .distanceToSqr(mark.world(heart, false));
            events.tick(heart);
            h.assertTrue(navigation.name(alignment.id()).equals(target.getName().getString()),
                    "Alignment missed the inclusive inner boundary: selected=" + navigation.name(alignment.id())
                            + ", boundaryDistanceSquared=" + innerDistance + ", innerRadius=" + alignment.value("innerRadius"));
            h.assertTrue(navigation.left(alignment.id()) + navigation.right(alignment.id()) > 0,
                    "Inner boundary target published no turn: left=" + navigation.left(alignment.id())
                            + ", right=" + navigation.right(alignment.id()) + ", boundaryDistanceSquared=" + innerDistance);
            placeAtCenter(target, mark.world(heart, false).add(0, 0, 8));
            var nearest = alignment(heart, 1);
            events.tick(heart);
            double outerDistance = SableBodyProbe.toWorld(h.getLevel(), target.getBoundingBox().getCenter())
                    .distanceToSqr(mark.world(heart, false));
            h.assertTrue(navigation.name(alignment.id()).equals(target.getName().getString())
                            && navigation.name(nearest.id()).equals(target.getName().getString()),
                    "Alignment missed the inclusive outer boundary: locked=" + navigation.name(alignment.id())
                            + ", nearest=" + navigation.name(nearest.id()) + ", boundaryDistanceSquared=" + outerDistance);
            placeAtCenter(target, mark.world(heart, false).add(0, 0, 8.25));
            events.tick(heart);
            h.assertTrue(navigation.name(alignment.id()).isEmpty()
                            && navigation.left(alignment.id()) == 0 && navigation.right(alignment.id()) == 0
                            && navigation.name(nearest.id()).isEmpty(),
                    "Alignment retained a target or turn beyond the outer boundary");
            h.succeed();
        } finally {
            inside.discard();
            target.discard();
            outside.discard();
        }
    }

    @GameTest(template = "combat", batch = "alignment_inner_radius_lock", timeoutTicks = 24)
    public static void NearestSwitchesButLockSurvivesUntilItsTargetEntersTheHole(GameTestHelper h) {
        var heart = fixture(h, SableLife.HEART.get());
        var locked = alignment(heart, 0);
        var nearest = alignment(heart, 1);
        var mark = locked.anchors().getFirst();
        var center = mark.world(heart, false);
        var first = namedCow(h, "Original lock", center.add(0, 0, 5));
        var next = namedCow(h, "Closer in ring", center.add(0, 0, -6));
        var events = heart.modules().events();
        var navigation = events.navigation();
        try {
            heart.tryActivate(TestActors.creative(h.getLevel()));
            events.tick(heart);
            h.assertTrue(navigation.name(locked.id()).equals(first.getName().getString())
                            && navigation.name(nearest.id()).equals(first.getName().getString()),
                    "Both selections must begin with the same closest target in the ring");
            placeAtCenter(next, center.add(0, 0, -3.5));
            h.runAfterDelay(7, () -> {
                try {
                    events.tick(heart);
                    h.assertTrue(navigation.name(locked.id()).equals(first.getName().getString())
                                    && navigation.name(nearest.id()).equals(next.getName().getString()),
                            "Nearest did not reselect, or Locked abandoned an eligible target");
                    placeAtCenter(first, mark.world(heart, false).add(0, 0, 1.25));
                    events.tick(heart);
                    h.assertTrue(navigation.name(locked.id()).equals(next.getName().getString())
                                    && navigation.left(locked.id()) > 0 && navigation.right(locked.id()) == 0,
                            "Entering the inner radius did not revoke the lock and switch the turn output");
                    h.succeed();
                } finally {
                    first.discard();
                    next.discard();
                }
            });
        } catch (RuntimeException | AssertionError failure) {
            first.discard();
            next.discard();
            throw failure;
        }
    }

    @GameTest(template = "combat", batch = "alignment_inner_radius_sources")
    public static void AlignmentRejectsSensorInsideTheHoleButKeepsWandOverride(GameTestHelper h) {
        var heart = fixture(h, SableLife.HEART.get());
        var sensor = sensor(heart, Direction.EAST);
        sensor.edit("shape", 0, "");
        sensor.edit("lineOfSight", 1, "");
        var alignment = alignment(heart, 0);
        alignment.edit("facingSource", 1, "");
        alignment.facingSensor(sensor.id());
        var center = alignment.anchors().getFirst().world(heart, false);
        var inside = namedCow(h, "Sensor lock", center.add(0, 0, 2));
        var other = namedCow(h, "Independent candidate", center.add(0, 0, 6));
        try {
            heart.tryActivate(TestActors.creative(h.getLevel()));
            var events = heart.modules().events();
            var navigation = events.navigation();
            var source = events.sensorAim(heart, sensor.id());
            h.assertTrue(source != null && source.id().equals(inside.getUUID()), "Sensor did not select the closer target");
            events.tick(heart);
            h.assertTrue(navigation.name(alignment.id()).isEmpty(),
                    "Sensor-linked alignment passed the inner limit or fell back to an independent target");
            placeAtCenter(inside, center.add(0, 0, 4));
            heart.modules().changed();
            events.tick(heart);
            h.assertTrue(navigation.name(alignment.id()).equals(inside.getName().getString()),
                    "Sensor-linked alignment did not reacquire when the source entered the detection ring");
            placeAtCenter(inside, center.add(0, 0, 2));
            heart.modules().commandTarget().entity(inside, h.getLevel().getGameTime() + 20, CommandTarget.TARGET_ALIGNMENT);
            events.tick(heart);
            h.assertTrue(navigation.name(alignment.id()).equals(inside.getName().getString()),
                    "The local exclusion radius suppressed the explicit Command Wand order");
            heart.modules().commandTarget().clear();
            heart.modules().changed();
            events.tick(heart);
            h.assertTrue(navigation.name(alignment.id()).isEmpty(), "Ending the wand order left the hole target selected");
            h.succeed();
        } finally {
            inside.discard();
            other.discard();
            heart.modules().commandTarget().clear();
        }
    }

    private static VirtualModule alignment(HeartBlockEntity heart, int lockMode) {
        var module = heart.modules().add("navigation");
        module.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), Direction.EAST)));
        module.edit("trigger", 0, "");
        module.edit("target", 1, "");
        module.edit("lineOfSight", 1, "");
        module.edit("radius", 8, "");
        module.edit("innerRadius", 3, "");
        module.edit("targetLock", lockMode, "");
        return module;
    }

    private static Cow namedCow(GameTestHelper h, String name, Vec3 center) {
        var cow = EntityType.COW.create(h.getLevel());
        cow.setCustomName(Component.literal(name));
        cow.setNoAi(true);
        cow.setNoGravity(true);
        placeAtCenter(cow, center);
        h.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static void placeAtCenter(Cow cow, Vec3 center) {
        cow.setPos(center.add(0, -cow.getBbHeight() / 2, 0));
    }

    private static HeartBlockEntity fixture(GameTestHelper h, Block block) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(3, 40, 3));
        level.setBlockAndUpdate(origin, block.defaultBlockState());
        level.setBlockAndUpdate(origin.north(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, origin.north()),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ() - 1, origin.getX(), origin.getY(), origin.getZ()));
        var heart = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        LoadedCoreIndex.observe(heart);
        return heart;
    }
}
