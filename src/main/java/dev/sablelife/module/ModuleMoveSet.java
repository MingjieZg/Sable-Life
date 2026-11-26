package dev.sablelife.module;

import dev.sablelife.diagnostics.CoreProfiler;
import dev.sablelife.heart.*;
import net.minecraft.nbt.*;
import java.util.*;

/** Move set owns conditions, weighted/priority selection and direct action timing. */
public final class ModuleMoveSet {
    private static final class State {
        UUID current, previous, target;
        final SightMemory memory = new SightMemory();
        UUID observedRule;
        int memoryRevision;
        long started, restUntil;
        int revision;
        final Map<UUID, Long> cooldown = new HashMap<>();
    }
    public record ActionIdentity(UUID table, UUID move, int actionIndex, long started) {}
    private record Activation(long started, int authoredOrder, UUID target, SightMemory.Target aim, ActionIdentity identity) {}

    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Activation> activations = new HashMap<>();
    private List<MoveSetOrder.Table> order;
    private int orderRevision;
    public long activation(UUID id) { var action = activations.get(id); return action == null ? Long.MIN_VALUE : action.started(); }
    public UUID target(UUID id) { var action = activations.get(id); return action == null ? null : action.target(); }
    public SightMemory.Target aim(UUID id) { var action = activations.get(id); return action == null ? null : action.aim(); }
    public ActionIdentity action(UUID id) { var action = activations.get(id); return action == null ? null : action.identity(); }
    public boolean aimLimited(UUID id) { return action(id) != null; }
    public boolean powered(UUID id) { return activations.containsKey(id); }
    public void clear() { states.clear(); activations.clear(); order = null; }
    public static String status(MoveSet.Move move, HeartBlockEntity heart) {
        if (!move.config().enabled()) return "disabled";
        if (move.actions().isEmpty()) return "empty";
        for (var a : move.actions()) {
            var target = heart.modules().find(a.module());
            if (target == null || !MoveSet.supportsModule(target.type())) return "missing";
            if (!HeartModules.status(target, heart).equals("valid")) return "unavailable";
            if (target.value("trigger") != 4) return "trigger";
        }
        return "ready";
    }
    public void tick(HeartBlockEntity heart) {
        activations.clear();
        if (!heart.operational() || !heart.wireless().master()) { clear(); return; }
        var level = heart.getLevel();
        long now = level.getGameTime(); var keep = new HashSet<UUID>();
        int revision = heart.modules().revision();
        if (order == null || orderRevision != revision) {
            order = MoveSetOrder.compile(heart);
            orderRevision = revision;
        }
        for (var entry : order) {
            var table = entry.module();
            if (!HeartModules.triggered(table, heart)) continue;
            keep.add(table.id()); var state = states.computeIfAbsent(table.id(), ignored -> new State());
            var moves = table.moveSet().moves();
            boolean ignore = ignoresConditions(table, heart.isControlCore());
            boolean sensorSource = table.value("facingSource") == 1;
            if (ignore || sensorSource || state.memoryRevision != revision) forget(state);
            state.memoryRevision = revision;
            if (state.observedRule != null) observe(heart, state, moves, now);
            var sensorAim = !ignore && sensorSource ? heart.modules().events().sensorAim(heart, table.facingSensorId()) : null;
            state.cooldown.keySet().removeIf(id -> moves.stream().noneMatch(m -> m.config().id().equals(id)));
            var running = moves.stream().filter(m -> m.config().id().equals(state.current)).findFirst().orElse(null);
            if (state.current != null && (running == null || !status(running, heart).equals("ready") || state.revision != revision
                    || now - state.started >= Math.max(running.length(), (int)running.config().value("dispatchBusy")))) {
                state.target = null;
                if(ModuleHurt.once(table)) { heart.modules().hurtTriggers.finish(table.id()); state.current=null; continue; }
                state.current = null; state.restUntil = now + (running == null ? 1 : (int)running.config().value("dispatchRest")); running = null;
            }
            if (state.current == null && now >= state.restUntil) {
                long selecting = moves.isEmpty() ? 0 : CoreProfiler.begin(level.getServer());
                var eligible = new ArrayList<MoveSet.Move>();
                var selectedTargets = new HashMap<UUID, SightMemory.Target>();
                for (var move : moves) {
                    var rules = move.config();
                    if (now < state.cooldown.getOrDefault(rules.id(), 0L) || !status(move, heart).equals("ready")) continue;
                    if (!ignore) {
                        if (sensorSource || rules.memoryTicks() > 0 || rules.value("inheritMoveTarget") == 1) {
                            var target = select(heart, table, rules, state, sensorAim, now);
                            if (target == null) continue;
                            selectedTargets.put(rules.id(), target);
                        } else if (!rules.targetPresent(heart)) continue;
                    }
                    eligible.add(move);
                }
                var selected = choose(eligible, state.previous, level.random.nextDouble());
                if (selecting != 0) CoreProfiler.endWork(heart, CoreProfiler.Work.MOVE_SELECT, selecting, 1);
                if (selected != null) {
                    long starting = CoreProfiler.begin(level.getServer());
                    running = eligible.stream().filter(m -> m.config().id().equals(selected)).findFirst().orElseThrow();
                    state.current = state.previous = selected; state.started = now; state.revision = revision;
                    var rules = running.config();
                    var selectedTarget = selectedTargets.get(selected);
                    if (sensorSource || rules.memoryTicks() == 0 || selectedTarget == null) {
                        forget(state);
                    } else if (selectedTarget.visible()) {
                        state.memory.see(selectedTarget.id(), selectedTarget.point());
                        state.observedRule = rules.id();
                    }
                    state.target = rules.value("inheritMoveTarget") == 1 && selectedTarget != null ? selectedTarget.id() : null;
                    state.cooldown.put(selected, now + (int)running.config().value("dispatchCooldown"));
                    if (starting != 0) CoreProfiler.endWork(heart, CoreProfiler.Work.MOVE_START, starting, 1);
                }
            }
            if(running==null && ModuleHurt.once(table)) heart.modules().hurtTriggers.finish(table.id());
            SightMemory.Target aim = null;
            boolean limited = false;
            if (running != null && state.target != null) {
                var rules = running.config();
                limited = sensorSource || rules.memoryTicks() > 0;
                if (sensorSource) {
                    aim = acceptSensor(heart, table, rules, sensorAim);
                } else if (limited) {
                    aim = rules.accept(heart, state.memory.aimAt(now), false);
                } else {
                    // Dispatch geometry only decides when a legacy move can start.
                    aim = rules.activeTarget(heart, state.target);
                }
                if (aim != null && !state.target.equals(aim.id())) aim = null;
            }
            if (running == null) continue;
            var actions = running.actions();
            for (int i = 0; i < actions.size(); i++) {
                var action = actions.get(i);
                if (!action.active(now - state.started)) continue;
                long started = state.started + action.start();
                var previous = activations.get(action.module());
                // Evaluation order can change, while equal starts still follow the authored order.
                if (previous != null && (started < previous.started()
                        || started == previous.started() && entry.authoredOrder() >= previous.authoredOrder())) continue;
                var identity = limited ? new ActionIdentity(table.id(), state.current, i, started) : null;
                activations.put(action.module(), new Activation(started, entry.authoredOrder(), state.target, aim, identity));
            }
        }
        states.keySet().retainAll(keep);
    }
    private static void forget(State state) {
        state.memory.clear();
        state.observedRule = null;
    }

