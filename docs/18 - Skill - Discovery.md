# Skill: Discovery

Discovery is the skill for seeing things for the first time. Every block state, item, food, recipe, enchantment, mob, player, potion effect, biome, dimension, and world you have never encountered before pays Discovery XP once and once only. It is the one skill that rewards wandering off and poking at things instead of grinding one action.

The plugin also watches whatever block you are looking at. Every half second it ray-traces up to five blocks ahead and records the block and its biome, so you pick up discoveries just by walking through new terrain with your eyes open. On top of that, any vanilla experience you collect is mirrored straight into Discovery XP.

The payouts are lopsided on purpose. A new block is worth a few points, a new mob or player is worth a lot more, and stepping into a dimension or world you have never visited is worth hundreds. Rare finds get their own particle flourish so you notice them.

The 14 adaptations mostly turn curiosity into utility: a HUD that reads out what you are looking at, direction hints toward unexplored structures, a compass that points at the nearest structure, glowing chests through walls, and better archaeology brushing. A few convert experience into something else, either armor, damage resistance, cheaper villager trades, or faster mending.

## Adaptations

Everything below only runs when you have learned the adaptation to level 1 or higher, the skill and the adaptation are both enabled in config, you hold the `adapt.use` permission, and any protection plugin or region policy allows the action. Those conditions are not repeated per entry.

### Experimental Unity (`discovery-unity`)

Every experience orb you pick up gets spread around. You gain a little Discovery XP and one of your existing skill lines, picked at random, gets a fresh XP grant on top. It is the passive that keeps the skills you are not actively using from falling behind.

Works on its own once learned. Goes to seven levels.

### World Armor (`discovery-world-armor`)

Standing on and near hard blocks makes you tougher. The bonus armor is derived from the hardness of the blocks around you, so it pays out in stone and deepslate and gives you nothing in a field.

Works on its own once learned.

### Experimental Resistance (`discovery-xp-resist`)

An emergency brake tied to your experience bar. It only fires when a hit would drop you below five hearts or kill you outright, and when it does it eats vanilla levels and cuts the damage. If you do not have the levels, it fails with a red puff and the hit lands in full. Higher adaptation levels both cut more damage and cost fewer levels.

Works on its own once learned.

### Villager Attraction (`discovery-villager-att`)

Right-clicking a villager has a chance to rewrite the trades in your favour, paid for with vanilla levels. When you cannot afford it the villager shakes its head at you. The chance improves as the adaptation levels.

**How to use it**

1. Learn it in the Adapt menu.
2. Keep some vanilla levels banked.
3. Right-click a villager. If it procs, the offers you open are the improved ones.

### Better Mending (`discovery-better-mending`)

Mending normally waits for you to pick up orbs. This spends your banked experience directly into the damaged Mending item in your hand, on demand. There is a cap on how much you can dump per click and a short item cooldown afterwards.

**How to use it**

1. Learn it in the Adapt menu.
2. Hold a damaged item with Mending in your main hand.
3. Sneak and left-click, air or block.

Nothing happens if the item is undamaged, if you have no experience, or if the item is still on cooldown.

### Archaeologist (`discovery-archaeologist`)

Brushing suspicious sand and gravel can pay out twice. On top of the vanilla find there is a chance for a bonus reward, and a smaller chance for a rare one. Both chances climb with level, and there is a cooldown between rewards that shrinks as you level.

**How to use it**

1. Learn it in the Adapt menu.
2. Brush a suspicious block the normal way.

### Cartographer Pulse (`discovery-cartographer-pulse`)

Points your compass at the nearest structure and draws a private glowing line toward it, so you can follow the direction instead of guessing. Each pulse costs food and puts you on a long cooldown, and the search range is enormous, hundreds of blocks even at level 1.

**How to use it**

1. Learn it in the Adapt menu.
2. Hold a compass in your main hand.
3. Sneak and right-click.

Not enough food, still on cooldown, or nothing found in range and you get a smoke puff instead.

