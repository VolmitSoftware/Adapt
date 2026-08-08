# Skill: Seaborne

Skill id `seaborne`. Earn XP through swimming and fishing. Seaborne has 14 registered adaptations and uses the `TRIDENT` icon.

**XP sources:** swimming, underwater movement and mining, fishing, and aquatic combat.

**Milestones / challenges** (stat keys):

- `challenge_swim_1nm` tracking `move.swim`
- `challenge_swim_5k` tracking `move.swim`
- `challenge_swim_20k` tracking `move.swim`
- `challenge_fish_25` tracking `seaborne.fish.caught`
- `challenge_fish_250` tracking `seaborne.fish.caught`
- `challenge_drowned_25` tracking `seaborne.drowned.kills`
- `challenge_drowned_250` tracking `seaborne.drowned.kills`
- `challenge_guardian_10` tracking `seaborne.guardian.kills`
- `challenge_guardian_100` tracking `seaborne.guardian.kills`
- `challenge_underwater_blocks_100` tracking `seaborne.underwater.blocks`
- `challenge_underwater_blocks_1k` tracking `seaborne.underwater.blocks`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `seaborne` |
| Class | `SkillSeaborne` |
| Icon | `TRIDENT` |
| Color | `BLUE` |
| Interval (ms) | `2120` |
| Skill config | `plugins/Adapt/adapt/skills/seaborne.toml` |
| Adaptation count | 14 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/seaborne.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `seaPickleCooldown` | `60000` | Sea pickle cooldown. |
| `drownedDamageXpCooldown` | `1500` | Cooldown between XP awards for damaging drowned. |
| `tridentDamageXpCooldown` | `1500` | Cooldown between XP awards for trident damage. |
| `fishCaughtXp` | `250` | XP awarded for catching a fish. |
| `entityCaughtXp` | `10` | XP awarded for reeling in an entity. |
| `fishXpCooldown` | `5000` | Cooldown between fishing XP awards. |
| `tridentxpmultiplier` | `4.0` | Unitless multiplier applied to XP from trident multiplier. |
| `damagedrownxpmultiplier` | `3` | Unitless multiplier applied to XP from damagedrown multiplier. |
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&9"` | Legacy ampersand color code used for this skill in menus and text. |
| `challengeSwim1nmReward` | `750` | Reward for the swim 1 nm challenge. |
| `challengeSwim5kReward` | `1500` | Reward for the swim 5 k challenge. |
| `challengeSwim20kReward` | `3750` | Reward for the swim 20 k challenge. |
| `swimXP` | `0.4` | XP awarded for swim. |
| `waterBreathingSwimXpBonusMultiplier` | `1.0` | Additional passive swimming XP multiplier for players actively moving in water with Water Breathing or Conduit Power. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Organic Oxygen Tank (`seaborne-oxygen`)

Increases underwater air capacity.

**Runtime entry points:** periodic evaluation every 3750 ms.

**Menu displays:** Oxygen Capacity Increase.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeaborneOxygen` |
| Icon | `GLASS_PANE` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 3 |
| Cost factor | 0.525 |
| Tick interval (ms) | 3750 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-oxygen.toml` |

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `airPerLevelTics` | `15` | Air per level tics. Server ticks (20 ticks = 1 second). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Dolphin's Grace (`seaborne-speed`)

Gain passive water speed; sprint-swimming also applies Dolphin's Grace for a level-scaled duration.

**Runtime entry points:** while moving; on teleport.

**Menu displays:** Passive water-speed multiplier; Dolphin's Grace duration while sprint-swimming.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeaborneSpeed` |
| Icon | `PRISMARINE_CRYSTALS` |
| Max level | 7 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 0.525 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-speed.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving
- `PlayerTeleportEvent` (`on`) — on teleport
- `PlayerQuitEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Fisher's Fantasy (`seaborne-fishers-fantasy`)

Fishing can grant additional XP and fish.

**Runtime entry points:** while fishing; periodic evaluation every 8080 ms.

**Menu displays:** For each level there is a chance to get more XP and Fish.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeaborneFishersFantasy` |
| Icon | `FISHING_ROD` |
| Max level | 7 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 5 |
| Cost factor | 0.9 |
| Tick interval (ms) | 8080 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-fishers-fantasy.toml` |

Listened events:

- `PlayerFishEvent` (`on`) — while fishing

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Turtle's Vision (`seaborne-turtles-vision`)

While underwater, you gain Night Vision.

**Runtime entry points:** on potion effect change; periodic evaluation every 3000 ms.

**Menu displays:** Night Vision while underwater after Water Breathing expires.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeaborneTurtlesVision` |
| Icon | `DIAMOND_HORSE_ARMOR` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 5 |
| Cost factor | 1 |
| Tick interval (ms) | 3000 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-turtles-vision.toml` |

Listened events:

- `EntityPotionEffectEvent` (`on`) — on potion effect change
- `PlayerQuitEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Turtle Miner (`seaborne-turtles-mining-speed`)

