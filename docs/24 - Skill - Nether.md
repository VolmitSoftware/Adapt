# Skill: Nether

Nether is the survival skill for the fire dimension. The skill itself pays out for wither business: taking wither damage, punching a Wither boss, killing wither skeletons, killing the Wither, and breaking wither roses. Everything else you earn in the Nether comes from the adaptations themselves, and most of them hand out their own XP for the thing they do.

The tree is mostly about not dying to the terrain. Fire Resistance, Ashwalker, Ghast Ward, Wither Resistance, and Magma Skin each shave off a different way the Nether kills you. Lava Walker, Soul Strider, and Strider Bond fix the movement problems: crossing a lava lake, slogging through soul sand, and riding a strider without it freezing up.

The rest is about taking things out of the Nether. Netherrack Mason speeds up bulk mining and adds bonus drops, Piglin Broker fattens up barters, Crimson Feast turns fungi and roots into real food, Wither Harvest makes wither skeletons pay properly, and Blaze Leech turns fire itself into food and regeneration.

Skull Throw is the one loud button in the tree. Right-click with a wither skeleton skull and you throw it as a live wither skull projectile.

## Adaptations

Everything below needs the same conditions: the adaptation learned at level 1 or higher, the Nether skill and that adaptation both enabled in config, the `adapt.use.*` permission (or the matching per-adaptation node), and any protection plugin on your server allowing the action where you are standing.

Several of these only work while you are in a Nether-environment world: Lava Walker, Ghast Ward, Netherrack Mason, and the meal half of Crimson Feast. The rest work anywhere, including Soul Strider, which speeds you across soul sand in the overworld too.

### Wither Resistance (`nether-wither-resist`)

Each piece of netherite armor you are wearing gives you a chance to shrug off wither damage entirely. The chances add up across the four slots and grow with level, so a full netherite set at max level negates the wither effect every time. Works on its own once learned.

### Wither Skull Throw (`nether-skull-toss`)

Wither skeleton skulls become ammunition. Right-click while holding one and you launch a real wither skull that flies where you are looking and explodes on impact, same as the boss fires. The skull is consumed (except in creative), and there is a cooldown that gets much shorter as you level. Landing a kill from 40 blocks or more unlocks a hidden challenge.

**How to use it**

1. Learn Wither Skull Throw in the Adapt menu.
2. Hold a wither skeleton skull in your main hand.
3. Right-click. Look where you want it to go first; the skull follows your aim.

### Fire Resistance (`nether-fire-resist`)

Every tick of fire damage has a chance to be cancelled outright. The chance climbs steeply with level, so a maxed version means you rarely notice standing in flames at all. It covers burning only, not lava. Works on its own once learned.

### Lava Walker (`nether-lava-walker`)

In the Nether, walking into lava pushes you forward across the surface instead of sinking. Each stride cancels your fall distance, puts out your fire, gives you a moment of fire resistance, and costs food. Higher levels stride farther, cost less food, and re-arm sooner. It does nothing if your food bar is empty, and it will not run while flying, gliding, or riding.

**How to use it**

1. Learn Lava Walker in the Adapt menu.
2. Be in the Nether with food in your bar.
3. Walk into the lava, facing the direction you want to go. Keep looking where you want to end up; each stride follows your view direction.

### Ghast Ward (`nether-ghast-ward`)

In the Nether, ghast fireballs hit you for much less, and getting hit by one also caps how long you burn afterward. Arrows from wither skeletons are cut down too, and so is any explosion damage while you are in the dimension. You earn Nether XP for every point of damage the ward removed. Works on its own once learned.

### Blaze Leech (`nether-blaze-leech`)

Fire feeds you. Whenever you take fire, lava, or magma-block damage, or land a hit on something that is currently burning, there is a chance to trigger a leech: food, saturation, and a burst of Regeneration. Higher levels raise the trigger chance, lengthen the regen, restore more food, and shorten the internal cooldown. Works on its own once learned.

### Piglin Broker (`nether-piglin-broker`)

