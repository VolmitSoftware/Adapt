# Skill: Hunter

Hunter is the mob-killing skill. You earn `hunter` XP whenever you land the killing blow on a mob, and the reward scales with that mob's max health, so a ravager pays far more than a chicken. Creepers pay double, mobs that came out of a spawner pay a fraction, and there is a short cooldown so a single crowd wipe cannot dump a hundred payouts at once.

Most of the Hunter tree is built around getting hit rather than hitting. Seven adaptations watch for damage landing on you and hand back a short buff: regeneration, invisibility, jump height, luck, speed, attack damage, or damage resistance. Each one charges you hunger for the privilege. Fight with an empty food bar and the same trigger gives you Poison instead of the buff. That trade is the feel of the skill. You stay in the fight longer by burning through food.

The rest of the tree is about the kill itself. Adrenaline pays you for fighting at low health. Predator Focus rewards staying on one target instead of flailing at the whole room. Big Game Hunter turns ravagers and bosses into loot. Trophy Skinner gives extra materials and the occasional mob head for clean kills. Blood Trail paints a glowing line behind wounded mobs that only you can see. Snare Line lets you craft traps that pin hostiles in place. Drop-To-Inventory is the quality-of-life pick, sending loot into your bag instead of onto the floor.

Killing an ender dragon, wither, elder guardian, or warden also fires a celebration effect and counts toward its own challenge line, which pays a much larger lump of XP than a normal kill.

## Adaptations

Everything below needs the same conditions: the adaptation learned at level 1 or higher, the Hunter skill and that adaptation both enabled in config, the `adapt.use.*` permission (or the matching per-adaptation node), and any protection plugin on your server allowing the action where you are standing.

The seven "when struck" buffs share a few more rules. They fire on most damage, but never on fall, void, lava, hot floor, suffocation, cramming, melting, wither damage, thorns, sonic boom, flying into a wall, or `/kill`. If you already have the Hunger effect on you they stay quiet, which is what `preventHunterSkillsWhenHungerApplied` in the main Adapt config controls. With food in your bar you get the buff and a Hunger effect on top; with an empty bar you get Poison and no buff. Set `useConsumable` to true on any of them and they eat one rotten flesh from your inventory per activation instead of applying Hunger. None of them re-trigger while their own effect is still running unless you turn `stackBuff` on.

### Adrenaline (`hunter-adrenaline`)

Your melee swings hit harder the lower your health is. At full health it does nothing at all; the bonus scales up as your health falls, so at half health you get half of the listed maximum. It reads melee only, meaning you have to be the one swinging, not a bow. Kills you land below 35 percent health count toward its challenges. Works on its own once learned.

### Hunter's Regen (`hunter-regen`)

Taking a hit gives you Regeneration at an amplifier equal to your adaptation level, running 1.5 seconds per level. The cost is a Hunger effect for 2.5 seconds per level. Good pick if you keep getting chipped down in long fights. Works on its own once learned.

### Vanishing Step (`hunter-invis`)

Taking a hit turns you invisible for 5 seconds per level, at the same Hunger cost as the other struck buffs. Useful for breaking off a fight you are losing. Works on its own once learned.

### Hunter's Heights (`hunter-jumpboost`)

Taking a hit raises your jump strength and your safe fall distance for 5 seconds per level. It uses attributes rather than the Jump Boost potion, and it will not fire while you already have a Jump Boost potion on you. Works on its own once learned.

### Hunter's Luck (`hunter-luck`)

Taking a hit raises your Luck attribute for 5 seconds per level, which improves loot table rolls on fishing and chests. Starving instead applies Poison and a matching negative Luck penalty, so fighting on empty actively hurts your drops. Dying clears both timers. Works on its own once learned.

### Hunter's Speed (`hunter-speed`)

Taking a hit shoves you along whatever direction you are steering, for 5 seconds per level. It is a forced velocity burst rather than the Speed potion: it accelerates you toward a target speed while you hold a movement key and brakes when you let go. Higher levels raise the target speed, up to the configured cap. Works on its own once learned.

### Hunter's Strength (`hunter-strength`)

