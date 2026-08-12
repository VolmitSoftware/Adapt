# Skill: Herbalism

Herbalism is the farming skill. You level it by harvesting and planting crops, shearing, feeding a composter, and eating. Fifteen adaptations sit on top of that, and between them they cover the whole loop: planting a field, growing it, harvesting it, replanting it, and turning the leftovers back into fertilizer.

The early adaptations are small conveniences. Crops you harvest go straight into your bag, grass and flowers start dropping useful things, and eating fills you up more than it should. Then it gets ambitious. Harvest and Replant turns one right-click into a harvested and re-seeded patch. Seed Sower plants a whole plot from one sneak-click. Growth Aura and Bee Shepherd push crops forward just by standing near them, paying in hunger instead of bone meal.

Compost Cascade is the big one. Sneak-click a composter and it sweeps everything nearby: loose drops on the ground, mature crops in the field, the compostable junk in your inventory. It fills the composter, spits out bone meal, spends the compost pushing your immature crops one stage forward, and occasionally coughs up something valuable.

Two adaptations point sideways. Hungry Shield pays your incoming damage out of your food bar instead of your health. Rooted Footing stops you trampling farmland and turns part of a fall into hunger. Four more just unlock recipes: mycelium, grass blocks, mushroom blocks, and cobwebs.

## Adaptations

Everything below needs the same four things: the adaptation learned at level 1 or higher in the Adapt menu, the Herbalism skill and that adaptation both enabled in config, an `adapt.use.` permission that has not been revoked for you, and any protection or region plugin allowing the block or entity you are acting on. Those are not repeated per entry.

Five of them ship with `permanent = true`, which means they act as already learned and skip the learn and unlearn flow: Herbalist's Myconid, Herbalist's Terralid, Mushroom Maker, Webby Creator, and Rooted Footing.

### Growth Aura (`herbalism-growth-aura`)

Crops near you grow on their own, paid for out of your hunger. Stand in a field and it ticks forward around you without any input. It works on its own once learned.

Each pulse throws random samples around you. Samples that land on a crop below full growth get queued, then a second or two later the crop jumps forward by a few age steps. Each step costs a fraction of a food point, so a big field drains you fast. Higher levels widen the radius, push more age steps per hit, and cost less food per step.

By default it only touches crops sitting on the surface, so it will not run a hidden underground farm for you.

### Harvest & Replant (`herbalism-replant`)

Harvest a crop and put a seed back in the same motion, without breaking anything by hand. At higher levels it does the neighbors too.

How to use it:

1. Hold a hoe. Off hand is checked first, then main hand. The hoe cannot be on cooldown.
2. Right-click a fully grown crop.
3. The crop drops its loot, one seed is taken out of that loot, and the crop resets to age 0. If there is no seed in the drops, the crop is removed instead.
4. At level 2 and above, a cube of crops around the clicked one is harvested the same way over the next few ticks.

The hoe takes durability per use, more at higher levels, and goes on a short item cooldown. If you also have Hoe Drop-To-Inventory learned, the harvested loot goes straight into your inventory.

### Hungry Shield (`herbalism-hungry-shield`)

Damage is paid out of your food bar before it reaches your health. It works on its own once learned.

Which damage types it covers depends on level. Level 1 covers the mundane ones: contact, cramming, drowning, suffocation, wall impacts, magma blocks, and freezing. Level 2 adds melee, sweep, and thorns. Level 3 adds fire, lava, and campfires. Level 4 adds projectiles, explosions, falling blocks, and lightning. Level 5 adds magic, poison, wither, dragon breath, and sonic boom.

It never eats your last six food points, so it will not starve you outright. When there is nothing left to spend, the shield makes a dull break sound and the damage lands normally. Damage-over-time sources only charge you once per second rather than per tick.

### Herbalist's Hippo (`herbalism-hippo`)

Eating anything on the food list gives you extra food and matching saturation on top of what the item normally restores. It works on its own once learned.

Golden apples, enchanted golden apples, and golden carrots get a bigger visual, but the bonus itself is the same for every food.

### Hoe Drop-To-Inventory (`herbalism-drop-to-inventory`)

Blocks you break with a hoe send their drops straight into your inventory. It works on its own once learned, and it is a single-level adaptation. Survival mode only.

Each drop is run through a normal pickup attempt first, so protection plugins that block pickups still win. Items that do not fit are dropped at your feet with a failure sound.

### Herbalist's Luck (`herbalism-luck`)

Breaking grass can drop a random seed. Breaking a flower can drop random food. It works on its own once learned, and it pays well, 100 skill XP per lucky drop.

The chance is the square of your adaptation level as a percentage, so it climbs steeply: level 3 is about 9 percent, level 7 is about 49 percent.

### Herbalist's Myconid (`herbalism-myconid`)

Unlocks a shapeless recipe: dirt plus one red mushroom plus one brown mushroom makes one mycelium. Active by default without learning it.

### Herbalist's Terralid (`herbalism-terralid`)

Unlocks a shaped recipe: three wheat seeds in a row over three dirt in a row makes three grass blocks. Active by default without learning it.

### Mushroom Maker (`herbalism-mushroom-blocks`)