### Insight (`discovery-insight`)

Study creatures at a glance. The entity you are looking at shows its name and a health bar over its head, tameable creatures also show their live speed, jump, and attack values, and your own hits sprout floating damage numbers with crits in orange. The HUD scales with distance so it stays the same size on your screen.

Works on its own once learned. Look at something.

### Trailblazer (`discovery-trailblazer`)

Rewards the first time you set foot in each biome or structure type with a burst of skill XP and a short speed boost, so exploring actually moves you along. Structure discoveries pay considerably more than biome discoveries.

Works on its own once learned.

### Field Notes (`discovery-field-notes`)

The first kill of each mob species pays a large XP bounty, and every kill after that banks a small permanent damage bonus against that species up to a per-species cap. Over time you become measurably better at killing the things you kill often.

Works on its own once learned.

### Polymath (`discovery-polymath`)

Rewards breadth. Every skill line you have pushed past a threshold level contributes a small bonus to all your XP gain, up to a combined ceiling. Someone with ten skills at level 5 gets far more out of this than someone with one skill at level 50.

Works on its own once learned.

### Relic Appraiser (`discovery-relic-appraiser`)

Turns rare junk into XP. Heads, music discs, armor trim templates, and pottery sherds can be appraised for Discovery XP scaled by how rare the category is. An appraised item is stamped so it cannot be appraised twice.

**How to use it**

1. Learn it in the Adapt menu.
2. Hold the head, disc, trim template, or sherd in your main hand.
3. Sneak and right-click, air or block.

Already-appraised items just puff smoke.

### Sixth Sense (`discovery-sixth-sense`)

A quiet nudge. When an unexplored structure comes within range, you get a private glowing direction line pointing at it. Structures you have already been close to stop triggering it, so it keeps pointing at things you have not seen.

Works on its own once learned.

### Keen Eye (`discovery-keen-eye`)

Chests and spawners inside your line of sight briefly light up as private glowing outlines. Only you see them. It has a forward-view cone rather than full radius, so you have to be roughly facing what you want to spot, and only a handful of containers light up per scan.

Works on its own once learned.

## Reference

Every adaptation config file also carries the shared keys `enabled`, `permanent`, `showParticles`, and `showSounds`.

### Identity

| Property | Value |
|----------|-------|
| Skill id | `discovery` |
| Class | `SkillDiscovery` |
| Icon | `FILLED_MAP` |
| Color | `AQUA` |
| Interval (ms) | `50` |
| Skill config | `plugins/Adapt/adapt/skills/discovery.toml` |
| Adaptation count | 14 |

### XP sources

Each discovery pays once. The record is kept per player per key.

| Trigger | Key recorded | XP | Stat |
|---------|--------------|----|------|
| Looking at a block, or clicking one | block data string | `discoverBlockBaseXP + value * discoverBlockValueXPMultiplier` | `discovery.blocks` |
| Any item seen (pickup, consume, or the material of a seen block) | `Material` | `discoverItemBaseXP + value * discoverItemValueXPMultiplier` | `discovery.items` |
| Enchantment on a seen item | enchantment name plus roman level | `discoverEnchantBaseXP + min(discoverEnchantMaxXP, level * discoverEnchantLevelXPMultiplier)` | none |
| Taking a craft result | recipe key | `discoverRecipeBaseXP` | none |
| Eating or drinking | `Material` | `discoverFoodTypeXP` | `discovery.foods` |
| Right-clicking an entity | `EntityType` | `discoverEntityTypeXP` | `discovery.mobs` |
| Right-clicking a player | that player's UUID | `discoverPlayerXP` | none |
| Active potion effect on an entity you right-click | effect type plus roman amplifier | `discoverPotionXP` | none |
| Looking at a block in a new biome | biome key | `discoverBiomeXP` | `discovery.biomes` |
| Changing world, 15 ticks after arrival | world identity plus seed | `discoverWorldXP` | none |
| The dimension of a newly seen world | `World.Environment` | `discoverEnvironmentXP` | none |
| Collecting vanilla experience | not a discovery | the raw vanilla amount | none |

