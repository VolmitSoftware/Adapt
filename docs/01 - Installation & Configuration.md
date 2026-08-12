# Installation & Configuration

Adapt 2.0.0-26.2 is a single Bukkit jar that runs on Paper, Purpur, and Folia servers built against the Minecraft 26.1 API line, on Java 25. Drop the jar into `plugins/`, start the server once so it writes its defaults, then edit the TOML files under `plugins/Adapt/adapt/`.

Most of what you will change is hot-reloadable. A watcher polls the config files every half second and applies valid edits to the running server, refreshing any Adapt menus that happen to be open. Broken TOML is rejected outright and the settings already in memory keep running, so a typo never takes the plugin down. The things that are not hot-reloadable are the ones Adapt only wires up while it enables: SQL, Redis, bStats metrics, the startup splash, the update check, and whichever optional plugins were present at boot.

Every plugin Adapt talks to is optional. Without PlaceholderAPI you lose the `%adapt_...%` placeholders, without Vault learning stays knowledge-only, and without a protection plugin Adapt never asks one for permission. Missing integrations are silent, not fatal. Settings live in three places: `adapt.toml` for global behavior, one file per skill and per adaptation under `adapt/skills/` and `adapt/adaptations/`, and `adapt/mutations.toml` for the experimental Mutation layer, which is off until you turn it on.

## Installing

1. Run a Paper, Purpur, or Folia server on the Minecraft 26.1 API line, on Java 25. Adapt declares `folia-supported: true`, so Folia needs no separate build.
2. Copy the shaded Adapt jar (`Adapt-<version>.jar`) into the backend's `plugins/` folder. On a Velocity network it goes on each backend, never on the proxy.
3. Start the server, watch for the Adapt splash, and confirm it enables without an API-version or dependency complaint.
4. Stop the server again before configuring SQL, Redis, or metrics. Those are read once, at enable.
5. Grant `adapt.main` to anyone who should reach `/adapt` at all, then add the specific command nodes. The gameplay `adapt.use.*` nodes default to true but do not get anyone past that root gate.

## Sharing player data across servers

By default a player's progression lives in `data/players/<uuid>.json` and that file is the truth. SQL moves the truth into an `ADAPT_DATA` table, which is what you want when several backends share one player base. Redis sits in front of SQL as a one-minute handoff cache so a player switching servers does not wait on a database round trip; it is not a second storage backend.

1. Create the database schema yourself and give the account SELECT, INSERT, UPDATE, DELETE, and CREATE TABLE on it. Adapt creates its table inside the schema but never the schema.
2. Fill in the `sql.*` host, port, database, username, and password keys, then set `sql.enabled = true`.
3. For Redis, set `redis.host` and `redis.port`, add `redis.username` and `redis.password` only if your Redis uses ACLs, then set `redis.enabled = true`. Redis stays inert unless `sql.enabled` is also true.
4. Restart. Both clients are only built during enable.
5. Confirm the table appears and that a test player's progression survives a relog and a server switch.

Adapt puts the SQL credentials straight into the JDBC URL and has no TLS switch of its own, so configure transport security on the database endpoint and in the driver environment. A shutdown write that cannot reach SQL is parked beside the player file as `<uuid>.json.pending-sql` rather than dropped. See `38 - Runtime Architecture.md` and `39 - Velocity & Cross-Server.md`.

## Charging money for learning

1. Install Vault and an economy plugin that registers with it, then set `learningEconomy.enabled = true`.
2. Set `learningEconomy.moneyPerKnowledge` to the currency charged per knowledge point. An adaptation's bill is its knowledge cost times this number.
3. Set `learningEconomy.refundPercent` to how much comes back on a normal unlearn, or `0` for no money refunds.

Without Vault or an economy provider, learning falls back to knowledge only. A failed withdrawal rejects the purchase. A failed refund is written onto the player's skill line as a pending receipt and settled by the next learning transaction on that line. `hardcoreNoRefunds = true` suppresses knowledge and money refunds entirely.

## Turning on Mutations

