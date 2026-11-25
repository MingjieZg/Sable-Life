package dev.sablelife.module;

import dev.sablelife.attack.ContactBox;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.sense.SensorGeometry;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import java.util.*;

/** Analog signals stay in plot coordinates; aiming is recomputed from the body's current pose. */
public final class TargetNavigation {
    public static final List<String> DIRECTIONS=List.of("Left","Right","Up","Down","Forward","Backward");
    private static final class State {
        UUID target;
        final SightMemory memory = new SightMemory();
        final SightMemory.SourceLink sourceLink = new SightMemory.SourceLink();
        int revision;
        long nextSearch;
        int lastTurn = 1;
        String name = "";
        int left, right, up, down, forward, backward;
    }
    private final Map<UUID, State> states = new HashMap<>();
    public void publish(VirtualModule module,Map<Integer,Integer> channels){
        var state=states.get(module.id());if(state==null)return;
        int[] values={state.left,state.right,state.up,state.down,state.forward,state.backward};
        for(int i=0;i<DIRECTIONS.size();i++){int channel=(int)module.value("navigation"+DIRECTIONS.get(i)+"Channel")-1;if(channel>=0&&values[i]>0)channels.merge(channel,values[i],Math::max);}
    }
    public void clear() { states.clear(); }
    public void retain(Set<UUID> active) { states.keySet().retainAll(active); }
    public String name(UUID id) { var state = states.get(id); return state == null ? "" : state.name; }
    public int left(UUID id) { var state = states.get(id); return state == null ? 0 : state.left; }
    public int right(UUID id) { var state = states.get(id); return state == null ? 0 : state.right; }
    public int up(UUID id) { var state = states.get(id); return state == null ? 0 : state.up; }
    public int down(UUID id) { var state = states.get(id); return state == null ? 0 : state.down; }
    public static Direction front(VirtualModule module) {
        int configured = (int) module.value("navigationFront");
        Direction face = configured == 0 ? module.anchors().getFirst().face()
                : Direction.byName(ModuleFields.find("navigation", "navigationFront").choices().get(configured));
        // A top/bottom mark still supplies a useful horizontal default; the UI can override it.
        return face.getAxis().isHorizontal() ? face : Direction.NORTH;
    }
    public static Vec3 worldFront(HeartBlockEntity heart, VirtualModule module) {
        return ContactBox.rotate(new Quaterniond(module.anchors().getFirst().body(heart).logicalPose().orientation()), Vec3.atLowerCornerOf(front(module).getNormal()));
    }
    private boolean eligible(VirtualModule module, LivingEntity entity) {
        return entity != null && !entity.isRemoved() && ModuleTargets.matches(entity, module);
    }

    private boolean sees(ServerLevel level, HeartBlockEntity heart, VirtualModule module, Vec3 center,
                         Quaterniond orientation, LivingEntity target) {
        return !SightMemory.enabled(module) || ModuleTargets.sensed(level, heart, center, orientation, target, Set.of());
    }

