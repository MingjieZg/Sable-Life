package dev.sablelife.compat.photomancy;

import dev.rew1nd.sableschematicapi.api.blueprint.*;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlock;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.HeartModules;
import dev.sablelife.module.ModuleAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.UUID;

/** Plot offsets alone are not portable: serialize blueprint-local references instead. */
public final class HeartBlueprintMapper implements SableBlueprintBlockMapper {
    private static final String REF = "sablelife:blueprint_mark";
    private static final String ARENA = "sablelife:blueprint_arena_offset";
    private static final String CONNECTED_PARTS = "sablelife:blueprint_connected_parts";
    public static void register() {
        SableBlueprintEventRegistry.register(new SableBlueprintEvent(){
            @Override public net.minecraft.resources.ResourceLocation id(){return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sablelife","respawn_placement");}
            @Override public void onPlaceBeforeBlocks(BlueprintPlaceSession session, CompoundTag data) {
                BlueprintFactionPlacement.observe(session.level(), session);
                PhotomancyRespawnBridge.observePlacement(session);
            }
        });
        SableBlueprintMapperRegistry.register(SableLife.HEART_ENTITY.get(), new HeartBlueprintMapper());
        com.mojang.logging.LogUtils.getLogger().info("Registered Sable Life Heart blueprint mapper for Photomancy");
    }

    @Override public CompoundTag save(BlueprintBlockSaveContext context, CompoundTag defaultTag) {
        if (defaultTag == null || !(context.blockEntity() instanceof HeartBlockEntity heart)) return defaultTag;
        var tag = defaultTag.copy();
        resetRuntime(tag);
        BlueprintFactionTags.export(context, heart, tag);
        var references = dev.sablelife.ownership.BlueprintSpawns.references(heart);
        if (!references.isEmpty()) tag.put(dev.sablelife.ownership.BlueprintSpawns.SAVED_REFERENCES,references);
        var parts = dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(context.subLevel()).stream()
                .filter(body -> !body.isRemoved())
                .flatMap(body -> context.subLevelRef(body.getUniqueId()).stream())
                .mapToInt(BlueprintSubLevelRef::subLevelId).distinct().toArray();
        tag.putIntArray(CONNECTED_PARTS, parts);
        for (var entry : tag.getCompound("VirtualModules").getList("Modules", 10)) {
            var module = (CompoundTag) entry;
            for (String key : java.util.List.of("Anchors", "ParticleAreaAnchors", "ParticlePointAnchors", "WatchedPart")) {
                if (!module.contains(key)) continue;
                var marks = new ListTag();
                for (var mark : module.getList(key, 10)) {
                    var saved = saveMark(context, heart, (CompoundTag)mark);
                    if (saved != null) marks.add(saved);
                }
                module.put(key, marks);
            }
            if (module.contains("ImportSource")) {
                var saved = saveMark(context, heart, module.getCompound("ImportSource"));
                module.remove("ImportSource");
                if (saved != null) module.put("ImportSource", saved);
            }
            if (module.contains("ArenaCenter") && !module.getString("Type").equals("arena")) {
                var center = module.getCompound("ArenaCenter");
                if (center.getString("Dimension").equals(context.level().dimension().location().toString())) {
                    var world = new Vec3(center.getDouble("X"), center.getDouble("Y"), center.getDouble("Z"));
                    var offset = context.subLevel().logicalPose().transformPositionInverse(world).subtract(context.storagePos().getCenter());
                    var relative = new CompoundTag();
                    relative.putDouble("X", offset.x); relative.putDouble("Y", offset.y); relative.putDouble("Z", offset.z);
                    module.put(ARENA, relative);
                }
            }
            module.remove("ArenaCenter");
        }
        mapWirelessFaces(tag, mark -> saveMark(context, heart, mark));
        return tag;
    }

    private static void mapWirelessFaces(CompoundTag tag, java.util.function.UnaryOperator<CompoundTag> mapper) {
        var key = dev.sablelife.heart.WirelessChannel.OUTPUT_FACES_TAG;
        for (var value : tag.getCompound("Wireless").getList("Channels", 10)) {
            var channel = (CompoundTag)value;
            if (!channel.contains(key)) continue;
            var faces = new ListTag();
            for (var mark : channel.getList(key, 10)) {
                var mapped = mapper.apply((CompoundTag)mark);
                if (mapped != null) {
                    var nameKey = dev.sablelife.heart.WirelessChannel.OUTPUT_FACE_NAME;
                    if (((CompoundTag)mark).contains(nameKey,8))
                        mapped.putString(nameKey,((CompoundTag)mark).getString(nameKey));
                    faces.add(mapped);
                }
            }
            channel.put(key, faces);
        }
    }

    private static CompoundTag saveMark(BlueprintBlockSaveContext context, HeartBlockEntity heart, CompoundTag original) {
        var anchor = ModuleAnchor.load(original);
        if (anchor == null) return null;
        var body = anchor.body(heart);
        if (body == null || body.isRemoved()) return null;
        var pos = anchor.position(body);
        if (context.level().getBlockState(pos).isAir()) return null;
        var ref = context.blockRef(pos);
        if (ref.isEmpty() || context.subLevelRef(anchor.bodyId()).isEmpty()) return null;
        var result = new CompoundTag();
        result.putString("Face", anchor.face().getName());
        var local = new CompoundTag(); local.putInt("Part", ref.get().subLevelId()); local.putLong("Pos", ref.get().localPos().asLong());
        result.put(REF, local);
        return result;
    }

    @Override public void beforeLoadBlockEntity(BlueprintBlockPlaceContext context, CompoundTag tag) {
        resetRuntime(tag);
        tag.remove(dev.sablelife.ownership.BlueprintSpawns.SAVED_REFERENCES);
        tag.putUUID("CreatureId", UUID.randomUUID());
        BlueprintFactionTags.restore(context, tag);
        mapWirelessFaces(tag, mark -> placeMark(context, mark));
        for (var entry : tag.getCompound("VirtualModules").getList("Modules", 10)) {
            var module = (CompoundTag) entry;
            if (module.hasUUID("Id")) module.putUUID("Id", context.allocateMappedUuid(module.getUUID("Id")));
            else module.putUUID("Id", UUID.randomUUID());
            var hitSettings=module.getCompound("Settings");if(module.getString("Type").equals("hit_counter")&&hitSettings.hasUUID("HitSourceId"))hitSettings.putUUID("HitSourceId",context.allocateMappedUuid(hitSettings.getUUID("HitSourceId")));
            // References must share the placement UUID map with their target modules, even when the target appears later.
            if(hitSettings.hasUUID("SummonAttackId"))hitSettings.putUUID("SummonAttackId",context.allocateMappedUuid(hitSettings.getUUID("SummonAttackId")));
            if (hitSettings.hasUUID("FacingSensorId")) hitSettings.putUUID("FacingSensorId", context.allocateMappedUuid(hitSettings.getUUID("FacingSensorId")));
            if(hitSettings.hasUUID("ImpactFieldId"))hitSettings.putUUID("ImpactFieldId",context.allocateMappedUuid(hitSettings.getUUID("ImpactFieldId")));
            if (dev.sablelife.module.AuthoringLimits.requiresOperator(module.getString("Type")))
                module.putBoolean("CommandApprovalRequired", true);
            for (var moveValue : module.getList("Moves", 10)) {
                var move = (CompoundTag)moveValue;
                var config = move.getCompound("Config");
                if (config.hasUUID("Id")) config.putUUID("Id", context.allocateMappedUuid(config.getUUID("Id")));
                for (var actionValue : move.getList("Actions", 10)) {
                    var action = (CompoundTag)actionValue;
                    if (action.hasUUID("Module")) action.putUUID("Module", context.allocateMappedUuid(action.getUUID("Module")));
                }
            }
            for(var value:module.getList("SignalRules",10)){var row=(CompoundTag)value;if(row.hasUUID("Source"))row.putUUID("Source",context.allocateMappedUuid(row.getUUID("Source")));}
            module.remove("RewardOwner"); module.remove("Detached"); module.remove("Invalidated");
            for (String key : java.util.List.of("Anchors", "ParticleAreaAnchors", "ParticlePointAnchors", "WatchedPart")) {
                if (!module.contains(key)) continue;
                var marks = new ListTag();
                for (var mark : module.getList(key, 10)) {
                    var mapped = placeMark(context, (CompoundTag)mark);
                    if (mapped != null) marks.add(mapped);
                }
                module.put(key, marks);
            }
            if (module.contains("ImportSource")) {
                var mapped = placeMark(context, module.getCompound("ImportSource"));
                module.remove("ImportSource");
                if (mapped != null) module.put("ImportSource", mapped);
            }
            // Arena migration is finalized after all body poses and block entities are placed.
            module.remove("ArenaCenter");
        }
        stageDisplayItems(context, tag);
    }

    // The placement session owns these tags until this specific heart loads successfully.
    // Photomancy passes copies to the mapper, so the original blueprint remains reusable.
    private static String displayItemsKey(UUID placedHeart) {
        return "sablelife:pending_display_items/" + placedHeart;
    }

    private static void stageDisplayItems(BlueprintBlockPlaceContext context, CompoundTag tag) {
        var modules = tag.getCompound("VirtualModules").getList("Modules", 10);
        var items = new CompoundTag();
        var seen = new HashSet<UUID>();
        for (int i = 0; i < Math.min(modules.size(), HeartModules.LIMIT); i++) {
            var row = modules.getCompound(i);
            if (row.getString("Type").equals("dispatch")) continue;
            var id = row.getUUID("Id");
            // HeartModules.load keeps the first row for a duplicate UUID.
            if (!seen.add(id) || !row.getString("Type").equals("item_display") || row.getInt("Version") > 1) continue;
            var item = row.getCompound("ProjectileItem");
            if (!item.isEmpty()) {
                items.put(id.toString(), item);
                row.remove("ProjectileItem");
            }
        }
        var sessionData = context.session().globalExtraData();
        var key = displayItemsKey(tag.getUUID("CreatureId"));
        if (items.isEmpty()) sessionData.remove(key);
        else sessionData.put(key, items);
    }

    private static CompoundTag placeMark(BlueprintBlockPlaceContext context, CompoundTag original) {
        if (!original.contains(REF)) return null; // Old blueprints must never retain references to the original boss.
        var ref = original.getCompound(REF);
        if (!ref.contains("Part") || !ref.contains("Pos")) return null;
        var body = context.session().placedSubLevel(ref.getInt("Part"));
        var pos = context.mapBlock(new BlueprintBlockRef(ref.getInt("Part"), BlockPos.of(ref.getLong("Pos"))));
        var face = net.minecraft.core.Direction.byName(original.getString("Face"));
        if (body == null || pos == null || face == null) return null;
        return new ModuleAnchor(body.getUniqueId(), pos.subtract(body.getPlot().getCenterBlock()), face).save();
    }

    @Override public void afterLoadBlockEntity(BlueprintBlockPlaceContext context, BlockEntity entity, CompoundTag loadedTag) {
        if (!(entity instanceof HeartBlockEntity heart)) return;
        var sessionData = context.session().globalExtraData();
        var key = displayItemsKey(heart.creatureId());
        if (sessionData.contains(key, 10)) {
            var items = sessionData.getCompound(key);
            for (var module : heart.modules().all()) {
                var item = items.get(module.id().toString());
                if (item instanceof CompoundTag model) module.restoreBlueprintDisplayItem(model);
            }
            sessionData.remove(key);
            heart.setChanged();
        }
        // Synaxis can publish its connections several ticks after Photomancy places the bodies.
        heart.blueprintPlaced();
        for (int id : loadedTag.getIntArray(CONNECTED_PARTS)) {
            var body = context.session().placedSubLevel(id);
            if (body != null) heart.expectBlueprintConnection(body.getUniqueId());
        }
        var modules = loadedTag.getCompound("VirtualModules").getList("Modules", 10).copy();
        context.deferAfterBlockEntities(() -> {
            for (var entry : modules) {
                var tag = (CompoundTag) entry;
                if (!tag.contains(ARENA) || !tag.hasUUID("Id")) continue;
                var module = heart.modules().find(tag.getUUID("Id"));
                if (module == null || !module.supportsFixedPoint() || module.type().equals("arena")) continue;
                var offset = tag.getCompound(ARENA);
                var local = context.storagePos().getCenter().add(offset.getDouble("X"), offset.getDouble("Y"), offset.getDouble("Z"));
                if (module.type().equals("linear_movement")) {
                    // Photomancy finalizes the body's center of mass and pose after the block entity hooks.
                    heart.deferBlueprintWaypoint(module.id(), local);
                } else {
                    module.arenaCenter(context.subLevel().logicalPose().transformPosition(local), context.level().dimension().location().toString());
                }
            }
            if (heart.getBlockState().getValue(HeartBlock.ACTIVE))
                context.level().setBlock(heart.getBlockPos(), heart.getBlockState().setValue(HeartBlock.ACTIVE, false), 3);
            heart.modules().changed(); heart.setChanged();
        });
    }

    private static void resetRuntime(CompoundTag tag) {
        tag.remove("PendingBlueprintActivation");
        tag.remove("BoundBody"); tag.remove("CombatMusicHits");
        tag.remove(HeartBlockEntity.REMOTE_ID_TAG);
        boolean survival=tag.contains("SurvivalTokens",4);
        if(survival)tag.putLong("SurvivalTokens",0);
        dev.sablelife.heart.SurvivalFuel.clearBlueprintContents(tag);
        tag.putString("LifeState", "DORMANT"); tag.putFloat("Health", survival?0:tag.getFloat("MaxHealth"));
        tag.putInt("RemainingDeathTicks", 0);
        tag.getCompound("Wireless").putLong("Elapsed", 0);
    }
}
