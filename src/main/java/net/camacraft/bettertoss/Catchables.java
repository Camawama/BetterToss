package net.camacraft.bettertoss;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Which projectiles can be caught, and how a caught one looks and flies when thrown back. */
public final class Catchables
{
    public static final TagKey<EntityType<?>> CATCHABLE = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(BetterTossMod.MODID, "catchable"));
    public static final TagKey<EntityType<?>> UNCATCHABLE = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(BetterTossMod.MODID, "uncatchable"));

    private static List<? extends String> blacklistSource;
    private static Set<ResourceLocation> blacklist = Set.of();
    private static List<? extends String> whitelistSource;
    private static Set<ResourceLocation> whitelist = Set.of();
    private static List<? extends String> displaySource;
    private static Map<ResourceLocation, Item> displayItems = Map.of();
    private static List<? extends String> speedSource;
    private static Map<ResourceLocation, Double> throwSpeeds = Map.of();

    private Catchables() {}

    public static boolean isCatchable(Projectile projectile)
    {
        EntityType<?> type = projectile.getType();
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (type.is(UNCATCHABLE) || (id != null && blacklist().contains(id))) return false;
        boolean explicit = type.is(CATCHABLE) || (id != null && whitelist().contains(id));
        if (explicit) return true;
        if (!whitelist().isEmpty()) return false;
        if (projectile instanceof FishingHook) return false;
        if (BTConfig.SERVER.blockArrows.get() && projectile instanceof AbstractArrow) return false;
        if (BTConfig.SERVER.blockHurtingProjectiles.get() && projectile instanceof AbstractHurtingProjectile) return false;
        return true;
    }

    /** The item shown in the catcher's hand (and used for particles); empty if nothing sensible exists. */
    public static ItemStack displayFor(Projectile projectile, CompoundTag saved)
    {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(projectile.getType());
        if (id != null) {
            Item override = displayItems().get(id);
            if (override != null && override != Items.AIR) return new ItemStack(override);
        }
        if (projectile instanceof ItemSupplier supplier) {
            ItemStack stack = supplier.getItem();
            if (!stack.isEmpty()) return stack.copyWithCount(1);
        }
        if (saved.contains("Trident", 10)) {
            ItemStack trident = ItemStack.of(saved.getCompound("Trident"));
            if (!trident.isEmpty()) return trident;
        }
        if (projectile instanceof AbstractArrow) {
            return new ItemStack(projectile.getType() == EntityType.SPECTRAL_ARROW ? Items.SPECTRAL_ARROW : Items.ARROW);
        }
        return ItemStack.EMPTY;
    }

    /** The speed (blocks/tick) a player's throw of this projectile kind has, before the power multiplier. */
    public static double baseSpeed(Projectile projectile)
    {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(projectile.getType());
        if (id != null) {
            Double override = throwSpeeds().get(id);
            if (override != null) return override;
        }
        if (projectile instanceof ThrownTrident) return 2.5D;
        if (projectile instanceof ThrownPotion) return 0.5D;
        if (projectile instanceof ThrownExperienceBottle) return 0.7D;
        if (projectile instanceof AbstractArrow) return 3.0D;
        return 1.5D;
    }

    public static SoundEvent throwSound(Projectile projectile)
    {
        EntityType<?> type = projectile.getType();
        if (type == EntityType.EGG) return SoundEvents.EGG_THROW;
        if (type == EntityType.ENDER_PEARL) return SoundEvents.ENDER_PEARL_THROW;
        if (type == EntityType.EXPERIENCE_BOTTLE) return SoundEvents.EXPERIENCE_BOTTLE_THROW;
        if (type == EntityType.POTION) {
            return projectile instanceof ItemSupplier s && s.getItem().is(Items.LINGERING_POTION)
                    ? SoundEvents.LINGERING_POTION_THROW : SoundEvents.SPLASH_POTION_THROW;
        }
        if (type == EntityType.TRIDENT) return SoundEvents.TRIDENT_THROW;
        if (projectile instanceof AbstractArrow) return SoundEvents.ARROW_SHOOT;
        return SoundEvents.SNOWBALL_THROW;
    }

    private static Set<ResourceLocation> blacklist()
    {
        List<? extends String> src = BTConfig.SERVER.catchBlacklist.get();
        if (src != blacklistSource) {
            blacklist = Throwables.parseIds(src);
            blacklistSource = src;
        }
        return blacklist;
    }

    private static Set<ResourceLocation> whitelist()
    {
        List<? extends String> src = BTConfig.SERVER.catchWhitelist.get();
        if (src != whitelistSource) {
            whitelist = Throwables.parseIds(src);
            whitelistSource = src;
        }
        return whitelist;
    }

    private static Map<ResourceLocation, Item> displayItems()
    {
        List<? extends String> src = BTConfig.SERVER.catchDisplayItems.get();
        if (src != displaySource) {
            Map<ResourceLocation, Item> out = new HashMap<>();
            for (String entry : src) {
                String[] parts = entry.split("=", 2);
                ResourceLocation type = parts.length == 2 ? ResourceLocation.tryParse(parts[0].trim()) : null;
                ResourceLocation item = parts.length == 2 ? ResourceLocation.tryParse(parts[1].trim()) : null;
                if (type == null || item == null || !ForgeRegistries.ITEMS.containsKey(item)) {
                    BetterTossMod.LOGGER.warn("[Better Toss] Ignoring malformed catch.displayItems entry: '{}'", entry);
                    continue;
                }
                out.put(type, ForgeRegistries.ITEMS.getValue(item));
            }
            displayItems = out;
            displaySource = src;
        }
        return displayItems;
    }

    private static Map<ResourceLocation, Double> throwSpeeds()
    {
        List<? extends String> src = BTConfig.SERVER.catchThrowSpeeds.get();
        if (src != speedSource) {
            Map<ResourceLocation, Double> out = new HashMap<>();
            for (String entry : src) {
                String[] parts = entry.split("=", 2);
                ResourceLocation type = parts.length == 2 ? ResourceLocation.tryParse(parts[0].trim()) : null;
                Double speed = null;
                if (parts.length == 2) {
                    try {
                        speed = Double.parseDouble(parts[1].trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (type == null || speed == null || speed <= 0.0D) {
                    BetterTossMod.LOGGER.warn("[Better Toss] Ignoring malformed catch.throwSpeeds entry: '{}'", entry);
                    continue;
                }
                out.put(type, speed);
            }
            throwSpeeds = out;
            speedSource = src;
        }
        return throwSpeeds;
    }
}
