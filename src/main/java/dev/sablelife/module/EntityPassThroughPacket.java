package dev.sablelife.module;

import dev.sablelife.SableLife;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A tracked-body change for the receiving player's local movement prediction. */
record EntityPassThroughPacket(ResourceLocation dimension, List<Change> changes) implements CustomPacketPayload {
    static final int LIMIT = 512;
    static final Type<EntityPassThroughPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "entity_pass_through"));

    record Change(UUID bodyId, boolean enabled) {}

    EntityPassThroughPacket {
        changes = List.copyOf(changes);
        if (changes.size() > LIMIT) throw new IllegalArgumentException("Too many entity pass-through changes");
    }

    static final StreamCodec<RegistryFriendlyByteBuf, EntityPassThroughPacket> CODEC = StreamCodec.of((buffer, packet) -> {
        buffer.writeResourceLocation(packet.dimension());
        buffer.writeVarInt(packet.changes().size());
        for (var change : packet.changes()) {
            buffer.writeUUID(change.bodyId());
            buffer.writeBoolean(change.enabled());
        }
    }, buffer -> {
        var dimension = buffer.readResourceLocation();
        int count = buffer.readVarInt();
        if (count < 0 || count > LIMIT) throw new DecoderException("Invalid entity pass-through change count " + count);
        var changes = new ArrayList<Change>(count);
        for (int i = 0; i < count; i++) changes.add(new Change(buffer.readUUID(), buffer.readBoolean()));
        return new EntityPassThroughPacket(dimension, changes);
    });

    @Override public Type<EntityPassThroughPacket> type() {
        return TYPE;
    }
}
