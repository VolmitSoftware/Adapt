# Adapt placeholders

Adapt registers a PlaceholderAPI expansion under the identifier `adapt` whenever PlaceholderAPI is installed. Every key looks like `%adapt_<path>%`, where `<path>` is one to four dot-separated segments of lowercase letters, digits and hyphens. You do not register anything and you do not compile against Adapt. Install PlaceholderAPI, install Adapt, and the keys resolve.

There is no underscore anywhere in a path. PlaceholderAPI uses `_` to terminate the expansion identifier, so `%adapt_player_level%` is not a typo Adapt can recover from, it is a request for a different expansion entirely. The separator inside a path is always `.`.

Values come from a snapshot that each player's own Adapt tick republishes roughly once a second, never from live game state. That makes resolution cheap enough to put in a scoreboard that updates every tick, and it means a value can be up to a second behind. It also means the answer is safe to ask for from any thread.

Three things can come back: the literal text (the path is not a key), `---` (the path is a key but there is nothing to show), or a value. Adapt never invents a plausible-looking zero for a path it does not recognise.

## Reading a placeholder

Resolve them the ordinary PlaceholderAPI way:

```java
String line = PlaceholderAPI.setPlaceholders(player, "%adapt_player.level% (%adapt_skill.pickaxe.level%)");
```

Paths are lowercased before resolution, so `%adapt_Player.Level%` works. Nothing else about a path is forgiving. Attribute matching is exact and never by prefix, and a numeric argument must be one to four unsigned digits. `can-claim-nextra`, `can-claim.next`, `can-claim.`, `cost-to.x`, `cost-to.-1`, `has-level.five` and `has-levelx.5` all come back as the literal.

### Telling the three outcomes apart

The literal text means you got the key wrong. Adapt returned `null` and PlaceholderAPI left your text alone, so `%adapt_skil.pickaxe.level%` renders as itself. So does a real key with a bad attribute (`%adapt_skill.pickaxe.levle%`) and a real attribute on an unknown id (`%adapt_skill.mynng.level%`). Fix the key.

`---` means the key is real but there is no data behind it right now: no snapshot for that player, no catalogue published yet, or the mutation runtime has nothing for them. Render a dash, or hide the row.

`0`, `0.00` and `false` are genuine values. A skill the player has never trained answers with real level-zero numbers rather than `---`, because Adapt substitutes a level-zero line instead of pretending it has no data.

### Formatting

