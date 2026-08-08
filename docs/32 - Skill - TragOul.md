# Skill: TragOul

Skill id `tragoul`. Earn XP by taking damage and surviving at low health. TragOul has 14 registered adaptations and uses the `CRIMSON_ROOTS` icon.

**XP sources:** taking damage, surviving at low health, and TragOul combat effects.

**Milestones / challenges** (stat keys):

- `challenge_trag_1k` tracking `trag.damage`
- `challenge_trag_10k` tracking `trag.damage`
- `challenge_trag_100k` tracking `trag.damage`
- `challenge_trag_hits_500` tracking `trag.hitsrecieved`
- `challenge_trag_hits_5k` tracking `trag.hitsrecieved`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `tragoul` |
| Class | `SkillTragOul` |
| Icon | `CRIMSON_ROOTS` |
| Color | `AQUA` |
| Interval (ms) | `2755` |
| Skill config | `plugins/Adapt/adapt/skills/tragoul.toml` |
| Adaptation count | 14 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/tragoul.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `deathXpLoss` | `250` | Amount of tragoul XP removed on death, clamped so XP never drops below zero. |
| `takeAwaySkillsOnDeath` | `false` | When true, player death applies the configured TragOul skill-loss behavior. |
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&b"` | Legacy ampersand color code used for this skill in menus and text. |
| `showParticles` | `true` | Controls whether particles are emitted. |
| `cooldownDelay` | `450` | Minimum delay between passive skill XP awards, in milliseconds. |
| `damageReceivedXpMultiplier` | `4.8` | Unitless multiplier applied to XP from damage received multiplier. |
| `lowHealthSurvivalXP` | `28` | TragOul XP awarded for an eligible low-health survival check. |
| `challengeTragReward` | `500` | Reward for the trag challenge. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Thorns (`tragoul-thorns`)

Reflect damage back to your attacker.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 25000 ms.

**Menu displays:** Damage retaliated when struck.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulThorns` |
| Icon | `CACTUS` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-thorns.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageMultiplierPerLevel` | `1.75` | Damage multiplier per level. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Globe of Pain (`tragoul-globe`)

Distributes outgoing damage across nearby enemies.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 25000 ms.

**Menu displays:** Sharing range and bonus damage distributed across nearby enemies.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulGlobe` |
| Icon | `CRYING_OBSIDIAN` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-globe.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldown` | `1` | Cooldown. |
| `rangePerLevel` | `3.0` | Range per level. Blocks. |
| `initalRange` | `5.0` | Inital range. Blocks. |
| `bonusDamagePerLevel` | `1` | Bonus damage per level. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Will of Pain (`tragoul-healing`)

Every eligible attacker who damages you loses a small fixed amount of life, which is restored to you.

**Runtime entry points:** on taking damage; periodic evaluation every 25000 ms.

**Menu displays:** Health drained from each attacker and restored to you.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulHealing` |
| Icon | `GLISTERING_MELON_SLICE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-healing.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `drainDamageStart` | `0.5` | Life drained from each attacker at adaptation level one. |
| `drainDamageEnd` | `2.0` | Life drained from each attacker at maximum adaptation level. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Corpse Lances (`tragoul-lance`)

Killing an enemy launches a red-tipped corpse lance that never targets you. Its damage is tripled while you wear no armor.

**Runtime entry points:** on entity death / kill credit; on player death.

**Menu displays:** Corpse-lance chaining; mitigated self-damage; maximum lances equal to one plus adaptation level.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulLance` |
| Icon | `TRIDENT` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-lance.toml` |

Listened events:

- `EntityDeathEvent` (`onEntityDeath`) — on entity death / kill credit
- `PlayerQuitEvent` (`on`)
- `PlayerDeathEvent` (`on`) — on player death

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `seekerDelay` | `12` | Seeker delay. |
| `seekerDamageMultiplier` | `1.0` | Seeker damage multiplier. Unitless multiplier. |
| `selfDamageAtFirstLevel` | `6.0` | Flat damage dealt to the caster when a level-one lance hits. |
| `selfDamageAtMaxLevel` | `2.0` | Flat damage dealt to the caster when a max-level lance hits. |
| `unarmoredDamageMultiplier` | `3.0` | Damage multiplier applied when the owner has no armor equipped. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Blood Pact (`tragoul-blood-pact`)

Losing at least 2 hearts after armor, Resistance, and absorption can trigger random beneficial effects.

**Runtime entry points:** on taking damage; on entity death / kill credit.

