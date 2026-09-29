package net.camacraft.bettertoss.client;

import net.camacraft.bettertoss.BTConfig;
import net.camacraft.bettertoss.ThrowMath;
import net.camacraft.camafy.util.anim.Easing;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Third-person arm poses, registered as Forge extensible ArmPoses so they slot into the vanilla
 * humanoid model exactly where BOW_AND_ARROW or THROW_SPEAR would, and stay compatible with other
 * mods that touch the model afterwards.
 *
 * Wind-up: the throwing arm is cocked up and back over the shoulder (swaying while the aim is
 * unsteady, if the viewer has handShake on), the torso twisted so the shoulder pulls back. Throw: the arm whips forward and down as
 * the torso untwists, then everything eases back to the walk. Hold: a caught projectile is carried
 * chest-high in front, ready.
 */
public final class ThrowPoses
{
    public static final int THROW_TICKS = ClientThrowing.THROW_ANIM_TICKS;

    private static final float WINDUP_X = -3.25F;
    private static final float FOLLOW_X = -0.8F;

    private static HumanoidModel.ArmPose charge;
    private static HumanoidModel.ArmPose throwPose;
    private static HumanoidModel.ArmPose hold;
    private static boolean initialized;

    private ThrowPoses() {}

    public static void init()
    {
        if (initialized) return;
        initialized = true;
        charge = HumanoidModel.ArmPose.create("BETTERTOSS_CHARGE", false, ThrowPoses::applyCharge);
        throwPose = HumanoidModel.ArmPose.create("BETTERTOSS_THROW", false, ThrowPoses::applyThrow);
        hold = HumanoidModel.ArmPose.create("BETTERTOSS_HOLD", false, ThrowPoses::applyHold);
    }

    /** Called before a player model is set up: picks the pose for whatever that player is doing. */
    public static void applyTo(Player player, HumanoidModel<?> model)
    {
        if (!initialized) return;
        ClientCatchState.AnimState state = ClientCatchState.anim(player.getId(), false);
        if (state == null) return;
        double now = ClientCatchState.now();
        boolean throwing = !state.charging && now - state.throwStart <= THROW_TICKS;
        HumanoidArm mainArm = player.getMainArm();
        if (state.charging || throwing) {
            HumanoidArm arm = state.offHand ? mainArm.getOpposite() : mainArm;
            set(model, arm, state.charging ? charge : throwPose);
        } else if (state.holding) {
            set(model, mainArm, hold);
        }
    }

    private static void set(HumanoidModel<?> model, HumanoidArm arm, HumanoidModel.ArmPose pose)
    {
        if (arm == HumanoidArm.RIGHT) model.rightArmPose = pose;
        else model.leftArmPose = pose;
    }

    private static void applyCharge(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm)
    {
        ClientCatchState.AnimState state = ClientCatchState.anim(entity.getId(), false);
        if (state == null) return;
        double now = ClientCatchState.now();
        double held = now - state.chargeStart;
        float blend = (float) Easing.smoothstep(held / 5.0D);
        float focus = (float) ThrowMath.focus(held);
        // The same slow sway as the first-person hand, and like it off unless handShake is on.
        float tremble = !BTConfig.CLIENT.handShake.get() ? 0.0F : (1.0F - focus) * 0.02F
                * (Mth.sin((float) now * 0.5F) + 0.5F * Mth.sin((float) now * 0.8F + 1.1F));
        poseWindup(model, arm, blend, tremble);
    }

    private static void applyThrow(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm)
    {
        ClientCatchState.AnimState state = ClientCatchState.anim(entity.getId(), false);
        if (state == null) return;
        double t = (ClientCatchState.now() - state.throwStart) / THROW_TICKS;
        if (t < 0.0D || t > 1.0D) return;
        float s = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        // The whip: fast ease-in from the wind-up to the follow-through over the first 40%...
        float a = (float) Mth.clamp(t / 0.4D, 0.0D, 1.0D);
        float ease = a * a;
        float x = Mth.lerp(ease, WINDUP_X, FOLLOW_X);
        float bodyYaw = Mth.lerp(ease, 0.4F, -0.3F) * s;
        // ...then the pose relaxes back onto the ordinary walk over the rest.
        float weight = t < 0.4D ? 1.0F : 1.0F - (float) Easing.smoothstep((t - 0.4D) / 0.6D);
        part.xRot = Mth.lerp(weight, part.xRot, x);
        part.yRot = Mth.lerp(weight, part.yRot, 0.1F * s);
        part.zRot = Mth.lerp(weight, part.zRot, 0.1F * s);
        twistBody(model, bodyYaw * weight);
    }

    private static void applyHold(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm)
    {
        ClientCatchState.AnimState state = ClientCatchState.anim(entity.getId(), false);
        if (state == null) return;
        float blend = (float) Easing.smoothstep((ClientCatchState.now() - state.holdStart) / 4.0D);
        float s = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float itemX = part.xRot * 0.5F - ((float) Math.PI / 10F); // vanilla's held-item pose
        part.xRot = Mth.lerp(blend, itemX, -1.05F);
        part.yRot = Mth.lerp(blend, part.yRot, -0.12F * s);
        part.zRot = Mth.lerp(blend, part.zRot, 0.05F * s);
    }

    private static void poseWindup(HumanoidModel<?> model, HumanoidArm arm, float weight, float tremble)
    {
        float s = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        part.xRot = Mth.lerp(weight, part.xRot, WINDUP_X + tremble);
        part.yRot = Mth.lerp(weight, part.yRot, 0.25F * s);
        part.zRot = Mth.lerp(weight, part.zRot, 0.12F * s + tremble * 0.5F);
        twistBody(model, 0.4F * s * weight);
    }

    /** Turns the torso and keeps both shoulders attached to it, the way vanilla's swing does. */
    private static void twistBody(HumanoidModel<?> model, float yaw)
    {
        model.body.yRot = yaw;
        model.rightArm.z = Mth.sin(yaw) * 5.0F;
        model.rightArm.x = -Mth.cos(yaw) * 5.0F;
        model.leftArm.z = -Mth.sin(yaw) * 5.0F;
        model.leftArm.x = Mth.cos(yaw) * 5.0F;
        model.rightArm.yRot += yaw;
        model.leftArm.yRot += yaw;
    }
}
