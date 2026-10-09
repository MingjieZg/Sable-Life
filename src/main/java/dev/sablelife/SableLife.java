package dev.sablelife;

import dev.sablelife.command.HeartCommands;
import dev.sablelife.combat.BodyCombat;
import dev.sablelife.ownership.BodyProtection;
import dev.sablelife.heart.HeartBlock;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.item.CreativeReleaseToolItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(SableLife.MOD_ID)
public final class SableLife {
    public static final String MOD_ID = "sablelife";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, MOD_ID);
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,net.minecraft.core.particles.SimpleParticleType> FLAME_JET =
            PARTICLES.register("flame_jet",()->new net.minecraft.core.particles.SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>,net.minecraft.core.particles.SimpleParticleType> SOUL_FLAME_JET =
            PARTICLES.register("soul_flame_jet",()->new net.minecraft.core.particles.SimpleParticleType(true));
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<dev.sablelife.heart.LevelRewardOrb>> LEVEL_REWARD_ORB=ENTITIES.register("level_reward_orb",()->net.minecraft.world.entity.EntityType.Builder.<dev.sablelife.heart.LevelRewardOrb>of(dev.sablelife.heart.LevelRewardOrb::new,net.minecraft.world.entity.MobCategory.MISC).sized(.5F,.5F).clientTrackingRange(6).updateInterval(20).build("sablelife:level_reward_orb"));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<dev.sablelife.attack.ModuleShot>> MODULE_SHOT =
            ENTITIES.register("module_shot", () -> net.minecraft.world.entity.EntityType.Builder.<dev.sablelife.attack.ModuleShot>of(dev.sablelife.attack.ModuleShot::new,
                    net.minecraft.world.entity.MobCategory.MISC).sized(.15F, .15F).clientTrackingRange(12).updateInterval(1).build("sablelife:module_shot"));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<dev.sablelife.attack.ModuleArrow>> MODULE_ARROW =
            ENTITIES.register("module_arrow", () -> net.minecraft.world.entity.EntityType.Builder.<dev.sablelife.attack.ModuleArrow>of(dev.sablelife.attack.ModuleArrow::new,
                    net.minecraft.world.entity.MobCategory.MISC).sized(.25F, .25F).clientTrackingRange(12).updateInterval(1).build("sablelife:module_arrow"));
    public static final DeferredHolder<MenuType<?>, MenuType<dev.sablelife.module.HeartDashboardMenu>> DASHBOARD_MENU =
            MENUS.register("heart_dashboard", () -> new MenuType<>(dev.sablelife.module.HeartDashboardMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<dev.sablelife.heart.HeartWirelessMenu>> WIRELESS_MENU =
            MENUS.register("heart_wireless", () -> new MenuType<>(dev.sablelife.heart.HeartWirelessMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<dev.sablelife.module.EffectSelectionMenu>> EFFECT_MENU =
            MENUS.register("module_effects", () -> new MenuType<>(dev.sablelife.module.EffectSelectionMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<dev.sablelife.module.ProjectileItemMenu>> PROJECTILE_MENU =
            MENUS.register("projectile_item", () -> new MenuType<>(dev.sablelife.module.ProjectileItemMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<dev.sablelife.module.ModuleFrequencyMenu>> MODULE_FREQUENCY_MENU =
            MENUS.register("module_frequency", () -> new MenuType<>(dev.sablelife.module.ModuleFrequencyMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<dev.sablelife.module.SequenceMenu>> SEQUENCE_MENU =
            MENUS.register("pulse_sequence", () -> new MenuType<>(dev.sablelife.module.SequenceMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<dev.sablelife.sound.SoundTimelineMenu>> SOUND_TIMELINE_MENU =
            MENUS.register("sound_timeline", () -> new MenuType<>(dev.sablelife.sound.SoundTimelineMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<dev.sablelife.heart.HeartSettingsMenu>> SETTINGS_MENU =
            MENUS.register("heart_settings", () -> new MenuType<>(dev.sablelife.heart.HeartSettingsMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<net.minecraft.world.inventory.ChestMenu>> LOOT_CHEST =
            MENUS.register("loot_chest", () -> new MenuType<>((id, inventory) -> new net.minecraft.world.inventory.ChestMenu(
                    SableLife.LOOT_CHEST.get(), id, inventory, new net.minecraft.world.SimpleContainer(54), 6), net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredBlock<HeartBlock> HEART = BLOCKS.register("heart", () -> new HeartBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED)
                    .strength(4.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(HeartBlock.ACTIVE) ? 7 : 0)));
    public static final DeferredItem<BlockItem> HEART_ITEM = ITEMS.registerSimpleBlockItem("heart", HEART);
    public static final DeferredItem<CreativeReleaseToolItem> CREATIVE_RELEASE_TOOL = ITEMS.register("creative_release_tool",
            () -> new CreativeReleaseToolItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<dev.sablelife.item.DataCopierItem> DATA_COPIER = ITEMS.register("data_copier",
            () -> new dev.sablelife.item.DataCopierItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredBlock<dev.sablelife.heart.RedstoneCoreBlock> REDSTONE_CORE=BLOCKS.register("redstone_core",()->new dev.sablelife.heart.RedstoneCoreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(4,6).sound(SoundType.METAL)));
    public static final DeferredItem<BlockItem> REDSTONE_CORE_ITEM=ITEMS.registerSimpleBlockItem(REDSTONE_CORE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeartBlockEntity>> HEART_ENTITY =
            BLOCK_ENTITIES.register("heart", () -> BlockEntityType.Builder.of(HeartBlockEntity::new, HEART.get(),REDSTONE_CORE.get()).build(null));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.sablelife"))
                    .icon(() -> HEART_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(HEART_ITEM.get());
                        output.accept(REDSTONE_CORE_ITEM.get());
                        output.accept(CREATIVE_RELEASE_TOOL.get());
                        output.accept(DATA_COPIER.get());
                    }).build());

    public SableLife(IEventBus bus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, SableLifeServerConfig.SPEC);
        dev.sablelife.ownership.CoreChunkLoading.register();
        bus.addListener(dev.sablelife.compat.photomancy.PhotomancyCompat::setup);
        dev.sablelife.sound.SoundLibrary.prepare();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
        MENUS.register(bus);
        ENTITIES.register(bus);
        PARTICLES.register(bus);
        bus.addListener(dev.sablelife.module.ModuleNetworking::register);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, dev.sablelife.module.MarkingSessions::click);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,dev.sablelife.heart.CorePlacementRules::placed);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, dev.sablelife.module.MarkingSessions::attack);
        NeoForge.EVENT_BUS.addListener(dev.sablelife.module.MarkingSessions::tick);
        NeoForge.EVENT_BUS.addListener(dev.sablelife.module.MarkingSessions::stopped);
        NeoForge.EVENT_BUS.addListener(HeartCommands::register);
        NeoForge.EVENT_BUS.addListener(dev.sablelife.ownership.LifeLifecycle::tick);
        NeoForge.EVENT_BUS.addListener(dev.sablelife.ownership.CoreChunkLoading::tick);
        NeoForge.EVENT_BUS.addListener(dev.sablelife.ownership.CoreChunkLoading::stopped);
        NeoForge.EVENT_BUS.addListener(dev.sablelife.ownership.LoadedCoreIndex::stopped);
        NeoForge.EVENT_BUS.addListener(dev.sablelife.compat.sable.SableBodyProbe::stopped);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BodyProtection::breaking);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, dev.sablelife.combat.BodyExplosionDamage::detonate);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BodyProtection::explosion);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BodyProtection::drops);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BodyProtection::placed);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BodyCombat::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BodyCombat::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BodyCombat::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, dev.sablelife.attack.ModuleBlast::impact);
        NeoForge.EVENT_BUS.addListener(dev.sablelife.attack.ModuleBlast::tick);
        NeoForge.EVENT_BUS.addListener(dev.sablelife.attack.ModuleBlast::stopped);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,dev.sablelife.attack.ProjectileBudget::joined);
        NeoForge.EVENT_BUS.addListener(dev.sablelife.attack.ProjectileBudget::left);
        NeoForge.EVENT_BUS.addListener(dev.sablelife.attack.ProjectileBudget::stopped);
    }
}

