# Skill: Axes

Axes is the woodcutting and axe-combat skill. You level it by breaking logs, wood, mushroom blocks and mangrove roots with an axe in your main hand, and by hitting things with an axe. It is the skill you live in if you spend your time clearing forests, and it doubles as a real melee tree for players who fight with an axe instead of a sword.

The woodcutting half is about doing more per swing. Axe Chop takes a stack of logs off a tree with a single right click. Wood-miner and Leaf-miner pull whole clusters of matching blocks in one break while you sneak. Drop-To-Inventory keeps the results out of the dirt. Lucy's Log-Swapper lets you trade one wood type for another at a crafting table so you are not hunting a biome for the color you want.

The combat half is a different feel. Throwing Axe turns your axe into a projectile that comes back to you once you max the adaptation. Cleave splashes damage into a cone. Sunder peels armor off whatever you keep hitting, Shield Splitter punishes anyone hiding behind a shield, and Bark Hide gives you absorption hearts just for working.

Eleven adaptations always register. A twelfth, Iris Feller, registers only when Iris is installed, because it hands the whole tree over to the Iris tree-feller instead of breaking blocks itself.

## Earning XP

Two things pay out. Breaking a log, wood, mushroom block, mangrove roots or muddy mangrove roots block with an axe pays XP based on that block's material value, plus its hardness and blast resistance up to the configured caps. Damaging a living entity while holding an axe pays XP scaled off the damage you dealt.

Both share one cooldown, so rapid-fire breaks and hits do not each pay out. Blocks with zero hardness are worth nothing, and blocks that Adapt's XP provenance system has already paid for do not pay again.

Breaking leaves with an axe only bumps the `axes.leaves` stat, which drives the leaf challenges. Leaves are not log-type blocks, so they never reach the XP branch, and `leavesMultiplier` has no effect on what you actually earn with the shipped code.

## Adaptations

All of these need the same things before they do anything: the adaptation learned at level 1 or higher, the Axes skill and the adaptation both enabled, the player holding the matching `adapt.use.` permission (or the `adapt.use.*` wildcard), and any protection or region plugin on the server allowing the action at that spot. Anything that breaks blocks routes each extra break through your own break action, so blocks in a claim you cannot build in do not break.

### Axe Ground Smash (`axe-ground-smash`)

Jump with an axe out, crouch in the air, and hit the ground: everything living around you takes damage and gets launched. Damage and force fall off toward the edge of the radius, so the middle of the crowd takes the worst of it. It is the crowd-control button for an axe build.

How to use it:

1. Learn Axe Ground Smash in the Adapt menu.
2. Hold an axe in your main hand.
3. Jump.
4. Hold sneak while you are off the ground. This arms the smash.
5. Land while still sneaking and still holding the axe.

Releasing sneak, or landing after the arm expires, cancels it. Each smash starts a cooldown that shortens as you level.

### Axe Chop (`axe-chop`)

Right-click the bottom log of a tree and Adapt strips the topmost log off the column above the block you clicked. It repeats once per adaptation level, so a level 3 chop takes three logs per click. Every log costs the axe durability and puts a short cooldown on that item type, and the cooldown and wear both shrink as you level.

How to use it:

1. Learn Axe Chop.
2. Hold an axe in your main hand.
3. Right-click a log.

On Folia the click has to land directly on a block. An air click resolves its target by ray trace, and that ray can land in another region, so air clicks are ignored there.

### Axe Drop-To-Inventory (`axe-drop-to-inventory`)

Logs and leaves you break with an axe go straight into your inventory instead of landing on the ground. Each item still runs through the normal pickup path, so anything a protection plugin blocks stays where it fell. If your inventory fills up mid-break, the overflow drops at your feet and you get a fail sound.

Works on its own once learned. No gesture, no cooldown.

### Leaf-miner (`axe-leaf-veinminer`)

Sneak and break a leaf block with an axe, and every connected leaf of the same type inside your range goes with it. Range is your level plus the base range, and the chain stops at the block cap. Mangrove roots and muddy mangrove roots count as leaves here.

