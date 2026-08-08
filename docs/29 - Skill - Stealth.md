# Skill: Stealth

Skill id `stealth`. Earn XP by sneaking and attacking while concealed. Stealth has 14 registered adaptations and uses the `WITHER_ROSE` icon.

**XP sources:** sneaking and damaging or killing targets while concealed.

**Milestones / challenges** (stat keys):

- `challenge_sneak_1k` tracking `move.sneak`
- `challenge_sneak_5k` tracking `move.sneak`
- `challenge_sneak_20k` tracking `move.sneak`
- `challenge_stealth_dmg_500` tracking `stealth.damage.sneaking`
- `challenge_stealth_dmg_5k` tracking `stealth.damage.sneaking`
- `challenge_stealth_kills_10` tracking `stealth.kills.sneaking`
- `challenge_stealth_kills_100` tracking `stealth.kills.sneaking`
- `challenge_stealth_arrows_50` tracking `stealth.arrows.sneaking`
- `challenge_stealth_arrows_500` tracking `stealth.arrows.sneaking`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `stealth` |
| Class | `SkillStealth` |
| Icon | `WITHER_ROSE` |
| Color | `DARK_GRAY` |
| Interval (ms) | `1412` |
| Skill config | `plugins/Adapt/adapt/skills/stealth.toml` |
| Adaptation count | 14 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/stealth.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&8"` | Legacy ampersand color code used for this skill in menus and text. |
| `challengeSneak1kReward` | `1750` | Reward for the sneak 1 k challenge. |
| `challengeSneak5kReward` | `3500` | Reward for the sneak 5 k challenge. |
| `challengeSneak20kReward` | `8750` | Reward for the sneak 20 k challenge. |
| `sneakXP` | `0.4` | XP awarded for sneak. |
| `sneakCombatXPMultiplier` | `3.0` | Unitless multiplier applied to XP from sneak combat multiplier. |
| `sneakCombatXpCooldown` | `1250` | Cooldown in milliseconds between XP awards for sneaking combat damage. |
| `sneakKillXP` | `15` | XP awarded for sneak kill. |
| `challengeStealthDmg500Reward` | `1500` | Reward for the stealth damage 500 challenge. |
| `challengeStealthDmg5kReward` | `5000` | Reward for the stealth damage 5 k challenge. |
| `challengeStealthKills10Reward` | `1000` | Reward for the stealth kills 10 challenge. |
| `challengeStealthKills100Reward` | `5000` | Reward for the stealth kills 100 challenge. |
| `challengeStealthArrows50Reward` | `1250` | Reward for the stealth arrows 50 challenge. |
| `challengeStealthArrows500Reward` | `5000` | Reward for the stealth arrows 500 challenge. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Stealth (`stealth-silent-step`)

Sneaking starts a concealment session that evaluates nearby observers. Attacks made while undetected deal increased backstab damage; invisibility, an active Shadow Decoy, or a Smoke Pellet concealment lease also count as undetected.

**How it activates:** sneak to enter concealment, then melee-attack an observer that has not detected you. Threat awareness and target suppression refresh every 50 ms while the session is active.

