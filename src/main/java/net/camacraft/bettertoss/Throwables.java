package net.camacraft.bettertoss;

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
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which items the charge system takes over. Three layers, so other mods' throwables work without
 * anyone having to know about this mod: the item tags, the config lists, and (by default) anything
 * that extends a vanilla throwable item class.
 */
public final class Throwables
{
    public static final TagKey<Item> THROWABLE = ItemTags.create(ResourceLocation.fromNamespaceAndPath(BetterTossMod.MODID, "throwable"));
    public static final TagKey<Item> NOT_THROWABLE = ItemTags.create(ResourceLocation.fromNamespaceAndPath(BetterTossMod.MODID, "not_throwable"));

    private static List<? extends String> allowSource;
    private static Set<ResourceLocation> allow = Set.of();
    private static List<? extends String> denySource;
    private static Set<ResourceLocation> deny = Set.of();

    private Throwables() {}

    public static boolean isThrowable(ItemStack stack)
    {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (stack.is(NOT_THROWABLE) || (id != null && deny().contains(id))) return false;
        if (stack.is(THROWABLE) || (id != null && allow().contains(id))) return true;
        if (BTConfig.SERVER.detectThrowableClasses.get()) {
            return item instanceof SnowballItem || item instanceof EggItem || item instanceof EnderpearlItem
                    || item instanceof ExperienceBottleItem || item instanceof ThrowablePotionItem
                    || item instanceof TridentItem;
        }
        return false;
    }

    /** The hand holding a throwable, main hand first; null if neither does. */
    @Nullable
    public static InteractionHand findThrowableHand(Player player)
    {
        if (isThrowable(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (isThrowable(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }

    private static Set<ResourceLocation> allow()
    {
        List<? extends String> src = BTConfig.SERVER.throwableItems.get();
        if (src != allowSource) {
            allow = parseIds(src);
            allowSource = src;
        }
        return allow;
    }

    private static Set<ResourceLocation> deny()
    {
        List<? extends String> src = BTConfig.SERVER.throwableBlacklist.get();
        if (src != denySource) {
            deny = parseIds(src);
            denySource = src;
        }
        return deny;
    }

    static Set<ResourceLocation> parseIds(List<? extends String> list)
    {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : list) {
            ResourceLocation id = ResourceLocation.tryParse(s.trim());
            if (id != null) out.add(id);
            else BetterTossMod.LOGGER.warn("[Better Toss] Ignoring malformed id in config: '{}'", s);
        }
        return out;
    }
}
