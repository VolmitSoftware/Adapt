# Adapt

[![image](https://github.com/VolmitSoftware/Adapt/raw/main/storepage/adapt-tc.png)](https://github.com/VolmitSoftware/Adapt/wiki/Why-did-you-click)

## Overview

_Adapt is a drag and drop solution for balanced passive abilities and skills that players can enjoy on a server._

### Description

Adapt Abilities are all accessible in the in-game GUI by right-clicking any bookshelf **face**, providing a more user-friendly experience to a "skills" system. Most servers aim to enhance the quality of the "vanilla" experience. However, most skill-based plugins offer powers, game-breaking systems, and are riddled with bugs. That's where Adapt comes in, being lightweight on the server and providing mere quality-of-life enhancements to a user's experience. 

Below is a **WIP** list of features _(and descriptions)_ that I'll fill in when I can. But this should give you a good idea of the roadmap for this plugin! Keep in mind that this is all WIP, can change at any time, and all of these features can be configured/disabled!

The master branch is for the latest version of Minecraft.

### Language and Localization

English is defined in typed Java message catalogs beside the code that uses it. When adding player-facing text, add its English key there first; the localization tests identify every non-English overlay that still needs the new key. Adapt does not ship a separate `en_US.toml` file.

Adapt bundles complete overlays for German, Spanish, Finnish, French, Hebrew, Italian, Japanese, Korean, Lithuanian, Dutch, Polish, Portuguese, Russian, Turkish, Vietnamese, Simplified Chinese, and Traditional Chinese. A server's selected TOML can contain only the keys it wants to override; missing values resolve through the bundled language and finally the code-owned English catalog.

Do you know one of these languages and want to improve its wording? Join the [Discord](https://discord.gg/volmit) or open a contribution. Please submit translations only when you are confident in the language so they can be reviewed accurately.

### PlaceholderAPI

Adapt registers the `adapt` expansion when PlaceholderAPI is installed. Every key is `%adapt_<path>%`, where `<path>` is one to four dot-separated segments of lowercase letters, digits and hyphens. There is no underscore anywhere in a path — PlaceholderAPI already uses `_` to terminate the expansion identifier.

Three answers, three meanings:

| Rendered | Meaning |
|---|---|
| `%adapt_skil.mining.level%` | the path is not a key — check the spelling |
| `---` | the path is a key, but there is no value right now |
| `0` | a genuine zero |

Every value is plain text: no colour codes, no unit suffixes, no thousands separators, and never a `%` character. Counts are exact integers; fractions and rates carry exactly two decimals. Booleans are `true` or `false`.

Per-player values come from an immutable snapshot published once per second by the player's own tick, on the thread that owns their data. Nothing is loaded for an offline player: their last snapshot is served for sixty seconds after they quit, and then every per-player key answers `---`. `%adapt_available%` answers whether a snapshot exists.

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

`<skill>` is a skill identifier such as `mining` or `hunter`. A skill the player has never trained answers with its level-zero values, not `---`.

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

`<adaptation>` is an adaptation identifier such as `mining-vein`.

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
**Building Adapt can be challenging and requires some technical knowledge**, such as with [Iris](https://www.spigotmc.org/resources/iris-world-gen-custom-biome-colors.84586/). However, you will need to set up a few things if your system has never been used for Java development.

<details>

<summary> Build Steps </summary>

### So this is fairly similar to Iris, but a bit modified.

### IDE Builds (for development & Compilation)

You NEED TO BE USING Intelij To build this project, or anything that can support the
plugin [Manifold](https://plugins.jetbrains.com/plugin/10057-manifold)

## Preface: if you need help compiling and you are a developer / intend to help out in the community or with development we would love to help you regardless in the discord! however do not come to the discord asking for free copies, or a tutorial on how to compile.

1. Install [Java JDK 25](https://adoptium.net/temurin/releases/?version=25)
2. Set the JDK installation path to `JAVA_HOME` as an environment variable.
    * Windows
        1. Start > Type `env` and press Enter
        2. Advanced > Environment Variables
        3. Under System Variables, click `New...`
        4. Variable Name: `JAVA_HOME`
        5. Variable Value: `C:\Program Files\Java\jdk-25` (verify this exists after installing java don't just copy
           the example text)
    * MacOS
        1. Run `/usr/libexec/java_home -V` and look for Java 25
        2. Run `sudo nano ~/.zshenv`
        3. Add `export JAVA_HOME=$(/usr/libexec/java_home)` as a new line
        4. Use `CTRL + X`, then Press `Y`, Then `ENTER`
        5. Quit & Reopen Terminal and verify with `echo $JAVA_HOME`. It should print a directory

3. Setup Gradle

<details>
<summary> Gradle Setup </summary>

* Run `gradlew setup` any time you get dependency issues with craftbukkit
* Configure ITJ Gradle to use JDK 25 (in settings, search for gradle)
* Resync the project & run your newly created task (under the development folder in gradle tasks!)

</details>

4. INSTALL [MANIFOLD](https://plugins.jetbrains.com/plugin/10057-manifold)
5. If this is your first time building Adapt for MC 1.19+ run `gradlew setup` inside the root Adapt project folder.
   Otherwise, skip this step. Grab a coffee, this may take up to 5 minutes depending on your cpu & internet connection.
6. Once the project has setup, run `gradlew adapt`
7. The Adapt jar will be placed in `Adapt/build/Adapt-XXX-XXX.jar` Enjoy! Consider supporting us by buying it on spigot!

</details>



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

<details>
<summary> Skill/Adaptation List (110 Adaptations) </summary> 

### Agility (4 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| AgilityArmorUp | Progressive armor boost while sprinting with wind-up ramp | Working |
| AgilitySuperJump | Enhanced vertical jump when sneaking | Working |
| AgilityWallJump | Mid-air jumps by sticking to walls | Working |
| AgilityWindUp | Progressive movement speed boost while sprinting | Working |

### Architect (5 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| ArchitectElevator | Teleports players between vertically-linked note block elevator markers | Working |
| ArchitectFoundation | Creates temporary tinted glass blocks beneath sneaking players | Working |
| ArchitectGlass | Silk-touch glass when breaking bare-handed | Working |
| ArchitectPlacement | 3x3 block placement preview and batch place while sneaking | Working |
| ArchitectWirelessRedstone | Bind redstone torches to any block for remote pulses | Working |

### Axes (6 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| AxeChop | Right-click logs with an axe to mine vertical columns of connected wood | Working |
| AxeCraftLogSwap | Crafting recipes to convert log types using saplings as catalysts | Working |
| AxeDropToInventory | Redirects axe block drops into player inventory | Working |
| AxeGroundSmash | Airborne crouch arms an AoE ground slam that fires on landing | Working |
| AxeLeafVeinminer | Vein-mines connected leaves when sneaking with an axe | Working |
| AxeWoodVeinminer | Vein-mines connected logs when sneaking with an axe | Working |

### Blocking (2 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| BlockingChainArmorer | Crafting recipe for chainmail armor from iron nuggets | Working (recipe-only) |
| BlockingMultiArmor | Combine chestplate and elytra into auto-switching MultiArmor item | Working |

### Brewing (13 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| BrewingAbsorption | Brewing recipe for Absorption potions | Working |
| BrewingBlindness | Brewing recipe for Blindness potions | Working |
| BrewingDarkness | Brewing recipe for Darkness potions | Working |
| BrewingDecay | Brewing recipe for Wither/Decay potions | Working |
| BrewingFatigue | Brewing recipe for Mining Fatigue potions | Working |
| BrewingHaste | Brewing recipe for Haste potions | Working |
| BrewingHealthBoost | Brewing recipe for Health Boost potions | Working |
| BrewingHunger | Brewing recipe for Hunger potions | Working |
| BrewingLingering | Extends potion durations and adds lore via BrewEvent | Working |
| BrewingNausea | Brewing recipe for Nausea potions | Working |
| BrewingResistance | Brewing recipe named "Resistance" but applies ABSORPTION effect | Bug (effect type mismatch) |
| BrewingSaturation | Brewing recipe for instant Saturation potions | Working |
| BrewingSuperHeated | Accelerates brewing speed based on adjacent lava/fire blocks | Working |

### Crafting (7 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| CraftingBackpacks | Crafting recipe for bundles from a chest wrapped in eight leather | Working |
| CraftingDeconstruction | Right-click floating items with shears while sneaking to deconstruct | Working |
| CraftingLeather | Campfire recipe to cook rotten flesh into leather | Working |
| CraftingReconstruction | 16 recipes to reconstruct ore blocks from stone and ingots | Working |
| CraftingSkulls | Crafting recipes for mob skulls from bone blocks and materials | Working |
| CraftingStations | Right-click portable crafting station items to open their UIs | Working |
| CraftingXP | Grants XP orbs when crafting items | Working |

### Discovery (4 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| DiscoveryArmor | Grants armor points based on hardness of nearby blocks | Working |
| DiscoveryUnity | Grants random XP to a random unlocked skill when gaining vanilla XP | Working |
| DiscoveryVillagerAtt | Steal XP and get Hero of the Village when right-clicking villagers | Working |
| DiscoveryXpResist | Converts XP levels into damage reduction | Broken (always-false condition) |

### Enchanting (3 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| EnchantingLapisReturn | Chance to return lapis lazuli when enchanting | Working |
| EnchantingQuickEnchant | Apply enchanted books to items by swapping cursor in inventory | Working |
| EnchantingXPReturn | Returns XP orbs when enchanting items | Working |

### Excavation (4 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| ExcavationDropToInventory | Shovel drops go directly to inventory | Working |
| ExcavationHaste | Grants Haste when starting to mine | Working |
| ExcavationOmniTool | Multi-tool that auto-switches between tool types based on block | Working |
| ExcavationSpelunker | Sneak with glowberries to highlight nearby ores with glowing markers | Working |

### Herbalism (10 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| HerbalismCraftableCobweb | Crafting recipe for cobweb from string | Working (recipe-only) |
| HerbalismCraftableMushroomBlocks | Crafting recipes for mushroom blocks from mushrooms | Working (recipe-only) |
| HerbalismDropToInventory | Hoe crop drops go directly to inventory | Working |
| HerbalismGrowthAura | Aura that accelerates nearby crop growth at hunger cost | Working (TODO notes XP is busted) |
| HerbalismHungryHippo | Bonus food saturation when eating | Working |
| HerbalismHungryShield | Converts incoming damage to hunger consumption | Working |
| HerbalismLuck | Chance to drop seeds and food when breaking grass and flowers | Working |
| HerbalismMyconid | Crafting recipe for mycelium from dirt and mushrooms | Working (recipe-only) |
| HerbalismReplant | Right-click mature crops with hoe to harvest and auto-replant in radius | Working |
| HerbalismTerralid | Crafting recipe for grass block from seeds and dirt | Working (recipe-only) |

### Hunter (9 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| HunterAdrenaline | Increased damage output when at low health | Working |
| HunterDropToInventory | Sword kill drops go directly to inventory | Working |
| HunterInvis | Grants invisibility when taking damage with optional hunger penalty | Working |
| HunterJumpBoost | Grants jump boost when taking damage | Working |
| HunterLuck | Grants luck when taking damage | Working |
| HunterRegen | Grants regeneration when taking damage | Working |
| HunterResistance | Grants damage resistance when taking damage | Working |
| HunterSpeed | Grants speed when taking damage | Working |
| HunterStrength | Grants strength when taking damage | Working |

### Nether (3 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| NetherFireResist | Chance to negate fire damage scaling with level | Working |
| NetherSkullYeet | Left-click with wither skull to launch a wither skull projectile | Working |
| NetherWitherResist | Chance to negate wither damage scaling with netherite armor count | Working |

### Pickaxe (5 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| PickaxeAutosmelt | Auto-smelts ores into ingots when mined, respects fortune | Working |
| PickaxeChisel | Right-click ores to extract raw drops with tool damage | Working |
| PickaxeDropToInventory | Pickaxe drops go directly to inventory | Working |
| PickaxeSilkSpawner | Silk-touch or sneak to pick up spawners preserving properties | Working |
| PickaxeVeinminer | Sneak-mine to break all connected ores and obsidian in radius | Working |

### Ranged (5 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| RangedArrowRecovery | Chance to recover arrows on hit scaling 10% to 80% | Working |
| RangedForce | Increases projectile velocity on launch | Working |
| RangedLungeShot | Launches player backward when shooting arrows mid-air | Working |
| RangedPiercing | Increases arrow pierce level based on adaptation level | Working |
| RangedWebBomb | Throwable snowballs that create temporary cobwebs on impact | Working |

### Rift (7 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| RiftAccess | Bind ender pearl to a container for remote inventory access | Working |
| RiftBlink | Short-range teleport in look direction while sprinting mid-air | Working |
| RiftDescent | Removes levitation and grants slow falling when un-sneaking | Working |
| RiftEnderchest | Right-click while holding ender chest to open it anywhere | Working |
| RiftGate | Bind eye of ender to locations for teleportation recall | Working |
| RiftResist | Grants damage resistance when interacting with ender pearls or eyes | Working |
| RiftVisage | Prevents endermen from targeting players carrying ender pearls | Working |

### Seaborne (5 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| SeaborneFishersFantasy | Bonus drops and XP when catching fish | Working |
| SeaborneOxygen | Grants water breathing when in water | Working |
| SeaborneSpeed | Grants Dolphin's Grace when swimming without Depth Strider | Bug (early return exits loop for all players) |
| SeaborneTurtlesMiningSpeed | Grants Haste when underwater | Working |
| SeaborneTurtlesVision | Grants Night Vision when underwater | Working |

### Stealth (5 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| StealthEnderVeil | Prevents Endermen from targeting or attacking the player | Working |
| StealthGhostArmor | Regenerating armor points that reset on damage | Working |
| StealthSight | Grants night vision, Blindness immunity, and private outlines on invisible players while sneaking | Working |
| StealthSnatch | Auto-collects nearby dropped items while sneaking | Working |
| StealthSpeed | Grants speed while sneaking | Working |

### Swords (3 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| SwordsBloodyBlade | Applies bleeding DoT effect to sword-hit targets | Working |
| SwordsMachete | Left-click with sword to harvest vegetation in an area | Working |
| SwordsPoisonedBlade | Applies poison and bleed to sword-hit targets | Working |

### Taming (3 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| TamingDamage | Increases attack damage of tamed entities | Working |
| TamingHealthBoost | Increases max health of tamed entities | Working |
| TamingHealthRegeneration | Grants regen to tamed entities when they take damage | Working (minor concurrency concern) |

### Tragoul (4 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| TragoulGlobe | Distributes damage dealt to all nearby entities as AoE | Working |
| TragoulHealing | Heals player on melee hit as percentage of damage dealt | Working |
| TragoulLance | Spawns seeking projectiles that chain between enemies on kill | Working |
| TragoulThorns | Reflects damage back to attackers including melee and projectile | Working |

### Unarmed (3 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| UnarmedGlassCannon | Amplifies unarmed damage inversely proportional to armor | Working |
| UnarmedPower | Passive unarmed damage increase scaling with level | Working |
| UnarmedSuckerPunch | Amplified unarmed damage while sprinting | Working |

### Known Bugs

| Adaptation | Issue |
|---|---|
| DiscoveryXpResist | Condition `p.getLevel() < p.getLevel() - getXpTaken(...)` is always false; damage reduction never triggers |
| SeaborneSpeed | `return;` inside player loop exits entire `onTick()` when any player has Depth Strider, skipping all remaining players |
| BrewingResistance | Named "Bottled Resistance" but applies ABSORPTION effect instead of RESISTANCE |

</details>
