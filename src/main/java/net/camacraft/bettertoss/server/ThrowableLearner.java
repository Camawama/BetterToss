package net.camacraft.bettertoss.server;

import net.camacraft.bettertoss.BTConfig;
import net.camacraft.bettertoss.BetterTossMod;
import net.camacraft.bettertoss.Throwables;
import net.camacraft.bettertoss.network.BTNet;
import net.camacraft.bettertoss.network.LearnedThrowablesMessage;
import net.camacraft.camafy.data.SavedDataUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Learns other mods' throwables from their own throws. Most of them extend a vanilla throwable item
 * class and are caught by {@link Throwables#isThrowable}'s class test, but some are a plain
 * {@code Item} whose use() throws a {@link ThrowableItemProjectile} the way vanilla throws an XP bottle
 * (Cardiac's life bottle: the owner, 2026-10-29, "I should be able to use better toss to throw the life
 * bottles"). When a right-click that Better Toss left alone throws, in that same tick, a
 * projectile owned by the player that shows the item used (a {@code ThrowableItemProjectile}, or another mod's
 * projectile that shows an item), and the player isn't
 * drawing the item (bows, spears and slingshots throw on release, later), the item is thrown that way:
 * it is charge-thrown from then on, saved with the world and synced to every client. Its first throw is
 * the item's own. The {@code not_throwable} tag and {@code throwableBlacklist} still win.
 */
public final class ThrowableLearner
{
    private static final String DATA_NAME = BetterTossMod.MODID + "_learned_throwables";

    /** A right-click Better Toss let through: which item, in which server tick. */
    private record Watch(Item item, long tick) {}

    private static final Map<UUID, Watch> WATCHES = new HashMap<>();

    private ThrowableLearner() {}

    /** A right-click no one cancelled, just before the item's own use() runs. */
    public static void onRightClickItem(ServerPlayer player, ItemStack stack)
    {
        if (!BTConfig.SERVER.enabled.get() || !BTConfig.SERVER.learnThrowables.get()) return;
        if (stack.isEmpty() || stack.getItem() instanceof TridentItem || Throwables.isThrowable(stack)
                || Throwables.isDenied(stack) || Throwables.throwsOnRelease(stack)) return;
        WATCHES.put(player.getUUID(), new Watch(stack.getItem(), tick(player.getServer())));
    }

    /** Every entity joining a server level. */
    public static void onEntityJoin(Entity entity)
    {
        // Any projectile that shows an item: a thrown-item projectile, or an arrow underneath that shows what was
        // thrown (MCreator's). A firework rocket is a boost, not a throw.
        if (WATCHES.isEmpty() || !(entity instanceof Projectile projectile) || !(entity instanceof ItemSupplier supplier)
                || entity instanceof FireworkRocketEntity) return;
        if (!(projectile.getOwner() instanceof ServerPlayer player)) return;
        Watch watch = WATCHES.remove(player.getUUID());
        if (watch == null || watch.tick() != tick(player.getServer()) || player.isUsingItem()) return;
        ItemStack shown = supplier.getItem();
        if (!shown.is(watch.item())) return;
        // Beyond thrown-item projectiles only an item with a right-click of its mod's own is learned: the game's
        // items that shoot something showing themselves are not throws.
        if (!(entity instanceof ThrowableItemProjectile) && watch.item().getClass().getName().startsWith("net.minecraft.")) return;
        if (!Throwables.learn(watch.item())) return;
        MinecraftServer server = player.getServer();
        data(server).add(BuiltInRegistries.ITEM.getKey(watch.item()));
        BetterTossMod.LOGGER.info("Better Toss: {} throws itself the way a snowball does ({}); it is charge-thrown from now on",
                BuiltInRegistries.ITEM.getKey(watch.item()), BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()));
        BTNet.CHANNEL.toAll(LearnedThrowablesMessage.of(Throwables.learned()));
    }

    /** The saved set becomes the server's at start. */
    public static void onServerStarted(MinecraftServer server)
    {
        List<Item> items = new ArrayList<>();
        for (ResourceLocation id : data(server).ids) {
            // Ids of mods that aren't loaded now stay saved for when they are.
            if (BuiltInRegistries.ITEM.containsKey(id)) items.add(BuiltInRegistries.ITEM.get(id));
        }
        Throwables.setLearned(items);
    }

    public static void onLogin(ServerPlayer player)
    {
        if (BTNet.CHANNEL.hasChannel(player)) BTNet.sendTo(player, LearnedThrowablesMessage.of(Throwables.learned()));
    }

    public static void onLogout(ServerPlayer player)
    {
        WATCHES.remove(player.getUUID());
    }

    public static void onServerStopped()
    {
        WATCHES.clear();
        Throwables.setLearned(List.of());
    }

    private static long tick(MinecraftServer server)
    {
        return server == null ? -1L : server.getTickCount();
    }

    private static Data data(MinecraftServer server)
    {
        return SavedDataUtil.getGlobal(server, DATA_NAME, Data::load, Data::new);
    }

    /** The learned items by id, with the overworld. */
    private static final class Data extends SavedData
    {
        private final Set<ResourceLocation> ids = new LinkedHashSet<>();

        void add(ResourceLocation id)
        {
            if (ids.add(id)) setDirty();
        }

        static Data load(CompoundTag tag)
        {
            Data data = new Data();
            ListTag list = tag.getList("items", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
                if (id != null) data.ids.add(id);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag)
        {
            ListTag list = new ListTag();
            for (ResourceLocation id : ids) list.add(StringTag.valueOf(id.toString()));
            tag.put("items", list);
            return tag;
        }
    }
}
