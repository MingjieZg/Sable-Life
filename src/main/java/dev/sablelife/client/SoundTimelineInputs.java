package dev.sablelife.client;

/** Display units: seconds, pitch, volume, blocks, seconds. */
public final class SoundTimelineInputs {
    public static double minimum(int index) { return index==1?.5:index==3?1:0; }
    public static double maximum(int index) { return index==0||index==4?600:index==1?2:index==3?256:4; }
    public static double scroll(int index,double value,double fallback,double direction) {
        if(!Double.isFinite(value))value=fallback;
        double step=index<=2?.1:1;
        return Math.clamp(Math.round((value+Math.signum(direction)*step)*100)/100.,minimum(index),maximum(index));
    }
    private SoundTimelineInputs() {}
}