Unlocks four recipes. Four red mushrooms in a 2x2 make a red mushroom block, four brown mushrooms in a 2x2 make a brown mushroom block, and either mushroom block alone converts to a mushroom stem. Active by default without learning it.

### Webby Creator (`herbalism-cobweb`)

Unlocks a shaped recipe: nine string fills the crafting grid and makes one cobweb. Active by default without learning it.

### Seed Sower (`herbalism-seed-sower`)

Plants a whole patch of farmland in one gesture instead of clicking every tile.

How to use it:

1. Hold a stack of seeds. Wheat seeds, carrots, potatoes, beetroot seeds, melon seeds, pumpkin seeds, torchflower seeds, and nether wart all work.
2. Sneak and right-click. Clicking a block sets the plane you plant on; clicking air uses the block you are looking at within 5 blocks, except on Folia where you must click a block.
3. Every empty tile above farmland within the radius gets planted, up to your per-use crop cap and the number of seeds you are holding.

Nether wart plants on soul sand instead of farmland. Seeds come out of the held stack, one per crop. If planting fails partway, the crops are rolled back and the seeds are refunded. The seed type goes on a short item cooldown afterward.

### Compost Cascade (`herbalism-compost-cascade`)

One sneak-click that runs your whole farm cleanup. It sweeps three sources in order and turns the results into compost, bone meal, and crop growth.

How to use it:

1. Sneak and right-click a composter. Clicking air targets a composter within 5 blocks, except on Folia where you must click the block directly.
2. Loose items on the ground nearby get pulled in and composted.
3. Mature crops in range get harvested and replanted, and leaves get stripped too if `consumeLeaves` is turned on.
4. Compostable items in your inventory get fed in.
5. The compost you just built is spent maturing nearby crops that are not ready yet.
6. Bone meal drops at the composter. If the composter hit full, it also rolls for something valuable.

The item budget is split three ways: 40 percent to the field scan, 20 percent to your inventory, and the rest to loose drops. Rewards it drops are tagged so a second cascade does not eat its own output. Radius, item budget, fill chance, and cooldown all scale with level.

### Rooted Footing (`herbalism-rooted-footing`)

Two safety nets. You stop trampling farmland when you walk or jump on it, and part of your fall damage is paid out of your food bar as long as you land on natural ground. Active by default without learning it.

Natural ground means farmland, grass block, moss block, mycelium, dirt, or rooted dirt directly under you. The conversion is capped both by the absorb percentage and by how much food you actually have. If the absorbed amount covers the whole fall, the damage is cancelled outright.

### Bee Shepherd (`herbalism-bee-shepherd`)

Hold a flower and nearby crops start growing, while nearby bees drift toward you and stay near your field.

How to use it:

1. Hold any flower in your main hand or your off hand. Tulips, dandelion, poppy, blue orchid, allium, azure bluet, oxeye daisy, cornflower, lily of the valley, wither rose, sunflower, lilac, rose bush, peony, torchflower, and pink petals all count.
2. Stand near crops. Pulses fire on their own while you keep holding the flower and have enough food.
3. Each pulse spends food, makes a batch of growth attempts inside its radius, and tugs up to eight nearby bees toward you.

Bees you have herded add extra growth attempts, up to a configured cap, so keeping a swarm around pays off. Bees pulled this way have their attack target cleared.

### Spore Bloom (`herbalism-spore-bloom`)

Turns a patch of ground into a mushroom field. The bloom spreads outward from where you clicked in rings, converting dirt-family soil into the surface you started from and swapping flowers into mushrooms as it goes.

How to use it:

1. Hold red or brown mushrooms.
2. Sneak and place one on top of mycelium or podzol. The placement itself is cancelled; the bloom happens instead.
3. Rings of ground convert outward over the next few seconds, a few blocks per pulse.

Mycelium seeds a mycelium bloom, podzol seeds a podzol bloom. Warm-colored flowers usually become red mushrooms, cool-colored ones usually become brown, and anything else follows whichever mushroom you were holding. Mushrooms and hunger are only charged once the first block actually converts, so a fully blocked bloom costs you nothing. Turn off `swapFlowersToMushrooms` to convert soil only and leave flowers alone.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `herbalism` |
| Class | `SkillHerbalism` |
| Icon | `WHEAT` |
| Color | `GREEN` |
| Interval (ms) | `3990` |
| Skill config | `plugins/Adapt/adapt/skills/herbalism.toml` |
| Adaptation count | 15 |

### Skill XP and stats

All handlers run at MONITOR priority.

| Event | XP awarded | Cooldown-gated | Stat |
|-------|-----------|----------------|------|
| `PlayerItemConsumeEvent` (potions ignored) | `foodConsumeXP` | Yes | `food.eaten` +1 |
| `PlayerShearEntityEvent` | `shearXP` | No | `herbalism.sheared` +1 |
| `PlayerHarvestBlockEvent` | `harvestPerAgeXP * age * provenance * novelty` | Yes | `harvest.blocks` +1 |
| `BlockBreakEvent` | Same as above | Yes | `harvest.blocks` +1 |
| `BlockPlaceEvent` | Same as above | Yes | `harvest.planted` +1 |
| `PlayerInteractEvent` on a composter | See below | No | `harvest.composted` +1 |