Taking a hit adds a flat chunk of attack damage for a short burst, 1.25 seconds per level. The damage bonus is large and the window is short, so it rewards swinging back immediately. It will not fire while you already have a Strength potion on you. Works on its own once learned.

### Hunter's Resistance (`hunter-resistance`)

Taking a hit gives you Resistance at an amplifier equal to your level, for half a second per level. It is meant to blunt the follow-up hit in a chain, not to carry you through a whole fight. Works on its own once learned.

### Items Drop-To-Inventory (`hunter-drop-to-inventory`)

Loot goes into your inventory instead of onto the ground. It works two ways. Any mob you kill sends its drops straight to you, whatever you are holding. Block drops only route to you while a sword is in your main hand, and they still have to pass the server's normal pickup checks first, so a protection plugin that denies the pickup leaves the item on the floor as usual. Anything that does not fit in your inventory drops at your feet. This one caps at level 1.

**How to use it**

1. Learn Items Drop-To-Inventory in the Adapt menu.
2. Kill mobs normally; their drops arrive in your inventory.
3. Hold a sword while breaking blocks if you want block drops routed the same way.

### Trophy Skinner (`hunter-trophy-skinner`)

Clean kills pay extra. A kill counts as clean if you shot the mob from at least the listed distance, or if you were sneaking when you landed the blow. Clean kills then roll two separate chances: one for bonus trophy materials matched to the mob (gunpowder from creepers, bone from skeletons, string from spiders, and so on, with leather as the fallback), and a much rarer one for the mob's head. Heads only exist for creepers, the skeleton family, the zombie family, and piglins. Bow kills add one to the trophy stack, and each trophy also pays Hunter XP. Higher levels shorten the shot distance you need.

**How to use it**

1. Learn Trophy Skinner in the Adapt menu.
2. Either shoot the mob from at least the distance shown in the menu, or stay sneaking as you land the killing blow.
3. Collect the trophies and heads from the mob's normal death drop.

### Predator Focus (`hunter-predator-focus`)

Hitting the same target over and over ramps your melee damage. The first hit sets one stack and gives nothing; every hit after that adds a flat percentage, up to a stack cap that grows with level. Switch targets or go quiet for longer than the decay window and the ramp resets to one. Melee only. Works on its own once learned.

### Big Game Hunter (`hunter-big-game`)

You hit harder against the six heavyweight mobs (ravager, iron golem, warden, wither, ender dragon, elder guardian) and their kills drop more. On a big-game kill each item already in the drop list gets a chance to be duplicated, up to a per-kill cap, and you get a large flat XP payout on top. Melee only for the damage bonus; the drop bonus applies to any kill you are credited with. Works on its own once learned.

### Blood Trail (`hunter-blood-trail`)

Wound a mob down to half health or lower with a melee hit and it starts bleeding a glowing red line as it runs. Only you see it. The trail redraws four times a second along the mob's actual path, and it fades when the mob leaves your tracking range, changes world, or the wound times out. Both the duration and the tracking range grow with level. Works on its own once learned.

### Snare Line (`hunter-snare-line`)

Learning this unlocks a crafting recipe for the Hunter's Snare, a tripwire hook you can plant on the ground. Hostile monsters that walk near a planted snare are pinned in place and have their momentum zeroed. Each snare holds a number of trigger charges and expires on its own after a couple of minutes. Good for choke points, mob farms, and buying yourself an escape.

**How to use it**

