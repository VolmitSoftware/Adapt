# Skill: Axes

Skill id `axes`. Earn XP by chopping wood and fighting with axes. Eleven adaptations register without integrations; `axe-iris-feller` is the twelfth documented source type and registers only when Iris is available. The skill uses the `GOLDEN_AXE` icon.

**XP sources:** chopping logs and wood with axes, plus axe combat damage.

**Milestones / challenges** (stat keys):

- `challenge_chop_1k` tracking `axes.blocks.broken`
- `challenge_chop_5k` tracking `axes.blocks.broken`
- `challenge_chop_50k` tracking `axes.blocks.broken`
- `challenge_axe_damage_1k` tracking `axes.damage`
- `challenge_axe_damage_10k` tracking `axes.damage`
- `challenge_axe_value_5k` tracking `axes.blocks.value`
- `challenge_axe_value_50k` tracking `axes.blocks.value`
- `challenge_leaves_500` tracking `axes.leaves`
- `challenge_leaves_5k` tracking `axes.leaves`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `axes` |
| Class | `SkillAxes` |
| Icon | `GOLDEN_AXE` |
| Color | `YELLOW` |
| Interval (ms) | `5251` |
| Skill config | `plugins/Adapt/adapt/skills/axes.toml` |
| Adaptation count | 11 normally; 12 with Iris |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/axes.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&e"` | Legacy ampersand color code used for this skill in menus and text. |
| `getXpForAttackingWithTools` | `true` | XP awarded for get for attacking with tools. |
| `maxHardnessBonus` | `9` | Maximum block-hardness contribution added to mining XP calculations. |
| `maxBlastResistanceBonus` | `10` | Maximum blast-resistance contribution added to mining XP calculations. |
| `challengeChopReward` | `1750` | Reward for the chop challenge. |
| `logOrWoodXPMultiplier` | `2.0` | Unitless multiplier applied to XP from log or wood multiplier. |
| `leavesMultiplier` | `0.75` | Unitless XP multiplier applied when the broken block is leaves. |
| `cooldownDelay` | `1500` | Minimum delay between passive skill XP awards, in milliseconds. |
| `valueXPMultiplier` | `0.175` | Unitless multiplier applied to XP from value multiplier. |
| `axeDamageXPMultiplier` | `7.0` | Unitless multiplier applied to XP from axe damage multiplier. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Axe Ground Smash (`axe-ground-smash`)

Jump, then crouch and smash all nearby enemies.

**Runtime entry points:** on sneak toggle; while moving; periodic evaluation every 4333 ms.

**Menu displays:** Damage; Block Radius; Force; Smash Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AxeGroundSmash` |
| Icon | `NETHERITE_AXE` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 6 |
| Cost factor | 0.75 |
| Tick interval (ms) | 4333 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-ground-smash.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerMoveEvent` (`on`) — while moving

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `falloffFactor` | `3` | Falloff factor. Unitless multiplier. |
| `radiusLevelFactorMultiplier` | `8` | Radius level factor multiplier. Blocks. |
| `damageLevelFactorMultiplier` | `8` | Damage level factor multiplier. Unitless multiplier. |
| `forceFactorMultiplier` | `1.15` | Force factor multiplier. Unitless multiplier. |
| `forceBase` | `0.27` | Base Force. |
| `cooldownTicksBase` | `80` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksInverseLevelMultiplier` | `225` | Cooldown ticks inverse level multiplier. Server ticks (20 ticks = 1 second). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Axe Chop (`axe-chop`)

Chop down trees by right clicking the base log.
Each additional log uses the player's native block-break action; a denied or non-owned break is not reported as
successful. Folia requires a direct block click because an air-click ray target can cross region ownership.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 6911 ms.

**Menu displays:** Blocks Per Chop; Chop Cooldown; Tool Wear.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AxeChop` |
| Icon | `IRON_AXE` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 0.35 |
| Tick interval (ms) | 6911 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-chop.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rangeLevelMultiplier` | `5` | Range level multiplier. Blocks. |
| `cooldownTicksBase` | `15` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksInverseLevelMultiplier` | `16` | Cooldown ticks inverse level multiplier. Server ticks (20 ticks = 1 second). |
| `damagePerBlockBase` | `1` | Base damage points charged per processed block (2 points = 1 heart). |
| `damagePerBlockInverseLevelMultiplier` | `4` | Damage per block inverse level multiplier. Blocks. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Axe Drop-To-Inventory (`axe-drop-to-inventory`)

Chopped wood drops directly into your inventory.
Each spawned block-drop entity must pass Bukkit's normal pickup events before it leaves the block's drop list;
a denied pickup remains on the original world-drop path.

