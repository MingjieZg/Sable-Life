package dev.sablelife.module;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Registry validation excludes particles requiring additional block, item or color payloads. */
public final class ModuleParticles {
    public static final int LIMIT = 16;
    public record Entry(String id, int ticks, float size) {
        public Entry { ticks = Math.clamp(ticks, 0, 1200); size = Float.isFinite(size) ? Math.clamp(size, .1F, 4F) : 1F; }
    }
    public static ListTag saveEntries(List<Entry> entries) {
        var tag = new ListTag();
        for (var e : entries) { var row = new net.minecraft.nbt.CompoundTag(); row.putString("Id", e.id()); row.putInt("Ticks", e.ticks()); row.putFloat("Size", e.size()); tag.add(row); }
        return tag;
    }
    public static List<Entry> loadEntries(ListTag properties, List<String> ids) {
        return ids.stream().map(id -> {
            for (int i = 0; i < Math.min(properties.size(), LIMIT); i++) {
                var row = properties.getCompound(i);
                if (row.getString("Id").equals(id)) return new Entry(id, row.getInt("Ticks"), row.contains("Size") ? row.getFloat("Size") : 1F);
            }
            return new Entry(id, 0, 1F);
        }).toList();
    }
    public static void send(net.minecraft.server.level.ServerLevel level, Entry entry,
            double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        var particle = type(entry.id()); if (particle == null) return;
        if (entry.ticks() == 0 && entry.size() == 1F) { send(level, particle, x, y, z, count, dx, dy, dz, speed); return; }
        var packet = new ModuleNetworking.ParticleVisual(entry.id(), new net.minecraft.world.phys.Vec3(x,y,z), Math.clamp(count, 0, 256),
                new net.minecraft.world.phys.Vec3(dx,dy,dz), speed, entry.ticks(), entry.size());
        for (var player : level.players()) if (player.position().distanceToSqr(x,y,z) <= 512 * 512 && VisualBudget.allow(player,Math.max(1,count)))
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
    }
    /** Bypass client particle reduction and use Minecraft's extended particle viewing distance. */
    public static <T extends net.minecraft.core.particles.ParticleOptions> void send(
            net.minecraft.server.level.ServerLevel level, T particle,
            double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        for (var player : level.players()) if(player.position().distanceToSqr(x,y,z)<=512*512&&VisualBudget.allow(player,Math.max(1,count)))
            level.sendParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
    }
    public static SimpleParticleType type(String id) {
        if (id.length() > 80) return null;
        var key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.PARTICLE_TYPE.containsKey(key)) return null;
        return BuiltInRegistries.PARTICLE_TYPE.get(key) instanceof SimpleParticleType particle ? particle : null;
    }
    public static boolean valid(String id) { return type(id) != null; }
    public static ListTag save(List<String> particles) {
        var list = new ListTag(); particles.forEach(id -> list.add(StringTag.valueOf(id))); return list;
    }
    public static List<String> load(ListTag tag) {
        var result = new ArrayList<String>();
        for (int i = 0; i < tag.size() && result.size() < LIMIT; i++) {
            String id = tag.getString(i); if (valid(id) && !result.contains(id)) result.add(id);
        }
        return List.copyOf(result);
    }
}