1. Learn Snare Line in the Adapt menu.
2. Craft Hunter's Snares: string in every slot of the grid except the middle, one iron ingot in the middle. Each craft gives you two.
3. Hold a snare and right-click the top of a block where mobs will walk. The snare sits one block above what you clicked and consumes one item.
4. Leave it. Every monster that comes within the trigger radius spends one charge and gets pinned.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `hunter` |
| Class | `SkillHunter` |
| Icon | `BONE` |
| Color | `RED` |
| Interval (ms) | `4150` |
| Skill config | `plugins/Adapt/adapt/skills/hunter.toml` |
| Adaptation count | 14 |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/hunter.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Turns the whole Hunter skill on or off. |
| `skillColor` | `"&c"` | Legacy ampersand color code used for Hunter in menus and text. |
| `getXpForAttackingWithTools` | `true` | Master switch for the kill handler. False means no kill XP, no kill stats, and no boss celebration. |
| `creeperKillMultiplier` | `2` | Extra multiplier applied to XP from creeper kills only. |
| `killMaxHealthXPMultiplier` | `3.0` | XP per point of the victim's max health. |
| `cooldownDelay` | `1000` | Milliseconds that must pass between two kill XP awards for the same player. |
| `spawnerMobReductionXpMultiplier` | `0.3` | Multiplier applied when the victim spawned from a monster spawner. |
| `killsChallengeReward` | `500` | Base XP paid by the kill-count challenges; some tiers pay 2x or 5x this. |
| `bossKillReward` | `1000` | Base XP paid by the boss challenges; the 10-boss tier pays 5x this. |

### Milestones and challenges

| Challenge key | Stat key | Threshold | Reward |
|---------------|----------|-----------|--------|
| `challenge_novice_hunter` | `killed.monsters` | 100 | `killsChallengeReward` |
| `challenge_intermediate_hunter` | `killed.monsters` | 500 | `killsChallengeReward` x2 |
| `challenge_advanced_hunter` | `killed.monsters` | 5000 | `killsChallengeReward` x5 |
| `challenge_creeper_conqueror` | `killed.creepers` | 50 | `killsChallengeReward` |
| `challenge_creeper_annihilator` | `killed.creepers` | 200 | `killsChallengeReward` x2 |
| `challenge_kills_500` | `killed.kills` | 500 | `killsChallengeReward` |
| `challenge_kills_5k` | `killed.kills` | 5000 | `killsChallengeReward` x5 |
| `challenge_boss_1` | `hunter.boss.kills` | 1 | `bossKillReward` |
| `challenge_boss_10` | `hunter.boss.kills` | 10 | `bossKillReward` x5 |

Boss kills counted by `hunter.boss.kills`: `ENDER_DRAGON`, `WITHER`, `ELDER_GUARDIAN`, `WARDEN`.

Skill-level events: `EntityDeathEvent` (kill XP, stats, boss celebration) and `CreatureSpawnEvent` (tags spawner mobs with the `adapt:hunter-spawner-mob` persistent key).

### Shared adaptation keys

Every adaptation TOML carries these on top of its own knobs: `enabled`, `permanent`, `showParticles`, `showSounds`, `baseCost`, `costFactor`, `maxLevel`, `initialCost`. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

The seven struck buffs also share this knob set:

| Key | Behavior / units |
|-----|------------------|
| `useConsumable` | When true, activation eats one `consumable` item instead of applying Hunger. |
| `consumable` | Material name consumed when `useConsumable` is true. |
| `poisonPenalty` | When true, activating on an empty food bar applies Poison. |
| `stackHungerPenalty` | When true, repeat triggers raise the Hunger amplifier instead of refreshing it. |
| `stackPoisonPenalty` | When true, repeat starve triggers raise the Poison amplifier instead of refreshing it. |
| `stackBuff` | When true, repeat triggers extend or raise the buff while it is still running. |
| `baseEffectbyLevel` | Buff duration in ticks per adaptation level (20 ticks = 1 second). |
| `baseHungerFromLevel` | Hunger amplifier is this minus your level. |
| `baseHungerDuration` | Hunger duration in ticks per level; also the flat Poison duration when starving. |
| `basePoisonFromLevel` | Poison amplifier is this minus your level. |

### Adrenaline

| Property | Default |
|----------|---------|
| Class | `HunterAdrenaline` |
| Icon | `LEATHER_HELMET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 1911 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-adrenaline.toml` |

