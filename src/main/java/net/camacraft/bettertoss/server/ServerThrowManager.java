package net.camacraft.bettertoss.server;

import net.camacraft.bettertoss.BTConfig;
import net.camacraft.bettertoss.HoldLockEvents;
import net.camacraft.bettertoss.ThrowMath;
import net.camacraft.bettertoss.ThrowMode;
import net.camacraft.bettertoss.Throwables;
import net.camacraft.bettertoss.network.BTNet;
import net.camacraft.bettertoss.network.ChargeSyncMessage;
import net.camacraft.bettertoss.network.ThrowMessage;
import net.camacraft.camafy.charge.AimScatter;
import net.camacraft.camafy.data.PlayerSessions;
import net.camacraft.camafy.network.NetValidate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Server side of every throw. The server times how long the button was held (accuracy), validates
 * the release, and then - for item throws - simply runs the item's own use() and rescales whatever
 * projectile that spawned, inside the join event so the spawn packet already carries the real
 * velocity. Nothing about the item or projectile is special-cased, which is what keeps modded
 * throwables working.
 */
public final class ServerThrowManager
{
    public static final class Session
    {
        public boolean charging;
        public ThrowMode mode = ThrowMode.ITEM;
        public InteractionHand hand = InteractionHand.MAIN_HAND;
        public long chargeStart;
        boolean dispatching;
        boolean applied;
        @Nullable PendingThrow pending;
    }

    /** speedMultiplier scales a spawned projectile's own speed; absoluteSpeed > 0 is used for flung item entities instead. */
    record PendingThrow(Vec3 dir, double speedMultiplier, double absoluteSpeed, boolean inheritMotion) {}

    /** Dropped when the player logs out and when the server stops. */
    private static final PlayerSessions<Session> SESSIONS = PlayerSessions.create(id -> new Session());
    private static final List<ServerPlayer> DISPATCHING = new ArrayList<>();

    private ServerThrowManager() {}

    private static Session session(ServerPlayer player)
    {
        return SESSIONS.get(player);
    }

    @Nullable
    public static Session chargeState(ServerPlayer player)
    {
        return SESSIONS.peek(player);
    }

    /** True while this player's throw is running the item's use() - our own RightClickItem gate lets it through. */
    public static boolean isDispatching(Player player)
    {
        for (ServerPlayer p : DISPATCHING) {
            if (p == player) return true;
        }
        return false;
    }

    private static long now(ServerPlayer player)
    {
        return player.serverLevel().getServer().getTickCount();
    }

    public static void onChargeStart(ServerPlayer player, ThrowMode mode, InteractionHand hand)
    {
        BTConfig.Server cfg = BTConfig.SERVER;
        if (!cfg.enabled.get() || player.isSpectator() || !player.isAlive()) return;
        switch (mode) {
            case ITEM -> {
                if (!cfg.chargedThrowsEnabled.get() || HoldLockEvents.locked(player)
                        || !Throwables.isThrowable(player.getItemInHand(hand))) return;
            }
            case CAUGHT -> {
                if (!cfg.catchEnabled.get() || !CatchManager.isHolding(player)) return;
                hand = InteractionHand.MAIN_HAND;
            }
            case DROP -> {
                if (!cfg.dropThrowEnabled.get() || HoldLockEvents.locked(player)
                        || player.getInventory().getSelected().isEmpty()) return;
                hand = InteractionHand.MAIN_HAND;
            }
        }
        Session s = session(player);
        s.charging = true;
        s.mode = mode;
        s.hand = hand;
        s.chargeStart = now(player);
        BTNet.sendToTracking(player, new ChargeSyncMessage(player.getId(), ChargeSyncMessage.CHARGING,
                hand == InteractionHand.OFF_HAND));
    }

    public static void onChargeCancel(ServerPlayer player)
    {
        cancelCharge(player, true);
    }

