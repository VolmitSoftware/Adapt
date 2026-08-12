# Configuration Math

This page explains how Adapt turns an action into XP, XP into levels, and levels into the power budget that caps how many adaptations a player can hold at once. Every config path below lives in `plugins/Adapt/adapt/adapt.toml`.

The short version of an XP award: a skill hands Adapt a number, then a chain of multipliers works on it before it reaches the skill line. Two of them exist to make automation unprofitable, one comes from region policy, and two are boost brackets. Order matters, because a region that denies XP kills the award outright and two of the stages clamp.

Levels come from one curve. The same curve is used for every skill line and for master level, so changing `xpCurve` moves progression and ability power together. Master XP is never awarded directly; it is granted only when a skill line crosses a level.

The defaults are tuned so a player doing normal varied work barely notices the anti-farm layers, while someone standing in one spot repeating one action bottoms out near one percent payout. If those defaults are too harsh for your server, raise the floors rather than switching the systems off.

## How an XP award is calculated

### Stage 1, the location gate

`SkillRuntimeGuards.grantXp` handles anything awarded with a `Location`. `Skill.xp(player, ...)` and `Skill.xpS(player, ...)` both go through it; `Skill.xp(player, xp)` counts too, because it fills in the player's own location. `Skill.xpSilent(player, xp)` routes through `grantXpSilent` and skips this entire stage.

First the award has to be legitimate at all: the skill must be enabled, the recipient must be a real `CraftPlayer`, and the number must be finite and greater than zero.

Then two multipliers apply, in this order:

1. Novelty. `xp *= XpNovelty.noveltyMultiplier(player, location, rewardKey)`.
2. Region policy. `xp = RegionPolicyService.adjustXp(...)`, which is `xp * policy.xpMultiplier()`, or `0` if the region denies XP. See `08 - Protection & Region Policy.md`.

The finite-and-positive check runs again on the result. A region that zeroes the award ends it right here and nothing downstream runs.

### Stage 2, the skill switch

`XP.xp` and `XP.xpSilent` call `PlayerData.resetMonotonyForOtherSkills(skill)` before handing the award on. If the player has switched to a different skill since the last award, every other line's accumulated staleness pressure is scaled down by `farmPrevention.crossSkillRecoveryFactor`, and the line that was carrying the most pressure may be tagged as the Inspired skill.

Inspired is cosmetic. It drives one action-bar popup, gated by `xpIntegrity.inspiredPopupEnabled` (off by default) and rate-limited by `inspiredCooldownMillis`. It grants no XP of its own. The real reward for switching skills is the pressure relief itself.

### Stage 3, the line

`PlayerSkillLine.giveXP` does three things:

1. `freshness -= 0.012 + (xp * 0.00025)`, using the stage-1 result, before any stage-3 multiplication.
2. `monotonyMultiplier = computeStalenessMultiplier(xp, rewardKey, now)`, the farm-prevention term described below.
3. `xp = lineMultiplier * monotonyMultiplier * xp`.

`lineMultiplier` is not computed here. It is a snapshot refreshed once per second by `PlayerSkillLine.updateMultiplier`, so it can lag a permission change or a fresh boost by up to one player update, about a second.

### The two multiplier snapshots

Player updates run on a one-second cadence, staggered per player UUID. `PlayerData.update` computes the player-wide bracket first, then walks the skill lines, so both snapshots come from the same tick.

Per player, `PlayerData.computeXpMultiplier`:

```
m  = 1 + sum(active player boosts) + sum(active global boosts)
m *= permissionMultiplier
playerMultiplier = clamp(m, 0.01, 1000)     // <= 0 becomes 0.01, > 1000 becomes 1000
```

Per line, `PlayerSkillLine.updateMultiplier`:

```
m = rfreshness + sum(active boosts on this line)
m = clamp(m, 0.01, 1000)
lineMultiplier = m * playerMultiplier
```

Boosts add together inside their own bracket and multiply across brackets. Expired entries are dropped while the sums are taken.

The two brackets have different sources. `/adapt boost` and `/adapt global-boost` both land in the player bracket. Line boosts come from the API, `XP.boostXP(player, skill, percent, ms)`, and from `AdaptPlayer.boostXPToRandom` and `boostXPToRecents`.

### Permission multipliers

`PlayerData.resolvePermissionMultiplier` reads `[permissionXpMultipliers]`. It returns `1.0` when the player is null, the section is disabled, or the table is empty. Entries with a blank node, a null value, or a value at or below zero are skipped, as are nodes the player does not hold.

