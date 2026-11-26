package dev.sablelife.module;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.Sable;
import dev.sablelife.diagnostics.CoreProfiler;
import dev.sablelife.diagnostics.CoreProfiler.Work;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ModuleBlockInteraction {
    private static final GameProfile ACTOR=new GameProfile(UUID.fromString("16543ce7-37b3-4ccd-bbe8-786035308725"),"[SableLife]");
    // One maximum-size request always fits. Palette pruning can make a scan cheaper,
    // but it must never allow a core to reserve more scan volume than this in one tick.
    static final int SCAN_VOLUME_PER_TICK = 30 * 30 * 30;
    private static final List<String> REQUEST_FIELDS = List.of("trigger", "channel", "hurtMode", "hurtCooldown",
            "hurtDuration", "interactionMode", "interactionInterval", "interactionSpace",
            "interactionSizeX", "interactionSizeY", "interactionSizeZ", "startDistance",
            "interactionOffsetX", "interactionOffsetY", "interactionOffsetZ");

    private record Activation(long next, long move) {}

    private static final class Request {
        final VirtualModule module;
        final ModuleAnchor anchor;
        final long move;
        final boolean oneShot;
        final boolean moveSet;
        final int volume;
        final double[] settings = new double[REQUEST_FIELDS.size()];

        Request(VirtualModule module, long move) {
            this.module = module;
            anchor = module.anchors().getFirst();
            this.move = move;
            oneShot = module.value("interactionMode") == 0 || ModuleHurt.once(module);
            moveSet = module.value("trigger") == 4;
            volume = (int) module.value("interactionSizeX") * (int) module.value("interactionSizeY")
                    * (int) module.value("interactionSizeZ");
            for (int i = 0; i < settings.length; i++) settings[i] = module.value(REQUEST_FIELDS.get(i));
        }

        boolean valid(HeartBlockEntity core, VirtualModule current, long activation, boolean powered) {
            if (current != module || activation != move || module.anchors().isEmpty()
                    || !anchor.equals(module.anchors().getFirst())) return false;
            if (!powered && (!oneShot || moveSet || !HeartModules.status(module, core).equals("valid"))) return false;
            for (int i = 0; i < settings.length; i++) if (settings[i] != module.value(REQUEST_FIELDS.get(i))) return false;
            return true;
        }
    }

    private final Map<UUID, Activation> active = new HashMap<>();
    private final LinkedHashMap<UUID, Request> pending = new LinkedHashMap<>();
    private long budgetTick = Long.MIN_VALUE;
    private int remainingVolume;

    void clear() {
        active.clear();
        pending.clear();
    }

    void tick(HeartBlockEntity core) {
        if (!(core.getLevel() instanceof ServerLevel level)) return;
        if (!core.wireless().master() || !core.operational()) {
            clear();
            return;
        }
        long now = level.getGameTime();
        if (budgetTick != now) {
            budgetTick = now;
            remainingVolume = SCAN_VOLUME_PER_TICK;
        }
        drain(level, core, now);

        HashSet<UUID> present = null;
        for (var module : core.modules().all()) {
            if (!module.type().equals("block_interaction")) continue;
            var id = module.id();
            if (present == null) present = new HashSet<>();
            present.add(id);
            boolean powered = HeartModules.triggered(module, core);
            long move = moveActivation(core, module);
            var request = pending.get(id);
            if (request != null && !request.valid(core, module, move, powered)) {
                pending.remove(id);
                active.remove(id);
            }
            if (!powered) {
                active.remove(id);
                continue;
            }
            if (pending.containsKey(id)) continue;

            var previous = active.get(id);
            if (previous != null && previous.move() == move
                    && (module.value("interactionMode") == 0 || now < previous.next())) continue;
            if (!core.modules().hurtTriggers.start(module)) continue;

            request = new Request(module, move);
            pending.put(id, request);
            if (request.oneShot) {
                active.put(id, new Activation(now, move));
                core.modules().hurtTriggers.completed(module);
            }
            // New actions run immediately when prior requests leave room. Their block changes
            // can still affect the trigger sampled for the next module in this same tick.
            if (pending.size() == 1 && request.volume <= remainingVolume) drain(level, core, now);
        }
        if (present == null) {
            clear();
            return;
        }
        pending.keySet().retainAll(present);
        active.keySet().retainAll(present);
    }

    private void drain(ServerLevel level, HeartBlockEntity core, long now) {
        while (remainingVolume > 0 && !pending.isEmpty()) {
            var next = pending.firstEntry();
            var request = next.getValue();
            var module = core.modules().find(next.getKey());
            boolean powered = module != null && HeartModules.triggered(module, core);
            if (module == null || !core.wireless().master()
                    || !request.valid(core, module, moveActivation(core, module), powered)) {
                pending.pollFirstEntry();
                active.remove(next.getKey());
                continue;
            }

            // Leave this request at the front for the next tick even if smaller ones could fit.
            if (request.volume > remainingVolume) break;
            pending.pollFirstEntry();
            remainingVolume -= request.volume;
            if (!request.oneShot)
                active.put(module.id(), new Activation(now + (long) module.value("interactionInterval"), request.move));
            interact(level, core, module);
        }
    }

    private static long moveActivation(HeartBlockEntity core, VirtualModule module) {
        return module.value("trigger") == 4 ? core.modules().moveActivation(module.id()) : Long.MIN_VALUE;
    }

    record Area(Vec3 point, BlockPos min, BlockPos max, Direction face,
                        dev.ryanhcode.sable.sublevel.SubLevel body) {
        Vec3 world(Vec3 point){return body==null?point:body.logicalPose().transformPosition(point);}
    }

    static Area area(HeartBlockEntity core, VirtualModule module) {
        var anchor=module.anchors().getFirst();
        var body=anchor.body(core);
        var normal=Vec3.atLowerCornerOf(anchor.face().getNormal());
        var pose=body.logicalPose();
        var local=anchor.position(body).getCenter().add(normal.scale(module.value("startDistance")))
                .add(module.value("interactionOffsetX"),module.value("interactionOffsetY"),module.value("interactionOffsetZ"));
        boolean structure=module.value("interactionSpace")==1;
        var point=structure?local:pose.transformPosition(local);
        var forward=pose.transformPosition(local.add(normal)).subtract(point);
        int spanX = (int) module.value("interactionSizeX") - 1;
        int spanY = (int) module.value("interactionSizeY") - 1;
        int spanZ = (int) module.value("interactionSizeZ") - 1;
        var min = BlockPos.containing(point.subtract(spanX * .5, spanY * .5, spanZ * .5));
        return new Area(point, min, min.offset(spanX, spanY, spanZ),
                structure?anchor.face():Direction.getNearest(forward.x,forward.y,forward.z),structure?body:null);
    }

    static void preview(ServerPlayer player,HeartBlockEntity core,VirtualModule module){
        var area=area(core,module);
        var selected=target(player.serverLevel(),area);
        var min=Vec3.atLowerCornerOf(area.min()).subtract(.002,.002,.002);
        var max=Vec3.atLowerCornerOf(area.max()).add(1.002,1.002,1.002);
        MarkOutline.send(player,MarkOutline.Kind.VOLUME,MarkOutline.MARK_COLOR,
                MarkOutline.box(min,max,area::world));
        if(selected!=null){
            var corner=Vec3.atLowerCornerOf(selected);
            MarkOutline.send(player,MarkOutline.Kind.DETAIL,0xFFFFFF,
                    MarkOutline.box(corner.subtract(.003,.003,.003),corner.add(1.003,1.003,1.003),area::world));
        }
    }

    private static boolean supported(BlockState state) {
        var block = state.getBlock();
        return block != Blocks.IRON_DOOR && block != Blocks.IRON_TRAPDOOR
                && (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                    || block instanceof ButtonBlock || block instanceof LeverBlock)
                && BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft");
    }

    private static BlockPos target(ServerLevel level, Area area) {
        if (area.max().getX() - area.min().getX() < 3 && area.max().getY() - area.min().getY() < 3
                && area.max().getZ() - area.min().getZ() < 3) return smallTarget(level, area);

        BlockPos nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (var part : candidateSections(level, area)) {
            if (part.minimumDistance() > best) break;
            for (var pos : BlockPos.betweenClosed(part.min(), part.max())) {
                if (!supported(part.section().getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15))
                        || area.body() == null && !level.getWorldBorder().isWithinBounds(pos)) continue;
                double distance = area.point().distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
                // Grouping positions by chunk changes visitation order. Preserve the original x-fast tie break.
                if (distance < best || distance == best && nearest != null && precedes(pos, nearest)) {
                    nearest = pos.immutable();
                    best = distance;
                }
            }
        }
        return nearest;
    }

    private static BlockPos smallTarget(ServerLevel level, Area area) {
        BlockPos nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (var pos : BlockPos.betweenClosed(area.min(), area.max())) {
            if (!level.hasChunkAt(pos) || area.body() == null && !level.getWorldBorder().isWithinBounds(pos)
                    || Sable.HELPER.getContaining(level, pos) != area.body() || !supported(level.getBlockState(pos))) continue;
            double distance = pos.getCenter().distanceToSqr(area.point());
            if (distance < best) {
                nearest = pos.immutable();
                best = distance;
            }
        }
        return nearest;
    }

    private record CandidateSection(LevelChunkSection section, BlockPos min, BlockPos max, double minimumDistance) {}

    private static List<CandidateSection> candidateSections(ServerLevel level, Area area) {
        int minY = Math.max(area.min().getY(), level.getMinBuildHeight());
        int maxY = Math.min(area.max().getY(), level.getMaxBuildHeight() - 1);
        if (minY > maxY) return List.of();

        var sections = new ArrayList<CandidateSection>();
        var chunks = level.getChunkSource();
        for (int z = area.min().getZ() >> 4; z <= (area.max().getZ() >> 4); z++) {
            for (int x = area.min().getX() >> 4; x <= (area.max().getX() >> 4); x++) {
                if (Sable.HELPER.getContaining(level, x, z) != area.body() || !chunks.hasChunk(x, z)) continue;
                // Sable routes this loaded-only lookup to the same plot storage as normal block reads.
                var chunk = chunks.getChunkNow(x, z);
                if (chunk == null) continue;
                int left = x << 4, front = z << 4;
                for (int index = chunk.getSectionIndex(minY); index <= chunk.getSectionIndex(maxY); index++) {
                    var section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(ModuleBlockInteraction::supported)) continue;
                    int bottom = chunk.getSectionYFromSectionIndex(index) << 4;
                    var min = new BlockPos(Math.max(area.min().getX(), left), Math.max(minY, bottom),
                            Math.max(area.min().getZ(), front));
                    var max = new BlockPos(Math.min(area.max().getX(), left + 15), Math.min(maxY, bottom + 15),
                            Math.min(area.max().getZ(), front + 15));
                    var point = area.point();
                    double distance = point.distanceToSqr(Math.clamp(point.x, min.getX() + .5, max.getX() + .5),
                            Math.clamp(point.y, min.getY() + .5, max.getY() + .5),
                            Math.clamp(point.z, min.getZ() + .5, max.getZ() + .5));
                    sections.add(new CandidateSection(section, min, max, distance));
                }
            }
        }
        sections.sort(Comparator.comparingDouble(CandidateSection::minimumDistance));
        return sections;
    }

    private static boolean precedes(BlockPos candidate, BlockPos current) {
        if (candidate.getZ() != current.getZ()) return candidate.getZ() < current.getZ();
        if (candidate.getY() != current.getY()) return candidate.getY() < current.getY();
        return candidate.getX() < current.getX();
    }

    private static void interact(ServerLevel level,HeartBlockEntity core,VirtualModule module){
        var area=area(core,module);
        long scan = CoreProfiler.begin(level.getServer());
        BlockPos pos;
        try {
            pos = target(level, area);
        } finally {
            if (scan != 0) CoreProfiler.endWork(core, Work.BLOCK_SCAN, scan, 1);
        }
        if(pos==null)return;
        var actor=FakePlayerFactory.get(level,ACTOR);
        if(!level.mayInteract(actor,pos))return;
        var face=area.face();
        actor.setPos(area.world(area.point()));
        actor.setYRot(face.getAxis().isHorizontal()?face.getOpposite().toYRot():0);
        var hit=pos.getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
        var result=new BlockHitResult(hit,face,pos,false);
        var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                actor,net.minecraft.world.InteractionHand.MAIN_HAND,pos,result);
        Runnable use=()->{
            long attempted = CoreProfiler.begin(level.getServer());
            boolean applied = false;
            try {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
                if (!event.isCanceled() && event.getUseBlock() != net.neoforged.neoforge.common.util.TriState.FALSE) {
                    var state = level.getBlockState(pos);
                    var outcome = state.useWithoutItem(level, actor, result);
                    // A powered button consumes the click without producing another press.
                    applied = attempted != 0 && outcome.consumesAction() && !state.equals(level.getBlockState(pos));
                }
            } finally {
                if (attempted != 0) CoreProfiler.endWork(core, Work.BLOCK_USE, attempted, applied ? 1 : 0);
            }
        };
        if(area.body()!=null)dev.sablelife.ownership.BodyInteraction.useOwnModule(core,actor,pos,use);
        else use.run();
    }
}