The block handlers only fire when the block data is `Ageable`, so non-crop blocks award nothing. A freshly placed seed is age 0, which means planting records the `harvest.planted` stat but awards 0 XP from this path; `plantCropSeedsXP` is what actually pays for replanting, and only Harvest & Replant uses it. `provenance` is `XpProvenance.harvestXpMultiplier` and `novelty` is `XpNovelty.fieldCycleMultiplier`, both anti-farm multipliers.

Composter XP is checked one tick after the interaction. If the composter level rose, or dropped from above zero to zero, the award is `composterBaseXP + (newLevel * composterLevelXPMultiplier) + (newLevel == 0 ? composterEmptyBonus : composterNonZeroLevelBonus)`.

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/herbalism.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Turns the whole skill and its adaptations off when false. |
| `harvestXpCooldown` | `3500` | Minimum milliseconds between cooldown-gated XP awards. |
| `foodConsumeXP` | `35` | Skill XP for eating one non-potion item. |
| `shearXP` | `35` | Skill XP for one shear. |
| `harvestPerAgeXP` | `5.0` | Skill XP per age step of the harvested or placed crop. |
| `plantCropSeedsXP` | `4.0` | Skill XP that Harvest & Replant pays for each crop it replants. |
| `composterBaseXP` | `2.5` | Flat skill XP for a composter interaction that changed its level. |
| `composterLevelXPMultiplier` | `1.25` | Extra skill XP per compost level the composter now holds. |
| `composterNonZeroLevelBonus` | `25` | Bonus skill XP when the composter ends above level zero. |
| `composterEmptyBonus` | `5` | Bonus skill XP when the composter empties after producing bone meal. |
| `challengeEat100Reward` | `1250` | Knowledge reward for `challenge_eat_100`. |
| `challengeEat1kReward` | `6250` | Knowledge reward for `challenge_eat_1000`; the 10000 tier pays this times 5. |
| `challengeHarvest100Reward` | `1250` | Knowledge reward for `challenge_harvest_100`. |
| `challengeHarvest1kReward` | `6250` | Knowledge reward for `challenge_harvest_1000`. |
| `challengePlant100Reward` | `1250` | Knowledge reward for `challenge_plant_100`. |
| `challengePlant1kReward` | `6250` | Knowledge reward for `challenge_plant_1k`. |
| `challengePlant5kReward` | `25000` | Knowledge reward for `challenge_plant_5k`. |
| `challengeCompost50Reward` | `1250` | Knowledge reward for `challenge_compost_50`. |
| `challengeCompost500Reward` | `6250` | Knowledge reward for `challenge_compost_500`. |
| `challengeShear50Reward` | `1250` | Knowledge reward for `challenge_shear_50`. |
| `challengeShear250Reward` | `6250` | Knowledge reward for `challenge_shear_250`. |
| `skillColor` | `"&a"` | Legacy ampersand color code used for this skill in menus and text. |

### Skill milestones

| Milestone key | Stat key | Threshold | Reward |
|---------------|----------|-----------|--------|
| `challenge_eat_100` | `food.eaten` | 100 | `challengeEat100Reward` |
| `challenge_eat_1000` | `food.eaten` | 1000 | `challengeEat1kReward` |
| `challenge_eat_10000` | `food.eaten` | 10000 | `challengeEat1kReward` * 5 |
| `challenge_harvest_100` | `harvest.blocks` | 100 | `challengeHarvest100Reward` |
| `challenge_harvest_1000` | `harvest.blocks` | 1000 | `challengeHarvest1kReward` |
| `challenge_plant_100` | `harvest.planted` | 100 | `challengePlant100Reward` |
| `challenge_plant_1k` | `harvest.planted` | 1000 | `challengePlant1kReward` |
| `challenge_plant_5k` | `harvest.planted` | 5000 | `challengePlant5kReward` |
| `challenge_compost_50` | `harvest.composted` | 50 | `challengeCompost50Reward` |
| `challenge_compost_500` | `harvest.composted` | 500 | `challengeCompost500Reward` |
| `challenge_shear_50` | `herbalism.sheared` | 50 | `challengeShear50Reward` |
| `challenge_shear_250` | `herbalism.sheared` | 250 | `challengeShear250Reward` |

### Shared adaptation keys

Every adaptation TOML at `plugins/Adapt/adapt/adaptations/<id>.toml` carries these in addition to the knobs listed below.

| Key | Behavior |
|-----|----------|
| `enabled` | Turns this adaptation off when false. |
| `permanent` | Treats the adaptation as always learned, bypassing learn and unlearn. |
| `showParticles` | Plays this adaptation's particle effects. |
| `showSounds` | Plays this adaptation's sound effects. |
| `baseCost`, `costFactor`, `maxLevel`, `initialCost` | Knowledge cost curve and level cap. Defaults per adaptation below. |

In the formulas below, `levelPercent` is the learned level divided by `maxLevel`, clamped to 0 through 1.

### Growth Aura

