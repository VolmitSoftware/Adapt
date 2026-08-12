# Skill: Crafting

Skill id `crafting`. Earn XP by crafting items. Crafting has 14 registered adaptations and uses the `CRAFTING_TABLE` icon.

**XP sources:** taking crafted results and completing eligible furnace smelts.

**Milestones / challenges** (stat keys):

- `challenge_craft_1k` tracking `crafted.items`
- `challenge_craft_5k` tracking `crafted.items`
- `challenge_craft_50k` tracking `crafted.items`
- `challenge_craft_value_10k` tracking `crafted.value`
- `challenge_craft_value_100k` tracking `crafted.value`
- `challenge_craft_tools_25` tracking `crafting.tools`
- `challenge_craft_tools_250` tracking `crafting.tools`
- `challenge_craft_armor_25` tracking `crafting.armor`
- `challenge_craft_armor_250` tracking `crafting.armor`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `crafting` |
| Class | `SkillCrafting` |
| Icon | `CRAFTING_TABLE` |
| Color | `YELLOW` |
| Interval (ms) | `3789` |
| Skill config | `plugins/Adapt/adapt/skills/crafting.toml` |
| Adaptation count | 14 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/crafting.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&e"` | Legacy ampersand color code used for this skill in menus and text. |
| `furnaceBaseXP` | `30` | Base skill XP credited for furnace base. |
| `furnaceValueXPMultiplier` | `4` | Unitless multiplier applied to XP from furnace value multiplier. |
| `furnaceXPRadius` | `32` | Radius in blocks used to find eligible furnace-XP recipients. |
| `cooldownDelay` | `3000` | Minimum delay between passive skill XP awards, in milliseconds. |
| `furnaceXPDuration` | `10000` | XP awarded for furnace duration. |
| `furnaceXpCooldown` | `10000` | Cooldown in milliseconds between spatial XP pulses per furnace. |
| `craftingValueXPMultiplier` | `2.0` | Unitless multiplier applied to XP from crafting value multiplier. |
| `baseCraftingXP` | `3.0` | Base skill XP credited for base crafting. |
| `challengeCraft1kReward` | `1200` | Reward for the craft 1 k challenge. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Deconstruction (`crafting-deconstruction`)

Deconstruct blocks & items into salvageable base components.
The targeted dropped item must be eligible for that player and pass Bukkit's normal pickup-event sequence
before it is replaced; denial leaves the item, shears, XP, and statistics unchanged. Folia requires the player
and item entity to share the current owning region and the complete six-block ray footprint to be owned.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 5590 ms.

**Menu displays:** Drop an item, then sneak-right-click it with shears.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingDeconstruction` |
| Icon | `SHEARS` |
| Max level | 1 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 9 |
| Cost factor | 1.0 |
| Tick interval (ms) | 5590 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-deconstruction.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Crafting XP (`crafting-xp`)

Gain passive XP when crafting.

**Runtime entry points:** when taking a craft result; periodic evaluation every 5580 ms.

**Menu displays:** Gain XP when crafting.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingXP` |
| Icon | `ENCHANTED_BOOK` |
| Max level | 7 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.3 |
| Tick interval (ms) | 5580 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-xp.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Craftable Leather (`crafting-leather`)

Craft Leather from Rotten Flesh.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 17776 ms.

**Menu displays:** Throw rotten flesh onto a campfire.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingLeather` |
| Icon | `LEATHER` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 17776 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-leather.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Craftable Skulls (`crafting-skulls`)

Unlocks recipes for mob skulls.

**Runtime entry points:** when taking a craft result; periodic evaluation every 17776 ms.

**Menu displays:** Recipes for zombie, skeleton, creeper, wither skeleton, and dragon heads.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingSkulls` |
| Icon | `SKELETON_SKULL` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 8 |
| Cost factor | 1 |
| Tick interval (ms) | 17776 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-skulls.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Backpacks (`crafting-backpacks`)

Craft a Backpack that stores whole stacks and opens as its own container.

**Runtime entry points:** on block/entity/air interact (click); on inventory click; on `InventoryDragEvent`; on `InventoryCloseEvent`; on player death; on `PlayerItemHeldEvent`; on drop item; on swap hands (F).

