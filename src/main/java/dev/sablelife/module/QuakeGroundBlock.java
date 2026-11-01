package dev.sablelife.module;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LightLayer;

/** Vanilla block-display packet/rendering; never a falling block, item drop, or saved terrain edit. */
public final class QuakeGroundBlock extends Display.BlockDisplay {
    private final BlockPos base;
    private final double rise;
    public QuakeGroundBlock(ServerLevel level, BlockPos base, BlockState block, double rise) {
        super(EntityType.BLOCK_DISPLAY, level);
        this.base = base.immutable(); this.rise = rise;
        var tag = new CompoundTag(); tag.put("block_state", NbtUtils.writeBlockState(block));
        // The display origin remains inside the solid floor during a small lift.
        // Sample the exposed surface instead, while preserving night and local lamps.
        var brightness = new CompoundTag();
        brightness.putInt("block", Math.max(level.getBrightness(LightLayer.BLOCK, base.above()), block.getLightEmission(level, base)));
        brightness.putInt("sky", level.getBrightness(LightLayer.SKY, base.above()));
        tag.put("brightness", brightness);
        tag.putInt("teleport_duration", 1); tag.putFloat("view_range", 4);
        tag.putFloat("width", 2); tag.putFloat("height", (float) (rise + 2));
        tag.putFloat("shadow_radius", 0); readAdditionalSaveData(tag);
        setNoGravity(true); setInvulnerable(true); setPos(base.getX(), base.getY() + .01, base.getZ());
    }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public void tick() {
        super.tick();
        if (tickCount >= 14) { discard(); return; }
        setPos(base.getX(), base.getY() + .01 + Math.sin(Math.PI * tickCount / 14.) * rise, base.getZ());
    }
}
