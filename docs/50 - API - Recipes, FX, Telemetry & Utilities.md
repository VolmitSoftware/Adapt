# API - Recipes, FX, Telemetry & Utilities

This page covers what is left of `art.arcane.adapt.api` after skills, adaptations, abilities, events, protection and player data have their own pages. Recipes and brewing, the FX engine, a few telemetry reads, projectile ownership, material value, data items, and the HUD queue are all callable from outside Adapt.

The FX engine is the part most plugins actually want. It draws particle shapes and plays sounds with viewer snapshotting, per-emitter and per-viewer caps, and a global packet budget that sheds low-priority effects when the server is under load, so a plugin using it cannot flood a busy server the way a raw `World.spawnParticle` loop can.

Everything else on this page is smaller and more specific: a recipe builder set with an Adapt level gate, a recipe-book planner, projectile ownership claims so two plugins do not fight over the same arrow, and a cached material value calculator.

The last two sections list types that are Java-public because Adapt's own content needs them across packages. They are documented so nobody mistakes visibility for a compatibility promise. Several expose relocated or version-specific classes and will break on you.

## Recipes and brewing

`AdaptRecipe` builds Bukkit recipes with an Adapt level requirement attached. Static factories cover shaped, shapeless, smithing, stonecutting, smoking, blasting, furnace and campfire forms. Every recipe carries a key, a result and a required level, and answers `register`, `unregister` and `is(Recipe)`. Registration mutates Bukkit's global recipe registry, so run it on the server thread. `MaterialChar` is the character-to-material, tag, or item-choice mapping the shaped builder uses.

`AdaptRecipeBook` is the discovery half. `plan(...)` is pure: give it your `Unlock` bindings and a function that resolves a player's level for an adaptation, and it returns an immutable `Plan` of recipe keys to discover and keys to undiscover, with any key in both lists resolved in favour of discovery. `synchronize(player, plan)` applies that plan on the player's owning thread.

`BrewingRecipe` describes an Adapt brewing recipe and `PotionBuilder` builds the potion item stacks, including vanilla base potions, custom colour, effects, and Adventure names and lore. `AdaptBrewCompleteEvent` is the observation event and is documented in [45 - API - Events.md](<45 - API - Events.md>). The rest of the potion package belongs to Adapt: `BrewingManager` owns the global recipe list and the brew and click listeners, `BrewingTask` owns one live brewing-stand transaction, and `AdaptPotionRegistry` tracks and removes effects Adapt applied.

## FX

`Fx.now(source, target, priority)` starts an immediate effect. The source is an `Adaptation`, a `Skill` or a `MutationType`, which is what lets Adapt honour that source's own particle and sound toggles. The target is a `Location` or an `Entity`. The returned `FxEmitter` chains shapes and sounds:

```java
Fx.now(this, player.getLocation(), FxPriority.COMBAT)
  .ring(Particle.CRIT, 1.5, 24, 0.1)
  .sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.4f);
```

The emitter snapshots nearby viewers once and enforces three limits: a per-emitter particle cap, a per-viewer emission cap, and a shared global packet budget that starts shedding by `FxPriority` when the server falls behind. `Fx.targeted(...)` is the single-viewer escape hatch for one particle effect, and it still respects global and per-player effect settings.

`FxTimeline` is the multi-frame version. `at(source, location)` pins it in place, `follow(source, entity)` tracks a moving target; set a duration in ticks, a priority, a cull radius and a `Frame` callback, then `start()` on the owning thread and `cancel()` to stop it. `FxPresets` holds the built-in sequences Adapt's own content uses. `FxViewers.dispatch(...)` is the low-level helper underneath, running your action for a supplied collection of players or for everyone inside a world radius; what gets sent is up to your action.

`ViewerDisplayDirector` owns per-viewer fake block and line displays keyed by channel and key. Pick a channel name unique to your plugin and clear that channel on disable. `ViewerGlowCoordinator` owns private per-viewer glow layers over GlowingEntities; the live instance is `Adapt.instance.getViewerGlowCoordinator()`, you must pick a specific `Layer`, and every successful `set(...)` needs a matching `unset(...)` or `clearLayer(...)`. Never construct a second coordinator.

`FxDirector` is Adapt's timeline ticker and lifecycle owner, not an integration point. `FxDispatch` is package-private and deliberately absent from the public surface.

## Telemetry

