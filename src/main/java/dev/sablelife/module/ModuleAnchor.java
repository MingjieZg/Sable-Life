package dev.sablelife.module;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Stored relative to the marked limb's plot, never relative to Heart or the world. */
public record ModuleAnchor(UUID bodyId, BlockPos offset, Direction face) {
    public static ModuleAnchor create(HeartBlockEntity heart, BlockPos pos, Direction face) {
        var level = heart.getLevel(); var body = Sable.HELPER.getContaining(level, pos);
        var root = Sable.HELPER.getContaining(level, heart.getBlockPos());
        if (body == null || root == null || !SubLevelHelper.getConnectedChain(root).contains(body)
                || level.getBlockState(pos).isAir()) return null;
        return new ModuleAnchor(body.getUniqueId(), pos.subtract(body.getPlot().getCenterBlock()), face);
    }
    public SubLevel body(HeartBlockEntity heart) { return SubLevelContainer.getContainer(heart.getLevel()).getSubLevel(bodyId); }
    public BlockPos position(SubLevel body) { return body.getPlot().getCenterBlock().offset(offset); }
    public String status(HeartBlockEntity heart) {
        var body = body(heart); if (body == null || body.isRemoved()) return "unloaded";
        var root = Sable.HELPER.getContaining(heart.getLevel(), heart.getBlockPos());
        if (root == null || !SubLevelHelper.getConnectedChain(root).contains(body)) return "disconnected";
        var pos = position(body);
        if (!heart.getLevel().hasChunkAt(pos)) return "unloaded";
        return heart.getLevel().getBlockState(pos).isAir() ? "destroyed" : "valid";
    }
    public Vec3 world(HeartBlockEntity heart, boolean nozzle) {
        var body = body(heart); if (body == null) return null;
        Vec3 local = position(body).getCenter();
        if (nozzle) local = local.add(Vec3.atLowerCornerOf(face.getNormal()).scale(.55));
        return body.logicalPose().transformPosition(local);
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putUUID("Body", bodyId); tag.putLong("Offset", offset.asLong()); tag.putString("Face", face.getName()); return tag;
    }
    public static ModuleAnchor load(CompoundTag tag) {
        Direction face = Direction.byName(tag.getString("Face"));
        return !tag.hasUUID("Body") || face == null ? null : new ModuleAnchor(tag.getUUID("Body"), BlockPos.of(tag.getLong("Offset")), face);
    }
}
