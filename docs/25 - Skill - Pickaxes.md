# Skill: Pickaxes

Pickaxes is the mining skill. You level it by breaking stone and ore with a pickaxe in your main hand, and it pays back with adaptations that dig faster, drop more, and keep the tool alive. The skill id is `pickaxe`, it shows up in the menu with a netherite pickaxe icon, and it carries 13 adaptations.

Most of what Pickaxes gives you is quiet. Autosmelt turns raw ore into ingots as it drops. Drop-To-Inventory keeps the floor clean. Repair Rhythm and Unbreakable Pact mean a good pickaxe lasts far longer than it should. Deep Core and Obsidian Rush make the two worst blocks in the game feel like stone.

The loud ones need you to do something. Sneak while you mine and Veinminer chases the whole ore vein, or Tunnel Bore cuts a full tunnel face in one swing. Sneak-right-click with an iron or better pickaxe and Quarry Sense paints nearby ore as glowing outlines only you can see. Right-click an exposed ore and Ore Chisel knocks extra material out of it, at a real cost in durability.

Pickaxes also feeds the combat side a little: hitting mobs with a pickaxe counts, and there are challenge milestones for blocks broken, ores mined, block value, and damage dealt.

## Earning XP

Breaking a block with a pickaxe awards XP based on what the block was. Each block starts from its configured material value, then picks up its hardness and blast resistance (both capped), then an ore bonus if it is an ore. Deepslate ore variants get their bonus multiplied. The whole thing is then scaled down by a fixed factor, so the numbers in the config are relative weights rather than raw XP.

Silk Touch skips all of that and pays a flat 5 XP, since the block you get back is worth mining again. Blocks that the anti-farm system has already devalued (placed blocks, repeatedly farmed areas) pay nothing.

Hitting a valid mob with a pickaxe awards XP scaled from the damage dealt, and counts toward the `pickaxe.damage` challenges. Both XP paths share one cooldown, so spamming breaks or hits faster than that window does not multiply your income.

## Adaptations

Everything below only runs when you have learned the adaptation (level 1 or higher), the skill and the adaptation are both enabled, you are not in a blacklisted world or a blocked game mode, you hold the `adapt.use.<adaptation>` permission, and the protection plugins and region policy allow the block action. See `08 - Protection & Region Policy.md` and `04 - Commands & Permissions.md`. Learn and level everything from the Adapt menu (`/adapt`).

### Ore Chisel (`pickaxe-chisel`)

Chisel lets you work an exposed ore without mining it out, popping loose extra material for heavy tool wear. There is also a flat chance the ore block gives up and breaks normally, so you never fully waste the swing. It refuses to work with Silk Touch or Mending on the pickaxe, because both would trivialize it.

1. Hold a pickaxe with no Silk Touch and no Mending in your main hand.
2. Right-click a vanilla ore block. Right-clicking air works too, targeting whatever ore you are looking at within 5 blocks, except on Folia where only a direct block click counts.
3. Wait out the short item cooldown before the next chisel.

Each chisel costs durability (worst at low levels, cheapest at max level) and rolls two separate chances: one for the bonus drop, one for the block breaking outright.

### Veinminer (`pickaxe-veinminer`)

Veinminer follows an ore vein instead of making you chase it block by block. It groups deepslate variants with their normal counterparts, so a mixed deepslate and stone iron vein still counts as one vein, and it also works on obsidian and ancient debris.

1. Learn it, then hold a pickaxe.
2. Sneak.
3. Break any ore, obsidian, or ancient debris. Connected blocks of the same family within range break with it.

Every sibling block goes through your normal break, so drops, enchantments, and other adaptations like Autosmelt apply to each one individually. Drops are not merged into a single stack. If HiddenOre is installed, its hidden veins chain the same way.

### Autosmelt (`pickaxe-autosmelt`)

Iron, gold, and copper ore come out of the ground as ingots instead of raw chunks. Higher levels add a small chance of one extra ingot on top. Silk Touch turns it off, and the pickaxe has to be the correct tool for the block. It works on its own once learned.

### Pickaxe Drop-To-Inventory (`pickaxe-drop-to-inventory`)

Blocks you break with a pickaxe put their drops straight into your inventory instead of on the ground. Anything that does not fit falls at your feet. It works on its own once learned.

