package dev.sablelife.heart;

import dev.sablelife.module.ModuleParticles;
import dev.sablelife.module.VirtualModule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Cosmetic burst at the impact point when the structure takes damage. */
public final class HitPresentation {
    public static final int MAX_COUNT = 32;
    public static final double MAX_SPREAD = 4;
    private static final int DEFAULT_COUNT = 4;
    private static final double DEFAULT_SPREAD = 0.15;

    private VirtualModule particles = defaultParticles();
    private int count = DEFAULT_COUNT;
    private double spread = DEFAULT_SPREAD;

    public VirtualModule particleSelection() { return particles; }
    public int count() { return count; }
    public double spread() { return spread; }

    public boolean edit(String key, double value) {
        if (!Double.isFinite(value) || value < 0) return false;
        switch (key) {
            case "hitParticleCount" -> {
                if (value > MAX_COUNT || value != Math.rint(value)) return false;
                count = (int)value;
            }
            case "hitParticleSpread" -> {
                if (value > MAX_SPREAD) return false;
                spread = value;
            }
            default -> { return false; }
        }
        return true;
    }

    public void emit(ServerLevel level, Vec3 point) {
        if (count == 0) return;
        var entries = particles.particleEntries();
        if (entries.isEmpty()) return;
        // Count is shared between effects. Randomize which effects get the extra particles.
        int extra = count % entries.size();
        int offset = extra == 0 ? 0 : level.random.nextInt(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            int amount = count / entries.size() + (i < extra ? 1 : 0);
            if (amount == 0) break;
            var entry = entries.get((i + offset) % entries.size());
            ModuleParticles.send(level, entry, point.x, point.y, point.z, amount, spread, spread, spread, 0.05);
        }
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Count", count);
        tag.putDouble("Spread", spread);
        tag.put("ParticleSelection", particles.save());
        return tag;
    }

    public void load(CompoundTag tag) {
        count = DEFAULT_COUNT;
        spread = DEFAULT_SPREAD;
        if (tag.contains("Count", Tag.TAG_ANY_NUMERIC)) edit("hitParticleCount", tag.getDouble("Count"));
        if (tag.contains("Spread", Tag.TAG_ANY_NUMERIC)) edit("hitParticleSpread", tag.getDouble("Spread"));
        if (tag.contains("ParticleSelection", Tag.TAG_COMPOUND)) {
            var saved = tag.getCompound("ParticleSelection").copy();
            saved.putString("Type", "particles");
            particles = VirtualModule.load(saved);
        } else {
            particles = defaultParticles();
        }
    }

    private static VirtualModule defaultParticles() {
        var module = new VirtualModule("particles");
        module.editParticle("add", "minecraft:damage_indicator");
        return module;
    }
}