**Menu displays:** Backpack opening control and the leather-and-chest crafting recipe.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingBackpacks` |
| Icon | `BUNDLE` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 5 |
| Cost factor | 1 |
| Tick interval (ms) | 17779 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-backpacks.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `InventoryClickEvent` (`on`) — on inventory click
- `InventoryDragEvent` (`on`)
- `InventoryCloseEvent` (`on`)
- `PlayerDeathEvent` (`on`) — on player death
- `PlayerQuitEvent` (`on`)
- `PlayerItemHeldEvent` (`on`)
- `PlayerDropItemEvent` (`on`) — on drop item
- `PlayerSwapHandItemsEvent` (`on`) — on swap hands (F)
- `PrepareItemCraftEvent` (`on`) — while crafting
- `CraftItemEvent` (`on`) — when taking a craft result

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `slots` | `9` | Backpack capacity. Only container representable sizes are valid: 9, 18, 27, 36, 45 and 54. Any other value snaps to the nearest valid size, and anything under 9 or over 54 clamps to 9 or 54. In slot mode this is the number of slots and every slot holds one ordinary stack, so 9 slots is 9 stacks. In bundle mode this is how many stacks worth of weight the backpack holds, using vanilla bundle weights where an item that stacks to 64 costs 1, an item that stacks to 16 costs 4, and an unstackable item costs 64. |
| `defaultStorageMode` | `"SLOTS"` | Storage mode a newly crafted Backpack starts in. SLOTS gives one ordinary stack per slot and always shows every stored stack in one view. BUNDLE gives vanilla bundle weight semantics with a paged projection of the stored stacks. Any other value falls back to SLOTS. |
| `allowModeToggle` | `true` | Allows a player to switch a Backpack between slot mode and bundle mode by crafting it alone in a crafting grid. The Backpack has to be empty before it will switch. |
| `maxStoredBytes` | `262144` | Maximum serialized size in bytes of the contents stored on one Backpack. Deposits that would push the stored data past this limit are refused, and anything already past it is handed back to the player instead of being dropped. |
| `denyNestedContainers` | `true` | Denies depositing a shulker box or a vanilla bundle that itself contains an Adapt Backpack. A Backpack can never be put directly inside another Backpack regardless of this setting. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Portable Tables (`crafting-stations`)

Click the air while holding an anvil, crafting table, grindstone, stonecutter, cartography table, or loom to open it without placing it. Each open costs hunger.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 9248 ms.

**Menu displays:** Items left in a portable table are lost when it closes; valid stations are anvil, crafting table, grindstone, cartography table, stonecutter, and loom; hunger cost per station opened.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingStations` |
| Icon | `CRAFTING_TABLE` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 5 |
| Cost factor | 1 |
| Tick interval (ms) | 9248 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-stations.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldown` | `125` | Cooldown. Server ticks (20 ticks = 1 second). |
| `hungerCost` | `2` | Hunger points consumed each time a portable station is opened. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Ore Reconstruction (`crafting-reconstruction`)

Recraft ores from their base components.

**Runtime entry points:** when taking a craft result; periodic evaluation every 80248 ms.

**Menu displays:** Eight drops and one host make one ore in a shapeless recipe; drops must be smelted when applicable; scraps, quartz, and emeralds are excluded; the host is the encasing block, such as stone, netherrack, or deepslate.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingReconstruction` |
| Icon | `COAL_ORE` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 5 |
| Cost factor | 1 |
| Tick interval (ms) | 80248 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-reconstruction.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bulk Artisan (`crafting-bulk-artisan`)

Shift-click a craft result to pull extra ingredients from your inventory and craft a bigger batch at once.

**Runtime entry points:** when taking a craft result.

**Menu displays:** Shift-click also pulls matching ingredients from your inventory; bonus items per shift-craft.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingBulkArtisan` |
| Icon | `CRAFTING_TABLE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.3 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-bulk-artisan.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `batchCapBase` | `32` | Bonus crafted items available at level 1 per shift-craft. |
| `batchCapFactor` | `96` | Additional bonus crafted items granted across levels. |
| `xpPerBatchItem` | `0.5` | Skill XP granted per bonus item produced. |
| `throttleMs` | `250` | Minimum delay in milliseconds between bulk batches. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Thrifty Hands (`crafting-thrifty-hands`)

Every craft has a chance to refund one of its ingredients.

**Runtime entry points:** when taking a craft result.

