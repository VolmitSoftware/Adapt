# Skill: Excavation

Excavation is the shovel skill. Break blocks with a shovel in your main hand and you earn XP scaled by the block's value, hardness, and blast resistance; hit mobs with a shovel and you earn XP from the damage you deal. Twelve adaptations sit on top of that, and most of them are about moving dirt faster and getting more out of it.

In play it starts quiet. You dig faster, drops go into your bag instead of on the floor, muddy blocks pay double, and falling onto soft ground stops hurting. Then it stops being quiet. Sneak-dig and you carve out a whole three-by-three face at once. Sneak-right-click and you drop a shaft straight down. Sneak-right-click with nothing in front of you and the ground throws every hostile mob nearby into the air.

A few adaptations are about finding things rather than removing them. Seismic Ping lights up a nearby ore while you work, Spelunker turns a handful of glow berries into an x-ray sweep for one ore type, Treasure Hunter pulls pottery sherds out of sand and gravel, and Grave Digger pulls bones out of dirt, along with the occasional angry skeleton.

OMNI - T.O.O.L. is the odd one out. It merges several tools into one item that switches to the right head for whatever you are pointed at.

## Adaptations

Everything below needs the same four things: the adaptation learned at level 1 or higher in the Adapt menu, the Excavation skill and that adaptation both enabled in config, an `adapt.use.` permission that has not been revoked for you, and any protection or region plugin allowing the block or entity you are acting on. Those are not repeated per entry.

Most of these also require a shovel in your main hand, and several restrict themselves to shovel-friendly blocks. Where that matters it is called out.

### Hasty Excavator (`excavation-haste`)

Starting to break a block gives you a block-break speed bonus that lasts long enough to finish the block, so mining speed does not stutter partway through a slow dig. It works on its own once learned.

The boost is an Adapt attribute modifier on block break speed, not the vanilla Haste potion effect. It stacks additively at 20 percent per adaptation level. The handler does not check what you are holding or what you are breaking, so the bonus applies to any block you start breaking, not only shovel work.

### Super-Seeing Spelunker! (`excavation-spelunker`)

Scans the ground around you for one specific ore type and outlines every hit with a glowing block so you can see them through terrain. You pick the ore by holding a sample of it.

How to use it:

1. Put the ore block you want to find in your off hand.
2. Hold glow berries in your main hand.
3. Sneak. The scan fires from where you stand.
4. Matching ore lights up in an ore-appropriate color for a few seconds. Only you can see the markers.

One glow berry is consumed per successful scan. If the scan finds nothing, or you swap items before it finishes, you keep the berry and get a dull click. Scan radius grows with level and is capped at 32 blocks.

### OMNI - T.O.O.L. (`excavation-omnitool`)

Combines several tools into one item that switches heads based on what you are aiming at: axe on wood, shovel on dirt, sword on webs and similar, pickaxe on everything else, hoe on crops, flint and steel on burnable blocks. The merged item is identified by its "Leatherman" lore.

How to use it:

1. Learn it, then merge tools together in your inventory. Merged tools keep their names, enchantments, and damage values.
2. Carry the merged item in your main hand and use it normally. It swaps heads on its own when you start breaking a block or right-click one.
3. To take it apart, sneak and drop the merged item. It bursts into its component tools.

Component tools do not break. A component at two durability from breaking is refused instead, and the action is cancelled with a puff of smoke. The merged item is also inert if you do not have the adaptation active: block breaks and attacks with it are cancelled outright.

The merge handler is wired to a shift-left-click that moves an item between inventories, but it reads the second tool from your cursor, and a shift-click leaves the cursor empty. In practice the merge branch does not run; what does run is the capacity check, which cancels the shift-click with a failure sound when the clicked tool already holds more components than your slot budget allows.

### Shovel Drop-To-Inventory (`excavation-drop-to-inventory`)

Blocks you break with a shovel send their drops straight into your inventory instead of onto the ground. It works on its own once learned, and it is a single-level adaptation.

Each drop is run through a normal pickup attempt first, so protection plugins that block pickups still win; anything they deny stays on the ground as usual. Items that do not fit in your inventory are dropped at your feet with a failure sound.

