# Skill: Swords

Skill id `swords`. Earn XP by dealing damage with swords. Swords has 14 registered adaptations and uses the `DIAMOND_SWORD` icon.

**XP sources:** sword damage and sword kill credit.

**Milestones / challenges** (stat keys):

- `challenge_sword_100` tracking `sword.hits`
- `challenge_sword_1k` tracking `sword.hits`
- `challenge_sword_10k` tracking `sword.hits`
- `challenge_sword_dmg_1k` tracking `sword.damage`
- `challenge_sword_dmg_10k` tracking `sword.damage`
- `challenge_sword_kills_50` tracking `sword.kills`
- `challenge_sword_kills_500` tracking `sword.kills`
- `challenge_sword_crit_50` tracking `sword.critical`
- `challenge_sword_crit_500` tracking `sword.critical`
- `challenge_sword_heavy_25` tracking `sword.heavy.hits`
- `challenge_sword_heavy_250` tracking `sword.heavy.hits`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `swords` |
| Class | `SkillSwords` |
| Icon | `DIAMOND_SWORD` |
| Color | `YELLOW` |
| Interval (ms) | `2150` |
| Skill config | `plugins/Adapt/adapt/skills/swords.toml` |
| Adaptation count | 14 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/swords.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&e"` | Legacy ampersand color code used for this skill in menus and text. |
| `cooldownDelay` | `1250` | Minimum delay between passive skill XP awards, in milliseconds. |
| `damageXPMultiplier` | `4.5` | Unitless multiplier applied to XP from damage multiplier. |
| `challengeSwordReward` | `500` | Reward for the sword challenge. |
| `challengeSwordDmgReward` | `500` | Reward for the sword damage challenge. |
| `challengeSwordKillsReward` | `500` | Reward for the sword kills challenge. |
| `challengeSwordCritReward` | `500` | Reward for the sword crit challenge. |
| `challengeSwordHeavyReward` | `500` | Reward for the sword heavy challenge. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Machete (`sword-machete`)

Cut through foliage with ease.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 5234 ms.

**Menu displays:** Slash Radius; Chop Cooldown; Tool Wear.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsMachete` |
| Icon | `IRON_SWORD` |
| Max level | 3 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 4 |
| Cost factor | 0.225 |
| Tick interval (ms) | 5234 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-machete.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `0.6` | Base Radius. Blocks. |
| `radiusFactor` | `2.36` | Radius factor. Blocks. |
| `cooldownTicksBase` | `7` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksSlowest` | `35` | Cooldown ticks slowest. Server ticks (20 ticks = 1 second). |
| `toolDamageBase` | `1` | Base Tool damage. health points (2 points = 1 heart). |
| `toolDamageInverseLevelFactor` | `5` | Tool damage inverse level factor. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Poisoned Blade (`sword-poison-blade`)

Strikes with your sword, cause Poison.

**Runtime entry points:** on melee/projectile hit (damage); on entity death / kill credit; periodic evaluation every 4984 ms.

**Menu displays:** Striking a Living entity with your Sword causes Poison; Poison Duration; Poison Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsPoisonedBlade` |
| Icon | `GREEN_DYE` |
| Max level | 7 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 7 |
| Cost factor | 0.325 |
| Tick interval (ms) | 4984 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-poison-blade.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldown` | `5000` | Cooldown. |
| `effectDuration` | `1000` | Effect duration. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bloody Blade (`sword-bloody-blade`)

Strikes with your sword, cause Bleeding.

**Runtime entry points:** on melee/projectile hit (damage); on entity death / kill credit; periodic evaluation every 5534 ms.

**Menu displays:** Striking a Living entity with your Sword causes Bleeding; Bleed Duration; Bleed Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsBloodyBlade` |
| Icon | `RED_DYE` |
| Max level | 7 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 7 |
| Cost factor | 0.325 |
| Tick interval (ms) | 5534 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-bloody-blade.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldown` | `5000` | Cooldown. |
| `damagePerBleedProc` | `0.5` | Health points dealt by each bleed proc (2 points = 1 heart). |
| `effectDuration` | `1000` | Effect duration. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Dual Wield Stance (`sword-dual-wield`)

Holding a sword in each hand grants bonus melee damage. Matching swords grant the higher bonus.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 1800 ms.

**Menu displays:** Matching Sword Bonus; Mixed Sword Bonus.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsDualWield` |
| Icon | `GOLDEN_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1800 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-dual-wield.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `sameWeaponBase` | `1.12` | Base Same weapon. |
| `sameWeaponFactor` | `0.43` | Same weapon factor. Unitless multiplier. |
| `mixedWeaponBase` | `1.06` | Base Mixed weapon. |
| `mixedWeaponFactor` | `0.28` | Mixed weapon factor. Unitless multiplier. |
| `xpPerDamage` | `2.0` | XP awarded for xp per damage. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Executioner's Edge (`sword-executioners-edge`)

Sword strikes deal extra damage to low-health targets.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 1900 ms.

