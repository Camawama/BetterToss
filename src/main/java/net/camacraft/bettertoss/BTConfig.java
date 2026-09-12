package net.camacraft.bettertoss;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Server config holds the gameplay rules and is synced to every client on join; client config holds
 * feel, visuals and input timing that only that player cares about.
 */
public class BTConfig
{
    /** How the power bar moves while the button is held. */
    public enum ChargeMode { OSCILLATE, RAMP }

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        var serverPair = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = serverPair.getKey();
        SERVER_SPEC = serverPair.getValue();
        var clientPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getKey();
        CLIENT_SPEC = clientPair.getValue();
    }

    /** True once the (synced) server config is available; false on the title screen. */
    public static boolean serverLoaded()
    {
        return SERVER_SPEC.isLoaded();
    }

    private static boolean isString(Object o)
    {
        return o instanceof String;
    }

    public static class Server
    {
        public final ForgeConfigSpec.BooleanValue enabled;

        public final ForgeConfigSpec.BooleanValue chargedThrowsEnabled;
        public final ForgeConfigSpec.DoubleValue minPowerMultiplier;
        public final ForgeConfigSpec.DoubleValue maxPowerMultiplier;
        public final ForgeConfigSpec.IntValue throwCooldownTicks;
        public final ForgeConfigSpec.BooleanValue inheritShooterMotion;
        public final ForgeConfigSpec.BooleanValue detectThrowableClasses;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> throwableItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> throwableBlacklist;

        public final ForgeConfigSpec.BooleanValue flickEnabled;
        public final ForgeConfigSpec.DoubleValue maxFlickDegrees;
        public final ForgeConfigSpec.DoubleValue flickSpeedBonus;

        public final ForgeConfigSpec.BooleanValue focusEnabled;
        public final ForgeConfigSpec.IntValue focusTicks;
        public final ForgeConfigSpec.DoubleValue maxInaccuracyDegrees;
        public final ForgeConfigSpec.DoubleValue minInaccuracyDegrees;

        public final ForgeConfigSpec.BooleanValue dropThrowEnabled;
        public final ForgeConfigSpec.DoubleValue dropThrowMinSpeed;
        public final ForgeConfigSpec.DoubleValue dropThrowMaxSpeed;
        public final ForgeConfigSpec.BooleanValue stackWeightEnabled;
        public final ForgeConfigSpec.IntValue stackWeightReference;
        public final ForgeConfigSpec.DoubleValue fullStackSpeedMultiplier;
        public final ForgeConfigSpec.DoubleValue stackWeightCurve;

        public final ForgeConfigSpec.BooleanValue catchEnabled;
        public final ForgeConfigSpec.DoubleValue catchRadius;
        public final ForgeConfigSpec.DoubleValue catchConeDegrees;
        public final ForgeConfigSpec.BooleanValue allowSelfCatch;
        public final ForgeConfigSpec.BooleanValue requireEmptyHand;
        public final ForgeConfigSpec.BooleanValue catchWhileUsingItem;
        public final ForgeConfigSpec.BooleanValue lockInputWhileHolding;
        public final ForgeConfigSpec.IntValue catchCooldownTicks;
        public final ForgeConfigSpec.DoubleValue rethrowSpeedMultiplier;
        public final ForgeConfigSpec.BooleanValue dropOnDeath;
        public final ForgeConfigSpec.BooleanValue blockArrows;
        public final ForgeConfigSpec.BooleanValue blockHurtingProjectiles;
        public final ForgeConfigSpec.BooleanValue allowReturningTridents;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> catchBlacklist;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> catchWhitelist;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> catchDisplayItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> catchThrowSpeeds;

        Server(ForgeConfigSpec.Builder b)
        {
            b.push("general");
            enabled = b.comment("Master switch. False restores vanilla throwing and dropping and disables catching.")
                    .define("enabled", true);
            b.pop();

            b.push("throwing");
            chargedThrowsEnabled = b.comment("Hold right-click with a throwable to charge the throw; a quick tap is a weak toss.")
                    .define("chargedThrowsEnabled", true);
            minPowerMultiplier = b.comment("Velocity multiplier (relative to the vanilla throw) at an empty bar - what a quick tap throws with.")
                    .defineInRange("minPowerMultiplier", 0.35D, 0.05D, 2.0D);
            maxPowerMultiplier = b.comment("Velocity multiplier at a full bar.")
                    .defineInRange("maxPowerMultiplier", 1.6D, 0.1D, 6.0D);
            throwCooldownTicks = b.comment("Ticks after any throw before another charge can start.")
                    .defineInRange("throwCooldownTicks", 4, 0, 100);
            inheritShooterMotion = b.comment("Add the thrower's own velocity to the projectile, like vanilla does.")
                    .define("inheritShooterMotion", true);
            detectThrowableClasses = b.comment("Treat any item extending the vanilla throwable item classes (snowball, egg, ender pearl, XP bottle, splash/lingering potion, trident) as throwable - this is what makes most modded throwables work out of the box.")
                    .define("detectThrowableClasses", true);
            throwableItems = b.comment("Extra items to treat as throwables, by registry name (e.g. \"somemod:throwing_knife\"). The item tag bettertoss:throwable does the same via datapacks.")
                    .defineListAllowEmpty("throwableItems", List.<String>of(), BTConfig::isString);
            throwableBlacklist = b.comment("Items that must never be charge-thrown, by registry name. The item tag bettertoss:not_throwable does the same.")
                    .defineListAllowEmpty("throwableBlacklist", List.<String>of(), BTConfig::isString);
            b.pop();

            b.push("flick");
            flickEnabled = b.comment("Flicking the mouse as you release adds that motion to the throw (flick up to lob it, sideways to curve it).")
                    .define("enabled", true);
            maxFlickDegrees = b.comment("Hard cap on how many degrees a flick can bend the throw direction.")
                    .defineInRange("maxFlickDegrees", 25.0D, 0.0D, 90.0D);
            flickSpeedBonus = b.comment("Extra velocity a full-strength flick adds, as a fraction (0.15 = up to +15%).")
                    .defineInRange("speedBonus", 0.15D, 0.0D, 2.0D);
            b.pop();

            b.push("focus");
            focusEnabled = b.comment("Throws start shaky and steady up the longer the button is held; hold time drives accuracy.")
                    .define("enabled", true);
            focusTicks = b.comment("Ticks of holding to reach a fully steady, accurate throw.")
                    .defineInRange("focusTicks", 40, 1, 400);
            maxInaccuracyDegrees = b.comment("Random spread (degrees) of a throw released the instant the button is pressed.")
                    .defineInRange("maxInaccuracyDegrees", 7.0D, 0.0D, 45.0D);
            minInaccuracyDegrees = b.comment("Random spread (degrees) of a fully steadied throw.")
                    .defineInRange("minInaccuracyDegrees", 0.25D, 0.0D, 45.0D);
            b.pop();

            b.push("dropThrow");
            dropThrowEnabled = b.comment("Holding the drop key (Q) charges a fling of the selected item; a tap still drops it normally.")
                    .define("enabled", true);
            dropThrowMinSpeed = b.comment("Fling speed (blocks/tick) at an empty bar. Vanilla drops travel at 0.3.")
                    .defineInRange("minSpeed", 0.35D, 0.05D, 5.0D);
            dropThrowMaxSpeed = b.comment("Fling speed (blocks/tick) at a full bar.")
                    .defineInRange("maxSpeed", 1.3D, 0.05D, 5.0D);
            stackWeightEnabled = b.comment("Bigger stacks are heavier: flinging a whole stack (Ctrl+Q) travels less far than flinging a single item.")
                    .define("stackWeight", true);
            stackWeightReference = b.comment("The stack count that is treated as \"fully heavy\". Counts above it are all equally heavy; a single item is never penalised.")
                    .defineInRange("stackWeightReference", 64, 2, 64);
            fullStackSpeedMultiplier = b.comment("Speed multiplier for a fully heavy stack (1 = no penalty). Everything between one item and the reference count is interpolated.")
                    .defineInRange("fullStackSpeedMultiplier", 0.35D, 0.05D, 1.0D);
            stackWeightCurve = b.comment("Shape of the penalty between one item and a full stack. 1 = linear; below 1 small stacks already feel heavy; above 1 only big stacks do.")
                    .defineInRange("stackWeightCurve", 1.0D, 0.25D, 4.0D);
            b.pop();

            b.push("catch");
            catchEnabled = b.comment("Players can catch projectiles by holding the catch key (default: right-click) as one arrives.")
                    .define("enabled", true);
            catchRadius = b.comment("How close (blocks, from the eyes) a projectile must be to be caught.")
                    .defineInRange("catchRadius", 2.5D, 0.5D, 8.0D);
            catchConeDegrees = b.comment("Half-angle of the cone in front of the player's view a projectile must be inside to be caught.")
                    .defineInRange("catchConeDegrees", 70.0D, 5.0D, 180.0D);
            allowSelfCatch = b.comment("Whether a player can catch their own projectiles.")
                    .define("allowSelfCatch", false);
            requireEmptyHand = b.comment("Require an empty main hand to catch.")
                    .define("requireEmptyHand", false);
            catchWhileUsingItem = b.comment("Allow catching while using an item (eating, blocking with a shield, drawing a bow).")
                    .define("catchWhileUsingItem", false);
            lockInputWhileHolding = b.comment("While holding a caught projectile the player can only throw it: no attacking, block breaking, block placing, item use or interacting.")
                    .define("lockInputWhileHolding", true);
            catchCooldownTicks = b.comment("Ticks after catching before the caught projectile can be thrown.")
                    .defineInRange("catchCooldownTicks", 5, 0, 200);
            rethrowSpeedMultiplier = b.comment("Extra multiplier on the speed of a caught projectile when thrown back.")
                    .defineInRange("rethrowSpeedMultiplier", 1.0D, 0.1D, 5.0D);
            dropOnDeath = b.comment("Drop a held caught projectile in place on death (false keeps it through respawn). Logging out always drops it.")
                    .define("dropOnDeath", true);
            blockArrows = b.comment("Never catch arrow-type projectiles (arrows, tridents, modded arrows) unless explicitly whitelisted.")
                    .define("blockArrows", true);
            blockHurtingProjectiles = b.comment("Never catch fireball-type projectiles (fireballs, wither skulls, dragon fireballs) unless explicitly whitelisted.")
                    .define("blockHurtingProjectiles", true);
            allowReturningTridents = b.comment("Allow catching a Loyalty trident while it is flying back to its owner.")
                    .define("allowReturningTridents", false);
            catchBlacklist = b.comment("Projectile entity types that can never be caught. The entity type tag bettertoss:uncatchable does the same (and already lists the vanilla arrows, fireballs, etc).")
                    .defineListAllowEmpty("blacklist", List.<String>of(), BTConfig::isString);
            catchWhitelist = b.comment("If non-empty, ONLY these projectile entity types (plus the bettertoss:catchable tag) can be caught. An entry here also overrides blockArrows/blockHurtingProjectiles for that type.")
                    .defineListAllowEmpty("whitelist", List.<String>of(), BTConfig::isString);
            catchDisplayItems = b.comment("Item shown in the hand for caught projectiles that carry no item of their own, as \"entity_type=item\" (e.g. \"minecraft:llama_spit=minecraft:slime_ball\").")
                    .defineListAllowEmpty("displayItems", List.<String>of(), BTConfig::isString);
            catchThrowSpeeds = b.comment("Base throw speed (blocks/tick, before the power multiplier) of caught projectile types, as \"entity_type=speed\". Unlisted types use the vanilla speed of their kind (1.5 for snowballs, 2.5 for tridents...).")
                    .defineListAllowEmpty("throwSpeeds", List.<String>of(), BTConfig::isString);
            b.pop();
        }
    }

    public static class Client
    {
        public final ForgeConfigSpec.BooleanValue showBar;
        public final ForgeConfigSpec.BooleanValue showHints;
        public final ForgeConfigSpec.BooleanValue barShake;
        public final ForgeConfigSpec.IntValue chargeSweepTicks;
        public final ForgeConfigSpec.EnumValue<ChargeMode> chargeMode;

        public final ForgeConfigSpec.IntValue dropHoldTicks;

        public final ForgeConfigSpec.IntValue flickWindowMs;
        public final ForgeConfigSpec.DoubleValue flickThresholdDegrees;
        public final ForgeConfigSpec.DoubleValue flickAccelerationDegrees;
        public final ForgeConfigSpec.DoubleValue flickStrength;

        public final ForgeConfigSpec.DoubleValue focusFovMultiplier;
        public final ForgeConfigSpec.BooleanValue cameraShake;
        public final ForgeConfigSpec.DoubleValue cameraShakeDegrees;
        public final ForgeConfigSpec.BooleanValue handShake;
        public final ForgeConfigSpec.BooleanValue throwKick;

        public final ForgeConfigSpec.BooleanValue firstPersonAnimations;
        public final ForgeConfigSpec.BooleanValue thirdPersonPoses;

        Client(ForgeConfigSpec.Builder b)
        {
            b.push("bar");
            showBar = b.comment("Show the charge bar in the experience bar's slot while charging or holding a caught projectile.")
                    .define("showBar", true);
            showHints = b.comment("Show a one-line hint above the hotbar while charging or holding a caught projectile. Off by default: the bar says enough.")
                    .define("showHints", false);
            barShake = b.comment("Jitter the bar while your aim is unsteady, settling as you focus.")
                    .define("barShake", false);
            chargeSweepTicks = b.comment("Ticks for the power bar to sweep from empty to full.")
                    .defineInRange("chargeSweepTicks", 30, 5, 200);
            chargeMode = b.comment("OSCILLATE: the bar sweeps up and back down repeatedly, so power is about timing the release. RAMP: the bar fills once and stays full.")
                    .defineEnum("chargeMode", ChargeMode.OSCILLATE);
            b.pop();

            b.push("controls");
            dropHoldTicks = b.comment("Ticks the drop key must be held before it starts charging a fling instead of dropping. Releasing sooner drops the item like vanilla.")
                    .defineInRange("dropHoldTicks", 5, 1, 40);
            b.pop();

            b.push("flick");
            flickWindowMs = b.comment("Milliseconds before the release over which mouse motion counts as a flick.")
                    .defineInRange("flickWindowMs", 70, 20, 400);
            flickThresholdDegrees = b.comment("Minimum view movement (degrees) within the window for a flick to register at all. Slow panning never counts.")
                    .defineInRange("flickThresholdDegrees", 5.0D, 0.0D, 90.0D);
            flickAccelerationDegrees = b.comment("The flick window must move at least this many degrees MORE than the window before it - a flick is a sudden motion, not a steady turn.")
                    .defineInRange("flickAccelerationDegrees", 2.0D, 0.0D, 90.0D);
            flickStrength = b.comment("How much of the flick angle is added to the throw (0.6 = a 20 degree flick bends the throw by 12 degrees, before the server-side cap).")
                    .defineInRange("flickStrength", 0.6D, 0.0D, 3.0D);
            b.pop();

            b.push("focus");
            focusFovMultiplier = b.comment("FOV multiplier once a throw is fully steadied (lower = more zoom).")
                    .defineInRange("focusFovMultiplier", 0.9D, 0.5D, 1.0D);
            cameraShake = b.comment("Shake the view while a throw is unsteady. Off by default; focus is shown by the FOV alone.")
                    .define("cameraShake", false);
            cameraShakeDegrees = b.comment("Peak camera shake (degrees) at the very start of a charge, if cameraShake is on.")
                    .defineInRange("cameraShakeDegrees", 0.3D, 0.0D, 5.0D);
            handShake = b.comment("Tremble the first-person hand while a throw is unsteady. Off by default.")
                    .define("handShake", false);
            throwKick = b.comment("A tiny camera kick as the throw releases. Off by default.")
                    .define("throwKick", false);
            b.pop();

            b.push("animation");
            firstPersonAnimations = b.comment("First-person wind-up while charging and the whip on release.")
                    .define("firstPersonAnimations", true);
            thirdPersonPoses = b.comment("Third-person wind-up, throw and holding poses, visible to everyone.")
                    .define("thirdPersonPoses", true);
            b.pop();
        }
    }
}