| Property | Value |
|----------|-------|
| Class | `HerbalismGrowthAura` |
| Icon | `BONE_MEAL` |
| Max level | 7 |
| Initial knowledge cost | 12 |
| Base knowledge cost | 8 |
| Cost factor | 0.325 |
| Tick interval (ms) | 10 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-growth-aura.toml` |
| Listened events | `PlayerQuitEvent`; the work itself runs on the adaptation tick |
| Menu stat lines | Block Radius; Growth Aura Strength; Food Cost |
| Stat key | `herbalism.growth-aura.blocks-grown` |
| Milestones | `challenge_herbalism_growth_1k` (1000, reward 300), `challenge_herbalism_growth_25k` (25000, reward 1000) |

Ticking is learner-bound. A player is re-evaluated every 850 ms while pulsing and every 250 ms while idle. Radius is `levelPercent * radiusFactor`; samples per pulse are `ceil(clamp(radius * radius, 3, 256))`. Strength is `level * strengthFactor` age steps per hit, capped by the crop's remaining age. Food per step is interpolated from `maxFoodCost` at no progress down to `minFoodCost` at full level. Mutations are applied 1500 to 3000 ms after their sample. Per-tick work caps are 32 player checks, 32 samples, 16 mutations, and 16 completions.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `surfaceOnly` | `true` | Only grows crops sitting directly on top of the highest block in their column. |
| `minFoodCost` | `0.05` | Food points per age step at full level. |
| `maxFoodCost` | `0.4` | Food points per age step at no level progress. |
| `radiusFactor` | `18` | Aura radius in blocks at full level. |
| `strengthFactor` | `0.75` | Age steps granted per adaptation level on a successful hit. |

### Harvest & Replant

| Property | Value |
|----------|-------|
| Class | `HerbalismReplant` |
| Icon | `PUMPKIN_SEEDS` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 6 |
| Cost factor | 0.95 |
| Tick interval (ms) | 6090 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-replant.toml` |
| Listened events | `PlayerInteractEvent` (MONITOR, cancelled events ignored) |
| Menu stat line | Blocks Replant Radius |
| Stat keys | `herbalism.replant.crops-replanted`, plus the skill's `harvest.blocks` and `harvest.planted` |
| Milestones | `challenge_herbalism_replant_500` (500, reward 300), `challenge_herbalism_replant_25k` (25000, reward 1000) |

Radius is `level - radiusSub`, so level 1 harvests only the clicked crop. Above level 1 the sweep is a cuboid expanded by `floor(radius)` vertically and `round(radius)` horizontally, each neighbor scheduled 1 to 6 ticks later. Tool damage is `1 + ((level - 1) * 7)`. Item cooldown is `cooldownLvl1` ticks at level 1, otherwise `(baseCooldown - cooldownFactor * levelPercent) + bonusCooldown` ticks. XP per crop is the skill's `harvestPerAgeXP * age`, plus `plantCropSeedsXP` when a seed was reclaimed. Air clicks are ignored on Folia.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldownLvl1` | `2` | Hoe cooldown in ticks while the adaptation is at level 1. |
| `baseCooldown` | `30` | Starting hoe cooldown in ticks above level 1. |
| `cooldownFactor` | `30` | Ticks removed from that cooldown at full level. |
| `bonusCooldown` | `20` | Flat ticks added to the scaled cooldown. |
| `radiusSub` | `1` | Levels subtracted before the level becomes a block radius. |

### Hungry Shield

| Property | Value |
|----------|-------|
| Class | `HerbalismHungryShield` |
| Icon | `APPLE` |
| Max level | 5 |
| Initial knowledge cost | 10 |
| Base knowledge cost | 7 |
| Cost factor | 0.78 |
| Tick interval (ms) | 875 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-hungry-shield.toml` |
| Listened events | `EntityDamageEvent` (HIGHEST) |
| Menu stat lines | Resisted by Hunger; Contact, Crushing & Weather Damage Shielded; Melee & Thorns Damage Shielded; Fire & Lava Damage Shielded; Projectile & Explosion Damage Shielded; Magic, Poison & Wither Damage Shielded |
| Stat key | `herbalism.hungry-shield.damage-absorbed` |
| Milestones | `challenge_herbalism_shield_500` (500, reward 400), `challenge_herbalism_shield_5k` (5000, reward 1500) |

Effectiveness is `min(maxEffectiveness, levelPercent^2 + effectivenessBase)`. Absorbed damage is `min(damage * effectiveness, max(0, foodLevel + saturation - 6))`, so it never spends your last 6 food points. Skill XP equals the absorbed damage. Damage-over-time causes are fire, fire tick, lava, campfire, hot floor, poison, wither, drowning, and freeze; those charge at most once per `dotChargeIntervalMs`. The shield-break effect is limited to once per 1500 ms and only for hits above 2 damage.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `effectivenessBase` | `0.15` | Fraction of damage moved to hunger at no level progress, 0-1. |
| `maxEffectiveness` | `0.95` | Hard ceiling on that fraction, 0-1. |
| `basicsUnlockLevel` | `1` | Level needed for contact, cramming, drowning, suffocation, wall, magma, and freeze. |
| `meleeUnlockLevel` | `2` | Level needed for melee, sweep, and thorns. |
| `fireUnlockLevel` | `3` | Level needed for fire, fire tick, lava, and campfire. |
| `burstUnlockLevel` | `4` | Level needed for projectile, explosion, falling block, and lightning. |
| `magicUnlockLevel` | `5` | Level needed for magic, poison, wither, dragon breath, and sonic boom. |
| `dotChargeIntervalMs` | `1000` | Milliseconds between hunger charges for damage-over-time sources. |

### Herbalist's Hippo

| Property | Value |
|----------|-------|
| Class | `HerbalismHungryHippo` |
| Icon | `POTATO` |
| Max level | 7 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 8 |
| Cost factor | 0.75 |
| Tick interval (ms) | 8111 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-hippo.toml` |
| Listened events | `PlayerItemConsumeEvent` (NORMAL) |
| Menu stat line | Food) additional saturation points on consumption |
| Stat key | `herbalism.hungry-hippo.bonus-saturation` |
| Milestones | `challenge_herbalism_hippo_500` (500, reward 400) |

