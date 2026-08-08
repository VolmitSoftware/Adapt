# API - Mutations

The supported mutation surface is the immutable catalogue and snapshot model under `art.arcane.adapt.api.mutation`. Adapt does not expose its live `MutationManager` as a service; command, GUI, config, persistence, claim, and combat-lock classes are public for first-party runtime use and are not third-party lifecycle contracts.

## Supported catalogue types

| Type | Supported use |
|------|---------------|
| `MutationType` | Fifteen mutation constants. Read `id()`, localized `displayName()`, `firstDomain()`, `secondDomain()`, `lineage()`, Bukkit `icon()`, descriptive strings, `pvpRelevant()`, `permission()`, or use `find(id)`. Do not call `keys()`: it returns relocated localization types. |
| `MutationDomain` | `BODY`, `HUNT`, `INDUSTRY`, `WILD`, `CRAFT`, and `ANOMALY`; `displayName()` is safe, while `keys()` returns relocated localization types. |
| `MutationLineage` | Normalized pair of domains; construct with `of(first, second)`. |
| `MutationCatalog` | Immutable default catalogue from `defaults()` with `mutations()`, `find(id)`, and domain-to-skill mappings. This is catalogue metadata, not the live player runtime. |
| `MutationPairingRules` | Pure compatibility evaluation. `evaluate(first, second)` returns a `PairResolution` containing compatibility, exclusive `MutationClaim` values, and the applied policy id. |
| `MutationProgression` | Immutable level thresholds with `isSlotUnlocked`, `unlockedSlotCount`, `isPerfect`, and `isBurdenActive`. |
| `MutationLimits` | Hard safety ceilings used by mutation profiles and runtime loops. |

`MutationType.permission()` returns `adapt.use.mutation.<mutation-id>` with the id's hyphens preserved, for example `adapt.use.mutation.bastion-spine`. Adapt registers these nodes with default `true` through `AdaptPermissionRegistrar`; they are children of `adapt.use.*`.

## Supported result and snapshot types

| Type | Contract |
|------|----------|
| `MutationSnapshot` | Immutable player view: slot ids, expressed/discovered types, perfect state, unlocked slots, cooperative opt-in, per-type `MutationState`, qualification reasons, and qualifying adaptations. `empty()` supplies an unavailable/default view. |
| `MutationState` | State reported for one type: use the enum value from `MutationSnapshot.state(type)`. |
| `MutationQualification` | Immutable result with overall, first-domain, and second-domain qualification flags, qualifying adaptation ids, and a reason; `rejected(reason)` creates a negative result. |
| `MutationSelectionResult` | Immutable result with success flag, message, and remaining cooldown, plus `success`, `rejected`, and `cooldown` factories. It describes manager operations but does not provide access to the live manager. |

PlaceholderAPI publishes a snapshot-backed mutation view without exposing the live manager; see `47 - API - PlaceholderAPI.md`. Code already executing inside Adapt may receive `MutationSnapshot` from first-party runtime paths, but external plugins should not construct a second manager to obtain one.

## Public runtime types that are not contracts

| Type | Runtime role and restriction |
|------|------------------------------|
| `MutationConfig` | Owns `mutations.toml`, profile subclasses, consent mode, reload, world checks, and live static config state. Read the operator docs instead of calling its lifecycle methods. |
| `MutationManager` | Owns live qualification, reconciliation, selection, bookshelf authorization, overrides, cooldowns, cleanup, reload, and shutdown. `new MutationManager(...)` creates disconnected state and must not be used by an integration. |
| `PlayerMutationData` | Mutable serialized fields inside `PlayerData`; direct writes bypass reconciliation and publication. |
| `MutationCombatLock` | Mutable dealer/receiver combat-tag clock owned by the live manager. |
| `MutationClaim` | Exclusive-effect claim enum consumed by pairing/runtime logic. |
| `MutationEventClaims` | Mutable per-event claim set used to prevent built-in mutation handlers from applying the same effect twice. |

No class under `content.mutation` or `content.mutation.runtime` is a supported API even when reflection can reach it.

## See also

- `04 - Commands & Permissions.md`
- `34 - Mutations Overview.md`
- `35 - Mutations Catalog.md`
- `47 - API - PlaceholderAPI.md`
