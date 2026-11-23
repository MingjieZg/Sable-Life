package dev.sablelife.attack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.minecraft.world.phys.Vec3;
/** A position-based body attack; no attacking entity is fabricated. */
public final class BodyMeleeSource extends DamageSource implements CreatureAttackSource {
    private java.util.UUID owner,module;
    public BodyMeleeSource module(java.util.UUID id){module=id;return this;}
    @Override public java.util.UUID creatureModule(){return module;}
    public BodyMeleeSource owner(java.util.UUID id){owner=id;return this;}
    @Override public java.util.UUID creatureOwner(){return owner;}
    private net.minecraft.network.chat.Component killer;private final String rawName;private String playerOwner="";
    public BodyMeleeSource playerOwner(String value){playerOwner=value;killer=dev.sablelife.heart.CreatureNames.killer(rawName,value);return this;}
    @Override public String playerOwner(){return playerOwner;}
    private boolean blocked;private int blockKind;
    private final boolean disableShield;
    private final boolean suppressVanillaKnockback;
    private ShieldBlock shieldBlock;

    private record ShieldBlock(Player player, ItemStack shield, LivingShieldBlockEvent event) {}

    public BodyMeleeSource(net.minecraft.world.level.Level level,Vec3 origin,String name) {
        this(level, origin, name, false);
    }

    public BodyMeleeSource(net.minecraft.world.level.Level level,Vec3 origin,String name,boolean disableShield) {
        this(level, origin, name, disableShield, false);
    }

    public BodyMeleeSource(net.minecraft.world.level.Level level, Vec3 origin, String name, boolean disableShield,
                           boolean suppressVanillaKnockback) {
        super(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
            .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK),origin);
        rawName=name;killer=dev.sablelife.heart.CreatureNames.killer(name);
        this.disableShield = disableShield;
        this.suppressVanillaKnockback = suppressVanillaKnockback;
    }

    public boolean suppressesVanillaKnockback() {
        return suppressVanillaKnockback;
    }

    void observeShieldBlock(LivingShieldBlockEvent event) {
        if (disableShield && event.getOriginalBlock() && event.getEntity() instanceof Player player) {
            shieldBlock = new ShieldBlock(player, player.getUseItem(), event);
        }
    }

    public void disableBlockedShield() {
        tryDisableBlockedShield();
    }

    /** Read the applied block after damage handling and all shield listeners have finished. */
    public boolean tryDisableBlockedShield() {
        var block = shieldBlock;
        shieldBlock = null;
        if (block == null || block.event().isCanceled() || !block.event().getBlocked()
                || !(block.event().getDamageContainer().getBlockedDamage() > 0)) return false;
        var player = block.player();
        var item = block.shield().getItem();
        if (!player.isAlive() || player.getUseItem() != block.shield() || !player.isBlocking()
                || player.getCooldowns().isOnCooldown(item)) return false;
        player.disableShield();
        return player.getCooldowns().isOnCooldown(item);
    }

    public void blocked(){blocked(2);}
    public void blocked(int kind){blocked=true;blockKind=kind;}
    public int blockKind(){return blockKind;}
    public boolean wasBlocked(){return blocked;}
    @Override public net.minecraft.network.chat.Component getLocalizedDeathMessage(LivingEntity victim){return ModuleDamage.deathMessage(victim,killer);}
}
