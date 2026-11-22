package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.attack.AttackHitCredit;
import dev.sablelife.attack.ModuleShot;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.CommandTarget;
import dev.sablelife.module.HeartDashboardMenu;
import dev.sablelife.module.HeartModules;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.module.ModuleCombat;
import dev.sablelife.module.ModuleLaser;
import dev.sablelife.module.ModuleMoveSet;
import dev.sablelife.module.ModuleNetworking;
import dev.sablelife.module.ModuleTargets;
import dev.sablelife.module.ProjectileAim;
import dev.sablelife.module.VirtualModule;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sablelife_control_tests")
@PrefixGameTestTemplate(false)
public final class MoveSetTargetGameTests {
    private static final String INHERIT = "inheritMoveTarget";

    @GameTest(template = "combat", batch = "moveset_aim_single")
    public static void DirectGunActionInheritsFartherTargetInMoveCone(GameTestHelper h) {
        var setup = setup(h);
        activate(h, setup);

        h.assertTrue(setup.moveTarget().getUUID().equals(setup.heart().modules().moveTarget(setup.gun().id())),
                "The Move Set did not retain the target that qualified within its cone");
        var shots = shots(h, setup);
        h.assertTrue(shots.size() == 1 && shots.getFirst().getDeltaMovement().x > 0,
                "The direct Gun action did not shoot the Move Set target");
        finish(h, setup);
    }

    @GameTest(template = "combat", batch = "moveset_aim_off")
    public static void SwitchOffRestoresIndependentGunAim(GameTestHelper h) {
        var setup = setup(h);
        h.assertTrue(dashboardEdit(h, setup, 0), "The dashboard rejected turning target inheritance off");
        activate(h, setup);

        h.assertTrue(setup.heart().modules().moveTarget(setup.gun().id()) == null,
                "The Move Set passed a target with inheritance off");
        var shots = shots(h, setup);
        h.assertTrue(shots.size() == 1 && shots.getFirst().getDeltaMovement().x < 0,
                "The Gun did not resume aiming independently at the closer target");
        finish(h, setup);
    }

    @GameTest(template = "combat", batch = "moveset_aim_multi")
    public static void MultiTargetGunStartsWithMoveTargetAndKeepsOtherTargets(GameTestHelper h) {
        var setup = setup(h);
        setup.gun().edit("targetMultiple", 1, "");
        activate(h, setup);

        var directions = ProjectileAim.directions(setup.heart(), setup.gun(),
                ModuleCombat.nozzle(setup.heart(), setup.gun()));
        h.assertTrue(directions.size() == 2 && directions.get(0).x > 0 && directions.get(1).x < 0,
                "Multi-target aim did not prioritize the Move Set target before the closer target");
        var shots = shots(h, setup);
        h.assertTrue(shots.size() == 2 && shots.stream().anyMatch(s -> s.getDeltaMovement().x > 0)
                        && shots.stream().anyMatch(s -> s.getDeltaMovement().x < 0),
                "The direct Gun action did not fire at both targets");
        finish(h, setup);
    }

    @GameTest(template = "combat", batch = "command_wand_weapon_priority")
    public static void WeaponOrderOverridesMoveSetAndKeepsOtherTargets(GameTestHelper h) {
        var setup = setup(h);
        setup.gun().edit("targetMultiple", 1, "");
        var origin = ModuleCombat.nozzle(setup.heart(), setup.gun());
        var wandTarget = cow(h, origin.add(0, 0, 5));
        var level = h.getLevel();
        setup.heart().modules().commandTarget().entity(wandTarget, level.getGameTime() + 40, CommandTarget.WEAPON_TARGETING);
        activate(h, setup);

        h.assertTrue(setup.moveTarget().getUUID().equals(setup.heart().modules().moveTarget(setup.gun().id())),
                "The wand changed which target the Move Set selected");
        var directions = ProjectileAim.directions(setup.heart(), setup.gun(), origin);
        h.assertTrue(directions.size() == 3 && directions.getFirst().z > .95
                        && directions.get(1).x < -.95 && directions.get(2).x > .95,
                "The wand target was not first with the other eligible targets filled normally");
        var shots = shots(h, setup);
        h.assertTrue(shots.size() == 3 && shots.stream().anyMatch(s -> s.getDeltaMovement().z > .1)
                        && shots.stream().anyMatch(s -> s.getDeltaMovement().x > .1)
                        && shots.stream().anyMatch(s -> s.getDeltaMovement().x < -.1),
                "The actual Gun cast did not fire at the wand target and both supplemental targets");
        wandTarget.discard();
        finish(h, setup);
    }

