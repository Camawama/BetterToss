package net.camacraft.bettertoss;

/** What a charge bar is charging. */
public enum ThrowMode
{
    /** A throwable item in a hand (snowball, pearl, trident, modded throwables). */
    ITEM,
    /** A projectile caught from someone else, thrown back. */
    CAUGHT,
    /** The selected hotbar stack, flung with the drop key. */
    DROP;

    private static final ThrowMode[] VALUES = values();

    public static ThrowMode byId(int id)
    {
        return id >= 0 && id < VALUES.length ? VALUES[id] : ITEM;
    }
}
