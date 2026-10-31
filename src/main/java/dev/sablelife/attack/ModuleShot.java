package dev.sablelife.attack;

import dev.sablelife.SableLife;
import dev.sablelife.module.VirtualModule;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.UUID;

/** Server swept collision; bullets have no gravity, shells share preview physics. */
public final class ModuleShot extends Entity implements ItemSupplier {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(ModuleShot.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ModuleShot.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HITBOX = SynchedEntityData.defineId(ModuleShot.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> FALLING_FIXED = SynchedEntityData.defineId(ModuleShot.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<org.joml.Vector3f> FALLING_ROTATION = SynchedEntityData.defineId(ModuleShot.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> FALLING_SIZE = SynchedEntityData.defineId(ModuleShot.class, EntityDataSerializers.FLOAT);
    public boolean fallingProjectile(){return entityData.get(KIND)==2;}
    public float fallingItemSize(){return entityData.get(FALLING_SIZE);}
    @Override public AABB getBoundingBoxForCulling(){return fallingProjectile()?new AABB(position(),position()).inflate(fallingItemSize()):super.getBoundingBoxForCulling();}
    public boolean fixedFallingItem(){return entityData.get(KIND)==2&&entityData.get(FALLING_FIXED);}
    public org.joml.Vector3f fallingItemAngles(){return new org.joml.Vector3f(entityData.get(FALLING_ROTATION));}
    public org.joml.Quaternionf fallingItemRotation(){var r=fallingItemAngles();return new org.joml.Quaternionf().rotationXYZ((float)Math.toRadians(r.x),(float)Math.toRadians(r.y),(float)Math.toRadians(r.z));}
    private static float visualAngle(float v){return Float.isFinite(v)?Math.clamp(v,-180,180):0;}
    private java.util.List<dev.sablelife.module.ModuleParticles.Entry> trail = java.util.List.of();
    private int mortarWarningColor;
    private CompoundTag blast = new CompoundTag();
    private VirtualModule impactConfig;
    private float damage;
    private double range = 64, traveled;
    private int age;
    private int visualLife;
    private UUID owner;
    private VirtualModule homingConfig;
    private UUID homingTarget;
    private Vec3 homingCenter=Vec3.ZERO, homingHover=Vec3.ZERO;
    public boolean defaultHomingAppearance() { return homingProjectile() && entityData.get(ITEM).isEmpty(); }
    private int homingIndex, homingFlightAge, homingLegTicks;
    private net.minecraft.core.Direction homingDirection;
    private boolean homingLaunched;
    private HeartBlockEntity homingHeart;
    public boolean homingProjectile() { return entityData.get(KIND)==3; }
    public UUID homingModule() { return homingConfig==null ? null : homingConfig.id(); }
    public UUID attackOwner() { return owner; }
    public UUID homingTarget() { return homingTarget; }
    public boolean hovering() { return homingProjectile() && !homingLaunched; }
    public static ModuleShot homing(ServerLevel level,Vec3 point,Vec3 center,VirtualModule module,HeartBlockEntity heart,int index) {
        var shot=new ModuleShot(level,module.value("homingDeploy") > 0 ? center : point,Vec3.ZERO,module,heart);
        shot.entityData.set(KIND,3); shot.entityData.set(HITBOX,(float)module.value("projectileHitbox")); shot.refreshDimensions();
        shot.homingConfig=VirtualModule.load(module.save()); shot.homingCenter=center; shot.homingHover=point; shot.homingIndex=index; shot.homingHeart=heart;
        var direction=point.subtract(center);
        shot.setYRot((float)Math.toDegrees(Math.atan2(direction.x,direction.z)));
        shot.setXRot((float)Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));
        shot.range=Math.min(2048,module.value("homingLife")*module.value("speed")+2); return shot;
    }
    private void homingSound(ServerLevel world,String key) {
        dev.sablelife.module.ModuleAudio.play(world,homingConfig.soundTimeline(key).authored()?homingConfig.id():getUUID(),homingConfig.soundTimeline(key),position());
    }
    private boolean steerHoming(ServerLevel world) {
        if(homingConfig==null || owner==null) { discard(); return false; }
        if(homingHeart==null || homingHeart.isRemoved()) homingHeart=dev.sablelife.ownership.BodyOwnership.get(world).loadedHeart(world,owner);
        if(homingHeart==null || homingHeart.health().state()!=dev.sablelife.heart.HeartState.ALIVE || !homingHeart.wireless().master()
                || !owner.equals(homingHeart.creatureId())) { discard(); return false; }
        var current=homingHeart.modules().find(homingConfig.id());
        if(current==null || !dev.sablelife.module.HeartModules.status(current,homingHeart).equals("valid")) { discard(); return false; }
        if(!homingLaunched) {
            setDeltaMovement(Vec3.ZERO);
            int deploy=(int)homingConfig.value("homingDeploy");
            if(age < deploy) {
                var previous=position(); setPos(homingCenter.lerp(homingHover,++age/(double)deploy));
                drawHomingTrail(world,previous,position()); return false;
            }
            if(age++ < deploy+(int)homingConfig.value("chargeTime")) {
                if(age%4==0) drawHomingTrail(world,position(),position());
                return false;
            }
            var targets=dev.sablelife.module.ModuleHoming.targets(world,homingHeart,homingConfig,homingCenter);
            if(targets.isEmpty()) { discard(); return false; }
            homingTarget=targets.get(homingIndex%targets.size()).getUUID(); homingLaunched=true;
            if(homingIndex==0) {
                for(var player:world.players()) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new dev.sablelife.module.ModuleNetworking.ModuleSound(homingConfig.id(),"",true,Vec3.ZERO,0,1));
                homingSound(world,"fireSound");
            }
        }
        if(++homingFlightAge > (int)homingConfig.value("homingLife")) { discard(); return false; }
        if(homingTarget==null) { discard(); return false; }
        var entity=dev.sablelife.module.ModuleTargets.resolve(world,homingTarget,owner);
        if(!(entity instanceof LivingEntity target) || !dev.sablelife.module.ModuleHoming.eligible(target,homingConfig)
                || target.getBoundingBox().getCenter().distanceToSqr(homingCenter)>Math.pow(homingConfig.value("targetRange"),2)) { discard(); return false; }
        var delta=target.getBoundingBox().getCenter().subtract(position());
        // Direct pursuit predicts a short lead, then approaches the actual center near impact.
        // Strength controls steering, not a random chance to apply damage.
        if(homingConfig.value("homingMode")==0) {
            double speed=homingConfig.value("speed"), distance=delta.length();
            var lead=target.getDeltaMovement().scale(distance<=2 ? 0 : Math.min(6,distance/speed));
            var aim=delta.add(lead);
            var desired=aim.normalize().scale(Math.min(speed,distance));
            setDeltaMovement(getDeltaMovement().lerp(desired,homingConfig.value("homingTracking")*.2));
            return true;
        }
        Vec3 desired;
        if(delta.lengthSqr()<=4) desired=delta.normalize().scale(homingConfig.value("speed"));
        else {
            if(--homingLegTicks<=0 || homingDirection==null || aligned(delta,homingDirection)
                    || world.clip(new ClipContext(position(),position().add(Vec3.atLowerCornerOf(homingDirection.getNormal())),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS) {
                var choices=new java.util.ArrayList<net.minecraft.core.Direction>();
                for(var direction:net.minecraft.core.Direction.values()) {
                    var step=Vec3.atLowerCornerOf(direction.getNormal());
                    if(step.dot(delta)<=.1 || homingDirection!=null && direction.getAxis()==homingDirection.getAxis()) continue;
                    if(world.clip(new ClipContext(position(),position().add(step),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS) choices.add(direction);
                }
                homingDirection=choices.isEmpty() ? net.minecraft.core.Direction.getNearest(delta.x,delta.y,delta.z) : choices.get(random.nextInt(choices.size()));
                homingLegTicks=10+random.nextInt(4)*10;
            }
            desired=Vec3.atLowerCornerOf(homingDirection.getNormal()).scale(homingConfig.value("speed"));
        }
        setDeltaMovement(getDeltaMovement().lerp(desired,.2)); return true;
    }
    private static boolean aligned(Vec3 delta,net.minecraft.core.Direction direction) {
        return Math.abs(switch(direction.getAxis()) { case X -> delta.x; case Y -> delta.y; case Z -> delta.z; })<.5;
    }
    private void drawHomingTrail(ServerLevel world,Vec3 start,Vec3 end) {
        if(trail.isEmpty()) dev.sablelife.module.ModuleParticles.send(world,ParticleTypes.END_ROD,end.x,end.y,end.z,1,0,0,0,0);
        else dev.sablelife.module.ProjectileTrail.draw(world,start,end,trail);
    }
    public ModuleShot(EntityType<? extends ModuleShot> type, Level level) { super(type, level); refreshDimensions(); }
    public ModuleShot(ServerLevel level, Vec3 origin, Vec3 velocity, VirtualModule module, HeartBlockEntity heart) {
        this(SableLife.MODULE_SHOT.get(), level);
        entityData.set(KIND, module.type().equals("mortar") ? 1 : 0);
        entityData.set(ITEM, module.projectileItem(level.registryAccess()));
        entityData.set(HITBOX, (float) module.value(mortar() ? "projectileHitbox" : "bulletHitbox")); refreshDimensions();
        setPos(origin); setDeltaMovement(velocity); damage = (float) module.value("damage");
        mortarWarningColor = dev.sablelife.module.ModuleColors.packed(module,"mortarWarningColor");
        if(dev.sablelife.module.ModuleSurfaceField.impactAttack(module)&&module.impactFieldId()!=null)impactConfig=VirtualModule.load(module.save());
        range = mortar() ? 2048 : module.value("range"); blast = ModuleBlast.settings(module); trail = module.particleEntries(); owner = heart.creatureId();
        getPersistentData().putUUID("SableLifeAttackOwner", owner);
        ModuleDamage.nameProjectile(this,heart);AttackHitCredit.attach(this,heart,module);blast.putString(ModuleDamage.ATTACK_NAME,heart.creatureName());
    }
    public boolean mortar() { return entityData.get(KIND) == 1; }
    public static ModuleShot fallingVisual(ServerLevel level, Vec3 origin, double speed, int lifetime, VirtualModule module) {
        var shot = new ModuleShot(SableLife.MODULE_SHOT.get(), level);
        shot.entityData.set(KIND, 2); shot.entityData.set(ITEM, module.projectileItem(level.registryAccess()));
        shot.entityData.set(FALLING_SIZE,(float)module.value("strikeItemSize"));shot.entityData.set(FALLING_FIXED,module.value("strikeItemMode")==1);shot.entityData.set(FALLING_ROTATION,new org.joml.Vector3f((float)module.value("strikeItemX"),(float)module.value("strikeItemY"),(float)module.value("strikeItemZ")));
        shot.entityData.set(HITBOX, (float) module.value("projectileHitbox"));
        shot.visualLife = lifetime; shot.setPos(origin); shot.setDeltaMovement(0, -speed, 0);
        return shot;
    }
    public float projectileDiameter() { return entityData == null ? .2F : entityData.get(HITBOX); }
    @Override protected AABB makeBoundingBox() {
        double radius = projectileDiameter() / 2.;
        return new AABB(position(), position()).inflate(radius);
    }
    @Override public void refreshDimensions() { super.refreshDimensions(); setBoundingBox(makeBoundingBox()); }
    @Override public ItemStack getItem() { return entityData.get(ITEM).isEmpty() ? new ItemStack(homingProjectile() ? Items.AIR : entityData.get(KIND) != 0 ? Items.FIRE_CHARGE : Items.IRON_NUGGET) : entityData.get(ITEM).copy(); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(ITEM, ItemStack.EMPTY); builder.define(KIND, 0); builder.define(HITBOX, .2F); builder.define(FALLING_SIZE,.5F);builder.define(FALLING_FIXED,false);builder.define(FALLING_ROTATION,new org.joml.Vector3f()); }
    @Override public EntityDimensions getDimensions(Pose pose) { float size = entityData.get(HITBOX); return EntityDimensions.fixed(size, size); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if (key.equals(HITBOX)) refreshDimensions(); }
    private BlockHitResult blockHit(ServerLevel world, Vec3 start, Vec3 end) {
        return blockHit(world, this, start, end, entityData.get(HITBOX));
    }
    public static BlockHitResult blockHit(ServerLevel world, Entity projectile, Vec3 start, Vec3 end, float diameter) {
        var best = world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
        var direction = end.subtract(start).normalize();
        var side = direction.cross(Math.abs(direction.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        var up = side.cross(direction).normalize(); double radius = diameter / 2.;
        int steps = Math.max(2, (int) Math.ceil(radius * 2 / .5));
        double distance = best.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : SableBodyProbe.toWorld(world, best.getLocation()).distanceToSqr(start);
        for (int x = 0; x <= steps; x++) for (int y = 0; y <= steps; y++) {
            var offset = side.scale(-radius + 2 * radius * x / steps).add(up.scale(-radius + 2 * radius * y / steps));
            var hit = world.clip(new ClipContext(start.add(offset), end.add(offset), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
            if (hit.getType() == HitResult.Type.MISS) continue;
            double d = SableBodyProbe.toWorld(world, hit.getLocation()).subtract(offset).distanceToSqr(start);
            if (d < distance) { best = hit; distance = d; }
        }
        return best;
    }
    @Override public void tick() {
        super.tick();
        if (homingProjectile()) {
            if(level() instanceof ServerLevel world) { if(!steerHoming(world)) return; }
            else if(getDeltaMovement().lengthSqr()<.000001) return;
        }
        var start = position(); var velocity = getDeltaMovement(); var end = start.add(velocity);
        if (entityData.get(KIND) == 2) {
            if (level() instanceof ServerLevel && ++age >= visualLife) { discard(); return; }
            setPos(end); return;
        }
        if (level() instanceof ServerLevel world) {
            if ((!homingProjectile() && ++age > 600) || traveled >= range || end.y < world.getMinBuildHeight() - 32) { discard(); return; }
            double remaining = range - traveled;
            if (velocity.length() > remaining) end = start.add(velocity.normalize().scale(remaining));
            var block = blockHit(world, start, end);
            var stop = block.getType() == HitResult.Type.MISS ? end : SableBodyProbe.toWorld(world, block.getLocation());
            var entity = ProjectileUtil.getEntityHitResult(world, this, start, stop, new AABB(start, stop).inflate(entityData.get(HITBOX) / 2. + .3),
                    e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.isCreative()), entityData.get(HITBOX) / 2F);
            if (entity != null) {
                var victim = (LivingEntity) entity.getEntity();
                stop = victim.getBoundingBox().inflate(entityData.get(HITBOX) / 2.).clip(start, stop).orElse(entity.getLocation());
                if (damage == 0 || ModuleDamage.hurt(victim, world.damageSources().thrown(this, null), damage)) {
                    dev.sablelife.module.ModuleEffects.apply(victim, dev.sablelife.module.ModuleEffects.load(blast.getList("Effects", 10)), this);
                    blast.putUUID("EffectVictim", victim.getUUID());
                }
            } else if (block.getType() == HitResult.Type.BLOCK) {
                var heart = SableBodyProbe.findActivatedHeart(world, block.getBlockPos());
                if (heart != null && heart.acceptsLifeAttack(owner)){float before=heart.health().current();if(heart.damageAt(block.getBlockPos(), damage)){AttackHitCredit.record(this,Math.max(0,before-heart.health().current()));CombatEventHooks.killed(this,heart);}}
                stop = stop.add(net.minecraft.world.phys.Vec3.atLowerCornerOf(block.getDirection().getNormal()).scale(.03));
            }
            if (entity != null || block.getType() == HitResult.Type.BLOCK) {
                if(impactConfig!=null)dev.sablelife.module.SurfaceFieldImpact.projectile(this,impactConfig,entity!=null?entity:block,velocity);
                setPos(stop); if(homingProjectile()) homingSound(world,"impactSound"); ModuleBlast.explode(world, this, stop, blast); discard(); return;
            }
            traveled += start.distanceTo(end);
            if(homingProjectile()) drawHomingTrail(world,start,end); else dev.sablelife.module.ProjectileTrail.draw(world, start, end, trail);
            if (mortar() && age % 5 == 0) ShotTrajectory.ring(world, null,
                    ShotTrajectory.trace(world, end, velocity.add(0, -ShotTrajectory.GRAVITY, 0)).landing(), blast.getFloat("Radius"), mortarWarningColor);
        }
        setPos(end); if (mortar()) setDeltaMovement(velocity.add(0, -ShotTrajectory.GRAVITY, 0));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (!entityData.get(ITEM).isEmpty()) tag.put("Item", entityData.get(ITEM).save(level().registryAccess()));
        tag.putFloat("FallingItemSize",fallingItemSize());tag.putBoolean("FallingFixed",entityData.get(FALLING_FIXED));var rotation=fallingItemAngles();tag.putFloat("FallingItemX",rotation.x);tag.putFloat("FallingItemY",rotation.y);tag.putFloat("FallingItemZ",rotation.z);
        tag.putFloat("Hitbox", entityData.get(HITBOX)); tag.putInt("Kind", entityData.get(KIND)); tag.putFloat("Damage", damage); tag.putDouble("Range", range);
        tag.putDouble("Traveled", traveled); tag.putInt("Age", age); tag.put("Blast", blast.copy());
        tag.putInt("MortarWarningColor", mortarWarningColor);
        if(impactConfig!=null)tag.put("ImpactConfig",impactConfig.save());
        tag.putInt("VisualLife", visualLife); tag.put("Trail", dev.sablelife.module.ModuleParticles.save(trail.stream().map(dev.sablelife.module.ModuleParticles.Entry::id).toList())); tag.put("TrailProperties", dev.sablelife.module.ModuleParticles.saveEntries(trail));
        if (owner != null) tag.putUUID("Owner", owner);
        if(homingConfig!=null) {
            tag.put("HomingConfig",homingConfig.save()); tag.putInt("HomingIndex",homingIndex); tag.putBoolean("HomingLaunched",homingLaunched);
            tag.putInt("HomingFlightAge",homingFlightAge); tag.putInt("HomingLegTicks",homingLegTicks);
            tag.putDouble("HoverX",homingHover.x); tag.putDouble("HoverY",homingHover.y); tag.putDouble("HoverZ",homingHover.z);
            tag.putDouble("HomingX",homingCenter.x); tag.putDouble("HomingY",homingCenter.y); tag.putDouble("HomingZ",homingCenter.z);
            if(homingTarget!=null) tag.putUUID("HomingTarget",homingTarget);
            if(homingDirection!=null) tag.putString("HomingDirection",homingDirection.getName());
        }
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(ITEM, ItemStack.parseOptional(level().registryAccess(), tag.getCompound("Item")));
        entityData.set(FALLING_FIXED,tag.getBoolean("FallingFixed"));entityData.set(FALLING_ROTATION,new org.joml.Vector3f(visualAngle(tag.getFloat("FallingItemX")),visualAngle(tag.getFloat("FallingItemY")),visualAngle(tag.getFloat("FallingItemZ"))));
        entityData.set(KIND, Math.clamp(tag.getInt("Kind"), 0, 3)); visualLife = Math.clamp(tag.getInt("VisualLife"), 1, 640); damage = Math.clamp(tag.getFloat("Damage"), 0F, 1000F);
        entityData.set(HITBOX, tag.contains("Hitbox") ? Math.clamp(tag.getFloat("Hitbox"), .05F, 2F) : mortar() ? .15F : .2F); refreshDimensions();
        float savedSize=tag.contains("FallingItemSize")?tag.getFloat("FallingItemSize"):entityData.get(HITBOX);entityData.set(FALLING_SIZE,Float.isFinite(savedSize)?Math.clamp(savedSize,.1F,32F):.5F);
        range = Math.clamp(tag.getDouble("Range"), 1, 2048); int savedColor=tag.getInt("MortarWarningColor");mortarWarningColor=savedColor>=0x1000000&&savedColor<=0x1FFFFFF?savedColor:Math.clamp(savedColor,0,6);
        traveled = Math.max(0, tag.getDouble("Traveled"));
        trail = dev.sablelife.module.ModuleParticles.loadEntries(tag.getList("TrailProperties", 10), dev.sablelife.module.ModuleParticles.load(tag.getList("Trail", 8))); age = Math.clamp(tag.getInt("Age"), 0, 1400); blast = tag.getCompound("Blast").copy();
        impactConfig=tag.contains("ImpactConfig")?VirtualModule.load(tag.getCompound("ImpactConfig")):null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        if (owner != null) getPersistentData().putUUID("SableLifeAttackOwner", owner);
        if(homingProjectile() && tag.contains("HomingConfig")) {
            homingConfig=VirtualModule.load(tag.getCompound("HomingConfig")); homingIndex=Math.clamp(tag.getInt("HomingIndex"),0,31);
            homingLaunched=tag.getBoolean("HomingLaunched"); homingFlightAge=Math.clamp(tag.getInt("HomingFlightAge"),0,1200);
            homingLegTicks=Math.clamp(tag.getInt("HomingLegTicks"),0,40); homingDirection=net.minecraft.core.Direction.byName(tag.getString("HomingDirection"));
            homingCenter=new Vec3(tag.getDouble("HomingX"),tag.getDouble("HomingY"),tag.getDouble("HomingZ"));
            homingHover=tag.contains("HoverX") ? new Vec3(tag.getDouble("HoverX"),tag.getDouble("HoverY"),tag.getDouble("HoverZ")) : position();
            homingTarget=tag.hasUUID("HomingTarget") ? tag.getUUID("HomingTarget") : null;
        }
    }
}
