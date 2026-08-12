# Skill: Brewing

Brewing is the potion skill. You level it by actually using potions, not by staring at a brewing stand: drinking one pays out, and landing a splash potion pays out. The stronger the effect and the longer it lasts, the more it is worth.

Two of the adaptations change how brewing works. Lingering Brew stretches the duration of everything that comes out of your stand. Super Heated Brew makes a stand surrounded by fire or lava run faster, which turns a lava-lined brewing room into a real build instead of decoration.

The other eleven unlock potions vanilla does not let you make. Absorption, Health Boost, Resistance, Haste and Saturation on the useful side; Blindness, Darkness, Decay, Fatigue, Hunger and Nausea on the throwing side. Each one adds two recipes (Darkness adds one), a normal strength and a stronger short version, and each is capped at level 1.

Every custom recipe is permanent once learned, so pick the ones you want. The brewing stand also remembers who owns it, which is how Lingering Brew and Super Heated Brew know whose adaptation level to use.

## Earning XP

Drinking a potion pays a base award plus a bonus scaled off the potion's custom effects and whether it is an upgraded (level II) potion. Water, mundane, thick and awkward potions are ignored, since they do nothing.

Throwing a splash potion pays the same base award plus a bonus for the total effect power of the splash. It also records how many entities the cloud actually caught.

Both awards share one cooldown, so chugging a stack does not pay per bottle. Placing a brewing stand records a stat for the placement challenges but pays no XP on its own.

## Adaptations

All of these need the same things before they do anything: the adaptation learned at level 1 or higher, the Brewing skill and the adaptation both enabled, the player holding the matching `adapt.use.` permission (or the `adapt.use.*` wildcard), and any protection or region plugin on the server allowing the action.

### Lingering Brew (`brewing-lingering`)

Everything with a duration that comes out of your brewing stand comes out longer. Adapt takes the base potion's effects, adds a flat tick bonus, and multiplies the original duration on top of that. Instant effects like Instant Health are left alone.

Works on its own once learned. Brew as normal.

The stand has to have a recorded owner, and the boost uses that owner's level, not the level of whoever pulls the bottles out. A stand gets its owner recorded when a player places it, or the first time a player opens one that has no owner yet. Extended potions get rewritten lore listing each effect with its new duration, and the vanilla effect tooltip is hidden so the numbers do not appear twice.

### Super Heated Brew (`brewing-super-heated`)

A brewing stand shaves time off its brew for every fire or lava block touching it. Adapt checks the block underneath and the four sides. Lava counts for far more than fire.

How to use it:

1. Learn Super Heated Brew.
2. Place a brewing stand you own.
3. Put fire or lava directly below it or against any of its four sides.
4. Brew as normal. The timer runs down faster while the stand is hot.

Like Lingering Brew, this uses the stand owner's level, and the owner has to be online. Adapt only watches stands it has recently seen activity on, so a stand that has been idle for a while stops being checked until someone touches it again.

### Brewing custom potions

The eleven potion adaptations below all work the same way. Each adds recipes to the brewing stand that vanilla does not have.

1. Learn the adaptation you want in the Adapt menu. All eleven are permanent, so you cannot refund them later.
2. Put blaze powder in the fuel slot. Custom recipes charge fuel like vanilla ones do.
3. Put the required base potion in the bottle slots.
4. Pick up the custom ingredient and left-click it into the ingredient slot yourself.

That last step matters. Adapt watches for the click rather than for the item appearing, checks that the click was not cancelled, and then confirms one tick later that you still have the same brewing stand open before it starts the brew. Dropping the ingredient in by hopper or dragging it will not start a custom brew. The player who clicks the ingredient in is the one whose adaptation is checked.

### Bottled Absorption (`brewing-absorption`)

Adds Potions of Absorption, which give temporary bonus hearts on top of your real health. Quartz gives the normal version, a block of quartz gives the shorter, stronger one. Both brew from an Instant Health potion.

### Bottled Blindness (`brewing-blindness`)

Adds Potions of Blindness, which black out whatever they hit. Throwing material. An ink sac gives the normal version, a glow ink sac the shorter, stronger one, both from an awkward potion.

### Bottled Darkness (`brewing-darkness`)