**Menu displays:** Bonus Damage; Health Threshold.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsExecutionersEdge` |
| Icon | `STONE_SWORD` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.65 |
| Tick interval (ms) | 1900 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-executioners-edge.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusDamageBase` | `0.08` | Base Bonus damage. health points (2 points = 1 heart). |
| `bonusDamageFactor` | `0.42` | Bonus damage factor. Unitless multiplier. |
| `thresholdBase` | `0.22` | Base Threshold. |
| `thresholdFactor` | `0.33` | Threshold factor. Unitless multiplier. |
| `maxThreshold` | `0.65` | Maximum threshold. |
| `xpPerBuffedDamage` | `1.9` | Skill XP awarded per point of damage dealt while the buff applies. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Riposte Window (`sword-riposte-window`)

Blocking with a shield arms a short riposte for your next strike.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 2100 ms.

**Menu displays:** Riposte Window; Riposte Damage Bonus.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsRiposteWindow` |
| Icon | `GOLDEN_CHESTPLATE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.71 |
| Tick interval (ms) | 2100 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-riposte-window.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `windowMillisBase` | `350` | Base Window millis. Milliseconds. |
| `windowMillisFactor` | `550` | Window millis factor. Milliseconds. |
| `damageBonusBase` | `0.22` | Base Damage bonus. health points (2 points = 1 heart). |
| `damageBonusFactor` | `0.75` | Damage bonus factor. Unitless multiplier. |
| `xpPerBuffedDamage` | `1.8` | XP awarded for xp per buffed damage. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Crimson Cyclone (`sword-crimson-cyclone`)

Land a sword crit to unleash a bleeding area slash around your target.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 2400 ms.

**Menu displays:** Cyclone Radius; Cyclone Damage; Cyclone Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsCrimsonCyclone` |
| Icon | `NETHERITE_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.76 |
| Tick interval (ms) | 2400 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-crimson-cyclone.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `showBleedParticles` | `true` | Controls whether bleed particles are emitted. |
| `radiusBase` | `2.6` | Base Radius. Blocks. |
| `radiusFactor` | `2.4` | Radius factor. Blocks. |
| `baseDamage` | `2.0` | Base damage. health points (2 points = 1 heart). |
| `damageFactor` | `4.0` | Damage factor. Unitless multiplier. |
| `bleedTicksBase` | `40` | Base Bleed ticks. Server ticks (20 ticks = 1 second). |
| `bleedTicksFactor` | `90` | Bleed ticks factor. Server ticks (20 ticks = 1 second). |
| `bleedDamagePerProcBase` | `0.35` | Base Bleed damage per proc. health points (2 points = 1 heart). |
| `bleedDamagePerProcFactor` | `0.45` | Bleed damage per proc factor. Unitless multiplier. |
| `hungerCostBase` | `2` | Base Hunger cost. food or saturation points. |
| `hungerCostFactor` | `2` | Hunger cost factor. Unitless multiplier. |
| `durabilityCostBase` | `3` | Base Durability cost. durability points. |
| `durabilityCostFactor` | `1.5` | Durability cost factor. Unitless multiplier. |
| `cooldownTicksBase` | `320` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksFactor` | `160` | Cooldown ticks factor. Server ticks (20 ticks = 1 second). |
| `xpPerTargetHit` | `10` | XP awarded for xp per target hit. |
| `maxCandidatesPerActivation` | `16` | Maximum secondary living targets inspected by one crimson cyclone. |
| `maxAffectedPerActivation` | `12` | Maximum total targets hit by one crimson cyclone, including the primary target. |
| `maxTargetFxPerActivation` | `9` | Maximum hit targets that receive individual crimson spark effects. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Lunge Strike (`sword-lunge-strike`)

Sprint-attack with a sword to lunge into the blow with extra reach.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Lunge Force; Bonus Reach.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsLungeStrike` |
| Icon | `IRON_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-lunge-strike.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `forceBase` | `0.35` | Base Force. |
| `forceFactor` | `0.45` | Force factor. Unitless multiplier. |
| `reachBase` | `0.8` | Base Reach. |
| `reachFactor` | `1.8` | Reach factor. Unitless multiplier. |
| `reachVelocityFactor` | `0.12` | Reach velocity factor. Unitless multiplier. |
| `verticalBoost` | `0.18` | Vertical boost. |
| `reachWindowTicks` | `12` | How long the bonus entity-reach window lasts after a lunge, in ticks. |
| `maxSurge` | `1.1` | Hard cap on total horizontal lunge velocity. |
| `cooldownMillis` | `350` | Minimum delay between lunges in milliseconds. |
| `xpPerLunge` | `6` | XP awarded for xp per lunge. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Blade Flow (`sword-blade-flow`)

Chain sword hits to build attack-speed stacks. Taking damage breaks the flow.

**Runtime entry points:** on melee/projectile hit (damage); on taking damage.

**Menu displays:** Max Flow Stacks; Attack Speed / Stack.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsBladeFlow` |
| Icon | `GOLDEN_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.62 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-blade-flow.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `stackCapBase` | `1.5` | Base Stack cap. |
| `stackCapFactor` | `4.5` | Stack cap factor. Unitless multiplier. |
| `windowMillis` | `4000` | How long a flow stack survives without a new hit, in milliseconds. |
| `xpPerStack` | `3` | XP awarded for xp per stack. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Duelist's Focus (`sword-duelists-focus`)

Deal more damage and take less while exactly one hostile is engaged with you; the focused attacker briefly glows when the defense activates.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Bonus Damage; Damage Reduction.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsDuelistsFocus` |
| Icon | `SHIELD` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.68 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-duelists-focus.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusDamageBase` | `0.10` | Base Bonus damage. health points (2 points = 1 heart). |
| `bonusDamageFactor` | `0.35` | Bonus damage factor. Unitless multiplier. |
| `reductionBase` | `0.08` | Base Reduction. |
| `reductionFactor` | `0.30` | Reduction factor. Unitless multiplier. |
| `maxReduction` | `0.40` | Maximum reduction. |
| `engageRadius` | `7` | Radius in blocks used to detect engaged hostiles. |
| `threatGlowTicks` | `30` | Ticks the current dueling threat glows after it attacks you. |
| `xpPerFocusedHit` | `4` | XP awarded for xp per focused hit. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Whetstone Ritual (`sword-whetstone-ritual`)

Sneak right-click a grindstone with a sword to grind a temporary sharpness buff for durability and XP levels.

**Runtime entry points:** on block/entity/air interact (click).

**Menu displays:** Sharpness Level; Buff Duration.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsWhetstoneRitual` |
| Icon | `GRINDSTONE` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.7 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-whetstone-ritual.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `strengthBase` | `0` | Base Strength. |
| `strengthFactor` | `2` | Strength factor. Unitless multiplier. |
| `durationTicksBase` | `200` | Base Duration ticks. Server ticks (20 ticks = 1 second). |
| `durationTicksFactor` | `400` | Duration ticks factor. Server ticks (20 ticks = 1 second). |
| `durabilityCost` | `15` | Durability consumed from the sword per ritual. |
| `xpCost` | `2` | Experience levels consumed per ritual. |
| `cooldownMillis` | `60000` | Minimum delay between rituals in milliseconds. |
| `skillXpOnRitual` | `14` | XP awarded for skill on ritual. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Crescent Guard (`sword-crescent-guard`)

