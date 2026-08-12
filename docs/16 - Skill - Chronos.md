# Skill: Chronos

Chronos is the time skill. You level it by being alive and busy: walking around, sleeping in beds, throwing ender pearls, drinking or splashing Speed potions, and just staying alive without dying. Carrying a clock multiplies everything Chronos pays out, and the multiplier is biggest when the clock sits in your off hand.

Because the XP comes from playing rather than from one specific action, Chronos has anti-AFK logic built in. If you stand still and stop doing varied things, your position variance drops and the plugin cuts your Chronos XP to a fraction until you move again. Doing several different kinds of things inside a short window pays a bonus instead, and night time pays a little more than day.

The 13 adaptations split into two groups. Some are consumable or gesture based: a bottle that stores time and dumps it into furnaces and crops, a thrown chrono bomb, a stasis bubble, two different rewinds. The rest are quiet passives that make you harder to kill, stretch your potion buffs, or fast-forward the blocks around you while you work.

Chronos also has its own advancement chain for hours online, distance travelled, beds used, and ender pearl teleports.

## Adaptations

Everything below only runs when you have learned the adaptation to level 1 or higher, the skill and the adaptation are both enabled in config, you hold the `adapt.use` permission, and any protection plugin or region policy allows the action. Those conditions are not repeated per entry.

### Time In A Bottle (`chronos-time-bottle`)

A craftable bottle that slowly fills with stored time while you carry it, then dumps that time into something that would otherwise take a while. Furnaces, smokers, blast furnaces, brewing stands, campfires, growable blocks, and any Ageable entity such as a baby cow are all valid targets. It is the closest thing Adapt has to a personal fast-forward button.

The recipe is shapeless and the plugin rejects the craft unless the potion in the grid is an actual Swiftness Potion, even though the recipe itself accepts any potion item.

Spending time on a sapling can grow a whole tree. Before that happens the plugin checks every block the tree would occupy, fires a `StructureGrowEvent`, and cancels the entire tree if any block or any listening plugin says no.

**How to use it**

1. Learn it in the Adapt menu.
2. Craft the bottle: Swiftness Potion, Clock, Glass Bottle, shapeless.
3. Carry it. It charges on its own, once per second, up to the stored-time cap for your level.
4. Right-click the furnace, brewing stand, campfire, growable block, or baby animal you want to speed up.

On Folia you have to click the block directly. The air-click variant that ray-traces to a block is disabled there. Off Folia, an air click that finds a block still has to pass a normal right-click-block check first; furnaces and brewing stands additionally need container access, and campfires and growables need block-place permission.

### Aberrant Touch (`chronos-aberrant-touch`)

Every melee hit you land smears slowness onto the target, and the stacks build up. At 5 stacks the target is rooted in place for a moment. Each proc eats hunger, so you cannot spam it while starving, and PvP targets get much tighter duration and amplifier caps than mobs do.

It works on its own once learned. Hit things.

### Instant Recall (`chronos-instant-recall`)

Click with a clock and you snap back to where you were a few seconds ago with your health and hunger restored to what they were then. The clock is consumed and you lose half your remaining health, but the recall will never kill you: health is floored at 1. Your inventory is not rolled back.

**How to use it**

1. Learn it in the Adapt menu.
2. Hold a clock in either hand.
3. Left-click or right-click, air or block. Both clicks and both target kinds are on by default.

Three other triggers exist and are off by default: sprint plus click, a single sneak press, and a double-tap jump. Turn them on in the adaptation's config file if you prefer them to plain clicking.

### Time Bomb (`chronos-time-bomb`)

A thrown chrono bomb. It is a lingering potion item under the hood, so you throw it the vanilla way, and where it lands a temporal field opens up. Everything inside is slowed and given mining fatigue, players in the air are pinned, and projectiles entering the field stop dead. You get slowed by your own field too, just less.

**How to use it**

1. Learn it in the Adapt menu.
2. Craft the bomb: Clock, Snowball, Diamond, Sand, shapeless.
3. Right-click to throw it.
4. Wait out the cooldown. Trying to throw early plays a reject sound and cancels the throw.

### Temporal Echo (`chronos-temporal-echo`)

Projectiles you fire get a second life. A short delay after the shot, the same projectile is replayed at reduced velocity. Handy for arrow volleys and for anything where a second hit at the same angle is worth having.

It works on its own once learned. Fire something.

### Stasis Field (`chronos-stasis-field`)

Drops a bubble around you that freezes projectiles in midair and pins mobs inside it. Mobs get heavy slowness and a jump lock, so nothing walks or hops out. The amethyst shard you cast with is consumed.

**How to use it**

1. Learn it in the Adapt menu.
2. Hold an amethyst shard.
3. Sneak and right-click.

By default frozen projectiles get their motion back when the bubble expires rather than being deleted.

### Rewind (`chronos-rewind`)

A two-press panic button. The first press marks the moment. If you press again before the window closes, you snap back to that spot with the health and hunger you had at the mark. Each completed rewind costs food.

**How to use it**

1. Learn it in the Adapt menu.
2. Sneak and press the swap-hands key (F by default) to mark the moment.
3. Sneak and press it again within the window to rewind.

The cooldown after a rewind shrinks as the adaptation levels up, down to a floor.

### Borrowed Time (`chronos-borrowed-time`)

Part of every hit you take is deferred instead of applied immediately, then drained back out of you one pulse per second afterwards. It buys you a couple of seconds to heal or run. Damage that is already deferred cannot be deferred again, so it does not spiral.

