# Skill: Unarmed

Skill id `unarmed`. Earn XP by fighting with an empty main hand. Unarmed has 12 registered adaptations and uses the `FIRE_CHARGE` icon.

**XP sources:** empty-main-hand damage and unarmed kill credit.

**Milestones / challenges** (stat keys):

- `challenge_unarmed_100` tracking `unarmed.hits`
- `challenge_unarmed_1k` tracking `unarmed.hits`
- `challenge_unarmed_10k` tracking `unarmed.hits`
- `challenge_unarmed_dmg_1k` tracking `unarmed.damage`
- `challenge_unarmed_dmg_10k` tracking `unarmed.damage`
- `challenge_unarmed_kills_25` tracking `unarmed.kills`
- `challenge_unarmed_kills_250` tracking `unarmed.kills`
- `challenge_unarmed_crit_25` tracking `unarmed.critical`
- `challenge_unarmed_crit_250` tracking `unarmed.critical`
- `challenge_unarmed_heavy_25` tracking `unarmed.heavy`
- `challenge_unarmed_heavy_250` tracking `unarmed.heavy`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `unarmed` |
| Class | `SkillUnarmed` |
| Icon | `FIRE_CHARGE` |
| Color | `YELLOW` |
| Interval (ms) | `2579` |
| Skill config | `plugins/Adapt/adapt/skills/unarmed.toml` |
| Adaptation count | 12 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/unarmed.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&e"` | Legacy ampersand color code used for this skill in menus and text. |
| `damageXPMultiplier` | `4.5` | Unitless multiplier applied to XP from damage multiplier. |
| `cooldownDelay` | `1250` | Minimum delay between passive skill XP awards, in milliseconds. |
| `challengeUnarmedReward` | `500` | Reward for the unarmed challenge. |
| `challengeUnarmedDmgReward` | `500` | Reward for the unarmed damage challenge. |
| `challengeUnarmedKillsReward` | `750` | Reward for the unarmed kills challenge. |
| `challengeUnarmedCritReward` | `750` | Reward for the unarmed crit challenge. |
| `challengeUnarmedHeavyReward` | `750` | Reward for the unarmed heavy challenge. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Sucker Punch (`unarmed-sucker-punch`)

Sprint punches, but more deadly.

**Runtime entry points:** on melee/projectile hit (damage); on entity death / kill credit; periodic evaluation every 4944 ms.

**Menu displays:** Damage; Requires an empty main hand while sprinting.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `UnarmedSuckerPunch` |
| Icon | `OBSIDIAN` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 2 |
| Cost factor | 0.225 |
| Tick interval (ms) | 4944 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-sucker-punch.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseDamage` | `0.2` | Base damage. health points (2 points = 1 heart). |
| `damageFactor` | `0.55` | Damage factor. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Unarmed Power (`unarmed-power`)

Your bare-handed strikes deal more damage.

**Runtime entry points:** on `PlayerItemHeldEvent`; on swap hands (F); on `InventoryCloseEvent`; on drop item; on `EntityPickupItemEvent`; on `PlayerItemBreakEvent`; on `PlayerRespawnEvent`; on world change.

**Menu displays:** Damage.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `UnarmedPower` |
| Icon | `IRON_INGOT` |
| Max level | 7 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 3 |
| Cost factor | 0.425 |
| Tick interval (ms) | 4444 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-power.toml` |

Listened events:

- `PlayerItemHeldEvent` (`on`)
- `PlayerSwapHandItemsEvent` (`on`) — on swap hands (F)
- `InventoryCloseEvent` (`on`)
- `PlayerDropItemEvent` (`on`) — on drop item
- `EntityPickupItemEvent` (`on`)
- `PlayerItemBreakEvent` (`on`)
- `PlayerRespawnEvent` (`on`)
- `PlayerChangedWorldEvent` (`on`) — on world change
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageFactor` | `2.57` | Damage factor. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Glass Cannon (`unarmed-glass-cannon`)

Bonus Unarmed Damage the lower your armor value is.

**Runtime entry points:** on melee/projectile hit (damage); on entity death / kill credit; periodic evaluation every 4544 ms.

**Menu displays:** Damage multiplier at zero armor and flat bonus damage per level.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `UnarmedGlassCannon` |
| Icon | `POINTED_DRIPSTONE` |
| Max level | 7 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 3 |
| Cost factor | 0.425 |
| Tick interval (ms) | 4544 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-glass-cannon.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `perLevelBonusMultiplier` | `0.25` | Per level bonus multiplier. Unitless multiplier. |
| `maxDamageFactor` | `4.0` | Maximum damage factor. Unitless multiplier. |
| `maxDamagePerLevelMultiplier` | `0.15` | Maximum damage per level multiplier. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Battering Charge (`unarmed-battering-charge`)

Sprint into enemies with fists or a shield to deal impact damage.

**Runtime entry points:** on melee/projectile hit (damage); on entity death / kill credit; while moving; on sprint toggle.

**Menu displays:** Impact Damage Bonus; Impact Knockback; Charge Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `UnarmedBatteringCharge` |
| Icon | `BLAZE_ROD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-battering-charge.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDeathEvent` (`on`) — on entity death / kill credit
- `PlayerMoveEvent` (`on`) — while moving
- `PlayerToggleSprintEvent` (`on`) — on sprint toggle

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageBase` | `0.5` | Base Damage. health points (2 points = 1 heart). |
| `damageFactor` | `4.2` | Damage factor. Unitless multiplier. |
| `knockbackBase` | `0.5` | Base Knockback. |
| `knockbackFactor` | `1.2` | Knockback factor. Unitless multiplier. |
| `cooldownTicksBase` | `80` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksFactor` | `50` | Cooldown ticks factor. Server ticks (20 ticks = 1 second). |
| `minimumVelocitySquared` | `0.05` | Minimum squared horizontal movement per tick required for a charge to connect. |
| `xpPerDamage` | `3.3` | XP awarded for xp per damage. health points (2 points = 1 heart). |
| `primedTrailIntervalMillis` | `120` | Milliseconds between primed trail particle pulses while charge is ready. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Combo Chain (`unarmed-combo-chain`)

Consecutive unarmed hits build combo stacks that increase punch damage.

**Runtime entry points:** on melee/projectile hit (damage); on block/entity/air interact (click); periodic evaluation every 1800 ms.

**Menu displays:** Max Combo Stacks; Damage Per Stack; Combo Window.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `UnarmedComboChain` |
| Icon | `CHAINMAIL_BOOTS` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.6 |
| Tick interval (ms) | 1800 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-combo-chain.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxStacksBase` | `2` | Base Maximum stacks. count. |
| `maxStacksFactor` | `8` | Maximum stacks factor. Unitless multiplier. |
| `damagePerStackBase` | `0.2` | Base Damage per stack. health points (2 points = 1 heart). |
| `damagePerStackFactor` | `0.85` | Damage per stack factor. Unitless multiplier. |
| `comboWindowMillisBase` | `1300` | Base Combo window millis. Milliseconds. |
| `comboWindowMillisFactor` | `1400` | Combo window millis factor. Milliseconds. |
| `missResetGraceMillis` | `280` | Miss reset grace millis. Milliseconds. |
| `xpPerBonusDamage` | `4.1` | XP awarded for xp per bonus damage. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Disarm (`unarmed-disarm`)

Bare-hand hits can knock the held item out of players and mobs alike, and mobs may have a worn armor piece knocked loose too.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 5125 ms.

