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

@GameTestHolder("sablelife_core_ui_tests")
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
        menu.setCarried(tag); menu.action(player,"name",0,tag.getHoverName().getString());
        menu.action(player,"bossbar",1,"");
        helper.assertTrue(heart.creatureName().equals("空中守卫") && heart.showBossBar() && ItemStack.matches(menu.getCarried(), tag),
                "Naming or Boss Bar toggle failed, or consumed the Name Tag");
        var saved = heart.saveWithFullMetadata(level.registryAccess());
        var restored = (HeartBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                heart.getBlockPos(), heart.getBlockState(), saved, level.registryAccess());
        helper.assertTrue(restored.creatureName().equals("空中守卫") && restored.showBossBar(), "Name or Boss Bar did not persist");
        heart.tryActivate(player);
        menu.setCarried(ItemStack.EMPTY);menu.action(player,"name",0,"");
        menu.action(player,"bossbar",0,"");
        helper.assertTrue(heart.creatureName().isEmpty() && !heart.showBossBar(), "Active Heart controls did not update");
        player.setGameMode(GameType.SURVIVAL);
        menu.setCarried(tag);menu.action(player,"name",0,tag.getHoverName().getString());
        menu.action(player,"bossbar",1,"");
        helper.assertTrue(heart.creatureName().isEmpty() && !heart.showBossBar(), "Survival edited Heart controls");
        helper.succeed();
    }

    @GameTest(template = "combat", batch="audit_heartsettingsgametests_corecontrolspersistandonlybreakingchangesafteractivation")
    public static void coreControlsPersistAndOnlyBreakingChangesAfterActivation(GameTestHelper helper) {
        var heart = fixture(helper); var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level); player.setGameMode(GameType.CREATIVE);
        player.setPos(SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter()));
        var menu = new HeartSettingsMenu(1, player.getInventory(), heart);
        menu.action(player,"health",110,"");menu.action(player,"delay",4,"");
        menu.action(player,"breaking",0,"");menu.action(player,"xp",1,"");
        helper.assertTrue(heart.health().maximum() == 110 && heart.health().deathDelay() == 80
                && !heart.allowBreaking() && heart.deathExperience() == 1, "Core menu controls did not change defaults");
        var saved = heart.saveWithFullMetadata(level.registryAccess());
        var restored = (HeartBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                heart.getBlockPos(), heart.getBlockState(), saved, level.registryAccess());
        helper.assertTrue(restored.health().maximum() == 110 && restored.health().deathDelay() == 80
                && !restored.allowBreaking() && restored.deathExperience() == 1, "Core menu settings did not persist");
        heart.tryActivate(player);
        helper.assertTrue(!LifeLifecycle.get(level.getServer()).breakable(heart.creatureId()), "Initial breaking switch did not bind to body");
        menu.action(player,"health",120,"");menu.action(player,"xp",2,"");
        helper.assertTrue(heart.health().maximum() == 110 && heart.deathExperience() == 1, "Active core allowed locked settings");
        menu.action(player,"breaking",1,"");
        helper.assertTrue(heart.allowBreaking() && LifeLifecycle.get(level.getServer()).breakable(heart.creatureId()),
                "Active core breaking switch did not update world protection");
        player.setGameMode(GameType.SURVIVAL);
        menu.action(player,"breaking",0,"");
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

    @GameTest(template="combat",batch="boss_color_persistence")
    public static void BossColorsValidatePersistCopyAndUpdateLive(GameTestHelper h)throws Exception {
        var heart=fixture(h);var level=h.getLevel();var player=TestActors.creative(level);player.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,heart.getBlockPos().getCenter()));
        h.assertTrue(heart.bossBarColor()==net.minecraft.world.BossEvent.BossBarColor.RED,"Legacy default changed");
        for(var color:net.minecraft.world.BossEvent.BossBarColor.values()){
            h.assertTrue(heart.editCore(player,"bossbarColor",color.ordinal(),"")&&heart.bossBarColor()==color,"Color edit failed");
            var copy=heart.copyConfiguration();h.assertTrue(copy.getString("BossBarColor").equals(color.name()),"Copy lost color");
            var saved=heart.saveWithFullMetadata(level.registryAccess());var loaded=(HeartBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(heart.getBlockPos(),heart.getBlockState(),saved,level.registryAccess());
            h.assertTrue(loaded.bossBarColor()==color,"World reload lost color");
        }
        h.assertTrue(!heart.editCore(player,"bossbarColor",Double.NaN,"")&&!heart.editCore(player,"bossbarColor",7,"")&&!heart.editCore(player,"bossbarColor",-1,"")&&!heart.editCore(player,"bossbarColor",1.5,""),"Invalid color accepted");
        heart.editCore(player,"bossbar",1,"");heart.tryActivate(player);
        var update=HeartBlockEntity.class.getDeclaredMethod("updateBossBar",net.minecraft.server.level.ServerLevel.class);update.setAccessible(true);update.invoke(heart,level);
        var field=HeartBlockEntity.class.getDeclaredField("bossBar");field.setAccessible(true);var bar=(net.minecraft.server.level.ServerBossEvent)field.get(heart);
        heart.editCore(player,"bossbarColor",net.minecraft.world.BossEvent.BossBarColor.BLUE.ordinal(),"");
        h.assertTrue(bar.getColor()==net.minecraft.world.BossEvent.BossBarColor.BLUE&&new HeartSettingsMenu(1,player.getInventory(),heart).snapshot().getInt("bossbarColor")==net.minecraft.world.BossEvent.BossBarColor.BLUE.ordinal(),"Live bar or UI color stale");
        player.setGameMode(GameType.SURVIVAL);h.assertTrue(!heart.editCore(player,"bossbarColor",0,"")&&heart.bossBarColor()==net.minecraft.world.BossEvent.BossBarColor.BLUE,"Survival altered color");
        var old=heart.saveWithFullMetadata(level.registryAccess());old.remove("BossBarColor");var legacy=(HeartBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(heart.getBlockPos(),heart.getBlockState(),old,level.registryAccess());
        h.assertTrue(legacy.bossBarColor()==net.minecraft.world.BossEvent.BossBarColor.RED,"Missing color did not default red");h.succeed();
    }
    private static HeartBlockEntity fixture(GameTestHelper helper) {
        return fixture(helper, false);
    }

    private static HeartBlockEntity fixture(GameTestHelper helper, boolean survival) {
        var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(4, 6, 4));
        var block = survival ? SableLife.SURVIVAL_LIFE_CORE.get() : SableLife.HEART.get();
        level.setBlockAndUpdate(origin, block.defaultBlockState());
        level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, origin.east()),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 1, origin.getY(), origin.getZ()));
        return (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    }

    @GameTest(template = "combat", batch = "core_hit_particles")
    public static void StructureHitsEmitOnlyConfiguredParticles(GameTestHelper h) {
        var level = h.getLevel();
        var core = fixture(h);
        var player = new HitSettingsPlayer(level, GameType.CREATIVE);
        var hit = core.getBlockPos().east();
        var point = SableBodyProbe.toWorld(level, hit.getCenter());
        player.setPos(point);
        var packets = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket>();
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if (packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket particle) packets.add(particle);
            }
        };
        var legacySave = core.saveWithFullMetadata(level.registryAccess());
        legacySave.remove("HitPresentation");
        core.loadWithComponents(legacySave, level.registryAccess());
        core.tryActivate(player);
        level.players().add(player);
        try {
            hitWithArrow(h, hit);
            h.assertTrue(packets.size() == 1 && packets.getFirst().getCount() == 4
                    && packets.getFirst().getParticle() == net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR
                    && packets.getFirst().getXDist() == .15F, "Legacy burst did not preserve the original dark hearts");
            packets.clear();
            var settings = new HeartSettingsMenu(1, player.getInventory(), core, HeartSettingsMenu.HIT_EFFECT_PAGE);
            h.assertTrue(editHitSettings(settings, player, "hitParticleCount", 7)
                    && editHitSettings(settings, player, "hitParticleSpread", 0.75), "Live hit particle settings were rejected");
            h.assertTrue(settings.clickMenuButton(player, 6)
                    && player.containerMenu instanceof dev.sablelife.module.EffectSelectionMenu, "Core did not open the particle picker");
            var picker = (dev.sablelife.module.EffectSelectionMenu) player.containerMenu;
            var selection = core.hitPresentation().particleSelection();
            h.assertTrue(picker.action(player, selection.id(), "remove", 0, "minecraft:damage_indicator")
                    && picker.action(player, selection.id(), "add", 0, "minecraft:flame")
                    && picker.action(player, selection.id(), "add", 0, "minecraft:cloud"), "Picker did not apply particle selection");
            h.assertTrue(picker.clickMenuButton(player, 0) && player.containerMenu instanceof HeartSettingsMenu back
                    && back.snapshot().getInt("InitialPage") == HeartSettingsMenu.HIT_EFFECT_PAGE, "Picker returned to the wrong page");
            hitWithArrow(h, hit);
            h.assertTrue(packets.size() == 2 && packets.stream().mapToInt(p -> p.getCount()).sum() == 7
                    && packets.stream().allMatch(p -> p.getXDist() == .75F
                    && new net.minecraft.world.phys.Vec3(p.getX(), p.getY(), p.getZ()).distanceToSqr(point) < 0.000001)
                    && packets.stream().map(p -> p.getParticle()).collect(java.util.stream.Collectors.toSet()).equals(
                        java.util.Set.of(net.minecraft.core.particles.ParticleTypes.FLAME, net.minecraft.core.particles.ParticleTypes.CLOUD)),
                    "Custom burst used the wrong type, total, spread or impact position");
            packets.clear();
            h.assertTrue(editHitSettings(settings, player, "hitParticleCount", 0), "Could not disable the burst");
            float before = core.health().current();
            hitWithArrow(h, hit);
            h.assertTrue(packets.isEmpty() && core.health().current() < before, "Disabling the effect changed damage or still sent particles");
        } finally {
            level.players().remove(player);
        }
        h.succeed();
    }

    @GameTest(template = "combat", batch = "survival_core_hit_particles")
    public static void SurvivalCoreHitParticlesPersistAndRejectInvalidEdits(GameTestHelper h) {
        var core = fixture(h, true);
        var level = h.getLevel();
        var player = new HitSettingsPlayer(level, GameType.SURVIVAL);
        player.setPos(SableBodyProbe.toWorld(level, core.getBlockPos().getCenter()));
        var menu = new HeartSettingsMenu(1, player.getInventory(), core);
        h.assertTrue(editHitSettings(menu, player, "hitParticleCount", 32)
                && editHitSettings(menu, player, "hitParticleSpread", 4)
                && !editHitSettings(menu, player, "hitParticleCount", 33)
                && !editHitSettings(menu, player, "hitParticleCount", 1.5)
                && !editHitSettings(menu, player, "hitParticleSpread", 4.01), "Survival menu accepted an invalid setting or rejected a limit");
        h.assertTrue(menu.clickMenuButton(player, 6)
                && player.containerMenu instanceof dev.sablelife.module.EffectSelectionMenu, "Survival player could not open the picker");
        var picker = (dev.sablelife.module.EffectSelectionMenu) player.containerMenu;
        h.assertTrue(picker.action(player, core.hitPresentation().particleSelection().id(), "remove", 0, "minecraft:damage_indicator"),
                "Could not clear the particle list");
        var saved = core.saveWithFullMetadata(level.registryAccess());
        var restored = (HeartBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                core.getBlockPos(), core.getBlockState(), saved, level.registryAccess());
        h.assertTrue(restored.hitPresentation().count() == 32 && restored.hitPresentation().spread() == 4
                && restored.hitPresentation().particleSelection().particles().isEmpty(), "Settings or empty list lost on reload");
        var copied = core.copyConfiguration();
        h.assertTrue(editHitSettings(menu, player, "hitParticleCount", 1) && core.pasteConfiguration(player, copied)
                && core.hitPresentation().count() == 32 && core.hitPresentation().particleSelection().particles().isEmpty(),
                "Portable configuration lost the hit particle settings");
        player.setGameMode(GameType.SPECTATOR);
        h.assertTrue(!editHitSettings(menu, player, "hitParticleCount", 0) && !menu.clickMenuButton(player, 6),
                "Read-only actor could modify hit particles");
        h.succeed();
    }

    private static boolean editHitSettings(HeartSettingsMenu menu, net.minecraft.world.entity.player.Player player, String key, double value) {
        var request = new dev.sablelife.module.ModuleNetworking.Action(menu.containerId, 0, new java.util.UUID(0, 0), "core", key, value, "");
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), player.level().registryAccess());
        try {
            dev.sablelife.module.ModuleNetworking.Action.CODEC.encode(buf, request);
            var decoded = dev.sablelife.module.ModuleNetworking.Action.CODEC.decode(buf);
            return menu.action(player, decoded.key(), decoded.value(), decoded.text());
        } finally {
            buf.release();
        }
    }

    private static void hitWithArrow(GameTestHelper h, BlockPos hit) {
        var arrow = new net.minecraft.world.entity.projectile.Arrow(h.getLevel(), 0, 0, 0, new ItemStack(Items.ARROW), null);
        arrow.setDeltaMovement(0, -1, 0);
        dev.sablelife.combat.BodyCombat.onProjectileImpact(new net.neoforged.neoforge.event.entity.ProjectileImpactEvent(arrow,
                new net.minecraft.world.phys.BlockHitResult(hit.getCenter(), net.minecraft.core.Direction.UP, hit, false)));
    }

    private static final class HitSettingsPlayer extends net.neoforged.neoforge.common.util.FakePlayer {
        private int nextMenu;

        HitSettingsPlayer(net.minecraft.server.level.ServerLevel level, GameType mode) {
            super(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "HitSettingsTester"));
            setGameMode(mode);
        }

        @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,
                java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> extra) {
            containerMenu = provider.createMenu(++nextMenu, getInventory(), this);
            return java.util.OptionalInt.of(nextMenu);
        }
    }
}