Listened events: `EntityDamageByEntityEvent` (player is the direct damager).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageBase` | `0.12` | Damage bonus fraction at 0 health before level scaling. |
| `damageFactor` | `0.21` | Extra bonus fraction added across levels, so the level 5 maximum is 0.33. |

Milestones: `challenge_hunter_adrenaline_100` and `challenge_hunter_adrenaline_2500` on `hunter.adrenaline.low-health-kills` (100 kills for 400 XP, 2500 for 1500 XP). The stat only counts kills landed below 35 percent health.

### Hunter's Regen

| Property | Default |
|----------|---------|
| Class | `HunterRegen` |
| Icon | `AXOLOTL_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 9744 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-regen.toml` |

Listened events: `EntityDamageEvent`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | See shared struck-buff keys. |
| `poisonPenalty` | `true` | See shared struck-buff keys. |
| `stackHungerPenalty` | `false` | See shared struck-buff keys. |
| `stackPoisonPenalty` | `false` | See shared struck-buff keys. |
| `stackBuff` | `false` | See shared struck-buff keys. |
| `baseEffectbyLevel` | `30` | Regeneration lasts 30 ticks per level (1.5s at level 1, 7.5s at level 5), amplifier equal to level. |
| `baseHungerFromLevel` | `10` | Hunger amplifier is 10 minus your level. |
| `baseHungerDuration` | `50` | Hunger lasts 50 ticks per level; Poison lasts a flat 50 ticks. |
| `basePoisonFromLevel` | `6` | Poison amplifier is 6 minus your level. |
| `consumable` | `"ROTTEN_FLESH"` | Item eaten per activation when `useConsumable` is true. |

Milestone: `challenge_hunter_regen_500` on `hunter.regen.health-regened`, 500 activations for 400 XP.

### Vanishing Step

| Property | Default |
|----------|---------|
| Class | `HunterInvis` |
| Icon | `TROPICAL_FISH_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 9444 |
| Localization key | `hunter.invisibility` |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-invis.toml` |

Listened events: `EntityDamageEvent`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | See shared struck-buff keys. |
| `poisonPenalty` | `true` | See shared struck-buff keys. |
| `stackHungerPenalty` | `false` | See shared struck-buff keys. |
| `stackPoisonPenalty` | `false` | See shared struck-buff keys. |
| `stackBuff` | `false` | See shared struck-buff keys. |
| `baseEffectbyLevel` | `100` | Invisibility lasts 100 ticks per level (5s at level 1, 25s at level 5), amplifier equal to level. |
| `baseHungerFromLevel` | `10` | Hunger amplifier is 10 minus your level. |
| `baseHungerDuration` | `50` | Hunger lasts 50 ticks per level; Poison lasts a flat 50 ticks. |
| `basePoisonFromLevel` | `6` | Poison amplifier is 6 minus your level. |
| `consumable` | `"ROTTEN_FLESH"` | Item eaten per activation when `useConsumable` is true. |

Milestone: `challenge_hunter_invis_200` on `hunter.invis.activations`, 200 activations for 300 XP.

### Hunter's Heights

| Property | Default |
|----------|---------|
| Class | `HunterJumpBoost` |
| Icon | `PUFFERFISH_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 9544 |
| Localization key | `hunter.jump_boost` |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-jumpboost.toml` |

Listened events: `EntityDamageEvent`.

Applies two timed attribute modifiers rather than a potion: jump strength `+0.1 * (level + 1)` and safe fall distance `+(level + 1)` blocks. Blocked while a Jump Boost potion effect is present, or if either attribute is missing on the running server version.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | See shared struck-buff keys. |
| `poisonPenalty` | `true` | See shared struck-buff keys. |
| `stackHungerPenalty` | `false` | See shared struck-buff keys. |
| `stackPoisonPenalty` | `false` | See shared struck-buff keys. |
| `stackBuff` | `false` | See shared struck-buff keys. |
| `baseEffectbyLevel` | `100` | Modifier duration in ticks per level (5s at level 1, 25s at level 5). |
| `baseHungerFromLevel` | `10` | Hunger amplifier is 10 minus your level. |
| `baseHungerDuration` | `50` | Hunger lasts 50 ticks per level; Poison lasts a flat 50 ticks. |
| `basePoisonFromLevel` | `6` | Poison amplifier is 6 minus your level. |
| `consumable` | `"ROTTEN_FLESH"` | Item eaten per activation when `useConsumable` is true. |

Milestone: `challenge_hunter_jump_200` on `hunter.jump-boost.activations`, 200 activations for 300 XP.

### Hunter's Luck

| Property | Default |
|----------|---------|
| Class | `HunterLuck` |
| Icon | `TADPOLE_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 9644 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-luck.toml` |

Listened events: `EntityDamageEvent`, `PlayerDeathEvent` (clears the buff and penalty timers).

Applies a timed Luck attribute modifier of `+(level + 1)`. The starve path adds a negative Luck modifier of `-(basePoisonFromLevel - level + 1)` for `baseHungerDuration` ticks.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | See shared struck-buff keys. |
| `poisonPenalty` | `true` | Also gates the negative Luck penalty, not just Poison. |
| `stackHungerPenalty` | `false` | See shared struck-buff keys. |
| `stackPoisonPenalty` | `false` | Also controls whether the negative Luck penalty extends itself. |
| `stackBuff` | `false` | See shared struck-buff keys. |
| `baseEffectbyLevel` | `100` | Luck modifier duration in ticks per level (5s at level 1, 25s at level 5). |
| `baseHungerFromLevel` | `10` | Hunger amplifier is 10 minus your level. |
| `baseHungerDuration` | `50` | Hunger lasts 50 ticks per level; Poison and the Luck penalty last a flat 50 ticks. |
| `basePoisonFromLevel` | `6` | Poison amplifier is 6 minus your level, and sets the size of the Luck penalty. |
| `consumable` | `"ROTTEN_FLESH"` | Item eaten per activation when `useConsumable` is true. |

Milestone: `challenge_hunter_luck_200` on `hunter.luck.activations`, 200 activations for 300 XP.

### Hunter's Speed

| Property | Default |
|----------|---------|
| Class | `HunterSpeed` |
| Icon | `SUGAR` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-speed.toml` |

