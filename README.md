# Adapt

[![image](https://github.com/VolmitSoftware/Adapt/raw/main/storepage/adapt-tc.png)](https://github.com/VolmitSoftware/Adapt/wiki/Why-did-you-click)

## Overview

_Adapt is a drag and drop solution for balanced passive abilities and skills that players can enjoy on a server._

### Description

Adapt Abilities are all accessible in the in-game GUI (Right-clicking any Bookshelf **_Face_**) providing a more user-friendly experience to a "skills" system. Most servers want to increase the quality of the "vanilla" experience. Most Skill based plugins are about powers, game breaking systems, and riddles with bugs. That's where Adapt comes in.  Lightweight on the server, and providing mere quality of life enhancements to a user's experience. Below is a **WIP** List of features (_and descriptions_) that ill fill when i can. But this should give you a good idea of the roadmap for this plugin! Keep in mine this all WIP, can change at any time, and all of these can be configured  / disabled!

The master branch is for the latest version of minecraft.

### Language and Localization

If you would like to help out in translating the contents of Adapt we would love to have you help translate! We take this on an Honor System, so please only if you are confident in the language submit a translation key. Furthermore all translation keys are here: [[Right here]](https://github.com/VolmitSoftware/Adapt/tree/main/src/main/resources) And to add one all you need to do is use the lang code (en_US for example) and translate all they heys. if [i, Myself](https://github.com/NextdoorPsycho) know the language in any capacity, ill do my best to translate it myself for every file that is there when a new Skill / ABility / Translatable Key. PLESE NOTE: LANGUAGE FILES WILL NOT WORK NOR WILL ADAPT IF YOU TRY TO LOAD A LANGUAGE FILE THAT DOES NOT HAVE ALL KEYS, AND ALL KEYS THAT MATCH THE en_US FILE. Ill preface updates that need lanuage updates with: "Language Update Needed" in the release / Comit logs / Tags

# [Support](https://discord.gg/volmit) **|** [Documentation](https://docs.volmit.com/adapt/)

# Building

### _Consider supporting our development by buying Adapt on [spigot](https://www.spigotmc.org/resources/adapt-leveling-skills-and-abilities.103790/)! We work hard to make Adapt the best it can be for everyone._


Building Adapt is not as Straightforward as [Iris](https://www.spigotmc.org/resources/iris-world-gen-custom-biome-colors.84586/), though you will need to setup a few things if your system has never been used for java development.

<details>

<summary> Build Steps </summary>

### So this is fairly similar to Iris, but a bit modified.

### IDE Builds (for development & Compilation)

You NEED TO BE USING Intelij To build this project, or anything that can support the plugin [Manifold](https://plugins.jetbrains.com/plugin/10057-manifold)

## Preface: if you need help compiling ask for support in the [discord](https://discord.gg/volmit), we give help regardless if you want to donate to us on spigot or compile it here :) we just want to be sure that you are able to use and enjoy the software regardless of circumstance.

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
  
4. INSTALL [MANIFOLD](https://plugins.jetbrains.com/plugin/10057-manifold)
5. If this is your first time building Adapt for MC 1.19+ run `gradlew setup` inside the root Adapt project folder. Otherwise, skip this step. Grab a coffee, this may take up to 5 minutes depending on your cpu & internet connection.
6. Once the project has setup, run `gradlew adapt`
7. The Adapt jar will be placed in `Adapt/build/Adapt-XXX-XXX.jar` Enjoy! Consider supporting us by buying it on spigot!
  
</details>


<details>
<summary> SKILLS </summary>

_The skills below are the fundamentals that we want implemented but PLEASE feel free to make an issue request for an idea/Added Ability into adapt.
Keep in mind it should be simple, but complex ones are welcome too!_

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
- [X] MultiTool (Merge multiple tools into one)

## Herbalism:
- [X] Hunger Shield (up to 50% less hunger consumption)
- [X] Replanted (replant items by right-clicking)
- [ ] Harvest Dupes
- [X] Food feeds more
- [ ] Instant Food Consumption (Cooldown)
- [ ] Xp Gain
- [X] Faster Grow Aura

## Hunter:
- [X] Adrenaline (more damage lower the health)
- [X] Regen while in combat -> massive loss in hunger
- [X] Resistance in combat  -> massive loss in hunger
- [X] Speed while in combat  -> massive loss in hunger
- [X] JumpBoost while in combat  -> massive loss in hunger
- [X] Luck while in combat  -> massive loss in hunger
- [X] Invisibility while in combat  -> massive loss in hunger
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
- [ ] Auto-smelt % chance
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
