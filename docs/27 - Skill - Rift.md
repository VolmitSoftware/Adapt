# Skill: Rift

Skill id `rift`. Earn XP by teleporting and using ender items. Rift has 13 registered adaptations and uses the `ENDER_EYE` icon.

**XP sources:** teleports, ender-pearl use, and rift-related projectile hits and kills.

**Milestones / challenges** (stat keys):

- `challenge_rift_50` tracking `rift.teleports`
- `challenge_rift_500` tracking `rift.teleports`
- `challenge_rift_5k` tracking `rift.teleports`
- `challenge_rift_pearls_50` tracking `rift.ender.pearls`
- `challenge_rift_pearls_500` tracking `rift.ender.pearls`
- `challenge_rift_enderman_50` tracking `rift.enderman.kills`
- `challenge_rift_enderman_500` tracking `rift.enderman.kills`
- `challenge_rift_dragon_500` tracking `rift.dragon.damage`
- `challenge_rift_dragon_5k` tracking `rift.dragon.damage`
- `challenge_rift_crystal_10` tracking `rift.crystals.destroyed`
- `challenge_rift_crystal_100` tracking `rift.crystals.destroyed`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `rift` |
| Class | `SkillRift` |
| Icon | `ENDER_EYE` |
| Color | `DARK_PURPLE` |
| Interval (ms) | `1154` |
| Skill config | `plugins/Adapt/adapt/skills/rift.toml` |
| Adaptation count | 13 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/rift.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&5"` | Legacy ampersand color code used for this skill in menus and text. |
| `destroyEndCrystalXP` | `250` | XP awarded for destroy end crystal. |
| `damageEndermanXPMultiplier` | `4` | Unitless multiplier applied to XP from damage enderman multiplier. |
| `damageEndermiteXPMultiplier` | `2` | Unitless multiplier applied to XP from damage endermite multiplier. |
| `damageEnderdragonXPMultiplier` | `8` | Unitless multiplier applied to XP from damage enderdragon multiplier. |
| `throwEnderpearlXP` | `65` | XP awarded for throw enderpearl. |
| `throwEnderEyeXP` | `30` | XP awarded for throw ender eye. |
| `teleportXP` | `15` | XP awarded for teleport. |
| `teleportXPCooldown` | `60000` | Rate-limit or history window for XP from teleport cooldown. |
| `challengeRiftReward` | `500` | Reward for the rift challenge. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Rift Resistance (`rift-resist`)

Gain Resistance when using Ender Items & Abilities.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 10288 ms.

**Menu displays:** Resistance from rift abilities and consumable ender items; Easy Enderchest does not trigger it.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RiftResist` |
| Icon | `SCULK_VEIN` |
| Max level | 1 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 10288 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-resist.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `amplitude` | `1` | Amplitude. |
| `duration` | `80` | Duration. |
| `activationCooldownMillis` | `4000` | Cooldown between manual right-click-air activations in milliseconds. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Remote Access (`rift-access`)

Craft a Reliquary Portkey (ender pearl + compass), bind it to a container, and use it to open that container from anywhere.

**Runtime entry points:** on block/entity/air interact (click); on `BlockBurnEvent`; on `BlockPistonRetractEvent`; on `BlockPistonExtendEvent`; on `BlockExplodeEvent`; on `EntityExplodeEvent`; when breaking blocks; on `InventoryCloseEvent`.

**Menu displays:** The recipe combines an ender pearl and compass into a Reliquary Portkey; the crafted item's instructions describe how to bind and use it.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RiftAccess` |
| Icon | `NETHER_STAR` |
| Max level | 1 |
| Initial knowledge cost | 15 |
| Base knowledge cost | 3 |
| Cost factor | 0.2 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-access.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `BlockBurnEvent` (`on`)
- `BlockPistonRetractEvent` (`on`)
- `BlockPistonExtendEvent` (`on`)
- `BlockExplodeEvent` (`on`)
- `EntityExplodeEvent` (`on`)
- `BlockBreakEvent` (`on`) — when breaking blocks
- `InventoryCloseEvent` (`on`)
- `PlayerQuitEvent` (`on`)
- `ChunkUnloadEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Easy Enderchest (`rift-enderchest`)

Click while holding an ender chest to open your ender chest without placing it.

**How it activates:** right-click air, left-click air, or left-click a block while holding an ender chest in the main hand. A successful use applies a five-second ender-chest cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RiftEnderchest` |
| Icon | `ENDER_CHEST` |
| Max level | 1 |
| Initial knowledge cost | 10 |
| Base knowledge cost | 0 |
| Cost factor | 0.0 |
| Tick interval (ms) | 9248 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-enderchest.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — opens the ender chest from the held item

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Rift Gate (`rift-gate`)