Killing blows with a sword grant a brief burst of absorption hearts.

**Runtime entry points:** on entity death / kill credit.

**Menu displays:** Absorption Hearts; Guard Duration.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsCrescentGuard` |
| Icon | `GOLDEN_APPLE` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.66 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-crescent-guard.toml` |

Listened events:

- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `amplifierBase` | `0` | Base Amplifier. Level or effect-amplifier units. |
| `amplifierFactor` | `2` | Amplifier factor. Unitless multiplier. |
| `durationTicksBase` | `120` | Base Duration ticks. Server ticks (20 ticks = 1 second). |
| `durationTicksFactor` | `180` | Duration ticks factor. Server ticks (20 ticks = 1 second). |
| `xpPerGuard` | `8` | XP awarded for xp per guard. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Hamstring (`sword-hamstring`)

Strikes on sprinting or fleeing targets slow them and stop them sprinting.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Slowness Tier; Slow Duration.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsHamstring` |
| Icon | `LEAD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-hamstring.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `slowTierBase` | `0` | Base Slow tier. |
| `slowTierFactor` | `2` | Slow tier factor. Unitless multiplier. |
| `durationTicksBase` | `40` | Base Duration ticks. Server ticks (20 ticks = 1 second). |
| `durationTicksFactor` | `80` | Duration ticks factor. Server ticks (20 ticks = 1 second). |
| `fleeSpeedThreshold` | `0.14` | Horizontal speed at which a target counts as fleeing. |
| `xpPerHamstring` | `5` | XP awarded for xp per hamstring. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Heirloom Edge (`sword-heirloom-edge`)

Name a sword at an anvil to make it an heirloom that banks a tiny permanent damage bonus every few kills.

**Runtime entry points:** at anvil; on entity death / kill credit.

**Menu displays:** Damage Per Bank; Kills Per Bank; Banked Damage Cap.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `SwordsHeirloomEdge` |
| Icon | `NETHERITE_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 6 |
| Cost factor | 0.72 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-heirloom-edge.toml` |

Listened events:

- `PrepareAnvilEvent` (`on`) — at anvil
- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `growthBase` | `0.15` | Base Growth. |
| `growthFactor` | `0.6` | Growth factor. Unitless multiplier. |
| `capBase` | `1.0` | Base Cap. |
| `capFactor` | `4.0` | Cap factor. Unitless multiplier. |
| `killsPerBank` | `5` | Kills required to bank one growth step on the blade. |
| `xpPerBank` | `12` | XP awarded for xp per bank. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## Support classes (not player adaptations)

- `DamagingBleedEffect` — runs the Bloody Blade bleed visual and applies each scheduled damage pulse on the target entity's owning thread.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
