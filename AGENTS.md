# Adapt Agent Guide

Adapt is a skills and abilities plugin for Paper/Purpur/Folia: twenty-three skill lines, 312 documented adaptation types, optional Mutations, bookshelves as the player GUI activator, and a public Java integration API. Read this file before making any change; the workspace-level `../AGENTS.md` also applies when working inside the VolmitSoftware workspace.

## Documentation Policy (mandatory)

- `docs/` is the authoritative reference for every feature of this plugin. Files are flat (no subfolders) and numbered `NN - Title.md`, ordered for someone new to the plugin; API docs always keep the highest numbers.
- ANY change that alters a feature, behavior, workflow, command, permission, setting, skill, adaptation, mutation, config TOML shape, protection/integration behavior, PlaceholderAPI key, or API surface MUST update the matching numbered doc in the same workstream. A behavior change with stale docs is an incomplete change — do not finish work without the doc update.
- Docs state actual runtime behavior, not intended behavior. If a change fixes a documented quirk, update or remove that quirk entry. If a change introduces surprising behavior, document it plainly.
- Docs are purely factual reference material: no marketing language, no emojis, no filler. Each file opens with a 1-4 sentence summary.
- Cross-references use exact filenames (for example `see "04 - Commands & Permissions.md"`). When adding or renumbering files, fix every cross-reference.
- Hosted external docs are not authority; this `docs/` tree is.
- When adding a new skill or adaptation, update `10 - Skills Catalog.md` and the matching `NN - Skill - <Name>.md` file. When adding a Mutation type, update `34 - Mutations Overview.md` and `35 - Mutations Catalog.md`.

## Doc Index

| File | Covers |
|------|--------|
| `00 - Overview.md` | What Adapt is, feature map, doc index, building |
| `01 - Installation & Configuration.md` | Install, data folder, `adapt.toml`, soft depends |
| `02 - Concepts.md` | Skills, adaptations, XP, knowledge, power, master level, wisdom |
| `03 - Player Usage.md` | Bookshelf activator, GUI, learning, effects |
| `04 - Commands & Permissions.md` | Every `/adapt` command and permission node |
| `05 - Configuration Math.md` | Level curves, XP formulas, power, farm prevention |
| `06 - GUI Customization.md` | Skills GUI size, icons, ordering |
| `07 - Localization.md` | Locales, catalogs, overrides |
| `08 - Protection & Region Policy.md` | WorldGuard flags and claim protectors |
| `09 - Integrations.md` | PlaceholderAPI, Vault, HiddenOre, Iris, Velocity |
| `10 - Skills Catalog.md` | Index of all twenty-three skills + quick usage table for every adaptation |
| `11`–`33` | Per-skill usage: how the skill works, how each adaptation activates, costs, events, config |
| `34 - Mutations Overview.md` | Mutation slots, opt-in, combat lock, perfect adaptation |
| `35 - Mutations Catalog.md` | All fifteen mutation types |
| `36 - Items, Orbs & Bound Objects.md` | Orbs, skill items, matter |
| `37 - Recipes, Brewing & Value.md` | Recipe book, brewing, material value |
| `38 - Runtime Architecture.md` | Boot, hotload, tick, Folia, player data, SQL |
| `39 - Velocity & Cross-Server.md` | Velocity module and Redis |
| `40 - Operator Runbooks & Smoke Tests.md` | Manual verification checklists |
| `41 - API - Getting Started.md` | Dependency setup, integration surface map |
| `42 - API - Skills & Adaptations.md` | Skill/Adaptation registries and learning |
| `43 - API - Ability Use Policy.md` | `AbilityUsePolicy` |
| `44 - API - Ability Cost.md` | `AbilityCostProvider` |
| `45 - API - Events.md` | Ability and adaptation events |
| `46 - API - Protection.md` | `Protector` / `ProtectorRegistry` |
| `47 - API - PlaceholderAPI.md` | `%adapt_…%` keys |
| `48 - API - Mutations.md` | Public mutation types |
| `49 - API - Player Data, XP & World.md` | `AdaptPlayer`, XP, player data |
| `50 - API - Recipes, FX, Telemetry & Utilities.md` | Remaining public packages |

Docs `00`–`40` serve operators and players in reading order; docs `41`–`50` serve plugin developers.

## Content Model

- **Skills** (`content/skill`) — skill lines with XP sources, milestones, and registered adaptations.
- **Adaptations** (`content/adaptation/<skill>`) — purchasable abilities under a skill; config under `plugins/Adapt/adapt/adaptations/<id>.toml`.
- **Mutations** (`api/mutation`, `content/mutation`) — experimental dual-slot traits; opt-in.
- **GUI** (`content/gui`) — skills, mutation, and config editor menus.
- **Protectors** (`content/protector`) — claim/region gates.
- **Public third-party API** — ability policy/cost, protectors, events, PlaceholderAPI, and documented `api/*` types. Internal helpers under `util/` are not a stable third-party contract unless listed in API docs.

## Build and Test

- Java 25, compiled with `-parameters`. Independent Gradle build from `Adapt/`: `./gradlew build`, `./gradlew test`, `./gradlew shadowJar`.
- Prefer the shaded `Adapt-*-all.jar` for runtime and for compile-only API consumers.
- Deploy only to the workspace Multiplexor test-server path when live-verifying; do not place jars into arbitrary server folders.