Craft a recall gate (Emerald + Amethyst Shard + Ender Pearl), then sneak-left-click it to bind your location and right-click to teleport back after a 5 second channel. Sneak-left-click into the air to unbind.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 1322 ms.

**Menu displays:** Craft with an emerald, amethyst shard, and ender pearl; five-second delay; the player remains vulnerable during the animation.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RiftGate` |
| Icon | `RESPAWN_ANCHOR` |
| Max level | 1 |
| Initial knowledge cost | 30 |
| Base knowledge cost | 0 |
| Cost factor | 0.0 |
| Tick interval (ms) | 1322 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-gate.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerQuitEvent` (`on`)
- `PlayerJoinEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `consumeOnUse` | `true` | Consume on use. |
| `requireCraftedEye` | `true` | Requires crafting the recall gate eye before it can be bound. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Rift Blink (`rift-blink`)

Double-jump to blink toward where you are looking. Aim at ground to land there, at a ledge to mantle onto it, or into open air to dash. Successful blinks consume no pearl, but deal normal ender pearl damage that decreases by level. Sneak while blinking to phase straight through walls and obstacles.

**Runtime entry points:** while moving; periodic evaluation every 9288 ms.

**Menu displays:** Blink Range; Self-Damage (hearts); Sneak while blinking to phase through walls.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RiftBlink` |
| Icon | `FEATHER` |
| Max level | 5 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 7 |
| Cost factor | 0.12 |
| Tick interval (ms) | 9288 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-blink.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldownMillis` | `2000` | Cooldown between successful Rift Blink triggers in milliseconds. |
| `pearlDamageBase` | `5.0` | Vanilla ender pearl damage applied after a successful level-1 blink. |
| `pearlDamageReductionPerLevel` | `1.0` | Blink self-damage removed for each level beyond the first. |
| `minimumPearlDamage` | `1.0` | Minimum ender pearl damage a successful blink can inflict. |
| `baseDistance` | `12` | Blink distance in blocks at level 0 before level scaling. |
| `distanceFactor` | `20` | Additional blink distance in blocks granted at max level, scaling linearly with level. |
| `groundSnapDepth` | `5` | Blocks searched downward from the aimed point to prefer landing on solid ground. |
| `momentumCarry` | `0.35` | Velocity carried along the look direction after a blink. |
| `minBlinkDistance` | `1.5` | Minimum distance a blink must cover to trigger. |
| `phaseWhileSneaking` | `true` | Allows a blink triggered while sneaking to phase through walls and obstacles, landing at the farthest open space within range. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Anti-Levitation (`rift-descent`)

Tap sneak while levitating to cancel Levitation and drift down gently with Slow Falling.

**Runtime entry points:** on sneak toggle; periodic evaluation every 9544 ms.

**Menu displays:** Sneak to cancel Levitation and descend with Slow Falling; cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RiftDescent` |
| Icon | `SHULKER_BOX` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 1 |
| Cost factor | 0.95 |
| Tick interval (ms) | 9544 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-descent.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldown` | `5.0` | Cooldown. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Rift Visage (`rift-visage`)

Prevents Endermen from becoming aggressive if you have Ender Pearls in your inventory.

**Runtime entry points:** on `EntityTargetEvent`.

**Menu displays:** Endermen remain passive while the player carries ender pearls.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RiftVisage` |
| Icon | `POPPED_CHORUS_FRUIT` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 8 |
| Cost factor | 0 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-visage.toml` |

Listened events:

- `EntityTargetEvent` (`onEntityTarget`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Ender Taglock (`rift-ender-taglock`)

Sneak-left-click entities with an ender pearl to bind them, then throw the tagged pearl to relocate only that target. The thrower is never teleported.

**Runtime entry points:** on melee/projectile hit (damage); on block/entity/air interact (click); on teleport; when a projectile hits; periodic evaluation every 1200 ms.

**Menu displays:** Level 1: Passive and hostile mobs; Level 2: Villagers and large targets; Level 3: Any target, including players; Tagged Pearl Throw Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RiftEnderTaglock` |
| Icon | `ENDER_PEARL` |
| Max level | 3 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 7 |
| Cost factor | 0.95 |
| Tick interval (ms) | 1200 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-ender-taglock.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerTeleportEvent` (`on`) — on teleport
- `ProjectileHitEvent` (`on`) — when a projectile hits
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `throwCooldownTicksBase` | `30` | Base Throw cooldown ticks. Server ticks (20 ticks = 1 second). |
| `throwCooldownTicksFactor` | `14` | Throw cooldown ticks factor. Server ticks (20 ticks = 1 second). |
| `suppressPearlTeleportWindowMillis` | `250` | How long the thrower's vanilla pearl teleport stays suppressed after a taglocked pearl lands. |
| `largeWidthThreshold` | `1.3` | Large width threshold. |
| `largeHeightThreshold` | `2.35` | Large height threshold. |
| `xpOnTag` | `8` | XP awarded for xp on tag. |
| `xpOnThrow` | `5` | XP awarded for xp on throw. |
| `damageSender` | `true` | Whether the player who sends an entity through the rift takes the configured feedback damage. |
| `xpOnTeleport` | `14` | XP awarded for xp on teleport. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Inflated Pocket Dimension (`rift-inflated-pocket-dimension`)

Empty-hand right-click a block to pull matching stacks from your ender chest; sneak-drop to store items into it.

**Runtime entry points:** on block/entity/air interact (click); when placing blocks; on drop item; periodic evaluation every 600 ms.

**Menu displays:** Right-click block to pull stack; Building auto-refill from ender chest; Sneak-drop stores item in ender chest.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RiftInflatedPocketDimension` |
| Icon | `ENDER_EYE` |
| Max level | 1 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 7 |
| Cost factor | 1 |
| Tick interval (ms) | 600 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-inflated-pocket-dimension.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `BlockPlaceEvent` (`on`) — when placing blocks
- `PlayerDropItemEvent` (`on`) — on drop item

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `buildRefillAmount` | `64` | Build refill amount. |
| `rightClickPullAmount` | `64` | Right click pull amount. |
| `xpPerTransferredItem` | `0.08` | XP awarded for xp per transferred item. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Void Magnet (`rift-void-magnet`)

