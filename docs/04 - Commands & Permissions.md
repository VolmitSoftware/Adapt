# Commands & Permissions

Adapt registers exactly one Bukkit command, `/adapt`, and everything else hangs off it as a subcommand. Before any routing happens, the dispatcher checks `adapt.main`. A sender without that node gets a permission message and nothing else, including no help text. Once past that gate, each subcommand checks its own permission node on top.

Almost every node defaults to op. The exceptions are `adapt.effects` and `adapt.mutations`, which default to true so ordinary players can toggle their own visuals and manage their own mutations, and the whole dynamically registered `adapt.use.*` family, which controls gameplay rather than commands. Holding a default-true node still does not get anyone past `adapt.main`.

`/adapt help`, `/adapt ?`, and any partial path render a paginated tree of subcommands and their parameters. Tab completion for skill and adaptation arguments only offers components that are currently enabled. Console can run most subcommands but has no implicit "you", so it refuses wherever a handler needs a player and you did not name one. The player-only subcommands are `/adapt effects`, `/adapt configure`, `/adapt mutations menu`, `/adapt mutations cooperative`, `/adapt debug particle`, and `/adapt debug sound`.

## Handing out progression

`/adapt boost` gives one player a temporary XP multiplier and `/adapt global-boost` does it for the whole server. Both take a duration in seconds and a multiplier, and both expire on their own.

`/adapt experience` and `/adapt knowledge` grant nothing directly. They put a snowball orb in the target's inventory that pays out when thrown. The skill argument takes a skill name, `all` for an orb covering every registered skill, or `random`. Both need `adapt.cheatitem`.

`/adapt determine` runs a learn or unlearn exactly as if the player did it in the menu, with a `force` flag to skip costs and restrictions. `/adapt claim-adaptation` is the higher-level version: give it a target level, and it works out the direction, runs the learning transaction, and names the reason when it fails, whether that is power, knowledge, funds, or a permanent adaptation it may not lower. `/adapt claim-skill` sets a skill line's level directly by writing the XP for that level. All three need `adapt.determine`, and both claim commands reject a level outside 0 to 100 before doing anything.

## Opening menus for someone

`/adapt gui` opens the Adapt UI for yourself or a named player, targeting `main`, `skill:<name>`, or `adaptation:<name>`. Passing `force=true` skips that component's `adapt.use` check but never its enabled state, so a disabled skill or adaptation still refuses to open. The `main` target opens the root skills menu without any of those checks.

`/adapt configure` opens the config editor in a menu and needs `adapt.configurator` or op. `/adapt effects` toggles your own Adapt particles and sounds; it accepts `on`, `true`, `yes`, `enabled` and their opposites, and toggles when given nothing.

## Wiping player data

There are two tools here and they are not interchangeable.

`/adapt clear` is surgical and works on online players only. Each subcommand wipes one slice: XP, knowledge, adaptations, the stats map, discovery data, or all of the above. Clearing XP is heavier than the name suggests, because it also empties every skill line's adaptations, resets the anti-farm pressure state, and resets master XP and the Inspired skill. Mutation data survives it, and `/adapt mutations reset` is the way to wipe only that. There is no standalone clear for advancements or wisdom; `/adapt clear all` is the only path to those.

`/adapt reset confirm` is the full delete and accepts offline targets. Run it once to get the warning, then run it again with the same target within 30 seconds. An online target has their profile replaced in place: attribute modifiers are unlearned, the mutation loadout dissolves, adaptation recipes are un-discovered, and the empty profile is saved immediately. They are not kicked, despite the wording of the message they receive. An offline target has their stored data purged instead. Both commands need `adapt.clear`.

## Resetting and migrating configs

`/adapt default skill`, `/adapt default adaptation`, and `/adapt default all` delete the relevant TOML, regenerate it from defaults, and reconcile mutations for online players. `default all` archives what it deletes first. These need `adapt.configurator`; see `01 - Installation & Configuration.md` for what `default all` does and does not touch.

`/adapt migrate-configs` rewrites every skill and adaptation config in canonical TOML form, then walks everything below `adapt/` and deletes each legacy JSON file that already has a TOML peer, reporting the counts. It needs `adapt.debug`.

## Developer tools

`/adapt debug` (alias `dev`) holds the tools you should not hand out. `/adapt debug mode` reveals every skill and adaptation regardless of progression and makes learning free and uncapped, by short-circuiting the power budget, the knowledge spend, and the over-budget pruner. It uses `adapt.debug`, the same node as `migrate-configs`.

