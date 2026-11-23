package dev.sablelife.ownership;

import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.compat.photomancy.BlueprintFactionPlacement;
import dev.sablelife.compat.photomancy.PhotomancyCompatibility;
import dev.sablelife.compat.photomancy.PhotomancyRespawnBridge;
import dev.sablelife.compat.photomancy.SpawnTemplateBridge;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Independent command jobs; existing arena/module records and spawn rules are never modified. */
public final class CommandBlueprintSpawns extends SavedData {
    private static final int LIMIT = 8;
    private static final class Job {
        UUID id = UUID.randomUUID(), creature;
        String name;
        ResourceLocation dimension;
        CompoundTag template = new CompoundTag();
        CompoundTag placement = new CompoundTag();
        Vec3 origin;
        AABB bounds;
        boolean activate, cleanup, waiting;
        int waitingTicks;
        final Set<UUID> bodies = new LinkedHashSet<>();
        transient CommandSourceStack source;
    }
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    public static CommandBlueprintSpawns get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(CommandBlueprintSpawns::new, CommandBlueprintSpawns::load), "sablelife_command_blueprints");
    }
    public static int submit(CommandSourceStack source, String name, boolean activate, Vec3 position, boolean surface) {
        if (!source.hasPermission(2)) return failure(source, "permission");
        if (!PhotomancyCompatibility.available()) return failure(source, "unavailable");
        var data = get(source.getServer());
        if (data.jobs.size() >= LIMIT) return failure(source, "busy");
        try {
            if (name.isBlank() || name.length() > 80) return failure(source, "file");
            var info = SpawnTemplateBridge.read(source.getServer(), name);
            if (activate && !info.canActivate()) return failure(source, "activation");
            long bytes = info.template().sizeInBytes();
            for (var job : data.jobs.values()) bytes += job.template.sizeInBytes();
            ArenaRespawnLimits.check("world_templates", bytes, SableLifeServerConfig.arenaWorldTemplateBytes());
            var level = source.getLevel();
            var geometry = SpawnTemplateBridge.geometry(info.template());
            var basis = geometry.bounds(new BlueprintSpawnPlacement.Destination(level.dimension().location(), Vec3.ZERO, null));
            var origin = new Vec3(position.x - basis.getCenter().x, position.y - basis.minY, position.z - basis.getCenter().z);
            checkBounds(level, basis.move(origin), !surface);
            if (surface) {
                var footprint = basis.move(origin);
                int top = level.getMinBuildHeight();
                for (int x = (int)Math.floor(footprint.minX); x < (int)Math.ceil(footprint.maxX); x++)
                    for (int z = (int)Math.floor(footprint.minZ); z < (int)Math.ceil(footprint.maxZ); z++) {
                        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                        if (height <= level.getMinBuildHeight()) throw new Rejected("ground");
                        if (!level.getFluidState(new BlockPos(x, height - 1, z)).isEmpty()) throw new Rejected("ground");
                        top = Math.max(top, height);
                    }
                origin = new Vec3(origin.x, top - basis.minY, origin.z);
            }
            var bounds = basis.move(origin);
            checkSpace(level, bounds);
            for (var pending : data.jobs.values())
                if (pending.dimension.equals(level.dimension().location()) && pending.bounds.intersects(bounds)) throw new Rejected("occupied");
            var job = new Job();
            job.name = name; job.dimension = level.dimension().location(); job.template = info.template().copy();
            job.origin = origin; job.bounds = bounds; job.activate = activate; job.source = source;
            data.jobs.put(job.id, job); data.setDirty();
            source.sendSuccess(() -> Component.translatable("command.sablelife.blueprint.queued", name), false);
            return 1;
        } catch (Rejected rejected) {
            return failure(source, rejected.reason);
        } catch (ArenaRespawnLimits.Exceeded exception) {
            return failure(source, "limit");
        } catch (java.io.IOException | IllegalArgumentException exception) {
            return failure(source, "file");
        } catch (RuntimeException exception) {
            com.mojang.logging.LogUtils.getLogger().warn("Blueprint command admission failed", exception);
            return failure(source, "failed");
        }
    }
    private static int failure(CommandSourceStack source, String reason) {
        source.sendFailure(Component.translatable("command.sablelife.blueprint.error." + reason));
        return 0;
    }
    private static final class Rejected extends RuntimeException {
        final String reason;
        Rejected(String reason) { this.reason = reason; }
    }
    private static void checkBounds(ServerLevel level, AABB bounds) {
        checkBounds(level, bounds, true);
    }
    private static void checkBounds(ServerLevel level, AABB bounds, boolean height) {
        if (!Double.isFinite(bounds.getSize()) || bounds.getXsize() > 512 || bounds.getZsize() > 512
                || height && (bounds.minY < level.getMinBuildHeight() || bounds.maxY > level.getMaxBuildHeight())
                || !level.getWorldBorder().isWithinBounds(bounds)) throw new Rejected("bounds");
        int x0 = (int)Math.floor(bounds.minX) >> 4, x1 = ((int)Math.ceil(bounds.maxX) - 1) >> 4;
        int z0 = (int)Math.floor(bounds.minZ) >> 4, z1 = ((int)Math.ceil(bounds.maxZ) - 1) >> 4;
        if ((long)(x1 - x0 + 1) * (z1 - z0 + 1) > 256) throw new Rejected("bounds");
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
            if (!level.hasChunk(x, z)) throw new Rejected("chunks");
    }
    private static void checkSpace(ServerLevel level, AABB bounds) {
        checkBounds(level, bounds);
        int x0 = (int)Math.floor(bounds.minX + 1e-7), x1 = (int)Math.ceil(bounds.maxX - 1e-7);
        int y0 = (int)Math.floor(bounds.minY + 1e-7), y1 = (int)Math.ceil(bounds.maxY - 1e-7);
        int z0 = (int)Math.floor(bounds.minZ + 1e-7), z1 = (int)Math.ceil(bounds.maxZ - 1e-7);
        if ((long)(x1 - x0) * (y1 - y0) * (z1 - z0) > SableLifeServerConfig.arenaCaptureScanVoxels()) throw new Rejected("bounds");
        var cursor = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++) for (int y = y0; y < y1; y++) {
            cursor.set(x, y, z);
            var state = level.getBlockState(cursor);
            if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, cursor).isEmpty()) throw new Rejected("occupied");
        }
        var container = SubLevelContainer.getContainer(level);
        if (container == null) throw new Rejected("unavailable");
        for (var body : container.getAllSubLevels()) {
            if (body.isRemoved()) continue;
            var box = body.boundingBox();
            if (box != null && bounds.intersects(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) throw new Rejected("occupied");
        }
    }
    public static void tick(ServerTickEvent.Post event) { get(event.getServer()).advance(event.getServer()); }
    public void advance(MinecraftServer server) {
        for (var job : List.copyOf(jobs.values())) {
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, job.dimension));
            if (level == null) continue;
            if (job.cleanup) {
                if (cleanup(level, job)) { jobs.remove(job.id); setDirty(); }
                continue;
            }
            if (job.waiting) {
                var core = LoadedCoreIndex.all(level).stream().filter(value -> value.creatureId().equals(job.creature)).findFirst().orElse(null);
                if (core != null && core.health().state() == dev.sablelife.heart.HeartState.ALIVE) {
                    complete(server, level, job);
                } else if (core == null || !core.awaitingBlueprintActivation() || ++job.waitingTicks > 600) {
                    job.cleanup = true; setDirty();
                    failure(source(server, level, job), "activation");
                    if (cleanup(level, job)) { jobs.remove(job.id); setDirty(); }
                }
                continue;
            }
            if (!PhotomancyCompatibility.available()) continue;
            ArenaRespawns.Build build = null;
            try {
                checkSpace(level, job.bounds);
                // Persisted as interrupted before the native placement; interrupted jobs are never replayed.
                job.cleanup = true; setDirty();
                var placement = PhotomancyRespawnBridge.createSpawn(job.template, new CompoundTag(), job.origin, null);
                build = placement;
                try {
                    if (!BlueprintFactionPlacement.withActor(level, factionActor(server, job),
                            () -> placement.step(level)).equals("DONE")) throw new Rejected("failed");
                } finally {
                    job.bodies.addAll(build.bodies());
                    job.placement = BlueprintCleanup.placement(build.save());
                    setDirty();
                }
                SableBodyProbe.invalidate(level);
                if (job.activate) {
                    var core = build.heart(level);
                    if (core == null) throw new Rejected("activation");
                    if (core.relations().hasPendingBlueprintFaction()) {
                        source(server, level, job).sendSuccess(() -> Component.translatable("command.sablelife.blueprint.faction_pending"), false);
                        job.activate = false;
                        complete(server, level, job);
                        break;
                    }
                    job.creature = core.creatureId(); setDirty();
                    build.captureOwnership(core);
                    if (!core.activateAfterBlueprintPlacement(build.bodies().size())) throw new Rejected("activation");
                    if (core.awaitingBlueprintActivation()) {
                        job.waiting = true; job.cleanup = false; setDirty();
                        break;
                    }
                }
                complete(server, level, job);
            } catch (RuntimeException exception) {
                job.cleanup = true;
                if (build != null) job.bodies.addAll(build.bodies());
                if (cleanup(level, job)) jobs.remove(job.id);
                setDirty();
                failure(source(server, level, job), exception instanceof Rejected rejected ? rejected.reason : "failed");
                if (!(exception instanceof Rejected)) com.mojang.logging.LogUtils.getLogger().error("Blueprint command placement failed", exception);
            }
            break; // At most one native placement per server tick.
        }
    }
    private void complete(MinecraftServer server, ServerLevel level, Job job) {
        source(server, level, job).sendSuccess(() -> Component.translatable("command.sablelife.blueprint.done", job.name,
                Component.translatable("command.sablelife.blueprint." + (job.activate ? "active" : "inactive"))), false);
        jobs.remove(job.id); setDirty();
    }
    private static CommandSourceStack source(MinecraftServer server, ServerLevel level, Job job) {
        return job.source == null ? server.createCommandSourceStack().withLevel(level) : job.source;
    }
    private static ServerPlayer factionActor(MinecraftServer server, Job job) {
        if (job.source == null || !(job.source.source instanceof ServerPlayer player)) return null;
        return server.getPlayerList().getPlayer(player.getUUID()) == player && player.hasPermissions(2) ? player : null;
    }
    private static boolean cleanup(ServerLevel level, Job job) {
        try {
            BlueprintCleanup.request(level.getServer(), job.id, job.dimension, job.creature, job.bodies, job.placement);
            return true;
        } catch (RuntimeException failure) {
            if (level.getGameTime() % 100 == 0) com.mojang.logging.LogUtils.getLogger().error("Blueprint command cleanup handover will retry", failure);
            return false;
        }
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var job : jobs.values()) {
            var row = new CompoundTag(); row.putUUID("Id", job.id); row.putString("Name", job.name);
            row.putString("Dimension", job.dimension.toString()); row.put("Template", job.template);
            row.putDouble("X", job.origin.x); row.putDouble("Y", job.origin.y); row.putDouble("Z", job.origin.z);
            row.putBoolean("Activate", job.activate); row.putBoolean("Cleanup", job.cleanup || job.waiting);
            if (job.creature != null) row.putUUID("Creature", job.creature);
            var bodies = new ListTag(); for (UUID id : job.bodies) bodies.add(NbtUtils.createUUID(id)); row.put("Bodies", bodies);
            if (!job.placement.isEmpty()) row.put("Placement", job.placement);
            var bounds = new ListTag(); for (double v : new double[]{job.bounds.minX, job.bounds.minY, job.bounds.minZ, job.bounds.maxX, job.bounds.maxY, job.bounds.maxZ}) bounds.add(DoubleTag.valueOf(v));
            row.put("Bounds", bounds); list.add(row);
        }
        tag.put("Jobs", list); return tag;
    }
    public static CommandBlueprintSpawns load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new CommandBlueprintSpawns();
        long bytes = 0;
        int rejectedTemplates = 0;
        for (var item : tag.getList("Jobs", Tag.TAG_COMPOUND)) {
            if (data.jobs.size() >= LIMIT) break;
            var row = (CompoundTag) item;
            var box = row.getList("Bounds", Tag.TAG_DOUBLE);
            if (!row.hasUUID("Id") || box.size() != 6) continue;
            var id = row.getUUID("Id");
            if (data.jobs.containsKey(id)) continue;
            var dimension = ResourceLocation.tryParse(row.getString("Dimension"));
            if (dimension == null) continue;
            var template = row.getCompound("Template");

            var job = new Job();
            job.id = id;
            job.name = row.getString("Name");
            job.dimension = dimension;
            job.origin = new Vec3(row.getDouble("X"), row.getDouble("Y"), row.getDouble("Z"));
            job.bounds = new AABB(box.getDouble(0), box.getDouble(1), box.getDouble(2), box.getDouble(3), box.getDouble(4), box.getDouble(5));
            if (!Double.isFinite(job.origin.lengthSqr()) || !Double.isFinite(job.bounds.getSize())) continue;
            job.activate = row.getBoolean("Activate");
            job.cleanup = row.getBoolean("Cleanup");
            if (row.hasUUID("Creature")) job.creature = row.getUUID("Creature");
            for (var body : row.getList("Bodies", Tag.TAG_INT_ARRAY)) {
                if (job.bodies.size() >= ArenaRespawnLimits.PARTS) break;
                if (((IntArrayTag) body).size() == 4) job.bodies.add(NbtUtils.loadUUID(body));
            }
            job.placement = BlueprintCleanup.placement(row.getCompound("Placement"));
            if (!job.bodies.isEmpty()) job.cleanup = true;
            if (template.isEmpty() && !job.cleanup) continue;
            int templateBytes = template.sizeInBytes();
            if (!ArenaRespawnLimits.fitsSavedTemplates(bytes, templateBytes)) {
                rejectedTemplates++;
                continue;
            }
            job.template = template.copy();
            data.jobs.put(id, job);
            bytes += templateBytes;
        }
        ArenaRespawnLimits.warnSkippedTemplates("sablelife_command_blueprints", rejectedTemplates);
        return data;
    }
}
