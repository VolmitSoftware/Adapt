# Skill: Blocking

Skill id `blocking`. Earn XP by blocking damage with a shield. Blocking has 14 registered adaptations and uses the `SHIELD` icon.

**XP sources:** damage successfully blocked with a shield.

**Milestones / challenges** (stat keys):

- `challenge_block_1k` tracking `blocked.hits`
- `challenge_block_5k` tracking `blocked.hits`
- `challenge_block_50k` tracking `blocked.hits`
- `challenge_block_dmg_1k` tracking `blocked.damage`
- `challenge_block_dmg_10k` tracking `blocked.damage`
- `challenge_block_proj_100` tracking `blocked.projectiles`
- `challenge_block_proj_1k` tracking `blocked.projectiles`
- `challenge_block_melee_500` tracking `blocked.melee`
- `challenge_block_melee_5k` tracking `blocked.melee`
- `challenge_block_heavy_50` tracking `blocked.heavy`
- `challenge_block_heavy_500` tracking `blocked.heavy`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `blocking` |
| Class | `SkillBlocking` |
| Icon | `SHIELD` |
| Color | `DARK_GRAY` |
| Interval (ms) | `5000` |
| Skill config | `plugins/Adapt/adapt/skills/blocking.toml` |
| Adaptation count | 14 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/blocking.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&8"` | Legacy ampersand color code used for this skill in menus and text. |
| `xpOnBlockedAttack` | `25` | XP awarded for xp on blocked attack. |
| `challengeBlock1kReward` | `500` | Reward for the block 1 k challenge. |
| `challengeBlock5kReward` | `2000` | Reward for the block 5 k challenge. |
| `cooldownDelay` | `1500` | Minimum delay between passive skill XP awards, in milliseconds. |
| `passiveXpForUsingShield` | `0` | XP awarded for passive for using shield. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Multi-Armor (`blocking-multiarmor`)

Bind an elytra to your chestplate and swap between them on the fly.

**Runtime entry points:** while moving; on drop item; on inventory click; periodic evaluation every 20202 ms.

**Menu displays:** Dynamically swaps between bound armor and an elytra; bind by left-clicking one piece onto the other in inventory; sneak-drop to unbind; destroying the MultiArmor destroys every bound item.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingMultiArmor` |
| Icon | `ELYTRA` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 1 |
| Cost factor | 1 |
| Tick interval (ms) | 20202 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-multiarmor.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving
- `PlayerDropItemEvent` (`on`) — on drop item
- `InventoryClickEvent` (`on`) — on inventory click

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `startingSlots` | `1` | Starting slots. count. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Chains of Mephistopheles (`blocking-chainarmorer`)

Unlocks chainmail armor recipes.

**Runtime entry points:** when taking a craft result; periodic evaluation every 17774 ms.

**Menu displays:** The Crafting recipe is the same as any other, but with iron nuggets instead.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingChainArmorer` |
| Icon | `CHAINMAIL_CHESTPLATE` |
| Max level | 1 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 1 |
| Cost factor | 0 |
| Tick interval (ms) | 17774 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-chainarmorer.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Craftable Saddle (`blocking-saddlecrafter`)

Craft a Saddle with Leather.

**Runtime entry points:** when taking a craft result; periodic evaluation every 17774 ms.

**Menu displays:** Saddle recipe requiring five leather.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingSaddlecrafter` |
| Icon | `LEATHER_HORSE_ARMOR` |
| Max level | 1 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 5 |
| Cost factor | 0 |
| Tick interval (ms) | 17774 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-saddlecrafter.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Craftable Horse Armor (`blocking-horsearmorer`)

Unlocks horse armor recipes.

**Runtime entry points:** when taking a craft result; periodic evaluation every 17774 ms.

**Menu displays:** Surround a saddle with the material you want to use to craft the armor.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingHorseArmorer` |
| Icon | `GOLDEN_HORSE_ARMOR` |
| Max level | 1 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 5 |
| Cost factor | 0 |
| Tick interval (ms) | 17774 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-horsearmorer.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Counter Guard (`blocking-counter-guard`)

