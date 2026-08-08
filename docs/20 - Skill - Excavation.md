# Skill: Excavation

Skill id `excavation`. Earn XP by digging dirt, sand, gravel, and other soft ground with shovels. Excavation has 12 registered adaptations and uses the `DIAMOND_SHOVEL` icon.

**XP sources:** digging eligible soft blocks with shovels and shovel combat damage.

**Milestones / challenges** (stat keys):

- `challenge_excavate_1k` tracking `excavation.blocks.broken`
- `challenge_excavate_5k` tracking `excavation.blocks.broken`
- `challenge_excavate_50k` tracking `excavation.blocks.broken`
- `challenge_dig_damage_1k` tracking `excavation.damage`
- `challenge_dig_damage_10k` tracking `excavation.damage`
- `challenge_dig_value_5k` tracking `excavation.blocks.value`
- `challenge_dig_value_50k` tracking `excavation.blocks.value`
- `challenge_dig_gravel_500` tracking `excavation.gravel`
- `challenge_dig_gravel_5k` tracking `excavation.gravel`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `excavation` |
| Class | `SkillExcavation` |
| Icon | `DIAMOND_SHOVEL` |
| Color | `YELLOW` |
| Interval (ms) | `5953` |
| Skill config | `plugins/Adapt/adapt/skills/excavation.toml` |
| Adaptation count | 12 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/excavation.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&e"` | Legacy ampersand color code used for this skill in menus and text. |
| `getXpForAttackingWithTools` | `true` | XP awarded for get for attacking with tools. |
| `maxHardnessBonus` | `9` | Maximum block-hardness contribution added to mining XP calculations. |
| `maxBlastResistanceBonus` | `10` | Maximum blast-resistance contribution added to mining XP calculations. |
| `challengeExcavationReward` | `1200` | Reward for the excavation challenge. |
| `valueXPMultiplier` | `0.6` | Unitless multiplier applied to XP from value multiplier. |
| `cooldownDelay` | `1250` | Minimum delay between passive skill XP awards, in milliseconds. |
| `axeDamageXPMultiplier` | `4.0` | Unitless multiplier applied to XP from axe damage multiplier. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Hasty Excavator (`excavation-haste`)

Starting a block break grants stable Haste long enough to finish slower excavation blocks.

**Runtime entry points:** on `BlockDamageEvent`; periodic evaluation every 4388 ms.

**Menu displays:** Haste level while actively excavating.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ExcavationHaste` |
| Icon | `GOLDEN_PICKAXE` |
| Max level | 3 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.3 |
| Tick interval (ms) | 4388 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-haste.toml` |

Listened events:

- `BlockDamageEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `hasteDurationTicks` | `100` | Duration in ticks that Hasty Excavator remains active after mining begins. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Super-Seeing Spelunker (`excavation-spelunker`)

Hold glow berries in your main hand to reveal glowing ore displays through the ground.

**Runtime entry points:** on sneak toggle; on `EntityRemoveEvent`; periodic evaluation every 20388 ms.

**Menu displays:** Hold ore in the off hand and glow berries in the main hand, then sneak; detection range; consumes one glow berry per use.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ExcavationSpelunker` |
| Icon | `GOLDEN_HELMET` |
| Max level | 5 |
| Initial knowledge cost | 10 |
| Base knowledge cost | 5 |
| Cost factor | 1 |
| Tick interval (ms) | 20388 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-spelunker.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `EntityRemoveEvent` (`on`)
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldown` | `6.0` | Cooldown. |
| `rangeMultiplier` | `5` | Range multiplier. Blocks. |
| `maxBlockChecks` | `8192` | Maximum world block checks made by one Spelunker activation. |
| `denseScanRadius` | `8` | Radius searched completely before the remaining Spelunker budget is spread across the full range. |
| `maxHighlights` | `16` | Maximum ore markers created by one Spelunker scan. |
| `highlightDurationTicks` | `100` | Duration in ticks that glowing ore displays remain visible. |
| `displayViewRange` | `1.0` | Client view-range multiplier for glowing ore displays. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Omnitool (`excavation-omnitool`)

Merge your tools into one omni-tool that swaps to the right tool for the job. Shift-click one tool onto another in your inventory to merge; sneak-drop to disassemble.

**Runtime entry points:** on melee/projectile hit (damage); when breaking blocks; on block/entity/air interact (click); on drop item; on `BlockDamageEvent`; on inventory click; periodic evaluation every 20202 ms.

**Menu displays:** Merges tools into one item and selects the suitable component automatically; shift-click one tool onto another to merge; sneak-drop to unbind; a zero-durability component cannot be used; maximum merged components.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ExcavationOmniTool` |
| Icon | `DISC_FRAGMENT_5` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 10 |
| Cost factor | 0.20 |
| Tick interval (ms) | 20202 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-omnitool.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `BlockBreakEvent` (`on`) — when breaking blocks
- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerDropItemEvent` (`on`) — on drop item
- `BlockDamageEvent` (`on`)
- `InventoryClickEvent` (`on`) — on inventory click

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `startingSlots` | `1` | Starting slots. count. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Shovel Drop-To-Inventory (`excavation-drop-to-inventory`)

Excavated blocks drop directly into your inventory.

**Runtime entry points:** on `BlockDropItemEvent`; periodic evaluation every 11777 ms.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ExcavationDropToInventory` |
| Icon | `CHEST` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 1 |
| Cost factor | 1 |
| Tick interval (ms) | 11777 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-drop-to-inventory.toml` |

Listened events:

- `BlockDropItemEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Seismic Ping (`excavation-seismic-ping`)

Mining can temporarily reveal a nearby ore block with a private colored glow.

**Runtime entry points:** when breaking blocks; periodic evaluation every 2200 ms.

**Menu displays:** Scan Range; Ping Chance; Ping Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ExcavationSeismicPing` |
| Icon | `GOAT_HORN` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.78 |
| Tick interval (ms) | 2200 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-seismic-ping.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `scanRangeBase` | `11` | Base Scan range. Blocks. |
| `scanRangeFactor` | `18` | Scan range factor. Blocks. |
| `maxBlockChecks` | `1024` | Maximum world block checks made by one seismic ping. |
| `denseScanRadius` | `5` | Radius searched completely before the remaining seismic budget is spread across the full range. |
| `pingChanceBase` | `0.14` | Proc chance for ping chance base. decimal probability. |
| `pingChanceFactor` | `0.37` | Proc chance for ping chance factor. decimal probability. |
| `maxPingChance` | `0.6` | Maximum XP credited for max ping chance. |
| `cooldownMillisBase` | `2600` | Base Cooldown millis. Milliseconds. |
| `cooldownMillisFactor` | `1850` | Cooldown millis factor. Milliseconds. |
| `xpPerPing` | `8` | XP awarded for xp per ping. |
| `targetValueXpMultiplier` | `0.5` | Unitless multiplier applied to XP from target value multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Tunneler (`excavation-tunneler`)

Sneak while digging soft blocks to carve a whole plane at once.

**Runtime entry points:** when breaking blocks; periodic evaluation every 3170 ms.

**Menu displays:** Bonus Blocks Per Dig; Extra Durability Per Bonus Block.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ExcavationTunneler` |
| Icon | `IRON_SHOVEL` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.75 |
| Tick interval (ms) | 3170 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-tunneler.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusBlocksMax` | `8` | Bonus blocks max. Blocks. |
| `durabilityCostPerBonusBlock` | `1` | Durability cost per bonus block. durability points. |
| `xpPerBonusBlock` | `1.5` | XP awarded for xp per bonus block. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Treasure Hunter (`excavation-treasure-hunter`)

Digging sand, gravel, mud, or clay can unearth archaeology treasure.

**Runtime entry points:** when breaking blocks; periodic evaluation every 3370 ms.

**Menu displays:** Treasure Chance; Treasures roll from a weighted archaeology table.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ExcavationTreasureHunter` |
| Icon | `EMERALD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 3370 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-treasure-hunter.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `treasureChanceBase` | `0.01` | Proc chance for treasure chance base. decimal probability. |
| `treasureChanceFactor` | `0.05` | Proc chance for treasure chance factor. decimal probability. |
| `maxTreasureChance` | `0.06` | Proc chance for max treasure chance. decimal probability. |
| `lootTable` | `["BONE:30:1:2", "FLINT:30:1:2", "CLAY_BALL:15:1:3", "ANGLER_POTTERY_SHERD:6:1:1", "ARMS_UP_POTTERY_SHERD:6:1:1", "SKULL_POTTERY_SHERD:4:1:1", "EMERALD:3:1:1"]` | Weighted loot table entries formatted as MATERIAL:weight:min:max. |
| `xpPerTreasure` | `12` | XP awarded for xp per treasure. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Soft Fall (`excavation-soft-fall`)

Landing on soft diggable ground reduces fall damage, up to full negation.

**Runtime entry points:** on taking damage; periodic evaluation every 3530 ms.

**Menu displays:** Fall Damage Reduction; Applies when landing on dirt, sand, gravel, clay, mud, or soul sand.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ExcavationSoftFall` |
| Icon | `SAND` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.55 |
| Tick interval (ms) | 3530 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-soft-fall.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `reductionBase` | `0.15` | Base Reduction. |
| `reductionFactor` | `0.85` | Reduction factor. Unitless multiplier. |
| `maxReduction` | `1.0` | Maximum reduction. |
| `xpPerDamagePrevented` | `3.0` | XP awarded for xp per damage prevented. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Earth Mover (`excavation-earth-mover`)

