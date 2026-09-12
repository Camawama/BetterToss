package net.camacraft.bettertoss;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** The shared throw arithmetic: aim vectors, spread, power and focus curves. */
public final class ThrowMath
{
    private ThrowMath() {}

    /** Unit vector for a yaw/pitch in degrees, vanilla's convention (the one shootFromRotation uses). */
    public static Vec3 direction(float yawDeg, float pitchDeg)
    {
        float yaw = yawDeg * Mth.DEG_TO_RAD;
        float pitch = pitchDeg * Mth.DEG_TO_RAD;
        float x = -Mth.sin(yaw) * Mth.cos(pitch);
        float y = -Mth.sin(pitch);
        float z = Mth.cos(yaw) * Mth.cos(pitch);
        return new Vec3(x, y, z);
    }

    public static float yawOf(Vec3 dir)
    {
        return (float) Math.toDegrees(Mth.atan2(-dir.x, dir.z));
    }

    public static float pitchOf(Vec3 dir)
    {
        double len = dir.length();
        if (len < 1.0E-8D) return 0.0F;
        return (float) Math.toDegrees(-Math.asin(Mth.clamp(dir.y / len, -1.0D, 1.0D)));
    }

    public static float clampPitch(float pitch)
    {
        return Mth.clamp(pitch, -89.9F, 89.9F);
    }

    /** Randomly bends the direction by up to the given spread (triangular distribution on each axis). */
    public static Vec3 withSpread(Vec3 dir, RandomSource random, double degrees)
    {
        if (degrees <= 0.0D) return dir;
        float yaw = yawOf(dir) + (float) random.triangle(0.0D, degrees);
        float pitch = clampPitch(pitchOf(dir) + (float) random.triangle(0.0D, degrees));
        return direction(yaw, pitch);
    }

    public static double smoothstep(double t)
    {
        t = Mth.clamp(t, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    /** 0 = just pressed, 1 = fully steadied, as a function of how long the button has been held. */
    public static double focus(double holdTicks)
    {
        if (!BTConfig.SERVER.focusEnabled.get()) return 1.0D;
        int need = BTConfig.SERVER.focusTicks.get();
        if (need <= 0) return 1.0D;
        return smoothstep(holdTicks / need);
    }

    /** Random spread in degrees for a given focus level. */
    public static double inaccuracyDegrees(double focus)
    {
        if (!BTConfig.SERVER.focusEnabled.get()) return 0.0D;
        return Mth.lerp(Mth.clamp(focus, 0.0D, 1.0D),
                BTConfig.SERVER.maxInaccuracyDegrees.get(), BTConfig.SERVER.minInaccuracyDegrees.get());
    }

    /** Velocity multiplier (vs. the item's vanilla throw) for a 0..1 bar value. */
    public static double powerMultiplier(float power)
    {
        return Mth.lerp(Mth.clamp(power, 0.0F, 1.0F),
                BTConfig.SERVER.minPowerMultiplier.get(), BTConfig.SERVER.maxPowerMultiplier.get());
    }

    /**
     * Speed multiplier for flinging a stack of the given count: a single item flies at full speed, a
     * stack at the reference count (64 by default) at the configured fraction, in between on a curve.
     */
    public static double stackWeightMultiplier(int count)
    {
        BTConfig.Server cfg = BTConfig.SERVER;
        if (!cfg.stackWeightEnabled.get() || count <= 1) return 1.0D;
        int reference = Math.max(2, cfg.stackWeightReference.get());
        double heft = Mth.clamp((count - 1) / (double) (reference - 1), 0.0D, 1.0D);
        heft = Math.pow(heft, cfg.stackWeightCurve.get());
        return Mth.lerp(heft, 1.0D, cfg.fullStackSpeedMultiplier.get());
    }
}