Works on its own once learned.

### Overtime (`chronos-overtime`)

Beneficial potion effects applied to you last longer. The extension is a fraction of the original duration and scales with level, with a cap on how many bonus ticks any single effect can gain. Once the adaptation is at max level, harmful effects applied to you are cut to half duration as well.

Works on its own once learned.

### Accelerate (`chronos-accelerate`)

A quiet aura that pulses around you and nudges time forward on whatever it samples. Crops advance a growth stage, furnaces and smokers and blast furnaces and brewing stands jump forward a chunk of their remaining cook or brew time. You do not aim it; it just makes working near your farm and your furnace row faster.

Each sampled block is only touched if you and the block are on the same region thread and the normal interaction checks pass. Crops additionally need block-place permission, and processing stations need container access.

Works on its own once learned.

### Hourglass Guard (`chronos-hourglass-guard`)

A death save. A blow that would kill you leaves you at half a heart instead, gives you a brief window of invulnerability, and slows the enemies standing around you. The cooldown is measured in minutes and drops with level, so it is a once-per-fight lifeline, not something to plan around.

Works on its own once learned. It caps at level 3 rather than 5.

### Pocket Watch (`chronos-pocket-watch`)

Turns any fall into a controlled drift. You get a slow-fall budget measured in seconds per airtime, and it refills when you land, so it covers a cliff drop but not an indefinite hover.

**How to use it**

1. Learn it in the Adapt menu.
2. Keep a clock anywhere in your inventory.
3. Hold sneak while falling, after you have dropped far enough for it to engage.

### Deja Vu (`chronos-deja-vu`)

Your body remembers recent pain. Taking the same damage cause again within a short window hurts noticeably less, and every repeat hit refreshes the memory. Good against anything that grinds you down with one repeated damage type.

Works on its own once learned.

## Reference

Every adaptation config file also carries the shared keys `enabled`, `permanent`, `showParticles`, and `showSounds`.

### Identity

| Property | Value |
|----------|-------|
| Skill id | `chronos` |
| Class | `SkillChronos` |
| Icon | `CLOCK` |
| Color | `AQUA` |
| Interval (ms) | `5050` (from `setInterval`) |
| Skill config | `plugins/Adapt/adapt/skills/chronos.toml` |
| Adaptation count | 13 |

### XP sources

| Source | Stat side effects |
|--------|-------------------|
| Active movement per pulse | `minutes.online`, `chronos.active.distance` |
| Passive activity while recently active | none |
| Survival time, checked once per minute | none |
| Entering a bed (`PlayerBedEnterEvent`, result `OK` only) | `chronos.beds.used` |
| Speed potion drunk, splashed, or lingering cloud applied to a player | none |
| Ender pearl thrown (`ProjectileLaunchEvent`) | none |
| Ender pearl teleport arrival (`PlayerTeleportEvent`) | `chronos.teleports` |

Clock multipliers apply to every one of these. Lingering Speed clouds are stamped with a persistent-data key so one cloud pays once.

### Milestones

| Advancement key | Stat key | Threshold | Reward |
|-----------------|----------|-----------|--------|
| `challenge_chronos_1h` | `minutes.online` | 60 | `challengeChronosReward` |
| `challenge_chronos_24h` | `minutes.online` | 1440 | `challengeChronosReward` x2 |
| `challenge_active_dist_1k` | `chronos.active.distance` | 1000 | `challengeChronosReward` |
| `challenge_active_dist_10k` | `chronos.active.distance` | 10000 | `challengeChronosReward` x2 |
| `challenge_active_dist_100k` | `chronos.active.distance` | 100000 | `challengeChronosReward` x5 |
| `challenge_beds_10` | `chronos.beds.used` | 10 | `challengeChronosReward` |
| `challenge_beds_100` | `chronos.beds.used` | 100 | `challengeChronosReward` x2 |
| `challenge_chronos_tp_50` | `chronos.teleports` | 50 | `challengeChronosReward` |
| `challenge_chronos_tp_500` | `chronos.teleports` | 500 | `challengeChronosReward` x2 |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/chronos.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `setInterval` | `5050` | Milliseconds between Chronos pulses that award movement, passive, and survival XP. |
| `enabled` | `true` | Turns the whole skill on or off. |
| `skillColor` | `"&b"` | Legacy ampersand color code for this skill in menus and text. |
| `minimumMovementForActiveCheck` | `0.35` | Blocks moved since the last pulse before the pulse counts as active movement. |
| `distancePerBonusXP` | `5` | Blocks travelled that equal one unit of `activeMovementXP`. |
| `activeMovementXP` | `3.5` | XP per `distancePerBonusXP` blocks of active movement. |
| `activeMovementXPCapPerTick` | `6` | Ceiling on movement XP per pulse, scaled by actual elapsed time. |
| `clockOffhandXpMultiplier` | `3` | Multiplier on all Chronos XP while a clock is in the off hand. Takes precedence over the inventory multiplier. |
| `clockInventoryXpMultiplier` | `2` | Multiplier on all Chronos XP while a clock is anywhere in the inventory and none is in the off hand. |
| `positionHistorySize` | `12` | Recent positions kept for the AFK variance check. |
| `afkVarianceThreshold` | `2.0` | Mean distance from the average position, in blocks, below which you may be judged AFK. |
| `afkMinActionTypes` | `3` | Distinct recent action categories that keep you out of the AFK penalty. |
| `afkPenaltyMultiplier` | `0.03` | Multiplier applied to Chronos XP while judged AFK. |
| `passiveActiveXP` | `0.4` | Base XP per pulse for having done anything inside the activity window. |
| `activityWindow` | `15000` | Milliseconds an action stays counted for passive XP and for the diversity bonus. |
| `activityTypesForBonus` | `4` | Distinct action categories inside the window that trigger the diversity bonus. |
| `activityBonusMultiplier` | `1.5` | Multiplier on passive XP once the diversity threshold is met. |
| `nightActivityMultiplier` | `1.3` | Multiplier on passive XP while world time is between 12542 and 23460. |
| `sleepXP` | `150` | XP for a successful bed entry. |
| `sleepCooldown` | `30000` | Milliseconds before another bed entry can pay out. |
| `speedPotionBaseXP` | `120` (`DEFAULT_SPEED_POTION_BASE_XP`) | Base XP for applying Speed to yourself or another player. |
| `speedPotionLevelMultiplier` | `1.5` | Multiplier applied when the Speed amplifier is 1 or higher. |
| `speedPotionRewardCooldown` | `1000` | Minimum milliseconds between Speed potion payouts from one player. |
| `speedPotionDiminishingDecay` | `0.15` | Fraction shaved per consecutive Speed payout, compounding. |
| `speedPotionDiminishingFloor` | `0.25` | Lowest multiplier the diminishing chain can reach. |
| `speedPotionResetWindow` | `300000` | Milliseconds without a Speed payout before the consecutive counter resets. |
| `enderPearlThrowXP` | `35` | XP for throwing an ender pearl. |
| `enderPearlTeleportXP` | `15` | XP on arriving from an ender pearl teleport. |
| `enderPearlCooldown` | `10000` | Milliseconds before another pearl throw can pay out. |
| `survivalXPPerMinute` | `3` | XP per minute survived since your last death. |
| `survivalStreakBonusPerHour` | `0.2` | Extra survival multiplier earned per continuous hour alive. |
| `survivalStreakHourCap` | `5` | Hours counted toward the survival multiplier before it stops growing. |
| `challengeChronosReward` | `500` | Base knowledge reward for the Chronos challenge chain. |