Bonus is `2 + level`, applied to food (capped at 20) and to saturation (capped at the new food value). Only materials in `ItemListings.getFood()` qualify. Flat 5 skill XP per meal. No adaptation-specific config knobs.

### Hoe Drop-To-Inventory

| Property | Value |
|----------|-------|
| Class | `HerbalismDropToInventory` |
| Icon | `HOPPER` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 1 |
| Cost factor | 1 |
| Tick interval (ms) | 7999 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-drop-to-inventory.toml` |
| Listened events | `BlockDropItemEvent` (HIGHEST) |
| Menu stat line | Whenever an item is dropped from a block you break it goes into your inventory if it can. |
| Stat key | `herbalism.drop-to-inv.items-caught` |
| Milestones | `challenge_herbalism_dti_10k` (10000, reward 500) |

Requires survival mode and a hoe from `ItemListings.toolHoes` in the main hand. Awards a flat 2 skill XP per item caught. The display name comes from `herbalism.drop_to_inventory.name` and the description and lore from the Pickaxe equivalents. No adaptation-specific config knobs.

### Herbalist's Luck

| Property | Value |
|----------|-------|
| Class | `HerbalismLuck` |
| Icon | `EMERALD` |
| Max level | 7 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 8 |
| Cost factor | 0.75 |
| Tick interval (ms) | 8121 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-luck.toml` |
| Listened events | `BlockDropItemEvent` (NORMAL) |
| Menu stat lines | Flowers = Food, and Grass = Seeds; Chance to get an item from breaking Flowers; Chance to get an item from breaking Grass |
| Stat key | `herbalism.luck.lucky-drops` |
| Milestones | `challenge_herbalism_luck_100` (100, reward 300), `challenge_herbalism_luck_2500` (2500, reward 1000) |

Chance is `min(highChance, level * level + lowChance)` rolled against a 0 to 100 range, so it uses the raw adaptation level rather than level percent. Grass and tall grass roll from `ItemListings.getHerbalLuckSeeds()`; flowers from `ItemListings.getFlowers()` roll from `ItemListings.getHerbalLuckFood()`. Each lucky drop awards 100 skill XP.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `lowChance` | `0.0` | Flat percentage added to the level-squared chance, 0-100. |
| `highChance` | `90` | Hard ceiling on the drop chance, 0-100. |

### Herbalist's Myconid

| Property | Value |
|----------|-------|
| Class | `HerbalismMyconid` |
| Icon | `MYCELIUM` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.75 |
| Permanent by default | Yes |
| Tick interval (ms) | 17771 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-myconid.toml` |
| Listened events | `CraftItemEvent` (MONITOR) |
| Menu stat line | Any Dirt, and a Brown & Red Mushroom will craft Mycelium. |
| Stat key | `herbalism.myconid.mycelium-crafted` |
| Milestones | `challenge_herbalism_myconid_100` (100, reward 300) |

Recipe `adapt:herbalism-dirt-myconid`, shapeless, `DIRT` + `RED_MUSHROOM` + `BROWN_MUSHROOM` to one `MYCELIUM`. No adaptation-specific config knobs.

### Herbalist's Terralid

| Property | Value |
|----------|-------|
| Class | `HerbalismTerralid` |
| Icon | `GRASS_BLOCK` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.75 |
| Permanent by default | Yes |
| Tick interval (ms) | 17771 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-terralid.toml` |
| Listened events | `CraftItemEvent` (MONITOR) |
| Menu stat line | Three Seeds, over 3 Dirt, will craft 3 Grass Blocks. |
| Stat key | `herbalism.terralid.grass-crafted` |
| Milestones | `challenge_herbalism_terralid_200` (200, reward 300) |

Recipe `adapt:herbalism-dirt-terralid`, shaped `SSS` over `DDD` where `S` is `WHEAT_SEEDS` and `D` is `DIRT`, producing three `GRASS_BLOCK`. No adaptation-specific config knobs.

### Mushroom Maker

