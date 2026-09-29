package net.camacraft.bettertoss.client;

import net.camacraft.bettertoss.BetterTossMod;
import net.camacraft.bettertoss.server.CatchManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client mod-bus setup: keybinds, the custom arm poses, the client-side "is holding" hook, and the bar's XP-bar slot. */
@Mod.EventBusSubscriber(modid = BetterTossMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents
{
    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event)
    {
        event.register(ThrowControls.CATCH);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> {
            ThrowPoses.init();
            CatchManager.clientHoldingCheck = ClientCatchState::isHolding;
        });
        ThrowBarOverlay.register();
    }
}
