# Configuration Math

This page defines the progression, XP, anti-farm, knowledge, and power calculations controlled by
`adapt/adapt.toml`. Values and ordering below describe current runtime behavior.

All config paths below are relative to `plugins/Adapt/adapt/adapt.toml`.

---

## Level curves

A curve is a `NewtonCurve`: one function `getXPForLevel(level)` and one inverse `computeLevelForXP(xp,
maxError)`. `xpCurve` picks the family; the same curve is used for **every** skill line *and* for master
level, so changing it changes progression and power at once.

```
xpCurve = "ADAPT_BALANCED"
```

### The default

`ADAPT_BALANCED`:

```
xp(L) = 100 * L^2 + 1200 * L
L(xp) = (sqrt(1440000 + 400 * xp) - 1200) / 200
```

Both directions are closed form. Level 1 costs 1,300 xp, level 10 costs 22,000, level 100 costs 1,120,000.

### Every family

`L` is level, `xp` is total accumulated xp on the line.

| Family | `xp(L)` |
|---|---|
| `QLOG` | `L^2 * ln(L)` |
| `ELIN` | `1000 * e^(0.001 * L)` |
| `CUBRT` | `L^(1/3)` |
| `HYPER` | `1000 / (2 - L)` |
| `SIGM` | `1000 / (1 + e^(-0.01 * (L - 50)))` |
| `X1D2` | `L^1.2` |
| `X1D5` | `L^1.5` |
| `X2` … `X7` | `L^2` … `L^7` |
| `L1K`, `L4K`, `L8K`, `L16K` | `1000 * L`, `4000 * L`, `8000 * L`, `16000 * L` |
| `SKYRIM` | `sum(i = 1 .. L-1) of ((i - 1)^1.95 + 300)` |
| `WOW` | `sum(i = 1 .. L-1) of ((8i + diff(i)) * (235 + 5i) * drf(i))` — Blizzard's classic table |
| `XL05L7` … `XL160L7`, `XL100L7` | `(k * L + (0.95 * L)^pi) / 1.137` |
| `ADAPT_BALANCED` | `100 * L^2 + 1200 * L` |
| `LINEAR_EXPONENTIAL_1` | `100 * L^2 + 1000 * L` |
| `LINEAR_EXPONENTIAL_2` | `50 * L^2.5 + 2000 * L` |
| `LINEAR_EXPONENTIAL_3` | `200 * L^1.5 + 500 * L` |

`k` for the `XL*` families is the leading coefficient in the name plus 337:

| Family | `k` | Family | `k` | Family | `k` |
|---|---|---|---|---|---|
| `XL05L7` | 537 | `XL4L7` | 4337 | `XL9L7` | 9337 |
| `XL1L7` | 1337 | `XL5L7` | 5337 | `XL20L7` | 20337 |
| `XL15L7` | 1837 | `XL6L7` | 6337 | `XL40L7` | 40337 |
| `XL2L7` | 2337 | `XL7L7` | 7337 | `XL80L7` | 80337 |
| `XL3L7` | 3337 | `XL8L7` | 8337 | `XL160L7` | 160337 |
| | | | | `XL100L7` | 100337 |

`ADAPT_BALANCED` and `LINEAR_EXPONENTIAL_1` are exactly invertible. `LINEAR_EXPONENTIAL_2` and
`LINEAR_EXPONENTIAL_3` mix a linear term with a non-integer power, which has no closed-form inverse, so
they resolve levels through the bisection fallback like the `XL*` group.

### Inverting a curve

Families declared with an explicit inverse evaluate it directly and ignore `maxError`.

The `XL*` families, `LINEAR_EXPONENTIAL_2`, and `LINEAR_EXPONENTIAL_3` declare only a forward function, so
they fall through to `NewtonCurve`'s default `computeLevelForXP`, which is a bisection, not Newton's method:

- cursor starts at `0`, jump size at `100`
- each iteration compares `getXPForLevel(cursor)` against the target and steps the cursor by the jump size
- the jump size halves on every direction change
- the loop ends when the jump size drops below `maxError` or after 100 iterations
- the cursor is clamped to `experienceMaxLevel` and the loop breaks the moment it exceeds it

Runtime lookups pass `maxError = 0.000001`. That is a few dozen forward evaluations per call, so the `XL*`
families cost more per level lookup than the closed-form ones.

### Level cap

```
experienceMaxLevel = 1000
```

Checked once per second per skill line. If the line's xp exceeds `getXPForLevel(experienceMaxLevel)` and the
player is not busy, the player gains 1 wisdom and the line's xp is set back to
`getXPForLevel(experienceMaxLevel - 1)`.

