package dev.sablelife.testing;

import dev.sablelife.client.HeartDashboardScreen;
import dev.sablelife.client.ModulePickerScreen;
import dev.sablelife.module.HeartDashboardMenu;
import dev.sablelife.module.DatapackModules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "sablelife_tests", value = Dist.CLIENT)
public final class AddonModuleNetworkClient {
    private static final String ID = DatapackAlarmScenario.ENABLED ? DatapackAlarmScenario.ALARM
            : "sablelife_example_addon:marker_chime";
    private static final String ROLE = System.getProperty("sablelife.addonNetworkRole", "");
    private static final boolean INTEGRATED = ROLE.equals("singleplayer");
    private static boolean connecting;
    private static boolean joined;
    private static boolean picked;
    private static int ticks;
    private static int phase;
    private static int chimes;
    private static int loggedPhase = -1;
    private static int reloadPhase;

    @SubscribeEvent
    public static void sound(PlaySoundEvent event) {
        if (!ROLE.isEmpty() && event.getOriginalSound().getLocation().equals(SoundEvents.NOTE_BLOCK_CHIME.value().getLocation())) chimes++;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (ROLE.isEmpty()) return;
        var mc = Minecraft.getInstance();
        try {
            if (++ticks > 3600) throw new IllegalStateException("Timed out in phase " + phase);
            if (phase != loggedPhase) {
                loggedPhase = phase;
                System.out.println("ADDON_MODULE_NETWORK_CLIENT " + ROLE + " phase=" + phase);
            }
            if (!connecting && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                connecting = true;
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                if (INTEGRATED) {
                    var settings = new LevelSettings("Addon module test", GameType.CREATIVE, false,
                            Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel("addon-module-test", settings, new WorldOptions(1234, false, false),
                            access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                    .value().createWorldDimensions(), mc.screen);
                } else {
                    String address = "127.0.0.1:" + Integer.getInteger("sablelife.addonNetworkPort", 25592);
                    ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(address),
                            new ServerData("Addon module integration test", address, ServerData.Type.OTHER), false, null);
                }
            }
            if (mc.player != null) joined = true;
            if (joined && mc.player == null) {
                int expectedChimes = DatapackAlarmScenario.ENABLED ? 3 : 1;
                if (!picked || chimes != expectedChimes || !ROLE.equals("observer") && phase != 8
                        || DatapackAlarmScenario.ENABLED && reloadPhase != 3) {
                    throw new IllegalStateException("Incomplete client flow. Picker=" + picked + " phase=" + phase
                            + " chimes=" + chimes + " reloadPhase=" + reloadPhase);
                }
                System.out.println("ADDON_MODULE_NETWORK_CLIENT " + ROLE + " PASS");
                mc.stop();
                return;
            }
            if (mc.player != null) {
                advance(mc);
                if (DatapackAlarmScenario.ENABLED) verifyDatapackSync(mc);
            }
        } catch (RuntimeException failure) {
            failure.printStackTrace();
            System.out.println("ADDON_MODULE_NETWORK_CLIENT " + ROLE + " FAIL");
            mc.stop();
        }
    }