How to use it:

1. Learn Leaf-miner.
2. Hold an axe in your main hand.
3. Hold sneak.
4. Break a leaf block.

The chain runs one tick after the first leaf finishes breaking, and only if you are still holding an axe. Every extra leaf uses your own break action, so denied blocks stay put and do not count toward the stat. The extra leaves drop normally.

### Iris Feller (`axe-iris-feller`)

Only present when Iris is installed. Sneak-break a log that Iris recognizes as part of one of its trees and Iris erodes the whole tree outward for you. The run keeps going only while you keep sneaking and keep holding the same axe you started with, and only while you have hunger left to pay for the next log. Higher levels give a growing chance to skip the durability hit on each felled log.

How to use it:

1. Learn Iris Feller.
2. Hold an axe in your main hand.
3. Hold sneak.
4. Break a log that belongs to an Iris tree.
5. Keep sneaking and keep that axe held while the tree comes down.

Hunger is reserved before each log and only spent once that log actually comes out, so a refused break costs you nothing. Once Iris accepts the run, the activation cooldown starts.

### Wood-miner (`axe-wood-veinminer`)

Sneak and break a log or wood block with an axe, and every matching block inside your range goes with it. Planks are not logs, so a plank wall is safe. Range is your level plus the base range, capped at the block limit. It stacks with Drop-To-Inventory.

How to use it:

1. Learn Wood-miner.
2. Hold an axe in your main hand.
3. Hold sneak.
4. Break a log or wood block.

Like Leaf-miner, the chain runs a tick later, uses your own break action for every extra block, and skips anything you are not allowed to break.

### Lucy's Log-Swapper (`axe-logswap`)

Adds shapeless crafting recipes that convert wood types. Eight logs of one kind plus one sapling gives you eight logs of the sapling's tree. Handy when a build needs dark oak and you are standing in a birch forest.

How to use it:

1. Learn Lucy's Log-Swapper.
2. Open a crafting table.
3. Place eight logs of one type plus one sapling of the type you want.
4. Take the result.

Cherry and pale oak recipes register only when the server's Minecraft version has those materials. This adaptation ships with `permanent` set to `true`, which means once you learn it you cannot unlearn it and get the knowledge back.

### Throwing Axe (`axe-throwing-axe`)

Left-click the air and your axe leaves your hand as a spinning projectile that deals a fraction of its melee damage. It is a real throw: the axe comes out of your inventory. Below max level it lands on the ground where it hit, so you have to go pick it up. At max level it flies back to your hand instead.

How to use it:

1. Learn Throwing Axe.
2. Hold an axe in your main hand.
3. Left-click the air.

Each throw spends durability, starts a cooldown, and puts a matching item cooldown on that axe type. If the axe hits nothing, it is recovered automatically once its flight timer runs out. Left-clicking a block does not throw, and the swing that Minecraft emits right after an axe block break is filtered out so mining does not fling your tool.

### Sunder (`axe-sunder`)

Every axe hit strips armor and a share of armor toughness from the target, and the layers stack up to a cap. Each new hit refreshes the timer on the whole stack, so a target you keep working on gets softer and softer. Stop hitting and it wears off.

Works on its own once learned. Just hit things with an axe.

### Cleave (`axe-cleave`)

Your axe swings splash a share of the primary hit's damage onto other living things standing in a cone in front of you. The arc, reach and number of extra targets all grow with level. Each connect costs a point of axe durability.

Works on its own once learned. Armor stands are skipped, and a target that was just cleaved is not double-hit by the same swing.

### Bark Hide (`axe-bark-hide`)

Chopping logs with an axe layers on absorption hearts. Each log adds a stack, up to a level-scaled cap, and the stacks stick around for a grace period after your last chop. It turns a woodcutting trip into a small buffer of temporary health, which matters when a creeper finds you in the trees.

Works on its own once learned. Dying clears the ceiling, and your next chop refills it.

### Shield Splitter (`axe-shield-splitter`)

Hitting someone who is actively blocking deals bonus damage and puts their shield on a much longer cooldown than a vanilla axe would. It also works against mobs that raise a shield.