The bisection above also clamps its cursor here, so on an `XL*` curve this value is a hard ceiling on any
reported level. Closed-form families are not clamped and can report a level above the cap between the
overflow and the next tick's reset.

---

## The XP multiplier chain

An award passes through two files. Order matters — the stages are not commutative because two of them clamp
and one of them can abort the award entirely.

### Stage 1 — `SkillRuntimeGuards.grantXp`

Applies only to awards that carry a `Location`. `Skill.xp(player, …)` and `Skill.xpS(player, …)` do;
`Skill.xpSilent(player, xp)` routes through `grantXpSilent`, which skips this entire stage.

1. **Novelty.** `xp *= XpNovelty.noveltyMultiplier(player, location, rewardKey)`.
2. **Region policy.** `xp = RegionPolicyService.adjustXp(xp, RegionPolicyService.resolve(player, location))`
   — that is `xp * policy.xpMultiplier()`, or `0` when the region denies XP. See
   `08 - Protection & Region Policy.md`.
3. **Validity gate.** The result must be finite and greater than zero. A region that zeroes the award ends
   it here; nothing downstream runs.

### Stage 2 — `XP.xp` / `XP.xpSilent`

`PlayerData.resetMonotonyForOtherSkills(skill)` runs first. If the player switched skills since the last
award, every *other* line's staleness pressure is multiplied by `farmPrevention.crossSkillRecoveryFactor`
and the line with the most relieved pressure may become the Inspired skill. Then the award is handed to
`PlayerSkillLine.giveXP`.

### Stage 3 — `PlayerSkillLine.giveXP`

1. `freshness -= 0.012 + (xp * 0.00025)` — using the stage-1 result, before any stage-3 multiplication.
2. `monotonyMultiplier = computeStalenessMultiplier(xp, rewardKey, now)` (farm prevention, below).
3. `xp = lineMultiplier * monotonyMultiplier * xp`.

`lineMultiplier` is not computed here. It is a snapshot refreshed once per second by
`PlayerSkillLine.updateMultiplier`, so it can be up to one player-skill update, approximately one second,
behind a permission or boost change.

### The two multiplier snapshots

**Per line**, once per second:

```
m = rfreshness + sum(active /adapt boost values on this line)
m = clamp(m, 0.01, 1000)
lineMultiplier = m * playerMultiplier
```

**Per player**, at the top of the same tick (`PlayerData.computeXpMultiplier`):

```
m  = 1 + sum(active player boosts) + sum(active global boosts)
m *= permissionMultiplier
playerMultiplier = clamp(m, 0.01, 1000)     // <= 0 becomes 0.01, > 1000 becomes 1000
```

Boosts are additive percentages inside their own bracket and multiplicative across brackets. Expired
entries are dropped while the sums are taken.

### Permission multiplier

```toml
[permissionXpMultipliers]
enabled = false
stack = false

[permissionXpMultipliers.multipliers]
"adapt.xpmultiplier.vip" = 1.5
"adapt.xpmultiplier.mvp" = 2.0
```

Resolution (`PlayerData.resolvePermissionMultiplier`):

- returns `1.0` when the player is null, the section is disabled, or the table is empty
- entries with a blank node, a null value, or a value `<= 0` are skipped entirely
- entries the player does not hold are skipped
- **`stack = false`** — the single highest matched value wins. Holding both nodes above yields `2.0`.
- **`stack = true`** — every matched value is multiplied together. Holding both nodes above yields `3.0`.
- no match in either mode yields `1.0`

The result is multiplied into the player bracket, so the `[0.01, 1000]` clamp still applies. Values below
`1.0` act as rank penalties.

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

When `xpIntegrity.pooledPayoutEnabled` is on (the default), the multiplied award is added to a pool instead
of the line. The pool is flushed on the one-second tick once it is older than
`xpIntegrity.pooledWindowMillis` **and** idle for longer than `xpIntegrity.pooledIdleFlushMillis`. Flushing
adds the whole pool to the line at once and emits a single action-bar figure.

---

## Freshness

`freshness` is the per-line diminishing-returns term; `rfreshness` is the smoothed value the multiplier
actually reads.

Once per second, before the multiplier snapshot:

```
max = 1 + (level * 0.004)
freshness += (0.08 * freshness) + 0.003
freshness = clamp(freshness, 0.01, max)

if freshness < rfreshness:  rfreshness -= (rfreshness - freshness) * 0.003
if freshness > rfreshness:  rfreshness += (freshness - rfreshness) * 0.265
```