Any piglin bartering near you pays better. When a barter resolves, the nearest player with this adaptation gets credited: a chance for a duplicated and enlarged roll of whatever came out, plus a smaller chance for a separate bonus item from a fixed premium pool. Works on its own once learned, and you do not have to be the one who threw the gold.

### Soul Strider (`nether-soul-strider`)

Soul sand and soul soil stop slowing you down; you move across them at full speed and then some. At max level, stepping back onto soul ground after a short gap also fires a short soul-speed burst. Works anywhere, not only in the Nether. Works on its own once learned.

### Magma Skin (`nether-magma-skin`)

Only active while you are on fire. Anyone who melees you catches fire, and your own melee swings deal bonus damage and set the target burning. Combines well with anything that keeps you lit, since being on fire is the requirement rather than the problem. Works on its own once learned.

### Netherrack Mason (`nether-netherrack-mason`)

In the Nether, starting to mine netherrack, basalt, or blackstone gives you a block-breaking speed boost that refreshes as you keep working. Every one of those blocks you break pays Nether XP, and some of them drop an extra item: usually a second copy of what you mined, sometimes gold nuggets, quartz, iron nuggets, or nether brick. Works on its own once learned.

### Strider Bond (`nether-strider-bond`)

Striders you ride stop shivering and move faster, including when they step out of lava. From level 2 up, dismounting over lava triggers a rescue: the adaptation looks for solid safe ground nearby and teleports you there instead of letting you fall in. The first successful rescue unlocks a hidden challenge.

**How to use it**

1. Learn Strider Bond in the Adapt menu.
2. Saddle a strider and ride it with a warped fungus on a stick, as normal.
3. The speed applies while you ride. If you get thrown off over lava at level 2 or higher, the rescue handles it.

### Crimson Feast (`nether-crimson-feast`)

Nether flora becomes food. Right-click while holding a crimson or warped fungus, roots, nether sprouts, weeping vines, or twisting vines to eat it for food and saturation. On top of that, eating anything at all while in the Nether gives you fire resistance for a few seconds. Both halves pay XP.

**How to use it**

1. Learn Crimson Feast in the Adapt menu.
2. Hold any nether fungus, roots, sprouts, or vines.
3. Right-click to eat. If your food bar is already full you have to sneak to force it down.
4. Eat any normal food while in the Nether for the fire resistance buff.

### Ashwalker (`nether-ashwalker`)

Magma blocks stop hurting you from the moment you learn it. From level 2 up, campfires stop hurting you too. At max level, soul fire does most of its damage to somebody else: the damage is cut hard and the burn is capped short. You earn XP for every point it takes off. Works on its own once learned.

### Wither Harvest (`nether-wither-harvest`)

Every wither skeleton you kill drops extra bones and coal, and gets a better chance at dropping its skull. The skull roll is skipped if the mob already dropped one on its own, so it never doubles up. Works on its own once learned.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `nether` |
| Class | `SkillNether` |
| Icon | `NETHER_STAR` |
| Color | `DARK_GRAY` |
| Interval (ms) | `7425` |
| Skill config | `plugins/Adapt/adapt/skills/nether.toml` |
| Adaptation count | 14 |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/nether.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `skillColor` | `"&8"` | Legacy ampersand color code used for Nether in menus and text. |
| `enabled` | `true` | Turns the whole Nether skill on or off. |
| `witherDamageXp` | `26.0` | XP for taking a tick of Wither effect damage that did not come from a block source. |
| `witherDamageXpCooldown` | `1500` | Milliseconds between wither-damage XP awards. |
| `witherAttackXp` | `15` | XP for landing a melee hit on a Wither boss. |
| `witherAttackXpCooldown` | `1500` | Milliseconds between wither-attack XP awards. |
| `witherSkeletonKillXp` | `225` | XP for killing a wither skeleton. |
| `witherKillXp` | `900` | XP for killing the Wither. |
| `witherRoseBreakXp` | `125` | XP for breaking a wither rose. |
| `witherRoseBreakCooldown` | `1200` (written as `60 * 20`) | Ticks between wither-rose payouts, converted to milliseconds at 50 ms per tick, so 60 seconds. |
| `challengeNetherReward` | `500` | Base XP for the Nether kill-count challenges; later tiers pay 2x and 5x. |
| `challengeWitherDmgReward` | `500` | Base XP for the wither-damage challenges; the second tier pays 2x. |
| `challengeWitherSkelReward` | `500` | Base XP for the wither-skeleton challenges; the second tier pays 2x. |
| `challengeWitherBossReward` | `1000` | Base XP for the Wither boss challenges; the second tier pays 2x. |
| `challengeRosesReward` | `500` | Base XP for the wither-rose challenges; the second tier pays 2x. |

