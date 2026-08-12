# Skill: Chronos

Skill id `chronos`. Earn XP through movement, sleeping, elapsed play time, and sharing Speed effects. Carrying a clock increases Chronos XP, with the largest bonus when it is in the off hand. Chronos has 13 registered adaptations and uses the `CLOCK` icon.

**XP sources:** elapsed play time, movement and teleports, sleeping, carrying clocks, and sharing Speed effects.

**Milestones / challenges** (stat keys):

- `challenge_chronos_1h` tracking `minutes.online`
- `challenge_chronos_24h` tracking `minutes.online`
- `challenge_active_dist_1k` tracking `chronos.active.distance`
- `challenge_active_dist_10k` tracking `chronos.active.distance`
- `challenge_active_dist_100k` tracking `chronos.active.distance`
- `challenge_beds_10` tracking `chronos.beds.used`
- `challenge_beds_100` tracking `chronos.beds.used`
- `challenge_chronos_tp_50` tracking `chronos.teleports`
- `challenge_chronos_tp_500` tracking `chronos.teleports`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `chronos` |
| Class | `SkillChronos` |
| Icon | `CLOCK` |
| Color | `AQUA` |
| Interval (ms) | `None` |
| Skill config | `plugins/Adapt/adapt/skills/chronos.toml` |
| Adaptation count | 13 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/chronos.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `setInterval` | `5050` | Tick interval used by this logic. |
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&b"` | Legacy ampersand color code used for this skill in menus and text. |
| `minimumMovementForActiveCheck` | `0.35` | Minimum movement-vector magnitude required to count an active movement sample. |
| `distancePerBonusXP` | `5` | Blocks traveled for each bonus Chronos XP increment. |
| `activeMovementXP` | `3.5` | XP awarded for active movement. |
| `activeMovementXPCapPerTick` | `6` | Maximum XP credited for active movement cap per tick. |
| `clockOffhandXpMultiplier` | `3` | Chronos XP multiplier while a clock is held in the offhand. |
| `clockInventoryXpMultiplier` | `2` | Chronos XP multiplier while a clock is stored in the player's inventory. |
| `positionHistorySize` | `12` | Number of recent positions retained for movement-variance checks. |
| `afkVarianceThreshold` | `2.0` | Minimum positional variance required to avoid the AFK penalty. |
| `afkMinActionTypes` | `3` | Distinct recent action categories required to avoid the AFK penalty. |
| `afkPenaltyMultiplier` | `0.03` | Unitless XP multiplier applied while activity is classified as AFK. |
| `passiveActiveXP` | `0.4` | XP awarded for passive active. |
| `activityWindow` | `15000` | Milliseconds of recent activity retained for diversity bonuses. |
| `activityTypesForBonus` | `4` | Distinct action categories required for the activity-diversity bonus. |
| `activityBonusMultiplier` | `1.5` | Unitless XP multiplier applied after the diversity threshold is met. |
| `nightActivityMultiplier` | `1.3` | Unitless Chronos XP multiplier applied during the configured night period. |
| `sleepXP` | `150` | Chronos XP granted when the player successfully enters a bed. |
| `sleepCooldown` | `30000` | Sleep cooldown. Milliseconds. |
| `speedPotionBaseXP` | `DEFAULT_SPEED_POTION_BASE_XP` | Base Chronos XP granted for applying a Speed potion to the user or another player. |
| `speedPotionLevelMultiplier` | `1.5` | Unitless multiplier applied per Speed effect level when calculating XP. |
| `speedPotionRewardCooldown` | `1000` | Minimum milliseconds between Speed potion rewards from the same player. |
| `speedPotionDiminishingDecay` | `0.15` | Amount subtracted from repeated Speed-effect reward weight in the diminishing window. |
| `speedPotionDiminishingFloor` | `0.25` | Lowest unitless reward multiplier allowed for repeated Speed effects. |
| `speedPotionResetWindow` | `300000` | Milliseconds without a Speed reward before diminishing returns reset. |
| `enderPearlThrowXP` | `35` | XP awarded for ender pearl throw. |
| `enderPearlTeleportXP` | `15` | XP awarded for ender pearl teleport. |
| `enderPearlCooldown` | `10000` | Ender pearl cooldown. Milliseconds. |
| `survivalXPPerMinute` | `3` | Chronos XP awarded for each eligible minute survived. |
| `survivalStreakBonusPerHour` | `0.2` | Additional unitless survival-XP multiplier earned per continuous hour. |
| `survivalStreakHourCap` | `5` | Maximum continuous hours counted toward the survival multiplier. |
| `challengeChronosReward` | `500` | Reward for the chronos challenge. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Time In A Bottle (`chronos-time-bottle`)