Recovery is fast (26.5% of the gap per second), decay is slow (0.3% of the gap per second). Each award
subtracts `0.012 + 0.00025 * xp` from `freshness`. Level raises only the ceiling, at 0.4% per level.

---

## Farm prevention

`[farmPrevention]` produces the monotony multiplier. It tracks *pressure*: a scalar that rises with each
award and decays exponentially with time.

For one tracker, given a pressure gain, a recovery constant, a decay curve and a floor:

```
pressure *= e^(-elapsedMillis / recoveryMillis)          // decay since the last award
pressure  = clamp(pressure + max(0, gain), 0, 100000)
multiplier = clamp(floor + (1 - floor) * e^(-pressure / curve), floor, 1)
```

A `curve <= 0` disables that tracker (returns `1.0`).

Two trackers run:

- **Skill**, always: `gain = skillBasePressure + (xp * skillXpPressure)`, with `skillRecoveryMillis`,
  `skillDecayCurve`, `skillFloorMultiplier`.
- **Activity**, when `perActivityTracking` is on and the award carries a non-blank reward key: one tracker
  per key, with `activityBasePressure`, `activityXpPressure`, `activityRecoveryMillis`,
  `activityDecayCurve`, `activityFloorMultiplier`. Keys idle for longer than `activityStateTtlMillis` are
  swept, at most once every 15 seconds.

The two are multiplied and clamped:

```
floor = clamp(skillFloorMultiplier, 0, 1)
if perActivityTracking: floor = clamp(floor * activityFloorMultiplier, 0, 1)
monotony = clamp(skillMultiplier * activityMultiplier, floor, 1)
```

Awards of `0` or less, and a disabled `[farmPrevention]`, both return `1.0` unconditionally.

| Key | Default | Effect |
|---|---|---|
| `enabled` | `true` | Master switch; off pins monotony at `1.0` |
| `perActivityTracking` | `true` | Adds the per-reward-key tracker |
| `skillRecoveryMillis` | `180000` | Skill pressure e-folding time |
| `activityRecoveryMillis` | `300000` | Activity pressure e-folding time |
| `activityStateTtlMillis` | `1800000` | Idle activity trackers are discarded after this |
| `skillBasePressure` | `1.0` | Flat pressure per award |
| `skillXpPressure` | `0.02` | Pressure per point of awarded xp |
| `skillDecayCurve` | `14.0` | Larger = pressure bites more slowly |
| `skillFloorMultiplier` | `0.08` | Lowest the skill tracker can go |
| `activityBasePressure` | `1.0` | Flat pressure per keyed award |
| `activityXpPressure` | `0.03` | Pressure per point of awarded xp, per key |
| `activityDecayCurve` | `9.0` | Larger = pressure bites more slowly |
| `activityFloorMultiplier` | `0.12` | Lowest the activity tracker can go |
| `crossSkillRecoveryFactor` | `0.9` | Every other line's pressure is scaled by this on a skill switch |

Combined floor at defaults: `0.08 * 0.12 = 0.0096`, i.e. a fully saturated farm still pays about 1%.

---

## XP integrity

`[xpIntegrity]` is the anti-automation layer. Provenance answers "did the player create this block";
novelty answers "is this award actually new work".

**Provenance** — placed blocks are stamped so they cannot be re-harvested for xp.

| Key | Default | Effect |
|---|---|---|
| `provenanceEnabled` | `true` | Master switch for the placed-block ledger |
| `placedBlockTtlMillis` | `86400000` | How long a placement stamp survives (24 h) |
| `replaceDenyTtlMillis` | `900000` | Window after a player breaks a block in which re-placing there is refused xp |
| `bonemealTrackingEnabled` | `true` | Stamps bonemealed growth |
| `bonemealTtlMillis` | `600000` | How long the bonemeal stamp lasts |
| `bonemealHarvestMultiplier` | `0.5` | Payout scale for harvesting bonemealed growth |

**Novelty** — `noveltyMultiplier` is `spatial * entropy`, floored, with a stillness override. Its floor is
`spatialFloorMultiplier * entropyFloorMultiplier * stillnessFloorMultiplier`.

- *Spatial*: the world is bucketed into cells of `2^spatialCellShift` blocks. The `n`-th award in a cell
  scores `max(spatialFloorMultiplier, 1 / (1 + spatialRepeatDecay * n))`. A cell idle longer than
  `spatialCellTtlMillis` resets to `n = 0`; at most `spatialCellCap` cells are retained per player, LRU.
