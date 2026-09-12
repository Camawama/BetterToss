package net.camacraft.bettertoss.network;

import net.camacraft.bettertoss.server.CatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the catch key went down (true) or up (false); the server catches while it is down. */
public class CatchIntentMessage
{
    private final boolean ready;

    public CatchIntentMessage(boolean ready)
    {
        this.ready = ready;
    }

    public static void encode(CatchIntentMessage msg, FriendlyByteBuf buf)
    {
        buf.writeBoolean(msg.ready);
    }

    public static CatchIntentMessage decode(FriendlyByteBuf buf)
    {
        return new CatchIntentMessage(buf.readBoolean());
    }

    public static void handle(CatchIntentMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                CatchManager.setIntent(sender, msg.ready);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
