# Skill: Hunter

Skill id `hunter`. Earn XP by killing mobs. Several Hunter adaptations trigger when the player is struck and consume hunger. Hunter has 14 registered adaptations and uses the `BONE` icon.

**XP sources:** killing eligible mobs; spawn tracking supplies provenance for those rewards.

**Milestones / challenges** (stat keys):

- `challenge_novice_hunter` tracking `killed.monsters`
- `challenge_intermediate_hunter` tracking `killed.monsters`
- `challenge_advanced_hunter` tracking `killed.monsters`
- `challenge_creeper_conqueror` tracking `killed.creepers`
- `challenge_creeper_annihilator` tracking `killed.creepers`
- `challenge_kills_500` tracking `killed.kills`
- `challenge_kills_5k` tracking `killed.kills`
- `challenge_boss_1` tracking `hunter.boss.kills`
- `challenge_boss_10` tracking `hunter.boss.kills`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `hunter` |
| Class | `SkillHunter` |
| Icon | `BONE` |
| Color | `RED` |
| Interval (ms) | `4150` |
| Skill config | `plugins/Adapt/adapt/skills/hunter.toml` |
| Adaptation count | 14 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/hunter.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&c"` | Legacy ampersand color code used for this skill in menus and text. |
| `getXpForAttackingWithTools` | `true` | XP awarded for get for attacking with tools. |
| `creeperKillMultiplier` | `2` | Unitless Hunter XP multiplier for creeper kills. |
| `killMaxHealthXPMultiplier` | `3.0` | Unitless multiplier applied to XP from kill max health multiplier. |
| `cooldownDelay` | `1000` | Minimum delay between passive skill XP awards, in milliseconds. |
| `spawnerMobReductionXpMultiplier` | `0.3` | Unitless multiplier applied to XP from spawner mob reduction multiplier. |
| `killsChallengeReward` | `500` | Kills challenge reward. |
| `bossKillReward` | `1000` | Fixed Hunter XP awarded for an eligible boss kill. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Adrenaline (`hunter-adrenaline`)

Melee damage increases as health falls.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 1911 ms.