With `stack = false` the single highest matched value wins, so holding a 1.5 node and a 2.0 node yields `2.0`. With `stack = true` every matched value is multiplied together, so the same pair yields `3.0`. No match yields `1.0` either way.

The result feeds the player bracket, so the `[0.01, 1000]` clamp still applies. Values below `1.0` work as rank penalties.

### The whole product

```
final = novelty
      * regionXpMultiplier
      * monotony
      * clamp(rfreshness + lineBoosts, 0.01, 1000)
      * clamp((1 + playerBoosts + globalBoosts) * permissionMultiplier, 0.01, 1000)
```

with `final = 0` if the region denies XP.

### Payout pooling

With `xpIntegrity.pooledPayoutEnabled` on, which is the default, the multiplied award goes into a pool instead of straight onto the line. The one-second tick flushes that pool once it is either older than `pooledWindowMillis` or has been idle longer than `pooledIdleFlushMillis`, whichever happens first. Flushing adds the whole pool at once and emits a single action-bar figure instead of a stream of small ones.

## Freshness

`freshness` is the per-line diminishing-returns term. `rfreshness` is the smoothed value the multiplier snapshot actually reads.

Once per second, before the multiplier snapshot:

```
max = 1 + (level * 0.004)
freshness += (0.08 * freshness) + 0.003
freshness = clamp(freshness, 0.01, max)

if freshness < rfreshness:  rfreshness -= (rfreshness - freshness) * 0.003
if freshness > rfreshness:  rfreshness += (freshness - rfreshness) * 0.265
```

Recovery is fast and decay is slow: `rfreshness` closes 26.5 percent of the gap per second going up, and 0.3 percent going down. Each award subtracts `0.012 + 0.00025 * xp` from `freshness`. Level only raises the ceiling, by 0.4 percent per level.

## Farm prevention

`[farmPrevention]` produces the monotony multiplier. It tracks *pressure*, a scalar that rises with each award and decays exponentially with elapsed time.

For one tracker, given a pressure gain, a recovery constant, a decay curve, and a floor:

```
pressure *= e^(-elapsedMillis / recoveryMillis)          // decay since the last award
pressure  = clamp(pressure + max(0, gain), 0, 100000)
multiplier = clamp(floor + (1 - floor) * e^(-pressure / curve), floor, 1)
```

A `curve` at or below zero disables that tracker and returns `1.0`.

Two trackers run. The skill tracker always runs, with `gain = skillBasePressure + (xp * skillXpPressure)`. The activity tracker runs when `perActivityTracking` is on and the award carries a non-blank reward key, with one tracker per key and its own gain, recovery, curve, and floor. Activity keys that have been idle longer than `activityStateTtlMillis` are swept, at most once every 15 seconds.

The two are multiplied and clamped against a combined floor:

```
floor = clamp(skillFloorMultiplier, 0, 1)
if perActivityTracking: floor = clamp(floor * activityFloorMultiplier, 0, 1)
monotony = clamp(skillMultiplier * activityMultiplier, floor, 1)
```

At the shipped defaults that floor is `0.08 * 0.12 = 0.0096`, so a fully saturated farm still pays about one percent. Awards of zero or less, and a disabled `[farmPrevention]`, both return `1.0` unconditionally.

## XP integrity

`[xpIntegrity]` is the anti-automation layer. Provenance answers "did this player create this block". Novelty answers "is this award actually new work".

### Provenance

Blocks a player places are stamped so they cannot be re-harvested for XP. `placedBlockTtlMillis` is how long that stamp lives. Breaking a block also stamps the spot, and re-placing there within `replaceDenyTtlMillis` earns nothing, which closes the break-and-replace loop. Bonemealed growth gets its own stamp with its own TTL, and harvesting it pays `bonemealHarvestMultiplier`.

### Novelty

`noveltyMultiplier` is `spatial * entropy`, with a stillness override on top, floored at `spatialFloorMultiplier * entropyFloorMultiplier * stillnessFloorMultiplier`.

Spatial bucketing divides the world into cubes `2^spatialCellShift` blocks on a side. The `n`-th award in a cube scores `max(spatialFloorMultiplier, 1 / (1 + spatialRepeatDecay * n))`. A cube idle longer than `spatialCellTtlMillis` resets to `n = 0`, and at most `spatialCellCap` cubes are kept per player, evicted least-recently-used.

Entropy watches a ring of the last `entropyWindow` reward keys. Until the ring fills, the term is `1.0`. Once full it is `entropyFloorMultiplier + (1 - entropyFloorMultiplier) * sqrt((distinct - 1) / 2)`, which saturates at 3 distinct keys.