Requires level ≥ 1, an enabled skill and adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthCore` |
| Icon | `WHITE_WOOL` |
| Max level | 2 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 2 |
| Cost factor | 0.325 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-silent-step.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — starts or stops concealment
- `PlayerMoveEvent` (`on`) — restores or clears the session after state changes
- `EntityTargetLivingEntityEvent` (`on`) — suppresses targeting while concealed
- `EntityDamageByEntityEvent` (`on`) — applies backstab damage
- `PlayerQuitEvent` (`on`) — clears the session

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `6` | Base Radius. Blocks. |
| `radiusFactor` | `8` | Radius factor. Blocks. |
| `playerDetectionRadiusBase` | `10` | Base Player detection radius. Blocks. |
| `playerDetectionRadiusFactor` | `14` | Player detection radius factor. Blocks. |
| `dimDurationTicksBase` | `20` | Base Dim duration ticks. Server ticks (20 ticks = 1 second). |
| `dimDurationTicksFactor` | `20` | Dim duration ticks factor. Server ticks (20 ticks = 1 second). |
| `dimAmplifier` | `0` | Dim amplifier. Level or effect-amplifier units. |
| `mobBackstabBase` | `1.5` | Base Mob backstab. |
| `mobBackstabFactor` | `0.5` | Mob backstab factor. Unitless multiplier. |
| `playerBackstabBase` | `1.25` | Base Player backstab. |
| `playerBackstabFactor` | `0.35` | Player backstab factor. Unitless multiplier. |
| `xpPerTargetDrop` | `2` | XP awarded for xp per target drop. |
| `xpPerBonusDamage` | `3.0` | XP awarded for xp per bonus damage. health points (2 points = 1 heart). |
| `showThreatGlows` | `true` | Shows nearby threats with per-player glowing while sneaking (red = can detect, gray = almost). |
| `almostLookDotMargin` | `0.2` | Look-dot margin below the full detection threshold used for gray 'almost detect' glow. |
| `detectionLookDotThreshold` | `0.2` | Look-dot threshold for stealth visibility checks while sneaking. |
| `allMobsAffectStealthVisibility` | `true` | If true, all nearby mobs (including passive) can break hidden state when they have line-of-sight. |
| `targetingBlacklistTypes` | `["WARDEN", "WITHER", "PHANTOM", "ENDER_DRAGON"]` | Entity types that are NOT ignored by stealth targeting suppression. |
| `threatScanIntervalMillis` | `250` | Milliseconds between nearby threat-awareness scans while sneaking. |
| `maxTargetDropEntitiesPerScan` | `32` | Maximum mobs inspected by each target-drop scan. |
| `maxThreatEntitiesPerScan` | `32` | Maximum mobs and players inspected by each threat-awareness scan. |
| `threatScanCompletionDelayTicks` | `2` | Ticks allowed for secondary entity threat checks to complete before applying partial results. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Sneak Speed (`stealth-speed`)

Sneak faster with each level - max level sneaks at full walk speed, the vanilla sneak-speed cap.

**Runtime entry points:** on player death; on sneak toggle; while moving.

**Menu displays:** Sneaking Speed.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthSpeed` |
| Icon | `MUSHROOM_STEW` |
| Max level | 3 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-speed.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `PlayerDeathEvent` (`on`) — on player death
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerMoveEvent` (`on`) — while moving

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `setInterval` | `50` | Tick interval (ms) used to update stealth speed. |
| `baselineWalkSpeed` | `0.2` | Reference walk speed used to convert the speed bonus into a sneak-speed multiplier when the player's live walk speed is zero. |
| `maxSpeedBonus` | `0.4666666666666667` | Maximum additional walk-speed-equivalent bonus granted at max level. The default lands max level exactly on the vanilla sneak-speed cap (sneaking at full walk speed). |
| `crawlBonusMultiplier` | `1.15` | Multiplier applied to bonus speed while crawling on land. |
| `minWalkSpeed` | `-1` | Minimum walk-speed-equivalent clamp used when computing the sneak-speed multiplier. |
| `maxWalkSpeed` | `1` | Maximum walk-speed-equivalent clamp used when computing the sneak-speed multiplier. |
| `enableAutoStep` | `true` | Enables automatic vertical stepping while stealth speed is active. |
| `enableAutoStepUp` | `true` | Grants extra step height for one-block ledges while stealth speed is active. |
| `stepHeightBonus` | `0.4` | Extra step height granted while stealth speed is active and step-up is enabled. |
| `enableAutoStepDown` | `true` | Allows stepping down one block while moving. |
| `autoStepProbeDistance` | `0.45` | Forward probe distance for auto-step-down checks. |
| `autoStepForwardPush` | `0.36` | Horizontal push applied during each auto-step-down teleport. |
| `autoStepUseInput` | `true` | Uses direct movement input for auto-step-down direction when available. |
| `autoStepVelocityThreshold` | `0.01` | Minimum horizontal velocity required before auto-step-down runs. |
| `autoStepCooldownMs` | `90` | Minimum delay between auto-step-down teleports. |
| `doubleHeadroomHeightThreshold` | `1.7` | Bounding-box height above which two-block headroom is required for step-down destinations. |
| `crawlHeightMax` | `0.61` | Maximum bounding-box height counted as crawling on land. |
| `requireGrounded` | `true` | Requires players to be grounded for stealth speed to run. |
| `allowWhileInWater` | `false` | Allows stealth speed to run while the player is in water. |
| `movementVelocityThreshold` | `0.005` | Minimum horizontal velocity used to count the player as moving for FX/stat tracking. |
| `showSoulParticles` | `true` | Shows a subtle soul particle near the player's feet while stealth speed is active. |
| `soulParticleChance` | `0.3` | Chance per tick to spawn a soul particle while moving. |
| `soulParticleYOffset` | `0.02` | Vertical offset for the soul particle effect. |
| `activationSoundVolume` | `1.6` | Activation sound volume heard by the boosted player. |
| `activationSoundPitch` | `0.9` | Activation sound pitch heard by the boosted player. |
| `activationSoundCooldownMs` | `250` | Minimum time between activation sounds. |
| `statIntervalMs` | `200` | Minimum time between progression stat increments while moving with stealth speed. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Item Snatch (`stealth-snatch`)

Snatch Dropped items instantly while sneaking.

**Runtime entry points:** on sneak toggle.

**Menu displays:** Snatch Radius.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthSnatch` |
| Icon | `CHEST_MINECART` |
| Max level | 3 |
| Initial knowledge cost | 12 |
| Base knowledge cost | 4 |
| Cost factor | 0.125 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-snatch.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `snatchRate` | `250` | Snatch rate. |
| `radiusFactor` | `5.55` | Radius factor. Blocks. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Ghost's Armor (`stealth-ghost-armor`)