Gain Haste III while mining underwater after Water Breathing expires; the effect stacks with Aqua Affinity.

**Runtime entry points:** when breaking blocks; periodic evaluation every 3000 ms.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeaborneTurtlesMiningSpeed` |
| Icon | `PRISMARINE_SHARD` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 15 |
| Cost factor | 1 |
| Tick interval (ms) | 3000 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-turtles-mining-speed.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Tidecaller (`seaborne-tidecaller`)

Surge forward with a water burst while in water or rain, triggered by sneaking or an attack swing depending on server settings.

**Runtime entry points:** on sneak toggle; on arm swing animation; periodic evaluation every 1600 ms.

**Menu displays:** Surge Distance; Surge Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeaborneTidecaller` |
| Icon | `HEART_OF_THE_SEA` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 1600 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-tidecaller.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerAnimationEvent` (`on`) — on arm swing animation

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `dashDistanceBase` | `6` | Base Dash distance. Blocks. |
| `dashDistanceFactor` | `8` | Dash distance factor. Blocks. |
| `cooldownTicksBase` | `140` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksFactor` | `80` | Cooldown ticks factor. Server ticks (20 ticks = 1 second). |
| `xpPerBurst` | `11` | XP awarded for xp per burst. |
| `allowRainTrigger` | `true` | Allows the original rain-based trigger for Tidecaller dashes. |
| `allowWaterTrigger` | `true` | Allows Tidecaller dashes while the player is in water. |
| `enableSneakTrigger` | `true` | Enables sneak-to-dash trigger. |
| `enableAttackTrigger` | `true` | Enables attack-swing trigger (any item or empty hand). |
| `attackTriggerRequiresSneak` | `false` | Requires sneaking for attack-swing trigger. |
| `attackTriggerWaterOnly` | `true` | Restricts attack-swing trigger to water states only. |
| `useVelocityDash` | `true` | Uses velocity-based dash movement instead of teleporting to a target. |
| `flattenVelocityDashDirection` | `false` | If true, removes pitch from velocity dash direction. |
| `velocityStrengthBase` | `1.05` | Base forward velocity strength of a velocity dash. |
| `velocityStrengthFactor` | `0.85` | Additional forward velocity strength gained by adaptation level. |
| `velocityVerticalBase` | `0.01` | Base vertical velocity contribution for velocity dashes. |
| `velocityVerticalFactor` | `0.05` | Additional vertical velocity contribution gained by adaptation level. |
| `velocityAdditive` | `true` | Adds dash velocity on top of current velocity when true. |
| `maxResultingVelocity` | `2.25` | Hard cap on resulting velocity magnitude after dash. |
| `blockDashWhenWallAhead` | `true` | Cancels dash if a solid block is detected directly ahead. |
| `wallCheckDistance` | `1.2` | Distance ahead used to detect blocking walls. |
| `applyForwardMomentumAfterDash` | `true` | Applies forward velocity after the dash teleport. |
| `forwardMomentum` | `1.05` | Horizontal forward momentum applied after the dash teleport. |
| `verticalMomentum` | `0.02` | Vertical momentum added or set after the dash teleport. |
| `replaceVerticalMomentum` | `false` | If true, replaces current vertical velocity with verticalMomentum. |
| `preserveSwimmingAfterDash` | `true` | Re-applies swimming pose after dash when the player started swimming and remains in water. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Pressure Diver (`seaborne-pressure-diver`)

Gain depth-based protection underwater and partially suppress mining fatigue pressure.

**Runtime entry points:** on taking damage; when breaking blocks; periodic evaluation every 20 ms while its conditions hold.

