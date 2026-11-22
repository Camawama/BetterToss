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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.io.InputStream;
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
 *
 * <p>Some thrown items no entity type names: a block item that is thrown instead of placed (Spelunkery's
 * glowsticks), many items sharing one projectile type (its pebbles: the type shows only one of them), a projectile
 * that is an arrow underneath (the rocks of Spelunker's Charm, as every MCreator "ranged item"). So a second pass
 * ({@code scanItemUses}) reads the right-click code of every item whose class is not the game's own
 * ({@link UseAnalysis}: the class files as data, nothing loaded or run) and takes the item when that code makes a
 * projectile and the item is a thing thrown, not a thing that shoots: it stacks, has no durability, is not food and
 * is not held or drawn. A projectile that is a firework rocket is a boost, not a throw.</p>
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
        int byType = finds.size();
        double typesMs = (System.nanoTime() - start) / 1.0e6;
        String uses = scanUses(finds);
        found = Collections.unmodifiableList(new ArrayList<>(finds));
        summary = String.format("%d misc entity types looked at, %d thrown-item projectiles, %d items found by them;"
                        + " %d known already: %s; %d not thrown by hand: %s; %d types failed to construct; %.1f ms. %s"
                        + " Found in all: %s",
                types, projectiles, byType, known.size(), known, rejected.size(), rejected, failed, typesMs, uses, foundIds());
        BetterTossMod.LOGGER.info("Better Toss: {}", summary);
        return found;
    }

    private static final String FIREWORK = "net/minecraft/world/entity/projectile/FireworkRocketEntity";
    private static final String THROWN_ITEM = "net/minecraft/world/entity/projectile/ThrowableItemProjectile";

    /**
     * The second pass (see the class notes): adds to {@code finds} every item whose own right-click code makes a
     * projectile and that is a thing thrown by hand. Answers one line on what it did. Never throws.
     */
    private static String scanUses(Set<Item> finds)
    {
        if (!BTConfig.SERVER.scanItemUses.get()) return "Item uses not read (scanItemUses off).";
        long start = System.nanoTime();
        int classes = 0, throwing = 0, added = 0;
        List<String> rejected = new ArrayList<>();
        try {
            UseAnalysis analysis = new UseAnalysis(classFiles(), ThrowableScan::gameProjectile);
            Set<Class<?>> seen = new java.util.HashSet<>();
            for (Item item : BuiltInRegistries.ITEM) {
                Class<?> cls = item.getClass();
                String name = cls.getName().replace('.', '/');
                if (UseAnalysis.isGameClass(name)) continue; // its use is the game's own: the class test covers those
                UseAnalysis.Result result = analysis.analyse(name);
                if (seen.add(cls)) {
                    classes++;
                    if (result != null && result.throwsSomething()) throwing++;
                }
                if (result == null || !result.throwsSomething() || finds.contains(item)) continue;
                // the item itself flies (its projectile shows an item) and leaves the hand (its use takes from the stack)
                boolean thrownItself = analysis.isA(result.projectile(), THROWN_ITEM) && analysis.takesFromStack(name);
                String why = analysis.isA(result.projectile(), FIREWORK) ? "its projectile is a firework rocket" : useRejection(item, thrownItself);
                if (why == null) {
                    finds.add(item);
                    added++;
                } else if (!why.isEmpty()) {
                    rejected.add(BuiltInRegistries.ITEM.getKey(item) + " (" + why + ")");
                }
            }
        } catch (Throwable t) {
            BetterTossMod.LOGGER.warn("Better Toss: reading the items' right-click code stopped early", t);
        }
        return String.format("%d item classes' right-click code read, %d make a projectile, %d more items found;"
                + " %d not taken: %s; %.1f ms.", classes, throwing, added, rejected.size(), rejected, (System.nanoTime() - start) / 1.0e6);
    }

    /**
     * A class's file by its internal name, from wherever the mods' classes come from: the class loader's resources,
     * and failing that the module of the mod layer that holds the class's package. Null where there is none.
     */
    private static java.util.function.Function<String, byte[]> classFiles()
    {
        ClassLoader loader = ThrowableScan.class.getClassLoader();
        java.util.Map<String, Module> byPackage = new java.util.HashMap<>();
        boolean[] indexed = {false};
        return name -> {
            String file = name + ".class";
            try (InputStream in = loader.getResourceAsStream(file)) {
                if (in != null) return in.readAllBytes();
            } catch (Throwable t) {
                // try the module below
            }
            try {
                if (!indexed[0]) {
                    indexed[0] = true;
                    ModuleLayer layer = ThrowableScan.class.getModule().getLayer();
                    if (layer != null) {
                        for (Module module : layer.modules()) {
                            for (String pkg : module.getPackages()) byPackage.putIfAbsent(pkg, module);
                        }
                    }
                }
                int slash = name.lastIndexOf('/');
                Module module = slash < 0 ? null : byPackage.get(name.substring(0, slash).replace('/', '.'));
                if (module == null) return null;
                try (InputStream in = module.getResourceAsStream(file)) {
                    return in == null ? null : in.readAllBytes();
                }
            } catch (Throwable t) {
                return null;
            }
        };
    }

    /** Whether one of the game's own classes is a projectile: asked of the class itself, and only of entity classes. */
    private static Boolean gameProjectile(String internalName)
    {
        if (!internalName.startsWith("net/minecraft/world/entity/")) return false;
        try {
            return Projectile.class.isAssignableFrom(Class.forName(internalName.replace('/', '.'), false, ThrowableScan.class.getClassLoader()));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Why an item whose right-click code makes a projectile isn't a find: null when it is one, "" when Better Toss
     * knows it by itself, else the reason. Unlike {@link #rejection} a block item passes: its class has a throw of
     * its own (aimed at a block it is still placed, as the mod has it).
     *
     * <p>A thing that shoots (a wand, a cannon, a slingshot) is told from a thing thrown by its stack: it doesn't
     * stack, or wears out. But when the projectile the code makes is a thrown-item projectile and the code takes the
     * item from the stack, the item itself is what flies, however it stacks: a throwing disc, knife or axe with
     * durability that comes back to the hand is a thing thrown ({@code thrownItemProjectile}), so those two tests are
     * skipped for it. A tool that casts a thrown-item projectile on a line and keeps it in the hand (Spelunkery's pick
     * on a stick) takes nothing from the stack and stays a thing that shoots.</p>
     */
    static String useRejection(Item item, boolean thrownItemProjectile)
    {
        if (item == null || item == Items.AIR) return "air";
        if (item instanceof TridentItem) return "a trident";
        ItemStack stack = new ItemStack(item);
        if (Throwables.isDenied(stack)) return "denied";
        if (Throwables.knownByItself(stack)) return "";
        if (!thrownItemProjectile) {
            if (stack.getMaxStackSize() <= 1) return "doesn't stack: a thing that shoots, not a thing thrown";
            if (stack.isDamageableItem()) return "has durability";
        }
        if (item.isEdible()) return "food";
        UseAnim anim = stack.getUseAnimation();
        if (anim != UseAnim.NONE) return "drawn or held (" + anim + ")";
        if (stack.getUseDuration() != 0) return "held for " + stack.getUseDuration() + " ticks";
        return null;
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
