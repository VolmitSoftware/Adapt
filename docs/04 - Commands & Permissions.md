# Commands & Permissions

Adapt exposes a single root command `/adapt` through the Director framework (`CommandSVC` + `CommandAdapt`). Every invocation requires `adapt.main` before routing. Nested subcommands add their own permission checks.

## Root

| Property | Value |
|----------|-------|
| Command | `/adapt` |
| Declared in | `plugin.yml` |
| Dispatcher gate | `adapt.main` (default op) |
| Nested roots | `clear`, `reset`, `default`, `debug` (`dev`), `mutations` |

Player-facing nodes such as `adapt.effects` (default true) still require `adapt.main` as well.

`/adapt help`, `/adapt ?`, and help paths resolved by the Director mini-menu show the command tree and paginated parameter help. Help still passes through the `adapt.main` dispatcher gate.

## Main handlers (`CommandAdapt`)

| Syntax | Origin | Permission | Effect |
|--------|--------|------------|--------|
| `/adapt boost [seconds=10] [multiplier=10] [player]` | both | `adapt.boost` | Temporary XP multiplier for a player |
| `/adapt global-boost [seconds=10] [multiplier=10]` | both | `adapt.boost.global` | Temporary global XP multiplier |
| `/adapt gui [target=main] [player] [force=false]` | both | `adapt.gui` | Open GUI; `target` is `main`, `skill:<name>`, or `adaptation:<name>`; `force=true` bypasses that target's use-permission check but not its enabled state |
| `/adapt effects [enabled=toggle]` | player | `adapt.effects` | Toggle own effect visibility (`toggle` / true / false synonyms) |
| `/adapt configure` (`config`, `cfg`) | player | `adapt.configurator` or op | In-game config editor |
| `/adapt experience <skill> [amount=10] [player]` | both | `adapt.cheatitem` | Give a snowball orb that awards stored XP when thrown; skill may be name, `all`, or `random` |
| `/adapt knowledge <skill> [amount=10] [player]` | both | `adapt.cheatitem` | Give a snowball orb that awards stored knowledge when thrown |
| `/adapt determine <skill:adaptation> <assign> <force> <level> [player]` | both | `adapt.determine` | Admin learn/unlearn transaction |
| `/adapt claim-skill <skill> <level> [player]` | both | `adapt.determine` | Set skill line level **0–100** (XP to that level) |
| `/adapt claim-adaptation <skill:adaptation> <level> [force=false] [player]` | both | `adapt.determine` | Set adaptation level 0–100 clamped to max via learning transaction |
| `/adapt migrate-configs` | both | `adapt.debug` | Canonicalize skill/adaptation TOML, then recursively delete legacy JSON below `adapt/` when its TOML peer exists |

Console must pass an explicit player where the handler needs one.

## `/adapt clear` (`CommandClear`)

Permission on every subcommand: `adapt.clear`. Online players only.

| Syntax | Effect |
|--------|--------|
| `/adapt clear all [player]` | XP, knowledge, adaptations, stats, discoveries, advancements, multipliers, wisdom, mutations |
| `/adapt clear xp [player]` | XP on all skill lines; also clears all skill-line adaptations, master XP, and inspired skill (mutations kept) |
| `/adapt clear knowledge [player]` | Knowledge on all skill lines |
| `/adapt clear adaptations [player]` | Unlearn all adaptations |
| `/adapt clear stats [player]` | Stats map |
| `/adapt clear discoveries [player]` | Discovery data |

There is no separate clear for advancements/wisdom alone; use `all`. Mutation-only wipe: `/adapt mutations reset`.

## `/adapt reset` (`CommandReset`)

| Syntax | Auth | Effect |
|--------|------|--------|
| `/adapt reset confirm [player]` | `adapt.clear` | Two-step confirm within 30s; `AdaptServer.resetPlayerData` (online players reset in place, never kicked; message `DATA_DELETED_KICK` is chat-only) |

Accepts offline player targets. Distinct from clear (full wipe of Adapt player data including permanent offline delete path).

## `/adapt default` (`CommandDefault`)

Auth: `adapt.configurator` on every subcommand.

| Syntax | Effect |
|--------|--------|
| `/adapt default skill <skill>` | Delete skill TOML, hot-reload, reconcile mutations |
| `/adapt default adaptation <skill:adaptation>` | Delete adaptation TOML, hot-reload |
| `/adapt default all` | Archive under `config-archive/<timestamp>/`, delete core/skill/adaptation TOML, then regenerate and reload them |