### Seismic Ping (`excavation-seismic-ping`)

While you dig, the ground occasionally answers back: one nearby ore block lights up for two seconds, in a color matched to the ore, visible only to you. The ping sound is pitched by distance, so a high chime means the ore is close. It works on its own once learned.

Works with a shovel or a pickaxe in your main hand. Scan range grows with level, capped at 32 blocks. XP is paid per ping and scales with how valuable the revealed ore is. If HiddenOre is installed, its hidden veins are included as scan targets.

### Tunneler (`excavation-tunneler`)

Turns a single dig into a whole plane. The plane is oriented off where you are looking: flat if you are looking up or down, vertical and aligned to your facing otherwise.

How to use it:

1. Hold a shovel and sneak.
2. Break a shovel-friendly block: dirt, sand, gravel, clay, mud, snow, and their variants.
3. One tick later the surrounding blocks in the plane break too, up to your bonus block budget.

Each bonus block costs extra durability, and the sweep stops early if the shovel would break. Bonus blocks are re-checked against protection plugins individually, so anything denied is skipped and costs you nothing.

### Treasure Hunter (`excavation-treasure-hunter`)

Digging sand, red sand, gravel, mud, or clay with a shovel sometimes turns up something buried. The table is mostly bones, flint, and clay, with pottery sherds and the odd emerald as the rare pulls. It works on its own once learned.

Rare finds get their own sparkle and level-up chime, so you know when something good came out.

### Soft Fall (`excavation-soft-fall`)

Landing on ground you could have dug reduces the fall damage, and at high levels removes it entirely. It works on its own once learned.

Counts as soft ground: dirt and its variants, grass, podzol, mycelium, path, farmland, sand, red sand, gravel, clay, mud, muddy mangrove roots, soul sand, soul soil, and snow. Either the block you land in or the block beneath it qualifies. XP is paid per point of damage prevented, so long falls onto sand pay well.

### Earth Mover (`excavation-earth-mover`)

Slams the ground and sends a ring of dirt outward, damaging every hostile mob in range, throwing them up and away, and slowing them. Damage comes from the tier of shovel you are holding, so a netherite shovel hits noticeably harder than a wooden one.

How to use it:

1. Hold a shovel and sneak.
2. Right-click. Air or a block both work.
3. Hunger is spent, the wave renders, and hostile mobs inside the radius are hit.

Mobs that take no actual damage, for example because something absorbed it, are not launched and do not pay XP. The wave has per-activation caps on how many mobs it inspects, launches, and decorates, so it stays cheap in mob farms.

### Burrow (`excavation-burrow`)

Digs a shaft straight down under you, one block every couple of ticks, and stops before it drops you into something bad. It refuses to break into lava and stops when there is a two-block air gap below, so you do not open a cave ceiling under your feet.

How to use it:

1. Hold a shovel and sneak.
2. Right-click the soft block you want to dig through. On Folia you must click a block directly; an air click is ignored because the ray target can cross region boundaries.
3. The first block breaks immediately. If that fails, no hunger or cooldown is spent.
4. The rest of the shaft digs itself out below you.

Each block costs durability and the whole activation costs hunger. The dig stops at a safety margin above the world floor. Every delayed block is re-authorized before it breaks, so a protection plugin can stop the shaft partway.

### Grave Digger (`excavation-grave-digger`)

Digging dirt, grass, coarse dirt, rooted dirt, podzol, mycelium, or dirt path with a shovel can turn up bone loot. Much more rarely it disturbs a grave and a zombie or skeleton claws out of the hole, already targeting you. It works on its own once learned.

Grave mobs are tagged in persistent data, so their deaths get a soul-and-ash effect. Grave spawns have their own cooldown independent of the loot roll.

### Mudlark (`excavation-mudlark`)

Two things at once. Breaking clay, mud, muddy mangrove roots, soul sand, or soul soil with a shovel can drop an extra copy of that block's material. Separately, digging while wet gives you a block-break speed bonus. It works on its own once learned.

