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
    public static int rank(HeartBlockEntity owner,LivingEntity target){if(owner==null)return 0;return switch(owner.relations().priority()){case 1->target instanceof Player?0:1;case 2->target instanceof LifeTarget?0:1;case 3->!(target instanceof Player)&&!(target instanceof LifeTarget)?0:1;default->0;};}
    public static Comparator<LivingEntity> comparator(HeartBlockEntity owner,java.util.function.ToDoubleFunction<LivingEntity> distance){return Comparator.<LivingEntity>comparingInt(e->rank(owner,e)).thenComparingDouble(distance);}
    public static void refresh(LivingEntity target) { if(target instanceof LifeTarget life) life.refresh(); }
    public static boolean visible(ServerLevel level,UUID owner,Vec3 start,LivingEntity target) {
        refresh(target);
        return dev.sablelife.sense.SensorGeometry.visible(level,owner,start,target.getBoundingBox().getCenter(),target instanceof LifeTarget ? target.getUUID() : null);
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
            boolean alive=isAlive();boolean hit=alive&&heart.acceptsLifeAttack(attacker)&&(dev.sablelife.combat.EntityHitboxes.mode(heart)?heart.modules().damageBoxes.lifeHit(heart,source,amount):heart.damageAt(heart.getBlockPos(),amount));
            if(hit&&!isAlive()&&level() instanceof ServerLevel server)dev.sablelife.attack.CombatEventHooks.emit(server,source,0,2,heart.creatureId());return hit;
        }
    }
    private ModuleTargets() {}
}