    private CommandTarget.Aim aim(ServerLevel level, HeartBlockEntity heart, VirtualModule module, State state,
                                  Vec3 center, Quaterniond orientation) {
        boolean sight = SightMemory.enabled(module);
        if (!sight || state.revision != heart.modules().revision()) {
            state.memory.clear();
            state.sourceLink.clear();
        }
        state.revision = heart.modules().revision();
        var order = heart.modules().commandTarget().current(level, CommandTarget.TARGET_ALIGNMENT);
        if (order != null) {
            state.target = null;
            state.memory.clear();
            state.sourceLink.clear();
            state.nextSearch = 0;
            var aim = order.resolve(level, module.value("target") == 4);
            if (aim == null || !sight) return aim;
            var start = SensorGeometry.sightStart(center, aim.point().subtract(center), orientation);
            return SensorGeometry.commandVisible(level, heart, start, aim.point(), order.lifeId()) ? aim : null;
        }
        double range = module.value("radius");
        double inner = module.value("innerRadius");
        double rangeSquared = range * range;
        double innerSquared = inner * inner;
        if (module.value("facingSource") == 1) {
            state.target = null;
            state.memory.clear();
            var source = heart.modules().events().sensorAim(heart, module.facingSensorId());
            var sensor = heart.modules().find(module.facingSensorId());
            var target = source == null || sensor == null ? null : ModuleTargets.resolveForModule(level, source.id(), heart, sensor);
            if (target == null || !eligible(sensor, target)) {
                state.sourceLink.clear();
                return null;
            }
            var point = state.sourceLink.follow(module.facingSensorId(), source, sight,
                    candidate -> withinRange(candidate.distanceToSqr(center), innerSquared, rangeSquared),
                    ignored -> sees(level, heart, module, center, orientation, target));
            return point == null ? null : new CommandTarget.Aim(point, target.getName().getString());
        }
        if (module.value("trigger") == 4 && heart.modules().moveTarget(module.id()) != null) {
            state.target = null;
            state.memory.clear();
            var inherited = heart.modules().moveAim(module.id());
            var target = inherited == null ? null : ModuleTargets.resolveForModule(level, inherited.id(), heart, module);
            if (!eligible(module, target)) {
                state.sourceLink.clear();
                return null;
            }
            var action = heart.modules().moveAction(module.id());
            if (action != null) {
                var point = state.sourceLink.follow(action.table(), inherited, sight,
                        candidate -> withinRange(candidate.distanceToSqr(center), innerSquared, rangeSquared),
                        ignored -> sees(level, heart, module, center, orientation, target));
                return point == null ? null : new CommandTarget.Aim(point, target.getName().getString());
            }
            state.sourceLink.clear();
            if (!withinRange(inherited.point().distanceToSqr(center), innerSquared, rangeSquared)
                    || !sees(level, heart, module, center, orientation, target)) return null;
            return new CommandTarget.Aim(inherited.point(), target.getName().getString());
        }
        state.sourceLink.clear();
        long now = level.getGameTime();
        int ticks = SightMemory.ticks(module);
        var last = state.memory.aimAt(now);
        var target = ModuleTargets.resolveForModule(level, state.target, heart, module);
        Vec3 targetPoint = null;
        if (eligible(module, target) && !(state.memory.obscured() && last == null)) {
            var point = SableBodyProbe.toWorld(level, target.getBoundingBox().getCenter());
            boolean withinCurrent = withinRange(point.distanceToSqr(center), innerSquared, rangeSquared);
            boolean withinLast = last != null && withinRange(last.point().distanceToSqr(center), innerSquared, rangeSquared);
            if (withinCurrent || withinLast) {
                if (SightMemory.canRecheck(center, point) && sees(level, heart, module, center, orientation, target)) {
                    if (withinCurrent) targetPoint = point;
                } else if (withinLast) {
                    var recalled = state.memory.remember(now, ticks);
                    if (recalled != null) return new CommandTarget.Aim(recalled.point(), target.getName().getString());
                }
            }
        }
        if (targetPoint == null) {
            state.target = null;
            state.memory.clear();
            target = null;
        }
        if (now >= state.nextSearch && (state.target == null || module.value("targetLock") != 0)) {
            state.nextSearch = now + 5;
            var selected = select(level, heart, module, center, orientation, range, innerSquared, rangeSquared);
            target = selected == null ? null : selected.entity();
            targetPoint = selected == null ? null : selected.point();
            state.target = target == null ? null : target.getUUID();
        }
        if (target == null) return null;
        if (ticks > 0) state.memory.see(target.getUUID(), targetPoint);
        return new CommandTarget.Aim(targetPoint, target.getName().getString());
    }

    private static boolean withinRange(double distanceSquared, double innerSquared, double rangeSquared) {
        return distanceSquared >= innerSquared && distanceSquared <= rangeSquared;
    }

    private record Candidate(LivingEntity entity, Vec3 point) {}

