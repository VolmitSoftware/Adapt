# Skill: Kinetics

Skill id `kinetics`. Earn XP through mace smashes, spear charges, bounces, launches, levitation, and falling anvils. Kinetics has 18 registered adaptations and uses the `MACE` icon.

**XP sources:** mace smashes, spear charges, knockback, launches, bounces, levitation, movement, and falling-anvil interactions.

**Milestones / challenges** (stat keys):

- `challenge_kinetics_anvil_drop` tracking `kinetics.anvil.deep-kills`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `kinetics` |
| Class | `SkillKinetics` |
| Icon | `MACE` |
| Color | `GOLD` |
| Interval (ms) | `1000` |
| Skill config | `plugins/Adapt/adapt/skills/kinetics.toml` |
| Adaptation count | 18 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/kinetics.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&6"` | Legacy ampersand color code used for this skill in menus and text. |
| `cooldownDelay` | `1000` | Minimum delay between passive skill XP awards, in milliseconds. |
| `smashHitXp` | `12` | XP awarded for smash hit. |
| `plainMaceHitXp` | `3` | XP awarded for plain mace hit. |
| `spearJabXp` | `6` | XP awarded for spear jab. |
| `spearChargeXp` | `12` | Kinetics XP awarded for an eligible charged spear hit. |
| `mountedChargeXp` | `14` | XP awarded for mounted charge. |
| `sweetRangeMin` | `3.0` | Minimum target distance in blocks for the spear sweet-spot reward. |
| `sweetRangeMax` | `6.0` | Maximum target distance in blocks for the spear sweet-spot reward. |
| `chargeMinSpeed` | `0.18` | Minimum movement-vector magnitude required to classify a spear hit as charged. |
| `lungeChargeWindowMs` | `1200` | Milliseconds after a spear lunge that a hit can receive the charge reward. |
| `breakFallXpPerBlock` | `1.2` | Kinetics XP awarded per block of fall distance safely broken. |
| `breakFallCap` | `25` | Maximum Kinetics XP credited for one broken fall. |
| `bounceXp` | `4` | XP awarded for bounce. |
| `bounceChainBonus` | `2` | Additional Kinetics XP per linked bounce in the active chain. |
| `bounceChainWindowMs` | `4000` | Milliseconds allowed between landings for them to remain one bounce chain. |
| `bounceCap` | `20` | Maximum Kinetics XP credited for one bounce event. |
| `launchXp` | `4` | XP awarded for launch. |
| `launchMinDeltaY` | `0.6` | Minimum upward velocity delta required for a launch reward. |
| `motionRewardCooldownMs` | `1000` | Minimum delay in milliseconds between bounce or launch rewards. |
| `motionRewardMinDistance` | `1.5` | Minimum horizontal travel in blocks between bounce or launch rewards. |
| `kbDealtBaseXp` | `3` | Base skill XP credited for kb dealt base. |
| `kbTakenBaseXp` | `1.5` | Base skill XP credited for kb taken base. |
| `kbMinMagnitude` | `0.25` | Minimum knockback-vector magnitude required for a knockback reward. |
| `kbXpCap` | `12` | Maximum XP credited for kb cap. |
| `kbCooldownMs` | `750` | Kb cooldown ms. Milliseconds. |
| `selfKnockbackFactor` | `0.35` | Unitless reward multiplier for knockback caused by the same player. |
| `levitationReceiveXp` | `5` | XP awarded for levitation receive. |
| `levitationApplyXp` | `5` | XP awarded for levitation apply. |
| `levitationPulseXp` | `0.8` | XP awarded for levitation pulse. |
| `levitationXpCap` | `15` | Maximum XP credited for levitation cap. |
| `levitationCooldownMs` | `1500` | Levitation cooldown ms. Milliseconds. |
| `anvilBaseXp` | `20` | Base skill XP credited for anvil base. |
| `anvilFallFactor` | `6` | Kinetics XP added per block of an anvil's fall distance. |
| `anvilHealthFactor` | `0.6` | Kinetics XP added per point of the struck entity's maximum health. |
| `anvilKillBonusMultiplier` | `1.5` | Unitless multiplier applied when a falling anvil earns kill credit. |
| `anvilPerEventCap` | `250` | Maximum Kinetics XP credited for one falling-anvil event. |
| `anvilCooldownMs` | `4000` | Anvil cooldown ms. Milliseconds. |
| `anvilLocationCooldownMs` | `8000` | Anvil location cooldown ms. Milliseconds. |
| `anvilShareRadius` | `8` | Radius in blocks within which anvil XP may be shared. |
| `anvilShareFactor` | `0.35` | Unitless fraction of anvil XP awarded to eligible nearby participants. |
| `anvilLedgerTtlMs` | `120000` | Milliseconds an anvil provenance entry remains valid. |
| `anvilAdvancementMinFall` | `8` | Minimum anvil fall distance in blocks for the related advancement. |
| `anvilDropReward` | `500` | Kinetics XP awarded for the qualifying anvil-drop milestone. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Moon Jump (`kinetics-moon-jump`)

