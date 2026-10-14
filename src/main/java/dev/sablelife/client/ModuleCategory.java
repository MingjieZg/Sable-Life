package dev.sablelife.client;

/** Display-only categories; no effect on module order, settings or execution. */
public enum ModuleCategory {
    WEAPON("weapon", 0xFFE45C61), FUNCTION("function", 0xFFE9C554), SIGNAL("signal", 0xFFAD75DF);
    public final String key;
    public final int color;
    ModuleCategory(String key, int color) { this.key = key; this.color = color; }
    public static ModuleCategory of(String type) {
        return switch (type) {
            case "sphere_blast", "slash", "melee", "flame", "arrow", "gun", "mortar", "homing", "laser", "ground_strike", "quake", "pull", "pulse" -> WEAPON;
            case "move_set", "health_phase", "hit_counter", "damage_accumulation", "sequence", "wireless_redstone", "command", "sensor", "navigation" -> SIGNAL;
            default -> FUNCTION;
        };
    }
}
