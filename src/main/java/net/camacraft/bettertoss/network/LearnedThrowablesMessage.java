package net.camacraft.bettertoss.network;

import net.camacraft.bettertoss.client.ClientPacketHandlers;
import net.camacraft.camafy.network.Packets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: every item the server has learned throws itself the way a snowball does
 * ({@code server.ThrowableLearner}), the whole set each time, so the client's use key charges them too.
 */
public class LearnedThrowablesMessage
{
    private final List<ResourceLocation> ids;

    public LearnedThrowablesMessage(List<ResourceLocation> ids)
    {
        this.ids = ids;
    }

    public static LearnedThrowablesMessage of(Collection<Item> items)
    {
        List<ResourceLocation> ids = new ArrayList<>(items.size());
        for (Item item : items) ids.add(BuiltInRegistries.ITEM.getKey(item));
        return new LearnedThrowablesMessage(ids);
    }

    public static void encode(LearnedThrowablesMessage msg, FriendlyByteBuf buf)
    {
        buf.writeVarInt(msg.ids.size());
        for (ResourceLocation id : msg.ids) buf.writeResourceLocation(id);
    }

    public static LearnedThrowablesMessage decode(FriendlyByteBuf buf)
    {
        int n = Math.min(buf.readVarInt(), 4096);
        List<ResourceLocation> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(buf.readResourceLocation());
        return new LearnedThrowablesMessage(ids);
    }

    public static void handle(LearnedThrowablesMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        Packets.handleClient(ctx, () -> () -> ClientPacketHandlers.onLearnedThrowables(msg.ids));
    }
}