1. Set `enabled = true` in `mutations.toml`.
2. Set the gates. `slotOneUnlockLevel` and `slotTwoUnlockLevel` are master levels; `minimumAdaptationLevel` is the learned adaptation level needed in each of a mutation's two skill domains.
3. Decide whether players may re-pick. `switchingEnabled` allows player-driven changes, `permanentSelection` locks the first choice until an admin clears it, and `switchCooldownMillis` and `combatLockMillis` throttle the rest.
4. Set `cooperativeConsentMode` if you use group effects. Every mode also needs the recipient's own saved opt-in.
5. Save. Mutation config hot-reloads and online players are reconciled; `/adapt mutations reload` does the same on demand.

Player-facing behavior for each type is in `35 - Mutations Catalog.md`.

## Turning Adapt off in a world

Add the world's namespaced Bukkit key to `blacklistedWorlds`. These are keys, not folder names: `minecraft:overworld`, `minecraft:the_nether`, `minecraft:the_end`, or whatever key a custom world provider supplies. The two entries in the generated file are placeholders that match nothing. The change applies on the next poll. Mutations have their own separate `worldBlacklist`, both globally and per type.

## Config maintenance

`/adapt configure` opens the config editor in a menu instead of a text editor, and needs `adapt.configurator` or op.

`/adapt default skill <skill>` and `/adapt default adaptation <skill:adaptation>` delete that file, regenerate it from defaults, and reconcile mutations. `/adapt default all` archives `adapt.toml` and every skill and adaptation TOML into `config-archive/<timestamp>/` first, then deletes, regenerates, and reloads them. It leaves `mutations.toml`, `models.toml`, language overrides, SQL and Redis data, and player progression alone. All three need `adapt.configurator`.

Older installs used `.json` config files, and Adapt still recognizes the TOML peer for `adapt/adapt.json`, `adapt/models.json`, `adapt/mutations.json`, `adapt/skills/<id>.json`, and `adapt/adaptations/<id>.json`. When a startup migration is actually needed, Adapt first zips every legacy JSON file under `adapt/` into `adapt/migrations/backups/<timestamp>-pre-toml-migration.zip` and drops a `.legacy-json-backed-up` marker so it never repeats. `/adapt migrate-configs` then rewrites skill and adaptation TOML in canonical form and deletes each legacy JSON file below `adapt/` that already has a TOML peer. While a legacy JSON file still shadows an existing TOML file, the hotload watcher ignores the JSON. The old misspelled key `value.valueMutlipliers` is folded into `value.valueMultipliers` when the core config loads, with correctly-spelled entries winning a collision.

## Reference

### Identity

| Property | Value |
|---|---|
| Version | `2.0.0-26.2` |
| Main class | `art.arcane.adapt.Adapt` |
| `api-version` | `26.1` |
| Java toolchain | 25 |
| Bukkit command root | `/adapt` |
| Root permission | `adapt.main` (default op) |
| `folia-supported` | `true` |

### Optional plugins (all soft dependencies)

| Plugin | What it adds |
|---|---|
| PlaceholderAPI | `%adapt_...%` placeholders |
| WorldGuard | Region flags and region-based protection |
| Factions, ChestProtect, Residence, GriefDefender, GriefPrevention, LockettePro | Claim and container protection checks |
| Vault | Currency charge and refund during learning |
| HiddenOre | Hidden-vein mining XP and drop adaptations |
| Iris | Iris tree-feller integration |
| AdvancedChests | Rift Access support for AdvancedChests containers |
| MagicCosmetics | Excludes equipped cosmetic hat and bag slots from Adapt's armor-value math |

Activation and failure behavior: `08 - Protection & Region Policy.md` and `09 - Integrations.md`.

### Data folder layout

```text
plugins/Adapt/
  adapt/
    adapt.toml
    models.toml
    mutations.toml
    skills/<skill-id>.toml
    adaptations/<adaptation-id>.toml
    migrations/.legacy-json-backed-up
    migrations/backups/<timestamp>-pre-toml-migration.zip
  config-archive/<timestamp>/
  languages/en_US.toml
  languages/<active-locale>.toml
  languages/overrides/<locale>.toml
  data/players/<uuid>.json
  data/players/<uuid>.json.pending-sql
  data/server-data.json
  data/value-cache.json
  data/advancements.db
  data/mantle/<namespace>/<world-key>/
```