**Menu displays:** Max Damage.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterAdrenaline` |
| Icon | `LEATHER_HELMET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 1911 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-adrenaline.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageBase` | `0.12` | Base Damage. health points (2 points = 1 heart). |
| `damageFactor` | `0.21` | Damage factor. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Hunter's Regen (`hunter-regen`)

When you are struck you gain regeneration, at the cost of hunger.

**Runtime entry points:** on taking damage; periodic evaluation every 9744 ms.

**Menu displays:** Regeneration stacks gained when struck; three-second effect duration; hunger cost per stack; amplifier stacks while duration does not.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterRegen` |
| Icon | `AXOLOTL_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 9744 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-regen.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | Use consumable. |
| `poisonPenalty` | `true` | Poison penalty. |
| `stackHungerPenalty` | `false` | When true, repeated triggers increase the Hunger penalty amplifier. |
| `stackPoisonPenalty` | `false` | Stack poison penalty. |
| `stackBuff` | `false` | When true, repeated triggers increase the beneficial effect amplifier. |
| `baseEffectbyLevel` | `30` | Base effectby level. Level or effect-amplifier units. |
| `baseHungerFromLevel` | `10` | Base hunger from level. food or saturation points. |
| `baseHungerDuration` | `50` | Base Hunger-effect duration, in server ticks (20 ticks = 1 second). |
| `basePoisonFromLevel` | `6` | Base poison from level. Level or effect-amplifier units. |
| `consumable` | `"ROTTEN_FLESH"` | Consumable. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Vanishing Step (`hunter-invis`)

When you are struck you gain invisibility, at the cost of hunger.

**Runtime entry points:** on taking damage; periodic evaluation every 9444 ms.

**Menu displays:** Invisibility gained when struck; three-second base duration; hunger cost per stack; invisibility duration.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterInvis` |
| Icon | `TROPICAL_FISH_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 9444 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-invis.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | Use consumable. |
| `poisonPenalty` | `true` | Poison penalty. |
| `stackHungerPenalty` | `false` | When true, repeated triggers increase the Hunger penalty amplifier. |
| `stackPoisonPenalty` | `false` | Stack poison penalty. |
| `stackBuff` | `false` | When true, repeated triggers increase the beneficial effect amplifier. |
| `baseEffectbyLevel` | `100` | Base effectby level. Level or effect-amplifier units. |
| `baseHungerFromLevel` | `10` | Base hunger from level. food or saturation points. |
| `baseHungerDuration` | `50` | Base Hunger-effect duration, in server ticks (20 ticks = 1 second). |
| `basePoisonFromLevel` | `6` | Base poison from level. Level or effect-amplifier units. |
| `consumable` | `"ROTTEN_FLESH"` | Consumable. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Hunter's Heights (`hunter-jumpboost`)

When you are struck you gain jump-boost, at the cost of hunger.

**Runtime entry points:** on taking damage; periodic evaluation every 9544 ms.

**Menu displays:** Jump Boost stacks gained when struck; three-second effect duration; hunger cost per stack; amplifier stacks while duration does not.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterJumpBoost` |
| Icon | `PUFFERFISH_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 9544 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-jumpboost.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | Use consumable. |
| `poisonPenalty` | `true` | Poison penalty. |
| `stackHungerPenalty` | `false` | When true, repeated triggers increase the Hunger penalty amplifier. |
| `stackPoisonPenalty` | `false` | Stack poison penalty. |
| `stackBuff` | `false` | When true, repeated triggers increase the beneficial effect amplifier. |
| `baseEffectbyLevel` | `100` | Base effectby level. Level or effect-amplifier units. |
| `baseHungerFromLevel` | `10` | Base hunger from level. food or saturation points. |
| `baseHungerDuration` | `50` | Base Hunger-effect duration, in server ticks (20 ticks = 1 second). |
| `basePoisonFromLevel` | `6` | Base poison from level. Level or effect-amplifier units. |
| `consumable` | `"ROTTEN_FLESH"` | Consumable. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Hunter's Luck (`hunter-luck`)

When you are struck you gain luck, at the cost of hunger.

**Runtime entry points:** on taking damage; on player death; periodic evaluation every 9644 ms.

**Menu displays:** Luck stacks gained when struck; three-second effect duration; hunger cost per stack; amplifier stacks while duration does not.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterLuck` |
| Icon | `TADPOLE_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 9644 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-luck.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage
- `PlayerDeathEvent` (`on`) — on player death

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | Use consumable. |
| `poisonPenalty` | `true` | Poison penalty. |
| `stackHungerPenalty` | `false` | When true, repeated triggers increase the Hunger penalty amplifier. |
| `stackPoisonPenalty` | `false` | Stack poison penalty. |
| `stackBuff` | `false` | When true, repeated triggers increase the beneficial effect amplifier. |
| `baseEffectbyLevel` | `100` | Base effectby level. Level or effect-amplifier units. |
| `baseHungerFromLevel` | `10` | Base hunger from level. food or saturation points. |
| `baseHungerDuration` | `50` | Base Hunger-effect duration, in server ticks (20 ticks = 1 second). |
| `basePoisonFromLevel` | `6` | Base poison from level. Level or effect-amplifier units. |
| `consumable` | `"ROTTEN_FLESH"` | Consumable. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Hunter's Speed (`hunter-speed`)

When you are struck you gain speed, at the cost of hunger.

**Runtime entry points:** on taking damage.

**Menu displays:** Speed stacks gained when struck; three-second effect duration; hunger cost per stack; amplifier stacks while duration does not.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterSpeed` |
| Icon | `SUGAR` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-speed.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | Use consumable. |
| `poisonPenalty` | `true` | Poison penalty. |
| `stackHungerPenalty` | `false` | When true, repeated triggers increase the Hunger penalty amplifier. |
| `stackPoisonPenalty` | `false` | Stack poison penalty. |
| `stackBuff` | `false` | When true, repeated triggers increase the beneficial effect amplifier. |
| `baseEffectbyLevel` | `100` | Base effectby level. Level or effect-amplifier units. |
| `baseHungerDuration` | `50` | Base Hunger-effect duration, in server ticks (20 ticks = 1 second). |
| `baseHungerFromLevel` | `10` | Base hunger from level. food or saturation points. |
| `basePoisonFromLevel` | `6` | Base poison from level. Level or effect-amplifier units. |
| `baseHorizontalSpeed` | `0.13` | Base horizontal speed used for hunter bursts before amplifier scaling. |
| `maxHorizontalSpeed` | `0.32` | Maximum horizontal speed this adaptation can force. |
| `accelPerTick` | `0.045` | How fast velocity accelerates toward the burst target per tick. |
| `brakePerTick` | `0.08` | How fast velocity decays when movement input is released. |
| `stopThreshold` | `0.01` | Horizontal velocity threshold considered fully stopped. |
| `hardStopOnInvalidState` | `true` | If true, burst velocity is force-cleared when entering invalid states. |
| `fallbackInputVelocityThreshold` | `0.0008` | Fallback movement threshold used when direct input API is unavailable. |
| `consumable` | `"ROTTEN_FLESH"` | Consumable. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Hunter's Strength (`hunter-strength`)

