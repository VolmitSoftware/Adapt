# Recipes, Brewing & Value

Adaptations register their crafting and brewing content during skill startup. Recipe discovery follows the player's learned adaptation levels, while the material-value system independently derives values from vanilla recipes for the adaptations and integrations that consume that value.

## Crafting recipe catalog

Shapes list rows from top to bottom; spaces are empty cells. Symbols are defined in the recipe column. Required level means the learned level of the owning adaptation, not the skill level. Every recipe defaults to level 1 except the Chalk Line tools and Netherite Phalanx Shield noted below.

| Adaptation | Key and type | Recipe | Output | Required level |
|---|---|---|---|---:|
| Architect: Chalk Line | `architect-chalk-straightedge`, shaped | `S` / `T`; `S` String, `T` Stick | Straightedge chalk wand | 1 |
| Architect: Chalk Line | `architect-chalk-polyline`, shaped | `S ` / ` T`; `S` String, `T` Stick | Polyline chalk wand | 2 |
| Architect: Chalk Line | `architect-chalk-compass`, shaped | `T` / `S`; `T` Stick, `S` String | Compass chalk wand | 3 |
| Architect: Chalk Line | `architect-chalk-arc-bow`, shaped | `TS`; `T` Stick, `S` String | Arc Bow chalk wand | 4 |
| Architect: Elevator | `elevator`, shaped | `XXX` / `XYX` / `XXX`; `X` any Wool, `Y` Ender Pearl | Elevator item | 1 |
| Architect: Wireless Redstone | `remote-redstone-torch`, shapeless | Redstone Torch + Target + Ender Pearl | Unbound wireless redstone torch | 1 |
| Blocking: Chain Armorer | `blocking-chainarmorer-boots`, shaped | `I I` / `I I`; `I` Iron Nugget | Chainmail Boots | 1 |
| Blocking: Chain Armorer | `blocking-chainarmorer-leggings`, shaped | `III` / `I I` / `I I`; `I` Iron Nugget | Chainmail Leggings | 1 |
| Blocking: Chain Armorer | `blocking-chainarmorer-chestplate`, shaped | `I I` / `III` / `III`; `I` Iron Nugget | Chainmail Chestplate | 1 |
| Blocking: Chain Armorer | `blocking-chainarmorer-helmet`, shaped | `III` / `I I`; `I` Iron Nugget | Chainmail Helmet | 1 |
| Blocking: Horse Armorer | `blocking-horsearmorerleather`, shaped | `III` / `IUI` / `III`; `I` Leather, `U` Saddle | Leather Horse Armor | 1 |
| Blocking: Horse Armorer | `blocking-horsearmoreriron`, shaped | same shape; `I` Iron Ingot, `U` Saddle | Iron Horse Armor | 1 |
| Blocking: Horse Armorer | `blocking-horsearmorergold`, shaped | same shape; `I` Gold Ingot, `U` Saddle | Golden Horse Armor | 1 |
| Blocking: Horse Armorer | `blocking-horsearmorerdiamond`, shaped | same shape; `I` Diamond, `U` Saddle | Diamond Horse Armor | 1 |
| Blocking: Phalanx Crafter | `blocking-phalanx-field-shield`, shaped | `WWW` / `PIP` / ` P `; `W` White Wool, `P` Oak Planks, `I` Iron Ingot | Shield | 1 |
| Blocking: Phalanx Crafter | `blocking-phalanx-netherite-shield`, shaped | ` N ` / `NSN` / ` N `; `N` Netherite Ingot, `S` Shield | Netherite Phalanx Shield | 2 |
| Blocking: Saddlecrafter | `blocking-saddlecrafter`, shaped | `I I` / `III`; `I` Leather | Saddle | 1 |
| Chronos: Time Bomb | `chronos-time-bomb`, shapeless | Snowball + Clock + Diamond + Sand | Time Bomb | 1 |
| Chronos: Time in a Bottle | `chronos-time-in-a-bottle`, shapeless | Clock + Potion + Glass Bottle | Empty Time in a Bottle | 1 |
| Crafting: Backpacks | `crafting-backpacks`, shaped | `LLL` / `LCL` / `LLL`; `L` Leather, `C` Chest | Backpack in configured default mode/capacity | 1 |
| Crafting: Backpacks | `crafting-backpacks-mode`, shapeless | One Adapt Backpack represented by the Bundle ingredient | Same empty backpack in the other mode | 1 |
| Crafting: Leather | `crafting-leather`, campfire | Rotten Flesh; 100-tick cook, 1 recipe XP | Leather | 1 |
| Crafting: Skulls | `crafting-skeletonskull`, shaped | `III` / `IXI` / `III`; `I` Bone, `X` Bone Block | Skeleton Skull | 1 |
| Crafting: Skulls | `crafting-witherskeletonskull`, shaped | same shape; `I` Nether Brick, `X` Bone Block | Wither Skeleton Skull | 1 |
| Crafting: Skulls | `crafting-zombieskull`, shaped | same shape; `I` Rotten Flesh, `X` Bone Block | Zombie Head | 1 |
| Crafting: Skulls | `crafting-creeperhead`, shaped | same shape; `I` Gunpowder, `X` Bone Block | Creeper Head | 1 |
| Crafting: Skulls | `crafting-dragonhead`, shaped | same shape; `I` Dragon's Breath, `X` Bone Block | Dragon Head | 1 |
| Herbalism: Craftable Cobweb | `herbalism-cobwebblock`, shaped | `III` / `III` / `III`; `I` String | Cobweb | 1 |
| Herbalism: Mushroom Blocks | `herbalism-redmushblock`, shaped | `II` / `II`; `I` Red Mushroom | Red Mushroom Block | 1 |
| Herbalism: Mushroom Blocks | `herbalism-brownmushblock`, shaped | `II` / `II`; `I` Brown Mushroom | Brown Mushroom Block | 1 |
| Herbalism: Mushroom Blocks | `herbalism-mushstemred`, shapeless | Red Mushroom Block | Mushroom Stem | 1 |
| Herbalism: Mushroom Blocks | `herbalism-mushstembrown`, shapeless | Brown Mushroom Block | Mushroom Stem | 1 |
| Herbalism: Myconid | `herbalism-dirt-myconid`, shapeless | Dirt + Red Mushroom + Brown Mushroom | Mycelium | 1 |
| Herbalism: Terralid | `herbalism-dirt-terralid`, shaped | `SSS` / `DDD`; `S` Wheat Seeds, `D` Dirt | 3 Grass Blocks | 1 |
| Hunter: Snare Line | `hunter-snare`, shaped | `S S` / `SIS` / `S S`; `S` String, `I` Iron Ingot | 2 Snare items | 1 |
| Ranged: Web Bomb | `ranged-web-bomb`, shaped | `III` / `ISI` / `III`; `I` Cobweb, `S` Snowball | Unbound Web Bomb | 1 |
| Rift: Rift Access | `rift-remote-access`, shapeless | Ender Pearl + Compass | Unbound remote-access pearl | 1 |
| Rift: Rift Gate | `rift-recall-gate`, shapeless | Ender Pearl + Amethyst Shard + Emerald | Unbound Rift Gate eye | 1 |

