package net.camacraft.bettertoss.network;

import net.camacraft.bettertoss.server.ServerThrowManager;
import net.camacraft.camafy.network.Packets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the charge was abandoned (screen opened, item swapped away) - no throw. */
public class ChargeCancelMessage
{
    public ChargeCancelMessage() {}

    public static void encode(ChargeCancelMessage msg, FriendlyByteBuf buf) {}

    public static ChargeCancelMessage decode(FriendlyByteBuf buf)
    {
        return new ChargeCancelMessage();
    }

    public static void handle(ChargeCancelMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        Packets.handleServer(ctx, ServerThrowManager::onChargeCancel);
    }
}
