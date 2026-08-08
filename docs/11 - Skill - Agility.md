# Skill: Agility

Skill id `agility`. Earn XP through sprinting, swimming, jumping, climbing, and other movement. Agility has 13 registered adaptations and uses the `FEATHER` icon.

**XP sources:** sprinting, swimming, jumping, climbing, and general movement.

**Milestones / challenges** (stat keys):

- `challenge_move_1k` tracking `move`
- `challenge_sprint_marathon` tracking `move.sprint`
- `challenge_sprint_dist_5k` tracking `move.sprint`
- `challenge_sprint_dist_50k` tracking `move.sprint`
- `challenge_agility_swim_1k` tracking `move.swim`
- `challenge_agility_swim_10k` tracking `move.swim`
- `challenge_fly_1k` tracking `move.fly`
- `challenge_fly_10k` tracking `move.fly`
- `challenge_agility_sneak_500` tracking `move.sneak`
- `challenge_agility_sneak_5k` tracking `move.sneak`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `agility` |
| Class | `SkillAgility` |
| Icon | `FEATHER` |
| Color | `GREEN` |
| Interval (ms) | `975` |
| Skill config | `plugins/Adapt/adapt/skills/agility.toml` |
| Adaptation count | 13 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/agility.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&a"` | Legacy ampersand color code used for this skill in menus and text. |
| `challengeMove1kReward` | `500` | Reward for the move 1 k challenge. |
| `challengeSprint5kReward` | `2000` | Reward for the sprint 5 k challenge. |
| `challengeSprintMarathonReward` | `6500` | Reward for the sprint marathon challenge. |
| `sprintXpPassive` | `0.35` | XP awarded for sprint passive. |
| `swimXpPassive` | `0.4` | XP awarded for swim passive. |
| `jumpXpPassive` | `0.15` | XP awarded for jump passive. |
| `climbXpPassive` | `0.4` | XP awarded for climb passive. |
| `moveXpPassive` | `0.05` | XP awarded for move passive. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Wind Up (`agility-wind-up`)

Continuous sprinting builds movement speed up to the configured cap; stopping or leaving a valid movement state clears the buildup.

**Runtime entry points:** on player death; on sprint toggle; on sneak toggle; on `PlayerToggleFlightEvent`; on `EntityToggleGlideEvent`; on gamemode change; periodic evaluation every 50 ms while its conditions hold.

**Menu displays:** Max Speed; Windup Time.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AgilityWindUp` |
| Icon | `POWERED_RAIL` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 2 |
| Cost factor | 0.65 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-wind-up.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `PlayerDeathEvent` (`on`) — on player death
- `EntityMountEvent` (`on`) — clears sprint momentum when mounting
- `EntityDismountEvent` (`on`) — clears sprint momentum when dismounting
- `PlayerMoveEvent` (`on`) — updates sprint momentum
- `PlayerToggleSprintEvent` (`on`) — on sprint toggle
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerToggleFlightEvent` (`on`)
- `EntityToggleGlideEvent` (`on`)
- `PlayerGameModeChangeEvent` (`on`) — on gamemode change

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `windupTicksSlowest` | `180` | Windup ticks slowest. Server ticks (20 ticks = 1 second). |
| `windupTicksFastest` | `60` | Windup ticks fastest. Server ticks (20 ticks = 1 second). |
| `windupSpeedBase` | `0.22` | Base windup speed. |
| `windupSpeedLevelMultiplier` | `0.225` | Windup speed level multiplier. Unitless multiplier. |
| `walkSpeedBonusScalar` | `0.75` | Scales the relative movement-speed modifier gained from windup speed increase while sprinting. |
| `walkSpeedLerpPerTick` | `0.45` | Smooths the relative movement-speed modifier toward the windup target bonus each tick. |
| `maxWalkSpeed` | `0.35` | Effective walk-speed ceiling expressed against the 0.2 vanilla base; the relative movement-speed bonus is capped at maxWalkSpeed / 0.2 - 1. |
| `movementVelocityThreshold` | `0.015` | Minimum horizontal movement speed required for max-speed stat credit. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Wall Jump (`agility-wall-jump`)

