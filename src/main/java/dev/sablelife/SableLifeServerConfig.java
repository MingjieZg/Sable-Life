package dev.sablelife;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Resource limits and module switches are server-owned. Saved configurations are retained. */
public final class SableLifeServerConfig {
    public static final ModConfigSpec SPEC;
    private static final java.util.Map<String,ModConfigSpec.BooleanValue> LIFE_MODULES=new java.util.LinkedHashMap<>();
    private static final java.util.Map<String,ModConfigSpec.BooleanValue> REDSTONE_MODULES=new java.util.LinkedHashMap<>();
    private static final ModConfigSpec.IntValue CHANNELS, PARTICLES, CLEANUP, MODULES;
    private static final ModConfigSpec.IntValue PROJECTILES, CREATURE_PROJECTILES;
    private static final ModConfigSpec.IntValue COLLISION, ARENA_CAPTURE_SCAN, ARENA_BLOCKS, ARENA_BLOCK_ENTITIES, ARENA_TEMPLATE_BYTES, ARENA_WORLD_BYTES;
    static {
        var builder = new ModConfigSpec.Builder();
        CHANNELS = builder.comment("Maximum newly authored wireless channels per core. Existing saves keep their channels.")
                .defineInRange("maximumWirelessChannels", 256, 1, 65535);
        MODULES=builder.comment("Maximum newly authored virtual modules per core; existing saved modules are retained up to the storage limit of 256.").defineInRange("maximumModulesPerCreature",128,16,256);
        PARTICLES = builder.comment("Visual particle points per player per server tick. Damage and timing are unaffected.")
                .defineInRange("particlePointsPerPlayerTick", 8192, 256, 65536);
        CLEANUP = builder.comment("Fallback block removals per server tick, shared across death jobs and dimensions. An eligible whole-body native removal is atomic and may exceed this block count.")
                .defineInRange("deathCleanupBlocksPerTick", 256, 16, 4096);
        PROJECTILES = builder.comment("Maximum loaded Sable Life projectiles per dimension. Saved projectiles are never discarded by this limit.")
                .defineInRange("maximumProjectilesPerWorld",8192,256,65536);
        CREATURE_PROJECTILES = builder.comment("Maximum new Sable Life projectiles for one creature. Existing flights continue when the limit is reached.")
                .defineInRange("maximumProjectilesPerCreature",2048,64,16384);
        COLLISION=builder.comment("Terrain voxel visits and body broad-phase checks per dimension/tick. Movement waits when exhausted. Minimum permits one maximum-size 16-step rotation check.")
                .defineInRange("collisionChecksPerTick",8388608,4194304,16777216);
        ARENA_CAPTURE_SCAN=builder.comment("Bounding-box cells (including air) allowed in one creative-operator Arena capture. Capture uses native Photomancy export synchronously; raising this can increase a one-time pause. Templates and actual block/entity limits remain separate.")
                .defineInRange("arenaCaptureScanVoxels",dev.sablelife.ownership.ArenaRespawnLimits.SCAN_VOXELS,262144,16777216);
        ARENA_BLOCKS=builder.comment("Actual blocks permitted in one administrator Arena record. Also bounds the work of native one-call respawn placement; this is not a per-tick construction budget.").defineInRange("arenaCaptureBlocks",dev.sablelife.ownership.ArenaRespawnLimits.BLOCKS,1024,1048576);
        ARENA_BLOCK_ENTITIES=builder.comment("Block entities permitted in one administrator Arena record. Limits NBT restoration work during native one-call placement.").defineInRange("arenaCaptureBlockEntities",dev.sablelife.ownership.ArenaRespawnLimits.BLOCK_ENTITIES,256,131072);
        ARENA_TEMPLATE_BYTES=builder.comment("Uncompressed estimated bytes permitted per Arena template at capture and saved-data admission. Required for native one-call respawn memory limits.").defineInRange("arenaTemplateBytes",dev.sablelife.ownership.ArenaRespawnLimits.TEMPLATE_BYTES,1048576,134217728);
        ARENA_WORLD_BYTES=builder.comment("Total uncompressed estimated bytes permitted across world Arena templates.").defineInRange("arenaWorldTemplateBytes",dev.sablelife.ownership.ArenaRespawnLimits.WORLD_TEMPLATE_BYTES,1048576,536870912);
        builder.comment("Server-owned module switches. False hides new modules and suspends existing modules without deleting their settings.").push("modules");
        builder.push("lifeCore");
        for(String type:dev.sablelife.module.VirtualModule.AVAILABLE_TYPES)LIFE_MODULES.put(type,builder.define(type,true));
        builder.pop();
        builder.comment("Only supported control modules are configurable here; this table cannot grant Life Core capabilities.").push("redstoneCore");
        for(String type:dev.sablelife.heart.RedstoneCorePolicy.TYPES)REDSTONE_MODULES.put(type,builder.define(type,dev.sablelife.heart.RedstoneCorePolicy.defaultEnabled(type)));
        builder.pop(2);
        SPEC = builder.build();
    }
    public static int maximumModulesPerCreature(){return SPEC.isLoaded()?MODULES.get():128;}
    public static boolean moduleEnabled(boolean redstoneCore,String type){
        var setting=(redstoneCore?REDSTONE_MODULES:LIFE_MODULES).get(type);
        return setting!=null&&(SPEC.isLoaded()?setting.get():!redstoneCore||dev.sablelife.heart.RedstoneCorePolicy.defaultEnabled(type));
    }
    public static int maximumWirelessChannels() { return SPEC.isLoaded() ? CHANNELS.get() : 256; }
    public static int particlePointsPerPlayerTick() { return SPEC.isLoaded() ? PARTICLES.get() : 8192; }
    public static int deathCleanupBlocksPerTick() { return SPEC.isLoaded() ? CLEANUP.get() : 256; }
    public static int maximumProjectilesPerWorld(){return SPEC.isLoaded()?PROJECTILES.get():8192;}
    public static int maximumProjectilesPerCreature(){return SPEC.isLoaded()?CREATURE_PROJECTILES.get():2048;}
    public static int collisionChecksPerTick(){return SPEC.isLoaded()?COLLISION.get():8388608;}
    public static int arenaCaptureScanVoxels(){return SPEC.isLoaded()?ARENA_CAPTURE_SCAN.get():dev.sablelife.ownership.ArenaRespawnLimits.SCAN_VOXELS;}
    public static int arenaCaptureBlocks(){return SPEC.isLoaded()?ARENA_BLOCKS.get():dev.sablelife.ownership.ArenaRespawnLimits.BLOCKS;}
    public static int arenaCaptureBlockEntities(){return SPEC.isLoaded()?ARENA_BLOCK_ENTITIES.get():dev.sablelife.ownership.ArenaRespawnLimits.BLOCK_ENTITIES;}
    public static int arenaTemplateBytes(){return SPEC.isLoaded()?ARENA_TEMPLATE_BYTES.get():dev.sablelife.ownership.ArenaRespawnLimits.TEMPLATE_BYTES;}
    public static int arenaWorldTemplateBytes(){return SPEC.isLoaded()?ARENA_WORLD_BYTES.get():dev.sablelife.ownership.ArenaRespawnLimits.WORLD_TEMPLATE_BYTES;}
    private SableLifeServerConfig() {}
}