Works on its own once learned.

## Support classes

These are internal helpers, not player-facing adaptations.

- `AxeBlockBreakSwingGuard` suppresses the one synthetic air swing Minecraft emits immediately after an axe block break, so mining never triggers Throwing Axe.
- `AxeRecoveryJournal` persists thrown-axe recovery entries and validates them before restoring an item, so a shutdown mid-flight does not eat your axe.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `axes` |
| Class | `SkillAxes` |
| Icon | `GOLDEN_AXE` |
| Color | `YELLOW` |
| Interval (ms) | `5251` |
| Skill config | `plugins/Adapt/adapt/skills/axes.toml` |
| Adaptation count | 11 normally, 12 with Iris installed |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/axes.toml` on first load.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `enabled` | `true` | Turns the whole Axes skill off when false. |
| `skillColor` | `"&e"` | Legacy ampersand color code used for this skill in menus and text. |
| `getXpForAttackingWithTools` | `true` | When false, axe combat damage stops paying XP. Block breaking still pays. |
| `maxHardnessBonus` | `9` | Cap on how much of a block's hardness is added to its XP value. |
| `maxBlastResistanceBonus` | `10` | Cap on how much of a block's blast resistance is added to its XP value. |
| `challengeChopReward` | `1750` | XP paid by the Axes milestones. The larger tier of each pair pays double this. |
| `logOrWoodXPMultiplier` | `2.0` | Flat bonus added to the value of any `_LOG` or `_WOOD` block. |
| `leavesMultiplier` | `0.75` | Flat bonus that would be added to the value of any `_LEAVES` block. The Axes block-break path never asks for a leaf block's value, so this knob is inert. |
| `cooldownDelay` | `1500` | Milliseconds between XP awards from this skill, shared by breaking and combat. |
| `valueXPMultiplier` | `0.175` | Multiplier applied to the base material value before the hardness and resistance bonuses. |
| `axeDamageXPMultiplier` | `7.0` | XP per point of damage dealt with an axe. |

### Milestones and stat keys

| Milestone key | Stat key | Threshold |
|---------------|----------|-----------|
| `challenge_chop_1k` | `axes.blocks.broken` | 1000 |
| `challenge_chop_5k` | `axes.blocks.broken` | 5000 |
| `challenge_chop_50k` | `axes.blocks.broken` | 50000 |
| `challenge_axe_damage_1k` | `axes.damage` | 1000 |
| `challenge_axe_damage_10k` | `axes.damage` | 10000 |
| `challenge_axe_value_5k` | `axes.blocks.value` | 5000 |
| `challenge_axe_value_50k` | `axes.blocks.value` | 50000 |
| `challenge_leaves_500` | `axes.leaves` | 500 |
| `challenge_leaves_5k` | `axes.leaves` | 5000 |
| `challenge_axe_ground_smash_500` | `axe.ground-smash.mobs-hit` | 500 |
| `challenge_axe_chop_100` | `axe.chop.trees-felled` | 100 |
| `challenge_axe_chop_2500` | `axe.chop.trees-felled` | 2500 |
| `challenge_axe_dti_5k` | `axe.drop-to-inv.items-caught` | 5000 |
| `challenge_axe_leaf_5k` | `axe.leaf-veinminer.leaves-broken` | 5000 |
| `challenge_axe_wood_vein_2500` | `axe.wood-veinminer.logs-veinmined` | 2500 |
| `challenge_axe_log_swap_500` | `axe.log-swap.conversions` | 500 |
| `challenge_axe_throw_500` | `axe.throwing-axe.hits` | 500 |
| `challenge_axe_throw_5k` | `axe.throwing-axe.hits` | 5000 |
| `challenge_axe_sunder_500` | `axe.sunder.stacks-applied` | 500 |
| `challenge_axe_sunder_5k` | `axe.sunder.stacks-applied` | 5000 |
| `challenge_axe_cleave_1k` | `axe.cleave.targets-hit` | 1000 |
| `challenge_axe_cleave_10k` | `axe.cleave.targets-hit` | 10000 |
| `challenge_axe_bark_hide_2500` | `axe.bark-hide.stacks-gained` | 2500 |
| `challenge_axe_shield_splitter_250` | `axe.shield-splitter.shields-broken` | 250 |

Three advancements are granted directly instead of by a stat threshold: `challenge_axe_chop_one_swing` when one Axe Chop click fells at least `maxLevel` logs, `challenge_axe_wood_vein_cascade` when one Wood-miner break takes 15 or more logs, and `challenge_axe_ground_smash_5` when one smash hits 5 or more targets.

Other stats tracked but not tied to a milestone: `axe.throwing-axe.thrown`. The `axes.blocks.value` stat is recorded from the raw material value, not the skill-adjusted value used for XP.

### Shared adaptation config keys

Every adaptation TOML at `plugins/Adapt/adapt/adaptations/<id>.toml` carries these keys on top of its own.

| Key | Default | What it does |
|-----|---------|--------------|
| `enabled` | `true` | Turns this adaptation off when false. |
| `permanent` | `false` | When true, learning it is one-way and it cannot be unlearned. |
| `showParticles` | `true` | Plays this adaptation's particle effects. |
| `showSounds` | `true` | Plays this adaptation's sound effects. |
| `baseCost` | per adaptation | Knowledge cost per level past the first. |
| `costFactor` | per adaptation | Growth applied to level-to-level knowledge cost. |
| `maxLevel` | per adaptation | Highest level a player can buy. |
| `initialCost` | per adaptation | Knowledge cost of level 1. |

The tick interval below is the adaptation's background tick rate. Only Cleave actually does work on that tick (it clears expired cleave marks); for every other Axes adaptation the interval is idle bookkeeping.

### Axe Ground Smash

| Property | Value |
|----------|-------|
| Class | `AxeGroundSmash` |
| Icon | `NETHERITE_AXE` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 6 |
| Cost factor | 0.75 |
| Tick interval (ms) | 4333 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-ground-smash.toml` |

