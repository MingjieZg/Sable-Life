package dev.sablelife.module;

import dev.sablelife.heart.*;
import dev.sablelife.ownership.BodyOwnership;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.stream.Stream;

/** Shared, extensible target categories. Core adapters never enter the entity world or save/network. */
public final class ModuleTargets {
    public static boolean ownerSupported(String type){return List.of("linear_movement","auto_facing","navigation","sensor").contains(type);}
    public static final List<String> OWNER_CHOICES=List.of("players","mobs","all","lifeforms","owner");
    public static final List<String> CHOICES=List.of("players","mobs","all","lifeforms");
    public static boolean matches(LivingEntity entity,int mode) {
        refresh(entity);
        if(entity instanceof dev.sablelife.combat.EntityHitbox)return false;
        if(!entity.isAlive() || entity.isSpectator() || entity instanceof Player p && p.isCreative()) return false;
        return switch(mode) { case 0 -> entity instanceof Player; case 1 -> !(entity instanceof Player) && !(entity instanceof LifeTarget); case 2 -> true; case 3 -> entity instanceof LifeTarget; default -> false; };
    }
    public static boolean matches(LivingEntity entity,VirtualModule module) { if(module.value("target")==4)return ownerSupported(module.type())&&entity instanceof Player&&entity.isAlive()&&!entity.isSpectator();return matches(entity,(int)module.value("target")); }
    public static List<LivingEntity> queryForModule(ServerLevel level,HeartBlockEntity core,VirtualModule module,AABB box,Predicate<LivingEntity> filter){
        if(module.value("target")!=4||!ownerSupported(module.type()))return query(level,core.creatureId(),box,filter);
        return level.getPlayers(p->core.relations().isOwner(p)&&p.isAlive()&&!p.isSpectator()&&box.intersects(p.getBoundingBox())&&filter.test(p)).stream().map(p->(LivingEntity)p).toList();
    }
    public static LivingEntity resolveForModule(ServerLevel level,UUID id,HeartBlockEntity core,VirtualModule module){
        if(module.value("target")!=4||!ownerSupported(module.type()))return resolve(level,id,core.creatureId());
        return id!=null&&level.getEntity(id) instanceof Player p&&p.isAlive()&&!p.isSpectator()&&core.relations().isOwner(p)?p:null;
    }
    public static List<LivingEntity> query(ServerLevel level,UUID owner,AABB box) { return query(level,owner,box,e -> true); }
    public static List<LivingEntity> query(ServerLevel level,UUID owner,AABB box,Predicate<LivingEntity> filter) {
        return query(level,owner,box,filter,true);
    }
    public static List<LivingEntity> damageQuery(ServerLevel level,UUID owner,AABB box,Predicate<LivingEntity> filter){return query(level,owner,box,filter,false);}
    private static List<LivingEntity> query(ServerLevel level,UUID owner,AABB box,Predicate<LivingEntity> filter,boolean acquiring){
        var result=new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,box,e->!(e instanceof dev.sablelife.combat.EntityHitbox)&&!ownerPlayer(level,owner,e)&&(!acquiring||CoreRelations.targetAllowed(level,owner,e))&&filter.test(e)));
        for(var heart:dev.sablelife.ownership.LoadedCoreIndex.query(level,box)) {
            if(heart.creatureId().equals(owner) || heart.health().state()!=HeartState.ALIVE) continue;
            var target=new LifeTarget(level,heart,owner); if(box.intersects(target.getBoundingBox()) && (acquiring?CoreRelations.targetAllowed(level,owner,target):CoreRelations.damageAllowed(heart,owner)) && filter.test(target)) result.add(target);
        }
        return result;
    }
    public static LivingEntity resolve(ServerLevel level,UUID id,UUID owner) {
        if(id==null) return null;
        if(level.getEntity(id) instanceof dev.sablelife.combat.EntityHitbox)return null;
        if(level.getEntity(id) instanceof LivingEntity entity) return CoreRelations.targetAllowed(level,owner,entity)?entity:null;
        var heart=BodyOwnership.get(level).loadedHeart(level,id);
        if(heart==null || id.equals(owner) || heart.health().state()!=HeartState.ALIVE)return null;
        var target=new LifeTarget(level,heart,owner);return CoreRelations.targetAllowed(level,owner,target)?target:null;
    }
    private static boolean ownerPlayer(ServerLevel level,UUID id,LivingEntity e){var core=dev.sablelife.ownership.LoadedCoreIndex.find(level,id);return core!=null&&core.relations().isOwner(e);}
    private static boolean priorityMob(HeartBlockEntity owner, LivingEntity target) {
        return target != null && !(target instanceof Player) && !(target instanceof LifeTarget)
                && owner.relations().prioritizesMob(target.getType());
    }

    static boolean passesPriorityFilter(HeartBlockEntity owner, VirtualModule module, LivingEntity target) {
        return module.value("priorityOnly") != 1 || priorityMob(owner, target);
    }

    static boolean matchesTrackingTarget(HeartBlockEntity owner, VirtualModule module, LivingEntity target) {
        return target != null && !target.isRemoved() && passesPriorityFilter(owner, module, target) && matches(target, module);
    }

    public static int rank(HeartBlockEntity owner, LivingEntity target) {
        if (owner == null) return 0;
        if (priorityMob(owner, target)) return -1;
        return switch (owner.relations().priority()) {
            case 1 -> target instanceof Player ? 0 : 1;
            case 2 -> target instanceof LifeTarget ? 0 : 1;
            case 3 -> !(target instanceof Player) && !(target instanceof LifeTarget) ? 0 : 1;
            default -> 0;
        };
    }
    public static Comparator<LivingEntity> comparator(HeartBlockEntity owner,java.util.function.ToDoubleFunction<LivingEntity> distance){return Comparator.<LivingEntity>comparingInt(e->rank(owner,e)).thenComparingDouble(distance);}
    public static void refresh(LivingEntity target) { if(target instanceof LifeTarget life) life.refresh(); }
    public static boolean visible(ServerLevel level,UUID owner,Vec3 start,LivingEntity target) {
        refresh(target);
        return dev.sablelife.sense.SensorGeometry.visible(level,owner,start,target.getBoundingBox().getCenter(),target instanceof LifeTarget ? target.getUUID() : null);
    }
    /** Standing, crouching and swimming use their actual eye and collision-box heights. */
    public static Vec3 upperChest(LivingEntity target) {
        var box = target.getBoundingBox();
        double height = box.getYsize();
        double y = Math.clamp(target.getEyeY() - Math.min(.3, height / 6), box.getCenter().y, box.maxY);
        return new Vec3(box.getCenter().x, y, box.getCenter().z);
    }

    static boolean sensed(ServerLevel level, HeartBlockEntity heart, Vec3 center, org.joml.Quaterniond orientation,
                          LivingEntity target, Set<UUID> ownBodies) {
        var point = SableBodyProbe.toWorld(level, target instanceof Player ? upperChest(target) : target.getBoundingBox().getCenter());
        if (senseRay(level, heart, center, orientation, target, point, ownBodies)) return true;
        if (!(target instanceof Player)) return false;
        var side = new Vec3(point.z - center.z, 0, center.x - point.x).normalize();
        var shoulder = side.scale(target.getBbWidth() * .4).add(0, target.getBbHeight() * .06, 0);
        return senseRay(level, heart, center, orientation, target, point.add(shoulder), ownBodies)
                || senseRay(level, heart, center, orientation, target, point.add(-shoulder.x, shoulder.y, -shoulder.z), ownBodies);
    }

    private static boolean senseRay(ServerLevel level, HeartBlockEntity heart, Vec3 center, org.joml.Quaterniond orientation,
                                    LivingEntity target, Vec3 point, Set<UUID> ownBodies) {
        var start = dev.sablelife.sense.SensorGeometry.sightStart(center, point.subtract(center), orientation);
        return dev.sablelife.sense.SensorGeometry.visible(level, heart.creatureId(), start, point,
                target instanceof LifeTarget ? target.getUUID() : null, ownBodies);
    }

    /** A preferred target and, for memory-enabled moves, the action that owns the observation. */
    public record AttackAim(UUID id, long moveActivation, ModuleMoveSet.ActionIdentity moveAction) {
        public AttackAim(UUID id, long moveActivation) {
            this(id, moveActivation, null);
        }

        public static AttackAim fixed(UUID id) {
            return id == null ? null : new AttackAim(id, Long.MIN_VALUE);
        }

        public boolean followsMove() {
            return moveActivation != Long.MIN_VALUE;
        }

        public SightMemory.Target observation(HeartBlockEntity core, VirtualModule module) {
            if (!followsMove()) return null;
            var active = core.modules().moveAction(module.id());
            if (active == null || active.started() != moveActivation || moveAction != null && !moveAction.equals(active)) return null;
            var observation = core.modules().moveAim(module.id());
            return observation != null && id.equals(observation.id()) ? observation : null;
        }

        /** Older pending projectiles can adopt the full identity of their already matching action. */
        public AttackAim bindMoveAction(HeartBlockEntity core, VirtualModule module) {
            if (moveAction != null || observation(core, module) == null) return this;
            return new AttackAim(id, moveActivation, core.modules().moveAction(module.id()));
        }

        public boolean available(HeartBlockEntity core, VirtualModule module) {
            return !followsMove() || observation(core, module) != null;
        }
    }

    public static boolean attackVisible(ServerLevel level, HeartBlockEntity core, VirtualModule module, Vec3 origin, LivingEntity target) {
        return attackVisible(level, core, module, origin, target, attackAim(core, module));
    }

    public static boolean inheritedAimAvailable(HeartBlockEntity core, VirtualModule module, UUID target) {
        var aim = attackAim(core, module);
        return aim == null || !aim.id().equals(target) || aim.available(core, module);
    }

    public static UUID inheritedTarget(HeartBlockEntity core, VirtualModule module) {
        return module.emitter() == null && module.value("trigger") == 4 ? core.modules().moveTarget(module.id()) : null;
    }

    public static boolean attackVisible(ServerLevel level, HeartBlockEntity core, VirtualModule module, Vec3 origin,
                                        LivingEntity target, AttackAim aim) {
        if (aim != null && aim.id().equals(target.getUUID()) && !aim.available(core, module)) return false;
        return !SightMemory.enabled(module) || visible(level, core.creatureId(), origin, target);
    }

    public static Vec3 attackPoint(ServerLevel level, HeartBlockEntity core, VirtualModule module, LivingEntity target, AttackAim aim) {
        var remembered = rememberedPoint(core, module, target, aim);
        return remembered != null ? remembered : SableBodyProbe.toWorld(level, target.getBoundingBox().getCenter());
    }

    public static Vec3 attackFeet(ServerLevel level, HeartBlockEntity core, VirtualModule module, LivingEntity target, AttackAim aim) {
        var remembered = rememberedPoint(core, module, target, aim);
        // An unseen target's pose cannot refine the remembered position.
        return remembered != null ? remembered : SableBodyProbe.toWorld(level, target.position());
    }

    private static Vec3 rememberedPoint(HeartBlockEntity core, VirtualModule module, LivingEntity target, AttackAim aim) {
        return aim == null || !aim.id().equals(target.getUUID()) ? null : rememberedPoint(core, module, aim);
    }

    private static Vec3 rememberedPoint(HeartBlockEntity core, VirtualModule module, AttackAim aim) {
        if (SightMemory.enabled(module)) return null;
        var observation = aim.observation(core, module);
        return observation != null && !observation.visible() ? observation.point() : null;
    }

    /** A wand order supplies an aim preference for an attack that has already been triggered. */
    public static AttackAim attackAim(HeartBlockEntity core, VirtualModule module) {
        if (module.emitter() != null || !(core.getLevel() instanceof ServerLevel level)) return null;
        var commanded = core.modules().commandTarget().weaponTarget(level);
        if (commanded != null) return AttackAim.fixed(commanded);
        var inherited = inheritedTarget(core, module);
        if (inherited == null) return null;
        var action = core.modules().moveAction(module.id());
        return action == null ? AttackAim.fixed(inherited) : new AttackAim(inherited, action.started(), action);
    }

    public static UUID attackTarget(HeartBlockEntity core, VirtualModule module) {
        var aim = attackAim(core, module);
        return aim == null ? null : aim.id();
    }

    public static List<LivingEntity> aimedTargets(ServerLevel level, HeartBlockEntity core, AABB bounds,
            Predicate<LivingEntity> eligible, Comparator<LivingEntity> order, Predicate<LivingEntity> valid, int limit, UUID inherited) {
        return aimedResults(level, core, bounds, eligible, order, target -> valid.test(target) ? target : null,
                inherited, limit > 1).limit(limit).toList();
    }

    /** An inherited target must pass both acquisition and cast geometry before other targets are considered. */
    public static <T> Stream<T> aimedResults(ServerLevel level, HeartBlockEntity core, AABB bounds,
            Predicate<LivingEntity> eligible, Comparator<LivingEntity> order, Function<LivingEntity, T> result,
            UUID inherited, boolean multiple) {
        return aimedResults(level, core, bounds, eligible, order, result, inherited, multiple, null);
    }

    /** A remembered position supplies selection bounds for an attack with its own sight check off. */
    public static <T> Stream<T> attackResults(ServerLevel level, HeartBlockEntity core, VirtualModule module, AABB bounds,
            Predicate<LivingEntity> eligible, Comparator<LivingEntity> order, Function<LivingEntity, T> result,
            AttackAim aim, boolean multiple, boolean remember) {
        if (aim == null && remember)
            return core.modules().weaponSight().results(level, core, module, bounds, eligible, order, result, null, multiple);
        if (aim == null) return aimedResults(level, core, bounds, eligible, order, result, null, multiple);
        if (!aim.available(core, module)) return Stream.empty();
        return aimedResults(level, core, bounds, eligible, order, result, aim.id(), multiple, rememberedPoint(core, module, aim));
    }

    private static <T> Stream<T> aimedResults(ServerLevel level, HeartBlockEntity core, AABB bounds,
            Predicate<LivingEntity> eligible, Comparator<LivingEntity> order, Function<LivingEntity, T> result,
            UUID inherited, boolean multiple, Vec3 remembered) {
        var owner = core.creatureId();
        if (inherited == null) {
            return query(level, owner, bounds, eligible).stream().sorted(order).map(result).filter(Objects::nonNull);
        }
        var target = resolve(level, inherited, owner);
        if (target == null || target.isRemoved() || !eligible.test(target)) return Stream.empty();
        if (remembered == null ? !bounds.intersects(target.getBoundingBox()) : !bounds.contains(remembered)) return Stream.empty();
        T first = result.apply(target);
        if (first == null) return Stream.empty();
        if (!multiple) return Stream.of(first);
        var others = query(level, owner, bounds, entity -> !inherited.equals(entity.getUUID()) && eligible.test(entity))
                .stream().sorted(order).map(result).filter(Objects::nonNull);
        return Stream.concat(Stream.of(first), others);
    }
    /** Attack impulses only: movement, gravity, attraction and native contact physics stay independent. */
    public static void attackPush(LivingEntity victim,Vec3 impulse){
        double scale=victim instanceof LifeTarget life?1-life.heart.knockbackResistance()/100:1;
        if(scale>0)victim.push(impulse.x*scale,impulse.y*scale,impulse.z*scale);
    }
    /** A detached adapter lets geometry/cadence code treat a core consistently with ordinary targets. */
    public static final class LifeTarget extends ArmorStand {
        private final HeartBlockEntity heart;
        public HeartBlockEntity core(){return heart;}
        private final UUID attacker;
        private double knockbackScale=1;
        private LifeTarget(ServerLevel level,HeartBlockEntity heart,UUID attacker) {
            super(level,0,0,0); this.heart=heart; this.attacker=attacker;
            setUUID(heart.creatureId()); setInvisible(true); setNoGravity(true); refresh();
        }
        private void refresh() {
            var center=SableBodyProbe.toWorld(heart.getLevel(),heart.getBlockPos().getCenter());
            setPos(center.x,center.y-.5,center.z);
            var box=dev.sablelife.ownership.LoadedCoreIndex.bounds(heart);
            setPos(center.x,box.minY,center.z); setBoundingBox(box); setCustomName(heart.displayName());
        }
        @Override public Vec3 getDeltaMovement() {
            if(heart==null) return super.getDeltaMovement();
            var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(heart.getLevel(),heart.getBlockPos());
            return body instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel server ? new Vec3(server.latestLinearVelocity.x/20,server.latestLinearVelocity.y/20,server.latestLinearVelocity.z/20) : Vec3.ZERO;
        }
        @Override public void knockback(double strength,double x,double z){
            if(heart==null){super.knockback(strength,x,z);return;}
            double scale=1-heart.knockbackResistance()/100;if(scale<=0)return;
            knockbackScale=scale;
            try{super.knockback(strength,x,z);}finally{knockbackScale=1;}
        }
        @Override public void setDeltaMovement(Vec3 movement) {
            if(heart==null) { super.setDeltaMovement(movement); return; }
            var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(heart.getLevel(),heart.getBlockPos());
            var system=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get((ServerLevel)heart.getLevel());
            if(body instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel server && system!=null && !server.isRemoved()) {
                var delta=movement.subtract(getDeltaMovement()).scale(20*knockbackScale);
                system.getPipeline().addLinearAndAngularVelocity(server,new org.joml.Vector3d(delta.x,delta.y,delta.z),new org.joml.Vector3d());
            }
        }
        @Override public boolean isAlive() { return heart!=null && !heart.isRemoved() && heart.health().state()==HeartState.ALIVE; }
        @Override public float getHealth() { return heart==null ? super.getHealth() : heart.health().current(); }
        @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount) {
            boolean alive=isAlive();boolean hit=alive&&heart.acceptsLifeAttack(attacker)&&(dev.sablelife.combat.EntityHitboxes.mode(heart)?heart.modules().damageBoxes.lifeHit(heart,source,amount):heart.damageAt(heart.getBlockPos(),amount,null,dev.sablelife.combat.EntityHitboxes.hitType(source,level().getGameTime())));
            if(hit&&!isAlive()&&level() instanceof ServerLevel server)dev.sablelife.attack.CombatEventHooks.emit(server,source,0,2,heart.creatureId());return hit;
        }
    }
    private ModuleTargets() {}
}