Stillness watches for a player who is not moving. If position stays inside `stillnessEpsilon` on every axis and yaw stays inside a fixed 10 degrees, across at least `stillnessMinEvents` awards spanning `stillnessWindowMillis`, the combined multiplier is capped at `stillnessFloorMultiplier`. Any movement past those bounds restarts the run.

### Adjacency bonus

Placing a block against one you already placed builds a streak worth `min(adjacencyBonusMax, streak * adjacencyBonusPerStreak)` on top of `1.0`. Placing somewhere not adjacent halves the streak. The streak only grows while the target cell is not already heavily repeated and the bonus has not hit its cap.

### Field cycle

Re-harvesting the same crop cell too soon pays `fieldCycleFloorMultiplier` and ramps linearly back to `1.0` over `fieldCycleMillis` since that cell was last harvested. The first harvest of a cell always pays full.

### Adaptation usage baseline

`[adaptationXp]` pays a small trickle for actually using an adaptation, so active abilities are not dead weight for progression. The reward is `usageBaselineXp + (level - 1) * usageBaselineXpPerLevel`, on a per-player, per-adaptation cooldown of `usageBaselineCooldownMillis` with a hard floor of 250 ms.

It is paid through `xpSilent` under the reward key `adaptation:<adaptation-name>:baseline-use`, which means it skips stage 1 entirely: no novelty term, no region multiplier. It still passes through monotony and the multiplier snapshots, and the reward key still feeds the per-activity tracker.

## Level curves

A curve is a `NewtonCurve`: one function `getXPForLevel(level)` and one inverse `computeLevelForXP(xp, maxError)`. `xpCurve` picks the family.

The default is `ADAPT_BALANCED`:

```
xp(L) = 100 * L^2 + 1200 * L
L(xp) = (sqrt(1440000 + 400 * xp) - 1200) / 200
```

Both directions are closed form. Level 1 costs 1,300 XP, level 10 costs 22,000, level 100 costs 1,120,000.

### Inverting a curve

Families that declare an explicit inverse evaluate it directly and ignore `maxError`. `ADAPT_BALANCED` and `LINEAR_EXPONENTIAL_1` are exactly invertible.

The `XL*` families, `LINEAR_EXPONENTIAL_2`, and `LINEAR_EXPONENTIAL_3` declare only a forward function, so they fall through to `NewtonCurve`'s default `computeLevelForXP`, which is a bisection despite the class name, not Newton's method:

- the cursor starts at `0` and the jump size at `100`
- each iteration compares `getXPForLevel(cursor)` against the target and steps the cursor by the jump size
- the jump size halves only when the search reverses direction
- the loop ends when the jump size drops below `maxError`, or after 100 iterations
- the cursor is clamped to `experienceMaxLevel` and the loop breaks the moment it exceeds it

Runtime lookups pass `maxError = 0.000001`, which is a few dozen forward evaluations per call. Those families therefore cost noticeably more per level lookup than the closed-form ones.

### The level cap

`experienceMaxLevel` defaults to 1000 and is checked once per second per skill line. If the line's XP exceeds `getXPForLevel(experienceMaxLevel)` and the player is not busy, the player gains 1 wisdom and the line's XP is set back to `getXPForLevel(experienceMaxLevel - 1)`.

The bisection clamps its cursor to the same value, so on an `XL*` curve this is a hard ceiling on any reported level. Closed-form families are not clamped and can briefly report a level above the cap, between the overflow and the next tick's reset.

## Master XP, master level and power

Master XP comes only from skill level-ups. On the one-second tick, for every level `i` the line just crossed (`lastLevel <= i < level`):

```
knowledge += (i / 13) + 1                                     // integer division
masterXp  += playerXpPerSkillLevelUpBase + (i * playerXpPerSkillLevelUpLevelMultiplier)
```

`i` is the level being left, not the level reached. The step from 9 to 10 uses `i = 9` and grants `489 + 9*44 = 885` master XP and `(9 / 13) + 1 = 1` knowledge. The step from 49 to 50 grants `2645` master XP and `4` knowledge. A line that gains several levels in one tick runs the loop once per level.

Master level uses the same `xpCurve`:

```
masterLevel = xpCurve.computeLevelForXP(masterXp)
```

Power follows from it:

```
maxPower  = max(0, (int)(masterLevel * powerPerLevel) + regionPowerBonus)
usedPower = sum of the level of every learned adaptation that is NOT region granted
available = maxPower - usedPower
```

