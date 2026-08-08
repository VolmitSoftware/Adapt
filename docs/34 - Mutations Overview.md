# Mutations Overview

Mutations are an experimental dual-slot trait system separate from skill adaptations. The feature is **off by default** (`enabled = false` in `plugins/Adapt/adapt/mutations.toml`). When enabled, players discover types, equip up to two compatible mutations, respect switch cooldowns and combat lock, and may reach perfect adaptation that softens drawbacks.

## Enablement

| Requirement | Detail |
|-------------|--------|
| Config | `adapt/mutations.toml` → `enabled = true` |
| Player perm | `adapt.mutations` plus `adapt.use.mutation.<id>` |
| Admin | `adapt.mutations.admin` for equip/discover/reset/perfect-test/reload |
| Bookshelf gate | Non-admin slot changes require a valid bookshelf authorization token (distance and TTL from config) |

## Core config defaults (`MutationConfig`)

| Key | Default | Role |
|-----|---------|------|
| `enabled` | `false` | Master switch |
| `slotOneUnlockLevel` | `25` | Master level for slot 1 |
| `slotTwoUnlockLevel` | `50` | Master level for slot 2 |
| `perfectAdaptationLevel` | `200` | Master level for perfect adaptation |
| `perfectAdaptationEnabled` | `true` | Allow level-based perfect |
| `minimumAdaptationLevel` | `1` | Min learned adaptation level per domain skill |
| `switchCooldownMillis` | `600000` | Per-slot switch cooldown (10 min) |
| `combatLockMillis` | `10000` | Combat blocks slot changes |
| `switchingEnabled` | `true` | Players may switch |
| `permanentSelection` | `false` | First equip permanent until admin |
| `pvpEnabled` | `true` | Global mutation PvP |
| `cooperativeEffectsEnabled` | `true` | Group effects |
| `cooperativeConsentMode` | `EXPLICIT` | EXPLICIT / PARTY / FRIEND / DISABLED |
| `bookshelfTokenMillis` | `60000` | Bookshelf auth window |
| `bookshelfMaximumDistance` | `8` | Max distance while editing |
| `particlesEnabled` | `true` | Global particle switch |
| `soundsEnabled` | `true` | Global sound switch |
| `worldBlacklist` | `[]` | Global world-key deny list |
| `domainMembership` | Domain map below | Skills assigned to each domain |

Hotload watches `mutations.toml`. `/adapt mutations reload` reloads and reconciles online players.

Normalization enforces `slotOneUnlockLevel >= 0`, `slotTwoUnlockLevel >= slotOneUnlockLevel`, `perfectAdaptationLevel >= slotTwoUnlockLevel`, and `minimumAdaptationLevel >= 1`. Switch and combat durations are clamped to 0–31,536,000,000 ms, the bookshelf token to 1,000–300,000 ms, and bookshelf distance to 2–32 blocks. World lists retain at most 256 normalized world keys, and each domain list retains at most 64 unique lowercase skill ids.

### Per-type profile defaults

Every mutation has these keys under its camel-case TOML section, such as `galeLung` or `resonantFormula`. Type-specific values and clamps are listed with each catalog entry in `35 - Mutations Catalog.md`.

| Key | Default | Normalization |
|-----|---------|---------------|
| `enabled` | `true` | Boolean |
| `pvpEnabled` | `true` | Boolean; also requires global `pvpEnabled` |
| `particlesEnabled` | `true` | Boolean; also requires global `particlesEnabled` |
| `soundsEnabled` | `true` | Boolean; also requires global `soundsEnabled` |
| `worldBlacklist` | `[]` | At most 256 normalized world keys |
| `conflicts` | `[]` | At most 15 unique lowercase mutation ids |

### Cooperative consent modes

Recipients must opt in for every cooperative mode. `EXPLICIT` accepts an opted-in recipient; `PARTY` also requires the recipient to match the initiating player's party. `FRIEND` currently has no friendship provider and therefore rejects every recipient, making it behaviorally equivalent to `DISABLED` until an integration supplies that relationship.

## Domains

Each type has two domains. Qualification requires at least one enabled learned adaptation (at `minimumAdaptationLevel`) in a skill mapped to each domain, plus use permissions.

