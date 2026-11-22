package dev.sablelife.compat.epicfight;

import net.minecraft.server.level.ServerPlayer;
import dev.sablelife.attack.BodyMeleeSource;
import net.minecraft.world.entity.LivingEntity;

/** Keep all optional Epic Fight linkage behind the verified native hooks. */
public final class EpicFightCompatibility {
    public static boolean supported(){return EpicFightMixinPlugin.supported();}
    public static boolean combatMode(ServerPlayer player){return supported()&&EpicFightBodyBridge.combatMode(player);}
    public static boolean blockEffectOnly(LivingEntity victim, BodyMeleeSource source) {
        return supported() && EpicFightBodyGuard.blockEffectOnly(victim, source);
    }

    private EpicFightCompatibility() {}
}
