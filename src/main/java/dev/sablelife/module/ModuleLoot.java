package dev.sablelife.module;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import dev.sablelife.ownership.BodyOwnership;
import dev.sablelife.ownership.LifeLifecycle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The marked surviving block identifies its detached connected fragment, never a box around the core. */
public final class ModuleLoot {
    public static void tick(HeartBlockEntity heart) {
        if (!(heart.getLevel() instanceof ServerLevel server) || heart.health().state() != HeartState.ALIVE) return;
        var root = Sable.HELPER.getContaining(server, heart.getBlockPos()); if (root == null) return;
        for (var module : heart.modules().all()) {
            if (!ModuleAvailability.enabled(heart,module) || !module.known() || !module.type().equals("loot") || !module.enabled() || module.invalidated() || module.anchors().size() != 1
                    || heart.creatureId().equals(module.rewardOwner())) continue;
            var anchor = module.anchors().getFirst(); var body = anchor.body(heart);
            if (body == null || body.isRemoved() || !server.hasChunkAt(anchor.position(body))) continue;
            // Includes the marked rigid body and its still-connected children, but cannot include Heart.
            var chain = SubLevelHelper.getConnectedChain(body); if (chain.contains(root)) continue;
            var ownership = BodyOwnership.get(server); var pos = anchor.position(body);
            if (!heart.creatureId().equals(ownership.owner(pos))) continue;
            var id = UUID.nameUUIDFromBytes((heart.creatureId() + ":loot:" + module.id()).getBytes(StandardCharsets.UTF_8));
            if (!ownership.claimPart(server, pos, heart.creatureId(), id)) continue;
            var templates = new ArrayList<ItemStack>(); var tag = module.lootSamples(); var chances = new int[27]; var weights = new int[27];
            for (int i = 0; i < 27; i++) {
                templates.add(ItemStack.parseOptional(server.registryAccess(), tag.getCompound("Slot" + i)));
                chances[i] = tag.contains("Chance" + i) ? Math.clamp(tag.getInt("Chance" + i), 0, 100) : 100;
                weights[i] = tag.contains("Weight" + i) ? Math.clamp(tag.getInt("Weight" + i), 0, 1000) : 1;
            }
            var imported = module.importData();
            var table = ResourceLocation.tryParse(imported.getString("Loot"));
            LifeLifecycle.get(server.getServer()).schedulePart(server, id, heart.creatureId(), module.name().isBlank() ? "limb" : module.name(),
                    anchor.world(heart, false), imported.contains("Delay") ? imported.getInt("Delay") : 60,
                    table == null ? ResourceLocation.withDefaultNamespace("empty") : table, imported.getInt("Experience"),
                    imported.getBoolean("TableMode") ? null : templates, chances, weights, module.value("weighted") != 0, (int) module.value("rolls"));
            module.rewardOwner(heart.creatureId()); module.detach(); heart.modules().changed(); heart.setChanged();
        }
    }
    private ModuleLoot() {}
}