### Milestones and challenges

| Challenge key | Stat key | Threshold | Reward |
|---------------|----------|-----------|--------|
| `challenge_nether_50` | `nether.kills` | 50 | `challengeNetherReward` |
| `challenge_nether_500` | `nether.kills` | 500 | `challengeNetherReward` x2 |
| `challenge_nether_5k` | `nether.kills` | 5000 | `challengeNetherReward` x5 |
| `challenge_wither_dmg_500` | `nether.wither.damage` | 500 | `challengeWitherDmgReward` |
| `challenge_wither_dmg_5k` | `nether.wither.damage` | 5000 | `challengeWitherDmgReward` x2 |
| `challenge_wither_skel_25` | `nether.skeleton.kills` | 25 | `challengeWitherSkelReward` |
| `challenge_wither_skel_250` | `nether.skeleton.kills` | 250 | `challengeWitherSkelReward` x2 |
| `challenge_wither_boss_1` | `nether.boss.kills` | 1 | `challengeWitherBossReward` |
| `challenge_wither_boss_10` | `nether.boss.kills` | 10 | `challengeWitherBossReward` x2 |
| `challenge_roses_10` | `nether.roses.broken` | 10 | `challengeRosesReward` |
| `challenge_roses_100` | `nether.roses.broken` | 100 | `challengeRosesReward` x2 |

`nether.kills` only counts wither skeletons and the Wither. `nether.wither.damage` accumulates raw damage, not a count.

Skill-level events: `EntityDamageEvent` (Wither effect damage, ignoring `EntityDamageByBlockEvent`), `BlockBreakEvent` (wither roses), `EntityDeathEvent` (wither skeleton and Wither kills), `EntityDamageByEntityEvent` (melee `ENTITY_ATTACK` on a Wither).

### Shared adaptation keys

Every adaptation TOML carries `enabled`, `permanent`, `showParticles`, `showSounds`, `baseCost`, `costFactor`, `maxLevel`, and `initialCost` on top of its own knobs. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Wither Resistance

| Property | Default |
|----------|---------|
| Class | `NetherWitherResist` |
| Icon | `NETHERITE_CHESTPLATE` |
| Max level | 3 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 9283 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-wither-resist.toml` |

Listened events: `EntityDamageEvent` (`onEntityDamage`), `WITHER` cause only.

Chance per netherite piece is `basePieceChance + chanceAddition * level`, summed over helmet, chestplate, leggings, and boots, then clamped to 100 percent. Full netherite at level 3 reaches 100 percent and plays an extra mastery effect.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `basePieceChance` | `10` | Percentage points contributed by each netherite piece before level scaling. |
| `chanceAddition` | `5` | Percentage points added per piece per adaptation level. |

Milestones: `challenge_nether_wither_100` and `challenge_nether_wither_1k` on `nether.wither-resist.negated` (100 for 300 XP, 1000 for 1000 XP).

### Wither Skull Throw

| Property | Default |
|----------|---------|
| Class | `NetherSkullYeet` |
| Icon | `WITHER_SKELETON_SKULL` |
| Max level | 3 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 10 |
| Cost factor | 0.92 |
| Tick interval (ms) | 2314 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-skull-toss.toml` |

