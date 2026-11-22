package net.camacraft.bettertoss;

import net.camacraft.bettertoss.network.BTNet;
import net.camacraft.bettertoss.network.DriveSyncMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Carries a charge to projectiles that hold their own speed. Better Toss gives a thrown projectile its charged
 * velocity as it joins the level ({@code ServerThrowManager.applyProjectile}); most keep it, but some drive
 * themselves (a throwing disc flies level at its tier's set speed for a set number of ticks and then comes back),
 * so one tick later the velocity is the item's own again and the charge changed nothing.
 *
 * <p>Such a projectile is told by its first tick: it was given one speed and holds another, its own. From then on,
 * while a charge above the item's own speed asked for more, it is moved a little further along its heading after
 * every tick of its own ({@code push} = the speed it was given less its own), so it covers the charged distance in
 * the flight time its mod gives it and arrives that much sooner, following its own bounces, hits and return. The
 * push stops short of anything in its way: a block or an entity in the extra stretch is left for the projectile's
 * own next step, so its mod's own hit and bounce code runs. The drive ends when the projectile is gone, has turned
 * its physics off (a disc on its way back), has stopped, or no longer flies at its own speed. A quick click can't
 * slow such a projectile below its own speed; it leaves at that.</p>
 *
 * <p>Clients tracking the projectile are told the push ({@link DriveSyncMessage}) and move their copy the same
 * way after its own tick, so the position the server sends every tick is where the client already is. Server
 * config {@code driveFixedSpeedProjectiles}.</p>
 *
 * <p>The push follows the projectile's own ticks: a tick in which it didn't move itself (the level frozen or stepped
 * by {@code /tick}, its chunk out of entity range) gets none.</p>
 *
 * <p>Every watch and drive belongs to the level its projectile is in and is judged and moved only when that level
 * ticks: the end of every other dimension's tick must not count the projectile as missing (with a dozen worlds
 * loaded, misses counted in every level ended each drive right after its first push).</p>
 */
public final class ProjectileDrives
{
    /** A projectile just launched in {@code dimension}, judged on its first tick of its own. */
    private record Watch(ResourceKey<Level> dimension, double ownSpeed, double givenSpeed, int ticksWaited) {}

    private static final class Drive
    {
        /** The level the projectile is in (on a client, the level it was in when the server's word came). */
        final ResourceKey<Level> dimension;
        final double push;
        final double ownSpeed;
        int ticks;
        int misses;
        /** The projectile's tick count at its last push: it is pushed once per tick of its own, never while it stands still. */
        int lastTick = Integer.MIN_VALUE;

        Drive(ResourceKey<Level> dimension, double push, double ownSpeed)
        {
            this.dimension = dimension;
            this.push = push;
            this.ownSpeed = ownSpeed;
        }
    }

    /** Ticks a drive lasts at most (a disc's flight is 12 to 17). */
    static final int MAX_TICKS = 200;
    /** How far from its own speed a projectile may stray and still count as holding it. */
    private static final double OWN_SPEED_TOLERANCE = 0.02D;
    /** Below this much more than the item's own speed a charge adds nothing worth driving. */
    private static final double MIN_GAIN = 1.05D;

    private static final Map<Integer, Watch> WATCHES = new HashMap<>();
    private static final Map<Integer, Drive> SERVER = new HashMap<>();
    private static final Map<Integer, Drive> CLIENT = new HashMap<>();

    private ProjectileDrives() {}

    public static boolean enabled()
    {
        return BTConfig.SERVER.enabled.get() && BTConfig.SERVER.chargedThrowsEnabled.get()
                && BTConfig.SERVER.driveFixedSpeedProjectiles.get();
    }

    /**
     * A projectile Better Toss just launched at {@code givenSpeed} where the item's own throw would have launched
     * it at {@code ownSpeed}: watched for its first tick (server side).
     */
    public static void watch(Projectile projectile, double ownSpeed, double givenSpeed)
    {
        if (!enabled() || projectile.level().isClientSide || ownSpeed <= 0.0D) return;
        if (givenSpeed < ownSpeed * MIN_GAIN) return; // a tap: it leaves at its own speed, nothing to add
        WATCHES.put(projectile.getId(), new Watch(projectile.level().dimension(), ownSpeed, givenSpeed, 0));
    }

    /** The server told this client the projectile's push (0: the drive ended); {@code dimension} is the client's level. */
    public static void clientSet(ResourceKey<Level> dimension, int entityId, double push)
    {
        if (push <= 0.0D || dimension == null) CLIENT.remove(entityId);
        else CLIENT.put(entityId, new Drive(dimension, push, 0.0D));
    }

    /** Whether this side drives the entity (for tests). */
    public static boolean isDriven(Entity entity)
    {
        return (entity.level().isClientSide ? CLIENT : SERVER).containsKey(entity.getId());
    }

