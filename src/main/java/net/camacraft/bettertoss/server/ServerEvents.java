package net.camacraft.bettertoss.server;

import net.camacraft.bettertoss.BTConfig;
import net.camacraft.bettertoss.BetterTossMod;
import net.camacraft.bettertoss.HoldLockEvents;
import net.camacraft.bettertoss.ProjectileDrives;
import net.camacraft.bettertoss.Throwables;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Server-side event wiring for the throw and catch managers. */
@Mod.EventBusSubscriber(modid = BetterTossMod.MODID)
public final class ServerEvents
{
    private ServerEvents() {}

    /**
     * The vanilla use-item packet still reaches the server for every right-click the client
     * intercepted; throwables are refused here unless the throw manager itself is dispatching them,
     * so the only way a throwable ever leaves a hand is through the throw manager: a charged
     * release, or a quick click it throws as an uncharged one.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (HoldLockEvents.locked(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.PASS);
            return;
        }
        BTConfig.Server cfg = BTConfig.SERVER;
        if (!cfg.enabled.get() || !cfg.chargedThrowsEnabled.get()) return;
        if (ServerThrowManager.isDispatching(player)) return;
        if (Throwables.isThrowable(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.PASS);
        }
    }

    /** Last, and only when no one cancelled it: the item's own use() runs next (see {@link ThrowableLearner}). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickItemLast(PlayerInteractEvent.RightClickItem event)
    {
        if (event.getEntity() instanceof ServerPlayer player && !ServerThrowManager.isDispatching(player)) {
            ThrowableLearner.onRightClickItem(player, event.getItemStack());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(EntityJoinLevelEvent event)
    {
        ServerThrowManager.onEntityJoin(event.getEntity(), event.getLevel());
        if (!event.getLevel().isClientSide) ThrowableLearner.onEntityJoin(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(ProjectileImpactEvent event)
    {
        if (CatchManager.onImpact(event.getProjectile(), event.getRayTraceResult())) {
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
        }
    }

    /** After a level's entities ticked, on either side: the projectiles that hold their own speed are carried further. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END) ProjectileDrives.onLevelTick(event.level);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END) return;
        if (event.player instanceof ServerPlayer player) {
            CatchManager.tickPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) {
            CatchManager.onLogin(player);
            ThrowableLearner.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerThrowManager.onLogout(player);
            CatchManager.onLogout(player);
            ThrowableLearner.onLogout(player);
        }
    }

    /**
     * The start-up scan runs before the saved learned set is loaded, so what it finds is told apart from what an
     * earlier world's throws taught; then both are the known set.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        java.util.List<net.minecraft.world.item.Item> scanned = ThrowableScan.run(event.getServer());
        ThrowableLearner.onServerStarted(event.getServer());
        Throwables.learnAll(scanned);
    }

    /** Session maps are static: clear them so a second singleplayer world in the same JVM starts clean. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        ServerThrowManager.onServerStopped();
        ProjectileDrives.onServerStopped();
        CatchManager.onServerStopped();
        ThrowableLearner.onServerStopped();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerThrowManager.cancelCharge(player, true);
            CatchManager.onDeath(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event)
    {
        if (event.getEntity().level().isClientSide) return;
        CatchManager.onClone(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) CatchManager.resync(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) CatchManager.resync(player);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event)
    {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target) {
            CatchManager.syncTo(viewer, target);
            ServerThrowManager.syncTo(viewer, target);
        }
    }
}