**Menu displays:** Minimum Depth Requirement; Depth Damage Reduction; Mining Fatigue Reduction Chance.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeabornePressureDiver` |
| Icon | `NAUTILUS_SHELL` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 20 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-pressure-diver.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage
- `PlayerQuitEvent` (`on`)
- `BlockBreakEvent` (`on`) — when breaking blocks
- `PlayerMoveEvent` (`on`) — updates depth protection and mining-fatigue suppression

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `depthThresholdBase` | `10` | Base Depth threshold. |
| `depthThresholdFactor` | `6` | Depth threshold factor. Unitless multiplier. |
| `deepThresholdBase` | `18` | Base Deep threshold. |
| `deepThresholdFactor` | `8` | Deep threshold factor. Unitless multiplier. |
| `damageReductionBase` | `0.12` | Base Damage reduction. health points (2 points = 1 heart). |
| `damageReductionFactor` | `0.26` | Damage reduction factor. Unitless multiplier. |
| `maxDamageReduction` | `0.45` | Maximum damage reduction. health points (2 points = 1 heart). |
| `fatigueTrimChanceBase` | `0.2` | Proc chance for fatigue trim chance base. decimal probability. |
| `fatigueTrimChanceFactor` | `0.45` | Proc chance for fatigue trim chance factor. decimal probability. |
| `fatigueTrimAmountBase` | `1` | Base Fatigue trim amount. |
| `fatigueTrimAmountFactor` | `1` | Fatigue trim amount factor. Unitless multiplier. |
| `effectTicks` | `60` | Effect ticks. Server ticks (20 ticks = 1 second). |
| `fatigueReplaceTicks` | `80` | Fatigue replace ticks. Server ticks (20 ticks = 1 second). |
| `xpPerDepthPulse` | `6` | XP awarded for xp per depth pulse. |
| `xpPulseCooldownMillis` | `3000` | Rate-limit or history window for XP from xp pulse cooldown millis. Milliseconds. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Coral Gardener (`seaborne-coral-gardener`)

Coral you place survives out of water far longer, bonemeal grows coral, and reef blocks grant bonus XP.

**Runtime entry points:** when placing blocks; on `BlockFadeEvent`; on block/entity/air interact (click).

**Menu displays:** Coral Survival Time; Bonemeal Growth Chance.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeaborneCoralGardener` |
| Icon | `BRAIN_CORAL_BLOCK` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.55 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-coral-gardener.toml` |

Listened events:

- `BlockPlaceEvent` (`on`) — when placing blocks
- `BlockFadeEvent` (`on`)
- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `survivalSecondsBase` | `60` | Base seconds placed coral survives out of water before it may fade. |
| `survivalSecondsFactor` | `240` | Additional survival seconds gained across levels. |
| `growthChanceBase` | `0.35` | Base chance bonemeal grows a new coral block from live coral. |
| `growthChanceFactor` | `0.5` | Additional bonemeal growth chance gained across levels. |
| `reefPlaceXp` | `8` | Bonus XP granted when placing a reef block. |
| `growthXp` | `14` | Bonus XP granted when bonemeal grows new coral. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Deep Salvager (`seaborne-deep-salvager`)

Underwater containers appear as private aqua glowing block displays and reward bonus treasure the first time you open them submerged.

**Runtime entry points:** on `InventoryOpenEvent`; periodic evaluation every 3000 ms.

**Menu displays:** Detection Range (blocks); Bonus Treasure Rolls.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeaborneDeepSalvager` |
| Icon | `CHEST` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Tick interval (ms) | 3000 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-deep-salvager.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `InventoryOpenEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `detectionRangeBase` | `4` | Base block range that underwater containers shimmer within. |
| `detectionRangeFactor` | `5` | Additional detection range at maximum level, in blocks. |
| `bonusRollsBase` | `1` | Base number of bonus treasure rolls added when salvaging a container. |
| `bonusRollsFactor` | `3` | Additional bonus treasure rolls gained across levels. |
| `salvageXp` | `12` | Bonus XP granted per bonus treasure item salvaged. |
| `shimmerScanCooldownMillis` | `3000` | Cooldown between underwater container shimmer scans per player. |
| `enableShimmer` | `true` | Enables the underwater container shimmer locator scan. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Ink Veil (`seaborne-ink-veil`)

Taking damage underwater bursts an ink cloud that blinds hostiles and briefly hides you from drowned and guardians.

**Runtime entry points:** on taking damage.

**Menu displays:** Ink Cloud Size (blocks); Ink Burst Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeaborneInkVeil` |
| Icon | `INK_SAC` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.55 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-ink-veil.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cloudSizeBase` | `4` | Base ink cloud radius in blocks. |
| `cloudSizeFactor` | `4` | Additional ink cloud radius gained across levels. |
| `cooldownMillisBase` | `12000` | Base cooldown in milliseconds between ink bursts. |
| `cooldownMillisReduction` | `8000` | Cooldown milliseconds removed at max level. |
| `invisTicksBase` | `40` | Base invisibility duration in ticks granted on a burst. |
| `invisTicksFactor` | `40` | Additional invisibility ticks gained across levels. |
| `blindTicksBase` | `60` | Base blindness duration in ticks applied to nearby hostiles. |
| `blindTicksFactor` | `60` | Additional blindness ticks gained across levels. |
| `burstXp` | `10` | Bonus XP granted when an ink cloud bursts. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Trident Mastery (`seaborne-trident-mastery`)

Tridents deal bonus damage and home back to you faster after a throw.

**Runtime entry points:** on melee/projectile hit (damage); when launching a projectile.

**Menu displays:** Bonus Trident Damage; Recall Speed.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeaborneTridentMastery` |
| Icon | `TRIDENT` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-trident-mastery.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `ProjectileLaunchEvent` (`on`) — when launching a projectile

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageBonusBase` | `0.15` | Base bonus trident damage as a fraction. |
| `damageBonusFactor` | `0.45` | Additional bonus trident damage fraction gained across levels. |
| `recallSpeedBase` | `0.8` | Base velocity applied when a thrown trident homes back to you. |
| `recallSpeedFactor` | `1.2` | Additional recall velocity gained across levels. |
| `flightGraceTicksBase` | `50` | Ticks a thrown trident is allowed to fly before recall forces its return. |
| `flightGraceTicksReduction` | `30` | Flight grace ticks removed at max level. |
| `recallDelayTicks` | `5` | Delay in ticks before recall first evaluates a thrown trident. |
| `enableRecall` | `true` | Enables the trident recall/homing return behavior. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Fish Whisperer (`seaborne-fish-whisperer`)

Fish school toward you, dolphins and axolotls assist your hunts, and you fish with a permanent Luck of the Sea tier.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 4000 ms.

**Menu displays:** Luck of the Sea Tier; Creature Affinity Range (blocks).

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeaborneFishWhisperer` |
| Icon | `TROPICAL_FISH` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Tick interval (ms) | 4000 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-fish-whisperer.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `schoolRangeBase` | `6` | Base block range fish school toward you within. |
| `schoolRangeFactor` | `8` | Additional fish schooling range gained across levels. |
| `schoolPullBase` | `0.12` | Base velocity pull applied to schooling fish. |
| `schoolPullFactor` | `0.18` | Additional schooling pull gained across levels. |
| `assistRangeBase` | `8` | Base block range dolphins and axolotls assist within. |
| `assistRangeFactor` | `8` | Additional assist recruitment range gained across levels. |
| `dolphinChargeStrength` | `0.9` | Velocity strength dolphins use to charge your combat target. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Hydro Jet (`seaborne-hydro-jet`)

