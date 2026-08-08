# Skill: Brewing

Skill id `brewing`. Earn XP by brewing potions. Brewing has 13 registered adaptations, including additional potion recipes, and uses the `LINGERING_POTION` icon.

**XP sources:** brewing and using potions, including drink, splash, lingering, and brewing-stand activity.

**Milestones / challenges** (stat keys):

- `challenge_brew_1k` tracking `brewing.consumed`
- `challenge_brew_5k` tracking `brewing.consumed`
- `challenge_brewsplash_1k` tracking `brewing.splashes`
- `challenge_brewsplash_5k` tracking `brewing.splashes`
- `challenge_brew_stands_10` tracking `brewing.stands.placed`
- `challenge_brew_stands_50` tracking `brewing.stands.placed`
- `challenge_brew_strong_25` tracking `brewing.strong`
- `challenge_brew_strong_250` tracking `brewing.strong`
- `challenge_brew_splash_hits_50` tracking `brewing.splash.hits`
- `challenge_brew_splash_hits_500` tracking `brewing.splash.hits`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `brewing` |
| Class | `SkillBrewing` |
| Icon | `LINGERING_POTION` |
| Color | `LIGHT_PURPLE` |
| Interval (ms) | `5851` |
| Skill config | `plugins/Adapt/adapt/skills/brewing.toml` |
| Adaptation count | 13 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/brewing.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&d"` | Legacy ampersand color code used for this skill in menus and text. |
| `challengeBrew1k` | `1000` | Challenge brew 1 k. |
| `challengeBrewSplash1k` | `1000` | Challenge brew splash 1 k. |
| `splashXP` | `100` | XP awarded for splash. |
| `cooldownDelay` | `2500` | Minimum delay between passive skill XP awards, in milliseconds. |
| `splashMultiplier` | `0.4` | Unitless XP multiplier applied to splash-potion rewards. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Lingering Brew (`brewing-lingering`)

Brewed potions last longer.

**Runtime entry points:** when a brew finishes; periodic evaluation every 4788 ms.

**Menu displays:** Duration; Duration.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BrewingLingering` |
| Icon | `DRAGON_BREATH` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 3 |
| Cost factor | 0.75 |
| Tick interval (ms) | 4788 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-lingering.toml` |

Listened events:

- `BrewEvent` (`on`) — when a brew finishes

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseDurationBoostTicks` | `100` | Base duration boost ticks. Server ticks (20 ticks = 1 second). |
| `durationBoostFactorTicks` | `500` | Duration boost factor ticks. Server ticks (20 ticks = 1 second). |
| `durationMultiplierFactor` | `0.45` | Duration multiplier factor. Unitless multiplier. |
| `baseDurationMultiplier` | `0.05` | Base duration multiplier. Unitless multiplier. |
| `useCustomLore` | `true` | Use custom lore. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Super Heated Brew (`brewing-super-heated`)

Brewing stands work faster the hotter they are.

**Runtime entry points:** on `InventoryMoveItemEvent`; when a brew finishes; on inventory click; periodic evaluation every 253 ms while its conditions hold.

**Menu displays:** Per Touching Fire Block; Per Touching Lava Block.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BrewingSuperHeated` |
| Icon | `LAVA_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 3 |
| Cost factor | 0.75 |
| Tick interval (ms) | 253 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-super-heated.toml` |

Listened events:

- `InventoryMoveItemEvent` (`on`)
- `BrewEvent` (`on`) — when a brew finishes
- `InventoryClickEvent` (`on`) — on inventory click

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `multiplierFactor` | `1.33` | Multiplier factor. Unitless multiplier. |
| `fireMultiplier` | `0.14` | Fire multiplier. Unitless multiplier. |
| `lavaMultiplier` | `0.69` | Lava multiplier. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bottled Absorption (`brewing-absorption`)

Unlocks brewing Potions of Absorption for temporary bonus hearts.

**Runtime entry points:** on `AdaptBrewCompleteEvent`; periodic evaluation every 1333 ms.

**Menu displays:** Instant Heal + Quartz = Potion of Absorption (60 seconds); Instant Heal + Quartz Block = Potion of Absorption-2 (30 seconds).

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BrewingAbsorption` |
| Icon | `QUARTZ` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 1333 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-absorption.toml` |

Listened events:

- `AdaptBrewCompleteEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bottled Blindness (`brewing-blindness`)

Unlocks brewing Potions of Blindness, which shroud a target's sight.

**Runtime entry points:** on `AdaptBrewCompleteEvent`; periodic evaluation every 1333 ms.

**Menu displays:** Awkward Potion + Ink sack = Potion of Blindness (30 seconds); Awkward Potion + Glowing Ink Sack = Potion of Blindness-2 (15 seconds).

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BrewingBlindness` |
| Icon | `INK_SAC` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 1333 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-blindness.toml` |

Listened events:

- `AdaptBrewCompleteEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bottled Darkness (`brewing-darkness`)

Unlocks brewing Potions of Darkness, which shroud vision and prevent sprinting.

**Runtime entry points:** on `AdaptBrewCompleteEvent`; periodic evaluation every 1335 ms.

