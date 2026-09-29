package net.camacraft.bettertoss;

import net.camacraft.bettertoss.server.CatchManager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The "you have to throw it first" rule: while a player holds a caught projectile, every other
 * interaction is refused on both sides. The client additionally swallows the raw clicks (see
 * ClientEvents) so nothing even swings; this is the authoritative backstop.
 */
@Mod.EventBusSubscriber(modid = BetterTossMod.MODID)
public final class HoldLockEvents
{
    private HoldLockEvents() {}

    public static boolean locked(Player player)
    {
        if (player == null || !BTConfig.serverLoaded()) return false;
        BTConfig.Server cfg = BTConfig.SERVER;
        // Never while a line is out: the rod's own use (Nice Catch re-dispatches it to reel in)
        // must not be refused mid-reel.
        return cfg.enabled.get() && cfg.catchEnabled.get() && cfg.lockInputWhileHolding.get()
                && player.fishing == null && CatchManager.isHolding(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event)
    {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event)
    {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event)
    {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreakBlock(BlockEvent.BreakEvent event)
    {
        if (locked(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event)
    {
        if (event.getEntity() instanceof Player player && locked(player)) event.setCanceled(true);
    }
}
