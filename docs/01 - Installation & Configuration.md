# Installation & Configuration

Adapt 2.0.0-26.2 targets the Minecraft/Paper 26.1 API line and requires Java 25. Install the shaded Bukkit jar on each Paper, Purpur, or Folia backend, start once to generate configuration, then edit the canonical TOML files under `plugins/Adapt/`. Most gameplay and presentation settings hot-reload; connection, metrics, and plugin-discovery settings require a restart.

## Install

1. Run a Minecraft 26.1-compatible Paper, Purpur, or Folia server on Java 25.
2. Place `Adapt-*-all.jar` in the backend server's `plugins/` directory.
3. Start the server and confirm Adapt enables without an API-version or dependency error.
4. Stop the server before configuring SQL, Redis, metrics, or optional plugin integrations.
5. Grant `adapt.main` plus the required command nodes. Gameplay `adapt.use.*` nodes default to true, but they do not bypass the root command gate.

The main class is `art.arcane.adapt.Adapt`; the only Bukkit command root is `/adapt`. `plugin.yml` declares `folia-supported: true`.

## Optional Bukkit plugins

| Plugin | Purpose |
|---|---|
| PlaceholderAPI | `%adapt_...%` placeholders |
| WorldGuard | Region flags and protection |
| Factions, ChestProtect, Residence, GriefDefender, GriefPrevention, LockettePro | Claim and container protection |
| Vault | Optional currency charge and refund during learning |
| HiddenOre | Hidden-vein mining, XP, and drop adaptations |
| Iris | Iris tree-feller integration |
| AdvancedChests | Rift Access support for AdvancedChests containers |
| MagicCosmetics | Excludes equipped cosmetic hat/bag slots from Adapt's armor-value calculation |

All are soft dependencies. See `08 - Protection & Region Policy.md` and `09 - Integrations.md` for activation and failure behavior.

## Data folder

```text
plugins/Adapt/
  adapt/
    adapt.toml
    models.toml
    mutations.toml
    skills/<skill-id>.toml
    adaptations/<adaptation-id>.toml
    migrations/
  config-archive/<timestamp>/
  languages/en_US.toml
  languages/<active-locale>.toml
  languages/overrides/<locale>.toml
  data/players/<uuid>.json
  data/players/<uuid>.json.pending-sql
  data/value-cache.json
```

Local player JSON is authoritative only when `sql.enabled = false`. With SQL enabled, a local JSON file can seed an empty SQL record, and `.pending-sql` files preserve shutdown writes that did not reach SQL; see `38 - Runtime Architecture.md`.

## Legacy JSON migration

Adapt recognizes TOML peers for these legacy files:

- `adapt/adapt.json`
- `adapt/models.json`
- `adapt/mutations.json`
- `adapt/skills/<skill-id>.json`
- `adapt/adaptations/<adaptation-id>.json`

Before a needed startup migration, Adapt writes a timestamped ZIP under `adapt/migrations/backups/` and records `.legacy-json-backed-up`. `/adapt migrate-configs` rewrites skill and adaptation TOML and then recursively removes a legacy JSON file anywhere below `adapt/` when its TOML peer exists. The legacy misspelling `value.valueMutlipliers` is migrated to `value.valueMultipliers` when core config loads.

## Core `adapt/adapt.toml`

### General and progression

| Key | Default | Behavior |
|---|---:|---|
| `debug` | `false` | Global debug behavior used by development surfaces |
| `verbose` | `false` | Extra diagnostic logging; `/adapt debug verbose` changes the in-memory value |
| `autoUpdateCheck` | `true` | Runs the startup update check |
| `splashScreen` | `true` | Prints the startup splash |
| `metrics` | `true` | Starts bStats and integration metrics at enable time |
| `language` | `en_US` | Active language and override filename |
| `xpCurve` | `ADAPT_BALANCED` | Skill and master-level curve; see `05 - Configuration Math.md` |
| `experienceMaxLevel` | `1000` | Skill-level cap and curve-search ceiling |
| `playerXpPerSkillLevelUpBase` | `489` | Flat master XP per crossed skill level |
| `playerXpPerSkillLevelUpLevelMultiplier` | `44` | Additional master XP per prior skill level |
| `powerPerLevel` | `0.65` | Ability-power budget per master level |
| `xpInCreative` | `false` | Allows skill XP in creative mode |
| `allowAdaptationsInCreative` | `false` | Allows adaptation effects in creative mode |
| `blacklistedWorlds` | two nonmatching example keys | Namespaced world keys where Adapt gameplay is disabled, such as `minecraft:overworld` |
| `hardcoreResetOnPlayerDeath` | `false` | Clears progression on player death |
| `hardcoreNoRefunds` | `false` | Suppresses knowledge and Vault refunds when unlearning |
| `loginBonus` | `true` | Enables the login bonus |
| `welcomeMessage` | `true` | Sends the Adapt welcome message |
| `advancements` | `true` | Registers and synchronizes Adapt advancements |
| `preventHunterSkillsWhenHungerApplied` | `true` | Blocks configured hunter behavior while hunger effects apply |

