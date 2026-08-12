# API - Mutations

The mutation surface a third-party plugin can rely on is read-only: an immutable catalogue describing what mutations exist, and immutable snapshot and result types describing what a player currently has. Everything lives under `art.arcane.adapt.api.mutation`.

Adapt does not expose its live `MutationManager` as a Bukkit service, and there is no supported way to reach one. The manager, the config, the combat lock and the persisted player record are public Java classes because Adapt's own commands, GUI and runtime need them across packages, not because they are contracts. Constructing your own copy gives you disconnected state that Adapt never reconciles or publishes.

If what you want is "show me this player's mutations", read them through PlaceholderAPI instead. That path is snapshot-backed, thread-safe, and documented in [47 - API - PlaceholderAPI.md](<47 - API - PlaceholderAPI.md>).

## What the catalogue gives you

`MutationType` is the enum of the fifteen mutations. Each constant carries its id, localized display name, its two domains and the normalized `MutationLineage` pair, a Bukkit `Material` icon, five descriptive strings, whether it matters in PvP, and its permission node. `MutationType.find(id)` looks one up by id and accepts underscores in place of hyphens.

`MutationCatalog.defaults()` is the immutable catalogue built from that enum. It adds the domain-to-skill mapping that decides which skills qualify a player for which domain.

`MutationPairingRules.evaluate(first, second)` is pure. Hand it two types and it tells you whether they can share a loadout, which exclusive `MutationClaim` values that pair contends for, and a plain-language policy string. Nothing about a player is involved, so it is safe to call from anywhere.

`MutationProgression` answers level questions: which slots are unlocked at a given master level, how many, whether the player is at perfect adaptation, and whether burdens are still active. `MutationLimits` holds the hard safety ceilings the runtime and the config profiles clamp against.

Do not call `MutationType.keys()` or `MutationDomain.keys()`. They exist to feed Adapt's localization pass and return `TextKey` instances from VolmLib, which is relocated inside Adapt's shaded jar, so the type you compile against is not the type you get at runtime.

## What a snapshot gives you

`MutationSnapshot` is the immutable per-player view: the two slot ids, the expressed and discovered types, perfect state, unlocked slot flags, the cooperative opt-in, a `MutationState` per type with a reason string, and per-type qualification data. `MutationSnapshot.empty()` builds the "runtime unavailable" view where every type is `LOCKED`.

`MutationQualification` and `MutationSelectionResult` are the immutable result records the manager produces. Qualification reports overall and per-domain qualification with the adaptation ids that earned it. Selection reports success, a message, and any remaining cooldown. Both are useful to read; neither gives you a way to reach the manager that produced it.

## Permissions

`MutationType.permission()` returns `adapt.use.mutation.<mutation-id>` with the id's hyphens intact, for example `adapt.use.mutation.bastion-spine`. Adapt registers all fifteen at startup with default `true` and lists them as children of `adapt.use.*`.

---

## Reference

### Catalogue types

| Type | Supported use |
|------|---------------|
| `MutationType` | Fifteen constants. `id()`, localized `displayName()`, `firstDomain()`, `secondDomain()`, `lineage()`, Bukkit `icon()`, the descriptive strings `benefit()` / `burden()` / `perfectResult()` / `tell()` / `control()`, `pvpRelevant()`, `permission()`, static `find(id)`. Do not call `keys()` |
| `MutationDomain` | `BODY`, `HUNT`, `INDUSTRY`, `WILD`, `CRAFT`, `ANOMALY`. `displayName()` is safe; do not call `keys()` |
| `MutationLineage` | `record(first, second)`. Build with `of(first, second)`. Rejects two identical domains and reorders the pair into enum order |
| `MutationCatalog` | `defaults()` returns the shared immutable catalogue. `mutations()`, `find(id)`, `domainSkills(domain)`, `domainSkills()` |
| `MutationPairingRules` | `evaluate(first, second)` returns a `PairResolution(compatible, exclusiveClaims, policy)`. Pure, no player state |
| `MutationProgression` | `record(slotOneLevel, slotTwoLevel, perfectLevel, perfectEnabled)` with `isSlotUnlocked(level, slot)`, `unlockedSlotCount(level)`, `isPerfect(level)`, `isBurdenActive(level)` |
| `MutationLimits` | Public `static final` ceilings such as `MAX_DURATION_MILLIS`, `MAX_DELAY_TICKS`, `QUALIFICATION_CANDIDATES_PER_DOMAIN`, plus per-mutation caps |

### Result and snapshot types

| Type | Contract |
|------|----------|
| `MutationSnapshot` | Immutable player view: slot ids, expressed and discovered types, perfect state, unlocked slots, cooperative opt-in, per-type `state(type)` and `reason(type)`, and `qualified(type)` / `qualifyingAdaptations(type)` / `qualificationReason(type)`. `state(type)` returns `RESTRICTED` for an unmapped type. `empty()` supplies the unavailable view |
| `MutationState` | `LOCKED`, `AVAILABLE`, `EXPRESSED`, `DORMANT`, `DISABLED`, `RESTRICTED`, `CONFLICT`. May gain constants |
| `MutationQualification` | Immutable record: `qualified`, `firstDomainQualified`, `secondDomainQualified`, `qualifyingAdaptations`, `reason`. `rejected(reason)` builds a negative result |
| `MutationSelectionResult` | Immutable record: `success`, `message`, `cooldownRemainingMillis`. Factories `success(message)`, `rejected(message)`, `cooldown(message, remainingMillis)` |
| `MutationClaim` | `DAMAGE`, `COOLDOWN_RESET`, `DEATH_PREVENTION`, `ITEM_PRESERVATION`, `UTILITY_ECHO`, `REWARD`, `MOVEMENT`, `POSTURE`, `WORLD_STATE`, `COOPERATIVE_LINK`, `RECOVERY` |

### Runtime types that are not contracts

| Type | Runtime role and restriction |
|------|------------------------------|
| `MutationConfig` | Owns `plugins/Adapt/adapt/mutations.toml`, the per-mutation profile subclasses, consent mode, reload and world checks, and live static config state. Read [34 - Mutations Overview.md](<34 - Mutations Overview.md>) instead of calling its lifecycle methods |
| `MutationManager` | Owns qualification, reconciliation, selection, bookshelf authorization, overrides, cooldowns, cleanup, reload and shutdown. `new MutationManager(config)` creates disconnected state |
| `PlayerMutationData` | Mutable serialized fields inside `PlayerData`. Direct writes bypass reconciliation and publication |
| `MutationCombatLock` | Mutable dealer and receiver combat-tag clock owned by the live manager |
| `MutationEventClaims` | Mutable per-event claim set that stops two built-in mutation handlers applying the same effect twice |

No class under `content.mutation` or `content.mutation.runtime` is a supported API even when reflection can reach it.

## See also

- [04 - Commands & Permissions.md](<04 - Commands & Permissions.md>)
- [34 - Mutations Overview.md](<34 - Mutations Overview.md>)
- [35 - Mutations Catalog.md](<35 - Mutations Catalog.md>)
- [47 - API - PlaceholderAPI.md](<47 - API - PlaceholderAPI.md>)