The look-ahead check runs on the skill tick. Each pass takes up to `maxTargetChecksPerPass` players from a rotating cursor, each player is throttled to one check every 500 ms, and the check is a five-block `getTargetBlockExact` with fluids ignored. Repeating the same block at the same coordinates with the same data and biome is skipped.

Discoveries with a value of 24 or more play the rare-find timeline effect instead of the plain particle.

### Milestones

| Advancement key | Stat key | Threshold | Reward |
|-----------------|----------|-----------|--------|
| `challenge_discover_items_50` | `discovery.items` | 50 | 500 |
| `challenge_discover_items_250` | `discovery.items` | 250 | 2500 |
| `challenge_discover_blocks_50` | `discovery.blocks` | 50 | 500 |
| `challenge_discover_blocks_250` | `discovery.blocks` | 250 | 2500 |
| `challenge_discover_mobs_25` | `discovery.mobs` | 25 | 500 |
| `challenge_discover_mobs_75` | `discovery.mobs` | 75 | 2500 |
| `challenge_discover_biomes_10` | `discovery.biomes` | 10 | 500 |
| `challenge_discover_biomes_40` | `discovery.biomes` | 40 | 2500 |
| `challenge_discover_foods_10` | `discovery.foods` | 10 | 500 |
| `challenge_discover_foods_30` | `discovery.foods` | 30 | 2500 |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/discovery.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Turns the whole skill on or off. |
| `skillColor` | `"&b"` | Legacy ampersand color code for this skill in menus and text. |
| `showParticles` | `true` | Plays the discovery particle and sound flourishes. |
| `discoverBiomeXP` | `15` | XP for a biome you have never been in. |
| `discoverPotionXP` | `36` | XP for a potion effect and amplifier pair you have never seen. |
| `discoverEntityTypeXP` | `125` | XP for an entity type you have never inspected. |
| `discoverFoodTypeXP` | `75` | XP the first time you consume a given food. |
| `discoverPlayerXP` | `125` | XP for a player you have never inspected. |
| `discoverEnvironmentXP` | `750` | XP for a dimension you have never entered. |
| `discoverWorldXP` | `750` | XP for a world you have never entered, keyed by identity and seed. |
| `discoverEnchantMaxXP` | `250` | Ceiling on the level-scaled part of an enchantment discovery. |
| `discoverEnchantLevelXPMultiplier` | `52` | XP per enchantment level, before the ceiling. |
| `discoverEnchantBaseXP` | `5` | Flat XP added to every enchantment discovery. |
| `discoverItemBaseXP` | `10` | Flat XP added to every item discovery. |
| `discoverRecipeBaseXP` | `15` | XP for a recipe you have never crafted. |
| `discoverItemValueXPMultiplier` | `1` | Multiplier applied to an item's value in an item discovery. |
| `discoverBlockBaseXP` | `3` | Flat XP added to every block discovery. |
| `discoverBlockValueXPMultiplier` | `0.333` | Multiplier applied to a block's value in a block discovery. |
| `maxTargetChecksPerPass` | `64` | Players given a look-ahead ray trace per skill tick, taken from a rotating cursor. |

### Experimental Unity

| Property | Default |
|----------|---------|
| Class | `DiscoveryUnity` |
| Icon | `END_CRYSTAL` |
| Max level | 7 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.3 |
| Tick interval (ms) | 666 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-unity.toml` |

Grants a flat 5 Discovery XP per orb pickup, then picks one random skill line and gives it `amount * xpGainedMultiplier * levelPercent` fresh XP, where `amount` is a random 1 to 3.

Milestones: `challenge_discovery_unity_5k` and `challenge_discovery_unity_50k` on `discovery.unity.orbs-distributed` at 5000 and 50000, rewarding 400 and 1500.

Listened events:

- `PlayerExpChangeEvent` (`on`): vanilla experience gained

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `xpGainedMultiplier` | `8` | Scales the XP handed to the randomly chosen skill line. |

### World Armor

| Property | Default |
|----------|---------|
| Class | `DiscoveryArmor` |
| Localization key | `discovery.armor` |
| Icon | `TURTLE_HELMET` |
| Max level | 3 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.3 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-world-armor.toml` |

