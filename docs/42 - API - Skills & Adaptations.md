# API - Skills & Adaptations

`Skill` and `Adaptation` are the catalogue objects Adapt hands you in its events and registries. A skill is
one of the 23 lines a player levels; an adaptation is one purchasable ability inside a line. Their
read-only accessors and `AdaptationLearningTransaction` are supported integration surfaces. The rest of
the package is Adapt's own authoring machinery.

You reach for this document when you already have a `Skill<?>` or an `Adaptation<?>` and want to know what
is safe to ask it, or when you want to give a player a level in something without reimplementing the
knowledge, power and economy checks by hand.

The caveat is linkage. These are the classes where Adapt's relocated utility library shows through: some
accessors return VolmLib collections, and calling one bakes a build-time package path into your jar. Stick
to the members listed in Reference and you never touch one. See
[Adapt relocates VolmLib](<41 - API - Getting Started.md#adapt-relocates-volmlib>).

## Getting the registry

There is one registry and it lives on the enabled plugin. Ask for it after Adapt has enabled, never in
your own `onLoad`.

```java
Plugin plugin = Bukkit.getPluginManager().getPlugin("Adapt");
if (plugin instanceof Adapt adapt && adapt.isEnabled()) {
    SkillRegistry registry = adapt.getAdaptServer().getSkillRegistry();
    Skill<?> skill = registry.getSkill("rift");
}
```

`getSkill(String)` finds enabled skills only; `getAnySkill(String)` also sees skills Adapt knows about but
has disabled. Both accept the id in any casing. `getSkills()` and `getAllSkills()` hand back a fresh
snapshot each call, declared as `List<Skill<?>>`, so iterating one is safe while the catalogue changes
underneath you. If you cache anything derived from the catalogue, key the cache on `getCatalogRevision()`;
it changes whenever a skill or adaptation is registered, unregistered or hot-reloaded.

Registration, hot reload, advancement and recipe synchronisation, the registry's own event handlers, and
`unregister()` are Adapt lifecycle operations. Calling them from outside is not supported.

## Reading a skill or an adaptation

Stick to the accessors that return a `String`, an `int`, a `boolean`, a `Material` or a Bukkit type. Those
are listed in Reference and they are stable. Two of them deserve a warning.

`Skill.getLocalizedName()` does not resolve the localization catalogue despite the name. It capitalises
the registry id, so `rift` becomes `Rift` in every locale. Use `getDisplayName()` for player-facing text.

On `Adaptation`, `getLevel(Player)` is the stored learned level and nothing else, while
`getActiveLevel(Player)` runs the whole gate: learned level, world blacklist, game mode, protection,
`adapt.use` permission, usage conflicts, `AdaptAdaptationUseEvent`, and every registered
`AbilityUsePolicy`. It returns `0` the moment any of them says no. If you want to know whether an ability
would actually fire right now, that is the one to call.

Everything else on these interfaces (storage, XP, scheduling, damage and projectile helpers, GUI, recipes,
advancements, models, ticking, registration, config mutation) is first-party authoring code. Some of it
names relocated types, and all of it mutates Adapt-owned runtime state.

## Learning and unlearning

`AdaptationLearningTransaction` is the only supported way to change a learned level. Each of its two
statics is a complete transaction: it clamps the target, checks power and knowledge, runs the Vault charge
or refund when the learning economy is on, honours permanent-adaptation rules and the hardcore no-refunds
setting, and converts a region-granted level into a paid one.

```java
AdaptationLearningTransaction.Result result =
    AdaptationLearningTransaction.learn(adaptation, player, 3, false);
```

Call it on the tick thread that owns the player, and pass the level the player should end up at rather
than a delta. `learn` clamps to the adaptation's max level, `unlearn` clamps at zero. Pass
`bypassCosts = true` only for an administrative action you have already authorised: it skips the power,
knowledge and money checks, and on `unlearn` it also overrides the permanent-adaptation refusal and pays
nothing back. The returned `Result` is the complete outcome, so do not also write `PlayerSkillLine`
yourself. A `RuntimeException` thrown part way through `learn` rolls back the level, the knowledge and any
Vault charge before it propagates.

## Runtime markers

Two annotations exist for Adapt's own adaptation classes, and both target handler **methods**, not types.
`@RunsWithoutLearnedAdaptation` opts a handler out of the non-learner gate on `PlayerMoveEvent` and
`PlayerJumpEvent`, which is how teardown and cleanup handlers still run for a player who has unlearned the
adaptation. `@ReceiveCancelledEvents` opts a handler out of Adapt's default `ignoreCancelled` behaviour so
it still receives an already-cancelled Bukkit event.

Neither is useful in an integration. Adapt only inspects methods on listeners it registers itself, and the
movement gate additionally requires the listener to be an `Adaptation<?>`, so putting either annotation on
your own listener does nothing.

## Reference

### Supported `SkillRegistry` members

| Member | Returns | Notes |
|---|---|---|
| `getSkill(String)` | `Skill<?>` | Enabled skills only. `null` when unknown |
| `getAnySkill(String)` | `Skill<?>` | Also sees known disabled skills |
| `getSkills()` | `List<Skill<?>>` | Snapshot of enabled skills |
| `getAllSkills()` | `List<Skill<?>>` | Snapshot of every known skill |
| `getCatalogRevision()` | `long` | Cache-invalidation key |

### Supported read-only `Skill` members

| Member | Returns |
|---|---|
| `getName()` | `String`, the registry id, for example `rift` |
| `isEnabled()` | `boolean` |
| `getIcon()` | `Material` |
| `getDescription()` | `String` |
| `getDisplayName()`, `getDisplayName(int)`, `getShortName()` | `String` |
| `getLocalizedName()` | `String`, the capitalised registry id, not a translation |
| `getConfigurationClass()` | `Class<T>` |
| `getConfig()` | `T`, when the consumer already knows the config type |

Do not call `getAdaptations()`, `getRecipes()`, `getStatTrackers()`, `getModel()`, the registration or XP
helpers, or the tick methods. Those either return a relocated `KList` or mutate Adapt-owned state.
`Skill.getId()` comes from the ticker and is a random UUID with a suffix, not the skill key.

### Supported read-only `Adaptation` members

| Member | Returns |
|---|---|
| `getName()` | `String`, the adaptation id, for example `rift-blink` |
| `getSkill()` | `Skill<?>` |
| `getIcon()` | `Material` |
| `getDescription()`, `getDisplayName()`, `getDisplayName(int)` | `String` |
| `getMaxLevel()`, `getBaseCost()`, `getInitialCost()` | `int` |
| `getCostFactor()` | `double` |
| `getCostFor(int)`, `getCostFor(int, int)`, `getRefundCostFor(int, int)`, `getPowerCostFor(int, int)` | `int` |
| `getLevel(Player)` | `int`, the stored learned level |
| `getActiveLevel(Player)` | `int`, `0` unless every gate passes |
| `isEnabled()`, `isPermanent()`, `canUse(Player)` | `boolean` |

`canUse(Player)` is public and fires `AdaptAdaptationUseEvent` plus every `AbilityUsePolicy` on its own,
without the learned-level test. It is the one path that can present a policy with `level() == 0`.

### `AdaptationConfig` fields

The base TOML shape for a first-party adaptation. External code may read these but must not replace a live
config object.

| Key | Type | Default | What it does |
|---|---|---|---|
| `enabled` | boolean | `true` | Turns the adaptation off without removing files |
| `permanent` | boolean | `false` | Treats the adaptation as always learned and blocks unlearning |
| `showParticles` | boolean | `true` | Plays this adaptation's particle effects |
| `showSounds` | boolean | `true` | Plays this adaptation's sound effects |
| `baseCost` | int | `4` | Knowledge charged per level before the scaling factor |
| `costFactor` | double | `0.45` | Growth applied to each level above the first |
| `maxLevel` | int | `5` | Highest level a player can reach |
| `initialCost` | int | `2` | Knowledge charged for level 1 |

### `AdaptationLearningTransaction.Result`

| Constant | Meaning |
|---|---|
| `LEARNED` | The level was raised |
| `UNLEARNED` | The level was lowered |
| `NO_CHANGE` | The target matched or was worse than the current paid level |
| `SKILL_LINE_UNAVAILABLE` | The player has no skill line for the owning skill |
| `INSUFFICIENT_POWER` | Not enough power for the requested levels |
| `INSUFFICIENT_KNOWLEDGE` | Not enough knowledge, or the knowledge spend failed |
| `INSUFFICIENT_FUNDS` | Vault refused the withdrawal for lack of balance |
| `ECONOMY_UNAVAILABLE` | Vault or its economy provider is missing |
| `ECONOMY_FAILED` | Vault accepted the call and reported a failure |
| `PERMANENT` | `unlearn` refused because the adaptation is permanent |

### Public types that are not contracts

Java-public so Adapt's catalogue can be assembled across packages. Not third-party extension points.

| Type | Runtime role and restriction |
|---|---|
| `SimpleSkill` | Base for Adapt's built-in skills. Its constructor requires `SkillPresentation`, whose `TextKey` members are relocated in the shaded jar |
| `SimpleAdaptation` | Base for built-in adaptations; owns config files, recipes, advancements, FX, storage, and registration lifecycle |
| `AbilityApiBridge` | Installs and uninstalls the internal ability funnel. Integrations register `AbilityUsePolicy` or `AbilityCostProvider` through Bukkit instead |
| `Cooldowns` | UUID cooldown map created by `PlayerStateRegistry`; uses relocated time utilities and is Adapt-owned state |
| `ItemCooldowns` | Shared item and material cooldown coordinator used by built-in abilities; its group registry and player cooldown mutation are global |
| `PlayerStateRegistry` | Tracks first-party per-player maps and owns the quit listener; `reset()` clears every registered map |
| `VelocityBurstRuntime` | Global movement-burst scheduler and its `Client`, `Profile`, `BurstRequest`, `Feedback` and `StartResult` nested types. Adapt owns startup, ticking and shutdown |
| `ChunkLoading` | Folia and Paper chunk-loading helper used by built-in adaptations; not an external scheduling contract |
| `SkillOwnerPulse` | Internal learner-index refresh pulse |

`AdaptationRuntimeGuards` and `SkillRuntimeGuards` are package-private implementation classes, not API
types and not annotations. The only public markers are `RunsWithoutLearnedAdaptation` and
`ReceiveCancelledEvents`, both `@Target(ElementType.METHOD)` with runtime retention.

## See also

- [41 - API - Getting Started.md](<41 - API - Getting Started.md>)
- [43 - API - Ability Use Policy.md](<43 - API - Ability Use Policy.md>)
- [44 - API - Ability Cost.md](<44 - API - Ability Cost.md>)
- [45 - API - Events.md](<45 - API - Events.md>)
- [10 - Skills Catalog.md](<10 - Skills Catalog.md>)