| Domain | Default skill membership |
|--------|--------------------------|
| BODY | agility, blocking, unarmed, kinetics |
| HUNT | swords, ranged, hunter, stealth |
| INDUSTRY | architect, axes, excavation, pickaxe |
| WILD | herbalism, taming, seaborne |
| CRAFT | crafting, brewing, enchanting, discovery |
| ANOMALY | nether, rift, chronos, tragoul |

Membership is overridable via `domainMembership` in `mutations.toml`.

## Slots and states

- Two expression slots (`slotOneId`, `slotTwoId`).
- Unlock by master level or admin `slot-override`.
- States: `LOCKED`, `AVAILABLE`, `EXPRESSED`, `DORMANT`, `DISABLED`, `RESTRICTED`, `CONFLICT`.
- Conflicts: config conflict lists and same-id in both slots.
- Perfect adaptation: master level threshold or admin `perfect-test`; softens burden text/runtime.

## Combat lock and switching

Damage tags players for `combatLockMillis`. Non-admin select/clear fails while locked. Switch cooldown applies after non-admin changes. Admin equip/clear skip most gates but still reject duplicates and configured conflicts.

## Pair resolution policies

Different mutation ids are compatible unless a profile's `conflicts` list rejects the pair. Seven pairs also claim shared runtime resources and use an explicit resolution policy:

| Pair | Exclusive claims | Resolution |
|------|------------------|------------|
| Umbral Echo + Resonant Formula | utility echo | Only the first legal utility echo is scheduled |
| Temperbound + Masterwork Bond | item preservation | Only one preservation result applies to a durability event |
| Packmind + Mycelial Nerve | cooperative link | Each recipient consents independently; propagation does not chain |
| Living Lattice + Gravebloom | world state | Each temporary structure remains separately owned and bounded |
| Gale Lung + Bastion Spine | movement, posture | The most recent deliberate movement or posture action owns the result |
| Deepblood + Gravebloom | recovery | Recovery evaluates once in deterministic slot order |
| Paradox Scar + Umbral Echo | movement, utility echo | Movement resolves before control echoes |

## GUI and commands

- `/adapt mutations menu` → `MutationGui` (pages of cards, cooperative toggle, detail views).
- Full command table: `04 - Commands & Permissions.md`.
- PlaceholderAPI: `47 - API - PlaceholderAPI.md`.

## Catalog

All fifteen types: `35 - Mutations Catalog.md`.

## Runtime support classes

These internal classes implement mutation behavior; they are not public API.

| Class | Role |
|-------|------|
| `MutationRuntimeRouter` | Registers mutation event handlers and routes them to the specialized runtimes |
| `MutationRuntimeAccess` | Provides config, player data, consent, PvP, FX, and eligibility access |
| `MutationRuntimeStore` | Holds bounded transient mutation state |
| `MutationCombatRuntime` | Implements combat mutations and combat-linked state |
| `MutationMovementRuntime` | Implements movement, posture, and return-point behavior |
| `MutationEquipmentRuntime` | Implements linked armor and bound-tool behavior |
| `MutationEffectRuntime` | Implements cleanse, copied-effect, and cooperative-effect behavior |
| `MutationFormulaRuntime` | Implements Resonant Formula preparation and echoes |
| `MutationWorldRuntime` | Implements world-state mutations, natural-block checks, and lifecycle cleanup |
| `MutationBlockProvenance` | Distinguishes natural blocks from player-placed blocks |
| `MutationEntityResolver` | Resolves players, owners, pets, projectiles, and credited attackers |
| `MutationProtectionAccess` | Applies Adapt's protection policy to mutation world changes |
| `MutationItemIdentity` | Marks and resolves mutation-owned or bound items |
| `MutationRuntimePolicy` | Centralizes bounded values and shared runtime policy decisions |
| `MutationUtilityTag` | Identifies utility-effect categories used by pairing policy |
| `MutationWeaponFamily` | Classifies held weapons and tools for combo behavior |

Manager and public types are documented in `48 - API - Mutations.md`.

## See also

- `35 - Mutations Catalog.md`
- `04 - Commands & Permissions.md`
- `48 - API - Mutations.md`
