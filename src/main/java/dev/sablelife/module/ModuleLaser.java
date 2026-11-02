package dev.sablelife.module;

import dev.sablelife.attack.ModuleDamage;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.sense.SensorGeometry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;

/** Charge, then emit a finite beam burst. Redstone withdrawal cancels either phase. */
public final class ModuleLaser {
    private static final class State {
        long ready, fireAt, finishAt, nextHit;
        final LaserBlockCut.Front cut=new LaserBlockCut.Front();
        int hits;
        boolean casting, firing;
        LivingEntity target;
        List<State> beams = List.of();
        Vec3 trackedDirection, aimPoint;
        long nextWarning;
        boolean warningUpdated;
        long trackedAt = Long.MIN_VALUE;
    }
    private final Map<UUID, State> states = new HashMap<>();
    private ServerLevel level;
    public Vec3 aimPoint(UUID id) { var state = states.get(id); return state == null || state.beams.isEmpty() ? null : state.beams.getFirst().aimPoint; }
    public int beamCount(UUID id) { var state = states.get(id); return state == null ? 0 : state.beams.size(); }
    public String phase(UUID id) { var s = states.get(id); return s == null || !s.casting ? "waiting" : s.firing ? "firing" : "charging"; }
    public void clear() { if (level != null) states.keySet().forEach(this::stop); states.clear(); level = null; }
    private UUID audioOwner;
    private void stop(UUID id) { sound(audioOwner==null?id:audioOwner, "", Vec3.ZERO, true); }
    private void sound(UUID id, String sound, Vec3 point, boolean stop) {
        if (!stop && sound.isEmpty()) return;
        for (var player : level.players()) if (stop || player.position().distanceToSqr(point) <= 128 * 128)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new ModuleNetworking.ModuleSound(id, sound, stop, point, 1, 1));
    }
    private static boolean eligible(LivingEntity e, VirtualModule m) {
        return ModuleTargets.matches(e,m);
    }
    public static List<LivingEntity> acquireTargets(ServerLevel level, HeartBlockEntity heart, VirtualModule m, Vec3 start) {
        double range = m.value("beamLength");
        return ModuleTargets.query(level, heart.creatureId(), new AABB(start, start).inflate(range), e -> eligible(e, m))
                .stream().filter(e -> e.getBoundingBox().getCenter().distanceToSqr(start) <= range * range
                        && ModuleTargets.visible(level, heart.creatureId(), start, e))
                .sorted(Comparator.comparingDouble(e -> e.getBoundingBox().getCenter().distanceToSqr(start)))
                .limit(m.value("targetMultiple") == 1 ? 64 : 1).toList();
    }
    private static Vec3 direction(HeartBlockEntity heart, VirtualModule m, State state) {
        if(m.emitter()!=null)return m.emitter().direction();
        var origin = ModuleCombat.nozzle(heart,m);
        if (m.value("laserAim") == 1 && state.target != null) {
            long now = heart.getLevel().getGameTime();
            state.warningUpdated = false;
            if (state.aimPoint == null) state.aimPoint = state.target.getBoundingBox().getCenter();
            if (state.trackedAt != now) {
                if (!state.firing && now >= state.nextWarning) {
                    state.aimPoint = state.target.getBoundingBox().getCenter();
                    state.nextWarning = now + Math.max(1, Math.round(1 / m.value("laserTracking")));
                    state.warningUpdated = true;
                } else if (state.firing && now > state.fireAt) {
                    state.aimPoint = pursue(state.aimPoint, state.target.getBoundingBox().getCenter(), m.value("laserFollow"));
                }
                state.trackedDirection = state.aimPoint.subtract(origin).normalize();
                state.trackedAt = now;
            }
            return state.trackedDirection;
        }
        var direction = ModuleCombat.direction(heart, m);
        if (sweeping(m) && state.casting) {
            long now = heart.getLevel().getGameTime();
            double progress = Math.clamp((double) (now - state.fireAt) / Math.max(1, state.finishAt - state.fireAt - 1), 0, 1);
            double angle = Math.toRadians(m.value("sweepAngle")) * (progress - .5);
            var up = dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond(m.anchors().getFirst().body(heart).logicalPose().orientation()), new Vec3(0, 1, 0));
            direction = dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond().rotationAxis(angle, up.x, up.y, up.z), direction);
        }
        return direction;
    }
    public static Vec3 pursue(Vec3 current, Vec3 target, double blocksPerTick) {
        var delta = target.subtract(current);
        return current.add(delta.scale(Math.min(1, Math.max(0, blocksPerTick) / Math.max(1e-12, delta.length()))));
    }
    public static Vec3 track(Vec3 current, Vec3 desired, double speed) {
        if (desired.lengthSqr() < 1e-12) return current;
        if (current.lengthSqr() < 1e-12) return desired;
        current = current.normalize(); desired = desired.normalize();
        double dot = Math.clamp(current.dot(desired), -1, 1);
        double fraction = LaserTracking.fraction(dot, speed);
        if (fraction >= 1) return desired;
        var axis = current.cross(desired);
        // Exactly opposite vectors still need a stable, finite turning axis.
        if (axis.lengthSqr() < 1e-12) axis = current.cross(Math.abs(current.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        axis = axis.normalize();
        return dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond().rotationAxis(Math.acos(dot) * fraction, axis.x, axis.y, axis.z), current).normalize();
    }
    private static boolean sweeping(VirtualModule m) { return m.value("laserAim") == 0 && m.value("sweepAngle") != 0; }
    public static DustParticleOptions color(VirtualModule m) {
        return new DustParticleOptions(ModuleColors.vector(m,"beamColor"),(float)Math.clamp(m.value("beamWidth")*2,.2,4));
    }
    private static void draw(ServerLevel level, ServerPlayer viewer, VirtualModule m, Vec3 start, Vec3 end, boolean charge) {
        int count = Math.clamp((int) Math.ceil(start.distanceTo(end) * 3), 2, 192);
        var dust = charge ? new DustParticleOptions(new Vector3f(.7F,.7F,.7F), .4F) : color(m);
        for (int i = 0; i <= count; i++) {
            var p = start.lerp(end, (double) i / count);
            if (viewer != null) level.sendParticles(viewer, dust, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            else dev.sablelife.module.ModuleParticles.send(level, dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            if (!charge && viewer == null && i % 12 == 0 && !m.particles().isEmpty()) {
                var particle = m.particleEntry(m.particles().get((i / 12 + (int) (level.getGameTime() % m.particles().size())) % m.particles().size()));
                if (particle != null) dev.sablelife.module.ModuleParticles.send(level, particle, p.x, p.y, p.z, 1, m.value("beamWidth") / 2, m.value("beamWidth") / 2, m.value("beamWidth") / 2, 0);
            }
        }
    }
    public static void preview(ServerPlayer player, HeartBlockEntity heart, VirtualModule m) {
        MarkingSessions.previewLaunch(player,heart,m);
        var level = (ServerLevel) player.level(); var state = new State();
        var origin = ModuleCombat.nozzle(heart,m);
        if (m.value("laserAim") == 1) {
            for (var target : acquireTargets(level, heart, m, origin)) {
                var preview = new State(); preview.target = target;
                var dir = direction(heart, m, preview); var start = origin;
                draw(level, player, m, start, SensorGeometry.beamEnd(level, heart.creatureId(), start, start.add(dir.scale(m.value("beamLength")))), false);
            }
            return;
        }
        var dir = direction(heart, m, state); var start = origin;
        draw(level, player, m, start, SensorGeometry.beamEnd(level, heart.creatureId(), start, start.add(dir.scale(m.value("beamLength")))), false);
        if (sweeping(m)) {
            state.casting = true; state.fireAt = level.getGameTime(); state.finishAt = state.fireAt + 1;
            for (int edge = 0; edge < 2; edge++) {
                state.fireAt = level.getGameTime() - edge; state.finishAt = state.fireAt + 2;
                dir = direction(heart, m, state); start = origin;
                draw(level, player, m, start, SensorGeometry.beamEnd(level, heart.creatureId(), start, start.add(dir.scale(m.value("beamLength")))), true);
            }
        }
    }
    public void tick(HeartBlockEntity heart){tickModules(heart,heart.modules().all(),false);}
    public void tickEmitter(HeartBlockEntity heart,VirtualModule module){audioOwner=module.audioId();tickModules(heart,List.of(module),true);}
    private void tickModules(HeartBlockEntity heart,List<VirtualModule> modules,boolean external) {
        if (!(heart.getLevel() instanceof ServerLevel server)) return;
        level = server.getServer().getLevel(server.dimension()); if (level == null) return;
        long now = level.getGameTime(); var active = new HashSet<UUID>();
        for (var m : modules) {
            if (!external && m.type().equals("laser") && heart.health().state() == dev.sablelife.heart.HeartState.ALIVE && !m.detached()
                    && HeartModules.status(m, heart).equals("disconnected")) { m.detach(); heart.setChanged(); }
            if (!m.type().equals("laser") || !external&&!HeartModules.triggered(m, heart)) continue;
            active.add(m.id()); var s = states.computeIfAbsent(m.id(), ignored -> new State());
            var origin = ModuleCombat.nozzle(heart,m);
            if (!s.casting) {
                if (now < s.ready || !external&&!heart.modules().hurtTriggers.start(m)) continue;
                if (m.value("laserAim") == 1) {
                    var targets = acquireTargets(level, heart, m, origin);
                    if (targets.isEmpty()) continue;
                    s.beams = targets.stream().map(target -> { var beam = new State(); beam.target = target; return beam; }).toList();
                } else s.beams = List.of(new State());
                s.casting = true; s.firing = false; s.hits = 0;
                s.fireAt = now + (long) m.value("chargeTime");
                s.finishAt = s.fireAt + (long) m.value("beamHits") * (long) m.value("hitInterval");
                s.nextHit = s.fireAt;
                for (var beam : s.beams) { beam.casting = true; beam.fireAt = s.fireAt; beam.finishAt = s.finishAt; }
                ModuleAudio.play(level,m,"chargeSound",origin);
            }
            if (now >= s.finishAt) {
                stop(m.id()); if(!external)heart.modules().hurtTriggers.completed(m); s.casting = false; s.beams = List.of(); s.ready = now + (long) m.value("interval"); continue;
            }
            if (now >= s.fireAt && !s.firing) {
                stop(m.id()); s.firing = true; ModuleAudio.play(level,m,"fireSound",origin);
            }
            boolean hit = s.firing && now >= s.nextHit && s.hits < m.value("beamHits");
            if (hit) { s.nextHit = now + (long) m.value("hitInterval"); s.hits++; }
            var damaged = new HashSet<UUID>();
            for (var beam : s.beams) {
                if (m.value("laserAim") == 1 && (!eligible(beam.target, m)
                        || beam.target.getBoundingBox().getCenter().distanceToSqr(origin) > m.value("beamLength") * m.value("beamLength"))) continue;
                beam.firing = s.firing;
                var dir = direction(heart, m, beam); var start = origin;
                var endpoint=start.add(dir.scale(m.value("beamLength")));
                var end=s.firing&&m.value("laserBreakBlocks")==1?LaserBlockCut.cut(level,heart,m,start,endpoint,beam.cut,now):SensorGeometry.beamEnd(level,heart.creatureId(),start,endpoint);
                if (s.firing || (m.value("laserAim") == 1 ? beam.warningUpdated : now % 4 == 0)) draw(level, null, m, start, end, !s.firing);
                if (!s.firing) {
                    ModuleParticles.send(level, color(m), start.x, start.y, start.z, 3, .15, .15, .15, 0); continue;
                }
                if(end.distanceToSqr(endpoint)>1e-6)SurfaceFieldImpact.terrainEnd(heart,m,end,dir);
                if (!hit) continue;
                double radius = m.value("beamWidth") / 2;
                for (var victim : ModuleTargets.query(level, heart.creatureId(), new AABB(start, end).inflate(radius), e -> eligible(e, m))) {
                    var box = victim.getBoundingBox().inflate(radius);
                    if (!box.contains(start) && box.clip(start, end).isEmpty()) continue;
                    if (!ModuleTargets.visible(level, heart.creatureId(), start, victim)) continue;
                    if (damaged.add(victim.getUUID())) {
                        var contact=dev.sablelife.combat.SegmentBoxContact.closest(start,end,victim.getBoundingBox(),new Vec3(1,1,1)).point();
                        SurfaceFieldImpact.entity(heart,m,contact,dir,victim.getUUID());
                        ModuleDamage.hurt(victim, ModuleDamage.named(ModuleDamage.physical(level),heart), (float) m.value("damage"),heart,m);
                    }
                }
            }
        }
        for (var id : List.copyOf(states.keySet())) if (!active.contains(id)) { stop(id); states.remove(id); }
    }
}
