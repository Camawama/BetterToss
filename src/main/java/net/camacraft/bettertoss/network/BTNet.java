package net.camacraft.bettertoss.network;

import net.camacraft.bettertoss.BetterTossMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BTNet
{
    /** Bump whenever any message's wire format changes, so mismatched jars fail fast at login. */
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(BetterTossMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private BTNet() {}

    public static void register()
    {
        int id = 0;
        CHANNEL.registerMessage(id++, ChargeStartMessage.class, ChargeStartMessage::encode, ChargeStartMessage::decode, ChargeStartMessage::handle);
        CHANNEL.registerMessage(id++, ChargeCancelMessage.class, ChargeCancelMessage::encode, ChargeCancelMessage::decode, ChargeCancelMessage::handle);
        CHANNEL.registerMessage(id++, ThrowMessage.class, ThrowMessage::encode, ThrowMessage::decode, ThrowMessage::handle);
        CHANNEL.registerMessage(id++, CatchIntentMessage.class, CatchIntentMessage::encode, CatchIntentMessage::decode, CatchIntentMessage::handle);
        CHANNEL.registerMessage(id++, ChargeSyncMessage.class, ChargeSyncMessage::encode, ChargeSyncMessage::decode, ChargeSyncMessage::handle);
        CHANNEL.registerMessage(id++, CaughtSyncMessage.class, CaughtSyncMessage::encode, CaughtSyncMessage::decode, CaughtSyncMessage::handle);
    }

    public static void sendToServer(Object message)
    {
        CHANNEL.sendToServer(message);
    }

    public static void sendTo(ServerPlayer player, Object message)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** To every player tracking this entity, NOT the entity itself (it predicts its own state). */
    public static void sendToTracking(Entity entity, Object message)
    {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), message);
    }

    /** To every player tracking this entity, and the entity itself if it is a player. */
    public static void sendToTrackingAndSelf(Entity entity, Object message)
    {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), message);
    }
}
