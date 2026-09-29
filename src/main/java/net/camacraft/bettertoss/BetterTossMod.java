package net.camacraft.bettertoss;

import com.mojang.logging.LogUtils;
import net.camacraft.bettertoss.network.BTNet;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Better Toss! - charge your throws, flick to curve them, steady your aim, fling items with the drop
 * key, and catch what other people throw at you.
 *
 * The mod never replaces projectile entities or items: a charged throw runs the item's own use()
 * on the server and rescales whatever projectile it spawned, so any mod's throwables work as-is.
 */
@Mod(BetterTossMod.MODID)
public class BetterTossMod
{
    public static final String MODID = "bettertoss";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BetterTossMod(FMLJavaModLoadingContext context)
    {
        BTNet.register();
        context.registerConfig(ModConfig.Type.SERVER, BTConfig.SERVER_SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, BTConfig.CLIENT_SPEC);
        BTConfig.registerMigrations(context.getModEventBus());
    }
}
