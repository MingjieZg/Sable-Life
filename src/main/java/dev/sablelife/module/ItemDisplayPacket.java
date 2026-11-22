package dev.sablelife.module;

import dev.sablelife.SableLife;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Legacy display snapshot, also used for health bars and summon previews. */
public record ItemDisplayPacket(BlockPos core, UUID creature, CompoundTag displays) implements CustomPacketPayload {
    public static final Type<ItemDisplayPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "item_displays"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemDisplayPacket> CODEC = StreamCodec.of((buffer, packet) -> {
        buffer.writeBlockPos(packet.core);
        buffer.writeUUID(packet.creature);
        buffer.writeNbt(packet.displays);
    }, buffer -> new ItemDisplayPacket(buffer.readBlockPos(), buffer.readUUID(), buffer.readNbt()));

    @Override public Type<ItemDisplayPacket> type() { return TYPE; }

    /** Optional channel for larger snapshots and lightweight renewal of an existing snapshot. */
    public record Part(BlockPos core, UUID creature, long revision, int index, int total, CompoundTag displays,
                       int accountedBytes) implements CustomPacketPayload {
        public static final Type<Part> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "item_display_part"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Part> CODEC = StreamCodec.of(
                Part::encode, Part::decode
        );

        private static void encode(RegistryFriendlyByteBuf buffer, Part packet) {
            buffer.writeBlockPos(packet.core);
            buffer.writeUUID(packet.creature);
            buffer.writeLong(packet.revision);
            buffer.writeVarInt(packet.index);
            buffer.writeVarInt(packet.total);
            if (packet.total != 0) buffer.writeNbt(packet.displays);
        }

        private static Part decode(RegistryFriendlyByteBuf buffer) {
            var core = buffer.readBlockPos();
            var creature = buffer.readUUID();
            long revision = buffer.readLong();
            int index = buffer.readVarInt();
            int total = buffer.readVarInt();
            if (revision <= 0 || total < 0 || total > ItemDisplaySync.MAX_PARTS
                    || index < 0 || index >= Math.max(total, 1)) {
                throw new DecoderException("Invalid item display snapshot header");
            }
            if (total == 0) return new Part(core, creature, revision, 0, 0, null, 0);
            int wireBytes = buffer.readableBytes();
            if (wireBytes > ItemDisplaySync.MAX_PART_BYTES) {
                throw new DecoderException("Item display snapshot part exceeds wire limit");
            }
            var budget = NbtAccounter.create(ItemDisplaySync.MAX_PART_BYTES);
            if (!(buffer.readNbt(budget) instanceof CompoundTag tag)) {
                throw new DecoderException("Invalid item display snapshot part");
            }
            return new Part(core, creature, revision, index, total, tag, Math.max(wireBytes, Math.toIntExact(budget.getUsage())));
        }

        @Override public Type<Part> type() { return TYPE; }
    }
}
