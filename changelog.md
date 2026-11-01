All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Cardiac's life bottles are charge-thrown like XP bottles (the owner, 2026-10-29: "Cardiac doesn't work with better toss. I should be able to use better toss to throw the life bottles"). The bottle is a plain item whose use throws a projectile of its own, so the class test (anything extending a vanilla throwable item) never saw it; it is now an optional entry in the `bettertoss:throwable` tag.
- Other mods' throwables built the same way are learned from their first throw: when an item's own use throws, at once and without a draw, a projectile showing that same item (the way vanilla throws a snowball or an XP bottle), it is charge-thrown from then on, saved with the world and synced to every client (server config `learnThrowables`, on). Its first throw in a world is the item's own. The `bettertoss:not_throwable` tag and `throwableBlacklist` still win. The network protocol is 3 (a client and server must both have this version).

### Changed
- Tridents are vanilla's again by default: their own draw, throw, Loyalty and Riptide, no charge bar, whatever the tags and lists say. Server config `tridents` (false) takes them over as before: the charge bar, its power, focus and strain apply to a trident throw.

## [1.0.0]

### Added
- Hold-to-charge throws for every throwable item, with an oscillating (or ramping) power bar in the XP bar's slot. A quick click still throws straight away as a weak, uncharged toss (empty-bar power, widest spread), with no throw cooldown, so eggs and snowballs can be spammed; the hold that starts a charge is configurable (`useHoldTicks`, 5 ticks). Tridents charge from the first tick.
- Flick throws: mouse motion at the instant of release bends the throw, with configurable thresholds, strength and caps.
- Focus: quick clicks and early releases have a wide spread that steadies over the first two seconds of the hold, with a slight FOV zoom. Optional one-line tips, off by default.
- Strain: a charge held too long gets shaky, the way a spear does when held out too long. The hand is steady at first, then trembles more and more (from 2 s, fully by 5 s: server `strain.steadyTicks` 40 and `strain.shakyTicks` 100), in first person and on the third-person arm, and the throw scatters by up to 4° more (`strain.maxInaccuracyDegrees`). How much the hand trembles is the client's `strain.handTremble` (1, 0 turns it off). This replaces the old camera and hand shake, which ran the other way (shaky at the start, steadying as you held) and was off by default: `cameraShake`, `cameraShakeDegrees` and `handShake` are gone, and `barShake` now shivers the bar with the strain instead. The tremble, the charge bar and the spread are Camafy's shared hold-to-charge mechanic, the same as Nice Catch's rod casts (needs Camafy 0.1.13).
- Client config migration: settings still on an old default (tips and shakes on, from the first builds) move to the new default; settings you changed are kept.
- First-person wind-up and whip animations, and third-person wind-up, throw and holding poses visible to other players.
- Drop-key flings: tap Q to drop, hold Q to charge a throw of the selected item (Ctrl for the whole stack). Bigger stacks are heavier and fly shorter, with the reachable part of the bar shown.
- Catching: hold the catch key (right-click by default) to catch incoming projectiles; caught projectiles stay projectiles, must be thrown before anything else can be done, and drop in place on logout.
- Generic compatibility: any item that spawns a projectile from its use() is charged, with item/entity tags and config lists for the rest.
- Extensive server and client configuration.