When you are struck you gain strength, at the cost of hunger.

**Runtime entry points:** on taking damage; on player death; periodic evaluation every 9044 ms.

**Menu displays:** Strength stacks gained when struck; three-second effect duration; hunger cost per stack; amplifier stacks while duration does not.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterStrength` |
| Icon | `COD_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 9044 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-strength.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage
- `PlayerDeathEvent` (`on`) — on player death

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | Use consumable. |
| `poisonPenalty` | `true` | Poison penalty. |
| `stackHungerPenalty` | `false` | When true, repeated triggers increase the Hunger penalty amplifier. |
| `stackPoisonPenalty` | `false` | Stack poison penalty. |
| `stackBuff` | `false` | When true, repeated triggers increase the beneficial effect amplifier. |
| `baseEffectbyLevel` | `25` | Base effectby level. Level or effect-amplifier units. |
| `baseHungerFromLevel` | `10` | Base hunger from level. food or saturation points. |
| `basePoisonFromLevel` | `6` | Base poison from level. Level or effect-amplifier units. |
| `baseHungerDuration` | `50` | Base Hunger-effect duration, in server ticks (20 ticks = 1 second). |
| `consumable` | `"ROTTEN_FLESH"` | Consumable. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Hunter's Resistance (`hunter-resistance`)

When you are struck you gain resistance, at the cost of hunger.

**Runtime entry points:** on taking damage; periodic evaluation every 9844 ms.

**Menu displays:** Resistance stacks gained when struck; three-second effect duration; hunger cost per stack; amplifier stacks while duration does not.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterResistance` |
| Icon | `POWDER_SNOW_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 9844 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-resistance.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | Use consumable. |
| `poisonPenalty` | `true` | Poison penalty. |
| `stackHungerPenalty` | `false` | When true, repeated triggers increase the Hunger penalty amplifier. |
| `stackPoisonPenalty` | `false` | Stack poison penalty. |
| `stackBuff` | `false` | When true, repeated triggers increase the beneficial effect amplifier. |
| `baseEffectbyLevel` | `10` | Base effectby level. Level or effect-amplifier units. |
| `baseHungerFromLevel` | `10` | Base hunger from level. food or saturation points. |
| `baseHungerDuration` | `50` | Base Hunger-effect duration, in server ticks (20 ticks = 1 second). |
| `basePoisonFromLevel` | `6` | Base poison from level. Level or effect-amplifier units. |
| `consumable` | `"ROTTEN_FLESH"` | Consumable. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Items Drop-To-Inventory (`hunter-drop-to-inventory`)

