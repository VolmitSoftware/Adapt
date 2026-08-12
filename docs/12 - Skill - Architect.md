# Skill: Architect

Skill id `architect`. Earn XP by placing blocks. Architect has 12 registered adaptations and uses the `IRON_BARS` icon.

**XP sources:** placing building blocks and breaking eligible placed blocks.

**Milestones / challenges** (stat keys):

- `challenge_place_1k` tracking `blocks.placed`
- `challenge_place_5k` tracking `blocks.placed`
- `challenge_place_50k` tracking `blocks.placed`
- `challenge_demolish_500` tracking `blocks.broken`
- `challenge_demolish_5k` tracking `blocks.broken`
- `challenge_value_placed_10k` tracking `blocks.placed.value`
- `challenge_value_placed_100k` tracking `blocks.placed.value`
- `challenge_demolish_val_5k` tracking `architect.demolish.value`
- `challenge_demolish_val_50k` tracking `architect.demolish.value`
- `challenge_high_build_100` tracking `architect.builds.high`
- `challenge_high_build_1k` tracking `architect.builds.high`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `architect` |
| Class | `SkillArchitect` |
| Icon | `IRON_BARS` |
| Color | `AQUA` |
| Interval (ms) | `3100` |
| Skill config | `plugins/Adapt/adapt/skills/architect.toml` |
| Adaptation count | 12 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/architect.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&b"` | Legacy ampersand color code used for this skill in menus and text. |
| `challengePlace1kReward` | `1750` | Reward for the place 1 k challenge. |
| `xpValueMultiplier` | `1.5` | Unitless multiplier applied to XP from xp value multiplier. |
| `cooldownDelay` | `1000` | Minimum delay between passive skill XP awards, in milliseconds. |
| `xpBase` | `3` | Base skill XP credited for xp base. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Silk-Touch Glass (`architect-glass`)

Break glass blocks with an empty hand to pick them up without shattering them.

**Runtime entry points:** when breaking blocks; periodic evaluation every 25000 ms.

**Menu displays:** Glass drops itself when broken by hand.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ArchitectGlass` |
| Icon | `GLASS` |
| Max level | 1 |
| Initial knowledge cost | 0 |
| Base knowledge cost | 3 |
| Cost factor | 5 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/architect-glass.toml` |

Listened events:

- `BlockBreakEvent` (`on`) — when breaking blocks

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Magic Foundation (`architect-foundation`)

Sneak to place a temporary foundation beneath you.
Each temporary block passes normal place-event authorization before it is journaled or placed; denial leaves
foundation power untouched. On Folia, the block and complete entity-obstruction query footprint must belong to
the player's current region, while expiry cleanup remains independent of place authorization.

**Runtime entry points:** while moving; on `BlockPistonExtendEvent`; on `BlockPistonRetractEvent`; on `BlockExplodeEvent`; when breaking blocks; on `EntityExplodeEvent`; on sneak toggle; on `ChunkLoadEvent`.

**Menu displays:** Temporary blocks placed beneath the player.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ArchitectFoundation` |
| Icon | `TINTED_GLASS` |
| Max level | 5 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 5 |
| Cost factor | 0.40 |
| Tick interval (ms) | 988 |
| Config file | `plugins/Adapt/adapt/adaptations/architect-foundation.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving
- `BlockPistonExtendEvent` (`on`)
- `BlockPistonRetractEvent` (`on`)
- `BlockExplodeEvent` (`on`)
- `BlockBreakEvent` (`on`) — when breaking blocks
- `EntityExplodeEvent` (`on`)
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `ChunkLoadEvent` (`on`)
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `duration` | `3000` | Duration. Milliseconds. |
| `minBlocks` | `9` | Lower bound or activation threshold for min blocks. Blocks. |
| `maxBlocks` | `35` | Maximum blocks. Blocks. |
| `cooldown` | `5000` | Cooldown. Milliseconds. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Builders Wand (`architect-placement`)