| Property | Value |
|----------|-------|
| Class | `HerbalismCraftableMushroomBlocks` |
| Icon | `BROWN_MUSHROOM_BLOCK` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 1 |
| Permanent by default | Yes |
| Tick interval (ms) | 17772 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-mushroom-blocks.toml` |
| Listened events | `CraftItemEvent` (MONITOR) |
| Menu stat line | Four Mushrooms to make a block, or a block to make a stem. |
| Stat key | `herbalism.mushroom-blocks.crafted` |
| Milestones | `challenge_herbalism_mushroom_100` (100, reward 300) |

Recipes: `adapt:herbalism-redmushblock` and `adapt:herbalism-brownmushblock` are 2x2 mushrooms to one matching mushroom block; `adapt:herbalism-mushstemred` and `adapt:herbalism-mushstembrown` are shapeless conversions of either mushroom block to one `MUSHROOM_STEM`. The stat only counts the two block recipes. No adaptation-specific config knobs.

### Webby Creator

| Property | Value |
|----------|-------|
| Class | `HerbalismCraftableCobweb` |
| Icon | `STRING` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 1 |
| Permanent by default | Yes |
| Tick interval (ms) | 17771 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-cobweb.toml` |
| Listened events | `CraftItemEvent` (MONITOR) |
| Menu stat line | Nine String, will craft a Cobweb. |
| Stat key | `herbalism.cobweb.cobwebs-crafted` |
| Milestones | `challenge_herbalism_cobweb_100` (100, reward 300) |

Recipe `adapt:herbalism-cobwebblock`, shaped 3x3 of `STRING` to one `COBWEB`. No adaptation-specific config knobs.

### Seed Sower

| Property | Value |
|----------|-------|
| Class | `HerbalismSeedSower` |
| Icon | `WHEAT_SEEDS` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.675 |
| Tick interval (ms) | 6920 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-seed-sower.toml` |
| Listened events | `PlayerInteractEvent` (MONITOR, also receives cancelled events) |
| Menu stat lines | Plant Radius; Max Crops Per Use; Sowing Cooldown |
| Stat keys | `herbalism.seed-sower.seeds-planted`, plus the skill's `harvest.planted` |
| Milestones | `challenge_herbalism_seed_1k` (1000, reward 300), `challenge_herbalism_seed_25k` (25000, reward 1000) |

Seed to crop mapping: wheat seeds to wheat, carrot to carrots, potato to potatoes, beetroot seeds to beetroots, melon seeds to melon stem, pumpkin seeds to pumpkin stem, torchflower seeds to torchflower crop, nether wart to nether wart. Valid base is `FARMLAND`, or `SOUL_SAND` for nether wart. Radius is `max(1, round(baseRadius + levelPercent * radiusFactor))`; crop cap is `max(1, round(baseCropCount + levelPercent * cropCountFactor))`; cooldown is `max(2, round(cooldownTicksBase - levelPercent * cooldownTicksReduction))` ticks on the seed item. Creative mode plants without consuming seeds. A partial plant is rolled back and the seed charge refunded.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseRadius` | `1` | Plant radius in blocks at no level progress. |
| `radiusFactor` | `2` | Blocks of radius added at full level. |
| `baseCropCount` | `3` | Crops planted per use at no level progress. |
| `cropCountFactor` | `10` | Crops added to that cap at full level. |
| `cooldownTicksBase` | `60` | Seed cooldown in ticks at no level progress. |
| `cooldownTicksReduction` | `42` | Ticks removed from that cooldown at full level. |
| `xpPerCrop` | `1.45` | Herbalism skill XP per crop planted. |

### Compost Cascade

