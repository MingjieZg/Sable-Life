package dev.sablelife.module;

/** A rising input starts a complete pulse; holding power never restarts it. */
public final class SignalPulse {
    private boolean previous;
    private int remaining;
    private long deadline=Long.MIN_VALUE;
    /** World-time clock: repeated publication in the same tick cannot consume pulse duration. */
    public boolean tick(long now,boolean input,boolean pulse,int duration){
        boolean rising=input&&!previous;previous=input;
        if(!pulse){deadline=Long.MIN_VALUE;return input;}
        if(rising){long ticks=Math.clamp(duration,1,12000);deadline=now>Long.MAX_VALUE-ticks?Long.MAX_VALUE:now+ticks;}
        return now<deadline;
    }
    public boolean tick(boolean input, boolean pulse, int duration) {
        boolean rising = input && !previous; previous = input;
        if (!pulse) { remaining = 0; return input; }
        if (rising) remaining = Math.clamp(duration, 1, 12000);
        if (remaining == 0) return false;
        remaining--; return true;
    }
}
