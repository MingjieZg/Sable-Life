package dev.sablelife.testing;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("sablelife_combat_tests")
@PrefixGameTestTemplate(false)
public final class BodyCombatGameTests {
    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_bodycombatgametests_realtridentdamageslivingbody")
    public static void realTridentDamagesLivingBody(GameTestHelper helper) {
        var creature = cube(helper, true);
        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            var target = creature.heart().getBlockPos().above();
            var center = SableBodyProbe.toWorld(level, Vec3.atCenterOf(target));
            var trident = new ThrownTrident(net.minecraft.world.entity.EntityType.TRIDENT, level);
            trident.setPos(center.add(1.5, 0, 0));
            trident.setDeltaMovement(-3, 0, 0);
            trident.setNoGravity(true);
            float before = creature.heart().health().current();
            trident.tick();
            helper.assertTrue(creature.heart().health().current() == before - 8,
                    "Real trident did not deal 8 HP; HP=" + creature.heart().health().current());
            helper.assertTrue(level.getBlockState(target).is(Blocks.IRON_BLOCK), "Trident removed the body block");
            trident.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_bodycombatgametests_livingbodystillallowsblockplacementonitsfaces")
    public static void livingBodyStillAllowsBlockPlacementOnItsFaces(GameTestHelper helper) {
        var creature = cube(helper, true);
        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            var target = creature.heart().getBlockPos().above();
            var player = playerAtFace(level, creature, target, Direction.UP);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.DIRT));
            var hit = new BlockHitResult(Vec3.atCenterOf(target).add(0, 0.5, 0), Direction.UP, target, false);
            InteractionResult result = player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            var placed = target.above();
            helper.assertTrue(result.consumesAction() && level.getBlockState(placed).is(Blocks.DIRT),
                    "Living body refused a block placed on its top face: result=" + result + "; target=" + target
                            + "; placed=" + placed + "; state=" + level.getBlockState(placed));
            helper.succeed();
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_bodycombatgametests_survivalcannotplaceonlivingbody")
    public static void survivalCannotPlaceOnLivingBody(GameTestHelper helper) {
        var creature = cube(helper, true);
        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            var target = creature.heart().getBlockPos().above();
            var player = playerAtFace(level, creature, target, Direction.UP);
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().instabuild = false;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.DIRT));
            var hit = new BlockHitResult(Vec3.atCenterOf(target).add(0, 0.5, 0), Direction.UP, target, false);
            InteractionResult result = player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(!result.consumesAction() && level.getBlockState(target.above()).isAir(),
                    "Survival player placed a block on a living body: result=" + result);
            helper.succeed();
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_bodycombatgametests_sharpnessaddsmeleedamage")
    public static void sharpnessAddsMeleeDamage(GameTestHelper helper) {
        var creature = cube(helper, true);
        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            var target = creature.heart().getBlockPos().east();
            var player = playerAtFace(level, creature, target, Direction.EAST);
            var weapon = new ItemStack(Items.DIAMOND_SWORD);
            var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            weapon.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 4);
            player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
            player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7);
            float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            float strength = player.getAttackStrengthScale(0.5F);
            float before = creature.heart().health().current();
            attack(player, target, Direction.EAST);
            float dealt = before - creature.heart().health().current();
            float vanillaBase = base * (0.2F + strength * strength * 0.8F);
            helper.assertTrue(dealt > vanillaBase + 1.9F * strength,
                    "Sharpness IV did not add melee damage: dealt=" + dealt + "; vanillaBase=" + vanillaBase);
            helper.succeed();
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_bodycombatgametests_survivalcannotplaceduringdeathdelay")
    public static void survivalCannotPlaceDuringDeathDelay(GameTestHelper helper) {
        var creature = cube(helper, false);
        var heart = creature.heart();
        heart.configure(100, 40);
        heart.tryActivate(new RecordingPlayer(helper.getLevel()));
        heart.damage(100);
        HeartBlockEntity.serverTick(helper.getLevel(), heart.getBlockPos(), heart.getBlockState(), heart);
        helper.assertTrue(heart.health().state() == dev.sablelife.heart.HeartState.DYING, "Fixture did not enter death delay");
        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            var target = heart.getBlockPos().above();
            var player = playerAtFace(level, creature, target, Direction.UP);
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().instabuild = false;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.DIRT));
            var hit = new BlockHitResult(Vec3.atCenterOf(target).add(0, 0.5, 0), Direction.UP, target, false);
            player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(level.getBlockState(target.above()).isAir(), "Survival player placed a block on a dead body");
            helper.succeed();
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_bodycombatgametests_airbornemeleeiscritical")
    public static void airborneMeleeIsCritical(GameTestHelper helper) {
        var creature = cube(helper, true);
        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            var target = creature.heart().getBlockPos().east();
            var player = playerAtFace(level, creature, target, Direction.EAST);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7);
            player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1000);
            player.setOnGround(false);
            player.fallDistance = 1.0F;
            float before = creature.heart().health().current();
            attack(player, target, Direction.EAST);
            float dealt = before - creature.heart().health().current();
            helper.assertTrue(dealt > 10.0F, "Airborne melee did not critically hit: dealt=" + dealt
                    + "; onGround=" + player.onGround() + "; fall=" + player.fallDistance
                    + "; strength=" + player.getAttackStrengthScale(0.5F) + "; range=" + player.canInteractWithBlock(target, 1));
            helper.succeed();
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_bodycombatgametests_rotatingmovingbodyreceivesarrowsandswordhits")
    public static void rotatingMovingBodyReceivesArrowsAndSwordHits(GameTestHelper helper) {
        var creature = cube(helper, true);
        helper.runAfterDelay(3, () -> {
            var handle = RigidBodyHandle.of(creature.body());
            handle.teleport(new Vector3d(creature.body().logicalPose().position()), new Quaterniond().rotationXYZ(0.25, 0.6, 0.15));
            handle.addLinearAndAngularVelocity(new Vector3d(0.5, 0, 0), new Vector3d(0, 0.3, 0));
            helper.runAfterDelay(3, () -> {
                var level = helper.getLevel();
                for (Direction side : Direction.values()) {
                    var target = creature.heart().getBlockPos().relative(side);
                    var normal = creature.body().logicalPose().transformNormal(Vec3.atLowerCornerOf(side.getNormal()));
                    var center = SableBodyProbe.toWorld(level, Vec3.atCenterOf(target));
                    Arrow arrow = arrow(level, center.add(normal.scale(1.5)), normal.scale(-3));
                    float before = creature.heart().health().current();
                    arrow.tick();
                    helper.assertTrue(creature.heart().health().current() < before, "Rotated arrow missed " + side);
                    arrow.discard();
                    var player = playerAtFace(level, creature, target, side);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
                    before = creature.heart().health().current();
                    attack(player, target, side);
                    helper.assertTrue(creature.heart().health().current() < before, "Rotated sword hit missed " + side);
                    helper.assertTrue(level.getBlockState(target).is(Blocks.IRON_BLOCK), "Sword erased a rotated body block");
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_bodycombatgametests_realarrowsdamagenoncoreblocksfromallsixfaces")
    public static void realArrowsDamageNonCoreBlocksFromAllSixFaces(GameTestHelper helper) {
        var creature = cube(helper, true);
        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            for (Direction side : Direction.values()) {
                BlockPos target = creature.heart().getBlockPos().relative(side);
                Vec3 normal = creature.body().logicalPose().transformNormal(Vec3.atLowerCornerOf(side.getNormal()));
                Vec3 center = SableBodyProbe.toWorld(level, Vec3.atCenterOf(target));
                Arrow arrow = arrow(level, center.add(normal.scale(1.5)), normal.scale(-3));
                String diagnostic = rayInfo(level, arrow.position(), arrow.position().add(arrow.getDeltaMovement()), arrow);
                float before = creature.heart().health().current();
                arrow.tick(); // Run the real projectile raycast, Sable transform and NeoForge impact hook.
                helper.assertTrue(creature.heart().health().current() == before - 6,
                        "Arrow did not deal 6 HP through body face " + side + "; HP=" + creature.heart().health().current() + "; expected=" + target + "; " + diagnostic);
                helper.assertTrue(level.getBlockState(target).is(Blocks.IRON_BLOCK), "An arrow must not remove the body block");
                arrow.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_bodycombatgametests_creativemeleeusesbodyfacesandrejectsduplicatestartpackets")
    public static void creativeMeleeUsesBodyFacesAndRejectsDuplicateStartPackets(GameTestHelper helper) {
        var creature = cube(helper, true);
        helper.runAfterDelay(3, () -> {
            for (Direction side : Direction.values()) {
                var target = creature.heart().getBlockPos().relative(side);
                var player = playerAtFace(helper.getLevel(), creature, target, side);
                player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7);
                float before = creature.heart().health().current();
                attack(player, target, side);
                float after = creature.heart().health().current();
                helper.assertTrue(after < before, "Creative melee did not damage face " + side + "; expected=" + target + "; inRange=" + player.canInteractWithBlock(target, 1) + "; "
                        + rayInfo(helper.getLevel(), player.getEyePosition(), SableBodyProbe.toWorld(helper.getLevel(), Vec3.atCenterOf(target)), player));
                helper.assertTrue(helper.getLevel().getBlockState(target).is(Blocks.IRON_BLOCK), "Creative attack erased body block");
                attack(player, target, side);
                helper.assertTrue(creature.heart().health().current() == after, "Repeated START in one tick dealt duplicate damage");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_bodycombatgametests_spentarrowcannotdamageagainafternbtreload")
    public static void spentArrowCannotDamageAgainAfterNbtReload(GameTestHelper helper) {
        var creature = cube(helper, true);
        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            var target = creature.heart().getBlockPos().east();
            Vec3 center = SableBodyProbe.toWorld(level, Vec3.atCenterOf(target));
            Arrow first = arrow(level, center.add(1.5, 0, 0), new Vec3(-3, 0, 0));
            String diagnostic = rayInfo(level, first.position(), first.position().add(first.getDeltaMovement()), first);
            first.tick();
            float afterFirst = creature.heart().health().current();
            helper.assertTrue(afterFirst == 94, "First real arrow did not hit; expected=" + target + "; " + diagnostic);
            var saved = first.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            Arrow restored = new Arrow(EntityType.ARROW, level);
            restored.load(saved);
            restored.setDeltaMovement(-3, 0, 0);
            EventHooks.onProjectileImpact(restored, new BlockHitResult(Vec3.atCenterOf(target), Direction.EAST, target, false));
            helper.assertTrue(creature.heart().health().current() == afterFirst, "Reloaded arrow repeated damage on the same creature");
            first.discard();
            restored.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_bodycombatgametests_meleecannothitthroughworldwallorfromfaraway")
    public static void meleeCannotHitThroughWorldWallOrFromFarAway(GameTestHelper helper) {
        var creature = cube(helper, true);
        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            var target = creature.heart().getBlockPos().east();
            var player = playerAtFace(level, creature, target, Direction.EAST);
            Vec3 center = SableBodyProbe.toWorld(level, Vec3.atCenterOf(target));
            var wall = BlockPos.containing(center.add(1.1, 0, 0));
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            attack(player, target, Direction.EAST);
            helper.assertTrue(creature.heart().health().current() == 100, "Melee bypassed an intervening world block");
            level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            player.setPos(center.add(30, 0, 0));
            attack(player, target, Direction.EAST);
            helper.assertTrue(creature.heart().health().current() == 100, "Out-of-range packet dealt damage");
            helper.succeed();
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_bodycombatgametests_detachedfragmentandterraindonotforwarddamage")
    public static void detachedFragmentAndTerrainDoNotForwardDamage(GameTestHelper helper) {
        var creature = cube(helper, true);
        var level = helper.getLevel();
        var block = creature.heart().getBlockPos().east();
        var fragment = SubLevelAssemblyHelper.assembleBlocks(level, block, List.of(block), bounds(block, block));
        var fragmentBlock = fragment.getPlot().getCenterBlock();
        helper.assertTrue(SableBodyProbe.findActivatedHeart(level, fragmentBlock) == null, "Detached fragment still has a live Heart");
        Arrow arrow = arrow(level, new Vec3(0, 100, 0), new Vec3(3, 0, 0));
        EventHooks.onProjectileImpact(arrow, new BlockHitResult(Vec3.atCenterOf(fragmentBlock), Direction.WEST, fragmentBlock, false));
        var terrain = helper.absolutePos(new BlockPos(0, 1, 0));
        level.setBlockAndUpdate(terrain, Blocks.IRON_BLOCK.defaultBlockState());
        EventHooks.onProjectileImpact(arrow, new BlockHitResult(Vec3.atCenterOf(terrain), Direction.WEST, terrain, false));
        helper.assertTrue(creature.heart().health().current() == 100, "Fragment or unrelated terrain damaged the Heart");
        arrow.discard();
        helper.succeed();
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_bodycombatgametests_dormantstructureignoresprojectiledamage")
    public static void dormantStructureIgnoresProjectileDamage(GameTestHelper helper) {
        var creature = cube(helper, false);
        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            var target = creature.heart().getBlockPos().east();
            Vec3 center = SableBodyProbe.toWorld(level, Vec3.atCenterOf(target));
            Arrow arrow = arrow(level, center.add(1.5, 0, 0), new Vec3(-3, 0, 0));
            arrow.tick();
            helper.assertTrue(creature.heart().health().current() == 100, "Dormant body received damage");
            arrow.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "combat", batch="audit_bodycombatgametests_normalandsneakrightclickgivedifferentfeedbackwithoutresettinghp")
    public static void normalAndSneakRightClickGiveDifferentFeedbackWithoutResettingHp(GameTestHelper helper) {
        var creature = cube(helper, false);
        var heart = creature.heart();
        var player = new RecordingPlayer(helper.getLevel());
        var hit = new BlockHitResult(Vec3.atCenterOf(heart.getBlockPos()), Direction.UP, heart.getBlockPos(), false);
        player.setShiftKeyDown(false);
        heart.getBlockState().useWithoutItem(helper.getLevel(), player, hit);
        helper.assertTrue(player.keys.contains("message.sablelife.status"), "Normal right click did not inspect");
        player.keys.clear();
        player.setShiftKeyDown(true);
        heart.getBlockState().useWithoutItem(helper.getLevel(), player, hit);
        helper.assertTrue(player.keys.contains("message.sablelife.activated"), "Sneak right click did not activate");
        heart.damage(20);
        player.keys.clear();
        heart.getBlockState().useWithoutItem(helper.getLevel(), player, hit);
        helper.assertTrue(player.keys.contains("message.sablelife.already_active"), "Repeated activation reused the inspect response");
        helper.assertTrue(heart.health().current() == 80, "Repeated activation reset health");
        helper.succeed();
    }

    @GameTest(template="combat",batch="protected_tool_attack")
    public static void ProtectedToolsAttackButBreakingEnabledKeepsMining(GameTestHelper h) {
        var creature=cube(h,true);h.runAfterDelay(3,()->{
            var core=creature.heart();var target=core.getBlockPos().east();
            var player=playerAtFace(h.getLevel(),creature,target,Direction.EAST);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_AXE));
            var click=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(player,target,Direction.EAST,
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START);
            float before=core.health().current();dev.sablelife.combat.BodyCombat.onLeftClickBlock(click);
            h.assertTrue(click.isCanceled()&&click.getUseItem()==net.neoforged.neoforge.common.util.TriState.FALSE
                &&click.getUseBlock()==net.neoforged.neoforge.common.util.TriState.FALSE,"Protected tool entered mining");
            h.assertTrue(core.health().current()<before,"Protected axe did not attack");
            h.assertTrue(core.editCore(player,"breaking",1,""),"Breaking switch failed");
            var allowed=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(player,target,Direction.EAST,
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START);
            before=core.health().current();dev.sablelife.combat.BodyCombat.onLeftClickBlock(allowed);
            h.assertTrue(!allowed.isCanceled()&&core.health().current()==before,"Allowed mining became an attack");
            var update=core.getUpdateTag(h.getLevel().registryAccess());h.assertTrue(update.getBoolean("AllowBreaking"),"Breaking mode not synchronized");
            var client=new HeartBlockEntity(core.getBlockPos(),core.getBlockState());client.handleUpdateTag(update,h.getLevel().registryAccess());
            h.assertTrue(client.allowBreaking(),"Client rejected synchronized mining mode");
            core.editCore(player,"breaking",0,"");client.handleUpdateTag(core.getUpdateTag(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(!client.allowBreaking(),"Client retained stale mining mode");h.succeed();
        });
    }
    private static Creature cube(GameTestHelper helper, boolean activate) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(6, 6, 6));
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos block : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            var position = block.immutable();
            blocks.add(position);
            level.setBlockAndUpdate(position, position.equals(center) ? SableLife.HEART.get().defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState());
        }
        var body = SubLevelAssemblyHelper.assembleBlocks(level, center, blocks, bounds(center.offset(-1, -1, -1), center.offset(1, 1, 1)));
        var heart = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        if (activate) heart.tryActivate(new RecordingPlayer(level));
        return new Creature(body, heart);
    }

    private static Arrow arrow(ServerLevel level, Vec3 position, Vec3 velocity) {
        var arrow = new Arrow(EntityType.ARROW, level);
        arrow.setPos(position);
        arrow.setDeltaMovement(velocity);
        arrow.setBaseDamage(2);
        arrow.setNoGravity(true);
        // Manual tick in the fixture deliberately avoids spawning pickup entities across test boundaries.
        return arrow;
    }

    private static RecordingPlayer playerAtFace(ServerLevel level, Creature creature, BlockPos block, Direction side) {
        var player = new RecordingPlayer(level);
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        var normal = creature.body().logicalPose().transformNormal(Vec3.atLowerCornerOf(side.getNormal()));
        var eye = SableBodyProbe.toWorld(level, Vec3.atCenterOf(block)).add(normal.scale(2));
        player.setPos(eye.subtract(0, player.getEyeHeight(), 0));
        return player;
    }

    private static void attack(RecordingPlayer player, BlockPos block, Direction side) {
        player.gameMode.handleBlockBreakAction(block, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                side, player.level().getMaxBuildHeight(), 0);
    }

    private static BoundingBox3i bounds(BlockPos a, BlockPos b) {
        return new BoundingBox3i(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    private static String rayInfo(ServerLevel level, Vec3 from, Vec3 to, net.minecraft.world.entity.Entity entity) {
        var ray = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        return "from=" + from + "; to=" + to + "; result=" + ray.getType() + "/" + ray.getBlockPos() + "; state=" + level.getBlockState(ray.getBlockPos());
    }

    private record Creature(ServerSubLevel body, HeartBlockEntity heart) {}

    private static final class RecordingPlayer extends FakePlayer {
        private final List<String> keys = new ArrayList<>();
        RecordingPlayer(ServerLevel level) { super(level, new GameProfile(UUID.randomUUID(), "SableLifeTest")); }
        @Override public void displayClientMessage(Component message, boolean overlay) {
            if (message.getContents() instanceof TranslatableContents text) keys.add(text.getKey());
        }
    }
}