Adds a Potion of Darkness, which drops a shroud over the target's vision. It brews from a Night Vision potion with black concrete, and there is only one strength. The menu lore also claims Darkness stops the drinker sprinting; that is a claim about the vanilla effect and nothing in Adapt enforces it.

### Bottled Decay (`brewing-decay`)

Adds Potions of Wither. A poisonous potato on a Weakness potion gives the normal version, crimson roots the shorter, stronger one. Wither ticks damage through armor, so this is one of the better throwables in the set.

### Bottled Fatigue (`brewing-fatigue`)

Adds Potions of Mining Fatigue, which slow a target's digging and swing speed. A slime ball on a Weakness potion for the normal version, a slime block for the stronger one.

### Bottled Haste (`brewing-haste`)

Adds Potions of Haste for when Efficiency V still is not fast enough. An amethyst shard on a Speed potion for the normal version, an amethyst block for the stronger one.

### Bottled Life (`brewing-healthboost`)

Adds Potions of Health Boost, which raise your maximum hearts rather than healing you. A golden apple on an Instant Health potion for the normal version, an enchanted golden apple for the stronger one. Both last the same length; the enchanted apple buys you the extra tier, not extra time.

### Bottled Hunger (`brewing-hunger`)

Adds Potions of Hunger, which drain the target's food bar. Rotten flesh on an awkward potion gives the normal version, and the same rotten flesh on a Weakness potion gives the stronger one.

### Bottled Nausea (`brewing-nausea`)

Adds Potions of Nausea, which warp the target's screen. A brown mushroom on an awkward potion for the normal version, a crimson fungus for the shorter, stronger one.

### Bottled Resistance (`brewing-resistance`)

Adds Potions of Resistance, which cut all incoming damage. An iron ingot on an awkward potion for the normal version, an iron block for the stronger one.

### Bottled Saturation (`brewing-saturation`)

Adds Potions of Saturation, which refill hunger instantly instead of over time. A baked potato on a Regeneration potion for the normal version, a hay bale for the stronger one. Both are instant, so duration does not apply.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `brewing` |
| Class | `SkillBrewing` |
| Icon | `LINGERING_POTION` |
| Color | `LIGHT_PURPLE` |
| Interval (ms) | `5851` |
| Skill config | `plugins/Adapt/adapt/skills/brewing.toml` |
| Adaptation count | 13 |

