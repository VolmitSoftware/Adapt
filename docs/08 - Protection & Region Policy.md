# Protection & Region Policy

Adapt gives claim and region plugins two ways to control it. Registered protectors answer yes or no whenever an adaptation is about to break a block, hit something, or open a container. WorldGuard gets a second channel on top of that: five region flags that turn adaptations off, change XP, hand out extra power, and temporarily lend adaptations to whoever is standing inside.

The flags are the interesting part. `use-adaptations deny` makes a region an Adapt-free zone. `adapt-xp-multiplier` turns a region into a training ground. `adapt-power-bonus` lets people hold more adaptations than their level normally affords, and takes them back when they leave. `adapt-unlock-adaptations` grants named adaptations at level 1 for free while a player is inside, and revokes them on the way out.

Region lookups fail open. A missing plugin, a missing location, or a thrown exception all resolve to the default policy: XP allowed, multiplier 1.0, no power bonus, no unlocks. The default grants nothing, so a fault revokes outstanding grants rather than freezing them in place.

Adapt also asks Bukkit. Indirect container and item actions dispatch the normal interaction, break, place, or pickup events before Adapt changes anything, which lets an event-driven protection plugin deny the same action without implementing Adapt's API.

## Requirements

WorldGuard has to be installed for any of the flags. Adapt registers them in `onLoad`, which is the only point WorldGuard accepts new flags, so this cannot be done later by a reload.

`protectorSupport.worldguard` in `adapt/adapt.toml` must be `true`, which it is by default. Turn it off and the region policy source short-circuits to the default policy on every lookup, so `adapt-xp`, `adapt-xp-multiplier`, `adapt-power-bonus` and `adapt-unlock-adaptations` all go inert. The `use-adaptations` flag goes through the protector instead, and turning the setting off drops the WorldGuard protector out of the default-active set; a per-adaptation `WorldGuard = true` override still re-adds it, and that protector keeps reading the flag.

Registration is defensive. A flag name already claimed by another plugin with a matching type is reused. A name claimed with a different type disables that one flag for the session and leaves the rest working. If Adapt registers too late, it falls back to injecting the flag into WorldGuard's flag map by reflection, with a warning either way.

## Turning adaptations off in a region

```
/rg flag <region> use-adaptations deny
```

This flag runs through the normal protector path, so a `deny` makes every adaptation resolve to level 0 for that player and blocks each block, entity, and container the adaptation would touch. WorldGuard bypass applies. See `46 - API - Protection.md`.

## Controlling XP

```
/rg flag <region> adapt-xp deny
/rg flag <region> adapt-xp-multiplier 2.5
```

`adapt-xp deny` zeroes the award before the skill line ever sees it. Only awards that carry a location are affected, and the location used is whatever the awarding skill passed, which for block work is the block, not the player. A null location resolves to the default policy and the award goes through.

WorldGuard bypass is honoured for `adapt-xp` and nothing else. A player whose session has bypass always earns XP. The multiplier, power bonus and unlock flags do not consult bypass, so an admin standing in a boosted region gets the boost.

`adapt-xp-multiplier` scales the award. It lands immediately after the novelty multiplier and before the skill line's own multipliers, so it stacks with them rather than replacing them. An absent flag is `1.0`, and `0` does the same thing as `adapt-xp deny` for that award. WorldGuard resolves the value with its normal priority and inheritance rules, so overlapping regions do not compound; the winning region's value is used as written.

Where this sits in the full award chain is in `05 - Configuration Math.md`, under "The XP multiplier chain."

## Lending extra power

```
/rg flag <region> adapt-power-bonus 8
```

The bonus is read on the player's one-second tick from their current position and added straight into max power. It is a transient field: never serialized, cleared when the player's runtime unregisters, and reset to `0` the moment a tick resolves a region that does not set it.

Treat a positive bonus as a lease. The bonus disappears on the next tick after the player leaves, and if they are then over budget the same tick prunes them back into it: the lowest-level learned adaptations are demoted level by level, level 1 entries are removed outright, until used power fits again. No refunds are issued for pruned levels.

The prune only fires when the bonus actually went down and the player no longer fits. Walking between regions with equal or higher bonuses never touches learned adaptations. Region-granted adaptations are exempt from the pruner and cost no power, so they never contribute to this. A player in `/adapt debug mode` is skipped by the pruner entirely.

## Lending adaptations

```
/rg flag <region> adapt-unlock-adaptations stealth-shadowmeld,axe-chop
```

Give it a set of adaptation registry ids, or the single entry `*` for every registered adaptation. Values are trimmed and lowercased, and the union of every applicable region's set is used.

On the one-second tick, for each named adaptation the player currently has at level 0, Adapt checks that the adaptation and its skill are both enabled and that the skill line exists, then grants it at level 1 through the normal `setAdaptation` path. Attributes apply, the learned index updates, the adaptation is genuinely active. The learning transaction is bypassed entirely: no knowledge cost, no Vault charge, no refund receipts. The `PlayerAdaptation` is stamped `regionGranted`.

