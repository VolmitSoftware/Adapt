# API - Player Data, XP & World

Adapt lets you look up an online player's progression and award them XP, knowledge and wisdom. It does not offer a general persistence API. The objects behind those lookups, `PlayerData` and `PlayerSkillLine`, are the live mutable runtime state Adapt itself ticks, saves and publishes, so reading them is fine and writing to them is not.

The three things you will actually use are `AdaptServer` for lookup, the `XP` / `Skill` / `Adaptation` helpers for rewards, and `WorldData` for block-scoped storage. Everything else on this page is documented so that public visibility is not mistaken for a promise.

Almost all of it is thread-bound. Adapt runs a player's data on the thread that owns them, the main thread on Paper and the owning region thread on Folia, and the reward pipeline touches inventories, effects and the HUD. Call these on the player's owning thread.

## Looking up online state

Get the server through `Adapt.instance.getAdaptServer()`, or through the checked plugin lookup shown in [42 - API - Skills & Adaptations.md](<42 - API - Skills & Adaptations.md>), once Adapt has enabled.

Two lookups are easy to mix up. `getPlayerData(UUID)` reads only the in-memory online map and returns an empty `Optional` for anyone who is not loaded; it never touches storage. `peekData(UUID)` is the one that can do real work: it checks the purge tombstone, the online map, the prefetch cache, then SQL if SQL is enabled, then the player's local json file, and it returns a fresh empty `PlayerData` rather than `null` when it finds nothing. Keep `peekData` off tick paths.

`AdaptPlayer` is the online wrapper. Read `getPlayer()`, `getData()`, `getSkillLine(name)`, `hasAdaptation(id)`, `hasSkill(skill)`, `isBusy()`, the food-charge queries, and `saveNow()` when you need an immediate flush. Do not construct one, do not call its runtime, tick, login or unregister methods, and do not use its random or recency XP routing from outside Adapt. Its nested `FxPosition` and `FoodCharge` records are plain value results.

`PlayerData` and `PlayerSkillLine` are safe to read and unsafe to write. Their setters, clear methods, XP and knowledge writes, adaptation writes, JSON methods, update methods and raw collection getters all bypass transaction ordering, integrity checks, snapshot publication and persistence ownership. Use `AdaptationLearningTransaction` to change learned levels and the reward helpers below to change XP.

## Awarding progression

There are three entry points and they are not equivalent.

`Skill.xp(player, ...)` and `Skill.xpS(player, location, ...)` run the full production path: skill-enabled and runtime-player checks, the novelty multiplier for the reward key and location, the region XP policy from whatever `RegionPolicySource` is installed, a telemetry record, and then the handoff to `XP`. The visible form also fires the XP particle burst when the payout is large enough and the config allows it.

`Skill.xpSilent(player, ...)` skips novelty and region policy. It still records telemetry and still runs the multiplier and monotony logic inside the skill line. Use it when the reward is not tied to a place, for example a periodic tick payout.

`XP.xp(...)` and `XP.xpSilent(...)` are the raw calls both of the above end in. They apply the player's multiplier (permission multipliers plus global and per-skill boosts), the monotony multiplier, and pooled payout batching if it is enabled, but no novelty, no region policy and no telemetry. Use them only when you deliberately want to bypass the location-aware integrity checks.

`Adaptation.xp(...)` and `Adaptation.xpSilent(...)` forward to the owning skill after rewriting the reward key to `adaptation:<adaptation-id>:<your key>`, falling back to `adaptation:<adaptation-id>:use` when you pass none. Pass a stable `rewardKey` for a repeated source: novelty scoring uses it to tell one source of XP from another, so a shared or missing key makes two unrelated grinds look like the same one.

`XP` also carries `knowledge(...)`, `wisdom(...)`, `boostXP(...)`, `spatialXP(...)` for a delayed reward claimable inside a radius, and pure curve helpers converting between XP, level and progress. `XpNovelty` and `XpProvenance` are the anti-farm layer behind those multipliers and can be called directly for the same numbers. `XpNoveltyListener` and `XpProvenanceListener` are Adapt-owned Bukkit listeners; registering either a second time double-counts every event.

## World-scoped data

`WorldData.of(world)` returns Adapt's live per-world store. It attaches typed values to individual blocks and drives the anti-farm earnings bookkeeping on top of that. `Earnings` and `PlacementStamp` are the stored unit types behind it and behind `XpProvenance`; their nested matter serializers are implementation detail, so use the higher-level provenance methods instead of instantiating serializers.

---

## Reference

### AdaptServer lookups

| Call | Result |
|------|--------|
| `AdaptPlayer getOnlineAdaptPlayer(UUID)` | Live `AdaptPlayer`, or `null` when the player is not online and loaded |
| `AdaptPlayer getPlayer(Player)` | Live wrapper for an online Bukkit player. Creates and starts one with a warning if it is missing. Owning thread only |
| `Optional<PlayerData> getPlayerData(UUID)` | Online in-memory data only. Empty for anyone not currently loaded. Performs no storage work |
| `PlayerData peekData(UUID)` | Purge guard, then online map, then prefetch cache, then SQL, then the local json file. Caches what it loads. Returns a new empty `PlayerData` when nothing is found, never `null`. Not a tick-path query |
| `int getOnlineAdaptationLevel(UUID, String skillName, String adaptationName)` | Stored online learned level for that adaptation, `0` when the player is offline or their runtime is not ready. The `skillName` argument is accepted and never read |
| `boolean hasOnlineLearner(String adaptationName)` | Whether any online player has learned it |
| `boolean hasOnlineLearner(UUID, String adaptationName)` | Whether that specific online player has learned it |
| `List<AdaptPlayer> getLearnedAdaptPlayerSnapshot(String adaptationName)` | Cached immutable snapshot of the online learners of that adaptation |