    /** Ends a charge without a throw; broadcast tells trackers to drop the wind-up pose. */
    public static void cancelCharge(ServerPlayer player, boolean broadcast)
    {
        Session s = SESSIONS.peek(player);
        if (s == null || !s.charging) return;
        s.charging = false;
        if (broadcast) {
            BTNet.sendToTracking(player, new ChargeSyncMessage(player.getId(), ChargeSyncMessage.IDLE,
                    s.hand == InteractionHand.OFF_HAND));
        }
    }

    /** A player just started tracking another: show them the charge in progress, if any. */
    public static void syncTo(ServerPlayer viewer, ServerPlayer target)
    {
        Session s = SESSIONS.peek(target);
        if (s != null && s.charging) {
            BTNet.sendTo(viewer, new ChargeSyncMessage(target.getId(), ChargeSyncMessage.CHARGING,
                    s.hand == InteractionHand.OFF_HAND));
        }
    }

    /** The session itself is dropped by PlayerSessions. */
    public static void onLogout(ServerPlayer player)
    {
        DISPATCHING.remove(player);
    }

    /** The integrated server can stop and a new world start in the same JVM: start that one clean (sessions clear themselves). */
    public static void onServerStopped()
    {
        DISPATCHING.clear();
    }

    public static void onThrow(ServerPlayer player, ThrowMessage msg)
    {
        BTConfig.Server cfg = BTConfig.SERVER;
        if (!cfg.enabled.get() || player.isSpectator() || !player.isAlive()) return;
        Session s = session(player);
        ThrowMode mode = msg.mode();
        // Only a charge the server saw start can be released: this is what makes hold time honest.
        if (!s.charging || s.mode != mode) return;
        int holdTicks = (int) Math.max(0L, now(player) - s.chargeStart);
        s.charging = false;
        InteractionHand hand = mode == ThrowMode.ITEM ? msg.hand() : InteractionHand.MAIN_HAND;
        BTNet.sendToTracking(player, new ChargeSyncMessage(player.getId(), ChargeSyncMessage.THROWN,
                hand == InteractionHand.OFF_HAND));
        if (mode != ThrowMode.CAUGHT && HoldLockEvents.locked(player)) return;
        player.resetLastActionTime();

        // Aim: the client's release-frame angles, unless they disagree wildly with what the server knows.
        float yaw = NetValidate.angle(msg.yaw(), player.getYRot(), 30.0F);
        float pitch = NetValidate.angle(msg.pitch(), player.getXRot(), 30.0F);

        // Flick: clamped server-side, and turned into an angular bend plus a small speed bonus.
        float flickYaw = 0.0F;
        float flickPitch = 0.0F;
        double flickMagnitude = 0.0D;
        if (cfg.flickEnabled.get()) {
            float max = cfg.maxFlickDegrees.get().floatValue();
            flickYaw = NetValidate.finiteClamp(msg.flickYaw(), -max, max);
            flickPitch = NetValidate.finiteClamp(msg.flickPitch(), -max, max);
            flickMagnitude = max > 0.0F ? Math.min(1.0D, Math.hypot(flickYaw, flickPitch) / max) : 0.0D;
        }

        // How long the button was held decides the random spread: a hurried release is wild until
        // focus builds, and a charge held too long trembles off target again (strain).
        double focus = ThrowMath.focus(holdTicks);
        double spread = ThrowMath.inaccuracyDegrees(focus)
                + ThrowMath.strainInaccuracyDegrees(ThrowMath.strain(holdTicks));
        Vec3 dir = AimScatter.scatter(
                ThrowMath.direction(yaw + flickYaw, ThrowMath.clampPitch(pitch + flickPitch)),
                player.getRandom(), spread);

        float power = NetValidate.finiteClamp(msg.power(), 0.0F, 1.0F);
        double flickBonus = 1.0D + cfg.flickSpeedBonus.get() * flickMagnitude;
        double powerMultiplier = ThrowMath.powerMultiplier(power) * flickBonus;
        boolean inherit = cfg.inheritShooterMotion.get();

        switch (mode) {
            case ITEM -> throwItem(player, s, hand, dir, powerMultiplier, power, inherit);
            case DROP -> throwDrop(player, s, dir, power, flickBonus, msg.fullStack());
            case CAUGHT -> CatchManager.throwHeld(player, dir, powerMultiplier, inherit);
        }
    }

