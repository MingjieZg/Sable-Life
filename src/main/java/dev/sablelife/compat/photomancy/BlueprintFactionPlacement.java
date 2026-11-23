package dev.sablelife.compat.photomancy;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/** Carries the authorized caller only through one synchronous placement session. */
public final class BlueprintFactionPlacement {
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();

    private static final class Scope {
        final ServerLevel level;
        final ServerPlayer actor;
        final boolean trustedLocal;
        Object session;

        Scope(ServerLevel level, ServerPlayer actor, boolean trustedLocal) {
            this.level = level;
            this.actor = actor;
            this.trustedLocal = trustedLocal;
        }
    }

    public static <T> T withActor(ServerLevel level, ServerPlayer actor, Supplier<T> action) {
        var scope = actor != null && actor.getServer() == level.getServer()
                ? new Scope(level, actor, false) : null;
        return withScope(scope, action);
    }

    public static <T> T withTrustedLocal(ServerLevel level, Supplier<T> action) {
        return withScope(new Scope(level, null, true), action);
    }

    private static <T> T withScope(Scope scope, Supplier<T> action) {
        var previous = ACTIVE.get();
        if (scope == null) ACTIVE.remove();
        else ACTIVE.set(scope);
        try {
            return action.get();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    static void observe(ServerLevel level, Object session) {
        var scope = ACTIVE.get();
        if (scope != null && scope.level == level && scope.session == null) scope.session = session;
    }

    static ServerPlayer actor(ServerLevel level, Object session) {
        var scope = ACTIVE.get();
        return scope != null && scope.level == level && scope.session == session ? scope.actor : null;
    }

    static boolean trustedLocal(ServerLevel level, Object session) {
        var scope = ACTIVE.get();
        return scope != null && scope.level == level && scope.session == session && scope.trustedLocal;
    }

    private BlueprintFactionPlacement() {}
}
