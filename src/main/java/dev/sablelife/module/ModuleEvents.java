package dev.sablelife.module;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import java.util.*;
import java.util.function.Predicate;

/** Event state belongs to each core/module, never to a template or physical marker. */
public final class ModuleEvents {
    private static final class State {
        final Set<UUID> inside = new HashSet<>();
        long approachDue, hurtDue, ambientDue, contactDue;
        boolean touching;
        boolean triggerPowered;
        long triggerDue;
        final Map<UUID, Long> musicListeners = new HashMap<>();
        int musicFade = 60;
    }
    private final Map<UUID, State> sounds = new HashMap<>();
    private final ModuleRedstone redstone = new ModuleRedstone();
    private final TargetNavigation navigation = new TargetNavigation();
    public TargetNavigation navigation() { return navigation; }
    private HeartBlockEntity owner;
    private long emitted;
    private final Set<UUID> sensorOutputIds=new HashSet<>();
    private final Set<UUID> sensorTargets=new HashSet<>();
    private final Set<UUID> sensorPowered=new HashSet<>();
    private record SensorSelection(long tick, int revision, SightMemory.Target target) {}
    private final Map<UUID, SightMemory> sensorMemory = new HashMap<>();
    private int sensorRevision;
    private final Map<UUID, SensorSelection> sensorSelections = new HashMap<>();
    public boolean sensorDetected(UUID id){return sensorTargets.contains(id);}
    public boolean sensorPowered(UUID id){return sensorPowered.contains(id);}
    public long emittedSounds() { return emitted; }
    public void clear() {
        if (owner != null) { sounds.forEach((id, state) -> stopAudio(owner, id, state)); ModuleSignals.clear(owner); owner.wireless().programOutputs("sensor",Map.of());owner.wireless().programOutputs("navigation",Map.of());owner.wireless().clearModuleFrequencies(sensorOutputIds); }
        sensorOutputIds.clear();sensorTargets.clear();sensorPowered.clear();sensorSelections.clear(); sensorMemory.clear(); sounds.clear(); redstone.clear(); navigation.clear(); owner = null;
    }
    public static boolean supported(String type) { return type.equals("sensor") || type.equals("sound") || type.equals("trigger_sound") || type.equals("bgm") || type.equals("navigation"); }
    public void tick(HeartBlockEntity heart) {
        owner = heart;
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        var weakFaces = new HashMap<ModuleSignals.Face, Integer>();
        var activeSounds = new HashSet<UUID>();
        var activeNavigation = new HashSet<UUID>();
        var activeSensors = new HashSet<UUID>();
        sensorTargets.clear();sensorPowered.clear();
        var faceSignals = new HashMap<ModuleSignals.Face, Integer>();
        var navigationChannels=new HashMap<Integer,Integer>();var trace=new HashMap<UUID,Map<Integer,Integer>>();
        sensorOutputIds.clear();
        for (var module : heart.modules().all()) {
            if(module.type().equals("sensor"))sensorOutputIds.add(module.id());
            if (!supported(module.type())) continue;
            if(heart.isControlCore()&&!dev.sablelife.heart.RedstoneCorePolicy.allowed(module))continue;
            HeartModules.latchDetachedMark(module, heart);
            if (module.type().equals("trigger_sound")) {
                // Retain state while power is off: cooldown persists and one-shot roars finish naturally.
                // Invalid/disabled/dead modules are excluded and their audio is stopped below.
                if (heart.operational() && (!heart.isControlCore()||heart.wireless().master()) && HeartModules.status(module, heart).equals("valid")) {
                    activeSounds.add(module.id());
                    var state = sounds.computeIfAbsent(module.id(), ignored -> new State());
                    boolean powered=HeartModules.triggered(module, heart);
                    triggerSound(level, heart, module, state, powered);
                    contactSound(level,heart,module,state,powered);
                }
                continue;
            }
            if (!HeartModules.triggered(module, heart)) continue;
            if (module.type().equals("navigation")) {
                activeNavigation.add(module.id()); navigation.tick(level, heart, module, faceSignals);var own=new HashMap<Integer,Integer>();navigation.publish(module,own);own.forEach((ch,power)->navigationChannels.merge(ch,power,Math::max));trace.put(module.id(),own); continue;
            }
            if (module.type().equals("bgm")) {
                activeSounds.add(module.id());
                var state = sounds.computeIfAbsent(module.id(), ignored -> new State());
                music(level, heart, module, state, SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter()));
                continue;
            }
            if (module.type().equals("sensor")) {
                activeSensors.add(module.id());
                boolean detected=detect(level,heart,module);if(detected)sensorTargets.add(module.id());
                if (detected!=(module.value("sensorInvert")==1)) {
                    sensorPowered.add(module.id());
                    var anchor = module.anchors().getFirst();
                    // Supply weak power into the marked block through its selected face,
                    // like Wireless redstone surface mode; the marker is a receiver, not an emitter.
                    weakFaces.merge(new ModuleSignals.Face(anchor.position(anchor.body(heart)).relative(anchor.face()), anchor.face().getOpposite()), 15, Math::max);
                }
            } else {
                activeSounds.add(module.id());
                var state = sounds.computeIfAbsent(module.id(), ignored -> new State());
                var center = module.anchors().getFirst().world(heart, false);
                var nearby = new HashSet<UUID>();
                for (var player : level.players()) if (player.isAlive() && !player.isSpectator()
                        && SableBodyProbe.toWorld(level, player.getEyePosition()).distanceToSqr(center) <= Math.pow(module.value("radius"), 2)) nearby.add(player.getUUID());
                boolean entered = nearby.stream().anyMatch(id -> !state.inside.contains(id));
                state.inside.clear(); state.inside.addAll(nearby);
                if (entered && level.getGameTime() >= state.approachDue) {
                    state.approachDue = level.getGameTime() + (int) module.value("cooldown");
                    play(level, heart, module, "approach");
                }
                long now = level.getGameTime();
                if (module.hasSound("ambient")) {
                    if (state.ambientDue == 0) state.ambientDue = now + ambientDelay(level, module);
                    if (now >= state.ambientDue) {
                        play(level, heart, module, "ambient");
                        state.ambientDue = now + ambientDelay(level, module);
                    }
                } else state.ambientDue = 0;

            }
        }
        var iterator = sounds.entrySet().iterator();
        while (iterator.hasNext()) { var entry = iterator.next(); if (!activeSounds.contains(entry.getKey())) { stopAudio(heart, entry.getKey(), entry.getValue()); iterator.remove(); } }
        heart.wireless().programOutputs("navigation",navigationChannels,trace);
        redstone.tick(heart, weakFaces,sensorPowered);
        heart.wireless().collectOutputFaces(weakFaces);
        ModuleSignals.updateWeakFaces(heart, weakFaces);
        ModuleSignals.update(heart, Map.of());
        navigation.retain(activeNavigation);
        sensorMemory.keySet().retainAll(activeSensors);
        sensorSelections.entrySet().removeIf(entry -> entry.getValue().tick() != level.getGameTime()
                || !activeSensors.contains(entry.getKey()));
        ModuleSignals.updateFaces(heart, faceSignals);
    }
    private void triggerSound(ServerLevel level, HeartBlockEntity heart, VirtualModule module, State state, boolean powered) {
        boolean repeat = module.value("soundMode") == 1;
        long now = level.getGameTime();
        if (powered && (repeat || !state.triggerPowered) && now >= state.triggerDue) {
            play(level, heart, module, "triggerSound");
            state.triggerDue = now + (repeat ? Math.max((int) module.value("interval"), (int) module.value("cooldown")) : (int) module.value("cooldown"));
        }
        state.triggerPowered = powered;
    }
    private void contactSound(ServerLevel level,HeartBlockEntity heart,VirtualModule module,State state,boolean powered){
        boolean touching=powered&&module.value("contactMode")==1&&contact(level,heart,module);
        long now=level.getGameTime();
        if(touching&&!state.touching&&now>=state.contactDue){play(level,heart,module,"contactSound");state.contactDue=now+(int)module.value("contactCooldown");}
        state.touching=touching;
    }
    private static void stopMusic(HeartBlockEntity heart, UUID id, State state) {
        if (heart.getLevel() instanceof ServerLevel level) for (UUID listener : state.musicListeners.keySet()) {
            var player = level.getServer().getPlayerList().getPlayer(listener);
            if (player != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new ModuleNetworking.BattleMusic(id, "", false, false, 0, state.musicFade));
        }
        state.musicListeners.clear();
    }
    private static void stopAudio(HeartBlockEntity heart, UUID id, State state) {
        stopMusic(heart, id, state);
        if (heart.getLevel() instanceof ServerLevel level) for (var player : level.players())
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ModuleNetworking.ModuleSound(id, "", true, Vec3.ZERO, 0, 1));
    }
    public static boolean musicReady(HeartBlockEntity heart,VirtualModule module){return heart.isControlCore()||module.value("bgmStart")!=0||heart.combatMusicHits()>0;}
    private void music(ServerLevel level, HeartBlockEntity heart, VirtualModule module, State state, Vec3 center) {
        state.musicFade = (int) module.value("bgmFade");
        var timeline=module.soundTimeline("bgm");
        if (!timeline.audible() || !musicReady(heart,module)) { stopMusic(heart, module.id(), state); return; }
        var nearby = new HashSet<UUID>();
        for (var player : level.players()) if (player.isAlive() && !player.isSpectator()
                && SableBodyProbe.toWorld(level, player.position()).distanceToSqr(center) <= Math.pow(timeline.rows().stream().filter(r->r.enabled()&&r.volume()>0).mapToDouble(dev.sablelife.sound.SoundTimeline.Row::radius).max().orElse(1),2)) {
            UUID id = player.getUUID(); nearby.add(id); var previous = state.musicListeners.get(id);
            // Only entry starts the track. Further hits play their own feedback over the current BGM.
            boolean restart = previous == null;
            if (restart || level.getGameTime() % 20 == 0) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new ModuleNetworking.BattleMusic(module.id(), module.text("bgm"), true, restart, 1, state.musicFade,timeline.save(),center));
            state.musicListeners.put(id, heart.combatMusicHits());
        }
        for (UUID id : List.copyOf(state.musicListeners.keySet())) if (!nearby.contains(id)) {
            var player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new ModuleNetworking.BattleMusic(module.id(), "", false, false, 0, state.musicFade));
            state.musicListeners.remove(id);
        }
    }
    private int ambientDelay(ServerLevel level, VirtualModule module) {
        int min = Math.max(20, (int) module.value("ambientMin"));
        int max = Math.max(min, (int) module.value("ambientMax"));
        return min + level.random.nextInt(max - min + 1);
    }
    /** Selected local face of the contact marker, following its limb orientation. */
    public static Vec3 contactStart(HeartBlockEntity heart, VirtualModule module) {
        return module.anchors().getFirst().world(heart, false).add(contactDirection(heart, module).scale(.501));
    }
    public static Vec3 contactDirection(HeartBlockEntity heart, VirtualModule module) {
        var choices = ModuleFields.find("trigger_sound", "contactDirection").choices();
        var face = net.minecraft.core.Direction.byName(choices.get(Math.clamp((int) module.value("contactDirection"), 0, choices.size() - 1)));
        return dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond(module.anchors().getFirst().body(heart).logicalPose().orientation()), Vec3.atLowerCornerOf(face.getNormal()));
    }
    public static boolean contact(ServerLevel level, HeartBlockEntity heart, VirtualModule module) {
        return contactHit(level,heart,module)!=null;
    }
    /** Shared contact probe for sound and spatial effects; excludes the creature itself. */
    public static BlockHitResult contactHit(ServerLevel level,HeartBlockEntity heart,VirtualModule module){
        var start = contactStart(heart, module);
        var hit = level.clip(new ClipContext(start, start.add(contactDirection(heart, module).scale(module.value("contactDistance"))),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
        return hit.getType() == HitResult.Type.BLOCK
                && !heart.creatureId().equals(dev.sablelife.ownership.BodyOwnership.get(level).owner(hit.getBlockPos())) ? hit : null;
    }
    public void hurt(HeartBlockEntity heart) {
        if(heart.isControlCore())return;
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        for (var module : heart.modules().all()) if (module.type().equals("sound")
                && (heart.health().state() == HeartState.ALIVE || heart.health().state() == HeartState.DYING)
                && HeartModules.status(module, heart).equals("valid") && HeartModules.controlPowered(module, heart)) {
            var state = sounds.computeIfAbsent(module.id(), ignored -> new State());
            if (level.getGameTime() < state.hurtDue) continue;
            state.hurtDue = level.getGameTime() + (int) module.value("cooldown");
            play(level, heart, module, "hurt");
        }
    }
    private void play(ServerLevel level, HeartBlockEntity heart, VirtualModule module, String key) {
        if (!module.hasSound(key)) return;
        var point = module.anchors().getFirst().world(heart, false);
        heart.modules().registerAudio(level,module.id());
        ModuleAudio.play(level,module,key,point);
        emitted++;
    }
    public static boolean withinShape(HeartBlockEntity heart, VirtualModule module, Vec3 delta) {
        if (module.value("shape") == 0) {
            double distance = delta.lengthSqr();
            return distance <= Math.pow(module.value("radius"), 2) && distance >= Math.pow(module.value("innerRadius"), 2);
        }
        var forward = ModuleCombat.direction(heart, module); double along = delta.dot(forward);
        double width = .5 + (module.value("frontWidth") - .5) * along / module.value("radius");
        return along >= 0 && along <= module.value("radius") && delta.subtract(forward.scale(along)).lengthSqr() <= width * width;
    }
    private boolean detect(ServerLevel level, HeartBlockEntity heart, VirtualModule module) {
        var selection = sensorSelections.get(module.id());
        if (SightMemory.ticks(module) > 0 || selection != null && selection.tick() == level.getGameTime())
            return sensorAim(heart, module.id()) != null;
        sensorMemory.remove(module.id());
        var center = module.anchors().getFirst().world(heart, false);
        return findSensorTarget(level, heart, module, center, sight(level, heart, module, center), false) != null;
    }

    public LivingEntity sensorTarget(HeartBlockEntity heart, UUID id) {
        var aim = sensorAim(heart, id);
        var sensor = heart.modules().find(id);
        return aim == null || sensor == null ? null : ModuleTargets.resolveForModule((ServerLevel) heart.getLevel(), aim.id(), heart, sensor);
    }

    /** One target and last visible point per sensor and tick, shared by its consumers. */
    public SightMemory.Target sensorAim(HeartBlockEntity heart, UUID id) {
        return sensorAim(heart, id, true);
    }

    SightMemory.Target previewSensorAim(HeartBlockEntity heart, UUID id) {
        return sensorAim(heart, id, false);
    }

    private SightMemory.Target sensorAim(HeartBlockEntity heart, UUID id, boolean update) {
        if (id == null || !(heart.getLevel() instanceof ServerLevel level)) return null;
        var sensor = heart.modules().find(id);
        if (sensor == null || !sensor.type().equals("sensor") || !HeartModules.triggered(sensor, heart)) {
            if (update) {
                sensorSelections.remove(id);
                sensorMemory.remove(id);
            }
            return null;
        }
        long now = level.getGameTime();
        int revision = heart.modules().revision();
        if (update && sensorRevision != revision) {
            sensorMemory.clear();
            sensorRevision = revision;
        }
        var selection = sensorSelections.get(id);
        if (selection != null && selection.tick() == now && selection.revision() == revision) {
            var target = selection.target();
            if (target == null || ModuleTargets.matchesTrackingTarget(heart, sensor,
                    ModuleTargets.resolveForModule(level, target.id(), heart, sensor))) return target;
        }
        var center = sensor.anchors().getFirst().world(heart, false);
        var sees = sight(level, heart, sensor, center);
        int ticks = SightMemory.ticks(sensor);
        var memory = ticks > 0 && sensorRevision == revision ? sensorMemory.get(id) : null;
        SightMemory.Target selected = null;
        if (memory != null) {
            var last = memory.aimAt(now);
            if (last != null) {
                var old = ModuleTargets.resolveForModule(level, last.id(), heart, sensor);
                if (ModuleTargets.matchesTrackingTarget(heart, sensor, old)) {
                    var point = targetCenter(level, old);
                    boolean withinCurrent = withinShape(heart, sensor, point.subtract(center));
                    boolean withinLast = withinShape(heart, sensor, last.point().subtract(center));
                    if (withinCurrent || withinLast) {
                        if (SightMemory.canRecheck(center, point) && sees.test(old)) {
                            if (withinCurrent) selected = new SightMemory.Target(last.id(), point, true);
                        } else if (withinLast) {
                            selected = update ? memory.remember(now, ticks) : new SightMemory.Target(last.id(), last.point(), false);
                        }
                    }
                }
            }
            if (update && selected == null) memory.clear();
        }
        if (selected == null) selected = findSensorTarget(level, heart, sensor, center, sees, true);
        if (update) {
            if (ticks == 0 || selected == null) {
                sensorMemory.remove(id);
            } else if (selected.visible()) {
                sensorMemory.computeIfAbsent(id, ignored -> new SightMemory()).see(selected.id(), selected.point());
            }
            sensorSelections.put(id, new SensorSelection(now, revision, selected));
        }
        return selected;
    }

    private static Vec3 targetCenter(ServerLevel level, LivingEntity target) {
        return SableBodyProbe.toWorld(level, target.getBoundingBox().getCenter());
    }

    private static Predicate<LivingEntity> sight(ServerLevel level, HeartBlockEntity heart, VirtualModule module, Vec3 center) {
        if (!SightMemory.enabled(module)) return ignored -> true;
        var orientation = new org.joml.Quaterniond(module.anchors().getFirst().body(heart).logicalPose().orientation());
        var bodies = heart.isControlCore() ? new HashSet<>(SableBodyProbe.inspect(level, heart.getBlockPos()).connectedBodies()) : Set.<UUID>of();
        return target -> ModuleTargets.sensed(level, heart, center, orientation, target, bodies);
    }

    private static SightMemory.Target findSensorTarget(ServerLevel level, HeartBlockEntity heart, VirtualModule sensor,
                                                        Vec3 center, Predicate<LivingEntity> sees, boolean chooseBest) {
        if (sensor.value("priorityOnly") == 1 && !heart.relations().hasPriorityMobs()) return null;
        var bounds = new AABB(center, center).inflate(sensor.value("radius") + 2);
        SightMemory.Target best = null;
        int bestRank = Integer.MAX_VALUE;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (var entity : ModuleTargets.queryForModule(level, heart, sensor, bounds, target -> ModuleTargets.matchesTrackingTarget(heart, sensor, target))) {
            var point = targetCenter(level, entity);
            var delta = point.subtract(center);
            if (!withinShape(heart, sensor, delta)) continue;
            var id = entity.getUUID();
            int rank = chooseBest ? ModuleTargets.rank(heart, entity) : 0;
            double distance = delta.lengthSqr();
            if (best != null && (rank > bestRank || rank == bestRank
                    && (distance > bestDistance || distance == bestDistance && id.compareTo(best.id()) >= 0))) continue;
            if (!sees.test(entity)) continue;
            best = new SightMemory.Target(id, point, true);
            if (!chooseBest) return best;
            bestRank = rank;
            bestDistance = distance;
        }
        return best;
    }
}
