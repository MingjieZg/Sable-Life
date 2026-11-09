package dev.sablelife;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Resource limits and module switches are server-owned. Saved configurations are retained. */
public final class SableLifeServerConfig {
    public static final java.util.List<String> DEFAULT_TOKEN_VALUES=java.util.List.of(
            "minecraft:copper_ingot=10",
            "minecraft:iron_ingot=30",
            "minecraft:gold_ingot=60",
            "minecraft:emerald=120",
            "minecraft:diamond=300",
            "minecraft:netherite_ingot=3000",
            "minecraft:copper_block=90",
            "minecraft:iron_block=270",
            "minecraft:gold_block=540",
            "minecraft:emerald_block=1080",
            "minecraft:diamond_block=2700",
            "minecraft:netherite_block=27000");
    public static final ModConfigSpec SPEC;
    public static final net.neoforged.fml.config.IConfigSpec LOADER_SPEC;
    private static final java.util.Map<String,ModConfigSpec.IntValue> CAST_RATES=new java.util.LinkedHashMap<>();
    private static final java.util.Map<String,ModConfigSpec.BooleanValue> LIFE_MODULES=new java.util.LinkedHashMap<>();
    private static final java.util.Map<String,ModConfigSpec.BooleanValue> REDSTONE_MODULES=new java.util.LinkedHashMap<>();
    private static final java.util.Map<String,ModConfigSpec.BooleanValue> SURVIVAL_MODULES=new java.util.LinkedHashMap<>();
    private static final ModConfigSpec.BooleanValue SURVIVAL_LIFE_CORE_EDITING, WILD_ENCOUNTERS;
    private static final ModConfigSpec.IntValue CHANNELS, PARTICLES, CLEANUP, MODULES;
    private static final ModConfigSpec.IntValue FACTIONS_PER_PLAYER;
    private static final ModConfigSpec.IntValue PROJECTILES, CREATURE_PROJECTILES, ENTITY_HITBOXES, BLUEPRINT_PROJECTION;
    private static final int DEFAULT_ENTITY_HITBOXES=8;
    private static final ModConfigSpec.ConfigValue<java.util.List<String>> TOKEN_VALUES;
    private static final ModConfigSpec.IntValue ACTIVATION_RATE, HEAL_RATE, ARROW_RATE, MELEE_RATE, ARMOR_RATE;
    private static final ModConfigSpec.IntValue WILD_ACTIVE, WILD_NEARBY;
    private static final ModConfigSpec.IntValue WILD_WORKBENCH_RADIUS;
    private static final ModConfigSpec.IntValue WILD_BLOCKS,WILD_PARTS,WILD_BYTES,WILD_SIZE,WILD_SCAN,WILD_CLEAR;
    private static final ModConfigSpec.IntValue COLLISION, ARENA_CAPTURE_SCAN, ARENA_BLOCKS, ARENA_BLOCK_ENTITIES, ARENA_TEMPLATE_BYTES, ARENA_WORLD_BYTES;
    static {
        var builder = new ModConfigSpec.Builder();
        CHANNELS = builder.comment("Maximum newly authored wireless channels per core. Existing saves keep their channels.")
                .defineInRange("maximumWirelessChannels", 256, 1, 65535);
        MODULES=builder.comment("Maximum newly authored virtual modules per core; existing saved modules are retained up to the storage limit of 256.").defineInRange("maximumModulesPerCreature",128,16,256);
        FACTIONS_PER_PLAYER=builder.comment("Maximum factions one non-operator player can own/create. 0 disables this personal limit. Operators (permission level 2+) bypass it. Existing factions and joining other factions are unaffected; the global 256-faction storage limit still applies.")
                .defineInRange("maximumFactionsPerPlayer",1,0,256);
        PARTICLES = builder.comment("Visual particle points per player per server tick. Damage and timing are unaffected.")
                .defineInRange("particlePointsPerPlayerTick", 8192, 256, 65536);
        CLEANUP = builder.comment("Fallback block removals per server tick, shared across death jobs and dimensions. An eligible whole-body native removal is atomic and may exceed this block count.")
                .defineInRange("deathCleanupBlocksPerTick", 256, 16, 4096);
        PROJECTILES = builder.comment("Maximum loaded Sable Life projectiles per dimension. Saved projectiles are never discarded by this limit.")
                .defineInRange("maximumProjectilesPerWorld",8192,256,65536);
        CREATURE_PROJECTILES = builder.comment("Maximum new Sable Life projectiles for one creature. Existing flights continue when the limit is reached.")
                .defineInRange("maximumProjectilesPerCreature",2048,64,16384);
        ENTITY_HITBOXES = builder.comment("Maximum simultaneous entity damage receivers per Heart Core. Extra modules keep their settings. Higher values increase entity and targeting work.")
                .defineInRange("maximumEntityHitboxesPerCreature",DEFAULT_ENTITY_HITBOXES,1,32);
        BLUEPRINT_PROJECTION=builder.comment("Maximum blueprint blocks shown as a model projection. Larger templates use a boundary preview. Raising this increases client rendering work.")
                .defineInRange("maximumBlueprintProjectionBlocks",8192,0,dev.sablelife.module.BlueprintProjection.MAX_BLOCKS);
        COLLISION=builder.comment("Terrain voxel visits and body broad-phase checks per dimension/tick. Movement waits when exhausted. Minimum permits one maximum-size 16-step rotation check.")
                .defineInRange("collisionChecksPerTick",8388608,4194304,16777216);
        ARENA_CAPTURE_SCAN=builder.comment("Bounding-box cells (including air) allowed in one creative-operator Arena capture. Capture uses native Photomancy export synchronously; raising this can increase a one-time pause. Templates and actual block/entity limits remain separate.")
                .defineInRange("arenaCaptureScanVoxels",dev.sablelife.ownership.ArenaRespawnLimits.SCAN_VOXELS,262144,16777216);
        ARENA_BLOCKS=builder.comment("Actual blocks permitted in one administrator Arena record. Also bounds the work of native one-call respawn placement; this is not a per-tick construction budget.").defineInRange("arenaCaptureBlocks",dev.sablelife.ownership.ArenaRespawnLimits.BLOCKS,1024,1048576);
        ARENA_BLOCK_ENTITIES=builder.comment("Block entities permitted in one administrator Arena record. Limits NBT restoration work during native one-call placement.").defineInRange("arenaCaptureBlockEntities",dev.sablelife.ownership.ArenaRespawnLimits.BLOCK_ENTITIES,256,131072);
        ARENA_TEMPLATE_BYTES=builder.comment("Uncompressed estimated bytes permitted per Arena template at capture and saved-data admission. Required for native one-call respawn memory limits.").defineInRange("arenaTemplateBytes",dev.sablelife.ownership.ArenaRespawnLimits.TEMPLATE_BYTES,1048576,134217728);
        ARENA_WORLD_BYTES=builder.comment("Total uncompressed estimated bytes permitted across world Arena templates.").defineInRange("arenaWorldTemplateBytes",dev.sablelife.ownership.ArenaRespawnLimits.WORLD_TEMPLATE_BYTES,1048576,536870912);
        SURVIVAL_LIFE_CORE_EDITING = builder.comment(
                "WARNING: Enable this only if you know what you are doing.",
                "Survival players gain Heart Core editing, including loot and experience rewards and terrain destruction.",
                "Players can farm items and experience or destroy terrain. These effects can be difficult to control.")
                .define("allowEditHeartCoreUnderSurvivalMode", false);
        WILD_ENCOUNTERS = builder.comment(
                "Master switch for the Wild Encounter Tool and natural blueprint encounters.",
                "Set false to disable automatic and test spawning and suspend encounter lifetime cleanup. Saved rules are retained.")
                .define("enableWildEncounters", true);
        WILD_ACTIVE = builder.comment("Server-wide tracked wild encounter cap across dimensions, including unloaded encounters.")
                .defineInRange("maximumWildEncounters", 64, 1, 256);
        WILD_NEARBY = builder.comment("Maximum tracked wild encounters within 128 blocks of a player or a candidate spawn.")
                .defineInRange("maximumNearbyWildEncounters", 4, 1, 64);
        WILD_WORKBENCH_RADIUS=builder.comment("Horizontal distance from a fixed-world vanilla crafting table to the nearest blueprint footprint edge where natural spawning is blocked, at every height. Default 64; 0 disables. Also applies to Test spawn and obstacle clearing. Unknown/unindexed areas wait without loading chunks.")
                .defineInRange("wildWorkbenchProtectionRadius",64,0,256);
        WILD_BLOCKS=builder.comment("Maximum actual blocks in one natural-spawn blueprint. Native placement is synchronous; large templates can cause a pause.")
                .defineInRange("maximumWildBlueprintBlocks",262144,1,1048576);
        WILD_PARTS=builder.comment("Maximum independent Sable structure parts in one natural-spawn blueprint. Independent of Arena limits. Model projections support at most 32 parts; larger templates use a boundary preview.")
                .defineInRange("maximumWildBlueprintParts",128,1,512);
        WILD_BYTES=builder.comment("Maximum compressed file bytes and estimated uncompressed NBT bytes for one natural-spawn blueprint. Independent of Arena capture limits.")
                .defineInRange("maximumWildBlueprintBytes",268435456,1048576,1073741824);
        WILD_SIZE=builder.comment("Maximum X or Z footprint in blocks for a natural-spawn blueprint. Its entire footprint must be in loaded chunks.")
                .defineInRange("maximumWildBlueprintHorizontalSize",256,16,512);
        WILD_SCAN=builder.comment("Maximum terrain voxel visits per natural-spawn placement check. Insufficient budget rejects the candidate without changing terrain.")
                .defineInRange("wildPlacementScanVoxels",4194304,262144,16777216);
        WILD_CLEAR=builder.comment("Maximum blocks removed by one natural-spawn Clear obstacles operation. Protected blocks reject the entire candidate. Zero disables pre-clearing.")
                .defineInRange("maximumWildClearedBlocks",131072,0,1048576);
        builder.comment("Server-owned module switches. False hides new modules and suspends existing modules without deleting their settings.").push("modules");
        builder.push("heartCore");
        for(String type:dev.sablelife.module.VirtualModule.AVAILABLE_TYPES)LIFE_MODULES.put(type,builder.define(type,true));
        builder.pop();
        builder.comment("Only supported control modules are configurable here; this table cannot grant Heart Core capabilities.").push("redstoneCore");
        for(String type:dev.sablelife.heart.RedstoneCorePolicy.TYPES)REDSTONE_MODULES.put(type,builder.define(type,dev.sablelife.heart.RedstoneCorePolicy.defaultEnabled(type)));
        builder.pop();
        builder.comment("Skill switches for the Survival Heart Core. Unsupported skills stay unavailable.").push("survivalHeartCore");
        for(String type:dev.sablelife.heart.SurvivalLifeCorePolicy.TYPES)SURVIVAL_MODULES.put(type,builder.define(type,true));
        builder.pop(2);
        builder.push("survivalTokens");
        TOKEN_VALUES=builder.comment("All refueling materials as item_id=energy_value, including copper. Later duplicate entries override earlier ones. An empty list disables refueling.")
                .define("depositValues",DEFAULT_TOKEN_VALUES,value->value instanceof java.util.List<?> entries&&entries.size()<=257
                        &&entries.stream().allMatch(entry->entry instanceof String text&&text.matches("[a-z0-9_.-]+:[a-z0-9/_.-]+=[1-9][0-9]{0,6}")&&Integer.parseInt(text.substring(text.indexOf('=')+1))<=1000000));
        ACTIVATION_RATE=builder.comment("Energy per missing health point when activating. Costs round up.")
                .defineInRange("activationTokensPerHealth",1,1,1000000);
        HEAL_RATE=builder.comment("Energy per health point actually restored while active. Costs round up.")
                .defineInRange("healingTokensPerHealth",1,1,1000000);
        ARROW_RATE=builder.comment("Energy multiplier for each Survival Heart Core arrow volley.")
                .defineInRange("arrowTokenMultiplier",1,1,1000000);
        MELEE_RATE=builder.comment("Energy per configured damage point for each successful Survival Heart Core melee hit.")
                .defineInRange("meleeTokensPerDamage",1,1,1000000);
        ARMOR_RATE=builder.comment("Energy per 1 percentage point of damage reduction per active second, for each Survival Heart Core armor module. Reduction is capped at 90 percent: default 90 percent costs 90 Energy/second.")
                .defineInRange("armorTokensPerReductionSecond",1,1,1000000);
        builder.push("moduleMultipliers");
        for(String type:dev.sablelife.heart.SurvivalTokens.CAST_TYPES)
            CAST_RATES.put(type,builder.defineInRange(type,1,1,1000000));
        builder.pop();
        builder.pop();
        SPEC = builder.build();
        LOADER_SPEC = new ServerConfigMigration(SPEC);
    }
    public static boolean wildEncountersEnabled() {
        return SPEC.isLoaded() && WILD_ENCOUNTERS.get();
    }
    public static int maximumWildEncounters(){return SPEC.isLoaded()?WILD_ACTIVE.get():64;}
    public static int maximumNearbyWildEncounters(){return SPEC.isLoaded()?WILD_NEARBY.get():4;}
    public static int wildWorkbenchProtectionRadius(){return SPEC.isLoaded()?WILD_WORKBENCH_RADIUS.get():64;}
    public static int maximumWildBlueprintBlocks(){return SPEC.isLoaded()?WILD_BLOCKS.get():262144;}
    public static int maximumWildBlueprintParts(){return SPEC.isLoaded()?WILD_PARTS.get():128;}
    public static int maximumWildBlueprintBytes(){return SPEC.isLoaded()?WILD_BYTES.get():268435456;}
    public static int maximumWildBlueprintHorizontalSize(){return SPEC.isLoaded()?WILD_SIZE.get():256;}
    public static int wildPlacementScanVoxels(){return SPEC.isLoaded()?WILD_SCAN.get():4194304;}
    public static int maximumWildClearedBlocks(){return SPEC.isLoaded()?WILD_CLEAR.get():131072;}
    public static boolean allowSurvivalLifeCoreEditing() {
        return SPEC.isLoaded() && SURVIVAL_LIFE_CORE_EDITING.get();
    }
    public static int maximumModulesPerCreature(){return SPEC.isLoaded()?MODULES.get():128;}
    public static boolean moduleEnabled(boolean redstoneCore,String type){
        var setting=(redstoneCore?REDSTONE_MODULES:LIFE_MODULES).get(type);
        return setting!=null&&(SPEC.isLoaded()?setting.get():!redstoneCore||dev.sablelife.heart.RedstoneCorePolicy.defaultEnabled(type));
    }
    public static boolean survivalModuleEnabled(String type){
        var setting=SURVIVAL_MODULES.get(type);
        return setting!=null&&(!SPEC.isLoaded()||setting.get());
    }
    public static java.util.List<String> tokenValues(){return SPEC.isLoaded()?TOKEN_VALUES.get():DEFAULT_TOKEN_VALUES;}
    public static int activationTokenRate(){return SPEC.isLoaded()?ACTIVATION_RATE.get():1;}
    public static int castTokenRate(String type){return SPEC.isLoaded()?CAST_RATES.get(type).get():1;}
    public static int arrowTokenRate(){return SPEC.isLoaded()?ARROW_RATE.get():1;}
    public static int armorTokenRate(){return SPEC.isLoaded()?ARMOR_RATE.get():1;}
    public static int meleeTokenRate(){return SPEC.isLoaded()?MELEE_RATE.get():1;}
    public static int healingTokenRate(){return SPEC.isLoaded()?HEAL_RATE.get():1;}
    public static int maximumWirelessChannels() { return SPEC.isLoaded() ? CHANNELS.get() : 256; }
    public static int maximumFactionsPerPlayer() { return SPEC.isLoaded() ? FACTIONS_PER_PLAYER.get() : 1; }
    public static int particlePointsPerPlayerTick() { return SPEC.isLoaded() ? PARTICLES.get() : 8192; }
    public static int deathCleanupBlocksPerTick() { return SPEC.isLoaded() ? CLEANUP.get() : 256; }
    public static int maximumProjectilesPerWorld(){return SPEC.isLoaded()?PROJECTILES.get():8192;}
    public static int maximumProjectilesPerCreature(){return SPEC.isLoaded()?CREATURE_PROJECTILES.get():2048;}
    public static int maximumBlueprintProjectionBlocks(){return SPEC.isLoaded()?BLUEPRINT_PROJECTION.get():8192;}
    public static int maximumEntityHitboxesPerCreature(){return SPEC.isLoaded()?ENTITY_HITBOXES.get():DEFAULT_ENTITY_HITBOXES;}
    public static int collisionChecksPerTick(){return SPEC.isLoaded()?COLLISION.get():8388608;}
    public static int arenaCaptureScanVoxels(){return SPEC.isLoaded()?ARENA_CAPTURE_SCAN.get():dev.sablelife.ownership.ArenaRespawnLimits.SCAN_VOXELS;}
    public static int arenaCaptureBlocks(){return SPEC.isLoaded()?ARENA_BLOCKS.get():dev.sablelife.ownership.ArenaRespawnLimits.BLOCKS;}
    public static int arenaCaptureBlockEntities(){return SPEC.isLoaded()?ARENA_BLOCK_ENTITIES.get():dev.sablelife.ownership.ArenaRespawnLimits.BLOCK_ENTITIES;}
    public static int arenaTemplateBytes(){return SPEC.isLoaded()?ARENA_TEMPLATE_BYTES.get():dev.sablelife.ownership.ArenaRespawnLimits.TEMPLATE_BYTES;}
    public static int arenaWorldTemplateBytes(){return SPEC.isLoaded()?ARENA_WORLD_BYTES.get():dev.sablelife.ownership.ArenaRespawnLimits.WORLD_TEMPLATE_BYTES;}
    private SableLifeServerConfig() {}
}
