package net.camacraft.bettertoss.client;

import net.camacraft.bettertoss.BTConfig;
import net.minecraft.util.Mth;

/**
 * A short ring buffer of the view angles sampled every frame. At release time it answers "did the
 * mouse just flick, and which way?": the motion over the last window must clear a size threshold
 * AND be a sudden increase over the window before it, so a smooth pan never reads as a flick.
 */
public final class FlickTracker
{
    public record Flick(float yaw, float pitch)
    {
        public static final Flick NONE = new Flick(0.0F, 0.0F);

        public boolean isZero()
        {
            return yaw == 0.0F && pitch == 0.0F;
        }
    }

    private static final int CAPACITY = 128;

    private final long[] times = new long[CAPACITY];
    private final float[] yaws = new float[CAPACITY];
    private final float[] pitches = new float[CAPACITY];
    /** Next write slot. */
    private int head;
    private int size;

    public void sample(long timeMs, float yaw, float pitch)
    {
        times[head] = timeMs;
        yaws[head] = yaw;
        pitches[head] = pitch;
        head = (head + 1) % CAPACITY;
        if (size < CAPACITY) size++;
    }

    public void clear()
    {
        size = 0;
        head = 0;
    }

    /** Index of the i-th sample counting from the newest (0 = newest). */
    private int fromNewest(int i)
    {
        return ((head - 1 - i) % CAPACITY + CAPACITY) % CAPACITY;
    }

    /** The flick (in degrees, already scaled by the configured strength) ending at the given view angles. */
    public Flick measure(long nowMs, float yaw, float pitch)
    {
        BTConfig.Client cfg = BTConfig.CLIENT;
        float strength = cfg.flickStrength.get().floatValue();
        if (size == 0 || strength <= 0.0F) return Flick.NONE;
        long window = Math.max(10L, cfg.flickWindowMs.get());
        float threshold = cfg.flickThresholdDegrees.get().floatValue();
        float acceleration = cfg.flickAccelerationDegrees.get().floatValue();

        // The reference sample is the newest one at least a full window old (or the oldest we
        // have); the sample before that is the newest one at least two windows old.
        int recent = -1;
        int previous = -1;
        for (int i = 0; i < size; i++) {
            int idx = fromNewest(i);
            long age = nowMs - times[idx];
            if (recent < 0 && age >= window) recent = idx;
            if (age >= 2L * window) {
                previous = idx;
                break;
            }
        }
        if (recent < 0) recent = fromNewest(size - 1);
        long span = Math.max(1L, nowMs - times[recent]);
        if (span < window / 3) return Flick.NONE; // not enough history to judge

        float dYaw = Mth.wrapDegrees(yaw - yaws[recent]);
        float dPitch = pitch - pitches[recent];
        // Normalise to "degrees per window" so a low frame rate doesn't inflate the reading.
        float scale = (float) window / (float) span;
        dYaw *= scale;
        dPitch *= scale;
        float magnitude = (float) Math.hypot(dYaw, dPitch);

        float previousMagnitude = 0.0F;
        if (previous >= 0) {
            long prevSpan = Math.max(1L, times[recent] - times[previous]);
            float prevScale = (float) window / (float) prevSpan;
            previousMagnitude = (float) Math.hypot(Mth.wrapDegrees(yaws[recent] - yaws[previous]) * prevScale,
                    (pitches[recent] - pitches[previous]) * prevScale);
        }

        if (magnitude < threshold || magnitude - previousMagnitude < acceleration) return Flick.NONE;
        return new Flick(dYaw * strength, dPitch * strength);
    }
}
