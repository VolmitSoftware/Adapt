# API - Player Data, XP & World

Adapt exposes online lookup and progression-award methods, but its serialized player/world objects are live mutable runtime state rather than a general persistence API. Use `AdaptServer` for lookup, `AdaptationLearningTransaction` for learned levels, and `XP` or `Skill` helpers for rewards; direct mutation of `PlayerData`, `PlayerSkillLine`, or persistence queues is unsupported.

## Looking up online state

After Adapt has enabled, obtain the server through `Adapt.instance.getAdaptServer()` or a checked plugin lookup as shown in `42 - API - Skills & Adaptations.md`.

| Call | Result |
|------|--------|
| `AdaptServer.getOnlineAdaptPlayer(UUID)` | Live `AdaptPlayer`, or `null` when the player is not online/loaded. |
| `AdaptServer.getPlayer(Player)` | Live wrapper for an online Bukkit player. Call on the player's owning thread. |
| `AdaptServer.getPlayerData(UUID)` | `Optional<PlayerData>` from online state or storage; this may perform persistence work and is not a tick-path query. |
| `AdaptServer.peekData(UUID)` | Already-loaded data only; no storage load. |
| `AdaptServer.getOnlineAdaptationLevel(...)` | Stored online level for a skill/adaptation id pair. |
| `AdaptServer.hasOnlineLearner(...)` / `getLearnedAdaptPlayerSnapshot(...)` | Learner-index queries; the returned list is a snapshot. |

`AdaptPlayer` provides `getPlayer()`, `getData()`, `getSkillLine(name)`, `hasAdaptation(id)`, `hasSkill(skill)`, `isBusy()`, food-charge queries, and immediate `saveNow()`. Treat it as an online, owning-thread object; do not construct it, start/unregister it, call its tick/login methods, or use its random/recency XP routing from an external plugin. Its nested `FxPosition` and `FoodCharge` records are value results used by first-party helpers.

`PlayerData` exposes progression reads such as `getLevel()`, `getMaxPower()`, `getUsedPower()`, `getAvailablePower()`, `hasPowerAvailable(...)`, `getStat(...)`, `getSkillLine(...)`, and `getMutationData()`. `PlayerSkillLine` exposes level, XP, knowledge, multiplier, progress, learned adaptation level, and recent-earn information. Both are live mutable objects: their setters, clear methods, XP/knowledge writes, adaptation writes, JSON methods, update methods, and raw collection getters bypass transaction, integrity, publication, and persistence ownership and are not supported integration calls.

`PlayerAdaptation` is the mutable serialized adaptation record; its `REGION_GRANTED_KEY`, `isRegionGranted()`, and `setRegionGranted(...)` are owned by region-grant reconciliation. `AdaptServerData`, `AdaptStatTracker`, `AdvancementHandler`, and generic `Discovery<T>` are first-party server/player state and advancement helpers, not external lifecycle contracts. `AdaptPlayerTracker` is currently an empty public marker with no behavior.

## Awarding progression

Use these calls on the player's owning thread:

| Type | Supported calls |
|------|-----------------|
| `XP` | `xp(...)` and `xpSilent(...)` award skill XP; `spatialXP(...)` creates a location/radius reward; `knowledge(...)`, `wisdom(...)`, and `boostXP(...)` apply other progression; curve helpers convert between XP, level, and progress. |
| `Skill` | `xp(...)` and `xpSilent(...)` route through the same production award path and accept an optional stable reward key. |
| `Adaptation` | `xp(...)` / `xpSilent(...)` prefix the reward key with `adaptation:<id>` before using the owning skill. |

These paths apply the production reward pipeline, including permission multipliers, global/skill boosts, region XP policy, provenance/novelty checks, and notification behavior appropriate to the selected visible or silent method. Use a stable `rewardKey` for repeated sources so novelty controls can distinguish one source from another.

`Curves` is the configured curve enum and exposes its `NewtonCurve`; `NewtonCurve.getXPForLevel` and `computeLevelForXP` perform pure conversion except that the default inverse clamps against the active Adapt level cap. `XPMultiplier` is the mutable timed multiplier record used inside player data. `SpatialXP` is an Adapt-owned pending spatial reward; create it indirectly through `XP.spatialXP` rather than constructing or offering it to `AdaptServer`.

`XpNovelty` and `XpProvenance` are production integrity helpers. `XpNovelty` computes reward-key, adjacency, and field-cycle multipliers and can clear a player's cache; `XpProvenance` records/checks placed, broken, piston-moved, replaced, and bonemealed blocks and supplies place/break/harvest multipliers. Call them only on the owning region thread. `XpNoveltyListener` and `XpProvenanceListener` are Adapt-owned Bukkit listeners and must not be registered a second time.

## World-scoped data

`WorldData.of(world)` returns Adapt's live world store. Its typed `get`, `set`, and `remove` methods attach data to blocks; `getEarningsMultiplier` and `reportEarnings` drive anti-farm bookkeeping. These operations are region-thread-bound and the `WorldData` lifecycle (`stop`, event handlers, tick, `unregister`) is Adapt-owned.

`Earnings` and `PlacementStamp` are the stored unit types used by `WorldData` and `XpProvenance`. Their nested matter serializers are implementation details; integrations should use the higher-level provenance methods instead of instantiating serializer objects.

## Public persistence and component types that are not contracts

| Type | Runtime role and restriction |
|------|------------------------------|
| `PlayerDataPersistenceQueue` | Owns asynchronous local/SQL save-delete ordering and shutdown flush. A second queue can race or resurrect data. |
| `PlayerDataPurgeGuard` | Global tombstone set preventing queued writes after deletion. Adapt owns mark/clear/reset lifecycle. |
| `AdaptComponent` | Large first-party convenience interface for item classification, server/player access, value, FX, and event helpers; some signatures use relocated/version-specific types. |
| `AdaptDebugMode` | Global operator debug-bypass state. Use `/adapt debug mode`; external mutation bypasses normal authorization. |
| `AdaptServer` | Public runtime owner as described above; constructors, lifecycle, event handlers, data reset, GUI opening, global boost, and persistence methods are not general API. |

## See also

- `02 - Concepts.md`
- `05 - Configuration Math.md`
- `38 - Runtime Architecture.md`
- `42 - API - Skills & Adaptations.md`
- `48 - API - Mutations.md`
