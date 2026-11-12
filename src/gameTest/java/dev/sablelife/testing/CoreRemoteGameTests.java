package dev.sablelife.testing;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.CoreRelease;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartSettingsMenu;
import dev.sablelife.heart.HeartState;
import dev.sablelife.module.HeartDashboardMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("sablelife_core_ui_tests")
@PrefixGameTestTemplate(false)
public final class CoreRemoteGameTests {
    @GameTest(template = "combat", batch = "core_remote_recipe")
    public static void remoteRequiresTheLeverAndRedstoneCross(GameTestHelper test) {
        var level = test.getLevel();
        var recipeId = ResourceLocation.fromNamespaceAndPath("sablelife", "core_remote");
        var redstone = new ItemStack(Items.REDSTONE);
        var lever = new ItemStack(Items.LEVER);
        var empty = ItemStack.EMPTY;
        var cross = new ArrayList<>(List.of(empty, redstone, empty, redstone, lever, redstone, empty, redstone, empty));
        var input = CraftingInput.of(3, 3, cross);
        var crafted = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        test.assertTrue(crafted.id().equals(recipeId)
                        && crafted.value().assemble(input, level.registryAccess()).is(SableLife.CORE_REMOTE.get()),
                "The lever and redstone cross did not craft the Core Remote");
        for (int slot : new int[] {1, 3, 4, 5, 7}) {
            var incomplete = new ArrayList<>(cross);
            incomplete.set(slot, empty);
            var match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, incomplete), level);
            test.assertTrue(match.isEmpty() || !match.orElseThrow().id().equals(recipeId), "Missing ingredient still crafted the remote");
        }
        var old = CraftingInput.of(2, 1, List.of(new ItemStack(Items.COMPASS), redstone));
        var oldMatch = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, old, level);
        test.assertTrue(oldMatch.isEmpty() || !oldMatch.orElseThrow().id().equals(recipeId), "The old compass recipe still works");

        test.succeed();
    }

    @GameTest(template = "combat", batch = "core_remote_switching")
    public static void repeatedRebindingAndDimensionChecks(GameTestHelper test) {
        if (unsupportedClaimsVersion()) { test.succeed(); return; }
        var level = test.getLevel();
        var ordinary = core(test, new BlockPos(3, 8, 3), false);
        var survival = core(test, new BlockPos(5, 8, 3), true);
        var controlPos = test.absolutePos(new BlockPos(7, 8, 3));
        level.setBlockAndUpdate(controlPos, SableLife.REDSTONE_CORE.get().defaultBlockState());
        var control = (HeartBlockEntity) level.getBlockEntity(controlPos);
        var player = new Editor(level, GameType.CREATIVE);
        var elsewhere = new Editor(level.getServer().getLevel(Level.NETHER), GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SableLife.CORE_REMOTE.get()));
        for (int round = 0; round < 20; round++) {
            for (var target : List.of(ordinary, survival, control)) {
                player.setGameMode(target == ordinary ? GameType.CREATIVE : GameType.SURVIVAL);
                test.assertTrue(bind(player, target), "Could not bind one of the three core types");
                player.setPos(target.getBlockPos().getCenter().add(0, 0, 24));
                test.assertTrue(open(player) && player.containerMenu instanceof HeartDashboardMenu
                                && dev.sablelife.item.CoreRemoteItem.hasAuthorizedSession(player, target),
                        "Rebinding opened the wrong core or lost the menu");
                player.closeContainer();
                elsewhere.setItemInHand(InteractionHand.MAIN_HAND, player.getMainHandItem().copy());
                test.assertTrue(!open(elsewhere), "A binding opened a core from another dimension");
            }
        }
        test.succeed();
    }

    @GameTest(template = "combat", batch = "core_remote_permissions")
    public static void bindingAndOpeningRespectCurrentMode(GameTestHelper test) {
        if (unsupportedClaimsVersion()) { test.succeed(); return; }
        var level = test.getLevel();
        var ordinary = core(test, new BlockPos(3, 8, 3), false);
        var survival = core(test, new BlockPos(5, 8, 3), true);
        var player = new Editor(level, GameType.CREATIVE);
        var remote = new ItemStack(SableLife.CORE_REMOTE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        test.assertTrue(bind(player, ordinary), "Creative player could not bind the Heart Core");
        var ordinaryBinding = player.getMainHandItem().copy();

        player.setGameMode(GameType.SURVIVAL);
        test.assertTrue(!open(player), "A Creative binding still opened the Heart Core in Survival");
        test.assertTrue(!bind(player, ordinary), "Survival could rebind the Heart Core");
        test.assertTrue(bind(player, survival), "Survival could not rebind to the Survival Heart Core");
        test.assertTrue(!ItemStack.matches(ordinaryBinding, player.getMainHandItem()), "Rebinding retained the old target");
        test.assertTrue(!bind(player, ordinary), "Survival could replace a valid binding with a forbidden one");

        player.setPos(SableBodyProbe.toWorld(level, survival.getBlockPos().getCenter()).add(0, 0, 24));
        test.assertTrue(open(player) && player.containerMenu instanceof HeartDashboardMenu, "Remote use did not open the dashboard at range");
        var dashboard = (HeartDashboardMenu) player.containerMenu;
        test.assertTrue(dashboard.action(player, survival.modules().revision(), UUID.randomUUID(), "settings", "", 0, "")
                        && player.containerMenu instanceof HeartSettingsMenu && player.containerMenu.stillValid(player),
                "Switching to the settings menu lost remote access");
        player.setGameMode(GameType.ADVENTURE);
        test.assertTrue(!player.containerMenu.stillValid(player), "An open remote menu ignored loss of editing permission");
        player.closeContainer();
        var other = new Editor(level, GameType.ADVENTURE);
        other.setItemInHand(InteractionHand.MAIN_HAND, player.getMainHandItem().copy());
        test.assertTrue(!open(other), "A borrowed remote inherited the binder's editing permission");
        other.setGameMode(GameType.SURVIVAL);
        test.assertTrue(open(other), "An eligible player could not use a borrowed remote");
        other.closeContainer();
        test.succeed();
    }

    @GameTest(template = "combat", batch = "core_remote_identity")
    public static void identitySurvivesAssemblyAndRelease(GameTestHelper test) {
        if (unsupportedClaimsVersion()) { test.succeed(); return; }
        var level = test.getLevel();
        var core = core(test, new BlockPos(3, 8, 3), false);
        var originalPos = core.getBlockPos();
        var player = new Editor(level, GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SableLife.CORE_REMOTE.get()));
        var legacy = core.saveWithFullMetadata(level.registryAccess());
        test.assertTrue(!legacy.hasUUID(HeartBlockEntity.REMOTE_ID_TAG), "Unbound core wrote a remote ID");
        test.assertTrue(bind(player, core), "Could not bind an existing core");
        var boundId = core.remoteId();
        level.setBlockAndUpdate(originalPos.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, originalPos, List.of(originalPos, originalPos.east()),
                new BoundingBox3i(originalPos.getX(), originalPos.getY(), originalPos.getZ(),
                        originalPos.getX() + 1, originalPos.getY(), originalPos.getZ()));
        var moved = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
        test.assertTrue(boundId.equals(moved.remoteId()) && open(player), "Assembly lost the bound core");
        player.closeContainer();
        if (dev.sablelife.compat.photomancy.PhotomancyCompatibility.available()) {
            var blueprint = dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(level,
                    SableBodyProbe.toWorld(level, moved.getBlockPos().getCenter()), List.of(body.getUniqueId()));
            var entities = blueprint.subLevels().stream().flatMap(part -> part.blockEntities().stream()).toList();
            test.assertTrue(!entities.isEmpty() && entities.stream().noneMatch(tag -> tag.hasUUID(HeartBlockEntity.REMOTE_ID_TAG)),
                    "Exported blueprint inherited a Core Remote binding");
        }
        RigidBodyHandle.of(body).teleport(new Vector3d(body.logicalPose().position()).add(0, 0, 24),
                new Quaterniond().rotateY(Math.PI / 2));
        test.assertTrue(open(player) && player.containerMenu.stillValid(player), "The moving core could not be opened remotely");
        player.closeContainer();
        var lifeId = moved.creatureId();
        moved.tryActivate(player);
        test.assertTrue(moved.health().state() == HeartState.ALIVE && CoreRelease.release(moved, player), "Core did not activate and release");
        test.assertTrue(!lifeId.equals(moved.creatureId()) && open(player), "Release broke the binding");
        player.closeContainer();

        var saved = moved.saveWithFullMetadata(level.registryAccess());
        moved.onChunkUnloaded();
        test.assertTrue(!open(player), "A core marked unloaded could still be opened remotely");
        moved.onLoad();
        test.assertTrue(open(player), "Reloading the chunk did not restore the binding index");
        player.closeContainer();
        var corePos = moved.getBlockPos();
        level.removeBlock(corePos, false);
        test.assertTrue(!open(player), "A removed core still opened remotely");
        level.setBlockAndUpdate(corePos, SableLife.HEART.get().defaultBlockState());
        test.assertTrue(!open(player), "An unrelated replacement inherited the old binding");
        var restored = (HeartBlockEntity) level.getBlockEntity(corePos);
        restored.loadWithComponents(saved, level.registryAccess());
        test.assertTrue(boundId.equals(restored.remoteId()) && open(player), "Reloading saved core data lost the remote binding");
        player.closeContainer();
        test.succeed();
    }

    @GameTest(template = "combat", batch = "core_remote_activation")
    public static void SneakUseActivatesOnlyAuthorizedBoundHeartCores(GameTestHelper test) {
        if (unsupportedClaimsVersion()) { test.succeed(); return; }
        var level = test.getLevel();
        var ordinary = core(test, new BlockPos(3, 8, 3), false);
        var survival = core(test, new BlockPos(5, 8, 3), true);
        var player = new Editor(level, GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SableLife.CORE_REMOTE.get()));

        test.assertTrue(bind(player, ordinary), "Creative player could not bind the ordinary core");
        sneakUse(player);
        test.assertTrue(ordinary.health().state() == HeartState.DORMANT, "Remote activated an unassembled core");
        var ordinaryBody = assemble(ordinary, level);
        player.setPos(SableBodyProbe.toWorld(level, ordinaryBody.getBlockPos().getCenter()).add(0, 0, 24));
        player.setGameMode(GameType.SURVIVAL);
        sneakUse(player);
        test.assertTrue(ordinaryBody.health().state() == HeartState.DORMANT, "Survival could activate an ordinary core");
        player.setGameMode(GameType.CREATIVE);
        sneakUse(player);
        test.assertTrue(ordinaryBody.health().state() == HeartState.ALIVE && player.containerMenu == player.inventoryMenu,
                "Remote activation did not start the ordinary core or opened a menu");

        player.setGameMode(GameType.SURVIVAL);
        test.assertTrue(bind(player, survival), "Could not rebind the Survival Heart Core");
        var survivalBody = assemble(survival, level);
        player.setPos(SableBodyProbe.toWorld(level, survivalBody.getBlockPos().getCenter()).add(0, 0, 24));
        sneakUse(player);
        test.assertTrue(survivalBody.health().state() == HeartState.DORMANT, "A Survival Heart Core activated without energy");
        var fuel = new ItemStack(Items.COPPER_INGOT, 64);
        test.assertTrue(survivalBody.depositSurvivalTokens(player, fuel), "Could not fuel the Survival Heart Core");
        long before = survivalBody.survivalTokenBalance();
        sneakUse(player);
        long after = survivalBody.survivalTokenBalance();
        test.assertTrue(survivalBody.health().state() == HeartState.ALIVE && after < before,
                "Survival remote activation did not consume energy");
        sneakUse(player);
        test.assertTrue(survivalBody.survivalTokenBalance() == after, "Repeated activation consumed energy again");
        test.assertTrue(open(player), "Regular right-click stopped opening the bound core");
        player.closeContainer();
        test.succeed();
    }

    @GameTest(template = "combat", batch = "core_remote_claims")
    public static void remoteHonorsInstalledAeroClaims(GameTestHelper test) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("aeroclaims")) { test.succeed(); return; }
        var level = test.getLevel();
        var core = core(test, new BlockPos(3, 8, 3), true);
        var player = new Editor(level, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SableLife.CORE_REMOTE.get()));
        if (unsupportedClaimsVersion()) {
            test.assertTrue(!bind(player, core), "An unsupported claim version allowed a remote binding");
            test.succeed();
            return;
        }

        var owner = new Editor(level, GameType.SURVIVAL);
        var claimData = Class.forName("com.mapter.aeroclaims.claim.ClaimSavedData");
        var savedData = claimData.getMethod("get", ServerLevel.class).invoke(null, level);
        @SuppressWarnings("unchecked")
        var claims = (List<Object>) claimData.getMethod("getClaims").invoke(savedData);
        var original = new ArrayList<>(claims);
        var invalidateIndex = claimData.getMethod("invalidateIndex");
        var claimType = Class.forName("com.mapter.aeroclaims.claim.Claim");
        var constructor = claimType.getConstructor(BlockPos.class, UUID.class, Set.class,
                boolean.class, boolean.class, boolean.class, boolean.class);
        var pos = core.getBlockPos();
        try {
            test.assertTrue(bind(player, core) && open(player), "Unclaimed Survival Heart Core could not open remotely");
            var claim = constructor.newInstance(pos, owner.getUUID(), Set.of(pos), true, false, false, false);
            claims.add(claim);
            invalidateIndex.invoke(savedData);
            test.assertTrue(!player.containerMenu.stillValid(player), "A new claim did not invalidate an open remote menu");
            player.closeContainer();
            test.assertTrue(!open(player) && !bind(player, core), "Claimed core accepted an unauthorized use or bind");
            owner.setItemInHand(InteractionHand.MAIN_HAND, player.getMainHandItem().copy());
            test.assertTrue(open(owner), "The claim owner could not open the same core");
            owner.closeContainer();
        } finally {
            claims.clear();
            claims.addAll(original);
            invalidateIndex.invoke(savedData);
        }
        test.succeed();
    }

    private static boolean unsupportedClaimsVersion() {
        return ModList.get().getModContainerById("aeroclaims")
                .map(mod -> !mod.getModInfo().getVersion().toString().equals("0.9.5")).orElse(false);
    }

    private static HeartBlockEntity core(GameTestHelper test, BlockPos offset, boolean survival) {
        var pos = test.absolutePos(offset);
        var block = survival ? SableLife.SURVIVAL_LIFE_CORE.get() : SableLife.HEART.get();
        test.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        return (HeartBlockEntity) test.getLevel().getBlockEntity(pos);
    }

    private static boolean bind(Editor player, HeartBlockEntity core) {
        var level = player.serverLevel();
        var pos = core.getBlockPos();
        player.setPos(SableBodyProbe.toWorld(level, pos.getCenter()).add(0, 0, 1.5));
        player.setShiftKeyDown(true);
        var hit = new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
        var result = player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        player.setShiftKeyDown(false);
        return result.consumesAction();
    }

    private static boolean open(Editor player) {
        return player.gameMode.useItem(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND).consumesAction();
    }

    private static void sneakUse(Editor player) {
        player.setShiftKeyDown(true);
        open(player);
        player.setShiftKeyDown(false);
    }

    private static HeartBlockEntity assemble(HeartBlockEntity core, ServerLevel level) {
        var pos = core.getBlockPos();
        level.setBlockAndUpdate(pos.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos, pos.east()),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY(), pos.getZ()));
        return (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    }

    private static final class Editor extends FakePlayer {
        private int nextMenu;

        private Editor(ServerLevel level, GameType mode) {
            super(level, new GameProfile(UUID.randomUUID(), "RemoteTester"));
            setGameMode(mode);
        }

        @Override
        public OptionalInt openMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraData) {
            closeContainer();
            containerMenu = provider.createMenu(++nextMenu, getInventory(), this);
            return OptionalInt.of(nextMenu);
        }
    }
}