**Menu displays:** Proc Chance; Buff Duration; Proc Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulBloodPact` |
| Icon | `NETHER_WART` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.62 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-blood-pact.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage
- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `minDamageTriggerHearts` | `2.0` | Lower bound or activation threshold for min damage trigger hearts. health points (2 points = 1 heart). |
| `procChanceBase` | `0.12` | Proc chance for proc chance base. decimal probability. |
| `procChanceFactor` | `0.38` | Proc chance for proc chance factor. decimal probability. |
| `maxProcChance` | `0.5` | Maximum XP credited for max proc chance. |
| `procCooldownMillisBase` | `18000` | Base Proc cooldown millis. Milliseconds. |
| `procCooldownMillisFactor` | `12000` | Proc cooldown millis factor. Milliseconds. |
| `effectDurationTicksBase` | `100` | Base Effect duration ticks. Server ticks (20 ticks = 1 second). |
| `effectDurationTicksFactor` | `150` | Effect duration ticks factor. Server ticks (20 ticks = 1 second). |
| `buffCountBase` | `1` | Base Buff count. |
| `buffCountFactor` | `2` | Buff count factor. Unitless multiplier. |
| `bonusBuffChanceBase` | `0.08` | Proc chance for bonus buff chance base. decimal probability. |
| `bonusBuffChanceFactor` | `0.34` | Proc chance for bonus buff chance factor. decimal probability. |
| `xpPerProc` | `24` | XP awarded for xp per proc. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bone Harvest (`tragoul-bone-harvest`)

Kills can spawn blood globes or bone snowball globes that grant buffs when picked up.

**Runtime entry points:** on entity death / kill credit; on `EntityPickupItemEvent`; on `InventoryPickupItemEvent`; periodic evaluation every 10000 ms.

**Menu displays:** Globe Spawn Chance; Globe Lifetime.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulBoneHarvest` |
| Icon | `BONE_BLOCK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 10000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-bone-harvest.toml` |

Listened events:

- `EntityDeathEvent` (`on`) — on entity death / kill credit
- `EntityPickupItemEvent` (`on`)
- `InventoryPickupItemEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `globeChanceBase` | `0.16` | Proc chance for globe chance base. decimal probability. |
| `globeChanceFactor` | `0.42` | Proc chance for globe chance factor. decimal probability. |
| `maxGlobeChance` | `0.7` | Proc chance for max globe chance. decimal probability. |
| `globeLifetimeTicksBase` | `120` | Base Globe lifetime ticks. Server ticks (20 ticks = 1 second). |
| `globeLifetimeTicksFactor` | `220` | Globe lifetime ticks factor. Server ticks (20 ticks = 1 second). |
| `bloodBuffTicks` | `80` | Blood buff ticks. Server ticks (20 ticks = 1 second). |
| `bloodBuffAmplifier` | `1` | Blood buff amplifier. Level or effect-amplifier units. |
| `boneBuffTicks` | `100` | Bone buff ticks. Server ticks (20 ticks = 1 second). |
| `boneBuffAmplifier` | `0` | Bone buff amplifier. Level or effect-amplifier units. |
| `boneBuffCountBase` | `1` | Base Bone buff count. |
| `boneBuffCountFactor` | `2` | Bone buff count factor. Unitless multiplier. |
| `xpPerGlobeSpawned` | `8` | XP awarded for xp per globe spawned. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Corpse Explosion (`tragoul-corpse-explosion`)

Mobs you kill immediately display a blood nova and damage nearby hostile mobs.

**Runtime entry points:** on entity death / kill credit; periodic evaluation every 25000 ms.

**Menu displays:** Nova radius and bonus damage as a share of the victim's maximum health.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulCorpseExplosion` |
| Icon | `WITHER_ROSE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-corpse-explosion.toml` |

Listened events:

- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `3.0` | Base nova radius before level scaling. |
| `radiusFactor` | `3.5` | Additional nova radius granted at max level. |
| `baseDamage` | `3.0` | Flat nova damage applied to every hostile mob hit. |
| `victimHealthFractionBase` | `0.10` | Fraction of the victim's max health added to nova damage before level scaling. |
| `victimHealthFractionFactor` | `0.40` | Additional victim max-health fraction granted at max level. |
| `maxDamage` | `24.0` | Hard cap on nova damage per mob. |
| `maxTargets` | `12` | Maximum hostile mobs damaged per nova, with a hard runtime ceiling of 16. |
| `chainSuppressionMillis` | `5000` | Window in milliseconds during which a nova-damaged mob cannot trigger another nova. |
| `xpPerMobHit` | `6` | XP granted per hostile mob hit by a nova. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Soul Siphon (`tragoul-soul-siphon`)

Every player-attributed damage source siphons part of its final damage as health.

**Runtime entry points:** on taking damage; periodic evaluation every 25000 ms.

