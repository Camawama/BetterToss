package net.camacraft.bettertoss.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.bettertoss.BetterTossMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Keybinds and raw key polling. The catch key defaults to the right mouse button (the "reach out"
 * button), which vanilla's use key also owns - stacked mappings on one button do not reliably both
 * see state changes, so every "is it held" question here is answered by GLFW directly.
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
        return rawDown(mc, mc.options.keyUse.getKey());
    }

    /** True while vanilla's drop key is physically held and no screen is open. */
    public static boolean dropDown(Minecraft mc)
    {
        return rawDown(mc, mc.options.keyDrop.getKey());
    }

    /** True while the catch key is physically held and no screen is open. */
    public static boolean catchDown(Minecraft mc)
    {
        return rawDown(mc, CATCH.getKey());
    }

    public static boolean rawDown(Minecraft mc, InputConstants.Key key)
    {
        if (mc.screen != null || mc.player == null || key == InputConstants.UNKNOWN) return false;
        long window = mc.getWindow().getWindow();
        return switch (key.getType()) {
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            default -> false;
        };
    }

    public static boolean matchesMouse(InputConstants.Key key, int button)
    {
        return key.getType() == InputConstants.Type.MOUSE && key.getValue() == button;
    }

    public static boolean matchesKey(InputConstants.Key key, int keyCode, int scanCode)
    {
        return switch (key.getType()) {
            case KEYSYM -> key.getValue() == keyCode;
            case SCANCODE -> key.getValue() == scanCode;
            default -> false;
        };
    }
}
