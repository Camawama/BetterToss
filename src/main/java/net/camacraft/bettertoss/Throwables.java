package net.camacraft.bettertoss;

import net.camacraft.camafy.config.IdMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;

import javax.annotation.Nullable;

/**
 * Which items the charge system takes over. Three layers, so other mods' throwables work without
 * anyone having to know about this mod: the item tags, the config lists, and (by default) anything
 * that extends a vanilla throwable item class.
 */
public final class Throwables
{
    public static final TagKey<Item> THROWABLE = ItemTags.create(ResourceLocation.fromNamespaceAndPath(BetterTossMod.MODID, "throwable"));
    public static final TagKey<Item> NOT_THROWABLE = ItemTags.create(ResourceLocation.fromNamespaceAndPath(BetterTossMod.MODID, "not_throwable"));

    private static final IdMatcher.Cached<Item> ALLOW = IdMatcher.cached(BuiltInRegistries.ITEM,
            "Better Toss throwableItems", () -> BTConfig.SERVER.throwableItems.get());
    private static final IdMatcher.Cached<Item> DENY = IdMatcher.cached(BuiltInRegistries.ITEM,
            "Better Toss throwableBlacklist", () -> BTConfig.SERVER.throwableBlacklist.get());

    private Throwables() {}

    public static boolean isThrowable(ItemStack stack)
    {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (stack.is(NOT_THROWABLE) || DENY.matches(item)) return false;
        if (stack.is(THROWABLE) || ALLOW.matches(item)) return true;
        if (BTConfig.SERVER.detectThrowableClasses.get()) {
            return item instanceof SnowballItem || item instanceof EggItem || item instanceof EnderpearlItem
                    || item instanceof ExperienceBottleItem || item instanceof ThrowablePotionItem
                    || item instanceof TridentItem;
        }
        return false;
    }

    /**
     * Items vanilla already throws on release after a hold: tridents, and modded spears or javelins
     * that draw like one (or like a bow). A quick click is no throw for these, so they charge from
     * the first tick. Not judged by use duration: splash potions inherit a potion's drinking time.
     */
    public static boolean throwsOnRelease(ItemStack stack)
    {
        if (stack.getItem() instanceof TridentItem) return true;
        UseAnim anim = stack.getUseAnimation();
        return anim == UseAnim.SPEAR || anim == UseAnim.BOW;
    }

    /** The hand holding a throwable, main hand first; null if neither does. */
    @Nullable
    public static InteractionHand findThrowableHand(Player player)
    {
        if (isThrowable(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (isThrowable(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }
}