    /**
     * A quick click that never became a charge: thrown exactly like a charge released the instant it
     * started - an empty bar (minPowerMultiplier) and the widest focus spread. It still goes through
     * the item's own use(), so the projectile, sound, stats, item use and item cooldown (ender
     * pearls) are the item's; only the thrower's charge cooldown is skipped, so clicks can be spammed.
     */
    public static void onTapThrow(ServerPlayer player, InteractionHand hand)
    {
        BTConfig.Server cfg = BTConfig.SERVER;
        if (!cfg.enabled.get() || !cfg.chargedThrowsEnabled.get() || player.isSpectator() || !player.isAlive()
                || HoldLockEvents.locked(player)) return;
        ItemStack stack = player.getItemInHand(hand);
        // Hold-to-throw items (tridents) always arrive as charges; a tap for one is not honoured.
        if (!Throwables.isThrowable(stack) || Throwables.throwsOnRelease(stack)) return;
        player.resetLastActionTime();

        // The client sent its aim just ahead of this message, as vanilla does before an item use.
        Vec3 dir = AimScatter.scatter(
                ThrowMath.direction(player.getYRot(), ThrowMath.clampPitch(player.getXRot())),
                player.getRandom(), ThrowMath.inaccuracyDegrees(ThrowMath.focus(0.0D)));
        throwItem(player, session(player), hand, dir, ThrowMath.powerMultiplier(0.0F), 0.0F,
                cfg.inheritShooterMotion.get());
    }

    /**
     * Runs the item's own use() (cooldowns, creative handling, stats, sounds, Forge hooks all
     * included) with a capture window open; the projectile it spawns is rescaled on join. Items
     * that throw on release (tridents) are released immediately as a full-strength throw.
     */
    private static void throwItem(ServerPlayer player, Session s, InteractionHand hand, Vec3 dir,
                                  double powerMultiplier, float power, boolean inherit)
    {
        if (!BTConfig.SERVER.chargedThrowsEnabled.get()) return;
        ItemStack stack = player.getItemInHand(hand);
        if (!Throwables.isThrowable(stack)) {
            InteractionHand other = Throwables.findThrowableHand(player);
            if (other == null) return;
            hand = other;
            stack = player.getItemInHand(hand);
        }
        if (player.getCooldowns().isOnCooldown(stack.getItem())) return;

        s.pending = new PendingThrow(dir, powerMultiplier, 0.0D, inherit);
        s.applied = false;
        s.dispatching = true;
        DISPATCHING.add(player);
        try {
            InteractionResult result = player.gameMode.useItem(player, player.serverLevel(), stack, hand);
            if (player.isUsingItem()) {
                // A hold-to-throw item started its use; release it right away as a charged throw.
                ItemStack using = player.getUseItem();
                int duration = using.getUseDuration();
                int elapsed = 10 + Math.round(power * 20.0F);
                player.useItemRemaining = Math.max(0, duration - elapsed);
                player.releaseUsingItem();
            }
            if (result == InteractionResult.FAIL && !s.applied) {
                // Nothing was thrown (e.g. a riptide trident out of water); nothing to do.
            }
        } finally {
            s.dispatching = false;
            s.pending = null;
            DISPATCHING.remove(player);
        }
    }

