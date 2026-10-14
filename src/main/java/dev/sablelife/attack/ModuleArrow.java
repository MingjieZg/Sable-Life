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
    private java.util.List<dev.sablelife.module.ModuleParticles.Entry> trail = java.util.List.of();
    private int residueTicks = 200, residueAge;
    private dev.sablelife.sound.SoundTimeline landingSound = new dev.sablelife.sound.SoundTimeline();
    private boolean customLandingSound, suppressNativeLanding;
    private net.minecraft.core.BlockPos edgeBlock;
    public ModuleArrow(EntityType<? extends ModuleArrow> type, Level level) { super(type, level); refreshDimensions(); }
    public ModuleArrow(Level level, double x, double y, double z, ItemStack arrow, ItemStack weapon) {
        this(SableLife.MODULE_ARROW.get(), level); setPos(x, y, z);
    }
    public void configure(VirtualModule module) {
        entityData.set(APPEARANCE, module.projectileItem(level().registryAccess()));
        entityData.set(HITBOX, (float) module.value("arrowHitbox"));
        entityData.set(SIZE, (float) module.value("projectileSize")); refreshDimensions();
        residueTicks = (int) module.value("arrowResidue"); trail = module.particleEntries();
        // soundTimeline returns a fresh snapshot; later editor changes cannot alter this shot.
        landingSound = module.soundTimeline("arrowLandSound");
        customLandingSound = module.hasSoundTimeline("arrowLandSound") || !module.text("arrowLandSound").equals("minecraft:entity.arrow.hit");
    }
    public ItemStack appearance() { return entityData.get(APPEARANCE).copy(); }
    public float visualSize() { return entityData.get(SIZE); }
    public float hitbox() { return entityData == null ? .25F : entityData.get(HITBOX); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(APPEARANCE, ItemStack.EMPTY); builder.define(HITBOX, .25F); builder.define(SIZE, 1F); builder.define(EDGE_STUCK, false);
    }
    @Override public EntityDimensions getDimensions(Pose pose) { float size = hitbox(); return EntityDimensions.fixed(size, size); }
    @Override protected AABB makeBoundingBox() { return new AABB(position(), position()).inflate(hitbox() / 2.); }
    @Override public void refreshDimensions() { super.refreshDimensions(); setBoundingBox(makeBoundingBox()); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if (key.equals(HITBOX)) refreshDimensions(); }
    @Override protected EntityHitResult findHitEntity(Vec3 start, Vec3 end) {
        return ProjectileUtil.getEntityHitResult(level(), this, start, end, new AABB(start, end).inflate(hitbox() / 2. + .3), this::canHitEntity, hitbox() / 2F);
    }
    @Override public void tick() {
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
                    if (impact.isCanceled() || isRemoved()) return;
                    onHitBlock(block); setPos(point); setDeltaMovement(Vec3.ZERO);
                    edgeBlock = block.getBlockPos().immutable(); entityData.set(EDGE_STUCK, true);
                    if (residueTicks == 0) discard();
                    return;
                }
                if (isRemoved()) return;
            }
        }
        var trailStart = position(); boolean flying = !inGround;
        super.tick();
        if (flying && !isRemoved() && level() instanceof ServerLevel server) dev.sablelife.module.ProjectileTrail.draw(server, trailStart, position(), trail);
        if (!level().isClientSide && inGround && residueTicks == 0) discard();
    }
    @Override protected void tickDespawn() {
        // Override vanilla's fixed 60-second limit; snapshot the module setting per shot.
        if (++residueAge >= residueTicks) discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!appearance().isEmpty()) tag.put("ModuleAppearance", appearance().save(level().registryAccess()));
        tag.putFloat("ModuleHitbox", hitbox()); tag.putFloat("ModuleSize", visualSize());
        tag.put("ModuleTrail", dev.sablelife.module.ModuleParticles.save(trail.stream().map(dev.sablelife.module.ModuleParticles.Entry::id).toList())); tag.put("ModuleTrailProperties", dev.sablelife.module.ModuleParticles.saveEntries(trail)); tag.putInt("ModuleResidueTicks", residueTicks); tag.putInt("ModuleResidueAge", residueAge);
        if (edgeBlock != null) tag.putLong("ModuleEdgeBlock", edgeBlock.asLong());
        if (customLandingSound) tag.put("ModuleLandingSound", landingSound.save());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(APPEARANCE, ItemStack.parseOptional(level().registryAccess(), tag.getCompound("ModuleAppearance")));
        entityData.set(HITBOX, tag.contains("ModuleHitbox") ? Math.clamp(tag.getFloat("ModuleHitbox"), .05F, 2F) : .25F);
        entityData.set(SIZE, tag.contains("ModuleSize") ? Math.clamp(tag.getFloat("ModuleSize"), .1F, 8F) : 1F); refreshDimensions();
        residueTicks = tag.contains("ModuleResidueTicks") ? Math.clamp(tag.getInt("ModuleResidueTicks"), 0, 12000) : 200;
        residueAge = Math.clamp(tag.getInt("ModuleResidueAge"), 0, 12000); trail = dev.sablelife.module.ModuleParticles.loadEntries(tag.getList("ModuleTrailProperties", 10), dev.sablelife.module.ModuleParticles.load(tag.getList("ModuleTrail", 8)));
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
    @Override protected void onHitBlock(BlockHitResult hit) {
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