Each level raises every jump by 0.5 blocks. Sneak-jump for an additional floaty, low-gravity hop.

**Runtime entry points:** on jump.

**Menu displays:** Base Jump Height; Sneak-Jump Boost; Gravity Reduction; Float Window.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsMoonJump` |
| Icon | `RABBIT_FOOT` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-moon-jump.toml` |

Listened events:

- `PlayerJumpEvent` (`on`) — on jump

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `jumpBonusBase` | `0.06` | Base jump strength bonus applied on a sneak-jump before level scaling. |
| `jumpBonusFactor` | `0.10` | Additional jump strength bonus granted at max level. |
| `gravityReductionBase` | `0.15` | Base fraction of gravity removed during the float window before level scaling. |
| `gravityReductionFactor` | `0.30` | Additional gravity reduction granted at max level. |
| `floatWindowTicksBase` | `20` | Base duration in ticks of the low-gravity float window before level scaling. |
| `floatWindowTicksFactor` | `20` | Additional float window ticks granted at max level. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Rubber Soul (`kinetics-rubber-soul`)

Your landings carry spring. Bouncy blocks send you higher, and every landing keeps more momentum.

**Runtime entry points:** while moving.

**Menu displays:** Bounciness; Bouncy Block Bonus.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsRubberSoul` |
| Icon | `SLIME_BALL` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-rubber-soul.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bouncinessBase` | `0.15` | Base passive bounciness bonus applied while the adaptation is active. |
| `bouncinessFactor` | `0.35` | Additional passive bounciness granted at max level. |
| `softBlockBonusBase` | `0.3` | Base extra bounciness granted after landing on a bouncy block before level scaling. |
| `softBlockBonusFactor` | `0.5` | Additional bouncy-block bounciness granted at max level. |
| `bonusWindowTicks` | `40` | Duration in ticks of the bouncy-block bounciness bonus window. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Soft Catch (`kinetics-soft-catch`)

Soft and springy blocks break your fall, and a fresh bounce grants a grace window.

**Runtime entry points:** while moving; on taking damage.

**Menu displays:** Damage Reduction.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsSoftCatch` |
| Icon | `WHITE_WOOL` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-soft-catch.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving
- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `reductionBase` | `0.35` | Base fraction of fall damage removed on a soft landing before level scaling. |
| `reductionFactor` | `0.45` | Additional fall damage reduction granted at max level. |
| `postBounceGraceTicks` | `30` | Duration in ticks of the grace window after bouncing off a bouncy block. |
| `xpPerDamagePrevented` | `1.5` | Experience granted per point of fall damage prevented. |
| `xpPerEventCap` | `50` | Maximum experience granted by one softened fall. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Surface Skate (`kinetics-surface-skate`)

Sprint to slide across the ground with lowered friction; sneak to grip hard.

**Runtime entry points:** on sprint toggle; on sneak toggle.

**Menu displays:** Slide; Grip.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsSurfaceSkate` |
| Icon | `PACKED_ICE` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-surface-skate.toml` |

Listened events:

- `PlayerToggleSprintEvent` (`on`) — on sprint toggle
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `slideFrictionBase` | `0.15` | Base fraction of ground friction removed while sprinting before level scaling. |
| `slideFrictionFactor` | `0.35` | Additional friction reduction granted at max level while sprinting. |
| `gripFrictionBase` | `0.2` | Base fraction of extra ground friction applied while sneaking before level scaling. |
| `gripFrictionFactor` | `0.4` | Additional friction increase granted at max level while sneaking. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Terminal Toggle (`kinetics-terminal-toggle`)

Sneak in midair to switch between a hard dive and a drifting hang.

**Runtime entry points:** on sneak toggle; while moving.

**Menu displays:** Drag Shift; Gravity Shift.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsTerminalToggle` |
| Icon | `PHANTOM_MEMBRANE` |
| Max level | 3 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-terminal-toggle.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerMoveEvent` (`on`) — while moving

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `dragDeltaBase` | `0.2` | Base air drag shift applied while a midair mode is active. |
| `dragDeltaFactor` | `0.4` | Additional air drag shift gained at maximum level. |
| `gravityDeltaBase` | `0.2` | Base gravity shift applied while a midair mode is active. |
| `gravityDeltaFactor` | `0.4` | Additional gravity shift gained at maximum level. |
| `minAirTicks` | `6` | Minimum airborne game ticks before sneaking can toggle a midair mode. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Heavy Frame (`kinetics-heavy-frame`)

Plant your feet while sneaking with a mace or spear: heavy knockback resistance at the cost of speed.

**Runtime entry points:** on sneak toggle; on `PlayerItemHeldEvent`.

**Menu displays:** Knockback Resistance; Blast Resistance; Speed Penalty.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsHeavyFrame` |
| Icon | `NETHERITE_CHESTPLATE` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-heavy-frame.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerItemHeldEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `kbResistBase` | `0.3` | Base knockback resistance granted while planted. |
| `kbResistFactor` | `0.5` | Additional knockback resistance gained at maximum level. |
| `explosionResistBase` | `0.3` | Base explosion knockback resistance granted while planted. |
| `explosionResistFactor` | `0.5` | Additional explosion knockback resistance gained at maximum level. |
| `speedPenaltyBase` | `0.15` | Base movement speed penalty while planted. |
| `speedPenaltyFactor` | `0.15` | Additional movement speed penalty gained at maximum level. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Mass Shift (`kinetics-mass-shift`)