### Pickaxe Silk-Spawner (`pickaxe-silk-spawner`)

Spawners drop as spawners, keeping the mob type they were set to. The pickaxe has to be the right tool for the block.

1. At level 1, break the spawner with a Silk Touch pickaxe.
2. At level 2, sneaking while you break it is enough, no Silk Touch needed.

If anything cancels the drop event afterward, the spawner item is removed again and you get a puff of smoke instead.

### Quarry Sense (`pickaxe-quarry-sense`)

Quarry Sense is an ore scan. It marks nearby ore with glowing block outlines that only you can see, colored per ore type, and pays Pickaxes XP for every ore it finds. It costs a slice of your pickaxe's durability per scan and puts the pickaxe on a short cooldown.

1. Learn it and hold an iron, diamond, or netherite pickaxe.
2. Sneak and right-click. The right-click is consumed, so the block you clicked does not activate.
3. Wait for the scan to finish, then follow the outlines before they fade.

The scan is budgeted rather than exhaustive: it searches a close-in radius fully, then spreads the remaining samples across the whole range, so it finds ore reliably up close and opportunistically far out. Only one scan can run at a time per player. Leveling widens the radius, shows more markers, keeps them up longer, and lowers both the cooldown and the durability cost. Hidden ore veins from HiddenOre are included.

### Tunnel Bore (`pickaxe-tunnel-bore`)

Tunnel Bore turns stone digging into tunnel digging. Break one stone-type block while sneaking and the surrounding face goes with it, oriented by the direction you are facing. Looking sharply up or down flips the face flat so you can sink or raise a shaft.

1. Learn it and hold a pickaxe.
2. Sneak.
3. Mine stone, cobblestone, deepslate, tuff, calcite, andesite, diorite, or granite. The bonus face breaks one tick later.

The face is 1 wide by 2 tall at level 1, 3 by 2 at level 2, and 3 by 3 at level 3. Only bore-eligible blocks in that face are taken, and each bonus block costs extra durability.

### Deep Core (`pickaxe-deep-core`)

Deepslate normally digs about twice as slow as stone. Deep Core gives you a block break speed bonus the moment you start hitting any deepslate block, refreshed on every hit, so the deep world stops feeling like a wall. It works on its own once learned, as long as you are holding a pickaxe.

### Obsidian Rush (`pickaxe-obsidian-rush`)

Same idea as Deep Core but aimed at obsidian, and much stronger. Start hitting obsidian or crying obsidian with a diamond or netherite pickaxe and you get a big break speed burst that lasts a few seconds past each hit. Lesser pickaxes get nothing. It works on its own once learned.

### Unbreakable Pact (`pickaxe-unbreakable-pact`)

Your pickaxe stops at 1 durability instead of shattering. On top of that, each durability hit has a chance to be ignored completely, which scales with level up to a cap. It works on its own once learned.

### Repair Rhythm (`pickaxe-repair-rhythm`)

Every block you break with a pickaxe has a chance to give a point or two of durability back. It only triggers on a damaged tool, and it stacks well with Unbreakable Pact for a pickaxe that basically maintains itself. It works on its own once learned.

### Gem Polish (`pickaxe-gem-polish`)

Mining diamond, emerald, or lapis ore, or an amethyst cluster, drops a bonus XP orb and has a chance to drop one extra matching gem. By default Silk Touch turns the whole thing off so you cannot silk the ore, place it, and mine it again for doubled rewards. It works on its own once learned.

### Stone Skin (`pickaxe-stone-skin`)

Mining stone builds stacks that turn into Resistance. Every few stone blocks adds a tier, each break refreshes the effect, and the stacks decay if you stop mining. It is a caving survival tool: by the time something ambushes you in a tunnel, you are already armored. It works on its own once learned.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `pickaxe` |
| Class | `SkillPickaxes` |
| Icon | `NETHERITE_PICKAXE` |
| Color | `GOLD` |
| Interval (ms) | `2750` |
| Skill config | `plugins/Adapt/adapt/skills/pickaxe.toml` |
| Adaptation count | 13 |