**Runtime entry points:** on `BlockDropItemEvent`; periodic evaluation every 8800 ms.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AxeDropToInventory` |
| Icon | `BARREL` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 1 |
| Cost factor | 1 |
| Tick interval (ms) | 8800 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-drop-to-inventory.toml` |

Listened events:

- `BlockDropItemEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Leaf-miner (`axe-leaf-veinminer`)

Break connected leaves in bulk.
The chain waits one tick and runs only after the original leaf finishes its normal break while the player still holds an axe. Every additional leaf
uses the player's native block-break action; denied or changed blocks remain in place and do not contribute to statistics or completion effects.

**Runtime entry points:** when breaking blocks; periodic evaluation every 5849 ms.

**Menu displays:** Sneak-mine leaves; leaf-mining range; bonus leaves do not drop items.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AxeLeafVeinminer` |
| Icon | `BIRCH_LEAVES` |
| Max level | 5 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 6 |
| Cost factor | 0.325 |
| Tick interval (ms) | 5849 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-leaf-veinminer.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseRange` | `5` | Base range. Blocks. |
| `maxBlocks` | `128` | Maximum blocks. Blocks. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Iris Feller (`axe-iris-feller`)

Sneak-break an Iris tree with an axe, then keep sneaking and keep that original axe held while the tree erodes outward. Each successfully eroded log costs hunger; the run halts when sneaking stops, the original axe is no longer held, or hunger cannot fund the next log. Accepted runs start an activation cooldown.

**Runtime entry points:** when breaking blocks; periodic evaluation every 6127 ms.

**Menu displays:** Axe-durability preservation chance; hunger cost per eroded log; activation cooldown; continuous-sneak and held-axe requirements.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AxeIrisFeller` |
| Icon | `NETHERITE_AXE` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.95 |
| Tick interval (ms) | 6127 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-iris-feller.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `hungerCost` | `2` | Hunger points reserved for each log and consumed only after that log is successfully eroded. |
| `cooldownSeconds` | `30` | Cooldown in seconds after Iris accepts a tree-felling request. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Wood-miner (`axe-wood-veinminer`)

Break connected logs and wood in bulk.
The chain waits one tick and runs only after the original block finishes its normal break while the player still holds an axe. Every additional wood
block uses the player's native block-break action; denied or changed blocks remain in place and do not contribute to statistics or cascade advancement progress.

**Runtime entry points:** when breaking blocks; periodic evaluation every 5849 ms.

**Menu displays:** Sneak-mine logs or wood, excluding planks; wood-mining range; compatible with drop-to-inventory.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AxeWoodVeinminer` |
| Icon | `DIAMOND_AXE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.95 |
| Tick interval (ms) | 5849 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-wood-veinminer.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxBlocks` | `20` | Maximum blocks. Blocks. |
| `baseRange` | `3` | Base range. Blocks. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Lucy's Log-Swapper (`axe-logswap`)

Change the flavor of logs in a Crafting Table.

**Runtime entry points:** when taking a craft result; periodic evaluation every 17773 ms.

**Menu displays:** 8 Log of any kind + 1 sapling = 8 log of the sapling's type.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AxeCraftLogSwap` |
| Icon | `MUDDY_MANGROVE_ROOTS` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 2 |
| Cost factor | 1 |
| Tick interval (ms) | 17773 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-logswap.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Throwing Axe (`axe-throwing-axe`)

Left-click air with an axe to hurl it as a spinning projectile that deals its melee damage.

**How it activates:** left-click air with an axe in the main hand. The remaining events manage the projectile, damage, return, cleanup, and kill credit after a throw.