Carry a temporal bottle that stores time and spend it to accelerate timed blocks, growables, and Ageable entities such as baby animals. Its shapeless recipe uses a Swiftness Potion, a Clock, and a Glass Bottle.
An air click that ray-targets a block must pass a normal right-click-block event before time is spent; furnace
and brewing-stand targets also require container-access permission, while campfires and growables require
placement permission. Sapling tree generation preflights every planned tree block, fires `StructureGrowEvent`,
and aborts the whole tree when any footprint block or listener is denied. Folia requires direct block clicks;
the air-click ray-target variant is disabled there.

**How it activates:** hold the bottle to accumulate stored time. Right-click an eligible furnace, brewing stand, campfire, growable block, or Ageable entity to spend that time.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ChronosTimeInABottle` |
| Icon | `CLOCK` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 6 |
| Cost factor | 0.35 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-time-bottle.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `CraftItemEvent` (`on`) — when taking a craft result
- `PlayerItemConsumeEvent` (`on`) — when consuming food/potion
- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerInteractEntityEvent` (`on`) — on entity right-click

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Play clock sounds. |
| `baseMaxStoredSeconds` | `900` | Maximum seconds a bottle can store before any per-level scaling. |
| `maxStoredSecondsPerLevel` | `180` | Extra seconds added to the stored time cap per adaptation level. |
| `chargePerSecond` | `0.1` | Stored seconds accumulated per real second while the bottle is charging. |
| `chargePerSecondPerLevel` | `0.02` | Additional stored seconds accumulated per real second for each adaptation level. |
| `maxPlayersPerPass` | `32` | Maximum learned players processed in one bottle charge pass. |
| `baseCookTicksPerStoredSecond` | `20` | Base cook ticks per stored second. Server ticks (20 ticks = 1 second). |
| `cookTicksPerSecondPerLevel` | `3` | Cook ticks per second per level. Server ticks (20 ticks = 1 second). |
| `maxCookTicksPerUse` | `140` | Maximum cook ticks per use. Server ticks (20 ticks = 1 second). |
| `maxCookTicksPerUsePerLevel` | `35` | Maximum cook ticks per use per level. Server ticks (20 ticks = 1 second). |
| `furnaceSpendMultiplier` | `1` | Furnace spend multiplier. Unitless multiplier. |
| `baseBrewingTicksPerStoredSecond` | `20` | Base brewing ticks per stored second. Server ticks (20 ticks = 1 second). |
| `brewingTicksPerSecondPerLevel` | `3` | Brewing ticks per second per level. Server ticks (20 ticks = 1 second). |
| `maxBrewingTicksPerUse` | `140` | Maximum brewing ticks per use. Server ticks (20 ticks = 1 second). |
| `maxBrewingTicksPerUsePerLevel` | `35` | Maximum brewing ticks per use per level. Server ticks (20 ticks = 1 second). |
| `brewingSpendMultiplier` | `1.05` | Brewing spend multiplier. Unitless multiplier. |
| `baseCampfireTicksPerStoredSecond` | `20` | Base campfire ticks per stored second. Server ticks (20 ticks = 1 second). |
| `campfireTicksPerSecondPerLevel` | `3` | Campfire ticks per second per level. Server ticks (20 ticks = 1 second). |
| `maxCampfireTicksPerUse` | `160` | Maximum campfire ticks per use. Server ticks (20 ticks = 1 second). |
| `maxCampfireTicksPerUsePerLevel` | `40` | Maximum campfire ticks per use per level. Server ticks (20 ticks = 1 second). |
| `campfireSpendMultiplier` | `0.9` | Campfire spend multiplier. Unitless multiplier. |
| `baseEntityAgeTicksPerStoredSecond` | `20` | Base entity age ticks per stored second. Server ticks (20 ticks = 1 second). |
| `entityAgeTicksPerSecondPerLevel` | `4` | Entity age ticks per second per level. Server ticks (20 ticks = 1 second). |
| `maxEntityAgeTicksPerUse` | `180` | Maximum entity age ticks per use. Server ticks (20 ticks = 1 second). |
| `maxEntityAgeTicksPerUsePerLevel` | `55` | Maximum entity age ticks per use per level. Server ticks (20 ticks = 1 second). |
| `entitySpendMultiplier` | `1.35` | Entity spend multiplier. Unitless multiplier. |
| `maxGrowthStepsPerUse` | `6` | Maximum growth steps per use. |
| `maxGrowthStepsPerUsePerLevel` | `2` | Maximum growth steps per use per level. Level or effect-amplifier units. |
| `allowSaplingTreeGeneration` | `true` | Allow sapling tree generation. |
| `saplingGrowChanceBase` | `0.18` | Proc chance for sapling grow chance base. decimal probability. |
| `saplingGrowChancePerLevel` | `0.04` | Proc chance for sapling grow chance per level. decimal probability. |
| `growthCostMultiplier` | `1` | Growth cost multiplier. Unitless multiplier. |
| `growthCostReductionPerLevel` | `0.05` | Growth cost reduction per level. Level or effect-amplifier units. |
| `minGrowthCostLevelScale` | `0.45` | Lower bound or activation threshold for min growth cost level scale. Level or effect-amplifier units. |
| `minGrowthStepSeconds` | `0.06` | Lower bound or activation threshold for min growth step seconds. seconds. |
| `saplingGrowthSteps` | `2` | Sapling growth steps. |
| `stemGrowthSteps` | `7` | Stem growth steps. |
| `berryGrowthSteps` | `3` | Berry growth steps. |
| `vineGrowthSteps` | `5` | Vine growth steps. |
| `caveVineGrowthSteps` | `5` | Cave vine growth steps. |
| `kelpGrowthSteps` | `5` | Kelp growth steps. |
| `defaultGrowthSteps` | `4` | Default growth steps. |
| `cropNaturalSeconds` | `300` | Crop natural seconds. seconds. |
| `netherWartNaturalSeconds` | `420` | Nether wart natural seconds. seconds. |
| `saplingNaturalSeconds` | `900` | Sapling natural seconds. seconds. |
| `stemNaturalSeconds` | `660` | Stem natural seconds. seconds. |
| `berryBushNaturalSeconds` | `260` | Berry bush natural seconds. seconds. |
| `vineNaturalSeconds` | `300` | Vine natural seconds. seconds. |
| `caveVineNaturalSeconds` | `280` | Cave vine natural seconds. seconds. |
| `kelpNaturalSeconds` | `240` | Kelp natural seconds. seconds. |
| `defaultGrowableNaturalSeconds` | `420` | Default growable natural seconds. seconds. |
| `cropCostMultiplier` | `1` | Crop cost multiplier. Unitless multiplier. |
| `netherWartCostMultiplier` | `1.2` | Nether wart cost multiplier. Unitless multiplier. |
| `saplingCostMultiplier` | `2.2` | Sapling cost multiplier. Unitless multiplier. |
| `stemCostMultiplier` | `1.4` | Stem cost multiplier. Unitless multiplier. |
| `berryBushCostMultiplier` | `0.8` | Berry bush cost multiplier. Unitless multiplier. |
| `vineCostMultiplier` | `0.85` | Vine cost multiplier. Unitless multiplier. |
| `caveVineCostMultiplier` | `0.9` | Cave vine cost multiplier. Unitless multiplier. |
| `kelpCostMultiplier` | `0.75` | Kelp cost multiplier. Unitless multiplier. |
| `defaultGrowableCostMultiplier` | `1` | Default growable cost multiplier. Unitless multiplier. |
| `xpPerCookTick` | `0.08` | XP awarded for xp per cook tick. |
| `xpPerBrewTick` | `0.08` | XP awarded for xp per brew tick. |
| `xpPerCampfireTick` | `0.08` | XP awarded for xp per campfire tick. |
| `xpPerEntityAgeTick` | `0.06` | XP awarded for xp per entity age tick. |
| `xpPerGrowthStep` | `2` | XP awarded for xp per growth step. |
| `maxXPPerUse` | `55` | Maximum XP credited for max per use. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Aberrant Touch (`chronos-aberrant-touch`)