    @GameTest(template = "combat", batch = "command_wand_weapon_fallback", timeoutTicks = 40)
    public static void WeaponOrderRespectsOwnFiltersAndFallsBackOnExpiryOrDeath(GameTestHelper h) {
        var setup = setup(h);
        setup.gun().edit("targetMultiple", 1, "");
        setup.gun().edit("lineOfSight", 1, "");
        edit(h, setup, "action_start", .25, "0|0");
        var origin = ModuleCombat.nozzle(setup.heart(), setup.gun());
        var target = cow(h, origin.add(0, 0, 6));
        var level = h.getLevel();
        var obstacle = BlockPos.containing(origin.add(0, 0, 3));
        var wall = BlockPos.betweenClosed(obstacle.offset(-2, -5, 0), obstacle.offset(2, 4, 0));
        for (var block : wall) level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
        var command = setup.heart().modules().commandTarget();
        command.entity(target, level.getGameTime() + 10, CommandTarget.WEAPON_TARGETING);
        activate(h, setup);
        h.assertTrue(!HeartModules.triggered(setup.gun(), setup.heart()) && shots(h, setup).isEmpty(),
                "The wand started a Gun whose Move Set action was still waiting");
        h.assertTrue(ProjectileAim.directions(setup.heart(), setup.gun(), origin).isEmpty(),
                "The wand bypassed the Gun's enabled line of sight");

        h.runAfterDelay(7, () -> {
            var muzzle = ModuleCombat.nozzle(setup.heart(), setup.gun());
            h.assertTrue(HeartModules.triggered(setup.gun(), setup.heart()), "Delayed Gun action never became active");
            h.assertTrue(target.getUUID().equals(command.weaponTarget(level)), "Wand order ended before the Gun action");
            h.assertTrue(!ModuleTargets.visible(level, setup.heart().creatureId(), muzzle, target),
                    "The wall stopped blocking the Gun's current muzzle");
            h.assertTrue(shots(h, setup).isEmpty() && target.getHealth() == target.getMaxHealth()
                            && setup.moveTarget().getHealth() == setup.moveTarget().getMaxHealth()
                            && setup.nearerTarget().getHealth() == setup.nearerTarget().getMaxHealth(),
                    "A blocked wand target caused a shot");
            for (var block : wall) level.removeBlock(block, false);
            setup.gun().edit("targetRange", 4, "");
            h.assertTrue(ProjectileAim.directions(setup.heart(), setup.gun(), origin).isEmpty(),
                    "The wand bypassed the Gun's range");
            setup.gun().edit("targetRange", 12, "");
            setup.gun().edit("target", 0, "");
            h.assertTrue(ProjectileAim.directions(setup.heart(), setup.gun(), origin).isEmpty(),
                    "The wand bypassed the Gun's target type");
            setup.gun().edit("target", 1, "");
        });
        h.runAfterDelay(12, () -> {
            assertGunFirstDirection(h, setup, origin, 1,
                    "The Move Set target was not restored when the wand order expired");
            command.entity(target, level.getGameTime() + 40, CommandTarget.WEAPON_TARGETING);
            target.setHealth(0);
            assertGunFirstDirection(h, setup, origin, 1,
                    "The Move Set target was not restored immediately when the wand target died");
            finish(h, setup);
        });
    }

    @GameTest(template = "combat", batch = "command_wand_weapon_independent")
    public static void DestinationAndDisabledWeaponOrdersLeaveIndependentAim(GameTestHelper h) {
        var setup = setup(h);
        setup.table().edit("ignoreConditions", 1, "");
        var origin = ModuleCombat.nozzle(setup.heart(), setup.gun());
        var wandTarget = cow(h, origin.add(0, 0, 5));
        var command = setup.heart().modules().commandTarget();
        long expiry = h.getLevel().getGameTime() + 40;
        command.destination(BlockPos.containing(origin.add(0, 0, 5)), expiry, CommandTarget.WEAPON_TARGETING);
        activate(h, setup);
        var shots = shots(h, setup);
        h.assertTrue(shots.size() == 1 && shots.getFirst().getDeltaMovement().x < 0,
                "A destination order aimed the Gun at a location or blocked its normal cast");
        command.entity(wandTarget, expiry, CommandTarget.TARGET_ALIGNMENT);
        assertGunFirstDirection(h, setup, origin, -1, "A wand order with the weapon option off aimed the Gun");
        command.entity(wandTarget, expiry, CommandTarget.WEAPON_TARGETING);
        var directions = ProjectileAim.directions(setup.heart(), setup.gun(), origin);
        h.assertTrue(directions.size() == 1 && directions.getFirst().z > .95,
                "Ignore Conditions prevented the independent wand order from aiming the Gun");
        setup.gun().edit("shotAim", 0, "");
        h.assertTrue(ProjectileAim.directions(setup.heart(), setup.gun(), origin)
                        .equals(List.of(ModuleCombat.direction(setup.heart(), setup.gun()))),
                "The wand changed a forward-only weapon into an aimed weapon");
        wandTarget.discard();
        finish(h, setup);
    }