`SkillBrewing` also registers the `BrewingStandOwnerMatter` spatial slice type, which is what stores brewing-stand ownership per world.

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/brewing.toml` on first load.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `enabled` | `true` | Turns the whole Brewing skill off when false. |
| `skillColor` | `"&d"` | Legacy ampersand color code used for this skill in menus and text. |
| `challengeBrew1k` | `1000` | XP paid by the consumed, stand-placed and strong-potion milestones. The larger tier of each pair pays double. |
| `challengeBrewSplash1k` | `1000` | XP paid by the splash and splash-hit milestones. The larger tier of each pair pays double. |
| `splashXP` | `100` | Flat XP paid for drinking a potion and for landing a splash potion. Despite the name it covers both. |
| `cooldownDelay` | `2500` | Milliseconds between Brewing XP awards. |
| `splashMultiplier` | `0.4` | Multiplier applied to a potion's summed effect power, where each effect counts as amplifier plus one times its duration in seconds. Drinking also adds this multiplier times 25, or times 50 for an upgraded potion. |

### Milestones and stat keys

| Milestone key | Stat key | Threshold |
|---------------|----------|-----------|
| `challenge_brew_1k` | `brewing.consumed` | 1000 |
| `challenge_brew_5k` | `brewing.consumed` | 5000 |
| `challenge_brewsplash_1k` | `brewing.splashes` | 1000 |
| `challenge_brewsplash_5k` | `brewing.splashes` | 5000 |
| `challenge_brew_stands_10` | `brewing.stands.placed` | 10 |
| `challenge_brew_stands_50` | `brewing.stands.placed` | 50 |
| `challenge_brew_strong_25` | `brewing.strong` | 25 |
| `challenge_brew_strong_250` | `brewing.strong` | 250 |
| `challenge_brew_splash_hits_50` | `brewing.splash.hits` | 50 |
| `challenge_brew_splash_hits_500` | `brewing.splash.hits` | 500 |
| `challenge_brewing_lingering_200` | `brewing.lingering.potions-extended` | 200 |
| `challenge_brewing_lingering_5k` | `brewing.lingering.potions-extended` | 5000 |
| `challenge_brewing_super_heated_100` | `brewing.super-heated.brews-accelerated` | 100 |
| `challenge_brewing_super_heated_2500` | `brewing.super-heated.brews-accelerated` | 2500 |
| `challenge_brewing_absorption_25` | `brewing.absorption.potions-brewed` | 25 |
| `challenge_brewing_blindness_25` | `brewing.blindness.potions-brewed` | 25 |
| `challenge_brewing_darkness_25` | `brewing.darkness.potions-brewed` | 25 |
| `challenge_brewing_decay_25` | `brewing.decay.potions-brewed` | 25 |
| `challenge_brewing_fatigue_25` | `brewing.fatigue.potions-brewed` | 25 |
| `challenge_brewing_haste_25` | `brewing.haste.potions-brewed` | 25 |
| `challenge_brewing_health_boost_25` | `brewing.health-boost.potions-brewed` | 25 |
| `challenge_brewing_hunger_25` | `brewing.hunger.potions-brewed` | 25 |
| `challenge_brewing_nausea_25` | `brewing.nausea.potions-brewed` | 25 |
| `challenge_brewing_resistance_25` | `brewing.resistance.potions-brewed` | 25 |
| `challenge_brewing_saturation_25` | `brewing.saturation.potions-brewed` | 25 |

`brewing.strong` counts drinks of upgraded (level II) potions.

### Shared adaptation config keys

Every adaptation TOML at `plugins/Adapt/adapt/adaptations/<id>.toml` carries these keys on top of its own.

| Key | Default | What it does |
|-----|---------|--------------|
| `enabled` | `true` | Turns this adaptation off when false. |
| `permanent` | `false` | When true, learning it is one-way and it cannot be unlearned. |
| `showParticles` | `true` | Plays this adaptation's particle effects. |
| `showSounds` | `true` | Plays this adaptation's sound effects. |
| `baseCost` | per adaptation | Knowledge cost per level past the first. |
| `costFactor` | per adaptation | Growth applied to level-to-level knowledge cost. |
| `maxLevel` | per adaptation | Highest level a player can buy. |
| `initialCost` | per adaptation | Knowledge cost of level 1. |

The tick interval below is the adaptation's background tick rate. Only Super Heated Brew does work on that tick, and only while it has a stand to watch; for every other Brewing adaptation the interval is idle bookkeeping.

All eleven potion adaptations share the same progression: max level 1, initial knowledge cost 2, base knowledge cost 3, cost factor 1, and `permanent` defaulting to `true`. They all listen to `AdaptBrewCompleteEvent` and use it only to record the brewed-potion stat and play effects; the recipes themselves are gated by `BrewingManager`. Every custom recipe brews for 320 ticks, which is 16 seconds. Fuel cost is in the same units the stand stores, where one blaze powder is 20 units.

### Lingering Brew

| Property | Value |
|----------|-------|
| Class | `BrewingLingering` |
| Icon | `DRAGON_BREATH` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 3 |
| Cost factor | 0.75 |
| Tick interval (ms) | 4788 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-lingering.toml` |

Listened events: `BrewEvent`.

New duration for each non-instant effect is the flat tick bonus plus the original duration times the multiplier. The multiplier curve squares level progress, so most of the gain arrives at high levels.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `baseDurationBoostTicks` | `100` | Flat ticks added to every non-instant effect at level 1. |
| `durationBoostFactorTicks` | `500` | Extra flat ticks added across the full level range. |
| `durationMultiplierFactor` | `0.45` | Size of the percentage stretch at max level, applied against squared level progress. |
| `baseDurationMultiplier` | `0.05` | Percentage stretch applied at every level regardless of progress. |
| `useCustomLore` | `true` | Rewrites the potion's lore with each effect and its new duration, and hides the vanilla effect tooltip. |

### Super Heated Brew

| Property | Value |
|----------|-------|
| Class | `BrewingSuperHeated` |
| Icon | `LAVA_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 3 |
| Cost factor | 0.75 |
| Tick interval (ms) | 253 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-super-heated.toml` |

