package net.camacraft.bettertoss.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.bettertoss.BTConfig;
import net.camacraft.bettertoss.ThrowMath;
import net.camacraft.bettertoss.ThrowMode;
import net.camacraft.bettertoss.Throwables;
import net.camacraft.bettertoss.network.BTNet;
import net.camacraft.bettertoss.network.CatchIntentMessage;
import net.camacraft.bettertoss.network.ChargeCancelMessage;
import net.camacraft.bettertoss.network.ChargeStartMessage;
import net.camacraft.bettertoss.network.ThrowMessage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;

/**
 * The local player's throw state machine: one charge at a time (a throwable item, a caught
 * projectile, or the selected stack via the drop key), released frame-accurately on the button's
 * release event with the flick measured at that instant. Also owns the drop-key tap-vs-hold logic
 * and the catch-key intent sent to the server.
 */
public final class ClientThrowing
{
    public enum Phase { IDLE, CHARGING }

    public static final int THROW_ANIM_TICKS = 7;

    private static Phase phase = Phase.IDLE;
    private static ThrowMode mode = ThrowMode.ITEM;
    private static InteractionHand hand = InteractionHand.MAIN_HAND;
    private static int chargeTicks;
    private static int cooldown;
    private static boolean fullStack;
    private static boolean focusCued;

    private static int throwAnimTicks;
    private static boolean throwAnimOffHand;

    private static boolean dropPending;
    private static int dropHeldTicks;
    private static boolean dropCtrl;

    private static boolean useHeldLastTick;
    private static boolean catchIntent;

    private static final FlickTracker FLICK = new FlickTracker();

    private ClientThrowing() {}

    public static Phase phase()
    {
        return phase;
    }

    public static ThrowMode mode()
    {
        return mode;
    }

    public static InteractionHand hand()
    {
        return hand;
    }

    public static int chargeTicks()
    {
        return chargeTicks;
    }

    public static boolean isFullStack()
    {
        return fullStack;
    }

    public static boolean isCharging()
    {
        return phase == Phase.CHARGING;
    }

    /** Whether the use button was already held during the previous tick (guards against auto-repeat starting a charge). */
    public static boolean useHeldLastTick()
    {
        return useHeldLastTick;
    }

    /** The power bar, 0..1: sweeping up and back down, or filling once, per client config. */
    public static float chargeValue(float partialTick)
    {
        float sweep = Math.max(1, BTConfig.CLIENT.chargeSweepTicks.get());
        float t = (chargeTicks + partialTick) / sweep;
        if (BTConfig.CLIENT.chargeMode.get() == BTConfig.ChargeMode.RAMP) return Math.min(1.0F, t);
        float s = t % 2.0F;
        return s <= 1.0F ? s : 2.0F - s;
    }

    /** 0 = just pressed and shaky, 1 = fully steadied. Mirrors the server's curve. */
    public static float focus(float partialTick)
    {
        return (float) ThrowMath.focus(chargeTicks + partialTick);
    }

    /** 1 at the instant of release easing to 0 - drives the first-person whip and camera kick. */
    public static float throwWhip(float partialTick, boolean mainHand)
    {
        if (throwAnimTicks <= 0 || throwAnimOffHand == mainHand) return 0.0F;
        return Mth.clamp((throwAnimTicks - partialTick) / (float) THROW_ANIM_TICKS, 0.0F, 1.0F);
    }

    public static boolean canStartCharge()
    {
        Minecraft mc = Minecraft.getInstance();
        return phase == Phase.IDLE && cooldown <= 0 && mc.screen == null && mc.player != null
                && !mc.player.isSpectator() && BTConfig.serverLoaded() && BTConfig.SERVER.enabled.get();
    }

