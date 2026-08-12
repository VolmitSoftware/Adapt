# Items, Orbs & Bound Objects

Adapt's custom items are ordinary Minecraft items with hidden data written onto them. A bound ender pearl is still an ender pearl, a backpack is still a bundle, and a chalk wand is still a stick. What makes them special is the persistent data Adapt reads back: a target block, a stored player, a plan, or a serialized set of contents.

Almost every item here belongs to one adaptation and only does its special thing for a player who can use that adaptation. Hand a bound eye of ender to someone who has not learned Rift Gate and they have an eye of ender. Level, permission, world, and region checks work the same way: the item carries the target, the adaptation carries the rules.

The exceptions are experience and knowledge orbs. Those are admin tools that hand their payload to whoever throws them, with no adaptation required.

## Experience and knowledge orbs

Both orbs are snowballs with a skill-to-amount map written on them. Throwing one gives the thrower everything in that map at once, so an orb made for another player still works in your own hand.

How to use them:

1. Run `/adapt experience <skill|all|random> [amount] [player]` or `/adapt knowledge <skill|all|random> [amount] [player]`. Both need `adapt.cheatitem`.
2. The orb goes to the target player, or to you if you left the player argument off. From console the player argument is required.
3. Throw it. Experience orbs award XP to each listed skill line, knowledge orbs award knowledge points.

`all` writes one entry per registered enabled skill onto a single orb and `random` picks one skill. Anything else has to be a real skill id, and `master` is not one, since master level is derived from skill XP rather than being a skill of its own.

## Items that belong to an adaptation

These items keep their data in the item's persistent data container, so it survives drops, chests, and restarts, and most are stamped with a hidden Curse of Binding purely for the enchant glow. Two of them, the bound redstone torch and the bound eye of ender, declare their own vanilla cooldown group, so putting one on cooldown does not gray out plain redstone torches or eyes of ender in the same inventory.

Binding, crafting, cooldowns, range, and protection rules live with the owning adaptation, covered in `12 - Skill - Architect.md`, `14 - Skill - Blocking.md`, `16 - Skill - Chronos.md`, `17 - Skill - Crafting.md`, `20 - Skill - Excavation.md`, `26 - Skill - Ranged.md`, and `27 - Skill - Rift.md`.

## Omni Tool and Multi Armor

Both work the same way: one visible item carries the others serialized inside it, and switching rotates a stored item into the visible slot. Destroy the combined item and everything inside goes with it.

Omni Tool:

1. Learn Excavation's Omni Tool.
2. Shift-left-click one tool onto another in your inventory to merge them.
3. Use it normally. It rotates a suitable tool into hand for the block or action you are doing.
4. Sneak and drop it to split it back into separate tools.

Multi Armor:

1. Learn Blocking's Multi Armor.
2. Left-click an elytra onto a chestplate, or the reverse, to merge them.
3. It swaps itself as you move: back to the chestplate once you are on the ground, to the elytra once you have fallen more than four blocks.
4. Sneak and drop it to split it back apart.

## Backpacks

Crafting's Backpacks adaptation registers a shaped recipe of leather in all eight outer cells around a chest. The result is a bundle-skinned item that opens its own storage window on right-click for a player who can use the adaptation.

A backpack stays in one of two modes unless you cycle it. `SLOTS` is a plain container where every slot holds one ordinary stack. `BUNDLE` uses vanilla bundle weights with a paged view, where a 64-stackable item costs one weight unit, a 16-stackable costs four, and an unstackable costs 64. New backpacks start in whatever `defaultStorageMode` says. To change it, craft an empty backpack alone in a grid: a shapeless recipe hands the same backpack back with its mode cycled. A backpack with anything in it will not cycle, and `allowModeToggle` turns the whole thing off.

