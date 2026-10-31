package dev.sablelife.attack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
/** A position-based body attack; no attacking entity is fabricated. */
public final class BodyMeleeSource extends DamageSource implements CreatureAttackSource {
    private java.util.UUID owner,module;
    public BodyMeleeSource module(java.util.UUID id){module=id;return this;}
    @Override public java.util.UUID creatureModule(){return module;}
    public BodyMeleeSource owner(java.util.UUID id){owner=id;return this;}
    @Override public java.util.UUID creatureOwner(){return owner;}
    private final net.minecraft.network.chat.Component killer;
    private boolean blocked;private int blockKind;
    public BodyMeleeSource(net.minecraft.world.level.Level level,Vec3 origin,String name) {
        super(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
            .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK),origin);
        killer=dev.sablelife.heart.CreatureNames.killer(name);
    }
    public void blocked(){blocked(2);}
    public void blocked(int kind){blocked=true;blockKind=kind;}
    public int blockKind(){return blockKind;}
    public boolean wasBlocked(){return blocked;}
    @Override public net.minecraft.network.chat.Component getLocalizedDeathMessage(LivingEntity victim){return ModuleDamage.deathMessage(victim,killer);}
}
