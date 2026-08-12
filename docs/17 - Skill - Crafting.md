# Skill: Crafting

Crafting is the workbench skill. You level it by pulling results out of a crafting grid and by running furnaces. Craft XP scales with the value of what you made, so a stack of sticks is worth much less than a diamond pickaxe, and a flat bonus is added on top of every craft. Furnace XP is granted at the furnace itself and reaches any Adapt player standing within range, which means a smelting room pays the whole base.

Both XP paths are throttled. One player can only be paid for a craft once every few seconds, and each individual furnace has its own cooldown, so an AFK auto-smelter does not print XP.

The 14 adaptations cover three jobs. Some unlock recipes that do not exist in vanilla: leather from rotten flesh on a campfire, mob heads, ores rebuilt from their drops, and a backpack. Some give you tools that save trips: portable crafting stations you open from your hand, a compactor bound to a crafting table, and shears that tear dropped items back into components. The rest quietly improve the crafts you were making anyway, with refunds, bigger batches, better gear, and bonus food. Five of them ship marked permanent in config, so once learned they stay learned.

## Adaptations

Everything below only runs when you have learned the adaptation to level 1 or higher, the skill and the adaptation are both enabled in config, you hold the `adapt.use` permission, and any protection plugin or region policy allows the action. Those conditions are not repeated per entry.

### Deconstruction (`crafting-deconstruction`)

Shears that work backwards. Point them at an item lying on the ground and it comes apart into the components it was made from. It is the answer to a chest full of half-useful crafted junk.

**How to use it**

1. Learn it in the Adapt menu.
2. Drop the item you want to break down.
3. Hold shears in your main hand, sneak, and right-click the dropped item.

The dropped item has to be one you are allowed to pick up, and it goes through Bukkit's normal pickup event sequence before it is replaced. If anything denies that, the item, your shears, your XP, and your stats are all left alone. On Folia the player and the item entity must be on the same owning region, and the whole six-block ray has to be region-owned.

### Crafting XP (`crafting-xp`)

Extra passive skill XP whenever you take a craft result. Nothing to aim, nothing to trigger. It goes to seven levels rather than five, so it is the cheap long-term investment for anyone who crafts a lot.

Works on its own once learned.

### Craftable Leather (`crafting-leather`)

Rotten flesh becomes useful. Cook it on a campfire and you get leather. Zombie farms turn into a leather supply.

**How to use it**

1. Learn it in the Adapt menu.
2. Hold rotten flesh.
3. Right-click a campfire to put it on.

Without the adaptation the click is cancelled and the campfire hisses at you.

### Craftable Skulls (`crafting-skulls`)

Unlocks shaped recipes for mob heads. Every one is a ring of eight of one material around a bone block: bones for a skeleton skull, nether bricks for a wither skeleton skull, rotten flesh for a zombie head, gunpowder for a creeper head, and dragon breath for a dragon head. Decorating no longer requires a charged creeper or a dead dragon.

Works on its own once learned. Craft the recipes.

### Backpacks (`crafting-backpacks`)

A craftable container you carry. It opens as its own inventory rather than taking a slot per item, and it has two storage modes. Slot mode gives you one ordinary stack per slot and shows everything in one view. Bundle mode uses vanilla bundle weights, where a 64-stackable item costs 1 and an unstackable item costs 64, and pages the view.

You can flip a backpack between modes by crafting it alone in a grid, as long as it is empty. Backpacks cannot be nested, and by default a shulker box or vanilla bundle holding a backpack cannot be put inside one either.

**How to use it**

1. Learn it in the Adapt menu.
2. Craft it from leather and a chest.
3. Right-click with it in hand to open it.
4. Craft it alone in a grid to switch storage modes, while it is empty.

### Portable Tables (`crafting-stations`)

Open a station straight out of your hand instead of placing it. Anvil, crafting table, grindstone, stonecutter, cartography table, and loom all work. Each open costs food, and the item goes on a short cooldown afterwards, so it is convenience rather than a free workshop.

**How to use it**