Tap sneak while swimming to burst forward on a jet of water. Costs hunger and consumes a charge.

**Runtime entry points:** on sneak toggle.

**Menu displays:** Burst Force; Jet Charges.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeaborneHydroJet` |
| Icon | `PRISMARINE_CRYSTALS` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-hydro-jet.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `burstForceBase` | `0.9` | Base forward burst force applied while swimming. |
| `burstForceFactor` | `0.9` | Additional burst force gained across levels. |
| `maxChargesBase` | `2` | Base number of stored jet charges. |
| `maxChargesFactor` | `3` | Additional stored charges gained across levels. |
| `chargeRegenMillis` | `2500` | Milliseconds to regenerate one jet charge. |
| `hungerCost` | `2.0` | Exhaustion (hunger) cost applied per jet. |
| `jetXp` | `6` | Bonus XP granted per jet burst. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Brine Skin (`seaborne-brine-skin`)

While wet you slowly regenerate and take reduced damage, and the buff lingers briefly after you dry off.

**Runtime entry points:** on taking damage; periodic evaluation every 2000 ms.

**Menu displays:** Brine Regeneration Tier; Damage Reduction While Wet.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SeaborneBrineSkin` |
| Icon | `KELP` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.55 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-brine-skin.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageReductionBase` | `0.06` | Base damage reduction fraction while wet. |
| `damageReductionFactor` | `0.14` | Additional damage reduction fraction gained across levels. |
| `maxDamageReduction` | `0.25` | Hard cap on damage reduction fraction. |
| `lingerSecondsBase` | `3` | Base seconds the wet buff lingers after drying off. |
| `lingerSecondsFactor` | `4` | Additional linger seconds gained across levels. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
