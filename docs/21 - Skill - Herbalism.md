# Skill: Herbalism

Skill id `herbalism`. Earn XP by farming, harvesting crops, and gathering plants. Herbalism has 15 registered adaptations and uses the `WHEAT` icon.

**XP sources:** farming, harvesting, replanting, gathering and shearing plants, and consuming food.

**Milestones / challenges** (stat keys):

- `challenge_eat_100` tracking `food.eaten`
- `challenge_eat_1000` tracking `food.eaten`
- `challenge_eat_10000` tracking `food.eaten`
- `challenge_harvest_100` tracking `harvest.blocks`
- `challenge_harvest_1000` tracking `harvest.blocks`
- `challenge_plant_100` tracking `harvest.planted`
- `challenge_plant_1k` tracking `harvest.planted`
- `challenge_plant_5k` tracking `harvest.planted`
- `challenge_compost_50` tracking `harvest.composted`
- `challenge_compost_500` tracking `harvest.composted`
- `challenge_shear_50` tracking `herbalism.sheared`
- `challenge_shear_250` tracking `herbalism.sheared`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `herbalism` |
| Class | `SkillHerbalism` |
| Icon | `WHEAT` |
| Color | `GREEN` |
| Interval (ms) | `3990` |
| Skill config | `plugins/Adapt/adapt/skills/herbalism.toml` |
| Adaptation count | 15 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/herbalism.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `harvestXpCooldown` | `3500` | Rate-limit or history window for XP from harvest cooldown. |
| `foodConsumeXP` | `35` | Herbalism XP awarded for an eligible food consumption. |
| `shearXP` | `35` | XP awarded for shear. |
| `harvestPerAgeXP` | `5.0` | XP awarded for harvest per age. |
| `plantCropSeedsXP` | `4.0` | Herbalism XP awarded for planting an eligible crop seed. |
| `composterBaseXP` | `2.5` | Base skill XP credited for composter base. |
| `composterLevelXPMultiplier` | `1.25` | Unitless multiplier applied to XP from composter level multiplier. |
| `composterNonZeroLevelBonus` | `25` | Bonus Herbalism XP when composter processing starts above level zero. |
| `composterEmptyBonus` | `5` | Bonus XP when a composter empties after collection. |
| `challengeEat100Reward` | `1250` | Reward for the eat 100 challenge. |
| `challengeEat1kReward` | `6250` | Reward for the eat 1 k challenge. |
| `challengeHarvest100Reward` | `1250` | Reward for the harvest 100 challenge. |
| `challengeHarvest1kReward` | `6250` | Reward for the harvest 1 k challenge. |
| `challengePlant100Reward` | `1250` | Reward for the plant 100 challenge. |
| `challengePlant1kReward` | `6250` | Reward for the plant 1 k challenge. |
| `challengePlant5kReward` | `25000` | Reward for the plant 5 k challenge. |
| `challengeCompost50Reward` | `1250` | Reward for the compost 50 challenge. |
| `challengeCompost500Reward` | `6250` | Reward for the compost 500 challenge. |
| `challengeShear50Reward` | `1250` | Reward for the shear 50 challenge. |
| `challengeShear250Reward` | `6250` | Reward for the shear 250 challenge. |
| `skillColor` | `"&a"` | Legacy ampersand color code used for this skill in menus and text. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Growth Aura (`herbalism-growth-aura`)

Periodically grows nearby plants.

**Runtime entry points:** periodic evaluation every 10 ms while its conditions hold.