Listened events: `PlayerInteractEvent` (`onRightClick`, receives cancelled events; main hand only, right-click air or block, item must be `WITHER_SKELETON_SKULL`), `EntityDeathEvent` (`onEntityDeath`, credits kills made by a thrown skull).

Cooldown is `max(1, baseCooldown - levelCooldown * level)` seconds, so 10 seconds at level 1 and 1 second at level 3. It is an item cooldown keyed to the skull material, shared by the gate and the sweep. The projectile is an uncharged, non-bouncing `WitherSkull` with the player as shooter, and throwing it pays 100 Nether XP directly. The interact event is always cancelled so the skull is never placed as a block.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseCooldown` | `15` | Seconds between throws at level 0. |
| `levelCooldown` | `5` | Seconds removed from the cooldown per adaptation level, floored at 1 second. |

Milestones: `challenge_nether_skull_100` on `nether.skull-yeet.skulls-thrown` (100 for 300 XP) and `challenge_nether_skull_kills_50` on `nether.skull-yeet.skull-kills` (50 for 500 XP). `challenge_nether_skull_long_bomb` is a hidden advancement granted directly on a skull kill at 40 blocks or more.

### Fire Resistance

| Property | Default |
|----------|---------|
| Class | `NetherFireResist` |
| Icon | `FIRE_CHARGE` |
| Max level | 3 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 4 |
| Cost factor | 0.75 |
| Tick interval (ms) | 4333 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-fire-resist.toml` |

Listened events: `EntityDamageEvent`, `FIRE` and `FIRE_TICK` causes only.

Negation chance is `fireResistBase + fireResistFactor * level` using the raw level, not a level percentage, so it is 35 percent at level 1 and 85 percent at level 3.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `fireResistBase` | `0.10` | Chance to cancel burn damage at level 0, 0 to 1. |
| `fireResistFactor` | `0.25` | Chance added per adaptation level, 0 to 1. |

Milestones: `challenge_nether_fire_200` and `challenge_nether_fire_5k` on `nether.fire-resist.negated` (200 for 300 XP, 5000 for 1000 XP).

### Lava Walker

| Property | Default |
|----------|---------|
| Class | `NetherLavaWalker` |
| Icon | `MAGMA_BLOCK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.75 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-lava-walker.toml` |

Listened events: `PlayerMoveEvent`. Requires a Nether-environment world, lava at your feet or directly below, food above 0, and not flying, gliding, or riding. Each stride sets velocity along your look direction, clears fall distance, clears fire ticks, and applies Fire Resistance.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `strideBase` | `0.18` | Horizontal push per stride at level 0, in blocks per tick. |
| `strideFactor` | `0.6` | Extra push added across levels. |
| `hungerCostBase` | `3` | Food points removed per stride at level 0. |
| `hungerCostFactor` | `2` | Food points subtracted from that cost across levels, floored at 1. |
| `cooldownMillisBase` | `900` | Milliseconds between strides at level 0. |
| `cooldownMillisFactor` | `700` | Milliseconds removed from the gap across levels, floored at 100. |
| `fireResistTicks` | `80` | Fire Resistance duration granted per stride, in ticks. |
| `xpPerStride` | `3.5` | Nether XP per stride. |

Milestones: `challenge_nether_lava_1k` and `challenge_nether_lava_25k` on `nether.lava-walker.blocks-walked` (1000 for 300 XP, 25000 for 1000 XP). The stat counts strides, not blocks.

### Ghast Ward