Slowly builds a separate armor layer while you avoid damage. It stacks beyond worn armor and is consumed by the next armor-respecting hit.

**Runtime entry points:** on `PlayerRespawnEvent`; while moving; on player death; on taking damage; periodic evaluation every 5353 ms.

**Menu displays:** Max Ghost Armor; Speed.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthGhostArmor` |
| Icon | `CHAINMAIL_HELMET` |
| Max level | 7 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 3 |
| Cost factor | 0.335 |
| Tick interval (ms) | 5353 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-ghost-armor.toml` |

Listened events:

- `PlayerJoinEvent` (`on`)
- `PlayerRespawnEvent` (`on`)
- `PlayerMoveEvent` (`on`) — while moving
- `PlayerQuitEvent` (`on`)
- `PlayerDeathEvent` (`on`) — on player death
- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxArmor` | `16` | Maximum armor. |
| `minArmor` | `2` | Lower bound or activation threshold for min armor. |
| `maxArmorPerTick` | `3` | Maximum armor per tick. |
| `minArmorPerTick` | `1` | Lower bound or activation threshold for min armor per tick. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Stealth Vision (`stealth-vision`)

While sneaking, gain night vision, ignore Blindness, and see invisible players outlined.

**Runtime entry points:** on sneak toggle; on potion effect change.

**Menu displays:** Night vision while sneaking; Blindness immunity while sneaking; invisible-player outlines while sneaking.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthSight` |
| Icon | `POTION` |
| Max level | 1 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 2 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-vision.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — begins or ends Stealth Vision
- `EntityPotionEffectEvent` (`onBlindness`) — prevents Blindness while sneaking
- `EntityPotionEffectEvent` (`on`) — refreshes Stealth Vision after effect changes
- `PlayerQuitEvent` (`on`) — clears tracked state and owned outlines

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Enderveil (`stealth-enderveil`)

