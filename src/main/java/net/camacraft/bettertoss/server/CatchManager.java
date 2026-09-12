package net.camacraft.bettertoss.server;

import net.camacraft.bettertoss.BTConfig;
import net.camacraft.bettertoss.BetterTossMod;
import net.camacraft.bettertoss.Catchables;
import net.camacraft.bettertoss.network.BTNet;
import net.camacraft.bettertoss.network.CaughtSyncMessage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Catching. While a player holds the catch key the server watches for catchable projectiles
 * closing in inside a cone in front of them (plus an impact hook for anything fast enough to skip
 * the radius between ticks). A caught projectile is serialised, removed from the world, and lives
 * in the catcher's hand until they throw it back - it is never turned into an item. The stored
 * NBT is written into the player's persistent data so a server restart keeps it; logging out
 * (and by default dying) drops it back into the world where the player stands.
 */
public final class CatchManager
{
    private static final String TAG = "bettertoss_caught";

    public static final class Held
    {
        public final CompoundTag nbt;
        public final ItemStack display;
        public final double baseSpeed;
        public final long caughtTick;

        Held(CompoundTag nbt, ItemStack display, double baseSpeed, long caughtTick)
        {
            this.nbt = nbt;
            this.display = display;
            this.baseSpeed = baseSpeed;
            this.caughtTick = caughtTick;
        }
    }

    private static final Map<UUID, Held> HELD = new HashMap<>();
    private static final Set<UUID> INTENT = new HashSet<>();

    /** Installed by the client so common code can ask "is this player holding?" on either side. */
    public static Predicate<Player> clientHoldingCheck = p -> false;

    private CatchManager() {}

    public static boolean isHolding(Player player)
    {
        if (player.level().isClientSide) return clientHoldingCheck.test(player);
        return HELD.containsKey(player.getUUID());
    }

    @Nullable
    public static Held held(ServerPlayer player)
    {
        return HELD.get(player.getUUID());
    }

    public static void setIntent(ServerPlayer player, boolean ready)
    {
        if (ready) INTENT.add(player.getUUID());
        else INTENT.remove(player.getUUID());
    }

    private static long now(ServerPlayer player)
    {
        return player.serverLevel().getServer().getTickCount();
    }

    private static boolean canCatchNow(ServerPlayer player)
    {
        BTConfig.Server cfg = BTConfig.SERVER;
        if (!cfg.enabled.get() || !cfg.catchEnabled.get()) return false;
        if (!INTENT.contains(player.getUUID()) || HELD.containsKey(player.getUUID())) return false;
        if (!player.isAlive() || player.isSpectator() || player.isSleeping()) return false;
        if (player.isUsingItem() && !cfg.catchWhileUsingItem.get()) return false;
        if (cfg.requireEmptyHand.get() && !player.getMainHandItem().isEmpty()) return false;
        return true;
    }

    /** Per server tick for every player: with the key held, grab the nearest projectile in the catch window. */
    public static void tickPlayer(ServerPlayer player)
    {
        if (INTENT.isEmpty() || !canCatchNow(player)) return;
        double radius = BTConfig.SERVER.catchRadius.get();
        Vec3 eye = player.getEyePosition();
        AABB box = new AABB(eye, eye).inflate(radius + 1.0D);
        List<Projectile> candidates = player.serverLevel().getEntitiesOfClass(Projectile.class, box,
                p -> isCandidate(player, p, eye, radius, false));
        if (candidates.isEmpty()) return;
        Projectile best = null;
        double bestDist = Double.MAX_VALUE;
        for (Projectile p : candidates) {
            double d = p.distanceToSqr(eye);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best != null) tryCatch(player, best);
    }

    /** Impact hook: a projectile about to strike a catch-ready player is caught instead of hitting. */
    public static boolean onImpact(Projectile projectile, HitResult hit)
    {
        if (projectile.level().isClientSide) return false;
        if (!(hit instanceof EntityHitResult entityHit)) return false;
        if (!(entityHit.getEntity() instanceof ServerPlayer player)) return false;
        if (!canCatchNow(player)) return false;
        if (!isCandidate(player, projectile, player.getEyePosition(), BTConfig.SERVER.catchRadius.get(), true)) return false;
        return tryCatch(player, projectile);
    }