Two classes expose read-only counters, both taking `System.currentTimeMillis()` as their `now` argument. `AbilityCheckTelemetry` covers ability-check rates, cache hit and miss rates, average check time, the share of the tick budget those checks consume, and an immutable `AbilitySnapshot` per ability. `AdaptRuntimeTelemetry` covers XP per minute, XP payout count, provenance operation count and event-handler operation count over the current minute.

Their `record...`, `beginExecution`, `endExecution` and `clear` methods are Adapt-owned instrumentation, and calling them corrupts the numbers your own dashboard is reading. `AdaptTelemetryClock` is the ticker-refreshed clock feeding those counters; read the wall clock yourself and never call `refresh()`.

## Projectile ownership

When an adaptation launches or repurposes a projectile it stamps an ownership key on the projectile's persistent data container. Anything that clones, redirects or replaces projectiles has to refuse ones carrying a foreign key, or it hijacks another system's arrow mid-flight.

`ProjectileClaims.isUnclaimed(projectile, ownedKeys...)` is the check. Pass every `NamespacedKey` your plugin owns. A `true` result means the projectile carries no key you do not own, so nobody else has claimed it.

`ProjectileReplacementRegistry` transfers that ownership when one projectile entity is swapped for another: `register` installs a one-shot claim, `begin` removes it and hands back a `Ticket` that must be completed or cancelled exactly once.

## Data items and material value

`DataItem<T>` is a Bukkit item with a typed JSON payload in its persistent data. You implement four methods and the interface handles storage, lore, meta and cooldown stamping. The persistent-data key is derived from Adapt's namespace and the hash of the payload class's canonical name, so renaming or moving the payload class orphans every item already in a player's inventory. Declaring a cooldown group keeps the vanilla cooldown sweep on your item rather than on every stack of the same material.

`MaterialValue.getValue(material)` returns Adapt's cached computed value for a material, and `debugValue(material)` logs how that value was expanded from recipes. Config reload invalidates the cache, so later reads pick up new value settings, and persisted values only last for the current server process.

## Notifications and HUD

`Notification` is a queued player-facing message with a total duration and a group, and Adapt ships action bar, title, sound and advancement kinds. `Notifier` owns a player's queue and its XP aggregation and tick lifecycle; Adapt constructs it, you do not.

`AdaptHud` submits messages into Adapt's shared HUD arbitration so Adapt's own action bar, XP ticker and titles do not fight each other or other plugins. Call it on the player's owning thread.

---

## Reference

### Recipes and brewing

| Type | Contract |
|------|----------|
| `AdaptRecipe` | Static builders `shaped()`, `shapeless()`, `smithing()`, `stonecutter()`, `smoker()`, `blast()`, `furnace()`, `campfire()`. Each recipe answers `getKey()`, `getNSKey()`, `getResult()`, `getRequiredLevel()`, `register()`, `unregister()`, `is(Recipe)`. `register` and `unregister` mutate Bukkit's recipe registry and need the server thread |
| `MaterialChar` | Shaped-recipe character to material, tag, or `RecipeChoice` mapping |
| `AdaptRecipeBook` | `plan(Collection<Unlock>, ToIntFunction<Adaptation<?>>)` is pure and returns `Plan(discover, undiscover)`; keys landing in both are kept only in `discover`. `synchronize(Player, Plan)` calls `undiscoverRecipes` then `discoverRecipes` on the owning thread. `Unlock` is `record(NamespacedKey key, Adaptation<?> adaptation, int requiredLevel)` |
| `BrewingRecipe` | Builder over `id`, `ingredient`, `basePotion`, `result`, `brewingTime`, `fuelCost` |
| `PotionBuilder` | `vanilla(Type, PotionType)`, `of(Type)`, `of(ItemStack)`, then `setColor`, `addEffect`, `setName`, `addLore`, `setLore`, `setBaseItem`, `setBaseType`, `build()`. `Type` is the item form |
| `BrewingManager`, `BrewingTask`, `AdaptPotionRegistry` | Adapt-owned. Do not register a second manager or task, and do not call `record`, `forget`, `strip`, `retainActive` or `reset` |

### FX

