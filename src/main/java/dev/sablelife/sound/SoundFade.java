package dev.sablelife.sound;

/** Fade occupies the end of the playback limit; zero limit leaves natural completion unchanged. */
public final class SoundFade {
    public static float gain(long elapsed, int duration, int fade) {
        if (duration <= 0) return 1;
        if (elapsed >= duration) return 0;
        int effectiveFade = Math.min(duration, Math.max(0, fade));
        if (effectiveFade == 0 || elapsed <= duration - effectiveFade) return 1;
        return (float) Math.clamp((duration - elapsed) / (double) effectiveFade, 0, 1);
    }
    public static long stopAfter(int duration, int fade) {
        return duration > 0 ? duration : Long.MAX_VALUE;
    }
    private SoundFade() {}
}