    @GameTest(template = "combat", batch = "command_wand_weapon_homing")
    public static void IndependentlyTriggeredHomingSalvoLocksTheWandTarget(GameTestHelper h) {
        var setup = setup(h);
        var heart = setup.heart();
        setup.table().edit("enabled", 0, "");
        setup.gun().edit("enabled", 0, "");
        var homing = heart.modules().add("homing");
        homing.anchors(setup.gun().anchors());
        homing.edit("trigger", 0, "");
        homing.edit("target", 1, "");
        homing.edit("targetRange", 12, "");
        homing.edit("homingCount", 1, "");
        homing.edit("homingDeploy", 0, "");
        homing.edit("chargeTime", 0, "");

        var origin = ModuleCombat.origin(heart, homing, true);
        var target = cow(h, origin.add(0, 0, 6));
        var command = heart.modules().commandTarget();
        var level = h.getLevel();
        command.entity(target, level.getGameTime() + 40, CommandTarget.WEAPON_TARGETING);
        activate(h, setup);
        var salvo = level.getEntitiesOfClass(ModuleShot.class, new AABB(origin, origin).inflate(24),
                shot -> homing.id().equals(shot.homingModule()));
        h.assertTrue(salvo.size() == 1, "The continuously triggered Homing module did not deploy its salvo");
        var shot = salvo.getFirst();
        shot.tick();
        h.assertTrue(target.getUUID().equals(shot.homingTarget()),
                "The Homing salvo did not lock the wand's target ahead of nearer mobs");
        command.entity(setup.nearerTarget(), level.getGameTime() + 40, CommandTarget.WEAPON_TARGETING);
        shot.tick();
        h.assertTrue(target.getUUID().equals(shot.homingTarget()),
                "A new wand order redirected a launched Homing projectile");
        homing.edit("enabled", 0, "");
        shot.discard();
        target.discard();
        finish(h, setup);
    }

    private static void assertGunFirstDirection(GameTestHelper h, Setup setup, Vec3 origin, int xSign, String message) {
        var directions = ProjectileAim.directions(setup.heart(), setup.gun(), origin);
        h.assertTrue(!directions.isEmpty() && directions.getFirst().x * xSign > .95, message);
    }

    @GameTest(template = "combat", batch = "moveset_aim_invalid", timeoutTicks = 30)
    public static void DelayedMultiTargetGunDoesNotFireWhenMoveTargetDisappears(GameTestHelper h) {
        var setup = setup(h);
        setup.gun().edit("targetMultiple", 1, "");
        edit(h, setup, "action_start", .15, "0|0");
        activate(h, setup);
        h.assertTrue(!HeartModules.triggered(setup.gun(), setup.heart()) && shots(h, setup).isEmpty(),
                "The Gun fired before its action window");
        setup.moveTarget().discard();

        h.runAfterDelay(5, () -> {
            h.assertTrue(HeartModules.triggered(setup.gun(), setup.heart()),
                    "Losing the inherited target unexpectedly cancelled the action window");
            h.assertTrue(setup.moveTarget().getUUID().equals(setup.heart().modules().moveTarget(setup.gun().id())),
                    "The inherited target changed after the move started");
            h.assertTrue(setup.nearerTarget().isAlive() && shots(h, setup).isEmpty(),
                    "A multi-target Gun fired at the remaining mob after losing its mandatory target");
            finish(h, setup);
        });
    }

