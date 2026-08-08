# Skill: Discovery

Skill id `discovery`. Earn XP through exploration, discoveries, and collected experience. Discovery has 14 registered adaptations and uses the `FILLED_MAP` icon.

**XP sources:** exploration, first-time discoveries, inspected entities, collected items, recipes, foods, and vanilla experience.

**Milestones / challenges** (stat keys):

- `challenge_discover_items_50` tracking `discovery.items`
- `challenge_discover_items_250` tracking `discovery.items`
- `challenge_discover_blocks_50` tracking `discovery.blocks`
- `challenge_discover_blocks_250` tracking `discovery.blocks`
- `challenge_discover_mobs_25` tracking `discovery.mobs`
- `challenge_discover_mobs_75` tracking `discovery.mobs`
- `challenge_discover_biomes_10` tracking `discovery.biomes`
- `challenge_discover_biomes_40` tracking `discovery.biomes`
- `challenge_discover_foods_10` tracking `discovery.foods`
- `challenge_discover_foods_30` tracking `discovery.foods`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `discovery` |
| Class | `SkillDiscovery` |
| Icon | `FILLED_MAP` |
| Color | `AQUA` |
| Interval (ms) | `50` |
| Skill config | `plugins/Adapt/adapt/skills/discovery.toml` |
| Adaptation count | 14 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/discovery.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&b"` | Legacy ampersand color code used for this skill in menus and text. |
| `showParticles` | `true` | Controls whether particles are emitted. |
| `discoverBiomeXP` | `15` | XP awarded for discover biome. |
| `discoverPotionXP` | `36` | XP awarded for discover potion. |
| `discoverEntityTypeXP` | `125` | XP awarded for discover entity type. |
| `discoverFoodTypeXP` | `75` | Discovery XP awarded the first time a food type is recorded. |
| `discoverPlayerXP` | `125` | XP awarded for discover player. |
| `discoverEnvironmentXP` | `750` | XP awarded for discover environment. |
| `discoverWorldXP` | `750` | XP awarded for discover world. |
| `discoverEnchantMaxXP` | `250` | Maximum XP credited for discover enchant max. |
| `discoverEnchantLevelXPMultiplier` | `52` | Unitless multiplier applied to XP from discover enchant level multiplier. |
| `discoverEnchantBaseXP` | `5` | Base skill XP credited for discover enchant base. |
| `discoverItemBaseXP` | `10` | Base skill XP credited for discover item base. |
| `discoverRecipeBaseXP` | `15` | Base skill XP credited for discover recipe base. |
| `discoverItemValueXPMultiplier` | `1` | Unitless multiplier applied to XP from discover item value multiplier. |
| `discoverBlockBaseXP` | `3` | Base skill XP credited for discover block base. |
| `discoverBlockValueXPMultiplier` | `0.333` | Unitless multiplier applied to XP from discover block value multiplier. |
| `maxTargetChecksPerPass` | `64` | Maximum target-block checks per scheduler pass. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Experimental Unity (`discovery-unity`)

Collecting Experience Orbs adds XP to random skills.

**Runtime entry points:** on vanilla XP change; periodic evaluation every 666 ms.

**Menu displays:** Damage-prevention percentage and XP consumed per save.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

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

Listened events:

- `PlayerExpChangeEvent` (`on`) — on vanilla XP change

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `xpGainedMultiplier` | `8` | Unitless multiplier applied to XP from xp gained multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### World Armor (`discovery-world-armor`)

Passive armor depending on nearby block hardness.

**Runtime entry points:** periodic evaluation every 50 ms while its conditions hold.

**Menu displays:** Passive armor derived from nearby block hardness; current armor strength.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `DiscoveryArmor` |
| Icon | `TURTLE_HELMET` |
| Max level | 3 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.3 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-world-armor.toml` |

Listened events:

- `PlayerJoinEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxPlayersPerPass` | `16` | Maximum players examined per scheduler pass. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Experimental Resistance (`discovery-xp-resist`)

Consume experience to mitigate damage only when a hit would drop you below 5 hearts or kill you.

**Runtime entry points:** on taking damage; periodic evaluation every 5215 ms.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `DiscoveryXpResist` |
| Icon | `TOTEM_OF_UNDYING` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 5 |
| Cost factor | 0.8 |
| Tick interval (ms) | 5215 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-xp-resist.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `effectivenessBase` | `0.15` | Base Effectiveness. |
| `maxEffectiveness` | `0.95` | Maximum effectiveness. |
| `levelDrain` | `2` | Level drain. Level or effect-amplifier units. |
| `levelCostAdd` | `12` | Level cost add. Level or effect-amplifier units. |
| `amplifier` | `1.0` | Amplifier. Level or effect-amplifier units. |
| `triggerHealthThreshold` | `10.0` | Trigger health threshold. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Villager Attraction (`discovery-villager-att`)

Improves villager trades at the cost of XP per interaction.

**Runtime entry points:** on entity right-click; on `PlayerTradeEvent`; on `InventoryCloseEvent`.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `DiscoveryVillagerAtt` |
| Icon | `GLASS_BOTTLE` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 1 |
| Cost factor | 0.01 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-villager-att.toml` |

