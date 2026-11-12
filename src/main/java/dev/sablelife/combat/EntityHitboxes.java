package dev.sablelife.combat;

import dev.sablelife.SableLife;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.attack.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.*;
import java.util.*;

/** Per-core receiver ownership and tick-scoped aggregate. No world-global references or saved entities. */
public final class EntityHitboxes {
    private static final int RETALIATION_TICKS = 15 * 20;
    private static final int MAX_RETALIATION_TARGETS = 64;
    private final Map<UUID,EntityHitbox> boxes=new LinkedHashMap<>();
    private final Map<Object,Hit> pending=new LinkedHashMap<>();
    private final Map<UUID,Boolean> bodyClaims=new HashMap<>();
    private final Set<Object> settled=new HashSet<>();
    private final Map<Resistance,Window> hurtWindows=new LinkedHashMap<>();
    private long claimTick=Long.MIN_VALUE;
    private LinkedHashMap<UUID, Long> retaliationTargets;
    private record Hit(DamageSource source,float damage,float raw,EntityHitbox receiver,net.neoforged.neoforge.common.damagesource.DamageContainer event,BlockHitResult block,net.minecraft.core.BlockPos part,boolean retaliates){}
    private record Attack(UUID direct,UUID attacker,Object type,long tick){}
    private record Resistance(UUID attacker,Object type){}
    private record Window(long until,float raw){}
    public List<EntityHitbox> entities(){return boxes.values().stream().filter(e->!e.isRemoved()&&e.isAlive()).toList();}
    public static List<VirtualModule> selected(HeartBlockEntity core){
        if(core==null||core.isRemoved()||core.isControlCore()||core.health().state()!=HeartState.ALIVE||!core.operational())return List.of();
        return core.modules().all().stream().filter(m->m.type().equals("entity_hitbox")&&HeartModules.triggered(m,core))
                .limit(SableLifeServerConfig.maximumEntityHitboxesPerCreature()).toList();
    }
    public VirtualModule active(HeartBlockEntity core,UUID id){return selected(core).stream().filter(m->m.id().equals(id)).findFirst().orElse(null);}
    public static boolean mode(HeartBlockEntity core){var active=selected(core);return core!=null&&core.modules().damageBoxes.entities().stream().anyMatch(e->active.stream().anyMatch(m->m.id().equals(e.moduleId())));}
    /** One live hybrid receiver opens the structure path for the whole creature. */
    public static boolean hybrid(HeartBlockEntity core){
        if(core==null)return false;var active=selected(core);
        return core.modules().damageBoxes.entities().stream().anyMatch(e->active.stream().anyMatch(m->m.id().equals(e.moduleId())&&m.value("hitboxDamageMode")==1));
    }
    public static boolean blocksStructure(HeartBlockEntity core){return mode(core)&&!hybrid(core);}
    private void currentTick(HeartBlockEntity core){long now=core.getLevel().getGameTime();if(claimTick!=now){bodyClaims.clear();settled.clear();hurtWindows.values().removeIf(w->w.until<=now);claimTick=now;}}
    private static UUID identity(DamageSource source){var entity=source.getDirectEntity()!=null?source.getDirectEntity():source.getEntity();return entity==null?null:entity.getUUID();}
    private boolean explosion(DamageSource source){return source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)||BodyExplosionDamage.nativeExplosionSource(source,claimTick);}
    /**
     * Immediate body APIs must keep their actual-health-loss return contract.
     * Before settlement, a real block contact wins over its queued receiver alias.
     * After settlement, the other route cannot charge the same source again.
     * Continuous callers retain their own body cadence; only cross-route aliases are removed.
     */
    public boolean bodyDamage(HeartBlockEntity core,net.minecraft.world.entity.Entity source,net.minecraft.core.BlockPos block,float raw,boolean oneShot,boolean continuous){
        if(!hybrid(core))return core.damageAt(block,raw);
        if(source==null||!Float.isFinite(raw)||raw<=0||core.health().state()!=HeartState.ALIVE)return false;
        currentTick(core);UUID key=source.getUUID();Boolean route=bodyClaims.get(key);
        if(Boolean.FALSE.equals(route)||Boolean.TRUE.equals(route)&&!continuous)return false;
        if(oneShot){var data=source.getPersistentData();var seen=data.getCompound("SableLifeHitCreatures");String id=core.creatureId().toString();if(seen.getBoolean(id)||seen.getAllKeys().size()>=64)return false;seen.putBoolean(id,true);data.put("SableLifeHitCreatures",seen);}
        bodyClaims.put(key,true);
        pending.entrySet().removeIf(e->!(e.getKey() instanceof DamageSource)&&key.equals(identity(e.getValue().source)));
        return core.damageAt(block,raw);
    }
    /** Body and receiver contributions share the exact native explosion DamageSource. */
    public void bodyExplosion(HeartBlockEntity core,DamageSource source,net.minecraft.core.BlockPos block,float raw){
        if(!hybrid(core)||!allowed(core,source)||!Float.isFinite(raw)||raw<=0)return;
        currentTick(core);if(settled.contains(source))return;
        float damage=ModuleArmor.damage(core,block,raw);if(!Float.isFinite(damage)||damage<0)return;
        var previous=pending.get(source);if(previous==null||damage>previous.damage)pending.put(source,new Hit(source,damage,raw,previous==null?null:previous.receiver,previous==null?null:previous.event,null,block,false));
    }
    /** Give optional native projectiles one core hit while retaining their normal hurt window. */
    public void externalProjectileBody(HeartBlockEntity core,ExternalProjectileDamageSource source,BlockHitResult block,float raw){
        if(!allowed(core,source))return;
        currentTick(core);
        var direct=source.getDirectEntity();var key=direct.getUUID();
        if(settled.contains(key)||bodyClaims.containsKey(key)
                ||direct.getPersistentData().getCompound("SableLifeHitCreatures").getBoolean(core.creatureId().toString()))return;
        float damage=ModuleArmor.damage(core,block.getBlockPos(),raw);
        if(!Float.isFinite(damage)||damage<0)return;
        var previous=pending.get(key);
        if(previous==null||previous.block==null)pending.put(key,new Hit(source,damage,raw,null,null,block,block.getBlockPos(),false));
    }
    public static Vec3 center(HeartBlockEntity core,VirtualModule m){
        if(m.anchors().size()!=1)return null;var a=m.anchors().getFirst();var body=a.body(core);if(body==null||body.isRemoved())return null;
        return body.logicalPose().transformPosition(a.position(body).getCenter().add(m.value("hitboxOffsetX"),m.value("hitboxOffsetY"),m.value("hitboxOffsetZ")));
    }
    public void tick(HeartBlockEntity core){
        var selected=selected(core);var ids=new HashSet<UUID>();
        for(var m:selected){ids.add(m.id());var box=boxes.get(m.id());if(box==null||box.isRemoved()||!box.isAlive()){
            if(box!=null)box.discard();
            box=SableLife.ENTITY_HITBOX.get().create(core.getLevel());if(box==null)continue;box.bind(core,m);
            if(box.isRemoved()||!((ServerLevel)core.getLevel()).addFreshEntity(box))continue;boxes.put(m.id(),box);
        }else box.follow(m);}
        boxes.entrySet().removeIf(e->{if(ids.contains(e.getKey())&&!e.getValue().isRemoved())return false;e.getValue().discard();return true;});
        if(Math.floorMod(core.getLevel().getGameTime(),10)==Math.floorMod(core.creatureId().hashCode(),10))
            for(var box:boxes.values())HitboxAggro.offer(box);
    }
    public void clear(){for(var e:boxes.values())e.discard();boxes.clear();pending.clear();bodyClaims.clear();settled.clear();hurtWindows.clear();claimTick=Long.MIN_VALUE;retaliationTargets=null;}

    public boolean retaliatesAgainst(HeartBlockEntity core, LivingEntity target) {
        if (retaliationTargets == null) return false;
        Long until = retaliationTargets.get(target.getUUID());
        return until != null && core.getLevel().getGameTime() < until && target.isAlive();
    }

    private void rememberAttacker(HeartBlockEntity core, DamageSource source) {
        var attacker = source.getEntity();
        if (attacker == null && source.getDirectEntity() instanceof Projectile projectile) attacker = projectile.getOwner();
        if (attacker instanceof LivingEntity living && ModuleTargets.matches(living, 1)
                && core.relations().excludesMob(living.getType())) {
            if (retaliationTargets == null) retaliationTargets = new LinkedHashMap<>();
            if (retaliationTargets.size() >= MAX_RETALIATION_TARGETS) {
                retaliationTargets.values().removeIf(until -> until <= claimTick);
            }
            UUID id = living.getUUID();
            if (retaliationTargets.containsKey(id) || retaliationTargets.size() < MAX_RETALIATION_TARGETS) {
                retaliationTargets.put(id, claimTick + RETALIATION_TICKS);
            }
        }
    }
    public static boolean allowed(HeartBlockEntity core,DamageSource source){
        UUID owner=source instanceof CreatureAttackSource attack?attack.creatureOwner():null;
        var direct=source.getDirectEntity();if(owner==null&&direct!=null&&direct.getPersistentData().hasUUID("SableLifeAttackOwner"))owner=direct.getPersistentData().getUUID("SableLifeAttackOwner");
        if(direct instanceof EntityHitbox proxy&&proxy.core()!=null)owner=proxy.core().creatureId();
        return owner==null||!owner.equals(core.creatureId())&&CoreRelations.damageAllowed(core,owner)&&core.acceptsLifeAttack(owner);
    }
    public void receive(HeartBlockEntity core,EntityHitbox box,DamageSource source,float raw,net.neoforged.neoforge.common.damagesource.DamageContainer event){
        var m=active(core,box.moduleId());if(m==null||!allowed(core,source))return;
        currentTick(core);
        var direct=source.getDirectEntity();Object key;
        if(BodyExplosionDamage.routedTerrainSource(source,claimTick))return; // Explicit spell damage already owns this visual/terrain explosion.
        if(explosion(source))key=source;
        else if(direct!=null&&ExternalProjectileDamageSource.identifiesProjectile(source)){
            var data=direct.getPersistentData();var seen=data.getCompound("SableLifeHitCreatures");String id=core.creatureId().toString();if(seen.getBoolean(id)||pending.containsKey(direct.getUUID()))return;
            // One projectile uses its first effective receiver, even if that part is fully immune.
            key=direct.getUUID();
        }else key=new Attack(direct==null?null:direct.getUUID(),source.getEntity()==null?null:source.getEntity().getUUID(),source.typeHolder(),core.getLevel().getGameTime());
        if(settled.contains(key))return;
        if(!explosion(source)){
            if(Boolean.TRUE.equals(bodyClaims.get(identity(source))))return;
            if(direct!=null&&direct.getPersistentData().getCompound("SableLifeHitCreatures").getBoolean(core.creatureId().toString()))return;
        }
        var a=m.anchors().getFirst();var part=a.position(a.body(core));float damage=ModuleArmor.damage(core,part,raw);if(!Float.isFinite(damage)||damage<0)return;
        var previous=pending.get(key);boolean melee=source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)||source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK)||source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK_NO_AGGRO);
        boolean retaliates=m.value("hitboxRetaliateAgainstWhitelisted")==1;
        if(previous==null||previous.block==null&&!melee&&damage>previous.damage)pending.put(key,new Hit(source,damage,raw,box,event,null,part,retaliates));
        else if(previous.receiver==null&&previous.block==null)pending.put(key,new Hit(source,previous.damage,previous.raw,box,event,null,previous.part,false));
    }
    /** Native non-projectile contact attacks share the victim's hurt window across all parts.
     * Separate attackers/types remain independent; real projectiles and blasts retain their existing identities.
     * Vanilla accepts only the raw increase during a window, before applying the selected part's armor.
     */
    private float acceptedFraction(Hit hit){
        var source=hit.source;if(source.getDirectEntity() instanceof Projectile||explosion(source)||source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN))return 1;
        var attacker=source.getEntity();var key=new Resistance(attacker==null?identity(source):attacker.getUUID(),source.typeHolder());
        var previous=hurtWindows.get(key);
        if(previous!=null){
            if(hit.raw<=previous.raw)return 0;
            hurtWindows.put(key,new Window(previous.until,hit.raw));return (hit.raw-previous.raw)/hit.raw;
        }
        int ticks=hit.event==null?20:hit.event.getPostAttackInvulnerabilityTicks();
        if(ticks>10){
            // Bound adversarial unique-source traffic without retaining Entity references or saved state.
            if(hurtWindows.size()>=256)hurtWindows.remove(hurtWindows.keySet().iterator().next());
            hurtWindows.put(key,new Window(claimTick+(long)ticks-10,hit.raw));
        }
        return 1;
    }
    public void flush(HeartBlockEntity core){
        currentTick(core);if(pending.isEmpty())return;
        var hits=new ArrayList<>(pending.entrySet());pending.clear();if(core.isRemoved()||core.health().state()!=HeartState.ALIVE)return;
        for(var entry:hits){if(core.health().state()!=HeartState.ALIVE)break;var hit=entry.getValue();if(!settled.add(entry.getKey()))continue;var direct=hit.source.getDirectEntity();boolean blast=entry.getKey() instanceof DamageSource;
            if(!blast){UUID identity=identity(hit.source);if(Boolean.TRUE.equals(bodyClaims.get(identity)))continue;if(identity!=null)bodyClaims.put(identity,false);}
            if(direct!=null&&!blast&&ExternalProjectileDamageSource.identifiesProjectile(hit.source)){var data=direct.getPersistentData();var seen=data.getCompound("SableLifeHitCreatures");var id=core.creatureId().toString();if(seen.getBoolean(id))continue;if(seen.getAllKeys().size()>=64)continue;seen.putBoolean(id,true);data.put("SableLifeHitCreatures",seen);}
            float fraction=acceptedFraction(hit);if(fraction<=0)continue;
            float before=core.health().current();boolean applied=core.damageFromHit(hit.part,hit.damage*fraction);float actual=Math.max(0,before-core.health().current());
            if(applied){
                if(hit.retaliates && actual>0 && core.health().state()==HeartState.ALIVE) rememberAttacker(core,hit.source);
                if(hit.block!=null)BodyCombat.feedback((ServerLevel)core.getLevel(),hit.block,core,actual,hit.source.getEntity() instanceof net.minecraft.world.entity.player.Player player?player:null,false);
                if(hit.receiver!=null&&actual>0){var attacker=hit.source.getEntity() instanceof net.minecraft.world.entity.player.Player p?p:direct instanceof Projectile projectile&&projectile.getOwner() instanceof net.minecraft.world.entity.player.Player p?p:null;BodyCombat.damageMessage(core,actual,attacker);}
                AttackHitCredit.record(direct,actual);if(core.health().state()!=HeartState.ALIVE)CombatEventHooks.emit((ServerLevel)core.getLevel(),hit.source,0,2,core.creatureId());}
            // Post-hit consumers (including lifesteal) see one accepted hit and actual core HP loss.
            // Suppressed aliases emit no post event; raw pre-armor receiver damage is never reported as health loss.
            if(hit.receiver!=null&&hit.event!=null){hit.event.setNewDamage(actual);net.neoforged.neoforge.common.CommonHooks.onLivingDamagePost(hit.receiver,hit.event);}
        }
    }
    /** Our life-target adapter remains one target, and resolves protection against the closest live receiver. */
    public boolean lifeHit(HeartBlockEntity core,DamageSource source,float raw){
        if(!allowed(core,source))return false;var origin=source.getSourcePosition();var candidates=selected(core);if(candidates.isEmpty())return false;
        var m=origin==null?candidates.getFirst():candidates.stream().min(Comparator.comparingDouble(v->center(core,v).distanceToSqr(origin))).orElseThrow();
        var a=m.anchors().getFirst();var part=a.position(a.body(core));return core.damageFromHit(part,ModuleArmor.damage(core,part,raw));
    }
    public static void endTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        for(var level:event.getServer().getAllLevels())for(var core:dev.sablelife.ownership.LoadedCoreIndex.all(level))core.modules().damageBoxes.flush(core);
    }
    public static void preview(net.minecraft.server.level.ServerPlayer player,HeartBlockEntity core,VirtualModule m){
        if(!List.of("valid","disabled").contains(HeartModules.status(m,core)))return;var c=center(core,m);if(c==null)return;
        var half=new Vec3(m.value("hitboxWidth")/2,m.value("hitboxHeight")/2,m.value("hitboxDepth")/2);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ModuleNetworking.BlueprintPreview(core.getLevel().dimension().location(),c.subtract(half),c.add(half),c,Vec3.ZERO));
    }
}