Wet means standing in water, or standing under open sky during a storm. The bonus is an Adapt attribute modifier at 20 percent per amplifier step, not the vanilla Haste effect.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `excavation` |
| Class | `SkillExcavation` |
| Icon | `DIAMOND_SHOVEL` |
| Color | `YELLOW` |
| Interval (ms) | `5953` |
| Skill config | `plugins/Adapt/adapt/skills/excavation.toml` |
| Adaptation count | 12 |

### Skill XP and stats

Two sources, both at MONITOR priority with cancelled events ignored, both spaced by `cooldownDelay`:

- `BlockBreakEvent` with a shovel in the main hand. Any block counts, not only soft ground. XP is `blockValue` run through the world's earnings multiplier, and is skipped when XP provenance marks the block as already farmed.
- `EntityDamageByEntityEvent` with a shovel in the main hand, when `getXpForAttackingWithTools` is true and the victim is a valid damageable entity. XP is `axeDamageXPMultiplier * damage`.

Block value is `MaterialValue * valueXPMultiplier + min(maxHardnessBonus, hardness) + min(maxBlastResistanceBonus, blastResistance)`.

| Stat key | Recorded |
|----------|----------|
| `excavation.blocks.broken` | 1 per block broken with a shovel |
| `excavation.blocks.value` | Computed block value per break |
| `excavation.gravel` | 1 per `GRAVEL`, `SAND`, `RED_SAND`, `CLAY`, `SOUL_SAND`, or `SOUL_SOIL` broken |
| `excavation.damage` | Damage dealt with a shovel |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/excavation.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Turns the whole skill and its adaptations off when false. |
| `skillColor` | `"&e"` | Legacy ampersand color code used for this skill in menus and text. |
| `getXpForAttackingWithTools` | `true` | Allows shovel melee damage to award Excavation XP at all. |
| `maxHardnessBonus` | `9` | Cap on the block-hardness term added to a block's XP value. |
| `maxBlastResistanceBonus` | `10` | Cap on the blast-resistance term added to a block's XP value. |
| `challengeExcavationReward` | `1200` | Base knowledge reward for the Excavation milestones. |
| `valueXPMultiplier` | `0.6` | Multiplier on the base material value before the hardness terms are added. |
| `cooldownDelay` | `1250` | Minimum milliseconds between skill XP awards. |
| `axeDamageXPMultiplier` | `4.0` | Skill XP per point of melee damage dealt with a shovel. The key name says axe; the code uses it for shovels. |

### Skill milestones

| Milestone key | Stat key | Threshold | Reward |
|---------------|----------|-----------|--------|
| `challenge_excavate_1k` | `excavation.blocks.broken` | 1000 | `challengeExcavationReward` |
| `challenge_excavate_5k` | `excavation.blocks.broken` | 5000 | `challengeExcavationReward` |
| `challenge_excavate_50k` | `excavation.blocks.broken` | 50000 | `challengeExcavationReward` |
| `challenge_dig_damage_1k` | `excavation.damage` | 1000 | `challengeExcavationReward` |
| `challenge_dig_damage_10k` | `excavation.damage` | 10000 | `challengeExcavationReward` * 2 |
| `challenge_dig_value_5k` | `excavation.blocks.value` | 5000 | `challengeExcavationReward` |
| `challenge_dig_value_50k` | `excavation.blocks.value` | 50000 | `challengeExcavationReward` * 2 |
| `challenge_dig_gravel_500` | `excavation.gravel` | 500 | `challengeExcavationReward` |
| `challenge_dig_gravel_5k` | `excavation.gravel` | 5000 | `challengeExcavationReward` * 2 |

### Shared adaptation keys

Every adaptation TOML at `plugins/Adapt/adapt/adaptations/<id>.toml` carries these in addition to the knobs listed below.

| Key | Behavior |
|-----|----------|
| `enabled` | Turns this adaptation off when false. |
| `permanent` | Treats the adaptation as always learned, bypassing learn and unlearn. |
| `showParticles` | Plays this adaptation's particle effects. |
| `showSounds` | Plays this adaptation's sound effects. |
| `baseCost`, `costFactor`, `maxLevel`, `initialCost` | Knowledge cost curve and level cap. Defaults per adaptation below. |

In the formulas below, `levelPercent` is the learned level divided by `maxLevel`, clamped to 0 through 1.

### Hasty Excavator

