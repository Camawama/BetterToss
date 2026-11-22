# Better Toss!

A Forge 1.20.1 mod that overhauls throwing. Every throwable projectile (snowballs, eggs, ender
pearls, XP bottles, splash and lingering potions, most modded throwables, and tridents if you ask)
gets:

- **Charged throws** - click right-click and the item is thrown straight away as a weak, uncharged
  toss, so eggs and snowballs can still be spammed; hold it a moment (a quarter of a second by
  default) and a power bar sweeps in the XP bar's slot, and the throw's speed follows where the bar
  is when you let go. Only a charge throws harder. Tridents are left to vanilla (their own draw,
  Loyalty and Riptide) unless the server config's `tridents` takes them over, and then they charge
  from the start, as vanilla already throws them on release.
- **Flicks** - flick the mouse as you release and the throw picks up that motion: flick up to lob
  it high, sideways to curve it. Thresholds and caps are all configurable.
- **Focus and strain** - a quick click or an early release has a wide spread, which narrows over
  the first two seconds of a hold, with a slight FOV narrowing as you concentrate. Hold on too
  long, though, and the charge gets shaky, like a spear held out too long: the hand is steady at
  first, starts to tremble after about two seconds and shakes clearly by five, and the throw
  scatters more the shakier it gets. Quick throws are wild and over-held throws are shaky; the
  bar's first sweep up and back down is the steady one. The tremble is Camafy's shared hold
  mechanic, the same as Nice Catch's rod casts. (The one-line tips above the hotbar are off by
  default.)
- **Throw animations** - a wind-up pose while charging and a whip on release, in first person and
  for everyone watching in third person.
- **Fling with Q** - tap the drop key to drop an item like vanilla; hold it to charge a fling.
  Stacks are heavy: heaving a whole stack (Ctrl+Q) travels much less far than flinging one item,
  and the bar shows how much reach the weight leaves you.
- **Catching** - hold the catch key (right-click by default) as a projectile comes at you and you
  catch it: an egg caught doesn't crack, an ender pearl caught never teleports its thrower. The
  projectile stays a projectile, sits in your hand, and you must throw it before you can do
  anything else. Logging out drops it where you stand. Arrows, fireballs and the like are
  blacklisted by default.

## Requirements

- Minecraft 1.20.1 with Forge 47.4 or newer, on the server and every client.
- [Camafy](https://www.curseforge.com/minecraft/mc-mods/camafy) 0.1.13 or newer (required: its hold mechanic and the XP-bar slot the power bar is drawn in).

## Compatibility

Nothing is replaced. A charged throw runs the item's own `use()` on the server and rescales the
projectile it spawns, so mod items that extend the vanilla throwable classes work as-is. Items that
don't, but throw themselves the way vanilla throws a snowball (their own `use()` throws, at once, a
projectile showing that same item: IceCaves' ice ball, Cardiac's life bottle), are found at every
world start before anyone throws one: each misc-category entity type is created once (never
spawned) and, when it is a thrown-item projectile, the item it shows is charge-thrown from the
start if it is plainly an item thrown by hand (not a block, not food, nothing held or drawn, a
`use()` of its own) (`scanThrowableProjectiles`). The same scan then reads the right-click code of
every modded item's class (the class files as data; nothing is loaded or run) and takes the items
whose code makes a projectile when they are things thrown, not things that shoot: they stack, have
no durability, are not food and are not held or drawn (`scanItemUses`); an item that doesn't stack
or wears out is still taken when its projectile shows an item and its code takes it from the stack,
so the item itself flies (Lloyd's throwing discs), while a tool that casts something on a line and
keeps it in the hand (Spelunkery's pick on a stick) is not. That finds what no entity
type names: block items that are thrown (Spelunkery's glowsticks; aimed at a block they are still
placed), many items sharing one projectile type (its pebbles) and projectiles that are arrows
underneath (the rocks of Spelunker's Charm). One the scan can't see is still learned from its
first throw and charged from then on, saved with the world (`learnThrowables`); Cardiac's life
bottle is in the tag as well. Anything else can be added through the `bettertoss:throwable` item
tag or the `throwableItems` config list, and the `bettertoss:not_throwable` tag or
`throwableBlacklist` keep an item out.
A projectile that holds its own speed (a throwing disc that flies level at its set speed for a set
time and comes back) can't be rescaled, so it is carried a little further along its heading after
each tick of its own while a charge asks for more than its own speed, through its own bounces and
return, and clients move their copy the same way (`driveFixedSpeedProjectiles`); a quick click leaves
it at its own speed.
Catching works on any `Projectile` entity, with the `bettertoss:catchable` / `bettertoss:uncatchable`
entity type tags and config lists to tune it.

## Config

- `config/bettertoss-client.toml` - bar sweep speed and mode, optional tips and bar shiver, how
  much the hand trembles (`handTremble`), flick sensitivity, FOV, animations, and how long
  right-click and the drop key must be held before they charge. Settings you never changed follow
  the mod when a default is retuned.
- `<world>/serverconfig/bettertoss-server.toml` - power range, flick caps, focus timing and
  spread, strain timing and spread (when a held charge starts to shake and how far it throws you
  off), fling speeds, catch radius/cone/rules/blacklists, and per-feature switches. Values nobody
  changed follow the mod when a default is retuned (the file's `configVersion`).

## License

[GPLv3](LICENSE.txt)