Kills and blocks broken with a sword in hand send their drops straight into your inventory.

**Runtime entry points:** on `BlockDropItemEvent`; on entity death / kill credit; periodic evaluation every 18440 ms.

**Menu displays:** Eligible mob and block drops go directly into the inventory when space is available.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterDropToInventory` |
| Icon | `TRAPPED_CHEST` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 1 |
| Cost factor | 1 |
| Tick interval (ms) | 18440 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-drop-to-inventory.toml` |

Listened events:

- `BlockDropItemEvent` (`on`)
- `EntityDeathEvent` (`on`) — on entity death / kill credit

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Trophy Skinner (`hunter-trophy-skinner`)

Precision kills can yield bonus trophy drops and occasional mob heads.

**Runtime entry points:** on entity death / kill credit; periodic evaluation every 2000 ms.

**Menu displays:** Bonus Trophy Chance; Head Drop Chance; Minimum Ranged Precision Distance.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterTrophySkinner` |
| Icon | `ZOMBIE_HEAD` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.8 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-trophy-skinner.toml` |

Listened events:

- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `dropChanceBase` | `0.14` | Proc chance for drop chance base. decimal probability. |
| `dropChanceFactor` | `0.3` | Proc chance for drop chance factor. decimal probability. |
| `maxDropChance` | `0.5` | Proc chance for max drop chance. decimal probability. |
| `headChanceBase` | `0.015` | Proc chance for head chance base. decimal probability. |
| `headChanceFactor` | `0.08` | Proc chance for head chance factor. decimal probability. |
| `maxHeadChance` | `0.12` | Proc chance for max head chance. decimal probability. |
| `trophyAmountBase` | `1` | Base Trophy amount. |
| `trophyAmountFactor` | `2` | Trophy amount factor. Unitless multiplier. |
| `minimumRangeBase` | `18` | Lower bound or activation threshold for minimum range base. Blocks. |
| `minimumRangeFactor` | `10` | Minimum range factor. Blocks. |
| `xpPerTrophy` | `16` | XP awarded for xp per trophy. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Predator Focus (`hunter-predator-focus`)

Repeated strikes on the same target ramp your damage; switching targets resets the ramp.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Maximum ramp stacks; Bonus melee damage per ramp stack; Bonus melee damage at full ramp.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterPredatorFocus` |
| Icon | `TARGET` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 5 |
| Cost factor | 0.45 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-predator-focus.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `perStackBonus` | `0.07` | Bonus melee damage added per ramp stack for the Hunter Predator Focus adaptation. |
| `rampCapBase` | `3` | Base ramp stack cap before level scaling for the Hunter Predator Focus adaptation. |
| `rampCapFactor` | `6` | Additional ramp stack cap gained across levels for the Hunter Predator Focus adaptation. |
| `decayMillis` | `3500` | Milliseconds of inactivity before the ramp decays for the Hunter Predator Focus adaptation. |
| `xpPerRampedHit` | `2` | Silent xp granted per ramped hit for the Hunter Predator Focus adaptation. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Big Game Hunter (`hunter-big-game`)

Deal bonus damage to and reap extra drops from large and boss-class mobs.

**Runtime entry points:** on melee/projectile hit (damage); on entity death / kill credit.

**Menu displays:** Bonus damage versus big game; Extra drop chance versus big game.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterBigGameHunter` |
| Icon | `NETHERITE_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 6 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-big-game.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusDamageBase` | `0.15` | Base bonus damage fraction versus big game for the Hunter Big Game Hunter adaptation. |
| `bonusDamageFactor` | `0.45` | Bonus damage fraction gained across levels for the Hunter Big Game Hunter adaptation. |
| `extraDropChanceBase` | `0.15` | Base per-drop duplication chance versus big game for the Hunter Big Game Hunter adaptation. |
| `extraDropChanceFactor` | `0.45` | Per-drop duplication chance gained across levels for the Hunter Big Game Hunter adaptation. |
| `maxExtraDropChance` | `0.75` | Maximum per-drop duplication chance for the Hunter Big Game Hunter adaptation. |
| `maxExtraDropsPerKill` | `6` | Maximum bonus drops duplicated from one big-game kill for the Hunter Big Game Hunter adaptation. |
| `xpPerBigGameKill` | `45` | Xp granted per big-game kill for the Hunter Big Game Hunter adaptation. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Blood Trail (`hunter-blood-trail`)

