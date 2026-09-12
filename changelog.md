All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0]

### Added
- Hold-to-charge throws for every throwable item, with an oscillating (or ramping) power bar in the XP bar's slot; a quick tap is a weak toss.
- Flick throws: mouse motion at the instant of release bends the throw, with configurable thresholds, strength and caps.
- Focus: throws start unsteady (camera shake, hand tremor, wide spread) and steady up the longer the button is held, with a slight FOV zoom.
- First-person wind-up and whip animations, and third-person wind-up, throw and holding poses visible to other players.
- Drop-key flings: tap Q to drop, hold Q to charge a throw of the selected item (Ctrl for the whole stack).
- Catching: hold the catch key (right-click by default) to catch incoming projectiles; caught projectiles stay projectiles, must be thrown before anything else can be done, and drop in place on logout.
- Generic compatibility: any item that spawns a projectile from its use() is charged, with item/entity tags and config lists for the rest.
- Extensive server and client configuration.