Sneak and swap hands: look up for persistent Titan form, down for persistent Pocket form, or level to return to normal. Titan grants 20% damage and health with Slowness I; Pocket trades 20% damage and health for Speed I.

**Runtime entry points:** on swap hands (F); on player death.

**Menu displays:** Titan Scale; Pocket Scale; Titan Damage and Max Health; Pocket Damage and Max Health; Look up: Titan | Look down: Pocket | Look level: Normal.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsMassShift` |
| Icon | `TOTEM_OF_UNDYING` |
| Max level | 3 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 6 |
| Cost factor | 0.45 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-mass-shift.toml` |

Listened events:

- `PlayerSwapHandItemsEvent` (`on`) — on swap hands (F)
- `PlayerQuitEvent` (`on`)
- `PlayerDeathEvent` (`on`) — on player death

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `titanScaleBase` | `0.25` | Base size increase of the titan form. |
| `titanScaleFactor` | `0.35` | Additional titan size increase gained at maximum level. |
| `pocketScaleBase` | `0.25` | Base size decrease of the pocket form. |
| `pocketScaleFactor` | `0.25` | Additional pocket size decrease gained at maximum level. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Meteor Cadence (`kinetics-meteor-cadence`)

Sneak while falling with a mace to accelerate sharply downward into your smash.

**Runtime entry points:** on sneak toggle; while moving.

**Menu displays:** Gravity Boost; Drag Cut; Downward Acceleration per Tick; Terminal Fall Speed.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsMeteorCadence` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-meteor-cadence.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerMoveEvent` (`on`) — while moving

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `gravityBoostBase` | `0.3` | Base gravity multiplier bonus while diving at level 1. |
| `gravityBoostFactor` | `0.6` | Additional gravity multiplier bonus granted at max level. |
| `dragCutBase` | `0.2` | Base air drag reduction while diving at level 1. |
| `dragCutFactor` | `0.4` | Additional air drag reduction granted at max level. |
| `downwardAccelerationBase` | `0.2` | Downward velocity added once per game tick while diving at level 1. |
| `downwardAccelerationFactor` | `0.3` | Additional downward acceleration granted at max level. |
| `terminalFallSpeed` | `3.5` | Fastest downward velocity Meteor Cadence itself can produce in blocks per tick. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Breachwright (`kinetics-breachwright`)

Your mace smashes shred the target's armor for a short time.

**Runtime entry points:** on `EntityAttemptSmashAttackEvent`.

**Menu displays:** Armor Shred; Toughness Shred; Shred Duration.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsBreachwright` |
| Icon | `NETHERITE_SCRAP` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-breachwright.toml` |

Listened events:

- `EntityAttemptSmashAttackEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `armorShredBase` | `2` | Base armor points removed from smashed targets at level 1. |
| `armorShredFactor` | `4` | Additional armor points removed at max level. |
| `toughnessShredBase` | `1` | Base armor toughness removed from smashed targets at level 1. |
| `toughnessShredFactor` | `3` | Additional armor toughness removed at max level. |
| `shredTicksBase` | `80` | Base shred duration in ticks at level 1. |
| `shredTicksFactor` | `60` | Additional shred duration in ticks granted at max level. |
| `targetCooldownMs` | `3000` | Per-target cooldown in milliseconds between shreds. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Windburst (`kinetics-windburst`)

Heavy smashes erupt in a radial shockwave that hurls nearby enemies away.

**Runtime entry points:** on `EntityAttemptSmashAttackEvent`.

**Menu displays:** Radius; Force.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsWindburst` |
| Icon | `WIND_CHARGE` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-windburst.toml` |

Listened events:

- `EntityAttemptSmashAttackEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `2.5` | Base shockwave radius in blocks at level 1. |
| `radiusFactor` | `2.5` | Additional shockwave radius granted at max level. |
| `forceBase` | `0.6` | Base knockback force at level 1. |
| `forceFactor` | `0.8` | Additional knockback force granted at max level. |
| `minFallDistanceBase` | `3` | Base fall distance in blocks required to trigger a burst at level 1. |
| `minFallDistanceFactor` | `-1` | Fall distance requirement change applied at max level. |
| `cooldownMs` | `4000` | Cooldown in milliseconds between bursts. |
| `xpPerBurst` | `8` | XP granted per triggered burst. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Quake Guard (`kinetics-quake-guard`)

Landing a smash braces you: knockback resistance, toughness, and safe footing for a moment.

**Runtime entry points:** on `EntityAttemptSmashAttackEvent`.

**Menu displays:** Knockback Resistance; Toughness; Safe Fall.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsQuakeGuard` |
| Icon | `POLISHED_DEEPSLATE` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-quake-guard.toml` |

Listened events:

- `EntityAttemptSmashAttackEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `kbResistBase` | `0.3` | Knockback resistance granted at level 0 progression after landing a smash. |
| `kbResistFactor` | `0.5` | Additional knockback resistance granted at maximum level progression. |
| `toughnessBase` | `2` | Armor toughness granted at level 0 progression after landing a smash. |
| `toughnessFactor` | `4` | Additional armor toughness granted at maximum level progression. |
| `safeFallBase` | `2` | Safe fall distance in blocks granted at level 0 progression after landing a smash. |
| `safeFallFactor` | `4` | Additional safe fall distance in blocks granted at maximum level progression. |
| `braceTicksBase` | `40` | Duration in ticks of the brace at level 0 progression. |
| `braceTicksFactor` | `40` | Additional brace duration in ticks at maximum level progression. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Rebound Anvil (`kinetics-rebound-anvil`)

Each smash coils your legs: land springy and cushioned, ready for a second meteor.

**Runtime entry points:** on `EntityAttemptSmashAttackEvent`.

**Menu displays:** Bounce Window; Fall Relief.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsReboundAnvil` |
| Icon | `SLIME_BLOCK` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.55 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-rebound-anvil.toml` |

Listened events:

- `EntityAttemptSmashAttackEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bouncinessBase` | `0.5` | Bounciness granted at level 0 progression after a smash. |
| `bouncinessFactor` | `0.6` | Additional bounciness granted at maximum level progression. |
| `fallReliefBase` | `0.4` | Fraction of fall damage removed at level 0 progression during the window. |
| `fallReliefFactor` | `0.4` | Additional fraction of fall damage removed at maximum level progression. |
| `windowTicksBase` | `40` | Duration in ticks of the rebound window at level 0 progression. |
| `windowTicksFactor` | `30` | Additional rebound window duration in ticks at maximum level progression. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Phalanx Reach (`kinetics-phalanx-reach`)

Spears strike farther in your hands.

**Runtime entry points:** on `PlayerItemHeldEvent`; periodic evaluation every 2000 ms.

**Menu displays:** Bonus Reach.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsPhalanxReach` |
| Icon | `COPPER_SPEAR` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-phalanx-reach.toml` |

Listened events:

- `PlayerItemHeldEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `reachBase` | `0.5` | Base bonus entity-interaction reach in blocks while holding a spear. |
| `reachFactor` | `1.25` | Additional reach in blocks granted as levels increase. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Charge Lance (`kinetics-charge-lance`)