Milestone: `challenge_discovery_armor_1hr` on `discovery.armor.ticks-with-bonus` at 72000, rewarding 400.

Listened events:

- `PlayerJoinEvent` (`on`)

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxPlayersPerPass` | `16` | Players whose surroundings are sampled per scheduler pass. |

### Experimental Resistance

| Property | Default |
|----------|---------|
| Class | `DiscoveryXpResist` |
| Localization key | `discovery.resist` |
| Icon | `TOTEM_OF_UNDYING` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 5 |
| Cost factor | 0.8 |
| Tick interval (ms) | 5215 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-xp-resist.toml` |

Damage reduction is `min(maxEffectiveness, levelPercent^2 + effectivenessBase)`. The vanilla level cost is `max(1, round(levelCostAdd * amplifier - level * levelDrain))`, so it gets cheaper as the adaptation levels. A successful save grants 5 Discovery XP.

Milestones: `challenge_discovery_xp_resist_25` and `challenge_discovery_xp_resist_250` on `discovery.xp-resist.saves` at 25 and 250, rewarding 500 and 2000. `challenge_discovery_xp_resist_clutch` is registered and localized but has no milestone threshold and no grant call anywhere in the code, so it is currently unobtainable.

Listened events:

- `EntityDamageEvent` (`on`): only when the hit would cross the health threshold

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `effectivenessBase` | `0.15` | Fraction of damage removed at level 0, before the level curve. |
| `maxEffectiveness` | `0.95` | Ceiling on the fraction of damage removed. |
| `levelDrain` | `2` | Vanilla levels shaved off the cost per adaptation level. |
| `levelCostAdd` | `12` | Base vanilla level cost per save before the amplifier and drain. |
| `amplifier` | `1.0` | Multiplier on the base level cost. |
| `triggerHealthThreshold` | `10.0` | Health in points below which a hit is treated as critical (2 points = 1 heart). |

### Villager Attraction

| Property | Default |
|----------|---------|
| Class | `DiscoveryVillagerAtt` |
| Localization key | `discovery.villager` |
| Icon | `GLASS_BOTTLE` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 1 |
| Cost factor | 0.01 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-villager-att.toml` |

Proc chance is `min(clamp(maxEffectiveness, 0, 1), levelPercent^2 + effectivenessBase)`. The vanilla level cost is `max(1, ceil(levelCostAdd * amplifier - level * levelDrain))`.

Milestones: `challenge_discovery_villager_100` and `challenge_discovery_villager_2500` on `discovery.villager-att.improved-trades` at 100 and 2500, rewarding 300 and 1000.

Listened events:

- `PlayerInteractEntityEvent` (`on`): rolls the proc on a villager
- `InventoryOpenEvent` (`on`): swaps in the adjusted merchant offers
- `PlayerTradeEvent` (`on`)
- `InventoryCloseEvent` (`on`): restores the original offers

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `effectivenessBase` | `0.005` | Proc chance at level 0, 0-1. |
| `maxEffectiveness` | `100` | Ceiling on the proc chance. The code clamps it to 1.0, so the default means no cap. |
| `levelDrain` | `2` | Vanilla levels shaved off the cost per adaptation level. |
| `levelCostAdd` | `10` | Base vanilla level cost per improved trade before the amplifier and drain. |
| `amplifier` | `1.0` | Multiplier on the base level cost. |

### Better Mending

| Property | Default |
|----------|---------|
| Class | `DiscoveryBetterMending` |
| Icon | `PHANTOM_MEMBRANE` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.8 |
| Tick interval (ms) | 2400 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-better-mending.toml` |

