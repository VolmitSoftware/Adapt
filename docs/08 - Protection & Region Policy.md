# Protection & Region Policy

Adapt registers five WorldGuard region flags and optional claim/container protectors. A server without those
plugins runs without their bridges; install or remove a protection plugin only while the server is stopped so
Adapt can rebuild its protector registry during enable.

```
/rg flag <region> use-adaptations deny
/rg flag <region> adapt-xp deny
/rg flag <region> adapt-xp-multiplier 2.5
/rg flag <region> adapt-power-bonus 8
/rg flag <region> adapt-unlock-adaptations stealth-shadowmeld,axes-chop
```

| Flag | Type | Default | Effect |
|---|---|---|---|
| `use-adaptations` | State | unset | `deny` makes every adaptation inert inside the region |
| `adapt-xp` | State | `allow` | `deny` zeroes all location-carrying XP earned in the region |
| `adapt-xp-multiplier` | Double | unset (`1.0`) | Scales XP earned in the region, clamped to `[0, 1000]` |
| `adapt-power-bonus` | Integer | unset (`0`) | Extra max power while standing in the region, clamped to `[-4096, 4096]` |
| `adapt-unlock-adaptations` | Set of String | unset (empty) | Temporarily grants the named adaptations at level 1; `*` grants all |

---

## Requirements

- WorldGuard must be installed. Flags are registered in Adapt's `onLoad`, which is the only point
  WorldGuard accepts new flags.
- `protectorSupport.worldguard` in `adapt/adapt.toml` must be `true` (the default). With it off, the flags
  are still registered but every lookup short-circuits to the default policy — the flags become inert.
- Registration is defensive. A flag name already claimed by another plugin **with a matching type** is
  reused. A name claimed with a different type logs
  `WorldGuard flag <name> is owned by another plugin with a different type; Adapt will not use it.` and that
  one flag is disabled for the session; the rest keep working. Late registration falls back to injecting
  into WorldGuard's flag map by reflection, with a warning.

---

## `use-adaptations`

This flag is consulted through the normal `Protector` path (`WorldGuardProtector.checkRegion` and each verb),
so `deny` makes the adaptation resolve to level 0 for
that player and blocks each block, entity or container the adaptation touches. WorldGuard bypass applies.
See `46 - API - Protection.md`.

---

## `adapt-xp`

`deny` inside the region makes the XP award `0`, which aborts it before the skill line ever sees it.

Only **location-carrying** awards are affected. Adapt's XP entry points split three ways:

| Entry point | Carries a location | Region policy applies |
|---|---|---|
| `Skill.xp(player, …)` | yes, the player's own position | yes |
| `Skill.xp(player, at, …)` / `xpS(player, at, …)` | yes, the given position | yes |
| `Skill.xpSilent(player, xp)` | no | no |

The location used is whatever the awarding skill passed, which for block work is the block, not the player.
A null location resolves to the default policy and the award proceeds.

**WorldGuard bypass is honoured for this flag only.** A player whose session has bypass always earns XP,
regardless of `adapt-xp`. `adapt-xp-multiplier`, `adapt-power-bonus` and `adapt-unlock-adaptations` do not
consult bypass — an admin standing in a boosted region gets the boost.

---

## `adapt-xp-multiplier`

Multiplied into the award immediately after the novelty multiplier and before the skill line's own
multipliers. An absent flag is `1.0`. The value is clamped to `[0, 1000]`; a non-finite value becomes `1.0`.
`0` is equivalent to `adapt-xp deny` for that award.

WorldGuard resolves the value with its normal priority and inheritance rules, so overlapping regions do not
compound — the winning region's value is used verbatim.

Where this sits in the full award chain is documented in
`05 - Configuration Math.md`, under "The XP multiplier chain."

---

## `adapt-power-bonus`

Refreshed on the player's one-second tick from their current position and added straight into max power:

```
maxPower = max(0, (int)(masterLevel * powerPerLevel) + regionPowerBonus)
```

The value is clamped to `[-4096, 4096]`, and `maxPower` itself floors at `0`, so a large negative bonus
cannot produce a negative budget. It is a transient field — never serialized, cleared when the player's
runtime unregisters, and reset to `0` the moment a tick resolves a region that does not set it.

### What happens on exit

The bonus disappears on the next tick, and if the player is then over budget the same tick runs
`pruneAdaptationsForPowerBudget`: the lowest-level learned adaptations are demoted level by level (level 1
entries are removed outright) until used power fits the shrunken budget again. The prune fires only when the
bonus actually decreased and the player no longer fits — walking between regions with equal or higher
bonuses never touches learned adaptations, and no refunds are issued for pruned levels.

Region-granted adaptations (below) are exempt from the pruner and cost no power, so they never contribute to
this.

Treat a positive `adapt-power-bonus` as a lease: extra levels learned against it are reclaimed on exit.

---

## `adapt-unlock-adaptations`

A set of adaptation registry ids, or the single entry `*` for every registered adaptation. Values are
trimmed and lowercased; the union of every applicable region's set is used.

On the one-second tick, for each named adaptation the player currently has at level 0:

- the adaptation and its skill must both be enabled, and the skill line must exist
- it is granted at **level 1 through the normal `setAdaptation` path** — attributes apply, the learned index
  updates, the adaptation is genuinely active
- the learning transaction is bypassed entirely: no knowledge cost, no Vault charge, no refund receipts
- the `PlayerAdaptation` is stamped `regionGranted`

Grants are free in every sense: `getUsedPower` skips region-granted adaptations, and the power-budget pruner
skips them too. A wildcard region therefore cannot bankrupt a player's power budget.

### Lifecycle

| Event | Result |
|---|---|
| Tick inside a qualifying region | Missing grants are created; existing ones are left untouched |
| Tick where the flag no longer names it | Revoked via `setAdaptation(…, 0)`, which strips its attribute modifiers |
| Policy source faults and quarantines | Policy falls back to default, so the next tick revokes everything and zeroes the power bonus |
| Player quits | `AdaptPlayer.unregister` strips every region-granted adaptation and zeroes the power bonus before saving |
| Player data loads | `PlayerData.fromJson` sweeps every `regionGranted` entry out |

The marker lives in the adaptation's storage map, which *is* serialized. The quit strip and the load sweep
are two independent guards so a crash between them cannot leak a temporary grant into permanent data.

An adaptation the player learned normally is never marked `regionGranted`, is never revoked, and keeps
costing power — being named by the flag does nothing to it.

### Learning over a grant

Buying a region-granted adaptation makes it permanently yours, at the full price from zero.

`AdaptationLearningTransaction.learn` reads `paidLevel = 0` when the current level is region-granted, rather
than the actual level. Consequences:

- the knowledge and power cost is computed from level 0 to the target — the free level 1 is charged for
- learning to level 1 is a real purchase, not a no-op, because `normalizedTarget (1) > paidLevel (0)`
- on success the `regionGranted` marker is cleared, so the adaptation now consumes power and survives
  leaving the region, quitting, and reloading
- if the transaction throws, the marker is restored along with the previous level

Unlearning is symmetric: `paidLevel = 0` for a region-granted adaptation, so the refund floor is `0` and no
knowledge or currency is refunded for a level that was never paid for. A player-initiated unlearn of a
still-granted adaptation is allowed and simply re-grants on the next tick while they remain inside.

---

## Failure behaviour

Region lookups are fail-open and quarantine on the first fault.

- A missing player, a missing location, or no installed policy source resolves to the default policy: XP
  allowed, multiplier `1.0`, no power bonus, no unlocks.
- On Folia, a lookup for a player not owned by the calling region thread resolves to the default policy
  rather than crossing threads.
- Any `Throwable` out of the WorldGuard query quarantines the source permanently for the session. One
  warning is logged —
  `Region policy source WorldGuard failed; Adapt region flags are now inert: <type> - <message>` — with the
  stack trace, and every later lookup returns the default policy without calling WorldGuard again.

Quarantine is safe by construction: the default policy grants nothing, so the next tick revokes outstanding
grants and zeroes the power bonus rather than freezing them in place. Only a plugin reload reinstalls the
source.


## Other claim protectors

Adapt registers a protector whenever its plugin is enabled during Adapt enable. The matching
`protectorSupport.*` value decides whether that registered protector belongs to the default-active set.
Core-config hotload rebuilds that default-active snapshot, while installing or removing a protection plugin
still requires a restart. Adaptation-level `protectionOverrides` are read from the hotloaded core config at use time.

| Config key | Default | Soft depend | Protector name |
|------------|---------|-------------|----------------|
| `protectorSupport.worldguard` | `true` | WorldGuard | `WorldGuard` |
| `protectorSupport.factionsClaim` | `false` | Factions | `Factions` |
| `protectorSupport.chestProtect` | `true` | ChestProtect | `ChestProtect` |
| `protectorSupport.residence` | `true` | Residence | `Residence` |
| `protectorSupport.griefdefender` | `true` | GriefDefender | `GriefDefender` |
| `protectorSupport.griefprevention` | `true` | GriefPrevention | `GriefPrevention` |
| `protectorSupport.lockettePro` | `true` | LockettePro | `LockettePro` |

Normal adaptation checks and Mutation placement/occupancy checks use the default-active set. The configured
activator block also checks every default-active protector before opening; per-adaptation overrides do not
apply to that GUI interaction because it has no adaptation context.

Per-adaptation overrides can add any registered protector or remove a default protector:

```toml
[protectionOverrides.rift-blink]
WorldGuard = true
GriefPrevention = false
```

The outer key is an exact adaptation registry ID. Inner keys are the exact protector names in the table;
`true` adds the protector and `false` removes it for that adaptation. An override cannot activate a protector
whose plugin was absent when Adapt enabled.

Protectors implement `art.arcane.adapt.api.protection.Protector` and are held by `ProtectorRegistry`. Region
policy aggregation for XP, multipliers, power bonus, and temporary unlocks is separate and uses
`RegionPolicy` / `RegionPolicySource`.

## See also

- `09 - Integrations.md`
- `46 - API - Protection.md`
- `01 - Installation & Configuration.md`