A `speedPotionBaseXP` of exactly `45` in an existing config is rewritten to `120` on load; that value was the old default.

### Time In A Bottle

| Property | Default |
|----------|---------|
| Class | `ChronosTimeInABottle` |
| Icon | `CLOCK` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 6 |
| Cost factor | 0.35 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-time-bottle.toml` |

Recipe key `chronos-time-bottle`, shapeless: `CLOCK` + `POTION` + `GLASS_BOTTLE`, with a craft-time check that the potion is `SWIFTNESS`.

Milestones: `challenge_chronos_bottle_seconds_1k` and `challenge_chronos_bottle_seconds_25k` on `chronos.time-bottle.seconds-spent` at 1000 and 25000, rewarding 500 and 2000.

Listened events:

- `PlayerQuitEvent` (`on`): clears charge state
- `CraftItemEvent` (`on`): rejects the craft without a Swiftness Potion
- `PlayerItemConsumeEvent` (`on`)
- `PlayerInteractEvent` (`on`): block or air click
- `PlayerInteractEntityEvent` (`on`): right-click on an entity

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Plays the clock charge, spend, and reject sounds. |
| `baseMaxStoredSeconds` | `900` | Stored-time cap in seconds at level 0. |
| `maxStoredSecondsPerLevel` | `180` | Extra stored-time cap in seconds per level. |
| `chargePerSecond` | `0.1` | Stored seconds gained per real second of carrying. |
| `chargePerSecondPerLevel` | `0.02` | Extra stored seconds gained per real second per level. |
| `maxPlayersPerPass` | `32` | Learned players charged in one charge pass. |
| `baseCookTicksPerStoredSecond` | `20` | Furnace cook ticks bought per stored second at level 0. |
| `cookTicksPerSecondPerLevel` | `3` | Extra furnace cook ticks per stored second per level. |
| `maxCookTicksPerUse` | `140` | Cook ticks a single furnace click can add at level 0. |
| `maxCookTicksPerUsePerLevel` | `35` | Extra cook ticks per click per level. |
| `furnaceSpendMultiplier` | `1` | Stored-time price multiplier for furnace targets. |
| `baseBrewingTicksPerStoredSecond` | `20` | Brewing ticks bought per stored second at level 0. |
| `brewingTicksPerSecondPerLevel` | `3` | Extra brewing ticks per stored second per level. |
| `maxBrewingTicksPerUse` | `140` | Brewing ticks a single click can add at level 0. |
| `maxBrewingTicksPerUsePerLevel` | `35` | Extra brewing ticks per click per level. |
| `brewingSpendMultiplier` | `1.05` | Stored-time price multiplier for brewing stands. |
| `baseCampfireTicksPerStoredSecond` | `20` | Campfire cook ticks bought per stored second at level 0. |
| `campfireTicksPerSecondPerLevel` | `3` | Extra campfire ticks per stored second per level. |
| `maxCampfireTicksPerUse` | `160` | Campfire ticks a single click can add at level 0. |
| `maxCampfireTicksPerUsePerLevel` | `40` | Extra campfire ticks per click per level. |
| `campfireSpendMultiplier` | `0.9` | Stored-time price multiplier for campfires. |
| `baseEntityAgeTicksPerStoredSecond` | `20` | Age ticks bought per stored second at level 0. |
| `entityAgeTicksPerSecondPerLevel` | `4` | Extra age ticks per stored second per level. |
| `maxEntityAgeTicksPerUse` | `180` | Age ticks a single click can add at level 0. |
| `maxEntityAgeTicksPerUsePerLevel` | `55` | Extra age ticks per click per level. |
| `entitySpendMultiplier` | `1.35` | Stored-time price multiplier for Ageable entities. |
| `maxGrowthStepsPerUse` | `6` | Growth stages a single click can advance at level 0. |
| `maxGrowthStepsPerUsePerLevel` | `2` | Extra growth stages per click per level. |
| `allowSaplingTreeGeneration` | `true` | Lets a fully grown sapling attempt to generate a tree. |
| `saplingGrowChanceBase` | `0.18` | Chance per attempt that a sapling advances, 0-1. |
| `saplingGrowChancePerLevel` | `0.04` | Extra sapling advance chance per level, 0-1. |
| `growthCostMultiplier` | `1` | Global multiplier on the stored-second price of one growth step. |
| `growthCostReductionPerLevel` | `0.05` | Fraction shaved off the growth price per level. |
| `minGrowthCostLevelScale` | `0.45` | Floor on the level-based growth price multiplier. |
| `minGrowthStepSeconds` | `0.06` | Minimum stored seconds charged for one growth step. |
| `saplingGrowthSteps` | `2` | Stages assumed for a sapling when pricing growth. |
| `stemGrowthSteps` | `7` | Stages assumed for melon and pumpkin stems. |
| `berryGrowthSteps` | `3` | Stages assumed for berry bushes. |
| `vineGrowthSteps` | `5` | Stages assumed for vines. |
| `caveVineGrowthSteps` | `5` | Stages assumed for cave vines. |
| `kelpGrowthSteps` | `5` | Stages assumed for kelp. |
| `defaultGrowthSteps` | `4` | Stages assumed for anything not listed. |
| `cropNaturalSeconds` | `300` | Seconds a crop is assumed to take to grow naturally. |
| `netherWartNaturalSeconds` | `420` | Seconds nether wart is assumed to take naturally. |
| `saplingNaturalSeconds` | `900` | Seconds a sapling is assumed to take naturally. |
| `stemNaturalSeconds` | `660` | Seconds a stem is assumed to take naturally. |
| `berryBushNaturalSeconds` | `260` | Seconds a berry bush is assumed to take naturally. |
| `vineNaturalSeconds` | `300` | Seconds a vine is assumed to take naturally. |
| `caveVineNaturalSeconds` | `280` | Seconds a cave vine is assumed to take naturally. |
| `kelpNaturalSeconds` | `240` | Seconds kelp is assumed to take naturally. |
| `defaultGrowableNaturalSeconds` | `420` | Seconds assumed for anything not listed. |
| `cropCostMultiplier` | `1` | Price multiplier for crops. |
| `netherWartCostMultiplier` | `1.2` | Price multiplier for nether wart. |
| `saplingCostMultiplier` | `2.2` | Price multiplier for saplings. |
| `stemCostMultiplier` | `1.4` | Price multiplier for stems. |
| `berryBushCostMultiplier` | `0.8` | Price multiplier for berry bushes. |
| `vineCostMultiplier` | `0.85` | Price multiplier for vines. |
| `caveVineCostMultiplier` | `0.9` | Price multiplier for cave vines. |
| `kelpCostMultiplier` | `0.75` | Price multiplier for kelp. |
| `defaultGrowableCostMultiplier` | `1` | Price multiplier for anything not listed. |
| `xpPerCookTick` | `0.08` | XP per furnace cook tick added. |
| `xpPerBrewTick` | `0.08` | XP per brewing tick added. |
| `xpPerCampfireTick` | `0.08` | XP per campfire tick added. |
| `xpPerEntityAgeTick` | `0.06` | XP per entity age tick added. |
| `xpPerGrowthStep` | `2` | XP per growth stage advanced. |
| `maxXPPerUse` | `55` | Ceiling on XP from a single spend. |

The stored-second price of one growth step is `naturalSeconds / steps`, times the profile multiplier, times `growthCostMultiplier`, times the level scale, floored at `minGrowthStepSeconds`.

### Aberrant Touch

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

Milestone: `challenge_chronos_aberrant_500` on `chronos.aberrant-touch.slowness-stacks-applied` at 500, rewarding 400. A second advancement, `challenge_chronos_aberrant_frozen`, has no milestone threshold and is granted directly in code.

Listened events:

- `EntityDamageByEntityEvent` (`on`): melee or projectile hit

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Plays the touch sounds. |
| `durationAddTicks` | `30` | Slowness ticks added per hit at level 0. |
| `durationPerLevelTicks` | `6` | Extra slowness ticks per hit per level. |
| `playerDurationCapTicks` | `80` | Hard cap on slowness ticks against a player. |
| `playerAmplifierCap` | `1` | Hard cap on slowness amplifier against a player. |
| `entityDurationCapTicks` | `120` | Cap on slowness ticks against a non-player at level 0. |
| `entityDurationCapPerLevelTicks` | `10` | Extra non-player slowness tick cap per level. |
| `entityAmplifierCap` | `4` | Cap on slowness amplifier against a non-player. |
| `hungerCost` | `1.0` | Food points spent per proc. |
| `minimumFoodLevel` | `4` | Food level required for the touch to proc at all. |
| `rootAtStacks` | `5` | Stacks on one target that trigger the root. |
| `rootDurationTicks` | `20` | Root duration in ticks. |
| `rootAmplifier` | `10` | Slowness amplifier used for the root. |
| `stackResetMillis` | `2500` | Milliseconds without a hit before a target's stacks clear. |
| `cooldownMillis` | `250` | Minimum milliseconds between procs. |
| `xpPerProc` | `4` | XP per proc at level 0. |
| `xpPerLevel` | `1.25` | Extra XP per proc per level. |

### Instant Recall

| Property | Default |
|----------|---------|
| Class | `ChronosInstantRecall` |
| Config class | `ChronosInstantRecallConfig` |
| Icon | `RECOVERY_COMPASS` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.45 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-instant-recall.toml` |