| Property | Value |
|----------|-------|
| Class | `ExcavationHaste` |
| Icon | `GOLDEN_PICKAXE` |
| Max level | 3 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.3 |
| Tick interval (ms) | 4388 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-haste.toml` |
| Listened events | `BlockDamageEvent` (HIGHEST, cancelled events ignored) |
| Menu stat lines | Gain Haste while excavating; x Levels of Haste while actively excavating. |
| Stat key | `excavation.haste.blocks-while-hasted` |
| Milestones | `challenge_excavation_haste_5k` (5000, reward 400), `challenge_excavation_haste_50k` (50000, reward 1500) |

Applies `BLOCK_BREAK_SPEED` as an `ADD_SCALAR` modifier of `0.20 * level`, refreshed on every block-damage tick. Effective duration is `hasteDurationTicks` clamped to 40 through 600 ticks.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `hasteDurationTicks` | `100` | Ticks the speed bonus lasts after a block break starts. Clamped to 40-600. |

### Super-Seeing Spelunker!

| Property | Value |
|----------|-------|
| Class | `ExcavationSpelunker` |
| Icon | `GOLDEN_HELMET` |
| Max level | 5 |
| Initial knowledge cost | 10 |
| Base knowledge cost | 5 |
| Cost factor | 1 |
| Tick interval (ms) | 20388 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-spelunker.toml` |
| Listened events | `PlayerToggleSneakEvent` (HIGH), `EntityRemoveEvent` (MONITOR), `PlayerQuitEvent` |
| Menu stat lines | Ore in your offhand, Glowberries in your main hand, and Sneak!; Block Range: {range}; Consumes Glowberry on use |
| Stat key | `excavation.spelunker.ores-revealed` |
| Milestones | `challenge_excavation_spelunker_1k` (1000, reward 400), `challenge_excavation_spelunker_25k` (25000, reward 1500) |

Scan radius is `rangeMultiplier * level`, clamped to 1 through 32 blocks. Markers are non-persistent `BlockDisplay` entities shown only to the scanning player, with a glow color chosen from the ore name. Hard caps override the config: 8192 block checks and 16 markers per activation.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldown` | `6.0` | Seconds between scans. |
| `rangeMultiplier` | `5` | Blocks of scan radius per adaptation level. |
| `maxBlockChecks` | `8192` | Block samples one scan may take. Clamped to 8192. |
| `denseScanRadius` | `8` | Radius searched exhaustively before remaining samples are spread over the full range. |
| `maxHighlights` | `16` | Ore markers one scan may create. Clamped to 16. |
| `highlightDurationTicks` | `100` | Ticks a marker stays visible. Clamped to 20-600. |
| `displayViewRange` | `1.0` | Client render distance multiplier for markers. Clamped to 0.5-2.0. |

### OMNI - T.O.O.L.

| Property | Value |
|----------|-------|
| Class | `ExcavationOmniTool` |
| Icon | `DISC_FRAGMENT_5` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 10 |
| Cost factor | 0.20 |
| Tick interval (ms) | 20202 |
| Localization key | `excavation.omni_tool` |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-omnitool.toml` |
| Listened events | `EntityDamageByEntityEvent` (HIGH), `BlockBreakEvent` (HIGH), `PlayerInteractEvent` (HIGH), `BlockDamageEvent` (HIGH), `PlayerDropItemEvent` (HIGHEST), `InventoryClickEvent` (HIGHEST) |
| Menu stat lines | Merges your tools into a single omni-tool that; dynamically swaps to the right tool on the fly, based on your needs.; To merge, shift click an item over another in your inventory.; To unbind tools, Sneak-Drop the item, and it will disassemble.; Merged tools never break, but tools at zero durability can't be used; total merge-able items.; You could use five or six tools, or just one! |
| Stat key | `excavation.omni-tool.auto-swaps` |
| Milestones | `challenge_excavation_omni_1k` (1000, reward 400), `challenge_excavation_omni_25k` (25000, reward 1500) |

