package dev.sablelife.module;

import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Optional cosmetic state for a deployable shield. Existing pattern births use their original payload. */
public record ShieldVisualPacket(PatternVisual visual, Phase phase) implements CustomPacketPayload {
    public enum Phase { HOLD, CLOSE, PREVIEW, SYNC }

    public static final int MODULE_PATTERN = 9;
    public static final int GEOMETRY_PATTERN = 7;
    public static final int LEASE_TICKS = 35;
    public static final Type<ShieldVisualPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "deployable_shield"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShieldVisualPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                PatternVisual.CODEC.encode(buffer, packet.visual);
                buffer.writeEnum(packet.phase);
            },
            buffer -> new ShieldVisualPacket(PatternVisual.CODEC.decode(buffer), buffer.readEnum(Phase.class)));

    public boolean valid() {
        return phase != null && visual != null && visual.pattern() == GEOMETRY_PATTERN && visual.valid();
    }

    public static double transitionTicks(double speed) {
        return Math.clamp(6 / Math.sqrt(speed), 2, 14);
    }

    @Override
    public Type<ShieldVisualPacket> type() {
        return TYPE;
    }
}
