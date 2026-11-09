package dev.sablelife.attack;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Module attacks apply their own cadence rather than inheriting vanilla hurt immunity. */
public final class ModuleDamage {
    /** Physical module hits use ordinary armor/protection rules, unlike vanilla generic/magic. */
    public static DamageSource physical(net.minecraft.world.level.Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK));
    }
    /** Boss-authored blast damage stays configured even in Peaceful; armor and blast protection still apply. */
    public static DamageSource blast(net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity projectile) {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sablelife", "module_blast"));
        return new DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key), projectile, null);
    }
    public static boolean hurt(LivingEntity target, DamageSource source, float amount,dev.sablelife.heart.HeartBlockEntity heart,dev.sablelife.module.VirtualModule module){
        source=named(source,heart);if(source instanceof BodyMeleeSource body)body.module(module.id());else source=new NamedSource(source,heart.creatureName(),heart.creatureId(),module.id(),heart.relations().ownerName());
        float before=target.getHealth();boolean hit=hurt(target,source,amount);
        if(source instanceof BodyMeleeSource body&&body.wasBlocked())CombatEventHooks.blocked(target,body);if(hit)heart.modules().hitCounter.record(heart,module.id(),Math.max(0,before-target.getHealth()));return hit;
    }
    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        if(target instanceof dev.sablelife.module.ModuleTargets.LifeTarget life&&source instanceof CreatureAttackSource attack&&!dev.sablelife.heart.CoreRelations.damageAllowed(life.core(),attack.creatureOwner()))return false;
        if(dev.sablelife.combat.OwnerPlayers.protectedFrom(target,source))return false;
        int previous = target.invulnerableTime;
        target.invulnerableTime = 0;
        try {
            float before=target.getHealth();boolean hit=target.hurt(source, amount);if(hit)AttackHitCredit.record(source.getDirectEntity(),Math.max(0,before-target.getHealth()));return hit;
        } finally {
            if (target.isAlive()) target.invulnerableTime = Math.max(previous, target.invulnerableTime);
        }
    }
    public static final String ATTACK_NAME="SableLifeAttackName",ATTACK_PLAYER_OWNER="SableLifePlayerOwner";
    public static String playerOwner(DamageSource source){if(source instanceof CreatureAttackSource c&&!c.playerOwner().isEmpty())return c.playerOwner();var direct=source.getDirectEntity();return direct==null?"":direct.getPersistentData().getString(ATTACK_PLAYER_OWNER);}
    /** Keep native damage type, entities and source position; customize only death attribution. */
    public static DamageSource named(DamageSource source, dev.sablelife.heart.HeartBlockEntity heart){
        if(source instanceof BodyMeleeSource body)return body.owner(heart.creatureId()).playerOwner(heart.relations().ownerName());
        return new NamedSource(source,heart.creatureName(),heart.creatureId(),source instanceof CreatureAttackSource c?c.creatureModule():null,heart.relations().ownerName());
    }
    public static DamageSource named(DamageSource source,String raw){
        return new NamedSource(source,raw,source instanceof CreatureAttackSource owner?owner.creatureOwner():null);
    }
    private static final class NamedSource extends DamageSource implements CreatureAttackSource {
        private final java.util.UUID owner,module;private final String playerOwner;private final net.minecraft.network.chat.Component killer;
        private NamedSource(DamageSource source,String name,java.util.UUID owner){this(source,name,owner,source instanceof CreatureAttackSource c?c.creatureModule():null);}
        private NamedSource(DamageSource source,String name,java.util.UUID owner,java.util.UUID module){this(source,name,owner,module,ModuleDamage.playerOwner(source));}
        private NamedSource(DamageSource source,String name,java.util.UUID owner,java.util.UUID module,String playerOwner){super(source.typeHolder(),source.getDirectEntity(),source.getEntity(),source.sourcePositionRaw());this.owner=owner;this.module=module;this.playerOwner=playerOwner;killer=dev.sablelife.heart.CreatureNames.killer(name,playerOwner);}
        @Override public String playerOwner(){return playerOwner;}
        @Override public java.util.UUID creatureOwner(){return owner;}
        @Override public java.util.UUID creatureModule(){return module;}
        @Override public net.minecraft.network.chat.Component getLocalizedDeathMessage(LivingEntity victim){return deathMessage(victim,killer);}
    }
    public static net.minecraft.network.chat.Component deathMessage(LivingEntity victim,net.minecraft.network.chat.Component killer){
        return net.minecraft.network.chat.Component.translatable("death.attack.sablelife.creature",victim.getDisplayName(),killer.copy());
    }
    public static void nameProjectile(net.minecraft.world.entity.Entity projectile,dev.sablelife.heart.HeartBlockEntity heart){projectile.getPersistentData().putString(ATTACK_NAME,heart.creatureName());projectile.getPersistentData().putString(ATTACK_PLAYER_OWNER,heart.relations().ownerName());}
    private ModuleDamage() {}
}