No tick interval is set. Listened events: `EntityDamageEvent`.

Runs through `VelocityBurstRuntime` instead of the Speed potion. Target speed is `baseHorizontalSpeed * (1 + (level + 1) * 0.2)`, clamped to `maxHorizontalSpeed`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | See shared struck-buff keys. |
| `poisonPenalty` | `true` | See shared struck-buff keys. |
| `stackHungerPenalty` | `false` | See shared struck-buff keys. |
| `stackPoisonPenalty` | `false` | See shared struck-buff keys. |
| `stackBuff` | `false` | When true, a new burst may overlap one still running. |
| `baseEffectbyLevel` | `100` | Burst duration in ticks per level (5s at level 1, 25s at level 5). |
| `baseHungerDuration` | `50` | Hunger lasts 50 ticks per level; Poison lasts a flat 50 ticks. |
| `baseHungerFromLevel` | `10` | Hunger amplifier is 10 minus your level. |
| `basePoisonFromLevel` | `6` | Poison amplifier is 6 minus your level. |
| `baseHorizontalSpeed` | `0.13` | Blocks per tick target speed before the level scalar is applied. |
| `maxHorizontalSpeed` | `0.32` | Hard cap on burst speed in blocks per tick. |
| `accelPerTick` | `0.045` | How much of the gap to the target speed is closed each tick. |
| `brakePerTick` | `0.08` | How fast the burst decays once you stop steering. |
| `stopThreshold` | `0.01` | Horizontal speed below this counts as stopped. |
| `hardStopOnInvalidState` | `true` | Force-clears burst velocity when the player enters a state the burst cannot run in. |
| `fallbackInputVelocityThreshold` | `0.0008` | Movement threshold used to infer steering on runtimes without the player input API. |
| `consumable` | `"ROTTEN_FLESH"` | Item eaten per activation when `useConsumable` is true. |

Milestone: `challenge_hunter_speed_200` on `hunter.speed.activations`, 200 activations for 300 XP.

### Hunter's Strength

| Property | Default |
|----------|---------|
| Class | `HunterStrength` |
| Icon | `COD_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 9044 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-strength.toml` |

Listened events: `EntityDamageEvent`, `PlayerDeathEvent` (clears the buff timer).