`data/advancements.db` is the SQLite advancement store used while `sql.enabled` is false. `config-archive` timestamps use `yyyy-MM-dd_HHmmss`; migration backup zips use `yyyyMMdd-HHmmss`.

### `adapt/adapt.toml`, general and progression

| Key | Default | What it does |
|---|---:|---|
| `debug` | `false` | Prints Adapt's developer debug lines to the console |
| `verbose` | `false` | Prints per-action diagnostic logging; `/adapt debug verbose` flips the in-memory value without writing the file |
| `autoUpdateCheck` | `true` | Runs the update check during enable |
| `splashScreen` | `true` | Prints the startup splash |
| `metrics` | `true` | Starts bStats and integration metrics during enable |
| `language` | `en_US` | Active locale, and the filename used for overrides |
| `xpCurve` | `ADAPT_BALANCED` | Curve family shared by every skill line and by master level; see `05 - Configuration Math.md` |
| `experienceMaxLevel` | `1000` | Skill level cap, and the ceiling the level-search cursor clamps to |
| `playerXpPerSkillLevelUpBase` | `489` | Flat master XP granted per skill level crossed |
| `playerXpPerSkillLevelUpLevelMultiplier` | `44` | Extra master XP per level already reached |
| `powerPerLevel` | `0.65` | Power budget granted per master level, truncated to a whole number |
| `xpInCreative` | `false` | Allows skill XP while in creative or spectator |
| `allowAdaptationsInCreative` | `false` | Allows adaptation effects while in creative |
| `blacklistedWorlds` | two placeholder keys | Namespaced world keys where Adapt gameplay is off |
| `hardcoreResetOnPlayerDeath` | `false` | Wipes progression when a player dies |
| `hardcoreNoRefunds` | `false` | Suppresses knowledge and Vault refunds on unlearn |
| `loginBonus` | `true` | Enables the login bonus |
| `welcomeMessage` | `true` | Sends the Adapt welcome message |
| `advancements` | `true` | Registers and syncs Adapt advancements |
| `preventHunterSkillsWhenHungerApplied` | `true` | Blocks Hunter passives while the player has the Hunger effect |

Default `blacklistedWorlds` entries are `minecraft:some_world_adapt_should_not_run_in` and `example:another_world`, neither of which matches a real world.

### `adapt/adapt.toml`, activator, GUI, and presentation

| Key | Default | What it does |
|---|---:|---|
| `adaptActivatorBlock` | `BOOKSHELF` | Bukkit material a player clicks to open the Adapt menu |
| `adaptActivatorBlockName` | `a Bookshelf` | Text used when a message names that block |
| `adaptActivatorAllowVerticalFaces` | `false` | Also accepts clicks on the top and bottom faces |
| `useEnchantmentTableParticleForActiveEffects` | `true` | Uses the enchantment-table particle style for active effects and XP bursts |
| `escClosesAllGuis` | `false` | Escape closes the whole menu stack instead of returning to the parent menu |
| `guiBackButton` | `true` | Shows Back buttons in menus that have a parent |
| `customModels` | `true` | Applies the model mappings in `adapt/models.toml` |
| `automaticGradients` | `false` | Applies the automatic rendered-text gradient |
| `learnUnlearnButtonDelayTicks` | `14` | Debounce, in ticks, between learn and unlearn clicks |
| `maxRecipeListPrecaution` | `25` | Depth bound on recursive recipe-value traversal |
| `actionbarNotifyXp` | `true` | Shows the XP action-bar figure |
| `actionbarNotifyLevel` | `true` | Shows level-up notifications |
| `unlearnAllButton` | `false` | Shows the bulk-unlearn control |
| `guiShowAllSkills` | `false` | Lists every enabled skill even when the player has no progress in it; display only, use permissions still apply |

The `[gui]` subsection, icon precedence, and menu ordering are in `06 - GUI Customization.md`.

### `[effects]`