Listened events: `InventoryMoveItemEvent`, `BrewEvent` and `InventoryClickEvent`, all of which mark the stand as recently active so it gets ticked.

Heat sources are counted on five faces: the block below the stand and the four sides. Each tick removes `ceil(interval_in_ticks * total_percent)` from the brew timer, where total percent is the fire boost times the fire block count plus the lava boost times the lava block count. A stand with no brew running is checked a few more times and then dropped from the watch list.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `multiplierFactor` | `1.33` | Scales both heat boosts by level progress. Effective boost is the per-source multiplier times this times level progress. |
| `fireMultiplier` | `0.14` | Fraction of the check interval removed per touching fire block, before level scaling. |
| `lavaMultiplier` | `0.69` | Fraction of the check interval removed per touching lava block, before level scaling. |

### Bottled Absorption

Icon `QUARTZ`, tick interval 1333 ms, config `plugins/Adapt/adapt/adaptations/brewing-absorption.toml`, class `BrewingAbsorption`.

| Recipe id | Base potion | Ingredient | Effect | Duration | Amplifier | Fuel |
|-----------|-------------|------------|--------|----------|-----------|------|
| `brewing-absorption-1` | Instant Health | `QUARTZ` | `ABSORPTION` | 1200 ticks (60 s) | 0 | 16 |
| `brewing-absorption-2` | Instant Health | `QUARTZ_BLOCK` | `ABSORPTION` | 600 ticks (30 s) | 1 | 32 |

### Bottled Blindness

Icon `INK_SAC`, tick interval 1333 ms, config `plugins/Adapt/adapt/adaptations/brewing-blindness.toml`, class `BrewingBlindness`.

| Recipe id | Base potion | Ingredient | Effect | Duration | Amplifier | Fuel |
|-----------|-------------|------------|--------|----------|-----------|------|
| `brewing-blindness-1` | `AWKWARD` | `INK_SAC` | `BLINDNESS` | 600 ticks (30 s) | 0 | 16 |
| `brewing-blindness-2` | `AWKWARD` | `GLOW_INK_SAC` | `BLINDNESS` | 300 ticks (15 s) | 1 | 32 |

### Bottled Darkness

Icon `BLACK_CONCRETE`, tick interval 1335 ms, config `plugins/Adapt/adapt/adaptations/brewing-darkness.toml`, class `BrewingDarkness`.

| Recipe id | Base potion | Ingredient | Effect | Duration | Amplifier | Fuel |
|-----------|-------------|------------|--------|----------|-----------|------|
| `brewing-darkness` | `NIGHT_VISION` | `BLACK_CONCRETE` | `DARKNESS` | 600 ticks (30 s) | 0 | 16 |

### Bottled Decay

Icon `WITHER_ROSE`, tick interval 1334 ms, config `plugins/Adapt/adapt/adaptations/brewing-decay.toml`, class `BrewingDecay`.

| Recipe id | Base potion | Ingredient | Effect | Duration | Amplifier | Fuel |
|-----------|-------------|------------|--------|----------|-----------|------|
| `brewing-decay-1` | `WEAKNESS` | `POISONOUS_POTATO` | `WITHER` | 320 ticks (16 s) | 0 | 16 |
| `brewing-decay-2` | `WEAKNESS` | `CRIMSON_ROOTS` | `WITHER` | 160 ticks (8 s) | 1 | 32 |

### Bottled Fatigue

Icon `SLIME_BALL`, tick interval 1332 ms, config `plugins/Adapt/adapt/adaptations/brewing-fatigue.toml`, class `BrewingFatigue`.

| Recipe id | Base potion | Ingredient | Effect | Duration | Amplifier | Fuel |
|-----------|-------------|------------|--------|----------|-----------|------|
| `brewing-fatigue-1` | `WEAKNESS` | `SLIME_BALL` | `SLOW_DIGGING` (Mining Fatigue) | 1200 ticks (60 s) | 0 | 16 |
| `brewing-fatigue-2` | `WEAKNESS` | `SLIME_BLOCK` | `SLOW_DIGGING` | 600 ticks (30 s) | 1 | 32 |