Milestones: `challenge_chronos_recall_50` and `challenge_chronos_recall_1k` on `chronos.instant-recall.recalls` at 50 and 1000, rewarding 300 and 1500. `challenge_chronos_recall_cheat_death` has no milestone threshold and is granted directly in code.

Listened events:

- `PlayerQuitEvent` (`on`)
- `PlayerJoinEvent` (`on`)
- `PlayerTeleportEvent` (`on`)
- `PlayerChangedWorldEvent` (`on`)
- `PlayerInteractEvent` (`on`): click triggers
- `PlayerToggleSneakEvent` (`on`): single-sneak trigger
- `EntityDamageEvent` (`on`)
- `PlayerMoveEvent` (`on`): snapshot capture
- `PlayerMoveEvent` (`onDoubleJumpMove`): double-jump trigger

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Plays the recall sounds. |
| `consumeClock` | `true` | Takes one clock from the casting hand when a recall starts. |
| `healthCostFraction` | `0.5` | Fraction of current health lost on a completed recall. Health is floored at 1.0, so it is never lethal. |
| `showRewindTraceParticles` | `true` | Draws the trace along the rewind path. |
| `rewindTracePoints` | `18` | Points sampled along the rewind path for the trace. |
| `rewindAnimationDurationMillis` | `1000` | Target length of the rewind animation in milliseconds. |
| `rewindAnimationTicks` | `18` | Legacy tick count used only when the duration value is invalid. |
| `rewindUseTemporarySpectator` | `true` | Puts the player in spectator during the animation so the camera passes through blocks. |
| `rewindUseClientCamera` | `true` | Anchors a client-side camera during the rewind so the server position only moves at the end. |
| `rewindTeleportXpSuppressExtraTicks` | `10` | Extra ticks after a recall during which teleport XP and stats are suppressed. |
| `enableClockClickTrigger` | `true` | Enables plain click-with-clock activation. |
| `clockClickLeftClick` | `true` | Allows left-click for the clock-click trigger. |
| `clockClickRightClick` | `true` | Allows right-click for the clock-click trigger. |
| `enableSprintClickTrigger` | `false` | Enables sprint-plus-click activation. |
| `sprintClickLeftClick` | `false` | Allows left-click for the sprint-click trigger. |
| `sprintClickRightClick` | `true` | Allows right-click for the sprint-click trigger. |
| `allowAirClicks` | `true` | Lets air clicks fire the enabled click triggers. |
| `allowBlockClicks` | `true` | Lets block clicks fire the enabled click triggers. |
| `enableSingleSneakTrigger` | `false` | Enables one sneak press as an activation. |
| `singleSneakRequiresSprint` | `false` | Requires sprinting for the single-sneak trigger. |
| `singleSneakRequiresClockInHand` | `true` | Requires a clock in either hand for the single-sneak trigger. |
| `enableDoubleJumpTrigger` | `false` | Enables double-tap jump as an activation. |
| `doubleJumpRequiresSprint` | `false` | Requires sprinting for the double-jump trigger. |
| `doubleJumpRequiresClockInHand` | `true` | Requires a clock in either hand for the double-jump trigger. |
| `baseRewindSeconds` | `3.5` | Seconds rewound at level 0. |
| `rewindSecondsPerLevel` | `0.35` | Extra seconds rewound per level. |
| `maxRewindSeconds` | `5` | Hard cap on rewind seconds. |
| `cooldownPaddingSeconds` | `1` | Seconds added on top of the rewind window to form the cooldown. |
| `snapshotIntervalMillis` | `50` | Milliseconds between position snapshots. |
| `historyPaddingSeconds` | `2` | Extra seconds of snapshot history kept beyond the max rewind window. |
| `rewindProtectionTicks` | `25` | Ticks of damage protection after a rewind lands. |
| `xpPerDistanceBlock` | `0.35` | XP per block of distance recovered. |
| `xpPerHealthPoint` | `0.85` | XP per health point restored. |
| `xpPerHungerPoint` | `0.7` | XP per food point restored. |
| `xpPerSaturationPoint` | `0.18` | XP per saturation point restored. |
| `xpLevelMultiplierPerLevel` | `0.08` | Extra XP multiplier per adaptation level. |
| `xpMinRawReward` | `1.35` | Raw reward below which the recall pays nothing beyond the minimum. |
| `xpMinAward` | `0.5` | Floor on XP for a paying recall. |
| `xpMaxAward` | `36` | Ceiling on XP for one recall. |
| `xpCrossWorldDistanceCredit` | `16` | Distance in blocks credited when the recall crosses worlds. |
| `xpDiminishWindowMillis` | `45000` | Window in which repeated recalls lose value. |
| `xpDiminishMinMultiplier` | `0.18` | Floor on the diminishing multiplier. |
| `xpRepeatWindowMillis` | `180000` | Window in which recalling between the same two spots counts as a repeat. |
| `xpRepeatSourceRadius` | `3.5` | Blocks within which a start point counts as the same start point. |
| `xpRepeatTargetRadius` | `3.5` | Blocks within which a destination counts as the same destination. |
| `xpRepeatPenaltyMultiplier` | `0.2` | Multiplier applied to XP for a repeated route. |