Listened events: `PlayerToggleSneakEvent` (arms the smash), `PlayerMoveEvent` (fires it on landing).

| Key | Code default | What it does |
|-----|--------------|--------------|
| `falloffFactor` | `3` | Curve exponent for how fast damage and force drop off with distance. Higher concentrates the hit near the center. |
| `radiusLevelFactorMultiplier` | `8` | Blocks of smash radius added across the full level range. |
| `damageLevelFactorMultiplier` | `8` | Health points of center damage added across the full level range. |
| `forceFactorMultiplier` | `1.15` | Launch velocity added across the full level range. |
| `forceBase` | `0.27` | Launch velocity applied at level 1. |
| `cooldownTicksBase` | `80` | Cooldown in ticks at max level. |
| `cooldownTicksInverseLevelMultiplier` | `225` | Extra cooldown ticks at level 1, removed as you level. |

### Axe Chop

| Property | Value |
|----------|-------|
| Class | `AxeChop` |
| Icon | `IRON_AXE` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 0.35 |
| Tick interval (ms) | 6911 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-chop.toml` |

Listened events: `PlayerInteractEvent` (right-click block, or right-click air off Folia).

| Key | Code default | What it does |
|-----|--------------|--------------|
| `rangeLevelMultiplier` | `5` | Blocks of column height searched per level when finding the top log. |
| `cooldownTicksBase` | `15` | Item cooldown in ticks at max level. |
| `cooldownTicksInverseLevelMultiplier` | `16` | Extra item cooldown ticks at level 1, removed as you level. |
| `damagePerBlockBase` | `1` | Durability spent per log at max level. |
| `damagePerBlockInverseLevelMultiplier` | `4` | Extra durability per log at level 1, removed as you level. |

### Axe Drop-To-Inventory

| Property | Value |
|----------|-------|
| Class | `AxeDropToInventory` |
| Icon | `BARREL` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 1 |
| Cost factor | 1 |
| Tick interval (ms) | 8800 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-drop-to-inventory.toml` |

Listened events: `BlockDropItemEvent`.

