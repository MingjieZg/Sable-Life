package dev.sablelife.module;

import dev.sablelife.attack.ContactBox;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.sense.SensorGeometry;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import java.util.*;

/** Analog signals stay in plot coordinates; aiming is recomputed from the body's current pose. */
public final class TargetNavigation {
    public static final List<String> DIRECTIONS=List.of("Left","Right","Up","Down","Forward","Backward");
    private static final class State {
        UUID target;
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
    private boolean eligible(ServerLevel level, HeartBlockEntity heart, VirtualModule module, LivingEntity entity, Vec3 center) {
        if (!entity.isAlive() || entity.isSpectator() || entity instanceof Player p && p.isCreative()&&module.value("target")!=4) return false;
        if (!ModuleTargets.matches(entity,module)) return false;
        Vec3 point = SableBodyProbe.toWorld(level, entity.getBoundingBox().getCenter());
        if (point.distanceToSqr(center) > Math.pow(module.value("radius"), 2)) return false;
        return module.value("lineOfSight") != 0 || ModuleTargets.visible(level, heart.creatureId(),
                SensorGeometry.sightStart(center, point.subtract(center), new Quaterniond(module.anchors().getFirst().body(heart).logicalPose().orientation())), entity);
    }
    public void tick(ServerLevel level, HeartBlockEntity heart, VirtualModule module, Map<ModuleSignals.Face, Integer> signals) {
        var state = states.computeIfAbsent(module.id(), ignored -> new State());
        var anchor = module.anchors().getFirst(); var body = anchor.body(heart); var center = anchor.world(heart, false);
        var pos = anchor.position(body);
        for (var face : Direction.values()) signals.putIfAbsent(new ModuleSignals.Face(pos, face), 0);
        LivingEntity target = ModuleTargets.resolveForModule(level,state.target,heart,module);
        if (level.getGameTime() >= state.nextSearch || target != null && !target.isAlive()) {
            state.nextSearch = level.getGameTime() + 5;
            if (module.value("targetLock") != 0 || target == null || !eligible(level, heart, module, target, center)) {
                target = ModuleTargets.queryForModule(level, heart, module, new AABB(center, center).inflate(module.value("radius") + 2),
                        e -> eligible(level, heart, module, e, center)).stream()
                        .min(ModuleTargets.comparator(heart,e -> SableBodyProbe.toWorld(level, e.getBoundingBox().getCenter()).distanceToSqr(center))
                                .thenComparing(e -> e.getUUID().toString())).orElse(null);
            }
            state.target = target == null ? null : target.getUUID();
        }
        state.left = state.right = state.up = state.down = 0; state.name = target == null ? "" : target.getName().getString();
        if (target == null) { output(module,state,signals,pos,0,0);return; }
        var delta = ContactBox.rotate(new Quaterniond(body.logicalPose().orientation()).conjugate(),
                SableBodyProbe.toWorld(level, target.getBoundingBox().getCenter()).subtract(center));
        if (delta.lengthSqr() > Math.pow(module.value("radius"), 2)) { state.target = null; state.name = ""; output(module,state,signals,pos,0,0);return; }
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