Melee attacks apply stacking slowness at the cost of hunger, with strict PvP caps, and root targets at 5 stacks.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Melee attacks apply stacking slowness; PvE slowness duration cap; PvP slowness amplifier cap.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ChronosAberrantTouch` |
| Icon | `SPIDER_EYE` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 7 |
| Cost factor | 0.38 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-aberrant-touch.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Play clock sounds. |
| `durationAddTicks` | `30` | Duration add ticks. Server ticks (20 ticks = 1 second). |
| `durationPerLevelTicks` | `6` | Duration per level ticks. Server ticks (20 ticks = 1 second). |
| `playerDurationCapTicks` | `80` | Player duration cap ticks. Server ticks (20 ticks = 1 second). |
| `playerAmplifierCap` | `1` | Player amplifier cap. Level or effect-amplifier units. |
| `entityDurationCapTicks` | `120` | Entity duration cap ticks. Server ticks (20 ticks = 1 second). |
| `entityDurationCapPerLevelTicks` | `10` | Entity duration cap per level ticks. Server ticks (20 ticks = 1 second). |
| `entityAmplifierCap` | `4` | Entity amplifier cap. Level or effect-amplifier units. |
| `hungerCost` | `1.0` | Hunger cost. food or saturation points. |
| `minimumFoodLevel` | `4` | Lower bound or activation threshold for minimum food level. food or saturation points. |
| `rootAtStacks` | `5` | Root at stacks. count. |
| `rootDurationTicks` | `20` | Root duration ticks. Server ticks (20 ticks = 1 second). |
| `rootAmplifier` | `10` | Root amplifier. Level or effect-amplifier units. |
| `stackResetMillis` | `2500` | Stack reset millis. Milliseconds. |
| `cooldownMillis` | `250` | Cooldown millis. Milliseconds. |
| `xpPerProc` | `4` | XP awarded for xp per proc. |
| `xpPerLevel` | `1.25` | XP awarded for xp per level. Level or effect-amplifier units. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Instant Recall (`chronos-instant-recall`)

Rewind to a recent snapshot with health and hunger restored. Costs the clock and part of your remaining health, but never kills you.

**How it activates:** with a clock in either hand, left- or right-click air or a block. Click activation is enabled by default; sprint-click, single-sneak, and double-jump triggers are configurable and disabled by default.

**Menu displays:** Rewind duration; Cooldown; No inventory rollback.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ChronosInstantRecall` |
| Icon | `RECOVERY_COMPASS` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.45 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-instant-recall.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `PlayerJoinEvent` (`on`)
- `PlayerTeleportEvent` (`on`) — on teleport
- `PlayerChangedWorldEvent` (`on`) — on world change
- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `EntityDamageEvent` (`on`) — on taking damage
- `PlayerMoveEvent` (`on`) — while moving
- `PlayerMoveEvent` (`onDoubleJumpMove`) — while moving

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Play clock sounds. |
| `consumeClock` | `true` | Consumes one clock from the casting hand when a recall successfully starts. |
| `healthCostFraction` | `0.5` | Fraction of current health lost when a recall completes; never lethal, health is floored at 1.0. |
| `showRewindTraceParticles` | `true` | Controls whether rewind trace particles are emitted. |
| `rewindTracePoints` | `18` | Rewind trace points. |
| `rewindAnimationDurationMillis` | `1000` | Target rewind animation duration in milliseconds. |
| `rewindAnimationTicks` | `18` | Legacy fallback rewind animation ticks used when duration is invalid. |
| `rewindUseTemporarySpectator` | `true` | Temporarily switches to spectator during rewind animation for smoother camera movement through obstacles. |
| `rewindUseClientCamera` | `true` | Uses a client-side spectator camera anchor during rewind so server position only updates at the end. |
| `rewindTeleportXpSuppressExtraTicks` | `10` | Extra ticks to suppress teleport XP/stat tracking after recall rewinds. |
| `enableClockClickTrigger` | `true` | Enables direct click-with-clock activation for instant recall. |
| `clockClickLeftClick` | `true` | Allows left-click to activate recall when clock-click trigger is enabled. |
| `clockClickRightClick` | `true` | Allows right-click to activate recall when clock-click trigger is enabled. |
| `enableSprintClickTrigger` | `false` | Enables sprint + click activation for instant recall with a valid recall clock. |
| `sprintClickLeftClick` | `false` | Allows left-click for sprint-click recall trigger. |
| `sprintClickRightClick` | `true` | Allows right-click for sprint-click recall trigger. |
| `allowAirClicks` | `true` | Allows click-in-air interactions for recall click triggers. |
| `allowBlockClicks` | `true` | Allows click-on-block interactions for recall click triggers. |
| `enableSingleSneakTrigger` | `false` | Enables single-sneak activation for instant recall. |
| `singleSneakRequiresSprint` | `false` | Require sprinting for single-sneak instant recall trigger. |
| `singleSneakRequiresClockInHand` | `true` | Require holding a valid recall clock in either hand for single-sneak trigger. |
| `enableDoubleJumpTrigger` | `false` | Enables double-tap jump activation for instant recall. |
| `doubleJumpRequiresSprint` | `false` | Require sprinting while double-jumping to trigger recall. |
| `doubleJumpRequiresClockInHand` | `true` | Require holding a valid recall clock in either hand for double-jump trigger. |
| `baseRewindSeconds` | `3.5` | Base rewind seconds. seconds. |
| `rewindSecondsPerLevel` | `0.35` | Rewind seconds per level. Level or effect-amplifier units. |
| `maxRewindSeconds` | `5` | Hard cap for recall rewind duration in seconds. |
| `cooldownPaddingSeconds` | `1` | Cooldown padding seconds. seconds. |
| `snapshotIntervalMillis` | `50` | Snapshot interval millis. Milliseconds. |
| `historyPaddingSeconds` | `2` | History padding seconds. seconds. |
| `rewindProtectionTicks` | `25` | Rewind protection ticks. Server ticks (20 ticks = 1 second). |
| `xpPerDistanceBlock` | `0.35` | XP awarded for xp per distance block. Blocks. |
| `xpPerHealthPoint` | `0.85` | XP awarded for xp per health point. health points (2 points = 1 heart). |
| `xpPerHungerPoint` | `0.7` | XP awarded for xp per hunger point. food or saturation points. |
| `xpPerSaturationPoint` | `0.18` | XP awarded for xp per saturation point. food or saturation points. |
| `xpLevelMultiplierPerLevel` | `0.08` | Unitless multiplier applied to XP from xp level multiplier per level. |
| `xpMinRawReward` | `1.35` | XP awarded for xp min raw reward. |
| `xpMinAward` | `0.5` | XP awarded for xp min award. |
| `xpMaxAward` | `36` | Maximum XP credited for xp max award. |
| `xpCrossWorldDistanceCredit` | `16` | XP awarded for xp cross world distance credit. Blocks. |
| `xpDiminishWindowMillis` | `45000` | Rate-limit or history window for XP from xp diminish window millis. Milliseconds. |
| `xpDiminishMinMultiplier` | `0.18` | Unitless multiplier applied to XP from xp diminish min multiplier. |
| `xpRepeatWindowMillis` | `180000` | Rate-limit or history window for XP from xp repeat window millis. Milliseconds. |
| `xpRepeatSourceRadius` | `3.5` | XP awarded for xp repeat source radius. Blocks. |
| `xpRepeatTargetRadius` | `3.5` | XP awarded for xp repeat target radius. Blocks. |
| `xpRepeatPenaltyMultiplier` | `0.2` | Unitless multiplier applied to XP from xp repeat penalty multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Time Bomb (`chronos-time-bomb`)