Everything else under `debug` sits behind `adapt.idontknowwhatimdoingiswear`. `verbose` flips diagnostic logging in memory without writing the config file. `pap` and `psp` print the generated `adapt.use` nodes for adaptations and skills to the console. `particle` and `sound` fire one at your feet for testing. `perf` prints ability-check rates, cache hit ratio, timing budget, and the top ticker hotspots, and can reset those counters afterwards.

## Mutations

`/adapt mutations menu` opens the mutation UI and `/adapt mutations cooperative` sets your own opt-in for group effects. Both are player-only and need `adapt.mutations`. `/adapt mutations view` shows a snapshot: viewing yourself needs `adapt.mutations`, viewing anyone else needs `adapt.mutations.admin`.

Everything else is admin-only: forcing a mutation into a slot, clearing a slot, marking a mutation discovered or not, clearing the switch and combat cooldowns, reconciling a player against current requirements, forcing a slot's unlock state, wiping only mutation data, forcing perfect adaptation on or off, and reloading `mutations.toml`. Several still work while the feature is disabled and will tell you the choice was saved but is not active.

## How use permissions work

Adapt registers a permission node per skill, per adaptation, and per mutation type once skills have loaded. These control gameplay, not commands, and an unset node counts as granted: the check tests whether the node is explicitly set before it looks at the value, so removing a node from a permission plugin denies nothing. You have to set it to false. Ops and anyone in debug mode bypass the check entirely.

If you turn on `permissionXpMultipliers`, those nodes are registered too, and unlike the use nodes they default to false.

## Reference

### Root

| Property | Value |
|---|---|
| Command | `/adapt` |
| Declared in | `plugin.yml` (no aliases) |
| Dispatcher gate | `adapt.main` |
| Nested roots | `clear`, `reset`, `default`, `debug` (alias `dev`), `mutations` |
| Handler classes | `CommandSVC`, `CommandAdapt` |

### `CommandAdapt` subcommands

| Syntax | Origin | Permission |
|---|---|---|
| `/adapt boost [seconds=10] [multiplier=10] [player]` | both | `adapt.boost` |
| `/adapt global-boost [seconds=10] [multiplier=10]` | both | `adapt.boost.global` |
| `/adapt gui [target=main] [player] [force=false]` | both | `adapt.gui` |
| `/adapt effects [enabled=toggle]` | player | `adapt.effects` |
| `/adapt configure` (`config`, `cfg`) | player | `adapt.configurator` or op |
| `/adapt experience <skill> [amount=10] [player]` | both | `adapt.cheatitem` |
| `/adapt knowledge <skill> [amount=10] [player]` | both | `adapt.cheatitem` |
| `/adapt determine <skill:adaptation> <assign> <force> <level> [player]` | both | `adapt.determine` |
| `/adapt claim-skill <skill> <level> [player]` | both | `adapt.determine` |
| `/adapt claim-adaptation <skill:adaptation> <level> [force=false] [player]` | both | `adapt.determine` |
| `/adapt migrate-configs` | both | `adapt.debug` |

`claim-skill` accepts levels 0-100 and writes `XP.getXpForLevel(level)` onto the line. `claim-adaptation` accepts 0-100 and then clamps to `adaptation.getMaxLevel()`.

### `/adapt clear` (`CommandClear`)

Permission on every subcommand: `adapt.clear`. Online targets only.

| Syntax | Effect |
|---|---|
| `/adapt clear all [player]` | Runs every clear below, then also empties advancements, XP multipliers, wisdom, and mutation data |
| `/adapt clear xp [player]` | Zeroes XP, pooled XP, last-level tracking, and monotony state on every line, empties every line's adaptations, and resets the Inspired skill; sets master XP to 1 |
| `/adapt clear knowledge [player]` | Zeroes knowledge on every skill line |
| `/adapt clear adaptations [player]` | Empties every skill line's adaptations and the region-granted count |
| `/adapt clear stats [player]` | Empties the stats map |
| `/adapt clear discoveries [player]` | Replaces every discovery set: biomes, mobs, foods, items, recipes, enchants, worlds, people, environments, potion effects, blocks |

### `/adapt reset` (`CommandReset`)

| Syntax | Permission | Effect |
|---|---|---|
| `/adapt reset confirm [player]` | `adapt.clear` | Two-step confirm within 30 seconds, then `AdaptServer.resetPlayerData` |

Accepts offline targets. The pending confirmation is keyed by sender and target; console uses the zero UUID as its sender key. Online resets replace the profile in place and send `DATA_DELETED_KICK` in chat without kicking.

### `/adapt default` (`CommandDefault`)

Permission on every subcommand: `adapt.configurator`.