Listened events:

- `PlayerInteractEntityEvent` (`on`) — on entity right-click
- `InventoryOpenEvent` (`on`) — prepares adjusted merchant offers
- `PlayerTradeEvent` (`on`)
- `InventoryCloseEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `effectivenessBase` | `0.005` | Base Effectiveness. |
| `maxEffectiveness` | `100` | Maximum effectiveness. |
| `levelDrain` | `2` | Level drain. Level or effect-amplifier units. |
| `levelCostAdd` | `10` | Level cost add. Level or effect-amplifier units. |
| `amplifier` | `1.0` | Amplifier. Level or effect-amplifier units. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Better Mending (`discovery-better-mending`)

Sneak-left-click to spend your stored XP and directly mend the Mending item in your hand.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 2400 ms.

**Menu displays:** Durability Repaired per XP; Max XP Spend per Click; Mending Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

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

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `repairPerXpBase` | `2.0` | Base skill XP credited for repair per base. |
| `repairPerXpFactor` | `4.0` | Unitless multiplier applied to XP from repair per factor. |
| `maxXpSpendBase` | `14.0` | Maximum XP credited for max spend base. |
| `maxXpSpendFactor` | `130.0` | Unitless multiplier applied to XP from max spend factor. |
| `cooldownTicksBase` | `38.0` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksReduction` | `26.0` | Cooldown ticks reduction. Server ticks (20 ticks = 1 second). |
| `skillXpPerDurability` | `0.35` | XP awarded for skill per durability. durability points. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Archaeologist (`discovery-archaeologist`)

Brushing suspicious blocks can yield bonus archaeology rewards.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 10 ms while its conditions hold.

**Menu displays:** Bonus Reward Chance; Rare Reward Chance; Reward Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

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

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusRollChanceBase` | `0.12` | Proc chance for bonus roll chance base. decimal probability. |
| `bonusRollChanceFactor` | `0.43` | Proc chance for bonus roll chance factor. decimal probability. |
| `maxBonusRollChance` | `0.72` | Proc chance for max bonus roll chance. decimal probability. |
| `rareRewardChanceBase` | `0.04` | Proc chance for rare reward chance base. decimal probability. |
| `rareRewardChanceFactor` | `0.24` | Proc chance for rare reward chance factor. decimal probability. |
| `maxRareRewardChance` | `0.3` | Proc chance for max rare reward chance. decimal probability. |
| `cooldownMillisBase` | `1600` | Base Cooldown millis. Milliseconds. |
| `cooldownMillisFactor` | `1250` | Cooldown millis factor. Milliseconds. |
| `xpPerReward` | `10` | XP awarded for xp per reward. |
| `rewardValueXpMultiplier` | `0.45` | Unitless multiplier applied to XP from reward value multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Cartographer Pulse (`discovery-cartographer-pulse`)

Sneak-right-click with a compass to lock it toward a nearby structure and see a private glowing direction line. Each pulse costs hunger.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 2000 ms.

**Menu displays:** Structure Search Range; Pulse Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

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

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `searchRangeBase` | `640` | Base Search range. Blocks. |
| `searchRangeFactor` | `768` | Search range factor. Blocks. |
| `cooldownMillisBase` | `26000` | Base Cooldown millis. Milliseconds. |
| `cooldownMillisFactor` | `14000` | Cooldown millis factor. Milliseconds. |
| `xpPerPulse` | `25` | XP awarded for xp per pulse. |
| `hungerCost` | `2` | Food points consumed per compass pulse. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Insight (`discovery-insight`)

Study creatures at a glance: the entity you look at shows its name and health bar above its head, tameable creatures show their live speed, jump, and attack stats, and your hits show floating damage numbers with crits in orange.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 50 ms while its conditions hold.

**Menu displays:** Detection Range.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

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

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `PlayerQuitEvent` (`on`)
- `PlayerMoveEvent` (`on`) — refreshes the inspected target display

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rangeBase` | `6` | Base Range. Blocks. |
| `rangeFactor` | `18` | Range factor. Blocks. |
| `hudScalePerBlock` | `0.22` | Display scale gained per block of distance so the HUD keeps a constant on-screen size. |
| `hudMinScale` | `0.5` | Minimum HUD display scale. |
| `hudMaxScale` | `4.0` | Maximum HUD display scale. |
| `healthBarSegments` | `12` | Number of segments used to render the inspected entity's health bar. |
| `showDamageNumbers` | `true` | Shows floating damage numbers when you hit creatures. |
| `damageNumberRise` | `0.7` | Vertical drift of damage numbers over their lifetime. |
| `damageNumberLifeTicks` | `16` | Lifetime of damage numbers in ticks. |
| `maxDamageNumbersPerTick` | `16` | Maximum damage numbers spawned per scheduler tick, capped internally at 16. |
| `xpPerInspection` | `3` | XP awarded for xp per inspection. |
| `xpCooldownMs` | `10000` | Cooldown between inspection XP grants in milliseconds. |
| `maxPlayersPerPass` | `32` | Maximum viewers examined per scheduler tick, capped internally at 32. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Trailblazer (`discovery-trailblazer`)

