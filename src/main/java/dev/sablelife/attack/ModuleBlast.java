package dev.sablelife.attack;

import dev.sablelife.module.VirtualModule;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/** Snapshot shot settings so edits cannot change projectiles already in flight. */
public final class ModuleBlast {
    private static final String KEY = "SableLifeBlast";
    private record Wave(ServerLevel world, Vec3 origin, float radius, long start) {}
    private static final java.util.List<Wave> waves = new java.util.ArrayList<>();
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { waves.removeIf(w->w.world.getServer()==event.getServer()); }
    public static CompoundTag settings(VirtualModule module) {
        var tag = new CompoundTag();
        tag.put("Effects", dev.sablelife.module.ModuleEffects.save(module.effects()));
        tag.putBoolean("Enabled", module.value("explode") == 1);
        tag.putFloat("Damage", (float) module.value("blastDamage"));
        tag.putFloat("Radius", (float) module.value("blastRadius"));
        tag.putBoolean("Blocks", module.value("blastBlocks") == 1);
        if (module.type().equals("homing")) {
            tag.putString("Sound", module.text("blastSound")); tag.put("SoundTimeline",module.soundTimeline("blastSound").save());tag.putUUID("SoundOwner",module.id());
            tag.putFloat("SoundVolume", (float)module.value("blastSoundVolume"));
            tag.putFloat("SoundRadius", (float)module.value("blastSoundRadius"));
        }
        return tag;
    }
    public static void configure(Entity arrow, VirtualModule module) { var tag=settings(module);if(arrow.getPersistentData().contains(ModuleDamage.ATTACK_NAME))tag.putString(ModuleDamage.ATTACK_NAME,arrow.getPersistentData().getString(ModuleDamage.ATTACK_NAME));arrow.getPersistentData().put(KEY, tag); }
    public static void impact(ProjectileImpactEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel world)
                || !(event.getRayTraceResult() instanceof BlockHitResult hit)) return;
        var entity = event.getEntity(); var tag = entity.getPersistentData().getCompound(KEY);
        if (!tag.getBoolean("Enabled") || tag.getBoolean("Spent")) return;
        tag.putBoolean("Spent", true);
        if (entity instanceof ModuleArrow arrow) {arrow.emitSurfaceField(hit);arrow.playLandingSound(hit);}
        explode(world, entity, SableBodyProbe.toWorld(world, hit.getLocation()).add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.03)), tag);
        entity.discard(); event.setCanceled(true);
    }
    public static void explode(ServerLevel world, Entity source, Vec3 point, CompoundTag tag) {
        if (!tag.getBoolean("Enabled")) return;
        float radius = Math.clamp(tag.getFloat("Radius"), .5F, 16F), damage = Math.clamp(tag.getFloat("Damage"), 0F, 1000F);
        var effects = dev.sablelife.module.ModuleEffects.load(tag.getList("Effects", 10));
        var owner=source!=null && source.getPersistentData().hasUUID("SableLifeAttackOwner") ? source.getPersistentData().getUUID("SableLifeAttackOwner") : null;
        for (var victim : dev.sablelife.module.ModuleTargets.query(world,owner, new AABB(point, point).inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.isCreative()))) {
            var box = victim.getBoundingBox();
            var nearest = new Vec3(Math.clamp(point.x, box.minX, box.maxX), Math.clamp(point.y, box.minY, box.maxY), Math.clamp(point.z, box.minZ, box.maxZ));
            if (nearest.distanceToSqr(point) > radius * radius) continue;
            // The warning sphere defines the full area of effect, including its edge.
            // A floor/sublevel voxel must not erase the advertised blast damage.
            if ((damage == 0 || ModuleDamage.hurt(victim, tag.contains(ModuleDamage.ATTACK_NAME)?ModuleDamage.named(ModuleDamage.blast(world, source),tag.getString(ModuleDamage.ATTACK_NAME)):ModuleDamage.blast(world, source), damage))
                    && (!tag.hasUUID("EffectVictim") || !tag.getUUID("EffectVictim").equals(victim.getUUID())))
                dev.sablelife.module.ModuleEffects.apply(victim, effects, source);
        }
        world.explode(source, null, new ExplosionDamageCalculator() {
            @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) { return false; }
            @Override public float getKnockbackMultiplier(Entity entity) { return 0; }
        }, point.x, point.y, point.z, radius / 2, false,
                tag.getBoolean("Blocks") ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE,
                net.minecraft.core.particles.ParticleTypes.EXPLOSION, net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                tag.contains("Sound") ? net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvents.EMPTY) : net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE);
        if(tag.contains("SoundTimeline")){
            var timeline=dev.sablelife.sound.SoundTimeline.load(tag.getCompound("SoundTimeline"));
            dev.sablelife.module.ModuleAudio.play(world,timeline.authored()&&tag.hasUUID("SoundOwner")?tag.getUUID("SoundOwner"):java.util.UUID.randomUUID(),timeline,point);
        }
        else if (tag.contains("Sound")) dev.sablelife.module.ModuleAudio.play(world, java.util.UUID.randomUUID(), tag.getString("Sound"), point,
                tag.getFloat("SoundVolume"), tag.getFloat("SoundRadius"));
        waves.add(new Wave(world, point, radius, world.getGameTime()));
        dev.sablelife.module.ModuleParticles.send(world, net.minecraft.core.particles.ParticleTypes.EXPLOSION, point.x, point.y + .2, point.z,
                Math.clamp((int) (radius * 4), 4, 64), radius * .4, .4, radius * .4, 0);
    }
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        var iterator = waves.iterator();
        while (iterator.hasNext()) {
            var wave = iterator.next();
            if (event.getServer().getLevel(wave.world.dimension()) != wave.world) { iterator.remove(); continue; }
            long age = wave.world.getGameTime() - wave.start;
            if (age > 8 || age < 0) { iterator.remove(); continue; }
            if (age % 2 != 0) continue;
            double r = wave.radius * Math.max(.15, age / 8.);
            for (int i = 0; i < 64; i++) {
                double a = Math.PI * 2 * i / 64; var p = wave.origin.add(Math.cos(a) * r, .18, Math.sin(a) * r);
                dev.sablelife.module.ModuleParticles.send(wave.world, net.minecraft.core.particles.ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0, .1, 0, .03);
                if (age == 8 && i % 4 == 0) dev.sablelife.module.ModuleParticles.send(wave.world, net.minecraft.core.particles.ParticleTypes.EXPLOSION, p.x, p.y + .3, p.z, 1, 0, 0, 0, 0);
            }
        }
    }
}