The `(int)` truncates, so the default `powerPerLevel = 0.65` yields one power point roughly every other master level at low levels. `regionPowerBonus` is the transient WorldGuard `adapt-power-bonus` contribution, refreshed on the same tick and never persisted; see `08 - Protection & Region Policy.md`.

`pruneAdaptationsForPowerBudget` repeatedly demotes the lowest-level non-region-granted adaptation by one level, removing it entirely at level 1, until `usedPower <= maxPower`. It runs after Trag'Oul's death drain and when a region power bonus drops while the player is over budget. Over-budget state arriving by any other route is left alone; new learning is just blocked, because `hasPowerAvailable(cost)` tests `available >= cost`. Region-bonus exit behavior is in `08 - Protection & Region Policy.md`.

Debug mode (`/adapt debug mode`) short-circuits `hasPowerAvailable`, `spendKnowledge`, and the pruner entirely.

## Reference

### Progression keys

| Key | Default | What it does |
|---|---:|---|
| `xpCurve` | `ADAPT_BALANCED` | Curve family used by every skill line and by master level |
| `experienceMaxLevel` | `1000` | Skill level cap, and the ceiling the bisection cursor clamps to |
| `playerXpPerSkillLevelUpBase` | `489` | Flat master XP per skill level crossed |
| `playerXpPerSkillLevelUpLevelMultiplier` | `44` | Extra master XP per level already reached |
| `powerPerLevel` | `0.65` | Power per master level, truncated to a whole number |

### Curve families

`L` is level, `xp` is total accumulated XP on the line.

| Family | `xp(L)` |
|---|---|
| `QLOG` | `L^2 * ln(L)` |
| `ELIN` | `1000 * e^(0.001 * L)` |
| `CUBRT` | `L^(1/3)` |
| `HYPER` | `1000 / (2 - L)` |
| `SIGM` | `1000 / (1 + e^(-0.01 * (L - 50)))` |
| `X1D2` | `L^1.2` |
| `X1D5` | `L^1.5` |
| `X2` ... `X7` | `L^2` ... `L^7` |
| `L1K`, `L4K`, `L8K`, `L16K` | `1000 * L`, `4000 * L`, `8000 * L`, `16000 * L` |
| `SKYRIM` | `sum(i = 1 .. L-1) of ((i - 1)^1.95 + 300)` |
| `WOW` | `sum(i = 1 .. L-1) of ((8i + diff(i)) * (235 + 5i) * drf(i))`, Blizzard's classic table |
| `XL05L7` ... `XL160L7`, `XL100L7` | `(k * L + (0.95 * L)^pi) / 1.137` |
| `ADAPT_BALANCED` | `100 * L^2 + 1200 * L` |
| `LINEAR_EXPONENTIAL_1` | `100 * L^2 + 1000 * L` |
| `LINEAR_EXPONENTIAL_2` | `50 * L^2.5 + 2000 * L` |
| `LINEAR_EXPONENTIAL_3` | `200 * L^1.5 + 500 * L` |

`k` for the `XL*` families is the leading number in the name plus 337:

| Family | `k` | Family | `k` | Family | `k` |
|---|---|---|---|---|---|
| `XL05L7` | 537 | `XL4L7` | 4337 | `XL9L7` | 9337 |
| `XL1L7` | 1337 | `XL5L7` | 5337 | `XL20L7` | 20337 |
| `XL15L7` | 1837 | `XL6L7` | 6337 | `XL40L7` | 40337 |
| `XL2L7` | 2337 | `XL7L7` | 7337 | `XL80L7` | 80337 |
| `XL3L7` | 3337 | `XL8L7` | 8337 | `XL160L7` | 160337 |
| | | | | `XL100L7` | 100337 |

### `[farmPrevention]`

| Key | Default | What it does |
|---|---:|---|
| `enabled` | `true` | Master switch; off pins monotony at `1.0` |
| `perActivityTracking` | `true` | Adds the per-reward-key tracker on top of the skill tracker |
| `skillRecoveryMillis` | `180000` | Milliseconds for skill pressure to decay by a factor of e |
| `activityRecoveryMillis` | `300000` | Same, for a per-key activity tracker |
| `activityStateTtlMillis` | `1800000` | Idle time after which an activity tracker is discarded |
| `skillBasePressure` | `1.0` | Flat pressure added per award |
| `skillXpPressure` | `0.02` | Extra pressure per point of awarded XP |
| `skillDecayCurve` | `14.0` | Pressure divisor in the exponent; larger means pressure bites more slowly |
| `skillFloorMultiplier` | `0.08` | Lowest multiplier the skill tracker can reach |
| `activityBasePressure` | `1.0` | Flat pressure added per keyed award |
| `activityXpPressure` | `0.03` | Extra pressure per point of awarded XP, per key |
| `activityDecayCurve` | `9.0` | Pressure divisor for the activity tracker |
| `activityFloorMultiplier` | `0.12` | Lowest multiplier the activity tracker can reach |
| `crossSkillRecoveryFactor` | `0.9` | Every other line's pressure is scaled by this on a skill switch |

