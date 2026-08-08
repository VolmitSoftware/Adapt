# API - Skills & Adaptations

`Skill` and `Adaptation` are the catalogue objects exposed by Adapt events and registries. Their read-only Bukkit/JDK accessors and `AdaptationLearningTransaction` are supported integration surfaces; the concrete authoring and runtime classes in this package remain first-party implementation types because several signatures expose Adapt's relocated utility classes.

## Supported catalogue access

Obtain the live registry only after Adapt has enabled:

```java
Plugin plugin = Bukkit.getPluginManager().getPlugin("Adapt");
if (plugin instanceof Adapt adapt && adapt.isEnabled()) {
    SkillRegistry registry = adapt.getAdaptServer().getSkillRegistry();
    Skill<?> skill = registry.getSkill("rift");
}
```

`SkillRegistry.getSkill(String)` returns enabled skills, `getAnySkill(String)` also sees known disabled skills, and `getSkills()` / `getAllSkills()` return JDK `List<Skill<?>>` snapshots. `getCatalogRevision()` changes when the catalogue changes and can be used to invalidate a consumer cache. Registration, hot reload, advancement synchronization, recipe synchronization, event handlers, and `unregister()` are Adapt-owned lifecycle operations and are not third-party contracts.

The supported read-only `Skill` members are `getName()`, `isEnabled()`, `getIcon()`, `getDescription()`, `getConfigurationClass()`, and `getConfig()` when the consumer already knows the config type. `getLocalizedName()` currently capitalizes the registry id rather than resolving the localization catalogue. Do not call `getAdaptations()`, `getRecipes()`, `getStatTrackers()`, color/model/display helpers, registration methods, XP helpers, or tick methods from an external plugin: those signatures or implementations use relocated VolmLib types or mutate Adapt-owned runtime state.

The supported read-only `Adaptation` members are `getName()`, `getSkill()`, `getIcon()`, `getDescription()`, `getMaxLevel()`, `getBaseCost()`, `getInitialCost()`, `getCostFactor()`, `getCostFor(...)`, `getRefundCostFor(...)`, `getPowerCostFor(...)`, `getLevel(Player)`, `getActiveLevel(Player)`, `isEnabled()`, `isPermanent()`, and `canUse(Player)`. `getActiveLevel` includes the learned-level, world, game-mode, permission, conflict, protector, and ability-policy gates; `getLevel` is the stored learned level. Storage, XP, scheduler, damage, projectile, GUI, recipe, advancement, model, tick, registration, and configuration mutation helpers are first-party authoring operations rather than a stable integration contract.

`AdaptationConfig` is the base TOML shape used by first-party adaptations: `enabled`, `permanent`, `showParticles`, `showSounds`, `baseCost`, `costFactor`, `maxLevel`, and `initialCost`. It is documented for config readers, but external code must not replace a live adaptation config object.

## Learning and unlearning

`AdaptationLearningTransaction.learn(adaptation, player, targetLevel, bypassCosts)` and `unlearn(...)` are the supported way to change a learned level. They clamp the target, apply knowledge and power checks, Vault charges/refunds, permanent-adaptation rules, hardcore refund policy, and region-grant conversion; call them on the player's owning thread and pass `bypassCosts = true` only for an administrative action that has already been authorized.

The nested `AdaptationLearningTransaction.Result` reports `LEARNED`, `UNLEARNED`, `NO_CHANGE`, cost failures, permanence, missing skill data, and economy failures. A result is the complete outcome; do not also write `PlayerSkillLine` directly.

## Runtime markers

`RunsWithoutLearnedAdaptation` marks a first-party adaptation whose guarded handlers may run before it is learned. `ReceiveCancelledEvents` marks a first-party adaptation whose component event handlers may receive an already-cancelled Bukkit event. The annotations are readable by integrations, but applying them to an unrelated listener has no effect because Adapt only inspects registered adaptation classes.

## Public authoring and runtime types that are not contracts

These classes are Java-public so Adapt's catalogue can be assembled across packages. They are not supported third-party extension points:

| Type | Runtime role and restriction |
|------|------------------------------|
| `SimpleSkill` | Base for Adapt's built-in skills. Its constructor requires `SkillPresentation`, whose `TextKey` members are relocated in the shaded jar. |
| `SimpleAdaptation` | Base for built-in adaptations; owns config files, recipes, advancements, FX, storage, and registration lifecycle. |
| `AbilityApiBridge` | Installs/uninstalls the internal ability funnel. External integrations register `AbilityUsePolicy` or `AbilityCostProvider` through Bukkit instead. |
| `Cooldowns` | UUID cooldown map created by `PlayerStateRegistry`; uses relocated time utilities and is Adapt-owned state. |
| `ItemCooldowns` | Shared item/material cooldown coordinator used by built-in abilities; its static group registry and player cooldown mutation are global. |
| `PlayerStateRegistry` | Tracks first-party per-player maps and owns the quit listener; `reset()` clears every registered map. |
| `VelocityBurstRuntime` | Global movement-burst scheduler and its `Client`, `Profile`, `BurstRequest`, `Feedback`, and `StartResult` nested types. Adapt owns startup, ticking, and shutdown. |
| `ChunkLoading` | Folia/Paper chunk-loading helper used by built-in adaptations; it does not define an external scheduling contract. |
| `SkillOwnerPulse` | Internal learner-index refresh pulse. |

`AdaptationRuntimeGuards` and `SkillRuntimeGuards` are package-private implementations, not API types and not runtime annotations. The public markers are `RunsWithoutLearnedAdaptation` and `ReceiveCancelledEvents`.

## See also

- `41 - API - Getting Started.md`
- `43 - API - Ability Use Policy.md`
- `44 - API - Ability Cost.md`
- `45 - API - Events.md`
- `10 - Skills Catalog.md`