### Time Bomb

| Property | Default |
|----------|---------|
| Class | `ChronosTimeBomb` |
| Icon | `TNT` |
| Max level | 5 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 8 |
| Cost factor | 0.42 |
| Tick interval (ms) | idle until a field is active, then 50 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-time-bomb.toml` |

Recipe key `chronos-time-bomb`, shapeless: `SNOWBALL` + `CLOCK` + `DIAMOND` + `SAND`, producing a lingering potion item.

Milestone: `challenge_chronos_bomb_freeze_50` on `chronos.time-bomb.projectiles-frozen` at 50, rewarding 500. `challenge_chronos_bomb_crowd_8` has no milestone threshold; it is granted directly in code when one field slows 8 entities.

Listened events:

- `PlayerQuitEvent` (`on`)
- `PlayerJoinEvent` (`on`)
- `PlayerInteractEvent` (`on`): right-click air or block, for the cooldown gate
- `ProjectileLaunchEvent` (`on`): arms the thrown bomb
- `LingeringPotionSplashEvent` (`on`): opens the field
- `EntitiesLoadEvent` (`on`): sweeps stale frozen stamps

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Plays the arm, tick, and reject sounds. |
| `baseRadius` | `6` | Field radius in blocks at level 0. |
| `radiusPerLevel` | `1.5` | Extra field radius in blocks per level. |
| `baseDurationTicks` | `60` | Field lifetime in ticks at level 0. |
| `durationPerLevelTicks` | `25` | Extra field lifetime in ticks per level. |
| `cooldownMillis` | `15000` | Milliseconds between throws. |
| `fieldCenterYOffset` | `1.25` | Blocks the field center sits above the impact point. |
| `slownessAmplifier` | `2` | Slowness amplifier applied to entities in the field. |
| `casterSlownessAmplifier` | `1` | Slowness amplifier applied to the thrower. |
| `fatigueAmplifier` | `1` | Mining fatigue amplifier applied in the field. |
| `freezePlayersInAir` | `true` | Pins airborne players in place instead of only slowing them. |
| `accumulateFrozenImpulse` | `true` | Stores the motion a frozen entity tried to apply and releases it on thaw. |
| `frozenImpulseMinMagnitude` | `0.03` | Motion below this magnitude is not stored. |
| `frozenImpulseSampleCap` | `2.8` | Ceiling on a single stored motion sample. |
| `frozenImpulseReleaseCap` | `7.5` | Ceiling on the motion released when an entity thaws. |
| `effectRefreshTicks` | `24` | Duration in ticks of each reapplied effect pulse. |
| `showFieldSphere` | `true` | Draws the sphere outline. |
| `fieldSphereParticleCount` | `280` | Particles requested per sphere refresh, before per-field caps. |
| `fieldSphereRefreshMillis` | `100` | Milliseconds between sphere refreshes. |
| `fieldTickSoundIntervalMillis` | `325` | Starting milliseconds between field tick sounds. |
| `fieldTickMinIntervalMillis` | `70` | Shortest the field tick interval can get. |
| `fieldTickPitchStart` | `0.42` | Pitch of the first field tick. |
| `fieldTickPitchEnd` | `1.96` | Pitch of the last field tick. |
| `fieldTickPitchCurveExponent` | `3.75` | Exponent shaping the pitch ramp. Higher values hold the low pitch longer. |
| `fieldTickAccelerationFactor` | `0.82` | Multiplier applied to the tick interval each tick, speeding the sound up. |
| `maxActiveFields` | `32` | Concurrent temporal fields the server will run. |
| `fieldScanIntervalMillis` | `250` | Milliseconds between entity scans per field. |
| `maxRegionScansPerCycle` | `64` | Loaded-region scans shared by all fields per cycle. |
| `maxRegionScansPerField` | `8` | Loaded-region scans one field gets per cycle. |
| `maxEntitiesPerScanCycle` | `512` | Entities scheduled for evaluation per cycle across all fields. |
| `maxEntitiesPerField` | `64` | Entities one field evaluates per cycle. |
| `maxEntitiesPerRegion` | `64` | Entities read from one owned region during a scan. |
| `maxFrozenEntitiesPerTick` | `256` | Frozen non-player entities reconciled per tick. |
| `maxFrozenPlayersPerTick` | `128` | Frozen players reconciled per tick. |
| `maxFieldFxParticlesPerTick` | `512` | Field particles shared by all fields per tick. |
| `maxFieldFxParticlesPerField` | `48` | Field particles one field gets per tick. |
| `maxFieldSphereParticlesPerField` | `36` | Sphere particles one field emits per refresh. |
| `maxFreezeFxParticlesPerScan` | `256` | Freeze-impact particles shared by all fields per scan cycle. |
| `maxFreezeFxParticlesPerField` | `24` | Freeze-impact particles one field gets per scan cycle. |
| `xpOnCast` | `28` | XP per bomb thrown at level 0. |
| `xpPerLevel` | `3` | Extra XP per bomb per level. |

### Temporal Echo

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

Milestone: `challenge_chronos_echo_200` on `chronos.temporal-echo.echo-hits` at 200, rewarding 400.

Listened events:

- `ProjectileLaunchEvent` (`on`): records the shot to replay
- `ProjectileHitEvent` (`on`): scores the echo hit

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `echoDelayTicksBase` | `18` | Ticks between the original shot and the echo at level 0. |
| `echoDelayTicksFactor` | `10` | Tick adjustment applied per level to the echo delay. |
| `echoVelocityFactorBase` | `0.45` | Fraction of the original velocity the echo starts with at level 0. |
| `echoVelocityFactorFactor` | `0.45` | Velocity fraction gained per level. |
| `maxEchoVelocityFactor` | `0.92` | Ceiling on the echo velocity fraction. |
| `cooldownMillisBase` | `5000` | Milliseconds between echoes at level 0. |
| `cooldownMillisFactor` | `2600` | Milliseconds of cooldown removed as level rises. |
| `xpPerEcho` | `12` | XP per echo produced. |

### Stasis Field

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

Milestones: `challenge_chronos_stasis_50` and `challenge_chronos_stasis_500` on `chronos.stasis-field.casts` at 50 and 500, rewarding 400 and 1500.

Listened events:

- `PlayerQuitEvent` (`on`)
- `EntityRemoveEvent` (`on`): drops bookkeeping for removed frozen entities
- `PlayerInteractEvent` (`on`): sneak plus right-click cast

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Plays the bubble sounds. |
| `consumeShard` | `true` | Takes the amethyst shard on cast. |
| `baseRadius` | `3.5` | Bubble radius in blocks at level 0. |
| `radiusPerLevel` | `0.75` | Extra bubble radius in blocks per level. |
| `baseDurationMillis` | `3000` | Bubble lifetime in milliseconds at level 0. |
| `durationPerLevelMillis` | `750` | Extra bubble lifetime in milliseconds per level. |
| `cooldownMillis` | `20000` | Milliseconds between casts. |
| `centerYOffset` | `1` | Blocks the bubble center sits above the caster. |
| `slownessAmplifier` | `5` | Slowness amplifier applied to mobs inside. |
| `jumpLockAmplifier` | `-6` | Jump strength reduction applied to mobs inside, scaling linearly from 0 (none) to -6 (full lock). Positive values apply nothing. |
| `effectRefreshTicks` | `20` | Duration in ticks of each reapplied stasis pulse. |
| `pulseIntervalMillis` | `250` | Milliseconds between bubble scans. |
| `removeProjectilesOnExpire` | `false` | Deletes frozen projectiles on expiry instead of giving their motion back. |
| `outlineParticleCount` | `10` | Outline particles requested per refresh. |
| `outlineRefreshMillis` | `400` | Milliseconds between outline refreshes. |
| `xpOnCast` | `22` | XP per admitted cast at level 0. |
| `xpPerLevel` | `3` | Extra XP per cast per level. |

### Rewind

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

Milestones: `challenge_chronos_rewind_50` and `challenge_chronos_rewind_500` on `chronos.rewind.rewinds` at 50 and 500, rewarding 350 and 1400.

Listened events:

- `PlayerQuitEvent` (`on`)
- `PlayerSwapHandItemsEvent` (`on`): mark and rewind gesture

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Plays the mark and rewind sounds. |
| `hungerCost` | `6` | Food points spent per completed rewind. |
| `snapshotWindowMillis` | `10000` | Milliseconds after marking during which the rewind can be completed. |
| `baseCooldownMillis` | `45000` | Cooldown in milliseconds after a rewind at level 0. |
| `cooldownReductionPerLevelMillis` | `4000` | Cooldown removed in milliseconds per level. |
| `minimumCooldownMillis` | `15000` | Floor on the cooldown regardless of level. |
| `xpOnRewind` | `18` | XP per completed rewind at level 0. |
| `xpPerLevel` | `3` | Extra XP per rewind per level. |

### Borrowed Time

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

Milestone: `challenge_chronos_borrowed_2500` on `chronos.borrowed-time.damage-deferred` at 2500, rewarding 900.

Listened events:

- `PlayerQuitEvent` (`on`)
- `PlayerJoinEvent` (`on`)
- `PlayerDeathEvent` (`on`): clears outstanding debt
- `EntityDamageEvent` (`on`): defers part of the hit

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseDeferFraction` | `0.1` | Fraction of a hit deferred at level 0. |
| `deferFractionPerLevel` | `0.06` | Extra deferred fraction per level. |
| `maxDeferFraction` | `0.45` | Ceiling on the deferred fraction. |
| `minimumDeferDamage` | `1.0` | Final damage a hit must reach before any of it is deferred. |
| `paybackPulses` | `10` | One-second pulses the deferred damage is repaid over. |