### `[xpIntegrity]`, provenance

| Key | Default | What it does |
|---|---:|---|
| `provenanceEnabled` | `true` | Master switch for the placed-block ledger |
| `placedBlockTtlMillis` | `86400000` | How long a placement stamp survives, 24 hours |
| `replaceDenyTtlMillis` | `900000` | Window after a break in which re-placing there earns no XP |
| `bonemealTrackingEnabled` | `true` | Stamps growth caused by bonemeal |
| `bonemealTtlMillis` | `600000` | How long the bonemeal stamp lasts |
| `bonemealHarvestMultiplier` | `0.5` | Payout scale for harvesting bonemealed growth |

### `[xpIntegrity]`, novelty

| Key | Default | What it does |
|---|---:|---|
| `noveltyEnabled` | `true` | Master switch for the whole novelty term |
| `spatialCellShift` | `2` | Cube size exponent; cells are `2^shift` blocks on a side |
| `spatialCellCap` | `256` | Cubes remembered per player, evicted least-recently-used |
| `spatialCellTtlMillis` | `900000` | Idle time after which a cube's repeat count resets to zero |
| `spatialRepeatDecay` | `0.3` | How fast payout falls with each repeat in the same cube |
| `spatialFloorMultiplier` | `0.25` | Lowest the spatial term can reach |
| `entropyWindow` | `48` | How many recent reward keys the variety ring holds |
| `entropyFloorMultiplier` | `0.7` | Lowest the variety term can reach, hit when every key is the same |
| `stillnessEnabled` | `true` | Enables the not-moving override |
| `stillnessWindowMillis` | `60000` | How long a still run must span before the override applies |
| `stillnessMinEvents` | `20` | How many awards a still run must contain |
| `stillnessEpsilon` | `0.75` | Blocks of positional drift allowed before the run restarts |
| `stillnessFloorMultiplier` | `0.25` | Cap applied to the combined multiplier while still |

Yaw drift tolerance is a fixed 10 degrees and is not configurable.

### `[xpIntegrity]`, bonuses, cycling, pooling

| Key | Default | What it does |
|---|---:|---|
| `adjacencyBonusEnabled` | `true` | Enables the place-against-your-own-work streak |
| `adjacencyBonusMax` | `0.25` | Cap on the added fraction, so at most 1.25x |
| `adjacencyBonusPerStreak` | `0.05` | Added fraction per streak step |
| `fieldCycleMillis` | `240000` | Time for a re-harvested crop cell to ramp back to full payout |
| `fieldCycleFloorMultiplier` | `0.15` | Payout for an immediate re-harvest of the same cell |
| `pooledPayoutEnabled` | `true` | Batches awards instead of applying each one to the line |
| `pooledWindowMillis` | `30000` | Pool age that forces a flush |
| `pooledIdleFlushMillis` | `8000` | Idle time since the last award that forces a flush |
| `inspiredPopupEnabled` | `false` | Shows the Inspired action-bar popup on a skill switch |
| `inspiredCooldownMillis` | `300000` | Minimum time between Inspired assignments |

### `[adaptationXp]`

| Key | Default | What it does |
|---|---:|---|
| `usageBaselineEnabled` | `true` | Pays a small silent award for using an adaptation |
| `usageBaselineXp` | `0.8` | Award at adaptation level 1 |
| `usageBaselineXpPerLevel` | `0.18` | Added per level above 1 |
| `usageBaselineCooldownMillis` | `12000` | Per-player, per-adaptation cooldown; values below 250 are raised to 250 |

### `[permissionXpMultipliers]`

| Key | Default | What it does |
|---|---:|---|
| `enabled` | `false` | Enables the table and registers its nodes as permissions defaulting to false |
| `stack` | `false` | False takes the single highest matched value; true multiplies every matched value together |
| `multipliers` | empty | Permission node to multiplier; values at or below zero are ignored |

```toml
[permissionXpMultipliers]
enabled = false
stack = false

[permissionXpMultipliers.multipliers]
"adapt.xpmultiplier.vip" = 1.5
"adapt.xpmultiplier.mvp" = 2.0
```

## See also

- `01 - Installation & Configuration.md`
- `08 - Protection & Region Policy.md`
- `00 - Overview.md`
