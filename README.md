# Adapt

[![image](https://github.com/VolmitSoftware/Adapt/raw/main/storepage/adapt-tc.png)](https://github.com/VolmitSoftware/Adapt/wiki/Why-did-you-click)

## Overview

[![gitlocalized ](https://gitlocalize.com/repo/8085/whole_project/badge.svg)](https://gitlocalize.com/repo/8085/whole_project?utm_source=badge)

_Adapt is a drag and drop solution for balanced passive abilities and skills that players can enjoy on a server._

### Description

Adapt Abilities are all accessible in the in-game GUI by right-clicking any bookshelf **face**, providing a more
user-friendly experience to a "skills" system. Most servers aim to enhance the quality of the "vanilla" experience.
However, most skill-based plugins offer powers, game-breaking systems, and are riddled with bugs. That's where Adapt
comes in, being lightweight on the server and providing mere quality-of-life enhancements to a user's experience.

Below is a **WIP** list of features _(and descriptions)_ that I'll fill in when I can. But this should give you a good
idea of the roadmap for this plugin! Keep in mind that this is all WIP, can change at any time, and all of these
features can be configured/disabled!

The master branch is for the latest version of Minecraft.

### Language and Localization

Do you know a language other than English? Do you want to play a big part in Adapt's localization into different
languages? Join the [Discord](https://discord.gg/volmit) and let us know or visit
the [gitlocalize repository](https://gitlocalize.com/repo/8085) to help remotely with language localizations!

If you don't see a language you can easily add it, or let us know here in discussions! We take this on an honor system,
so please submit a translation key only if you are confident in the language, and they will be verified.

# [Support](https://discord.gg/volmit) **|** [Documentation](https://docs.volmit.com/adapt/)

## Building

### Download .jar release

**Consider supporting our development by buying Adapt**
On [SpigotMC](https://www.spigotmc.org/resources/adapt-leveling-skills-and-abilities.103790/)! We work hard to make
Adapt
the best it can be for everyone.

### Build your own .jar

**Building Adapt can be challenging and requires some technical knowledge**, such as
with [Iris](https://www.spigotmc.org/resources/iris-world-gen-custom-biome-colors.84586/). However, you will need to set
up a few things if your system has never been used for Java development.

<details>

<summary> Build Steps </summary>

### So this is fairly similar to Iris, but a bit modified.

### IDE Builds (for development & Compilation)

You NEED TO BE USING Intelij To build this project, or anything that can support the
plugin [Manifold](https://plugins.jetbrains.com/plugin/10057-manifold)

## Preface: if you need help compiling and you are a developer / intend to help out in the community or with development we would love to help you regardless in the discord! however do not come to the discord asking for free copies, or a tutorial on how to compile.

1. Install [Java JDK 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
2. Set the JDK installation path to `JAVA_HOME` as an environment variable.
    * Windows
        1. Start > Type `env` and press Enter
        2. Advanced > Environment Variables
        3. Under System Variables, click `New...`
        4. Variable Name: `JAVA_HOME`
        5. Variable Value: `C:\Program Files\Java\jdk-17.0.1` (verify this exists after installing java don't just copy
           the example text)
    * MacOS
        1. Run `/usr/libexec/java_home -V` and look for Java 17
        2. Run `sudo nano ~/.zshenv`
        3. Add `export JAVA_HOME=$(/usr/libexec/java_home)` as a new line
        4. Use `CTRL + X`, then Press `Y`, Then `ENTER`
        5. Quit & Reopen Terminal and verify with `echo $JAVA_HOME`. It should print a directory

3. Setup Gradle

<details>
<summary> Gradle Setup </summary>

* Run `gradlew setup` any time you get dependency issues with craftbukkit
* Configure ITJ Gradle to use JDK 17 (in settings, search for gradle)
* Resync the project & run your newly created task (under the development folder in gradle tasks!)

</details>

1. INSTALL [MANIFOLD](https://plugins.jetbrains.com/plugin/10057-manifold)
2. If this is your first time building Adapt for MC 1.19+ run `gradlew setup` inside the root Adapt project folder.
   Otherwise, skip this step. Grab a coffee, this may take up to 5 minutes depending on your cpu & internet connection.
3. Once the project has setup, run `gradlew adapt`
4. The Adapt jar will be placed in `Adapt/build/Adapt-XXX-XXX.jar` Enjoy! Consider supporting us by buying it on spigot!

</details>


<details>
<summary> SKILLS </summary>

_The skills listed below are the fundamentals that we want to implement. However, please feel free to make an issue
request for any ideas or additional abilities that you would like to see in Adapt. Keep in mind that simpler ideas are
preferred, but complex ones are welcome too!_

## Agility:

- [ ] Slide?
- [X] Super jump (Allows a Crouch jump to launch yourself up to 5 blocks High)
- [X] Wall jump (Jump on walls)
- [X] Wind-Up (Sprint and go faster)
- [X] Armor-Up (Sprint and get more armor)(you need to have it equipped)
- [ ] Running start, Sprint = Jump boost
- [ ] Climb WOod

## Architect:

- [X] Temporary blocks (Crouch off a ledge)
- [X] BuildersWand (Small) (You can place up to 16 blocks at once)
- [ ] TypeReplace Blocks
- [X] DontBreakGlass (Passive Silk-Touch for Glass only)
- [ ] Forced Leaf Decay

## Axe:

- [ ] Tomahawk Throw
- [X] Drop to inventory
- [X] Axe Ground-Smash
- [X] Axe TreeFeller
- [ ] StripLogger (Sticks got from stripping)
- [ ] Speedy/Hasty Axe
- [ ] Wood Dupe?

## Brewing:

- [ ] Chance not to consume potion
- [ ] Chance to refund ingredients
- [X] Lingering Potions (Crafted potions last longer)
- [X] Splash Range Increase (Chance to increase Range)

## Crafting:

- [X] Xp for crafting
- [ ] Chance for Extras
- [ ] offhand autocrafting
- [X] Deconstruction Table (De-craft to basics)

## Discovery:

- [ ] Tiny Potato
- [ ] Armored Elytras
- [X] Worldly Armor
- [X] Passive XP
- [ ] Villager Attitude
- [X] Xp Damage Mitigation

## Enchanting:

- [X] XP Refund
- [X] Lapis Refund (Chance per enchant to give Lapis)
- [X] In-Inventory Enchanting (Books to Items)
- [ ] Xp for making Bookshelf/Book/Table
- [ ] Better Enchant Levels

## Excavation**:

- [ ] Dirt/Grass does not consume Durability
- [X] Haste while digging
- [X] Drop to inventory
- [X] MultiTool (Merge multiple tools into one)

## Herbalism:

- [X] Hunger Shield (up to 50% less hunger consumption)
- [X] Drop to inventory
- [X] Replanted (replant items by right-clicking)
- [ ] Harvest Dupes
- [X] Food feeds more
- [X] Herbalist Luck (breaking things can give you things)
- [X] Herbalist's Myconid (craftable Mycelia)
- [ ] Instant Food Consumption (Cooldown)
- [ ] Xp Gain
- [X] Faster Grow Aura

## Hunter:

- [X] Adrenaline (more damage lower the health)
- [X] Drop to inventory
- [X] Regen while in combat -> massive loss in hunger
- [X] Resistance in combat -> massive loss in hunger
- [X] Speed while in combat -> massive loss in hunger
- [X] JumpBoost while in combat -> massive loss in hunger
- [X] Luck while in combat -> massive loss in hunger
- [X] Invisibility while in combat -> massive loss in hunger
- [ ] Prevent the first damage proc

## Nether:

- [X] Wither Resist (Resistance to wither)
- [X] Wither Skull Throw (Pvsshhh)
- [ ] Soul Speed
- [ ] Nether Tools Apply Wither
- [ ] Nearby Withering applies regen

## Pickaxe:

- [X] Chisel ores (more ore, less durability)
- [X] Vein-miner (Vein-miner)
- [ ] Locate Nearest Ore:
- [ ] HammerMiner -> more duration cost
- [X] Auto-smelt % chance
- [X] Drop to inventory
- [ ] Chance not to eat Durability

## Ranged**:

- [X] Ranged Arrow Recovery (On hit, chance to refund)
- [X] Ranged Force (More dps at range)
- [X] Lunge SHot (Lunging will do damage)
- [X] Piercing Shot (Pierce through enemies)

## Rift:

- [X] Remote Container Access (Remote Container Access)
- [X] Short-Ranged "blink" (teleport)
- [X] No-Place Enderchest (like /ec )
- [X] Rift Recall (Teleport to a location)
- [X] Resilience based on Ender Artifact Used (blink = 10% Enderperal = 25% etc)

## Seaborn:

- [X] WaterBreathing
- [X] Passive Speed bonus while swimming
- [ ] Night vision underwater
- [ ] Passive Fish?
- [ ] Water Refiles Hunger/regen

## Stealth:

- [X] Snatching (close-range item Vacuum)
- [X] Sneak-Speed (Destroy FOV in a single button press)
- [X] Ghost Armor (Armor passively that grown on you, but only works for 1 hit)
- [X] StealthSight
- [ ] Sneak Attack

## Swords:

- [X] Machete (chopping blocks down)
- [ ] Throwing Knife
- [ ] Bleed Damage
- [ ] More damage to Non-Armored Enemies
- [ ] Turrets, Deploy Swords, that fling to a target

## Taming:

- [X] Tame Health Boost (Tames have more health)
- [X] Tame Damage Boost (Tames do more DPS)
- [X] Tame Health Regen (Tames have passive regen)
- [ ] Tamed Vampirism  (Familiar)

## Unarmed:

- [X] Unarmed Power (Make unarmed Viable)
- [X] Sucker Punch (One PunCh!)
- [ ] One-Punch man?
- [X] Glass Cannon (Less Armor = More damage to / from you)
- [ ] Remote Grab?
- [ ] Increased Boss Damage
- [ ] Passive Strength while unarmed

## Chronos: _(Unimplemented)_

- [ ] Chronos Slowdown (Passive Slowdown for entities in the world near you)
- [ ] Chronos Speed (Passive Speed for entities in the world near you)

## TragOul: _(Unimplemented)_

- [ ] Blood Mechanich and hurt yourself to get X

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