1. Learn it in the Adapt menu.
2. Hold the station block in your main hand.
3. Right-click the air, left-click the air, or left-click a block.

Anything left inside a portable station is lost when it closes. Not enough food and the click just puffs smoke.

### Ore Reconstruction (`crafting-reconstruction`)

Turns drops back into ore blocks. Every recipe is shapeless: eight of the drop plus one host block gives one ore. The host is whatever the ore is normally encased in, so stone for overworld ores, deepslate for the deepslate variants, and nether bricks for nether gold, nether quartz, and ancient debris.

The in-game lore says scraps, quartz, and emeralds are excluded. That text is out of date. Emerald ore, deepslate emerald ore, nether quartz ore, and ancient debris from netherite scraps all have working recipes.

Works on its own once learned. Craft the recipes.

### Bulk Artisan (`crafting-bulk-artisan`)

Shift-clicking a result normally only crafts what is already in the grid. With this, the shift-click reaches into your inventory, pulls out matching ingredients, and crafts a much bigger batch in one action. The batch cap grows with level.

**How to use it**

1. Learn it in the Adapt menu.
2. Set up the recipe in a crafting grid.
3. Shift-click the result.

### Thrifty Hands (`crafting-thrifty-hands`)

Every craft has a chance to hand one ingredient back. It is small at level 1 and climbs toward a cap, and over a long crafting session it adds up to real material saved.

Works on its own once learned.

### Masterwork (`crafting-masterwork`)

Tools and armor you craft can come out better than they should. A masterwork roll adds a fraction of the item's base durability on top. At full level there is a further chance for a small attribute bonus: attack damage on a tool, armor on a piece of armor.

Works on its own once learned.

### Compactor (`crafting-compactor`)

A one-gesture way to squash loose materials into blocks. Look at a crafting table, sneak, tap swap hands, and every full stack in your inventory that has a block form gets compacted. It deliberately ignores partial stacks, so you keep your loose remainders.

**How to use it**

1. Learn it in the Adapt menu.
2. Stand within 5 blocks of a crafting table and look at it, with no container open.
3. Sneak and press the swap-hands key (F by default).

### Tinkerer (`crafting-tinkerer`)

Grid-repairing two damaged tools of the same type normally throws away most of the enchantments. With Tinkerer there is a chance to keep all of them, and when the roll fails you only lose one enchantment at random rather than the lot. The chance climbs with level.

Works on its own once learned.

### Provisioner (`crafting-provisioner`)

Food multiplies. Crafting food or smelting it in a furnace has a chance to produce bonus portions on top of the normal output. Cooked food looks for a nearby player to credit, so you have to be somewhere near the furnace.

Works on its own once learned.

### Artisan's Signature (`crafting-signature`)

Items you craft get stamped with your name in their lore and a hidden signature. Walk up to a villager while carrying your own signed goods and you get Hero of the Village for a moment, which is what makes the trades cheaper.

**How to use it**

1. Learn it in the Adapt menu.
2. Craft something. It is signed automatically.
3. Carry signed goods and right-click a villager before trading.

The effect is skipped if you already have Hero of the Village from any source.

## Reference

Every adaptation config file also carries the shared keys `enabled`, `permanent`, `showParticles`, and `showSounds`.

### Identity

| Property | Value |
|----------|-------|
| Skill id | `crafting` |
| Class | `SkillCrafting` |
| Icon | `CRAFTING_TABLE` |
| Color | `YELLOW` |
| Interval (ms) | `3789` |
| Skill config | `plugins/Adapt/adapt/skills/crafting.toml` |
| Adaptation count | 14 |

### XP sources

Taking a craft result pays `amount * itemValue * craftingValueXPMultiplier + baseCraftingXP` and adds to `crafted.items` and `crafted.value`, plus `crafting.tools` for pickaxes, axes, shovels, hoes, and swords, plus `crafting.armor` for helmets, chestplates, leggings, and boots. It is gated by `cooldownDelay` per player.

