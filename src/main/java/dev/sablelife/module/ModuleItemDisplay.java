package dev.sablelife.module;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Cosmetic snapshots only: no entities, item drops, collision or damage. */
public final class ModuleItemDisplay {
    private static final int RANGE = 256;
    private static final int KEEPALIVE_TICKS = 20;
    private static final int REFRESH_TICKS = 600;
    private static final int MAX_QUEUED_VIEWS = 256;
    private static final int MAX_FRAMES_PER_TICK = 32;
    private static final AtomicLong REVISIONS = new AtomicLong();
    // This server-thread map holds only connections with queued model data. Keepalives go directly to the viewer.
    private static final Map<ServerPlayer, Delivery> deliveries = new IdentityHashMap<>();

    // The tag belongs to VirtualModule. Edits replace it, so pages can share it without copying model data.
    static final class Model {
        final CompoundTag item;
        private int bytes = Integer.MIN_VALUE;
        private Boolean valid;

        Model(CompoundTag item) { this.item = item; }

        int bytes() {
            if (bytes == Integer.MIN_VALUE) bytes = ItemDisplaySync.measureModel(item);
            return bytes;
        }

        boolean valid(HolderLookup.Provider registries) {
            if (valid == null) {
                try {
                    valid = !ItemStack.parseOptional(registries, item).isEmpty();
                } catch (RuntimeException invalidComponent) {
                    // A component codec can fail independently of the saved NBT and its byte count.
                    valid = false;
                }
            }
            return valid;
        }
    }

    private record Row(UUID module, ModuleAnchor anchor, Model model, float size,
                       float x, float y, float z, float rotX, float rotY, float rotZ) {
        CompoundTag tag() {
            var row = new CompoundTag();
            row.putUUID("Id", module);
            row.put("Anchor", anchor.save());
            row.put("Item", model.item);
            row.putFloat("displaySize", size);
            row.putFloat("displayX", x);
            row.putFloat("displayY", y);
            row.putFloat("displayZ", z);
            row.putFloat("displayRotX", rotX);
            row.putFloat("displayRotY", rotY);
            row.putFloat("displayRotZ", rotZ);
            return row;
        }
    }

    private record Snapshot(UUID creature, long revision, List<ItemDisplaySync.Page> pages, boolean empty) {
        ItemDisplayPacket legacy(BlockPos pos) {
            return new ItemDisplayPacket(pos, creature, pages.getFirst().data());
        }
    }

    private static final class Viewer {
        final ServerGamePacketListenerImpl connection;
        final ServerLevel level;
        final BlockPos pos;
        final boolean split;
        long lastSeen;
        Vec3 seenPlayerPosition;
        long revision;
        long lastFull;
        long lastKeepalive;
        Snapshot latest;

        Viewer(ServerPlayer player, ServerLevel level, BlockPos pos) {
            this.connection = player.connection;
            this.level = level;
            this.pos = pos;
            split = connection.getConnection().channel() != null
                    && NetworkRegistry.hasChannel(connection, ItemDisplayPacket.Part.TYPE.id());
        }

        void update(ServerPlayer player, Snapshot snapshot, long now) {
            lastSeen = now;
            seenPlayerPosition = player.position();
            latest = snapshot;
            if (snapshot.empty) {
                var delivery = deliveries.get(player);
                if (delivery != null) delivery.cancel(this);
                if (revision != snapshot.revision) {
                    PacketDistributor.sendToPlayer(player, snapshot.legacy(pos));
                    sent(snapshot, now);
                }
                return;
            }

            if (revision != snapshot.revision || now - lastFull >= (split ? REFRESH_TICKS : KEEPALIVE_TICKS)) {
                deliveries.computeIfAbsent(player, ignored -> new Delivery()).offer(this);
            }
            if (split && revision != 0 && now - lastKeepalive >= KEEPALIVE_TICKS) {
                PacketDistributor.sendToPlayer(player, new ItemDisplayPacket.Part(pos, snapshot.creature, revision, 0, 0, null, 0));
                lastKeepalive = now;
            }
        }