Hold shift while mid-air against a wall to latch, then release shift to jump.

**Runtime entry points:** on sneak toggle; on player death; on gamemode change; periodic evaluation every 50 ms while its conditions hold.

**Menu displays:** Max Jumps; Jump Height.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AgilityWallJump` |
| Icon | `VINE` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 2 |
| Cost factor | 0.65 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-wall-jump.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerDeathEvent` (`on`) — on player death
- `PlayerGameModeChangeEvent` (`on`) — on gamemode change
- `PlayerMoveEvent` (`on`) — updates the wall latch and jump state

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxJumpsLevelBonusDivisor` | `2` | Maximum jumps level bonus divisor. Level or effect-amplifier units. |
| `jumpHeightBase` | `0.625` | Base Jump height. |
| `jumpHeightBonusLevelMultiplier` | `0.225` | Jump height bonus level multiplier. Unitless multiplier. |
| `backwardPushSpeed` | `0.22` | Backward push speed. |
| `backwardIntentDotThreshold` | `0.35` | Backward intent dot threshold. |
| `inputMovementThreshold` | `0.0025` | Input movement threshold. |
| `inputWindowMs` | `450` | Input window ms. Milliseconds. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Super Jump (`agility-super-jump`)

Sneak and jump to launch a super jump. Four levels scale the apex from 1.5 to 2.5 blocks.

**Runtime entry points:** on sneak toggle; on gamemode change; on world change; on jump.

**Menu displays:** Jump apex (blocks); Sneak + Jump to Super Jump.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AgilitySuperJump` |
| Icon | `LEATHER_BOOTS` |
| Max level | 4 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 2 |
| Cost factor | 0.55 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-super-jump.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerGameModeChangeEvent` (`on`) — on gamemode change
- `PlayerChangedWorldEvent` (`on`) — on world change
- `PlayerJumpEvent` (`on`) — on jump

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `minimumJumpHeight` | `1.5` | Jump apex in blocks at level 1. |
| `maximumJumpHeight` | `2.5` | Jump apex in blocks at level 4. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Armor-Up (`agility-armor-up`)

Build temporary armor while sprinting; the bonus fades after you stop.

**Runtime entry points:** on player death; while moving; on sprint toggle; on sneak toggle; periodic evaluation every 50 ms while its conditions hold.

**Menu displays:** Max Armor; Armor-Up Time; Armor Decay Time.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AgilityArmorUp` |
| Icon | `IRON_CHESTPLATE` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 2 |
| Cost factor | 0.65 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-armor-up.toml` |

Listened events:

- `PlayerJoinEvent` (`on`)
- `PlayerQuitEvent` (`on`)
- `PlayerDeathEvent` (`on`) — on player death
- `PlayerMoveEvent` (`on`) — while moving
- `PlayerToggleSprintEvent` (`on`) — on sprint toggle
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `windupTicksSlowest` | `180` | Windup ticks slowest. Server ticks (20 ticks = 1 second). |
| `windupTicksFastest` | `60` | Windup ticks fastest. Server ticks (20 ticks = 1 second). |
| `windupArmorBase` | `0.22` | Base windup armor. |
| `windupArmorLevelMultiplier` | `0.525` | Windup armor level multiplier. Unitless multiplier. |
| `decaySecondsBase` | `5.0` | Base Decay seconds. |
| `decaySecondsMaxLevelBonus` | `5.0` | Decay seconds max level bonus. Level or effect-amplifier units. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Ladder Slide (`agility-ladder-slide`)

Look up to climb quickly and look down to descend quickly. Looking near the horizon returns to normal ladder control, sneaking halts directional movement, and the first and last two climbable blocks always use normal control.

**Runtime entry points:** on sneak toggle; on player death; on gamemode change; on world change; on teleport; on `ServerResourcesReloadedEvent`; on taking damage; periodic evaluation every 50 ms while its conditions hold.

**Menu displays:** Ladder descent speed (blocks/sec); Ladder climb speed (blocks/sec); Look activation / release angles.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AgilityLadderSlide` |
| Icon | `LADDER` |
| Max level | 1 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 1 |
| Cost factor | 0.12 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-ladder-slide.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerQuitEvent` (`on`)
- `PlayerDeathEvent` (`on`) — on player death
- `PlayerGameModeChangeEvent` (`on`) — on gamemode change
- `PlayerChangedWorldEvent` (`on`) — on world change
- `PlayerTeleportEvent` (`on`) — on teleport
- `ServerResourcesReloadedEvent` (`on`)
- `EntityDamageEvent` (`on`) — on taking damage
- `PlayerMoveEvent` (`on`) — applies directional ladder movement

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `descentSpeedBase` | `0.30` | Base downward speed in blocks per tick before per-level scaling. |
| `descentSpeedPerLevel` | `0.30` | Additional downward speed granted at the maximum level. |
| `climbAssistBase` | `0.28` | Base upward speed in blocks per tick before per-level scaling. |
| `climbAssistPerLevel` | `0.22` | Additional upward speed granted at the maximum level. |
| `lookActivationDegrees` | `DEFAULT_LOOK_ACTIVATION` | Camera angle above or below the horizon that activates gaze-directed ladder movement. |
| `lookReleaseDegrees` | `DEFAULT_LOOK_RELEASE` | Camera angle where an active gaze direction releases back to normal ladder control. |
| `safeLanding` | `true` | Prevents fall damage that results directly from a fast ladder descent. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Roll Landing (`agility-roll-landing`)

Timed crouch before landing converts part of fall damage into hunger cost.

**Runtime entry points:** on sneak toggle; while moving; on taking damage; periodic evaluation every 1200 ms.

**Menu displays:** Fall Damage Conversion; Input Timing Window; Roll Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AgilityRollLanding` |
| Icon | `HAY_BLOCK` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.62 |
| Tick interval (ms) | 1200 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-roll-landing.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerMoveEvent` (`on`) — while moving
- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `reductionBase` | `0.22` | Base Reduction. |
| `reductionFactor` | `0.43` | Reduction factor. Unitless multiplier. |
| `maxReduction` | `0.8` | Maximum reduction. |
| `inputWindowMillisBase` | `450` | How long a crouch input stays armed before landing, in milliseconds, before level scaling. |
| `inputWindowMillisFactor` | `350` | Extra crouch-input window granted at max level, in milliseconds. |
| `hungerPerDamageBase` | `1.4` | Base food-point cost per prevented damage point. |
| `hungerPerDamageReduction` | `0.75` | Food-point cost reduction across adaptation levels. |
| `cooldownTicksBase` | `22` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksFactor` | `12` | Cooldown ticks factor. Server ticks (20 ticks = 1 second). |
| `maxVerticalVelocityForRollInput` | `-0.08` | Vertical velocity at or below which a crouch counts as a descent input. |
| `proneTicksBase` | `4` | Base Prone ticks. Server ticks (20 ticks = 1 second). |
| `proneTicksFactor` | `5` | Prone ticks factor. Server ticks (20 ticks = 1 second). |
| `xpPerDamagePrevented` | `4.2` | XP awarded for xp per damage prevented. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Slipstream Slide (`agility-slipstream-slide`)