Throw a crafted chrono bomb that creates a temporal field, slows entities, and freezes projectiles.

**Runtime entry points:** on block/entity/air interact (click); when launching a projectile; on `LingeringPotionSplashEvent`; on `EntitiesLoadEvent`.

**Menu displays:** Temporal field radius; Temporal field duration; Bomb cooldown; Recipe (Shapeless): Clock + Snowball + Diamond + Sand.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ChronosTimeBomb` |
| Icon | `TNT` |
| Max level | 5 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 8 |
| Cost factor | 0.42 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-time-bomb.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `PlayerJoinEvent` (`on`)
- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `ProjectileLaunchEvent` (`on`) — when launching a projectile
- `LingeringPotionSplashEvent` (`on`)
- `EntitiesLoadEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Play clock sounds. |
| `baseRadius` | `6` | Base temporal-field radius in blocks. |
| `radiusPerLevel` | `1.5` | Additional temporal-field radius per adaptation level. |
| `baseDurationTicks` | `60` | Base duration ticks. Server ticks (20 ticks = 1 second). |
| `durationPerLevelTicks` | `25` | Duration per level ticks. Server ticks (20 ticks = 1 second). |
| `cooldownMillis` | `15000` | Cooldown millis. Milliseconds. |
| `fieldCenterYOffset` | `1.25` | Field center yoffset. |
| `slownessAmplifier` | `2` | Slowness amplifier. Level or effect-amplifier units. |
| `casterSlownessAmplifier` | `1` | Caster slowness amplifier. Level or effect-amplifier units. |
| `fatigueAmplifier` | `1` | Fatigue amplifier. Level or effect-amplifier units. |
| `freezePlayersInAir` | `true` | Whether affected players are held in place while airborne. |
| `accumulateFrozenImpulse` | `true` | Accumulate frozen impulse. |
| `frozenImpulseMinMagnitude` | `0.03` | Frozen impulse min magnitude. |
| `frozenImpulseSampleCap` | `2.8` | Frozen impulse sample cap. |
| `frozenImpulseReleaseCap` | `7.5` | Frozen impulse release cap. |
| `effectRefreshTicks` | `24` | Effect refresh ticks. Server ticks (20 ticks = 1 second). |
| `showFieldSphere` | `true` | Controls whether field sphere are emitted. |
| `fieldSphereParticleCount` | `280` | Field sphere particle count. |
| `fieldSphereRefreshMillis` | `100` | Field sphere refresh millis. Milliseconds. |
| `fieldTickSoundIntervalMillis` | `325` | Field tick sound interval millis. Milliseconds. |
| `fieldTickMinIntervalMillis` | `70` | Field tick min interval millis. Milliseconds. |
| `fieldTickPitchStart` | `0.42` | Field tick pitch start. |
| `fieldTickPitchEnd` | `1.96` | Field tick pitch end. |
| `fieldTickPitchCurveExponent` | `3.75` | XP awarded for field tick pitch curve e onent. |
| `fieldTickAccelerationFactor` | `0.82` | Field tick acceleration factor. Unitless multiplier. |
| `maxActiveFields` | `32` | Maximum concurrent temporal fields processed by this server. |
| `fieldScanIntervalMillis` | `250` | Interval between temporal field entity scans. |
| `maxRegionScansPerCycle` | `64` | Maximum loaded-region scans shared by all temporal fields per scan cycle. |
| `maxRegionScansPerField` | `8` | Maximum loaded-region scans assigned to one temporal field per scan cycle. |
| `maxEntitiesPerScanCycle` | `512` | Maximum entities scheduled for temporal field evaluation per scan cycle. |
| `maxEntitiesPerField` | `64` | Maximum entities assigned to one temporal field per scan cycle. |
| `maxEntitiesPerRegion` | `64` | Maximum entities read from one owned loaded region during a field scan. |
| `maxFrozenEntitiesPerTick` | `256` | Maximum frozen non-player entities reconciled per tick. |
| `maxFrozenPlayersPerTick` | `128` | Maximum frozen players reconciled per tick. |
| `maxFieldFxParticlesPerTick` | `512` | Maximum field presentation particles shared by all temporal fields per tick. |
| `maxFieldFxParticlesPerField` | `48` | Maximum field presentation particles assigned to one temporal field per tick. |
| `maxFieldSphereParticlesPerField` | `36` | Maximum sphere particles emitted by one field refresh. |
| `maxFreezeFxParticlesPerScan` | `256` | Maximum freeze-impact particles shared by all temporal fields per scan cycle. |
| `maxFreezeFxParticlesPerField` | `24` | Maximum freeze-impact particles assigned to one temporal field per scan cycle. |
| `xpOnCast` | `28` | XP awarded for xp on cast. |
| `xpPerLevel` | `3` | XP awarded for xp per level. Level or effect-amplifier units. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Temporal Echo (`chronos-temporal-echo`)