World keys are Bukkit namespaced keys, not folder names. Normal vanilla keys are `minecraft:overworld`, `minecraft:the_nether`, and `minecraft:the_end`; custom worlds use the key supplied by their world provider.

### Activator, GUI, and presentation

| Key | Default | Behavior |
|---|---:|---|
| `adaptActivatorBlock` | `BOOKSHELF` | Bukkit material used as the GUI activator |
| `adaptActivatorBlockName` | `a Bookshelf` | Display text used in activator messages |
| `adaptActivatorAllowVerticalFaces` | `false` | Allows top and bottom faces in addition to side faces |
| `useEnchantmentTableParticleForActiveEffects` | `true` | Selects the active-effect particle style |
| `escClosesAllGuis` | `false` | Escape closes the whole stack instead of returning to the parent |
| `guiBackButton` | `true` | Shows navigation buttons |
| `customModels` | `true` | Applies `adapt/models.toml` model mappings |
| `automaticGradients` | `false` | Applies the automatic rendered-text gradient |
| `learnUnlearnButtonDelayTicks` | `14` | Debounces learning and unlearning clicks |
| `maxRecipeListPrecaution` | `25` | Bounds recursive recipe-value traversal |
| `actionbarNotifyXp` | `true` | Shows XP action-bar notifications |
| `actionbarNotifyLevel` | `true` | Shows level notifications |
| `unlearnAllButton` | `false` | Shows the bulk-unlearn control |
| `guiShowAllSkills` | `false` | Shows untouched enabled skills when the player may use them |

The `[gui]` keys, icon precedence, and ordering rules are in `06 - GUI Customization.md`.

### Effects

```toml
[effects]
particlesEnabled = true
soundsEnabled = true

[effects.adaptationParticleOverrides]
"adaptation-name" = true

[effects.skillParticleOverrides]
"skill-name" = true
```

`particlesEnabled` and `soundsEnabled` are global switches. Adaptation and skill particle maps use registry IDs as additional gates: `false` disables that component's particles, while `true` leaves the global decision unchanged. A player's `/adapt effects` preference remains another gate.

### Ability API guard

| Key | Default | Behavior |
|---|---:|---|
| `abilityApi.enabled` | `true` | Enables external ability policy and cost providers through the Bukkit provider gateways |
| `abilityApi.usePolicyFailureMode` | `deny` | `allow` or `deny` when a use-policy provider faults |
| `abilityApi.costProviderFailureMode` | `allow` | `allow` or `deny` when a cost provider faults |
| `abilityApi.providerFaultLimit` | `5` | Consecutive provider faults before quarantine; `0` disables the watchdog |
| `abilityApi.slowProviderMillis` | `2` | Slow-provider warning threshold; `0` disables it |
| `abilityApi.denyMessageThrottleMillis` | `2000` | Minimum interval for repeated denial messages |

Invalid failure-mode strings fall back to `deny` for use policies and `allow` for cost providers. See `43 - API - Ability Use Policy.md` and `44 - API - Ability Cost.md`.

### Learning economy

| Key | Default | Behavior |
|---|---:|---|
| `learningEconomy.enabled` | `false` | Enables Vault currency charges when a provider is available |
| `learningEconomy.moneyPerKnowledge` | `1.0` | Currency charged for each knowledge point spent |
| `learningEconomy.refundPercent` | `100.0` | Percentage of recorded learning charges refunded on normal unlearn |

Without Vault or an economy provider, knowledge-only learning remains available. A failed withdrawal rejects the purchase; failed refunds are stored on the skill line and retried by later learning transactions.

### SQL