Block break XP formula, from `SkillPickaxes.getValue`: `(materialValue * blockValueMultiplier + min(maxHardnessBonus, hardness) + min(maxBlastResistanceBonus, blastResistance) + oreBonus) * 0.48`, then multiplied by the world anti-farm multiplier. Deepslate ore variants use their base ore bonus times `deepslateMultiplier`.

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/pickaxe.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Turns the whole Pickaxes skill off when false. |
| `skillColor` | `"&6"` | Legacy ampersand color code used for this skill in menus and text. |
| `getXpForAttackingWithTools` | `true` | When false, hitting mobs with a pickaxe grants no Pickaxes XP. |
| `damageXPMultiplier` | `6.0` | XP granted per point of damage dealt with a pickaxe. |
| `blockValueMultiplier` | `0.125` | Scales the configured material value before hardness and ore bonuses are added. |
| `maxHardnessBonus` | `9` | Cap on the block hardness added to a block's mining value. |
| `maxBlastResistanceBonus` | `10` | Cap on the block blast resistance added to a block's mining value. |
| `coalBonus` | `18` | Value added for coal ore. |
| `copperBonus` | `22` | Value added for copper ore. |
| `ironBonus` | `30` | Value added for iron ore. |
| `goldBonus` | `38` | Value added for gold ore. |
| `redstoneBonus` | `55` | Value added for redstone ore. |
| `lapisBonus` | `75` | Value added for lapis ore. |
| `netherGoldBonus` | `105` | Value added for Nether gold ore. |
| `netherQuartzBonus` | `125` | Value added for Nether quartz ore. |
| `diamondBonus` | `175` | Value added for diamond ore. |
| `emeraldBonus` | `210` | Value added for emerald ore, and the base unit for every Pickaxes milestone reward. |
| `debrisBonus` | `210` | Value added for ancient debris. |
| `deepslateMultiplier` | `1.35` | Multiplier applied to the ore bonus of deepslate ore variants. |
| `cooldownDelay` | `1250` | Milliseconds between XP awards from mining or pickaxe damage. |

### Skill milestones

| Advancement key | Stat key | Threshold | XP reward |
|-----------------|----------|-----------|-----------|
| `challenge_pickaxe_1k` | `pickaxe.blocks.broken` | 1000 | `emeraldBonus` x 2 |
| `challenge_pickaxe_5k` | `pickaxe.blocks.broken` | 5000 | `emeraldBonus` x 5 |
| `challenge_pickaxe_50k` | `pickaxe.blocks.broken` | 50000 | `emeraldBonus` x 10 |
| `challenge_pick_damage_1k` | `pickaxe.damage` | 1000 | `emeraldBonus` |
| `challenge_pick_damage_10k` | `pickaxe.damage` | 10000 | `emeraldBonus` x 2 |
| `challenge_pick_value_5k` | `pickaxe.blocks.value` | 5000 | `emeraldBonus` |
| `challenge_pick_value_50k` | `pickaxe.blocks.value` | 50000 | `emeraldBonus` x 2 |
| `challenge_pick_ores_500` | `pickaxe.ores` | 500 | `emeraldBonus` |
| `challenge_pick_ores_5k` | `pickaxe.ores` | 5000 | `emeraldBonus` x 2 |

`pickaxe.blocks.broken` counts every block broken with a pickaxe, `pickaxe.blocks.value` sums their computed values, and `pickaxe.ores` counts blocks whose material name contains `_ORE`.

### Shared adaptation keys

Every adaptation TOML at `plugins/Adapt/adapt/adaptations/<id>.toml` carries `enabled`, `permanent`, `showParticles`, `showSounds`, plus the cost fields `baseCost`, `costFactor`, `maxLevel`, and `initialCost` listed per adaptation below.

### Ore Chisel

