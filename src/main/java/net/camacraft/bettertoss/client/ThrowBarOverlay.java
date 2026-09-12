package net.camacraft.bettertoss.client;

import com.mojang.blaze3d.platform.Window;
import net.camacraft.bettertoss.BTConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the charge bar in the XP bar's slot (the way the horse jump bar replaces it): gold for an
 * item throw, green for a drop-key fling, purple for a caught projectile. The bar jitters with the
 * unsteady aim and brightens as focus builds; a full purple bar means "you're holding a catch".
 */
public final class ThrowBarOverlay
{
    private static final ResourceLocation GUI_ICONS = ResourceLocation.withDefaultNamespace("textures/gui/icons.png");

    private ThrowBarOverlay() {}

    public static boolean shouldShow()
    {
        return ClientThrowing.isCharging() || ClientCatchState.localHolding();
    }

    public static void render(GuiGraphics graphics, Window window, float partialTick)
    {
        int x = window.getGuiScaledWidth() / 2 - 91;
        int y = window.getGuiScaledHeight() - 32 + 3;

        if (ClientThrowing.isCharging()) {
            float value = ClientThrowing.chargeValue(partialTick);
            float focus = ClientThrowing.focus(partialTick);
            double t = ClientThrowing.chargeTicks() + partialTick;
            if (BTConfig.CLIENT.barShake.get()) {
                x += (int) Math.round(Math.sin(t * 2.6D) * 2.5D * (1.0D - focus));
            }
            int color;
            String hint;
            switch (ClientThrowing.mode()) {
                case DROP -> {
                    color = 0xFF7EE044;
                    hint = ClientThrowing.isFullStack() ? "bettertoss.hint.drop_stack" : "bettertoss.hint.drop";
                }
                case CAUGHT -> {
                    color = 0xFFB47CF0;
                    hint = "bettertoss.hint.caught_charge";
                }
                default -> {
                    color = 0xFFE8A33A;
                    hint = focus > 0.95F ? "bettertoss.hint.steady" : "bettertoss.hint.charge";
                }
            }
            color = lerpColor(focus, color, brighten(color, 0.3F));
            drawBackground(graphics, x, y);
            drawFill(graphics, x, y, value, color);
            drawHint(graphics, window, hint, brighten(color, 0.2F), false);
        } else if (ClientCatchState.localHolding()) {
            drawBackground(graphics, x, y);
            drawFill(graphics, x, y, 1.0F, 0xFF8E5FD8);
            drawHint(graphics, window, "bettertoss.hint.holding", 0xFFD9B8FF, true);
        }
    }

    private static void drawHint(GuiGraphics graphics, Window window, String key, int color, boolean flash)
    {
        if (!BTConfig.CLIENT.showHints.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (flash && mc.player != null && (mc.player.tickCount / 10) % 4 == 3) return;
        graphics.drawCenteredString(mc.font, Component.translatable(key),
                window.getGuiScaledWidth() / 2, window.getGuiScaledHeight() - 68, color);
    }

    private static void drawBackground(GuiGraphics graphics, int x, int y)
    {
        graphics.blit(GUI_ICONS, x, y, 0, 64, 182, 5);
    }

    /** A 5px fill with a highlight top row and shadow bottom row, matching the vanilla bar sprites. */
    private static void drawFill(GuiGraphics graphics, int x, int y, float fraction, int color)
    {
        int width = Math.round(Mth.clamp(fraction, 0.0F, 1.0F) * 182.0F);
        if (width <= 0) return;
        graphics.fill(x, y, x + width, y + 1, brighten(color, 0.45F));
        graphics.fill(x, y + 1, x + width, y + 4, color);
        graphics.fill(x, y + 4, x + width, y + 5, darken(color, 0.45F));
    }

    private static int lerpColor(float t, int from, int to)
    {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int brighten(int color, float amount)
    {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * (1.0F + amount)) + 30);
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * (1.0F + amount)) + 30);
        int b = Math.min(255, (int) ((color & 0xFF) * (1.0F + amount)) + 30);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, float amount)
    {
        int r = (int) (((color >> 16) & 0xFF) * (1.0F - amount));
        int g = (int) (((color >> 8) & 0xFF) * (1.0F - amount));
        int b = (int) ((color & 0xFF) * (1.0F - amount));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