        boolean eligible(ServerPlayer player, long now) {
            if (connection != player.connection || level != player.level() || lastSeen < now - 1) return false;
            if (lastSeen == now && player.position().equals(seenPlayerPosition)) return true;
            // A newly loaded core can miss its first regular tick. Continue only while the same core is still nearby.
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof HeartBlockEntity core)
                    || core.isRemoved() || !core.creatureId().equals(latest.creature)) return false;
            var center = SableBodyProbe.toWorld(level, pos.getCenter());
            return SableBodyProbe.toWorld(level, player.position()).distanceToSqr(center) < RANGE * RANGE;
        }

        boolean invalidateUnlessSeen(long now) {
            if (lastSeen == now) return false;
            lastSeen = Long.MIN_VALUE;
            return true;
        }

        void sent(Snapshot snapshot, long now) {
            revision = snapshot.revision;
            lastFull = lastKeepalive = now;
        }
    }

    private static final class Delivery {
        // Updating a queued view leaves its FIFO position intact and points it to the current immutable pages.
        final LinkedHashSet<Viewer> waiting = new LinkedHashSet<>();
        Viewer active;
        Snapshot sending;
        int nextPart;

        void offer(Viewer viewer) {
            if (active != viewer && waiting.size() < MAX_QUEUED_VIEWS) waiting.add(viewer);
        }

        void cancel(Viewer viewer) {
            waiting.remove(viewer);
            if (active == viewer) clearActive();
        }

        private void clearActive() {
            active = null;
            sending = null;
            nextPart = 0;
        }

        boolean flush(ServerPlayer player, long now) {
            if (active != null && !active.eligible(player, now)) clearActive();
            waiting.removeIf(viewer -> !viewer.eligible(player, now));
            int budget = ItemDisplaySync.MAX_PART_BYTES;
            for (int frames = 0; frames < MAX_FRAMES_PER_TICK; frames++) {
                if (active == null) {
                    if (waiting.isEmpty()) break;
                    var viewer = waiting.getFirst();
                    var snapshot = viewer.latest;
                    int bytes = snapshot.pages.getFirst().bytes();
                    if (bytes > budget) break;
                    waiting.removeFirst();
                    if (!viewer.split || snapshot.pages.size() == 1) {
                        PacketDistributor.sendToPlayer(player, snapshot.legacy(viewer.pos));
                        viewer.sent(snapshot, now);
                        budget -= bytes;
                        continue;
                    }
                    active = viewer;
                    sending = snapshot;
                }
                var page = sending.pages.get(nextPart);
                // One multipart page per connection per tick. Keep this snapshot contiguous until completion.
                PacketDistributor.sendToPlayer(player, new ItemDisplayPacket.Part(active.pos, sending.creature,
                        sending.revision, nextPart, sending.pages.size(), page.data(), page.bytes()));
                if (++nextPart == sending.pages.size()) {
                    active.sent(sending, now);
                    clearActive();
                }
                break;
            }
            return active == null && waiting.isEmpty();
        }
    }

    public static void flush(ServerTickEvent.Post event) {
        var server = event.getServer();
        long now = server.getTickCount();
        var iterator = deliveries.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var player = entry.getKey();
            if (player.server != server) continue;
            if (entry.getValue().flush(player, now)) iterator.remove();
        }
    }

    public static void stopped(ServerStoppedEvent event) {
        deliveries.keySet().removeIf(player -> player.server == event.getServer());
    }

    private List<Row> lastRows = List.of();
    private Snapshot last;
    private final Map<UUID, Viewer> viewers = new HashMap<>();

    private static boolean active(HeartBlockEntity core, VirtualModule module) {
        return core.getLevel() != null && module.type().equals("item_display") && module.enabled()
                && HeartModules.status(module, core).equals("valid")
                && (module.value("displayAlways") == 1 || HeartModules.triggered(module, core));
    }

    public static boolean showing(HeartBlockEntity core, VirtualModule module) {
        if (!active(core, module)) return false;
        var registries = core.getLevel().registryAccess();
        return module.displayModel(registries).valid(registries);
    }

    private static Row row(VirtualModule module, Model model) {
        return new Row(module.id(), module.anchors().getFirst(), model, (float) module.value("displaySize"),
                (float) module.value("displayX"), (float) module.value("displayY"), (float) module.value("displayZ"),
                (float) module.value("displayRotX"), (float) module.value("displayRotY"), (float) module.value("displayRotZ"));
    }

    private static List<Row> rows(HeartBlockEntity core, HolderLookup.Provider registries) {
        var rows = new ArrayList<Row>();
        int bytes = 0;
        for (var module : core.modules().all()) {
            if (rows.size() == ItemDisplaySync.MAX_ROWS) break;
            if (!active(core, module)) continue;
            var model = module.displayModel(registries);
            int modelBytes = model.bytes();
            if (modelBytes < 0) continue;
            int nextBytes = bytes + modelBytes + ItemDisplaySync.ROW_OVERHEAD;
            if (nextBytes > ItemDisplaySync.MAX_ROW_BYTES || !model.valid(registries)) continue;
            bytes = nextBytes;
            rows.add(row(module, model));
        }
        return rows;
    }

    public static CompoundTag snapshot(HeartBlockEntity core) {
        var data = new CompoundTag();
        var tags = new ListTag();
        for (var module : core.modules().all()) {
            if (showing(core, module)) tags.add(row(module, module.displayModel(core.getLevel().registryAccess())).tag().copy());
        }
        data.put("Displays", tags);
        return data;
    }

    public static Vec3 localPoint(ModuleAnchor anchor, SubLevel body, VirtualModule module) {
        return anchor.position(body).getCenter().add(Vec3.atLowerCornerOf(anchor.face().getNormal()).scale(.55))
                .add(module.value("displayX"), module.value("displayY"), module.value("displayZ"));
    }

    public static void preview(ServerPlayer player, HeartBlockEntity core, VirtualModule module) {
        var anchor = module.anchors().getFirst();
        var body = anchor.body(core);
        if (body == null) return;
        var center = localPoint(anchor, body, module);
        double half = module.value("displaySize") / 2;
        var rotation = new Quaterniond().rotationXYZ(Math.toRadians(module.value("displayRotX")),
                Math.toRadians(module.value("displayRotY")), Math.toRadians(module.value("displayRotZ")));
        var extent = new Vec3(half, half, half);
        MarkOutline.send(player, MarkOutline.Kind.DETAIL, 0xFFD940, MarkOutline.box(extent.scale(-1), extent, point -> {
            var rotated = rotation.transform(new Vector3d(point.x, point.y, point.z));
            return body.logicalPose().transformPosition(center.add(rotated.x, rotated.y, rotated.z));
        }));
    }

    public void tick(HeartBlockEntity core) {
        if (!(core.getLevel() instanceof ServerLevel level)) return;
        if (level.players().isEmpty()) {
            clearViewers();
            return;
        }
        var rows = rows(core, level.registryAccess());
        var creature = core.creatureId();
        if (last == null || !creature.equals(last.creature) || !rows.equals(lastRows)) {
            lastRows = rows;
            long revision = REVISIONS.incrementAndGet();
            var parts = new ItemDisplaySync.Parts(revision);
            for (var row : rows) parts.add(row.tag(), row.model.bytes() + ItemDisplaySync.ROW_OVERHEAD);
            if (last != null && !creature.equals(last.creature)) clearViewers();
            last = new Snapshot(creature, revision, parts.finish(), rows.isEmpty());
        }
        if (last.empty && viewers.isEmpty()) return;

        var pos = core.getBlockPos();
        var center = SableBodyProbe.toWorld(level, pos.getCenter());
        long now = level.getServer().getTickCount();
        for (var player : level.players()) {
            var connection = player.connection;
            if (connection == null || SableBodyProbe.toWorld(level, player.position()).distanceToSqr(center) >= RANGE * RANGE) continue;
            var id = player.getUUID();
            var viewer = viewers.get(id);
            if (viewer == null || viewer.connection != connection) {
                if (last.empty) continue;
                viewer = new Viewer(player, level, pos);
                viewers.put(id, viewer);
            }
            viewer.update(player, last, now);
        }
        if (last.empty) clearViewers();
        else viewers.values().removeIf(viewer -> viewer.invalidateUnlessSeen(now));
    }

    private void clearViewers() {
        for (var viewer : viewers.values()) viewer.lastSeen = Long.MIN_VALUE;
        viewers.clear();
    }
}