Applies a timed attack damage modifier of `+3.0 * (level + 1)`. Blocked while a Strength potion effect is present.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | See shared struck-buff keys. |
| `poisonPenalty` | `true` | See shared struck-buff keys. |
| `stackHungerPenalty` | `false` | See shared struck-buff keys. |
| `stackPoisonPenalty` | `false` | See shared struck-buff keys. |
| `stackBuff` | `false` | See shared struck-buff keys. |
| `baseEffectbyLevel` | `25` | Modifier duration in ticks per level (1.25s at level 1, 6.25s at level 5). |
| `baseHungerFromLevel` | `10` | Hunger amplifier is 10 minus your level. |
| `basePoisonFromLevel` | `6` | Poison amplifier is 6 minus your level. |
| `baseHungerDuration` | `50` | Hunger lasts 50 ticks per level; Poison lasts a flat 50 ticks. |
| `consumable` | `"ROTTEN_FLESH"` | Item eaten per activation when `useConsumable` is true. |

Milestone: `challenge_hunter_strength_200` on `hunter.strength.activations`, 200 activations for 300 XP.

### Hunter's Resistance

| Property | Default |
|----------|---------|
| Class | `HunterResistance` |
| Icon | `POWDER_SNOW_BUCKET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 9844 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-resistance.toml` |

Listened events: `EntityDamageEvent`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `useConsumable` | `false` | See shared struck-buff keys. |
| `poisonPenalty` | `true` | See shared struck-buff keys. |
| `stackHungerPenalty` | `false` | See shared struck-buff keys. |
| `stackPoisonPenalty` | `false` | See shared struck-buff keys. |
| `stackBuff` | `false` | See shared struck-buff keys. |
| `baseEffectbyLevel` | `10` | Resistance lasts 10 ticks per level (0.5s at level 1, 2.5s at level 5), amplifier equal to level. |
| `baseHungerFromLevel` | `10` | Hunger amplifier is 10 minus your level. |
| `baseHungerDuration` | `50` | Hunger lasts 50 ticks per level; Poison lasts a flat 50 ticks. |
| `basePoisonFromLevel` | `6` | Poison amplifier is 6 minus your level. |
| `consumable` | `"ROTTEN_FLESH"` | Item eaten per activation when `useConsumable` is true. |

Milestone: `challenge_hunter_resistance_500` on `hunter.resistance.activations`, 500 activations for 400 XP.

### Items Drop-To-Inventory

| Property | Default |
|----------|---------|
| Class | `HunterDropToInventory` |
| Icon | `TRAPPED_CHEST` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 1 |
| Cost factor | 1 |
| Tick interval (ms) | 18440 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-drop-to-inventory.toml` |

Listened events: `BlockDropItemEvent` (requires a sword from `ItemListings.toolSwords` in the main hand, an allowed interact context, and a passing block-break check), `EntityDeathEvent` (any mob you killed, no held-item requirement).

Block drops are probed through `ProtectionEventProbe` first, so a denied pickup leaves the item on the normal world-drop path. Entity death stacks are not live pickup entities and go straight to the inventory. Overflow is dropped at the player's location.

No adaptation-specific knobs; shared keys only.

Milestone: `challenge_hunter_dti_10k` on `hunter.drop-to-inv.items-caught`, 10000 items for 500 XP.

### Trophy Skinner

| Property | Default |
|----------|---------|
| Class | `HunterTrophySkinner` |
| Icon | `ZOMBIE_HEAD` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.8 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-trophy-skinner.toml` |

Listened events: `EntityDeathEvent`.

