package dev.sablelife.compat.photomancy;

import dev.rew1nd.sableschematicapi.api.blueprint.*;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlock;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.ModuleAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Plot offsets alone are not portable: serialize blueprint-local references instead. */
public final class HeartBlueprintMapper implements SableBlueprintBlockMapper {
    private static final String REF = "sablelife:blueprint_mark";
    private static final String ARENA = "sablelife:blueprint_arena_offset";
    public static void register() {
        SableBlueprintEventRegistry.register(new SableBlueprintEvent(){
            @Override public net.minecraft.resources.ResourceLocation id(){return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sablelife","respawn_placement");}
            @Override public void onPlaceBeforeBlocks(BlueprintPlaceSession session,CompoundTag data){PhotomancyRespawnBridge.observePlacement(session);}
        });
        SableBlueprintMapperRegistry.register(SableLife.HEART_ENTITY.get(), new HeartBlueprintMapper());
        com.mojang.logging.LogUtils.getLogger().info("Registered Sable Life Heart blueprint mapper for Photomancy");
    }

    @Override public CompoundTag save(BlueprintBlockSaveContext context, CompoundTag defaultTag) {
        if (defaultTag == null || !(context.blockEntity() instanceof HeartBlockEntity heart)) return defaultTag;
        var tag = defaultTag.copy();
        for (var entry : tag.getCompound("VirtualModules").getList("Modules", 10)) {
            var module = (CompoundTag) entry;
            for (String key : java.util.List.of("Anchors", "ParticleAreaAnchors", "ParticlePointAnchors")) {
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
            if (module.contains("ArenaCenter")) {
                var center = module.getCompound("ArenaCenter");
                if (center.getString("Dimension").equals(context.level().dimension().location().toString())) {
                    var world = new Vec3(center.getDouble("X"), center.getDouble("Y"), center.getDouble("Z"));
                    var offset = context.subLevel().logicalPose().transformPositionInverse(world).subtract(context.storagePos().getCenter());
                    var relative = new CompoundTag();
                    relative.putDouble("X", offset.x); relative.putDouble("Y", offset.y); relative.putDouble("Z", offset.z);
                    module.put(ARENA, relative);
                }
                module.remove("ArenaCenter");
            }
        }
        resetRuntime(tag);
        return tag;
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
        tag.putUUID("CreatureId", UUID.randomUUID());
        for (var entry : tag.getCompound("VirtualModules").getList("Modules", 10)) {
            var module = (CompoundTag) entry;
            if (module.hasUUID("Id")) module.putUUID("Id", context.allocateMappedUuid(module.getUUID("Id")));
            else module.putUUID("Id", UUID.randomUUID());
            if (module.getString("Type").equals("command")) module.putBoolean("CommandApprovalRequired", true);
            for (var moveValue : module.getList("Moves", 10)) {
                var move = (CompoundTag)moveValue;
                var config = move.getCompound("Config");
                if (config.hasUUID("Id")) config.putUUID("Id", context.allocateMappedUuid(config.getUUID("Id")));
                for (var actionValue : move.getList("Actions", 10)) {
                    var action = (CompoundTag)actionValue;
                    if (action.hasUUID("Module")) action.putUUID("Module", context.allocateMappedUuid(action.getUUID("Module")));
                }
            }
            module.remove("RewardOwner"); module.remove("Detached"); module.remove("Invalidated");
            for (String key : java.util.List.of("Anchors", "ParticleAreaAnchors", "ParticlePointAnchors")) {
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
        var modules = loadedTag.getCompound("VirtualModules").getList("Modules", 10).copy();
        context.deferAfterBlockEntities(() -> {
            for (var entry : modules) {
                var tag = (CompoundTag) entry;
                if (!tag.contains(ARENA) || !tag.hasUUID("Id")) continue;
                var module = heart.modules().find(tag.getUUID("Id"));
                if (module == null || !module.type().equals("arena")) continue;
                var offset = tag.getCompound(ARENA);
                var local = context.storagePos().getCenter().add(offset.getDouble("X"), offset.getDouble("Y"), offset.getDouble("Z"));
                module.arenaCenter(context.subLevel().logicalPose().transformPosition(local), context.level().dimension().location().toString());
            }
            if (heart.getBlockState().getValue(HeartBlock.ACTIVE))
                context.level().setBlock(heart.getBlockPos(), heart.getBlockState().setValue(HeartBlock.ACTIVE, false), 3);
            heart.modules().changed(); heart.setChanged();
        });
    }

    private static void resetRuntime(CompoundTag tag) {
        tag.remove("BoundBody"); tag.remove("CombatMusicHits");
        tag.putString("LifeState", "DORMANT"); tag.putFloat("Health", tag.getFloat("MaxHealth"));
        tag.putInt("RemainingDeathTicks", 0);
        tag.getCompound("Wireless").putLong("Elapsed", 0);
    }
}
