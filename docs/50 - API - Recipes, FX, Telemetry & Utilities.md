# API - Recipes, FX, Telemetry & Utilities

This page completes the public-type inventory under `art.arcane.adapt.api`. Recipe, FX, selected telemetry reads, projectile ownership checks, value reads, and data-item helpers are callable integration surfaces; lifecycle controllers, listeners, platform bindings, mutable global registries, and first-party content helpers are listed separately and are not compatibility contracts.

## Recipes and brewing

`AdaptRecipe` provides builders for shaped, shapeless, smithing, stonecutting, smoking, blasting, furnace, and campfire recipes. Each recipe has a key, result, required Adapt level, `register`, `unregister`, and `is(Bukkit Recipe)` operations; registration mutates Bukkit's recipe registry and must run on the server thread. `MaterialChar` is the shaped-recipe character-to-material/tag/item choice used by those builders.

`AdaptRecipeBook.plan(registeredRecipes, levelResolver)` is a pure planner that returns an immutable `Plan` of recipe keys to discover and undiscover. `AdaptRecipeBook.synchronize(player, plan)` applies that plan on the player's owning thread; `Unlock` binds a `NamespacedKey` to an `Adaptation` and required level.

`BrewingRecipe` is the immutable-by-convention recipe description built from id, ingredient, base potion, result, brewing time, and fuel cost. `PotionBuilder` builds Bukkit potion item stacks and supports vanilla base potions, custom color, effects, Adventure name/lore, base item/type, and the `Type` item form. `AdaptBrewCompleteEvent` is the supported observation event and is documented in `45 - API - Events.md`.

The remaining potion classes are first-party runtime owners: `BrewingManager` owns the global recipe list and brew/click listeners, `BrewingTask` owns a live brewing-stand transaction, and `AdaptPotionRegistry` tracks/removes effects applied by Adapt. Do not register another manager/task or call registry reset/strip/forget from an integration.

## FX

Create immediate attributed effects with `Fx.now(adaptation|skill|mutation, location|entity, priority)`. The returned `FxEmitter` supports particles, rings, arcs, helixes, lines, bursts, columns, domes, trails, colored dust, sounds, and chords; it snapshots nearby viewers, honors the source's effect toggles, and enforces emitter/viewer/global budgets. `Fx.targeted(...)` sends one particle effect to one viewer while still checking global and per-player effect settings.

`FxTimeline.at(...)` and `follow(...)` create a frame sequence; configure duration, `FxPriority`, cull radius, `Frame`, and completion callback, then call `start()` on the owning thread. `cancel()` stops it. `FxPresets` provides the built-in charge, shockwave, impact, success/failure, trail, ready, level-up, and learning sequences. `FxPriority` selects budget shedding order; `FxBudget` publishes the hard caps and read-only `usedPackets()` / `shedBand()` diagnostics. Do not call `tryConsume(...)` from unrelated code because it consumes the shared packet budget.

`FxViewers.dispatch(...)` runs an action for a supplied collection or for viewers within a world radius; it is a low-level dispatch helper and the action itself is responsible for what it sends. `ViewerDisplayDirector` owns per-viewer fake block and line displays keyed by channel/key, with show, query, targeted clear, channel clear, viewer retirement, orphan purge, and global clear operations. A plugin using it must use a unique channel and clear that channel on disable.

`ViewerGlowCoordinator` owns private per-viewer glow layers over GlowingEntities. The live instance is `Adapt.instance.getViewerGlowCoordinator()`; use a specific `Layer`, pair every successful `set(...)` with `unset(...)` or `clearLayer(...)`, and never construct a second coordinator. `FxDirector` is Adapt's timeline ticker and lifecycle owner and is not an integration contract.

`FxDispatch` is package-private and intentionally absent from the public inventory.

## Telemetry

`AbilityCheckTelemetry` exposes rolling ability-check/cache/execution rates, timings, budget percentage, ability ids, and immutable `AbilitySnapshot` maps. Read with the current epoch milliseconds; `record...`, `beginExecution`, `endExecution`, and `clear` are Adapt-owned instrumentation operations.

`AdaptRuntimeTelemetry` exposes `xpPerMinute`, XP payout count, provenance-operation count, and event-handler-operation count over the current minute. Its `record...` and `clear` methods mutate global metrics and are not integration calls. `AdaptTelemetryClock` is the ticker-refreshed clock feeding those counters; external code must use `System.currentTimeMillis()` for telemetry reads and must not call `refresh()`.

## Projectile ownership

`ProjectileClaims.isUnclaimed(projectile, ownedKeys...)` and `isUnclaimedContainer(data, ownedKeys...)` reject a projectile carrying persistent-data keys not owned by the caller. Pass every key your plugin owns; a `true` result means another system has not claimed the projectile.