    private static boolean isCandidate(ServerPlayer player, Projectile projectile, Vec3 eye, double radius, boolean impact)
    {
        if (!projectile.isAlive() || projectile.isRemoved()) return false;
        if (!Catchables.isCatchable(projectile)) return false;
        Entity owner = projectile.getOwner();
        if (projectile instanceof AbstractArrow arrow && arrow.isNoPhysics() && !BTConfig.SERVER.allowReturningTridents.get()) return false;
        Vec3 velocity = projectile.getDeltaMovement();
        if (velocity.lengthSqr() < 0.0025D) return false; // resting, not in flight
        Vec3 to = projectile.position().subtract(eye);
        double dist = to.length();
        if (owner == player) {
            if (!BTConfig.SERVER.allowSelfCatch.get()) return false;
            // Your own throw only counts once it has genuinely left your hand and is coming back;
            // otherwise holding the button while throwing would snatch it straight back.
            if (projectile.tickCount < 10 || velocity.dot(to.scale(-1.0D)) <= 0.0D) return false;
        }
        if (!impact && dist > radius) return false;
        if (dist > 0.3D) {
            double cos = to.normalize().dot(player.getViewVector(1.0F));
            if (cos < Math.cos(Math.toRadians(BTConfig.SERVER.catchConeDegrees.get()))) return false;
        }
        // It has to be coming at you; a projectile flying away is not a catch.
        if (!impact && dist > 0.6D && velocity.dot(to.scale(-1.0D)) <= 0.0D) return false;
        return true;
    }

