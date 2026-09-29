package net.camacraft.bettertoss.network;

import net.camacraft.bettertoss.server.ServerThrowManager;
import net.camacraft.camafy.network.Packets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: a quick click with a throwable, released before it became a charge. The server
 * throws it right away as an uncharged throw: an empty bar's power and the widest focus spread.
 */
public class TapThrowMessage
{
    private final InteractionHand hand;

    public TapThrowMessage(InteractionHand hand)
    {
        this.hand = hand;
    }

    public static void encode(TapThrowMessage msg, FriendlyByteBuf buf)
    {
        buf.writeBoolean(msg.hand == InteractionHand.OFF_HAND);
    }

    public static TapThrowMessage decode(FriendlyByteBuf buf)
    {
        return new TapThrowMessage(buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    }

    public static void handle(TapThrowMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        Packets.handleServer(ctx, sender -> ServerThrowManager.onTapThrow(sender, msg.hand));
    }
}