No adaptation-specific config keys.

### Leaf-miner

| Property | Value |
|----------|-------|
| Class | `AxeLeafVeinminer` |
| Icon | `BIRCH_LEAVES` |
| Max level | 5 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 6 |
| Cost factor | 0.325 |
| Tick interval (ms) | 5849 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-leaf-veinminer.toml` |

Listened events: `BlockBreakEvent`.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `baseRange` | `5` | Blocks of chain radius before level is added. Effective radius is level plus this. |
| `maxBlocks` | `128` | Hard cap on leaves taken by one chain. |

### Iris Feller

| Property | Value |
|----------|-------|
| Class | `AxeIrisFeller` |
| Icon | `NETHERITE_AXE` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.95 |
| Tick interval (ms) | 6127 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-iris-feller.toml` |

Listened events: `BlockBreakEvent`.

Durability preservation chance is hard-coded per level, not configurable: level 1 gives 0 percent, level 2 gives 25 percent, level 3 gives 75 percent. `maxLevel` is forced back to 3 on every config load.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `hungerCost` | `2` | Hunger points reserved per log and spent only after that log actually comes out. Clamped to 0 through 20; 0 disables the cost. |
| `cooldownSeconds` | `30` | Seconds before another fell can be accepted. 0 disables the cooldown. |

### Wood-miner

| Property | Value |
|----------|-------|
| Class | `AxeWoodVeinminer` |
| Icon | `DIAMOND_AXE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.95 |
| Tick interval (ms) | 5849 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-wood-veinminer.toml` |

Listened events: `BlockBreakEvent`.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `maxBlocks` | `20` | Hard cap on logs taken by one chain. |
| `baseRange` | `3` | Blocks of chain radius before level is added. Effective radius is level plus this. |

### Lucy's Log-Swapper

| Property | Value |
|----------|-------|
| Class | `AxeCraftLogSwap` |
| Icon | `MUDDY_MANGROVE_ROOTS` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 2 |
| Cost factor | 1 |
| Tick interval (ms) | 17773 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-logswap.toml` |

Listened events: `CraftItemEvent`.

Registers up to 70 shapeless recipes under the `adapt` namespace with keys of the form `axe-swap<from><to>`. Cherry and pale oak entries are skipped when the running Minecraft version lacks those materials. `permanent` defaults to `true` here, unlike every other Axes adaptation.

No adaptation-specific config keys.

### Throwing Axe

| Property | Value |
|----------|-------|
| Class | `AxeThrowingAxe` |
| Icon | `IRON_AXE` |
| Max level | 4 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.55 |
| Tick interval (ms) | 1000 (framework default) |
| Config file | `plugins/Adapt/adapt/adaptations/axe-throwing-axe.toml` |

Listened events: `PlayerInteractEvent` (throw on left-click air), `BlockBreakEvent` (marks the swing guard), `ProjectileHitEvent`, `EntityRemoveEvent`, `EntityDamageByEntityEvent`, `EntityDeathEvent` (kill credit), `PlayerJoinEvent` and `PlayerQuitEvent` (recovery bookkeeping).

At most 512 thrown axes are tracked in flight server-wide.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `damageMultiplierBase` | `0.6` | Fraction of the axe's melee damage dealt on a hit at level 1. |
| `damageMultiplierFactor` | `0.6` | Extra fraction added across the full level range. |
| `throwSpeedBase` | `1.2` | Launch velocity in blocks per tick at level 1. |
| `throwSpeedFactor` | `0.8` | Extra launch velocity added across the full level range. |
| `cooldownMsBase` | `1200` | Throw cooldown in milliseconds at level 1. |
| `cooldownMsLevelReduction` | `600` | Milliseconds of cooldown removed at max level. The result never drops below 250. |
| `durabilityCost` | `3` | Durability spent from the axe on each throw. |
| `maxFlightTicks` | `80` | Ticks a thrown axe stays airborne before it is recovered automatically. |
| `returnUnlockLevelPercent` | `1.0` | Level progress, 0 to 1, needed before thrown axes return to your hand instead of dropping. |
| `xpPerHit` | `6` | Skill XP per thrown-axe hit on an entity. |

### Sunder

| Property | Value |
|----------|-------|
| Class | `AxeSunder` |
| Icon | `NETHERITE_AXE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Tick interval (ms) | 1000 (framework default) |
| Config file | `plugins/Adapt/adapt/adaptations/axe-sunder.toml` |