    public static void beginCharge(ThrowMode newMode, InteractionHand newHand)
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        phase = Phase.CHARGING;
        mode = newMode;
        hand = newHand;
        chargeTicks = 0;
        focusCued = false;
        fullStack = newMode == ThrowMode.DROP && dropCtrl;
        BTNet.sendToServer(new ChargeStartMessage(newMode, newHand));
        ClientCatchState.setCharging(player.getId(), newHand == InteractionHand.OFF_HAND);
        if (BTConfig.CLIENT.chargeSounds.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER, 1.4F, 0.35F));
        }
    }

    public static void cancelCharge(boolean notifyServer)
    {
        if (phase != Phase.CHARGING) return;
        phase = Phase.IDLE;
        if (notifyServer) BTNet.sendToServer(new ChargeCancelMessage());
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) ClientCatchState.setIdle(player.getId());
    }

    /** The button came up: read the bar and the flick at this exact moment and tell the server. */
    public static void release(float partialTick)
    {
        if (phase != Phase.CHARGING) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            phase = Phase.IDLE;
            return;
        }
        float power = chargeValue(partialTick);
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        FlickTracker.Flick flick = BTConfig.SERVER.flickEnabled.get()
                ? FLICK.measure(Util.getMillis(), yaw, pitch) : FlickTracker.Flick.NONE;
        BTNet.sendToServer(new ThrowMessage(mode, hand, power, flick.yaw(), flick.pitch(), yaw, pitch, fullStack));

        phase = Phase.IDLE;
        cooldown = BTConfig.SERVER.throwCooldownTicks.get();
        throwAnimTicks = THROW_ANIM_TICKS;
        throwAnimOffHand = hand == InteractionHand.OFF_HAND;
        ClientCatchState.setThrown(player.getId());
        mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
    }

    // ---- drop key ----

    /** The drop key was pressed (its click already swallowed from vanilla); decide tap vs. hold over the next ticks. */
    public static void onDropKeyPressed(boolean ctrl)
    {
        if (phase != Phase.IDLE || dropPending) return;
        if (ClientCatchState.localHolding() && BTConfig.SERVER.lockInputWhileHolding.get()) return; // throw it first
        dropPending = true;
        dropHeldTicks = 0;
        dropCtrl = ctrl;
    }

    private static void tapDrop(Minecraft mc)
    {
        dropPending = false;
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) return;
        // Exactly what vanilla does for a drop-key press.
        if (player.drop(dropCtrl)) player.swing(InteractionHand.MAIN_HAND);
    }

    private static boolean dropThrowAllowed(LocalPlayer player)
    {
        return BTConfig.SERVER.dropThrowEnabled.get() && !player.getInventory().getSelected().isEmpty();
    }

    // ---- frame-accurate release ----

    private static InputConstants.Key controlKey(Minecraft mc)
    {
        return mode == ThrowMode.DROP ? mc.options.keyDrop.getKey() : mc.options.keyUse.getKey();
    }

    private static boolean controlDown(Minecraft mc)
    {
        return mode == ThrowMode.DROP ? ThrowControls.dropDown(mc) : ThrowControls.useDown(mc);
    }

    public static void onMouseButtonReleased(int button)
    {
        Minecraft mc = Minecraft.getInstance();
        if (phase == Phase.CHARGING) {
            if (ThrowControls.matchesMouse(controlKey(mc), button)) release(mc.getPartialTick());
        } else if (dropPending && ThrowControls.matchesMouse(mc.options.keyDrop.getKey(), button)) {
            tapDrop(mc);
        }
    }

    public static void onKeyReleased(int keyCode, int scanCode)
    {
        Minecraft mc = Minecraft.getInstance();
        if (phase == Phase.CHARGING) {
            if (ThrowControls.matchesKey(controlKey(mc), keyCode, scanCode)) release(mc.getPartialTick());
        } else if (dropPending && ThrowControls.matchesKey(mc.options.keyDrop.getKey(), keyCode, scanCode)) {
            tapDrop(mc);
        }
    }

    /** Called every render frame so the flick tracker sees the view at frame resolution. */
    public static void sampleRotation(Minecraft mc)
    {
        LocalPlayer player = mc.player;
        if (player == null) return;
        FLICK.sample(Util.getMillis(), player.getYRot(), player.getXRot());
    }

    // ---- tick ----

    public static void clientTick(Minecraft mc)
    {
        LocalPlayer player = mc.player;
        if (player == null || !BTConfig.serverLoaded()) {
            reset();
            return;
        }
        if (cooldown > 0) cooldown--;
        if (throwAnimTicks > 0) throwAnimTicks--;

        if (dropPending) {
            if (mc.screen != null) {
                dropPending = false;
            } else if (ThrowControls.dropDown(mc)) {
                dropHeldTicks++;
                if (dropHeldTicks >= BTConfig.CLIENT.dropHoldTicks.get()) {
                    dropPending = false;
                    if (dropThrowAllowed(player) && canStartCharge()) {
                        beginCharge(ThrowMode.DROP, InteractionHand.MAIN_HAND);
                    } else {
                        tapDrop(mc);
                    }
                }
            } else {
                tapDrop(mc);
            }
        }

        if (phase == Phase.CHARGING) {
            boolean valid = mc.screen == null && !player.isSpectator() && switch (mode) {
                case ITEM -> Throwables.isThrowable(player.getItemInHand(hand));
                case CAUGHT -> ClientCatchState.localHolding();
                case DROP -> !player.getInventory().getSelected().isEmpty();
            };
            if (!valid) {
                cancelCharge(true);
            } else if (!controlDown(mc)) {
                release(0.0F); // the release event was missed (focus loss etc.): release now
            } else {
                chargeTicks++;
                if (!focusCued && focus(0.0F) >= 0.999F) {
                    focusCued = true;
                    if (BTConfig.CLIENT.chargeSounds.get()) {
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.7F, 0.18F));
                    }
                }
            }
        }

        useHeldLastTick = ThrowControls.useDown(mc);

        // Catch intent: only state changes travel.
        boolean want = BTConfig.SERVER.enabled.get() && BTConfig.SERVER.catchEnabled.get()
                && !ClientCatchState.localHolding() && !player.isSpectator() && ThrowControls.catchDown(mc);
        if (want != catchIntent) {
            catchIntent = want;
            BTNet.sendToServer(new CatchIntentMessage(want));
        }
    }

    /** The server says we now hold (or no longer hold) a caught projectile. */
    public static void onCaughtLocal(boolean holding)
    {
        if (!holding) return;
        // Whatever we were winding up is over: the catch owns the hand now, and a fresh press is
        // needed before the caught projectile can be thrown.
        if (phase == Phase.CHARGING && mode != ThrowMode.CAUGHT) cancelCharge(false);
        cooldown = Math.max(cooldown, BTConfig.serverLoaded() ? BTConfig.SERVER.throwCooldownTicks.get() : 4);
        dropPending = false;
    }

    public static void reset()
    {
        phase = Phase.IDLE;
        chargeTicks = 0;
        cooldown = 0;
        throwAnimTicks = 0;
        dropPending = false;
        dropHeldTicks = 0;
        useHeldLastTick = false;
        catchIntent = false;
        focusCued = false;
        FLICK.clear();
    }
}