| Property | Default |
|----------|---------|
| Class | `NetherGhastWard` |
| Icon | `GHAST_TEAR` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.73 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-ghast-ward.toml` |

Listened events: `EntityDamageByEntityEvent` at `HIGHEST` (ghast fireballs and wither skeleton arrows), `EntityDamageEvent` at `HIGH` (`ENTITY_EXPLOSION` and `BLOCK_EXPLOSION`, skipped when the damager was already handled as a ghast fireball). All of it requires a Nether-environment world.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `ghastProjectileReductionBase` | `0.14` | Fraction of ghast fireball damage removed at level 0, 0 to 1. |
| `ghastProjectileReductionFactor` | `0.54` | Extra ghast reduction across levels. |
| `maxGhastProjectileReduction` | `0.8` | Ceiling on ghast reduction. |
| `explosionReductionBase` | `0.08` | Fraction of explosion damage removed at level 0, 0 to 1. |
| `explosionReductionFactor` | `0.42` | Extra explosion reduction across levels. |
| `maxExplosionReduction` | `0.65` | Ceiling on explosion reduction. |
| `witherSkeletonReductionBase` | `0.1` | Fraction of wither skeleton arrow damage removed at level 0, 0 to 1. |
| `witherSkeletonReductionFactor` | `0.4` | Extra arrow reduction across levels. |
| `maxWitherSkeletonReduction` | `0.55` | Ceiling on arrow reduction. |
| `maxFireTicksBase` | `80` | Burn ticks you are clamped to after a ghast fireball, at level 0. |
| `maxFireTicksFactor` | `70` | Ticks subtracted from that clamp across levels, floored at 0. |
| `xpPerMitigatedDamage` | `4.2` | Nether XP per point of damage the ward removed. |

Milestone: `challenge_nether_ghast_500` on `nether.ghast-ward.damage-reduced`, 500 points of damage for 400 XP.

### Blaze Leech

| Property | Default |
|----------|---------|
| Class | `NetherBlazeLeech` |
| Icon | `BLAZE_POWDER` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.62 |
| Tick interval (ms) | 900 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-blaze-leech.toml` |

Listened events: `EntityDamageEvent` (defensive trigger on `FIRE`, `FIRE_TICK`, `LAVA`, `HOT_FLOOR`), `EntityDamageByEntityEvent` (offensive trigger when you hit a target that is already burning). Both share one cooldown stored on the player.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `triggerChanceBase` | `0.16` | Chance to leech at level 0, 0 to 1. |
| `triggerChanceFactor` | `0.34` | Extra trigger chance across levels. |
| `maxTriggerChance` | `0.7` | Ceiling on trigger chance. |
| `regenTicksBase` | `28` | Regeneration duration in ticks at level 0; the result is floored at 20. |
| `regenTicksFactor` | `42` | Extra regeneration ticks across levels. |
| `regenAmplifierBase` | `0` | Regeneration amplifier at level 0 (0 is Regeneration I). |
| `regenAmplifierFactor` | `1` | Extra amplifier across levels, floored to a whole number. |
| `foodRestoreBase` | `1` | Food points restored per proc at level 0. |
| `foodRestoreFactor` | `2` | Extra food points restored across levels. |
| `saturationRestore` | `0.6` | Saturation points restored per proc, flat. |
| `cooldownMillisBase` | `1400` | Milliseconds between procs at level 0. |
| `cooldownMillisFactor` | `900` | Milliseconds removed from the gap across levels, floored at 100. |
| `xpOnDefensiveProc` | `6` | Nether XP when the proc came from damage you took. |
| `xpOnOffensiveProc` | `5` | Nether XP when the proc came from hitting a burning target. |

Milestones: `challenge_nether_blaze_200` and `challenge_nether_blaze_2500` on `nether.blaze-leech.health-from-fire` (200 for 300 XP, 2500 for 1000 XP). The stat counts procs.

### Piglin Broker