    private static boolean tryCatch(ServerPlayer player, Projectile projectile)
    {
        CompoundTag nbt = new CompoundTag();
        if (!projectile.save(nbt)) return false; // non-persistent entity type; can't be carried
        // Everything positional or per-flight is stripped; the projectile is re-launched fresh.
        for (String key : new String[] {"Pos", "Motion", "Rotation", "UUID", "LeftOwner", "HasBeenShot",
                "Owner", "inGround", "DealtDamage", "shake", "life", "Passengers", "PortalCooldown",
                "Fire", "OnGround", "Air", "FallDistance", "TicksFrozen"}) {
            nbt.remove(key);
        }
        ItemStack display = Catchables.displayFor(projectile, nbt);
        double baseSpeed = Catchables.baseSpeed(projectile);
        Held held = new Held(nbt, display, baseSpeed, now(player));
        Entity owner = projectile.getOwner();
        Vec3 where = projectile.position();
        projectile.discard();

        HELD.put(player.getUUID(), held);
        persist(player, held);
        ServerThrowManager.cancelCharge(player, true);

        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7F, 0.55F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8F, 1.3F);
        level.sendParticles(ParticleTypes.POOF, where.x, where.y, where.z, 4, 0.12D, 0.12D, 0.12D, 0.02D);
        if (BTConfig.SERVER.catchMessages.get() && owner instanceof ServerPlayer thrower && thrower != player) {
            Component what = display.isEmpty() ? projectile.getType().getDescription() : display.getHoverName();
            thrower.displayClientMessage(Component.translatable("bettertoss.caught_yours", player.getDisplayName(), what), true);
        }
        sync(player);
        return true;
    }

    /** Re-launch the held projectile from the catcher's eyes, now owned by them. */
    public static void throwHeld(ServerPlayer player, Vec3 dir, double speedMultiplier, boolean inheritMotion)
    {
        Held held = HELD.get(player.getUUID());
        if (held == null) return;
        if (now(player) - held.caughtTick < BTConfig.SERVER.catchCooldownTicks.get()) return;
        ServerLevel level = player.serverLevel();
        Optional<Entity> created = EntityType.create(held.nbt.copy(), level);
        if (created.isEmpty() || !(created.get() instanceof Projectile projectile)) {
            BetterTossMod.LOGGER.warn("[Better Toss] Could not recreate caught projectile {} for {}", held.nbt.getString("id"), player.getName().getString());
            clear(player);
            return;
        }
        projectile.moveTo(player.getX(), player.getEyeY() - 0.1D, player.getZ(), player.getYRot(), player.getXRot());
        projectile.setOwner(player);
        double speed = held.baseSpeed * speedMultiplier * BTConfig.SERVER.rethrowSpeedMultiplier.get();
        projectile.shoot(dir.x, dir.y, dir.z, (float) speed, 0.0F);
        if (inheritMotion) {
            Vec3 v = player.getDeltaMovement();
            projectile.setDeltaMovement(projectile.getDeltaMovement().add(v.x, player.onGround() ? 0.0D : v.y, v.z));
        }
        clear(player);
        level.addFreshEntity(projectile);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), Catchables.throwSound(projectile), SoundSource.PLAYERS,
                0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    /** Put the held projectile back into the world where the player stands, with no velocity, still owned by them. */
    public static void dropHeld(ServerPlayer player)
    {
        Held held = HELD.get(player.getUUID());
        if (held == null) return;
        ServerLevel level = player.serverLevel();
        Optional<Entity> created = EntityType.create(held.nbt.copy(), level);
        clear(player);
        if (created.isEmpty() || !(created.get() instanceof Projectile projectile)) return;
        projectile.moveTo(player.getX(), player.getEyeY() - 0.3D, player.getZ(), player.getYRot(), 0.0F);
        projectile.setOwner(player);
        projectile.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(projectile);
    }

    private static void clear(ServerPlayer player)
    {
        HELD.remove(player.getUUID());
        persist(player, null);
        sync(player);
    }

    // ---- lifecycle ----

    public static void onLogin(ServerPlayer player)
    {
        Held held = load(player);
        if (held != null) HELD.put(player.getUUID(), held);
        else HELD.remove(player.getUUID());
        INTENT.remove(player.getUUID());
        sync(player);
    }

    public static void onLogout(ServerPlayer player)
    {
        if (HELD.containsKey(player.getUUID())) dropHeld(player);
        HELD.remove(player.getUUID());
        INTENT.remove(player.getUUID());
    }

    public static void onDeath(ServerPlayer player)
    {
        if (!HELD.containsKey(player.getUUID())) return;
        if (BTConfig.SERVER.dropOnDeath.get()) dropHeld(player);
    }

    /** Respawn / dimension-return entity swap: carry the persisted tag over so a kept catch survives. */
    public static void onClone(Player original, Player replacement)
    {
        CompoundTag tag = original.getPersistentData().getCompound(TAG);
        if (!tag.isEmpty()) replacement.getPersistentData().put(TAG, tag.copy());
        else replacement.getPersistentData().remove(TAG);
    }

    /** After a respawn or dimension change the client has a fresh level: tell it (and new trackers) what it holds. */
    public static void resync(ServerPlayer player)
    {
        Held held = HELD.get(player.getUUID());
        if (held == null) {
            Held persisted = load(player);
            if (persisted != null) HELD.put(player.getUUID(), persisted);
        }
        sync(player);
    }

    public static void syncTo(ServerPlayer viewer, ServerPlayer holder)
    {
        Held held = HELD.get(holder.getUUID());
        if (held != null) BTNet.sendTo(viewer, new CaughtSyncMessage(holder.getId(), true, held.display));
    }

    private static void sync(ServerPlayer player)
    {
        Held held = HELD.get(player.getUUID());
        BTNet.sendToTrackingAndSelf(player, new CaughtSyncMessage(player.getId(), held != null,
                held != null ? held.display : ItemStack.EMPTY));
    }

    private static void persist(ServerPlayer player, @Nullable Held held)
    {
        if (held == null) {
            player.getPersistentData().remove(TAG);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Entity", held.nbt.copy());
        tag.put("Display", held.display.save(new CompoundTag()));
        tag.putDouble("BaseSpeed", held.baseSpeed);
        player.getPersistentData().put(TAG, tag);
    }

    @Nullable
    private static Held load(ServerPlayer player)
    {
        CompoundTag tag = player.getPersistentData().getCompound(TAG);
        if (tag.isEmpty() || !tag.contains("Entity", 10)) return null;
        CompoundTag nbt = tag.getCompound("Entity");
        ItemStack display = ItemStack.of(tag.getCompound("Display"));
        double baseSpeed = tag.contains("BaseSpeed") ? tag.getDouble("BaseSpeed") : 1.5D;
        return new Held(nbt, display, baseSpeed, now(player) - BTConfig.SERVER.catchCooldownTicks.get());
    }
}
