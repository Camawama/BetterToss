package net.camacraft.bettertoss.client;

import net.camacraft.bettertoss.BTConfig;
import net.camacraft.bettertoss.ThrowMath;
import net.camacraft.bettertoss.ThrowMode;
import net.camacraft.bettertoss.Throwables;
import net.camacraft.bettertoss.network.BTNet;
import net.camacraft.bettertoss.network.CatchIntentMessage;
import net.camacraft.bettertoss.network.ChargeCancelMessage;
import net.camacraft.bettertoss.network.ChargeStartMessage;
import net.camacraft.bettertoss.network.TapThrowMessage;
import net.camacraft.bettertoss.network.ThrowMessage;
import net.camacraft.camafy.client.charge.ChargeBar;
import net.camacraft.camafy.client.input.Keys;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * The local player's throw state machine: one charge at a time (a throwable item, a caught
 * projectile, or the selected stack via the drop key), released frame-accurately on the button's
 * release event with the flick measured at that instant. Also owns the tap-vs-hold logic of the use
 * key (a quick click is an uncharged throw) and the drop key (a tap drops), and the catch-key intent
 * sent to the server.
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

    private static int throwAnimTicks;
    private static boolean throwAnimOffHand;

    private static boolean dropPending;
    private static int dropHeldTicks;
    private static boolean dropCtrl;

    // A use click with a throwable waits here until the button comes up (an uncharged throw) or has
    // been held for useHoldTicks (a charge).
    private static boolean usePending;
    private static boolean usePendingThisTick;
    private static int useHeldTicks;
    private static InteractionHand useHand = InteractionHand.MAIN_HAND;
    /** This click is already ours: the hands vanilla has still to ask about it are refused. */
    private static boolean useClickTaken;
    /** False from a catch until the use button next comes up: the press that caught must not also throw. */
    private static boolean useReleasedSinceCatch = true;
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

    /** Whether the use button has come up since the last catch, so a caught projectile needs a fresh press. */
    public static boolean useReleasedSinceCatch()
    {
        return useReleasedSinceCatch;
    }

    /** Charging, a press still deciding between a click and a charge, or a click already thrown: any use input is ours to refuse. */
    public static boolean ownsUseClick()
    {
        return phase == Phase.CHARGING || usePending || useClickTaken;
    }

    /** The power bar, 0..1: sweeping up and back down, or filling once, per client config (Camafy's shared charge bar). */
    public static float chargeValue(float partialTick)
    {
        int sweep = BTConfig.CLIENT.chargeSweepTicks.get();
        float ticks = chargeTicks + partialTick;
        if (BTConfig.CLIENT.chargeMode.get() == BTConfig.ChargeMode.RAMP) return ChargeBar.ramp(ticks, sweep);
        return ChargeBar.oscillate(ticks, sweep);
    }

    /** 0 = just pressed (the widest spread), 1 = fully focused. Mirrors the server's curve. */
    public static float focus(float partialTick)
    {
        return (float) ThrowMath.focus(chargeTicks + partialTick);
    }

    /** 0 while the charge is fresh and steady, rising to 1 once it has been held too long. Mirrors the server's curve. */
    public static float strain(float partialTick)
    {
        return phase == Phase.CHARGING ? ThrowMath.strain(chargeTicks + partialTick) : 0.0F;
    }

    /** 1 at the instant of release easing to 0 - drives the first-person whip and camera kick. */
    public static float throwWhip(float partialTick, boolean mainHand)
    {
        if (throwAnimTicks <= 0 || throwAnimOffHand == mainHand) return 0.0F;
        return Mth.clamp((throwAnimTicks - partialTick) / (float) THROW_ANIM_TICKS, 0.0F, 1.0F);
    }

    public static boolean canStartCharge()
    {
        return cooldown <= 0 && canPress();
    }

    /** Whether a use click can be taken at all. Unlike a charge it ignores the throw cooldown: a quick click must never be lost. */
    public static boolean canPress()
    {
        Minecraft mc = Minecraft.getInstance();
        return phase == Phase.IDLE && mc.screen == null && mc.player != null
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
        fullStack = newMode == ThrowMode.DROP && dropCtrl;
        BTNet.sendToServer(new ChargeStartMessage(newMode, newHand));
        ClientCatchState.setCharging(player.getId(), newHand == InteractionHand.OFF_HAND);
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

    // ---- use key: quick click vs. hold ----

    /**
     * Vanilla asks the main hand first on every use click, so this is where a click begins. A click
     * queued behind a press that only started waiting this tick means both landed between the same
     * two ticks, so that press was already let go: it was a quick click, and it is thrown now.
     * (Auto-repeat can't be what arrived: vanilla holds it off for 4 ticks after any use.)
     */
    public static void onUseClick()
    {
        useClickTaken = false;
        if (usePending && usePendingThisTick) tap(Minecraft.getInstance(), useHand);
    }

    /**
     * A use click with a throwable, aimed at nothing (the caller has cancelled vanilla's use).
     * Released before useHoldTicks it is thrown at once as a weak, uncharged throw, so throwables
     * can still be spammed; held longer it becomes a charge. Items vanilla already throws on release
     * (tridents) charge from the first tick.
     */
    public static void pressUse(InteractionHand pressHand, ItemStack stack)
    {
        useClickTaken = true;
        if (Throwables.throwsOnRelease(stack)) {
            if (canStartCharge()) beginCharge(ThrowMode.ITEM, pressHand);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!ThrowControls.useDown(mc)) {
            tap(mc, pressHand); // pressed and let go before this tick even saw the click
            return;
        }
        usePending = true;
        usePendingThisTick = true;
        useHeldTicks = 0;
        useHand = pressHand;
    }

    /**
     * A quick click: the server throws it as a charge released at an empty bar (minimum power, the
     * widest spread) through the item's own use(). No throw cooldown applies, so clicks can be
     * spammed; the arm just swings, as vanilla's would.
     */
    private static void tap(Minecraft mc, InteractionHand tapHand)
    {
        usePending = false;
        LocalPlayer player = mc.player;
        ClientPacketListener connection = mc.getConnection();
        if (player == null || connection == null || mc.screen != null || player.isSpectator()) return;
        ItemStack stack = player.getItemInHand(tapHand);
        if (!Throwables.isThrowable(stack) || player.getCooldowns().isOnCooldown(stack.getItem())) return;
        // What vanilla sends ahead of every item use: the selected slot (a number key pressed this
        // same tick is not sent until the next) and this frame's aim.
        connection.send(new ServerboundSetCarriedItemPacket(player.getInventory().selected));
        connection.send(new ServerboundMovePlayerPacket.PosRot(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.onGround()));
        BTNet.sendToServer(new TapThrowMessage(tapHand));
        player.swing(tapHand);
        mc.gameRenderer.itemInHandRenderer.itemUsed(tapHand);
    }

    /** The use button came up: a press still waiting to become a charge was a quick click. */
    private static void onUseReleased(Minecraft mc)
    {
        useReleasedSinceCatch = true;
        if (usePending) tap(mc, useHand);
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

    private static KeyMapping controlKey(Minecraft mc)
    {
        return mode == ThrowMode.DROP ? mc.options.keyDrop : mc.options.keyUse;
    }

    private static boolean controlDown(Minecraft mc)
    {
        return mode == ThrowMode.DROP ? ThrowControls.dropDown(mc) : ThrowControls.useDown(mc);
    }

    public static void onMouseButtonReleased(int button)
    {
        Minecraft mc = Minecraft.getInstance();
        if (Keys.matchesMouseEvent(mc.options.keyUse, button)) onUseReleased(mc);
        if (phase == Phase.CHARGING) {
            if (Keys.matchesMouseEvent(controlKey(mc), button)) release(mc.getPartialTick());
        } else if (dropPending && Keys.matchesMouseEvent(mc.options.keyDrop, button)) {
            tapDrop(mc);
        }
    }

    public static void onKeyReleased(int keyCode, int scanCode)
    {
        Minecraft mc = Minecraft.getInstance();
        if (Keys.matchesKeyEvent(mc.options.keyUse, keyCode, scanCode)) onUseReleased(mc);
        if (phase == Phase.CHARGING) {
            if (Keys.matchesKeyEvent(controlKey(mc), keyCode, scanCode)) release(mc.getPartialTick());
        } else if (dropPending && Keys.matchesKeyEvent(mc.options.keyDrop, keyCode, scanCode)) {
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

        if (usePending) {
            if (mc.screen != null || player.isSpectator() || !Throwables.isThrowable(player.getItemInHand(useHand))) {
                usePending = false; // a menu opened or the item left the hand: the click is void
            } else if (!ThrowControls.useDown(mc)) {
                tap(mc, useHand); // the release event was missed (focus loss etc.): still a quick click
            } else if (++useHeldTicks >= BTConfig.CLIENT.useHoldTicks.get() && canStartCharge()) {
                usePending = false;
                beginCharge(ThrowMode.ITEM, useHand);
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
            }
        }

        if (!ThrowControls.useDown(mc)) useReleasedSinceCatch = true;
        // Every click this tick has been through vanilla's keybind handling by now.
        useClickTaken = false;
        usePendingThisTick = false;

        // Catch intent: only state changes travel. Never while a line is out: the catch key
        // shares right-click with reeling, and a mid-reel catch would lock the rod's use-clicks.
        boolean want = BTConfig.SERVER.enabled.get() && BTConfig.SERVER.catchEnabled.get()
                && !ClientCatchState.localHolding() && !player.isSpectator() && player.fishing == null
                && ThrowControls.catchDown(mc);
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
        usePending = false;
        useReleasedSinceCatch = !ThrowControls.useDown(Minecraft.getInstance());
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
        usePending = false;
        usePendingThisTick = false;
        useHeldTicks = 0;
        useClickTaken = false;
        useReleasedSinceCatch = true;
        catchIntent = false;
        FLICK.clear();
    }
}