Every value is plain text. No colour codes, no unit suffixes, no thousands separators, and never a literal `%` character, because display names have `%` and legacy section-sign colour codes stripped before they are stored. A non-finite number renders `---` rather than `Infinity` or `NaN`, and a display name that sanitises down to nothing renders `---` rather than an empty string. The exact shapes are in the [Reference](#value-formats).

## Where the values come from, and when

Per-player values are read from an immutable snapshot.

The snapshot is rebuilt and published by the player's own Adapt tick, roughly once per second, on the thread that owns their data. Publication is staggered per player UUID, so a full server does not rebuild every snapshot on the same tick. Most values are pre-rendered into strings at publish time; the rest (the `can-claim`, `cost-to`, `power-to` and `has-level` arguments, and the per-mutation metrics) are computed at resolve time from integers already in the snapshot. Either way, resolving a placeholder touches no game state. It is a map lookup, a field read, and at worst a subtraction.

Nothing is ever loaded for an offline player. A player who has not been online since the server started has no snapshot and every per-player key answers `---`.

When a player quits, Adapt keeps their snapshot and stamps it with an expiry sixty seconds out. For that minute every per-player key keeps serving the last published values and `%adapt_available%` stays `true`. After it, the entry is evicted on the next read, every per-player key answers `---`, and `%adapt_available%` answers `false`. That exists so quit messages, leaderboards and logout hooks that render a moment after the player is gone still have something to show.

Design around the staleness: a value can be up to one second old while the player is online and up to sixty-one seconds old immediately after they leave. Do not use placeholders as the source of truth for anything transactional.

The catalogue snapshot, which holds skills, adaptations, mutations and their costs, is separate and server-wide. It is republished whenever the skill registry changes and it is available to any reader, including one with no player snapshot at all. That is why `catalog.*` answers for offline players and for the console.

## Threading

Any thread. This is the one surface in Adapt where that is the correct answer, and it is worth saying why rather than asserting it. The per-player store is a `ConcurrentHashMap` of immutable records holding immutable maps, the catalogue is a single `volatile` reference to an immutable object, and resolution reads those records. The only Bukkit call on the path is `OfflinePlayer.getUniqueId()`, which Adapt uses to key the store. No entity is read, no world is touched, no lock is taken. PlaceholderAPI may resolve on the main thread, a region thread, or an async chat or scoreboard task, and all three are safe.

Publication is the only write, and it happens on the thread that owns the player's data. A reader that races a publication sees either the whole previous snapshot or the whole new one, never a mixture.

## What you can ask for

Server and catalogue keys need no player and answer for the console. Player keys need a published snapshot. Skill keys take a skill id, adaptation keys take a full adaptation id such as `rift-blink` or `pickaxe-veinminer`, and mutation keys take a mutation id such as `gale-lung`. Every key, id and metric is listed in the [Reference](#server-and-catalogue-keys).

Two behaviours catch people out.

`can-use` is not a permission check. It reads three things (learned level, adaptation enabled, skill enabled) and nothing else. It ignores `adapt.use.*`, world blacklists, game mode, protection plugins, usage conflicts and any registered [`AbilityUsePolicy`](<43 - API - Ability Use Policy.md>). A player can show `can-use = true` here and still have the adaptation refuse to fire. Read it as "owned and switched on", not "will work if they try".

`can-claim.<n>` follows the same rules the GUI does. A target equal to the current level is always `true`. A target above it is `true` only when the player has enough ability power and enough knowledge in the owning skill for the whole jump. A target below it is a refund, which is `false` for a permanent adaptation and otherwise `true` whenever the current level is above zero; refunds do not need spare power. The target is clamped to `0..max-level`, so `can-claim.9999` answers about the maximum level.

## Discovering keys at runtime

Adapt publishes its key list to PlaceholderAPI, so anything that reads an expansion's advertised placeholders sees it. The list contains every exact key on this page plus three group markers, `skill.*`, `adaptation.*` and `mutation.*`, because the per-skill, per-adaptation and per-mutation paths are resolved dynamically from the catalogue rather than enumerated one by one.

To check a specific path on a running server, parse it against a real player rather than reading the list. The three outcomes tell an invalid key (literal text) from a missing snapshot (`---`) from a value.

There is nothing to configure. The expansion has no keys in `adapt.toml`, no toggle, and no per-key switches. `%adapt_player.max-level%` reads the `experienceMaxLevel` core setting, and the skill and adaptation `enabled` keys read their own components' config, but the placeholder layer itself is not configurable.

---

## Reference

### Expansion identity

| | |
|---|---|
| Identifier | `adapt` |
| Author | `Volmit Software` |
| Version | `1.0.0` |
| Required plugin | `Adapt` |
| Persists across PAPI reloads | yes |
| Maximum path segments | 4 |
| Numeric argument length | 1 to 4 digits, unsigned |
| Post-quit grace | 60000 ms |

### Value formats

| Shape | Format | Example |
|-------|--------|---------|
| Counts | exact integer, no separators | `1000` |
| Fractions, rates, XP | exactly two decimals | `100.00`, `1.25` |
| Percentages | two decimals, no `%` character | `50.00` |
| Booleans | `true` or `false`, lowercase | `true` |
| Enum states | the constant name, lowercased | `expressed` |
| Nothing available | `---` | `---` |

### Server and catalogue keys

| Key | Value |
|-----|-------|
| `%adapt_available%` | `true` while the reading player has a published snapshot, `false` otherwise. Never `---` |
| `%adapt_catalog.available%` | `true` once the skill catalogue has been published |
| `%adapt_catalog.skills%` | how many skills are registered |
| `%adapt_catalog.adaptations%` | how many adaptations are registered |
| `%adapt_catalog.mutations%` | how many mutation types exist |

The three counting keys answer `---` before the catalogue is published. None of the five needs a player.

### Player keys

All answer `---` without a snapshot.

| Key | Value |
|-----|-------|
| `%adapt_player.level%` | master level, truncated to a whole number |
| `%adapt_player.max-level%` | the configured maximum master level (`experienceMaxLevel`, default `1000`) |
| `%adapt_player.master-xp%` | total master experience, two decimals |
| `%adapt_player.multiplier%` | the player's global experience multiplier, two decimals |
| `%adapt_player.wisdom%` | wisdom earned |
| `%adapt_player.power%` | `power-max` minus `power-used`. Not clamped, so a shrunk power budget can render a negative number |
| `%adapt_player.power-max%` | `floor(exact master level * powerPerLevel)`, `powerPerLevel` default `0.65` |
| `%adapt_player.power-used%` | the sum of every learned adaptation's level |
| `%adapt_player.known-skills%` | skills at level 1 or higher |
| `%adapt_player.learned-adaptations%` | adaptations at level 1 or higher |

### Skill keys

`%adapt_skill.<skill>.<metric>%`. `<skill>` is one of:

```
agility     architect   axes        blocking    brewing     chronos     crafting
discovery   enchanting  excavation  herbalism   hunter      kinetics    nether
pickaxe     ranged      rift        seaborne    stealth     swords      taming
tragoul     unarmed
```

A skill the player has never trained answers with genuine level-zero values, not `---`.

| Metric | Value |
|--------|-------|
| `level` | current level in this skill, floored |
| `xp` | experience in this skill, two decimals |
| `knowledge` | unspent knowledge in this skill |
| `multiplier` | this skill's experience multiplier, two decimals |
| `progress` | progress through the current level, `0.00` to `1.00` |
| `progress-percent` | the same progress as `0.00` to `100.00`, with no `%` character |
| `xp-to-next` | experience still needed to reach the next level, two decimals |
| `current-level-xp` | the experience threshold of the current level, two decimals |
| `next-level-xp` | the experience threshold of the next level, two decimals |
| `learned-adaptations` | adaptations learned in this skill |
| `known` | `true` when the level is 1 or higher |
| `name` | the skill's localized name |
| `enabled` | `true` when the skill is enabled in the config |
| `adaptations` | how many adaptations this skill offers |
| `has-level.<n>` | `true` when the level is at least `<n>`. `<n>` is 1 to 4 digits |

`name`, `enabled` and `adaptations` come from the catalogue rather than the player, but they still need a player snapshot to resolve, because the resolver checks for one before it answers anything under `skill.`.

### Adaptation keys

`%adapt_adaptation.<adaptation>.<metric>%`. `<adaptation>` is a full adaptation id such as `rift-blink`, `pickaxe-veinminer` or `tragoul-skeletal-servant`.

| Metric | Value |
|--------|-------|
| `level` | the level the player has purchased, clamped to `0..max-level` |
| `max-level` | the highest level this adaptation offers |
| `name` | the adaptation's display name, colour codes stripped |
| `skill` | the id of the owning skill |
| `enabled` | `true` when the adaptation is enabled in the config |
| `learned` | `true` when the level is 1 or higher |
| `can-use` | `true` when it is learned and it and its skill are both enabled |
| `cost-next` | knowledge cost of the next level. `0` at maximum |
| `power-next` | ability power cost of the next level. `0` at maximum, otherwise `1` |
| `can-claim-next` | `true` when the player can afford the next level right now |
| `can-claim.<n>` | `true` when the player can move to level `<n>` right now |
| `cost-to.<n>` | knowledge cost to reach level `<n>` from the current level. `0` when `<n>` is not above it |
| `power-to.<n>` | ability power cost to reach level `<n>`, which is `<n>` minus the current level. `0` when `<n>` is not above it |

### Mutation keys

Server-wide mutation state for the reading player.

| Key | Value |
|-----|-------|
| `%adapt_mutation.available%` | `true` once a mutation snapshot has been published for this player, `false` otherwise. Never `---` |
| `%adapt_mutation.enabled%` | `true` when the mutation feature is enabled |
| `%adapt_mutation.perfect%` | `true` when the player has perfect adaptation |
| `%adapt_mutation.expressed%` | how many mutations are currently expressed |
| `%adapt_mutation.slot-1%`, `%adapt_mutation.slot-2%` | the display name in that slot, `---` when empty |
| `%adapt_mutation.slot-1-id%`, `%adapt_mutation.slot-2-id%` | the id in that slot, `---` when empty |
| `%adapt_mutation.slot-1-unlocked%`, `%adapt_mutation.slot-2-unlocked%` | `true` when that slot is unlocked |
| `%adapt_mutation.combat-lock%` | seconds left on the combat lock before slots can change, two decimals |
| `%adapt_mutation.can-swap%` | `true` when the combat lock has expired |

Every key in that table except `available` answers `---` when there is no player snapshot or when the mutation runtime has not published for this player. Check `available` first.

Per mutation, `%adapt_mutation.<mutation>.<metric>%`:

```
gale-lung        bastion-spine    verdant-molt      temperbound      paradox-scar
arsenal-cortex   packmind         trophy-crucible   umbral-echo      living-lattice
masterwork-bond  deepblood        mycelial-nerve    gravebloom       resonant-formula
```

| Metric | Value |
|--------|-------|
| `id` | the mutation id, echoed back |
| `name` | the mutation's display name |
| `state` | `locked`, `available`, `expressed`, `dormant`, `disabled`, `restricted` or `conflict` |
| `expressed` | `true` when this mutation is currently expressed |
| `qualified` | `true` when the player qualifies for this mutation |
| `slot` | `1`, `2`, or `0` when it is not in a slot |

`state` is the `MutationState` enum constant lowercased, and the enum may gain constants, so treat an unrecognised value as unknown rather than switching exhaustively on the seven above. These six answer `---` when the mutation runtime is unavailable for the player, and the literal for any other attribute name.

### Failure policy

| Situation | What renders |
|-----------|--------------|
| PlaceholderAPI is not installed | Nothing registers. `%adapt_...%` is inert text everywhere |
| Adapt is disabled or shutting down | Both snapshot stores are cleared; `%adapt_available%` is `false` and every value key answers `---` |
| The reading player is offline, within 60 s of quitting | The last published snapshot, unchanged |
| The reading player is offline, past 60 s | `---` for every per-player key, `false` for `%adapt_available%` |
| No player at all, a console request or a null player | `---` for per-player keys, `false` for `%adapt_available%`. `catalog.*` still answers |
| A resolver throws | `---`, and one `WARNING` naming the exact path is logged. Repeats of that path are silent, and Adapt stops logging new paths after 64 distinct ones |
| Building a snapshot throws | The old snapshot is kept and served; Adapt logs one warning for the whole server lifetime |
| An unknown path | The literal, unchanged |

## See also

- [04 - Commands & Permissions.md](<04 - Commands & Permissions.md>)
- [35 - Mutations Catalog.md](<35 - Mutations Catalog.md>)
- [41 - API - Getting Started.md](<41 - API - Getting Started.md>)
- [43 - API - Ability Use Policy.md](<43 - API - Ability Use Policy.md>)
