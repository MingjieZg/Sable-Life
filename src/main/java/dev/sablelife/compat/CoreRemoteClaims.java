package dev.sablelife.compat;

import com.mojang.logging.LogUtils;
import dev.sablelife.SableLifeServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Optional claim checks for an editor opened without a physical block interaction. */
public final class CoreRemoteClaims {
    private static final String AERO_CLAIMS = "aeroclaims";
    private static final List<String> OTHER_PROTECTIONS = List.of(
            "openpartiesandclaims", "ftbchunks", "ftbchunksaerospace", "create_aeronautics_ftb_chunks");

    private static final class Installed {
        private static final List<String> UNSUPPORTED_IDS = OTHER_PROTECTIONS.stream().filter(ModList.get()::isLoaded).toList();
        private static final String UNSUPPORTED_NAMES = String.join(", ", UNSUPPORTED_IDS);
        private static final boolean UNSUPPORTED = !UNSUPPORTED_IDS.isEmpty();
        private static final boolean AERO = ModList.get().isLoaded(AERO_CLAIMS);
    }

    private static final class AeroClaims {
        private static final Access ACCESS = resolve();

        private static Access resolve() {
            if (!ModVersion.atLeast(aeroClaimsVersion(), "0.9.5")) return null;
            try {
                var loader = CoreRemoteClaims.class.getClassLoader();
                var manager = Class.forName("com.mapter.aeroclaims.claim.ClaimManager", false, loader);
                var claim = Class.forName("com.mapter.aeroclaims.claim.Claim", false, loader);
                var resolver = Class.forName("com.mapter.aeroclaims.permission.ClaimPermissionResolver", false, loader);
                var findClaim = manager.getMethod("getClaimAtWithMargin", ServerLevel.class, BlockPos.class);
                var getResolver = manager.getMethod("getPermissionResolver");
                var canAccess = resolver.getMethod("canAccess", ServerPlayer.class, claim);
                if (findClaim.getReturnType() != claim || getResolver.getReturnType() != resolver
                        || canAccess.getReturnType() != boolean.class
                        || !Modifier.isStatic(findClaim.getModifiers()) || !findClaim.canAccess(null)
                        || !Modifier.isStatic(getResolver.getModifiers()) || !getResolver.canAccess(null)
                        || Modifier.isStatic(canAccess.getModifiers())
                        || !Modifier.isPublic(canAccess.getDeclaringClass().getModifiers())) return null;
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

    public enum Denial {
        UNSUPPORTED_PROTECTION, UNSUPPORTED_AEROCLAIMS, CLAIM_DENIED, CHECK_FAILED
    }

    public static boolean allows(ServerPlayer player, BlockPos pos) {
        return denial(player, pos) == null;
    }

    /** Null means allowed. Reporting the cause does not bypass any protection check. */
    public static Denial denial(ServerPlayer player, BlockPos pos) {
        boolean allowUnsupported = SableLifeServerConfig.allowCoreRemoteWithUnsupportedClaims();
        if (Installed.UNSUPPORTED && !allowUnsupported) return Denial.UNSUPPORTED_PROTECTION;
        if (!Installed.AERO) return null;
        var access = AeroClaims.ACCESS;
        if (access == null) return allowUnsupported ? null : Denial.UNSUPPORTED_AEROCLAIMS;
        try {
            return access.allows(player, pos) ? null : Denial.CLAIM_DENIED;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            return Denial.CHECK_FAILED;
        }
    }

    public static String unsupportedProtectionIds() { return Installed.UNSUPPORTED_NAMES; }

    public static String aeroClaimsVersion() {
        return ModList.get().getModContainerById(AERO_CLAIMS)
                .map(mod -> mod.getModInfo().getVersion().toString()).orElse("");
    }

    private CoreRemoteClaims() {}
}