Tap sneak while sprinting to enter a sustained prone slide and shed ground friction, keeping momentum through 1-block gaps.

**Runtime entry points:** while moving; on sneak toggle; on player death; on teleport.

**Menu displays:** Slide speed (blocks/sec); Slide cooldown; Max level: mobs you slide through are slowed.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AgilitySlipstreamSlide` |
| Icon | `ICE` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.55 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-slipstream-slide.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerQuitEvent` (`on`)
- `PlayerDeathEvent` (`on`) — on player death
- `PlayerTeleportEvent` (`on`) — on teleport

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `slideForceBase` | `0.5` | Base horizontal slide velocity in blocks per tick before level scaling. |
| `slideForceFactor` | `0.45` | Additional slide velocity in blocks per tick granted at max level. |
| `cooldownMillisBase` | `4000` | Cooldown between slides in milliseconds at level 0 before scaling. |
| `cooldownMillisReduction` | `2500` | Cooldown reduction in milliseconds applied at max level. |
| `cooldownMillisFloor` | `1300` | Minimum slide cooldown in milliseconds after all reductions. |
| `slideTicksBase` | `DEFAULT_SLIDE_TICKS_BASE` | Base slide prone-pose duration in ticks before level scaling. |
| `slideTicksFactor` | `DEFAULT_SLIDE_TICKS_FACTOR` | Additional slide prone-pose duration in ticks granted at max level. |
| `slideFrictionReduction` | `0.9` | Fraction of ground friction removed while the slide is active. |
| `hungerCost` | `1.8` | Saturation/hunger cost paid per slide. |
| `slowAmplifier` | `1` | Slowness amplifier applied to mobs slid through at max level. |
| `slowDurationTicks` | `40` | Duration in ticks of the max-level slow applied to mobs slid through. |
| `xpPerSlide` | `3` | Experience granted per successful slide. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Air Dash (`agility-air-dash`)

