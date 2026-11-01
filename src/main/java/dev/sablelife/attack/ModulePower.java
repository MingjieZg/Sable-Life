package dev.sablelife.attack;

import dev.sablelife.ownership.BodyOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Vanilla redstone plus the sensor's short-range corner connection on one living body. */
public final class ModulePower {
    /** Virtual controls must not be self-powered by the adjacent core's heartbeat. */
    public static boolean hasControlSignal(ServerLevel level, BlockPos module) {
        return controlStrength(level,module)>0;
    }
    public static int controlStrength(ServerLevel level,BlockPos module){
        int power=0;
        for (var direction : net.minecraft.core.Direction.values()) {
            var neighbor = module.relative(direction);
            if (level.getBlockState(neighbor).getBlock() instanceof dev.sablelife.heart.HeartBlock) continue;
            power=Math.max(power,level.getSignal(neighbor,direction));
        }
        return Math.max(power,sensorStrength(level,module));
    }
    public static boolean hasSignal(ServerLevel level, BlockPos module) {
        return signalStrength(level,module)>0;
    }
    public static int signalStrength(ServerLevel level,BlockPos module){
        return Math.max(level.getBestNeighborSignal(module),sensorStrength(level,module));
    }
    private static int sensorStrength(ServerLevel level, BlockPos module) {
        var ownership = BodyOwnership.get(level);
        var owner = ownership.owner(module);
        if (owner == null) return 0;
        int power=0;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && y == 0 && z == 0) continue;
            BlockPos candidate = module.offset(x, y, z);
            if (owner.equals(ownership.owner(candidate)))power=Math.max(power,dev.sablelife.module.ModuleSignals.signal(level,candidate));
        }
        return power;
    }
    private ModulePower() {}
}
