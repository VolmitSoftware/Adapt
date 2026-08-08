# Items, Orbs & Bound Objects

Adapt items carry persistent data that identifies their purpose and, where necessary, their target or stored contents. Experience and knowledge orbs are administrative consumables; every other item below belongs to an adaptation and only performs its special behavior for a player who can use that adaptation.

## Experience and knowledge orbs

Both orb types are snowballs. Giving an orb requires `adapt.cheatitem`; throwing it consumes the projectile and applies its stored payload immediately, without requiring the recipient to learn an adaptation.

| Implementation | Command | Payload |
|---|---|---|
| `ExperienceOrb` | `/adapt experience <skill\|all\|random> [amount=10] [player]` | XP entries for the selected skill set |
| `KnowledgeOrb` | `/adapt knowledge <skill\|all\|random> [amount=10] [player]` | Knowledge entries for the selected skill set |

The command gives the target player an encoded orb; the player who throws it receives every skill-map entry stored in that orb. Invalid skill selectors are rejected by the command, and `master` is not a selector; see `04 - Commands & Permissions.md`.

## Adaptation-owned items

| Item implementation | Owning adaptation or workflow | Stored purpose |
|---|---|---|
| `BackpackItem` | Crafting: Backpacks | Portable slot or bundle-weight storage |
| `BoundEnderPearl` | Rift: Ender Taglock, Rift Access, Rift Pearls | Block target used by rift travel |
| `BoundEyeOfEnder` | Rift: Rift Gate | Bound gate location |
| `BoundRedstoneTorch` | Architect: Wireless Redstone | Target location and block face |
| `BoundSnowBall` | Ranged: Web Bomb | Bound player target |
| `ChalkWandItem` | Architect: Chalk Line | Tool type and the current point/plane plan |
| `ChronoTimeBombItem` | Chronos: Time Bomb | Created-time data for the thrown bomb |
| `ChronoTimeBottle` | Chronos: Time in a Bottle | Stored time |
| `OmniTool` | Excavation: Omni Tool | Combined tool state |
| `MultiArmor` | Blocking: Multi Armor | Combined armor state |

Binding, crafting, interaction, cost, protection, and range rules belong to the owning adaptation. These items are covered by `12 - Skill - Architect.md`, `14 - Skill - Blocking.md`, `16 - Skill - Chronos.md`, `17 - Skill - Crafting.md`, `20 - Skill - Excavation.md`, `26 - Skill - Ranged.md`, and `27 - Skill - Rift.md`; possession of an item does not bypass its adaptation, permission, world, or region checks.

`MultiItem` is the shared persistent container used by `OmniTool` and `MultiArmor`: one visible component carries the serialized remaining components, and switching rotates a matching component into the visible slot. Omni Tool is assembled by shift-clicking tools together, automatically selects a suitable tool for block/use actions, and disassembles on sneak-drop. Multi Armor is assembled by left-clicking an elytra and chestplate together, switches between them during movement, and disassembles on sneak-drop; destroying the combined item destroys its bound contents. Capacity and activation details are in the two owning skill pages.

## Backpacks

Crafting: Backpacks registers a shaped recipe with leather in all eight outside cells and a chest in the center. A newly crafted backpack uses the adaptation's configured `slots` and `defaultStorageMode`; right-clicking it opens its storage when the player can use the adaptation.

Backpacks have two persistent modes:

| Mode | Storage model |
|---|---|
| `SLOTS` | Ordinary stacks in 9–54 slots. The configured capacity is clamped and snapped to `9`, `18`, `27`, `36`, `45`, or `54`. |
| `BUNDLE` | Vanilla bundle weights with a paged inventory view. A 64-stackable item costs one weight unit, a 16-stackable item costs four, and an unstackable item costs 64. |

When `allowModeToggle=true`, crafting one empty backpack by itself cycles its mode while preserving the backpack identity; a nonempty backpack cannot be cycled. A backpack can never be deposited directly into another backpack. `denyNestedContainers=true` also rejects shulker boxes or vanilla bundles that contain an Adapt backpack, and `maxStoredBytes` rejects a deposit that would exceed the serialized-data ceiling; the byte ceiling is never lower than 4,096. If the backing backpack disappears or cannot safely receive a write-back while its view is open, Adapt returns recoverable contents to the player instead of silently discarding them.

## Internal ownership data

`ScaffoldMatter` is a persistent codec for temporary scaffold data, not a player-facing item registry. `BrewingStandOwner` and `BrewingStandOwnerMatter` record brewing-stand ownership for the custom brewing workflow; players do not give or use them as held custom items.

## Related pages

- `03 - Player Usage.md`
- `04 - Commands & Permissions.md`
- `37 - Recipes, Brewing & Value.md`