Projectile actions can replay once after a short delay at reduced strength.

**Runtime entry points:** when launching a projectile; when a projectile hits; periodic evaluation every 1600 ms.

**Menu displays:** Echo Delay; Echo Velocity Factor; Echo Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ChronosTemporalEcho` |
| Icon | `AMETHYST_CLUSTER` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.75 |
| Tick interval (ms) | 1600 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-temporal-echo.toml` |

Listened events:

- `ProjectileLaunchEvent` (`on`) — when launching a projectile
- `ProjectileHitEvent` (`on`) — when a projectile hits

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `echoDelayTicksBase` | `18` | Base Echo delay ticks. Server ticks (20 ticks = 1 second). |
| `echoDelayTicksFactor` | `10` | Echo delay ticks factor. Server ticks (20 ticks = 1 second). |
| `echoVelocityFactorBase` | `0.45` | Base Echo velocity factor. Unitless multiplier. |
| `echoVelocityFactorFactor` | `0.45` | Echo velocity factor factor. Unitless multiplier. |
| `maxEchoVelocityFactor` | `0.92` | Maximum echo velocity factor. Unitless multiplier. |
| `cooldownMillisBase` | `5000` | Base Cooldown millis. Milliseconds. |
| `cooldownMillisFactor` | `2600` | Cooldown millis factor. Milliseconds. |
| `xpPerEcho` | `12` | XP awarded for xp per echo. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Stasis Field (`chronos-stasis-field`)