Merged items are recognized by `Leatherman` appearing in their lore. Component capacity is `startingSlots + level`. Head selection on `BlockDamageEvent` follows `ItemListings.getAxePreference()`, `getShovelPreference()`, `getSwordPreference()`, and falls back to pickaxe; `PlayerInteractEvent` swaps to a hoe on `ItemListings.farmable` blocks and to flint and steel on `ItemListings.burnable` blocks. Any use with two or fewer durability remaining is cancelled.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `startingSlots` | `1` | Component slots granted before adaptation levels are added. |

### Shovel Drop-To-Inventory

| Property | Value |
|----------|-------|
| Class | `ExcavationDropToInventory` |
| Icon | `CHEST` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 1 |
| Cost factor | 1 |
| Tick interval (ms) | 11777 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-drop-to-inventory.toml` |
| Listened events | `BlockDropItemEvent` (HIGHEST) |
| Menu stat line | Whenever an item is dropped from a block you break it goes into your inventory if it can. |
| Stat key | `excavation.drop-to-inv.items-caught` |
| Milestones | `challenge_excavation_dti_10k` (10000, reward 500) |

Awards a flat 2 skill XP per item caught. The display name comes from `excavation.drop_to_inventory.name` and the description and lore from the Pickaxe equivalents. No adaptation-specific config knobs.

### Seismic Ping

| Property | Value |
|----------|-------|
| Class | `ExcavationSeismicPing` |
| Icon | `GOAT_HORN` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.78 |
| Tick interval (ms) | 2200 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-seismic-ping.toml` |
| Listened events | `BlockBreakEvent` (HIGHEST, cancelled events ignored), `PlayerQuitEvent` |
| Menu stat lines | Scan Range; Ping Chance; Ping Cooldown |
| Stat key | `excavation.seismic-ping.pings-triggered` |
| Milestones | `challenge_excavation_seismic_200` (200, reward 400) |

Triggers on any block broken while holding an item whose name ends in `_SHOVEL` or `_PICKAXE`. Targets are `ANCIENT_DEBRIS` and anything ending in `_ORE`, plus the nearest HiddenOre vein when that plugin is present. Scan range is `round(scanRangeBase + levelPercent * scanRangeFactor)` clamped to 6 through 32. Ping chance is `min(maxPingChance, pingChanceBase + levelPercent * pingChanceFactor)`. Cooldown is `max(350, round(cooldownMillisBase - levelPercent * cooldownMillisFactor))` milliseconds and only starts once the reveal window closes. The reveal lasts 40 ticks. This adaptation canonicalizes its config file on load. Hard cap of 2048 block checks per activation.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `scanRangeBase` | `11` | Scan radius in blocks at level 0 progress. |
| `scanRangeFactor` | `18` | Blocks of scan radius added at full level. |
| `maxBlockChecks` | `1024` | Block samples one ping may take. Clamped to 2048. |
| `denseScanRadius` | `5` | Radius searched exhaustively before remaining samples are spread over the full range. |
| `pingChanceBase` | `0.14` | Chance a break triggers a ping at level 0 progress, 0-1. |
| `pingChanceFactor` | `0.37` | Extra ping chance added at full level, 0-1. |
| `maxPingChance` | `0.6` | Hard ceiling on ping chance, 0-1. |
| `cooldownMillisBase` | `2600` | Milliseconds between pings at level 0 progress. |
| `cooldownMillisFactor` | `1850` | Milliseconds removed from that cooldown at full level. |
| `xpPerPing` | `8` | Flat Excavation skill XP per successful ping. |
| `targetValueXpMultiplier` | `0.5` | Extra skill XP per point of the revealed ore's material value. |

### Tunneler

| Property | Value |
|----------|-------|
| Class | `ExcavationTunneler` |
| Icon | `IRON_SHOVEL` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.75 |
| Tick interval (ms) | 3170 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-tunneler.toml` |
| Listened events | `BlockBreakEvent` (HIGHEST, cancelled events ignored) |
| Menu stat lines | Bonus Blocks Per Dig; Extra Durability Per Bonus Block |
| Stat key | `excavation.tunneler.blocks-tunneled` |
| Milestones | `challenge_excavation_tunneler_10k` (10000, reward 600) |

Bonus blocks are `max(1, min(8, floor(levelPercent * bonusBlocksMax)))` taken from the eight cells around the origin. Plane orientation is horizontal when pitch is at or beyond 50 degrees up or down, otherwise vertical and perpendicular to your yaw. The sweep runs one tick after the original break and aborts if the origin block did not actually change. Shovel-friendly blocks are clay, dirt, coarse dirt, rooted dirt, farmland, grass block, dirt path, gravel, mycelium, podzol, sand, red sand, soul sand, soul soil, snow, snow block, mud, and muddy mangrove roots.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusBlocksMax` | `8` | Bonus blocks broken at full level. Hard-capped at 8. |
| `durabilityCostPerBonusBlock` | `1` | Durability points spent per bonus block. |
| `xpPerBonusBlock` | `1.5` | Excavation skill XP per bonus block broken. |