| Syntax | Effect |
|---|---|
| `/adapt default skill <skill>` | Deletes the skill TOML, hot-reloads it, reconciles mutations |
| `/adapt default adaptation <skill:adaptation>` | Deletes the adaptation TOML, hot-reloads it, reconciles mutations |
| `/adapt default all` | Archives `adapt.toml` plus every skill and adaptation TOML under `config-archive/<timestamp>/`, deletes them, reloads from defaults, reconciles mutations |

The `skill` and `adaptation` subcommands only accept `SimpleSkill` and `SimpleAdaptation` components; anything else reports that reset is unsupported. `default all` deletes every `.toml` in both folders regardless.

### `/adapt debug` (`dev`) (`CommandDebug`)

| Syntax | Origin | Permission |
|---|---|---|
| `/adapt debug mode [enabled=toggle] [player]` | both | `adapt.debug` |
| `/adapt debug verbose` | both | `adapt.idontknowwhatimdoingiswear` |
| `/adapt debug pap` | both | `adapt.idontknowwhatimdoingiswear` |
| `/adapt debug psp` | both | `adapt.idontknowwhatimdoingiswear` |
| `/adapt debug particle <particle>` | player | `adapt.idontknowwhatimdoingiswear` |
| `/adapt debug sound <sound>` | player | `adapt.idontknowwhatimdoingiswear` |
| `/adapt debug perf [top=12] [reset=false]` | both | `adapt.idontknowwhatimdoingiswear` |

`particle` refuses any particle whose Bukkit data type is not `Void`.

### `/adapt mutations` (`CommandMutation`)

| Syntax | Origin | Permission |
|---|---|---|
| `menu` | player | `adapt.mutations` |
| `view [player]` | both | `adapt.mutations` for yourself, `adapt.mutations.admin` for anyone else |
| `cooperative [on\|off\|toggle]` | player | `adapt.mutations` |
| `equip <mutation> <1\|2> [player]` | both | `adapt.mutations.admin` |
| `clear <1\|2> [player]` | both | `adapt.mutations.admin` |
| `discover <mutation> [discovered=true] [player]` | both | `adapt.mutations.admin` |
| `cooldown [player]` | both | `adapt.mutations.admin` |
| `refresh [player]` | both | `adapt.mutations.admin` |
| `slot-override <1\|2> <on\|off\|clear> [player]` | both | `adapt.mutations.admin` |
| `reset [player]` | both | `adapt.mutations.admin` |
| `perfect-test [on\|off\|clear] [player]` | both | `adapt.mutations.admin` |
| `reload` | both | `adapt.mutations.admin` |

### Static permissions (`plugin.yml`)

| Node | Default | Covers |
|---|---|---|
| `adapt.main` | op | The `/adapt` root gate |
| `adapt.idontknowwhatimdoingiswear` | op | Developer debug tools |
| `adapt.cheatitem` | op | XP and knowledge orbs |
| `adapt.boost` | op | Per-player XP boost |
| `adapt.boost.global` | op | Server-wide XP boost |
| `adapt.gui` | op | Opening the GUI via command |
| `adapt.determine` | op | `determine`, `claim-skill`, `claim-adaptation` |
| `adapt.debug` | op | Debug mode and `migrate-configs` |
| `adapt.configurator` | op | Config editor and config resets |
| `adapt.effects` | true | Toggling your own effects |
| `adapt.mutations` | true | Your own mutations, once the feature is enabled |
| `adapt.mutations.admin` | op | Mutation admin tools |
| `adapt.clear` | op | `clear` and `reset` subcommands |

### Dynamic use permissions

Registered by `AdaptPermissionRegistrar` once skills have loaded.

| Pattern | Default | Covers |
|---|---|---|
| `adapt.use.<skillNameWithoutHyphens>` | true | A skill and, as children, its adaptations |
| `adapt.use.<adaptationNameWithoutHyphens>` | true | One adaptation; `agility-air-dash` becomes `adapt.use.agilityairdash` |
| `adapt.use.mutation.<mutation-id>` | true | One mutation type, hyphens kept, for example `adapt.use.mutation.gale-lung` |
| `adapt.use.*` | true | Parent of every skill and mutation node |
| `permissionXpMultipliers` nodes | false | Registered only while that feature is enabled |

An unset use node is treated as granted, because the check tests `isPermissionSet` first and returns true when the node is absent. Ops and players in debug mode skip the check.

## See also

- `03 - Player Usage.md`
- `01 - Installation & Configuration.md`
- `34 - Mutations Overview.md`
- `40 - Operator Runbooks.md`