Your first visit to each biome or structure type grants a skill-XP burst and brief speed.

**Runtime entry points:** periodic evaluation every 600 ms.

**Menu displays:** First-Visit XP Burst; Discovery Speed Duration.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

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

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `firstVisitXpBase` | `40` | Base skill XP credited for first visit base. |
| `firstVisitXpFactor` | `160` | Unitless multiplier applied to XP from first visit factor. |
| `structureXpMultiplier` | `2.5` | Multiplier applied to the reward when the first visit is a structure type instead of a biome. |
| `speedDurationTicksBase` | `80` | Base Speed duration ticks. Server ticks (20 ticks = 1 second). |
| `speedDurationTicksFactor` | `120` | Speed duration ticks factor. Server ticks (20 ticks = 1 second). |
| `speedAmplifier` | `1` | Speed tier granted on a fresh discovery (0 grants +20% movement speed, each tier adds another +20%). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Field Notes (`discovery-field-notes`)

Your first kill of each mob species pays big XP and banks a small permanent damage bonus against that species.

**Runtime entry points:** on entity death / kill credit; on melee/projectile hit (damage); periodic evaluation every 4400 ms.

**Menu displays:** Max Damage Bonus per Species; First-Kill XP Bounty.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

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

Listened events:

- `EntityDeathEvent` (`on`) — on entity death / kill credit
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `firstKillXpBase` | `120` | Base skill XP credited for first kill base. |
| `firstKillXpFactor` | `240` | Unitless multiplier applied to XP from first kill factor. |
| `bonusPerKill` | `0.15` | Damage bonus banked against a species per kill until the cap is reached. |
| `perSpeciesCapBase` | `0.5` | Base Per species cap. |
| `perSpeciesCapFactor` | `2.5` | Per species cap factor. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Polymath (`discovery-polymath`)

Each skill you have leveled past a threshold adds a small global XP-gain bonus.

**Runtime entry points:** passive evaluation while learned.

**Menu displays:** Global XP Bonus per Qualifying Skill; Skill Level Needed to Qualify.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `DiscoveryPolymath` |
| Icon | `KNOWLEDGE_BOOK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/discovery-polymath.toml` |

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `perSkillBonusBase` | `0.015` | Base Per skill bonus. |
| `perSkillBonusFactor` | `0.045` | Per skill bonus factor. Unitless multiplier. |
| `skillThreshold` | `5` | Skill level a line must reach to contribute a global XP bonus. |
| `maxTotalBonus` | `1.0` | Maximum combined global XP bonus across all qualifying skills. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Relic Appraiser (`discovery-relic-appraiser`)

Sneak-right-click rare drops (heads, discs, armor trims, pottery sherds) to appraise them for Discovery XP; appraised items gain a lore tag.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 3300 ms.

**Menu displays:** Appraisal XP (scaled by item rarity).

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

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

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `appraiseXpBase` | `60` | Base skill XP credited for appraise base. |
| `appraiseXpFactor` | `180` | Unitless multiplier applied to XP from appraise factor. |
| `discRarityWeight` | `1.5` | Rarity multiplier applied to music disc appraisals. |
| `headRarityWeight` | `1.4` | Rarity multiplier applied to head and skull appraisals. |
| `trimRarityWeight` | `1.25` | Rarity multiplier applied to armor trim template appraisals. |
| `sherdRarityWeight` | `1.0` | Rarity multiplier applied to pottery sherd appraisals. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Sixth Sense (`discovery-sixth-sense`)

A private glowing direction line hints when an unexplored structure is within range.

**Runtime entry points:** periodic evaluation every 2000 ms.

**Menu displays:** Structure Detection Range.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

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

Listened events:

- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `detectionRangeBase` | `48` | Base Detection range. Blocks. |
| `detectionRangeFactor` | `112` | Additional detection range at maximum level, in blocks. |
| `exploredRadius` | `20` | Distance under which a structure is treated as already reached, suppressing the pulse. |
| `pulseIntervalMillis` | `4000` | Milliseconds between sense pulses for a single player. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Keen Eye (`discovery-keen-eye`)

Chests and spawners in your line of sight briefly appear as private glowing block displays.

**Runtime entry points:** passive evaluation while learned.

**Menu displays:** Line-of-Sight Range; Glimmer Duration.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

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

Listened events:

- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rangeBase` | `10` | Base Range. Blocks. |
| `rangeFactor` | `14` | Range factor. Blocks. |
| `glimmerDurationTicksBase` | `12` | Base Glimmer duration ticks. Server ticks (20 ticks = 1 second). |
| `glimmerDurationTicksFactor` | `28` | Glimmer duration ticks factor. Server ticks (20 ticks = 1 second). |
| `viewConeCos` | `0.55` | Minimum forward-view alignment (cosine) required for a container to glimmer. |
| `maxHighlightsPerScan` | `6` | Maximum containers highlighted per scan, capped internally at 8. |
| `scanIntervalMillis` | `1500` | Milliseconds between line-of-sight scans for a single player. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