### Treasure Hunter

| Property | Value |
|----------|-------|
| Class | `ExcavationTreasureHunter` |
| Icon | `EMERALD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 3370 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-treasure-hunter.toml` |
| Listened events | `BlockBreakEvent` (HIGHEST, cancelled events ignored) |
| Menu stat lines | Treasure Chance; Treasures roll from a weighted archaeology table |
| Stat key | `excavation.treasure-hunter.treasures-found` |
| Milestones | `challenge_excavation_treasure_500` (500, reward 500) |

Eligible blocks are `SAND`, `RED_SAND`, `GRAVEL`, `MUD`, and `CLAY`. Chance is `min(maxTreasureChance, treasureChanceBase + levelPercent * treasureChanceFactor)`. Entries with weight 6 or lower are treated as rare and get an extra effect burst.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `treasureChanceBase` | `0.01` | Treasure chance per eligible block at level 0 progress, 0-1. |
| `treasureChanceFactor` | `0.05` | Extra treasure chance added at full level, 0-1. |
| `maxTreasureChance` | `0.06` | Hard ceiling on treasure chance, 0-1. |
| `lootTable` | `["BONE:30:1:2", "FLINT:30:1:2", "CLAY_BALL:15:1:3", "ANGLER_POTTERY_SHERD:6:1:1", "ARMS_UP_POTTERY_SHERD:6:1:1", "SKULL_POTTERY_SHERD:4:1:1", "EMERALD:3:1:1"]` | Weighted drops as `MATERIAL:weight:min:max`. Unparsable or zero-weight rows are dropped. |
| `xpPerTreasure` | `12` | Excavation skill XP per treasure found. |

### Soft Fall

| Property | Value |
|----------|-------|
| Class | `ExcavationSoftFall` |
| Icon | `SAND` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.55 |
| Tick interval (ms) | 3530 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-soft-fall.toml` |
| Listened events | `EntityDamageEvent` (HIGHEST) |
| Menu stat lines | Fall Damage Reduction; Applies when landing on dirt, sand, gravel, clay, mud, or soul sand |
| Stat key | `excavation.soft-fall.damage-prevented` |
| Milestones | `challenge_excavation_softfall_1k` (1000, reward 500) |

Only `FALL` damage on a player is considered. Reduction is `min(maxReduction, reductionBase + levelPercent * reductionFactor)`; a remaining damage value at or under 0.01 cancels the event outright.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `reductionBase` | `0.15` | Fraction of fall damage removed at level 0 progress, 0-1. |
| `reductionFactor` | `0.85` | Extra fraction removed at full level, 0-1. |
| `maxReduction` | `1.0` | Hard ceiling on the removed fraction, 0-1. |
| `xpPerDamagePrevented` | `3.0` | Excavation skill XP per half-heart of damage prevented. |

### Earth Mover

| Property | Value |
|----------|-------|
| Class | `ExcavationEarthMover` |
| Icon | `DIRT` |
| Max level | 5 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 6 |
| Cost factor | 0.8 |
| Tick interval (ms) | 3730 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-earth-mover.toml` |
| Listened events | `PlayerInteractEvent` (HIGHEST) |
| Menu stat lines | Wave Radius; Shovel Damage Multiplier; Knockback Force; Slow Duration; Wave Cooldown; Hunger Cost |
| Stat keys | `excavation.earth-mover.waves-unleashed`, `excavation.earth-mover.mobs-launched` |
| Milestones | `challenge_excavation_earthmover_250` (250, reward 450) |