    /** The push a driven entity gets each tick, 0 when it isn't driven (for tests). */
    public static double pushOf(Entity entity)
    {
        Drive drive = (entity.level().isClientSide ? CLIENT : SERVER).get(entity.getId());
        return drive == null ? 0.0D : drive.push;
    }

    /** The integrated server can stop and another world start in the same JVM. */
    public static void onServerStopped()
    {
        WATCHES.clear();
        SERVER.clear();
    }

    public static void onClientLeft()
    {
        CLIENT.clear();
    }

    /** After the entities of {@code level} ticked (both sides). */
    public static void onLevelTick(Level level)
    {
        if (level.isClientSide) {
            drive(level, CLIENT, false);
            return;
        }
        if (!WATCHES.isEmpty()) judge(level);
        drive(level, SERVER, true);
    }

    /** A watched projectile of this level that has had its first tick of its own: does it hold the speed it was given? */
    private static void judge(Level level)
    {
        ResourceKey<Level> dimension = level.dimension();
        Iterator<Map.Entry<Integer, Watch>> it = WATCHES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Watch> entry = it.next();
            Watch w = entry.getValue();
            if (!w.dimension().equals(dimension)) continue; // another level's: judged when that one ticks
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || entity.tickCount < 1) {
                // gone already, or added after this tick's entity loop: three ticks of its level of looking
                if (w.ticksWaited() >= 3 || (entity != null && entity.isRemoved())) it.remove();
                else entry.setValue(new Watch(w.dimension(), w.ownSpeed(), w.givenSpeed(), w.ticksWaited() + 1));
                continue;
            }
            it.remove();
            if (entity.isRemoved()) continue;
            double speed = entity.getDeltaMovement().length();
            if (Math.abs(speed - w.ownSpeed()) > w.ownSpeed() * OWN_SPEED_TOLERANCE) continue; // kept what it was given
            double push = w.givenSpeed() - w.ownSpeed();
            SERVER.put(entity.getId(), new Drive(dimension, push, w.ownSpeed()));
            BTNet.sendToTracking(entity, new DriveSyncMessage(entity.getId(), (float) push));
            BetterTossMod.LOGGER.debug("Better Toss: {} holds its own speed {}; driven {} blocks a tick further",
                    entity.getType(), w.ownSpeed(), push);
        }
    }

    private static void drive(Level level, Map<Integer, Drive> drives, boolean server)
    {
        if (drives.isEmpty()) return;
        ResourceKey<Level> dimension = level.dimension();
        Iterator<Map.Entry<Integer, Drive>> it = drives.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Drive> entry = it.next();
            Drive drive = entry.getValue();
            if (!drive.dimension.equals(dimension)) {
                // a server drive waits for its own level's tick; a client's level changed under it: the entity is gone
                if (!server) it.remove();
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null) {
                if (++drive.misses > 2) it.remove(); // gone (a thrown thing never changes level)
                continue;
            }
            drive.misses = 0;
            if (!entity.isRemoved() && entity.tickCount == drive.lastTick) continue; // it didn't tick (frozen, stepped, out of range)
            drive.lastTick = entity.tickCount;
            if (entity.isRemoved() || ++drive.ticks > MAX_TICKS || !push(level, entity, drive, server)) {
                it.remove();
                if (server && !entity.isRemoved()) BTNet.sendToTracking(entity, new DriveSyncMessage(entity.getId(), 0.0F));
            }
        }
    }

    /** One tick's push; false when the drive is over. */
    private static boolean push(Level level, Entity entity, Drive drive, boolean server)
    {
        Vec3 velocity = entity.getDeltaMovement();
        double speed = velocity.length();
        if (entity.noPhysics || speed < 1.0E-2D) return false;
        // the server knows the projectile's own speed; a client takes the server's word and only mirrors the push
        if (server && Math.abs(speed - drive.ownSpeed) > drive.ownSpeed * 0.10D) return false;
        Vec3 dir = velocity.scale(1.0D / speed);
        Vec3 from = entity.position();
        Vec3 to = from.add(dir.scale(drive.push));
        if (server) {
            Entity owner = entity instanceof Projectile projectile ? projectile.getOwner() : null;
            AABB sweep = entity.getBoundingBox().expandTowards(dir.scale(drive.push)).inflate(0.3D);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, entity, from, to, sweep,
                    other -> other != entity && other != owner && other.isAlive() && other.isPickable() && !other.isSpectator());
            if (hit != null) return true; // its own next step meets them
        }
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (block.getType() != HitResult.Type.MISS) return true; // its own next step hits it
        entity.setPos(to);
        if (server) entity.hasImpulse = true; // send this tick's position too
        return true;
    }
}
