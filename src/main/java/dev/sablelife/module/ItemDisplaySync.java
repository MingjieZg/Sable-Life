package dev.sablelife.module;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Byte and row limits shared by the producer and the client assembling snapshots. */
public final class ItemDisplaySync {
    public static final int MAX_ROWS = 64;
    public static final int MAX_MODEL_BYTES = 900 * 1024;
    public static final int MAX_PART_BYTES = 960 * 1024;
    public static final int MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;
    public static final int MAX_PARTS = 32;
    public static final String REVISION = "DisplayRevision";
    // Allow for the NBT key, map entry, and payload charges for the row's fixed fields and its outer container.
    static final int ROW_OVERHEAD = 2 * 1024;
    private static final int SNAPSHOT_OVERHEAD = 512;
    static final int MAX_ROW_BYTES = MAX_SNAPSHOT_BYTES - MAX_PARTS * SNAPSHOT_OVERHEAD;
    private static final int MAX_PENDING_BYTES = 2 * MAX_SNAPSHOT_BYTES;
    private static final int MAX_PENDING_SNAPSHOTS = 16;
    private static final int PENDING_TICKS = 100;

    /** Measure the model using the same NBT wire format and accounting as the packet reader. */
    static int measureModel(CompoundTag data) {
        if (data.isEmpty()) return -1;
        var buffer = Unpooled.buffer(256, MAX_MODEL_BYTES);
        try {
            NbtIo.writeAnyTag(data, new ByteBufOutputStream(buffer));
            int written = buffer.readableBytes();
            // Leave depth for the outer packet and row containers.
            var budget = new NbtAccounter(MAX_MODEL_BYTES, 500);
            NbtIo.readAnyTag(new ByteBufInputStream(buffer), budget);
            return Math.max(written, Math.toIntExact(budget.getUsage()));
        } catch (IOException | RuntimeException invalid) {
            return -1;
        } finally {
            buffer.release();
        }
    }

    static CompoundTag payload(ListTag rows, long revision) {
        var data = new CompoundTag();
        data.put("Displays", rows);
        data.putLong(REVISION, revision);
        return data;
    }

    record Page(CompoundTag data, int bytes) {}

    /** Hide a stalled view before releasing its bounded model data. A matching heartbeat renews both. */
    public record Lease(long visibleUntil, long retainedUntil) {
        private static final int VISIBLE_TICKS = 40;
        private static final int RETAINED_TICKS = 600;

        public static Lease refreshed(long now) { return new Lease(now + VISIBLE_TICKS, now + RETAINED_TICKS); }
        public boolean visible(long now) { return now <= visibleUntil; }
        public boolean expired(long now) { return now > retainedUntil; }
    }

    static final class Parts {
        private final long revision;
        private final List<Page> parts = new ArrayList<>();
        private ListTag rows = new ListTag();
        private int bytes = SNAPSHOT_OVERHEAD;

        Parts(long revision) { this.revision = revision; }

        void add(CompoundTag row, int size) {
            if (!rows.isEmpty() && bytes + size > MAX_PART_BYTES) {
                parts.add(new Page(payload(rows, revision), bytes));
                rows = new ListTag();
                bytes = SNAPSHOT_OVERHEAD;
            }
            rows.add(row);
            bytes += size;
        }

        List<Page> finish() {
            parts.add(new Page(payload(rows, revision), bytes));
            return List.copyOf(parts);
        }
    }

    /** Client-only state, independent of the renderer and convenient for testing through the codec. */
    public static final class Assembly {
        public record Complete(ListTag rows, int bytes) {}

        private static final class Pending {
            final UUID creature;
            final long revision;
            final int total;
            final ListTag rows = new ListTag();
            int next, bytes;
            long expires;

            Pending(ItemDisplayPacket.Part first, long now) {
                creature = first.creature();
                revision = first.revision();
                total = first.total();
                expires = now + PENDING_TICKS;
            }
        }

        private final Map<BlockPos, Pending> pending = new LinkedHashMap<>();
        private int pendingBytes;

        public void clear() {
            pending.clear();
            pendingBytes = 0;
        }

        public void forget(BlockPos pos) {
            var removed = pending.remove(pos);
            if (removed != null) pendingBytes -= removed.bytes;
        }

        public void expire(long now) {
            var iterator = pending.values().iterator();
            while (iterator.hasNext()) {
                var snapshot = iterator.next();
                if (snapshot.expires <= now) {
                    pendingBytes -= snapshot.bytes;
                    iterator.remove();
                }
            }
        }

        public Complete accept(ItemDisplayPacket.Part part, long now) {
            if (part.total() < 1 || part.total() > MAX_PARTS || part.index() < 0 || part.index() >= part.total()
                    || part.displays() == null || part.accountedBytes() < 1 || part.accountedBytes() > MAX_PART_BYTES) return null;
            expire(now);
            var pos = part.core();
            var snapshot = pending.get(pos);
            if (snapshot != null && snapshot.creature.equals(part.creature()) && part.revision() < snapshot.revision) return null;
            if (part.index() == 0) {
                forget(pos);
                if (pending.size() >= MAX_PENDING_SNAPSHOTS) forget(pending.keySet().iterator().next());
                snapshot = new Pending(part, now);
                pending.put(pos, snapshot);
            }
            if (snapshot == null || snapshot.revision != part.revision() || !snapshot.creature.equals(part.creature())
                    || snapshot.total != part.total() || snapshot.next != part.index()) {
                return null;
            }

            var rows = part.displays().getList("Displays", Tag.TAG_COMPOUND);
            int bytes = part.accountedBytes();
            if (rows.isEmpty() || snapshot.rows.size() + rows.size() > MAX_ROWS || bytes < 0
                    || snapshot.bytes + bytes > MAX_SNAPSHOT_BYTES) {
                forget(pos);
                return null;
            }
            while (pendingBytes + bytes > MAX_PENDING_BYTES) {
                var oldest = pending.keySet().iterator().next();
                forget(oldest);
                if (oldest.equals(pos)) return null;
            }
            snapshot.rows.addAll(rows);
            snapshot.bytes += bytes;
            pendingBytes += bytes;
            snapshot.expires = now + PENDING_TICKS;
            if (++snapshot.next != snapshot.total) return null;

            forget(pos);
            return new Complete(snapshot.rows, snapshot.bytes);
        }
    }

    private ItemDisplaySync() {}
}
