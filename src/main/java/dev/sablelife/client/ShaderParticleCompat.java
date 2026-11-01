package dev.sablelife.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/** Optional Iris public API bridge; no Iris dependency on ordinary clients or dedicated servers. */
final class ShaderParticleCompat {
    private static final MethodHandle IN_USE = findApi();
    private ShaderParticleCompat() {}

    private static MethodHandle findApi() {
        try {
            var api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            var instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().unreflect(api.getMethod("isShaderPackInUse")).bindTo(instance);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    static boolean active() {
        if (IN_USE == null) return false;
        try {
            return (boolean) IN_USE.invokeExact();
        } catch (Throwable unavailable) {
            return false;
        }
    }
}
