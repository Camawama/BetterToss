package net.camacraft.bettertoss.client;

import net.camacraft.bettertoss.BTConfig;
import net.camacraft.bettertoss.BetterTossMod;
import net.camacraft.bettertoss.ThrowMath;
import net.camacraft.camafy.client.anim.Blink;
import net.camacraft.camafy.client.gui.hud.HudBar;
import net.camacraft.camafy.client.gui.hud.HudHint;
import net.camacraft.camafy.client.gui.hud.XpBarSlot;
import net.camacraft.camafy.util.color.Argb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the charge bar in the XP bar's slot (the way the horse jump bar replaces it): gold for an
 * item throw, green for a drop-key fling, purple for a caught projectile. The bar brightens as focus
 * builds (and, with barShake on, sways while the aim is unsteady); a full purple bar means "you're
 * holding a catch". The one-line tips above the hotbar only show with showHints on.
 * The slot is claimed through Camafy, which draws one mod's bar per frame.
 */
public final class ThrowBarOverlay
{
    /** Below Nice Catch's fishing bar: a cast or a fight in progress wins the slot. */
    private static final int SLOT_PRIORITY = 100;
    /** The "holding a catch" hint hides for the last 10 of every 40 ticks. */
    private static final Blink HOLD_FLASH = new Blink(40, 30);

    private ThrowBarOverlay() {}

    /** Claims the XP bar's slot; called once at client setup. */
    public static void register()
    {
        XpBarSlot.claim(ResourceLocation.fromNamespaceAndPath(BetterTossMod.MODID, "throw_bar"), SLOT_PRIORITY,
                new XpBarSlot.Claim()
                {
                    @Override
                    public boolean active()
                    {
                        return BTConfig.CLIENT.showBar.get() && shouldShow();
                    }

                    @Override
                    public void render(GuiGraphics graphics, int x, int y, float partialTick)
                    {
                        ThrowBarOverlay.render(graphics, x, y, partialTick);
                    }
                });
    }

    public static boolean shouldShow()
    {
        return ClientThrowing.isCharging() || ClientCatchState.localHolding();
    }

    /** {@code (x, y)} is the XP bar's top-left. */
    public static void render(GuiGraphics graphics, int x, int y, float partialTick)
    {
        if (ClientThrowing.isCharging()) {
            float value = ClientThrowing.chargeValue(partialTick);
            float focus = ClientThrowing.focus(partialTick);
            double t = ClientThrowing.chargeTicks() + partialTick;
            if (BTConfig.CLIENT.barShake.get()) {
                x += HudBar.jitter(t, 0.7D, 1.5D, 1.0D - focus); // a slow sway of a pixel or two
            }
            int color;
            String hint;
            float reach = 1.0F; // how much of the bar a heavy stack can actually use
            switch (ClientThrowing.mode()) {
                case DROP -> {
                    color = 0xFF7EE044;
                    Minecraft mc = Minecraft.getInstance();
                    int count = ClientThrowing.isFullStack() && mc.player != null
                            ? mc.player.getInventory().getSelected().getCount() : 1;
                    reach = (float) ThrowMath.stackWeightMultiplier(count);
                    hint = !ClientThrowing.isFullStack() ? "bettertoss.hint.drop"
                            : reach < 0.7F ? "bettertoss.hint.drop_stack_heavy" : "bettertoss.hint.drop_stack";
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
            color = Argb.lerpRgb(focus, color, Argb.brighten(color, 0.3F));
            HudBar.background(graphics, x, y, HudBar.Style.XP_BEVEL);
            // A heavy stack can't use the whole bar: shade the unreachable part and cap the fill.
            HudBar.cap(graphics, x, y, reach, color);
            HudBar.fill(graphics, x, y, value * reach, color, HudBar.Style.XP_BEVEL);
            drawHint(graphics, hint, Argb.brighten(color, 0.2F), false);
        } else if (ClientCatchState.localHolding()) {
            HudBar.draw(graphics, x, y, 1.0F, 0xFF8E5FD8, HudBar.Style.XP_BEVEL);
            drawHint(graphics, "bettertoss.hint.holding", 0xFFD9B8FF, true);
        }
    }

    private static void drawHint(GuiGraphics graphics, String key, int color, boolean flash)
    {
        if (!BTConfig.CLIENT.showHints.get()) return;
        Component text = Component.translatable(key);
        Minecraft mc = Minecraft.getInstance();
        if (flash && mc.player != null) {
            HudHint.draw(graphics, text, color, HOLD_FLASH, mc.player.tickCount);
        } else {
            HudHint.draw(graphics, text, color);
        }
    }
}
