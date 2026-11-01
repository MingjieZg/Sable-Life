package dev.sablelife.module;

import dev.sablelife.SableLifeServerConfig;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared cosmetic-only budget. It never changes damage, movement or action timelines. */
public final class VisualBudget {
    private static final class Budget { java.lang.ref.WeakReference<Object> level; long tick=Long.MIN_VALUE; int remaining; }
    private static final Map<ServerPlayer,Budget> budgets=new WeakHashMap<>();
    public static boolean allow(ServerPlayer player,int points) {
        var budget=budgets.computeIfAbsent(player,ignored->new Budget());
        long now=player.level().getGameTime();
        if(budget.level==null||budget.level.get()!=player.level()||budget.tick!=now){budget.level=new java.lang.ref.WeakReference<>(player.level());budget.tick=now;budget.remaining=SableLifeServerConfig.particlePointsPerPlayerTick();}
        points=Math.max(1,points);if(points>budget.remaining)return false;
        budget.remaining-=points;return true;
    }
    private VisualBudget(){}
}
