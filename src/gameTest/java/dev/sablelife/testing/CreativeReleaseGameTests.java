package dev.sablelife.testing;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlock;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("sablelife_tests")
@PrefixGameTestTemplate(false)
public final class CreativeReleaseGameTests {
    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_creativereleasegametests_dyingbodyreleasesfromnoncoreblockandallowscreativebreaking")
    public static void dyingBodyReleasesFromNonCoreBlockAndAllowsCreativeBreaking(GameTestHelper helper) {
        var creature = platform(helper);
        var level = helper.getLevel();
        var heart = creature.heart();
        heart.configure(250, 40);
        heart.tryActivate(new RecordingPlayer(level));
        heart.damage(250);
        HeartBlockEntity.serverTick(level, heart.getBlockPos(), heart.getBlockState(), heart);
        helper.assertTrue(heart.health().state() == HeartState.DYING, "Fixture did not enter death delay");
        UUID oldId = heart.creatureId();

        // A nearby but separate structure must not be included just because its bounds are close.
        var otherOrigin = helper.absolutePos(new BlockPos(2, 7, 2));
        level.setBlockAndUpdate(otherOrigin, SableLife.HEART.get().defaultBlockState());
        var otherBody = SubLevelAssemblyHelper.assembleBlocks(level, otherOrigin, List.of(otherOrigin), bounds(otherOrigin, otherOrigin));
        var other = (HeartBlockEntity) level.getBlockEntity(otherBody.getPlot().getCenterBlock());
        other.tryActivate(new RecordingPlayer(level));
        UUID otherId = other.creatureId();

        helper.runAfterDelay(3, () -> {
            BlockPos target = heart.getBlockPos().below().east();
            var player = playerAtFace(level, creature, target, Direction.EAST, GameType.CREATIVE);
            attack(player, target, Direction.EAST);
            helper.assertTrue(level.getBlockState(target).is(Blocks.IRON_BLOCK), "Fixture did not reproduce protected dead body");
            long dropsBefore = itemCount(level);
            helper.assertTrue(useTool(player, target, Direction.EAST).consumesAction(), "Creative release did not consume the interaction");
            assertReset(helper, heart, oldId, 250, 40);
            helper.assertTrue(level.getBlockState(target).is(Blocks.IRON_BLOCK), "Release deleted the clicked body block");
            helper.assertTrue(level.getBlockState(heart.getBlockPos()).is(SableLife.HEART.get()), "Release deleted the Heart block");
            helper.assertTrue(!creature.body().isRemoved(), "Release dismantled the Sable structure");
            helper.assertTrue(itemCount(level) == dropsBefore, "Release spawned item drops");
            helper.assertTrue(other.health().state() == HeartState.ALIVE && other.creatureId().equals(otherId),
                    "Release changed an unrelated structure");

            var saved = heart.saveWithFullMetadata(level.registryAccess());
            var loaded = (HeartBlockEntity) BlockEntity.loadStatic(heart.getBlockPos(), heart.getBlockState(), saved, level.registryAccess());
            helper.assertTrue(loaded != null, "Released Heart could not reload");
            assertReset(helper, loaded, oldId, 250, 40);
            helper.assertTrue(loaded.creatureId().equals(heart.creatureId()), "Reload changed the new creature identity");

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            attack(player, target, Direction.EAST);
            helper.assertTrue(level.getBlockState(target).isAir(), "Creative empty-hand breaking stayed blocked after release");
            helper.succeed();
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_creativereleasegametests_toolonlivingcorerunsbeforeinspectandcanstartfreshlife")
    public static void toolOnLivingCoreRunsBeforeInspectAndCanStartFreshLife(GameTestHelper helper) {
        var creature = platform(helper);
        var heart = creature.heart();
        var level = helper.getLevel();
        heart.configure(240, 80);
        heart.tryActivate(new RecordingPlayer(level));
        heart.damage(40);
        UUID oldId = heart.creatureId();
        helper.runAfterDelay(3, () -> {
            var player = playerAtFace(level, creature, heart.getBlockPos(), Direction.UP, GameType.CREATIVE);
            helper.assertTrue(useTool(player, heart.getBlockPos(), Direction.UP).consumesAction(), "Tool use on the core was not handled");
            assertReset(helper, heart, oldId, 240, 80);
            helper.assertTrue(!player.messageKeys.contains("message.sablelife.status"), "Core inspect swallowed the release tool interaction");
            helper.assertTrue(player.getMainHandItem().is(SableLife.CREATIVE_RELEASE_TOOL.get()) && player.getMainHandItem().getCount() == 1,
                    "Creative release consumed its tool");
            UUID freshId = heart.creatureId();
            heart.tryActivate(player);
            helper.assertTrue(heart.health().state() == HeartState.ALIVE && heart.health().current() == 240,
                    "Released structure could not start a new life with its configured health");
            helper.assertTrue(heart.creatureId().equals(freshId), "Activation did not retain the newly reset identity");
            helper.assertTrue(heart.saveWithoutMetadata(level.registryAccess()).hasUUID("BoundBody"), "Fresh activation did not bind the structure");
            helper.succeed();
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_creativereleasegametests_releasingdyingbodyclearspendingdeathandoutputs")
    public static void releasingDyingBodyClearsPendingDeathAndOutputs(GameTestHelper helper) {
        var creature = platform(helper);
        var heart = creature.heart();
        var level = helper.getLevel();
        heart.configure(180, 4);
        heart.tryActivate(new RecordingPlayer(level));
        helper.runAfterDelay(3, () -> {
            heart.damage(180);
            helper.assertTrue(heart.health().state() == HeartState.DYING && heart.health().remainingDeathTicks() == 4,
                    "Fixture did not enter the pending-death state");
            UUID oldId = heart.creatureId();
            var target = heart.getBlockPos().below().east();
            var player = playerAtFace(level, creature, target, Direction.EAST, GameType.CREATIVE);
            InteractionResult releaseResult = useTool(player, target, Direction.EAST);
            String releaseDiagnostic = "result=" + releaseResult + "; messages=" + player.messageKeys
                    + "; clicked=" + target + "; clickedState=" + level.getBlockState(target)
                    + "; inRange=" + player.canInteractWithBlock(target, 1);
            helper.assertTrue(releaseResult.consumesAction(), "Dying release was rejected: " + releaseDiagnostic + "; " + describe(level, heart));
            helper.assertTrue(heart.health().state() == HeartState.DORMANT,
                    "Immediately after dying release: " + releaseDiagnostic + "; " + describe(level, heart));
            assertReset(helper, heart, oldId, 180, 4);
            UUID resetId = heart.creatureId();
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(heart.health().state() == HeartState.DORMANT,
                        "Eight ticks after dying release: " + releaseDiagnostic + "; " + describe(level, heart));
                assertReset(helper, heart, oldId, 180, 4);
                helper.assertTrue(heart.creatureId().equals(resetId), "Old death countdown changed the reset identity");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "combat", timeoutTicks = 160, batch="audit_creativereleasegametests_survivalcannotreleasethroughtoolorcoremethod")
    public static void survivalCannotReleaseThroughToolOrCoreMethod(GameTestHelper helper) {
        var creature = platform(helper);
        var heart = creature.heart();
        var level = helper.getLevel();
        heart.configure(220, 80);
        heart.tryActivate(new RecordingPlayer(level));
        heart.damage(30);
        helper.runAfterDelay(3, () -> {
            var target = heart.getBlockPos().below().east();
            var player = playerAtFace(level, creature, target, Direction.EAST, GameType.SURVIVAL);
            var before = heart.saveWithoutMetadata(level.registryAccess());
            useTool(player, target, Direction.EAST);
            helper.assertTrue(before.equals(heart.saveWithoutMetadata(level.registryAccess())), "Survival tool use reset body state");
            var corePlayer = playerAtFace(level, creature, heart.getBlockPos(), Direction.UP, GameType.SURVIVAL);
            useTool(corePlayer, heart.getBlockPos(), Direction.UP);
            helper.assertTrue(before.equals(heart.saveWithoutMetadata(level.registryAccess())), "Survival tool use on core bypassed restriction");
            helper.assertTrue(!heart.resetForCreative(player), "Direct reset method accepted a survival player");
            helper.assertTrue(before.equals(heart.saveWithoutMetadata(level.registryAccess())), "Rejected direct reset mutated the Heart");
            helper.assertTrue(heart.getBlockState().getValue(HeartBlock.ACTIVE), "Rejected release cleared activation flag");
            helper.assertTrue(level.getBlockState(target).is(Blocks.IRON_BLOCK), "Rejected release removed a body block");
            helper.succeed();
        });
    }

    private static void assertReset(GameTestHelper helper, HeartBlockEntity heart, UUID oldId, float maximum, int delay) {
        helper.assertTrue(heart.health().state() == HeartState.DORMANT, "Released Heart is not dormant");
        helper.assertTrue(heart.health().current() == maximum && heart.health().maximum() == maximum && heart.health().deathDelay() == delay,
                "Release did not retain the configured health/death delay and restore full health");
        helper.assertTrue(heart.health().remainingDeathTicks() == 0, "Release left a pending death countdown");
        helper.assertTrue(heart.pulseSignal() == 0 && heart.health().comparatorSignal() == 0, "Release left redstone output active");
        helper.assertTrue(!heart.creatureId().equals(oldId), "Release retained the previous creature identity");
        helper.assertTrue(!heart.getBlockState().getValue(HeartBlock.ACTIVE), "Release retained the active block state");
        helper.assertTrue(!heart.saveWithoutMetadata(helper.getLevel().registryAccess()).contains("BoundBody"), "Release retained a body binding");
    }

    private static Creature platform(GameTestHelper helper) {
        var level = helper.getLevel();
        var floor = helper.absolutePos(new BlockPos(6, 6, 6));
        var origin = floor.above();
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos block : BlockPos.betweenClosed(floor.offset(-1, 0, -1), floor.offset(1, 0, 1))) {
            var position = block.immutable();
            blocks.add(position);
            level.setBlockAndUpdate(position, Blocks.IRON_BLOCK.defaultBlockState());
        }
        blocks.add(origin);
        level.setBlockAndUpdate(origin, SableLife.HEART.get().defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, blocks, bounds(floor.offset(-1, 0, -1), floor.offset(1, 1, 1)));
        return new Creature(body, (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock()));
    }

    private static RecordingPlayer playerAtFace(ServerLevel level, Creature creature, BlockPos block, Direction side, GameType mode) {
        var player = new RecordingPlayer(level);
        player.setGameMode(mode);
        player.getAbilities().instabuild = mode == GameType.CREATIVE;
        var normal = creature.body().logicalPose().transformNormal(Vec3.atLowerCornerOf(side.getNormal()));
        var eye = SableBodyProbe.toWorld(level, Vec3.atCenterOf(block)).add(normal.scale(2));
        player.setPos(eye.subtract(0, player.getEyeHeight(), 0));
        return player;
    }

    private static InteractionResult useTool(RecordingPlayer player, BlockPos target, Direction side) {
        var stack = new ItemStack(SableLife.CREATIVE_RELEASE_TOOL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(target).add(Vec3.atLowerCornerOf(side.getNormal()).scale(0.5)), side, target, false);
        return player.gameMode.useItemOn(player, player.level(), stack, InteractionHand.MAIN_HAND, hit);
    }

    private static void attack(RecordingPlayer player, BlockPos target, Direction side) {
        player.gameMode.handleBlockBreakAction(target, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                side, player.level().getMaxBuildHeight(), 0);
    }

    private static long itemCount(ServerLevel level) {
        long count = 0;
        for (var entity : level.getAllEntities()) if (entity instanceof ItemEntity) count++;
        return count;
    }

    private static String describe(ServerLevel level, HeartBlockEntity heart) {
        var current = level.getBlockEntity(heart.getBlockPos());
        return "heartState=" + heart.health().state() + "; hp=" + heart.health().current()
                + "; remainingDeathTicks=" + heart.health().remainingDeathTicks()
                + "; removed=" + heart.isRemoved() + "; sameWorldBE=" + (current == heart)
                + "; blockState=" + level.getBlockState(heart.getBlockPos())
                + "; connectedCores=" + SableBodyProbe.findConnectedHearts(level, heart.getBlockPos()).stream()
                        .map(found -> found.creatureId() + "/" + found.health().state() + "/same=" + (found == heart)).toList()
                + "; heartNbt=" + heart.saveWithoutMetadata(level.registryAccess())
                + "; worldHeartNbt=" + (current instanceof HeartBlockEntity live ? live.saveWithoutMetadata(level.registryAccess()) : current);
    }

    private static BoundingBox3i bounds(BlockPos a, BlockPos b) {
        return new BoundingBox3i(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    private record Creature(ServerSubLevel body, HeartBlockEntity heart) {}

    private static final class RecordingPlayer extends FakePlayer {
        private final List<String> messageKeys = new ArrayList<>();

        RecordingPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "SableReleaseTest"));
        }

        @Override public void displayClientMessage(Component message, boolean overlay) {
            if (message.getContents() instanceof TranslatableContents contents) messageKeys.add(contents.getKey());
        }
    }
}