Sneak and right click with an amethyst shard to deploy a stasis bubble that freezes projectiles in midair and locks down mobs inside. Consumes the shard on cast.

**Runtime entry points:** on `EntityRemoveEvent`; on block/entity/air interact (click); periodic evaluation every 50 ms while its conditions hold.

**Menu displays:** Stasis bubble radius; Stasis bubble duration; Cooldown; Sneak + Right Click with an Amethyst Shard.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ChronosStasisField` |
| Icon | `AMETHYST_SHARD` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 7 |
| Cost factor | 0.4 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-stasis-field.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `EntityRemoveEvent` (`on`)
- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Play clock sounds. |
| `consumeShard` | `true` | Consumes the amethyst shard used to deploy a stasis bubble. |
| `baseRadius` | `3.5` | Base radius of the stasis bubble in blocks. |
| `radiusPerLevel` | `0.75` | Extra bubble radius granted per adaptation level. |
| `baseDurationMillis` | `3000` | Base bubble lifetime in milliseconds. |
| `durationPerLevelMillis` | `750` | Extra bubble lifetime in milliseconds per adaptation level. |
| `cooldownMillis` | `20000` | Cooldown between bubble deployments in milliseconds. |
| `centerYOffset` | `1` | Vertical offset of the bubble center above the caster. |
| `slownessAmplifier` | `5` | Slowness amplifier applied to mobs inside the bubble. |
| `jumpLockAmplifier` | `-6` | Jump suppression amplifier applied to mobs inside the bubble as a jump_strength reduction scaling linearly from 0 (none) to -6 (full lock); positive values apply no modifier. |
| `effectRefreshTicks` | `20` | Duration in ticks of each refreshed stasis pulse. |
| `pulseIntervalMillis` | `250` | Milliseconds between bubble scans. |
| `removeProjectilesOnExpire` | `false` | Removes frozen projectiles when the bubble expires instead of restoring their motion. |
| `outlineParticleCount` | `10` | Number of outline particles requested per visual refresh. |
| `outlineRefreshMillis` | `400` | Milliseconds between bubble outline refreshes. |
| `xpOnCast` | `22` | XP granted when an admitted bubble is deployed. |
| `xpPerLevel` | `3` | Extra XP granted per adaptation level on an admitted cast. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Rewind (`chronos-rewind`)

Sneak and swap hands to mark a moment in time, then do it again within the window to snap back with health and hunger restored. Each rewind costs hunger.

**Runtime entry points:** on swap hands (F).

**Menu displays:** Rewind window; Cooldown after a rewind; Sneak + Swap Hands to mark, repeat to rewind.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ChronosRewind` |
| Icon | `ENDER_EYE` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 6 |
| Cost factor | 0.4 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-rewind.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `PlayerSwapHandItemsEvent` (`on`) — on swap hands (F)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Play clock sounds. |
| `hungerCost` | `6` | Food points consumed when a rewind completes. |
| `snapshotWindowMillis` | `10000` | Window in milliseconds after marking a snapshot during which the rewind can be completed. |
| `baseCooldownMillis` | `45000` | Base cooldown in milliseconds applied after a completed rewind. |
| `cooldownReductionPerLevelMillis` | `4000` | Cooldown reduction in milliseconds per adaptation level. |
| `minimumCooldownMillis` | `15000` | Lowest possible cooldown in milliseconds regardless of level. |
| `xpOnRewind` | `18` | XP granted when a rewind completes. |
| `xpPerLevel` | `3` | Extra XP granted per adaptation level on rewind. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Borrowed Time (`chronos-borrowed-time`)

