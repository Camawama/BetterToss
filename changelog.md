All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0]

### Added
- Hold-to-charge throws for every throwable item, with an oscillating (or ramping) power bar in the XP bar's slot. A quick click still throws straight away as a weak, uncharged toss (empty-bar power, widest spread), with no throw cooldown, so eggs and snowballs can be spammed; the hold that starts a charge is configurable (`useHoldTicks`, 5 ticks). Tridents charge from the first tick.
- Flick throws: mouse motion at the instant of release bends the throw, with configurable thresholds, strength and caps.
- Focus: quick clicks and early releases have a wide spread that steadies the longer the button is held, with a slight FOV zoom. Optional slow camera, hand and bar sway, and optional one-line tips, all off by default.
- Client config migration: settings still on an old default (tips and shakes on, from the first builds) move to the new default; settings you changed are kept.
- First-person wind-up and whip animations, and third-person wind-up, throw and holding poses visible to other players.
- Drop-key flings: tap Q to drop, hold Q to charge a throw of the selected item (Ctrl for the whole stack). Bigger stacks are heavier and fly shorter, with the reachable part of the bar shown.
- Catching: hold the catch key (right-click by default) to catch incoming projectiles; caught projectiles stay projectiles, must be thrown before anything else can be done, and drop in place on logout.
- Generic compatibility: any item that spawns a projectile from its use() is charged, with item/entity tags and config lists for the rest.
- Extensive server and client configuration.
