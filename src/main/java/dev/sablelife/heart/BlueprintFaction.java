package dev.sablelife.heart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Portable faction settings and direct alliance hints, without player records. */
public record BlueprintFaction(UUID sourceWorld, UUID sourceFaction, String name, boolean hasSettings,
        boolean privateFaction, boolean friendlyFire, boolean alliedFire, List<BlueprintFaction.Ally> allies) {
    public static final String TAG = "sablelife:blueprint_faction";
    private static final int VERSION = 1;
    static final int MAX_NAME_POINTS = 40;

    public record Ally(UUID id, String name) {
        public Ally {
            if (!userFaction(id) || !validName(name)) throw new IllegalArgumentException("Invalid blueprint ally");
        }
    }

    public BlueprintFaction {
        if (!userFaction(sourceFaction) || !validName(name) || allies == null
                || allies.size() >= FactionRegistry.MAX_FACTIONS || hasSettings != (sourceWorld != null)) {
            throw new IllegalArgumentException("Invalid blueprint faction");
        }
        if (!hasSettings && (!name.isEmpty() || privateFaction || friendlyFire || alliedFire || !allies.isEmpty())) {
            throw new IllegalArgumentException("Unexpected blueprint faction settings");
        }
        var ids = new HashSet<UUID>();
        ids.add(sourceFaction);
        for (var ally : allies) {
            if (ally == null || !ids.add(ally.id())) throw new IllegalArgumentException("Repeated blueprint ally");
        }
        allies = List.copyOf(allies);
    }

    public static BlueprintFaction legacy(UUID faction) {
        return userFaction(faction) ? new BlueprintFaction(null, faction, "", false, false, false, false, List.of()) : null;
    }

    public static BlueprintFaction read(CompoundTag tag) {
        if (tag == null || !tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != VERSION
                || !tag.hasUUID("SourceFaction") || !tag.contains("Name", Tag.TAG_STRING)
                || !flag(tag, "HasSettings") || !flag(tag, "Private") || !flag(tag, "FriendlyFire")
                || !flag(tag, "AlliedFire") || !(tag.get("Allies") instanceof ListTag rows)
                || rows.size() >= FactionRegistry.MAX_FACTIONS
                || (rows.getElementType() != Tag.TAG_COMPOUND && !(rows.isEmpty() && rows.getElementType() == Tag.TAG_END))
                || (tag.contains("SourceWorld") && !tag.hasUUID("SourceWorld"))) return null;
        try {
            var allies = new ArrayList<Ally>(rows.size());
            for (var value : rows) {
                var ally = (CompoundTag) value;
                if (!ally.hasUUID("Id") || !ally.contains("Name", Tag.TAG_STRING)) return null;
                allies.add(new Ally(ally.getUUID("Id"), ally.getString("Name")));
            }
            return new BlueprintFaction(tag.hasUUID("SourceWorld") ? tag.getUUID("SourceWorld") : null,
                    tag.getUUID("SourceFaction"), tag.getString("Name"), tag.getBoolean("HasSettings"),
                    tag.getBoolean("Private"), tag.getBoolean("FriendlyFire"), tag.getBoolean("AlliedFire"), allies);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        if (sourceWorld != null) tag.putUUID("SourceWorld", sourceWorld);
        tag.putUUID("SourceFaction", sourceFaction);
        tag.putString("Name", name);
        tag.putBoolean("HasSettings", hasSettings);
        tag.putBoolean("Private", privateFaction);
        tag.putBoolean("FriendlyFire", friendlyFire);
        tag.putBoolean("AlliedFire", alliedFire);
        var rows = new ListTag();
        for (var ally : allies) {
            var row = new CompoundTag();
            row.putUUID("Id", ally.id());
            row.putString("Name", ally.name());
            rows.add(row);
        }
        tag.put("Allies", rows);
        return tag;
    }

    static boolean userFaction(UUID id) {
        return id != null && !FactionRegistry.WILDS.equals(id);
    }

    static boolean validName(String name) {
        return name != null && name.length() <= 2 * MAX_NAME_POINTS
                && name.codePointCount(0, name.length()) <= MAX_NAME_POINTS
                && name.codePoints().noneMatch(Character::isISOControl);
    }

    private static boolean flag(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_BYTE) && (tag.getByte(key) == 0 || tag.getByte(key) == 1);
    }
}