    private static void observe(HeartBlockEntity heart, State state, List<MoveSet.Move> moves, long now) {
        if (state.memory.aimAt(now) == null) {
            forget(state);
            return;
        }
        for (var move : moves) {
            var rules = move.config();
            if (!rules.id().equals(state.observedRule)) continue;
            var observed = rules.observe(heart, state.memory.lastSeen());
            if (observed == null) break;
            if (observed.visible()) {
                state.memory.see(observed.id(), observed.point());
                return;
            }
            if (state.memory.remember(now, rules.memoryTicks()) != null) return;
            break;
        }
        forget(state);
    }

    private static SightMemory.Target acceptSensor(HeartBlockEntity heart, VirtualModule table, MoveRules rules, SightMemory.Target source) {
        if (source == null) return null;
        var sensor = heart.modules().find(table.facingSensorId());
        return sensor == null ? null : rules.accept(heart, source, !SightMemory.enabled(sensor));
    }

    private static SightMemory.Target select(HeartBlockEntity heart, VirtualModule table, MoveRules rules, State state,
                                             SightMemory.Target sensorAim, long now) {
        if (table.value("facingSource") == 1) return acceptSensor(heart, table, rules, sensorAim);
        if (state != null && rules.memoryTicks() > 0) {
            var candidate = rules.accept(heart, state.memory.aimAt(now), false);
            if (candidate != null) return candidate;
        }
        return rules.select(heart);
    }
    /** Selection override only; action validity and timing remain independent. */
    public static boolean ignoresConditions(VirtualModule table){return table!=null&&table.type().equals("move_set")&&table.value("ignoreConditions")==1;}
    public static boolean ignoresConditions(VirtualModule table, boolean controlCore){return table!=null&&table.type().equals("move_set")&&(controlCore||ignoresConditions(table));}
    public static boolean ruleLocked(VirtualModule table,String key,boolean controlCore){return ignoresConditions(table, controlCore)&&!List.of("dispatchWeight","dispatchCooldown","dispatchBusy","dispatchRest","dispatchRepeat").contains(key)&&ModuleFields.moveRules().stream().anyMatch(f->f.key().equals(key));}
    /** Eligible priority moves follow list order; weighted draws only run when none are ready. */
    public static UUID choose(List<MoveSet.Move> eligible, UUID previous, double random) {
        var priority = eligible.stream().filter(MoveSet.Move::priority).findFirst();
        if (priority.isPresent()) return priority.get().config().id();
        return MoveChoice.choose(eligible.stream().map(m -> new MoveChoice.Candidate(m.config().id(),
                (int)m.config().value("dispatchWeight"), m.config().value("dispatchRepeat") == 0)).toList(), previous, random);
    }
    public CompoundTag snapshot(VirtualModule table, HeartBlockEntity heart) {
        var tag = new CompoundTag(); var state = states.get(table.id()); long now = heart.getLevel().getGameTime();
        if (state != null && state.current != null) { tag.putUUID("Current", state.current); tag.putInt("Elapsed", (int)(now - state.started)); }
        boolean checkConditions = !ignoresConditions(table, heart.isControlCore());
        boolean sensorSource = table.value("facingSource") == 1;
        var sensorAim = checkConditions && sensorSource ? heart.modules().events().previewSensorAim(heart, table.facingSensorId()) : null;
        var rows = new ListTag();
        for (var move : table.moveSet().moves()) {
            var row = new CompoundTag(); var id = move.config().id(); row.putUUID("Id", id);
            String status = status(move, heart);
            if (status.equals("ready") && checkConditions) {
                var rules = move.config();
                boolean present = sensorSource || rules.memoryTicks() > 0
                        ? select(heart, table, rules, state, sensorAim, now) != null : rules.targetPresent(heart);
                if (!present) status = "conditions";
            }
            row.putString("Status", status); row.putInt("Cooldown", state == null ? 0 : (int)Math.max(0, state.cooldown.getOrDefault(id, 0L) - now));
            var problems = new ListTag(); var seen = new HashSet<UUID>();
            for (int i=0;i<move.actions().size();i++) {
                var action=move.actions().get(i); if(!seen.add(action.module()))continue;
                var target=heart.modules().find(action.module());
                String reason=target==null?"missing":!MoveSet.supportsModule(target.type())?"unsupported":!target.enabled()?"disabled":HeartModules.status(target,heart);
                if(reason.equals("valid"))reason=target.value("trigger")!=4?"trigger":"";
                if(!reason.isEmpty()) {var issue=new CompoundTag();issue.putUUID("Module",action.module());issue.putInt("Action",i+1);issue.putString("Reason",reason);problems.add(issue);}
            }
            row.put("Problems",problems); rows.add(row);
        }
        tag.put("Rows", rows); return tag;
    }
}