| Property | Value |
|----------|-------|
| Class | `PickaxeChisel` |
| Icon | `IRON_NUGGET` |
| Max level | 7 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 6 |
| Cost factor | 0.4 |
| Tick interval (ms) | 7433 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-chisel.toml` |
| Listened events | `PlayerInteractEvent` (`on`, MONITOR) |
| Stats | `pickaxe.chisel.extra-ores` |
| Milestone | `challenge_pickaxe_chisel_500` at 500 extra ores, 400 XP |
| Menu lore | Chance to Drop; Tool Wear |

Chiselable ores and their drops: coal ore to coal, copper ore to raw copper, gold and Nether gold ore to raw gold, iron ore to raw iron, diamond ore to diamond, lapis ore to lapis lazuli, emerald ore to emerald, Nether quartz ore to quartz, redstone ore to redstone. Deepslate variants use the same drop.

The ore must pass a cancellable block-break probe before Chisel applies its cooldown, tool wear, bonus drop, stats, effects, or the possible block break. On Folia the click must land directly on a block, and the effects are centered on that block instead of on a second ray trace.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldownTime` | `5` | Item cooldown put on the held pickaxe after a chisel, in ticks. |
| `dropChanceBase` | `0.07` | Extra-ore chance before the level bonus, 0-1. |
| `dropChanceFactor` | `0.22` | Extra-ore chance added at max level, scaled by level progress, 0-1. |
| `breakChance` | `0.25` | Chance the chiselled block breaks normally, 0-1. Level does not change it. |
| `damagePerBlockBase` | `1` | Durability charged on every chisel. |
| `damageFactorInverseMultiplier` | `2` | Extra durability charged at level 1, falling to 0 at max level. |

### Veinminer

| Property | Value |
|----------|-------|
| Class | `PickaxeVeinminer` |
| Icon | `IRON_PICKAXE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 6 |
| Cost factor | 0.95 |
| Tick interval (ms) | 8484 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-veinminer.toml` |
| Listened events | `BlockBreakEvent` (`on`, HIGH) |
| Stats | `pickaxe.veinminer.ores-veinmined` |
| Milestones | `challenge_pickaxe_veinminer_2500` at 2500 blocks, 500 XP; `challenge_pickaxe_veinminer_20` granted when one vein yields 20 or more blocks |
| Menu lore | Sneak, and mine ORES; range of vein-mining; This skill does NOT group all drops together! |

Vein search radius is `level + baseRange`. Eligible blocks are any material ending in `_ORE`, plus `OBSIDIAN` and `ANCIENT_DEBRIS`. `DEEPSLATE_*_ORE` is treated as the same family as its base ore.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseRange` | `2` | Blocks added to the vein search radius. Radius is level plus this value. |
| `maxBlocks` | `64` | Cap on blocks collected by one vein, counting the block you broke. |

### Autosmelt

| Property | Value |
|----------|-------|
| Class | `PickaxeAutosmelt` |
| Icon | `RAW_GOLD` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 6 |
| Cost factor | 0.95 |
| Tick interval (ms) | 7444 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-autosmelt.toml` |
| Listened events | `BlockDropItemEvent` (`on`, MONITOR) |
| Stats | `pickaxe.autosmelt.ores-smelted` |
| Milestones | `challenge_pickaxe_autosmelt_1k` at 1000 ores, 400 XP; `challenge_pickaxe_autosmelt_25k` at 25000 ores, 1500 XP |
| Menu lore | Ores that can be smelted are smelted automatically; % chance for an extra |

Converted ores: iron ore to iron ingot, gold ore to gold ingot, copper ore to copper ingot, including deepslate variants. Extra-drop chance is `level * 1.25%`, hardcoded and not configurable.

### Pickaxe Drop-To-Inventory

| Property | Value |
|----------|-------|
| Class | `PickaxeDropToInventory` |
| Icon | `MINECART` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 1 |
| Cost factor | 1 |
| Tick interval (ms) | 7944 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-drop-to-inventory.toml` |
| Listened events | `BlockDropItemEvent` (`on`, MONITOR) |
| Stats | `pickaxe.drop-to-inv.items-caught` |
| Milestone | `challenge_pickaxe_dti_25k` at 25000 items, 500 XP |
| Menu lore | Whenever an item is dropped from a block you break it goes into your inventory if it can. |

Each drop must pass a simulated pickup event before it is pulled out of the block's drop list. A denied pickup stays on the normal world-drop path. Overflow that does not fit is dropped at the player's feet.

### Pickaxe Silk-Spawner

| Property | Value |
|----------|-------|
| Class | `PickaxeSilkSpawner` |
| Icon | `SPAWNER` |
| Max level | 2 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 6 |
| Cost factor | 0.95 |
| Tick interval (ms) | 8444 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-silk-spawner.toml` |
| Listened events | `BlockDropItemEvent` (`onBlockDropPrepare`, HIGH); `BlockDropItemEvent` (`onBlockDropCommit`, MONITOR) |
| Stats | `pickaxe.silk-spawner.spawners-collected` |
| Milestones | `challenge_pickaxe_spawner_10` at 10 spawners, 500 XP; `challenge_pickaxe_spawner_50` at 50 spawners, 2000 XP |
| Menu lore | Level 1: Makes Spawners breakable with silk touch. Level 2+: Makes Spawners breakable while sneaking. |