**Menu displays:** Block Radius; Growth Aura Strength; Food Cost.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismGrowthAura` |
| Icon | `BONE_MEAL` |
| Max level | 7 |
| Initial knowledge cost | 12 |
| Base knowledge cost | 8 |
| Cost factor | 0.325 |
| Tick interval (ms) | 10 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-growth-aura.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `surfaceOnly` | `true` | Surface only. |
| `minFoodCost` | `0.05` | Lower bound or activation threshold for min food cost. food or saturation points. |
| `maxFoodCost` | `0.4` | Maximum food cost. food or saturation points. |
| `radiusFactor` | `18` | Radius factor. Blocks. |
| `strengthFactor` | `0.75` | Strength factor. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Harvest & Replant (`herbalism-replant`)

Right click a crop with a hoe to harvest & replant it.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 6090 ms.

**Menu displays:** Blocks Replant Radius.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismReplant` |
| Icon | `PUMPKIN_SEEDS` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 6 |
| Cost factor | 0.95 |
| Tick interval (ms) | 6090 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-replant.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldownLvl1` | `2` | Cooldown lvl 1. |
| `baseCooldown` | `30` | Base cooldown. |
| `cooldownFactor` | `30` | Cooldown factor. Unitless multiplier. |
| `bonusCooldown` | `20` | Bonus cooldown. |
| `radiusSub` | `1` | Radius sub. Blocks. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Hungry Shield (`herbalism-hungry-shield`)

Take damage to your hunger before your health, covering more damage types as it levels up.

**Runtime entry points:** on taking damage; periodic evaluation every 875 ms.

**Menu displays:** Resisted by Hunger; Contact, Crushing & Weather Damage Shielded; Melee & Thorns Damage Shielded; Fire & Lava Damage Shielded; Projectile & Explosion Damage Shielded; Magic, Poison & Wither Damage Shielded.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismHungryShield` |
| Icon | `APPLE` |
| Max level | 5 |
| Initial knowledge cost | 10 |
| Base knowledge cost | 7 |
| Cost factor | 0.78 |
| Tick interval (ms) | 875 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-hungry-shield.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `effectivenessBase` | `0.15` | Base Effectiveness. |
| `maxEffectiveness` | `0.95` | Maximum effectiveness. |
| `basicsUnlockLevel` | `1` | Level required before contact, cramming, drowning, suffocation, wall, magma and freeze damage are shielded. |
| `meleeUnlockLevel` | `2` | Level required before melee, sweep and thorns damage is shielded. |
| `fireUnlockLevel` | `3` | Level required before fire, lava and campfire damage is shielded. |
| `burstUnlockLevel` | `4` | Level required before projectile, explosion, falling block and lightning damage is shielded. |
| `magicUnlockLevel` | `5` | Level required before magic, poison, wither, dragon breath and sonic boom damage is shielded. |
| `dotChargeIntervalMs` | `1000` | Milliseconds between hunger charges for damage-over-time sources such as fire, lava, poison and drowning. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Herbalist's Hippo (`herbalism-hippo`)

Food restores additional saturation.

**Runtime entry points:** when consuming food/potion; periodic evaluation every 8111 ms.

**Menu displays:** Food) additional saturation points on consumption.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismHungryHippo` |
| Icon | `POTATO` |
| Max level | 7 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 8 |
| Cost factor | 0.75 |
| Tick interval (ms) | 8111 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-hippo.toml` |

Listened events:

- `PlayerItemConsumeEvent` (`on`) — when consuming food/potion

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Hoe Drop-To-Inventory (`herbalism-drop-to-inventory`)

Harvested crops drop directly into your inventory.

**Runtime entry points:** on `BlockDropItemEvent`; periodic evaluation every 7999 ms.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismDropToInventory` |
| Icon | `HOPPER` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 1 |
| Cost factor | 1 |
| Tick interval (ms) | 7999 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-drop-to-inventory.toml` |

Listened events:

- `BlockDropItemEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Herbalist's Luck (`herbalism-luck`)

Breaking grass or flowers can add a random item to the drops.

**Runtime entry points:** on `BlockDropItemEvent`; periodic evaluation every 8121 ms.

**Menu displays:** Chance to get an item from breaking Flowers; Chance to get an item from breaking Grass.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismLuck` |
| Icon | `EMERALD` |
| Max level | 7 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 8 |
| Cost factor | 0.75 |
| Tick interval (ms) | 8121 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-luck.toml` |

Listened events:

- `BlockDropItemEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `lowChance` | `0.0` | Chance at the lowest adaptation level, on the implementation's 0–100 scale. |
| `highChance` | `90` | Chance at maximum adaptation level, on the implementation's 0–100 scale. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Herbalist's Myconid (`herbalism-myconid`)

Unlocks a mycelium recipe.

**Runtime entry points:** when taking a craft result; periodic evaluation every 17771 ms.

**Menu displays:** Dirt plus one brown mushroom and one red mushroom crafts mycelium.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismMyconid` |
| Icon | `MYCELIUM` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.75 |
| Tick interval (ms) | 17771 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-myconid.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Herbalist's Terralid (`herbalism-terralid`)

Unlocks a grass-block recipe.

**Runtime entry points:** when taking a craft result; periodic evaluation every 17771 ms.

**Menu displays:** Three seeds over three dirt craft three grass blocks.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismTerralid` |
| Icon | `GRASS_BLOCK` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.75 |
| Tick interval (ms) | 17771 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-terralid.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Mushroom Maker (`herbalism-mushroom-blocks`)

Unlocks mushroom-block recipes.

**Runtime entry points:** when taking a craft result; periodic evaluation every 17772 ms.