Left-click air after a sprint-jump to dash forward with pure horizontal velocity.

**Runtime entry points:** while moving; on block/entity/air interact (click).

**Menu displays:** Dash speed (blocks/sec); Mid-air dash charges.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AgilityAirDash` |
| Icon | `PHANTOM_MEMBRANE` |
| Max level | 4 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 3 |
| Cost factor | 0.55 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-air-dash.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving
- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `dashForceBase` | `0.85` | Base dash velocity in blocks per tick before level scaling. |
| `dashForceFactor` | `0.6` | Additional dash velocity in blocks per tick granted at max level. |
| `upwardLift` | `0.12` | Small upward velocity added on dash to preserve airtime. |
| `maxLevelCharges` | `2` | Number of dash charges available at max level per sprint-jump. |
| `debounceMillis` | `250` | Minimum milliseconds between dash inputs to debounce double clicks. |
| `hungerCost` | `2` | Saturation/hunger cost paid per dash. |
| `xpPerDash` | `3` | Experience granted per successful dash. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Cat Reflexes (`agility-cat-reflexes`)

A chance while sprinting to dodge incoming projectiles entirely.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Projectile dodge chance.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AgilityCatReflexes` |
| Icon | `RABBIT_HIDE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-cat-reflexes.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `dodgeChanceBase` | `0.08` | Base projectile dodge chance while sprinting before level scaling. |
| `dodgeChanceFactor` | `0.3` | Additional projectile dodge chance granted at max level. |
| `maxDodgeChance` | `0.35` | Hard cap on projectile dodge chance regardless of level. |
| `xpPerDodge` | `4` | Experience granted per dodged projectile. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Featherfoot (`agility-featherfoot`)

Sprinting ignores farmland, pressure plates, sweet-berry snags, and eventually powder snow.

**Runtime entry points:** on block/entity/air interact (click); on `EntityInsideBlockEvent`; on `PlayerInputEvent`; on sprint toggle; while moving.

**Menu displays:** Surfaces ignored while sprinting; Farmland > pressure plates > sweet berries > powder snow.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AgilityFeatherfoot` |
| Icon | `RABBIT_FOOT` |
| Max level | 4 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 1 |
| Cost factor | 0.2 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-featherfoot.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `EntityInsideBlockEvent` (`on`)
- `PlayerInputEvent` (`on`)
- `PlayerToggleSprintEvent` (`on`) — on sprint toggle
- `PlayerQuitEvent` (`on`)
- `PlayerMoveEvent` (`on`) — while moving

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `requireSprint` | `true` | Requires the player to be sprinting for any protection to apply. |
| `farmlandEnabled` | `true` | Enables the farmland protection entirely. |
| `farmlandMinLevel` | `1` | Minimum level at which farmland protection applies. |
| `farmlandMaterials` | `["FARMLAND"]` | Blocks protected from being trampled by stepping on them. |
| `pressurePlateEnabled` | `true` | Enables the pressure-plate protection entirely. |
| `pressurePlateMinLevel` | `2` | Minimum level at which pressure plates are not triggered. |
| `pressurePlateUseVanillaTag` | `true` | Covers every block in the vanilla pressure plate tag. |
| `pressurePlateMaterials` | `[]` | Extra blocks treated as pressure plates. |
| `berryBushEnabled` | `true` | Enables the sweet-berry protection entirely. |
| `berryBushMinLevel` | `3` | Minimum level at which sweet-berry slowdown and damage are ignored. |
| `berryBushMaterials` | `["SWEET_BERRY_BUSH"]` | Blocks whose contact damage and slowdown are ignored. |
| `powderSnowEnabled` | `true` | Enables the powder-snow protection entirely. |
| `powderSnowMinLevel` | `4` | Minimum level at which powder-snow freezing is shrugged off. |
| `powderSnowMaterials` | `["POWDER_SNOW"]` | Blocks whose freezing effect is cleared on contact. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Vault (`agility-vault`)

