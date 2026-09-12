package net.camacraft.bettertoss.network;

import net.camacraft.bettertoss.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients tracking a player: that player started charging, threw, or stopped - drives the third-person poses. */
public class ChargeSyncMessage
{
    public static final byte IDLE = 0;
    public static final byte CHARGING = 1;
    public static final byte THROWN = 2;

    private final int entityId;
    private final byte state;
    private final boolean offHand;

    public ChargeSyncMessage(int entityId, byte state, boolean offHand)
    {
        this.entityId = entityId;
        this.state = state;
        this.offHand = offHand;
    }

    public static void encode(ChargeSyncMessage msg, FriendlyByteBuf buf)
    {
        buf.writeVarInt(msg.entityId);
        buf.writeByte(msg.state);
        buf.writeBoolean(msg.offHand);
    }

    public static ChargeSyncMessage decode(FriendlyByteBuf buf)
    {
        return new ChargeSyncMessage(buf.readVarInt(), buf.readByte(), buf.readBoolean());
    }

    public static void handle(ChargeSyncMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandlers.onChargeSync(msg.entityId, msg.state, msg.offHand)));
        ctx.get().setPacketHandled(true);
    }
}