Gate in code: a Silk Touch pickaxe works at any level; without Silk Touch the level must be 2 or higher and the player must be sneaking. The dropped item carries the spawner's block state, so the mob type is preserved.

### Quarry Sense

| Property | Value |
|----------|-------|
| Class | `PickaxeQuarrySense` |
| Icon | `MAP` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1200 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-quarry-sense.toml` |
| Listened events | `PlayerInteractEvent` (`on`, HIGHEST); `PlayerQuitEvent` (`on`) |
| Stats | `pickaxe.quarry-sense.scans` |
| Milestone | `challenge_pickaxe_quarry_200` at 200 scans, 300 XP |
| Menu lore | Ore Scan Radius; Durability Cost (% of Max Durability); Sense Cooldown |

Hard caps in code, applied after the config values: scan radius clamped to 4-32 blocks, block samples clamped to 4096, markers clamped to 16, marker lifetime at least 20 ticks, cooldown at least 10 ticks, durability cost at least 1 point. Only iron, diamond, and netherite pickaxes qualify. Quitting cancels any running scan and clears that player's markers.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `costsReduceMaxDurability` | `false` | When true, a scan lowers the pickaxe's maximum durability instead of damaging it. |
| `scanRadiusBase` | `10` | Scan radius in blocks before the level bonus. |
| `scanRadiusFactor` | `18` | Blocks added to the scan radius at max level. |
| `maxBlockChecks` | `2048` | World block samples budgeted per scan. |
| `denseScanRadius` | `6` | Radius in blocks searched exhaustively before the remaining samples spread over the full radius. |
| `maxHighlightsBase` | `6` | Ore markers shown before the level bonus. |
| `maxHighlightsFactor` | `10` | Extra markers at max level. |
| `highlightTicksBase` | `90` | Marker lifetime in ticks before the level bonus. |
| `highlightTicksFactor` | `90` | Extra marker ticks at max level. |
| `cooldownTicksBase` | `60` | Pickaxe cooldown in ticks before the level reduction. |
| `cooldownTicksFactor` | `40` | Cooldown ticks removed at max level. |
| `durabilityCostPercentBase` | `0.006` | Fraction of max durability charged per scan before the level reduction, 0-1. |
| `durabilityCostPercentFactor` | `0.0045` | Fraction subtracted from the scan cost at max level. |
| `minDurabilityCostPercent` | `0.001` | Floor for the scan cost fraction, 0-1. |
| `xpPerFoundOre` | `6` | Pickaxes XP granted per ore revealed by a successful scan. |

### Tunnel Bore

| Property | Value |
|----------|-------|
| Class | `PickaxeTunnelBore` |
| Icon | `COBBLESTONE` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.6 |
| Tick interval (ms) | 8123 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-tunnel-bore.toml` |
| Listened events | `BlockBreakEvent` (`on`, HIGHEST) |
| Stats | `pickaxe.tunnel-bore.blocks-bored` |
| Milestone | `challenge_pickaxe_tunnelbore_10k` at 10000 blocks, 500 XP |
| Menu lore | Sneak, and mine STONE; tunnel face bored per block; extra durability per bonus block |

Bore face: 1x2 at level 1, 3x2 at level 2, 3x3 at level 3. Eligible block types: `STONE`, `COBBLESTONE`, `MOSSY_COBBLESTONE`, `DEEPSLATE`, `COBBLED_DEEPSLATE`, `TUFF`, `CALCITE`, `ANDESITE`, `DIORITE`, `GRANITE`. The face is vertical and perpendicular to your facing, or horizontal when your pitch is beyond 60 degrees up or down. The bonus face runs one tick after the original break and each bonus block is revalidated and probed for protection immediately before removal.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `durabilityPerBonusBlock` | `1` | Durability charged per bonus block broken, on top of the normal break. |

