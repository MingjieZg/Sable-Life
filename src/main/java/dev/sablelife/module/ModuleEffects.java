package dev.sablelife.module;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/** Bounded, registry-validated effect snapshots shared by every supported attack. */
public final class ModuleEffects {
    public static final int LIMIT = 128;
    public static final List<String> TYPES = List.of("sphere_blast", "slash", "melee", "flame", "pulse", "arrow", "gun", "mortar", "homing");
    public record Entry(String id, int ticks, int level) {
        public MobEffectInstance instance() {
            var key = ResourceLocation.tryParse(id);
            var effect = key == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(key).orElse(null);
            // Instant effects must not become repeated ticking damage/healing on a tipped arrow.
            return effect == null || ticks <= 0 ? null : new MobEffectInstance(effect,
                    effect.value().isInstantenous() ? 1 : ticks, level - 1);
        }
        public CompoundTag save() {
            var tag = new CompoundTag(); tag.putString("Id", id); tag.putInt("Ticks", ticks); tag.putInt("Level", level); return tag;
        }
    }
    public static boolean valid(String id) {
        var key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.MOB_EFFECT.containsKey(key) && id.length() <= 80;
    }
    public static ListTag save(List<Entry> effects) {
        var tag = new ListTag(); effects.forEach(e -> tag.add(e.save())); return tag;
    }
    public static List<Entry> load(ListTag tag) {
        var result = new ArrayList<Entry>(); var seen = new java.util.HashSet<String>();
        for (int i = 0; i < tag.size() && result.size() < LIMIT; i++) {
            var row = tag.getCompound(i); String id = row.getString("Id");
            if (valid(id) && seen.add(id)) result.add(new Entry(id, Math.clamp(row.getInt("Ticks"), 0, 1200), Math.clamp(row.getInt("Level"), 1, 5)));
        }
        return List.copyOf(result);
    }
    public static void apply(LivingEntity victim, List<Entry> effects, Entity source) {
        if (victim.level().isClientSide || !victim.isAlive() || victim instanceof ModuleTargets.LifeTarget) return;
        for (var entry : effects) {
            var effect = entry.instance(); if (effect == null) continue;
            if (effect.getEffect().value().isInstantenous())
                effect.getEffect().value().applyInstantenousEffect(source, null, victim, effect.getAmplifier(), 1);
            else victim.addEffect(effect, source);
        }
    }
}