Grants are free in every sense. Used power skips region-granted adaptations and so does the power-budget pruner, so a wildcard region cannot bankrupt a player's power budget.

An adaptation the player learned normally is never marked `regionGranted`, is never revoked, and keeps costing power. Being named by the flag does nothing to it.

### Buying a granted adaptation

Buying a region-granted adaptation makes it permanently yours, at the full price from zero.

The learning transaction reads `paidLevel = 0` when the current level is region-granted, rather than the actual level. So the knowledge and power cost is computed from level 0 to the target, meaning the free level 1 is charged for, and learning to level 1 is a real purchase rather than a no-op. On success the `regionGranted` marker is cleared, and the adaptation now consumes power and survives leaving the region, quitting, and reloading. If the transaction throws, the marker is restored along with the previous level.

Unlearning is symmetric. `paidLevel = 0` for a region-granted adaptation, so the refund floor is `0` and no knowledge or currency comes back for a level that was never paid for. A player-initiated unlearn of a still-granted adaptation is allowed, and it re-grants on the next tick while they remain inside.

## Choosing which protectors apply

Adapt registers a protector for every supported plugin that is enabled while Adapt enables. The matching `protectorSupport.*` value then decides whether that registered protector belongs to the default-active set. A core-config hotload rebuilds that snapshot, but installing or removing a protection plugin still requires a restart so Bukkit load order and Adapt's registries can be rebuilt.

Normal adaptation checks and Mutation placement and occupancy checks use the default-active set. The configured activator block also checks every default-active protector before opening, and per-adaptation overrides do not apply to that GUI interaction because it has no adaptation context.

To change the set for one adaptation:

1. Add a table under `protectionOverrides` keyed by the exact adaptation registry id.
2. Inside it, set a protector name to `true` to add it or `false` to remove it.
3. Save. Overrides are read from the hotloaded core config at use time.

```toml
[protectionOverrides.rift-blink]
WorldGuard = true
GriefPrevention = false
```

An override cannot activate a protector whose plugin was absent when Adapt enabled; an unknown name logs `Could not find protector <name> for adaptation <id>. Skipping...` and is ignored.

## Bukkit action-event checks

Adapt asks Bukkit listeners for permission before it performs container and item work that vanilla would normally route through a player event. These marked checks supplement the active protector set rather than replacing it, so WorldGuard's Adapt-specific flag and per-adaptation protector overrides still apply.

Adapt's own gameplay handlers ignore marked interaction, break, and place authorization checks to prevent recursive activation. Pickup handlers and other plugins receive the ordinary Bukkit events.

A marked interaction asks listeners whether the action is allowed; it does not execute vanilla block use. Remote inventories therefore do not simulate reach, obstructed-chest, spectator, or vanilla `Lockable` key behavior. Use a supported protection plugin when those rules must govern remote access.

## Failure behaviour

The first `Throwable` out of the WorldGuard query quarantines the source permanently for the session. One warning is logged with the stack trace, and every later lookup returns the default policy without calling WorldGuard again. Only a plugin reload reinstalls the source.

Quarantine is safe by construction. The default policy grants nothing, so the next tick revokes outstanding grants and zeroes the power bonus.

## Reference

### Region flags

| Flag | Type | Default | Effect |
|---|---|---|---|
| `use-adaptations` | State | unset | `deny` makes every adaptation inert inside the region |
| `adapt-xp` | State | `allow` | `deny` zeroes all location-carrying XP earned in the region |
| `adapt-xp-multiplier` | Double | unset (`1.0`) | Scales XP earned in the region, clamped to `[0, 1000]` |
| `adapt-power-bonus` | Integer | unset (`0`) | Extra max power while standing in the region, clamped to `[-4096, 4096]` |
| `adapt-unlock-adaptations` | Set of String | unset (empty) | Temporarily grants the named adaptations at level 1; `*` grants all |

A non-finite multiplier resolves to `1.0`. Max power itself floors at `0`, so a large negative power bonus cannot produce a negative budget.

```
maxPower = max(0, (int)(masterLevel * powerPerLevel) + regionPowerBonus)
```

### XP entry points and region policy

| Entry point | Carries a location | Region policy applies |
|---|---|---|
| `Skill.xp(player, ...)` | yes, the player's own position | yes |
| `Skill.xp(player, at, ...)` / `xpS(player, at, ...)` | yes, the given position | yes |
| `Skill.xpSilent(player, xp)` | no | no |
| `Skill.xp(at, xp, rad, duration)` spatial pulses | the pulse has one, the award does not use it | no |

### Region-grant lifecycle

| Event | Result |
|---|---|
| Tick inside a qualifying region | Missing grants are created; existing ones are left untouched |
| Tick where the flag no longer names it | Revoked via `setAdaptation(..., 0)`, which strips its attribute modifiers |
| Policy source faults and quarantines | Policy falls back to default, so the next tick revokes everything and zeroes the power bonus |
| Player quits | `AdaptPlayer.unregister` strips every region-granted adaptation and zeroes the power bonus before saving |
| Player data loads | `PlayerData.fromJson` sweeps every `regionGranted` entry out |