| Property | Default |
|----------|---------|
| Class | `NetherPiglinBroker` |
| Icon | `GOLD_INGOT` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 2300 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-piglin-broker.toml` |

Listened events: `PiglinBarterEvent`. The nearest player within `brokerRange` who has the adaptation active is credited. The rare bonus pool is one ender pearl, two obsidian, four string, six iron nuggets, or two spectral arrows.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `brokerRange` | `18` | Blocks from the piglin searched for an eligible broker. |
| `extraRollChanceBase` | `0.1` | Chance to duplicate one outcome item at level 0, 0 to 1. |
| `extraRollChanceFactor` | `0.45` | Extra duplication chance across levels. |
| `maxExtraRollChance` | `0.6` | Ceiling on duplication chance. |
| `rareBonusChanceBase` | `0.03` | Chance for a premium bonus item at level 0, 0 to 1. |
| `rareBonusChanceFactor` | `0.2` | Extra premium chance across levels. |
| `maxRareBonusChance` | `0.25` | Ceiling on premium chance. |
| `amountMultiplierBase` | `1.0` | Stack size multiplier on the duplicated item at level 0, floored at 1. |
| `amountMultiplierFactor` | `0.5` | Extra stack multiplier across levels; the result is capped by the item's max stack size. |
| `xpOnBoostedBarter` | `12` | Nether XP when a barter was improved. |

Milestones: `challenge_nether_piglin_100` and `challenge_nether_piglin_2500` on `nether.piglin-broker.improved-barters` (100 for 300 XP, 2500 for 1000 XP).

### Soul Strider

| Property | Default |
|----------|---------|
| Class | `NetherSoulStrider` |
| Icon | `SOUL_SAND` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.6 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-soul-strider.toml` |

Listened events: `PlayerMoveEvent`. Requires soul sand or soul soil at your feet or below, and not flying, gliding, or riding. Applies a movement efficiency modifier of `1.0` (which is what removes the soul sand slowdown) plus a movement speed multiplier, both refreshed in 40-tick holds while you stay on soul ground and removed the moment you step off. The burst only fires at max level.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `strideSpeedBase` | `0.20` | Reference stride speed; the movement speed bonus is `(levelPercent * factor) / base`. |
| `strideSpeedFactor` | `0.10` | Extra stride speed across levels. |
| `burstTicks` | `60` | Soul-speed burst duration in ticks. |
| `burstAmplifier` | `1` | Burst strength; the speed multiplier is `0.2 * (amplifier + 1)`. |
| `burstGapMillis` | `600` | Milliseconds you must be off soul ground before stepping back on can fire a burst. |
| `burstCooldownMillis` | `3000` | Milliseconds between bursts. |
| `xpPerStride` | `2.0` | Nether XP per XP interval while striding. |
| `xpIntervalMillis` | `1500` | Milliseconds between stride XP awards. |

Milestones: `challenge_nether_soul_1k` and `challenge_nether_soul_25k` on `nether.soul-strider.blocks-walked` (1000 for 300 XP, 25000 for 1000 XP). The stat accumulates actual horizontal distance.

### Magma Skin

| Property | Default |
|----------|---------|
| Class | `NetherMagmaSkin` |
| Icon | `MAGMA_CREAM` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.7 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-magma-skin.toml` |

Listened events: `EntityDamageByEntityEvent` twice, `onDefend` (you are the victim) and `onAttack` (you are the damager). Both require you to be on fire and the cause to be `ENTITY_ATTACK` or `ENTITY_SWEEP_ATTACK`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `reflectFireTicksBase` | `40` | Ticks an attacker is set alight at level 0. |
| `reflectFireTicksFactor` | `60` | Extra attacker burn ticks across levels. |
| `bonusDamageBase` | `0.5` | Flat damage added to your melee hits at level 0, in half-hearts. |
| `bonusDamageFactor` | `2.5` | Extra flat damage across levels. |
| `bonusFireTicksBase` | `40` | Ticks your target is set alight at level 0. |
| `bonusFireTicksFactor` | `40` | Extra target burn ticks across levels. |
| `xpOnReflect` | `6` | Nether XP each time an attacker is ignited. |
| `xpPerBonusDamage` | `3` | Nether XP per point of bonus damage dealt. |

Milestones: `challenge_nether_magma_100` and `challenge_nether_magma_2500` on `nether.magma-skin.attackers-ignited` (100 for 300 XP, 2500 for 1000 XP).

### Netherrack Mason

| Property | Default |
|----------|---------|
| Class | `NetherNetherrackMason` |
| Icon | `BLACKSTONE` |
| Max level | 4 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.65 |
| Tick interval (ms) | 1500 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-netherrack-mason.toml` |