Deposits have three guards. A backpack can never go directly inside another backpack. With `denyNestedContainers` on, a shulker box or vanilla bundle holding a backpack is refused too, scanned four levels deep. And `maxStoredBytes` refuses any deposit that would push the serialized contents past the ceiling. If the backing item disappears or cannot take a write-back while its window is open, Adapt hands the recoverable contents back to the player rather than dropping them.

## Data that is not an item

Some persistent Adapt data looks item-shaped but is never held by a player. `ScaffoldMatter` in `content/block` stores temporary scaffold data, and `BrewingStandOwner` with `BrewingStandOwnerMatter` in `content/matter` record brewing-stand ownership for the custom brewing workflow. None are giveable items.

## Reference

### Orbs

| Implementation | Base material | Command | Permission | Payload |
|---|---|---|---|---|
| `ExperienceOrb` | `SNOWBALL` | `/adapt experience <skill\|all\|random> [amount=10] [player]` | `adapt.cheatitem` | Skill to XP map, applied to the thrower |
| `KnowledgeOrb` | `SNOWBALL` | `/adapt knowledge <skill\|all\|random> [amount=10] [player]` | `adapt.cheatitem` | Skill to knowledge map, applied to the thrower |

Both apply on projectile launch, to the player who threw the orb, with no adaptation or learning requirement.

### Adaptation-owned items

| Implementation | Base material | Owning adaptations | Stored data |
|---|---|---|---|
| `BackpackItem` | `BUNDLE` | Crafting: Backpacks. Read by Architect: Supply Line. | Backpack id, mode, capacity, used amount, plus the serialized contents under a separate key |
| `BoundEnderPearl` | `ENDER_PEARL` | Rift: Ender Taglock, Rift Access, Rift Pearls | Target block |
| `BoundEyeOfEnder` | `ENDER_EYE` | Rift: Rift Gate | Bound location |
| `BoundRedstoneTorch` | `REDSTONE_TORCH` | Architect: Wireless Redstone | Target location and block face |
| `BoundSnowBall` | `SNOWBALL` | Ranged: Web Bomb | Bound player |
| `ChalkWandItem` | `STICK` | Architect: Chalk Line, Chalk Geometry | Tool id, world, up to 32 control points, plane |
| `ChronoTimeBombItem` | `LINGERING_POTION` | Chronos: Time Bomb. Checked by Chronos: Instant Recall. | Creation timestamp. Legacy `CLOCK` bombs are still recognized. |
| `ChronoTimeBottle` | `POTION` | Chronos: Time in a Bottle | Stored seconds |
| `OmniTool` | The visible tool | Excavation: Omni Tool | Serialized remaining tools |
| `MultiArmor` | The visible piece | Blocking: Multi Armor | Serialized remaining pieces |

`BoundRedstoneTorch` and `BoundEyeOfEnder` declare item cooldown groups. `OmniTool` and `MultiArmor` both implement the shared `MultiItem` container.

### Backpack config (`CraftingBackpacks`)

| Key | Default | What it does |
|---|---|---|
| `slots` | `9` | Capacity in stacks. Snapped to 9, 18, 27, 36, 45, or 54, and clamped into that range. In `SLOTS` mode it is the slot count; in `BUNDLE` mode it is the weight budget in stacks. |
| `defaultStorageMode` | `SLOTS` | Mode a newly crafted backpack starts in. Anything unrecognized falls back to `SLOTS`. |
| `allowModeToggle` | `true` | Allows cycling an empty backpack's mode by crafting it alone |
| `maxStoredBytes` | `262144` | Serialized-contents ceiling per backpack, in bytes. Raised to 4,096 if configured lower. |
| `denyNestedContainers` | `true` | Refuses depositing a shulker box or vanilla bundle that itself contains a backpack, scanned 4 levels deep |

Bundle weight units: 64 weight per stack budget, so a 64-stackable item costs 1 per item, a 16-stackable costs 4, and an unstackable costs 64.

## See also

- `03 - Player Usage.md`
- `04 - Commands & Permissions.md`
- `37 - Recipes, Brewing & Value.md`