    /** The drop-key fling: vanilla's drop path (so ItemTossEvent and container sync happen), with our velocity. */
    private static void throwDrop(ServerPlayer player, Session s, Vec3 dir, float power, double flickBonus, boolean fullStack)
    {
        BTConfig.Server cfg = BTConfig.SERVER;
        if (!cfg.dropThrowEnabled.get()) return;
        Inventory inv = player.getInventory();
        ItemStack selected = inv.getSelected();
        if (selected.isEmpty() || !selected.onDroppedByPlayer(player)) return;
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && (fullStack || selected.getCount() == 1)) {
            player.stopUsingItem();
        }
        // Unlike a vanilla drop, the client did not remove the item itself, so the container's
        // normal change broadcast must be left to send the new slot contents.
        ItemStack removed = inv.removeFromSelected(fullStack);

        // A whole stack is heavy: the more items actually leave the hand, the shorter the fling.
        double speed = Mth.lerp(power, cfg.dropThrowMinSpeed.get(), cfg.dropThrowMaxSpeed.get()) * flickBonus
                * ThrowMath.stackWeightMultiplier(removed.getCount());
        s.pending = new PendingThrow(dir, 1.0D, speed, false);
        s.applied = false;
        s.dispatching = true;
        DISPATCHING.add(player);
        try {
            ItemEntity entity = player.drop(removed, true);
            if (entity != null && !s.applied) {
                // An ItemTossEvent listener swapped the entity: apply the fling after the fact.
                entity.setDeltaMovement(dir.scale(speed));
                entity.hasImpulse = true;
            }
        } finally {
            s.dispatching = false;
            s.pending = null;
            DISPATCHING.remove(player);
        }
    }

    /**
     * Called for every entity joining a server level. While a player's throw is dispatching, the
     * projectile (or flung item) it produces gets the charged velocity here - before it is added to
     * the world, so the very first packet clients see is already correct.
     */
    public static void onEntityJoin(Entity entity, Level level)
    {
        if (level.isClientSide || DISPATCHING.isEmpty()) return;
        for (ServerPlayer player : DISPATCHING) {
            Session s = SESSIONS.peek(player);
            if (s == null || s.pending == null || s.applied || player.level() != level) continue;
            PendingThrow pending = s.pending;
            if (entity instanceof Projectile projectile && pending.absoluteSpeed() <= 0.0D) {
                Entity owner = projectile.getOwner();
                boolean ours = owner == player || (owner == null && projectile.distanceToSqr(player) < 16.0D);
                if (!ours) continue;
                applyProjectile(projectile, player, pending);
                s.applied = true;
                return;
            }
            if (entity instanceof ItemEntity item && pending.absoluteSpeed() > 0.0D) {
                boolean ours = item.getOwner() == player || item.distanceToSqr(player) < 4.0D;
                if (!ours) continue;
                item.setDeltaMovement(pending.dir().scale(pending.absoluteSpeed()));
                s.applied = true;
                return;
            }
        }
    }

    /**
     * Keeps the item's own launch speed (1.5 for a snowball, 0.5 for a potion, 2.5 for a trident,
     * whatever a modded item chose) and scales it by the charge, along our aim direction.
     */
    private static void applyProjectile(Projectile projectile, ServerPlayer player, PendingThrow pending)
    {
        Vec3 velocity = projectile.getDeltaMovement();
        Vec3 shooter = player.getDeltaMovement();
        Vec3 shooterAdj = new Vec3(shooter.x, player.onGround() ? 0.0D : shooter.y, shooter.z);
        double raw = velocity.length();
        double minusShooter = velocity.subtract(shooterAdj).length();
        double base = shooterAdj.lengthSqr() > 1.0E-6D && minusShooter > 0.05D ? minusShooter : raw;
        if (base < 0.05D) base = 1.5D;
        double speed = base * pending.speedMultiplier();
        Vec3 dir = pending.dir();
        projectile.shoot(dir.x, dir.y, dir.z, (float) speed, 0.0F);
        if (pending.inheritMotion()) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().add(shooterAdj));
        }
    }
}