A kill counts as precise when it was a projectile kill at or beyond the minimum range, or when the killer was sneaking. Trophy material is chosen by entity type (`GUNPOWDER`, `BONE`, `ROTTEN_FLESH`, `STRING`, `BLAZE_POWDER`, `ENDER_PEARL`, `REDSTONE`, `PORKCHOP`, with `LEATHER` as the fallback). Head drops exist only for creepers, skeletons, strays, bogged, wither skeletons, zombies, husks, drowned, zombified piglins, piglins, and piglin brutes.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `dropChanceBase` | `0.14` | Trophy chance at level 0, 0 to 1. |
| `dropChanceFactor` | `0.3` | Trophy chance added across levels, 0 to 1. |
| `maxDropChance` | `0.5` | Ceiling on trophy chance, 0 to 1. |
| `headChanceBase` | `0.015` | Head chance at level 0, 0 to 1. |
| `headChanceFactor` | `0.08` | Head chance added across levels, 0 to 1. |
| `maxHeadChance` | `0.12` | Ceiling on head chance, 0 to 1. |
| `trophyAmountBase` | `1` | Trophy stack size at level 0, before rounding. |
| `trophyAmountFactor` | `2` | Extra stack size added across levels; a projectile kill adds 1 more, and the stack is capped at 8. |
| `minimumRangeBase` | `18` | Blocks a shot must cover at level 0 to count as precise. |
| `minimumRangeFactor` | `10` | Blocks subtracted from that requirement across levels, floored at 4. |
| `xpPerTrophy` | `16` | Hunter XP paid per trophy drop. |

Milestones: `challenge_hunter_trophy_50` on `hunter.trophy-skinner.trophies-collected` (50 for 400 XP) and `challenge_hunter_trophy_heads_100` on `hunter.trophy-skinner.heads-collected` (100 for 1000 XP).

### Predator Focus

| Property | Default |
|----------|---------|
| Class | `HunterPredatorFocus` |
| Icon | `TARGET` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 5 |
| Cost factor | 0.45 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-predator-focus.toml` |

No tick interval is set. Listened events: `EntityDamageByEntityEvent` (player is the direct damager).

Stack cap is `3 + round(levelPercent * 6)`, so 4 at level 1 and 9 at level 5. Bonus damage is `perStackBonus * (stacks - 1)`, meaning 21 percent at a full level 1 ramp and 56 percent at a full level 5 ramp.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `perStackBonus` | `0.07` | Melee damage added per stack past the first, as a fraction. |
| `rampCapBase` | `3` | Stack cap at level 0. |
| `rampCapFactor` | `6` | Extra stack cap gained across levels. |
| `decayMillis` | `3500` | Milliseconds of no hits before the ramp resets to one stack. |
| `xpPerRampedHit` | `2` | Silent Hunter XP per hit that actually gained a bonus. |

Milestones: `challenge_hunter_predator_focus_500` and `challenge_hunter_predator_focus_5k` on `hunter.predator-focus.ramped-hits` (500 for 400 XP, 5000 for 1500 XP).

### Big Game Hunter

| Property | Default |
|----------|---------|
| Class | `HunterBigGameHunter` |
| Icon | `NETHERITE_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 6 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-big-game.toml` |

No tick interval is set. Listened events: `EntityDamageByEntityEvent` (player is the direct damager) and `EntityDeathEvent`.

Big game is exactly `RAVAGER`, `IRON_GOLEM`, `WARDEN`, `WITHER`, `ENDER_DRAGON`, `ELDER_GUARDIAN`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusDamageBase` | `0.15` | Melee damage bonus fraction at level 0. |
| `bonusDamageFactor` | `0.45` | Bonus fraction added across levels, reaching 0.6 at max level. |
| `extraDropChanceBase` | `0.15` | Chance per existing drop to be duplicated, at level 0. |
| `extraDropChanceFactor` | `0.45` | Duplication chance added across levels. |
| `maxExtraDropChance` | `0.75` | Ceiling on duplication chance, 0 to 1. |
| `maxExtraDropsPerKill` | `6` | Hard cap on duplicated stacks from one kill. |
| `xpPerBigGameKill` | `45` | Hunter XP paid per big-game kill. |

Milestones: `challenge_hunter_big_game_100` and `challenge_hunter_big_game_1k` on `hunter.big-game.big-game-slain` (100 for 600 XP, 1000 for 2000 XP).

### Blood Trail