### Bottled Haste

Icon `AMETHYST_SHARD`, tick interval 1334 ms, config `plugins/Adapt/adapt/adaptations/brewing-haste.toml`, class `BrewingHaste`.

| Recipe id | Base potion | Ingredient | Effect | Duration | Amplifier | Fuel |
|-----------|-------------|------------|--------|----------|-----------|------|
| `brewing-haste-1` | Speed | `AMETHYST_SHARD` | `FAST_DIGGING` (Haste) | 1200 ticks (60 s) | 0 | 16 |
| `brewing-haste-2` | Speed | `AMETHYST_BLOCK` | `FAST_DIGGING` | 600 ticks (30 s) | 1 | 32 |

### Bottled Life

Icon `ENCHANTED_GOLDEN_APPLE`, tick interval 1330 ms, config `plugins/Adapt/adapt/adaptations/brewing-healthboost.toml`, class `BrewingHealthBoost`.

| Recipe id | Base potion | Ingredient | Effect | Duration | Amplifier | Fuel |
|-----------|-------------|------------|--------|----------|-----------|------|
| `brewing-healthboost-1` | Instant Health | `GOLDEN_APPLE` | `HEALTH_BOOST` | 1200 ticks (60 s) | 0 | 16 |
| `brewing-healthboost-2` | Instant Health | `ENCHANTED_GOLDEN_APPLE` | `HEALTH_BOOST` | 1200 ticks (60 s) | 1 | 32 |

### Bottled Hunger

Icon `ROTTEN_FLESH`, tick interval 1331 ms, config `plugins/Adapt/adapt/adaptations/brewing-hunger.toml`, class `BrewingHunger`.

| Recipe id | Base potion | Ingredient | Effect | Duration | Amplifier | Fuel |
|-----------|-------------|------------|--------|----------|-----------|------|
| `brewing-hunger-1` | `AWKWARD` | `ROTTEN_FLESH` | `HUNGER` | 1200 ticks (60 s) | 0 | 16 |
| `brewing-hunger-2` | `WEAKNESS` | `ROTTEN_FLESH` | `HUNGER` | 600 ticks (30 s) | 1 | 32 |

### Bottled Nausea

Icon `CRIMSON_FUNGUS`, tick interval 1333 ms, config `plugins/Adapt/adapt/adaptations/brewing-nausea.toml`, class `BrewingNausea`.

| Recipe id | Base potion | Ingredient | Effect | Duration | Amplifier | Fuel |
|-----------|-------------|------------|--------|----------|-----------|------|
| `brewing-nausea-1` | `AWKWARD` | `BROWN_MUSHROOM` | `CONFUSION` (Nausea) | 600 ticks (30 s) | 0 | 16 |
| `brewing-nausea-2` | `AWKWARD` | `CRIMSON_FUNGUS` | `CONFUSION` | 300 ticks (15 s) | 1 | 32 |

### Bottled Resistance

Icon `IRON_BLOCK`, tick interval 1333 ms, config `plugins/Adapt/adapt/adaptations/brewing-resistance.toml`, class `BrewingResistance`.

| Recipe id | Base potion | Ingredient | Effect | Duration | Amplifier | Fuel |
|-----------|-------------|------------|--------|----------|-----------|------|
| `brewing-resistance-1` | `AWKWARD` | `IRON_INGOT` | `RESISTANCE` | 1200 ticks (60 s) | 0 | 16 |
| `brewing-resistance-2` | `AWKWARD` | `IRON_BLOCK` | `RESISTANCE` | 600 ticks (30 s) | 1 | 32 |

### Bottled Saturation

Icon `BAKED_POTATO`, tick interval 1334 ms, config `plugins/Adapt/adapt/adaptations/brewing-saturation.toml`, class `BrewingSaturation`.

| Recipe id | Base potion | Ingredient | Effect | Duration | Amplifier | Fuel |
|-----------|-------------|------------|--------|----------|-----------|------|
| `brewing-saturation-1` | Regeneration | `BAKED_POTATO` | `SATURATION` | 1 tick (instant) | 4 | 16 |
| `brewing-saturation-2` | Regeneration | `HAY_BLOCK` | `SATURATION` | 1 tick (instant) | 8 | 32 |

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
