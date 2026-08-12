# Overview

Adapt is a skills plugin for Paper, Purpur, and Folia servers. Players earn experience in twenty-three skill lines just by playing, spend the knowledge those lines produce on adaptations (abilities attached to a skill), and hold as many adaptation levels at once as their ability power allows. Nothing is handed out by an operator; you unlock it by doing the activity.

In play it works like this. You mine for a while, Pickaxes levels up and pays you knowledge. You right-click a bookshelf, the Adapt menu opens, and you spend that knowledge on something like faster ore breaking. Some adaptations are passive and start working the moment you buy them. Others give you a gesture: sneak-right-click with a certain item, left-click the air mid-jump, raise a shield just before a hit lands. The menu is the only place a player spends anything, so nobody needs to learn a command.

Around that core sit the optional parts. Experimental Mutations add a second, late-game progression track. Protectors make adaptations respect WorldGuard regions and claim plugins. PlaceholderAPI, Vault, HiddenOre, Iris, AdvancedChests, and MagicCosmetics hook in when those plugins are present. A Velocity and Redis companion module shares player data across a network, and a Java API lets other plugins price, deny, or watch ability use.

This file is the map. Each section below says what a piece is and which doc owns the detail.

## What is in the plugin

**Skills** are the progress lines: Agility, Pickaxes, Chronos, and twenty more. Each one watches for its own activities, pays skill XP, and owns a set of adaptations. `10 - Skills Catalog.md` indexes them; `11` through `33` cover one skill each, including where its XP comes from and how every adaptation activates.

**Adaptations** are what a player buys. Each has levels, a knowledge price per level, and an ability power price it keeps charging while you hold it. Each lives in its own file under `plugins/Adapt/adapt/adaptations/`, so an operator can retune or disable a single ability without touching the rest.

**Progression** runs skill XP to skill level to knowledge, and skill level also feeds a shared master level that sets the ability power budget. Knowledge decides what you can afford, power decides how much you can carry at once. See `02 - Concepts.md` for the model and `05 - Configuration Math.md` for the curves.

**Menus** carry the whole player experience: the skills list, one page per skill, a level picker per adaptation, the mutation menu, and an in-game config editor for admins. See `03 - Player Usage.md` and `06 - GUI Customization.md`.

**Mutations** are a separate opt-in track with two slots, paired domains, a combat lock that stops mid-fight swapping, and an end-game perfect adaptation state. They are off by default and do not use knowledge. See `34 - Mutations Overview.md` and `35 - Mutations Catalog.md`.

**Protection** runs before any adaptation touches the world, so claims and regions hold, and WorldGuard gets custom Adapt flags. See `08 - Protection & Region Policy.md` and `09 - Integrations.md`.

**The public API** lets other plugins deny an ability, charge for it, register a protector, or listen for activation events. None of them can grant an unlearned adaptation. Docs `41` through `50` cover it.

## Building from source

Adapt builds on its own from the `Adapt/` directory and needs a JDK 25 toolchain. Run `./gradlew build` to compile and check, `./gradlew test` for the JVM suite alone, and `./gradlew shadowJar` to produce the shaded jar. Use `build/libs/Adapt-*-all.jar` both at runtime and as the compile-only dependency for API consumers. See `41 - API - Getting Started.md`.

## Reference

### Identity

| Property | Value |
|---|---|
| Plugin version | `2.0.0-26.2` |
| Declared `api-version` | `26.1` |
| Main class | `art.arcane.adapt.Adapt` |
| Command root | `/adapt` |
| Folia | `folia-supported: true` |
| Java toolchain / release | 25 |
| Skill lines | 23 |
| Adaptation types | 312 declared, 311 active without Iris |

### Documentation index

| File | Covers |
|------|--------|
| `00 - Overview.md` | This file |
| `01 - Installation & Configuration.md` | Install, data folder, `adapt.toml` |
| `02 - Concepts.md` | Skills, adaptations, XP, knowledge, power |
| `03 - Player Usage.md` | Activator block, menus, learning |
| `04 - Commands & Permissions.md` | Commands and permission nodes |
| `05 - Configuration Math.md` | Curves, XP, power, farm prevention |
| `06 - GUI Customization.md` | Icons, order, window size |
| `07 - Localization.md` | Languages and overrides |
| `08 - Protection & Region Policy.md` | WorldGuard and claim protectors |
| `09 - Integrations.md` | Soft depends and bridges |
| `10 - Skills Catalog.md` | All skills index |
| `11`-`33` | Per-skill adaptation reference |
| `34 - Mutations Overview.md` | Mutation system |
| `35 - Mutations Catalog.md` | All mutation types |
| `36 - Items, Orbs & Bound Objects.md` | Orbs and skill items |
| `37 - Recipes, Brewing & Value.md` | Recipes and brewing |
| `38 - Runtime Architecture.md` | Boot, tick, data, Folia |
| `39 - Velocity & Cross-Server.md` | Proxy module |
| `40 - Operator Runbooks.md` | Pre-launch and upgrade procedures |
| `41`-`50` | Public API |

Docs `00` through `40` are written for operators and players in reading order. Docs `41` through `50` are for plugin developers.

### Project layout

| Path | Role |
|------|------|
| `src/main/java/art/arcane/adapt/Adapt.java` | Plugin entry |
| `src/main/java/art/arcane/adapt/AdaptConfig.java` | `adapt.toml` model and defaults |
| `src/main/java/art/arcane/adapt/api/` | Registries, XP, ability pipeline, mutations, world/player types |
| `src/main/java/art/arcane/adapt/content/skill/` | Skill implementations |
| `src/main/java/art/arcane/adapt/content/adaptation/` | Adaptation implementations |
| `src/main/java/art/arcane/adapt/content/gui/` | Inventory menus |
| `src/main/java/art/arcane/adapt/content/item/` | Orbs and adaptation-created items |
| `src/main/java/art/arcane/adapt/content/protector/` | Claim and region protectors |
| `src/main/java/art/arcane/adapt/content/integration/` | HiddenOre and Iris bridges |
| `src/main/java/art/arcane/adapt/command/` | Director command tree |
| `src/main/java/art/arcane/adapt/localization/` | English catalogs and language writer |
| `src/main/java/art/arcane/adapt/papi/` | PlaceholderAPI expansion |
| `src/main/java/art/arcane/adapt/service/` | Hotload, mutation, command services |
| `src/main/resources/` | `plugin.yml` and the shipped locale TOMLs |
| `velocity/` | Velocity and Redis companion module |
| `docs/` | This documentation tree |

Soft depends declared in `plugin.yml`: PlaceholderAPI, WorldGuard, Factions, ChestProtect, Residence, GriefDefender, GriefPrevention, LockettePro, HiddenOre, Iris, Vault, AdvancedChests, MagicCosmetics.

## See also

- `01 - Installation & Configuration.md`
- `02 - Concepts.md`
- `03 - Player Usage.md`
