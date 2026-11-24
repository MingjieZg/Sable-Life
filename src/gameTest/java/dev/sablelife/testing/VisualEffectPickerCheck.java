package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.client.HeartDashboardScreen;
import dev.sablelife.client.LifeUiStyle;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.VirtualModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;

@EventBusSubscriber(modid = "sablelife_tests", value = Dist.CLIENT)
public final class VisualEffectPickerCheck {
    private static final List<String> EFFECTS = List.of("shock_ring", "shock_radial", "visual_gather", "visual_mechanical",
            "visual_shield", "visual_jet", "visual_fissures", "visual_echo", "visual_symbols", "visual_deployable_shield");
    private static final int DEPLOYABLE_SHIELD = EFFECTS.size() - 1;
    private static int phase, ticks;
    private static int loggedPhase = -1;
    private static BlockPos pos;
    private static CompletableFuture<Void> reload;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post ignored) throws Exception {
        if (!Boolean.getBoolean("sablelife.visualEffectPickerCheck")) return;
        var mc = Minecraft.getInstance();
        if (++ticks > 1600) throw new AssertionError("Visual effect test timed out in phase " + phase);
        if (phase != loggedPhase) {
            loggedPhase = phase;
            System.out.println("VISUAL_EFFECT_PICKER phase=" + phase);
        }
        if (phase == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            phase++;
            mc.options.pauseOnLostFocus = false;
            GLFW.glfwHideWindow(mc.getWindow().getWindow());
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
            while (LifeUiStyle.density() != 3) LifeUiStyle.cycle();
            var settings = new LevelSettings("Effect picker", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("effect-picker", settings, new WorldOptions(1234, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.screen instanceof TitleScreen) return;
        if (phase == 1 && mc.screen == null && mc.level != null) {
            phase++;
            pos = mc.player.blockPosition().east(2);
            var server = mc.getSingleplayerServer();
            var playerId = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var level = player.serverLevel();
                level.setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
                var core = (HeartBlockEntity) level.getBlockEntity(pos);
                core.modules().add("visual_shockwave").edit("shockPattern", 1, "");
            });
        }
        if (phase == 2 && mc.level.getBlockState(pos).is(SableLife.HEART.get())) {
            phase++;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(pos.getCenter(), Direction.UP, pos, false));
        }
        if (phase == 3 && mc.screen instanceof HeartDashboardScreen dashboard
                && dashboard.getMenu().snapshot().getBoolean("Ready")) {
            phase++;
            open(dashboard, 1);
            verify(mc.screen, 1, false);
            click(mc.screen, Component.translatable("dashboard.sablelife.back").getString(), 0);
            if (mc.screen != dashboard || pattern(dashboard) != 1) throw new AssertionError("Back did not cancel");
            click(dashboard, name(1) + " …", 1);
            verify(mc.screen, 1, false);
            mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            if (mc.screen != dashboard || pattern(dashboard) != 1) throw new AssertionError("Escape did not cancel");
            click(dashboard, name(1) + " …", 0);
        } else if (phase == 4) {
            capture(mc, "effect-picker-en");
            click(mc.screen, name(DEPLOYABLE_SHIELD), 0);
            phase++;
        } else if (phase == 5 && mc.screen instanceof HeartDashboardScreen dashboard && pattern(dashboard) == DEPLOYABLE_SHIELD) {
            phase++;
            mc.getLanguageManager().setSelected("zh_cn");
            mc.options.languageCode = "zh_cn";
            mc.options.guiScale().set(4);
            mc.resizeDisplay();
            while (LifeUiStyle.density() != 0) LifeUiStyle.cycle();
            reload = mc.reloadResourcePacks();
        } else if (phase == 6 && reload.isDone() && mc.getOverlay() == null && mc.screen instanceof HeartDashboardScreen dashboard) {
            reload.join();
            open(dashboard, DEPLOYABLE_SHIELD);
            verify(mc.screen, DEPLOYABLE_SHIELD, true);
            phase++;
        } else if (phase == 7) {
            capture(mc, "effect-picker-zh");
            click(mc.screen, name(0), 0);
            phase++;
        } else if (phase == 8 && mc.screen instanceof HeartDashboardScreen dashboard && pattern(dashboard) == 0) {
            phase++;
            verifyExpandedList(mc, dashboard);
            System.out.println("VISUAL_EFFECT_PICKER_PASS: live core, both locales, page boundaries, selection, Back/Escape, server edits");
            mc.stop();
        }
    }

    private static void open(HeartDashboardScreen screen, int selected) {
        String label = name(selected) + " …";
        for (int page = 0; page < 8; page++) {
            if (buttons(screen).stream().anyMatch(b -> b.getMessage().getString().equals(label))) {
                click(screen, label, 0);
                return;
            }
            click(screen, "›", 0, true);
        }
        throw new AssertionError("No effect picker button for " + label);
    }

    private static void verify(Screen screen, int selected, boolean chinese) {
        String title = chinese ? "选择视觉特效" : "Choose a visual effect";
        if (!screen.getTitle().getString().equals(title)) throw new AssertionError("Incorrect picker title");
        var options = buttons(screen).subList(1, 1 + EFFECTS.size());
        verifyPager(screen, false, false);
        for (int i = 0; i < options.size(); i++) {
            var option = options.get(i);
            String expected = (i == selected ? "✓ " : "") + name(i);
            if (!option.getMessage().getString().equals(expected) || expected.contains("dashboard.sablelife"))
                throw new AssertionError("Unexpected option " + option.getMessage().getString());
            if (i % 2 == 1 && (option.getY() != options.get(i - 1).getY() || option.getX() <= options.get(i - 1).getRight()))
                throw new AssertionError("Misaligned effect row");
            if (i >= 2 && (option.getX() != options.get(i - 2).getX() || option.getY() <= options.get(i - 2).getBottom()))
                throw new AssertionError("Misaligned effect column");
        }
    }

    private static void verifyExpandedList(Minecraft mc, Screen parent) throws ReflectiveOperationException {
        var options = new ArrayList<>(EFFECTS);
        options.addAll(EFFECTS.subList(0, 3));
        var pickerClass = Class.forName("dev.sablelife.client.VisualEffectPickerScreen");
        var constructor = pickerClass.getDeclaredConstructor(Screen.class, List.class, int.class, IntConsumer.class);
        constructor.setAccessible(true);
        int[] chosen = {-1};
        IntConsumer choose = index -> chosen[0] = index;
        int repeatedOption = 2;
        int selected = EFFECTS.size() + repeatedOption;
        var picker = (Screen) constructor.newInstance(parent, options, selected, choose);
        mc.setScreen(picker);
        verifyPager(picker, true, false);
        if (buttons(picker).stream().noneMatch(button -> button.getMessage().getString().equals("✓ " + name(repeatedOption)))) {
            throw new AssertionError("Picker did not open on the selected effect's page");
        }
        click(picker, "‹", 0);
        verifyPager(picker, false, true);
        click(picker, "›", 0);
        verifyPager(picker, true, false);
        click(picker, "✓ " + name(repeatedOption), 0);
        if (chosen[0] != selected || mc.screen != parent) throw new AssertionError("Second page returned the wrong effect");
    }

    private static void verifyPager(Screen screen, boolean previousEnabled, boolean nextEnabled) {
        var previous = pagerButton(screen, "‹");
        var next = pagerButton(screen, "›");
        if (previous.active != previousEnabled || next.active != nextEnabled
                || previous.getY() != next.getY() || previous.getBottom() > screen.height) {
            throw new AssertionError("Incorrect pager controls or alignment");
        }
    }

    private static Button pagerButton(Screen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.visible && button.getMessage().getString().equals(label))
                .findFirst().orElseThrow();
    }

    private static void capture(Minecraft mc, String name) throws Exception {
        var folder = mc.gameDirectory.toPath().resolve("screenshots");
        Files.createDirectories(folder);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(folder.resolve(name + ".png"));
        }
    }

    private static String name(int pattern) {
        return Component.translatable("dashboard.sablelife.choice_" + EFFECTS.get(pattern)).getString();
    }

    private static double pattern(HeartDashboardScreen screen) {
        return VirtualModule.load(screen.getMenu().snapshot().getList("Modules", 10).getCompound(0)).value("shockPattern");
    }

    private static List<Button> buttons(Screen screen) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.visible && b.active).toList();
    }

    private static void click(Screen screen, String label, int mouseButton) {
        click(screen, label, mouseButton, false);
    }

    private static void click(Screen screen, String label, int mouseButton, boolean rightmost) {
        var targets = buttons(screen).stream().filter(b -> b.getMessage().getString().equals(label)).toList();
        var target = targets.isEmpty() ? null : rightmost ? targets.getLast() : targets.getFirst();
        if (target == null || !screen.mouseClicked(target.getX() + target.getWidth() / 2.0,
                target.getY() + target.getHeight() / 2.0, mouseButton)) throw new AssertionError("Could not click " + label);
    }
}
