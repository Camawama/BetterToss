package net.camacraft.bettertoss.client;

import net.camacraft.bettertoss.ProjectileDrives;
import net.camacraft.bettertoss.network.ChargeSyncMessage;
import net.camacraft.bettertoss.Throwables;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

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

    /** The server's learned throwables (in singleplayer the set is the server's own already). */
    public static void onLearnedThrowables(List<ResourceLocation> ids)
    {
        if (Minecraft.getInstance().getSingleplayerServer() != null) return;
        List<Item> items = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            if (BuiltInRegistries.ITEM.containsKey(id)) items.add(BuiltInRegistries.ITEM.get(id));
        }
        Throwables.setLearned(items);
    }

    /** The server's push for a projectile that holds its own speed, tied to the level this client is in. */
    public static void onDriveSync(int entityId, double push)
    {
        Minecraft mc = Minecraft.getInstance();
        ProjectileDrives.clientSet(mc.level == null ? null : mc.level.dimension(), entityId, push);
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