**Menu displays:** NightVision Potion + Black Concrete = Potion of Darkness (30 seconds); Note: Darkness prevents the drinker from sprinting.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BrewingDarkness` |
| Icon | `BLACK_CONCRETE` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 1335 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-darkness.toml` |

Listened events:

- `AdaptBrewCompleteEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bottled Decay (`brewing-decay`)

Unlocks brewing Potions of Wither, which afflict a target with decay.

**Runtime entry points:** on `AdaptBrewCompleteEvent`; periodic evaluation every 1334 ms.

**Menu displays:** Weakness Potion + Poisonous Potato = Potion of Wither (16 seconds); Weakness Potion + Crimson Roots = Potion of Wither-2 (8 seconds).

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BrewingDecay` |
| Icon | `WITHER_ROSE` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 1334 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-decay.toml` |

Listened events:

- `AdaptBrewCompleteEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bottled Fatigue (`brewing-fatigue`)

Unlocks brewing Potions of Mining Fatigue, which slow a target's digging and attacks.

**Runtime entry points:** on `AdaptBrewCompleteEvent`; periodic evaluation every 1332 ms.

**Menu displays:** Weakness Potion + Slime Ball = Potion of Fatigue (60 seconds); Weakness Potion + Slime Block = Potion of Fatigue-2 (30 seconds).

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BrewingFatigue` |
| Icon | `SLIME_BALL` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 1332 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-fatigue.toml` |

Listened events:

- `AdaptBrewCompleteEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bottled Haste (`brewing-haste`)

Unlocks brewing Potions of Haste for faster mining, when Efficiency is not enough.

**Runtime entry points:** on `AdaptBrewCompleteEvent`; periodic evaluation every 1334 ms.

**Menu displays:** Speed Potion + Amethyst Shard = Potion of Haste (60 seconds); Speed Potion + Amethyst Block = Potion of Haste-2 (30 seconds).

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BrewingHaste` |
| Icon | `AMETHYST_SHARD` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 1334 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-haste.toml` |

Listened events:

- `AdaptBrewCompleteEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bottled Life (`brewing-healthboost`)

Unlocks brewing Potions of Health Boost for extra maximum hearts.

**Runtime entry points:** on `AdaptBrewCompleteEvent`; periodic evaluation every 1330 ms.

**Menu displays:** Instant-Healing Potion + Golden Apple = Potion of Health Boost (60 seconds); Instant-Healing Potion + Enchanted Golden Apple = Potion of Health Boost-2 (60 seconds).

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BrewingHealthBoost` |
| Icon | `ENCHANTED_GOLDEN_APPLE` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 1330 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-healthboost.toml` |

Listened events:

- `AdaptBrewCompleteEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bottled Hunger (`brewing-hunger`)

Unlocks brewing Potions of Hunger, which drain a target's food.

**Runtime entry points:** on `AdaptBrewCompleteEvent`; periodic evaluation every 1331 ms.

**Menu displays:** Awkward Potion + Rotten Flesh = Potion of Hunger (60 seconds); Weakness Potion + Rotten Flesh = Potion of Hunger-2 (30 seconds).

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BrewingHunger` |
| Icon | `ROTTEN_FLESH` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 1331 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-hunger.toml` |

Listened events:

- `AdaptBrewCompleteEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bottled Nausea (`brewing-nausea`)

Unlocks brewing Potions of Nausea, which warp a target's vision.

**Runtime entry points:** on `AdaptBrewCompleteEvent`; periodic evaluation every 1333 ms.

**Menu displays:** Awkward Potion + Brown Mushroom = Potion of Nausea (30 seconds); Awkward Potion + Crimson Fungus = Potion of Nausea-2 (15 seconds).

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BrewingNausea` |
| Icon | `CRIMSON_FUNGUS` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 1333 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-nausea.toml` |

Listened events:

- `AdaptBrewCompleteEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bottled Resistance (`brewing-resistance`)

Unlocks brewing Potions of Resistance, which reduce incoming damage.

**Runtime entry points:** on `AdaptBrewCompleteEvent`; periodic evaluation every 1333 ms.

**Menu displays:** Awkward Potion + Iron Ingot = Potion of Resistance (60 seconds); Awkward Potion + Iron Block = Potion of Resistance-2 (30 seconds).

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BrewingResistance` |
| Icon | `IRON_BLOCK` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 1333 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-resistance.toml` |

Listened events:

- `AdaptBrewCompleteEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bottled Saturation (`brewing-saturation`)

Unlocks brewing Potions of Saturation, which restore hunger.

**Runtime entry points:** on `AdaptBrewCompleteEvent`; periodic evaluation every 1334 ms.

**Menu displays:** Regen Potion + Baked Potato = Potion of Saturation; Regen Potion + Hay Bale = Potion of Saturation-2.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `BrewingSaturation` |
| Icon | `BAKED_POTATO` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 1334 |
| Config file | `plugins/Adapt/adapt/adaptations/brewing-saturation.toml` |

Listened events:

- `AdaptBrewCompleteEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