Spear hits scale with your speed. Hit them at a run.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Speed Scaling; Bonus Cap.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsChargeLance` |
| Icon | `IRON_SPEAR` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-charge-lance.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `speedDamageFactorBase` | `0.8` | Base multiplier converting horizontal speed into bonus damage fraction. |
| `speedDamageFactorFactor` | `1.2` | Additional speed-to-damage multiplier gained as levels increase. |
| `minSpeed` | `0.18` | Minimum horizontal speed required for a charge bonus to apply. |
| `bonusCapBase` | `0.5` | Base cap on the bonus damage fraction per hit. |
| `bonusCapFactor` | `0.75` | Additional bonus-damage cap gained as levels increase. |
| `cooldownMs` | `1500` | Minimum delay between charge bonuses in milliseconds. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Impale Pin (`kinetics-impale-pin`)

Spear hits at sweet range pin the target with heavy slowness.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Sweet Range; Slow Duration.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsImpalePin` |
| Icon | `COBWEB` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-impale-pin.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `sweetMin` | `3.0` | Minimum distance in blocks for a hit to count as sweet range. |
| `sweetMaxBase` | `5.0` | Base maximum distance in blocks for the sweet-range band. |
| `sweetMaxFactor` | `1.5` | Additional maximum sweet-range distance gained as levels increase. |
| `slowTierBase` | `0` | Base slowness amplifier applied on a sweet-range pin. |
| `slowTierFactor` | `2` | Additional slowness amplifier gained as levels increase, rounded to an integer. |
| `durationTicksBase` | `40` | Base slowness duration in ticks applied on a pin. |
| `durationTicksFactor` | `50` | Additional slowness duration in ticks gained as levels increase. |
| `targetCooldownMs` | `2500` | Minimum delay between pins on the same target in milliseconds. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Lunge Conductor (`kinetics-lunge-conductor`)

Your spear lunges strike harder and carry you farther.

**Runtime entry points:** on `EntityLungeEvent`.

**Menu displays:** Lunge Power; Dash Boost.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsLungeConductor` |
| Icon | `FEATHER` |
| Max level | 3 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-lunge-conductor.toml` |

Listened events:

- `EntityLungeEvent` (`on`)
- `EntityLungeEvent` (`finalizeLunge`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `powerBonusBase` | `1` | Base lunge power added to every boosted lunge. |
| `powerBonusFactor` | `2` | Additional lunge power granted as the adaptation levels up. |
| `dashBoostBase` | `0.2` | Base forward velocity assist applied when a lunge fires. |
| `dashBoostFactor` | `0.3` | Additional forward velocity assist granted at max level. |
| `cooldownMs` | `2500` | Minimum milliseconds between boosted lunges. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Mounted Shock (`kinetics-mounted-shock`)

Spear charges from the saddle hit harder the faster your mount moves.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Mount Speed Scaling.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsMountedShock` |
| Icon | `SADDLE` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.55 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-mounted-shock.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `mountSpeedFactorBase` | `1.0` | Base multiplier converting mount speed into bonus spear damage. |
| `mountSpeedFactorFactor` | `1.5` | Additional mount-speed multiplier granted at max level. |
| `bonusCapBase` | `0.4` | Base hard cap on the mounted damage bonus. |
| `bonusCapFactor` | `0.6` | Additional damage-bonus cap granted at max level. |
| `cooldownMs` | `2000` | Minimum milliseconds between boosted mounted charges. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Dead Zone (`kinetics-dead-zone`)

Enemies that crowd inside your spear's dead zone get shoved out, arming a riposte.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Dead Zone Range; Riposte Bonus.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `KineticsDeadZone` |
| Icon | `ARMOR_STAND` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.6 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-dead-zone.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `deadZoneRangeBase` | `2.0` | Base radius of the dead zone around you while holding a spear. |
| `deadZoneRangeFactor` | `1.0` | Additional dead zone radius granted at max level. |
| `shoveForceBase` | `0.5` | Base force of the shove applied to crowding attackers. |
| `shoveForceFactor` | `0.6` | Additional shove force granted at max level. |
| `riposteWindowTicks` | `30` | How long the riposte window stays armed after a shove, in ticks. |
| `riposteBonusBase` | `0.2` | Base damage bonus applied to a riposte strike. |
| `riposteBonusFactor` | `0.4` | Additional riposte damage bonus granted at max level. |
| `cooldownMs` | `3000` | Minimum milliseconds between dead zone shoves. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## Support classes (not player adaptations)

- `KineticsJumpPhysics` — converts between jump strength, jump height, and the additional velocity needed for a target height.
- `KineticsAnvils` — tracks player-placed anvils through falls and piston moves, then calculates bounded crush XP.
- `KineticsKnockback` — validates knockback magnitude and calculates dealt, taken, and self-caused XP.
- `KineticsLevitation` — calculates bounded levitation application, receipt, multi-target, and pulse XP.
- `KineticsMotion` — classifies bouncy and soft surfaces and calculates fall, launch, and bounce-chain rewards.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