Sneak-right-click the air with a shovel to damage, knock back, and slow hostile mobs with a wave of earth. Damage scales from the held shovel. Each wave costs hunger.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 3730 ms.

**Menu displays:** Wave Radius; Shovel Damage Multiplier; Knockback Force; Slow Duration; Wave Cooldown; Hunger Cost.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ExcavationEarthMover` |
| Icon | `DIRT` |
| Max level | 5 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 6 |
| Cost factor | 0.8 |
| Tick interval (ms) | 3730 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-earth-mover.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `3` | Base Radius. Blocks. |
| `radiusFactor` | `5` | Radius factor. Blocks. |
| `verticalRange` | `3` | Vertical range. Blocks. |
| `forceBase` | `0.6` | Base Force. |
| `forceFactor` | `1.0` | Force factor. Unitless multiplier. |
| `damageMultiplierBase` | `0.75` | Base multiplier applied to the held shovel's attack damage for Earth Mover. |
| `damageMultiplierFactor` | `0.75` | Additional held-shovel damage multiplier gained across Earth Mover levels. |
| `liftVelocity` | `0.35` | Lift velocity. |
| `slowTicksBase` | `40` | Base Slow ticks. Server ticks (20 ticks = 1 second). |
| `slowTicksFactor` | `60` | Slow ticks factor. Server ticks (20 ticks = 1 second). |
| `slowAmplifierMax` | `2` | Slow amplifier max. Level or effect-amplifier units. |
| `cooldownMillisBase` | `16000` | Base Cooldown millis. Milliseconds. |
| `cooldownMillisFactor` | `8000` | Cooldown millis factor. Milliseconds. |
| `cooldownScale` | `0.5` | Multiplier applied to Earth Mover cooldowns after level scaling. |
| `hungerCost` | `2` | Hunger points consumed per earth wave. |
| `xpPerMobHit` | `6` | XP awarded for xp per mob hit. |
| `maxCandidatesPerActivation` | `16` | Maximum hostile mobs inspected by one earth wave. |
| `maxAffectedPerActivation` | `12` | Maximum hostile mobs launched by one earth wave. |
| `maxTargetFxPerActivation` | `8` | Maximum launched mobs that receive individual block-particle effects. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Burrow (`excavation-burrow`)

Sneak-right-click soft ground with a shovel to rapidly dig straight down, stopping before hazards. Each burrow costs hunger and tool durability.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 4130 ms.

**Menu displays:** Max Burrow Depth; Durability Per Block; Burrow Cooldown; Hunger Cost.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ExcavationBurrow` |
| Icon | `COARSE_DIRT` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 5 |
| Cost factor | 0.78 |
| Tick interval (ms) | 4130 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-burrow.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `depthBase` | `3` | Base Depth. |
| `depthFactor` | `13` | Depth factor. Unitless multiplier. |
| `ticksPerBlock` | `2` | Ticks per block. Server ticks (20 ticks = 1 second). |
| `safeFloorMargin` | `16` | Safe floor margin. |
| `durabilityCostPerBlock` | `1` | Durability points consumed per processed block. |
| `hungerCost` | `1` | Hunger points consumed per burrow activation. |
| `cooldownMillisBase` | `14000` | Base Cooldown millis. Milliseconds. |
| `cooldownMillisFactor` | `7000` | Cooldown millis factor. Milliseconds. |
| `xpPerBlock` | `2` | XP awarded for xp per block. Blocks. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Grave Digger (`excavation-grave-digger`)