```toml
[effects]
particlesEnabled = true
soundsEnabled = true
[effects.adaptationParticleOverrides]
"adaptation-name" = true
[effects.skillParticleOverrides]
"skill-name" = true
```

`particlesEnabled` and `soundsEnabled` are the global switches. The two override maps are keyed by registry ID and act as extra gates: `false` turns that component's particles off, `true` leaves the global decision alone. The player's own `/adapt effects` preference is a further gate. The `adaptation-name` and `skill-name` rows are placeholders.

### `[abilityApi]`

| Key | Default | What it does |
|---|---:|---|
| `abilityApi.enabled` | `true` | Enables external ability policy and cost providers through the Bukkit provider gateways |
| `abilityApi.usePolicyFailureMode` | `deny` | What happens when a use-policy provider throws: `allow` or `deny` |
| `abilityApi.costProviderFailureMode` | `allow` | What happens when a cost provider throws: `allow` or `deny` |
| `abilityApi.providerFaultLimit` | `5` | Consecutive faults before a provider is quarantined; `0` disables the watchdog |
| `abilityApi.slowProviderMillis` | `2` | Milliseconds a provider may take before a slow warning is logged; `0` disables the warning |
| `abilityApi.denyMessageThrottleMillis` | `2000` | Minimum milliseconds between repeated denial messages to the same player |

An unrecognized failure-mode string falls back to `deny` for use policies and `allow` for cost providers. See `43 - API - Ability Use Policy.md` and `44 - API - Ability Cost.md`.

### `[learningEconomy]`

| Key | Default | What it does |
|---|---:|---|
| `learningEconomy.enabled` | `false` | Charges Vault currency on learn when a provider is available |
| `learningEconomy.moneyPerKnowledge` | `1.0` | Currency charged per knowledge point spent |
| `learningEconomy.refundPercent` | `100.0` | Percentage of the recorded charge returned on a normal unlearn |

### `[sql]`

| Key | Default | What it does |
|---|---:|---|
| `sql.enabled` | `false` | Makes the `ADAPT_DATA` table authoritative instead of local JSON |
| `sql.host` | `localhost` | MySQL-compatible server hostname |
| `sql.port` | `3306` | Server port |
| `sql.database` | `adapt` | Existing schema the table lives in; Adapt creates the table, never the schema |
| `sql.username` | `user` | SQL account |
| `sql.password` | `password` | SQL account password, sent in plain text unless the server enforces TLS |
| `sql.poolSize` | `10` | Connection pool size requested by the advancement backend only |
| `sql.connectionTimeout` | `5000` | Milliseconds allowed for the JDBC connect handshake; raised to 1000 if set lower, and the socket timeout is twice the result |
| `sql.secondsCheckverify` | `30` | Seconds passed to `Connection.isValid`; the startup probe clamps it to 1-10, the reconnect probe uses the raw value |

### `[redis]`

| Key | Default | What it does |
|---|---:|---|
| `redis.enabled` | `false` | Enables Redis pub/sub handoff; ignored unless `sql.enabled` is also true |
| `redis.host` | `127.0.0.1` | Redis hostname |
| `redis.port` | `6379` | Redis port |
| `redis.username` | empty | ACL username; credentials are only attached when username or password is non-empty |
| `redis.password` | empty | Redis password |

Cached entries expire one minute after write. Channel: `Adapt:data`.

### Conflicts and protection overrides

```toml
[adaptationUsageConflicts]
"rift-blink" = ["agility-air-dash"]

[protectionOverrides.rift-blink]
WorldGuard = true
GriefPrevention = false
```

Both blocks are examples. `adaptationUsageConflicts` ships empty; `protectionOverrides` ships with one placeholder row, `"adaptation-name"` mapped to `WorldGuard = true`.

Conflict pairs are symmetric at runtime: listing `agility-air-dash` under `rift-blink` means holding either one blocks use of the other, not just the direction the file reads. `protectionOverrides` starts from the currently enabled default protector set, then adds or removes protectors by exact `Protector.getName()` value; a `true` naming an unknown protector logs an error and is skipped. Full protector names and defaults: `08 - Protection & Region Policy.md`.

### Other nested sections