    private static void advance(Minecraft mc) {
        boolean author = !ROLE.equals("observer");
        if (phase == 1 && mc.screen instanceof ModulePickerScreen picker) {
            var search = picker.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
            search.setValue(ID);
            String label = DatapackAlarmScenario.ENABLED ? (author ? "Alarm" : "警报器")
                    : (author ? "Marker Chime" : "标记点钟声");
            Button example = button(picker, label);
            if (example == null) throw new IllegalStateException("Example module name missing in picker for " + ROLE);
            if (DatapackAlarmScenario.ENABLED) {
                var info = DatapackModules.clientPresentation(ID);
                String locale = mc.getLanguageManager().getSelected();
                if (example.getTooltip() == null || info == null || !info.optionLabel("volume").forLocale(locale)
                        .equals(author ? "Alarm volume" : "警报音量")) {
                    throw new IllegalStateException("Missing localized alarm tooltip or option");
                }
            }
            picked = true;
            if (author) {
                example.onPress();
                button(picker, I18n.get("module_picker.sablelife.load")).onPress();
            } else {
                picker.onClose();
            }
            phase = 2;
            return;
        }
        if (phase == 7 && HeartDashboardScreen.marking && mc.screen == null) {
            // Hidden test windows can skip the renderer's per-frame crosshair update.
            var picked = mc.player.pick(mc.player.blockInteractionRange(), 1, false);
            if (picked instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            } else if (ticks % 100 == 0) {
                System.out.println("ADDON_MODULE_NETWORK waiting for ray hit from " + mc.player.getEyePosition()
                        + " toward " + mc.player.getLookAngle());
            }
            return;
        }
        if (!(mc.screen instanceof HeartDashboardScreen dashboard) || !(mc.player.containerMenu instanceof HeartDashboardMenu menu)) return;
        var snapshot = menu.snapshot();
        if (!snapshot.getBoolean("Ready")) return;
        if (phase == 0) {
            button(dashboard, I18n.get("dashboard.sablelife.add_short")).onPress();
            phase = 1;
            return;
        }
        if (!author) return;
        var row = snapshot.getList("Modules", 10).stream().map(CompoundTag.class::cast)
                .filter(module -> ID.equals(module.getString("Type"))).findFirst().orElse(null);
        if (row == null) return;
        switch (phase) {
            case 2 -> {
                var volume = dashboard.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .filter(box -> box.visible && box.getValue().equals("1.0")).findFirst().orElse(null);
                if (volume != null) {
                    enter(dashboard, volume, "1.75");
                    phase = 3;
                }
            }
            case 3 -> {
                if (row.getCompound("Settings").getDouble("addon_volume") == 1.75 && click(dashboard, "trigger_0")) phase = 4;
            }
            case 4 -> {
                if (row.getInt("Trigger") == 1 && click(dashboard, "trigger_1")) phase = 5;
            }
            case 5 -> {
                if (row.getInt("Trigger") != 2) return;
                var channel = dashboard.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .filter(box -> box.visible && box.getMessage().getString().equals(I18n.get("dashboard.sablelife.channel")))
                        .findFirst().orElse(null);
                if (channel != null) {
                    enter(dashboard, channel, "2");
                    phase = 6;
                }
            }
            case 6 -> {
                if (row.getInt("Channel") == 1 && click(dashboard, "mark")) phase = 7;
            }
            case 7 -> {
                if (row.getString("Status").equals("valid")) phase = 8;
            }
        }
    }

    private static void verifyDatapackSync(Minecraft mc) {
        if (!picked || DatapackModules.clientPresentation(DatapackAlarmScenario.CHECKPOINT) == null) return;
        boolean present = DatapackModules.clientPresentation(ID) != null;
        if (reloadPhase == 0 && present && chimes >= 1) observed(1);
        else if (reloadPhase == 1 && !present && chimes >= 2) {
            if (!ROLE.equals("observer") && mc.player.containerMenu instanceof HeartDashboardMenu menu
                    && (menu.snapshot().getList("AvailableTypes", 8).stream().anyMatch(type -> ID.equals(type.getAsString()))
                        || !row(menu).getString("Status").equals("unknown"))) return;
            observed(2);
        } else if (reloadPhase == 2 && present && chimes == 3) {
            if (!ROLE.equals("observer")) {
                if (!(mc.player.containerMenu instanceof HeartDashboardMenu menu)) return;
                var restored = row(menu);
                if (!restored.getString("Status").equals("valid")) return;
                if (restored.getCompound("Settings").getDouble("addon_volume") != 1.75) {
                    throw new IllegalStateException("Client lost the saved volume");
                }
            }
            observed(3);
        }
    }

    private static CompoundTag row(HeartDashboardMenu menu) {
        return menu.snapshot().getList("Modules", 10).stream().map(CompoundTag.class::cast)
                .filter(module -> ID.equals(module.getString("Type"))).findFirst().orElseThrow();
    }

    private static void observed(int state) {
        DatapackAlarmScenario.observe(ROLE, state);
        reloadPhase = state;
        System.out.println("DATAPACK_ALARM client observed " + ROLE + " state=" + state + " chimes=" + chimes);
    }

    private static void enter(Screen screen, EditBox box, String value) {
        screen.setFocused(box);
        box.setFocused(true);
        box.setValue(value);
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    }

    private static boolean click(Screen screen, String key) {
        Button button = button(screen, I18n.get("dashboard.sablelife." + key));
        if (button == null || !button.active) return false;
        button.onPress();
        return true;
    }

    private static Button button(Screen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.visible && button.getMessage().getString().equals(label)).findFirst().orElse(null);
    }
}
