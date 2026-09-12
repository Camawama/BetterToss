package net.camacraft.bettertoss.network;

import net.camacraft.bettertoss.ThrowMode;
import net.camacraft.bettertoss.server.ServerThrowManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: the button was released. Carries the bar value, the flick the client measured
 * over the last few frames, and the exact view angles at the release frame (the server sanity-checks
 * them against what it knows and applies its own spread, clamps and power curve).
 */
public class ThrowMessage
{
    private final ThrowMode mode;
    private final InteractionHand hand;
    private final float power;
    private final float flickYaw;
    private final float flickPitch;
    private final float yaw;
    private final float pitch;
    private final boolean fullStack;

    public ThrowMessage(ThrowMode mode, InteractionHand hand, float power, float flickYaw, float flickPitch,
                        float yaw, float pitch, boolean fullStack)
    {
        this.mode = mode;
        this.hand = hand;
        this.power = power;
        this.flickYaw = flickYaw;
        this.flickPitch = flickPitch;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fullStack = fullStack;
    }

    public ThrowMode mode() { return mode; }
    public InteractionHand hand() { return hand; }
    public float power() { return power; }
    public float flickYaw() { return flickYaw; }
    public float flickPitch() { return flickPitch; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public boolean fullStack() { return fullStack; }

    public static void encode(ThrowMessage msg, FriendlyByteBuf buf)
    {
        buf.writeByte(msg.mode.ordinal());
        buf.writeBoolean(msg.hand == InteractionHand.OFF_HAND);
        buf.writeFloat(msg.power);
        buf.writeFloat(msg.flickYaw);
        buf.writeFloat(msg.flickPitch);
        buf.writeFloat(msg.yaw);
        buf.writeFloat(msg.pitch);
        buf.writeBoolean(msg.fullStack);
    }

    public static ThrowMessage decode(FriendlyByteBuf buf)
    {
        ThrowMode mode = ThrowMode.byId(buf.readByte());
        InteractionHand hand = buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        float power = buf.readFloat();
        float flickYaw = buf.readFloat();
        float flickPitch = buf.readFloat();
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();
        boolean fullStack = buf.readBoolean();
        return new ThrowMessage(mode, hand, power, flickYaw, flickPitch, yaw, pitch, fullStack);
    }

    public static void handle(ThrowMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                ServerThrowManager.onThrow(sender, msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