| Property | Value |
|----------|-------|
| Class | `HerbalismCompostCascade` |
| Icon | `COMPOSTER` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 600 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-compost-cascade.toml` |
| Listened events | `PlayerInteractEvent` (MONITOR, cancelled events ignored) |
| Menu stat lines | Cascade Radius; Max Items Processed; Compost Fill Chance; Cascade Cooldown |
| Stat keys | `herbalism.compost-cascade.items-composted`, plus the skill's `harvest.composted` |
| Milestones | `challenge_herbalism_compost_1k` (1000, reward 300), `challenge_herbalism_compost_25k` (25000, reward 1000) |

Item budget is split 40 percent to the field scan, 20 percent to your inventory, and the remainder to loose drops. The field scan inspects at most 24576 blocks and tracks at most 512 immature crops. Maturation attempts are `min(configuredAttempts, levelGains + overflowFills)`. Bone meal is `baseBoneMeal + (itemsConsumed / itemsPerBoneMeal) + overflowBoneMeal`, plus the ready bonus the first time the composter reaches level 8, capped at a stack. Valuable rolls only happen at a full composter and pick from honeycomb (45 percent), glow berries (25 percent), amethyst shards (18 percent), emerald (9 percent), and diamond (3 percent). Dropped rewards are tagged in persistent data so a later cascade will not re-consume them. Total XP is `(itemsConsumed * xpPerItemConsumed) + (levelGains * xpPerLevelGain) + (cropsMatured * xpPerCropMatured)`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `consumeLeaves` | `false` | Lets the cascade break and compost leaf blocks in range. |
| `radiusBase` | `5.0` | Cascade radius in blocks at no level progress. |
| `radiusFactor` | `12.0` | Blocks of radius added at full level. |
| `maxItemsBase` | `80.0` | Items processed per cascade at no level progress. |
| `maxItemsFactor` | `240.0` | Items added to that budget at full level. |
| `fillChanceBase` | `0.5` | Chance one composted item adds a compost level, 0-1. |
| `fillChanceFactor` | `0.42` | Extra fill chance at full level, 0-1. |
| `maxFillChance` | `0.98` | Hard ceiling on fill chance, 0-1. |
| `leafCompostBurstsBase` | `3` | Leaf items credited per broken leaf block at no level progress. |
| `leafCompostBurstsFactor` | `9` | Extra leaf items credited at full level. |
| `leafFillChanceMultiplierBase` | `1.35` | Multiplier on fill chance for leaves at no level progress. |
| `leafFillChanceMultiplierFactor` | `0.7` | Extra leaf multiplier at full level. Result is capped at 1.0. |
| `cooldownTicksBase` | `36.0` | Composter cooldown in ticks at no level progress. |
| `cooldownTicksReduction` | `28.0` | Ticks removed from that cooldown at full level. |
| `boneMealBase` | `2.0` | Bone meal dropped per cascade at no level progress. |
| `boneMealFactor` | `6.0` | Extra bone meal at full level. |
| `readyBonusBoneMealBase` | `2.0` | Extra bone meal when the composter first fills, at no level progress. |
| `readyBonusBoneMealFactor` | `8.0` | Extra fill bonus at full level. |
| `itemsPerBoneMealBase` | `20.0` | Items consumed per extra bone meal at no level progress. |
| `itemsPerBoneMealReduction` | `14.0` | Items removed from that ratio at full level. |
| `overflowFillsPerBoneMeal` | `4` | Fills wasted on an already-full composter that convert to one bone meal. |
| `maturationAttemptsBase` | `6` | Crop maturation attempts at level one. |
| `maturationAttemptsPerLevel` | `6` | Extra maturation attempts per level above one. |
| `valuableChanceBase` | `0.01` | Chance per roll of a valuable drop at no level progress, 0-1. |
| `valuableChanceFactor` | `0.09` | Extra valuable chance at full level, 0-1. |
| `maxValuableChance` | `0.12` | Hard ceiling on valuable chance, 0-1. |
| `valuableRollsBase` | `1` | Valuable rolls per full composter at no level progress. |
| `valuableRollsFactor` | `3` | Extra rolls at full level. |
| `xpPerItemConsumed` | `1.2` | Herbalism skill XP per item composted. |
| `xpPerLevelGain` | `2.8` | Herbalism skill XP per compost level gained. |
| `xpPerCropMatured` | `2.0` | Herbalism skill XP per crop pushed forward a stage. |

### Rooted Footing

| Property | Value |
|----------|-------|
| Class | `HerbalismRootedFooting` |
| Icon | `FARMLAND` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.65 |
| Permanent by default | Yes |
| Tick interval (ms) | 2050 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-rooted-footing.toml` |
| Listened events | `PlayerInteractEvent` (HIGHEST, `PHYSICAL` action only), `EntityDamageEvent` (HIGH) |
| Menu stat lines | Fall Damage Converted; Food Per Damage; Prevents Farmland Trample |
| Stat key | `herbalism.rooted-footing.farmland-saved` |
| Milestones | `challenge_herbalism_rooted_500` (500, reward 300) |

Trample protection cancels the physical interact on `FARMLAND`; its effect is throttled to once per 500 ms. Fall absorption applies only when the block directly under you is farmland, grass block, moss block, mycelium, dirt, or rooted dirt. Absorb cap is `damage * min(maxAbsorbPercent, absorbBase + levelPercent * absorbFactor)`, and the amount actually absorbed is `min(absorbCap, usableFood / foodPerDamage)`. Remaining damage at or under 0.01 cancels the event.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `absorbBase` | `0.28` | Fraction of fall damage eligible for conversion at no level progress, 0-1. |
| `absorbFactor` | `0.12` | Extra fraction at full level, 0-1. |
| `maxAbsorbPercent` | `0.45` | Hard ceiling on that fraction, 0-1. |
| `foodPerDamage` | `1.8` | Food points spent per point of damage absorbed. |

### Bee Shepherd