Listened events: `EntityDamageByEntityEvent`.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `shredPerStackBase` | `1.5` | Armor points removed per stack at level 1. |
| `shredPerStackFactor` | `1.5` | Extra armor points per stack added across the full level range. |
| `toughnessShredPerStackRatio` | `0.5` | Fraction of the armor shred also taken off armor toughness. |
| `maxStacksBase` | `2` | Stack cap at level 1. |
| `maxStacksFactor` | `3` | Extra stack cap added across the full level range. |
| `durationTicks` | `120` | Ticks before the whole stack expires. Every fresh hit restarts this. |
| `xpPerStack` | `3` | Skill XP per stack applied. |

### Cleave

| Property | Value |
|----------|-------|
| Class | `AxeCleave` |
| Icon | `DIAMOND_AXE` |
| Max level | 3 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 5 |
| Cost factor | 0.6 |
| Tick interval (ms) | 9973 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-cleave.toml` |

Listened events: `EntityDamageByEntityEvent`. This is the one Axes adaptation that uses its background tick, to drop expired cleave marks.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `halfArcDegreesBase` | `25` | Half the cone width in degrees at level 1. The menu shows double this. |
| `halfArcDegreesFactor` | `25` | Extra half-arc degrees added across the full level range. |
| `radiusBase` | `2.5` | Cleave reach in blocks at level 1. |
| `radiusFactor` | `1.5` | Extra reach in blocks added across the full level range. |
| `targetCapBase` | `2` | Secondary targets one swing can hit at level 1. |
| `targetCapMaxBonus` | `2` | Extra secondary targets at max level. |
| `damageShareBase` | `0.25` | Fraction of the primary hit's damage dealt to each cleaved target at level 1. |
| `damageShareFactor` | `0.45` | Extra damage-share fraction added across the full level range. |
| `durabilityCost` | `1` | Durability spent from the axe when a cleave connects. |
| `xpPerTarget` | `4` | Skill XP per cleaved target. |

### Bark Hide

| Property | Value |
|----------|-------|
| Class | `AxeBarkHide` |
| Icon | `OAK_WOOD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Tick interval (ms) | 1000 (framework default) |
| Config file | `plugins/Adapt/adapt/adaptations/axe-bark-hide.toml` |

Listened events: `BlockBreakEvent`, `PlayerDeathEvent`.

Each stack is 4 absorption points, which is 2 hearts.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `absorptionCapBase` | `1` | Stack cap at level 1. |
| `absorptionCapFactor` | `3` | Extra stack cap added across the full level range. |
| `gracePeriodTicksBase` | `100` | Ticks the absorption survives after your last chop at level 1. Never less than 20. |
| `gracePeriodTicksFactor` | `200` | Extra grace ticks added across the full level range. |
| `xpPerStack` | `2` | Skill XP each time a fresh stack is added. |

### Shield Splitter

| Property | Value |
|----------|-------|
| Class | `AxeShieldSplitter` |
| Icon | `SHIELD` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Tick interval (ms) | 1000 (framework default) |
| Config file | `plugins/Adapt/adapt/adaptations/axe-shield-splitter.toml` |

Listened events: `EntityDamageByEntityEvent`.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `disableTicksBase` | `60` | Shield cooldown in ticks applied at level 1. Never less than 20. |
| `disableTicksFactor` | `60` | Extra shield-cooldown ticks added across the full level range. |
| `bonusDamagePctBase` | `0.15` | Extra damage fraction against a blocking target at level 1. |
| `bonusDamagePctFactor` | `0.35` | Extra damage fraction added across the full level range. |
| `xpPerBreak` | `5` | Skill XP per shield broken open. |

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