**Menu displays:** Attributed-damage healing percentage and maximum health restored per second.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulSoulSiphon` |
| Icon | `SOUL_LANTERN` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-soul-siphon.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `healPercentBase` | `0.05` | Fraction of final attributed damage returned as healing before level scaling. |
| `healPercentFactor` | `0.32` | Additional attributed-damage lifesteal fraction granted at max level. |
| `healCapPerSecondBase` | `2.0` | Maximum health restored per second before level scaling. |
| `healCapPerSecondFactor` | `6.5` | Additional per-second healing cap granted at max level. |
| `xpPerHeal` | `3` | XP granted per successful siphon heal. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Skeletal Servant (`tragoul-skeletal-servant`)

Sneak-right-click with bones to raise temporary skeletal servants, with one living servant allowed per level. Servants spawn with level-scaled random gear, inherit other TragOul perks, and hunt the last target you struck or that struck you. Summoning at the cap recycles the oldest servant and consumes a level-scaled number of bones.

**Runtime entry points:** on block/entity/air interact (click); when mobs target; on melee/projectile hit (damage); on entity death / kill credit; on `EntitiesUnloadEvent`; on player death; periodic evaluation every 25000 ms.

**Menu displays:** Summon controls; servant lifetime, bone cost, cooldown, cap, inherited TragOul effects, and maximum-health cost.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulSkeletalServant` |
| Icon | `SKELETON_SKULL` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.75 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-skeletal-servant.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `EntityTargetLivingEntityEvent` (`on`) — when mobs target
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDamageByEntityEvent` (`onCombatPerks`) — on melee/projectile hit (damage)
- `EntityDeathEvent` (`on`) — on entity death / kill credit
- `EntitiesUnloadEvent` (`on`)
- `EntityDeathEvent` (`onServantKill`) — on entity death / kill credit
- `PlayerQuitEvent` (`on`)
- `PlayerDeathEvent` (`on`) — on player death

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `boneCostBase` | `8` | Bones consumed per summon before level scaling. |
| `boneCostReduction` | `5` | Bone cost reduction granted at max level. |
| `durationTicksBase` | `400` | Servant lifetime in ticks before level scaling. |
| `durationTicksFactor` | `800` | Additional servant lifetime ticks granted at max level. |
| `cooldownMillisBase` | `10000` | Summon cooldown in milliseconds before level scaling. |
| `cooldownMillisFactor` | `9000` | Cooldown reduction in milliseconds granted at max level. |
| `servantCapPerLevel` | `1.0` | Living servants allowed per adaptation level, with a hard runtime ceiling of 16 per owner. |
| `replaceOldestAtCap` | `true` | Replaces the oldest living servant when summoning at the cap. |
| `playerThreatWindowMillis` | `5000` | Window in milliseconds during which the entity the owner last hit or was hit by stays the pack's priority target. |
| `gearChancePerPiece` | `0.55` | Chance for each armor piece to be equipped on a freshly summoned servant. |
| `enchantChanceBase` | `0.0` | Base chance for an equipped piece to receive an enchantment at mid gear tiers. |
| `enchantChanceFactor` | `0.45` | Additional enchant chance granted at max level. |
| `bowChance` | `0.3` | Chance a servant spawns with a bow instead of a sword. |
| `healthBonusPerLevel` | `3.0` | Bonus max health granted to servants per adaptation level. |
| `attackBonusPerLevel` | `1.0` | Bonus attack damage granted to servants per adaptation level. |
| `retargetIntervalTicks` | `20` | Ticks between servant retarget pulses, clamped to at least 10 ticks. |
| `targetSearchRadius` | `12` | Radius the servant scans for hostile mobs to attack, capped at 24 blocks. |
| `xpPerSummon` | `30` | XP granted per servant summon. |
| `healthCostEnabled` | `true` | Enables the owner max health upkeep while servants are alive. |
| `healthCostPerMinion` | `2.0` | Max health removed from the owner per living servant. |
| `minimumOwnerMaxHealth` | `4.0` | Lowest max health the servant upkeep can reduce the owner to. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Marrow Armor (`tragoul-marrow-armor`)

Bones in your inventory shatter to absorb part of incoming hits.

**Runtime entry points:** on taking damage; periodic evaluation every 25000 ms.

**Menu displays:** Damage share absorbed per bone and internal cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulMarrowArmor` |
| Icon | `BONE_MEAL` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-marrow-armor.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `minDamageToTrigger` | `2.0` | Minimum final damage required before a bone is consumed. |
| `absorbPercentBase` | `0.20` | Fraction of the hit absorbed before level scaling. |
| `absorbPercentFactor` | `0.30` | Additional absorbed fraction granted at max level. |
| `maxAbsorbPercent` | `0.6` | Hard cap on the absorbed fraction of a hit. |
| `internalCooldownMillisBase` | `4000` | Internal cooldown in milliseconds before level scaling. |
| `internalCooldownMillisFactor` | `2000` | Cooldown reduction in milliseconds granted at max level. |
| `xpPerAbsorb` | `8` | XP granted per absorbed hit. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Curse of Frailty (`tragoul-curse-of-frailty`)

