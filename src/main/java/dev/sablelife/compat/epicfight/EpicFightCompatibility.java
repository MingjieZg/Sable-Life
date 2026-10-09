package dev.sablelife.compat.epicfight;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Keep all optional Epic Fight linkage behind the verified release boundary. */
public final class EpicFightCompatibility {
    public static boolean supported(){return ModList.get().getModContainerById("epicfight").map(c->c.getModInfo().getVersion().toString().equals("21.17.3.1")).orElse(false);}
    public static boolean combatMode(ServerPlayer player){return supported()&&EpicFightBodyBridge.combatMode(player);}
    private EpicFightCompatibility() {}
}