A `FurnaceSmeltEvent` pays `furnaceBaseXP + resultValue * furnaceValueXPMultiplier`, granted spatially within `furnaceXPRadius` for `furnaceXPDuration`, and sets no stats. It is gated by `furnaceXpCooldown` per furnace block, keyed by world and coordinates.

### Milestones

| Advancement key | Stat key | Threshold | Reward |
|-----------------|----------|-----------|--------|
| `challenge_craft_1k` | `crafted.items` | 1000 | `challengeCraft1kReward` |
| `challenge_craft_5k` | `crafted.items` | 5000 | `challengeCraft1kReward` |
| `challenge_craft_50k` | `crafted.items` | 50000 | `challengeCraft1kReward` |
| `challenge_craft_value_10k` | `crafted.value` | 10000 | `challengeCraft1kReward` |
| `challenge_craft_value_100k` | `crafted.value` | 100000 | `challengeCraft1kReward` x2 |
| `challenge_craft_tools_25` | `crafting.tools` | 25 | `challengeCraft1kReward` |
| `challenge_craft_tools_250` | `crafting.tools` | 250 | `challengeCraft1kReward` x2 |
| `challenge_craft_armor_25` | `crafting.armor` | 25 | `challengeCraft1kReward` |
| `challenge_craft_armor_250` | `crafting.armor` | 250 | `challengeCraft1kReward` x2 |

The three `crafted.items` tiers all pay the same reward, unlike the other chains where the second tier doubles.

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/crafting.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Turns the whole skill on or off. |
| `skillColor` | `"&e"` | Legacy ampersand color code for this skill in menus and text. |
| `furnaceBaseXP` | `30` | Flat XP granted for a furnace smelt, before item value. |
| `furnaceValueXPMultiplier` | `4` | Multiplier applied to the smelted result's value when adding to furnace XP. |
| `furnaceXPRadius` | `32` | Blocks from the furnace within which players receive the XP. |
| `cooldownDelay` | `3000` | Minimum milliseconds between craft XP awards for one player. |
| `furnaceXPDuration` | `10000` | Milliseconds the furnace XP pulse stays claimable by players in range. |
| `furnaceXpCooldown` | `10000` | Milliseconds before the same furnace block can pay out again. |
| `craftingValueXPMultiplier` | `2.0` | Multiplier applied to crafted item value for both XP and the `crafted.value` stat. |
| `baseCraftingXP` | `3.0` | Flat XP added on top of value XP for every paying craft. |
| `challengeCraft1kReward` | `1200` | Base knowledge reward for the Crafting challenge chains. |

### Deconstruction

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

Milestones: `challenge_crafting_decon_200` and `challenge_crafting_decon_5k` on `crafting.deconstruction.items-deconstructed` at 200 and 5000, rewarding 300 and 1000.

Listened events:

- `PlayerInteractEvent` (`on`): sneak plus right-click with shears, ray-traced up to six blocks

### Crafting XP

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

Milestones: `challenge_crafting_xp_1k` and `challenge_crafting_xp_25k` on `crafting.xp.items-crafted` at 1000 and 25000, rewarding 300 and 1500.

Listened events:

- `CraftItemEvent` (`on`): taking a craft result

### Craftable Leather

| Property | Default |
|----------|---------|
| Class | `CraftingLeather` |
| Icon | `LEATHER` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Permanent | `true` |
| Tick interval (ms) | 17776 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-leather.toml` |

Recipe key `crafting-leather`, campfire: `ROTTEN_FLESH` to `LEATHER`, cook time 100 ticks, 1 vanilla experience.

Milestone: `challenge_crafting_leather_100` on `crafting.leather.leather-crafted` at 100, rewarding 300.

Listened events:

- `PlayerInteractEvent` (`on`): rotten flesh onto a `CAMPFIRE`

### Craftable Skulls

| Property | Default |
|----------|---------|
| Class | `CraftingSkulls` |
| Icon | `SKELETON_SKULL` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 8 |
| Cost factor | 1 |
| Permanent | `true` |
| Tick interval (ms) | 17776 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-skulls.toml` |

