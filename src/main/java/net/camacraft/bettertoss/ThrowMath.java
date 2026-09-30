package net.camacraft.bettertoss;

import net.camacraft.camafy.charge.HoldStrain;
import net.camacraft.camafy.util.anim.Easing;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shared throw arithmetic: aim vectors, power, and the two hold-time curves that decide the
 * spread (focus for a hurried release, strain for a hold kept up too long). The spread itself is
 * Camafy's {@code AimScatter}.
 */
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

    public static float clampPitch(float pitch)
    {
        return Mth.clamp(pitch, -89.9F, 89.9F);
    }

    /** 0 = just pressed (the widest spread), 1 = fully focused, as a function of how long the button has been held. */
    public static double focus(double holdTicks)
    {
        if (!BTConfig.SERVER.focusEnabled.get()) return 1.0D;
        int need = BTConfig.SERVER.focusTicks.get();
        if (need <= 0) return 1.0D;
        return Easing.smoothstep(holdTicks / need);
    }

    /** Random spread in degrees for a given focus level. */
    public static double inaccuracyDegrees(double focus)
    {
        if (!BTConfig.SERVER.focusEnabled.get()) return 0.0D;
        return Mth.lerp(Mth.clamp(focus, 0.0D, 1.0D),
                BTConfig.SERVER.maxInaccuracyDegrees.get(), BTConfig.SERVER.minInaccuracyDegrees.get());
    }

    /**
     * 0 while a charge is freshly held, rising to 1 once it has been held too long: Camafy's shared
     * hold strain on the server's timing. The throwing hand trembles by it and the throw scatters
     * by it. 0 when strain is off, and before the (synced) server config has arrived.
     */
    public static float strain(double holdTicks)
    {
        if (!BTConfig.serverLoaded() || !BTConfig.SERVER.strainEnabled.get()) return 0.0F;
        return HoldStrain.at(holdTicks, BTConfig.SERVER.strainSteadyTicks.get(), BTConfig.SERVER.strainShakyTicks.get());
    }

    /** Extra random spread in degrees for a given strain, on top of the focus spread. */
    public static double strainInaccuracyDegrees(double strain)
    {
        if (!BTConfig.SERVER.strainEnabled.get()) return 0.0D;
        return Mth.clamp(strain, 0.0D, 1.0D) * BTConfig.SERVER.strainMaxInaccuracyDegrees.get();
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