Milestones: `challenge_discovery_mending_10k` and `challenge_discovery_mending_100k` on `discovery.better-mending.durability-restored` at 10000 and 100000, rewarding 400 and 1500.

Listened events:

- `PlayerInteractEvent` (`on`): sneak plus left-click, air or block, main hand only

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `repairPerXpBase` | `2.0` | Durability points restored per experience point at level 0. |
| `repairPerXpFactor` | `4.0` | Additional durability per experience point unlocked across the level range. |
| `maxXpSpendBase` | `14.0` | Experience points one click can spend at level 0. |
| `maxXpSpendFactor` | `130.0` | Additional experience per click unlocked across the level range. |
| `cooldownTicksBase` | `38.0` | Item cooldown after a mend at level 0, in server ticks (20 ticks = 1 second). |
| `cooldownTicksReduction` | `26.0` | Ticks removed from that cooldown across the level range. |
| `skillXpPerDurability` | `0.35` | Discovery XP per durability point restored. |

### Archaeologist

| Property | Default |
|----------|---------|
| Class | `DiscoveryArchaeologist` |
| Icon | `BRUSH` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.8 |
| Tick interval (ms) | 10 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-archaeologist.toml` |

Brush completions arrive through a `BrushEventBridge` built by reflection at construction against `BlockBrushEvent`. When the server does not expose that event the bridge is null, and the right-click handler's queued pending brush plus its fallback window is what resolves the reward.

Milestones: `challenge_discovery_archaeologist_50` and `challenge_discovery_archaeologist_500` on `discovery.archaeologist.bonus-finds` at 50 and 500, rewarding 300 and 1000.

Listened events:

- `PlayerInteractEvent` (`on`): the brush interaction
- `PlayerQuitEvent` (`on`)

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusRollChanceBase` | `0.12` | Chance of a bonus reward at level 0, 0-1. |
| `bonusRollChanceFactor` | `0.43` | Additional bonus chance unlocked across the level range. |
| `maxBonusRollChance` | `0.72` | Ceiling on the bonus chance. |
| `rareRewardChanceBase` | `0.04` | Chance the bonus is a rare reward at level 0, 0-1. |
| `rareRewardChanceFactor` | `0.24` | Additional rare chance unlocked across the level range. |
| `maxRareRewardChance` | `0.3` | Ceiling on the rare chance. |
| `cooldownMillisBase` | `1600` | Milliseconds between rewards at level 0. |
| `cooldownMillisFactor` | `1250` | Milliseconds removed from that cooldown across the level range. |
| `xpPerReward` | `10` | Flat Discovery XP per bonus reward. |
| `rewardValueXpMultiplier` | `0.45` | Multiplier on the reward item's value added to that XP. |

### Cartographer Pulse

| Property | Default |
|----------|---------|
| Class | `DiscoveryCartographerPulse` |
| Icon | `COMPASS` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-cartographer-pulse.toml` |

Milestones: `challenge_discovery_cartographer_100` and `challenge_discovery_cartographer_1k` on `discovery.cartographer-pulse.pulses` at 100 and 1000, rewarding 300 and 1000.

Listened events:

- `PlayerInteractEvent` (`on`): sneak plus right-click with a `COMPASS` in the main hand
- `PlayerQuitEvent` (`on`)

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `searchRangeBase` | `640` | Structure search radius in blocks at level 0. |
| `searchRangeFactor` | `768` | Additional search radius in blocks across the level range. |
| `cooldownMillisBase` | `26000` | Milliseconds between pulses at level 0. |
| `cooldownMillisFactor` | `14000` | Milliseconds removed from that cooldown across the level range. |
| `xpPerPulse` | `25` | Discovery XP per successful pulse. |
| `hungerCost` | `2` | Food points spent per pulse. The pulse is refused below this food level. |

### Insight

| Property | Default |
|----------|---------|
| Class | `DiscoveryInsight` |
| Icon | `SPYGLASS` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 2 |
| Cost factor | 0.2 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-insight.toml` |

