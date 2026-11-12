package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.sablelife.module.HeartDashboardMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import java.util.List;

@EventBusSubscriber(modid = "sablelife_tests")
public final class CoreRemoteNetworkReplay {
    static final boolean STABILITY = Boolean.getBoolean("sablelife.coreRemoteStability");
    static final boolean ACTIVATION = Boolean.getBoolean("sablelife.coreRemoteNetworkActivation");
    static final int CYCLES = Integer.getInteger("sablelife.coreRemoteCycles", 75);
    static final int UNLOADED_DISTANCE = 256;
    static final BlockPos ORDINARY = new BlockPos(STABILITY ? 518 : 6, 160, 6);
    static final BlockPos SURVIVAL = ORDINARY.east(4);
    private static int phase;
    private static int ticks;
    private static int transition;
    private static HeartBlockEntity ordinary;
    private static HeartBlockEntity survival;
    private static int creativeCycles;
    private static int survivalCycles;
    private static long survivalEnergyBeforeActivation;

    @SubscribeEvent
    public static void menuOpened(PlayerContainerEvent.Open event) {
        if (!STABILITY || phase != 5 || !(event.getContainer() instanceof HeartDashboardMenu)) return;
        String name = event.getEntity().getGameProfile().getName();
        if (name.equals("RemoteCreative")) creativeCycles++;
        if (name.equals("RemoteSurvival")) survivalCycles++;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("sablelife.coreRemoteNetworkServer") || phase == 4) return;
        var server = event.getServer();
        if (++ticks > 4000) throw new IllegalStateException("Core Remote network replay timed out in phase " + phase);
        var creative = server.getPlayerList().getPlayerByName("RemoteCreative");
        var player = server.getPlayerList().getPlayerByName("RemoteSurvival");
        if (creative == null || player == null) return;
        var level = server.overworld();
        boolean restart = Boolean.getBoolean("sablelife.coreRemoteNetworkRestart");
        if (phase == 0) {
            if (restart) {
                if (!(level.getBlockEntity(ORDINARY) instanceof HeartBlockEntity savedOrdinary) || savedOrdinary.remoteId() == null
                        || !(level.getBlockEntity(SURVIVAL) instanceof HeartBlockEntity savedSurvival) || savedSurvival.remoteId() == null) {
                    throw new IllegalStateException("Saved cores lost their remote IDs during restart");
                }
                creative.setGameMode(GameType.CREATIVE);
                creative.setNoGravity(true);
                player.setNoGravity(true);
                move(creative, ORDINARY, 24);
                move(player, SURVIVAL, 24);
                phase = 2;
                return;
            }
            level.setBlockAndUpdate(ORDINARY, SableLife.HEART.get().defaultBlockState());
            level.setBlockAndUpdate(SURVIVAL, SableLife.SURVIVAL_LIFE_CORE.get().defaultBlockState());
            ordinary = (HeartBlockEntity) level.getBlockEntity(ORDINARY);
            survival = (HeartBlockEntity) level.getBlockEntity(SURVIVAL);
            prepare(creative, ORDINARY, GameType.CREATIVE);
            prepare(player, SURVIVAL, GameType.SURVIVAL);
            phase = 1;
        } else if (phase == 1 && ordinary.remoteId() != null && survival.remoteId() != null) {
            if (STABILITY && !player.getRecipeBook().contains(ResourceLocation.fromNamespaceAndPath("sablelife", "core_remote"))) {
                throw new IllegalStateException("Obtaining a lever did not unlock the Core Remote recipe");
            }
            if (STABILITY) System.out.println("CORE_REMOTE_NETWORK_RECIPE_UNLOCK PASS");
            if (ACTIVATION) {
                ordinary = assemble(level, ORDINARY);
                survival = assemble(level, SURVIVAL);
                if (!survival.depositSurvivalTokens(player, new ItemStack(Items.COPPER_INGOT, 64))) {
                    throw new IllegalStateException("Could not fuel the Survival Heart Core");
                }
                survivalEnergyBeforeActivation = survival.survivalTokenBalance();
            }
            move(creative, ORDINARY, 24);
            move(player, SURVIVAL, 24);
            phase = STABILITY ? 5 : 2;
        } else if (phase == 5) {
            if (creativeCycles >= CYCLES && survivalCycles >= CYCLES
                    && creative.containerMenu == creative.inventoryMenu && player.containerMenu == player.inventoryMenu) {
                System.out.println("CORE_REMOTE_NETWORK_CYCLES PASS " + creativeCycles + "/" + survivalCycles);
                move(creative, ORDINARY, UNLOADED_DISTANCE);
                move(player, SURVIVAL, UNLOADED_DISTANCE);
                transition = ticks;
                phase = 6;
            }
        } else if (phase == 6 && !level.hasChunkAt(ORDINARY) && !level.hasChunkAt(SURVIVAL)) {
            System.out.println("CORE_REMOTE_NETWORK_CHUNK_UNLOADED");
            transition = ticks;
            phase = 7;
        } else if (phase == 6 && ticks - transition >= 240) {
            throw new IllegalStateException("Core chunk stayed loaded after both players left: "
                    + creative.blockPosition() + "/" + player.blockPosition() + ", menus "
                    + creative.containerMenu.getClass().getSimpleName() + "/" + player.containerMenu.getClass().getSimpleName());
        } else if (phase == 7) {
            if (creative.containerMenu != creative.inventoryMenu || player.containerMenu != player.inventoryMenu
                    || level.hasChunkAt(ORDINARY) || level.hasChunkAt(SURVIVAL)) {
                throw new IllegalStateException("A remote request reopened or loaded an unloaded core");
            }
            if (ticks - transition >= 100) {
                System.out.println("CORE_REMOTE_NETWORK_UNLOADED_USE PASS");
                move(creative, ORDINARY, 24);
                move(player, SURVIVAL, 24);
                phase = 2;
            }
        } else if (phase == 2 && creative.containerMenu instanceof HeartDashboardMenu && player.containerMenu instanceof HeartDashboardMenu) {
            if (ACTIVATION && (ordinary.health().state() != HeartState.ALIVE || survival.health().state() != HeartState.ALIVE
                    || survival.survivalTokenBalance() >= survivalEnergyBeforeActivation)) {
                throw new IllegalStateException("Remote activation or Survival Energy charge failed over the network");
            }
            if (restart) {
                System.out.println("CORE_REMOTE_NETWORK_RESTART PASS");
                if (STABILITY) {
                    move(creative, ORDINARY, UNLOADED_DISTANCE);
                    move(player, SURVIVAL, UNLOADED_DISTANCE);
                    transition = ticks;
                    phase = 8;
                } else {
                    phase = 4;
                    server.halt(false);
                }
                return;
            }
            if (STABILITY) System.out.println("CORE_REMOTE_NETWORK_RELOADED PASS");
            creative.setGameMode(GameType.SURVIVAL);
            transition = ticks;
            phase = 3;
        } else if (phase == 3 && ticks - transition >= 60) {
            if (creative.containerMenu instanceof HeartDashboardMenu || !(player.containerMenu instanceof HeartDashboardMenu)) {
                throw new IllegalStateException("Game-mode change did not close just the unauthorized remote editor");
            }
            System.out.println("CORE_REMOTE_NETWORK_SERVER PASS");
            phase = 4;
            server.halt(false);
        } else if (phase == 8) {
            if (!level.hasChunkAt(ORDINARY) && !level.hasChunkAt(SURVIVAL)
                    && creative.containerMenu == creative.inventoryMenu && player.containerMenu == player.inventoryMenu) {
                System.out.println("CORE_REMOTE_NETWORK_OPEN_MENU_UNLOAD PASS");
                phase = 4;
                server.halt(false);
            } else if (ticks - transition >= 240) {
                throw new IllegalStateException("An open remote dashboard remained after both players left the core chunk");
            }
        }
    }

    private static void prepare(ServerPlayer player, BlockPos core, GameType mode) {
        player.setGameMode(mode);
        player.setNoGravity(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SableLife.CORE_REMOTE.get()));
        if (STABILITY) player.getInventory().add(new ItemStack(Items.LEVER));
        move(player, core, 2);
    }

    private static HeartBlockEntity assemble(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos, pos.east()),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY(), pos.getZ()));
        return (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    }

    private static void move(ServerPlayer player, BlockPos core, int distance) {
        player.serverLevel().setBlockAndUpdate(core.south(distance).below(), Blocks.STONE.defaultBlockState());
        player.connection.teleport(core.getX() + 0.5, core.getY(), core.getZ() + distance + 0.5, 180, 0);
    }
}
