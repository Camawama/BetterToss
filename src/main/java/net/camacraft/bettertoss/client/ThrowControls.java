package net.camacraft.bettertoss.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.bettertoss.BetterTossMod;
import net.camacraft.camafy.client.input.Keys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Keybinds and raw key polling. The catch key defaults to the right mouse button (the "reach out"
 * button), which vanilla's use key also owns - stacked mappings on one button do not reliably both
 * see state changes, so every "is it held" question here is asked of the device (Camafy's Keys).
 */
public final class ThrowControls
{
    public static final KeyMapping CATCH = new KeyMapping(
            "key." + BetterTossMod.MODID + ".catch", KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, "key.categories." + BetterTossMod.MODID);

    private ThrowControls() {}

    /** True while vanilla's use key is physically held and no screen is open. */
    public static boolean useDown(Minecraft mc)
    {
        return Keys.isHeldInGame(mc.options.keyUse);
    }

    /** True while vanilla's drop key is physically held and no screen is open. */
    public static boolean dropDown(Minecraft mc)
    {
        return Keys.isHeldInGame(mc.options.keyDrop);
    }

    /** True while the catch key is physically held and no screen is open. */
    public static boolean catchDown(Minecraft mc)
    {
        return Keys.isHeldInGame(CATCH);
    }
}