Rift Gate registers its recipe only when `requireCraftedEye=true`. Backpack mode crafting is registered independently of `allowModeToggle`, but the crafting handler permits it only when that setting is true and the single input is an empty Adapt backpack.

### Log Swap recipe family

Axe: Craft Log Swap registers shapeless conversions using eight source logs plus one destination sapling (or a Mangrove Propagule), producing eight destination logs at adaptation level 1. Keys use `axe-swap<source><destination>` with lowercase material family names and no underscores, such as `axe-swapdarkoakpaleoak`.

- Sources Birch, Oak, Acacia, Dark Oak, Jungle, Spruce, and Mangrove each convert to every other family in Birch, Oak, Acacia, Dark Oak, Jungle, Spruce, Mangrove, Cherry, and Pale Oak.
- Source Cherry converts to Birch, Oak, Acacia, Dark Oak, Jungle, Spruce, and Pale Oak.
- Source Pale Oak converts to Birch, Oak, Acacia, Dark Oak, Jungle, Spruce, and Cherry.
- Cherry and Pale Oak recipes register only when those compatibility materials exist. There is no Cherry-to-Mangrove or Pale-Oak-to-Mangrove recipe.

### Reconstruction recipe family

Crafting: Reconstruction recipes are shapeless and require adaptation level 1. The following rows are the complete family; each resource quantity is eight.

