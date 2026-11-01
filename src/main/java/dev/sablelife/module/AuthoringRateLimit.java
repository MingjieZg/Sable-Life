package dev.sablelife.module;

import net.minecraft.world.entity.player.Player;
import java.util.Map;
import java.util.WeakHashMap;

/** Main-thread token bucket; weak keys do not retain disconnected players or old worlds. */
public final class AuthoringRateLimit {
    private static final class Bucket { long tick; int tokens = 80; }
    private static final Map<Player, Bucket> buckets = new WeakHashMap<>();
    public static boolean allow(Player player, int cost) {
        var bucket = buckets.computeIfAbsent(player, ignored -> new Bucket());
        long now = player.level().getGameTime();
        if (now < bucket.tick) bucket.tokens = 80;
        else bucket.tokens = (int)Math.min(80, bucket.tokens + Math.min(20, now - bucket.tick) * 4);
        bucket.tick = now;
        if (cost < 1 || cost > bucket.tokens) return false;
        bucket.tokens -= cost;
        return true;
    }
    private AuthoringRateLimit() {}
}
