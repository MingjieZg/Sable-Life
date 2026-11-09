package dev.sablelife.module;

import dev.sablelife.attack.ModuleDamage;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;
import java.util.*;

/** Telegraphs are frozen world positions; target movement can evade an already advertised strike. */
public final class ModuleGroundStrike {
 private UUID emitterAudio;private UUID audio(UUID id){return emitterAudio==null?id:emitterAudio;}
    private record Strike(Vec3 point, long warning, long falling, long impact, double height, double radius, float damage, DustParticleOptions dust,
                          net.minecraft.world.item.ItemStack item, float size, float itemSize, int itemMode, org.joml.Vector3f itemRotation, List<ModuleParticles.Entry> particles, dev.sablelife.sound.SoundTimeline fallSound, dev.sablelife.sound.SoundTimeline impactSound,
                          dev.sablelife.attack.ModuleShot[] visual, ImpactVisual impactVisual, VirtualModule impactConfig) {}
    private static final class State { long due; final List<Strike> pending = new ArrayList<>(); }
    private final Map<UUID, State> states = new HashMap<>();
    private ServerLevel currentLevel;
    private void cancel(UUID id, State state) {
        for (var strike : state.pending) if (strike.visual[0] != null) strike.visual[0].discard();
        if (currentLevel != null) for (var player : currentLevel.players()) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ModuleNetworking.ModuleSound(audio(id), "", true, Vec3.ZERO, 0, 1));
    }
    public void clear() { states.forEach(this::cancel); states.clear(); currentLevel = null; }
    public static DustParticleOptions color(VirtualModule module) {
        return new DustParticleOptions(ModuleColors.vector(module,"strikeColor"),1.4F);
    }
    public static void ring(ServerLevel level, Vec3 point, double radius, DustParticleOptions dust) {
        for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2 * i / 40; var p = point.add(Math.cos(a) * radius, .12, Math.sin(a) * radius);
            dev.sablelife.module.ModuleParticles.send(level, dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
    private static boolean eligible(LivingEntity entity, VirtualModule module) {
        return ModuleTargets.matches(entity,module);
    }
    static Vec3 floor(ServerLevel level, HeartBlockEntity heart, Vec3 point) {
        return floor(level, heart, point, null);
    }
    /** A life target's own voxels are not the terrain supporting its feet. */
    static Vec3 floor(ServerLevel level, HeartBlockEntity heart, Vec3 point, UUID target) {
        var start = point.add(0, 4, 0); var end = point.add(0, -64, 0);
        for (int i = 0; i < 64; i++) {
            if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(start))) return null;
            var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
            if (hit.getType() != HitResult.Type.BLOCK) return null;
            var position = SableBodyProbe.toWorld(level, hit.getLocation());
            var owner=dev.sablelife.ownership.BodyOwnership.get(level).owner(hit.getBlockPos());
            if (!heart.creatureId().equals(owner) && (target==null || !target.equals(owner))) return position.add(0, .06, 0);
            var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(level,hit.getBlockPos());
            var rotation=body==null?new org.joml.Quaterniond():new org.joml.Quaterniond(body.logicalPose().orientation());
            var next=dev.sablelife.sense.SensorGeometry.leaveVoxel(position,SableBodyProbe.toWorld(level,hit.getBlockPos().getCenter()),new Vec3(0,-1,0),rotation);
            if(next.y>=start.y-.001 || next.y<=end.y)return null;
            start=next;
        }
        return null;
    }
    public static List<Vec3> pattern(ServerLevel level, HeartBlockEntity heart, VirtualModule module) {
        var origin = ModuleCombat.baseOrigin(heart,module); var result = new ArrayList<Vec3>();
        if (module.value("strikeMode") == 3) {
            double start = module.value("strikeStart"), range = module.value("strikeRange");
            if (start > range) return result;
            // Uniform area sampling in an annulus, rather than clustering at the center.
            // Limited retries allow sparse terrain without unbounded server work.
            var positions = new HashSet<net.minecraft.core.BlockPos>();
            int count = (int) module.value("strikeRandomCount");
            for (int attempt = 0; attempt < count * 8 && result.size() < count; attempt++) {
                var candidate = randomPoint(origin, start, range, level.random.nextDouble(), level.random.nextDouble());
                var point = floor(level, heart, candidate);
                if (point != null && positions.add(net.minecraft.core.BlockPos.containing(point))) result.add(point);
            }
        } else if (module.value("strikeMode") == 2) {
            ModuleTargets.query(level, heart.creatureId(), new AABB(origin, origin).inflate(module.value("strikeRange")), e -> eligible(e, module))
                    .stream().filter(e -> { double distance = ModuleArena.horizontalDistance(SableBodyProbe.toWorld(level, e.position()), origin); return distance >= module.value("strikeStart") && distance <= module.value("strikeRange"); })
                    .sorted(ModuleTargets.comparator(heart,e -> SableBodyProbe.toWorld(level, e.position()).distanceToSqr(origin))).limit(module.value("targetMultiple") == 1 ? 64 : 1)
                    .forEach(e -> { var p = floor(level, heart, SableBodyProbe.toWorld(level, e.position())); if (p != null) result.add(p); });
        } else {
            var direction = ModuleCombat.direction(heart, module); direction = new Vec3(direction.x, 0, direction.z).normalize();
            if (direction.lengthSqr() < .001) direction = new Vec3(0, 0, -1);
            double range = module.value("strikeRange"), start = module.value("strikeStart");
            if (start > range) return result;
            int rays = module.value("strikeMode") == 1 ? (int) module.value("strikeRays") : 1;
            if (start == 0) { var center = floor(level, heart, origin); if (center != null) result.add(center); }
            int layers = layers(module); double step = step(module);
            for (int layer = 0; layer < layers; layer++) for (int ray = 0; ray < rays; ray++) {
                double distance = Math.min(range, firstDistance(module) + layer * step);
                double angle = Math.PI * 2 * ray / rays, x = direction.x * Math.cos(angle) - direction.z * Math.sin(angle), z = direction.x * Math.sin(angle) + direction.z * Math.cos(angle);
                var p = floor(level, heart, origin.add(x * distance, 0, z * distance)); if (p != null) result.add(p);
            }
        }
        return result;
    }
    static Vec3 randomPoint(Vec3 origin, double inner, double outer, double radial, double angular) {
        var offset = GroundStrikeScatter.sample(inner, outer, radial, angular);
        return origin.add(offset.x(), 0, offset.z());
    }
    /** Geometric maximum before terrain and loaded-position checks. */
    public static int plannedPointCount(VirtualModule module){
        if(module.value("strikeStart")>module.value("strikeRange"))return 0;
        if(module.value("strikeMode")==3)return (int)module.value("strikeRandomCount");
        if(module.value("strikeMode")==2)return module.value("targetMultiple")==1?64:1;
        int rays=module.value("strikeMode")==1?(int)module.value("strikeRays"):1;
        return layers(module)*rays+(module.value("strikeStart")==0?1:0);
    }

    private static double firstDistance(VirtualModule module) { return module.value("strikeStart") > 0 ? module.value("strikeStart") : Math.min(module.value("strikeStep"), module.value("strikeRange")); }
    private static int layers(VirtualModule module) {
        int rays = module.value("strikeMode") == 1 ? (int) module.value("strikeRays") : 1;
        return Math.max(1, Math.min((int) Math.ceil((module.value("strikeRange") - firstDistance(module)) / module.value("strikeStep")) + 1, 127 / rays));
    }
    private static double step(VirtualModule module) { return layers(module) == 1 ? module.value("strikeStep") : Math.max(module.value("strikeStep"), (module.value("strikeRange") - firstDistance(module)) / (layers(module) - 1)); }
    private static void sound(ServerLevel level, UUID module, String sound, Vec3 point) {
        if (sound.isEmpty()) return;
        for (var player : level.players()) if (player.position().distanceToSqr(point) <= 64 * 64)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ModuleNetworking.ModuleSound(module, sound, false, point, 1, 1));
    }
    public void tick(HeartBlockEntity heart){tick(heart,heart.modules().all());}
 public void tickEmitter(HeartBlockEntity heart,VirtualModule module){emitterAudio=module.audioId();tick(heart,List.of(module));}
 private void tick(HeartBlockEntity heart,List<VirtualModule> modules){
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        currentLevel = level;
        var active = new HashSet<UUID>(); long now = level.getGameTime();
        for (var module : modules) {
            if (!module.type().equals("ground_strike") || !EmitterRuntime.active(module, heart)) continue;
            active.add(module.id()); var state = states.computeIfAbsent(module.id(), ignored -> new State());
            if (now >= state.due && state.pending.isEmpty() && EmitterRuntime.start(module,heart)) {
                var points = pattern(level, heart, module);
                if(!points.isEmpty()&&!dev.sablelife.heart.SurvivalTokens.chargeCast(heart,module,points.size())){
                    state.due=now+(long)module.value("interval");
                    continue;
                }
                var origin = ModuleCombat.baseOrigin(heart,module);
                int randomIndex = 0;
                for (var p : points) {
                    double distance = ModuleArena.horizontalDistance(p, origin);
                    long layer = distance < .001 ? 0 : Math.max(0, Math.round((distance - firstDistance(module)) / step(module))) + (module.value("strikeStart") == 0 ? 1 : 0);
                    long delay = module.value("strikeMode") == 3 ? randomIndex++ * (long) module.value("waveDelay")
                            : module.value("strikeMode") == 2 ? 0 : layer * (long) module.value("waveDelay");
                    long warning = now + delay, falling = warning + (long) module.value("warningTime");
                    long flight = Math.max(1, (long) Math.ceil(module.value("strikeHeight") / module.value("strikeSpeed")));
                    state.pending.add(new Strike(p, warning, falling, falling + flight, module.value("strikeHeight"), module.value("strikeRadius"), (float) module.value("damage"), color(module),
                            module.projectileItem(level.registryAccess()), (float) module.value("projectileHitbox"), (float)module.value("strikeItemSize"), (int)module.value("strikeItemMode"), new org.joml.Vector3f((float)module.value("strikeItemX"),(float)module.value("strikeItemY"),(float)module.value("strikeItemZ")), module.particleEntries(), module.soundTimeline("fallSound"), module.soundTimeline("impactSound"), new dev.sablelife.attack.ModuleShot[1],ImpactVisual.of(module,module.value("strikeRadius")),module.impactFieldId()==null?null:EmitterRuntime.copy(module)));
                }
                state.due = now + (long) module.value("interval");
            }
            var iterator = state.pending.iterator();
            while (iterator.hasNext()) {
                var strike = iterator.next(); if (now < strike.warning) continue;
                if (now >= strike.impact) { if (strike.visual[0] != null) strike.visual[0].discard(); impact(level, heart, module, strike); iterator.remove(); continue; }
                if (now % 5 == 0) ring(level, strike.point, strike.radius, strike.dust);
                if (now >= strike.falling) {
                    if (strike.visual[0] == null) {
                        // Capture each cast's appearance instead of reading later edits.
                        var appearance = new VirtualModule("ground_strike");
                        appearance.projectileItem(strike.item, level.registryAccess()); appearance.edit("projectileHitbox", strike.size, "");
                        appearance.edit("strikeItemSize",strike.itemSize,"");appearance.edit("strikeItemMode",strike.itemMode,"");appearance.edit("strikeItemX",strike.itemRotation.x,"");appearance.edit("strikeItemY",strike.itemRotation.y,"");appearance.edit("strikeItemZ",strike.itemRotation.z,"");
                        strike.visual[0] = dev.sablelife.attack.ModuleShot.fallingVisual(level, strike.point.add(0, strike.height, 0), strike.height / (strike.impact - strike.falling), (int) (strike.impact - now), appearance);
                        level.addFreshEntity(strike.visual[0]); ModuleAudio.play(level, module.audioId(), strike.fallSound, strike.point);
                    }
                    double t = (double) (now - strike.falling) / (strike.impact - strike.falling);
                    double y = strike.point.y + strike.height * (1 - t), length = Math.min(strike.height, 1 + strike.height / (strike.impact - strike.falling));
                    for (int i = 0; i < 8; i++) {
                        var trail = strike.dust;
                        if (strike.particles.isEmpty()) dev.sablelife.module.ModuleParticles.send(level, trail, strike.point.x, y + length * i / 8., strike.point.z, 1, .08, 0, .08, 0);
                        else {
                            var particle = strike.particles.get((i + (int) (now % strike.particles.size())) % strike.particles.size());
                            dev.sablelife.module.ModuleParticles.send(level, particle, strike.point.x, y + length * i / 8., strike.point.z, 1, .08, 0, .08, 0);
                        }
                    }
                }
            }
            if (state.pending.isEmpty()) EmitterRuntime.completed(module,heart);
        }
        for (UUID id : List.copyOf(states.keySet())) if (!active.contains(id)) cancel(id, states.remove(id));
    }
    private static void impact(ServerLevel level, HeartBlockEntity heart, VirtualModule module, Strike strike) {
        if(strike.impactConfig!=null)heart.modules().surfaceFields.impact(heart,strike.impactConfig,strike.point,new Vec3(0,1,0),new Vec3(0,-1,0));
        ring(level, strike.point, strike.radius, strike.dust);
        if(strike.impactVisual.custom())strike.impactVisual.emit(level,strike.point);
        else dev.sablelife.module.ModuleParticles.send(level, ParticleTypes.EXPLOSION, strike.point.x, strike.point.y + .2, strike.point.z, Math.clamp((int) (strike.radius * 3), 2, 32), strike.radius * .3, .2, strike.radius * .3, 0);
        ModuleAudio.play(level,module.audioId(),strike.impactSound,strike.point);
        for (var victim : ModuleTargets.damageQuery(level, heart.creatureId(), new AABB(strike.point, strike.point).inflate(strike.radius), e -> eligible(e, module))) {
            var box = victim.getBoundingBox();
            var nearest = new Vec3(Math.clamp(strike.point.x, box.minX, box.maxX), Math.clamp(strike.point.y, box.minY, box.maxY), Math.clamp(strike.point.z, box.minZ, box.maxZ));
            if (nearest.distanceToSqr(strike.point) > strike.radius * strike.radius) continue;
            if (!ModuleTargets.visible(level,heart.creatureId(),strike.point,victim)) continue;
            if (strike.damage > 0) ModuleDamage.hurt(victim, ModuleDamage.named(ModuleDamage.physical(level),heart), strike.damage,heart,module);
        }
    }
}
