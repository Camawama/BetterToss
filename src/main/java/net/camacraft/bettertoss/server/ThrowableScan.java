package net.camacraft.bettertoss.server;

import net.camacraft.bettertoss.BTConfig;
import net.camacraft.bettertoss.BetterTossMod;
import net.camacraft.bettertoss.Throwables;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds other mods' hand-thrown items at world start, before anyone throws one (the owner, 2026-10-31:
 * "I thought better toss would work with any thrown projectile by default? It seems like I keep finding
 * more things that need to be allowed to throw with better toss. now it's one of my backport mods that
 * adds the item ice_caves:ice_ball"). {@link ThrowableLearner} only learns such an item from its first
 * throw, so that throw was never charged.
 *
 * <p>A thrown item is a {@link ThrowableItemProjectile} that shows the item it was (a snowball, an XP
 * bottle, IceCaves' ice ball). So every entity type of the misc category (where projectiles live) is
 * created once in the overworld, never added to it, and when it is such a projectile the item it shows
 * by default is a candidate. The candidate is accepted when it is plainly an item thrown by hand: not a
 * block, not food, nothing held or drawn ({@link UseAnim#NONE}, no use duration), and its own class (or
 * one between it and {@link Item}) overrides {@code use(Level, Player, InteractionHand)}, so a right-click
 * does something of its own; matched by parameter and return types, never by name, because production
 * runs on SRG names. That keeps out a mob's spit whose projectile shows some other item (the item has no
 * use of its own, or is food). The {@code not_throwable} tag and {@code throwableBlacklist} still win,
 * items the tags, lists or vanilla classes already cover are left as they are, and the finds aren't saved:
 * the scan runs again at every start ({@code scanThrowableProjectiles}).</p>
 */
public final class ThrowableScan
{
    private static volatile List<Item> found = List.of();
    private static volatile String summary = "not run";

    private ThrowableScan() {}

    /** The items the last scan found (none before a scan, or with the switch off). */
    public static List<Item> found()
    {
        return found;
    }

    /** The found items' ids, for tests and the log. */
    public static List<ResourceLocation> foundIds()
    {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Item item : found) ids.add(BuiltInRegistries.ITEM.getKey(item));
        return ids;
    }

    /** One line on what the last scan did. */
    public static String summary()
    {
        return summary;
    }

    /**
     * Scans the entity registry (see the class notes) and answers the hand-thrown items it found that
     * Better Toss didn't know by itself. The caller adds them to the known set. Never throws.
     */
    public static List<Item> run(MinecraftServer server)
    {
        found = List.of();
        if (!BTConfig.SERVER.enabled.get() || !BTConfig.SERVER.scanThrowableProjectiles.get()) {
            summary = "off";
            return found;
        }
        ServerLevel level = server.overworld();
        if (level == null) {
            summary = "no overworld";
            return found;
        }
        long start = System.nanoTime();
        Set<Item> finds = new LinkedHashSet<>();
        List<String> known = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        int types = 0, failed = 0, projectiles = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.getCategory() != MobCategory.MISC) continue;
            types++;
            Item item;
            try {
                Entity entity = type.create(level);
                if (!(entity instanceof ThrowableItemProjectile projectile)) {
                    if (entity != null) entity.discard();
                    continue;
                }
                projectiles++;
                item = projectile.getItem().getItem();
                entity.discard();
            } catch (Throwable t) {
                failed++;
                continue;
            }
            String id = String.valueOf(BuiltInRegistries.ITEM.getKey(item));
            if (finds.contains(item)) continue;
            String why = rejection(item);
            if (why == null) {
                finds.add(item);
            } else if (why.isEmpty()) {
                known.add(id);
            } else {
                rejected.add(id + " (" + why + ")");
            }
        }
        found = Collections.unmodifiableList(new ArrayList<>(finds));
        summary = String.format("%d misc entity types looked at, %d thrown-item projectiles, %d items found: %s;"
                        + " %d known already: %s; %d not thrown by hand: %s; %d types failed to construct; %.1f ms",
                types, projectiles, finds.size(), foundIds(), known.size(), known, rejected.size(), rejected, failed,
                (System.nanoTime() - start) / 1.0e6);
        BetterTossMod.LOGGER.info("Better Toss: {}", summary);
        return found;
    }

    /**
     * Why {@code item} isn't a find: null when it is one, "" when Better Toss knows it by itself (the tag,
     * the list or a vanilla class), else the reason it isn't an item thrown by hand.
     */
    static String rejection(Item item)
    {
        if (item == null || item == Items.AIR) return "air";
        if (item instanceof TridentItem) return "a trident";
        if (item instanceof BlockItem) return "a block";
        ItemStack stack = new ItemStack(item);
        if (Throwables.isDenied(stack)) return "denied";
        if (Throwables.knownByItself(stack)) return "";
        if (item.isEdible()) return "food";
        UseAnim anim = stack.getUseAnimation();
        if (anim != UseAnim.NONE) return "drawn or held (" + anim + ")";
        if (stack.getUseDuration() != 0) return "held for " + stack.getUseDuration() + " ticks";
        if (!overridesUse(item.getClass())) return "no use of its own";
        return null;
    }

    /**
     * Whether {@code cls}, or a class between it and {@link Item}, declares its own
     * {@code InteractionResultHolder use(Level, Player, InteractionHand)}: matched by the parameter and
     * return types (the name is {@code m_7203_} in production).
     */
    static boolean overridesUse(Class<?> cls)
    {
        for (Class<?> c = cls; c != null && c != Item.class && c != Object.class; c = c.getSuperclass()) {
            Method[] methods;
            try {
                methods = c.getDeclaredMethods();
            } catch (Throwable t) {
                return false; // a class whose methods can't be listed (a missing optional dependency) is no throwable
            }
            for (Method m : methods) {
                if (m.getParameterCount() != 3 || m.getReturnType() != InteractionResultHolder.class) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p[0] == Level.class && p[1] == Player.class && p[2] == InteractionHand.class) return true;
            }
        }
        return false;
    }
}
