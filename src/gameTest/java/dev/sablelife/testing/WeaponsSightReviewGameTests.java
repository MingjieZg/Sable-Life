package dev.sablelife.testing;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.sablelife.SableLife;
import dev.sablelife.attack.ModuleShot;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.HeartDashboardMenu;
import dev.sablelife.module.HeartModules;
import dev.sablelife.module.MarkingSessions;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.module.ModuleCombat;
import dev.sablelife.module.ModuleHoming;
import dev.sablelife.module.ModuleLaser;
import dev.sablelife.module.ModuleSummonedWeapon;
import dev.sablelife.module.ModuleTargets;
import dev.sablelife.module.MoveSet;
import dev.sablelife.module.ProjectileAim;
import dev.sablelife.module.TargetCastOrigin;
import dev.sablelife.module.VirtualModule;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@GameTestHolder("sablelife_weapon_sight_tests")
@PrefixGameTestTemplate(false)
public final class WeaponsSightReviewGameTests {
    @GameTest(template = "combat", batch = "weapon_sight_homing")
    public static void BlockedPreferredHomingTargetDoesNotFallBack(GameTestHelper h) {
        var heart = core(h);
        var homing = module(heart, "homing");
        homing.edit("targetMultiple", 1, "");
        homing.edit("targetRange", 24, "");
        homing.edit("homingDeploy", 0, "");
        homing.edit("chargeTime", 0, "");
        heart.tryActivate(TestActors.creative(h.getLevel()));

        var world = h.getLevel();
        var origin = ModuleCombat.origin(heart, homing, true);
        var primary = cow(h, origin.add(12, 0, 0));
        var secondary = cow(h, origin.add(0, 0, 12));
        var hover = origin.add(0, 0, 8);
        var obstruction = BlockPos.containing(origin.add(6, 0, 4));
        for (var pos : BlockPos.betweenClosed(obstruction.offset(-1, -1, -1), obstruction.offset(1, 1, 1)))
            world.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        h.assertTrue(ModuleTargets.visible(world, heart.creatureId(), origin, primary), "Primary must be visible from deploy origin");
        h.assertTrue(!ModuleTargets.visible(world, heart.creatureId(), hover, primary), "Primary must be blocked from hover point");
        h.assertTrue(ModuleTargets.visible(world, heart.creatureId(), hover, secondary), "Secondary must be visible from hover point");

        var preferred = ModuleShot.homing(world, hover, origin, homing, heart, 0, primary.getUUID());
        preferred.tick();
        h.assertTrue(preferred.isRemoved() && preferred.homingTarget() == null,
                "Homing fired despite an obstruction between the missile and its preferred target");

        homing.edit("targetMultiple", 0, "");
        primary.setPos(primary.position().add(-1, 0, 0));
        h.assertTrue(!ModuleTargets.visible(world, heart.creatureId(), hover, primary), "Nearest target must remain blocked from hover");
        var independent = ModuleShot.homing(world, hover, origin, homing, heart, 0);
        h.assertTrue(ModuleTargets.visible(world, heart.creatureId(), origin, secondary), "Both targets must be eligible at deploy");
        independent.tick();
        h.assertTrue(secondary.getUUID().equals(independent.homingTarget()), "Single-target launch omitted a visible alternative");
        independent.discard();
        primary.discard();
        secondary.discard();
        heart.forceDeath();
        h.succeed();
    }

    @GameTest(template = "combat", batch = "weapon_sight_preview")
    public static void PulsePreviewDoesNotAffectTheNextRealCast(GameTestHelper h) {
        var heart = core(h);
        var pulse = module(heart, "pulse");
        pulse.edit("releasePosition", 1, "");
        pulse.edit("sightMemory", 60, "");
        pulse.edit("damage", 3, "");
        heart.tryActivate(TestActors.creative(h.getLevel()));

        var world = h.getLevel();
        var origin = TargetCastOrigin.searchOrigin(heart, pulse);
        var shown = cow(h, origin.add(-3, 0, 0));
        var alternative = cow(h, origin.add(7, 0, 0));
        h.assertTrue(TargetCastOrigin.points(heart, pulse).equals(List.of(shown.getBoundingBox().getCenter())),
                "Fixture did not preview the nearer target");

        wall(h, BlockPos.containing(origin).west(2), 2, 2);
        h.assertTrue(!ModuleTargets.visible(world, heart.creatureId(), origin, shown), "Previewed target must be obscured");
        h.assertTrue(ModuleTargets.visible(world, heart.creatureId(), origin, alternative), "Alternative must be visible");
        heart.modules().tick(heart);
        h.assertTrue(alternative.getHealth() == alternative.getMaxHealth() - 3,
                "An earlier preview prevented the first real cast from using the visible target");
        h.assertTrue(shown.getHealth() == shown.getMaxHealth(), "Pulse struck through the preview target's wall");
        shown.discard();
        alternative.discard();
        heart.forceDeath();
        h.succeed();
    }