`ProjectileReplacementRegistry` transfers ownership when Adapt replaces one projectile entity with another. `register(projectile, Claim)` installs a one-shot claim; `begin(source)` removes it and returns a `Ticket`, whose `complete(replacement)` or `cancel()` must be called exactly once. `unregister` removes one claim. `clear()` is Adapt's shutdown operation and must not be called by integrations.

## Data items and material value

`DataItem<T>` defines a Bukkit item plus typed JSON payload stored in persistent data. Implement `getMaterial`, `getType`, `applyLore`, and `applyMeta`; `withData`, `setData`, `getData`, `hasData`, and `ensureCooldownGroup` handle the item. The encoded key is derived from Adapt's namespace and the payload class name, so changing the payload class breaks existing items. `PotionItem` and its nested `Data` class are the built-in potion-item base, not a registered public item type.

`MaterialValue.getValue(material)` returns Adapt's cached computed value and `debugValue(material)` logs its recipe expansion. `MaterialValue.get()`, `save()`, and `invalidateCache()` expose the singleton/cache lifecycle and are Adapt-owned; config reload invalidates the cache so later reads use the new value settings, and persisted values are scoped to the current server process. `MaterialCount` is a mutable material/amount pair and `MaterialRecipe` is a mutable input-list/output pair used by the calculator; neither registers Bukkit recipes.

## Notifications and HUD

`Notification` defines duration, group, and `play(AdaptPlayer)`. `ActionBarNotification`, `TitleNotification`, `SoundNotification`, and `AdvancementNotification` are queued implementations; `SoundNotification.withXP(...)` adds its XP payload. `Notifier` owns a player's queue, XP aggregation, and tick lifecycle and is constructed by Adapt, not integrations.

`AdaptHud.actionBar`, `xpTicker`, `title`, and `guiTitle` submit messages to Adapt's shared HUD arbitration. Call them on the player's owning thread; `clear(player)` removes that player's Adapt HUD state. `start` and `stop` are plugin lifecycle operations.

## Attribute, minion, advancement, tick, and platform internals

The following Java-public types support Adapt's built-in catalogue. They are documented here so public visibility is not mistaken for a compatibility promise.

| Package | Types | Runtime ownership |
|---------|-------|-------------------|
| `api.attribute` | `AdaptAttributeKey`, `AdaptAttributeResolver`, `AdaptAttributeScheduler`, `AdaptAttributeService`, `AdaptAttributeTracker` | Global namespaced attribute application, timed removal, entity scheduling, and tracking. Adapt owns service startup/shutdown, listeners, reconciliation, and tracker state. |
| `api.minion` | `MinionBurden` | Global minion-health burden service and `MinionRegistry`; only pure `computeReduction(...)` is safe as a calculation. Adapt owns runtime and registrations. |
| `api.advancement` | `AdaptAdvancement`, `AdaptAdvancementFrame`, `AdvancementManager`, `AdvancementSpec`, `AdvancementVisibility` | Built-in advancement construction and UltimateAdvancementAPI bindings. Several signatures expose relocated/shaded library types. |
| `api.tick` | `Ticked`, `TickedObject`, `Ticker` | Adapt scheduler registration, burst/skip state, telemetry, tick execution, and shutdown. External plugins must use their own Paper/Folia scheduler. |
| `api.runtime` | `AdaptationGate` | First-party fast gates for world/game-mode/player checks. This is an implementation helper, not a complete ability authorization decision. |
| `api.version` | `Version`, `IBindings`, `IAttribute`, `RuntimeBindings`, `RuntimeAttribute` | Bukkit-version binding and emulated attribute layer. Signatures expose internal model/collection types and the singleton is Adapt-owned. |

## Root component and permission internals

`AdaptPermissionRegistrar` owns startup registration of skill, adaptation, mutation, and XP-multiplier permission nodes. `useNode(name)` is a pure mapping, but `registerAll` and `registerXpMultiplierNodes` mutate Bukkit's global permission manager and are Adapt lifecycle operations.

`Component` is the first-party adaptation/skill helper interface for player lookup, material tests, FX, value, damage, cooldown, and event calls. `ComponentEventRegistrar` scans an Adapt listener and registers its handlers; `EventHandlerInvoker` builds the guarded executors and records handler telemetry. These types are not general listener-registration APIs; external plugins use Bukkit's `PluginManager.registerEvents`.

## See also

- `37 - Recipes, Brewing & Value.md`
- `41 - API - Getting Started.md`
- `45 - API - Events.md`
- `49 - API - Player Data, XP & World.md`
