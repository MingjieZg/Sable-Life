package dev.sablelife.testing;

import com.mojang.blaze3d.platform.NativeImage;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.nio.file.Files;
import java.util.List;

@EventBusSubscriber(modid = "sablelife_tests", value = Dist.CLIENT)
public final class RedstoneCoreClientPreview {
    private static int ticks;
    private static boolean opened;
    private static boolean worldRequested;
    private static boolean placing;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("sablelife.redstonePreview")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        if (!opened) {
            if (mc.screen == null) return;
            checkBakedModels(mc);
            var items = items();
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
            mc.setScreen(new Screen(Component.literal("Sable Life core and tool models")) {
                @Override
                public void render(GuiGraphics graphics, int x, int y, float partial) {
                    graphics.fill(0, 0, width, height, 0xff242430);
                    graphics.drawCenteredString(font, title, width / 2, 35, 0xffffff);
                    for (int i = 0; i < items.size(); i++) {
                        graphics.pose().pushPose();
                        graphics.pose().translate(width / 2 - 236 + i * 68, height / 2 - 32, 0);
                        graphics.pose().scale(4, 4, 4);
                        graphics.renderItem(items.get(i), 0, 0);
                        graphics.pose().popPose();
                    }
                }
            });
            opened = true;
            return;
        }
        if (!worldRequested) {
            if (++ticks < 15) return;
            screenshot(mc, "core-assets.png");
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("core-assets-" + System.currentTimeMillis(),
                    new LevelSettings("Core model check", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(823, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            worldRequested = true;
            ticks = 0;
            return;
        }
        var server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) return;
        if (!placing) {
            placing = true;
            server.execute(() -> {
                var world = server.overworld();
                world.setDayTime(6000);
                for (int x = -6; x <= 6; x++) for (int z = -2; z <= 11; z++)
                    world.setBlockAndUpdate(new BlockPos(x, 76, z), Blocks.SMOOTH_STONE.defaultBlockState());
                var cores = cores();
                var materials = materials();
                for (int column = 0; column < cores.size(); column++) {
                    for (int row = 0; row < 3; row++) {
                        var pos = position(column, row);
                        world.setBlockAndUpdate(pos, cores.get(column).defaultBlockState());
                        if (row > 0) ((HeartBlockEntity) world.getBlockEntity(pos)).setMaterial(materials.get(row - 1).defaultBlockState());
                    }
                }
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(world, .5, 78.1, 12, 180, -1);
            });
            mc.options.hideGui = true;
            return;
        }
        if (++ticks < 55) return;
        var cores = cores();
        var materials = materials();
        for (int column = 0; column < cores.size(); column++) {
            for (int row = 0; row < 3; row++) {
                var pos = position(column, row);
                require(mc.level.getBlockState(pos).is(cores.get(column)), "Core missing from client world: " + pos);
                if (row > 0) {
                    var core = (HeartBlockEntity) mc.level.getBlockEntity(pos);
                    require(core != null && core.getMaterial().is(materials.get(row - 1)), "Camouflage did not reach client: " + pos);
                }
            }
        }
        screenshot(mc, "core-assets-world.png");
        System.out.println("CORE_ASSETS_PASS cores=3 faces=18 camouflageFaces=36 items=7 placed=9 photomancy="
                + ModList.get().isLoaded("sable_schematic_api"));
        mc.stop();
    }

    private static void checkBakedModels(Minecraft mc) throws Exception {
        for (var block : cores()) {
            var state = block.defaultBlockState();
            var model = mc.getBlockRenderer().getBlockModel(state);
            String texture = block == SableLife.REDSTONE_CORE.get() ? "redstone_crystal" : "heart_crystal";
            var id = ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "block/" + texture);
            var file = id.withPath("textures/block/" + texture + ".png");
            try (var input = mc.getResourceManager().open(file); var expected = NativeImage.read(input)) {
                for (var side : Direction.values()) {
                    var bare = model.getQuads(state, side, RandomSource.create(), ModelData.EMPTY, RenderType.cutoutMipped());
                    require(bare.size() == 6, "Wrong casing, crystal or frame count: " + block + " " + side);
                    require(bare.stream().noneMatch(quad -> quad.getSprite().contents().name().getPath().equals("missingno")), "Missing core sprite");
                    var casing = bare.stream().filter(quad -> quad.getSprite().contents().name().toString().equals("create:block/copycat_base")).findFirst().orElseThrow();
                    require(hasTransparentPixel(casing.getSprite().contents().getOriginalImage()), "Copycat casing lost transparent holes");
                    var diamond = bare.stream().filter(quad -> quad.getSprite().contents().name().equals(id)).findFirst().orElseThrow();
                    var actual = diamond.getSprite().contents().getOriginalImage();
                    require(actual.getWidth() == 32 && actual.getHeight() == 32, "Crystal atlas dimensions changed");
                    for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                        int a = actual.getPixelRGBA(x, y), b = expected.getPixelRGBA(x, y);
                        require((a >>> 24) == (b >>> 24) && ((b >>> 24) == 0 || a == b), "Crystal atlas pixels differ: " + id + " " + side);
                    }
                    for (var material : materials()) {
                        var data = ModelData.builder().with(CopycatModel.MATERIAL_PROPERTY, material.defaultBlockState()).build();
                        var quads = model.getQuads(state, side, RandomSource.create(), data, null);
                        require(quads.stream().anyMatch(quad -> quad.getSprite().contents().name().equals(id)), "Camouflage lost crystal");
                        require(quads.stream().anyMatch(quad -> quad.getSprite().contents().name().getNamespace().equals("minecraft")), "Camouflage lost donor");
                        require(quads.stream().noneMatch(quad -> quad.getSprite().contents().name().getNamespace().equals("create")), "Default casing leaked through camouflage");
                    }
                }
            }
        }
        for (var stack : items())
            require(mc.getItemRenderer().getModel(stack, null, null, 0) != mc.getModelManager().getMissingModel(), "Missing item model: " + stack);
    }

    private static boolean hasTransparentPixel(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if (image.getPixelRGBA(x, y) >>> 24 == 0) return true;
        return false;
    }

    private static List<Block> cores() {
        return List.of(SableLife.HEART.get(), SableLife.REDSTONE_CORE.get(), SableLife.SURVIVAL_LIFE_CORE.get());
    }

    private static List<Block> materials() { return List.of(Blocks.IRON_BLOCK, Blocks.OAK_LOG); }

    private static List<ItemStack> items() {
        return List.of(new ItemStack(SableLife.HEART_ITEM.get()), new ItemStack(SableLife.REDSTONE_CORE_ITEM.get()),
                new ItemStack(SableLife.SURVIVAL_LIFE_CORE_ITEM.get()), new ItemStack(SableLife.WILD_ENCOUNTER_TOOL.get()),
                new ItemStack(SableLife.CREATIVE_RELEASE_TOOL.get()), new ItemStack(SableLife.SURVIVAL_RELEASE_TOOL.get()),
                new ItemStack(SableLife.DATA_COPIER.get()));
    }

    private static BlockPos position(int column, int row) { return new BlockPos((column - 1) * 3, 81 - row * 2, 0); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void screenshot(Minecraft mc, String name) throws Exception {
        var path = mc.gameDirectory.toPath().resolve("screenshots").resolve(name);
        Files.createDirectories(path.getParent());
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(path); }
    }
}