Milestones: `challenge_discovery_insight_100` and `challenge_discovery_insight_1000` on `discovery.insight.entities-inspected` at 100 and 1000, rewarding 300 and 1200.

Listened events:

- `EntityDamageByEntityEvent` (`on`): spawns damage numbers
- `PlayerMoveEvent` (`on`): refreshes the inspected target display
- `PlayerQuitEvent` (`on`)

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rangeBase` | `6` | Inspection range in blocks at level 0. |
| `rangeFactor` | `18` | Additional inspection range in blocks across the level range. |
| `hudScalePerBlock` | `0.22` | Display scale added per block of distance so the HUD keeps a constant on-screen size. |
| `hudMinScale` | `0.5` | Floor on the HUD display scale. |
| `hudMaxScale` | `4.0` | Ceiling on the HUD display scale. |
| `healthBarSegments` | `12` | Segments used to draw the inspected entity's health bar. |
| `showDamageNumbers` | `true` | Shows floating damage numbers when you hit something. |
| `damageNumberRise` | `0.7` | Blocks a damage number drifts upward over its lifetime. |
| `damageNumberLifeTicks` | `16` | Lifetime of a damage number in ticks. |
| `maxDamageNumbersPerTick` | `16` | Damage numbers spawned per scheduler tick, capped internally at 16. |
| `xpPerInspection` | `3` | Discovery XP per inspection. |
| `xpCooldownMs` | `10000` | Milliseconds between inspection XP grants for one player. |
| `maxPlayersPerPass` | `32` | Viewers refreshed per scheduler tick, capped internally at 32. |

### Trailblazer

| Property | Default |
|----------|---------|
| Class | `DiscoveryTrailblazer` |
| Icon | `LEATHER_BOOTS` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.3 |
| Tick interval (ms) | 600 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-trailblazer.toml` |

Milestones: `challenge_discovery_trailblazer_25` and `challenge_discovery_trailblazer_100` on `discovery.trailblazer.discoveries` at 25 and 100, rewarding 400 and 1200.

No event handlers. It runs entirely on its tick.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `firstVisitXpBase` | `40` | Skill XP for a first visit at level 0. |
| `firstVisitXpFactor` | `160` | Additional first-visit XP unlocked across the level range. |
| `structureXpMultiplier` | `2.5` | Multiplier applied when the first visit is a structure type rather than a biome. |
| `speedDurationTicksBase` | `80` | Speed duration in ticks at level 0. |
| `speedDurationTicksFactor` | `120` | Additional speed duration in ticks across the level range. |
| `speedAmplifier` | `1` | Speed tier granted on a fresh discovery. 0 is +20% movement speed and each tier adds another +20%. |

### Field Notes

| Property | Default |
|----------|---------|
| Class | `DiscoveryFieldNotes` |
| Icon | `WRITABLE_BOOK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Tick interval (ms) | 4400 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-field-notes.toml` |

Milestones: `challenge_discovery_fieldnotes_25` and `challenge_discovery_fieldnotes_100` on `discovery.field-notes.species` at 25 and 100, rewarding 500 and 2000.

Listened events:

- `EntityDeathEvent` (`on`): records the species and pays the first-kill bounty
- `EntityDamageByEntityEvent` (`on`): applies the banked species damage bonus

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `firstKillXpBase` | `120` | Skill XP for a first kill of a species at level 0. |
| `firstKillXpFactor` | `240` | Additional first-kill XP unlocked across the level range. |
| `bonusPerKill` | `0.15` | Damage bonus banked against a species per kill, until the cap. |
| `perSpeciesCapBase` | `0.5` | Cap on the banked bonus per species at level 0. |
| `perSpeciesCapFactor` | `2.5` | Additional per-species cap unlocked across the level range. |

### Polymath