Targets are entities implementing `Enemy`. Base shovel damage is 2.5 wooden and golden, 3.5 stone and copper, 4.5 iron, 5.5 diamond, 6.5 netherite, multiplied by `max(0, damageMultiplierBase + levelPercent * damageMultiplierFactor)`. Cooldown is `max(500, round((cooldownMillisBase - levelPercent * cooldownMillisFactor) * cooldownScale))` milliseconds. Hard caps override the config at 32 candidates, 16 affected, and 12 effect targets; the batch finishes after 20 ticks regardless.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `3` | Horizontal wave radius in blocks at level 0 progress. |
| `radiusFactor` | `5` | Blocks of radius added at full level. |
| `verticalRange` | `3` | Blocks above and below the caster that the wave reaches. |
| `forceBase` | `0.6` | Horizontal knockback velocity at level 0 progress. |
| `forceFactor` | `1.0` | Knockback velocity added at full level. |
| `damageMultiplierBase` | `0.75` | Multiplier on the held shovel's base damage at level 0 progress. |
| `damageMultiplierFactor` | `0.75` | Extra damage multiplier at full level. |
| `liftVelocity` | `0.35` | Upward velocity applied to launched mobs. |
| `slowTicksBase` | `40` | Slowness duration in ticks at level 0 progress. |
| `slowTicksFactor` | `60` | Ticks of slowness added at full level. |
| `slowAmplifierMax` | `2` | Slowness amplifier at full level; 0 at no progress. |
| `cooldownMillisBase` | `16000` | Pre-scale cooldown in milliseconds at level 0 progress. |
| `cooldownMillisFactor` | `8000` | Milliseconds removed from that cooldown at full level. |
| `cooldownScale` | `0.5` | Multiplier applied after level scaling. Clamped to 0-1. |
| `hungerCost` | `2` | Food points spent per wave. 0 disables the cost. |
| `xpPerMobHit` | `6` | Excavation skill XP per mob that actually took damage. |
| `maxCandidatesPerActivation` | `16` | Mobs inspected per wave. Clamped to 32. |
| `maxAffectedPerActivation` | `12` | Mobs launched per wave. Clamped to 16. |
| `maxTargetFxPerActivation` | `8` | Launched mobs that get their own particle burst. Clamped to 12. |

### Burrow