**Menu displays:** Four mushrooms craft a mushroom block; a block crafts a stem.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismCraftableMushroomBlocks` |
| Icon | `BROWN_MUSHROOM_BLOCK` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 1 |
| Tick interval (ms) | 17772 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-mushroom-blocks.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Webby Creator (`herbalism-cobweb`)

Unlocks a cobweb recipe.

**Runtime entry points:** when taking a craft result; periodic evaluation every 17771 ms.

**Menu displays:** Nine string craft one cobweb.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismCraftableCobweb` |
| Icon | `STRING` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 1 |
| Tick interval (ms) | 17771 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-cobweb.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Seed Sower (`herbalism-seed-sower`)

Sneak-right-click with seeds to plant nearby farmland and soul-sand plots.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 6920 ms.

**Menu displays:** Plant Radius; Max Crops Per Use; Sowing Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismSeedSower` |
| Icon | `WHEAT_SEEDS` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.675 |
| Tick interval (ms) | 6920 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-seed-sower.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseRadius` | `1` | Base radius. Blocks. |
| `radiusFactor` | `2` | Radius factor. Blocks. |
| `baseCropCount` | `3` | Base crop count. |
| `cropCountFactor` | `10` | Crop count factor. Unitless multiplier. |
| `cooldownTicksBase` | `60` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksReduction` | `42` | Cooldown ticks reduction. Server ticks (20 ticks = 1 second). |
| `xpPerCrop` | `1.45` | XP awarded for xp per crop. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Compost Cascade (`herbalism-compost-cascade`)

Sneak-right-click a composter to consume nearby drops, harvest and replant mature crops, compost your inventory, and spend the compost maturing nearby crops. Leaves are only consumed when enabled in the config.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 600 ms.

**Menu displays:** Cascade Radius; Max Items Processed; Compost Fill Chance; Cascade Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismCompostCascade` |
| Icon | `COMPOSTER` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 600 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-compost-cascade.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `consumeLeaves` | `false` | Allows the cascade to break and compost nearby leaf blocks. |
| `radiusBase` | `5.0` | Base Radius. Blocks. |
| `radiusFactor` | `12.0` | Radius factor. Blocks. |
| `maxItemsBase` | `80.0` | Base Maximum items. count. |
| `maxItemsFactor` | `240.0` | Maximum items factor. Unitless multiplier. |
| `fillChanceBase` | `0.5` | Proc chance for fill chance base. decimal probability. |
| `fillChanceFactor` | `0.42` | Proc chance for fill chance factor. decimal probability. |
| `maxFillChance` | `0.98` | Proc chance for max fill chance. decimal probability. |
| `leafCompostBurstsBase` | `3` | Base Leaf compost bursts. |
| `leafCompostBurstsFactor` | `9` | Leaf compost bursts factor. Unitless multiplier. |
| `leafFillChanceMultiplierBase` | `1.35` | Proc chance for leaf fill chance multiplier base. configured chance scale. |
| `leafFillChanceMultiplierFactor` | `0.7` | Proc chance for leaf fill chance multiplier factor. decimal probability. |
| `cooldownTicksBase` | `36.0` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksReduction` | `28.0` | Cooldown ticks reduction. Server ticks (20 ticks = 1 second). |
| `boneMealBase` | `2.0` | Base Bone meal. |
| `boneMealFactor` | `6.0` | Bone meal factor. Unitless multiplier. |
| `readyBonusBoneMealBase` | `2.0` | Base Ready bonus bone meal. |
| `readyBonusBoneMealFactor` | `8.0` | Ready bonus bone meal factor. Unitless multiplier. |
| `itemsPerBoneMealBase` | `20.0` | Base Items per bone meal. count. |
| `itemsPerBoneMealReduction` | `14.0` | Items per bone meal reduction. count. |
| `overflowFillsPerBoneMeal` | `4` | Compost fills wasted on a full composter that are worth one extra bone meal. |
| `maturationAttemptsBase` | `6` | Crop maturation attempts granted at level one. |
| `maturationAttemptsPerLevel` | `6` | Additional crop maturation attempts granted per adaptation level above one. |
| `valuableChanceBase` | `0.01` | Proc chance for valuable chance base. decimal probability. |
| `valuableChanceFactor` | `0.09` | Proc chance for valuable chance factor. decimal probability. |
| `maxValuableChance` | `0.12` | Proc chance for max valuable chance. decimal probability. |
| `valuableRollsBase` | `1` | Base Valuable rolls. |
| `valuableRollsFactor` | `3` | Valuable rolls factor. Unitless multiplier. |
| `xpPerItemConsumed` | `1.2` | XP awarded for xp per item consumed. |
| `xpPerLevelGain` | `2.8` | XP awarded for xp per level gain. Level or effect-amplifier units. |
| `xpPerCropMatured` | `2.0` | Experience granted for every nearby crop the cascade matures. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Rooted Footing (`herbalism-rooted-footing`)

Permanent passive: protect farmland and convert part of fall damage into hunger while on natural ground.

**Runtime entry points:** on block/entity/air interact (click); on taking damage; periodic evaluation every 2050 ms.

**Menu displays:** Fall Damage Converted; Food Per Damage; Prevents Farmland Trample.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismRootedFooting` |
| Icon | `FARMLAND` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.65 |
| Tick interval (ms) | 2050 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-rooted-footing.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `absorbBase` | `0.28` | Base Absorb. |
| `absorbFactor` | `0.12` | Absorb factor. Unitless multiplier. |
| `maxAbsorbPercent` | `0.45` | Maximum absorb percent. |
| `foodPerDamage` | `1.8` | Food points spent per absorbed damage point. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bee Shepherd (`herbalism-bee-shepherd`)

