# Skill: Pickaxes

Skill id `pickaxe`. Earn XP by mining stone and ores with pickaxes. Pickaxes has 13 registered adaptations and uses the `NETHERITE_PICKAXE` icon.

**XP sources:** mining stone and ores with pickaxes, plus pickaxe combat damage.

**Milestones / challenges** (stat keys):

- `challenge_pickaxe_1k` tracking `pickaxe.blocks.broken`
- `challenge_pickaxe_5k` tracking `pickaxe.blocks.broken`
- `challenge_pickaxe_50k` tracking `pickaxe.blocks.broken`
- `challenge_pick_damage_1k` tracking `pickaxe.damage`
- `challenge_pick_damage_10k` tracking `pickaxe.damage`
- `challenge_pick_value_5k` tracking `pickaxe.blocks.value`
- `challenge_pick_value_50k` tracking `pickaxe.blocks.value`
- `challenge_pick_ores_500` tracking `pickaxe.ores`
- `challenge_pick_ores_5k` tracking `pickaxe.ores`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `pickaxe` |
| Class | `SkillPickaxes` |
| Icon | `NETHERITE_PICKAXE` |
| Color | `GOLD` |
| Interval (ms) | `2750` |
| Skill config | `plugins/Adapt/adapt/skills/pickaxe.toml` |
| Adaptation count | 13 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/pickaxe.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `debrisBonus` | `210` | Bonus mining XP for ancient debris. |
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&6"` | Legacy ampersand color code used for this skill in menus and text. |
| `getXpForAttackingWithTools` | `true` | XP awarded for get for attacking with tools. |
| `damageXPMultiplier` | `6.0` | Unitless multiplier applied to XP from damage multiplier. |
| `blockValueMultiplier` | `0.125` | Unitless multiplier applied to the configured material value in mining XP. |
| `maxHardnessBonus` | `9` | Maximum block-hardness contribution added to mining XP calculations. |
| `maxBlastResistanceBonus` | `10` | Maximum blast-resistance contribution added to mining XP calculations. |
| `coalBonus` | `18` | Bonus mining XP for coal ore. |
| `ironBonus` | `30` | Bonus mining XP for iron ore. |
| `redstoneBonus` | `55` | Bonus mining XP for redstone ore. |
| `copperBonus` | `22` | Bonus mining XP for copper ore. |
| `goldBonus` | `38` | Bonus mining XP for gold ore. |
| `lapisBonus` | `75` | Bonus mining XP for lapis ore. |
| `cooldownDelay` | `1250` | Minimum delay between passive skill XP awards, in milliseconds. |
| `diamondBonus` | `175` | Bonus mining XP for diamond ore. |
| `emeraldBonus` | `210` | Bonus mining XP for emerald ore. |
| `netherGoldBonus` | `105` | Bonus mining XP for Nether gold ore. |
| `netherQuartzBonus` | `125` | Bonus mining XP for Nether quartz ore. |
| `deepslateMultiplier` | `1.35` | Unitless mining-XP multiplier for deepslate ore variants. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Ore Chisel (`pickaxe-chisel`)

Right Click Ores to Chisel more ore out of them, at a severe durability cost.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 7433 ms.

**Menu displays:** Chance to Drop; Tool Wear.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `PickaxeChisel` |
| Icon | `IRON_NUGGET` |
| Max level | 7 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 6 |
| Cost factor | 0.4 |
| Tick interval (ms) | 7433 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-chisel.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldownTime` | `5` | Cooldown time. |
| `dropChanceBase` | `0.07` | Proc chance for drop chance base. decimal probability. |
| `dropChanceFactor` | `0.22` | Proc chance for drop chance factor. decimal probability. |
| `breakChance` | `0.25` | Proc chance for break chance. decimal probability. |
| `damagePerBlockBase` | `1` | Base damage points charged per processed block (2 points = 1 heart). |
| `damageFactorInverseMultiplier` | `2` | Damage factor inverse multiplier. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Veinminer (`pickaxe-veinminer`)

Break connected vanilla ore veins and clusters.

**Runtime entry points:** when breaking blocks; periodic evaluation every 8484 ms.

**Menu displays:** Sneak-mine ores; vein-mining range; drops remain separate.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `PickaxeVeinminer` |
| Icon | `IRON_PICKAXE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 6 |
| Cost factor | 0.95 |
| Tick interval (ms) | 8484 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-veinminer.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseRange` | `2` | Base range. Blocks. |
| `maxBlocks` | `64` | Maximum blocks mined by one Veinminer activation. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Autosmelt (`pickaxe-autosmelt`)

Automatically smelts supported vanilla ores when mined.

**Runtime entry points:** on `BlockDropItemEvent`; periodic evaluation every 7444 ms.

**Menu displays:** Ores that can be smelted are smelted automatically; % chance for an extra.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `PickaxeAutosmelt` |
| Icon | `RAW_GOLD` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 6 |
| Cost factor | 0.95 |
| Tick interval (ms) | 7444 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-autosmelt.toml` |

