package dev.sablelife.module;

import net.minecraft.nbt.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/** Bounds untrusted authoring input before recursive parsing or copying. World saves use their own codec. */
public final class AuthoringLimits {
    public static final int MAX_DEPTH = 64, MAX_NODES = 8192, MAX_PRESET_BYTES = 65536;
    private record Node(Tag tag, int depth) {}

    public static boolean presetText(String text) {
        if (text.length() > ModulePreset.MAX_TEXT || text.getBytes(StandardCharsets.UTF_8).length > MAX_PRESET_BYTES) return false;
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') quote = c;
            else if (c == '{' || c == '[') { if (++depth > MAX_DEPTH) return false; }
            else if (c == '}' || c == ']') { if (--depth < 0) return false; }
        }
        return depth == 0 && quote == 0;
    }

    public static boolean compound(CompoundTag tag, long maximumBytes) {
        var pending = new ArrayDeque<Node>();
        pending.push(new Node(tag, 0));
        int nodes = 0;
        int maximumNodes = (int)Math.min(131072, Math.max(MAX_NODES, maximumBytes / 8));
        long bytes = 0;
        while (!pending.isEmpty()) {
            var node = pending.pop();
            if (++nodes > maximumNodes || node.depth > MAX_DEPTH) return false;
            bytes += 16;
            if (node.tag instanceof CompoundTag compound) {
                if (compound.size() > maximumNodes - nodes) return false;
                for (var key : compound.getAllKeys()) {
                    bytes += key.getBytes(StandardCharsets.UTF_8).length + 3;
                    pending.push(new Node(compound.get(key), node.depth + 1));
                }
            } else if (node.tag instanceof ListTag list) {
                if (list.size() > maximumNodes - nodes) return false;
                for (var value : list) pending.push(new Node(value, node.depth + 1));
            } else if (node.tag instanceof StringTag string) bytes += string.getAsString().getBytes(StandardCharsets.UTF_8).length;
            else if (node.tag instanceof ByteArrayTag array) bytes += array.getAsByteArray().length;
            else if (node.tag instanceof IntArrayTag array) bytes += (long)array.getAsIntArray().length * 4;
            else if (node.tag instanceof LongArrayTag array) bytes += (long)array.getAsLongArray().length * 8;
            if (bytes > maximumBytes) return false;
        }
        return true;
    }

    public static boolean containsCommands(CompoundTag configuration) {
        for (var value : configuration.getList("Modules", 10))
            if (((CompoundTag)value).getString("Type").equals("command")) return true;
        return false;
    }
    private AuthoringLimits() {}
}