Digging earthen ground can unearth bone loot, and rarely disturbs a hostile grave.

**Runtime entry points:** on entity death / kill credit; when breaking blocks; periodic evaluation every 4310 ms.

**Menu displays:** Bone Loot Chance; Disturbed Grave Chance.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ExcavationGraveDigger` |
| Icon | `BONE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.68 |
| Tick interval (ms) | 4310 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-grave-digger.toml` |

Listened events:

- `EntityDeathEvent` (`on`) — on entity death / kill credit
- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `lootChanceBase` | `0.008` | Proc chance for loot chance base. decimal probability. |
| `lootChanceFactor` | `0.035` | Proc chance for loot chance factor. decimal probability. |
| `maxLootChance` | `0.045` | Proc chance for max loot chance. decimal probability. |
| `graveChanceBase` | `0.001` | Proc chance for grave chance base. decimal probability. |
| `graveChanceFactor` | `0.004` | Proc chance for grave chance factor. decimal probability. |
| `maxGraveChance` | `0.005` | Proc chance for max grave chance. decimal probability. |
| `graveCooldownMillis` | `45000` | Grave cooldown millis. Milliseconds. |
| `lootTable` | `["BONE:40:1:2", "BONE_MEAL:25:2:4", "ROTTEN_FLESH:20:1:2", "BONE_BLOCK:6:1:1", "SKELETON_SKULL:2:1:1"]` | Weighted loot table entries formatted as MATERIAL:weight:min:max. |
| `xpPerLoot` | `8` | XP awarded for xp per loot. |
| `xpPerGrave` | `35` | XP awarded for xp per grave. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Mudlark (`excavation-mudlark`)

Bonus drops from muddy blocks, plus haste while digging in water or rain.

**Runtime entry points:** on `BlockDamageEvent`; when breaking blocks; periodic evaluation every 4530 ms.

**Menu displays:** Bonus Drop Chance; x Levels of haste while digging wet.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ExcavationMudlark` |
| Icon | `MUD` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.6 |
| Tick interval (ms) | 4530 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-mudlark.toml` |

Listened events:

- `BlockDamageEvent` (`on`)
- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusChanceBase` | `0.05` | Proc chance for bonus chance base. decimal probability. |
| `bonusChanceFactor` | `0.2` | Proc chance for bonus chance factor. decimal probability. |
| `maxBonusChance` | `0.25` | Proc chance for max bonus chance. decimal probability. |
| `maxHasteLevel` | `3` | Maximum haste level. Level or effect-amplifier units. |
| `hasteDurationTicks` | `60` | Haste duration ticks. Server ticks (20 ticks = 1 second). |
| `xpPerBonusDrop` | `3` | XP awarded for xp per bonus drop. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