| Key/output family | Ingredients | Output |
|---|---|---|
| `reconstruction-<resource>-ore` | Stone + 8 of Iron Ingot, Gold Ingot, Copper Ingot, Lapis Lazuli, Redstone, Emerald, Diamond, or Coal | Matching Iron, Gold, Copper, Lapis, Redstone, Emerald, Diamond, or Coal Ore |
| `reconstruction-deepslate-<resource>-ore` | Deepslate + the same eight-resource choices | Matching Deepslate Ore |
| `reconstruction-nether-gold-ore` | Nether Bricks + 8 Gold Ingots | Nether Gold Ore |
| `reconstruction-nether-quartz-ore` | Nether Bricks + 8 Quartz | Nether Quartz Ore |
| `reconstruction-ancient-debris` | Nether Bricks + 8 Netherite Scrap | Ancient Debris |

When a player learns the required adaptation level, Adapt discovers its recipes in the vanilla recipe book; unlearning removes those discoveries unless another learned unlock still owns the same recipe key. Online players are synchronized when unlock state changes.

`maxRecipeListPrecaution` does not cap Adapt's registered recipes. It is a recursion/value-growth guard used while tracing vanilla recipes for material values.

## Custom brewing workflow

Custom brewing begins when a player places a registered custom ingredient in a brewing stand. That player must be allowed to use the recipe's owning adaptation; changing the ingredient, base potions, stand state, or other required inputs cancels the active task.
Cancelled inventory clicks are ignored. Ingredient-slot handling runs one tick later and changes the cursor or
starts a task only when the player still has the same brewing-stand inventory and physical stand open.

Every registered custom brew takes 320 ticks. A blaze powder contributes 20 fuel units; weak recipes consume 16 units and strong recipes consume 32, except Darkness, which has one 16-unit recipe. Fuel is reserved when the task starts, the ingredient is consumed on completion, and every matching potion in the stand's three bottle slots is converted. Successful completion fires `AdaptBrewCompleteEvent`.

| Effect | Weak ingredient and base | Strong ingredient and base |
|---|---|---|
| Absorption | Quartz + Healing | Quartz Block + Healing |
| Blindness | Ink Sac + Awkward | Glow Ink Sac + Awkward |
| Darkness | Black Concrete + Night Vision | None |
| Decay / Wither | Poisonous Potato + Weakness | Crimson Roots + Weakness |
| Mining Fatigue | Slime Ball + Weakness | Slime Block + Weakness |
| Haste | Amethyst Shard + Speed | Amethyst Block + Speed |
| Health Boost | Golden Apple + Healing | Enchanted Golden Apple + Healing |
| Hunger | Rotten Flesh + Awkward | Rotten Flesh + Weakness |
| Nausea | Brown Mushroom + Awkward | Crimson Fungus + Awkward |
| Resistance | Iron Ingot + Awkward | Iron Block + Awkward |
| Saturation | Baked Potato + Regeneration | Hay Bale + Regeneration |

Vanilla gunpowder and dragon's breath conversion remains available for Adapt potions after brewing. See `15 - Skill - Brewing.md` for each owning adaptation's level, duration, amplifier, and enable setting.

## Material values

Material values start from `value.baseValue`, recursively incorporate the ingredients of recipes returned by Bukkit, average multiple recipe paths, divide by recipe output count, and finally apply a case-insensitive `value.valueMultipliers` entry for the output material. Adapt recipes are excluded from this traversal to avoid feeding plugin recipes back into their own value calculations. Blocks with zero hardness resolve to zero.

Resolved values are cached at `plugins/Adapt/data/value-cache.json`. The cache is scoped to one server process so recipe-registry changes cannot reuse values from an earlier startup; a core-config hotload also invalidates the in-memory instance. Unreadable, unsigned, or earlier-session cache data is rebuilt. Material value contributes to Architect XP and Placement, Axes XP and value statistics, Crafting XP and Deconstruction, Discovery XP and Archaeologist, Excavation XP and Seismic Ping, Pickaxes XP, and HiddenOre XP/value calculations.

`value.valueMultipliers` keys are Bukkit material names such as `DIAMOND_ORE`. An absent key uses multiplier `1`; the default map and core value settings are listed in `01 - Installation & Configuration.md`.

## Related pages

- `05 - Configuration Math.md`
- `15 - Skill - Brewing.md`
- `36 - Items, Orbs & Bound Objects.md`
- `50 - API - Recipes, FX, Telemetry & Utilities.md`