| Property | Value |
|----------|-------|
| Class | `ExcavationBurrow` |
| Icon | `COARSE_DIRT` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 5 |
| Cost factor | 0.78 |
| Tick interval (ms) | 4130 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-burrow.toml` |
| Listened events | `PlayerInteractEvent` (MONITOR, also receives cancelled events) |
| Menu stat lines | Max Burrow Depth; Durability Per Block; Burrow Cooldown; Hunger Cost |
| Stat keys | `excavation.burrow.burrows-dug`, `excavation.burrow.blocks-burrowed` |
| Milestones | `challenge_excavation_burrow_100` (100, reward 450) |

Depth is `max(2, round(depthBase + levelPercent * depthFactor))`. Cooldown is `max(2000, round(cooldownMillisBase - levelPercent * cooldownMillisFactor))` milliseconds. Planning stops at `worldMinHeight + safeFloorMargin`, at lava directly below, at a two-block air gap below, at any non shovel-friendly block, and at any block a protection plugin refuses. Shovel-friendly blocks match Tunneler's list.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `depthBase` | `3` | Blocks dug per activation at level 0 progress. |
| `depthFactor` | `13` | Blocks of depth added at full level. |
| `ticksPerBlock` | `2` | Server ticks between each block in the shaft. |
| `safeFloorMargin` | `16` | Blocks above the world floor where the shaft stops. |
| `durabilityCostPerBlock` | `1` | Durability points spent per block dug. |
| `hungerCost` | `1` | Food points spent per activation. 0 disables the cost. |
| `cooldownMillisBase` | `14000` | Milliseconds between burrows at level 0 progress. |
| `cooldownMillisFactor` | `7000` | Milliseconds removed from that cooldown at full level. |
| `xpPerBlock` | `2` | Excavation skill XP per block dug. |

### Grave Digger

| Property | Value |
|----------|-------|
| Class | `ExcavationGraveDigger` |
| Icon | `BONE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.68 |
| Tick interval (ms) | 4310 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-grave-digger.toml` |
| Listened events | `BlockBreakEvent` (HIGHEST, cancelled events ignored), `EntityDeathEvent` (MONITOR, effects only) |
| Menu stat lines | Bone Loot Chance; Disturbed Grave Chance |
| Stat keys | `excavation.grave-digger.bones-unearthed`, `excavation.grave-digger.graves-disturbed` |
| Milestones | `challenge_excavation_gravedigger_300` (300, reward 450) |

Eligible blocks are `DIRT`, `GRASS_BLOCK`, `COARSE_DIRT`, `ROOTED_DIRT`, `PODZOL`, `MYCELIUM`, and `DIRT_PATH`. Loot and grave rolls are independent. Grave mobs are a zombie or skeleton chosen at random, spawned already targeting you and tagged with `adapt:excavation_grave_mob` in persistent data.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `lootChanceBase` | `0.008` | Loot chance per eligible block at level 0 progress, 0-1. |
| `lootChanceFactor` | `0.035` | Extra loot chance added at full level, 0-1. |
| `maxLootChance` | `0.045` | Hard ceiling on loot chance, 0-1. |
| `graveChanceBase` | `0.001` | Grave spawn chance at level 0 progress, 0-1. |
| `graveChanceFactor` | `0.004` | Extra grave chance added at full level, 0-1. |
| `maxGraveChance` | `0.005` | Hard ceiling on grave chance, 0-1. |
| `graveCooldownMillis` | `45000` | Minimum milliseconds between grave spawns for one player. |
| `lootTable` | `["BONE:40:1:2", "BONE_MEAL:25:2:4", "ROTTEN_FLESH:20:1:2", "BONE_BLOCK:6:1:1", "SKELETON_SKULL:2:1:1"]` | Weighted drops as `MATERIAL:weight:min:max`. |
| `xpPerLoot` | `8` | Excavation skill XP per loot drop. |
| `xpPerGrave` | `35` | Excavation skill XP per disturbed grave. |

### Mudlark

| Property | Value |
|----------|-------|
| Class | `ExcavationMudlark` |
| Icon | `MUD` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.6 |
| Tick interval (ms) | 4530 |
| Config file | `plugins/Adapt/adapt/adaptations/excavation-mudlark.toml` |
| Listened events | `BlockDamageEvent` (HIGHEST), `BlockBreakEvent` (HIGHEST, cancelled events ignored) |
| Menu stat lines | Bonus Drop Chance; x Levels of haste while digging wet |
| Stat key | `excavation.mudlark.bonus-drops` |
| Milestones | `challenge_excavation_mudlark_1k` (1000, reward 500) |

Bonus-drop blocks and their extra drop: `CLAY` gives a clay ball, `MUD` and `MUDDY_MANGROVE_ROOTS` give mud, `SOUL_SAND` gives soul sand, `SOUL_SOIL` gives soul soil. Bonus chance is `min(maxBonusChance, bonusChanceBase + levelPercent * bonusChanceFactor)`. The wet-dig bonus is `BLOCK_BREAK_SPEED` as an `ADD_SCALAR` of `0.20 * (amplifier + 1)`, where amplifier is `round(levelPercent * (maxHasteLevel - 1))`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusChanceBase` | `0.05` | Bonus drop chance at level 0 progress, 0-1. |
| `bonusChanceFactor` | `0.2` | Extra bonus drop chance added at full level, 0-1. |
| `maxBonusChance` | `0.25` | Hard ceiling on bonus drop chance, 0-1. |
| `maxHasteLevel` | `3` | Displayed haste steps at full level; drives the break-speed scalar. |
| `hasteDurationTicks` | `60` | Ticks the wet-dig speed bonus lasts. 0 or less disables it. |
| `xpPerBonusDrop` | `3` | Excavation skill XP per bonus drop. |

## See also

- `02 - Concepts.md` for skills, adaptations, and knowledge
- `03 - Player Usage.md` for the Adapt menu and learning flow
- `10 - Skills Catalog.md` for the full skill list
- `04 - Commands & Permissions.md` for the `adapt.use` permission tree