Each blocked hit builds shield stacks. Your next proc consumes stacks to reflect damage to the attacker.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Max Stored Counter Stacks; Reflect Proc Chance; Base Reflect Damage.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingCounterGuard` |
| Icon | `IRON_BARS` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.75 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-counter-guard.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseStacks` | `2` | Base stacks. count. |
| `stackFactor` | `8` | Stack factor. Unitless multiplier. |
| `reflectChanceBase` | `0.08` | Proc chance for reflect chance base. decimal probability. |
| `reflectChanceFactor` | `0.27` | Proc chance for reflect chance factor. decimal probability. |
| `maxReflectChance` | `0.6` | Proc chance for max reflect chance. decimal probability. |
| `baseReflectDamage` | `1` | Base reflect damage. health points (2 points = 1 heart). |
| `reflectDamageFactor` | `3.5` | Reflect damage factor. Unitless multiplier. |
| `damagePerStack` | `0.28` | Damage per stack. health points (2 points = 1 heart). |
| `stackCostOnReflect` | `1` | Stack cost on reflect. |
| `xpPerReflectedDamage` | `5.0` | XP awarded for xp per reflected damage. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bastion Stance (`blocking-bastion-stance`)

While sneaking and actively blocking with a shield, reduce knockback and incoming projectile pressure.

**Runtime entry points:** on melee/projectile hit (damage); on `PlayerVelocityEvent`; on sneak toggle; while moving; on gamemode change; periodic evaluation every 2000 ms.