Five shaped recipes, each eight of a ring material around one `BONE_BLOCK`: `crafting-skeletonskull` (`BONE` to `SKELETON_SKULL`), `crafting-witherskeletonskull` (`NETHER_BRICK` to `WITHER_SKELETON_SKULL`), `crafting-zombieskull` (`ROTTEN_FLESH` to `ZOMBIE_HEAD`), `crafting-creeperhead` (`GUNPOWDER` to `CREEPER_HEAD`), and `crafting-dragonhead` (`DRAGON_BREATH` to `DRAGON_HEAD`).

Milestones: `challenge_crafting_skulls_10` and `challenge_crafting_skulls_100` on `crafting.skulls.skulls-crafted` at 10 and 100, rewarding 300 and 1000.

Listened events:

- `CraftItemEvent` (`on`): taking a craft result

### Backpacks

| Property | Default |
|----------|---------|
| Class | `CraftingBackpacks` |
| Icon | `BUNDLE` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 5 |
| Cost factor | 1 |
| Permanent | `true` |
| Tick interval (ms) | 17779 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-backpacks.toml` |

Two registered recipes: a shaped craft from `LEATHER` and `CHEST`, and a shapeless single-`BUNDLE` recipe used for the mode cycle. The cycle recipe is a real registered recipe on purpose, so the swap consumes exactly one backpack and returns exactly one instead of duplicating it.

Milestone: `challenge_crafting_backpack_25` on `crafting.backpacks.bundles-crafted` at 25, rewarding 300.

Listened events:

- `PlayerInteractEvent` (`on`): opens the backpack
- `InventoryClickEvent` (`on`)
- `InventoryDragEvent` (`on`)
- `InventoryCloseEvent` (`on`)
- `PlayerDeathEvent` (`on`)
- `PlayerQuitEvent` (`on`)
- `PlayerItemHeldEvent` (`on`)
- `PlayerDropItemEvent` (`on`)
- `PlayerSwapHandItemsEvent` (`on`)
- `PrepareItemCraftEvent` (`on`): mode cycle preview
- `CraftItemEvent` (`on`): craft and mode cycle

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `slots` | `9` | Backpack capacity. Only container-representable sizes are valid: 9, 18, 27, 36, 45, and 54. Any other value snaps to the nearest valid size, and anything under 9 or over 54 clamps to 9 or 54. In slot mode this is the number of slots and every slot holds one ordinary stack, so 9 slots is 9 stacks. In bundle mode it is how many stacks worth of weight the backpack holds, using vanilla bundle weights where an item that stacks to 64 costs 1, an item that stacks to 16 costs 4, and an unstackable item costs 64. |
| `defaultStorageMode` | `"SLOTS"` | Mode a newly crafted backpack starts in. `SLOTS` gives one ordinary stack per slot in one view. `BUNDLE` gives vanilla bundle weight semantics with a paged view. Any other value falls back to `SLOTS`. |
| `allowModeToggle` | `true` | Lets a player switch a backpack between modes by crafting it alone in a grid. The backpack has to be empty first. |
| `maxStoredBytes` | `262144` | Maximum serialized size in bytes of one backpack's contents. Deposits that would exceed it are refused, and anything already over is handed back to the player rather than dropped. |
| `denyNestedContainers` | `true` | Refuses deposits of a shulker box or vanilla bundle that itself contains an Adapt backpack. A backpack can never go directly inside another backpack regardless of this setting. |

### Portable Tables

| Property | Default |
|----------|---------|
| Class | `CraftingStations` |
| Icon | `CRAFTING_TABLE` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 5 |
| Cost factor | 1 |
| Permanent | `true` |
| Tick interval (ms) | 9248 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-stations.toml` |

Recognised held items and the inventory each opens: `CRAFTING_TABLE` to `WORKBENCH`, `GRINDSTONE` to `GRINDSTONE`, `ANVIL` to `ANVIL`, `STONECUTTER` to `STONECUTTER`, `CARTOGRAPHY_TABLE` to `CARTOGRAPHY`, `LOOM` to `LOOM`. Main hand only.