Look at Endermen freely - prevents Enderman aggression without wearing a pumpkin.

**Runtime entry points:** when mobs target; periodic evaluation every 9182 ms.

**Menu displays:** Prevent enderman attacks while sneaking; Prevent all enderman attacks.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthEnderVeil` |
| Icon | `CARVED_PUMPKIN` |
| Max level | 2 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 6 |
| Cost factor | 1.0 |
| Tick interval (ms) | 9182 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-enderveil.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `EntityTargetLivingEntityEvent` (`onTarget`) — when mobs target
- `EndermanAttackPlayerEvent` (`onTarget`) — suppresses direct Enderman aggression

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Shadow Decoy (`stealth-shadow-decoy`)

Stopping a sneak creates a short-lived decoy with the player's skin and equipment. Nearby mobs redirect their aggression to the decoy while the owner becomes temporarily invisible and leaves a configurable smoke trail.

**How it activates:** stop sneaking while the adaptation is ready. The decoy cannot be damaged and expires after its level-scaled duration.

Requires level ≥ 1, an enabled skill and adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthShadowDecoy` |
| Icon | `PLAYER_HEAD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 5 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-shadow-decoy.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — creates the decoy when sneaking ends
- `EntityDamageEvent` (`on`) — cancels damage to the decoy and renders hit feedback
- `PlayerAnimationEvent` (`on`) — detects attack swings against the decoy
- `PlayerQuitEvent` (`on`) — removes active decoys and cooldown state

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldownMillisBase` | `18000` | Base cooldown after creating a decoy, in milliseconds. |
| `cooldownMillisFactor` | `12000` | How much cooldown is reduced by leveling. |
| `decoyTicksBase` | `60` | Base active duration in ticks. |
| `decoyTicksFactor` | `80` | Duration scaling from level, in ticks. |
| `decoyRadiusBase` | `8` | Base aggro redirect radius. |
| `decoyRadiusFactor` | `10` | Aggro radius scaling from level. |
| `decoyEyeHeight` | `1.62` | Visual eye height used for fake player facing. |
| `tabListRemoveDelayTicks` | `40` | Delay before removing the skinned fake player from tab list, in ticks. |
| `legacyFallbackEnabled` | `true` | Allows armor stand visual fallback if packet NPC creation fails. |
| `ownerInvisibilityRefreshTicks` | `30` | Refresh duration for owner invisibility while a decoy is active. |
| `ownerInvisibilityAmplifier` | `0` | Amplifier for the temporary invisibility effect. |
| `ownerTrailParticles` | `5` | Smoke particles emitted around the invisible owner each tick while decoy is active. |
| `ownerTrailHorizontalSpread` | `0.18` | Horizontal spread for owner smoke trail. |
| `ownerTrailVerticalSpread` | `0.05` | Vertical spread for owner smoke trail. |
| `ownerTrailYOffset` | `0.1` | Vertical offset for smoke trail spawn location. |
| `ownerTrailSpeed` | `0.01` | Particle speed for owner smoke trail. |
| `ownerTrailIntervalMillis` | `75` | Milliseconds between owner trail particle bursts while decoy is active. |
| `ownerEquipmentHideResendMillis` | `250` | How often owner equipment-hide packets are resent while invisible, in milliseconds. |
| `aggroRedirectIntervalMillis` | `150` | Milliseconds between aggro redirect scans while a decoy is active. |
| `maxAggroEntitiesPerScan` | `32` | Maximum mobs dispatched by each decoy aggro scan. |
| `maxPacketViewers` | `64` | Maximum players sent fake-decoy and equipment packets for one decoy. |
| `maxViewerAddsPerRefresh` | `8` | Maximum newly tracked viewers initialized during each decoy refresh. |
| `maxViewerLookUpdatesPerRefresh` | `16` | Maximum viewer-facing rotations sent during each decoy refresh. |
| `decoyHitKnockback` | `0.28` | Horizontal knockback applied when the decoy is hit. |
| `decoyHitLift` | `0.08` | Vertical lift applied when the decoy is hit. |
| `decoySwingDetectionReach` | `4.5` | Swing ray distance used to detect decoy hits. |
| `decoySkinLayerMask` | `127` | Bitmask for visible skin layers on the fake player decoy. |
| `xpOnDecoy` | `18` | Experience granted on each decoy spawn. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Shadowmeld (`stealth-shadowmeld`)