| Section | Documented in |
|---|---|
| `value` | `37 - Recipes, Brewing & Value.md` |
| `gui` | `06 - GUI Customization.md` |
| `farmPrevention` | `05 - Configuration Math.md` |
| `adaptationXp` | `05 - Configuration Math.md` |
| `xpIntegrity` | `05 - Configuration Math.md` |
| `permissionXpMultipliers` | `05 - Configuration Math.md` |
| `protectorSupport` | `08 - Protection & Region Policy.md` |

### `adapt/mutations.toml`, global keys

| Key | Default | What it does |
|---|---:|---|
| `enabled` | `false` | Master switch for the whole Mutation feature |
| `slotOneUnlockLevel` | `25` | Master level needed for slot 1 |
| `slotTwoUnlockLevel` | `50` | Master level needed for slot 2; normalized up to at least slot 1 |
| `perfectAdaptationLevel` | `200` | Master level at which drawbacks soften |
| `perfectAdaptationEnabled` | `true` | Enables that level-based softening |
| `minimumAdaptationLevel` | `1` | Learned adaptation level required in each of the mutation's two domains |
| `switchCooldownMillis` | `600000` | Wait after any normal slot change |
| `combatLockMillis` | `10000` | How long taking damage blocks a normal slot change |
| `switchingEnabled` | `true` | Allows player-driven switching |
| `permanentSelection` | `false` | Makes the first choice admin-clearable only |
| `pvpEnabled` | `true` | Global switch for Mutation effects in PvP |
| `cooperativeEffectsEnabled` | `true` | Global switch for cooperative effects |
| `cooperativeConsentMode` | `EXPLICIT` | Which recipients count as consenting |
| `bookshelfTokenMillis` | `60000` | How long one bookshelf interaction authorizes changes |
| `bookshelfMaximumDistance` | `8.0` | How far the player may stray from that bookshelf |
| `particlesEnabled` | `true` | Global Mutation particle switch |
| `soundsEnabled` | `true` | Global Mutation sound switch |
| `worldBlacklist` | empty | Namespaced world keys where all Mutations are off |
| `domainMembership` | built-in map | Which skills count toward each Mutation domain |

Every consent mode also requires the recipient's saved opt-in. `EXPLICIT` accepts any opted-in eligible recipient, `PARTY` additionally requires both players to share a Bukkit scoreboard and be on the same team, and `FRIEND` and `DISABLED` both accept nobody (no friend provider is implemented). Every type profile also carries `enabled = true`, `pvpEnabled = true`, `particlesEnabled = true`, `soundsEnabled = true`, an empty `worldBlacklist`, and an empty `conflicts`; world keys and conflict lists are normalized on load.

### `adapt/mutations.toml`, per-type tables

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

Keys ending in `Millis` are milliseconds and keys ending in `Ticks` are server ticks. Factors are multipliers, ranges and distances are blocks, angles are degrees, and every count or charge value is clamped into a safe band during load.

### Reload matrix

The watcher polls every 500 ms over `adapt.toml` and its legacy JSON peer, `models.toml` and its legacy peer, `mutations.toml`, everything directly inside `adapt/skills/` and `adapt/adaptations/`, and the locale override folder.

| Change | Hot reload | Restart required |
|---|---|---|
| Skill and adaptation config, including enabled flags | yes | no |
| GUI, effects, progression math, conflicts, protection overrides | yes | no |
| Language, model mappings, advancements | yes | no |
| Mutation config | yes, and online players are reconciled | no |
| Ability API policy, failure, watchdog, and throttle settings | yes | no |
| `protectorSupport.*` default-active membership | yes | no |
| SQL or Redis endpoint, or their enabled state | no | yes |
| Metrics enabled state | no | yes |
| Installing or removing an optional plugin, and protector registration | no | yes |
| Startup splash or update check | takes effect next enable | yes |

## See also

- `04 - Commands & Permissions.md`
- `05 - Configuration Math.md`
- `06 - GUI Customization.md`
- `08 - Protection & Region Policy.md`
- `38 - Runtime Architecture.md`
- `39 - Velocity & Cross-Server.md`