Sneak to pull nearby item drops into your ender chest first, then inventory overflow.

**Runtime entry points:** on sneak toggle.

**Menu displays:** Magnet Radius; Max Items Per Pulse; Pulse Delay.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RiftVoidMagnet` |
| Icon | `HOPPER_MINECART` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-void-magnet.toml` |

Listened events:

- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `allowEnderChestOverflow` | `false` | Allow ender chest overflow. |
| `radiusBase` | `5` | Base Radius. Blocks. |
| `radiusFactor` | `9` | Radius factor. Blocks. |
| `maxItemsBase` | `10` | Base number of separate item drops pulled per pulse for the Rift Void Magnet adaptation. |
| `maxItemsFactor` | `22` | Additional item drops pulled per pulse granted by level for the Rift Void Magnet adaptation. |
| `pulseTicksBase` | `20` | Base Pulse ticks. Server ticks (20 ticks = 1 second). |
| `pulseTicksFactor` | `12` | Pulse ticks factor. Server ticks (20 ticks = 1 second). |
| `xpPerMovedItem` | `0.7` | XP awarded for xp per moved item. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Void Skin (`rift-void-skin`)

Any lethal damage blinks you to a nearby safe spot instead of killing you, using the current world's spawn when no nearby spot exists, and grants brief resistance. Costs an ender pearl and has a long cooldown. Leveling shortens the cooldown and extends the protection.