    @GameTest(template = "combat", batch = "moveset_laser_charge_target", timeoutTicks = 30)
    public static void MultiTargetLaserCancelsWhenMoveTargetDiesDuringCharge(GameTestHelper h) {
        var setup = setup(h);
        var heart = setup.heart();
        var laser = heart.modules().add("laser");
        laser.anchors(setup.gun().anchors());
        laser.edit("laserAim", 1, "");
        laser.edit("target", 1, "");
        laser.edit("targetMultiple", 1, "");
        laser.edit("beamLength", 12, "");
        laser.edit("damage", 5, "");
        laser.edit("chargeTime", 8, "");
        laser.edit("hitInterval", 2, "");
        laser.edit("beamHits", 2, "");
        setup.gun().edit("enabled", 0, "");
        edit(h, setup, "action_delete", 0, "0|0");
        edit(h, setup, "action_add", 0, "0|" + laser.id());
        activate(h, setup);

        h.assertTrue(laserPhase(heart).equals("charging"), "The Laser did not begin charging");
        var targets = ModuleLaser.acquireTargets(h.getLevel(), heart, laser, ModuleCombat.nozzle(heart, laser));
        h.assertTrue(targets.equals(List.of(setup.moveTarget(), setup.nearerTarget())),
                "The Laser charge did not acquire the inherited target and the secondary target");

        h.runAfterDelay(3, () -> setup.moveTarget().setHealth(0));
        h.runAfterDelay(10, () -> {
            h.assertTrue(HeartModules.triggered(laser, heart), "The action ended before the Laser could emit");
            h.assertTrue(laserPhase(heart).equals("waiting"), "The Laser emitted after the inherited target died");
            h.assertTrue(setup.nearerTarget().getHealth() == setup.nearerTarget().getMaxHealth(),
                    "The secondary target took damage after the inherited target died");
            laser.edit("enabled", 0, "");
            finish(h, setup);
        });
    }

    @GameTest(template = "combat", batch = "moveset_aim_ignore")
    public static void IgnoreConditionsLeavesTheGunAimingIndependently(GameTestHelper h) {
        var setup = setup(h);
        setup.moveTarget().discard();
        setup.table().edit("ignoreConditions", 1, "");
        h.assertTrue(ModuleMoveSet.ruleLocked(setup.table(), INHERIT, false),
                "Ignore Conditions left target inheritance editable");
        h.assertTrue(!dashboardEdit(h, setup, 0), "The server accepted a change to a locked condition");
        activate(h, setup);

        h.assertTrue(setup.heart().modules().moveTarget(setup.gun().id()) == null,
                "Ignore Conditions unexpectedly supplied a target");
        var shots = shots(h, setup);
        h.assertTrue(shots.size() == 1 && shots.getFirst().getDeltaMovement().x < 0,
                "The Gun did not acquire the closer mob independently");
        finish(h, setup);
    }

    @GameTest(template = "combat", batch = "moveset_aim_save")
    public static void NewLegacyAndExplicitTargetInheritanceSurviveSaving(GameTestHelper h) {
        var setup = setup(h);
        var saved = setup.table().save();
        h.assertTrue(inheritance(VirtualModule.load(saved)) == 1, "New moves did not default to inheriting");
        saved.getList("Moves", 10).getCompound(0).getCompound("Config").getCompound("Settings").remove(INHERIT);
        var legacy = VirtualModule.load(saved);
        h.assertTrue(inheritance(legacy) == 0 && inheritance(VirtualModule.load(legacy.save())) == 0,
                "The original target behavior of old moves was lost");

        h.assertTrue(dashboardEdit(h, setup, 0), "Could not set Off through the Move Set packet");
        h.assertTrue(inheritance(VirtualModule.load(setup.table().save())) == 0, "Explicit Off was lost");
        h.assertTrue(dashboardEdit(h, setup, 1), "Could not set On through the Move Set packet");
        h.assertTrue(inheritance(VirtualModule.load(setup.table().save())) == 1, "Explicit On was lost");
        finish(h, setup);
    }

    @GameTest(template = "combat", batch = "moveset_sight_memory", timeoutTicks = 45)
    public static void FollowingMoveUsesRememberedAimWithoutFiringThroughCover(GameTestHelper h) {
        var setup = setup(h);
        setup.gun().edit("lineOfSight", 1, "");
        setup.gun().edit("interval", 1, "");
        edit(h, setup, "lineOfSight", 1, "0");
        edit(h, setup, "sightMemory", 12, "0");
        edit(h, setup, "dispatchCooldown", 0, "0");
        edit(h, setup, "dispatchBusy", 2, "0");
        edit(h, setup, "dispatchRest", 1, "0");
        edit(h, setup, "dispatchRepeat", 1, "0");
        edit(h, setup, "action_duration", .1, "0|0");
        activate(h, setup);
        var origin = ModuleCombat.nozzle(setup.heart(), setup.gun());
        var initial = setup.heart().modules().moveAim(setup.gun().id());
        h.assertTrue(initial != null && initial.visible(), "Move did not inherit a visible aim");
        long first = setup.heart().modules().moveActivation(setup.gun().id());
        shots(h, setup).forEach(Entity::discard);
        var wall = BlockPos.containing(origin).east(4);
        h.runAfterDelay(1, () -> {
            for (var pos : BlockPos.betweenClosed(wall.offset(0, -2, -4), wall.offset(0, 2, 4)))
                h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            setup.moveTarget().setPos(setup.moveTarget().position().add(0, 0, 1));
        });
        h.runAfterDelay(9, () -> {
            var next = setup.heart().modules().moveAim(setup.gun().id());
            h.assertTrue(setup.heart().modules().moveActivation(setup.gun().id()) > first, "The next move did not run");
            h.assertTrue(next != null && !next.visible() && next.point().equals(initial.point()),
                    "The next move did not inherit the last visible point");
            h.assertTrue(shots(h, setup).isEmpty(), "The Gun fired at the remembered target through cover");
        });
        h.runAfterDelay(17, () -> {
            h.assertTrue(setup.heart().modules().moveAim(setup.gun().id()) == null, "A following move extended the timer");
            finish(h, setup);
        });
    }