Listened events: `BlockDamageEvent` (grants the mining speed modifier when you start hitting an eligible block) and `BlockBreakEvent` (XP, stats, bonus drop). Both require a Nether-environment world.

Eligible blocks: `NETHERRACK`, `BASALT`, `POLISHED_BASALT`, `SMOOTH_BASALT`, `BLACKSTONE`, `POLISHED_BLACKSTONE`, `GILDED_BLACKSTONE`, `CHISELED_POLISHED_BLACKSTONE`, `POLISHED_BLACKSTONE_BRICKS`, `CRACKED_POLISHED_BLACKSTONE_BRICKS`. The boost is a block-break-speed attribute modifier of `0.20 * tier`, not the Haste potion effect.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `hasteTierBase` | `1` | Mining speed tier at level 0, floored at 1. |
| `hasteTierFactor` | `1.5` | Extra tier across levels, rounded to a whole number. |
| `hasteDurationTicks` | `120` | How long each mining speed application lasts, in ticks. |
| `hasteRefreshMillis` | `4000` | Milliseconds before the modifier is reapplied. |
| `bonusDropChanceBase` | `0.08` | Chance of a bonus drop per block at level 0, 0 to 1. |
| `bonusDropChanceFactor` | `0.35` | Extra bonus drop chance across levels. |
| `maxBonusDropChance` | `0.4` | Ceiling on bonus drop chance. |
| `premiumDropChance` | `0.25` | Chance that a bonus drop is a premium item (gold nugget, quartz, iron nugget, or nether brick) instead of a copy of the mined block. |
| `xpPerBlock` | `1.5` | Nether XP per eligible block broken. |
| `xpOnBonusDrop` | `5` | Extra Nether XP when a bonus drop lands. |

Milestones: `challenge_nether_mason_1k` and `challenge_nether_mason_25k` on `nether.netherrack-mason.blocks-mined` (1000 for 300 XP, 25000 for 1000 XP).

### Strider Bond

| Property | Default |
|----------|---------|
| Class | `NetherStriderBond` |
| Icon | `WARPED_FUNGUS_ON_A_STICK` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.6 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-strider-bond.toml` |

Listened events: `PlayerMoveEvent` (only while your vehicle is a strider; clears shivering and refreshes the mount speed modifier) and Adapt's reflective `EntityDismountEvent` (the lava rescue). Rescue teleports go through the async teleport path and only settle when the teleport actually succeeds.

The strider speed modifier is `0.2 * (amplifier + 1)` applied to the strider, not to you.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `striderSpeedAmplifierBase` | `0` | Speed amplifier at level 0. |
| `striderSpeedAmplifierFactor` | `1.5` | Extra amplifier across levels, rounded to a whole number. |
| `speedTicks` | `60` | How long each speed application lasts, in ticks. |
| `safetyUnlockLevel` | `2` | Adaptation level required before the lava dismount rescue works. |
| `searchRadiusBase` | `4` | Blocks searched outward for safe ground at level 0. |
| `searchRadiusFactor` | `4` | Extra search radius across levels. |
| `searchRadiusMax` | `8` | Hard cap on the search radius, keeping the scan local. |
| `xpPerRide` | `2` | Nether XP per XP interval while riding. |
| `xpIntervalMillis` | `1500` | Milliseconds between riding XP awards. |
| `xpPerRescue` | `30` | Nether XP for a successful lava rescue. |

Milestones: `challenge_nether_strider_500` and `challenge_nether_strider_5k` on `nether.strider-bond.blocks-ridden` (500 for 300 XP, 5000 for 1000 XP). `challenge_nether_strider_rescue` is a hidden advancement granted on your first successful rescue, which also increments `nether.strider-bond.lava-rescues`.

### Crimson Feast

| Property | Default |
|----------|---------|
| Class | `NetherCrimsonFeast` |
| Icon | `CRIMSON_FUNGUS` |
| Max level | 4 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.55 |
| Tick interval (ms) | 3000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-crimson-feast.toml` |

