package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/** Evaluates a sensor's producers before the tables that use it. */
final class MoveSetOrder {
    record Table(VirtualModule module, int authoredOrder) {}

    private static final class Sensor {
        final List<Integer> consumers = new ArrayList<>();
        int pendingProducers;
    }

    static List<Table> compile(HeartBlockEntity heart) {
        var tables = new ArrayList<Table>();
        var controlledSensors = new HashSet<UUID>();
        for (var module : heart.modules().all()) {
            if (!module.enabled()) continue;
            if (module.type().equals("move_set")) tables.add(new Table(module, tables.size()));
            if (module.type().equals("sensor") && module.value("trigger") == 4) controlledSensors.add(module.id());
        }
        if (controlledSensors.isEmpty() || heart.isControlCore()) return List.copyOf(tables);

        var dependencies = new HashMap<UUID, Sensor>();
        var inputs = new Sensor[tables.size()];
        for (var entry : tables) {
            var table = entry.module();
            var sensor = table.facingSensorId();
            if (table.value("facingSource") != 1 || ModuleMoveSet.ignoresConditions(table) || !controlledSensors.contains(sensor)) continue;
            var dependency = dependencies.computeIfAbsent(sensor, ignored -> new Sensor());
            dependency.consumers.add(entry.authoredOrder());
            inputs[entry.authoredOrder()] = dependency;
        }
        if (dependencies.isEmpty()) return List.copyOf(tables);

        var outputs = new ArrayList<Set<Sensor>>(tables.size());
        for (var entry : tables) {
            var produced = new HashSet<Sensor>();
            for (var move : entry.module().moveSet().moves()) {
                if (!move.config().enabled()) continue;
                for (var action : move.actions()) {
                    var dependency = dependencies.get(action.module());
                    if (dependency != null && produced.add(dependency)) dependency.pendingProducers++;
                }
            }
            outputs.add(produced);
        }

        var ready = new PriorityQueue<Integer>();
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] == null || inputs[i].pendingProducers == 0) ready.add(i);
        }
        var ordered = new ArrayList<Table>(tables.size());
        var scheduled = new boolean[tables.size()];
        int firstUnscheduled = 0;
        while (ordered.size() < tables.size()) {
            if (ready.isEmpty()) {
                // A cycle reads any outputs already published. Evaluate each table once in authored order.
                while (scheduled[firstUnscheduled]) firstUnscheduled++;
                ready.add(firstUnscheduled);
            }
            int index = ready.remove();
            scheduled[index] = true;
            ordered.add(tables.get(index));
            for (var output : outputs.get(index)) {
                if (--output.pendingProducers != 0) continue;
                for (int consumer : output.consumers) {
                    if (!scheduled[consumer]) ready.add(consumer);
                }
            }
        }
        return List.copyOf(ordered);
    }
}