`AdaptServer` constructors, lifecycle, event handlers, data reset, GUI opening, global boost and persistence methods are not general API.

### Reward calls

| Call | Novelty | Region XP policy | Telemetry |
|------|---------|------------------|-----------|
| `Skill.xp(Player, double[, key])`, `Skill.xp(Player, Location, double[, key])` | yes | yes | yes |
| `Skill.xpS(Player, Location, double[, key])`, silent but keeps visuals | yes | yes | yes |
| `Skill.xpSilent(Player, double[, key])` | no | no | yes |
| `Adaptation.xp(...)` / `Adaptation.xpSilent(...)`, keyed `adaptation:<id>:<key>` | as the `Skill` call it forwards to | | |
| `XP.xp(...)`, `XP.xpSilent(...)` | no | no | no |

Other rewards: `Skill.knowledge(Player, long)`, `Skill.xp(Location, double, int radius, long duration)` and `XP.spatialXP(Location, Skill, double, int radius, long duration)` for a spatial pulse, and `XP.knowledge(...)` / `XP.wisdom(...)` / `XP.boostXP(...)`.

Every path applies the player's XP multiplier (permission multipliers plus global and per-skill boosts) and the monotony multiplier inside `PlayerSkillLine.giveXP`, and honours pooled payout batching when `xpIntegrity.pooledPayoutEnabled` is on.

### Curves, multipliers and integrity helpers

| Type | Contract |
|------|----------|
| `Curves` | The configured curve enum; `getCurve()` returns its `NewtonCurve` |
| `NewtonCurve` | `getXPForLevel(level)` and `computeLevelForXP(xp, maxError)` are pure conversions. The default bisection inverse clamps at `experienceMaxLevel`; the closed-form entries do not |
| `XPMultiplier` | Mutable timed multiplier record stored inside player data |
| `SpatialXP` | Adapt-owned pending spatial reward. Create it through `XP.spatialXP`, never by constructing one and offering it to `AdaptServer` |
| `XpNovelty` | `noveltyMultiplier(player, location, rewardKey)`, `adjacencyBonusMultiplier(player, placedBlock)`, `fieldCycleMultiplier(player, cropBlock)`, `clear(uuid)`. Owning region thread only |
| `XpProvenance` | Records placed, broken, piston-moved, replaced and bonemealed blocks and returns `placeXpMultiplier`, `breakXpMultiplier`, `harvestXpMultiplier` from that history. Owning region thread only |

### WorldData

`WorldData.of(world)` gives the live store. `get(Block, Class<T>)`, `set(Block, T)` and `remove(Block, Class<T>)` are the typed block-attached accessors; `getEarningsMultiplier(Block)` reads the current anti-farm multiplier and `reportEarnings(Block)` records an earning and returns the resulting multiplier. All of them are region-thread-bound. `stop()`, `unregister()`, `onTick()` and the world save and unload handlers are Adapt-owned lifecycle.

### Types that are not contracts

| Type | Runtime role and restriction |
|------|------------------------------|
| `PlayerData` | Live mutable progression record. Reads such as `getLevel()`, `getMaxPower()`, `getUsedPower()`, `getAvailablePower()`, `hasPowerAvailable(...)`, `getStat(...)`, `getSkillLine(...)` and `getMutationData()` are fine. All writes bypass transaction, integrity, publication and persistence ownership |
| `PlayerSkillLine` | Live mutable skill line. Level, XP, knowledge, multiplier, progress, learned adaptation level and recent-earn reads are fine; writes are not |
| `PlayerAdaptation` | Mutable serialized adaptation record. `REGION_GRANTED_KEY`, `isRegionGranted()` and `setRegionGranted(...)` belong to region-grant reconciliation |
| `AdaptServerData`, `AdaptStatTracker`, `AdvancementHandler`, `Discovery<T>` | First-party server and player state plus advancement helpers, not lifecycle contracts |
| `AdaptPlayerTracker` | An empty public class with no members and no behavior |
| `PlayerDataPersistenceQueue` | Owns async local and SQL save/delete ordering and the shutdown flush. A second queue can race or resurrect data |
| `PlayerDataPurgeGuard` | Global tombstone set that stops queued writes landing after a deletion. Adapt owns mark, clear and reset |
| `AdaptComponent` | Large first-party convenience interface for item classification, server and player access, value, FX and event helpers. Some signatures use relocated or version-specific types |
| `AdaptDebugMode` | Global operator debug-bypass state. Use `/adapt debug mode`; setting it externally bypasses normal authorization |

## See also

- [02 - Concepts.md](<02 - Concepts.md>)
- [05 - Configuration Math.md](<05 - Configuration Math.md>)
- [38 - Runtime Architecture.md](<38 - Runtime Architecture.md>)
- [42 - API - Skills & Adaptations.md](<42 - API - Skills & Adaptations.md>)
- [48 - API - Mutations.md](<48 - API - Mutations.md>)
