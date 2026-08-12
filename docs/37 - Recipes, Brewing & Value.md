# Recipes, Brewing & Value

Adapt adds three things on the crafting side of the game: extra crafting recipes that ship with certain adaptations, a custom brewing track that runs on an ordinary brewing stand, and a material value estimate that several skills use to size XP rewards. Recipes and brews belong to the adaptation that registered them, so only players who learned that adaptation get them. Material value is server-wide and ignores who learned what.

Adapt's recipes are real server recipes, registered at startup. They stay hidden in the recipe book until a player learns the owning adaptation at the level the recipe asks for. Learn it and the recipe shows up; unlearn it and it goes away again. The crafting is plain vanilla crafting, so a crafting table, a recipe book, or an autocrafter all treat them like any other recipe. Custom brewing works the same way, with a longer ingredient list layered on an ordinary stand.

Material value is a rough "what is this worth" number Adapt works out by walking vanilla recipes backwards. Skills that pay XP for what you mined, placed, or crafted read that number instead of carrying their own price list.

## Crafting recipes

A recipe exists on the server only while its skill and its adaptation are both enabled. Turn an adaptation off in config and its recipes are unregistered outright. Each recipe key belongs to exactly one adaptation; if two claim the same key, Adapt logs a conflict warning and the later registration wins.

Discovery is per player. Adapt recalculates the whole set on join, whenever a player learns or unlearns anything, and whenever the skill catalog changes, then pushes only the difference to that player's recipe book. The gate is the learned level of the owning adaptation, not the skill level.

On Folia, Adapt refuses to register or re-register recipes while players are online, because reloading the recipe registry under a live server is unsafe. It logs a warning and retries until the server has no players, so a Folia box that never empties will not pick up recipe changes made after boot.

Three recipes carry extra conditions. Rift Gate registers its recipe only when `requireCraftedEye` is true. The backpack mode-cycle recipe is always registered, but the crafting handler lets it through only when `allowModeToggle` is true and the grid holds exactly one empty Adapt backpack. The Cherry and Pale Oak log swaps register only when the server actually has those materials.

`maxRecipeListPrecaution` does not cap how many recipes Adapt registers. It is a guard on the material value walk, described below.

## Custom brewing

Brewing a custom potion:

1. Learn the brewing adaptation that owns the recipe. Adapt checks that on every click.
2. Put the recipe's base potion in one, two, or three of the stand's bottle slots.
3. Put blaze powder in the fuel slot. One blaze powder is worth 20 fuel units, and Adapt counts stored fuel plus the powder still sitting in the slot.
4. Left-click the recipe's ingredient into the empty ingredient slot.
5. Wait out the timer. When it finishes the ingredient drops by one and every bottle slot holding the matching base potion converts at once.

Fuel is taken up front when the task starts, not at the end. Changing the ingredient, pulling the base potions, or breaking the stand mid-brew cancels the task and the reserved fuel is gone. Starting a different custom recipe on the same stand cancels the one already running.

The ingredient click is handled carefully. If another plugin cancels the click, Adapt does nothing at all. Otherwise Adapt cancels the vanilla click itself and completes the move one tick later, and only if the player still has that same physical stand open with the ingredient slot still empty. That is what stops an item landing in a stand the player already walked away from.

A finished brew fires `AdaptBrewCompleteEvent` when at least one bottle converted. Vanilla gunpowder and dragon's breath conversion still works afterwards, so Adapt potions become splash and lingering versions the normal way. Each brewing adaptation's own level, duration, amplifier, and enable setting are in `15 - Skill - Brewing.md`.

## Material values

Every material resolves to a number. Adapt starts at `value.baseValue`, asks Bukkit for every recipe that produces the material, and for each of those recipes adds up `baseValue` plus the resolved value of each ingredient, then divides by how many items the recipe outputs. Those per-recipe numbers are averaged and added to the base. Recipes already visited on the current walk are skipped so a crafting loop cannot recurse forever.

Two clamps sit on the result. If the running total passes `maxRecipeListPrecaution`, it collapses to `total / 10 + 1`, which keeps deep recipe chains from exploding. Any block whose hardness is zero reads back as `0` regardless of what was computed. The last step is `value.valueMultipliers`, looked up case insensitively on the Bukkit material name, with `1` for anything absent.

Resolved values are cached in memory and written to `plugins/Adapt/data/value-cache.json` at shutdown. The cache signature carries a random per-process id, so the file is never reused across restarts and every boot recomputes from the recipe registry that actually exists. A core config reload throws the in-memory cache away too, so edits to `baseValue` or the multiplier map take effect without a restart.

Material value feeds Architect XP and Placement, Axes XP and its value statistics, Crafting XP and Deconstruction, Discovery XP and Archaeologist, Excavation XP and Seismic Ping, Pickaxes XP, and the HiddenOre bridge.

The walk is meant to skip Adapt's own recipes so plugin recipes cannot feed back into their own prices. It does not. The guard tests the Bukkit recipe against Adapt's `AdaptRecipe` type, and Adapt registers plain Bukkit recipe objects, so the test never matches and Adapt recipes do contribute to material values.

## Reference

### Crafting recipe catalog

Shapes list rows top to bottom, and a space is an empty cell. Symbols are defined in the recipe column. Required level is the learned level of the owning adaptation. Every recipe is level 1 except the chalk wands and the netherite shield.

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
| Crafting: Backpacks | `crafting-backpacks`, shaped | `LLL` / `LCL` / `LLL`; `L` Leather, `C` Chest | Backpack in the configured default mode and capacity | 1 |
| Crafting: Backpacks | `crafting-backpacks-mode`, shapeless | One Adapt Backpack, matched through the Bundle ingredient | The same empty backpack in the other mode | 1 |
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

### Log Swap family

