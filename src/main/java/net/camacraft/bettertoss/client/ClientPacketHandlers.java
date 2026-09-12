package net.camacraft.bettertoss.client;

import net.camacraft.bettertoss.network.ChargeSyncMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Client main-thread handlers for the server's sync packets. */
public final class ClientPacketHandlers
{
    private ClientPacketHandlers() {}

    public static void onChargeSync(int entityId, byte state, boolean offHand)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == entityId) return; // the local player predicts its own
        switch (state) {
            case ChargeSyncMessage.CHARGING -> ClientCatchState.setCharging(entityId, offHand);
            case ChargeSyncMessage.THROWN -> ClientCatchState.setThrown(entityId);
            default -> ClientCatchState.setIdle(entityId);
        }
    }

    public static void onCaughtSync(int entityId, boolean holding, ItemStack display)
    {
        if (holding) ClientCatchState.setCaught(entityId, display);
        else ClientCatchState.clearCaught(entityId);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == entityId) {
            ClientThrowing.onCaughtLocal(holding);
        }
    }
}
