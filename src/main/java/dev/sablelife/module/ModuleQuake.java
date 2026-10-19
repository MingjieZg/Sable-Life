package dev.sablelife.module;

import dev.sablelife.attack.ModuleDamage;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;

/** Each cast freezes its center, heading and warning circles; moving or jumping can evade a front. */
public final class ModuleQuake {
    private static final int MAX_VISUALS = 192;
    private record Area(Vec3 origin, List<Vec3> warnings) {}
    private static final class State {
        long due, start, finish;
        VirtualModule cast;
        Vec3 origin, direction;
        List<Area> areas = List.of();
        final Map<Integer, Set<UUID>> hit = new HashMap<>();
        final Set<String> columns = new HashSet<>();
        final List<QuakeGroundBlock> visuals = new ArrayList<>();
        boolean sounding;
    }
    private final Map<UUID, State> states = new HashMap<>();
    private ServerLevel level;
    public String phase(UUID id) {
        var s = states.get(id);
        return s == null || s.cast == null ? "waiting" : level.getGameTime() < s.start ? "warning" : "wave";
    }
    private void stop(UUID id, State state) {
        state.visuals.forEach(QuakeGroundBlock::discard);
        if (level != null) for (var player : level.players())
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new ModuleNetworking.ModuleSound(id, "", true, Vec3.ZERO, 0, 1));
    }
    public void clear() { states.forEach(this::stop); states.clear(); level = null; }
    private static boolean eligible(LivingEntity e, VirtualModule m) {
        return ModuleTargets.matches(e,m);
    }
    private static Vec3 direction(HeartBlockEntity heart, VirtualModule module) {
        var d = ModuleCombat.direction(heart, module); d = new Vec3(d.x, 0, d.z).normalize();
        return d.lengthSqr() < .001 ? new Vec3(0, 0, -1) : d;
    }
    private static List<LivingEntity> targets(ServerLevel level, HeartBlockEntity heart, VirtualModule m, Vec3 origin) {
        double r = m.value("quakeRange") + m.value("quakeWidth");
        return ModuleTargets.query(level, heart.creatureId(), new AABB(origin, origin).inflate(r, 64, r), e -> eligible(e, m));
    }
    private static Vec3 targetFloor(ServerLevel level,HeartBlockEntity heart,LivingEntity target) {
        return ModuleGroundStrike.floor(level,heart,target.position(),target instanceof ModuleTargets.LifeTarget?target.getUUID():null);
    }
    public static List<LivingEntity> detectTargets(ServerLevel level, HeartBlockEntity heart, VirtualModule m, Vec3 origin) {
        double radius = m.value("quakeDetectRange");
        return ModuleTargets.query(level, heart.creatureId(), new AABB(origin, origin).inflate(radius), e -> eligible(e, m))
                .stream().filter(e -> e.position().distanceToSqr(origin) <= radius * radius)
                .sorted(Comparator.comparingDouble(e -> e.position().distanceToSqr(origin))).toList();
    }
    private static boolean path(VirtualModule m, Vec3 d, Vec3 delta, double previous, double current) {
        if (m.value("quakeMode") == 2) return Math.hypot(delta.x, delta.z) <= m.value("quakeRange");
        return QuakeGeometry.crossed(m.value("quakeMode") != 0, delta.dot(d), delta.x * d.z - delta.z * d.x,
                Math.hypot(delta.x, delta.z), previous, current, m.value("quakeWidth"), m.value("quakeRange"));
    }
    private static DustParticleOptions color(VirtualModule m) {
        return new DustParticleOptions(ModuleColors.vector(m,"quakeColor"),1.3F);
    }
    private static void dot(ServerLevel level, ServerPlayer viewer, DustParticleOptions color, Vec3 point) {
        if (viewer == null) ModuleParticles.send(level, color, point.x, point.y + .12, point.z, 1, 0, 0, 0, 0);
        else level.sendParticles(viewer, color, true, point.x, point.y + .12, point.z, 1, 0, 0, 0, 0);
    }
    private static void warning(ServerLevel level, ServerPlayer viewer, VirtualModule m, Vec3 origin, Vec3 direction, List<Vec3> feet) {
        var dust = color(m); double range = m.value("quakeRange"), width = m.value("quakeWidth");
        for (var foot : feet) for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24.; dot(level, viewer, dust, foot.add(Math.cos(a), 0, Math.sin(a)));
        }
        for (int i = 0; i < 64; i++) {
            if (m.value("quakeMode") != 0) {
                double a = Math.PI * 2 * i / 64.; dot(level, viewer, dust, origin.add(Math.cos(a) * range, 0, Math.sin(a) * range));
            } else {
                var side = new Vec3(-direction.z, 0, direction.x).scale(width / 2);
                var point = origin.add(direction.scale(range * i / 63.));
                dot(level, viewer, dust, point.add(side)); dot(level, viewer, dust, point.subtract(side));
            }
        }
    }
    public static void preview(ServerPlayer player, HeartBlockEntity heart, VirtualModule m) {
        var server = (ServerLevel) player.level();
        var origin = ModuleGroundStrike.floor(server, heart, m.anchors().getFirst().world(heart, false).add(direction(heart, m).scale(m.value("startDistance"))));
        if (origin != null) warning(server, player, m, origin, direction(heart, m), List.of());
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("dashboard.sablelife.quake_help"), true);
    }
    private boolean begin(HeartBlockEntity heart, VirtualModule module, State s, long now) {
        var origin = ModuleGroundStrike.floor(level, heart, module.anchors().getFirst().world(heart, false).add(direction(heart, module).scale(module.value("startDistance"))));
        if (origin == null) return false;
        List<Vec3> centers = List.of(origin);
        if (module.value("quakeOrigin") == 1) {
            centers = detectTargets(level, heart, module, origin).stream()
                    .map(e -> targetFloor(level, heart, e)).filter(Objects::nonNull)
                    .distinct().limit(module.value("targetMultiple") == 1 ? 64 : 1).toList();
            if (centers.isEmpty()) return false;
        }
        origin = centers.getFirst();
        s.cast = VirtualModule.load(module.save()); s.origin = origin; s.direction = direction(heart, module);
        s.start = now + (long) module.value("warningTime");
        long travel = module.value("quakeMode") == 2 ? 0 : (long) Math.ceil(module.value("quakeRange") / module.value("quakeSpeed"));
        s.finish = s.start + travel + ((long) module.value("quakeWaves") - 1) * (long) module.value("quakeGap") + 15;
        s.hit.clear(); s.columns.clear(); s.sounding = false;
        s.areas = centers.stream().map(point -> new Area(point,
                targets(level, heart, module, point).stream().filter(e -> path(module, s.direction, e.position().subtract(point), 0, module.value("quakeRange")))
                .limit(64).map(e -> targetFloor(level, heart, e)).filter(Objects::nonNull).toList())).toList();
        ModuleAudio.play(level,module,"warningSound",origin);
        return true;
    }
    private void visual(HeartBlockEntity heart, State s, int wave, Vec3 point) {
        var floor = ModuleGroundStrike.floor(level, heart, point); if (floor != null) visualAtFloor(s,wave,floor,true);
    }
    private boolean liftable(Vec3 floor){
        var block=level.getBlockState(BlockPos.containing(floor.add(0,-.1,0)));
        return !block.isAir()&&!block.hasBlockEntity()&&block.getFluidState().isEmpty()&&block.getRenderShape()==net.minecraft.world.level.block.RenderShape.MODEL;
    }
    private void visualAtFloor(State s,int wave,Vec3 floor,boolean lift){
        var blockPos = BlockPos.containing(floor.add(0, -.1, 0));
        if (!s.columns.add(wave + ":" + blockPos.asLong())) return;
        var m = s.cast; dot(level, null, color(m), floor);
        for (var id : m.particles()) {
            var particle = m.particleEntry(id);
            if (particle != null) ModuleParticles.send(level, particle, floor.x, floor.y + .2, floor.z, 1, .15, .1, .15, .03);
        }
        var block = level.getBlockState(blockPos);
        if (!lift || m.value("quakeRise") <= 0 || states.values().stream().mapToInt(state -> state.visuals.size()).sum() >= MAX_VISUALS || block.isAir() || block.hasBlockEntity()
                || !block.getFluidState().isEmpty() || block.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) return;
        var display = new QuakeGroundBlock(level, blockPos, block, m.value("quakeRise"));
        if (level.addFreshEntity(display)) s.visuals.add(display);
    }
    private void wave(HeartBlockEntity heart, State s, int wave, double previous, double current) {
        var primary = s.origin;
        // Share the hit ledger across all centers: overlapping casts cannot multiply damage.
        for (var area : s.areas) { s.origin = area.origin(); waveAt(heart, s, wave, previous, current); }
        s.origin = primary;
    }
    private void waveAt(HeartBlockEntity heart, State s, int wave, double previous, double current) {
        var m = s.cast; var hits = s.hit.computeIfAbsent(wave, ignored -> new HashSet<>());
        for (var victim : targets(level, heart, m, s.origin)) {
            if (hits.contains(victim.getUUID())) continue;
            var delta = victim.position().subtract(s.origin);
            if (!path(m, s.direction, delta, previous, current)) continue;
            var floor = targetFloor(level, heart, victim);
            if (floor == null || Math.abs(victim.getY() - floor.y) > m.value("quakeReach")) continue;
            // Teleported or modded entities can stand on a collision surface before onGround updates.
            // Rising motion is excluded so the very first tick of a jump already evades the wave.
            boolean grounded = victim.onGround() || Math.abs(victim.getY() - floor.y) <= .12 && victim.getDeltaMovement().y <= 0;
            if (m.value("quakeGrounded") == 0 && !grounded) continue;
            hits.add(victim.getUUID());
            if (m.value("damage") > 0) ModuleDamage.hurt(victim, ModuleDamage.named(ModuleDamage.physical(level),heart), (float) m.value("damage"),heart,m);
            var outward = m.value("quakeMode") != 0 ? new Vec3(delta.x, 0, delta.z).normalize() : s.direction;
            var velocity = victim.getDeltaMovement();
            ModuleTargets.attackPush(victim,new Vec3(outward.x*m.value("quakePush"),Math.max(0,QuakeGeometry.launchSpeed(m.value("quakeLaunch"))-velocity.y),outward.z*m.value("quakePush")));
            victim.hurtMarked = true;
        }
        if (m.value("quakeMode") == 2) {
            double range = m.value("quakeRange");
            double spacing = Math.max(1, Math.sqrt(Math.PI * range * range / MAX_VISUALS));
            for (double x = -range; x <= range; x += spacing) for (double z = -range; z <= range; z += spacing)
                if (x*x + z*z <= range*range) visual(heart, s, wave, s.origin.add(x, 0, z));
        } else if (m.value("quakeMode") != 0) {
            // Prepare the entire circumference before assigning the remaining display budget.
            // Sequential spawning used to exhaust the cap halfway around a ring.
            int samples=QuakeGeometry.ringSamples(current);
            var floors=new LinkedHashMap<Long,Vec3>();
            for(int i=0;i<samples;i++){
                double angle=Math.PI*2*(i+.5)/samples;
                var floor=ModuleGroundStrike.floor(level,heart,s.origin.add(Math.cos(angle)*current,0,Math.sin(angle)*current));
                if(floor==null)continue;long key=BlockPos.containing(floor.add(0,-.1,0)).asLong();
                if(!s.columns.contains(wave+":"+key))floors.putIfAbsent(key,floor);
            }
            var points=new ArrayList<>(floors.values());var candidates=new ArrayList<Integer>();
            if(m.value("quakeRise")>0)for(int i=0;i<points.size();i++)if(liftable(points.get(i)))candidates.add(i);
            int remaining=Math.max(0,MAX_VISUALS-states.values().stream().mapToInt(state->state.visuals.size()).sum());
            var chosen=new HashSet<Integer>();for(int index:QuakeGeometry.evenSamples(candidates.size(),remaining))chosen.add(candidates.get(index));
            for(int i=0;i<points.size();i++)visualAtFloor(s,wave,points.get(i),chosen.contains(i));
        } else {
            var side = new Vec3(-s.direction.z, 0, s.direction.x);
            int samples = Math.max(1, (int) Math.ceil(m.value("quakeWidth")));
            for (int i = 0; i <= samples; i++) visual(heart, s, wave,
                    s.origin.add(s.direction.scale(current)).add(side.scale(m.value("quakeWidth") * (i / (double) samples - .5))));
        }
    }
    public void tick(HeartBlockEntity heart) {
        if (!(heart.getLevel() instanceof ServerLevel server)) return;
        level = server; long now = level.getGameTime(); var active = new HashSet<UUID>();
        for (var module : heart.modules().all()) {
            if (!module.type().equals("quake")) continue;
            if (heart.health().state() != HeartState.ALIVE || !heart.wireless().master() || !HeartModules.status(module, heart).equals("valid")) continue;
            active.add(module.id()); var s = states.computeIfAbsent(module.id(), ignored -> new State());
            s.visuals.removeIf(QuakeGroundBlock::isRemoved);
            if (s.cast == null && now >= s.due && HeartModules.triggered(module, heart) && heart.modules().hurtTriggers.start(module)) {
                if (!begin(heart, module, s, now)) { s.due = now + 10; continue; }
            }
            if (s.cast == null) continue;
            if (now >= s.finish) { stop(module.id(), s); heart.modules().hurtTriggers.completed(module); s.cast = null; s.due = now + (long) module.value("interval"); continue; }
            var m = s.cast;
            if (now < s.start) {
                if (now % 4 == 0) for (var area : s.areas) warning(level, null, m, area.origin(), s.direction, area.warnings());
                continue;
            }
            if (!s.sounding) {
                stop(module.id(), s); s.sounding = true;
                ModuleAudio.play(level,module.id(),m.soundTimeline("quakeSound"),s.origin);
            }
            for (int wave = 0; wave < (int) m.value("quakeWaves"); wave++) {
                long age = now - s.start - wave * (long) m.value("quakeGap");
                if (m.value("quakeMode") == 2) {
                    if (age == 0) wave(heart, s, wave, 0, m.value("quakeRange"));
                    continue;
                }
                if (age < 0 || age > Math.ceil(m.value("quakeRange") / m.value("quakeSpeed"))) continue;
                wave(heart, s, wave, Math.max(0, (age - 1) * m.value("quakeSpeed")), Math.min(m.value("quakeRange"), age * m.value("quakeSpeed")));
            }
        }
        for (var id : List.copyOf(states.keySet())) if (!active.contains(id)) stop(id, states.remove(id));
    }
}