Axe: Craft Log Swap registers 70 shapeless conversions, all at adaptation level 1. Each takes eight logs of one family plus one sapling of the destination family, or a Mangrove Propagule for Mangrove, and returns eight destination logs. Keys are `axe-swap<source><destination>` with lowercase family names and no underscores, for example `axe-swapdarkoakpaleoak`.

- Birch, Oak, Acacia, Dark Oak, Jungle, Spruce, and Mangrove each convert into every other family among Birch, Oak, Acacia, Dark Oak, Jungle, Spruce, Mangrove, Cherry, and Pale Oak.
- Cherry converts into Birch, Oak, Acacia, Dark Oak, Jungle, Spruce, and Pale Oak.
- Pale Oak converts into Birch, Oak, Acacia, Dark Oak, Jungle, Spruce, and Cherry.
- The Cherry and Pale Oak entries register only when those materials exist on the server. There is no Cherry to Mangrove or Pale Oak to Mangrove recipe.

### Reconstruction family

Crafting: Reconstruction registers 19 shapeless recipes, all at adaptation level 1. Every one of them takes one base block plus eight of a resource.

| Key family | Ingredients | Output |
|---|---|---|
| `reconstruction-<resource>-ore` | Stone + 8 of Iron Ingot, Gold Ingot, Copper Ingot, Lapis Lazuli, Redstone, Emerald, Diamond, or Coal | The matching Iron, Gold, Copper, Lapis, Redstone, Emerald, Diamond, or Coal Ore |
| `reconstruction-deepslate-<resource>-ore` | Deepslate + the same eight resource choices | The matching Deepslate Ore |
| `reconstruction-nether-gold-ore` | Nether Bricks + 8 Gold Ingots | Nether Gold Ore |
| `reconstruction-nether-quartz-ore` | Nether Bricks + 8 Quartz | Nether Quartz Ore |
| `reconstruction-ancient-debris` | Nether Bricks + 8 Netherite Scrap | Ancient Debris |

### Custom brewing recipes

All 21 registered brews run 320 ticks. Weak recipes cost 16 fuel units and strong recipes cost 32. Darkness has only the one 16-unit recipe.

| Effect | Weak recipe id, ingredient, base | Strong recipe id, ingredient, base |
|---|---|---|
| Absorption | `brewing-absorption-1`, Quartz + Healing | `brewing-absorption-2`, Quartz Block + Healing |
| Blindness | `brewing-blindness-1`, Ink Sac + Awkward | `brewing-blindness-2`, Glow Ink Sac + Awkward |
| Darkness | `brewing-darkness`, Black Concrete + Night Vision | none |
| Decay / Wither | `brewing-decay-1`, Poisonous Potato + Weakness | `brewing-decay-2`, Crimson Roots + Weakness |
| Mining Fatigue | `brewing-fatigue-1`, Slime Ball + Weakness | `brewing-fatigue-2`, Slime Block + Weakness |
| Haste | `brewing-haste-1`, Amethyst Shard + Speed | `brewing-haste-2`, Amethyst Block + Speed |
| Health Boost | `brewing-healthboost-1`, Golden Apple + Healing | `brewing-healthboost-2`, Enchanted Golden Apple + Healing |
| Hunger | `brewing-hunger-1`, Rotten Flesh + Awkward | `brewing-hunger-2`, Rotten Flesh + Weakness |
| Nausea | `brewing-nausea-1`, Brown Mushroom + Awkward | `brewing-nausea-2`, Crimson Fungus + Awkward |
| Resistance | `brewing-resistance-1`, Iron Ingot + Awkward | `brewing-resistance-2`, Iron Block + Awkward |
| Saturation | `brewing-saturation-1`, Baked Potato + Regeneration | `brewing-saturation-2`, Hay Bale + Regeneration |

### Value settings

| Key | Default | What it does |
|---|---|---|
| `value.baseValue` | `1` | Starting value for every material, and the flat term added to each recipe path |
| `value.valueMultipliers` | see below | Bukkit material name to final multiplier, matched case insensitively; absent keys use `1` |
| `maxRecipeListPrecaution` | `25` | Ceiling on the running value total; anything above it collapses to `total / 10 + 1`, and it also bounds how many recipes the verbose value dump will visit |

Default `value.valueMultipliers` entries, written into `adapt.toml` when it is first generated:

| Material | x | Material | x | Material | x |
|---|---:|---|---:|---|---:|
| `BLAZE_ROD` | 50 | `EGG` | 1.335 | `DIAMOND_ORE` | 5 |
| `ENDER_PEARL` | 75 | `WHEAT` | 1.25 | `GOLD_ORE` | 4 |
| `GHAST_TEAR` | 100 | `BEETROOT` | 1.25 | `LAPIS_ORE` | 3.5 |
| `LEATHER` | 1.5 | `CARROT` | 1.25 | `COAL_ORE` | 1.35 |
| `BEEF` | 1.125 | `FLINT` | 1.35 | `REDSTONE_ORE` | 4.5 |
| `PORKCHOP` | 1.125 | `IRON_ORE` | 1.75 | `NETHER_GOLD_ORE` | 4.5 |
| `MUTTON` | 1.125 | `CHICKEN` | 1.13 | `NETHER_QUARTZ_ORE` | 1.11 |

Adapt's recipe keys live in the plugin's own namespace, so a full key reads `adapt:crafting-backpacks`. The value cache file is `plugins/Adapt/data/value-cache.json`. `AdaptBrewCompleteEvent` carries the stand block, the recipe, the brewer's UUID, and how many bottles converted.

## See also

- `05 - Configuration Math.md`
- `15 - Skill - Brewing.md`
- `36 - Items, Orbs & Bound Objects.md`
- `50 - API - Recipes, FX, Telemetry & Utilities.md`
