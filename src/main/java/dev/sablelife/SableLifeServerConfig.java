package dev.sablelife;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Resource limits are server-owned. Existing saved channel numbers are never truncated. */
public final class SableLifeServerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue CHANNELS, PARTICLES, CLEANUP;
    private static final ModConfigSpec.IntValue PROJECTILES, CREATURE_PROJECTILES;
    private static final ModConfigSpec.IntValue COLLISION;
    static {
        var builder = new ModConfigSpec.Builder();
        CHANNELS = builder.comment("Maximum newly authored wireless channels per core. Existing saves keep their channels.")
                .defineInRange("maximumWirelessChannels", 256, 1, 65535);
        PARTICLES = builder.comment("Visual particle points per player per server tick. Damage and timing are unaffected.")
                .defineInRange("particlePointsPerPlayerTick", 8192, 256, 65536);
        CLEANUP = builder.comment("Successful death-cleanup block removals per server tick, shared across jobs and dimensions.")
                .defineInRange("deathCleanupBlocksPerTick", 256, 16, 4096);
        PROJECTILES = builder.comment("Maximum loaded Sable Life projectiles per dimension. Saved projectiles are never discarded by this limit.")
                .defineInRange("maximumProjectilesPerWorld",8192,256,65536);
        CREATURE_PROJECTILES = builder.comment("Maximum new Sable Life projectiles for one creature. Existing flights continue when the limit is reached.")
                .defineInRange("maximumProjectilesPerCreature",2048,64,16384);
        COLLISION=builder.comment("Terrain voxel visits and body broad-phase checks per dimension/tick. Movement waits when exhausted. Minimum permits one maximum-size 16-step rotation check.")
                .defineInRange("collisionChecksPerTick",8388608,4194304,16777216);
        SPEC = builder.build();
    }
    public static int maximumWirelessChannels() { return SPEC.isLoaded() ? CHANNELS.get() : 256; }
    public static int particlePointsPerPlayerTick() { return SPEC.isLoaded() ? PARTICLES.get() : 8192; }
    public static int deathCleanupBlocksPerTick() { return SPEC.isLoaded() ? CLEANUP.get() : 256; }
    public static int maximumProjectilesPerWorld(){return SPEC.isLoaded()?PROJECTILES.get():8192;}
    public static int maximumProjectilesPerCreature(){return SPEC.isLoaded()?CREATURE_PROJECTILES.get():2048;}
    public static int collisionChecksPerTick(){return SPEC.isLoaded()?COLLISION.get():8388608;}
    private SableLifeServerConfig() {}
}