| Property | Value |
|----------|-------|
| Class | `HerbalismBeeShepherd` |
| Icon | `BEE_NEST` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.64 |
| Tick interval (ms) | 10 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-bee-shepherd.toml` |
| Listened events | `PlayerQuitEvent`; the work itself runs on the adaptation tick |
| Menu stat lines | Pulse Radius; Growth Attempts; Pulse Cooldown; Bee Growth Bonus |
| Stat key | `herbalism.bee-shepherd.bees-attracted` |
| Milestones | `challenge_herbalism_bee_100` (100, reward 300) |

Ticking is learner-bound. Requires a flower in the main or off hand. Radius is `radiusBase + levelPercent * radiusFactor`. Growth attempts are `round(growthAttemptsBase + levelPercent * growthAttemptsFactor)`, then multiplied by `1 + min(bees, maxBonusBees) * growthBonusPerBee`. Growth step is `round(growthStepBase + levelPercent * growthStepFactor)` age stages. Food cost is `max(1, round(foodCostBase - levelPercent * foodCostFactor))`, charged once per pulse at the first committed growth. Pulse spacing is `max(250, round(pulseMillisBase - levelPercent * pulseMillisFactor))` milliseconds. At most 8 bees are pulled per pulse; the attracted-bee stat only counts each bee once per 60 seconds. Per-tick work caps are 32 player checks, 96 growth samples, 8 bee pulls, and 16 completions.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `showGrowthParticles` | `true` | Emits the per-crop growth particles. |
| `radiusBase` | `7` | Pulse radius in blocks at no level progress. |
| `radiusFactor` | `12` | Blocks of radius added at full level. |
| `growthAttemptsBase` | `10` | Growth attempts per pulse at no level progress. |
| `growthAttemptsFactor` | `18` | Extra attempts at full level. |
| `growthBonusPerBee` | `0.15` | Fraction of base attempts added per herded bee. |
| `maxBonusBees` | `5` | Herded bees that count toward the bonus. |
| `growthStepBase` | `1` | Age stages per successful growth at no level progress. |
| `growthStepFactor` | `2.0` | Extra age stages at full level. |
| `foodCostBase` | `1` | Food points per pulse at no level progress. |
| `foodCostFactor` | `1.2` | Food points removed from that cost at full level. Minimum is 1. |
| `pulseMillisBase` | `900` | Milliseconds between pulses at no level progress. |
| `pulseMillisFactor` | `650` | Milliseconds removed from that spacing at full level. |
| `beePullStrengthBase` | `0.07` | Velocity applied to herded bees at no level progress. |
| `beePullStrengthFactor` | `0.14` | Extra pull velocity at full level. |
| `xpPerGrowth` | `0.9` | Herbalism skill XP per crop grown. |

### Spore Bloom

| Property | Value |
|----------|-------|
| Class | `HerbalismSporeBloom` |
| Icon | `RED_MUSHROOM_BLOCK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 2100 |
| Config file | `plugins/Adapt/adapt/adaptations/herbalism-spore-bloom.toml` |
| Listened events | `BlockPlaceEvent` (MONITOR, cancelled events ignored) |
| Menu stat lines | Bloom Attempts; Bloom Radius; Bloom Cooldown; Mushroom Cost |
| Stat key | `herbalism.spore-bloom.blocks-spread` |
| Milestones | `challenge_herbalism_spore_500` (500, reward 300) |

Triggered by sneak-placing `RED_MUSHROOM` or `BROWN_MUSHROOM` on `MYCELIUM` or `PODZOL`; the placement is cancelled and the bloom runs instead. Convertible soil is dirt, grass block, coarse dirt, rooted dirt, mycelium, and podzol. Bloom attempts are `round(bloomAttemptsBase + levelPercent * bloomAttemptsFactor) + (level - 1) * bloomAttemptsPerLevel`. Radius is `bloomRadiusBase + levelPercent * bloomRadiusFactor`, floored at 6 once level 5 is reached, which also forces the first 6 rings to be filled. Mushroom cost is `sporeCostBase + (level - 1) * sporeCostPerLevel`. Cooldown is `max(250, round(cooldownMillisBase - levelPercent * cooldownMillisFactor))` milliseconds. Costs are charged on the first committed conversion, not on activation.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `swapFlowersToMushrooms` | `true` | Lets the bloom replace flowers with mushrooms. |
| `bloomAttemptsBase` | `26` | Blocks visited per bloom at no level progress. |
| `bloomAttemptsFactor` | `58` | Extra blocks visited at full level. |
| `bloomAttemptsPerLevel` | `12` | Extra blocks visited per level above one. |
| `sporeCostBase` | `1` | Mushrooms consumed by a level one bloom. |
| `sporeCostPerLevel` | `1` | Extra mushrooms consumed per level above one. |
| `bloomRadiusBase` | `5` | Bloom radius in blocks at no level progress. |
| `bloomRadiusFactor` | `10` | Blocks of radius added at full level. |
| `spokesBase` | `6` | Spokes used to build the ring pattern at no level progress. Sectors are three times this, clamped to 8-48. |
| `spokesFactor` | `7` | Extra spokes at full level. |
| `blocksPerPulseBase` | `2` | Blocks converted per pulse at no level progress. |
| `blocksPerPulseFactor` | `4` | Extra blocks per pulse at full level. |
| `spreadIntervalTicksBase` | `3` | Server ticks between pulses at no level progress. |
| `spreadIntervalTicksFactor` | `1.6` | Ticks removed from that interval at full level. |
| `foodCostBase` | `2` | Food points per bloom at no level progress. |
| `foodCostFactor` | `1.2` | Food points removed from that cost at full level. Minimum is 1. |
| `cooldownMillisBase` | `1700` | Milliseconds between blooms at no level progress. |
| `cooldownMillisFactor` | `1100` | Milliseconds removed from that cooldown at full level. |
| `xpPerMushroomPlaced` | `1.4` | Herbalism skill XP per block the bloom converts. |

## See also

- `02 - Concepts.md` for skills, adaptations, and knowledge
- `03 - Player Usage.md` for the Adapt menu and learning flow
- `10 - Skills Catalog.md` for the full skill list
- `04 - Commands & Permissions.md` for the `adapt.use` permission tree