    private Candidate select(ServerLevel level, HeartBlockEntity heart, VirtualModule module, Vec3 center,
                             Quaterniond orientation, double range, double innerSquared, double rangeSquared) {
        var bounds = new AABB(center, center).inflate(range + 2);
        Candidate best = null;
        int bestRank = Integer.MAX_VALUE;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (var entity : ModuleTargets.queryForModule(level, heart, module, bounds, e -> eligible(module, e))) {
            var point = SableBodyProbe.toWorld(level, entity.getBoundingBox().getCenter());
            double distance = point.distanceToSqr(center);
            if (!withinRange(distance, innerSquared, rangeSquared)) continue;
            int rank = ModuleTargets.rank(heart, entity);
            if (best != null && (rank > bestRank || rank == bestRank && (distance > bestDistance
                    || distance == bestDistance && entity.getUUID().compareTo(best.entity().getUUID()) >= 0))) continue;
            if (!sees(level, heart, module, center, orientation, entity)) continue;
            best = new Candidate(entity, point);
            bestRank = rank;
            bestDistance = distance;
        }
        return best;
    }
    public void tick(ServerLevel level, HeartBlockEntity heart, VirtualModule module, Map<ModuleSignals.Face, Integer> signals) {
        var state = states.computeIfAbsent(module.id(), ignored -> new State());
        var anchor = module.anchors().getFirst(); var body = anchor.body(heart); var center = anchor.world(heart, false);
        var pos = anchor.position(body);
        for (var face : Direction.values()) signals.putIfAbsent(new ModuleSignals.Face(pos, face), 0);
        var orientation = new Quaterniond(body.logicalPose().orientation());
        var aim = aim(level, heart, module, state, center, orientation);
        state.left = state.right = state.up = state.down = 0;
        state.name = aim == null ? "" : aim.name();
        if (aim == null) { output(module,state,signals,pos,0,0);return; }
        var delta = ContactBox.rotate(orientation.conjugate(), aim.point().subtract(center));
        var planar = new Vec3(delta.x, 0, delta.z).normalize();
        var front = front(module); var right = front.getClockWise(); var left = front.getCounterClockWise();
        double forwardDot = planar.dot(Vec3.atLowerCornerOf(front.getNormal()));
        double rightDot = planar.dot(Vec3.atLowerCornerOf(right.getNormal()));
        int forward = bearing(forwardDot), backward = bearing(-forwardDot);
        if (planar.lengthSqr() > .0001) {
            double angle = Math.toDegrees(Math.atan2(rightDot, forwardDot));
            // Keep the chosen turn while the target crosses directly behind the construct.
            if (Math.abs(angle) > 170) angle = state.lastTurn * Math.abs(angle);
            int power = correction(angle, module.value("deadZone"));
            if (power > 0) { state.lastTurn = angle < 0 ? -1 : 1; if (angle < 0) state.left = power; else state.right = power; }
        }
        if (module.value("verticalTracking") == 1 && delta.lengthSqr() > .0001) {
            // Work in the marked body's rotating local frame, so pitching toward the target
            // reduces the error even if the world-space height difference remains unchanged.
            double pitch = Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
            int power = correction(pitch, module.value("pitchDeadZone"));
            if (pitch > 0) state.up = power; else state.down = power;
        }
        output(module,state,signals,pos,forward,backward);
    }
    private static void output(VirtualModule module,State state,Map<ModuleSignals.Face,Integer> signals,net.minecraft.core.BlockPos pos,int forward,int backward){
        boolean invert=module.value("navigationInvert")==1;var front=front(module);
        if(invert){int swap=state.up;state.up=state.down;state.down=swap;swap=state.left;state.left=state.right;state.right=swap;swap=forward;forward=backward;backward=swap;}
        state.forward=forward;state.backward=backward;
        put(signals,new ModuleSignals.Face(pos,Direction.UP),state.up);put(signals,new ModuleSignals.Face(pos,Direction.DOWN),state.down);
        put(signals,new ModuleSignals.Face(pos,front),forward);put(signals,new ModuleSignals.Face(pos,front.getOpposite()),backward);
        put(signals,new ModuleSignals.Face(pos,front.getCounterClockWise()),state.left);put(signals,new ModuleSignals.Face(pos,front.getClockWise()),state.right);
    }
    private static int correction(double angle, double dead) {
        double error = Math.abs(angle);
        return error <= dead ? 0 : Math.clamp((int) Math.ceil(15 * (error - dead) / (90 - dead)), 1, 15);
    }
    private static int bearing(double dot) { return Math.clamp((int) Math.round(Math.max(0, dot) * 15), 0, 15); }
    private static void put(Map<ModuleSignals.Face, Integer> signals, ModuleSignals.Face face, int power) {
        if (power > 0) signals.merge(face, power, Math::max);
    }
}
