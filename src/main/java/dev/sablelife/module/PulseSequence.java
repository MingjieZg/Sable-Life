package dev.sablelife.module;

import java.util.List;

/** Pure tick clock: a short input pulse starts the entire authored sequence. */
public final class PulseSequence {
    public static final int LIMIT = 128;
    public record Step(boolean waiting, int channel, int power, int ticks) {
        public Step { channel = Math.clamp(channel, 0, 65535); power = Math.clamp(power, 1, 15); ticks = Math.clamp(ticks, 1, 12000); }
    }
    private boolean previous, running;
    private int index, remaining;
    public boolean running() { return running; }
    public int index() { return index; }
    public int remaining() { return remaining; }
    public void reset(boolean input) { previous = input; running = false; index = remaining = 0; }
    public Step tick(List<Step> steps, boolean input, boolean reset, boolean repeat, boolean stopOnLow) {
        boolean rising = input && !previous; previous = input;
        if (reset || stopOnLow && !input || steps.isEmpty()) { reset(input); return null; }
        if (running && remaining == 0 && ++index >= steps.size()) { running = false; index = 0; }
        if (!running && (rising || repeat && input)) { running = true; index = 0; remaining = steps.getFirst().ticks(); }
        if (!running) return null;
        var step = steps.get(index);
        if (remaining == 0) remaining = step.ticks();
        remaining--; return step;
    }
}