### Overtime

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

Milestone: `challenge_chronos_overtime_1k` on `chronos.overtime.seconds-extended` at 1000, rewarding 750.

Listened events:

- `PlayerQuitEvent` (`on`)
- `EntityPotionEffectEvent` (`onHarmfulEffect`): shortens harmful effects at max level
- `EntityPotionEffectEvent` (`on`): extends beneficial effects

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseExtensionPercent` | `0.05` | Fraction of the original duration added at level 0. |
| `extensionPercentPerLevel` | `0.07` | Extra added fraction per level. |
| `maxExtensionPercent` | `0.4` | Ceiling on the added fraction. |
| `maxBonusTicks` | `2400` | Ceiling on bonus ticks for any one effect application. |
| `minimumDurationTicks` | `60` | Original duration an effect needs before it qualifies. |
| `maximumBaseDurationTicks` | `72000` | Original duration above which an effect is left alone. |
| `xpPerBonusSecond` | `0.2` | XP per bonus second granted. |
| `maxXpPerExtension` | `8` | Ceiling on XP from one extension. |
| `halveHarmfulEffectsAtMaxLevel` | `true` | Shortens harmful effects applied to you once this adaptation is maxed. |
| `maxLevelHarmfulDurationMultiplier` | `0.5` | Fraction of the original duration a harmful effect keeps at max level. |

### Accelerate

| Property | Default |
|----------|---------|
| Class | `ChronosAccelerate` |
| Icon | `SUGAR` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.4 |
| Tick interval (ms) | `pulseIntervalMillis`, default 3000 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-accelerate.toml` |