Mobs you wound below half health leave a private glowing blood trail only you can see.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 250 ms while its conditions hold.

**Menu displays:** Seconds the scent trail lingers; Blocks you can track the scent from.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterBloodTrail` |
| Icon | `REDSTONE` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 250 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-blood-trail.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `trailDurationTicksBase` | `100` | Base blood-trail duration in ticks for the Hunter Blood Trail adaptation. |
| `trailDurationTicksFactor` | `200` | Additional blood-trail duration in ticks gained across levels for the Hunter Blood Trail adaptation. |
| `rangeBase` | `16` | Base tracking range in blocks for the Hunter Blood Trail adaptation. |
| `rangeFactor` | `32` | Additional tracking range in blocks gained across levels for the Hunter Blood Trail adaptation. |
| `woundHealthFraction` | `0.5` | Fraction of max health a target must fall to or below to bleed for the Hunter Blood Trail adaptation. |
| `maxTrackedWounds` | `64` | Maximum simultaneously tracked blood trails for the Hunter Blood Trail adaptation. |
| `trailThickness` | `0.06` | Thickness of each private glowing blood trail segment. |
| `displayDurationTicks` | `30` | Duration of each private glowing blood trail segment, in ticks. |
| `xpPerWound` | `3` | Silent xp granted when a fresh target starts bleeding for the Hunter Blood Trail adaptation. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Snare Line (`hunter-snare-line`)

Craft a string-and-iron snare; hostile mobs that cross it are rooted briefly.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 250 ms while its conditions hold.

**Menu displays:** Seconds mobs are rooted; Trigger charges per snare; Craft with string and iron, then right-click the ground to set it.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HunterSnareLine` |
| Icon | `TRIPWIRE_HOOK` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 5 |
| Cost factor | 0.5 |
| Tick interval (ms) | 250 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-snare-line.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rootDurationTicksBase` | `30` | Base snare root duration in ticks for the Hunter Snare Line adaptation. |
| `rootDurationTicksFactor` | `50` | Additional snare root duration in ticks gained across levels for the Hunter Snare Line adaptation. |
| `chargesBase` | `3` | Base trigger charges per crafted snare for the Hunter Snare Line adaptation. |
| `chargesFactor` | `5` | Additional trigger charges gained across levels for the Hunter Snare Line adaptation. |
| `triggerRadius` | `1.6` | Snare trigger radius in blocks for the Hunter Snare Line adaptation. |
| `rootAmplifier` | `6` | Slowness-equivalent amplifier mapped to a movement speed reduction of 15 percent per level, capped at a full stop, while a mob is rooted for the Hunter Snare Line adaptation. |
| `rearmBufferMillis` | `500` | Milliseconds a snared mob is immune to re-triggering the same snare for the Hunter Snare Line adaptation. |
| `snareLifetimeTicks` | `2400` | Ticks a placed snare persists before decaying for the Hunter Snare Line adaptation. |
| `maxSnaresPerPlayer` | `4` | Maximum concurrent snares one player may keep placed for the Hunter Snare Line adaptation. |
| `maxActiveSnares` | `64` | Maximum concurrent snares processed by this server for the Hunter Snare Line adaptation. |
| `maxTargetsPerScan` | `8` | Maximum mobs scheduled for rooting by one snare per scan for the Hunter Snare Line adaptation. |
| `xpPerSnare` | `6` | Xp granted per mob snared for the Hunter Snare Line adaptation. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