Listened events:

- `BlockDropItemEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Pickaxe Drop-To-Inventory (`pickaxe-drop-to-inventory`)

Blocks you break send their drops straight into your inventory.

**Runtime entry points:** on `BlockDropItemEvent`; periodic evaluation every 7944 ms.

**Menu displays:** Block drops go directly into the inventory when space is available.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `PickaxeDropToInventory` |
| Icon | `MINECART` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 1 |
| Cost factor | 1 |
| Tick interval (ms) | 7944 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-drop-to-inventory.toml` |

Listened events:

- `BlockDropItemEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Pickaxe Silk-Spawner (`pickaxe-silk-spawner`)

Allows spawners to drop when broken under the documented conditions.

**Runtime entry points:** on `BlockDropItemEvent`; periodic evaluation every 8444 ms.

**Menu displays:** Spawners become breakable with Silk Touch while sneaking.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `PickaxeSilkSpawner` |
| Icon | `SPAWNER` |
| Max level | 2 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 6 |
| Cost factor | 0.95 |
| Tick interval (ms) | 8444 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-silk-spawner.toml` |

Listened events:

- `BlockDropItemEvent` (`onBlockDropPrepare`)
- `BlockDropItemEvent` (`onBlockDropCommit`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Quarry Sense (`pickaxe-quarry-sense`)

Sneak-right-click a block with an iron+ pickaxe to reveal nearby ores as private glowing block displays.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 1200 ms.

**Menu displays:** Ore Scan Radius; Durability Cost (% of Max Durability); Sense Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `PickaxeQuarrySense` |
| Icon | `MAP` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1200 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-quarry-sense.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `costsReduceMaxDurability` | `false` | When true, scan costs reduce maximum durability instead of current durability. |
| `scanRadiusBase` | `10` | Base Scan radius. Blocks. |
| `scanRadiusFactor` | `18` | Scan radius factor. Blocks. |
| `maxBlockChecks` | `2048` | Maximum world block checks made by one Quarry Sense activation. |
| `denseScanRadius` | `6` | Radius searched completely before the remaining Quarry Sense budget is spread across the full range. |
| `maxHighlightsBase` | `6` | Base Maximum highlights. |
| `maxHighlightsFactor` | `10` | Maximum highlights factor. Unitless multiplier. |
| `highlightTicksBase` | `90` | Base Highlight ticks. Server ticks (20 ticks = 1 second). |
| `highlightTicksFactor` | `90` | Highlight ticks factor. Server ticks (20 ticks = 1 second). |
| `cooldownTicksBase` | `60` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksFactor` | `40` | Cooldown ticks factor. Server ticks (20 ticks = 1 second). |
| `durabilityCostPercentBase` | `0.006` | Base fraction of maximum durability charged per scan. |
| `durabilityCostPercentFactor` | `0.0045` | Level-scaled reduction to the maximum-durability cost fraction. |
| `minDurabilityCostPercent` | `0.001` | Lower bound or activation threshold for min durability cost percent. durability points. |
| `xpPerFoundOre` | `6` | XP awarded for xp per found ore. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Tunnel Bore (`pickaxe-tunnel-bore`)

Sneak and mine stone-type blocks to bore out a whole tunnel face at once.

**Runtime entry points:** when breaking blocks; periodic evaluation every 8123 ms.

**Menu displays:** Sneak-mine stone; tunnel face bored per block; extra durability cost per bonus block.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `PickaxeTunnelBore` |
| Icon | `COBBLESTONE` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.6 |
| Tick interval (ms) | 8123 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-tunnel-bore.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `durabilityPerBonusBlock` | `1` | Extra pickaxe durability damage taken per bonus block bored. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Deep Core (`pickaxe-deep-core`)

Mining deepslate grants Haste so it digs like normal stone.

**Runtime entry points:** on `BlockDamageEvent`; when breaking blocks; periodic evaluation every 5825 ms.

**Menu displays:** Mine DEEPSLATE to gain Haste; Haste level while mining deepslate.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `PickaxeDeepCore` |
| Icon | `DEEPSLATE` |
| Max level | 3 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Tick interval (ms) | 5825 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-deep-core.toml` |

Listened events:

- `BlockDamageEvent` (`on`)
- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `amplifierBase` | `2` | Haste-equivalent amplifier granted at level 1 while mining deepslate. |
| `maxAmplifier` | `5` | Maximum Haste-equivalent amplifier this adaptation can grant. |
| `durationTicks` | `60` | Duration in ticks of the mining-speed boost applied when damaging deepslate. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Obsidian Rush (`pickaxe-obsidian-rush`)

Mining obsidian with a diamond or netherite pickaxe grants a strong Haste burst.

**Runtime entry points:** on `BlockDamageEvent`; when breaking blocks; periodic evaluation every 6233 ms.

**Menu displays:** Mine OBSIDIAN with a diamond+ pickaxe; Haste level while mining obsidian; Also works on crying obsidian.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `PickaxeObsidianRush` |
| Icon | `CRYING_OBSIDIAN` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.55 |
| Tick interval (ms) | 6233 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-obsidian-rush.toml` |

Listened events:

- `BlockDamageEvent` (`on`)
- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `amplifierBase` | `3` | Haste-equivalent amplifier added on top of the adaptation level while mining obsidian; each amplifier step adds 20% block break speed. |
| `maxAmplifier` | `7` | Maximum haste-equivalent amplifier this adaptation can grant. |
| `durationTicks` | `120` | Duration in ticks of the mining speed burst applied when damaging obsidian. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Unbreakable Pact (`pickaxe-unbreakable-pact`)

Your pickaxe refuses to break, surviving at 1 durability instead.

**Runtime entry points:** when held item takes durability damage; periodic evaluation every 9122 ms.

**Menu displays:** Pickaxes never break, stopping at 1 durability; chance to ignore durability loss entirely.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `PickaxeUnbreakablePact` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 6 |
| Cost factor | 0.65 |
| Tick interval (ms) | 9122 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-unbreakable-pact.toml` |

Listened events:

- `PlayerItemDamageEvent` (`on`) — when held item takes durability damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `ignoreChancePerLevel` | `0.04` | Chance per level to ignore pickaxe durability loss entirely. |
| `maxIgnoreChance` | `0.25` | Maximum total chance to ignore durability loss. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Repair Rhythm (`pickaxe-repair-rhythm`)

Sustained mining has a chance to restore durability to your pickaxe.

**Runtime entry points:** when breaking blocks; periodic evaluation every 7561 ms.

**Menu displays:** Each broken block can restore 1-2 durability; chance to repair per broken block.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `PickaxeRepairRhythm` |
| Icon | `EXPERIENCE_BOTTLE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.6 |
| Tick interval (ms) | 7561 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-repair-rhythm.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `chanceBase` | `0.05` | Base chance per broken block to restore durability. |
| `chancePerLevel` | `0.06` | Additional repair chance gained per adaptation level. |
| `maxChance` | `0.5` | Maximum total repair chance per broken block. |
| `restoreMin` | `1` | Minimum durability restored per repair proc. |
| `restoreMax` | `2` | Maximum durability restored per repair proc. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Gem Polish (`pickaxe-gem-polish`)

Mining gem ores grants bonus XP orbs and a chance for an extra gem.

**Runtime entry points:** when breaking blocks; periodic evaluation every 6844 ms.

**Menu displays:** Mine diamond, emerald, lapis or amethyst; chance for an extra matching gem; bonus XP per gem ore mined.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `PickaxeGemPolish` |
| Icon | `DIAMOND` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 6 |
| Cost factor | 0.7 |
| Tick interval (ms) | 6844 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-gem-polish.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `preventSilkTouchDoubleDip` | `true` | Skips all bonuses when the pickaxe has Silk Touch. |
| `gemChanceBase` | `0.04` | Base chance for an extra gem drop when mining a gem ore. |
| `gemChancePerLevel` | `0.05` | Additional extra-gem chance gained per adaptation level. |
| `maxGemChance` | `0.4` | Maximum total extra-gem chance. |
| `bonusXpBase` | `1` | Base bonus XP orb value granted per mined gem ore. |
| `bonusXpPerLevel` | `2` | Additional bonus XP orb value gained per adaptation level. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Stone Skin (`pickaxe-stone-skin`)

Breaking stone-type blocks builds short-lived stacking damage resistance.

**Runtime entry points:** when breaking blocks; periodic evaluation every 5377 ms.

**Menu displays:** Mine stone to build Stone Skin stacks; maximum Resistance level.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `PickaxeStoneSkin` |
| Icon | `STONE` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.55 |
| Tick interval (ms) | 5377 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-stone-skin.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `blocksPerStack` | `4` | Stone blocks that must be broken to gain one resistance tier. |
| `stackDurationMs` | `6000` | Milliseconds before built stacks expire without mining. |
| `effectDurationTicks` | `80` | Duration in ticks of the applied resistance effect. |
| `maxAmplifier` | `3` | Maximum resistance amplifier this adaptation can reach. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