    @GameTest(template = "combat", batch = "weapon_sight_laser", timeoutTicks = 30)
    public static void OccludedPreferredLaserTargetStopsTheWholeBurst(GameTestHelper h) {
        var heart = core(h);
        var laser = module(heart, "laser");
        laser.edit("laserAim", 1, "");
        laser.edit("targetMultiple", 1, "");
        laser.edit("beamLength", 12, "");
        laser.edit("damage", 5, "");
        laser.edit("chargeTime", 8, "");
        laser.edit("hitInterval", 2, "");
        laser.edit("beamHits", 2, "");
        moves(h, heart, laser, 40);

        var origin = ModuleCombat.nozzle(heart, laser);
        var primary = cow(h, origin.add(7, 0, 0));
        var secondary = cow(h, origin.add(-3, 0, 0));
        heart.tryActivate(TestActors.creative(h.getLevel()));
        heart.modules().tick(heart);
        pin(h, heart, 12);

        h.assertTrue(primary.getUUID().equals(heart.modules().moveTarget(laser.id())), "Move failed to select the intended primary");
        var state = heart.modules().snapshot(heart, false).getList("Modules", 10);
        h.assertTrue(state.getCompound(0).getString("LaserPhase").equals("charging"), "Laser did not begin charging");
        h.assertTrue(ModuleLaser.acquireTargets(h.getLevel(), heart, laser, origin).size() == 2, "Fixture missed a secondary target");

        h.runAfterDelay(1, () -> wall(h, BlockPos.containing(origin).east(4), 10, 7));
        h.runAfterDelay(7, () -> {
            var shooter = ModuleCombat.nozzle(heart, laser);
            h.assertTrue(!ModuleTargets.visible(h.getLevel(), heart.creatureId(), shooter, primary)
                            && ModuleTargets.visible(h.getLevel(), heart.creatureId(), shooter, secondary)
                            && primary.getUUID().equals(heart.modules().moveTarget(laser.id()))
                            && secondary.getHealth() == secondary.getMaxHealth(),
                    "Fixture failed to block only the primary before release");
        });
        h.runAfterDelay(10, () -> {
            h.assertTrue(secondary.getHealth() == secondary.getMaxHealth(), "Burst damaged secondary after its primary became obscured");
            primary.discard();
            secondary.discard();
            heart.forceDeath();
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "weapon_sight_inherited", timeoutTicks = 30)
    public static void BlindWeaponUsesLastObservedPointUntilMoveMemoryExpires(GameTestHelper h) {
        var heart = core(h);
        var gun = module(heart, "gun");
        gun.edit("shotAim", 1, "");
        gun.edit("targetRange", 8, "");
        gun.edit("lineOfSight", 0, "");
        gun.edit("interval", 200, "");
        moves(h, heart, gun, 6);
        var origin = ModuleCombat.nozzle(heart, gun);
        var primary = cow(h, origin.add(7, 0, 0));
        heart.tryActivate(TestActors.creative(h.getLevel()));
        heart.modules().tick(heart);
        pin(h, heart, 10);

        var first = heart.modules().moveAim(gun.id());
        h.assertTrue(first != null && first.visible() && first.id().equals(primary.getUUID()), "Move did not initially see the primary");
        h.runAfterDelay(1, () -> {
            wall(h, BlockPos.containing(origin).east(4), 10, 7);
            primary.setPos(primary.position().add(2, 0, 2));
        });
        h.runAfterDelay(4, () -> {
            var observation = heart.modules().moveAim(gun.id());
            h.assertTrue(observation != null && !observation.visible() && observation.point().distanceTo(first.point()) < .01,
                    "Fixture failed to retain the original visible point");
            var shooter = ModuleCombat.nozzle(heart, gun);
            var directions = ProjectileAim.previewDirections(heart, gun, shooter);
            var expected = first.point().subtract(shooter).normalize();
            var hiddenDelta = primary.getBoundingBox().getCenter().subtract(shooter);
            h.assertTrue(hiddenDelta.length() > gun.value("targetRange"), "Hidden target did not move beyond the weapon's range");
            var hidden = hiddenDelta.normalize();
            h.assertTrue(expected.distanceTo(hidden) > .1, "Fixture did not separate the remembered point from hidden movement");
            h.assertTrue(directions.size() == 1 && directions.getFirst().distanceTo(expected) < .01,
                    "Weapon aimed at an unseen position instead of the last observed position");
        });
        h.runAfterDelay(10, () -> {
            h.assertTrue(primary.getUUID().equals(heart.modules().moveTarget(gun.id())), "Move action ended before its sight deadline");
            h.assertTrue(heart.modules().moveAim(gun.id()) == null && ProjectileAim.previewDirections(heart, gun,
                    ModuleCombat.nozzle(heart, gun)).isEmpty(), "Weapon retained a target after the move's sight deadline");
            primary.discard();
            heart.forceDeath();
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "laser_retains_heading_after_cover", timeoutTicks = 28)
    public static void LegacyTrackingLaserContinuesAlongItsLastHeading(GameTestHelper h) {
        firingLaserWithCover(h, false);
    }

    @GameTest(template = "combat", batch = "laser_retains_last_visible_range", timeoutTicks = 28)
    public static void PositiveLaserMemoryDoesNotEmitWhenHiddenTargetExitsRange(GameTestHelper h) {
        firingLaserWithCover(h, true);
    }

    private static void firingLaserWithCover(GameTestHelper h, boolean retain) {
        var level = h.getLevel();
        var heart = core(h);
        var laser = module(heart, "laser");
        laser.edit("laserAim", 1, "");
        laser.edit("chargeTime", 0, "");
        laser.edit("beamLength", 24, "");
        laser.edit("beamWidth", .8, "");
        laser.edit("beamHits", 4, "");
        laser.edit("hitInterval", 3, "");
        laser.edit("damage", 2, "");
        laser.edit("interval", 100, "");
        if (retain) laser.edit("sightMemory", 10, "");

        heart.tryActivate(TestActors.creative(level));
        var origin = ModuleCombat.nozzle(heart, laser);
        var primary = cow(h, origin.add(12, 0, 0));
        heart.modules().tick(heart);
        h.assertTrue(primary.getHealth() == primary.getMaxHealth() - 2, "Burst failed to hit before the wall was placed");

        pin(h, heart, 8);

        h.runAfterDelay(1, () -> {
            wall(h, BlockPos.containing(origin).east(8), 10, 7);
            if (retain) primary.setPos(primary.position().add(19, 0, 2));
            var witness = cow(h, origin.add(4, 0, 0));
            h.assertTrue(!ModuleTargets.visible(level, heart.creatureId(), origin, primary), "Primary must be behind the wall");
            h.assertTrue(ModuleTargets.visible(level, heart.creatureId(), origin, witness), "Witness must be in front of the wall");
            h.runAfterDelay(6, () -> {
                var muzzle = ModuleCombat.nozzle(heart, laser);
                h.assertTrue(!ModuleTargets.visible(level, heart.creatureId(), muzzle, primary)
                                && ModuleTargets.visible(level, heart.creatureId(), muzzle, witness),
                        "Wall or orientation changed during the burst");
                h.assertTrue(retain ? witness.getHealth() == witness.getMaxHealth() : witness.getHealth() < witness.getMaxHealth(),
                        retain ? "Positive weapon memory emitted after hidden target moved outside range"
                                : "Already-firing laser stopped emitting along its last heading");
                witness.discard();
                primary.discard();
                heart.forceDeath();
                h.succeed();
            });
        });
    }

    @GameTest(template = "combat", batch = "dashboard_preview_read_only", timeoutTicks = 28)
    public static void DashboardGunPreviewDoesNotWriteCombatMemory(GameTestHelper h) {
        var level = h.getLevel();
        var heart = core(h);
        var gun = sightGun(heart);
        gun.edit("trigger", 2, "");
        gun.edit("channel", 1, "");
        var origin = ModuleCombat.nozzle(heart, gun);
        var target = cow(h, origin.add(7, 0, 0));
        var profile = new GameProfile(UUID.randomUUID(), "WeaponPreviewViewer");
        var viewer = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        var transport = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(transport);
        NetworkRegistry.configureMockConnection(transport);
        level.getServer().getPlayerList().placeNewPlayer(transport, viewer, CommonListenerCookie.createInitial(profile, false));
        viewer.setGameMode(GameType.CREATIVE);
        viewer.setPos(origin.add(0, 0, -2));
        viewer.setNoGravity(true);
        heart.tryActivate(viewer);
        pin(h, heart, 6);
        h.assertTrue(!heart.canEdit(viewer), "Fixture must lock editing");
        h.assertTrue(heart.modules().weaponSight().target(heart, gun) == null, "Fixture already had a live lock");
        var menu = new HeartDashboardMenu(1, viewer.getInventory(), heart);
        try {
            h.assertTrue(menu.action(viewer, heart.modules().revision(), gun.id(), "preview", "", 0, ""),
                    "Dashboard rejected Preview action");
        } catch (RuntimeException failure) {
            level.getServer().getPlayerList().remove(viewer);
            channel.finishAndReleaseAll();
            throw failure;
        }

        long delay = 5 - level.getGameTime() % 5;
        h.runAfterDelay(delay, () -> {
            try {
                var nozzle = ModuleCombat.nozzle(heart, gun);
                h.assertTrue(level.getGameTime() % 5 == 0 && ModuleTargets.visible(level, heart.creatureId(), nozzle, target)
                                && !HeartModules.triggered(gun, heart), "Preview fixture is not ready");
                h.assertTrue(!ProjectileAim.previewDirections(heart, gun, nozzle).isEmpty(), "No preview target");
                MarkingSessions.tick(new ServerTickEvent.Post(() -> true, level.getServer()));
                var selected = heart.modules().weaponSight().target(heart, gun);
                h.assertTrue(selected == null, "Dashboard preview wrote a live gun target");
                h.succeed();
            } finally {
                level.getServer().getPlayerList().remove(viewer);
                channel.finishAndReleaseAll();
                target.discard();
                heart.forceDeath();
            }
        });
    }

    @GameTest(template = "combat", batch = "independent_gun_remembered_range", timeoutTicks = 28)
    public static void HiddenMovementBeyondRangeKeepsIndependentGunLockUntilDeadline(GameTestHelper h) {
        var heart = core(h);
        var gun = sightGun(heart);
        var level = h.getLevel();
        var origin = ModuleCombat.nozzle(heart, gun);
        var primary = cow(h, origin.add(7, 0, 0));
        var secondary = cow(h, origin.add(-7.5, 0, 0));
        heart.tryActivate(TestActors.creative(level));
        heart.modules().tick(heart);
        pin(h, heart, 12);
        h.assertTrue(primary.getUUID().equals(heart.modules().weaponSight().target(heart, gun)), "Fixture failed to acquire primary");
        h.runAfterDelay(1, () -> {
            wall(h, BlockPos.containing(origin).east(4), 10, 7);
            primary.setPos(primary.position().add(3, 0, 2));
        });
        h.runAfterDelay(3, () -> {
            var muzzle = ModuleCombat.nozzle(heart, gun);
            h.assertTrue(!ModuleTargets.visible(level, heart.creatureId(), muzzle, primary)
                            && ModuleTargets.visible(level, heart.creatureId(), muzzle, secondary)
                            && primary.getBoundingBox().getCenter().distanceTo(muzzle) > gun.value("targetRange"),
                    "Primary must be hidden beyond range and secondary visible within range");
            var attempt = ProjectileAim.directions(heart, gun, muzzle);
            h.assertTrue(attempt.isEmpty() && primary.getUUID().equals(heart.modules().weaponSight().target(heart, gun)),
                    "Gun dropped its last visible in-range point when the hidden target moved out of range");
        });
        h.runAfterDelay(11, () -> {
            var direction = ProjectileAim.directions(heart, gun, ModuleCombat.nozzle(heart, gun));
            h.assertTrue(direction.size() == 1 && direction.getFirst().x < -.9
                    && secondary.getUUID().equals(heart.modules().weaponSight().target(heart, gun)),
                    "Gun failed to acquire the visible alternative after expiry");
            primary.discard();
            secondary.discard();
            heart.forceDeath();
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "homing_post_launch_lifetime", timeoutTicks = 28)
    public static void LaunchedHomingShotOutlivesItsMoveAction(GameTestHelper h) {
        var heart = core(h);
        var level = h.getLevel();
        var homing = heart.modules().add("homing");
        homing.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), Direction.UP)));
        homing.edit("target", 1, "");
        homing.edit("lineOfSight", 1, "");
        homing.edit("targetRange", 16, "");
        homing.edit("homingDeploy", 0, "");
        homing.edit("chargeTime", 0, "");
        homing.edit("homingLife", 30, "");
        homing.edit("interval", 200, "");
        var origin = ModuleCombat.origin(heart, homing, true);
        var target = cow(h, origin.add(10, 0, 0));
        addMove(h, heart, homing, .2);
        heart.tryActivate(TestActors.creative(level));
        heart.modules().tick(heart);
        pin(h, heart, 8);
        for (var deployed : level.getEntitiesOfClass(ModuleShot.class,
                new AABB(origin, origin).inflate(32),
                candidate -> heart.creatureId().equals(candidate.attackOwner()))) deployed.discard();
        var handle = ModuleTargets.attackAim(heart, homing);
        h.assertTrue(handle != null && handle.followsMove() && target.getUUID().equals(handle.id()), "Fixture failed to acquire Move aim");
        var shot = ModuleShot.homingWithAim(level, origin.add(0, 2, 0), origin, homing, heart, 0, handle);
        var oldPending = shot.saveWithoutId(new CompoundTag());
        oldPending.getCompound("HomingConfig").getCompound("Settings").putDouble("chargeTime", 6);
        for (var key : List.of("HomingMoveTable", "HomingMoveRule", "HomingMoveAction")) oldPending.remove(key);
        var restored = new ModuleShot(SableLife.MODULE_SHOT.get(), level);
        restored.load(oldPending);
        restored.tick();
        var rebound = restored.saveWithoutId(new CompoundTag());
        h.assertTrue(restored.hovering() && rebound.hasUUID("HomingMoveTable")
                        && handle.moveAction().table().equals(rebound.getUUID("HomingMoveTable")),
                "An older pending projectile did not bind its matching action identity");
        restored.discard();

        shot.tick();
        h.assertTrue(!shot.hovering() && !shot.isRemoved() && target.getUUID().equals(shot.homingTarget()), "Fixture failed to launch");
        h.runAfterDelay(6, () -> {
            h.assertTrue(heart.modules().moveTarget(homing.id()) == null && target.isAlive()
                    && ModuleTargets.visible(level, heart.creatureId(), shot.position(), target),
                    "Fixture failed to end Move with an uninterrupted visible target");
            shot.tick();
            h.assertTrue(!shot.isRemoved() && target.getUUID().equals(shot.homingTarget()),
                    "Launch inherited a Move lifetime instead of the projectile's own lifetime");
            shot.discard();
            target.discard();
            heart.forceDeath();
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "exact_move_action_identity", timeoutTicks = 28)
    public static void SecondMoveSetWithSameStartDoesNotOwnFirstCapture(GameTestHelper h) {
        var heart = core(h);
        var gun = sightGun(heart);
        var world = h.getLevel();
        var target = cow(h, ModuleCombat.nozzle(heart, gun).add(7, 0, 0));
        addMove(h, heart, gun, .15);
        addMove(h, heart, gun, 1);
        heart.tryActivate(TestActors.creative(world));
        heart.modules().tick(heart);
        pin(h, heart, 8);
        var first = ModuleTargets.attackAim(heart, gun);
        h.assertTrue(first != null && first.followsMove() && first.id().equals(target.getUUID()), "Fixture failed to capture first action");
        h.runAfterDelay(5, () -> {
            var next = ModuleTargets.attackAim(heart, gun);
            h.assertTrue(next != null && next.id().equals(first.id()) && next.moveActivation() == first.moveActivation(),
                    "Fixture did not switch active action while maintaining the same legacy tick and target");
            h.assertTrue(!first.available(heart, gun) && next.available(heart, gun),
                    "A stale weapon capture matched a different Move Set action with the same start tick and target");
            target.discard();
            heart.forceDeath();
            h.succeed();
        });
    }

    @GameTest(template = "combat", batch = "summon_retains_last_visible_range", timeoutTicks = 34)
    public static void SummonedWeaponKeepsLastInRangeAimUntilDeadline(GameTestHelper h) {
        var heart = core(h);
        var world = h.getLevel();
        var template = heart.modules().add("gun");
        template.edit("enabled", 0, "");
        var summon = module(heart, "summoned_weapon");
        summon.projectileItem(new ItemStack(Items.CROSSBOW), world.registryAccess());
        summon.summonAttack(template.id());
        summon.edit("summonAim", 1, "");
        summon.edit("summonTurnSpeed", 180, "");
        summon.edit("summonWindup", 30, "");
        summon.edit("targetRange", 8, "");
        summon.edit("sightMemory", 10, "");
        var origin = ModuleSummonedWeapon.position(heart, summon);
        var primary = cow(h, origin.add(6, 0, 0));
        var secondary = cow(h, origin.add(-7, 0, 0));
        heart.tryActivate(TestActors.creative(world));
        heart.modules().tick(heart);
        pin(h, heart, 20);

        var frame = heart.modules().summonedWeapons.frame(summon.id());
        h.assertTrue(frame != null && frame.direction().x > .95, "Summon did not aim at the nearer primary");
        h.runAfterDelay(1, () -> {
            wall(h, BlockPos.containing(origin).east(3), 10, 7);
            primary.setPos(primary.position().add(5, 0, 2));
        });
        h.runAfterDelay(8, () -> {
            var aim = heart.modules().summonedWeapons.frame(summon.id());
            h.assertTrue(!ModuleTargets.visible(world, heart.creatureId(), aim.muzzle(), primary)
                            && ModuleTargets.visible(world, heart.creatureId(), aim.muzzle(), secondary),
                    "Fixture failed to hide only the first target");
            h.assertTrue(aim.direction().x > .95, "Summon forgot the hidden primary before its deadline");
        });
        h.runAfterDelay(18, () -> {
            var aim = heart.modules().summonedWeapons.frame(summon.id());
            h.assertTrue(aim.direction().x < -.95, "Summon did not retarget once its memory expired");
            primary.discard();
            secondary.discard();
            heart.forceDeath();
            h.succeed();
        });
    }

    private static void addMove(GameTestHelper h, HeartBlockEntity heart, VirtualModule weapon, double duration) {
        var table = moves(h, heart, weapon, 20);
        h.assertTrue(table.edit("action_duration", duration, "0|0", heart.modules()), "Invalid action duration");
    }

    private static VirtualModule sightGun(HeartBlockEntity heart) {
        var gun = module(heart, "gun");
        gun.edit("shotAim", 1, "");
        gun.edit("sightMemory", 8, "");
        gun.edit("targetRange", 8, "");
        gun.edit("damage", 1, "");
        gun.edit("interval", 200, "");
        return gun;
    }

    private static HeartBlockEntity core(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 16, 8));
        level.setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(pos.south(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos, pos.south()),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ() + 1));
        return (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    }

    private static VirtualModule module(HeartBlockEntity heart, String type) {
        var anchor = ModuleAnchor.create(heart, heart.getBlockPos(), Direction.UP);
        var module = heart.modules().add(type);
        module.anchors(List.of(anchor));
        module.edit("target", 1, "");
        module.edit("lineOfSight", 1, "");
        return module;
    }

    private static MoveSet moves(GameTestHelper h, HeartBlockEntity heart, VirtualModule weapon, int memoryTicks) {
        var moves = heart.modules().add("move_set").moveSet();
        h.assertTrue(moves.edit("add", 0, "", heart.modules()), "Move creation rejected");
        h.assertTrue(moves.edit("action_add", 0, "0|" + weapon.id(), heart.modules()), "Weapon binding rejected");
        var rules = moves.get(0).config();
        rules.edit("dispatchFront", 3, "");
        rules.edit("dispatchCone", 60, "");
        rules.edit("target", 1, "");
        rules.edit("inheritMoveTarget", 1, "");
        rules.edit("lineOfSight", 1, "");
        rules.edit("sightMemory", memoryTicks, "");
        return moves;
    }

    private static LivingEntity cow(GameTestHelper h, Vec3 center) {
        var cow = EntityType.COW.create(h.getLevel());
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setPos(center.add(0, -cow.getBbHeight() / 2, 0));
        h.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static void wall(GameTestHelper h, BlockPos center, int height, int width) {
        for (var pos : BlockPos.betweenClosed(center.offset(0, -height, -width), center.offset(0, height, width)))
            h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    }

    private static void pin(GameTestHelper h, HeartBlockEntity heart, int ticks) {
        var body = (ServerSubLevel) Sable.HELPER.getContaining(h.getLevel(), heart.getBlockPos());
        var pipeline = SubLevelPhysicsSystem.get(h.getLevel()).getPipeline();
        var position = new Vector3d(body.logicalPose().position());
        var orientation = new Quaterniond(body.logicalPose().orientation());
        for (int tick = 1; tick <= ticks; tick++) h.runAfterDelay(tick, () -> {
            pipeline.resetVelocity(body);
            pipeline.teleport(body, position, orientation);
        });
    }
}