Hold flowers near crops to pulse growth and draw nearby bees toward you.

**Runtime entry points:** periodic evaluation every 10 ms while its conditions hold.

**Menu displays:** Pulse Radius; Growth Attempts; Pulse Cooldown; Bee Growth Bonus.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismBeeShepherd` |
| Icon | `BEE_NEST` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.64 |
| Tick interval (ms) | 10 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-bee-shepherd.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `showGrowthParticles` | `true` | Controls whether growth particles are emitted. |
| `radiusBase` | `7` | Base Radius. Blocks. |
| `radiusFactor` | `12` | Radius factor. Blocks. |
| `growthAttemptsBase` | `10` | Base Growth attempts. |
| `growthAttemptsFactor` | `18` | Growth attempts factor. Unitless multiplier. |
| `growthBonusPerBee` | `0.15` | Extra growth attempts granted per herded bee, as a fraction of the base attempts. |
| `maxBonusBees` | `5` | Maximum number of herded bees that count toward the growth bonus. |
| `growthStepBase` | `1` | Base Growth step. |
| `growthStepFactor` | `2.0` | Growth step factor. Unitless multiplier. |
| `foodCostBase` | `1` | Base Food cost. food or saturation points. |
| `foodCostFactor` | `1.2` | Food cost factor. Unitless multiplier. |
| `pulseMillisBase` | `900` | Base Pulse millis. Milliseconds. |
| `pulseMillisFactor` | `650` | Pulse millis factor. Milliseconds. |
| `beePullStrengthBase` | `0.07` | Base Bee pull strength. |
| `beePullStrengthFactor` | `0.14` | Bee pull strength factor. Unitless multiplier. |
| `xpPerGrowth` | `0.9` | XP awarded for xp per growth. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Spore Bloom (`herbalism-spore-bloom`)

Sneak-right-click mycelium with mushrooms to spread controlled bloom patches.

**Runtime entry points:** when placing blocks; periodic evaluation every 2100 ms.

**Menu displays:** Bloom Attempts; Bloom Radius; Bloom Cooldown; Mushroom Cost.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `HerbalismSporeBloom` |
| Icon | `RED_MUSHROOM_BLOCK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 2100 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-spore-bloom.toml` |

Listened events:

- `BlockPlaceEvent` (`on`) — when placing blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `swapFlowersToMushrooms` | `true` | Allows flowers hit by the bloom to be replaced with mushrooms. |
| `bloomAttemptsBase` | `26` | Base Bloom attempts. |
| `bloomAttemptsFactor` | `58` | Bloom attempts factor. Unitless multiplier. |
| `bloomAttemptsPerLevel` | `12` | Additional bloom attempts granted each adaptation level. |
| `sporeCostBase` | `1` | Mushrooms consumed by a level one bloom. |
| `sporeCostPerLevel` | `1` | Additional mushrooms consumed per adaptation level above one. |
| `bloomRadiusBase` | `5` | Base Bloom radius. Blocks. |
| `bloomRadiusFactor` | `10` | Bloom radius factor. Blocks. |
| `spokesBase` | `6` | Base Spokes. |
| `spokesFactor` | `7` | Spokes factor. Unitless multiplier. |
| `blocksPerPulseBase` | `2` | Base Blocks per pulse. Blocks. |
| `blocksPerPulseFactor` | `4` | Blocks per pulse factor. Blocks. |
| `spreadIntervalTicksBase` | `3` | Base Spread interval ticks. Server ticks (20 ticks = 1 second). |
| `spreadIntervalTicksFactor` | `1.6` | Spread interval ticks factor. Server ticks (20 ticks = 1 second). |
| `foodCostBase` | `2` | Base Food cost. food or saturation points. |
| `foodCostFactor` | `1.2` | Food cost factor. Unitless multiplier. |
| `cooldownMillisBase` | `1700` | Base Cooldown millis. Milliseconds. |
| `cooldownMillisFactor` | `1100` | Cooldown millis factor. Milliseconds. |
| `xpPerMushroomPlaced` | `1.4` | XP awarded for xp per mushroom placed. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