### Deep Core

| Property | Value |
|----------|-------|
| Class | `PickaxeDeepCore` |
| Icon | `DEEPSLATE` |
| Max level | 3 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Tick interval (ms) | 5825 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-deep-core.toml` |
| Listened events | `BlockDamageEvent` (`on`, HIGHEST); `BlockBreakEvent` (`on`, MONITOR) |
| Stats | `pickaxe.deep-core.deepslate-mined` |
| Milestone | `challenge_pickaxe_deepcore_5k` at 5000 blocks, 400 XP |
| Menu lore | Mine DEEPSLATE to gain Haste; Haste level while mining deepslate |

The bonus is a timed `BLOCK_BREAK_SPEED` attribute modifier, not a Haste potion effect. Amplifier is `min(maxAmplifier, amplifierBase + level - 1)` and the speed bonus is `20% * (amplifier + 1)`, so +60% at level 1 and +100% at level 3 with the defaults. Trigger blocks: `DEEPSLATE`, `COBBLED_DEEPSLATE`, `POLISHED_DEEPSLATE`, `DEEPSLATE_BRICKS`, `DEEPSLATE_TILES`, and every `DEEPSLATE_*_ORE`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `amplifierBase` | `2` | Amplifier at level 1. Speed bonus is 20% per amplifier step plus 20%. |
| `maxAmplifier` | `5` | Cap on the amplifier, worth +120% break speed. |
| `durationTicks` | `60` | How long the speed bonus lasts after each hit on deepslate, in ticks. |

### Obsidian Rush

| Property | Value |
|----------|-------|
| Class | `PickaxeObsidianRush` |
| Icon | `CRYING_OBSIDIAN` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.55 |
| Tick interval (ms) | 6233 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-obsidian-rush.toml` |
| Listened events | `BlockDamageEvent` (`on`, HIGHEST); `BlockBreakEvent` (`on`, MONITOR) |
| Stats | `pickaxe.obsidian-rush.obsidian-mined` |
| Milestone | `challenge_pickaxe_obsidianrush_1k` at 1000 blocks, 500 XP |
| Menu lore | Mine OBSIDIAN with a diamond+ pickaxe; Haste level while mining obsidian; Also works on crying obsidian! |

Also a timed `BLOCK_BREAK_SPEED` modifier. Amplifier is `min(maxAmplifier, amplifierBase + level)` and the bonus is `20% * (amplifier + 1)`, so +100% at level 1 and +140% at level 3 with the defaults. Only `DIAMOND_PICKAXE` and `NETHERITE_PICKAXE` qualify; targets are `OBSIDIAN` and `CRYING_OBSIDIAN`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `amplifierBase` | `3` | Amplifier added on top of the adaptation level. Speed bonus is 20% per amplifier step plus 20%. |
| `maxAmplifier` | `7` | Cap on the amplifier, worth +160% break speed. |
| `durationTicks` | `120` | How long the burst lasts after each hit on obsidian, in ticks. |

### Unbreakable Pact