| Property | Default |
|----------|---------|
| Class | `DiscoveryPolymath` |
| Icon | `KNOWLEDGE_BOOK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.4 |
| Tick interval (ms) | 3000 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-polymath.toml` |

Milestones: `challenge_discovery_polymath_500` and `challenge_discovery_polymath_5k` on `discovery.polymath.boosts` at 500 and 5000, rewarding 400 and 1500.

No event handlers. It refreshes on its tick.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `perSkillBonusBase` | `0.015` | Global XP bonus contributed by each qualifying skill at level 0. |
| `perSkillBonusFactor` | `0.045` | Additional per-skill bonus unlocked across the level range. |
| `skillThreshold` | `5` | Level a skill line must reach to count as qualifying. |
| `maxTotalBonus` | `1.0` | Ceiling on the combined bonus across all qualifying skills. |

### Relic Appraiser

| Property | Default |
|----------|---------|
| Class | `DiscoveryRelicAppraiser` |
| Icon | `SPYGLASS` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.3 |
| Tick interval (ms) | 3300 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-relic-appraiser.toml` |

An appraised item gets a persistent-data byte and a lore tag, and is refused on a second attempt.

Milestones: `challenge_discovery_appraiser_50` and `challenge_discovery_appraiser_500` on `discovery.relic-appraiser.appraised` at 50 and 500, rewarding 300 and 1200.

Listened events:

- `PlayerInteractEvent` (`on`): sneak plus right-click, air or block, main hand only

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `appraiseXpBase` | `60` | Discovery XP for an appraisal at level 0, before rarity weighting. |
| `appraiseXpFactor` | `180` | Additional appraisal XP unlocked across the level range. |
| `discRarityWeight` | `1.5` | Rarity multiplier for music discs. |
| `headRarityWeight` | `1.4` | Rarity multiplier for heads and skulls. |
| `trimRarityWeight` | `1.25` | Rarity multiplier for armor trim templates. |
| `sherdRarityWeight` | `1.0` | Rarity multiplier for pottery sherds. |

### Sixth Sense

| Property | Default |
|----------|---------|
| Class | `DiscoverySixthSense` |
| Icon | `ECHO_SHARD` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.4 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-sixth-sense.toml` |

Milestones: `challenge_discovery_sixthsense_100` and `challenge_discovery_sixthsense_1k` on `discovery.sixth-sense.senses` at 100 and 1000, rewarding 300 and 1000.

Listened events:

- `PlayerQuitEvent` (`on`)

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `detectionRangeBase` | `48` | Structure detection radius in blocks at level 0. |
| `detectionRangeFactor` | `112` | Additional detection radius in blocks across the level range. |
| `exploredRadius` | `20` | Distance in blocks under which a structure counts as already reached, suppressing the pulse. |
| `pulseIntervalMillis` | `4000` | Milliseconds between sense pulses for one player. |

### Keen Eye

| Property | Default |
|----------|---------|
| Class | `DiscoveryKeenEye` |
| Icon | `ENDER_EYE` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.3 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-keen-eye.toml` |

Milestones: `challenge_discovery_keeneye_250` and `challenge_discovery_keeneye_2500` on `discovery.keen-eye.glimmers` at 250 and 2500, rewarding 300 and 1200.

Listened events:

- `PlayerQuitEvent` (`on`)

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rangeBase` | `10` | Line-of-sight range in blocks at level 0. |
| `rangeFactor` | `14` | Additional line-of-sight range in blocks across the level range. |
| `glimmerDurationTicksBase` | `12` | Ticks a highlight stays visible at level 0. |
| `glimmerDurationTicksFactor` | `28` | Additional highlight ticks across the level range. |
| `viewConeCos` | `0.55` | Minimum cosine of the angle between your look direction and the container for it to glimmer. |
| `maxHighlightsPerScan` | `6` | Containers highlighted per scan, capped internally at 8. |
| `scanIntervalMillis` | `1500` | Milliseconds between line-of-sight scans for one player. |

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
