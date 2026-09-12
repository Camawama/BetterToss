# Better Toss!

A Forge 1.20.1 mod that overhauls throwing. Every throwable projectile (snowballs, eggs, ender
pearls, XP bottles, splash and lingering potions, tridents, and most modded throwables) gets:

- **Charged throws** - tap right-click for a weak toss, or hold it: a power bar sweeps in the XP
  bar's slot and the throw's speed follows where the bar is when you let go.
- **Flicks** - flick the mouse as you release and the throw picks up that motion: flick up to lob
  it high, sideways to curve it. Thresholds and caps are all configurable.
- **Focus** - a throw starts shaky (camera shake, trembling hand, wide spread) and steadies the
  longer you hold, with a slight FOV narrowing as you concentrate. Quick throws are wild; patient
  throws are precise.
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

## Compatibility

Nothing is replaced. A charged throw runs the item's own `use()` on the server and rescales the
projectile it spawns, so mod items that extend the vanilla throwable classes work as-is; anything
else can be added through the `bettertoss:throwable` item tag or the `throwableItems` config list.
Catching works on any `Projectile` entity, with the `bettertoss:catchable` / `bettertoss:uncatchable`
entity type tags and config lists to tune it.

## Config

- `config/bettertoss-client.toml` - bar sweep speed and mode, flick sensitivity, shake, FOV,
  animations, sounds, drop-key hold time.
- `<world>/serverconfig/bettertoss-server.toml` - power range, flick caps, focus timing and
  spread, fling speeds, catch radius/cone/rules/blacklists, and per-feature switches.

## License

[GPLv3](LICENSE.txt)