**Menu displays:** Disarm Chance; Per-Target Cooldown; chance a disarmed mob also drops a worn armor piece.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `UnarmedDisarm` |
| Icon | `STICK` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.55 |
| Tick interval (ms) | 5125 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-disarm.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `allowDisarmPlayers` | `true` | Allows disarming other players, not just mobs. |
| `mobArmorDropChance` | `0.5` | Chance that a successful disarm against a mob also knocks loose a worn armor piece. |
| `chanceBase` | `0.04` | Base chance for a bare-hand hit to disarm the target. |
| `chanceFactor` | `0.18` | Additional disarm chance granted at max level. |
| `pickupDelayTicks` | `60` | Pickup delay ticks applied to the knocked item. |
| `targetCooldownMillis` | `8000` | Per-target cooldown in milliseconds between disarms. |
| `xpPerDisarm` | `28` | XP granted per successful disarm. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Pressure Point (`unarmed-pressure-point`)

Bare-hand hits apply stacking slowness, with weakness at higher levels.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 4733 ms.

**Menu displays:** Max Slowness Stacks; Max Weakness Stacks; Weakness unlocks at higher levels.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `UnarmedPressurePoint` |
| Icon | `TRIPWIRE_HOOK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Tick interval (ms) | 4733 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-pressure-point.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxSlownessAmplifierBase` | `0` | Base maximum slowness amplifier at level 1. |
| `maxSlownessAmplifierFactor` | `2` | Additional maximum slowness amplifier granted at max level. |
| `slownessDurationTicks` | `60` | Slowness duration in ticks per pressure strike. |
| `weaknessUnlockPercent` | `0.6` | Level percent required before weakness stacking unlocks. |
| `maxWeaknessAmplifier` | `1` | Maximum weakness amplifier once unlocked. |
| `weaknessDurationTicks` | `50` | Weakness duration in ticks per pressure strike. |
| `xpPerStrike` | `3.1` | XP granted per pressure strike. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Shockwave Clap (`unarmed-shockwave-clap`)

Sneak and punch the air to clap a shockwave that knocks back enemies in a cone. Each clap costs hunger.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 5230 ms.

