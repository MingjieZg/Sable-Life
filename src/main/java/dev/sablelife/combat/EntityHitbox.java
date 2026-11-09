package dev.sablelife.combat;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.VirtualModule;
import net.minecraft.network.syncher.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import java.util.UUID;

/** A real, non-persistent living damage receiver. The core owns health and lifecycle. */
public final class EntityHitbox extends Mob {
    private static final EntityDataAccessor<Float> WIDTH=SynchedEntityData.defineId(EntityHitbox.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT=SynchedEntityData.defineId(EntityHitbox.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DEPTH=SynchedEntityData.defineId(EntityHitbox.class,EntityDataSerializers.FLOAT);
    private HeartBlockEntity core;
    private UUID module;
    private boolean provokeMobs;
    private HitboxQueryBounds queryBounds;
    private boolean largeQueryBounds;
    public EntityHitbox(EntityType<? extends EntityHitbox> type,Level level){super(type,level);setNoAi(true);setNoGravity(true);setInvisible(true);setSilent(true);noPhysics=true;}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,1024).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.MOVEMENT_SPEED,0);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(WIDTH,1F);builder.define(HEIGHT,1F);builder.define(DEPTH,1F);}
    public HeartBlockEntity core(){return core;}
    public UUID moduleId(){return module;}
    public void queryBounds(HitboxQueryBounds bounds){
        if(queryBounds!=bounds){
            if(largeQueryBounds)queryBounds.removedLargeReceiver();
            queryBounds=bounds;largeQueryBounds=false;
        }
        updateQueryBounds();
    }
    public void clearQueryBounds(HitboxQueryBounds bounds){if(queryBounds==bounds)queryBounds(null);}
    private void updateQueryBounds(){
        var box=getBoundingBox();boolean large=queryBounds!=null&&(box.getXsize()>4||box.getYsize()>4||box.getZsize()>4);
        if(large==largeQueryBounds)return;
        if(large)queryBounds.addedLargeReceiver();else queryBounds.removedLargeReceiver();
        largeQueryBounds=large;
    }
    boolean provokesMobs(){return provokeMobs&&isAlive()&&!isRemoved();}
    public void bind(HeartBlockEntity owner,VirtualModule settings){core=owner;module=settings.id();follow(settings);}
    public void follow(VirtualModule settings){
        provokeMobs=settings.value("hitboxProvokeMobs")==1;
        entityData.set(WIDTH,(float)settings.value("hitboxWidth"));entityData.set(HEIGHT,(float)settings.value("hitboxHeight"));entityData.set(DEPTH,(float)settings.value("hitboxDepth"));
        var center=EntityHitboxes.center(core,settings);if(center==null){discard();return;}
        setPos(center.x,center.y-entityData.get(HEIGHT)/2,center.z);setBoundingBox(makeBoundingBox());updateQueryBounds();
    }
    @Override protected AABB makeBoundingBox(){double w=entityData.get(WIDTH)/2.,h=entityData.get(HEIGHT),d=entityData.get(DEPTH)/2.;return new AABB(getX()-w,getY(),getZ()-d,getX()+w,getY()+h,getZ()+d);}
    @Override protected EntityDimensions getDefaultDimensions(Pose pose){return EntityDimensions.scalable(Math.max(entityData.get(WIDTH),entityData.get(DEPTH)),entityData.get(HEIGHT));}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key==WIDTH||key==HEIGHT||key==DEPTH){refreshDimensions();updateQueryBounds();}}
    @Override public void tick(){
        // No AI, movement, environmental damage, loot, equipment or effect ticking.
        tickCount++;xo=getX();yo=getY();zo=getZ();setDeltaMovement(Vec3.ZERO);invulnerableTime=0;
        if(isDeadOrDying()){discard();return;}
        if(!level().isClientSide){var settings=core==null?null:core.modules().damageBoxes.active(core,module);if(settings==null){discard();return;}follow(settings);}
        else setBoundingBox(makeBoundingBox());
    }
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){setPos(x,y,z);setYRot(yaw);setXRot(pitch);}
    @Override public boolean hurt(DamageSource source,float amount){
        if(level().isClientSide||core==null||isRemoved()||!Float.isFinite(amount)||amount<=0||core.modules().damageBoxes.active(core,module)==null||!EntityHitboxes.allowed(core,source))return false;
        // Core-scoped dedup replaces per-proxy vanilla immunity; different attackers remain independent.
        invulnerableTime=0;
        int depth=damageContainers.size();
        try{return super.hurt(source,Math.min(amount,dev.sablelife.heart.HeartHealth.MAX_CONFIGURED_HEALTH));}
        finally{invulnerableTime=0;while(damageContainers.size()>depth)damageContainers.pop();}
    }
    @Override protected void actuallyHurt(DamageSource source,float amount){
        var container=damageContainers.peek();
        float damage=net.neoforged.neoforge.common.CommonHooks.onLivingDamagePre(this,container);
        if(Float.isFinite(damage)&&damage>0)core.modules().damageBoxes.receive(core,this,source,damage,container);
    }
    @Override public net.minecraft.world.InteractionResult interactAt(net.minecraft.world.entity.player.Player player,Vec3 point,net.minecraft.world.InteractionHand hand){
        var result=dev.sablelife.item.CreativeReleaseToolItem.releaseReceiver(player,this,hand);
        return result!=net.minecraft.world.InteractionResult.PASS?result:super.interactAt(player,point,hand);
    }
    @Override protected net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
        var result=dev.sablelife.item.CreativeReleaseToolItem.releaseReceiver(player,this,hand);
        return result!=net.minecraft.world.InteractionResult.PASS?result:super.mobInteract(player,hand);
    }
    @Override public boolean isPushable(){return false;}
    @Override public void push(double x,double y,double z){}
    @Override public void knockback(double strength,double x,double z){}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean canBeLeashed(){return false;}
}
