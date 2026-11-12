package dev.sablelife.module;

import dev.sablelife.SableLife;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Settings captured at launch and the visual-only, collision-clipped flight segment. */
public record ProjectileTracer(int color, float length, float width) {
    public static boolean supports(String type) {
        return switch (type) {
            case "arrow", "gun", "mortar", "homing" -> true;
            default -> false;
        };
    }

    public static boolean visible(VirtualModule module, String key) {
        if (!supports(module.type())) return true;
        boolean tracer = module.value("projectileTrailStyle") == 1;
        return switch (key) {
            case "particles" -> !tracer;
            case "projectileTrailColor", "projectileTrailLength", "projectileTrailWidth" -> tracer;
            default -> true;
        };
    }

    public static ProjectileTracer from(VirtualModule module) {
        if (module.value("projectileTrailStyle") != 1) return null;
        var color = ModuleColors.vector(module, "projectileTrailColor");
        int rgb = Math.round(color.x * 255) << 16 | Math.round(color.y * 255) << 8 | Math.round(color.z * 255);
        return new ProjectileTracer(rgb, (float) module.value("projectileTrailLength"), (float) module.value("projectileTrailWidth"));
    }

    public void save(CompoundTag tag) {
        var settings = new CompoundTag();
        settings.putInt("Color", color);
        settings.putFloat("Length", length);
        settings.putFloat("Width", width);
        tag.put("ProjectileTracer", settings);
    }

    public static ProjectileTracer read(CompoundTag tag) {
        var settings = tag.getCompound("ProjectileTracer");
        var tracer = new ProjectileTracer(settings.getInt("Color"), settings.getFloat("Length"), settings.getFloat("Width"));
        return tracer.valid() ? tracer : null;
    }

    private boolean valid() {
        return color >= 0 && color <= 0xFFFFFF && length >= .25F && length <= 16F && width >= .02F && width <= .5F;
    }

    public void send(ServerLevel level, int projectile, Vec3 start, Vec3 end) {
        double distance = start.distanceTo(end);
        if (!(distance > 0.001) || !Double.isFinite(distance) || level.players().isEmpty()) return;
        var center = start.lerp(end, .5);
        PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, 96 + distance * .5,
                new Segment(projectile, start, end, this));
    }

    public record Segment(int projectile, Vec3 start, Vec3 end, ProjectileTracer tracer) implements CustomPacketPayload {
        public static final Type<Segment> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "projectile_tracer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Segment> CODEC = StreamCodec.of((buf, segment) -> {
            buf.writeVarInt(segment.projectile);
            buf.writeDouble(segment.start.x);
            buf.writeDouble(segment.start.y);
            buf.writeDouble(segment.start.z);
            var displacement = segment.end.subtract(segment.start);
            buf.writeFloat((float) displacement.x);
            buf.writeFloat((float) displacement.y);
            buf.writeFloat((float) displacement.z);
            buf.writeInt(segment.tracer.color);
            buf.writeFloat(segment.tracer.length);
            buf.writeFloat(segment.tracer.width);
        }, buf -> {
            int projectile = buf.readVarInt();
            var start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            var end = start.add(buf.readFloat(), buf.readFloat(), buf.readFloat());
            var tracer = new ProjectileTracer(buf.readInt(), buf.readFloat(), buf.readFloat());
            return new Segment(projectile, start, end, tracer);
        });

        public boolean valid() {
            return tracer.valid() && Double.isFinite(start.lengthSqr()) && Double.isFinite(end.lengthSqr());
        }

        @Override public Type<Segment> type() { return TYPE; }
    }
}
