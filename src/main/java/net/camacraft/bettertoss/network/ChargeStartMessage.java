package net.camacraft.bettertoss.network;

import net.camacraft.bettertoss.ThrowMode;
import net.camacraft.bettertoss.server.ServerThrowManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player started holding the button; the server times the hold and shows the pose to others. */
public class ChargeStartMessage
{
    private final ThrowMode mode;
    private final InteractionHand hand;

    public ChargeStartMessage(ThrowMode mode, InteractionHand hand)
    {
        this.mode = mode;
        this.hand = hand;
    }

    public static void encode(ChargeStartMessage msg, FriendlyByteBuf buf)
    {
        buf.writeByte(msg.mode.ordinal());
        buf.writeBoolean(msg.hand == InteractionHand.OFF_HAND);
    }

    public static ChargeStartMessage decode(FriendlyByteBuf buf)
    {
        ThrowMode mode = ThrowMode.byId(buf.readByte());
        InteractionHand hand = buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return new ChargeStartMessage(mode, hand);
    }

    public static void handle(ChargeStartMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                ServerThrowManager.onChargeStart(sender, msg.mode, msg.hand);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