Milestones: `challenge_crafting_stations_200` and `challenge_crafting_stations_5k` on `crafting.stations.portable-opens` at 200 and 5000, rewarding 300 and 1000.

Listened events:

- `PlayerInteractEvent` (`on`): right-click air, left-click air, or left-click block

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldown` | `125` | Vanilla item cooldown applied to the station item after an open, in server ticks (20 ticks = 1 second). |
| `hungerCost` | `2` | Food points spent per open. The open is refused if your food level is below this. |

### Ore Reconstruction

| Property | Default |
|----------|---------|
| Class | `CraftingReconstruction` |
| Icon | `COAL_ORE` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 5 |
| Cost factor | 1 |
| Permanent | `true` |
| Tick interval (ms) | 80248 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-reconstruction.toml` |

Nineteen shapeless recipes, each one host block plus eight drops. `STONE` hosts `IRON_INGOT`, `GOLD_INGOT`, `COPPER_INGOT`, `LAPIS_LAZULI`, `REDSTONE`, `EMERALD`, `DIAMOND`, and `COAL` into the matching plain ore. `DEEPSLATE` hosts the same eight into the matching `DEEPSLATE_*` ore. `NETHER_BRICKS` hosts `GOLD_INGOT` into `NETHER_GOLD_ORE`, `QUARTZ` into `NETHER_QUARTZ_ORE`, and `NETHERITE_SCRAP` into `ANCIENT_DEBRIS`.

Milestone: `challenge_crafting_recon_100` on `crafting.reconstruction.ores-reconstructed` at 100, rewarding 300.

Listened events:

- `CraftItemEvent` (`on`): taking a craft result

### Bulk Artisan

| Property | Default |
|----------|---------|
| Class | `CraftingBulkArtisan` |
| Icon | `CRAFTING_TABLE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.3 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-bulk-artisan.toml` |

Milestones: `challenge_crafting_bulk_1k` and `challenge_crafting_bulk_10k` on `crafting.bulk-artisan.batch-crafted` at 1000 and 10000, rewarding 400 and 1500.

Listened events:

- `CraftItemEvent` (`on`): shift-click on a craft result

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `batchCapBase` | `32` | Bonus items a shift-craft can add at level 1. |
| `batchCapFactor` | `96` | Additional bonus items unlocked across the level range. |
| `xpPerBatchItem` | `0.5` | Skill XP per bonus item produced. |
| `throttleMs` | `250` | Minimum milliseconds between bulk batches for one player. |

### Thrifty Hands

| Property | Default |
|----------|---------|
| Class | `CraftingThriftyHands` |
| Icon | `STRING` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.3 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-thrifty-hands.toml` |

Milestones: `challenge_crafting_thrifty_500` and `challenge_crafting_thrifty_5k` on `crafting.thrifty-hands.ingredients-refunded` at 500 and 5000, rewarding 400 and 1500.

Listened events:

- `CraftItemEvent` (`on`): taking a craft result

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `refundChanceBase` | `0.15` | Chance to refund an ingredient at level 1, 0-1. |
| `refundChanceFactor` | `0.5` | Additional refund chance gained across the level range. |
| `refundChanceMax` | `0.6` | Ceiling on the refund chance. |

### Masterwork

| Property | Default |
|----------|---------|
| Class | `CraftingMasterwork` |
| Icon | `NETHERITE_INGOT` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-masterwork.toml` |

Milestones: `challenge_crafting_masterwork_50` and `challenge_crafting_masterwork_500` on `crafting.masterwork.pieces-forged` at 50 and 500, rewarding 400 and 1500.

Listened events:

- `CraftItemEvent` (`on`): taking a craft result

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rollChanceBase` | `0.2` | Chance a crafted tool or armor piece rolls masterwork at level 1, 0-1. |
| `rollChanceFactor` | `0.55` | Additional roll chance gained across the level range. |
| `rollChanceMax` | `0.75` | Ceiling on the masterwork roll chance. |
| `bonusPercentBase` | `0.1` | Fraction of base durability added by a masterwork roll at level 1. |
| `bonusPercentFactor` | `0.4` | Additional durability fraction gained across the level range. |
| `attributeChance` | `0.15` | Chance a full-level masterwork roll also grants an attribute bonus, 0-1. |
| `attackDamageBonus` | `1.0` | Attack damage added by that bonus on a tool. |
| `armorBonus` | `1.0` | Armor added by that bonus on an armor piece. |

### Compactor

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

Activation requires all four: sneaking, an active level above 0, no container open beyond the default inventory view, and a `CRAFTING_TABLE` as the exact target block within 5 blocks. Stored levels above 1 are normalized back down on join.

Milestones: `challenge_crafting_compactor_1k` and `challenge_crafting_compactor_10k` on `crafting.compactor.blocks-compacted` at 1000 and 10000, rewarding 400 and 1500.

Listened events:

- `PlayerSwapHandItemsEvent` (`on`): the compact gesture
- `PlayerJoinEvent` (`on`): level normalization

### Tinkerer

| Property | Default |
|----------|---------|
| Class | `CraftingTinkerer` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-tinkerer.toml` |

Milestones: `challenge_crafting_tinkerer_50` and `challenge_crafting_tinkerer_500` on `crafting.tinkerer.tools-tinkered` at 50 and 500, rewarding 400 and 1500.

Listened events:

- `CraftItemEvent` (`on`): grid repair of two matching tools

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `preserveChanceBase` | `0.4` | Chance to keep every enchantment at level 1, 0-1. When the roll fails, one random enchantment is dropped instead. |
| `preserveChanceFactor` | `0.6` | Additional preservation chance gained across the level range. |

### Provisioner

| Property | Default |
|----------|---------|
| Class | `CraftingProvisioner` |
| Icon | `COOKED_BEEF` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.3 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-provisioner.toml` |

Milestones: `challenge_crafting_provisioner_500` and `challenge_crafting_provisioner_5k` on `crafting.provisioner.bonus-portions` at 500 and 5000, rewarding 400 and 1500.

Listened events:

- `CraftItemEvent` (`on`): crafted food
- `FurnaceSmeltEvent` (`on`): cooked food

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusChanceBase` | `0.25` | Chance of bonus portions at level 1, 0-1. |
| `bonusChanceFactor` | `0.5` | Additional bonus chance gained across the level range. |
| `bonusChanceMax` | `0.75` | Ceiling on the bonus chance. |
| `bonusPortionsBase` | `1` | Bonus portions granted per activation at level 1. |
| `bonusPortionsFactor` | `2` | Additional bonus portions unlocked across the level range. |
| `cookingRadius` | `8.0` | Blocks searched around a furnace for a player to credit. |

### Artisan's Signature

| Property | Default |
|----------|---------|
| Class | `CraftingSignature` |
| Icon | `WRITABLE_BOOK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.3 |
| Config file | `plugins/Adapt/adapt/adaptations/crafting-signature.toml` |

The signature is a persistent-data string holding the crafter's UUID, plus a lore line. The trade bonus is a `HERO_OF_THE_VILLAGE` potion effect applied on villager interaction, skipped if the player already has that effect.

Milestones: `challenge_crafting_signature_100` and `challenge_crafting_signature_1k` on `crafting.signature.signed-trades` at 100 and 1000, rewarding 400 and 1500.

Listened events:

- `CraftItemEvent` (`on`): stamps the result
- `PlayerInteractEntityEvent` (`on`): right-click on a villager

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `amplifierBase` | `0` | Hero of the Village amplifier at level 1. |
| `amplifierFactor` | `1` | Additional amplifier gained across the level range. |
| `amplifierMax` | `1` | Ceiling on the amplifier. |
| `tradeDurationTicks` | `200` | Duration in ticks of the effect applied on villager interaction. |

### Support classes (not player adaptations)

- `CraftingIngredients`: counts per-craft ingredient totals from a shaped or shapeless recipe, used by the bulk and refund behavior.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
- `37 - Recipes, Brewing & Value.md`
