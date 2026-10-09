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
    public static final List<String> CHOICES=List.of("players","mobs","all","lifeforms");
    public static boolean matches(LivingEntity entity,int mode) {
        refresh(entity);
        if(!entity.isAlive() || entity.isSpectator() || entity instanceof Player p && p.isCreative()) return false;
        return switch(mode) { case 0 -> entity instanceof Player; case 1 -> !(entity instanceof Player) && !(entity instanceof LifeTarget); case 2 -> true; case 3 -> entity instanceof LifeTarget; default -> false; };
    }
    public static boolean matches(LivingEntity entity,VirtualModule module) { return matches(entity,(int)module.value("target")); }
    public static List<LivingEntity> query(ServerLevel level,UUID owner,AABB box) { return query(level,owner,box,e -> true); }
    public static List<LivingEntity> query(ServerLevel level,UUID owner,AABB box,Predicate<LivingEntity> filter) {
        var result=new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,box,filter));
        for(var heart:BodyOwnership.get(level).loadedHearts(level)) {
            if(heart.creatureId().equals(owner) || heart.health().state()!=HeartState.ALIVE) continue;
            var target=new LifeTarget(level,heart,owner); if(box.intersects(target.getBoundingBox()) && filter.test(target)) result.add(target);
        }
        return result;
    }
    public static LivingEntity resolve(ServerLevel level,UUID id,UUID owner) {
        if(id==null) return null;
        if(level.getEntity(id) instanceof LivingEntity entity) return entity;
        var heart=BodyOwnership.get(level).loadedHeart(level,id);
        return heart==null || id.equals(owner) || heart.health().state()!=HeartState.ALIVE ? null : new LifeTarget(level,heart,owner);
    }
    public static void refresh(LivingEntity target) { if(target instanceof LifeTarget life) life.refresh(); }
    public static boolean visible(ServerLevel level,UUID owner,Vec3 start,LivingEntity target) {
        refresh(target);
        return dev.sablelife.sense.SensorGeometry.visible(level,owner,start,target.getBoundingBox().getCenter(),target instanceof LifeTarget ? target.getUUID() : null);
    }
    /** A detached adapter lets geometry/cadence code treat a core consistently with ordinary targets. */
    public static final class LifeTarget extends ArmorStand {
        private final HeartBlockEntity heart;
        private final UUID attacker;
        private LifeTarget(ServerLevel level,HeartBlockEntity heart,UUID attacker) {
            super(level,0,0,0); this.heart=heart; this.attacker=attacker;
            setUUID(heart.creatureId()); setInvisible(true); setNoGravity(true); refresh();
        }
        private void refresh() {
            var center=SableBodyProbe.toWorld(heart.getLevel(),heart.getBlockPos().getCenter());
            setPos(center.x,center.y-.5,center.z);
            var box=new AABB(center,center).inflate(.5);
            var root=dev.ryanhcode.sable.Sable.HELPER.getContaining(heart.getLevel(),heart.getBlockPos());
            if(root!=null) for(var body:dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(root)) {
                if(body.isRemoved()) continue;
                var bounds=body.getPlot().getBoundingBox(); if(bounds==null) continue;
                for(int x=0;x<2;x++) for(int y=0;y<2;y++) for(int z=0;z<2;z++) {
                    var corner=body.logicalPose().transformPosition(new Vec3(x==0?bounds.minX():bounds.maxX()+1.,y==0?bounds.minY():bounds.maxY()+1.,z==0?bounds.minZ():bounds.maxZ()+1.));
                    box=box.minmax(new AABB(corner,corner));
                }
            }
            setPos(center.x,box.minY,center.z); setBoundingBox(box); setCustomName(heart.displayName());
        }
        @Override public Vec3 getDeltaMovement() {
            if(heart==null) return super.getDeltaMovement();
            var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(heart.getLevel(),heart.getBlockPos());
            return body instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel server ? new Vec3(server.latestLinearVelocity.x/20,server.latestLinearVelocity.y/20,server.latestLinearVelocity.z/20) : Vec3.ZERO;
        }
        @Override public void setDeltaMovement(Vec3 movement) {
            if(heart==null) { super.setDeltaMovement(movement); return; }
            var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(heart.getLevel(),heart.getBlockPos());
            var system=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get((ServerLevel)heart.getLevel());
            if(body instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel server && system!=null && !server.isRemoved()) {
                var delta=movement.subtract(getDeltaMovement()).scale(20);
                system.getPipeline().addLinearAndAngularVelocity(server,new org.joml.Vector3d(delta.x,delta.y,delta.z),new org.joml.Vector3d());
            }
        }
        @Override public boolean isAlive() { return heart!=null && !heart.isRemoved() && heart.health().state()==HeartState.ALIVE; }
        @Override public float getHealth() { return heart==null ? super.getHealth() : heart.health().current(); }
        @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount) {
            return isAlive() && heart.acceptsLifeAttack(attacker) && heart.damageAt(heart.getBlockPos(),amount);
        }
    }
    private ModuleTargets() {}
}
