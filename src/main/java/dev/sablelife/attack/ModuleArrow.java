package dev.sablelife.attack;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.*;
import net.minecraft.world.entity.*;
import net.minecraft.network.syncher.*;
import net.minecraft.nbt.CompoundTag;
import dev.sablelife.SableLife;
import dev.sablelife.module.ProjectileTracer;
import dev.sablelife.module.VirtualModule;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.ProjectileUtil;

/** Non-pickup module projectile; only its own impacts bypass hurt frames. */
public final class ModuleArrow extends Arrow {
    private static final EntityDataAccessor<ItemStack> APPEARANCE = SynchedEntityData.defineId(ModuleArrow.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> HITBOX = SynchedEntityData.defineId(ModuleArrow.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(ModuleArrow.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> EDGE_STUCK = SynchedEntityData.defineId(ModuleArrow.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FACE_CAMERA = SynchedEntityData.defineId(ModuleArrow.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<org.joml.Vector3f> ITEM_ROTATION = SynchedEntityData.defineId(ModuleArrow.class, EntityDataSerializers.VECTOR3);
    public org.joml.Vector3f itemRotation(){return new org.joml.Vector3f(entityData.get(ITEM_ROTATION));}
    private java.util.List<dev.sablelife.module.ModuleParticles.Entry> trail = java.util.List.of();
    private ProjectileTracer tracer;
    private Vec3 tracerImpact;
    private int residueTicks = 200, residueAge;
    private VirtualModule impactConfig;
    private boolean fieldBlockSpent;
    private final java.util.Set<java.util.UUID> fieldVictims=new java.util.HashSet<>();
    private dev.sablelife.sound.SoundTimeline landingSound = new dev.sablelife.sound.SoundTimeline();
    private boolean customLandingSound, suppressNativeLanding;
    private net.minecraft.core.BlockPos edgeBlock;
    public ModuleArrow(EntityType<? extends ModuleArrow> type, Level level) { super(type, level); refreshDimensions(); }
    public ModuleArrow(Level level, double x, double y, double z, ItemStack arrow, ItemStack weapon) {
        this(SableLife.MODULE_ARROW.get(), level); setPos(x, y, z);
    }
    public void configure(VirtualModule module) {
        impactConfig=module.impactFieldId()==null?null:VirtualModule.load(module.save());fieldBlockSpent=false;fieldVictims.clear();
        entityData.set(APPEARANCE, module.projectileItem(level().registryAccess()));
        entityData.set(FACE_CAMERA,module.value("arrowFaceCamera")==1);
        entityData.set(ITEM_ROTATION,ProjectileItemPose.from(module));
        entityData.set(HITBOX, (float) module.value("arrowHitbox"));
        entityData.set(SIZE, (float) module.value("projectileSize")); refreshDimensions();
        residueTicks = (int) module.value("arrowResidue"); trail = module.particleEntries();
        tracer = ProjectileTracer.from(module);
        // soundTimeline returns a fresh snapshot; later editor changes cannot alter this shot.
        landingSound = module.soundTimeline("arrowLandSound");
        customLandingSound = module.hasSoundTimeline("arrowLandSound") || !module.text("arrowLandSound").equals("minecraft:entity.arrow.hit");
    }
    public ItemStack appearance() { return entityData.get(APPEARANCE).copy(); }
    public float visualSize() { return entityData.get(SIZE); }
    public boolean facesCamera(){return entityData.get(FACE_CAMERA);}
    public float hitbox() { return entityData == null ? .25F : entityData.get(HITBOX); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(APPEARANCE, ItemStack.EMPTY); builder.define(HITBOX, .25F); builder.define(SIZE, 1F); builder.define(EDGE_STUCK, false); builder.define(FACE_CAMERA,false); builder.define(ITEM_ROTATION,new org.joml.Vector3f());
    }
    @Override public EntityDimensions getDimensions(Pose pose) { float size = hitbox(); return EntityDimensions.fixed(size, size); }
    @Override protected AABB makeBoundingBox() { return new AABB(position(), position()).inflate(hitbox() / 2.); }
    @Override public void refreshDimensions() { super.refreshDimensions(); setBoundingBox(makeBoundingBox()); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if (key.equals(HITBOX)) refreshDimensions(); }
    @Override protected boolean canHitEntity(Entity target){return !dev.sablelife.combat.OwnerPlayers.protectedFromProjectile(target,this)&&super.canHitEntity(target);}
    @Override protected EntityHitResult findHitEntity(Vec3 start, Vec3 end) {
        return ProjectileUtil.getEntityHitResult(level(), this, start, end, new AABB(start, end).inflate(hitbox() / 2. + .3), this::canHitEntity, hitbox() / 2F);
    }
    @Override public void tick() {
        tracerImpact = null;
        if (entityData.get(EDGE_STUCK)) {
            if (level() instanceof ServerLevel && edgeBlock != null && level().getBlockState(edgeBlock).getCollisionShape(level(), edgeBlock).isEmpty()) {
                entityData.set(EDGE_STUCK, false); inGround = false; edgeBlock = null;
            } else {
                super.baseTick(); setDeltaMovement(Vec3.ZERO);
                if (!level().isClientSide) tickDespawn();
                return;
            }
        }
        // Vanilla keeps its normal center-ray flight and embedded-arrow behavior. Test the
        // additional swept volume first so wide projectiles cannot pass beside a wall.
        if (!inGround && !isNoPhysics() && level() instanceof ServerLevel world && getDeltaMovement().lengthSqr() > .000001) {
            var start = position(); var end = start.add(getDeltaMovement());
            var block = ModuleShot.blockHit(world, this, start, end, hitbox());
            var center = world.clip(new net.minecraft.world.level.ClipContext(start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            if (block.getType() == HitResult.Type.BLOCK && (center.getType() == HitResult.Type.MISS
                    || SableBodyProbe.toWorld(world, block.getLocation()).distanceToSqr(start) + .0001 < SableBodyProbe.toWorld(world, center.getLocation()).distanceToSqr(start))) {
                var point = SableBodyProbe.toWorld(world, block.getLocation());
                var entity = findHitEntity(start, point);
                if (entity == null) {
                    var impact = new net.neoforged.neoforge.event.entity.ProjectileImpactEvent(this, block);
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(impact);
                    if (impact.isCanceled() || isRemoved()) {
                        drawTracer(world, start);
                        return;
                    }
                    onHitBlock(block); setPos(point); setDeltaMovement(Vec3.ZERO);
                    drawTracer(world, start);
                    edgeBlock = block.getBlockPos().immutable(); entityData.set(EDGE_STUCK, true);
                    if (residueTicks == 0) discard();
                    return;
                }
                if (isRemoved()) return;
            }
        }
        var trailStart = position(); boolean flying = !inGround;
        super.tick();
        if (flying && level() instanceof ServerLevel server) {
            if (tracer != null) drawTracer(server, trailStart);
            else if (!isRemoved()) dev.sablelife.module.ProjectileTrail.draw(server, trailStart, position(), trail);
        }
        if (!level().isClientSide && inGround && residueTicks == 0) discard();
    }
    @Override protected void tickDespawn() {
        // Override vanilla's fixed 60-second limit; snapshot the module setting per shot.
        if (++residueAge >= residueTicks) discard();
    }
    private void drawTracer(ServerLevel level, Vec3 start) {
        if (tracer != null) tracer.send(level, getId(), start, tracerImpact == null ? position() : tracerImpact);
    }

    void recordTracerImpact(HitResult hit) {
        if (tracer != null && tracerImpact == null) {
            if (hit instanceof EntityHitResult entity) {
                var start = position();
                tracerImpact = entity.getEntity().getBoundingBox().inflate(hitbox() * .5)
                        .clip(start, start.add(getDeltaMovement())).orElse(start);
            } else {
                tracerImpact = SableBodyProbe.toWorld(level(), hit.getLocation());
            }
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if(impactConfig!=null)tag.put("ImpactConfig",impactConfig.save());
        tag.putBoolean("FieldBlockSpent",fieldBlockSpent);var victims=new net.minecraft.nbt.ListTag();fieldVictims.stream().limit(64).forEach(id->victims.add(net.minecraft.nbt.NbtUtils.createUUID(id)));tag.put("FieldVictims",victims);
        if (!appearance().isEmpty()) tag.put("ModuleAppearance", appearance().save(level().registryAccess()));
        tag.putFloat("ModuleHitbox", hitbox()); tag.putFloat("ModuleSize", visualSize());
        tag.putBoolean("ModuleFaceCamera",facesCamera());
        tag.put("ProjectileItemRotation",ProjectileItemPose.save(itemRotation()));
        tag.put("ModuleTrail", dev.sablelife.module.ModuleParticles.save(trail.stream().map(dev.sablelife.module.ModuleParticles.Entry::id).toList())); tag.put("ModuleTrailProperties", dev.sablelife.module.ModuleParticles.saveEntries(trail)); tag.putInt("ModuleResidueTicks", residueTicks); tag.putInt("ModuleResidueAge", residueAge);
        if (tracer != null) tracer.save(tag);
        if (edgeBlock != null) tag.putLong("ModuleEdgeBlock", edgeBlock.asLong());
        if (customLandingSound) tag.put("ModuleLandingSound", landingSound.save());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        impactConfig=tag.contains("ImpactConfig")?VirtualModule.load(tag.getCompound("ImpactConfig")):null;fieldBlockSpent=tag.getBoolean("FieldBlockSpent");fieldVictims.clear();
        var victims=tag.getList("FieldVictims",11);for(int i=0;i<Math.min(64,victims.size());i++)try{fieldVictims.add(net.minecraft.nbt.NbtUtils.loadUUID(victims.get(i)));}catch(IllegalArgumentException ignored){}
        entityData.set(APPEARANCE, ItemStack.parseOptional(level().registryAccess(), tag.getCompound("ModuleAppearance")));
        entityData.set(FACE_CAMERA,tag.getBoolean("ModuleFaceCamera"));
        entityData.set(ITEM_ROTATION,ProjectileItemPose.read(tag.getCompound("ProjectileItemRotation")));
        entityData.set(HITBOX, tag.contains("ModuleHitbox") ? Math.clamp(tag.getFloat("ModuleHitbox"), .05F, 2F) : .25F);
        entityData.set(SIZE, tag.contains("ModuleSize") ? Math.clamp(tag.getFloat("ModuleSize"), .1F, 8F) : 1F); refreshDimensions();
        residueTicks = tag.contains("ModuleResidueTicks") ? Math.clamp(tag.getInt("ModuleResidueTicks"), 0, 12000) : 200;
        residueAge = Math.clamp(tag.getInt("ModuleResidueAge"), 0, 12000); trail = dev.sablelife.module.ModuleParticles.loadEntries(tag.getList("ModuleTrailProperties", 10), dev.sablelife.module.ModuleParticles.load(tag.getList("ModuleTrail", 8)));
        tracer = ProjectileTracer.read(tag);
        edgeBlock = tag.contains("ModuleEdgeBlock") ? net.minecraft.core.BlockPos.of(tag.getLong("ModuleEdgeBlock")) : null;
        entityData.set(EDGE_STUCK, edgeBlock != null);
        customLandingSound = tag.contains("ModuleLandingSound");
        landingSound = customLandingSound ? dev.sablelife.sound.SoundTimeline.load(tag.getCompound("ModuleLandingSound")) : new dev.sablelife.sound.SoundTimeline();
    }
    /** Also used by an accepted explosive block impact, which skips vanilla embedding. */
    public void playLandingSound(BlockHitResult hit) {
        if (customLandingSound && level() instanceof ServerLevel world)
            dev.sablelife.module.ModuleAudio.play(world, getUUID(), landingSound, SableBodyProbe.toWorld(world, hit.getLocation()));
    }
    /** Accepted explosive block impacts also call this before they discard the arrow. */
    public void emitSurfaceField(HitResult hit){
        if(impactConfig==null||impactConfig.impactFieldId()==null||!(level() instanceof ServerLevel))return;
        if(hit instanceof BlockHitResult){if(fieldBlockSpent)return;fieldBlockSpent=true;}
        else if(hit instanceof EntityHitResult entity){if(fieldVictims.size()>=64||!fieldVictims.add(entity.getEntity().getUUID()))return;}
        dev.sablelife.module.SurfaceFieldImpact.projectile(this,impactConfig,hit,getDeltaMovement());
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        recordTracerImpact(hit);
        emitSurfaceField(hit);
        suppressNativeLanding = customLandingSound;
        try { super.onHitBlock(hit); }
        finally { suppressNativeLanding = false; }
        playLandingSound(hit);
    }
    @Override public void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (suppressNativeLanding && sound == getHitGroundSoundEvent()) return;
        super.playSound(sound, volume, pitch);
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        recordTracerImpact(hit);
        emitSurfaceField(hit);
        if (hit.getEntity() instanceof LivingEntity target) {
            float before=target.getHealth();
            int previous = target.invulnerableTime;
            target.invulnerableTime = 0;
            try { super.onHitEntity(hit); }
            finally { if (target.isAlive()) target.invulnerableTime = Math.max(previous, target.invulnerableTime); }
            AttackHitCredit.record(this,Math.max(0,before-target.getHealth()));
        } else super.onHitEntity(hit);
    }
}
