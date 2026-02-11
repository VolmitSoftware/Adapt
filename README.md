# Adapt

[![image](https://github.com/VolmitSoftware/Adapt/raw/main/storepage/adapt-tc.png)](https://github.com/VolmitSoftware/Adapt/wiki/Why-did-you-click)

## Overview

[![gitlocalized ](https://gitlocalize.com/repo/8085/whole_project/badge.svg)](https://gitlocalize.com/repo/8085/whole_project?utm_source=badge)

_Adapt is a drag and drop solution for balanced passive abilities and skills that players can enjoy on a server._

### Description

Adapt Abilities are all accessible in the in-game GUI by right-clicking any bookshelf **face**, providing a more user-friendly experience to a "skills" system. Most servers aim to enhance the quality of the "vanilla" experience. However, most skill-based plugins offer powers, game-breaking systems, and are riddled with bugs. That's where Adapt comes in, being lightweight on the server and providing mere quality-of-life enhancements to a user's experience. 

Below is a **WIP** list of features _(and descriptions)_ that I'll fill in when I can. But this should give you a good idea of the roadmap for this plugin! Keep in mind that this is all WIP, can change at any time, and all of these features can be configured/disabled!

The master branch is for the latest version of Minecraft.

### Language and Localization

Do you know a language other than English? Do you want to play a big part in Adapt's localization into different languages? Join the [Discord](https://discord.gg/volmit) and let us know or visit the [gitlocalize repository](https://gitlocalize.com/repo/8085) to help remotely with language localizations! 

If you don't see a language you can easily add it, or let us know here in discussions! We take this on an honor system, so please submit a translation key only if you are confident in the language, and they will be verified.

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

1. Install [Java JDK 21](https://www.oracle.com/java/technologies/downloads/#java21)
2. Set the JDK installation path to `JAVA_HOME` as an environment variable.
    * Windows
        1. Start > Type `env` and press Enter
        2. Advanced > Environment Variables
        3. Under System Variables, click `New...`
        4. Variable Name: `JAVA_HOME`
        5. Variable Value: `C:\Program Files\Java\jdk-21` (verify this exists after installing java don't just copy
           the example text)
    * MacOS
        1. Run `/usr/libexec/java_home -V` and look for Java 21
        2. Run `sudo nano ~/.zshenv`
        3. Add `export JAVA_HOME=$(/usr/libexec/java_home)` as a new line
        4. Use `CTRL + X`, then Press `Y`, Then `ENTER`
        5. Quit & Reopen Terminal and verify with `echo $JAVA_HOME`. It should print a directory

3. Setup Gradle

<details>
<summary> Gradle Setup </summary>

* Run `gradlew setup` any time you get dependency issues with craftbukkit
* Configure ITJ Gradle to use JDK 21 (in settings, search for gradle)
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
| ArchitectWirelessRedstone | Bind redstone torches to target blocks for remote pulses | Working |

### Axes (6 adaptations)

| Adaptation | Description | Status |
|---|---|---|
| AxeChop | Right-click logs with an axe to mine vertical columns of connected wood | Working |
| AxeCraftLogSwap | Crafting recipes to convert log types using saplings as catalysts | Working |
| AxeDropToInventory | Redirects axe block drops into player inventory | Working |
| AxeGroundSmash | AoE ground slam when sneaking and hitting mobs with an axe | Working |
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
| CraftingBackpacks | Crafting recipe for bundles from leather, lead, chest, and barrel | Working |
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
| StealthSight | Grants night vision while sneaking | Working |
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