Listened events: `PlayerInteractEvent` (receives cancelled events; main hand right-click with eligible flora) and `PlayerItemConsumeEvent` (any food, Nether only). Eating flora is blocked at a full food bar unless you are sneaking, and the flora item is consumed except in creative. The fire resistance grant only happens in a Nether-environment world, so eating flora elsewhere still feeds you but gives no buff.

Eligible flora: `CRIMSON_FUNGUS`, `WARPED_FUNGUS`, `CRIMSON_ROOTS`, `WARPED_ROOTS`, `NETHER_SPROUTS`, `WEEPING_VINES`, `TWISTING_VINES`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `floraFoodBase` | `2` | Food points restored per flora item at level 0, floored at 1. |
| `floraFoodFactor` | `4` | Extra food points across levels. |
| `floraSaturationBase` | `1.5` | Saturation restored per flora item at level 0. |
| `floraSaturationFactor` | `3` | Extra saturation across levels. |
| `resistTicksBase` | `60` | Fire Resistance duration in ticks at level 0. |
| `resistTicksFactor` | `140` | Extra Fire Resistance ticks across levels. |
| `eatCooldownMillis` | `350` | Milliseconds between flora bites. |
| `xpPerFungus` | `4` | Nether XP per flora item eaten. |
| `xpPerNetherMeal` | `3` | Nether XP for eating anything else while in the Nether. |

Milestones: `challenge_nether_feast_100` and `challenge_nether_feast_2500` on `nether.crimson-feast.fungi-eaten` (100 for 300 XP, 2500 for 1000 XP).

### Ashwalker

| Property | Default |
|----------|---------|
| Class | `NetherAshwalker` |
| Icon | `CAMPFIRE` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.7 |
| Tick interval (ms) | 4000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-ashwalker.toml` |

Listened events: `EntityDamageEvent`, ignoring anything that is an `EntityDamageByEntityEvent`. `HOT_FLOOR` (magma blocks) is cancelled outright at any level. `CAMPFIRE` damage, and `FIRE`/`FIRE_TICK` damage traced to a lit campfire under or at your feet, is cancelled from `campfireUnlockLevel` up. Soul fire is only reduced at max level.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `campfireUnlockLevel` | `2` | Adaptation level required before campfire burns are cancelled. |
| `soulFireReduction` | `0.8` | Fraction of soul fire damage removed at max level, 0 to 1. |
| `soulFireMaxFireTicks` | `20` | Burn ticks you are clamped to after soul fire damage. |
| `xpPerNegatedDamage` | `3` | Nether XP per point of damage cancelled or reduced. |

Milestones: `challenge_nether_ash_200` and `challenge_nether_ash_5k` on `nether.ashwalker.damage-negated` (200 for 300 XP, 5000 for 1000 XP).

### Wither Harvest

| Property | Default |
|----------|---------|
| Class | `NetherWitherHarvest` |
| Icon | `BONE` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.7 |
| Tick interval (ms) | 5000 |
| Config file | `plugins/Adapt/adapt/adaptations/nether-wither-harvest.toml` |

Listened events: `EntityDeathEvent`, wither skeletons killed by a player only.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusBonesBase` | `1` | Extra bones dropped at level 0, floored at 1. |
| `bonusBonesFactor` | `2` | Extra bones added across levels. |
| `bonusCoalBase` | `0.5` | Extra coal dropped at level 0, floored at 1. |
| `bonusCoalFactor` | `2` | Extra coal added across levels. |
| `skullChanceBase` | `0.03` | Chance to add a wither skeleton skull at level 0, 0 to 1. |
| `skullChanceFactor` | `0.12` | Extra skull chance across levels. |
| `maxSkullChance` | `0.15` | Ceiling on skull chance. |
| `xpPerHarvest` | `12` | Nether XP per harvested wither skeleton. |
| `xpOnSkull` | `40` | Extra Nether XP when the roll adds a skull. |

Milestones: `challenge_nether_harvest_100` and `challenge_nether_harvest_2500` on `nether.wither-harvest.skeletons-harvested` (100 for 300 XP, 2500 for 1000 XP).

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