`default all` does not reset `mutations.toml`, `models.toml`, language overrides, SQL/Redis data, or player progression.

## `/adapt debug` (`dev`) (`CommandDebug`)

| Syntax | Permission | Effect |
|--------|------------|--------|
| `/adapt debug mode [enabled] [player]` | `adapt.debug` | Debug mode: reveal all; free uncapped learning |
| `/adapt debug verbose` | `adapt.idontknowwhatimdoingiswear` | Toggle in-memory verbose logging |
| `/adapt debug pap` | `adapt.idontknowwhatimdoingiswear` | Print adaptation use permission nodes |
| `/adapt debug psp` | `adapt.idontknowwhatimdoingiswear` | Print skill use permission nodes |
| `/adapt debug particle <particle>` | `adapt.idontknowwhatimdoingiswear` | Test particle (player) |
| `/adapt debug sound <sound>` | `adapt.idontknowwhatimdoingiswear` | Test sound (player) |
| `/adapt debug perf [top=12] [reset=false]` | `adapt.idontknowwhatimdoingiswear` | Ability-check and ticker metrics |

## `/adapt mutations` (`CommandMutation`)

| Syntax | Origin | Permission | Effect |
|--------|--------|------------|--------|
| `menu` | player | `adapt.mutations` | Open `MutationGui` |
| `view [player]` | both | self: `adapt.mutations`; other: `adapt.mutations.admin` | Snapshot |
| `cooperative [on\|off\|toggle]` | player | `adapt.mutations` | Cooperative opt-in |
| `equip <mutation> <1\|2> [player]` | both | `adapt.mutations.admin` | Force equip |
| `clear <1\|2> [player]` | both | `adapt.mutations.admin` | Clear slot |
| `discover <mutation> [true\|false] [player]` | both | `adapt.mutations.admin` | Discover/undiscover |
| `cooldown [player]` | both | `adapt.mutations.admin` | Clear switch + combat lock |
| `refresh [player]` | both | `adapt.mutations.admin` | Reconcile |
| `slot-override <1\|2> <on\|off\|clear> [player]` | both | `adapt.mutations.admin` | Force slot unlock state |
| `reset [player]` | both | `adapt.mutations.admin` | Clear mutation data only |
| `perfect-test [on\|off\|clear] [player]` | both | `adapt.mutations.admin` | Force perfect adaptation |
| `reload` | both | `adapt.mutations.admin` | Reload `mutations.toml` |

## Static permissions (`plugin.yml`)

| Node | Default | Description |
|------|---------|-------------|
| `adapt.main` | op | Root `/adapt` gate |
| `adapt.idontknowwhatimdoingiswear` | op | Developer debug tools |
| `adapt.cheatitem` | op | XP/knowledge orbs |
| `adapt.boost` | op | Player XP boost |
| `adapt.boost.global` | op | Global XP boost |
| `adapt.gui` | op | Open GUI via command |
| `adapt.determine` | op | Determine / claim-skill / claim-adaptation |
| `adapt.debug` | op | Debug mode + migrate-configs |
| `adapt.configurator` | op | Config editor |
| `adapt.effects` | true | Toggle own effects |
| `adapt.mutations` | true | Personal mutations (feature must be enabled) |
| `adapt.mutations.admin` | op | Mutation admin tools |
| `adapt.clear` | op | Clear subcommands |

## Dynamic use permissions

Registered after skills load by `AdaptPermissionRegistrar`:

| Pattern | Default | Meaning |
|---------|---------|---------|
| `adapt.use.<skillNameWithoutHyphens>` | true | Skill + child adaptations |
| `adapt.use.<adaptationNameWithoutHyphens>` | true | Adaptation (`agility-air-dash` → `adapt.use.agilityairdash`) |
| `adapt.use.mutation.<mutation-id>` | true | Mutation type (hyphens kept, e.g. `adapt.use.mutation.gale-lung`) |
| `adapt.use.*` | true | Parent over skill and mutation nodes |

Unset use nodes are treated as granted. Op and debug mode bypass use checks. XP multiplier nodes from `permissionXpMultipliers` register with default **false** when that feature is enabled.

## See also

- `03 - Player Usage.md`
- `34 - Mutations Overview.md`
- `40 - Operator Runbooks & Smoke Tests.md`