| Type | Contract |
|------|----------|
| `Fx` | `now(Adaptation / Skill / MutationType, Location / Entity, FxPriority)` returns an `FxEmitter`. `targeted(Player, Particle, Location, count, spreadX, spreadY, spreadZ, speed)` sends one effect to one viewer |
| `FxEmitter` | `particle`, `ring`, `arc`, `helix`, `line`, `burst`, `column`, `dome`, `trail`, `dustRing`, `dustBurst`, `dustHelix` (each with an optional `Color`), `sound`, and two- and three-note `chord` |
| `FxTimeline` | `at(Adaptation / Skill, Location)`, `follow(Adaptation / Skill, Entity)`, then `duration(ticks)`, `priority(FxPriority)`, `cullRadius(double)`, `frame(Frame)`, `onComplete(Runnable)`, `start()`, `cancel()` |
| `FxPresets` | `chargeRing`, `shockwave`, `impact`, `successShimmer`, `failFizzle`, `streakTrail`, `readyPing`, `levelUpBurst`, `learnCelebration` |
| `FxPriority` | `GAMEPLAY`, `COMBAT`, `TRANSITION`, `TRAIL`, `AMBIENT`, listed highest priority first |
| `FxViewers` | `dispatch(Collection<Player>, Consumer<Player>)`, `dispatch(World, x, y, z, radius, Consumer<Player>)`. `DEFAULT_CULL_RADIUS` `24.0`, `MAX_CULL_RADIUS` `48.0` |
| `ViewerDisplayDirector` | `showBlock`, `showPersistentBlock`, `showLine`, `isShowing`, `clearViewerKey`, `clearViewer`, `clearChannel`, `retireViewer`, `purgeOrphans`, `clearAll` |
| `ViewerGlowCoordinator` | `isAvailable`, `set(Layer, Entity, Player, ChatColor)`, `unset`, `clearLayer`, `discardViewer`. `Layer`: `STEALTH_SIGHT`, `TRAGOUL_DEATH_SENSE`, `RANGED_TRAJECTORY_SIGHT`, `STEALTH_THREAT`, `MUTATION_UMBRAL_ECHO`, `TAMING_ALPHAS_COMMAND`, `RANGED_HEARTSEEKER` |
| `FxDirector` | Adapt's timeline ticker and lifecycle owner. Not an integration contract |

### FX budget

| Constant or read | Value |
|------------------|-------|
| `FxBudget.GLOBAL_PACKET_BUDGET` | `10000` particle-times-viewer packets per tick, reset by `FxDirector`. Each priority gets a share of it: `GAMEPLAY` 100%, `COMBAT` 95%, `TRANSITION` 80%, `TRAIL` 65%, `AMBIENT` 50% |
| `FxBudget.PER_EMITTER_PARTICLE_CAP` | `256` particles per emitter call |
| `FxBudget.PER_VIEWER_EMISSION_CAP` | `64` emissions per viewer |
| `FxBudget.usedPackets()` | Packets consumed so far this tick. Read-only diagnostics |
| `FxBudget.shedBand()` | Which shed band the TPS sampler has settled on, `0` for none. Read-only diagnostics |
| `FxBudget.densityScalar(FxPriority)` | The particle-count scalar for that priority in the current shed band |
| `FxBudget.tryConsume(FxPriority, int)` | Consumes the shared budget. Adapt-owned; calling it from unrelated code starves real effects |

### Telemetry reads

Every read takes `now` in epoch milliseconds.

`AbilityCheckTelemetry`: `checksPerMinute`, `successfulChecksPerMinute`, `checksPerSecond`, `successfulChecksPerSecond`, `cacheHitsPerMinute`, `cacheMissesPerMinute`, `cacheHitRatio`, `averageCheckMicros`, `estimatedTimingMillisPerSecond`, `timingBudgetPercent`, `checksPerTick`, `abilityIds`, `abilitySnapshots`.

`AdaptRuntimeTelemetry`: `xpPerMinute`, `xpPayoutOpsPerMinute`, `provenanceOpsPerMinute`, `eventHandlerOpsPerMinute`.

`AdaptTelemetryClock.millis()` is the ticker-refreshed clock; `refresh()` is Adapt-owned, as are `record...`, `beginExecution`, `endExecution` and `clear` on both telemetry classes.

### Projectiles, items and value

