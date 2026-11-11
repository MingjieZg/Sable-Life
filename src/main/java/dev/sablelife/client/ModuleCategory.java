package dev.sablelife.client;

/** Display-only categories; no effect on module order, settings or execution. */
public enum ModuleCategory {
    WEAPON("weapon", 0xFFE45C61), FUNCTION("function", 0xFFE9C554), SIGNAL("signal", 0xFFAD75DF);
    public final String key;
    public final int color;
    ModuleCategory(String key, int color) { this.key = key; this.color = color; }
    public static ModuleCategory of(String type) {
        var addon = dev.sablelife.module.ModuleTypes.find(type);
        if (addon != null) return valueOf(addon.category().name());
        return switch (type) {
            case "summoned_weapon", "surface_field", "sphere_blast", "slash", "melee", "flame", "arrow", "gun", "mortar", "homing", "laser", "ground_strike", "quake", "pull", "freeze", "grab", "pulse" -> WEAPON;
            case "move_set", "health_phase","signal_logic","combat_event", "hit_counter", "damage_accumulation", "sequence", "wireless_redstone", "command", "sensor", "navigation" -> SIGNAL;
            default -> FUNCTION;
        };
    }
}
