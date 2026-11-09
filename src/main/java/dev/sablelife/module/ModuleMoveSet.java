package dev.sablelife.module;

import dev.sablelife.heart.*;
import net.minecraft.nbt.*;
import java.util.*;

/** Move set owns conditions, weighted/priority selection and direct action timing. */
public final class ModuleMoveSet {
    private static final class State {
        UUID current, previous;
        long started, restUntil;
        int revision;
        final Map<UUID, Long> cooldown = new HashMap<>();
    }
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID,Long> activations=new HashMap<>();
    public long activation(UUID id){return activations.getOrDefault(id,Long.MIN_VALUE);}
    private final Set<UUID> active = new HashSet<>();
    public boolean powered(UUID id) { return active.contains(id); }
    public void clear() { states.clear(); active.clear();activations.clear(); }
    public static String status(MoveSet.Move move, HeartBlockEntity heart) {
        if (!move.config().enabled()) return "disabled";
        if (move.actions().isEmpty()) return "empty";
        for (var a : move.actions()) {
            var target = heart.modules().find(a.module());
            if (target == null || !MoveSet.TARGETS.contains(target.type())) return "missing";
            if (!HeartModules.status(target, heart).equals("valid")) return "unavailable";
            if (target.value("trigger") != 4) return "trigger";
        }
        return "ready";
    }
    public void tick(HeartBlockEntity heart) {
        active.clear();activations.clear();
        if (!heart.operational() || !heart.wireless().master()) { clear(); return; }
        long now = heart.getLevel().getGameTime(); var keep = new HashSet<UUID>();
        for (var table : heart.modules().all()) {
            if (!table.type().equals("move_set") || !HeartModules.triggered(table, heart)) continue;
            keep.add(table.id()); var state = states.computeIfAbsent(table.id(), ignored -> new State());
            var moves = table.moveSet().moves();
            state.cooldown.keySet().removeIf(id -> moves.stream().noneMatch(m -> m.config().id().equals(id)));
            var running = moves.stream().filter(m -> m.config().id().equals(state.current)).findFirst().orElse(null);
            if (state.current != null && (running == null || !status(running, heart).equals("ready") || state.revision != heart.modules().revision()
                    || now - state.started >= Math.max(running.length(), (int)running.config().value("dispatchBusy")))) {
                if(ModuleHurt.once(table)) { heart.modules().hurtTriggers.finish(table.id()); state.current=null; continue; }
                state.current = null; state.restUntil = now + (running == null ? 1 : (int)running.config().value("dispatchRest")); running = null;
            }
            if (state.current == null && now >= state.restUntil) {
                var eligible = moves.stream().filter(m -> status(m, heart).equals("ready") && now >= state.cooldown.getOrDefault(m.config().id(), 0L)
                        && (ignoresConditions(table, heart.isControlCore())||m.config().targetPresent(heart))).toList();
                var selected = choose(eligible, state.previous, heart.getLevel().random.nextDouble());
                if (selected != null) {
                    running = eligible.stream().filter(m -> m.config().id().equals(selected)).findFirst().orElseThrow();
                    state.current = state.previous = selected; state.started = now; state.revision = heart.modules().revision();
                    state.cooldown.put(selected, now + (int)running.config().value("dispatchCooldown"));
                }
            }
            if(running==null && ModuleHurt.once(table)) heart.modules().hurtTriggers.finish(table.id());
            if (running != null) for (var a : running.actions()) if (a.active(now - state.started)){active.add(a.module());activations.merge(a.module(),state.started+a.start(),Math::max);}
        }
        states.keySet().retainAll(keep);
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
        var rows = new ListTag();
        for (var move : table.moveSet().moves()) {
            var row = new CompoundTag(); var id = move.config().id(); row.putUUID("Id", id);
            String status = status(move, heart);
            if (status.equals("ready") && !ignoresConditions(table, heart.isControlCore()) && !move.config().targetPresent(heart)) status = "conditions";
            row.putString("Status", status); row.putInt("Cooldown", state == null ? 0 : (int)Math.max(0, state.cooldown.getOrDefault(id, 0L) - now));
            var problems = new ListTag(); var seen = new HashSet<UUID>();
            for (int i=0;i<move.actions().size();i++) {
                var action=move.actions().get(i); if(!seen.add(action.module()))continue;
                var target=heart.modules().find(action.module());
                String reason=target==null?"missing":!MoveSet.TARGETS.contains(target.type())?"unsupported":!target.enabled()?"disabled":HeartModules.status(target,heart);
                if(reason.equals("valid"))reason=target.value("trigger")!=4?"trigger":"";
                if(!reason.isEmpty()) {var issue=new CompoundTag();issue.putUUID("Module",action.module());issue.putInt("Action",i+1);issue.putString("Reason",reason);problems.add(issue);}
            }
            row.put("Problems",problems); rows.add(row);
        }
        tag.put("Rows", rows); return tag;
    }
}
