package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartSettingsMenu;
import dev.sablelife.module.HeartDashboardMenu;
import dev.sablelife.module.ModuleNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

@EventBusSubscriber(modid = "sablelife_tests", value = Dist.CLIENT)
public final class CoreRemoteNetworkClient {
    private static boolean connecting;
    private static boolean ready;
    private static int ticks;
    private static int lastUse;
    private static int cycles;
    private static int unloadedAttempts;
    private static boolean openingSettings;
    private static boolean traveledAway;
    private static boolean activationAttempted;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var role = System.getProperty("sablelife.coreRemoteRole");
        if (role == null) return;
        var mc = Minecraft.getInstance();
        if (++ticks > 3600) throw new IllegalStateException("Core Remote client replay timed out");
        if (!connecting && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            connecting = true;
            var address = "127.0.0.1:" + Integer.getInteger("sablelife.coreRemotePort", 25594);
            ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(address),
                    new ServerData("Core Remote replay", address, ServerData.Type.OTHER), false, null);
        }
        if (mc.player == null || mc.gameMode == null) {
            if (ready) mc.stop();
            return;
        }
        var player = mc.player;
        var stack = player.getMainHandItem();
        if (!stack.is(SableLife.CORE_REMOTE.get())) return;
        boolean creative = role.equals("Creative");
        var pos = creative ? CoreRemoteNetworkReplay.ORDINARY : CoreRemoteNetworkReplay.SURVIVAL;
        boolean bound = !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).isEmpty();
        boolean stability = CoreRemoteNetworkReplay.STABILITY;
        boolean runCycles = stability && !Boolean.getBoolean("sablelife.coreRemoteNetworkRestart");
        double distance = player.distanceToSqr(pos.getCenter());
        boolean away = distance > 200 * 200;
        if (away) traveledAway = true;
        if (!bound && ticks - lastUse >= 10 && player.distanceToSqr(pos.getCenter()) < 16
                && mc.level.getBlockState(pos).getBlock() instanceof dev.sablelife.heart.HeartBlock) {
            var connection = player.connection;
            player.setShiftKeyDown(true);
            connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(pos.getCenter(), Direction.UP, pos, false));
            connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
            player.setShiftKeyDown(false);
            lastUse = ticks;
        } else if (bound && distance > 100 && ticks - lastUse >= (stability ? 8 : 20)
                && player.containerMenu == player.inventoryMenu
                && (!runCycles || cycles < CoreRemoteNetworkReplay.CYCLES || traveledAway)) {
            if (CoreRemoteNetworkReplay.ACTIVATION && !activationAttempted) {
                player.setShiftKeyDown(true);
                player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                player.setShiftKeyDown(false);
                activationAttempted = true;
            } else {
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            }
            lastUse = ticks;
            if (stability && away) unloadedAttempts++;
            if (creative && ready && mc.gameMode.getPlayerMode() == GameType.SURVIVAL) {
                System.out.println("CORE_REMOTE_NETWORK_CLIENT Creative retried after switching to Survival");
            }
        }
        if (!ready && player.containerMenu instanceof HeartDashboardMenu menu && menu.snapshot().getBoolean("Ready")) {
            var expected = creative ? "Core Remote" : "\u6838\u5fc3\u9065\u63a7\u5668";
            if (!stack.getHoverName().getString().equals(expected)
                    || menu.snapshot().getBoolean("SurvivalCore") == creative) {
                throw new IllegalStateException("Incorrect localized item name or wrong remote menu for " + role);
            }
            if (Boolean.getBoolean("sablelife.coreRemoteCheckItemPose")) checkItemPose(mc, stack);
            ready = true;
            System.out.println("CORE_REMOTE_NETWORK_CLIENT " + role + " PASS");
        }
        if (runCycles && ready && !traveledAway && cycles < CoreRemoteNetworkReplay.CYCLES) {
            if (player.containerMenu instanceof HeartDashboardMenu menu && menu.snapshot().getBoolean("Ready")) {
                if (cycles % 5 == 0) {
                    if (!openingSettings) {
                        openingSettings = true;
                        PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,
                                menu.snapshot().getInt("Revision"), UUID.randomUUID(), "settings", "", 0, ""));
                    }
                } else {
                    closeCycle(mc, role);
                }
            } else if (openingSettings && player.containerMenu instanceof HeartSettingsMenu) {
                openingSettings = false;
                closeCycle(mc, role);
            }
        }
        if (stability && traveledAway && !away && unloadedAttempts > 0
                && player.containerMenu instanceof HeartDashboardMenu) {
            System.out.println("CORE_REMOTE_NETWORK_CLIENT_RELOAD " + role + " PASS " + unloadedAttempts + " out-of-range attempts");
            unloadedAttempts = 0;
        }
    }

    private static void checkItemPose(Minecraft mc, ItemStack stack) {
        var renderer = mc.getItemRenderer();
        var remote = renderer.getModel(stack, mc.level, mc.player, 0);
        var paper = renderer.getModel(new ItemStack(Items.PAPER), mc.level, mc.player, 0);
        if (!remote.getParticleIcon().contents().name().toString().equals("sablelife:item/core_remote")) {
            throw new IllegalStateException("Core Remote did not load its custom sprite");
        }
        for (var hand : new ItemDisplayContext[] {ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND}) {
            var actual = remote.getTransforms().getTransform(hand);
            var expected = paper.getTransforms().getTransform(hand);
            if (!actual.rotation.equals(expected.rotation) || !actual.translation.equals(expected.translation)
                    || !actual.scale.equals(expected.scale) || !actual.rightRotation.equals(expected.rightRotation)) {
                throw new IllegalStateException("Core Remote does not use the standard item pose for " + hand);
            }
        }
        System.out.println("CORE_REMOTE_ITEM_POSE PASS");
    }

    private static void closeCycle(Minecraft mc, String role) {
        mc.player.closeContainer();
        lastUse = ticks;
        if (++cycles == CoreRemoteNetworkReplay.CYCLES) {
            System.out.println("CORE_REMOTE_NETWORK_CLIENT_CYCLES " + role + " PASS " + cycles);
        }
    }
}