Attackers receive Weakness and, at higher levels, Slowness.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 5000 ms.

**Menu displays:** Attackers are cursed with Weakness; Curse Duration; Attackers are also cursed with Slowness.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulCurseOfFrailty` |
| Icon | `FERMENTED_SPIDER_EYE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 5000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-curse-of-frailty.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `curseDurationTicksBase` | `60` | Curse duration in ticks before level scaling. |
| `curseDurationTicksFactor` | `100` | Additional curse duration ticks granted at max level. |
| `slownessUnlockPercent` | `0.6` | Level progress required before slowness is added to the curse. |
| `slownessAmplifier` | `0` | Amplifier of the slowness component once unlocked. |
| `perAttackerCooldownMillis` | `4000` | Per-attacker cooldown in milliseconds between curses. |
| `xpPerCurse` | `5` | XP granted per curse applied. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Death Sense (`tragoul-death-sense`)

Sense wounded creatures and players near you through walls with a private outline that intensifies in color as their health falls.

**Runtime entry points:** on taking damage; on entity death / kill credit; on `EntitiesLoadEvent`; on `EntitiesUnloadEvent`; periodic evaluation every 50 ms while its conditions hold.

**Menu displays:** Dying-prey health threshold and sense radius.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulDeathSense` |
| Icon | `SPIDER_EYE` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.6 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-death-sense.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage
- `EntityDeathEvent` (`on`) — on entity death / kill credit
- `EntitiesLoadEvent` (`on`)
- `EntitiesUnloadEvent` (`on`)
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `8` | Scan radius before level scaling. |
| `radiusFactor` | `8` | Additional scan radius granted at max level. |
| `maxRadius` | `32` | Maximum effective sensing radius, capped internally at 32 blocks. |
| `healthThresholdStart` | `0.5` | Health fraction at or below which targets are sensed at adaptation level one. |
| `healthThresholdEnd` | `0.9` | Health fraction at or below which targets are sensed at maximum adaptation level. |
| `maxOwnersPerTick` | `24` | Maximum learned owners refreshed per scheduler tick, capped internally at 24. |
| `maxTargetInspectionsPerTick` | `48` | Maximum indexed living targets inspected per scheduler tick, capped internally at 48. |
| `maxMarksPerTick` | `12` | Maximum owner-specific target glows refreshed per scheduler tick, capped internally at 12. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Plague Bearer (`tragoul-plague-bearer`)

Mobs that die poisoned or withered by you spread an amplified affliction across a large radius to any nearby mob you can damage.

**Runtime entry points:** on melee/projectile hit (damage); on entity death / kill credit; periodic evaluation every 25000 ms.

**Menu displays:** Your poison and wither spread with increased potency on death; Spread Radius; Spread Effect Duration.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulPlagueBearer` |
| Icon | `POISONOUS_POTATO` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-plague-bearer.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `spreadRadiusStart` | `8` | Spread radius at adaptation level one. |
| `spreadRadiusEnd` | `20` | Spread radius at maximum adaptation level. |
| `spreadDurationTicksBase` | `80` | Spread effect duration in ticks before level scaling. |
| `spreadDurationTicksFactor` | `120` | Additional spread effect duration ticks granted at max level. |
| `maxGenerations` | `3` | Maximum plague generations a single affliction can chain through. |
| `maxSpreadTargets` | `6` | Maximum eligible mobs infected per death. |
| `amplifierBonus` | `1` | Potion amplifier levels added when the affliction spreads. |
| `afflictionFreshnessMillis` | `15000` | Window in milliseconds during which an affliction mark is considered fresh at death. |
| `xpPerInfection` | `6` | XP granted per mob infected by the spread. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Last Rites (`tragoul-last-rites`)

A killing blow leaves you at 1 HP as a fleeting spirit instead of dying.

**Runtime entry points:** on taking damage; periodic evaluation every 25000 ms.

**Menu displays:** One-health spirit duration and activation cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TragoulLastRites` |
| Icon | `TOTEM_OF_UNDYING` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 6 |
| Cost factor | 0.85 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-last-rites.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `spiritDurationTicks` | `60` | Duration in ticks of the spirit state after defying death. |
| `resistanceAmplifier` | `3` | Amplifier of the resistance effect during the spirit state. |
| `targetClearRadius` | `12` | Radius in which hostile mob targets locked onto you are cleared. |
| `cooldownMillisBase` | `600000` | Cooldown in milliseconds before level scaling. |
| `cooldownMillisFactor` | `300000` | Cooldown reduction in milliseconds granted at max level. |
| `xpPerSave` | `120` | XP granted per death defied. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## Support classes (not player adaptations)

- `TragoulReactiveDamage` — marks reflected or reactive damage while it runs so TragOul handlers do not recursively trigger themselves.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