The marker lives in the adaptation's storage map, which is serialized. The quit strip and the load sweep are two independent guards, so a crash between them cannot leak a temporary grant into permanent data.

### Default policy triggers

| Condition | Result |
|---|---|
| No installed policy source | Default policy |
| Missing player or missing location | Default policy |
| `protectorSupport.worldguard = false` | Default policy |
| Folia lookup for a player not owned by the calling region thread | Default policy |
| Source quarantined | Default policy, WorldGuard not called again |

### Console messages

| Message | Meaning |
|---|---|
| `WorldGuard flag <name> is owned by another plugin with a different type; Adapt will not use it.` | That one flag is disabled for the session |
| `WorldGuard flag <name> was not registered in time. Injecting it now...` | Falling back to reflection injection |
| `Failed to inject WorldGuard flag <name>: <type> - <message>` | Injection failed, flag unavailable |
| `Region policy source WorldGuard failed; Adapt region flags are now inert: <type> - <message>` | Source quarantined for the session |
| `Could not find protector <name> for adaptation <id>. Skipping...` | `protectionOverrides` named a protector that is not registered |

### Protector settings

| Config key | Default | Soft depend | Protector name |
|------------|---------|-------------|----------------|
| `protectorSupport.worldguard` | `true` | WorldGuard | `WorldGuard` |
| `protectorSupport.factionsClaim` | `false` | Factions | `Factions` |
| `protectorSupport.chestProtect` | `true` | ChestProtect | `ChestProtect` |
| `protectorSupport.residence` | `true` | Residence | `Residence` |
| `protectorSupport.griefdefender` | `true` | GriefDefender | `GriefDefender` |
| `protectorSupport.griefprevention` | `true` | GriefPrevention | `GriefPrevention` |
| `protectorSupport.lockettePro` | `true` | LockettePro | `LockettePro` |

Protectors implement `art.arcane.adapt.api.protection.Protector` and are held by `ProtectorRegistry`. The interface has seven checks, all defaulting to allow: `checkRegion`, `canBlockBreak`, `canBlockPlace`, `canPVP`, `canPVE`, `canInteract`, `canAccessChest`. Region policy aggregation for XP, multipliers, power bonus, and temporary unlocks is separate and uses `RegionPolicy` and `RegionPolicySource`.

The WorldGuard protector maps its checks onto stock WorldGuard flags, and every one of them also requires `use-adaptations` to not be `deny`:

| Adapt check | WorldGuard flag |
|---|---|
| `checkRegion` | `use-adaptations` |
| `canBlockBreak` | `use-adaptations` + `BLOCK_BREAK` |
| `canBlockPlace` | `use-adaptations` + `BLOCK_PLACE` |
| `canPVP` | `use-adaptations` + `PVP` |
| `canPVE` | `use-adaptations` + `DAMAGE_ANIMALS` |
| `canInteract` | `use-adaptations` + `INTERACT` |
| `canAccessChest` | `use-adaptations` + `CHEST_ACCESS` |

### Marked Bukkit checks by feature

| Feature | Events dispatched |
|---|---|
| Rift Access and deferred Rift Conduit actions | Marked `RIGHT_CLICK_BLOCK` for every physical container block, including both halves of a double chest; refused when a listener denies block use |
| Initial Rift Conduit gesture | Uses its real clicked-block event and probes any other physical half |
| Indirect item-entity transfers | `PlayerAttemptPickupItemEvent` first on Paper; when capacity allows, `PlayerPickupItemEvent` then `EntityPickupItemEvent` on both Paper and Spigot |
| Veinminer | Waits for the original block to finish breaking, then uses the player's native break action for every sibling |
| Time In A Bottle | Plans a sapling tree without changing the world, authorizes the complete footprint, then fires `StructureGrowEvent` before generation |
| Chronos crop acceleration, Compost Cascade, Builders Wand, Magic Foundation, Seed Sower, Coral Gardener | Marked `BlockBreakEvent` or `BlockPlaceEvent` authorization checks as applicable, before committing |
| Deconstruction | The normal pickup-event sequence before it replaces a dropped item |

Cancellation stops the transfer and leaves the entity or block drop in its normal world path.

### Folia constraints

| Situation | Behaviour |
|---|---|
| Marked player event | Dispatched only while the player and every target block or item share the current owning region |
| Indirect actions without established ownership | Fail closed, so deferred Rift Conduit binds and flows do not cross Folia regions or worlds |
| Air-click ray-target variants | Disabled, because resolving a ray across a region boundary is not owner-thread safe; direct block-click variants remain available |
| Nearby-entity adaptations | Run their query only when the complete horizontal search footprint belongs to the current region |
| Region policy lookup off the owning thread | Resolves to the default policy rather than crossing threads |

## See also

- `09 - Integrations.md`
- `46 - API - Protection.md`
- `05 - Configuration Math.md`
- `01 - Installation & Configuration.md`
