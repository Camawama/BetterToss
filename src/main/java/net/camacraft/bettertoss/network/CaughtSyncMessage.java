package net.camacraft.bettertoss.network;

import net.camacraft.bettertoss.client.ClientPacketHandlers;
import net.camacraft.camafy.network.Packets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> clients tracking a player (and that player): they now hold a caught projectile shown as
 * this item, or stopped holding one. The holder's own client uses it to enter/leave throw-only mode.
 */
public class CaughtSyncMessage
{
    private final int entityId;
    private final boolean holding;
    private final ItemStack display;

    public CaughtSyncMessage(int entityId, boolean holding, ItemStack display)
    {
        this.entityId = entityId;
        this.holding = holding;
        this.display = display;
    }

    public static void encode(CaughtSyncMessage msg, FriendlyByteBuf buf)
    {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.holding);
        buf.writeItem(msg.display);
    }

    public static CaughtSyncMessage decode(FriendlyByteBuf buf)
    {
        return new CaughtSyncMessage(buf.readVarInt(), buf.readBoolean(), buf.readItem());
    }

    public static void handle(CaughtSyncMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        Packets.handleClient(ctx, () -> () -> ClientPacketHandlers.onCaughtSync(msg.entityId, msg.holding, msg.display));
    }
}
