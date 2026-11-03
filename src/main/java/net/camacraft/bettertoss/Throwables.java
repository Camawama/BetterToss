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
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which items the charge system takes over. Five layers, so other mods' throwables work without
 * anyone having to know about this mod: the item tags, the config lists, (by default) anything
 * that extends a vanilla throwable item class, (by default) every item some thrown-item projectile
 * of the pack shows, found at world start before anyone throws one ({@code server.ThrowableScan}:
 * IceCaves' ice ball), and (by default) any item the server has seen throw itself the way vanilla
 * throws a snowball ({@code server.ThrowableLearner}: a plain item, such as Cardiac's life bottle,
 * whose own use() throws a projectile showing that same item).
 */
public final class Throwables
{
    public static final TagKey<Item> THROWABLE = ItemTags.create(ResourceLocation.fromNamespaceAndPath(BetterTossMod.MODID, "throwable"));
    public static final TagKey<Item> NOT_THROWABLE = ItemTags.create(ResourceLocation.fromNamespaceAndPath(BetterTossMod.MODID, "not_throwable"));

    private static final IdMatcher.Cached<Item> ALLOW = IdMatcher.cached(BuiltInRegistries.ITEM,
            "Better Toss throwableItems", () -> BTConfig.SERVER.throwableItems.get());
    private static final IdMatcher.Cached<Item> DENY = IdMatcher.cached(BuiltInRegistries.ITEM,
            "Better Toss throwableBlacklist", () -> BTConfig.SERVER.throwableBlacklist.get());

    /**
     * Items found by the start-up scan or learned from their own throws; the server's set, and on a
     * client the copy the server synced.
     */
    private static final Set<Item> LEARNED = ConcurrentHashMap.newKeySet();

    private Throwables() {}

    public static boolean isThrowable(ItemStack stack)
    {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        // Vanilla throws a trident itself (its draw, Loyalty, Riptide); it is left to vanilla unless asked.
        if (item instanceof TridentItem && !BTConfig.SERVER.tridents.get()) return false;
        if (stack.is(NOT_THROWABLE) || DENY.matches(item)) return false;
        if (knownByItself(stack)) return true;
        return BTConfig.SERVER.learnThrowables.get() && LEARNED.contains(item);
    }

    /**
     * Throwable by the tag, the config list or a vanilla throwable class alone: what {@link #isThrowable}
     * answers before the start-up scan and the learner have added anything (the scan asks this, so an item
     * it finds is reported as its own find whatever an earlier world's throws taught).
     */
    public static boolean knownByItself(ItemStack stack)
    {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (stack.is(THROWABLE) || ALLOW.matches(item)) return true;
        if (BTConfig.SERVER.detectThrowableClasses.get()) {
            return item instanceof SnowballItem || item instanceof EggItem || item instanceof EnderpearlItem
                    || item instanceof ExperienceBottleItem || item instanceof ThrowablePotionItem
                    || item instanceof TridentItem;
        }
        return false;
    }

    /** Never charge-thrown: the not_throwable tag or throwableBlacklist. */
    public static boolean isDenied(ItemStack stack)
    {
        return stack.is(NOT_THROWABLE) || DENY.matches(stack.getItem());
    }

    /** Adds an item learned from its own throw; false if it was known already. */
    public static boolean learn(Item item)
    {
        return LEARNED.add(item);
    }

    /** Adds the start-up scan's finds (the saved set stays what it was). */
    public static void learnAll(Collection<Item> items)
    {
        LEARNED.addAll(items);
    }

    /** Replaces the learned set (the server's saved one at start, a client's on the server's sync, empty on leaving). */
    public static void setLearned(Collection<Item> items)
    {
        LEARNED.clear();
        LEARNED.addAll(items);
    }

    public static Set<Item> learned()
    {
        return Set.copyOf(LEARNED);
    }

    /**
     * Items vanilla already throws on release after a hold: tridents (when {@code tridents} takes
     * them over), and modded spears or javelins that draw like one (or like a bow). A quick click is
     * no throw for these, so they charge from the first tick. Not judged by use duration: splash
     * potions inherit a potion's drinking time.
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
