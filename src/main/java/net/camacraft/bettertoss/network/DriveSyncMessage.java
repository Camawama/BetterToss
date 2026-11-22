package net.camacraft.bettertoss.network;

import net.camacraft.bettertoss.ProjectileDrives;
import net.camacraft.bettertoss.client.ClientPacketHandlers;
import net.camacraft.camafy.network.Packets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> clients tracking a projectile that holds its own speed: how far Better Toss moves it further along its
 * heading after each tick (0: the drive ended), so the client moves its copy the same way ({@link ProjectileDrives}).
 */
public class DriveSyncMessage
{
    private final int entityId;
    private final float push;

    public DriveSyncMessage(int entityId, float push)
    {
        this.entityId = entityId;
        this.push = push;
    }

    public static void encode(DriveSyncMessage msg, FriendlyByteBuf buf)
    {
        buf.writeVarInt(msg.entityId);
        buf.writeFloat(msg.push);
    }

    public static DriveSyncMessage decode(FriendlyByteBuf buf)
    {
        return new DriveSyncMessage(buf.readVarInt(), buf.readFloat());
    }

    public static void handle(DriveSyncMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        float push = Float.isFinite(msg.push) ? Math.max(0.0F, Math.min(8.0F, msg.push)) : 0.0F;
        Packets.handleClient(ctx, () -> () -> ClientPacketHandlers.onDriveSync(msg.entityId, push));
    }
}