**Menu displays:** Melee Damage Multiplier; Throw Speed; Throw Cooldown; Returns to your hand at max level.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AxeThrowingAxe` |
| Icon | `IRON_AXE` |
| Max level | 4 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.55 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-throwing-axe.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `BlockBreakEvent` (`on`) — when breaking blocks
- `ProjectileHitEvent` (`on`) — when a projectile hits
- `EntityRemoveEvent` (`on`)
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDeathEvent` (`on`) — on entity death / kill credit
- `PlayerJoinEvent` (`on`)
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageMultiplierBase` | `0.6` | Base fraction of the axe's melee damage dealt on a throw hit. |
| `damageMultiplierFactor` | `0.6` | Extra melee-damage fraction added by leveling this adaptation. |
| `throwSpeedBase` | `1.2` | Base flight velocity of a thrown axe in blocks per tick. |
| `throwSpeedFactor` | `0.8` | Extra flight velocity added by leveling this adaptation. |
| `cooldownMsBase` | `1200` | Base throw cooldown in milliseconds before level scaling. |
| `cooldownMsLevelReduction` | `600` | Milliseconds of cooldown removed at maximum level. |
| `durabilityCost` | `3` | Durability spent from the axe on each throw. |
| `maxFlightTicks` | `80` | Ticks a thrown axe stays airborne before it is recovered automatically. |
| `returnUnlockLevelPercent` | `1.0` | Level progress required before thrown axes return to your hand. |
| `xpPerHit` | `6` | XP granted per thrown-axe hit on an entity. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Sunder (`axe-sunder`)

Axe hits shred a target's armor and armor toughness in stacking layers that fade over time.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Armor Shred Per Stack; Max Stacks; Shred Duration; Toughness Shred Per Stack.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AxeSunder` |
| Icon | `NETHERITE_AXE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-sunder.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `shredPerStackBase` | `1.5` | Armor points removed per Sunder stack before level scaling. |
| `shredPerStackFactor` | `1.5` | Extra armor points per stack granted by leveling. |
| `toughnessShredPerStackRatio` | `0.5` | Fraction of each stack's armor shred also removed from armor toughness. |
| `maxStacksBase` | `2` | Base maximum Sunder stacks before level scaling. |
| `maxStacksFactor` | `3` | Additional maximum stacks granted at maximum level. |
| `durationTicks` | `120` | Ticks a Sunder application lasts before that layer decays. |
| `xpPerStack` | `3` | XP granted per Sunder stack applied. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Cleave (`axe-cleave`)

Melee axe swings cleave extra enemies in a short frontal arc.

**Runtime entry points:** on melee/projectile hit (damage); periodic evaluation every 9973 ms.

**Menu displays:** Cleave Arc; Max Extra Targets; Damage Share.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AxeCleave` |
| Icon | `DIAMOND_AXE` |
| Max level | 3 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 5 |
| Cost factor | 0.6 |
| Tick interval (ms) | 9973 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-cleave.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `halfArcDegreesBase` | `25` | Half the cleave arc width in degrees before level scaling. |
| `halfArcDegreesFactor` | `25` | Extra half-arc degrees granted by leveling. |
| `radiusBase` | `2.5` | Cleave reach in blocks before level scaling. |
| `radiusFactor` | `1.5` | Extra cleave reach in blocks granted by leveling. |
| `targetCapBase` | `2` | Base number of secondary targets cleave can strike. |
| `targetCapMaxBonus` | `2` | Additional secondary targets unlocked at maximum level. |
| `damageShareBase` | `0.25` | Fraction of the primary hit's damage dealt to each cleaved target. |
| `damageShareFactor` | `0.45` | Extra damage-share fraction granted by leveling. |
| `durabilityCost` | `1` | Durability spent from the axe when a cleave connects. |
| `xpPerTarget` | `4` | XP granted per cleaved target. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bark Hide (`axe-bark-hide`)

Chopping logs layers on short-lived absorption that fades once you stop working.

**Runtime entry points:** when breaking blocks; on player death.

**Menu displays:** Absorption Cap; Decay Grace.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AxeBarkHide` |
| Icon | `OAK_WOOD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-bark-hide.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks
- `PlayerDeathEvent` (`on`) — on player death

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `absorptionCapBase` | `1` | Base maximum absorption stacks before level scaling. Each stack is two hearts. |
| `absorptionCapFactor` | `3` | Additional absorption stacks unlocked at maximum level. |
| `gracePeriodTicksBase` | `100` | Grace ticks the absorption persists after your last chop before level scaling. |
| `gracePeriodTicksFactor` | `200` | Extra grace ticks granted by leveling. |
| `xpPerStack` | `2` | XP granted each time a fresh bark absorption stack is added. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Shield Splitter (`axe-shield-splitter`)

Axe hits on blocking foes disable their shield longer and deal bonus damage.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Shield Disable Duration; Bonus Damage vs Blocking.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `AxeShieldSplitter` |
| Icon | `SHIELD` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Config file | `plugins/Adapt/adapt/adaptations/axe-shield-splitter.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `disableTicksBase` | `60` | Base shield-disable duration in ticks before level scaling. |
| `disableTicksFactor` | `60` | Extra shield-disable ticks granted by leveling. |
| `bonusDamagePctBase` | `0.15` | Base bonus damage fraction against a blocking target. |
| `bonusDamagePctFactor` | `0.35` | Extra bonus damage fraction granted by leveling. |
| `xpPerBreak` | `5` | XP granted per shield broken open. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## Support classes (not player adaptations)

- `AxeBlockBreakSwingGuard` — suppresses the one synthetic air swing emitted immediately after an axe block break.
- `AxeRecoveryJournal` — atomically persists thrown-axe recovery entries and validates them before restoring an item.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