**Menu displays:** Knockback Resistance; Projectile Damage Reduction; Projectile Full-Block Chance.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingBastionStance` |
| Icon | `SHIELD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.68 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-bastion-stance.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `PlayerVelocityEvent` (`on`)
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerMoveEvent` (`on`) — while moving
- `PlayerGameModeChangeEvent` (`on`) — on gamemode change

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `knockbackReductionBase` | `0.18` | Base Knockback reduction. |
| `knockbackReductionFactor` | `0.52` | Knockback reduction factor. Unitless multiplier. |
| `maxKnockbackReduction` | `0.75` | Maximum knockback reduction. |
| `projectileReductionBase` | `0.12` | Base Projectile reduction. |
| `projectileReductionFactor` | `0.5` | Projectile reduction factor. Unitless multiplier. |
| `maxProjectileReduction` | `0.7` | Maximum XP credited for max projectile reduction. |
| `projectileNegateChanceBase` | `0.05` | Proc chance for projectile negate chance base. decimal probability. |
| `projectileNegateChanceFactor` | `0.22` | Proc chance for projectile negate chance factor. decimal probability. |
| `maxProjectileNegateChance` | `0.35` | Maximum XP credited for max projectile negate chance. |
| `xpPerMitigatedDamage` | `2.5` | XP awarded for xp per mitigated damage. health points (2 points = 1 heart). |
| `xpOnNegate` | `8.0` | XP awarded for xp on negate. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Mirror Block (`blocking-mirror-block`)

Blocking with a shield can reflect incoming projectiles with reduced follow-up force.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 1200 ms.

**Menu displays:** Projectile Reflect Chance; Reflected Damage Factor; Reflect Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingMirrorBlock` |
| Icon | `LIGHT_WEIGHTED_PRESSURE_PLATE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1200 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-mirror-block.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `reflectChanceBase` | `0.1` | Proc chance for reflect chance base. decimal probability. |
| `reflectChanceFactor` | `0.35` | Proc chance for reflect chance factor. decimal probability. |
| `maxReflectChance` | `0.7` | Proc chance for max reflect chance. decimal probability. |
| `reflectedDamageFactorBase` | `0.45` | Base Reflected damage factor. Unitless multiplier. |
| `reflectedDamageFactorIncrease` | `0.35` | Reflected damage factor increase. Unitless multiplier. |
| `maxReflectedDamageFactor` | `0.95` | Maximum reflected damage factor. Unitless multiplier. |
| `reflectVelocityFactorBase` | `0.42` | Base Reflect velocity factor. Unitless multiplier. |
| `reflectVelocityFactor` | `0.45` | Reflect velocity factor. Unitless multiplier. |
| `maxReflectVelocityFactor` | `1.1` | Maximum reflect velocity factor. Unitless multiplier. |
| `cooldownMillisBase` | `2000` | Base Cooldown millis. Milliseconds. |
| `cooldownMillisFactor` | `1200` | Cooldown millis factor. Milliseconds. |
| `minReflectedVelocitySquared` | `0.08` | Lower bound or activation threshold for min reflected velocity squared. |
| `fallbackReflectedSpeed` | `0.95` | Fallback reflected speed. |
| `xpOnReflect` | `8` | Skill XP awarded when a projectile is reflected. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bulwark Bash (`blocking-bulwark-bash`)

Sprint-jump and land a shielded crit to trigger a bash shockwave.

**Runtime entry points:** on sprint toggle; on melee/projectile hit (damage); periodic evaluation every 2000 ms.

**Menu displays:** Bash Range; Bash Damage; Bash Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingBulwarkBash` |
| Icon | `BELL` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-bulwark-bash.toml` |

Listened events:

- `PlayerToggleSprintEvent` (`on`) — on sprint toggle
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseDamage` | `1.0` | Base damage. health points (2 points = 1 heart). |
| `damageBonusBase` | `0.3` | Base Damage bonus. health points (2 points = 1 heart). |
| `damageBonusFactor` | `2.2` | Damage bonus factor. Unitless multiplier. |
| `rangeBase` | `2.4` | Base Range. Blocks. |
| `rangeFactor` | `1.8` | Range factor. Blocks. |
| `knockbackBase` | `0.6` | Base Knockback. |
| `knockbackFactor` | `0.6` | Knockback factor. Unitless multiplier. |
| `upwardKnockbackBase` | `0.18` | Base Upward knockback. |
| `upwardKnockbackFactor` | `0.14` | Upward knockback factor. Unitless multiplier. |
| `stunTicksBase` | `18` | Base Stun ticks. Server ticks (20 ticks = 1 second). |
| `stunTicksFactor` | `24` | Stun ticks factor. Server ticks (20 ticks = 1 second). |
| `stunAmplifierBase` | `2` | Base Stun amplifier. Level or effect-amplifier units. |
| `stunAmplifierFactor` | `1` | Stun amplifier factor. Unitless multiplier. |
| `cooldownTicksBase` | `220` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksFactor` | `120` | Cooldown ticks factor. Server ticks (20 ticks = 1 second). |
| `minFallDistanceForCrit` | `0.08` | Lower bound or activation threshold for min fall distance for crit. Blocks. |
| `recentSprintWindowMillis` | `900` | Recent sprint window millis. Milliseconds. |
| `xpPerTargetHit` | `8` | XP awarded for xp per target hit. |
| `maxCandidatesPerActivation` | `16` | Maximum secondary living targets inspected by one bulwark bash. |
| `maxAffectedPerActivation` | `12` | Maximum total targets affected by one bulwark bash, including the primary target. |
| `maxTargetFxPerActivation` | `6` | Maximum affected targets that receive individual impact particles. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Shield Wall (`blocking-shield-wall`)

While blocking, allies sheltered behind your shield take reduced projectile damage.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Protection Arc (Degrees); Projectile Damage Reduction; Protection Range.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingShieldWall` |
| Icon | `SHIELD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.65 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-shield-wall.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rangeBase` | `3.0` | Base Range. Blocks. |
| `rangeFactor` | `4.0` | Range factor. Blocks. |
| `arcDegreesBase` | `60` | Base Arc degrees. degrees. |
| `arcDegreesFactor` | `90` | Arc degrees factor. degrees. |
| `damageReductionBase` | `0.18` | Base Damage reduction. health points (2 points = 1 heart). |
| `damageReductionFactor` | `0.5` | Damage reduction factor. Unitless multiplier. |
| `maxDamageReduction` | `0.6` | Maximum damage reduction. health points (2 points = 1 heart). |
| `minFacingAlignment` | `0.1` | Lower bound or activation threshold for min facing alignment. |
| `xpPerDamageShielded` | `3.0` | XP awarded for xp per damage shielded. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Perfect Guard (`blocking-perfect-guard`)

Raise your shield the instant before a hit lands to negate it entirely and stagger the attacker.

**Runtime entry points:** on block/entity/air interact (click); on melee/projectile hit (damage).

