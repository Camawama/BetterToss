package net.camacraft.bettertoss.client;

import net.camacraft.bettertoss.BTConfig;
import net.camacraft.bettertoss.BetterTossMod;
import net.camacraft.bettertoss.ThrowMode;
import net.camacraft.bettertoss.Throwables;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = BetterTossMod.MODID, value = Dist.CLIENT)
public final class ClientEvents
{
    private ClientEvents() {}

    private static boolean modActive(Minecraft mc)
    {
        return mc.player != null && mc.level != null && BTConfig.serverLoaded() && BTConfig.SERVER.enabled.get();
    }

    private static boolean locked()
    {
        // A line out belongs to the fishing rod (Nice Catch reels on the use button), so the
        // lock never takes use-clicks from a player who is fishing.
        LocalPlayer player = Minecraft.getInstance().player;
        return ClientCatchState.localHolding() && BTConfig.SERVER.catchEnabled.get()
                && BTConfig.SERVER.lockInputWhileHolding.get()
                && (player == null || player.fishing == null);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            // Vanilla polls the drop key's clicks later this same tick; taking them here first is
            // what turns "tap = drop, hold = fling" into a decision we get to make.
            if (modActive(mc) && mc.screen == null && !mc.player.isSpectator()
                    && (BTConfig.SERVER.dropThrowEnabled.get() || locked())) {
                while (mc.options.keyDrop.consumeClick()) {
                    ClientThrowing.onDropKeyPressed(Screen.hasControlDown());
                }
            }
            return;
        }
        if (mc.level == null || mc.player == null) {
            ClientThrowing.reset();
            ClientCatchState.clear();
            return;
        }
        ClientCatchState.tick(mc);
        ClientThrowing.clientTick(mc);
    }

    /**
     * First interception point: the use key. Holding a caught projectile turns every click into a
     * (caught) throw charge. Otherwise a right-click with a throwable while aiming at nothing is
     * taken: a quick click is a weak, uncharged throw, a hold becomes a charge. Aiming at a block
     * or entity keeps vanilla behaviour so doors and chests still work.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event)
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !modActive(mc)) return;
        if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND) ClientThrowing.onUseClick();

        if (locked()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND
                    && ClientThrowing.canStartCharge() && ClientThrowing.useReleasedSinceCatch()) {
                ClientThrowing.beginCharge(ThrowMode.CAUGHT, InteractionHand.MAIN_HAND);
            }
            return;
        }
        if (!event.isUseItem()) return;
        if (ClientThrowing.ownsUseClick()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (!BTConfig.SERVER.chargedThrowsEnabled.get()) return;
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!Throwables.isThrowable(stack)) return;
        boolean aimingAtNothing = mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS;
        if (aimingAtNothing && !player.isHandsBusy() && ClientThrowing.canPress()
                && !player.getCooldowns().isOnCooldown(stack.getItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            ClientThrowing.pressUse(event.getHand(), stack);
        }
    }

    /**
     * Second interception point: item uses that fell through a block interaction (right-clicking
     * plain stone with a snowball) skip the path above and arrive here instead.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        if (!event.getLevel().isClientSide || !(event.getEntity() instanceof LocalPlayer player)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!modActive(mc)) return;

        if (locked()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.PASS);
            if (event.getHand() == InteractionHand.MAIN_HAND && ClientThrowing.canStartCharge()
                    && ClientThrowing.useReleasedSinceCatch()) {
                ClientThrowing.beginCharge(ThrowMode.CAUGHT, InteractionHand.MAIN_HAND);
            }
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!Throwables.isThrowable(stack) || !BTConfig.SERVER.chargedThrowsEnabled.get()) return;
        if (player.getCooldowns().isOnCooldown(stack.getItem())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.PASS);
        if (!ClientThrowing.ownsUseClick() && !player.isHandsBusy() && ClientThrowing.canPress()) {
            ClientThrowing.pressUse(event.getHand(), stack);
        }
    }

    /** Releases are caught at the input callback, not the tick, so the flick and bar are read at the true instant. */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event)
    {
        if (event.getAction() == GLFW.GLFW_RELEASE) ClientThrowing.onMouseButtonReleased(event.getButton());
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event)
    {
        if (event.getAction() == GLFW.GLFW_RELEASE) ClientThrowing.onKeyReleased(event.getKey(), event.getScanCode());
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) ClientThrowing.sampleRotation(mc);
    }

    /** Focusing a throw narrows the view a little; vanilla smooths the transition for us. */
    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event)
    {
        if (!ClientThrowing.isCharging()) return;
        float focus = ClientThrowing.focus(Minecraft.getInstance().getPartialTick());
        float multiplier = Mth.lerp(focus, 1.0F, BTConfig.CLIENT.focusFovMultiplier.get().floatValue());
        event.setNewFovModifier(event.getNewFovModifier() * multiplier);
    }

    /**
     * A tiny kick on release (if enabled). The view never shakes while charging: a charge held too
     * long trembles in the hand instead (FirstPersonAnimator).
     */
    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event)
    {
        float pt = (float) event.getPartialTick();
        if (BTConfig.CLIENT.throwKick.get()) {
            float whip = Math.max(ClientThrowing.throwWhip(pt, true), ClientThrowing.throwWhip(pt, false));
            if (whip > 0.0F) {
                event.setPitch(event.getPitch() - 1.2F * Mth.sin(whip * (float) Math.PI));
            }
        }
    }

    /** Third-person poses: swap in the wind-up / throw / holding arm pose before the model is set up. */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event)
    {
        if (!BTConfig.CLIENT.thirdPersonPoses.get()) return;
        ThrowPoses.applyTo(event.getEntity(), event.getRenderer().getModel());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        ClientThrowing.reset();
        ClientCatchState.clear();
        // A remote server's learned throwables belong to it (singleplayer's server clears its own on stop).
        if (Minecraft.getInstance().getSingleplayerServer() == null) Throwables.setLearned(java.util.List.of());
    }
}
