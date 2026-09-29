package net.camacraft.bettertoss.network;

import net.camacraft.bettertoss.BetterTossMod;
import net.camacraft.camafy.network.CamafyChannel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class BTNet
{
    /** Bump whenever any message's wire format changes, so mismatched jars fail fast at login. */
    private static final String PROTOCOL_VERSION = "2";

    public static final CamafyChannel CHANNEL = CamafyChannel.create(
            ResourceLocation.fromNamespaceAndPath(BetterTossMod.MODID, "main"), PROTOCOL_VERSION);

    private BTNet() {}

    public static void register()
    {
        // Each message is pinned to the side that receives it: one arriving the wrong way is dropped,
        // so a modified client can never run a client-bound handler on an integrated server.
        // Ids follow registration order, so keep it.
        CHANNEL.serverbound(ChargeStartMessage.class, ChargeStartMessage::encode, ChargeStartMessage::decode, ChargeStartMessage::handle)
                .serverbound(ChargeCancelMessage.class, ChargeCancelMessage::encode, ChargeCancelMessage::decode, ChargeCancelMessage::handle)
                .serverbound(ThrowMessage.class, ThrowMessage::encode, ThrowMessage::decode, ThrowMessage::handle)
                .serverbound(CatchIntentMessage.class, CatchIntentMessage::encode, CatchIntentMessage::decode, CatchIntentMessage::handle)
                .clientbound(ChargeSyncMessage.class, ChargeSyncMessage::encode, ChargeSyncMessage::decode, ChargeSyncMessage::handle)
                .clientbound(CaughtSyncMessage.class, CaughtSyncMessage::encode, CaughtSyncMessage::decode, CaughtSyncMessage::handle)
                .serverbound(TapThrowMessage.class, TapThrowMessage::encode, TapThrowMessage::decode, TapThrowMessage::handle);
    }

    public static void sendToServer(Object message)
    {
        CHANNEL.toServer(message);
    }

    public static void sendTo(ServerPlayer player, Object message)
    {
        CHANNEL.toPlayer(player, message);
    }

    /** To every player tracking this entity, NOT the entity itself (it predicts its own state). */
    public static void sendToTracking(Entity entity, Object message)
    {
        CHANNEL.toTracking(entity, message);
    }

    /** To every player tracking this entity, and the entity itself if it is a player. */
    public static void sendToTrackingAndSelf(Entity entity, Object message)
    {
        CHANNEL.toTrackingAndSelf(entity, message);
    }
}