Jump toward a fence to clear it.

**Runtime entry points:** while moving; on jump; on teleport; on gamemode change; on player death.

**Menu displays:** Fence jump apex (blocks).

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AgilityVault` |
| Icon | `OAK_FENCE` |
| Max level | 1 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-vault.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving
- `PlayerJumpEvent` (`on`) — on jump
- `PlayerTeleportEvent` (`on`) — on teleport
- `PlayerGameModeChangeEvent` (`on`) — on gamemode change
- `PlayerDeathEvent` (`on`) — on player death
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `jumpHeight` | `1.75` | Jump apex in blocks when clearing a fence. |
| `xpPerVault` | `3` | Experience granted per successful vault. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Marathoner (`agility-marathoner`)

Sprinting drains less saturation, letting you run further before hunger bites.

**Runtime entry points:** on `EntityExhaustionEvent`.

**Menu displays:** Sprint saturation drain reduction.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AgilityMarathoner` |
| Icon | `LEATHER_BOOTS` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-marathoner.toml` |

Listened events:

- `EntityExhaustionEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `drainReductionBase` | `0.15` | Base fraction of sprint saturation drain removed before level scaling. |
| `drainReductionFactor` | `0.45` | Additional sprint drain reduction fraction granted at max level. |
| `maxDrainReduction` | `0.6` | Hard cap on sprint saturation drain reduction regardless of level. |
| `xpPerSaturationSaved` | `0.6` | Experience granted per unit of saturation saved. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Kip-Up (`agility-kip-up`)

Jump right after taking a hit to convert knockback into recovered momentum and a speed burst.

**Runtime entry points:** on melee/projectile hit (damage); while moving.

**Menu displays:** Recovery window; Recovery speed boost tier.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AgilityKipUp` |
| Icon | `SHIELD` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.55 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-kip-up.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `PlayerMoveEvent` (`on`) — while moving
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `recoveryWindowMillisBase` | `350` | Base recovery window in milliseconds after a hit before level scaling. |
| `recoveryWindowMillisFactor` | `550` | Additional recovery window in milliseconds granted at max level. |
| `speedAmplifierBase` | `0` | Base speed-effect amplifier granted on a recovery before level scaling. |
| `speedAmplifierFactor` | `1.6` | Additional speed-effect amplifier granted at max level. |
| `speedDurationTicks` | `40` | Duration in ticks of the recovery speed burst. |
| `recoverySpeed` | `0.5` | Horizontal velocity applied toward the intended direction on recovery. |
| `jumpVelocityThreshold` | `0.2` | Minimum upward velocity treated as a jump for triggering a kip-up. |
| `cooldownMillis` | `3000` | Cooldown between kip-up recoveries in milliseconds. |
| `xpPerRecovery` | `5` | Experience granted per successful recovery. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## Support classes (not player adaptations)

- `AgilityJumpPhysics` — converts between jump strength, jump height, and the additional velocity needed for a target height.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