| Type | Contract |
|------|----------|
| `ProjectileClaims` | `isUnclaimed(Projectile, NamespacedKey...)`, `isUnclaimedContainer(PersistentDataContainer, NamespacedKey...)`. `false` for a null container; `true` for an empty one |
| `ProjectileReplacementRegistry` | `register(Projectile, Claim)`, `begin(Projectile)` returning a `Ticket`, `unregister(UUID)`. `Ticket.complete(replacement)` or `cancel()` exactly once. `clear()` is Adapt shutdown only |
| `DataItem<T>` | Implement `getMaterial`, `getType`, `applyLore`, `applyMeta`; optionally `getCooldownGroup`. Provided: `blank`, `withData`, `setData`, `getData`, `hasData`, `ensureCooldownGroup`. The persistent-data key comes from Adapt's namespace plus the payload class canonical name's hash |
| `PotionItem` and its nested `Data` | The built-in potion-item base, not a registered public item type |
| `MaterialValue` | `getValue(Material)` reads the cached computed value; `debugValue(Material)` logs the recipe expansion. `get()`, `save()` and `invalidateCache()` are Adapt-owned singleton and cache lifecycle |
| `MaterialCount`, `MaterialRecipe` | Mutable material-and-amount and inputs-and-output pairs used by the calculator. Neither registers Bukkit recipes |

### Notifications and HUD

`Notification` declares `getTotalDuration()`, `play(AdaptPlayer)` and `getGroup()`, which defaults to `"default"`. `ActionBarNotification`, `TitleNotification`, `SoundNotification` and `AdvancementNotification` implement it, and `SoundNotification.withXP(double)` attaches an XP payload. `Notifier` owns a player's queue, XP aggregation and tick lifecycle and is constructed by Adapt.

`AdaptHud` exposes `actionBar(Player, String)`, `xpTicker(Player, String)`, `title(Player, title, subtitle, inTicks, stayTicks, outTicks)`, `guiTitle(...)` with the same shape, and `clear(Player)`, all on the owning thread. `start(Adapt)` and `stop()` are plugin lifecycle.

### First-party internals

Java-public because Adapt's built-in catalogue needs them across packages. None is a compatibility promise.

| Package | Types | Runtime ownership |
|---------|-------|-------------------|
| `api.attribute` | `AdaptAttributeKey`, `AdaptAttributeResolver`, `AdaptAttributeScheduler`, `AdaptAttributeService`, `AdaptAttributeTracker` | Namespaced attribute application, timed removal, entity scheduling and tracking. Adapt owns startup, shutdown, listeners, reconciliation and tracker state |
| `api.minion` | `MinionBurden` and its `MinionRegistry` | Global minion health-burden service. Only the pure `computeReduction(count, healthPerMinion, baselineMaxHealth, minimumMaxHealth)` is safe as a calculation |
| `api.advancement` | `AdaptAdvancement`, `AdaptAdvancementFrame`, `AdvancementManager`, `AdvancementSpec`, `AdvancementVisibility` | Built-in advancement construction and UltimateAdvancementAPI bindings. Several signatures expose relocated library types |
| `api.tick` | `Ticked`, `TickedObject`, `Ticker` | Adapt scheduler registration, burst and skip state, telemetry, tick execution and shutdown. Use your own Paper or Folia scheduler instead |
| `api.runtime` | `AdaptationGate` | First-party fast world, game-mode and player checks. An implementation helper, not a complete ability authorization decision |
| `api.version` | `Version`, `IBindings`, `IAttribute`, `RuntimeBindings`, `RuntimeAttribute` | Bukkit-version binding and emulated attribute layer. Signatures expose internal model and collection types; the singleton is Adapt-owned |
| `api` root | `Component`, `ComponentEventRegistrar`, `EventHandlerInvoker` | The first-party skill and adaptation helper interface, the listener scanner that registers Adapt handlers, and the guarded executor builder that records handler telemetry. External plugins use Bukkit's `PluginManager.registerEvents` |
| `api` root | `AdaptPermissionRegistrar` | `useNode(name)` is a pure mapping to `adapt.use.<name minus hyphens>`. `registerAll` and `registerXpMultiplierNodes` mutate Bukkit's global permission manager and are Adapt lifecycle |

## See also

- [37 - Recipes, Brewing & Value.md](<37 - Recipes, Brewing & Value.md>)
- [41 - API - Getting Started.md](<41 - API - Getting Started.md>)
- [45 - API - Events.md](<45 - API - Events.md>)
- [49 - API - Player Data, XP & World.md](<49 - API - Player Data, XP & World.md>)
