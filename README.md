# Adapt

[![image](https://github.com/VolmitSoftware/Adapt/raw/main/storepage/adapt-tc.png)](https://github.com/VolmitSoftware/Adapt/wiki/Why-did-you-click)

## Overview

_Adapt is a drag and drop solution for balanced passive abilities and skills that players can enjoy on a server._

### Description

Adapt Abilities are all accessible in the in-game GUI by right-clicking a side face of a bookshelf. Top and bottom faces work only when `adaptActivatorAllowVerticalFaces` is enabled. Most servers aim to enhance the quality of the "vanilla" experience. However, most skill-based plugins offer powers, game-breaking systems, and are riddled with bugs. That's where Adapt comes in, being lightweight on the server and providing mere quality-of-life enhancements to a user's experience.

Adapt declares 23 skills and 312 adaptation types. A plain server registers 311; Iris Feller registers only when the Iris integration is available. The authoritative, current feature reference is the [skills catalog](https://docs.volmit.com/adapt/10-skills-catalog).

The master branch is for the latest version of Minecraft.

### Language and Localization

English is defined in typed Java message catalogs beside the code that uses it. When adding player-facing text, add its English key there first; the localization tests identify every non-English overlay that still needs the new key. Adapt does not ship a separate `en_US.toml` file.

Set `language` in `plugins/Adapt/adapt.toml` to a supported locale such as `de_DE`. Adapt downloads only that revision-pinned locale, verifies it, caches it under `languages/downloaded/`, and activates it automatically. A server override TOML can contain only the keys it wants to change; missing values resolve through the downloaded locale and finally the code-owned English catalog.

Do you know one of these languages and want to improve its wording? Join the [Discord](https://discord.gg/volmit) or open a contribution. Please submit translations only when you are confident in the language so they can be reviewed accurately.

### PlaceholderAPI

Adapt registers the `adapt` expansion when PlaceholderAPI is installed. Every key is `%adapt_<path>%`, where `<path>` is one to four dot-separated segments of lowercase letters, digits and hyphens. There is no underscore anywhere in a path — PlaceholderAPI already uses `_` to terminate the expansion identifier.

Three answers, three meanings:

| Rendered | Meaning |
|---|---|
| `%adapt_skil.pickaxe.level%` | the path is not a key — check the spelling |
| `---` | the path is a key, but there is no value right now |
| `0` | a genuine zero |

Every value is plain text: no colour codes, no unit suffixes, no thousands separators, and never a `%` character. Counts are exact integers; fractions and rates carry exactly two decimals. Booleans are `true` or `false`.

Per-player values come from an immutable snapshot published once per second by the player's own tick, on the thread that owns their data. Nothing is loaded for an offline player: their last snapshot is served for sixty seconds after a normal quit, and then every per-player key answers `---`. If an online player's profile cannot be loaded safely or its SQL fence is retired, Adapt removes the snapshot immediately: `%adapt_available%` answers `false`, every per-player and mutation value answers `---`, and catalogue values remain available. The Minecraft session stays connected while Adapt progression and abilities remain inactive.

#### Server

| Key | Value |
|---|---|
| `%adapt_available%` | `true` while the reading player has a published snapshot |
| `%adapt_catalog.available%` | `true` once the skill catalogue has been published |
| `%adapt_catalog.skills%` | registered skills |
| `%adapt_catalog.adaptations%` | registered adaptations |
| `%adapt_catalog.mutations%` | mutation types |

#### Player

| Key | Value |
|---|---|
| `%adapt_player.level%` | master level |
| `%adapt_player.max-level%` | configured maximum level |
| `%adapt_player.master-xp%` | master experience |
| `%adapt_player.multiplier%` | global experience multiplier |
| `%adapt_player.wisdom%` | wisdom earned |
| `%adapt_player.power%` | ability power still available |
| `%adapt_player.power-max%` | ability power granted by level |
| `%adapt_player.power-used%` | ability power spent on adaptations |
| `%adapt_player.known-skills%` | skills at level 1 or higher |
| `%adapt_player.learned-adaptations%` | adaptations at level 1 or higher |

#### Skill — `%adapt_skill.<skill>.<metric>%`

`<skill>` is a skill identifier such as `pickaxe` or `hunter`. A skill the player has never trained answers with its level-zero values, not `---`.

| Metric | Value |
|---|---|
| `level` | current level |
| `xp` | experience in this skill |
| `knowledge` | unspent knowledge in this skill |
| `multiplier` | this skill's experience multiplier |
| `progress` | progress through the current level, `0.00` to `1.00` |
| `progress-percent` | the same progress as `0.00` to `100.00`, with no `%` character |
| `xp-to-next` | experience still needed for the next level |
| `current-level-xp` | experience threshold of the current level |
| `next-level-xp` | experience threshold of the next level |
| `learned-adaptations` | adaptations learned in this skill |
| `known` | `true` when the level is 1 or higher |
| `name` | the skill's localized name |
| `enabled` | `true` when the skill is enabled |
| `adaptations` | adaptations this skill offers |
| `has-level.<n>` | `true` when the level is at least `<n>` |

#### Adaptation — `%adapt_adaptation.<adaptation>.<metric>%`

`<adaptation>` is an adaptation identifier such as `pickaxe-veinminer`.

| Metric | Value |
|---|---|
| `level` | the level the player has purchased |
| `max-level` | the highest level this adaptation offers |
| `name` | the adaptation's display name |
| `skill` | the identifier of the owning skill |
| `enabled` | `true` when the adaptation is enabled |
| `learned` | `true` when the level is 1 or higher |
| `can-use` | `true` when it is learned and both it and its skill are enabled |
| `cost-next` | knowledge cost of the next level, `0` at maximum |
| `power-next` | ability power cost of the next level, `0` at maximum |
| `can-claim-next` | `true` when the player can afford the next level right now |
| `can-claim.<n>` | `true` when the player can move to level `<n>` right now |
| `cost-to.<n>` | knowledge cost to reach level `<n>` from the current level |
| `power-to.<n>` | ability power cost to reach level `<n>` from the current level |

#### Mutation

| Key | Value |
|---|---|
| `%adapt_mutation.available%` | `true` once a mutation snapshot has been published for this player |
| `%adapt_mutation.enabled%` | `true` when the mutation feature is enabled |
| `%adapt_mutation.perfect%` | `true` when the player has perfect adaptation |
| `%adapt_mutation.expressed%` | how many mutations are currently expressed |
| `%adapt_mutation.slot-1%` / `slot-2%` | the display name in that slot, `---` when empty |
| `%adapt_mutation.slot-1-id%` / `slot-2-id%` | the identifier in that slot, `---` when empty |
| `%adapt_mutation.slot-1-unlocked%` / `slot-2-unlocked%` | `true` when that slot is unlocked |
| `%adapt_mutation.combat-lock%` | seconds left on the combat lock before slots can change |
| `%adapt_mutation.can-swap%` | `true` when the combat lock has expired |

Per mutation, `%adapt_mutation.<mutation>.<metric>%`, where `<mutation>` is an identifier such as `bastion-spine`:

| Metric | Value |
|---|---|
| `id` | the mutation identifier |
| `name` | the mutation's display name |
| `state` | `locked`, `available`, `expressed`, `dormant`, `disabled`, `restricted` or `conflict` |
| `expressed` | `true` when this mutation is currently expressed |
| `qualified` | `true` when the player qualifies for this mutation |
| `slot` | `1`, `2`, or `0` when it is not selected |

# [Support](https://discord.gg/volmit) **|** [Documentation](https://docs.volmit.com/adapt/)

## Building

### Download .jar release
**Consider supporting our development by buying Adapt**
On [SpigotMC](https://www.spigotmc.org/resources/adapt-leveling-skills-and-abilities.103790/)! We work hard to make Adapt
the best it can be for everyone.

### Build your own .jar

Install Java JDK 25, clone the repository, and run the Gradle wrapper from the Adapt project root. IntelliJ and the Manifold IDE plugin are optional; the wrapper supplies the build dependencies.

```shell
./gradlew build
```

`./gradlew build` emits the shaded runtime jar. `./gradlew shadowJar` builds only the jar, and `./gradlew adapt` creates the convenience jar copy `build/Adapt-<version>.jar`. Locale sources remain outside the jar; the build embeds their pinned revision and checksums for verified on-demand downloads.

## Credits

Helping out in any way you can is appreciated, and you will be listed here for your contributions :)
<details>
<summary> Language </summary>

* [NextdoorPsycho](https://github.com/NextdoorPsycho): English Translation
* [Nowhere (Armin231)](https://github.com/Armin231): German Translation

</details>
<details>
<summary> Code </summary>

* [Vatuu](https://github.com/Vatuu)
* [Cyberpwn](https://github.com/cyberpwnn)
* [NextdoorPsycho](https://github.com/NextdoorPsycho)

</details>
