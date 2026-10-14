package dev.sablelife.module;

import net.minecraft.nbt.*;
import java.util.*;

/** Named moves with stable module references. Timings are server ticks, not wireless channels. */
public final class MoveSet {
    public static final int LIMIT = 32, ACTION_LIMIT = 16, MAX_TICKS = 12000;
    public static final Set<String> TARGETS = Set.of("sphere_blast", "slash", "arrow", "flame", "gun", "mortar", "homing", "laser",
            "ground_strike", "quake", "pull", "pulse", "melee", "particles", "item_display", "damage_accumulation", "trigger_sound", "wireless_redstone", "command", "linear_movement", "auto_facing", "signal_turn", "gyroscope", "hover_anchor", "sequence", "health_adjustment", "sensor", "navigation", "sound", "bgm");
    public record Action(UUID module, int start, int duration) {
        public Action {
            Objects.requireNonNull(module);
            start = Math.clamp(start, 0, MAX_TICKS - 1);
            duration = Math.clamp(duration, 1, MAX_TICKS - start);
        }
        public int end() { return start + duration; }
        public boolean active(long tick) { return tick >= start && tick < end(); }
    }
    public static final class Move {
        private final MoveRules config;
        private final List<Action> actions = new ArrayList<>();
        private boolean priority;
        public boolean priority() { return priority; }
        public Move() { config = new MoveRules(); }
        private Move(MoveRules config) { this.config = config; }
        public MoveRules config() { return config; }
        public List<Action> actions() { return List.copyOf(actions); }
        public int length() { return actions.stream().mapToInt(Action::end).max().orElse(0); }
        public CompoundTag save() {
            var tag = new CompoundTag(); tag.put("Config", config.save()); tag.putBoolean("Priority", priority); var list = new ListTag();
            for (var a : actions) { var t = new CompoundTag(); t.putUUID("Module", a.module()); t.putInt("Start", a.start()); t.putInt("Duration", a.duration()); list.add(t); }
            tag.put("Actions", list); return tag;
        }
    }
    private final List<Move> moves = new ArrayList<>();
    public List<Move> moves() { return List.copyOf(moves); }
    public Move get(int index) { return index >= 0 && index < moves.size() ? moves.get(index) : null; }
    public ListTag save() { var list = new ListTag(); moves.forEach(m -> list.add(m.save())); return list; }
    public void load(ListTag list) {
        moves.clear(); var ids = new HashSet<UUID>();
        for (int i = 0; i < Math.min(LIMIT, list.size()); i++) {
            var tag = list.getCompound(i); var configTag = tag.getCompound("Config").copy();
            if(configTag.getInt("Version")>1)continue;var config = MoveRules.load(configTag);
            if (!ids.add(config.id())) continue;
            var move = new Move(config); move.priority = tag.getBoolean("Priority"); var actions = tag.getList("Actions", 10);
            for (int j = 0; j < Math.min(ACTION_LIMIT, actions.size()); j++) { var a = actions.getCompound(j);
                if (a.hasUUID("Module")) move.actions.add(new Action(a.getUUID("Module"), a.getInt("Start"), a.getInt("Duration")));
            }
            moves.add(move);
        }
    }
    public void remap(Map<UUID, UUID> ids) {
        for (var move : moves) for (int i = 0; i < move.actions.size(); i++) {
            var a = move.actions.get(i); move.actions.set(i, new Action(ids.getOrDefault(a.module(), a.module()), a.start(), a.duration()));
        }
    }
    /** Commands use row/action indices under the dashboard's revision lock. */
    public boolean edit(String key, double value, String text, HeartModules modules) {
        if (!Double.isFinite(value)) return false;
        if (key.equals("add")) { if (moves.size() >= LIMIT) return false; moves.add(new Move()); return true; }
        try {
            String[] parts = text.split("\\|", 3); int index = Integer.parseInt(parts[0]); var move = get(index); if (move == null) return false;
            switch (key) {
                case "delete" -> moves.remove(index);
                case "duplicate" -> {
                    if (moves.size() >= LIMIT) return false;
                    var tag = move.save(); tag.getCompound("Config").putUUID("Id", UUID.randomUUID());
                    var copy = new MoveSet(); var list = new ListTag(); list.add(tag); copy.load(list); moves.add(index + 1, copy.get(0));
                }
                case "up", "down" -> { int next = index + (key.equals("up") ? -1 : 1); if (next < 0 || next >= moves.size()) return false; Collections.swap(moves, index, next); }
                case "name" -> { return move.config.edit("name", 0, parts.length > 1 ? text.substring(text.indexOf('|') + 1) : ""); }
                case "enabled" -> { return move.config.edit("enabled", value, ""); }
                case "selection_mode" -> { if (value != 0 && value != 1) return false; move.priority = value == 1; }
                case "action_add" -> {
                    if (move.actions.size() >= ACTION_LIMIT || parts.length < 2) return false;
                    var target = modules.find(UUID.fromString(parts[1])); if (target == null || !TARGETS.contains(target.type())) return false;
                    move.actions.add(new Action(target.id(), 0, 60)); target.edit("trigger", 4, "");
                }
                case "action_up", "action_down" -> {
                    if (parts.length < 2) return false; int n = Integer.parseInt(parts[1]);
                    int next = n + (key.equals("action_up") ? -1 : 1);
                    if (n < 0 || n >= move.actions.size() || next < 0 || next >= move.actions.size()) return false;
                    Collections.swap(move.actions, n, next);
                }
                case "action_delete", "action_start", "action_duration" -> {
                    if (parts.length < 2) return false; int n = Integer.parseInt(parts[1]);
                    if (n < 0 || n >= move.actions.size()) return false; var old = move.actions.get(n);
                    if (key.equals("action_delete")) move.actions.remove(n);
                    else { if (value < 0 || value > 600) return false;
                        int ticks = (int)Math.round(value * 20);
                        move.actions.set(n, key.equals("action_start") ? new Action(old.module(), ticks, old.duration()) : new Action(old.module(), old.start(), ticks));
                    }
                }
                default -> {
                    if (!Set.of("dispatchWeight", "dispatchCooldown", "dispatchRest", "dispatchBusy", "target", "dispatchNear", "dispatchFar", "phaseLower", "phaseUpper", "dispatchCone", "dispatchFront", "lineOfSight", "dispatchRepeat").contains(key)) return false;
                    return move.config.edit(key, value, "");
                }
            }
            return true;
        } catch (IllegalArgumentException ignored) { return false; }
    }
}