A portion of incoming damage is deferred and quietly drained back once per second over the following seconds.

**Runtime entry points:** on player death; on taking damage.

**Menu displays:** Damage deferred; Payback window; Deferred damage cannot be deferred again.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ChronosBorrowedTime` |
| Icon | `SOUL_SAND` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 6 |
| Cost factor | 0.42 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-borrowed-time.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `PlayerJoinEvent` (`on`)
- `PlayerDeathEvent` (`on`) — on player death
- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseDeferFraction` | `0.1` | Base fraction of incoming damage that is deferred. |
| `deferFractionPerLevel` | `0.06` | Extra deferred fraction per adaptation level. |
| `maxDeferFraction` | `0.45` | Hard cap on the deferred damage fraction. |
| `minimumDeferDamage` | `1.0` | Minimum final damage required before any deferral happens. |
| `paybackPulses` | `10` | Number of one second pulses the deferred damage is paid back over. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Overtime (`chronos-overtime`)

Beneficial potion effects applied to you last longer, scaled by adaptation level. At max level, harmful effects applied to you last half as long.

**Runtime entry points:** on potion effect change; periodic evaluation every 60000 ms.

**Menu displays:** Extra effect duration; Maximum bonus per effect; Only beneficial effects are extended.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ChronosOvertime` |
| Icon | `GLISTERING_MELON_SLICE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.38 |
| Tick interval (ms) | 60000 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-overtime.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `EntityPotionEffectEvent` (`onHarmfulEffect`) — on potion effect change
- `EntityPotionEffectEvent` (`on`) — on potion effect change

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseExtensionPercent` | `0.05` | Base duration extension as a fraction of the original duration. |
| `extensionPercentPerLevel` | `0.07` | Extra extension fraction per adaptation level. |
| `maxExtensionPercent` | `0.4` | Hard cap on the duration extension fraction. |
| `maxBonusTicks` | `2400` | Maximum bonus ticks added to any single effect application. |
| `minimumDurationTicks` | `60` | Minimum original duration in ticks for an effect to be extended. |
| `maximumBaseDurationTicks` | `72000` | Maximum original duration in ticks eligible for extension. |
| `xpPerBonusSecond` | `0.2` | XP granted per bonus second of effect duration. |
| `maxXpPerExtension` | `8` | Maximum XP granted by a single extension. |
| `halveHarmfulEffectsAtMaxLevel` | `true` | Shorten harmful potion effects applied to you once this adaptation is at max level. |
| `maxLevelHarmfulDurationMultiplier` | `0.5` | Fraction of the original duration kept by harmful effects at max level. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Accelerate (`chronos-accelerate`)

Passively accelerate time around you, occasionally growing nearby crops and fast-forwarding furnaces, smokers, blast furnaces, and brewing stands.
Each sampled target is changed only while the player and block are owned by the current region and interaction
events allow it; crops also require placement permission, while processing stations require container access.

**Runtime entry points:** passive evaluation while learned.

**Menu displays:** Aura radius; Crop growth chance per pulse; of a cook or brew fast-forwarded per pulse hit.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ChronosAccelerate` |
| Icon | `SUGAR` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-accelerate.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `pulseIntervalMillis` | `3000` | Milliseconds between acceleration pulses. |
| `baseRadius` | `4` | Base aura radius in blocks. |
| `radiusPerLevel` | `0.8` | Extra aura radius per adaptation level. |
| `baseSamplesPerPulse` | `3` | Base number of random blocks sampled per pulse. |
| `maxSamplesPerPulse` | `8` | Hard cap on blocks sampled per pulse. |
| `maxPlayersPerPass` | `512` | Maximum learned players prepared in one acceleration pass. |
| `maxSamplesPerPass` | `8192` | Maximum block-region jobs admitted by one acceleration pass. |
| `baseGrowChance` | `0.3` | Base chance for a sampled crop to advance one growth stage. |
| `growChancePerLevel` | `0.06` | Extra crop growth chance per adaptation level. |
| `baseCookBoostFraction` | `0.25` | Base fraction of total cook or brew time fast-forwarded per pulse hit. |
| `cookBoostFractionPerLevel` | `0.07` | Extra cook fast-forward fraction per adaptation level. |
| `maxCookBoostFraction` | `0.6` | Hard cap on the cook fast-forward fraction per pulse hit. |
| `xpPerAcceleratedBlock` | `1.2` | XP granted per accelerated block. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Hourglass Guard (`chronos-hourglass-guard`)

