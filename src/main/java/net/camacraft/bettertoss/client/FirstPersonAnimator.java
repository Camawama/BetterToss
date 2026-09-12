package net.camacraft.bettertoss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.camacraft.bettertoss.BTConfig;
import net.camacraft.bettertoss.BetterTossMod;
import net.camacraft.bettertoss.ThrowMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * First-person feel: the charging hand pulls up and back and trembles while the aim is unsteady,
 * whips forward on release; and a caught projectile is drawn in the main hand in place of whatever
 * that hand nominally holds. All visual, all read from the local throw state.
 */
@Mod.EventBusSubscriber(modid = BetterTossMod.MODID, value = Dist.CLIENT)
public final class FirstPersonAnimator
{
    /**
     * Vanilla renders both hands inside ONE push/pop, main hand first, so a transform applied for
     * the main hand would still be on the stack when the off hand draws. Every transform applied
     * to an un-cancelled main-hand render is recorded here and undone when the off hand's event
     * arrives (or discarded at the next frame if the off hand never renders).
     */
    private static final List<Runnable> UNDO = new ArrayList<>();

    private FirstPersonAnimator() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event)
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;
        float pt = event.getPartialTick();

        if (mainHand) {
            UNDO.clear();
        } else if (!UNDO.isEmpty()) {
            for (int i = UNDO.size() - 1; i >= 0; i--) UNDO.get(i).run();
            UNDO.clear();
        }

        if (mainHand && ClientCatchState.localHolding()) {
            ItemStack display = ClientCatchState.display(player);
            if (!display.isEmpty()) {
                renderCaught(mc, player, event, display);
                event.setCanceled(true);
                return;
            }
        }
        if (!BTConfig.CLIENT.firstPersonAnimations.get()) return;
        applyThrowAnim(event.getPoseStack(), player, mainHand, pt, mainHand);
    }

    private static void translate(PoseStack pose, double x, double y, double z, boolean record)
    {
        pose.translate(x, y, z);
        if (record) UNDO.add(() -> pose.translate(-x, -y, -z));
    }

    private static void rotate(PoseStack pose, Axis axis, float degrees, boolean record)
    {
        pose.mulPose(axis.rotationDegrees(degrees));
        if (record) UNDO.add(() -> pose.mulPose(axis.rotationDegrees(-degrees)));
    }

    /** Vanilla's ordinary held-item arm transform, then the item of the caught projectile. */
    private static void renderCaught(Minecraft mc, LocalPlayer player, RenderHandEvent event, ItemStack display)
    {
        PoseStack pose = event.getPoseStack();
        HumanoidArm arm = player.getMainArm();
        boolean right = arm == HumanoidArm.RIGHT;
        float i = right ? 1.0F : -1.0F;
        float swing = event.getSwingProgress();
        float equip = event.getEquipProgress();
        float pi = (float) Math.PI;

        pose.pushPose();
        float f5 = -0.4F * Mth.sin(Mth.sqrt(swing) * pi);
        float f6 = 0.2F * Mth.sin(Mth.sqrt(swing) * (pi * 2.0F));
        float f10 = -0.2F * Mth.sin(swing * pi);
        pose.translate(i * f5, f6, f10);
        pose.translate(i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        float f = Mth.sin(swing * swing * pi);
        pose.mulPose(Axis.YP.rotationDegrees(i * (45.0F + f * -20.0F)));
        float f1 = Mth.sin(Mth.sqrt(swing) * pi);
        pose.mulPose(Axis.ZP.rotationDegrees(i * f1 * -20.0F));
        pose.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
        pose.mulPose(Axis.YP.rotationDegrees(i * -45.0F));
        if (BTConfig.CLIENT.firstPersonAnimations.get()) {
            applyThrowAnim(pose, player, true, event.getPartialTick(), false);
        }
        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, display,
                right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !right, pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    /**
     * Wind-up (with tremble) while charging with this hand, and the forward whip after a release.
     * With record set, every transform is remembered so it can be undone before the off hand draws.
     */
    private static void applyThrowAnim(PoseStack pose, LocalPlayer player, boolean mainHand, float pt, boolean record)
    {
        HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float t = player.tickCount + pt;

        // These run BEFORE vanilla places the hand at (0.56, -0.52, -0.72), so a rotation here
        // orbits the whole hand around the camera: positive X lifts it up the screen (and slightly
        // away), negative X drops it down and closer. Keep angles moderate or the hand leaves view.
        float whip = ClientThrowing.throwWhip(pt, mainHand);
        if (whip > 0.0F) {
            float arc = Mth.sin(whip * (float) Math.PI);
            translate(pose, 0.0D, -0.02D * arc, -0.18D * arc, record);
            rotate(pose, Axis.XP, -22.0F * arc, record);
            rotate(pose, Axis.ZP, side * -6.0F * arc, record);
        }

        if (ClientThrowing.isCharging() && (ClientThrowing.hand() == InteractionHand.MAIN_HAND) == mainHand) {
            float c = (float) ThrowMath.smoothstep((ClientThrowing.chargeTicks() + pt) / 5.0D);
            float focus = ClientThrowing.focus(pt);
            float shake = BTConfig.CLIENT.handShake.get() ? (1.0F - focus) * 2.0F : 0.0F;
            float tx = Mth.sin(t * 2.3F) * shake + Mth.sin(t * 3.9F + 1.3F) * shake * 0.5F;
            float tz = Mth.sin(t * 2.9F + 0.7F) * shake * 0.7F;
            // Cocked: lifted up the screen, pulled in toward the centre and a touch further away.
            translate(pose, side * -0.10D * c, 0.02D * c, -0.08D * c, record);
            rotate(pose, Axis.XP, 34.0F * c + tx * c, record);
            rotate(pose, Axis.ZP, side * -8.0F * c + tz * c, record);
            rotate(pose, Axis.YP, side * -10.0F * c, record);
        }
    }
}
