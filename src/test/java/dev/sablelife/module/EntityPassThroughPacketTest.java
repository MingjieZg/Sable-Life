package dev.sablelife.module;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityPassThroughPacketTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");

    @Test
    void batchesRoundTripAtTheChangeLimit() {
        var changes = IntStream.range(0, EntityPassThroughPacket.LIMIT).mapToObj(index ->
                new EntityPassThroughPacket.Change(new UUID(0, index), index % 2 == 0)).toList();
        var packet = new EntityPassThroughPacket(DIMENSION, changes);
        var buffer = buffer();
        try {
            EntityPassThroughPacket.CODEC.encode(buffer, packet);
            assertEquals(packet, EntityPassThroughPacket.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void invalidCountsAreRejectedBeforeReadingBodyIds() {
        for (int count : new int[] {-1, EntityPassThroughPacket.LIMIT + 1}) {
            var buffer = buffer();
            try {
                buffer.writeResourceLocation(DIMENSION);
                buffer.writeVarInt(count);
                assertThrows(DecoderException.class, () -> EntityPassThroughPacket.CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