| Key | Default | Behavior |
|---|---:|---|
| `sql.enabled` | `false` | Uses the `ADAPT_DATA` SQL table instead of local JSON as authority |
| `sql.host` | `localhost` | MySQL-compatible server host |
| `sql.port` | `3306` | Server port |
| `sql.database` | `adapt` | Existing schema; Adapt creates the table, not the schema |
| `sql.username` | `user` | Account with table create/read/write/delete access |
| `sql.password` | `password` | Account password |
| `sql.poolSize` | `10` | Advancement database pool size |
| `sql.connectionTimeout` | `5000` | JDBC connection timeout in milliseconds; startup clamps it to at least 1000 |
| `sql.secondsCheckverify` | `30` | Connection validation timeout in seconds; startup validation clamps it to 1–10 |

SQL and Redis clients are created only during plugin enable. Restart after changing this section. SQL credentials are placed in the JDBC URL without an Adapt-controlled TLS switch; configure transport security on the database endpoint and driver environment.

### Redis

| Key | Default | Behavior |
|---|---:|---|
| `redis.enabled` | `false` | Enables Redis pub/sub only when SQL is also enabled |
| `redis.host` | `127.0.0.1` | Redis host |
| `redis.port` | `6379` | Redis port |
| `redis.username` | empty | Optional ACL username |
| `redis.password` | empty | Optional password |

Redis is a one-minute handoff cache over SQL, not a storage replacement. It starts only at plugin enable; see `39 - Velocity & Cross-Server.md`.

### Protection and conflicts

```toml
[adaptationUsageConflicts]
"rift-blink" = ["agility-air-dash"]

[protectionOverrides.rift-blink]
WorldGuard = true
GriefPrevention = false
```

`adaptationUsageConflicts` maps an adaptation ID to learned adaptation IDs that prevent its use. `protectionOverrides` starts from the enabled/default protector set and then includes or removes protectors by exact `Protector.getName()` values. Full protector names and defaults are in `08 - Protection & Region Policy.md`.

### Other nested sections

| Section | Reference |
|---|---|
| `value` | `37 - Recipes, Brewing & Value.md` |
| `gui` | `06 - GUI Customization.md` |
| `farmPrevention` | `05 - Configuration Math.md` |
| `adaptationXp` | `05 - Configuration Math.md` |
| `xpIntegrity` | `05 - Configuration Math.md` |
| `permissionXpMultipliers` | `05 - Configuration Math.md` |
| `protectorSupport` | `08 - Protection & Region Policy.md` |

## Mutation configuration

Mutations use `adapt/mutations.toml`, not `adapt.toml`. The feature is off by default.

### Global Mutation keys

| Key | Default | Behavior |
|---|---:|---|
| `enabled` | `false` | Master Mutation switch |
| `slotOneUnlockLevel` | `25` | Master level required for slot 1 |
| `slotTwoUnlockLevel` | `50` | Master level required for slot 2; normalized to at least slot 1 |
| `perfectAdaptationLevel` | `200` | Master level for drawback softening |
| `perfectAdaptationEnabled` | `true` | Enables level-based perfect adaptation |
| `minimumAdaptationLevel` | `1` | Learned adaptation level required in each domain |
| `switchCooldownMillis` | `600000` | Delay after a normal slot change |
| `combatLockMillis` | `10000` | Damage lockout for normal slot changes |
| `switchingEnabled` | `true` | Allows player-controlled switching |
| `permanentSelection` | `false` | Makes first selections admin-clearable only |
| `pvpEnabled` | `true` | Global Mutation PvP effects switch |
| `cooperativeEffectsEnabled` | `true` | Global cooperative-effects switch |
| `cooperativeConsentMode` | `EXPLICIT` | Cooperative recipient rule |
| `bookshelfTokenMillis` | `60000` | Bookshelf authorization lifetime |
| `bookshelfMaximumDistance` | `8.0` | Maximum distance while using that authorization |
| `particlesEnabled` | `true` | Global Mutation particle switch |
| `soundsEnabled` | `true` | Global Mutation sound switch |
| `worldBlacklist` | empty | Namespaced world keys where all Mutations are disabled |
| `domainMembership` | built-in domain map | Skills accepted for each Mutation domain |

All cooperative modes also require the recipient's saved opt-in. `EXPLICIT` accepts any opted-in eligible recipient. `PARTY` additionally requires both players to be on the same Bukkit scoreboard and team. `FRIEND` currently accepts nobody because no friend provider is implemented. `DISABLED` accepts nobody.

Every type profile also has `enabled = true`, `pvpEnabled = true`, `particlesEnabled = true`, `soundsEnabled = true`, an empty `worldBlacklist`, and an empty `conflicts` list. Profile world keys and conflicts are normalized on load.

### Type-specific Mutation settings