**Menu displays:** Perfect Guard Window; Stagger Duration; Stagger Power.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingPerfectGuard` |
| Icon | `SHIELD` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.78 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-perfect-guard.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `windowMillisBase` | `120` | Base Window millis. Milliseconds. |
| `windowMillisFactor` | `240` | Window millis factor. Milliseconds. |
| `staggerTicksBase` | `20` | Base Stagger ticks. Server ticks (20 ticks = 1 second). |
| `staggerTicksFactor` | `40` | Stagger ticks factor. Server ticks (20 ticks = 1 second). |
| `staggerAmplifierBase` | `1` | Base Stagger amplifier. Level or effect-amplifier units. |
| `staggerAmplifierFactor` | `2` | Stagger amplifier factor. Unitless multiplier. |
| `staggerKnockback` | `0.55` | Stagger knockback. |
| `minFacingAlignment` | `0.15` | Lower bound or activation threshold for min facing alignment. |
| `cooldownMillis` | `1500` | Cooldown millis. Milliseconds. |
| `xpOnNegate` | `14` | XP awarded for xp on negate. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Tempered Guard (`blocking-tempered-guard`)

Blocked hits can temper your gear, restoring a sliver of shield and armor durability.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Repair Chance Per Blocked Hit; Durability Restored Per Proc.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingTemperedGuard` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-tempered-guard.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `repairChanceBase` | `0.15` | Proc chance for repair chance base. decimal probability. |
| `repairChanceFactor` | `0.4` | Proc chance for repair chance factor. decimal probability. |
| `maxRepairChance` | `0.55` | Proc chance for max repair chance. decimal probability. |
| `repairAmountBase` | `2` | Base Repair amount. durability points. |
| `repairAmountFactor` | `6` | Repair amount factor. Unitless multiplier. |
| `xpPerDurabilityRepaired` | `2.0` | XP awarded for xp per durability repaired. durability points. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Shieldbearer's Resolve (`blocking-shieldbearers-resolve`)

When an axe disables your shield, brace with resistance and recover the shield faster.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Shield Recovery Speed; Resistance Tier.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingShieldbearersResolve` |
| Icon | `NETHERITE_AXE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-shieldbearers-resolve.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `recoverySpeedBase` | `0.2` | Base Recovery speed. |
| `recoverySpeedFactor` | `0.45` | Recovery speed factor. Unitless multiplier. |
| `maxRecoverySpeed` | `0.7` | Maximum recovery speed. |
| `resistanceAmplifierBase` | `0` | Base Resistance amplifier. Level or effect-amplifier units. |
| `resistanceAmplifierFactor` | `2.2` | Resistance amplifier factor. Unitless multiplier. |
| `minResistanceTicks` | `40` | Lower bound or activation threshold for min resistance ticks. Server ticks (20 ticks = 1 second). |
| `minCooldownTicks` | `20` | Minimum cooldown ticks. Server ticks (20 ticks = 1 second). |
| `reprocessGuardMillis` | `500` | Reprocess guard millis. Milliseconds. |
| `xpOnResolve` | `12` | XP awarded for xp on resolve. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Phalanx Crafter (`blocking-phalanx-crafter`)

Craft banner-faced shields directly, and reinforce shields with netherite for bonus durability.

**Runtime entry points:** when taking a craft result.

**Menu displays:** Level 1 banner-faced shield recipe; level 2 netherite-reinforced shield recipe.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingPhalanxCrafter` |
| Icon | `SHIELD` |
| Max level | 2 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 0 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-phalanx-crafter.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Interpose (`blocking-interpose`)

Sneak-block near a wounded ally to redirect part of the damage they take onto your shield.

**Runtime entry points:** on taking damage.

**Menu displays:** Damage Redirect Share; Interpose Range; Ally Low-Health Threshold.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BlockingInterpose` |
| Icon | `SHIELD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-interpose.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `redirectShareBase` | `0.22` | Base Redirect share. |
| `redirectShareFactor` | `0.4` | Redirect share factor. Unitless multiplier. |
| `maxRedirectShare` | `0.6` | Maximum redirect share. |
| `rangeBase` | `3.5` | Base Range. Blocks. |
| `rangeFactor` | `4.5` | Range factor. Blocks. |
| `lowHealthThreshold` | `0.4` | Low health threshold. health points (2 points = 1 heart). |
| `durabilityPerDamage` | `1.0` | Durability per damage. health points (2 points = 1 heart). |
| `exhaustionPerRedirect` | `1.0` | Exhaustion per redirect. |
| `xpPerDamageRedirected` | `3.0` | XP awarded for xp per damage redirected. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
