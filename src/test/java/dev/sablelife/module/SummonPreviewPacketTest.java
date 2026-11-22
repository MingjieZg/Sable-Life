package dev.sablelife.module;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SummonPreviewPacketTest {
    @Test void largeFormationUsesOneBoundedLegacyPacket() {
        checkPreview(32, 80 * 1024, 11);
    }

    @Test void ordinaryModelsKeepAllFramesAndOversizedModelsClearTheOldPreview() {
        checkPreview(32, 20 * 1024, 32);
        checkPreview(1, 80 * 1024, 1);
        checkPreview(32, 950 * 1024, 0);
    }

    private static void checkPreview(int members, int modelSize, int expected) {
        var shape = new CompoundTag();
        shape.putByteArray("shape", new byte[modelSize]);
        var components = new CompoundTag();
        components.put("minecraft:custom_data", shape);
        var item = new CompoundTag();
        item.putString("id", "minecraft:paper");
        item.putInt("count", 1);
        item.put("components", components);
        var frames = IntStream.range(0, members).mapToObj(i -> new ModuleSummonedWeapon.Frame(
                new Vec3(i, 0, 0), new Vec3(i + 1, 2, 3), new Vec3(1, 0, 0), new Quaterniond(), new Quaterniond())).toList();
        var data = ModuleSummonedWeapon.previewModels(item, ItemDisplaySync.measureModel(item), frames, 1.25F);
        var packet = new ItemDisplayPacket(new BlockPos(3, 4, 5), new UUID(0, 77), data);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ItemDisplayPacket.CODEC.encode(buffer, packet);
            var core = buffer.readBlockPos();
            var owner = buffer.readUUID();
            assertEquals(packet.core(), core);
            assertEquals(packet.creature(), owner);
            assertTrue(buffer.readableBytes() <= ItemDisplaySync.MAX_PART_BYTES);
            var budget = NbtAccounter.create(ItemDisplaySync.MAX_PART_BYTES);
            assertEquals(data, buffer.readNbt(budget));

            buffer.readerIndex(0);
            var decoded = ItemDisplayPacket.CODEC.decode(buffer).displays();
            assertEquals(0, buffer.readableBytes());
            assertEquals(Set.of("SummonPreview"), decoded.getAllKeys());
            var rows = decoded.getList("SummonPreview", Tag.TAG_COMPOUND);
            assertEquals(expected, rows.size());
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.getCompound(i);
                assertEquals(item, row.getCompound("Item"));
                assertEquals((double) i, row.getDouble("X"));
                assertEquals((double) i + 1, row.getDouble("MuzzleX"));
                assertEquals(1.25F, row.getFloat("Size"));
                assertEquals(1.0, row.getDouble("QW"));
            }
        } finally {
            buffer.release();
        }
    }
}