**Runtime entry points:** on taking damage.

**Menu displays:** Any lethal damage triggers the escape; Escape Cooldown; Costs an Ender Pearl.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RiftVoidSkin` |
| Icon | `ECHO_SHARD` |
| Max level | 4 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 8 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-void-skin.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage
- `PlayerQuitEvent` (`on`)
- `PlayerJoinEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldownBaseMillis` | `120000` | Base cooldown in milliseconds between escapes. |
| `cooldownReductionPerLevelMillis` | `18000` | Cooldown reduction in milliseconds per adaptation level beyond the first. |
| `minimumCooldownMillis` | `45000` | Lowest possible cooldown in milliseconds regardless of level. |
| `resistanceTicksBase` | `60` | Base resistance duration in ticks applied after an escape. |
| `resistanceTicksPerLevel` | `20` | Extra resistance ticks per adaptation level. |
| `resistanceAmplifier` | `2` | Resistance amplifier applied after an escape. |
| `searchRadius` | `9` | Maximum horizontal search radius in blocks for a safe blink spot. |
| `minRadius` | `4` | Minimum horizontal blink distance in blocks. |
| `xpOnEscape` | `40` | Skill XP granted when an escape triggers. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Pearl Rebound (`rift-pearl-rebound`)

A plain thrown ender pearl bounces once instead of teleporting at the first surface it hits. The rebound steers toward your crosshair, then teleports normally at its next impact. Pearl landing damage is also reduced; leveling improves both reduction and steering.

**Runtime entry points:** when launching a projectile; when a projectile hits; on taking damage.

**Menu displays:** Pearl Damage Reduction; Aim Control.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RiftPearlRebound` |
| Icon | `SLIME_BALL` |
| Max level | 4 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 5 |
| Cost factor | 0.35 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-pearl-rebound.toml` |

Listened events:

- `ProjectileLaunchEvent` (`on`) — when launching a projectile
- `ProjectileHitEvent` (`on`) — when a projectile hits
- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageReductionBase` | `0.3` | Fraction of ender pearl teleport damage removed at level 1. |
| `damageReductionPerLevel` | `0.15` | Extra pearl damage reduction fraction per adaptation level beyond the first. |
| `aimBiasBase` | `0.3` | Fraction the rebounded pearl steers toward your look direction at level 1. |
| `aimBiasPerLevel` | `0.15` | Extra aim steering fraction per adaptation level beyond the first. |
| `reboundSpeed` | `1.5` | Launch speed of the rebounded pearl. |
| `xpOnRebound` | `6` | Skill XP granted each time a pearl rebounds. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Rift Conduit (`rift-conduit`)

Sneak-right-click a container with an ender pearl to capture a conduit taglock, then right-click a second container to link them. Items left in one linked container flow into the other when you close it. At max level the two containers can even be in different dimensions.

**Runtime entry points:** on block/entity/air interact (click); on `InventoryCloseEvent`.

**Menu displays:** Items Per Flow; Binding Range; Links across dimensions.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RiftConduit` |
| Icon | `CONDUIT` |
| Max level | 4 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 8 |
| Cost factor | 0.6 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-conduit.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `InventoryCloseEvent` (`on`)
- `PlayerQuitEvent` (`on`)
- `PlayerJoinEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `throughputBase` | `48` | Base number of items moved per flow before level scaling. |
| `throughputFactor` | `336` | Additional items moved per flow granted at max level, scaling with level. |
| `rangeBase` | `24` | Base binding range in blocks before level scaling. |
| `rangeFactor` | `200` | Additional binding range in blocks granted at max level, scaling with level. |
| `crossDimensionAtMax` | `true` | Allows binding across different worlds once the adaptation reaches max level. |
| `xpOnLink` | `30` | Skill XP granted when a new link is formed. |
| `xpPerFlow` | `0.4` | Skill XP granted per item flowed between linked containers. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## Support classes (not player adaptations)

- `RiftAccessViewRegistry` — owns remote-container sessions and the chunk references held while each view remains open.
- `RiftPearls` — distinguishes plain ender pearls from pearls already claimed by a Rift adaptation.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
