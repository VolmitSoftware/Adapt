# Overview

Adapt is a Paper/Purpur/Folia skills plugin for the Minecraft 26.1 API line. Players earn experience on skill lines, spend knowledge and ability power to learn adaptations, and open the main menu through a configured activator block or command. Optional Mutations, protection plugins, PlaceholderAPI, Vault, HiddenOre, Iris, AdvancedChests, MagicCosmetics, Redis/Velocity, and a Java API extend the runtime.

## Feature map

- **Skills** — twenty-three lines (Agility through Unarmed). Each awards XP from gameplay and owns adaptations. `10 - Skills Catalog.md` indexes them; docs `11`–`33` list each line's XP sources, milestones, adaptations, events, costs, and settings.
- **Adaptations** — three hundred-plus purchasable abilities; each skill doc states what they do and **how they activate**, plus levels, costs, events, and TOML under `plugins/Adapt/adapt/adaptations/`.
- **Progression** — skill XP, knowledge, master XP/level, ability power budget, optional wisdom on cap. See `02 - Concepts.md` and `05 - Configuration Math.md`.
- **GUI** — skills list, adaptation lists, level pickers, mutation menu, in-game config editor. Activator block and `/adapt gui`. See `03 - Player Usage.md` and `06 - GUI Customization.md`.
- **Mutations** — experimental dual-slot traits with domains, combat lock, and perfect adaptation. See `34`–`35`.
- **Protection** — WorldGuard flags and claim plugins via `ProtectorRegistry`. See `08 - Protection & Region Policy.md`.
- **Integrations** — PlaceholderAPI, Vault, HiddenOre, Iris, AdvancedChests, MagicCosmetics, and Velocity/Redis. See `09 - Integrations.md`.
- **Public API** — ability use policy, ability cost providers, protectors, events, PlaceholderAPI. See docs `41`–`50`.

## Documentation index

| File | Covers |
|------|--------|
| `00 - Overview.md` | This file |
| `01 - Installation & Configuration.md` | Install, data folder, `adapt.toml` |
| `02 - Concepts.md` | Skills, adaptations, XP, knowledge, power |
| `03 - Player Usage.md` | Bookshelf, GUI, learning |
| `04 - Commands & Permissions.md` | Commands and permission nodes |
| `05 - Configuration Math.md` | Curves, XP, power, farm prevention |
| `06 - GUI Customization.md` | Icons, order, window size |
| `07 - Localization.md` | Languages and overrides |
| `08 - Protection & Region Policy.md` | WorldGuard and claim protectors |
| `09 - Integrations.md` | Soft depends and bridges |
| `10 - Skills Catalog.md` | All skills index |
| `11`–`33` | Per-skill adaptation reference |
| `34 - Mutations Overview.md` | Mutation system |
| `35 - Mutations Catalog.md` | All mutation types |
| `36 - Items, Orbs & Bound Objects.md` | Orbs and skill items |
| `37 - Recipes, Brewing & Value.md` | Recipes and brewing |
| `38 - Runtime Architecture.md` | Boot, tick, data, Folia |
| `39 - Velocity & Cross-Server.md` | Proxy module |
| `40 - Operator Runbooks & Smoke Tests.md` | Checklists |
| `41`–`50` | Public API |

Docs `01`–`40` are for operators and players; `41`–`50` are for plugin developers.

## Project layout

| Path | Role |
|------|------|
| `src/main/java/art/arcane/adapt/Adapt.java` | Plugin entry |
| `src/main/java/art/arcane/adapt/api/` | Registries, XP, ability pipeline, mutations, world/player types |
| `src/main/java/art/arcane/adapt/content/skill/` | Skill implementations |
| `src/main/java/art/arcane/adapt/content/adaptation/` | Adaptation implementations |
| `src/main/java/art/arcane/adapt/content/gui/` | Inventory GUIs |
| `src/main/java/art/arcane/adapt/content/protector/` | Claim protectors |
| `src/main/java/art/arcane/adapt/command/` | Director command tree |
| `src/main/java/art/arcane/adapt/localization/` | English catalogs and language writer |
| `src/main/java/art/arcane/adapt/papi/` | PlaceholderAPI expansion |
| `src/main/java/art/arcane/adapt/service/` | Hotload, mutation, command services |
| `velocity/` | Velocity/Redis companion module |
| `docs/` | This documentation tree |

## Building

Java 25. From `Adapt/`:

```bash
./gradlew build
./gradlew test
./gradlew shadowJar
```

Runtime and API consumers should use the shaded `build/libs/Adapt-*-all.jar`. See `41 - API - Getting Started.md`.

## See also

- `01 - Installation & Configuration.md`