    private static Setup setup(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 16, 8));
        level.setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(pos.south(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos, pos.south()),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ() + 1));
        var heart = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        var anchor = ModuleAnchor.create(heart, heart.getBlockPos(), Direction.UP);
        var gun = heart.modules().add("gun");
        gun.anchors(List.of(anchor));
        gun.edit("shotAim", 1, "");
        gun.edit("target", 1, "");
        gun.edit("targetRange", 12, "");
        gun.edit("interval", 200, "");
        var table = heart.modules().add("move_set");
        var origin = ModuleCombat.nozzle(heart, gun);
        var setup = new Setup(heart, gun, table, cow(h, origin.add(7, 0, 0)), cow(h, origin.add(-3, 0, 0)));
        edit(h, setup, "add", 0, "");
        edit(h, setup, "action_add", 0, "0|" + gun.id());
        edit(h, setup, "dispatchFront", 3, "0");
        edit(h, setup, "dispatchCone", 60, "0");
        edit(h, setup, "target", 1, "0");
        return setup;
    }

    private static LivingEntity cow(GameTestHelper h, Vec3 center) {
        var cow = EntityType.COW.create(h.getLevel());
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setPos(center.add(0, -cow.getBbHeight() / 2, 0));
        h.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static void activate(GameTestHelper h, Setup setup) {
        setup.heart().tryActivate(TestActors.creative(h.getLevel()));
        setup.heart().modules().tick(setup.heart());
    }

    private static void edit(GameTestHelper h, Setup setup, String key, double value, String text) {
        h.assertTrue(setup.table().moveSet().edit(key, value, text, setup.heart().modules()), "Move edit rejected: " + key);
    }

    private static boolean dashboardEdit(GameTestHelper h, Setup setup, double value) {
        var player = TestActors.creative(h.getLevel());
        player.setPos(SableBodyProbe.toWorld(h.getLevel(), setup.heart().getBlockPos().getCenter()).add(0, 1, 0));
        var menu = new HeartDashboardMenu(1, player.getInventory(), setup.heart());
        var request = new ModuleNetworking.Action(1, setup.heart().modules().revision(), setup.table().id(),
                "move_set", INHERIT, value, "0");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ModuleNetworking.Action.CODEC.encode(buffer, request);
            var received = ModuleNetworking.Action.CODEC.decode(buffer);
            h.assertTrue(received.equals(request), "The Move Set packet did not preserve the new key");
            return menu.action(player, received.revision(), received.module(), received.operation(), received.key(),
                    received.value(), received.text());
        } finally {
            buffer.release();
        }
    }

    private static double inheritance(VirtualModule table) {
        return table.moveSet().get(0).config().value(INHERIT);
    }

    private static String laserPhase(HeartBlockEntity heart) {
        var modules = heart.modules().snapshot(heart, false).getList("Modules", 10);
        return modules.getCompound(modules.size() - 1).getString("LaserPhase");
    }

    private static List<ModuleShot> shots(GameTestHelper h, Setup setup) {
        var origin = ModuleCombat.nozzle(setup.heart(), setup.gun());
        return h.getLevel().getEntitiesOfClass(ModuleShot.class, new AABB(origin, origin).inflate(24),
                shot -> shot.getPersistentData().hasUUID(AttackHitCredit.MODULE)
                        && shot.getPersistentData().getUUID(AttackHitCredit.MODULE).equals(setup.gun().id()));
    }

    private static void finish(GameTestHelper h, Setup setup) {
        setup.gun().edit("enabled", 0, "");
        shots(h, setup).forEach(Entity::discard);
        setup.moveTarget().discard();
        setup.nearerTarget().discard();
        h.succeed();
    }

    private record Setup(HeartBlockEntity heart, VirtualModule gun, VirtualModule table, LivingEntity moveTarget,
                         LivingEntity nearerTarget) {}
}
