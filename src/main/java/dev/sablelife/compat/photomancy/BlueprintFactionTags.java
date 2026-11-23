package dev.sablelife.compat.photomancy;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockPlaceContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockSaveContext;
import dev.sablelife.heart.BlueprintFaction;
import dev.sablelife.heart.FactionRegistry;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

final class BlueprintFactionTags {
    private static final String RELATIONS = "Relations";
    private static final String FACTION = "Faction";
    private static final String FACTIONS = "sablelife:blueprint_factions";
    private static final String REFERENCE = "sablelife:blueprint_faction_ref";
    private static final int MAX_CATALOG_ENTRIES = 256;
    private static final int REFERENCE_LENGTH = 80;

    static void export(BlueprintBlockSaveContext context, HeartBlockEntity heart, CompoundTag tag) {
        var relations = heart.relations();
        var activeFaction = relations.faction();
        var descriptor = relations.blueprintFaction();
        if (activeFaction != null) {
            var registry = FactionRegistry.get(context.level().getServer());
            if (registry.contains(activeFaction)) descriptor = registry.portable(activeFaction);
        }
        if (descriptor == null && activeFaction != null) descriptor = BlueprintFaction.legacy(activeFaction);
        if (descriptor == null) return;

        var relationTag = tag.getCompound(RELATIONS);
        tag.put(RELATIONS, relationTag);
        var global = context.session().globalExtraData();
        var definitions = global.getCompound(FACTIONS);
        var id = descriptor.sourceFaction();
        var world = descriptor.sourceWorld();
        var key = (world == null ? "legacy" : world.toString()) + "/" + id;
        var portable = descriptor.save();
        // Preserve a differing descriptor or an oversized catalog inline so re-export never loses it.
        boolean existing = definitions.contains(key, Tag.TAG_COMPOUND);
        if ((!existing && definitions.size() < MAX_CATALOG_ENTRIES) || (existing && definitions.getCompound(key).equals(portable))) {
            if (!existing) {
                definitions.put(key, portable);
                global.put(FACTIONS, definitions);
            }
            relationTag.remove(BlueprintFaction.TAG);
            relationTag.putString(REFERENCE, key);
        } else {
            relationTag.remove(REFERENCE);
            relationTag.put(BlueprintFaction.TAG, portable);
        }
    }

    static void restore(BlueprintBlockPlaceContext context, CompoundTag tag) {
        var relations = tag.getCompound(RELATIONS);
        var sourceFaction = relations.hasUUID(FACTION) ? relations.getUUID(FACTION) : null;
        var descriptor = BlueprintFaction.read(relations.getCompound(BlueprintFaction.TAG));
        String reference = relations.getString(REFERENCE);
        relations.remove(REFERENCE);
        if (descriptor == null && !reference.isEmpty() && reference.length() <= REFERENCE_LENGTH) {
            var definitions = context.session().globalExtraData().getCompound(FACTIONS);
            if (definitions.size() <= MAX_CATALOG_ENTRIES) descriptor = BlueprintFaction.read(definitions.getCompound(reference));
        }
        if (descriptor == null && sourceFaction != null) descriptor = BlueprintFaction.legacy(sourceFaction);
        relations.remove(FACTION);
        relations.remove(BlueprintFaction.TAG);
        var level = context.level();
        var session = context.session();
        var actor = BlueprintFactionPlacement.actor(level, session);
        boolean trusted = actor == null && (BlueprintFactionPlacement.trustedLocal(level, session)
                || PhotomancyRespawnBridge.trustedFaction(session));
        if (descriptor == null) {
            if (trusted && FactionRegistry.WILDS.equals(sourceFaction)) relations.putUUID(FACTION, sourceFaction);
            return;
        }

        var registry = FactionRegistry.get(level.getServer());
        UUID localFaction = null;
        if (actor != null) {
            localFaction = registry.resolveBlueprintFaction(actor, descriptor);
        } else if (trusted && descriptor.sourceFaction().equals(sourceFaction)) {
            // A server template keeps an existing assignment. Pending imports still need a player decision.
            localFaction = registry.resolveTrustedBlueprintFaction(descriptor);
        }
        relations.put(BlueprintFaction.TAG, descriptor.save());
        if (localFaction != null) relations.putUUID(FACTION, localFaction);
        tag.put(RELATIONS, relations);
    }

    private BlueprintFactionTags() {}
}