| Property | Value |
|----------|-------|
| Class | `PickaxeUnbreakablePact` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 6 |
| Cost factor | 0.65 |
| Tick interval (ms) | 9122 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-unbreakable-pact.toml` |
| Listened events | `PlayerItemDamageEvent` (`on`, HIGHEST) |
| Stats | `pickaxe.unbreakable-pact.damage-ignored`, `pickaxe.unbreakable-pact.saves` |
| Milestone | `challenge_pickaxe_pact_100` at 100 saves, 400 XP |
| Menu lore | Pickaxes never break, stopping at 1 durability; chance to ignore durability loss entirely |

Ignore chance is `min(maxIgnoreChance, level * ignoreChancePerLevel)`. When the roll fails and the incoming damage would destroy the pickaxe, the event is cancelled and the item's damage is clamped to one point below its maximum.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `ignoreChancePerLevel` | `0.04` | Chance per level to cancel a durability hit outright, 0-1. |
| `maxIgnoreChance` | `0.25` | Cap on the ignore chance, 0-1. |

### Repair Rhythm

| Property | Value |
|----------|-------|
| Class | `PickaxeRepairRhythm` |
| Icon | `EXPERIENCE_BOTTLE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.6 |
| Tick interval (ms) | 7561 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-repair-rhythm.toml` |
| Listened events | `BlockBreakEvent` (`on`, NORMAL) |
| Stats | `pickaxe.repair-rhythm.durability-restored` |
| Milestone | `challenge_pickaxe_rhythm_5k` at 5000 durability, 500 XP |
| Menu lore | Each broken block can restore 1-2 durability; chance to repair per broken block |

Repair chance is `min(maxChance, chanceBase + level * chancePerLevel)`. It fires on any block broken with a pickaxe, not only stone, and does nothing if the tool is already at full durability.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `chanceBase` | `0.05` | Repair chance before the level bonus, 0-1. |
| `chancePerLevel` | `0.06` | Repair chance added per level, 0-1. |
| `maxChance` | `0.5` | Cap on the repair chance, 0-1. |
| `restoreMin` | `1` | Fewest durability points restored per proc. |
| `restoreMax` | `2` | Most durability points restored per proc. |

### Gem Polish

| Property | Value |
|----------|-------|
| Class | `PickaxeGemPolish` |
| Icon | `DIAMOND` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 6 |
| Cost factor | 0.7 |
| Tick interval (ms) | 6844 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-gem-polish.toml` |
| Listened events | `BlockBreakEvent` (`on`, HIGH) |
| Stats | `pickaxe.gem-polish.gems-polished` |
| Milestone | `challenge_pickaxe_gempolish_500` at 500 gems, 400 XP |
| Menu lore | Mine diamond, emerald, lapis or amethyst; chance for an extra matching gem; bonus XP per gem ore mined |

Triggers: diamond ore to diamond, emerald ore to emerald, lapis ore to lapis lazuli (all including deepslate variants), and amethyst cluster to amethyst shard. The bonus XP orb is worth `bonusXpBase + level * bonusXpPerLevel` and spawns on every qualifying break; the extra gem rolls `min(maxGemChance, gemChanceBase + level * gemChancePerLevel)`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `preventSilkTouchDoubleDip` | `true` | Skips the XP orb and the extra gem when the pickaxe has Silk Touch. |
| `gemChanceBase` | `0.04` | Extra-gem chance before the level bonus, 0-1. |
| `gemChancePerLevel` | `0.05` | Extra-gem chance added per level, 0-1. |
| `maxGemChance` | `0.4` | Cap on the extra-gem chance, 0-1. |
| `bonusXpBase` | `1` | Vanilla XP in the bonus orb before the level bonus. |
| `bonusXpPerLevel` | `2` | Vanilla XP added to the bonus orb per level. |

### Stone Skin

| Property | Value |
|----------|-------|
| Class | `PickaxeStoneSkin` |
| Icon | `STONE` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.55 |
| Tick interval (ms) | 5377 |
| Config file | `plugins/Adapt/adapt/adaptations/pickaxe-stone-skin.toml` |
| Listened events | `BlockBreakEvent` (`on`, HIGHEST) |
| Stats | `pickaxe.stone-skin.stacks-gained` |
| Milestone | `challenge_pickaxe_stoneskin_10k` at 10000 stacks, 500 XP |
| Menu lore | Mine stone to build Stone Skin stacks; maximum Resistance level |

Trigger blocks are the same list Tunnel Bore uses. One stack per qualifying break; the Resistance amplifier is `stacks / blocksPerStack`, capped at `min(level, maxAmplifier + 1)` tiers, so 4 levels of the adaptation reach Resistance IV after 16 stone blocks. Every break reapplies the effect for `effectDurationTicks`, and stacks reset if you go longer than `stackDurationMs` without a qualifying break.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `blocksPerStack` | `4` | Stone blocks broken per Resistance tier. |
| `stackDurationMs` | `6000` | Milliseconds of no mining before built stacks reset. |
| `effectDurationTicks` | `80` | Resistance duration reapplied on each qualifying break, in ticks. |
| `maxAmplifier` | `3` | Cap on the Resistance amplifier, worth Resistance IV. |

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