| Property | Default |
|----------|---------|
| Class | `HunterBloodTrail` |
| Icon | `REDSTONE` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Tick interval (ms) | 250 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-blood-trail.toml` |

Listened events: `EntityDamageByEntityEvent` (player is the direct damager) and `PlayerQuitEvent` (drops that player's wounds and clears their display channel). The tick only runs while at least one wound is tracked.

Trail duration is `100 + round(levelPercent * 200)` ticks, so 7 seconds at level 1 and 15 at level 5. Tracking range is `16 + levelPercent * 32` blocks, so 22.4 at level 1 and 48 at level 5. Segments are drawn per viewer through `ViewerDisplayDirector` and never render for other players.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `trailDurationTicksBase` | `100` | Wound lifetime in ticks at level 0. |
| `trailDurationTicksFactor` | `200` | Extra wound lifetime in ticks across levels. |
| `rangeBase` | `16` | Blocks you can be from the mob and still see the trail, at level 0. |
| `rangeFactor` | `32` | Extra tracking range in blocks across levels. |
| `woundHealthFraction` | `0.5` | Fraction of max health the hit must leave the target at or below. |
| `maxTrackedWounds` | `64` | Cap on simultaneously tracked wounds; also the per-tick render budget. |
| `trailThickness` | `0.06` | Width of each drawn trail segment, in blocks. |
| `displayDurationTicks` | `30` | How long each drawn segment stays visible, in ticks. |
| `xpPerWound` | `3` | Silent Hunter XP the first time you wound a given target. |

Milestones: `challenge_hunter_blood_trail_250` and `challenge_hunter_blood_trail_2500` on `hunter.blood-trail.trails-followed` (250 for 400 XP, 2500 for 1500 XP). The stat increments the first time a wound renders in range for its owner.

### Snare Line

| Property | Default |
|----------|---------|
| Class | `HunterSnareLine` |
| Icon | `TRIPWIRE_HOOK` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 5 |
| Cost factor | 0.5 |
| Tick interval (ms) | 250 |
| Config file | `plugins/Adapt/adapt/adaptations/hunter-snare-line.toml` |

Listened events: `PlayerInteractEvent` (right-click on a block, receives cancelled events) and `PlayerQuitEvent` (removes that player's snares). The tick only runs while at least one snare is placed.

Recipe `hunter-snare`: shaped, `S S` / `SIS` / `S S` with `S` = `STRING` and `I` = `IRON_INGOT`, producing 2 tripwire hooks named "Hunter's Snare" and tagged with the `adapt:hunter-snare-item` persistent key. Only tagged items place snares.

Root duration is `max(1, 30 + round(levelPercent * 50))` ticks, so 2 seconds at level 1 and 4 at level 5. Charges are `max(1, 3 + round(levelPercent * 5))`, so 4 at level 1 and 8 at level 5. Rooting applies a `MULTIPLY_SCALAR_1` movement speed modifier of `-min(1, 0.15 * (rootAmplifier + 1))`, which at the default amplifier is a full stop, and zeroes the mob's velocity. Only `Monster` entities are affected, and mobs protected as friendly to the snare owner are skipped.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rootDurationTicksBase` | `30` | Root duration in ticks at level 0. |
| `rootDurationTicksFactor` | `50` | Extra root duration in ticks across levels. |
| `chargesBase` | `3` | Trigger charges per snare at level 0. |
| `chargesFactor` | `5` | Extra trigger charges across levels. |
| `triggerRadius` | `1.6` | Blocks from the snare a monster must enter to trip it. |
| `rootAmplifier` | `6` | Slowness-equivalent amplifier; each point is 15 percent speed reduction, capped at a full stop. |
| `rearmBufferMillis` | `500` | Extra milliseconds after a root ends before the same snare can re-trigger on that mob. |
| `snareLifetimeTicks` | `2400` | Ticks a placed snare survives before decaying (2 minutes). |
| `maxSnaresPerPlayer` | `4` | Snares one player may keep placed at once; placing more plays a deny sound. |
| `maxActiveSnares` | `64` | Snares this server processes at once; placement silently fails past this. |
| `maxTargetsPerScan` | `8` | Monsters one snare will schedule for rooting per scan. |
| `xpPerSnare` | `6` | Hunter XP paid per mob snared. |

Milestones: `challenge_hunter_snare_200` and `challenge_hunter_snare_2k` on `hunter.snare-line.mobs-snared` (200 for 400 XP, 2000 for 1500 XP).

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