Sneak while holding a block that matches the surface you are looking at to place multiple blocks across it at once. You may need to move slightly to refresh the placement preview.
Every replicated block passes normal place-event authorization before Adapt consumes its matching item or
changes the world. On Folia, the complete source and destination footprint must share the player's current
owning region or the adaptation leaves the original placement unchanged.

**Runtime entry points:** when placing blocks; on sneak toggle.

**Menu displays:** Required matching blocks in hand; placement count; material builder's wand.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ArchitectPlacement` |
| Icon | `SCAFFOLDING` |
| Max level | 1 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 6 |
| Cost factor | 2 |
| Config file | `plugins/Adapt/adapt/adaptations/architect-placement.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `BlockPlaceEvent` (`on`) — when placing blocks
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerMoveEvent` (`on`) — refreshes the placement preview

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxBlocks` | `20` | Maximum blocks. Blocks. |
| `useDisplayEntities` | `true` | Use owner-only block display previews instead of particles for the wand guide. |
| `displayEntityViewRange` | `0.75` | View range used for wand preview display entities. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Redstone Remote (`architect-wireless-redstone`)

Use a redstone torch to toggle redstone remotely. Every powered block, neighboring component, and door half
must pass a current interaction check before the pulse begins. Folia requires the complete pulse footprint and
player to share the target region. Denied, stale, or unschedulable pulses do not start the remote's cooldown.

**Runtime entry points:** when placing blocks; on block/entity/air interact (click); on `ChunkUnloadEvent`.

**Menu displays:** Target + Redstone Torch + Ender Pearl = 1 Redstone Remote.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ArchitectWirelessRedstone` |
| Icon | `REDSTONE_TORCH` |
| Max level | 1 |
| Initial knowledge cost | 0 |
| Base knowledge cost | 5 |
| Cost factor | 1 |
| Config file | `plugins/Adapt/adapt/adaptations/architect-wireless-redstone.toml` |

Listened events:

- `BlockPlaceEvent` (`onPlaceBlock`) — when placing blocks
- `PlayerInteractEvent` (`onPlayerInteract`) — on block/entity/air interact (click)
- `ChunkUnloadEvent` (`onChunkUnload`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldown` | `125` | Cooldown. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Elevator (`architect-elevator`)

Build fast vertical elevators. Craft each Elevator Block with an Ender Pearl in the center, surrounded by 8 Wool.

**Runtime entry points:** while moving; on sneak toggle; when placing blocks; on `CustomBlockDataMoveEvent`; on `BlockExplodeEvent`; on `EntityExplodeEvent`; on `CustomBlockDataRemoveEvent`; periodic evaluation every 988 ms.

**Menu displays:** Unlocks elevator recipe: X=WOOL, Y=ENDER PEARL; XXX; XYX; XXX.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ArchitectElevator` |
| Icon | `HEAVY_WEIGHTED_PRESSURE_PLATE` |
| Max level | 1 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 5 |
| Cost factor | 0.40 |
| Tick interval (ms) | 988 |
| Config file | `plugins/Adapt/adapt/adaptations/architect-elevator.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving
- `PlayerJoinEvent` (`on`)
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `BlockPlaceEvent` (`on`) — when placing blocks
- `CustomBlockDataMoveEvent` (`on`)
- `BlockExplodeEvent` (`on`)
- `EntityExplodeEvent` (`on`)
- `CustomBlockDataRemoveEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseDistance` | `32` | Base distance. Blocks. |
| `multiplier` | `1` | Multiplier. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Smart Shape (`architect-smart-shape`)

Sneak-punch blocks with an empty hand to rotate orientation.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 800 ms.

**Menu displays:** Rotates directional and axis block states; Requires empty main hand.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ArchitectSmartShape` |
| Icon | `BRICKS` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.6 |
| Tick interval (ms) | 800 |
| Config file | `plugins/Adapt/adapt/adaptations/architect-smart-shape.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `minXpPerRotate` | `0.4` | XP awarded for min per rotate. |
| `xpPerOrientationOption` | `0.16` | XP awarded for xp per orientation option. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Scaffolder (`architect-scaffolder`)

Sneak-place blocks as temporary scaffolds that dissolve on their own and refund the block to you.

**Runtime entry points:** when placing blocks; when breaking blocks; periodic evaluation every 9220 ms.

**Menu displays:** Sneak-placed blocks dissolve automatically; Seconds before a scaffold dissolves and refunds.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ArchitectScaffolder` |
| Icon | `SCAFFOLDING` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Tick interval (ms) | 9220 |
| Config file | `plugins/Adapt/adapt/adaptations/architect-scaffolder.toml` |

Listened events:

- `BlockPlaceEvent` (`on`) — when placing blocks
- `BlockBreakEvent` (`on`) — when breaking blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `minDurationSeconds` | `5` | Scaffold lifetime in seconds at level 0 progression. |
| `maxDurationSeconds` | `30` | Scaffold lifetime in seconds at maximum level progression. |
| `maxScaffoldsPerPlayer` | `24` | Maximum number of active scaffolds tracked per player. |
| `blockFilterMode` | `"OFF"` | Block filter mode: OFF, BLACKLIST, or WHITELIST. |
| `blockFilterMaterials` | `[]` | Material names used by the block filter, for example TNT or SAND. |
| `hungerExhaustionPerScaffold` | `0` | Exhaustion added per scaffolded block, where 4.0 drains half a hunger point. Zero disables the cost. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Supply Line (`architect-supply-line`)

When the stack in your hand runs out, it refills automatically from shulker boxes or bundles in your inventory.

**Runtime entry points:** when placing blocks; periodic evaluation every 13780 ms.

**Menu displays:** Hand auto-refills from shulkers and bundles; Refills per minute.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ArchitectSupplyLine` |
| Icon | `SHULKER_BOX` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 5 |
| Cost factor | 0.5 |
| Tick interval (ms) | 13780 |
| Config file | `plugins/Adapt/adapt/adaptations/architect-supply-line.toml` |

Listened events:

- `BlockPlaceEvent` (`on`) — when placing blocks

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `minRefillsPerMinute` | `4` | Hand refills allowed per minute at level 0 progression. |
| `maxRefillsPerMinute` | `20` | Hand refills allowed per minute at maximum level progression. |
| `xpPerRefill` | `2` | Adaptation xp granted per successful refill. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Steady Hands (`architect-steady-hands`)

While bridging over open air you take no knockback, shrug off falls, and place with a steadier rhythm.

**Runtime entry points:** when placing blocks; on sneak toggle; periodic evaluation every 10440 ms.

**Menu displays:** No knockback while bridging; Blocks of fall damage shielded.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ArchitectSteadyHands` |
| Icon | `LIGHTNING_ROD` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 10440 |
| Config file | `plugins/Adapt/adapt/adaptations/architect-steady-hands.toml` |

Listened events:

- `BlockPlaceEvent` (`on`) — when placing blocks
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `minShieldedBlocks` | `3` | Fall damage shielded in blocks at level 0 progression. |
| `maxShieldedBlocks` | `12` | Fall damage shielded in blocks at maximum level progression. |
| `bridgeGraceMillis` | `4000` | How long in milliseconds after a bridge placement the protections stay active. |
| `hasteDurationTicks` | `40` | Duration in ticks of the haste boost granted per bridge placement. |
| `hasteAmplifier` | `0` | Amplifier of the haste boost granted per bridge placement. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Chalk Line (`architect-chalk-line`)

Craft persistent Chalk Wands from one Stick and one String in different orientations. Level 1 unlocks straight lines, level 2 polylines, level 3 circles, and level 4 arcs. Each level immediately reveals its new crafting recipe in the vanilla recipe book. Their private block guides appear only while that wand is held; sneak-click the air to clear its saved plan.

**Runtime entry points:** when taking a craft result; on `CrafterCraftEvent`; on block/entity/air interact (click); when placing blocks; on `PlayerInventorySlotChangeEvent`; on gamemode change; on `PlayerRespawnEvent`; on `PlayerItemHeldEvent`.

**Menu displays:** New wand recipes appear in your vanilla recipe book.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ArchitectChalkLine` |
| Icon | `STRING` |
| Max level | 4 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 3 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/architect-chalk-line.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result
- `CrafterCraftEvent` (`on`)
- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `BlockPlaceEvent` (`on`) — when placing blocks
- `PlayerQuitEvent` (`on`)
- `PlayerJoinEvent` (`on`)
- `PlayerInventorySlotChangeEvent` (`on`)
- `PlayerGameModeChangeEvent` (`on`) — on gamemode change
- `PlayerRespawnEvent` (`on`)
- `PlayerItemHeldEvent` (`on`)
- `PlayerSwapHandItemsEvent` (`on`) — on swap hands (F)
- `PlayerChangedWorldEvent` (`on`) — on world change

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxSelectionDistance` | `96` | Maximum distance in blocks between selected control points. |
| `maxPolylineVertices` | `12` | Maximum number of control vertices stored by a polyline wand. |
| `maxCircleRadius` | `15` | Maximum circle radius in blocks. |
| `maxArcRadius` | `64` | Maximum computed radius in blocks for a three-point arc. |
| `maxGuideBlocks` | `96` | Maximum block-display markers in one chalk guide. |
| `renderRangeBlocks` | `64` | Maximum distance in blocks at which the held wand renders its private guide. |
| `xpPerGuide` | `3` | Adaptation xp granted whenever a complete chalk guide is drafted or extended. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Mason's Eraser (`architect-demolition`)

Erase your own recent placements near-instantly without producing drops.

**Runtime entry points:** when placing blocks; on `BlockDamageEvent`; when breaking blocks; periodic evaluation every 10880 ms.

**Menu displays:** Your fresh placements break near-instantly; Seconds a placement counts as fresh.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ArchitectDemolition` |
| Icon | `TNT` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 10880 |
| Config file | `plugins/Adapt/adapt/adaptations/architect-demolition.toml` |

Listened events:

- `BlockPlaceEvent` (`on`) — when placing blocks
- `BlockDamageEvent` (`on`)
- `BlockBreakEvent` (`on`) — when breaking blocks
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `minWindowSeconds` | `10` | How many seconds a placement counts as recent at level 0 progression. |
| `maxWindowSeconds` | `60` | How many seconds a placement counts as recent at maximum level progression. |
| `maxTrackedPerPlayer` | `64` | Maximum number of recent placements tracked per player. |
| `xpPerDemolish` | `1` | Adaptation xp granted per demolished block. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Stonecutter Savant (`architect-stonecutter-savant`)

Sneak-punch the air with an empty hand to open a stonecutter wherever you are, as long as you carry a stonecutter.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 24420 ms.

**Menu displays:** Portable stonecutter on demand; Requires a stonecutter item in your inventory; Requires a stonecutter in your offhand.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `ArchitectStonecutterSavant` |
| Icon | `STONECUTTER` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Tick interval (ms) | 24420 |
| Config file | `plugins/Adapt/adapt/adaptations/architect-stonecutter-savant.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerJoinEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `requireOffhand` | `false` | Requires the stonecutter item to be in the offhand specifically. |
| `xpPerUse` | `2` | Adaptation xp granted per stonecutter opened. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## Support classes (not player adaptations)

- `ArchitectChalkGeometry` — generates the discrete line, polyline, circle, and arc points used by Chalk Wands.
- `ArchitectRedstonePulse` — owns remote redstone activations and restores their previous block state when a pulse completes or is cancelled.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
