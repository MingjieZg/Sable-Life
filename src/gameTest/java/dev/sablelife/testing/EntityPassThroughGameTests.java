package dev.sablelife.testing;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.EntityPassThroughState;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.module.VirtualModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@GameTestHolder("sablelife_facing_tests")
@PrefixGameTestTemplate(false)
public final class EntityPassThroughGameTests {
    private static final String TYPE = "entity_pass_through";
    private static final String WHOLE_BODY = "passThroughWholeBody";
    private static final String SIGNAL_SOURCE = "pass_through_game_test";

    @GameTest(template = "combat", batch = "entity_pass_through_geometry", timeoutTicks = 40)
    public static void MovementFollowsMarkedAndConnectedScopesAndRecoversOnSignalOff(GameTestHelper h) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("simulated")) {
            h.succeed();
            return;
        }
        var connectedPanels = swivelPanels(h);
        var root = connectedPanels.root();
        var marked = connectedPanels.child();
        var unrelated = panel(h, new BlockPos(14, 70, 4), Blocks.IRON_BLOCK);
        var core = (HeartBlockEntity) h.getLevel().getBlockEntity(root.body().getPlot().getCenterBlock());
        var outsidePin = pinSeparatePanel(h, unrelated);
        pin(core);
        int markedChannel = channel(core);
        int wholeChannel = channel(core);
        module(h, core, marked, false, markedChannel);
        module(h, core, marked, true, wholeChannel);
        core.tryActivate(TestActors.creative(h.getLevel()));
        h.assertTrue(core.operational(), "Ordinary Heart Core could not activate");

        var worldWall = h.absolutePos(new BlockPos(14, 72, 11));
        for (int y = -1; y <= 1; y++) h.getLevel().setBlockAndUpdate(worldWall.above(y), Blocks.STONE.defaultBlockState());
        var inside = pig(h);
        var waiting = pig(h);
        var probe = pig(h);

        h.runAfterDelay(3, () -> {
            var connected = SableBodyProbe.inspect(h.getLevel(), core.getBlockPos()).connectedBodies();
            h.assertTrue(connected.contains(marked.body().getUniqueId()) && !connected.contains(unrelated.body().getUniqueId()),
                    "The marked wall must be connected and the control wall separate");
            checkMovement(h, probe, root, false);
            checkMovement(h, probe, marked, false);
            checkRayAndProjectile(h, probe, marked);
            signal(core, markedChannel);
        });
        h.runAfterDelay(5, () -> {
            checkMovement(h, probe, root, false);
            checkMovement(h, probe, marked, true);
            checkMovement(h, probe, unrelated, false);
            checkRayAndProjectile(h, probe, marked);
            signal(core, wholeChannel);
        });
        h.runAfterDelay(7, () -> {
            checkMovement(h, probe, root, true);
            checkMovement(h, probe, marked, true);
            checkMovement(h, probe, unrelated, false);
            var center = worldWall.getCenter();
            checkMotion(h, probe, center.add(-2, -.4, 0), center.add(2, -.4, 0), false, "stationary world wall");
            checkRayAndProjectile(h, probe, marked);
            inside.setPos(marked.point(0, -.4));
            waiting.setPos(root.point(0, -.4));
            h.assertTrue(EntityPassThroughState.ignores(inside, marked.body()), "Living entity inside the enabled part did not have the pass-through policy");
            signal(core, -1);
        });
        h.runAfterDelay(9, () -> {
            checkMovement(h, probe, root, false);
            checkMovement(h, probe, marked, false);
            h.assertTrue(EntityPassThroughState.ignores(inside, marked.body()), "Disabling while an entity overlaps the former panel did not let it exit safely");
            checkMotion(h, inside, inside.position(), marked.point(2, -.4), true, "exit from the disabled panel");
        });
        h.runAfterDelay(11, () -> {
            checkMovement(h, inside, marked, false);
            inside.discard();
            probe.setPos(root.point(-4, -.4));
        });
        h.runAfterDelay(25, () -> {
            h.assertTrue(EntityPassThroughState.ignores(waiting, root.body())
                            && waiting.position().distanceTo(root.point(0, -.4)) < .25,
                    "The server moved an occupant before the exit window ended");
            h.assertTrue(probe.position().distanceTo(root.point(-4, -.4)) < .25,
                    "The server moved a non-overlapping mob during the exit window");
        });
        h.runAfterDelay(31, () -> {
            double ejection = waiting.position().distanceTo(root.point(0, -.4));
            h.assertTrue(!EntityPassThroughState.ignores(waiting, root.body()) && ejection > .45 && ejection <= 2.25,
                    "A stationary occupant did not leave the disabled wall within the expected short distance: " + ejection);
            h.assertTrue(probe.position().distanceTo(root.point(-4, -.4)) < .25,
                    "Automatic ejection moved a non-overlapping mob");
            waiting.discard();
            probe.discard();
            outsidePin.remove();
            core.modules().clearRuntime();
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "entity_pass_through_survival", timeoutTicks = 30)
    public static void SurvivalCoreEnablesWalkingAndMasterOffRestoresCollision(GameTestHelper h) {
        checkCore(h, SableLife.SURVIVAL_LIFE_CORE.get());
    }

    @GameTest(template = "combat", batch = "entity_pass_through_redstone", timeoutTicks = 30)
    public static void RedstoneCoreEnablesWalkingWithoutLifeActivation(GameTestHelper h) {
        checkCore(h, SableLife.REDSTONE_CORE.get());
    }

    private static void checkCore(GameTestHelper h, Block type) {
        var panel = panel(h, new BlockPos(5, 70, 4), type);
        var core = (HeartBlockEntity) h.getLevel().getBlockEntity(panel.body().getPlot().getCenterBlock());
        pin(core);
        int channel = channel(core);
        module(h, core, panel, false, channel);
        if (core.isSurvivalCore()) {
            var actor = TestActors.creative(h.getLevel());
            core.depositSurvivalTokens(actor, new ItemStack(Items.DIAMOND, 64));
            core.tryActivate(actor);
        }
        h.assertTrue(core.operational(), "The configured core did not become operational");
        var probe = pig(h);
        h.runAfterDelay(3, () -> {
            checkMovement(h, probe, panel, false);
            signal(core, channel);
        });
        h.runAfterDelay(5, () -> {
            checkMovement(h, probe, panel, true);
            var tracking = (EntityMovementExtension) probe;
            tracking.sable$setTrackingSubLevel(panel.body());
            probe.move(MoverType.SELF, Vec3.ZERO);
            h.assertTrue(tracking.sable$getTrackingSubLevel() != panel.body(), "Entity stayed attached to a pass-through part");
            core.wireless().master(false);
        });
        h.runAfterDelay(7, () -> {
            checkMovement(h, probe, panel, false);
            core.wireless().master(true);
            signal(core, channel);
        });
        h.runAfterDelay(9, () -> {
            probe.setPos(panel.point(-2, -.4));
            h.assertTrue(EntityPassThroughState.ignores(probe, panel.body()), "Could not re-arm pass-through after turning the master back on");
            core.onChunkUnloaded();
        });
        h.runAfterDelay(11, () -> {
            h.assertTrue(!EntityPassThroughState.ignores(probe, panel.body()), "Chunk unload left stale pass-through state");
            probe.discard();
            h.succeed();
        });
    }

    private static void checkMovement(GameTestHelper h, Pig entity, Panel wall, boolean pass) {
        var start = wall.point(-2, -.4);
        var end = wall.point(2, -.4);
        entity.setPos(start);
        h.assertTrue(EntityPassThroughState.ignores(entity, wall.body()) == pass,
                "Server policy did not match the expected scope for body " + wall.body().getUniqueId());
        checkMotion(h, entity, start, end, pass, "Sable wall " + wall.body().getUniqueId());
    }

    private static void checkMotion(GameTestHelper h, Pig entity, Vec3 start, Vec3 end, boolean pass, String subject) {
        entity.setPos(start);
        var step = end.subtract(start);
        entity.move(MoverType.SELF, step);
        double fraction = entity.position().subtract(start).dot(step) / step.lengthSqr();
        h.assertTrue(pass ? fraction > .9 : fraction < .53,
                "Wrong movement outcome for " + subject + ", pass expected " + pass + ", distance fraction " + fraction);
    }

    private static void checkRayAndProjectile(GameTestHelper h, Pig observer, Panel wall) {
        var start = wall.point(-2, 0);
        var end = wall.point(2, 0);
        var hit = h.getLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, observer));
        h.assertTrue(hit.getType() == HitResult.Type.BLOCK && Sable.HELPER.getContaining(h.getLevel(), hit.getBlockPos()) == wall.body(),
                "Living-entity block ray stopped hitting the Sable wall");

        var arrow = new Arrow(h.getLevel(), start.x, start.y, start.z, new ItemStack(Items.ARROW), null);
        arrow.setDeltaMovement(end.subtract(start));
        var projectileHit = ProjectileUtil.getHitResultOnMoveVector(arrow, ignored -> false);
        h.assertTrue(!EntityPassThroughState.ignores(arrow, wall.body()) && projectileHit instanceof BlockHitResult blockHit
                        && blockHit.getType() == HitResult.Type.BLOCK && Sable.HELPER.getContaining(h.getLevel(), blockHit.getBlockPos()) == wall.body(),
                "An ordinary projectile no longer hit the Sable wall");
    }

    private static Pig pig(GameTestHelper h) {
        var pig = EntityType.PIG.create(h.getLevel());
        pig.setPos(h.absolutePos(new BlockPos(2, 72, 2)).getCenter());
        pig.setNoGravity(true);
        pig.setNoAi(true);
        h.assertTrue(h.getLevel().addFreshEntity(pig), "Could not add the test mob");
        return pig;
    }

    private static VirtualModule module(GameTestHelper h, HeartBlockEntity core, Panel marked, boolean all, int channel) {
        var module = core.modules().add(TYPE);
        h.assertTrue(module != null, "The pass-through module is not available on this core");
        h.assertTrue(module.value(WHOLE_BODY) == 0 && module.value("trigger") == 0,
                "New modules should default to the marked part and continuous activation");
        if (all) {
            h.assertTrue(module.edit(WHOLE_BODY, 1, ""), "Whole-body scope could not be selected without marking");
        } else {
            module.anchors(List.of(ModuleAnchor.create(core, marked.solid(), Direction.WEST)));
        }
        h.assertTrue(module.edit("trigger", 2, "") && module.edit("channel", channel + 1, ""),
                "Could not configure the internal wireless trigger");
        return module;
    }

    private static void pin(HeartBlockEntity core) {
        var anchor = core.modules().add("hover_anchor");
        anchor.anchors(List.of(ModuleAnchor.create(core, core.getBlockPos(), Direction.DOWN)));
        anchor.edit("hoverRotation", 1, "");
    }

    private static int channel(HeartBlockEntity core) {
        int channel = core.wireless().add();
        core.wireless().channel(channel).set(0, 1);
        core.wireless().channel(channel).set(6, 3);
        return channel;
    }

    private static void signal(HeartBlockEntity core, int channel) {
        core.wireless().programOutputs(SIGNAL_SOURCE, channel < 0 ? Map.of() : Map.of(channel, 15));
    }

    private static Panel panel(GameTestHelper h, BlockPos local, Block central) {
        var center = h.absolutePos(local);
        var points = buildWall(h, center, central);
        var body = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), center, points,
                new BoundingBox3i(center.getX(), center.getY(), center.getZ() - 1, center.getX(), center.getY() + 2, center.getZ() + 1));
        return wallPanel(h, body);
    }

    private static ConnectedPanels swivelPanels(GameTestHelper h) throws ReflectiveOperationException {
        var center = h.absolutePos(new BlockPos(5, 70, 4));
        var points = buildWall(h, center, SableLife.HEART.get());
        var bearing = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("simulated", "swivel_bearing"));
        for (int z = 2; z < 6; z++) {
            var beam = center.south(z);
            h.getLevel().setBlockAndUpdate(beam, Blocks.IRON_BLOCK.defaultBlockState());
            points.add(beam);
        }
        var swivel = center.south(6);
        h.getLevel().setBlockAndUpdate(swivel, bearing.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        points.add(swivel);
        h.getLevel().setBlockAndUpdate(swivel.above(), Blocks.IRON_BLOCK.defaultBlockState());
        points.add(swivel.above());
        var root = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), center, points,
                new BoundingBox3i(center.getX(), center.getY(), center.getZ() - 1, center.getX(), center.getY() + 2, swivel.getZ()));
        var controller = h.getLevel().getBlockEntity(root.getPlot().getCenterBlock().south(6));
        h.assertTrue(controller instanceof BlockEntitySubLevelActor, "The fixture did not assemble its native swivel controller");
        controller.getClass().getMethod("assemble").invoke(controller);
        var dependencies = ((BlockEntitySubLevelActor) controller).sable$getConnectionDependencies().iterator();
        h.assertTrue(dependencies.hasNext(), "The fixture's native swivel did not assemble its child");
        var child = (ServerSubLevel) dependencies.next();
        child.getPlot().updateBoundingBox();
        var bounds = child.getPlot().getBoundingBox();
        h.assertTrue(bounds != null, "The fixture's child has no blocks");
        var tip = new BlockPos(bounds.minX(), bounds.maxY(), bounds.minZ());
        h.assertTrue(h.getLevel().getBlockState(tip).is(Blocks.IRON_BLOCK),
                "Native swivel did not move its attached iron block into the child plot");
        buildWall(h, tip, Blocks.IRON_BLOCK);
        SableBodyProbe.invalidate(h.getLevel());
        return new ConnectedPanels(wallPanel(h, root), wallPanel(h, child, tip.above()));
    }

    private static ArrayList<BlockPos> buildWall(GameTestHelper h, BlockPos center, Block central) {
        var points = new ArrayList<BlockPos>();
        for (var point : BlockPos.betweenClosed(center.north(), center.south().above(2))) {
            points.add(point.immutable());
            h.getLevel().setBlockAndUpdate(point, (point.equals(center) ? central : Blocks.IRON_BLOCK).defaultBlockState());
        }
        return points;
    }

    private static Panel wallPanel(GameTestHelper h, ServerSubLevel body) {
        return wallPanel(h, body, body.getPlot().getCenterBlock().above());
    }

    private static Panel wallPanel(GameTestHelper h, ServerSubLevel body, BlockPos solid) {
        h.assertTrue(h.getLevel().getBlockState(solid).is(Blocks.IRON_BLOCK),
                "Fixture did not assemble a solid face at " + solid + " for body " + body.getUniqueId());
        return new Panel(body, solid);
    }

    private static dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle pinSeparatePanel(GameTestHelper h, Panel panel) {
        Vec3 center = panel.solid().getCenter();
        Vec3 world = panel.body().logicalPose().transformPosition(center);
        var physics = SubLevelPhysicsSystem.get(h.getLevel()).getPipeline();
        return physics.addConstraint(null, panel.body(), new GenericConstraintConfiguration(
                new Vector3d(world.x, world.y, world.z), new Vector3d(center.x, center.y, center.z),
                new Quaterniond(), new Quaterniond(), EnumSet.allOf(ConstraintJointAxis.class)));
    }

    private record Panel(ServerSubLevel body, BlockPos solid) {
        Vec3 point(double x, double y) { return body.logicalPose().transformPosition(solid.getCenter().add(x, y, 0)); }
    }
    private record ConnectedPanels(Panel root, Panel child) {}
}