Milestone: `challenge_chronos_accelerate_1k` on `chronos.accelerate.blocks-accelerated` at 1000, rewarding 600.

Listened events:

- `PlayerQuitEvent` (`on`)

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `pulseIntervalMillis` | `3000` | Milliseconds between acceleration pulses. Also sets the adaptation tick interval. |
| `baseRadius` | `4` | Aura radius in blocks at level 0. |
| `radiusPerLevel` | `0.8` | Extra aura radius in blocks per level. |
| `baseSamplesPerPulse` | `3` | Random blocks sampled per pulse at level 0. |
| `maxSamplesPerPulse` | `8` | Ceiling on blocks sampled per pulse. |
| `maxPlayersPerPass` | `512` | Learned players prepared in one pass. |
| `maxSamplesPerPass` | `8192` | Block jobs admitted in one pass across all players. |
| `baseGrowChance` | `0.3` | Chance a sampled crop advances a stage at level 0, 0-1. |
| `growChancePerLevel` | `0.06` | Extra crop growth chance per level, 0-1. |
| `baseCookBoostFraction` | `0.25` | Fraction of total cook or brew time skipped per hit at level 0. |
| `cookBoostFractionPerLevel` | `0.07` | Extra skipped fraction per level. |
| `maxCookBoostFraction` | `0.6` | Ceiling on the skipped fraction per hit. |
| `xpPerAcceleratedBlock` | `1.2` | XP per block actually accelerated. |

