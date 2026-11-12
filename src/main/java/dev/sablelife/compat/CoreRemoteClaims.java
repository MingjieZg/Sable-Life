package dev.sablelife.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.List;

/** Optional claim checks for an editor opened without a physical block interaction. */
public final class CoreRemoteClaims {
    private static final String AERO_CLAIMS = "aeroclaims";
    private static final List<String> OTHER_PROTECTIONS = List.of(
            "openpartiesandclaims", "ftbchunks", "ftbchunksaerospace", "create_aeronautics_ftb_chunks");

    private static final class Installed {
        private static final boolean UNSUPPORTED = OTHER_PROTECTIONS.stream().anyMatch(ModList.get()::isLoaded);
        private static final boolean AERO = ModList.get().isLoaded(AERO_CLAIMS);
    }

    private static final class AeroClaims {
        private static final Access ACCESS = resolve();

        private static Access resolve() {
            var mod = ModList.get().getModContainerById(AERO_CLAIMS);
            if (mod.isEmpty() || !mod.get().getModInfo().getVersion().toString().equals("0.9.5")) return null;
            try {
                var loader = CoreRemoteClaims.class.getClassLoader();
                var manager = Class.forName("com.mapter.aeroclaims.claim.ClaimManager", false, loader);
                var claim = Class.forName("com.mapter.aeroclaims.claim.Claim", false, loader);
                var resolver = Class.forName("com.mapter.aeroclaims.permission.ClaimPermissionResolver", false, loader);
                var findClaim = manager.getMethod("getClaimAtWithMargin", ServerLevel.class, BlockPos.class);
                var getResolver = manager.getMethod("getPermissionResolver");
                var canAccess = resolver.getMethod("canAccess", ServerPlayer.class, claim);
                if (findClaim.getReturnType() != claim || getResolver.getReturnType() != resolver
                        || canAccess.getReturnType() != boolean.class) return null;
                return new Access(findClaim, getResolver, canAccess);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                LogUtils.getLogger().warn("Could not connect the Core Remote to AeroClaims protection", error);
                return null;
            }
        }

        private record Access(Method findClaim, Method getResolver, Method canAccess) {
            boolean allows(ServerPlayer player, BlockPos pos) throws ReflectiveOperationException {
                var claim = findClaim.invoke(null, player.serverLevel(), pos);
                return claim == null || (boolean) canAccess.invoke(getResolver.invoke(null), player, claim);
            }
        }
    }

    public static boolean allows(ServerPlayer player, BlockPos pos) {
        if (Installed.UNSUPPORTED) return false;
        if (!Installed.AERO) return true;
        var access = AeroClaims.ACCESS;
        if (access == null) return false;
        try {
            return access.allows(player, pos);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            return false;
        }
    }

    private CoreRemoteClaims() {}
}
