package net.camacraft.bettertoss.server;

import net.camacraft.bettertoss.BTConfig;
import net.camacraft.bettertoss.BetterTossMod;
import net.camacraft.bettertoss.HoldLockEvents;
import net.camacraft.bettertoss.Throwables;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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
     * so the only way a throwable ever leaves a hand is through a charged release.
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(EntityJoinLevelEvent event)
    {
        ServerThrowManager.onEntityJoin(event.getEntity(), event.getLevel());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(ProjectileImpactEvent event)
    {
        if (CatchManager.onImpact(event.getProjectile(), event.getRayTraceResult())) {
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
        }
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
        if (event.getEntity() instanceof ServerPlayer player) CatchManager.onLogin(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerThrowManager.onLogout(player);
            CatchManager.onLogout(player);
        }
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