A killing blow instead leaves you at half a heart, granting brief invulnerability and slowing nearby enemies, on a long cooldown.

**Runtime entry points:** on taking damage.

**Menu displays:** Invulnerability after a save; Cooldown; Nearby enemies are briefly slowed.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ChronosHourglassGuard` |
| Icon | `TOTEM_OF_UNDYING` |
| Max level | 3 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 9 |
| Cost factor | 0.5 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-hourglass-guard.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Play clock sounds. |
| `survivalHealth` | `1.0` | Health the player is left with after a save. |
| `invulnerabilityMillis` | `2000` | Invulnerability window in milliseconds after a save. |
| `baseCooldownMillis` | `480000` | Base cooldown in milliseconds between saves. |
| `cooldownReductionPerLevelMillis` | `60000` | Cooldown reduction in milliseconds per adaptation level. |
| `minimumCooldownMillis` | `180000` | Lowest possible cooldown in milliseconds regardless of level. |
| `enemySlowRadius` | `5` | Radius in blocks for the slow applied to nearby enemies on save. |
| `enemySlowTicks` | `50` | Duration in ticks of the slow applied to nearby enemies. |
| `enemySlowAmplifier` | `2` | Slowness amplifier applied to nearby enemies on save. |
| `xpOnSave` | `60` | XP granted when a save triggers. |
| `xpPerLevel` | `10` | Extra XP granted per adaptation level on save. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Pocket Watch (`chronos-pocket-watch`)

Sneak while falling with a clock in your inventory to drift in slow motion for a limited, level scaled duration each airtime.

**Runtime entry points:** passive evaluation while learned.

**Menu displays:** Slow fall budget per airtime; Budget refills on landing; Hold Sneak while falling with a Clock in your inventory.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ChronosPocketWatch` |
| Icon | `FEATHER` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.35 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-pocket-watch.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseBudgetSeconds` | `2` | Base seconds of slow falling sustainable per airtime. |
| `budgetSecondsPerLevel` | `1` | Extra sustain seconds per adaptation level. |
| `pulseDurationTicks` | `15` | Duration in ticks of each refreshing slow falling pulse. |
| `minFallDistance` | `1.5` | Minimum fall distance in blocks before the slow fall kicks in. |
| `requireClock` | `true` | Requires a clock anywhere in the inventory for the slow fall to apply. |
| `xpPerPulse` | `0.3` | XP granted per slow fall pulse. |
| `maxPlayersPerPass` | `512` | Maximum learned players processed per pulse pass. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Deja Vu (`chronos-deja-vu`)

Your body remembers recent pain; taking the same kind of damage again within a short window hurts noticeably less.

**Runtime entry points:** on taking damage; periodic evaluation every 60000 ms.

**Menu displays:** Repeat damage absorbed; Damage memory window; The memory refreshes on every hit of the same kind.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ChronosDejaVu` |
| Icon | `ITEM_FRAME` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.35 |
| Tick interval (ms) | 60000 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-deja-vu.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `memoryWindowMillis` | `8000` | Window in milliseconds during which a repeated damage cause counts as familiar. |
| `baseReductionFraction` | `0.15` | Base fraction of repeated damage that is absorbed. |
| `reductionFractionPerLevel` | `0.09` | Extra absorbed fraction per adaptation level. |
| `maxReductionFraction` | `0.6` | Hard cap on the absorbed damage fraction. |
| `xpPerAbsorbedDamage` | `0.8` | XP granted per point of absorbed damage. |
| `fxCooldownMillis` | `1500` | Minimum milliseconds between familiar-hit effect emissions. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## Support classes (not player adaptations)

- `ChronosInstantRecallConfig` — defines Instant Recall's trigger, rewind, cost, protection, and XP defaults.
- `ChronosInstantRecallTypes` — stores recall snapshots, XP calculation context, and repeat-reward stamps.
- `ChronosSoundFX` — schedules the clock, bottle, rewind, touch, bomb, and temporal-field sounds used by Chronos adaptations.
- `ChronosWorkBudget` — bounds and rotates batch work, catch-up pulses, and per-job allocation for Chronos runtimes.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
