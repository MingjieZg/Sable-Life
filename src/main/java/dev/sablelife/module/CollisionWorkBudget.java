package dev.sablelife.module;

import dev.sablelife.SableLifeServerConfig;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared collision work bound; callers defer movement rather than treating exhaustion as terrain. */
public final class CollisionWorkBudget {
    public static final class Budget {
        private long tick=Long.MIN_VALUE;
        private int remaining;
        private boolean exhausted;
        public boolean spend(int count){if(count>remaining){exhausted=true;return false;}remaining-=count;return true;}
        public void refund(int count){remaining+=count;}
    }
    private static final Map<ServerLevel,Budget> budgets=new WeakHashMap<>();
    public static Budget begin(ServerLevel level){
        var b=budgets.computeIfAbsent(level,k->new Budget());
        if(b.tick!=level.getGameTime()){b.tick=level.getGameTime();b.remaining=SableLifeServerConfig.collisionChecksPerTick();}
        b.exhausted=false;return b;
    }
    public static boolean exhausted(ServerLevel level){var b=budgets.get(level);return b!=null&&b.exhausted;}
    private CollisionWorkBudget(){}
}