| Table | Keys and defaults |
|---|---|
| `galeLung` | `maximumMomentum = 100`, `sprintMomentumPerBlock = 8`, `airborneMomentumPerBlock = 4`, `stationaryVentMillis = 1250`, `burdenKnockbackMultiplier = 1.35`, `meleeFlankDistance = 1.5`, `projectileDisplacement = 0.45` |
| `bastionSpine` | `anchorChargeMillis = 1500`, `maximumStability = 8`, `stabilityPerDamage = 0.5`, `waveRange = 5`, `waveAngleDegrees = 90`, `maximumVelocity = 0.85`, `maximumTargets = 12` |
| `verdantMolt` | `chargeTicks = 50`, `cooldownMillis = 90000`, `saturationCost = 6`, `recoveryTicks = 40`, `maximumEffects = 32` |
| `temperbound` | `rejectionMillis = 30000` |
| `paradoxScar` | `minimumDistance = 8`, `echoLifetimeMillis = 12000`, `maximumReturnDistance = 64`, `hostileCollapseTicks = 60` |
| `arsenalCortex` | `chainTimeoutMillis = 5000`, `maximumChain = 4`, `dullnessMillis = 3000` |
| `packmind` | `quarryMillis = 20000`, `participationRange = 16`, `maximumTempo = 6`, `maximumMembers = 8`, `waitingDamageFactor = 0.8` |
| `trophyCrucible` | `imprintLifetimeMillis = 1800000`, `recognitionRange = 16` |
| `umbralEcho` | `angleBucketDegrees = 45`, `techniqueMemoryMillis = 5000`, `echoDelayTicks = 8`, `exposureTicks = 60`, `maximumTargetMemories = 8` |
| `livingLattice` | `maximumRootCharge = 12`, `pathLength = 5`, `blockLifetimeMillis = 15000`, `collapseLockMillis = 4000`, `maximumBlocks = 16`, `maximumStructures = 3` |
| `masterworkBond` | `abandonCooldownMillis = 86400000` |
| `deepblood` | `maximumDepthY = 16`, `ichorPerBlock = 1`, `maximumIchor = 100`, `regenerationCost = 4`, `toolPreservationCost = 25`, `aboveGroundHalfLifeMillis = 300000` |
| `mycelialNerve` | `range = 16`, `copiedDurationFactor = 0.5`, `rootDurationFactor = 0.75`, `maximumRecipients = 8`, `reconnectLockMillis = 5000` |
| `gravebloom` | `lifetimeMillis = 20000`, `radius = 6`, `maximumBlooms = 3`, `regenerationFactor = 0.5`, `pulseTicks = 20`, `maximumCrops = 16`, `maximumAnimals = 8` |
| `resonantFormula` | `sigilLifetimeMillis = 600000`, `collapseLockMillis = 30000`, `echoFactor = 0.5`, `echoDelayTicks = 10` |

Durations ending in `Millis` are milliseconds; fields ending in `Ticks` use server ticks. Factors are multipliers, ranges and distances are blocks, angles are degrees, and count/charge fields are bounded during normalization. Player-facing behavior for each profile is in `35 - Mutations Catalog.md`.

## Reload behavior

The hotload watcher polls `adapt.toml`, its legacy JSON peer, `models.toml`, `models.json`, `mutations.toml`, skill TOML, adaptation TOML, and locale overrides. A successful change refreshes open Adapt GUIs. Invalid TOML is rejected and the prior in-memory configuration remains active.

| Change | Hot reload | Restart required |
|---|---|---|
| Skill/adaptation config and enabled flags | yes | no |
| GUI, effects, progression math, conflicts, protection overrides | yes | no |
| Language, model mappings, advancements | yes | no |
| Mutation config | yes; online players reconcile | no |
| Ability API policy, failure, watchdog, and throttle settings | yes | no |
| `protectorSupport.*` default-active membership | yes | no |
| SQL or Redis endpoint/enabled state | no | yes |
| Metrics enabled state | no | yes |
| Optional plugin installation/removal and protector registration | no | yes |
| Startup splash or update check | affects next enable | yes |

Use `/adapt configure` for the in-game editor, `/adapt default ...` to reset selected core/skill/adaptation TOML, and `/adapt migrate-configs` for canonical skill/adaptation rewrites and legacy JSON cleanup. Command scopes and permissions are in `04 - Commands & Permissions.md`.

## See also

- `04 - Commands & Permissions.md`
- `05 - Configuration Math.md`
- `06 - GUI Customization.md`
- `08 - Protection & Region Policy.md`
- `38 - Runtime Architecture.md`
- `39 - Velocity & Cross-Server.md`