### Hourglass Guard

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

Milestone: `challenge_chronos_hourglass_10` on `chronos.hourglass-guard.saves` at 10, rewarding 800.

Listened events:

- `PlayerQuitEvent` (`on`)
- `EntityDamageEvent` (`on`): intercepts the killing blow

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `playClockSounds` | `true` | Plays the save sound. |
| `survivalHealth` | `1.0` | Health points you are left with after a save. |
| `invulnerabilityMillis` | `2000` | Milliseconds of invulnerability after a save. |
| `baseCooldownMillis` | `480000` | Cooldown in milliseconds at level 0. |
| `cooldownReductionPerLevelMillis` | `60000` | Cooldown removed in milliseconds per level. |
| `minimumCooldownMillis` | `180000` | Floor on the cooldown regardless of level. |
| `enemySlowRadius` | `5` | Radius in blocks for the slow applied on a save. |
| `enemySlowTicks` | `50` | Duration in ticks of that slow. |
| `enemySlowAmplifier` | `2` | Slowness amplifier of that slow. |
| `xpOnSave` | `60` | XP per save at level 0. |
| `xpPerLevel` | `10` | Extra XP per save per level. |

### Pocket Watch

| Property | Default |
|----------|---------|
| Class | `ChronosPocketWatch` |
| Icon | `FEATHER` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.35 |
| Tick interval (ms) | 250 |
| Config file | `plugins/Adapt/adapt/adaptations/chronos-pocket-watch.toml` |

Milestone: `challenge_chronos_pocket_watch_500` on `chronos.pocket-watch.slow-fall-seconds` at 500, rewarding 650.

Listened events:

- `PlayerQuitEvent` (`on`)

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseBudgetSeconds` | `2` | Seconds of slow falling available per airtime at level 0. |
| `budgetSecondsPerLevel` | `1` | Extra seconds of slow falling per level. |
| `pulseDurationTicks` | `15` | Duration in ticks of each slow falling pulse. |
| `minFallDistance` | `1.5` | Blocks you must have fallen before it engages. |
| `requireClock` | `true` | Requires a clock somewhere in the inventory. |
| `xpPerPulse` | `0.3` | XP per slow falling pulse. |
| `maxPlayersPerPass` | `512` | Learned players processed per pulse pass. |

### Deja Vu

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

Milestone: `challenge_chronos_deja_vu_500` on `chronos.deja-vu.damage-absorbed` at 500, rewarding 700.

Listened events:

- `EntityDamageEvent` (`on`): absorbs repeats of a familiar damage cause

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `memoryWindowMillis` | `8000` | Milliseconds a damage cause stays familiar. |
| `baseReductionFraction` | `0.15` | Fraction of a repeated hit absorbed at level 0. |
| `reductionFractionPerLevel` | `0.09` | Extra absorbed fraction per level. |
| `maxReductionFraction` | `0.6` | Ceiling on the absorbed fraction. |
| `xpPerAbsorbedDamage` | `0.8` | XP per point of damage absorbed. |
| `fxCooldownMillis` | `1500` | Minimum milliseconds between familiar-hit effects. |

### Support classes (not player adaptations)

- `ChronosInstantRecallConfig`: Instant Recall's trigger, rewind, cost, protection, and XP defaults.
- `ChronosInstantRecallTypes`: recall snapshots, XP calculation context, and repeat-reward stamps.
- `ChronosSoundFX`: schedules the clock, bottle, rewind, touch, bomb, and temporal-field sounds.
- `ChronosWorkBudget`: bounds and rotates batch work, catch-up pulses, and per-job allocation for Chronos runtimes.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