**Menu displays:** Shockwave Range; Knockback Force; Clap Cooldown; Hunger Cost.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `UnarmedShockwaveClap` |
| Icon | `NOTE_BLOCK` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 5 |
| Cost factor | 0.7 |
| Tick interval (ms) | 5230 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-shockwave-clap.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rangeBase` | `3.5` | Base shockwave range in blocks at level 1. |
| `rangeFactor` | `3` | Additional shockwave range granted at max level. |
| `forceBase` | `0.8` | Base knockback force at level 1. |
| `forceFactor` | `1.2` | Additional knockback force granted at max level. |
| `upwardForceBase` | `0.25` | Base upward knockback component at level 1. |
| `upwardForceFactor` | `0.2` | Additional upward knockback granted at max level. |
| `coneDotThreshold` | `0.45` | Look-direction dot threshold that defines the cone width. |
| `cooldownMillisBase` | `10000` | Base clap cooldown in milliseconds at level 1. |
| `cooldownMillisFactor` | `6000` | Cooldown reduction in milliseconds granted at max level. |
| `hungerCost` | `2` | Hunger points consumed per shockwave clap. |
| `xpPerTargetHit` | `14` | XP granted per enemy knocked back by a clap. |
| `maxCandidatesPerActivation` | `16` | Maximum living targets inspected by one shockwave clap. |
| `maxAffectedPerActivation` | `12` | Maximum living targets knocked back by one shockwave clap. |
| `maxTargetFxPerActivation` | `8` | Maximum knocked-back targets that receive individual cloud particles. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Iron Fists (`unarmed-iron-fists`)

Bare fists hit harder and punch through soft blocks faster.

**Runtime entry points:** on melee/projectile hit (damage); on `BlockDamageEvent`; periodic evaluation every 4622 ms.

**Menu displays:** Flat Punch Damage; Soft Block Punch Haste.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `UnarmedIronFists` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.45 |
| Tick interval (ms) | 4622 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-iron-fists.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `BlockDamageEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageBase` | `0.5` | Base flat bare-hand damage bonus at level 1. |
| `damageFactor` | `2.5` | Additional flat damage bonus granted at max level. |
| `softBlockMaxHardness` | `0.8` | Maximum block hardness still considered a soft block. |
| `hasteDurationTicks` | `25` | Haste duration in ticks while punching soft blocks. |
| `hasteAmplifierFactor` | `2` | Haste amplifier granted at max level while punching soft blocks. |
| `xpPerHit` | `2.4` | XP granted per bare-hand hit. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Grapple (`unarmed-grapple`)

Sneak-punch a mob or player to grab it, then hurl it where you look. Player grapples respect PvP protection. Each throw adds exhaustion.

**Runtime entry points:** on melee/projectile hit (damage); on sneak toggle.

**Menu displays:** Hurl Force; Grapple Cooldown; Hit again or release sneak to hurl; Exhaustion per Throw.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `UnarmedGrapple` |
| Icon | `LEAD` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 5 |
| Cost factor | 0.65 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-grapple.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `forceBase` | `0.9` | Base hurl force at level 1. |
| `forceFactor` | `1.4` | Additional hurl force granted at max level. |
| `upwardBoost` | `0.2` | Base upward component added to the hurl velocity. |
| `upwardBoostFactor` | `0.25` | Additional upward hurl component granted at max level. |
| `maxHurlRange` | `6` | Maximum distance in blocks a grabbed target can be hurled from. |
| `grabTimeoutMillis` | `5000` | Milliseconds before an unused grab expires. |
| `cooldownMillisBase` | `9000` | Base grapple cooldown in milliseconds at level 1. |
| `cooldownMillisFactor` | `5000` | Cooldown reduction in milliseconds granted at max level. |
| `exhaustionPerThrow` | `2.0` | Exhaustion added to the player per hurled mob. |
| `xpPerHurl` | `32` | XP granted per hurled target. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Second Wind (`unarmed-second-wind`)

Bare-hand kills restore hunger and grant a short regeneration burst.

**Runtime entry points:** on entity death / kill credit; periodic evaluation every 4960 ms.

**Menu displays:** Hunger Restored; Regeneration Duration.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `UnarmedSecondWind` |
| Icon | `COOKED_BEEF` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Tick interval (ms) | 4960 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-second-wind.toml` |

Listened events:

- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `foodRestoreBase` | `1` | Base hunger points restored per bare-hand kill. |
| `foodRestoreFactor` | `4` | Additional hunger points restored at max level. |
| `saturationRestore` | `1.5` | Saturation restored per bare-hand kill. |
| `regenDurationTicksBase` | `40` | Base regeneration duration in ticks per bare-hand kill. |
| `regenDurationTicksFactor` | `80` | Additional regeneration duration ticks granted at max level. |
| `regenAmplifier` | `0` | Regeneration amplifier applied by the burst. |
| `cooldownMillis` | `3000` | Cooldown in milliseconds between second wind triggers. |
| `xpPerSecondWind` | `18` | XP granted per second wind trigger. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Meditation (`unarmed-meditation`)

Meditate while sneaking, still, and empty-handed to slowly build absorption hearts.

**Runtime entry points:** on melee/projectile hit (damage); on sneak toggle.

**Menu displays:** Max Absorption; Absorption Per Pulse; Combat Lockout.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `UnarmedMeditation` |
| Icon | `AMETHYST_CLUSTER` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.55 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-meditation.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `absorptionCapBase` | `2` | Base absorption cap in health points at level 1. |
| `absorptionCapFactor` | `10` | Additional absorption cap granted at max level. |
| `gainPerPulse` | `0.5` | Absorption health points gained per meditation pulse. |
| `combatLockoutMillis` | `8000` | Milliseconds after combat before meditation can resume. |
| `stationaryEpsilonSquared` | `0.01` | Maximum squared movement distance still considered stationary. |
| `xpPerPulse` | `1.2` | Silent XP granted per meditation pulse. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
