package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartLootMenu;
import dev.sablelife.heart.HeartSettingsMenu;
import dev.sablelife.ownership.LifeLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("sablelife_tests")
@PrefixGameTestTemplate(false)
public final class HeartSettingsGameTests {
    @GameTest(template = "combat", batch="audit_heartsettingsgametests_nameandbossbarpersistandremaincreativeonly")
    public static void nameAndBossBarPersistAndRemainCreativeOnly(GameTestHelper helper) {
        var heart = fixture(helper); var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level); player.setGameMode(GameType.CREATIVE);
        player.setPos(SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter()));
        var menu = new HeartSettingsMenu(1, player.getInventory(), heart);
        var tag = new ItemStack(Items.NAME_TAG);
        tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("空中守卫"));
        menu.setCarried(tag); menu.clicked(16, 0, ClickType.PICKUP, player);
        menu.clicked(15, 0, ClickType.PICKUP, player);
        helper.assertTrue(heart.creatureName().equals("空中守卫") && heart.showBossBar() && ItemStack.matches(menu.getCarried(), tag),
                "Naming or Boss Bar toggle failed, or consumed the Name Tag");
        var saved = heart.saveWithFullMetadata(level.registryAccess());
        var restored = (HeartBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                heart.getBlockPos(), heart.getBlockState(), saved, level.registryAccess());
        helper.assertTrue(restored.creatureName().equals("空中守卫") && restored.showBossBar(), "Name or Boss Bar did not persist");
        heart.tryActivate(player);
        menu.setCarried(ItemStack.EMPTY); menu.clicked(16, 1, ClickType.PICKUP, player);
        menu.clicked(15, 0, ClickType.PICKUP, player);
        helper.assertTrue(heart.creatureName().isEmpty() && !heart.showBossBar(), "Active Heart controls did not update");
        player.setGameMode(GameType.SURVIVAL);
        menu.setCarried(tag); menu.clicked(16, 0, ClickType.PICKUP, player);
        menu.clicked(15, 0, ClickType.PICKUP, player);
        helper.assertTrue(heart.creatureName().isEmpty() && !heart.showBossBar(), "Survival edited Heart controls");
        helper.succeed();
    }

    @GameTest(template = "combat", batch="audit_heartsettingsgametests_corecontrolspersistandonlybreakingchangesafteractivation")
    public static void coreControlsPersistAndOnlyBreakingChangesAfterActivation(GameTestHelper helper) {
        var heart = fixture(helper); var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level); player.setGameMode(GameType.CREATIVE);
        player.setPos(SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter()));
        var menu = new HeartSettingsMenu(1, player.getInventory(), heart);
        menu.clicked(10, 0, ClickType.PICKUP, player);
        menu.clicked(11, 0, ClickType.PICKUP, player);
        menu.clicked(12, 0, ClickType.PICKUP, player);
        menu.clicked(14, 0, ClickType.PICKUP, player);
        helper.assertTrue(heart.health().maximum() == 110 && heart.health().deathDelay() == 80
                && !heart.allowBreaking() && heart.deathExperience() == 1, "Core menu controls did not change defaults");
        var saved = heart.saveWithFullMetadata(level.registryAccess());
        var restored = (HeartBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                heart.getBlockPos(), heart.getBlockState(), saved, level.registryAccess());
        helper.assertTrue(restored.health().maximum() == 110 && restored.health().deathDelay() == 80
                && !restored.allowBreaking() && restored.deathExperience() == 1, "Core menu settings did not persist");
        heart.tryActivate(player);
        helper.assertTrue(!LifeLifecycle.get(level.getServer()).breakable(heart.creatureId()), "Initial breaking switch did not bind to body");
        menu.clicked(10, 0, ClickType.PICKUP, player);
        menu.clicked(14, 0, ClickType.PICKUP, player);
        helper.assertTrue(heart.health().maximum() == 110 && heart.deathExperience() == 1, "Active core allowed locked settings");
        menu.clicked(12, 0, ClickType.PICKUP, player);
        helper.assertTrue(heart.allowBreaking() && LifeLifecycle.get(level.getServer()).breakable(heart.creatureId()),
                "Active core breaking switch did not update world protection");
        player.setGameMode(GameType.SURVIVAL);
        menu.clicked(12, 0, ClickType.PICKUP, player);
        helper.assertTrue(heart.allowBreaking(), "Survival player changed breaking switch");
        var previous = player.containerMenu; heart.openSettings(player);
        helper.assertTrue(player.containerMenu == previous, "Survival player opened core settings");
        helper.succeed();
    }

    @GameTest(template = "combat", timeoutTicks = 100, batch="audit_heartsettingsgametests_wholebodylootmenusnapshotsrewardandpreservescomponents")
    public static void wholeBodyLootMenuSnapshotsRewardAndPreservesComponents(GameTestHelper helper) {
        var heart = fixture(helper); var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level); player.setGameMode(GameType.CREATIVE);
        player.setPos(SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter()));
        var menu = new HeartLootMenu(1, player.getInventory(), heart);
        var trophy = new ItemStack(Items.DIAMOND_SWORD);
        trophy.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Core trophy"));
        menu.setCarried(trophy); menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.setCarried(new ItemStack(Items.DIAMOND, 7)); menu.clicked(1, 0, ClickType.PICKUP, player);
        helper.assertTrue(heart.deathTemplateMode() && ItemStack.matches(heart.deathTemplates().getFirst(), trophy)
                && heart.deathTemplates().get(1).getCount() == 7 && menu.getCarried().getCount() == 7,
                "Core loot template copied incorrectly");
        var saved = heart.saveWithFullMetadata(level.registryAccess());
        var restored = (HeartBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                heart.getBlockPos(), heart.getBlockState(), saved, level.registryAccess());
        helper.assertTrue(restored.deathTemplateMode() && ItemStack.matches(restored.deathTemplates().getFirst(), trophy),
                "Core loot template did not persist");
        heart.configure(100, 5); heart.tryActivate(player);
        menu.setCarried(new ItemStack(Items.EMERALD)); menu.clicked(2, 0, ClickType.PICKUP, player);
        helper.assertTrue(heart.deathTemplates().get(2).isEmpty(), "Active core loot was editable");
        var point = SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter());
        var area = new net.minecraft.world.phys.AABB(point, point).inflate(4);
        heart.damage(100);
        var lifecycle = LifeLifecycle.get(level.getServer());
        level.getServer().overworld().getDataStorage().set("sablelife_lifecycle",
                LifeLifecycle.load(lifecycle.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess()));
        helper.runAfterDelay(12, () -> {
            var drops = level.getEntitiesOfClass(ItemEntity.class, area);
            helper.assertTrue(drops.size() == 2 && drops.stream().anyMatch(e -> ItemStack.matches(e.getItem(), trophy))
                    && drops.stream().anyMatch(e -> e.getItem().is(Items.DIAMOND) && e.getItem().getCount() == 7),
                    "Whole-body reward lost template components/count or added extra table loot");
            LifeLifecycle.load(LifeLifecycle.get(level.getServer()).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()),
                    level.registryAccess()).tickDeaths(level.getServer());
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area).size() == 2, "Core reward duplicated after reload");
            helper.succeed();
        });
    }

    private static HeartBlockEntity fixture(GameTestHelper helper) {
        var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(4, 6, 4));
        level.setBlockAndUpdate(origin, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, origin.east()),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 1, origin.getY(), origin.getZ()));
        return (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    }
}