**Menu displays:** Chance to refund a crafting ingredient.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingThriftyHands` |
| Icon | `STRING` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.3 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-thrifty-hands.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `refundChanceBase` | `0.15` | Refund chance at level 1. |
| `refundChanceFactor` | `0.5` | Additional refund chance gained across levels. |
| `refundChanceMax` | `0.6` | Maximum refund chance at full level. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Masterwork (`crafting-masterwork`)

Tools and armor you craft can roll bonus durability, with a chance for a minor attribute bonus at full level.

**Runtime entry points:** when taking a craft result.

**Menu displays:** Masterwork chance; maximum-durability bonus; full-level minor-attribute chance.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingMasterwork` |
| Icon | `NETHERITE_INGOT` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-masterwork.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rollChanceBase` | `0.2` | Chance to roll a masterwork piece at level 1. |
| `rollChanceFactor` | `0.55` | Additional masterwork roll chance gained across levels. |
| `rollChanceMax` | `0.75` | Maximum masterwork roll chance at full level. |
| `bonusPercentBase` | `0.1` | Fraction of base durability granted as a bonus at level 1. |
| `bonusPercentFactor` | `0.4` | Additional bonus durability fraction gained across levels. |
| `attributeChance` | `0.15` | Chance for a minor attribute bonus at full level. |
| `attackDamageBonus` | `1.0` | Attack damage granted by a masterwork tool attribute bonus. |
| `armorBonus` | `1.0` | Armor granted by a masterwork armor attribute bonus. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Compactor (`crafting-compactor`)

Sneak and swap hands while aiming at a Crafting Table to compact full stacks into blocks immediately.

**Runtime entry points:** on swap hands (F); periodic evaluation every 2000 ms.

**Menu displays:** Sneak + swap hands while aiming at a Crafting Table; materials compacted into block form; Only acts on full stacks, leaving loose remainders.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingCompactor` |
| Icon | `IRON_BLOCK` |
| Max level | 1 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.3 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-compactor.toml` |

Listened events:

- `PlayerSwapHandItemsEvent` (`on`) — on swap hands (F)
- `PlayerJoinEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Tinkerer (`crafting-tinkerer`)

Combine two damaged tools of the same type in the crafting grid to keep their best enchantments.

**Runtime entry points:** when taking a craft result.

**Menu displays:** Grid-repair two matching tools to keep the better enchantments; chance to preserve every enchantment; Otherwise a single random enchantment is lost.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingTinkerer` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-tinkerer.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `preserveChanceBase` | `0.4` | Chance to preserve every enchantment at level 1. |
| `preserveChanceFactor` | `0.6` | Additional preservation chance gained across levels. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Provisioner (`crafting-provisioner`)

Crafting or cooking food has a chance to yield bonus portions.

**Runtime entry points:** when taking a craft result; on `FurnaceSmeltEvent`.

**Menu displays:** Bonus-food chance and portions produced per activation.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingProvisioner` |
| Icon | `COOKED_BEEF` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.3 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-provisioner.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result
- `FurnaceSmeltEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusChanceBase` | `0.25` | Bonus portion chance at level 1. |
| `bonusChanceFactor` | `0.5` | Additional bonus portion chance gained across levels. |
| `bonusChanceMax` | `0.75` | Maximum bonus portion chance at full level. |
| `bonusPortionsBase` | `1` | Bonus portions granted at level 1. |
| `bonusPortionsFactor` | `2` | Additional bonus portions gained across levels. |
| `cookingRadius` | `8.0` | Radius in blocks searched for a player when cooking food. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Artisan's Signature (`crafting-signature`)

Items you craft carry your signature, and villagers offer better trades while you carry your signed goods.

**Runtime entry points:** when taking a craft result; on entity right-click.

**Menu displays:** Crafted items are signed with your name; Villagers offer better trades while you carry signed goods.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `CraftingSignature` |
| Icon | `WRITABLE_BOOK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.3 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-signature.toml` |

Listened events:

- `CraftItemEvent` (`on`) — when taking a craft result
- `PlayerInteractEntityEvent` (`on`) — on entity right-click

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `amplifierBase` | `0` | Trade discount amplifier at level 1. |
| `amplifierFactor` | `1` | Additional trade discount amplifier gained across levels. |
| `amplifierMax` | `1` | Maximum trade discount amplifier. |
| `tradeDurationTicks` | `200` | Duration in ticks of the trade discount applied on interacting with a villager. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## Support classes (not player adaptations)

- `CraftingIngredients` — calculates the per-craft ingredient counts used by bulk and refund behavior.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
