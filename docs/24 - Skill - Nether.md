# Skill: Nether

Skill id `nether`. Earn XP through Nether exploration and combat. Nether has 14 registered adaptations and uses the `NETHER_STAR` icon.

**XP sources:** Nether exploration, Nether block breaking, hostile kills and combat, and surviving Nether hazards.

**Milestones / challenges** (stat keys):

- `challenge_nether_50` tracking `nether.kills`
- `challenge_nether_500` tracking `nether.kills`
- `challenge_nether_5k` tracking `nether.kills`
- `challenge_wither_dmg_500` tracking `nether.wither.damage`
- `challenge_wither_dmg_5k` tracking `nether.wither.damage`
- `challenge_wither_skel_25` tracking `nether.skeleton.kills`
- `challenge_wither_skel_250` tracking `nether.skeleton.kills`
- `challenge_wither_boss_1` tracking `nether.boss.kills`
- `challenge_wither_boss_10` tracking `nether.boss.kills`
- `challenge_roses_10` tracking `nether.roses.broken`
- `challenge_roses_100` tracking `nether.roses.broken`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `nether` |
| Class | `SkillNether` |
| Icon | `NETHER_STAR` |
| Color | `DARK_GRAY` |
| Interval (ms) | `7425` |
| Skill config | `plugins/Adapt/adapt/skills/nether.toml` |
| Adaptation count | 14 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/nether.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `skillColor` | `"&8"` | Legacy ampersand color code used for this skill in menus and text. |
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `witherDamageXp` | `26.0` | Nether XP awarded for an eligible wither-damage event. |
| `witherDamageXpCooldown` | `1500` | Cooldown in milliseconds between XP awards for taking wither effect damage. |
| `witherAttackXp` | `15` | XP awarded for wither attack. |
| `witherAttackXpCooldown` | `1500` | Cooldown in milliseconds between XP awards for melee-attacking a wither boss. |
| `witherSkeletonKillXp` | `225` | XP awarded for wither skeleton kill. |
| `witherKillXp` | `900` | XP awarded for wither kill. |
| `witherRoseBreakXp` | `125` | XP awarded for wither rose break. |
| `witherRoseBreakCooldown` | `60 * 20` | Wither rose break cooldown. |
| `challengeNetherReward` | `500` | Reward for the nether challenge. |
| `challengeWitherDmgReward` | `500` | Reward for the wither damage challenge. |
| `challengeWitherSkelReward` | `500` | Reward for the wither skeleton challenge. |
| `challengeWitherBossReward` | `1000` | Reward for the wither boss challenge. |
| `challengeRosesReward` | `500` | Reward for the roses challenge. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Wither Resistance (`nether-wither-resist`)

Resists withering through the power of Netherite.

**Runtime entry points:** on taking damage; periodic evaluation every 9283 ms.

**Menu displays:** Per-piece wither-negation chance while wearing Netherite armor.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherWitherResist` |
| Icon | `NETHERITE_CHESTPLATE` |
| Max level | 3 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 9283 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-wither-resist.toml` |

Listened events:

- `EntityDamageEvent` (`onEntityDamage`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `basePieceChance` | `10` | Base percentage chance for each eligible armor piece. |
| `chanceAddition` | `5` | Percentage points added per additional eligible armor piece. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Wither Skull Throw (`nether-skull-toss`)

Use a player head to activate a temporary Wither form.

**Runtime entry points:** on block/entity/air interact (click); on entity death / kill credit; periodic evaluation every 2314 ms.

**Menu displays:** Cooldown between skull tosses; thrown wither skulls explode on impact.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherSkullYeet` |
| Icon | `WITHER_SKELETON_SKULL` |
| Max level | 3 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 10 |
| Cost factor | 0.92 |
| Tick interval (ms) | 2314 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-skull-toss.toml` |

Listened events:

- `PlayerInteractEvent` (`onRightClick`) — on block/entity/air interact (click)
- `EntityDeathEvent` (`onEntityDeath`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseCooldown` | `15` | Base cooldown. |
| `levelCooldown` | `5` | Level cooldown. Level or effect-amplifier units. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Fire Resistance (`nether-fire-resist`)

Resists fire by hardening your skin.

**Runtime entry points:** on taking damage; periodic evaluation every 4333 ms.

**Menu displays:** Chance to negate fire damage.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherFireResist` |
| Icon | `FIRE_CHARGE` |
| Max level | 3 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 4 |
| Cost factor | 0.75 |
| Tick interval (ms) | 4333 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-fire-resist.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `fireResistBase` | `0.10` | Base Fire resist. |
| `fireResistFactor` | `0.25` | Fire resist factor. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Lava Walker (`nether-lava-walker`)

Stride over lava in the Nether at the cost of hunger.

**Runtime entry points:** while moving.

**Menu displays:** Lava Stride Speed; Hunger Cost.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherLavaWalker` |
| Icon | `MAGMA_BLOCK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.75 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-lava-walker.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `strideBase` | `0.18` | Base Stride. |
| `strideFactor` | `0.6` | Stride factor. Unitless multiplier. |
| `hungerCostBase` | `3` | Base Hunger cost. food or saturation points. |
| `hungerCostFactor` | `2` | Hunger cost factor. Unitless multiplier. |
| `cooldownMillisBase` | `900` | Base Cooldown millis. Milliseconds. |
| `cooldownMillisFactor` | `700` | Cooldown millis factor. Milliseconds. |
| `fireResistTicks` | `80` | Fire resist ticks. Server ticks (20 ticks = 1 second). |
| `xpPerStride` | `3.5` | Skill XP awarded for each qualifying stride. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Ghast Ward (`nether-ghast-ward`)

Harden against ghast blasts and wither-skeleton ranged pressure in the Nether.

**Runtime entry points:** on melee/projectile hit (damage); on taking damage; periodic evaluation every 2000 ms.

**Menu displays:** Ghast Projectile Reduction; Explosion Reduction; Wither Skeleton Projectile Reduction.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherGhastWard` |
| Icon | `GHAST_TEAR` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.73 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-ghast-ward.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `ghastProjectileReductionBase` | `0.14` | Base Ghast projectile reduction. |
| `ghastProjectileReductionFactor` | `0.54` | Ghast projectile reduction factor. Unitless multiplier. |
| `maxGhastProjectileReduction` | `0.8` | Maximum ghast projectile reduction. |
| `explosionReductionBase` | `0.08` | Base skill XP credited for e losion reduction base. |
| `explosionReductionFactor` | `0.42` | Unitless multiplier applied to XP from e losion reduction factor. |
| `maxExplosionReduction` | `0.65` | Maximum XP credited for max e losion reduction. |
| `witherSkeletonReductionBase` | `0.1` | Base Wither skeleton reduction. |
| `witherSkeletonReductionFactor` | `0.4` | Wither skeleton reduction factor. Unitless multiplier. |
| `maxWitherSkeletonReduction` | `0.55` | Maximum wither skeleton reduction. |
| `maxFireTicksBase` | `80` | Base Maximum fire ticks. Server ticks (20 ticks = 1 second). |
| `maxFireTicksFactor` | `70` | Maximum fire ticks factor. Server ticks (20 ticks = 1 second). |
| `xpPerMitigatedDamage` | `4.2` | XP awarded for xp per mitigated damage. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Blaze Leech (`nether-blaze-leech`)

Fire interactions can trigger brief hunger and regeneration gains.

**Runtime entry points:** on taking damage; on melee/projectile hit (damage); periodic evaluation every 900 ms.

**Menu displays:** Leech Trigger Chance; Regen Burst Duration; Food Restored per Proc.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherBlazeLeech` |
| Icon | `BLAZE_POWDER` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.62 |
| Tick interval (ms) | 900 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-blaze-leech.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `triggerChanceBase` | `0.16` | Proc chance for trigger chance base. decimal probability. |
| `triggerChanceFactor` | `0.34` | Proc chance for trigger chance factor. decimal probability. |
| `maxTriggerChance` | `0.7` | Proc chance for max trigger chance. decimal probability. |
| `regenTicksBase` | `28` | Base Regen ticks. Server ticks (20 ticks = 1 second). |
| `regenTicksFactor` | `42` | Regen ticks factor. Server ticks (20 ticks = 1 second). |
| `regenAmplifierBase` | `0` | Base Regen amplifier. Level or effect-amplifier units. |
| `regenAmplifierFactor` | `1` | Regen amplifier factor. Unitless multiplier. |
| `foodRestoreBase` | `1` | Base Food restore. food or saturation points. |
| `foodRestoreFactor` | `2` | Food restore factor. Unitless multiplier. |
| `saturationRestore` | `0.6` | Saturation restore. food or saturation points. |
| `cooldownMillisBase` | `1400` | Base Cooldown millis. Milliseconds. |
| `cooldownMillisFactor` | `900` | Cooldown millis factor. Milliseconds. |
| `xpOnDefensiveProc` | `6` | XP awarded for xp on defensive proc. |
| `xpOnOffensiveProc` | `5` | XP awarded for xp on offensive proc. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Piglin Broker (`nether-piglin-broker`)

Nearby barters can grant extra rolls and occasional premium bonus items.

**Runtime entry points:** on `PiglinBarterEvent`; periodic evaluation every 2300 ms.

**Menu displays:** Extra Roll Chance; Rare Bonus Chance.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherPiglinBroker` |
| Icon | `GOLD_INGOT` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 2300 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-piglin-broker.toml` |

Listened events:

- `PiglinBarterEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `brokerRange` | `18` | Broker range. Blocks. |
| `extraRollChanceBase` | `0.1` | Proc chance for extra roll chance base. decimal probability. |
| `extraRollChanceFactor` | `0.45` | Proc chance for extra roll chance factor. decimal probability. |
| `maxExtraRollChance` | `0.6` | Proc chance for max extra roll chance. decimal probability. |
| `rareBonusChanceBase` | `0.03` | Proc chance for rare bonus chance base. decimal probability. |
| `rareBonusChanceFactor` | `0.2` | Proc chance for rare bonus chance factor. decimal probability. |
| `maxRareBonusChance` | `0.25` | Proc chance for max rare bonus chance. decimal probability. |
| `amountMultiplierBase` | `1.0` | Base Amount multiplier. Unitless multiplier. |
| `amountMultiplierFactor` | `0.5` | Amount multiplier factor. Unitless multiplier. |
| `xpOnBoostedBarter` | `12` | XP awarded for xp on boosted barter. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Soul Strider (`nether-soul-strider`)

Move at full speed across soul sand and soul soil, gaining soul-speed bursts at mastery.

**Runtime entry points:** while moving.

**Menu displays:** Soul Stride Speed; Mastery Soul-Speed Burst.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherSoulStrider` |
| Icon | `SOUL_SAND` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.6 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-soul-strider.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `strideSpeedBase` | `0.20` | Base Stride speed. |
| `strideSpeedFactor` | `0.10` | Stride speed factor. Unitless multiplier. |
| `burstTicks` | `60` | Burst ticks. Server ticks (20 ticks = 1 second). |
| `burstAmplifier` | `1` | Burst amplifier. Level or effect-amplifier units. |
| `burstGapMillis` | `600` | Burst gap millis. Milliseconds. |
| `burstCooldownMillis` | `3000` | Burst cooldown millis. Milliseconds. |
| `xpPerStride` | `2.0` | XP awarded for xp per stride. |
| `xpIntervalMillis` | `1500` | XP awarded for xp interval millis. Milliseconds. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Magma Skin (`nether-magma-skin`)

While burning, melee attackers catch fire and your own strikes deal bonus fire damage.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 2000 ms.

**Menu displays:** Attacker Ignite Duration; Bonus Fire Damage while Burning.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherMagmaSkin` |
| Icon | `MAGMA_CREAM` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.7 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-magma-skin.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`onDefend`) — on melee/projectile hit (damage)
- `EntityDamageByEntityEvent` (`onAttack`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `reflectFireTicksBase` | `40` | Base Reflect fire ticks. Server ticks (20 ticks = 1 second). |
| `reflectFireTicksFactor` | `60` | Reflect fire ticks factor. Server ticks (20 ticks = 1 second). |
| `bonusDamageBase` | `0.5` | Base Bonus damage. health points (2 points = 1 heart). |
| `bonusDamageFactor` | `2.5` | Bonus damage factor. Unitless multiplier. |
| `bonusFireTicksBase` | `40` | Base Bonus fire ticks. Server ticks (20 ticks = 1 second). |
| `bonusFireTicksFactor` | `40` | Bonus fire ticks factor. Server ticks (20 ticks = 1 second). |
| `xpOnReflect` | `6` | XP awarded for xp on reflect. |
| `xpPerBonusDamage` | `3` | XP awarded for xp per bonus damage. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Netherrack Mason (`nether-netherrack-mason`)

Mine netherrack, basalt, and blackstone faster in the Nether with occasional bonus drops.

**Runtime entry points:** on `BlockDamageEvent`; when breaking blocks; periodic evaluation every 1500 ms.

**Menu displays:** Mining Haste Tier; Bonus Drop Chance.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherNetherrackMason` |
| Icon | `BLACKSTONE` |
| Max level | 4 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.65 |
| Tick interval (ms) | 1500 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-netherrack-mason.toml` |

Listened events:

- `BlockDamageEvent` (`on`)
- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `hasteTierBase` | `1` | Base Haste tier. |
| `hasteTierFactor` | `1.5` | Haste tier factor. Unitless multiplier. |
| `hasteDurationTicks` | `120` | Haste duration ticks. Server ticks (20 ticks = 1 second). |
| `hasteRefreshMillis` | `4000` | Haste refresh millis. Milliseconds. |
| `bonusDropChanceBase` | `0.08` | Proc chance for bonus drop chance base. decimal probability. |
| `bonusDropChanceFactor` | `0.35` | Proc chance for bonus drop chance factor. decimal probability. |
| `maxBonusDropChance` | `0.4` | Proc chance for max bonus drop chance. decimal probability. |
| `premiumDropChance` | `0.25` | Proc chance for premium drop chance. decimal probability. |
| `xpPerBlock` | `1.5` | XP awarded for xp per block. Blocks. |
| `xpOnBonusDrop` | `5` | XP awarded for xp on bonus drop. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Strider Bond (`nether-strider-bond`)

Ride striders faster, keep their pace out of lava, and land safely when dismounting over lava.

**Runtime entry points:** while moving; periodic evaluation every 2000 ms.

**Menu displays:** Strider Speed Tier; Lava Dismount Rescue Radius.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherStriderBond` |
| Icon | `WARPED_FUNGUS_ON_A_STICK` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.6 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-strider-bond.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving
- `EntityDismountEvent` (`on`) — performs the lava dismount rescue

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `striderSpeedAmplifierBase` | `0` | Base Strider speed amplifier. Level or effect-amplifier units. |
| `striderSpeedAmplifierFactor` | `1.5` | Strider speed amplifier factor. Unitless multiplier. |
| `speedTicks` | `60` | Speed ticks. Server ticks (20 ticks = 1 second). |
| `safetyUnlockLevel` | `2` | Safety unlock level. Level or effect-amplifier units. |
| `searchRadiusBase` | `4` | Base Search radius. Blocks. |
| `searchRadiusFactor` | `4` | Search radius factor. Blocks. |
| `searchRadiusMax` | `8` | Search radius max. Blocks. |
| `xpPerRide` | `2` | Skill XP awarded for each qualifying mounted-riding interval. |
| `xpIntervalMillis` | `1500` | XP awarded for xp interval millis. Milliseconds. |
| `xpPerRescue` | `30` | XP awarded for xp per rescue. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Crimson Feast (`nether-crimson-feast`)

Eat nether fungi and warped flora, and gain fire resistance from any meal in the Nether.

**Runtime entry points:** on block/entity/air interact (click); when consuming food/potion; periodic evaluation every 3000 ms.

**Menu displays:** Food Restored per Flora; Bonus Saturation; Nether Meal Fire Resistance.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherCrimsonFeast` |
| Icon | `CRIMSON_FUNGUS` |
| Max level | 4 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.55 |
| Tick interval (ms) | 3000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-crimson-feast.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerItemConsumeEvent` (`on`) — when consuming food/potion

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `floraFoodBase` | `2` | Base Flora food. food or saturation points. |
| `floraFoodFactor` | `4` | Flora food factor. Unitless multiplier. |
| `floraSaturationBase` | `1.5` | Base Flora saturation. food or saturation points. |
| `floraSaturationFactor` | `3` | Flora saturation factor. Unitless multiplier. |
| `resistTicksBase` | `60` | Base Resist ticks. Server ticks (20 ticks = 1 second). |
| `resistTicksFactor` | `140` | Resist ticks factor. Server ticks (20 ticks = 1 second). |
| `eatCooldownMillis` | `350` | Eat cooldown millis. Milliseconds. |
| `xpPerFungus` | `4` | XP awarded for xp per fungus. |
| `xpPerNetherMeal` | `3` | XP awarded for xp per nether meal. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Ashwalker (`nether-ashwalker`)

Ignore magma-block and campfire burns, and shrug off most soul-fire damage at mastery.

**Runtime entry points:** on taking damage; periodic evaluation every 4000 ms.

**Menu displays:** Magma Block Immunity; Campfire Immunity; Soul-Fire Damage Reduction.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherAshwalker` |
| Icon | `CAMPFIRE` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.7 |
| Tick interval (ms) | 4000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-ashwalker.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `campfireUnlockLevel` | `2` | Campfire unlock level. Level or effect-amplifier units. |
| `soulFireReduction` | `0.8` | Soul fire reduction. |
| `soulFireMaxFireTicks` | `20` | Soul fire max fire ticks. Server ticks (20 ticks = 1 second). |
| `xpPerNegatedDamage` | `3` | XP awarded for xp per negated damage. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Wither Harvest (`nether-wither-harvest`)

Wither skeletons yield extra bones and coal, with slightly improved skull odds.

**Runtime entry points:** on entity death / kill credit; periodic evaluation every 5000 ms.

**Menu displays:** Bonus Bones; Bonus Coal; Improved Skull Chance.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `NetherWitherHarvest` |
| Icon | `BONE` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.7 |
| Tick interval (ms) | 5000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-wither-harvest.toml` |

Listened events:

- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusBonesBase` | `1` | Base Bonus bones. |
| `bonusBonesFactor` | `2` | Bonus bones factor. Unitless multiplier. |
| `bonusCoalBase` | `0.5` | Base Bonus coal. |
| `bonusCoalFactor` | `2` | Bonus coal factor. Unitless multiplier. |
| `skullChanceBase` | `0.03` | Proc chance for skull chance base. decimal probability. |
| `skullChanceFactor` | `0.12` | Proc chance for skull chance factor. decimal probability. |
| `maxSkullChance` | `0.15` | Proc chance for max skull chance. decimal probability. |
| `xpPerHarvest` | `12` | XP awarded for xp per harvest. |
| `xpOnSkull` | `40` | XP awarded for xp on skull. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
