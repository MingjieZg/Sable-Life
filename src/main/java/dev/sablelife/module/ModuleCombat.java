package dev.sablelife.module;

import dev.sablelife.attack.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.SurvivalTokens;
import dev.sablelife.SableLifeServerConfig;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Quaterniond;
import java.util.*;

/** Per-core server runtime. Motion history never survives an inactive interval or reload. */
public final class ModuleCombat {
    public static boolean supported(String type) { return List.of("melee", "flame", "arrow", "pulse", "gun", "mortar", "particles").contains(type); }
    private static final class State {
        long due, tick = Long.MIN_VALUE;
        final List<FlameFront> flames = new ArrayList<>();
        Vec3 center;
        Quaterniond rotation;
        Vec3 emittedCenter;
        Quaterniond emittedRotation;
        boolean pulseTarget;
        String particleSignature;
        final Map<UUID, Long> hits = new HashMap<>();
        final Map<UUID, Integer> meleeHitCounts = new HashMap<>();
        MeleeGeometry.Frame meleeFrame;
    }
    private final Map<UUID, State> states = new HashMap<>();
    public boolean pulseTarget(UUID id) { var state = states.get(id); return state != null && state.pulseTarget; }
    public void clear() { states.clear(); }
    public void tick(HeartBlockEntity heart) {
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        var world = level.getServer().getLevel(level.dimension()); if (world == null) return;
        var active = new HashSet<UUID>();
        for (var module : heart.modules().all()) {
            if(heart.isControlCore()&&!module.type().equals("particles"))continue;
            if (supported(module.type()) && heart.health().state() == dev.sablelife.heart.HeartState.ALIVE
                    && !module.detached() && HeartModules.status(module, heart).equals("disconnected")) {
                module.detach(); heart.setChanged();
            }
            if (!supported(module.type()) || !HeartModules.triggered(module, heart)) { states.remove(module.id()); continue; }
            active.add(module.id()); var state = states.computeIfAbsent(module.id(), ignored -> new State());
            switch (module.type()) {
                case "particles" -> particles(world, heart, module, state);
                case "melee" -> melee(world, heart, module, state);
                case "flame" -> flame(world, heart, module, state);
                case "mortar" -> { if (ready(world, heart, module, state)) shots(world, heart, module, List.of()); }
                case "gun", "arrow" -> { if(world.getGameTime()>=state.due){var directions=ProjectileAim.directions(heart,module,nozzle(heart,module));if(!directions.isEmpty()&&ready(world,heart,module,state)){if(module.type().equals("gun"))shots(world,heart,module,directions);else arrows(world,heart,module,directions);}} }
                case "pulse" -> {
                    var centers=TargetCastOrigin.targeted(module)?TargetCastOrigin.points(heart,module):List.of(origin(heart,module,false));
                    var groups=centers.stream().map(center->pulseTargets(world,heart,module,center)).toList();state.pulseTarget=groups.stream().anyMatch(group->group.stream().anyMatch(e->dev.sablelife.heart.CoreRelations.targetAllowed(world,heart.creatureId(),e)));
                    // A whole batch shares one cooldown; no target means no target-position cast.
                    if(!centers.isEmpty()&&(module.value("pulseStart")==0||state.pulseTarget)&&ready(world,heart,module,state)&&dev.sablelife.heart.SurvivalTokens.chargeCast(heart,module,centers.size()))
                        for(int i=0;i<centers.size();i++)pulse(world,heart,module,centers.get(i),groups.get(i));
                }
            }
        }
        states.keySet().retainAll(active);
    }
    /** A summon owns this engine; the referenced module's trigger, Marks and aim are not consulted. */
    public void tickEmitter(HeartBlockEntity heart,VirtualModule module){
        if(!(heart.getLevel() instanceof ServerLevel world)||module.emitter()==null)return;
        var state=states.computeIfAbsent(module.id(),ignored->new State());
        if(module.type().equals("pulse")){var center=module.emitter().position();var targets=pulseTargets(world,heart,module,center);state.pulseTarget=targets.stream().anyMatch(e->dev.sablelife.heart.CoreRelations.targetAllowed(world,heart.creatureId(),e));if((module.value("pulseStart")==0||state.pulseTarget)&&ready(world,heart,module,state)&&dev.sablelife.heart.SurvivalTokens.chargeCast(heart,module,1))pulse(world,heart,module,center,targets);}
        else if(module.type().equals("flame"))flame(world,heart,module,state);
        else if(ready(world,heart,module,state)){var directions=List.of(module.emitter().direction());if(module.type().equals("arrow"))arrows(world,heart,module,directions);else if(module.type().equals("gun"))shots(world,heart,module,directions);}
    }
    private boolean ready(ServerLevel world, HeartBlockEntity heart, VirtualModule module, State state) {
        if (world.getGameTime() < state.due) return false;
        if (module.emitter()==null && !module.type().equals("flame") && !heart.modules().hurtTriggers.start(module)) return false;
        state.due = world.getGameTime() + Math.max(1, (int) module.value("interval")); return true;
    }
    private boolean target(LivingEntity entity) { return entity.isAlive() && !entity.isSpectator() && !(entity instanceof Player p && p.isCreative()); }
    public static Vec3 halfSize(HeartBlockEntity heart, VirtualModule module) {
        if (module.importData().contains("MeleeRadius") && module.anchors().getFirst().equals(module.anchors().get(1))) {
            double half = module.importData().getInt("MeleeRadius") + .5;
            return new Vec3(half, half, half);
        }
        var body = module.anchors().getFirst().body(heart);
        var a = module.anchors().getFirst().position(body); var b = module.anchors().get(1).position(body);
        return new Vec3((Math.abs(a.getX() - b.getX()) + 1) / 2., (Math.abs(a.getY() - b.getY()) + 1) / 2., (Math.abs(a.getZ() - b.getZ()) + 1) / 2.);
    }
    public static Vec3 meleeCenter(HeartBlockEntity heart, VirtualModule module) {
        if(module.type().equals("melee"))return MeleeGeometry.frame(heart,module).center();
        var body = module.anchors().getFirst().body(heart);
        var a = module.anchors().getFirst().position(body).getCenter(); var b = module.anchors().get(1).position(body).getCenter();
        return offsetStart(heart, module, body.logicalPose().transformPosition(a.lerp(b, .5)));
    }
    private void particles(ServerLevel world, HeartBlockEntity heart, VirtualModule module, State state) {
        var frame = ParticlePattern.frame(heart, module);
        var center = frame.center(); var rotation = frame.rotation();
        String signature = module.anchors().toString() + ":" + module.value("particlePattern") + ":" + module.value("startDistance") + ":" + module.value("direction") + ":" + module.value("particleRadius") + ":" + module.value("particleLength")+":"+module.value("particleExpandLength")+":"+module.value("particleExpandWidth")+":"+module.value("particleExpandHeight");
        if (!signature.equals(state.particleSignature)) { state.center = null; state.particleSignature = signature; }
        long now = world.getGameTime();
        if (state.center == null || now != state.tick + 1 || state.center.distanceToSqr(center) > 64) {
            state.center = center; state.rotation = new Quaterniond(rotation);
            state.emittedCenter = center; state.emittedRotation = new Quaterniond(rotation);
        }
        double angular = 2 * Math.acos(Math.min(1, Math.abs(state.rotation.dot(rotation))));
        boolean moving = state.center.distanceToSqr(center) > .000001 || angular > .001;
        if (ready(world, heart, module, state) && (module.value("motionOnly") == 0 || moving) && !module.particles().isEmpty()) {
            // Bound total network emission, while interpolating the rotating volume for continuous trails.
            var origin = state.emittedCenter.distanceToSqr(center) > 64 ? center : state.emittedCenter;
            var orientation = origin == center ? rotation : state.emittedRotation;
            double sweepAngle = 2 * Math.acos(Math.min(1, Math.abs(orientation.dot(rotation))));
            int steps = module.value("trail") == 0 ? 1 : Math.clamp((int) Math.ceil((origin.distanceTo(center) + sweepAngle * ParticlePattern.sweepRadius(module, frame)) / .25), 1, 16);
            int count = Math.clamp((int) module.value("density"), 1, 32);
            int samples = Math.min(steps * count, 256 / module.particles().size());
            for (String id : module.particles()) {
                var particle = ParticlePattern.emissionStyle(module, module.particleEntry(id));
                for (int i = 0; i < samples; i++) {
                    double t = module.value("trail") == 0 ? 1 : (i + 1.) / samples;
                    var interpolated = new Quaterniond(orientation).slerp(rotation, t);
                    if(ParticlePattern.travels(module)){ParticlePattern.emit(world,module,module.particleEntry(id),origin.lerp(center,t),interpolated,frame.forward(),i,samples,world.random,null);continue;}
                    var offset = ParticlePattern.sample(module, frame.half(), frame.forward(), i, samples, world.random);
                    var position = origin.lerp(center, t).add(ContactBox.rotate(interpolated, offset));
                    var velocity = ParticlePattern.velocity(module, interpolated, frame.forward(), world.random);
                    ModuleParticles.send(world, particle, position.x, position.y, position.z, 0, velocity.x, velocity.y, velocity.z, 1);
                }
            }
            state.emittedCenter = center; state.emittedRotation = new Quaterniond(rotation);
        }
        state.center = center; state.rotation = rotation; state.tick = now;
    }
    private void melee(ServerLevel world, HeartBlockEntity heart, VirtualModule module, State state) {
        var frame=MeleeGeometry.frame(heart,module);long now=world.getGameTime();
        var previous=state.meleeFrame;
        // A far limb tip can move >8 blocks during an ordinary rotation. Check the body origin instead.
        if(previous==null||now!=state.tick+1||!previous.signature().equals(frame.signature())||previous.position().distanceToSqr(frame.position())>64)previous=frame;
        long cost=heart.isSurvivalCore()?SurvivalTokens.meleeCost(module,SableLifeServerConfig.meleeTokenRate()):0;
        int steps=MeleeGeometry.samples(previous,frame),limit=(int)module.value("meleeHitLimit");
        double sag=MeleeGeometry.arcRadius(previous,frame)*(1-Math.cos(MeleeGeometry.angular(previous,frame)/2));
        var candidates=new AABB(previous.center(),frame.center()).inflate(frame.half().length()+sag+1);
        state.hits.entrySet().removeIf(e->e.getValue()<=now);
        for(var victim:ModuleTargets.damageQuery(world,heart.creatureId(),candidates,this::target)){
            UUID id=victim.getUUID();
            if(state.hits.containsKey(id)||limit>0&&state.meleeHitCounts.getOrDefault(id,0)>=limit)continue;
            for(int i=0;i<=steps;i++){
                var sample=MeleeGeometry.sample(previous,frame,i/(double)steps);
                if(!ContactBox.intersects(sample.center(),frame.half(),sample.rotation(),victim.getBoundingBox()))continue;
                var source=new dev.sablelife.attack.BodyMeleeSource(world,previous.position().lerp(frame.position(),i/(double)steps),heart.creatureName());
                if(!heart.spendAttackTokens(cost))break;
                boolean hit=false;
                try{
                    hit=ModuleDamage.hurt(victim,source,(float)module.value("damage"),heart,module);
                }finally{
                    if(!hit)heart.refundAttackTokens(cost);
                }
                if(hit||source.wasBlocked())state.hits.put(id,now+Math.max(1,(int)module.value("interval")));
                if(hit){
                    if(limit>0)state.meleeHitCounts.merge(id,1,Integer::sum);
                    push(victim,sample.center(),module.value("knockback"));effect(victim,module,heart);
                    if (module.value("burn") > 0) victim.igniteForSeconds((int) module.value("burn"));
                    ModuleParticles.send(world,ParticleTypes.CRIT,victim.getX(),victim.getY()+victim.getBbHeight()/2,victim.getZ(),5,.15,.15,.15,.1);
                }
                break;
            }
        }
        state.meleeFrame=frame;state.tick=now;
    }
    private void push(LivingEntity victim, Vec3 origin, double strength) {
        if (strength > 0) { var delta = victim.position().subtract(origin); victim.knockback(strength, -delta.x, -delta.z); }
    }
    private void effect(LivingEntity victim,VirtualModule module,HeartBlockEntity heart){ModuleEffects.apply(victim,ModuleEffects.forCore(heart,module),null);}
    /** Selected face before firing-angle adjustments; used only to position the muzzle. */
    public static Vec3 baseDirection(HeartBlockEntity heart, VirtualModule module) {
        if(module.emitter()!=null)return module.emitter().direction();
        var anchor=module.anchors().getFirst();var body=anchor.body(heart);
        return ContactBox.rotate(new Quaterniond(body.logicalPose().orientation()), localDirection(module)).normalize();
    }
    private static Vec3 localDirection(VirtualModule module){
        var anchor=module.anchors().getFirst();int configured=(int)module.value("direction");
        var face=configured==0?anchor.face():Direction.byName(ModuleFields.find(module.type(),"direction").choices().get(configured));
        return Vec3.atLowerCornerOf(face.getNormal());
    }
    public static Vec3 direction(HeartBlockEntity heart, VirtualModule module) {
        if(module.emitter()!=null)return module.emitter().direction();
        var local=localDirection(module);
        if(module.type().equals("flame") || module.type().equals("pull") || List.of("arrow","gun").contains(module.type())&&module.value("shotAim")==0
                || module.type().equals("laser")&&module.value("laserAim")==0)
            local=ContactBox.rotate(MeleeGeometry.angles(local,module.value("shotHorizontal"),module.value("shotVertical")),local);
        return ContactBox.rotate(new Quaterniond(module.anchors().getFirst().body(heart).logicalPose().orientation()),local).normalize();
    }
    /** Position first along the original face; firing angles must never orbit the muzzle. */
    public static Vec3 offsetStart(HeartBlockEntity heart, VirtualModule module, Vec3 origin) {
        if(module.emitter()!=null)return origin;
        double distance=module.value("startDistance");
        return distance==0?origin:origin.add(baseDirection(heart,module).scale(distance));
    }
    public static Vec3 baseOrigin(HeartBlockEntity heart,VirtualModule module){return module.emitter()!=null?module.emitter().position():module.anchors().getFirst().world(heart,false);}
    public static Vec3 origin(HeartBlockEntity heart, VirtualModule module, boolean surface) {
        return module.emitter()!=null?module.emitter().position():offsetStart(heart, module, module.anchors().getFirst().world(heart, surface));
    }
    public static Vec3 nozzle(HeartBlockEntity heart, VirtualModule module) { return module.emitter()!=null?module.emitter().position():origin(heart, module, false).add(baseDirection(heart, module).scale(.55)); }
    private static Vec3 clip(ServerLevel world, Vec3 start, Vec3 end) {
        var hit = world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
        return hit.getType() == HitResult.Type.MISS ? end : SableBodyProbe.toWorld(world, hit.getLocation());
    }
    private static final class FlameFront {
        Vec3 point;
        final Vec3 direction;
        final FlameFanGeometry fan;
        boolean terrainImpact;
        double distance;
        FlameFront(Vec3 point, Vec3 direction, FlameFanGeometry fan) {
            this.point = point;
            this.direction = direction;
            this.fan = fan;
        }
    }
    private void flame(ServerLevel world, HeartBlockEntity heart, VirtualModule module, State state) {
        var start = nozzle(heart, module); var direction = direction(heart, module);
        double range = module.value("range"), width = module.value("width"), speed = module.value("flameSpeed");
        int emissionPeriod = Math.max(2, (int) Math.ceil(.4 / speed));
        boolean emitted=(ModuleHurt.once(module) || world.getGameTime() % emissionPeriod == 0) && (module.emitter()!=null||heart.modules().hurtTriggers.start(module));
        double fanAngle = FlameFanGeometry.angle(module);
        boolean funded=emitted&&dev.sablelife.heart.SurvivalTokens.chargeCast(heart,module,1);
        if (funded) state.flames.add(new FlameFront(start, direction,
                fanAngle > 0 ? FlameFanGeometry.create(start, direction, range, width, fanAngle) : null));
        if ((!heart.isSurvivalCore()||funded)&&ready(world, heart, module, state)) fireSound(world, module, start);
        state.hits.entrySet().removeIf(e -> e.getValue() <= world.getGameTime());
        var iterator = state.flames.iterator();
        while (iterator.hasNext()) {
            var flame = iterator.next();
            if (flame.fan != null) {
                if (fanStep(world, heart, module, state, flame, speed)) iterator.remove();
                continue;
            }
            var previous = flame.point;
            var intended = previous.add(flame.direction.scale(Math.min(speed, range - flame.distance)));
            var terrainHit=world.clip(new ClipContext(previous,intended,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null));
            var end=terrainHit.getType()==HitResult.Type.MISS?intended:SableBodyProbe.toWorld(world,terrainHit.getLocation());
            if(terrainHit.getType()==HitResult.Type.BLOCK)heart.modules().surfaceFields.impact(heart,module,end,Vec3.atLowerCornerOf(terrainHit.getDirection().getNormal()),flame.direction);
            double radius = .25 + (width - .25) * Math.min(1, (flame.distance + speed) / range);
            for (var victim : ModuleTargets.damageQuery(world, heart.creatureId(), new AABB(previous, end).inflate(radius + 1), this::target)) {
                if (victim instanceof ModuleTargets.LifeTarget || victim.fireImmune() || state.hits.containsKey(victim.getUUID())) continue;
                var point = SableBodyProbe.toWorld(world, victim.getBoundingBox().getCenter());
                var delta = point.subtract(previous); double along = delta.dot(flame.direction);
                double half = victim.getBbWidth() / 2;
                if (along < -half || along > previous.distanceTo(end) + half
                        || delta.subtract(flame.direction.scale(Math.clamp(along, 0, previous.distanceTo(end)))).lengthSqr() > Math.pow(radius + half, 2)
                        || clip(world, previous, point).distanceToSqr(point) > .0001) continue;
                hurtFlameTarget(world, heart, module, state, victim);
            }
            for (var hit : dev.sablelife.combat.BodySphereQuery.querySweep(world, heart.creatureId(), previous, end, radius))
                hurtFlameBody(world, heart, module, state, hit);
            flame.distance += previous.distanceTo(end); flame.point = end;
            if (end.distanceToSqr(intended) > .0001 || flame.distance >= range - .001) iterator.remove();
        }
        if(!ModuleHurt.once(module)||emitted){
            var packet=new ModuleNetworking.FlameJet(start,direction,speed,range,width,0,1F,module.id().getLeastSignificantBits()^world.getGameTime()*0x9E3779B97F4A7C15L,module.particleEntries(),fanAngle);
            for(var player:world.players())if(player.position().distanceToSqr(start)<=128*128&&VisualBudget.allow(player,1))
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet);
        }
        if(state.flames.isEmpty()&&module.emitter()==null) heart.modules().hurtTriggers.completed(module);
    }
    private boolean fanStep(ServerLevel world, HeartBlockEntity heart, VirtualModule module, State state,
                            FlameFront front, double speed) {
        var fan = front.fan;
        double end = Math.min(fan.range(), front.distance + speed);
        var volumes = fan.sweep(front.distance, end);
        AABB bounds = volumes.getFirst().bounds();
        for (var volume : volumes) bounds = bounds.minmax(volume.bounds());
        for (var victim : ModuleTargets.damageQuery(world, heart.creatureId(), bounds.inflate(1), this::target)) {
            if (victim instanceof ModuleTargets.LifeTarget || victim.fireImmune() || state.hits.containsKey(victim.getUUID())) continue;
            var box = victim.getBoundingBox();
            var point = SableBodyProbe.toWorld(world, box.getCenter());
            var hitbox = new AttackVolume(point, new Vec3(box.getXsize() / 2, 0, 0),
                    new Vec3(0, box.getYsize() / 2, 0), new Vec3(0, 0, box.getZsize() / 2));
            if (volumes.stream().noneMatch(volume -> volume.intersects(hitbox)) || clip(world, fan.origin(), point).distanceToSqr(point) > .0001) continue;
            hurtFlameTarget(world, heart, module, state, victim);
        }
        for (var hit : dev.sablelife.combat.BodySphereQuery.queryVolumes(world, heart.creatureId(), fan.origin(), volumes))
            hurtFlameBody(world, heart, module, state, hit);
        if (!front.terrainImpact) {
            var terrain = world.clip(new ClipContext(fan.point(front.distance, 0, 0), fan.point(end, 0, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
            if (terrain.getType() == HitResult.Type.BLOCK) {
                front.terrainImpact = true;
                heart.modules().surfaceFields.impact(heart, module, SableBodyProbe.toWorld(world, terrain.getLocation()),
                        Vec3.atLowerCornerOf(terrain.getDirection().getNormal()), fan.forward());
            }
        }
        front.distance = end;
        return end >= fan.range() - .001;
    }

    private void hurtFlameTarget(ServerLevel world, HeartBlockEntity heart, VirtualModule module, State state, LivingEntity victim) {
        var damage = module.value("flameDirectHit") == 1 ? ModuleDamage.physical(world) : world.damageSources().inFire();
        if (ModuleDamage.hurt(victim, ModuleDamage.named(damage, heart), (float) module.value("damage"), heart, module)) {
            state.hits.put(victim.getUUID(), world.getGameTime() + Math.max(1, (int) module.value("interval")));
            if (module.value("burn") > 0) victim.igniteForSeconds((int) module.value("burn"));
            effect(victim,module,heart);
        }
    }

    private void hurtFlameBody(ServerLevel world, HeartBlockEntity heart, VirtualModule module, State state,
                               dev.sablelife.combat.BodySphereQuery.Hit hit) {
        var id = hit.core().creatureId();
        if (state.hits.containsKey(id)) return;
        float before = hit.core().health().current();
        if (hit.core().damageAt(hit.block(), (float) module.value("damage"))) {
            CombatEventHooks.killed(heart, module.id(), hit.core());
            heart.modules().hitCounter.record(heart, module.id(), Math.max(0, before - hit.core().health().current()));
            state.hits.put(id, world.getGameTime() + Math.max(1, (int) module.value("interval")));
        }
    }

    private void arrows(ServerLevel world, HeartBlockEntity heart, VirtualModule module, List<Vec3> directions) {
        var origin = nozzle(heart, module);
        int count=(int)module.value("arrows");
        int rate=heart.isSurvivalCore()?SableLifeServerConfig.arrowTokenRate():0;
        long cost=SurvivalTokens.arrowCost(module,count,rate);
        boolean sounded=false;
        for(var direction:directions){
            if(!heart.spendAttackTokens(cost))break;
            int spawned=0;
            try{
                if(!sounded){
                    fireSound(world,module,origin);
                    sounded=true;
                }
                var side = direction.cross(Math.abs(direction.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize(); var up = side.cross(direction).normalize();
                double phase = world.random.nextDouble() * Math.PI * 2;
                for (int i = 0; i < count; i++) {
                    double distance = i == 0 ? 0 : Math.sqrt((double) i / (count - 1)) * module.value("endRadius"); double angle = phase + 2 * Math.PI * i / Math.max(1, count - 1);
                    var shot = direction.scale(8).add(side.scale(distance * Math.cos(angle))).add(up.scale(distance * Math.sin(angle))).normalize();
                    var arrow = new ModuleArrow(world, origin.x, origin.y, origin.z, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
                    arrow.configure(module);
                    arrow.getPersistentData().putUUID("SableLifeAttackOwner", heart.creatureId());
                    ModuleDamage.nameProjectile(arrow,heart);dev.sablelife.attack.AttackHitCredit.attach(arrow,heart,module);
                    var effects=ModuleEffects.forCore(heart,module);
                    ModuleBlast.configure(arrow,module,effects);
                    for (var entry : effects) { var instance = entry.instance(); if (instance != null) arrow.addEffect(instance); }
                    arrow.setBaseDamage(module.value("damage")); arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                    arrow.shoot(shot.x, shot.y, shot.z, (float) module.value("speed"), 0);
                    if(world.addFreshEntity(arrow))spawned++;
                }
            }finally{
                heart.refundAttackTokens(cost-SurvivalTokens.arrowCost(module,spawned,rate));
            }
        }
    }
    private void fireSound(ServerLevel world, VirtualModule module, Vec3 origin) {
        ModuleAudio.play(world,module,"fireSound",origin);
    }
    private void shots(ServerLevel world, HeartBlockEntity heart, VirtualModule module, List<Vec3> directions) {
        var origin = nozzle(heart, module);
        if (module.type().equals("mortar")) {
            var velocities = ShotTrajectory.firingVelocities(world, heart, module);
            if (velocities.isEmpty()||!dev.sablelife.heart.SurvivalTokens.chargeCast(heart,module,velocities.size())) return;
            fireSound(world, module, origin);
            for (var velocity : velocities) {
                world.addFreshEntity(new ModuleShot(world, origin, velocity, module, heart));
                ShotTrajectory.ring(world, null, ShotTrajectory.trace(world, origin, velocity).landing(), module.value("blastRadius"), dev.sablelife.module.ModuleColors.packed(module,"mortarWarningColor"));
            }
            return;
        }
        if(directions.isEmpty()||!dev.sablelife.heart.SurvivalTokens.chargeCast(heart,module,directions.size()))return;
        fireSound(world, module, origin);
        for(var direction:directions) {
        var side = direction.cross(Math.abs(direction.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        var up = side.cross(direction).normalize(); int count = (int) module.value("arrows");
        double phase = world.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double radius = i == 0 ? 0 : Math.sqrt((double) i / (count - 1)) * module.value("endRadius"), angle = phase + Math.PI * 2 * i / Math.max(1, count - 1);
            var velocity = direction.scale(8).add(side.scale(radius * Math.cos(angle))).add(up.scale(radius * Math.sin(angle))).normalize().scale(module.value("speed"));
            world.addFreshEntity(new ModuleShot(world, origin, velocity, module, heart));
        }
        }
    }
    private List<LivingEntity> pulseTargets(ServerLevel world, HeartBlockEntity heart, VirtualModule module,Vec3 center) {
        double radius = module.value("radius");
        return ModuleTargets.damageQuery(world, heart.creatureId(), new AABB(center, center).inflate(radius + 2), entity -> {
            if (!target(entity)) return false;
            if (!ModuleTargets.matches(entity,module)) return false;
            var point = SableBodyProbe.toWorld(world, entity.getBoundingBox().getCenter()); var delta = point.subtract(center);
            return delta.lengthSqr() <= radius * radius && (TargetCastOrigin.targeted(module)||delta.lengthSqr() >= .0001)
                    && ModuleTargets.visible(world,heart.creatureId(),center.add(delta.normalize().scale(.55)),entity);
        });
    }
    private void pulse(ServerLevel world, HeartBlockEntity heart, VirtualModule module, Vec3 center,List<LivingEntity> targets) {
        double radius = module.value("radius");
        ModuleAudio.play(world,module,"sound",center);
        PulseVisual.send(world,module,center);
        for (var victim : targets) {
            if (ModuleDamage.hurt(victim, ModuleDamage.named(ModuleDamage.physical(world),heart), (float) module.value("damage"),heart,module)) { push(victim, center, module.value("knockback")); effect(victim,module,heart); }
        }
    }
}