Remain sneaking while Stealth reports you undetected to become invisible. Detection, acting, taking damage, or standing ends the meld.

**Runtime entry points:** on sneak toggle; when mobs target; on melee/projectile hit (damage); on taking damage; on block/entity/air interact (click); periodic evaluation every 250 ms while its conditions hold.

**Menu displays:** Undetected Sneak Delay.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthShadowmeld` |
| Icon | `SCULK` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.4 |
| Tick interval (ms) | 250 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-shadowmeld.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerQuitEvent` (`on`)
- `EntityTargetLivingEntityEvent` (`on`) — when mobs target
- `EntityDamageByEntityEvent` (`onAct`) — on melee/projectile hit (damage)
- `EntityDamageEvent` (`onHurt`) — on taking damage
- `PlayerInteractEvent` (`onInteract`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `meldDelayStartMillis` | `3000` | Delay before melding at level one, in milliseconds. |
| `meldDelayEndMillis` | `250` | Delay before melding at maximum level, in milliseconds. |
| `xpOnMeld` | `6` | Experience granted the moment you meld into shadow. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Smoke Pellet (`stealth-smoke-pellet`)

Sneaking with gunpowder in either hand consumes one gunpowder and casts a smoke cloud along the player's aim. Players in the cloud become invisible, living entities are blinded, and affected mobs drop their targets and cannot immediately reacquire concealed players.

**How it activates:** begin sneaking while holding gunpowder in the main hand or off hand and while the cooldown is ready.

Requires level ≥ 1, an enabled skill and adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthSmokePellet` |
| Icon | `GUNPOWDER` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-smoke-pellet.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — consumes gunpowder and casts the cloud
- `EntityTargetLivingEntityEvent` (`on`) — prevents mobs from targeting concealed players
- `PlayerQuitEvent` (`on`) — clears concealment state

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `2.5` | Base cloud radius of the smoke pellet. |
| `radiusFactor` | `2.5` | Extra cloud radius gained across levels. |
| `radiusMax` | `6.0` | Maximum cloud radius after scaling. |
| `pulsesBase` | `8` | Base number of blinding pulses in the cloud. |
| `pulsesFactor` | `10` | Extra pulses gained across levels. |
| `raycastRange` | `24.0` | Maximum smoke ray distance in blocks. |
| `cooldownMillis` | `1500` | Cooldown between pellet throws, in milliseconds. |
| `xpOnThrow` | `10` | Experience granted per pellet thrown. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Cutpurse (`stealth-cutpurse`)

While Stealth reports you undetected, hits on pillagers, vindicators, and piglins can steal loot without a kill.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Steal Chance; Loot Stacks.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthCutpurse` |
| Icon | `SHEARS` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-cutpurse.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `stealChanceBase` | `0.25` | Base chance to pick a pocket on a qualifying sneak-hit. |
| `stealChanceFactor` | `0.4` | Extra steal chance gained across levels. |
| `stealChanceMax` | `0.9` | Maximum steal chance after scaling. |
| `lootQualityBase` | `0.0` | Base loot luck used when rolling stolen loot. |
| `lootQualityFactor` | `2.0` | Extra loot luck gained across levels. |
| `lootStacksBase` | `1` | Base number of loot stacks taken per successful steal. |
| `lootStacksFactor` | `2` | Extra loot stacks gained across levels. |
| `xpOnSteal` | `15` | Experience granted per successful steal. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Trap Sense (`stealth-trap-sense`)

While sneaking, nearby trapped chests, tripwire string, hooks, pressure plates, and sculk blocks privately glow. Maximum level prevents all of your movement vibrations from triggering sculk.

**Runtime entry points:** on sneak toggle; on `BlockReceiveGameEvent`; periodic evaluation every 400 ms while its conditions hold.

**Menu displays:** Detection Range; Sculk Movement Suppression.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthTrapSense` |
| Icon | `TRIPWIRE_HOOK` |
| Max level | 4 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.35 |
| Tick interval (ms) | 400 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-trap-sense.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerQuitEvent` (`on`)
- `BlockReceiveGameEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rangeBase` | `4.0` | Base detection range for revealing traps, in blocks. |
| `rangeFactor` | `4.0` | Extra detection range gained across levels, in blocks. |
| `mercyMaxChance` | `0.7` | Suppression chance used below maximum level. |
| `scanIntervalMillis` | `500` | Milliseconds between trap scans while sneaking. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Assassinate (`stealth-assassinate`)

While Stealth reports you undetected, strike eligible non-boss mobs for exactly their current health instead of synthetic overkill damage.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Executable Health Cap; Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthAssassinate` |
| Icon | `NETHERITE_SWORD` |
| Max level | 4 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 6 |
| Cost factor | 0.85 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-assassinate.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `healthCapBase` | `22.0` | Base maximum health a mob can have to be executable. |
| `healthCapFactor` | `38.0` | Extra executable health granted across levels. |
| `cooldownBase` | `40000` | Base cooldown between executions, in milliseconds. |
| `cooldownFactor` | `20000` | How much the cooldown is reduced by leveling, in milliseconds. |
| `xpOnExecution` | `45` | Experience granted for a successful execution. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Decoy Swap (`stealth-decoy-swap`)

Requires Shadow Decoy. While your decoy is alive, double-tap sneak to swap places with it.

**Runtime entry points:** on sneak toggle.

**Menu displays:** Swap Range; Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthDecoySwap` |
| Icon | `ENDER_PEARL` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-decoy-swap.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `swapRangeBase` | `10.0` | Base range within which you can swap with your decoy. |
| `swapRangeFactor` | `20.0` | Extra swap range gained across levels. |
| `cooldownBase` | `12000` | Base cooldown between swaps, in milliseconds. |
| `cooldownFactor` | `8000` | How much the cooldown is reduced by leveling, in milliseconds. |
| `doubleTapWindowMillis` | `400` | Maximum time between the two sneak taps to register a double-tap, in milliseconds. |
| `xpOnSwap` | `12` | Experience granted per successful swap. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Umbral Recovery (`stealth-umbral-recovery`)

Kills made while sneaking refund hunger and extend any active invisibility window.

**Runtime entry points:** on entity death / kill credit.

**Menu displays:** Hunger Refund; Invisibility Extension.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `StealthUmbralRecovery` |
| Icon | `COOKED_BEEF` |
| Max level | 4 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.35 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-umbral-recovery.toml` |

Listened events:

- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `refundBase` | `2` | Base hunger points refunded per sneaking kill. |
| `refundFactor` | `4` | Extra hunger points refunded gained across levels. |
| `extensionTicksBase` | `40` | Base invisibility extension per sneaking kill, in ticks. |
| `extensionTicksFactor` | `120` | Extra invisibility extension gained across levels, in ticks. |
| `maxInvisibilityTicks` | `1200` | Maximum invisibility duration reachable through extension, in ticks. |
| `xpOnRecovery` | `8` | Experience granted per recovery. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## Support classes (not player adaptations)

- `StealthShadowDecoyPackets` — creates, updates, and removes the fake-player packets used by Shadow Decoy, including bounded viewer refreshes.
- `EntityListing` — defines the hostile entity types used by legacy Stealth aggression checks.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