- *Entropy*: a ring of the last `entropyWindow` reward keys. Until the ring fills, the term is `1.0`. Once
  full it is `entropyFloorMultiplier + (1 - entropyFloorMultiplier) * sqrt((distinct - 1) / 2)`, saturating
  at 3 distinct keys.
- *Stillness*: if the player's position and yaw stay inside `stillnessEpsilon` (and 10 degrees of yaw) for
  at least `stillnessMinEvents` awards spanning `stillnessWindowMillis`, the combined multiplier is capped
  at `stillnessFloorMultiplier`.

| Key | Default | | Key | Default |
|---|---|---|---|---|
| `noveltyEnabled` | `true` | | `entropyWindow` | `48` |
| `spatialCellShift` | `2` | | `entropyFloorMultiplier` | `0.7` |
| `spatialCellCap` | `256` | | `stillnessEnabled` | `true` |
| `spatialCellTtlMillis` | `900000` | | `stillnessWindowMillis` | `60000` |
| `spatialRepeatDecay` | `0.3` | | `stillnessMinEvents` | `20` |
| `spatialFloorMultiplier` | `0.25` | | `stillnessEpsilon` | `0.75` |
| | | | `stillnessFloorMultiplier` | `0.25` |

**Adjacency bonus** — placing against an existing player-placed block builds a streak worth
`min(adjacencyBonusMax, streak * adjacencyBonusPerStreak)` on top of `1.0`; breaking the chain halves the
streak. Defaults `adjacencyBonusEnabled = true`, `adjacencyBonusMax = 0.25`,
`adjacencyBonusPerStreak = 0.05`.

**Field cycle** — an immediate re-harvest of the same crop cell pays `fieldCycleFloorMultiplier` (`0.15`)
and ramps linearly back to `1.0` over `fieldCycleMillis` (`240000`) since that cell was last harvested. The
first harvest of a cell always pays full.

**Pooling and Inspired** — `pooledPayoutEnabled` (`true`), `pooledWindowMillis` (`30000`),
`pooledIdleFlushMillis` (`8000`), `inspiredPopupEnabled` (`false`), `inspiredCooldownMillis` (`300000`).

**Adaptation usage xp** — `[adaptationXp]` pays a small baseline for using an adaptation:
`usageBaselineEnabled` (`true`), `usageBaselineXp` (`0.8`), `usageBaselineXpPerLevel` (`0.18`),
`usageBaselineCooldownMillis` (`12000`).

---

## Master XP, master level and power

Master xp is earned only from skill level-ups. On the one-second tick, for every level `i` the line just
crossed (`lastLevel <= i < level`):

```
knowledge += (i / 13) + 1                                     // integer division
masterXp  += playerXpPerSkillLevelUpBase + (i * playerXpPerSkillLevelUpLevelMultiplier)
```

```
playerXpPerSkillLevelUpBase = 489.0
playerXpPerSkillLevelUpLevelMultiplier = 44.0
```

`i` is the level being left, not the level reached: the step from 9 to 10 uses `i = 9` and grants
`489 + 9*44 = 885` master xp and `(9 / 13) + 1 = 1` knowledge. The step from 49 to 50 grants `2645` master
xp and `4` knowledge. A line that gains several levels in one tick runs the loop once per level.

Master level uses the same `xpCurve`:

```
masterLevel = xpCurve.computeLevelForXP(masterXp)
```

Power:

```
powerPerLevel = 0.65

maxPower  = max(0, (int)(masterLevel * powerPerLevel) + regionPowerBonus)
usedPower = sum of the level of every learned adaptation that is NOT region granted
available = maxPower - usedPower
```

The `(int)` truncates, so `powerPerLevel = 0.65` yields one power point every other master level at low
levels. `regionPowerBonus` is the transient WorldGuard `adapt-power-bonus` contribution, refreshed on the
same tick and never persisted; see `08 - Protection & Region Policy.md`.

`pruneAdaptationsForPowerBudget` repeatedly demotes the lowest-level non-region-granted adaptation until
`usedPower <= maxPower`. It runs after Trag'Oul's death drain and when a WorldGuard region power bonus falls
and the player is over budget. Ordinary over-budget state caused by another path is retained; new learning
is blocked because `hasPowerAvailable(cost)` tests `available >= cost`. Region-bonus exit behavior is in
`08 - Protection & Region Policy.md`.

Debug mode (`/adapt debug mode`) short-circuits `hasPowerAvailable`, `spendKnowledge` and the pruner
entirely.

## See also

- `01 - Installation & Configuration.md`
- `00 - Overview.md`
