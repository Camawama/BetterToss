package net.camacraft.bettertoss.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Everything the client knows about other players' (and its own) throwing state: who is holding a
 * caught projectile and what it looks like, who is winding up, who just threw. Fed by sync packets
 * for others and directly by ClientThrowing for the local player, and read by the pose and HUD code.
 */
public final class ClientCatchState
{
    public static final class AnimState
    {
        public boolean charging;
        public double chargeStart = -1.0E9D;
        public double throwStart = -1.0E9D;
        public boolean offHand;
        public boolean holding;
        public double holdStart = -1.0E9D;
    }

    private static final Map<Integer, ItemStack> CAUGHT = new HashMap<>();
    private static final Map<Integer, AnimState> ANIM = new HashMap<>();
    private static long ticks;

    private ClientCatchState() {}

    /** A smooth client clock in ticks, for animation blending. */
    public static double now()
    {
        return ticks + Minecraft.getInstance().getPartialTick();
    }

    public static void tick(Minecraft mc)
    {
        ticks++;
        if (ticks % 200L == 0L && mc.level != null) {
            ANIM.keySet().removeIf(id -> mc.level.getEntity(id) == null);
            CAUGHT.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        }
    }

    public static boolean isHolding(Entity entity)
    {
        return CAUGHT.containsKey(entity.getId());
    }

    public static ItemStack display(Entity entity)
    {
        return CAUGHT.getOrDefault(entity.getId(), ItemStack.EMPTY);
    }

    public static boolean localHolding()
    {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && CAUGHT.containsKey(player.getId());
    }

    @Nullable
    public static AnimState anim(int entityId, boolean create)
    {
        AnimState state = ANIM.get(entityId);
        if (state == null && create) {
            state = new AnimState();
            ANIM.put(entityId, state);
        }
        return state;
    }

    public static void setCharging(int entityId, boolean offHand)
    {
        AnimState state = anim(entityId, true);
        state.charging = true;
        state.chargeStart = now();
        state.offHand = offHand;
    }

    public static void setThrown(int entityId)
    {
        AnimState state = anim(entityId, true);
        state.charging = false;
        state.throwStart = now();
    }

    public static void setIdle(int entityId)
    {
        AnimState state = anim(entityId, false);
        if (state != null) state.charging = false;
    }

    public static void setCaught(int entityId, ItemStack display)
    {
        CAUGHT.put(entityId, display);
        AnimState state = anim(entityId, true);
        if (!state.holding) state.holdStart = now();
        state.holding = true;
    }

    public static void clearCaught(int entityId)
    {
        CAUGHT.remove(entityId);
        AnimState state = anim(entityId, false);
        if (state != null) state.holding = false;
    }

    public static void clear()
    {
        CAUGHT.clear();
        ANIM.clear();
    }
}
