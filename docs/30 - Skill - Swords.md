# Skill: Swords

Swords is the melee skill for anyone who fights with a blade in the main hand. You level it by hitting things with a sword and by killing with one, and the XP scales with the damage you actually deal. It has 14 adaptations and shows up in the menu as a yellow `DIAMOND_SWORD`.

Most of the tree is damage. Dual Wield Stance pays you for holding a sword in both hands. Executioner's Edge hits harder as targets drop low. Riposte Window turns a shield block into a counterattack. Blade Flow builds attack speed for chaining hits. Lunge Strike closes the gap on a sprint attack, and Crimson Cyclone turns a crit into a bleeding area slash.

The rest is texture. Poisoned Blade and Bloody Blade put damage-over-time on whatever you cut. Hamstring stops runners. Crescent Guard hands you absorption hearts on every kill. Duelist's Focus rewards clean one-on-one fights with both damage and defence. Machete is the odd one out, a utility pick that clears foliage with a left click.

Two adaptations are gear-shaped rather than fight-shaped. Whetstone Ritual grinds a temporary attack damage buff off a grindstone for durability and XP levels. Heirloom Edge lets you name a sword at an anvil and grow a small permanent damage bonus into that specific blade over hundreds of kills.

## Adaptations

Everything below needs the same four things before it does anything: the adaptation learned at level 1 or higher, the skill and the adaptation both enabled in config, the matching `adapt.use.*` permission, and any protection or region plugin allowing the action. Nearly every adaptation here also needs a sword in your main hand, which for Adapt means a wooden, stone, copper, iron, golden, diamond, or netherite sword. Those preconditions are not repeated per adaptation.

### Machete (`sword-machete`)

Left-click with a sword and you cut a sphere of foliage in front of you: grass, ferns, vines, flowers, leaves, bamboo, sugar cane, seagrass, mushrooms, and crops. Blocks nearer the center are more likely to be cut, and each block cut chews a bit of durability off the sword.

Higher levels give a bigger radius, a shorter cooldown, and less wear per block. Every cut block pays skill XP, so clearing a jungle is a real levelling route.

1. Learn Machete.
2. Hold a sword.
3. Left-click at the foliage in front of you.
4. Wait for the item cooldown to clear before the next swing.

Each block still goes through a normal block break event, so a region plugin that would deny you the break denies the cut.

### Poisoned Blade (`sword-poison-blade`)

Sword hits apply Poison III to the target and spray a blood-and-fern visual. Mobs that vanilla treats as poison-immune (zombies, skeletons, phantoms, wither, zoglin, giant, spiders, skeleton and zombie horses) take a small damaging bleed instead so the adaptation still does something.

There is a cooldown between applications, so it is one proc per fight opener rather than a stack on every swing. Kills that happen while your poison is still on the target credit you a poison kill.

Passive. Hit things with a sword.

### Bloody Blade (`sword-bloody-blade`)

Sword hits start a bleed on the target that ticks damage every quarter second for a level-scaled duration. It ignores armor because it is direct damage, which makes it strong against heavily armored targets.

Every single bleed tick is re-authorized against your protection rules and your friendly-entity rules before it lands, so a bleed cannot follow a target into a region where you are not allowed to hurt it, and it never hurts your own tamed pets.

Passive. Hit things with a sword.

### Dual Wield Stance (`sword-dual-wield`)

Hold a sword in your main hand and a sword in your off hand and every melee hit is multiplied. Two swords of the same material give the bigger multiplier; a mismatched pair gives a smaller one.

Passive. Fill both hands.

### Executioner's Edge (`sword-executioners-edge`)

Sword hits against a target already below a health threshold deal extra damage. Both the threshold and the bonus grow with level, and the threshold is capped so it never turns into a full-health execute.

Land five buffed hits inside ten seconds and you get an advancement.

Passive.

### Riposte Window (`sword-riposte-window`)

Raise a shield, eat a hit, and you arm a short riposte. The next sword strike you land inside that window deals a large bonus. The window opens on the block itself, not on a perfect parry, so it rewards actually using the shield rather than timing a frame.

1. Learn Riposte Window.
2. Hold a shield in either hand and raise it.
3. Let an attack land on the shield. A gold ring shows the riposte is armed.
4. Swap to your sword and hit back before the window closes. The window is short at low level and roughly a second at max.

Land three ripostes inside five seconds and you get an advancement.

### Crimson Cyclone (`sword-crimson-cyclone`)

Land a critical hit with a sword, which in vanilla means swinging while falling, and you erupt a bleeding slash around your target. The primary target eats extra damage on the same swing, everything else in the radius takes the cyclone damage, and every target it touches starts bleeding.

It is not free. Each cyclone costs hunger and sword durability, and it is on a long cooldown. Both costs get cheaper as you level and the cooldown gets shorter.

1. Learn Crimson Cyclone.
2. Hold a sword and fill your hunger bar.
3. Jump and hit a mob on the way down so the swing crits.
4. The cyclone fires automatically. Hit six or more targets in one activation for an advancement.
5. Wait out the cooldown.

Secondary targets are individually authorized against your PvP and PvE rules, and your own tamed pets are never hit.

### Lunge Strike (`sword-lunge-strike`)

Sprint-attack with a sword and you get thrown forward into the blow, with a brief window of extra entity reach so the swing that started the lunge connects at longer range. It is a gap closer bolted onto an attack you were making anyway.

1. Learn Lunge Strike.
2. Sprint at a target with a sword out.
3. Attack while still sprinting. You surge forward and the reach bonus applies for the next few ticks.

There is a short cooldown so you cannot chain-fling yourself across the map.

### Blade Flow (`sword-blade-flow`)

Every sword hit adds a flow stack and each stack adds ten percent attack speed. Stacks decay if you stop hitting for a few seconds, and any damage you take drops the whole stack immediately. Level raises the ceiling on how many stacks you can hold.

Passive, but it rewards not getting hit. Reach the stack cap once for an advancement.

### Duelist's Focus (`sword-duelists-focus`)

Works only when the fight is genuinely one-on-one. If exactly one hostile mob or player is inside the engage radius, your sword damage goes up and incoming damage goes down, and the thing hitting you briefly glows so you can see who your duel partner is. Bring a second attacker into that radius and the whole effect stops.

Passive. The defence half also needs a sword in your main hand.

### Whetstone Ritual (`sword-whetstone-ritual`)

Grind a temporary attack damage buff into yourself at a grindstone. It costs sword durability and experience levels, and it is on a one minute cooldown by default. The grindstone GUI does not open when the ritual fires.

1. Learn Whetstone Ritual.
2. Hold the sword you want to grind in your main hand.
3. Stand at a grindstone with enough experience levels.
4. Sneak and right-click the grindstone. The sword takes durability, you lose the XP levels, and the buff starts.
5. Wait out the cooldown before grinding again.

The ritual refuses if you are short on XP levels, and it refuses if the durability cost would break the sword.

### Crescent Guard (`sword-crescent-guard`)

Every kill you land with a sword in your main hand hands you absorption hearts for a few seconds. It stacks up in a fight full of mobs, because each kill refreshes the guard rather than replacing it with something weaker. The tier and duration both grow with level.

Passive. Kill with a sword.

### Hamstring (`sword-hamstring`)

Hit something that is running and you slow it hard. A sprinting player also has their sprint cancelled outright. Non-players count as fleeing when their horizontal speed crosses a threshold, so it lands on anything actually trying to leave.

The slow is a movement speed modifier rather than the Slowness potion, so it does not show in the effect list and cannot be milked off.

Passive.

### Heirloom Edge (`sword-heirloom-edge`)

Turn one sword into your sword. Name it at an anvil and it is stamped as an heirloom with a gold lore line. From then on, every few kills you make while holding it bank a small permanent attack damage bonus straight onto the item, up to a level-scaled cap.

The bonus lives on the item, not on you, so the blade keeps it if you drop it, store it, or hand it to someone else.

1. Learn Heirloom Edge.
2. Put a sword in an anvil and type any new name.
3. Take the result. It now carries the Heirloom Edge lore line.
4. Kill things while holding it. Every few kills banks another step of damage.
5. Keep going until the blade hits its cap. Raising the adaptation level raises the cap.

## Reference

Everything below is exact code truth. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`. Every adaptation TOML also carries the shared keys `enabled`, `permanent`, `showParticles`, and `showSounds`, which are not repeated per adaptation.

Adapt treats `WOODEN_SWORD`, `STONE_SWORD`, `COPPER_SWORD`, `IRON_SWORD`, `GOLDEN_SWORD`, `DIAMOND_SWORD`, and `NETHERITE_SWORD` as swords.

### Identity

| Property | Value |
|----------|-------|
| Skill id | `swords` |
| Class | `SkillSwords` |
| Icon | `DIAMOND_SWORD` |
| Color | `YELLOW` |
| Interval (ms) | `2150` |
| Skill config | `plugins/Adapt/adapt/skills/swords.toml` |
| Adaptation count | 14 |

### Skill XP sources

| Trigger | Award | Notes |
|---------|-------|-------|
| Damaging a valid living entity with a sword in the main hand | `damageXPMultiplier` times the damage dealt | Rate-limited by `cooldownDelay`. Stats are added before the rate limit, so `sword.hits` and `sword.damage` always count. Parrots and the invalid-damageable entity listing are excluded. |
| Killing with a sword in the main hand | No XP | Adds `sword.kills` only. |

A hit counts as critical for `sword.critical` when the attacker's fall distance is above 0 and the attacker is not on the ground. A hit counts as heavy for `sword.heavy.hits` when the event damage is above 8.

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/swords.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Set to false to disable the whole skill. |
| `skillColor` | `"&e"` | Legacy ampersand color code used for this skill in menus and text. |
| `cooldownDelay` | `1250` | Milliseconds between sword damage XP awards, per player. |
| `damageXPMultiplier` | `4.5` | Multiplier applied to sword damage dealt when converting it to XP. |
| `challengeSwordReward` | `500` | XP paid for `challenge_sword_100`. The 1k tier pays double and the 10k tier pays five times this value. |
| `challengeSwordDmgReward` | `500` | XP paid for `challenge_sword_dmg_1k`. The 10k tier pays triple. |
| `challengeSwordKillsReward` | `500` | XP paid for `challenge_sword_kills_50`. The 500 tier pays triple. |
| `challengeSwordCritReward` | `500` | XP paid for `challenge_sword_crit_50`. The 500 tier pays triple. |
| `challengeSwordHeavyReward` | `500` | XP paid for `challenge_sword_heavy_25`. The 250 tier pays triple. |

### Skill milestones

| Challenge key | Stat key | Threshold | Reward |
|---------------|----------|-----------|--------|
| `challenge_sword_100` | `sword.hits` | 100 | `challengeSwordReward` |
| `challenge_sword_1k` | `sword.hits` | 1000 | `challengeSwordReward` x2 |
| `challenge_sword_10k` | `sword.hits` | 10000 | `challengeSwordReward` x5 |
| `challenge_sword_dmg_1k` | `sword.damage` | 1000 | `challengeSwordDmgReward` |
| `challenge_sword_dmg_10k` | `sword.damage` | 10000 | `challengeSwordDmgReward` x3 |
| `challenge_sword_kills_50` | `sword.kills` | 50 | `challengeSwordKillsReward` |
| `challenge_sword_kills_500` | `sword.kills` | 500 | `challengeSwordKillsReward` x3 |
| `challenge_sword_crit_50` | `sword.critical` | 50 | `challengeSwordCritReward` |
| `challenge_sword_crit_500` | `sword.critical` | 500 | `challengeSwordCritReward` x3 |
| `challenge_sword_heavy_25` | `sword.heavy.hits` | 25 | `challengeSwordHeavyReward` |
| `challenge_sword_heavy_250` | `sword.heavy.hits` | 250 | `challengeSwordHeavyReward` x3 |

### Machete

| Property | Value |
|----------|-------|
| Class | `SwordsMachete` |
| Icon | `IRON_SWORD` |
| Max level | 3 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 4 |
| Cost factor | 0.225 |
| Tick interval (ms) | 5234 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-machete.toml` |

Menu stat lines: Slash Radius; Chop Cooldown; Tool Wear.

Listened events:

- `PlayerInteractEvent` - fires on `LEFT_CLICK_AIR` or `LEFT_CLICK_BLOCK` with the main hand

The cut sphere is centered 2.25 blocks along the player's look vector and half a block below eye level. Each block inside it is cut with probability `levelPercent * 2.8 / distanceSquared`, so the center is reliable and the edge is sparse. Cut blocks are the foliage listing: tall grass, grass, cactus, sugar cane, carrot, potato, nether wart, fern, large fern, vine, rose bush, wither rose, the six vanilla leaf types plus mangrove leaves, brown and red mushroom, dead bush, dandelion, seagrass and tall seagrass, all six small flowers, sunflower, cornflower, chorus flower, bamboo and bamboo sapling, lilac, peony, lily pad, and cocoa. Every candidate fires a real `BlockBreakEvent` and is skipped if that event is cancelled. Skill XP is `11.25` per block cut. Sword durability taken is `damagePerBlock * blocksCut`. The item cooldown is set on the sword's material.

Milestones: `challenge_swords_machete_2500` on `swords.machete.foliage-cut` at 2500 (reward 300); `challenge_swords_machete_25k` at 25000 (reward 1000).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `0.6` | Cut radius in blocks at level 0 percent. |
| `radiusFactor` | `2.36` | Extra cut radius in blocks gained at max level. |
| `cooldownTicksBase` | `7` | Floor of the item cooldown in ticks, reached at max level. |
| `cooldownTicksSlowest` | `35` | Extra cooldown ticks added at level 0 percent. Cooldown is `cooldownTicksBase + (1 - levelPercent) * cooldownTicksSlowest`. |
| `toolDamageBase` | `1` | Floor of the durability cost per cut block, reached at max level. |
| `toolDamageInverseLevelFactor` | `5` | Extra durability per cut block at level 0 percent. Cost is `toolDamageBase + toolDamageInverseLevelFactor * (1 - levelPercent)`. |

### Poisoned Blade

| Property | Value |
|----------|-------|
| Class | `SwordsPoisonedBlade` |
| Icon | `GREEN_DYE` |
| Max level | 7 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 7 |
| Cost factor | 0.325 |
| Tick interval (ms) | 4984 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-poison-blade.toml` |

Menu stat lines: Striking a Living entity with your Sword causes Poison; Poison Duration; Poison Cooldown.

Listened events:

- `EntityDamageByEntityEvent` (`HIGHEST`, ignore cancelled) - applies poison
- `EntityDeathEvent` (`MONITOR`, ignore cancelled) - credits a poison kill

The applied potion effect is `POISON` at amplifier 2 for `50 * level` ticks. The menu's Poison Duration line instead shows `effectDuration * level` milliseconds, so the displayed duration and the applied potion duration are computed from different numbers and do not match at default settings. The cooldown is `max(cooldown, effectDuration * level)` milliseconds. Poison-immune targets (zombie, abstract skeleton, skeleton horse, zombie horse, phantom, wither, zoglin, giant, spider) get a damaging bleed of 1 health per proc instead of the potion. A kill within `4000` ms of the poison expiring still credits a poison kill.

Milestones: `challenge_swords_poison_500` on `swords.poisoned-blade.poison-applied` at 500 (reward 400); `challenge_swords_poison_kills_50` on `swords.poisoned-blade.poison-kills` at 50 (reward 1000).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldown` | `5000` | Minimum milliseconds between poison applications. The effective cooldown is the larger of this and the level-scaled effect duration. |
| `effectDuration` | `1000` | Milliseconds of effect duration granted per adaptation level. Drives the cooldown floor and the bleed visual length, and is what the menu duration line shows. |

### Bloody Blade

| Property | Value |
|----------|-------|
| Class | `SwordsBloodyBlade` |
| Icon | `RED_DYE` |
| Max level | 7 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 7 |
| Cost factor | 0.325 |
| Tick interval (ms) | 5534 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-bloody-blade.toml` |

Menu stat lines: Striking a Living entity with your Sword causes Bleeding; Bleed Duration; Bleed Cooldown.

Listened events:

- `EntityDamageByEntityEvent` (`HIGHEST`, ignore cancelled) - starts the bleed
- `EntityDeathEvent` (`MONITOR`, ignore cancelled) - credits a bleed kill

Bleed duration is `effectDuration * level` milliseconds. Procs land every `5` ticks, so the proc count is `ceil(durationTicks / 5)` with a minimum of 1. Each proc calls `damage` on the target with the player as the source, which means it goes through armor and knockback like normal damage. Before each proc the adaptation re-checks that the player is online, still has the adaptation, and passes `canPVP` or `canPVE` at the target's current location, and it skips targets that are protected friendlies or the player's own tamed pets. The bleed damage stat records the health and absorption actually removed. A kill within `4000` ms of the bleed expiring still credits a bleed kill.

Milestones: `challenge_swords_bloody_500` on `swords.bloody-blade.bleed-damage` at 500 (reward 400); `challenge_swords_bloody_kills_100` on `swords.bloody-blade.bleed-kills` at 100 (reward 1000).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldown` | `5000` | Minimum milliseconds between bleed applications. The effective cooldown is the larger of this and the level-scaled bleed duration. |
| `damagePerBleedProc` | `0.5` | Health points dealt by each bleed proc (2 points = 1 heart). Floored at 0.01. |
| `effectDuration` | `1000` | Milliseconds of bleed duration granted per adaptation level. |

### Dual Wield Stance

| Property | Value |
|----------|-------|
| Class | `SwordsDualWield` |
| Icon | `GOLDEN_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1800 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-dual-wield.toml` |

Menu stat lines: Matching Sword Bonus; Mixed Sword Bonus.

Listened events:

- `EntityDamageByEntityEvent` (`HIGHEST`, ignore cancelled) - applies the multiplier

Requires a sword in the main hand and a sword in the off hand. Matching means the two items are the exact same material. The multiplier is clamped to a minimum of 1, so a misconfigured base below 1 cannot reduce your damage. XP is the final damage times `xpPerDamage`, and the stat records only the bonus damage added.

Milestones: `challenge_swords_dual_1k` on `swords.dual-wield.bonus-damage` at 1000 (reward 400); `challenge_swords_dual_25k` at 25000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `sameWeaponBase` | `1.12` | Damage multiplier with two identical swords, before level scaling. |
| `sameWeaponFactor` | `0.43` | Extra matching multiplier gained at max level. |
| `mixedWeaponBase` | `1.06` | Damage multiplier with two different swords, before level scaling. |
| `mixedWeaponFactor` | `0.28` | Extra mixed multiplier gained at max level. |
| `xpPerDamage` | `2.0` | Skill XP per point of final damage on a dual-wield hit. |

### Executioner's Edge

| Property | Value |
|----------|-------|
| Class | `SwordsExecutionersEdge` |
| Icon | `STONE_SWORD` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.65 |
| Tick interval (ms) | 1900 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-executioners-edge.toml` |

Menu stat lines: Bonus Damage; Health Threshold.

Listened events:

- `EntityDamageByEntityEvent` (`HIGHEST`, ignore cancelled) - applies the bonus

The trigger is the target's current health divided by its maximum health being at or below the threshold. The stat counts every buffed hit, not only lethal ones. The `challenge_swords_execute_5in10` advancement is granted directly in code after 5 buffed hits within 10 seconds and has no stat milestone.

Milestones: `challenge_swords_execute_200` on `swords.executioners-edge.executions` at 200 (reward 400); `challenge_swords_execute_2500` at 2500 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusDamageBase` | `0.08` | Bonus damage as a fraction of base damage, before level scaling. |
| `bonusDamageFactor` | `0.42` | Extra damage fraction gained at max level. |
| `thresholdBase` | `0.22` | Target health fraction at or below which the bonus applies, before level scaling. |
| `thresholdFactor` | `0.33` | Extra threshold fraction gained at max level. |
| `maxThreshold` | `0.65` | Hard cap on the health fraction threshold, 0-1. |
| `xpPerBuffedDamage` | `1.9` | Skill XP per point of buffed damage dealt. |

### Riposte Window

| Property | Value |
|----------|-------|
| Class | `SwordsRiposteWindow` |
| Icon | `GOLDEN_CHESTPLATE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.71 |
| Tick interval (ms) | 2100 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-riposte-window.toml` |

Menu stat lines: Riposte Window; Riposte Damage Bonus.

Listened events:

- `EntityDamageByEntityEvent` (`HIGHEST`, ignore cancelled) - arms the window when the damaged entity is a learner blocking with a shield, and spends it when the damager is a learner with an armed window

Arming requires `isBlocking()` and a `SHIELD` in the main or off hand. The window is consumed on the first qualifying sword hit. The `challenge_swords_riposte_3in5` advancement is granted directly in code after 3 ripostes within 5 seconds and has no stat milestone.

Milestones: `challenge_swords_riposte_200` on `swords.riposte.ripostes-landed` at 200 (reward 400); `challenge_swords_riposte_2500` at 2500 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `windowMillisBase` | `350` | Milliseconds the riposte stays armed, before level scaling. Floored at 150 ms. |
| `windowMillisFactor` | `550` | Extra armed milliseconds gained at max level. |
| `damageBonusBase` | `0.22` | Riposte bonus as a fraction of base damage, before level scaling. |
| `damageBonusFactor` | `0.75` | Extra bonus fraction gained at max level. |
| `xpPerBuffedDamage` | `1.8` | Skill XP per point of riposte damage dealt. |

### Crimson Cyclone

| Property | Value |
|----------|-------|
| Class | `SwordsCrimsonCyclone` |
| Icon | `NETHERITE_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.76 |
| Tick interval (ms) | 2400 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-crimson-cyclone.toml` |

Menu stat lines: Cyclone Radius; Cyclone Damage; Cyclone Cooldown.

Listened events:

- `EntityDamageByEntityEvent` (`HIGHEST`, ignore cancelled) - fires the cyclone on a critical sword hit

Requires the server to report the hit as critical. The cyclone adds its damage to the triggering hit, then damages nearby living entities for the same amount and starts a bleed on each. Secondary targets are re-authorized one at a time against `canPVP` or `canPVE` at their current location, skipping protected friendlies and the player's own tamed pets, and the batch times out after 3 ticks. Hard caps not exposed in config: 32 candidates, 16 affected, 12 target effects. The affected limit can never exceed the candidate limit plus one. Hitting 6 or more targets in one activation grants the `challenge_swords_cyclone_6` advancement, which has no stat milestone.

Milestones: `challenge_swords_cyclone_500` on `swords.crimson-cyclone.mobs-hit` at 500 (reward 400); `challenge_swords_cyclone_5k` at 5000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `showBleedParticles` | `true` | Shows the crimson roots bleed particle on hit targets. |
| `radiusBase` | `2.6` | Cyclone radius in blocks, before level scaling. |
| `radiusFactor` | `2.4` | Extra radius gained at max level. |
| `baseDamage` | `2.0` | Cyclone damage in health points, before level scaling. |
| `damageFactor` | `4.0` | Extra cyclone damage gained at max level. |
| `bleedTicksBase` | `40` | Bleed duration in ticks, before level scaling. Floored at 20 ticks. |
| `bleedTicksFactor` | `90` | Extra bleed ticks gained at max level. |
| `bleedDamagePerProcBase` | `0.35` | Health points per bleed proc, before level scaling. Floored at 0.01. |
| `bleedDamagePerProcFactor` | `0.45` | Extra bleed damage per proc gained at max level. |
| `hungerCostBase` | `2` | Food points spent per cyclone at level 0 percent. |
| `hungerCostFactor` | `2` | Food points removed from the cost at max level. The cost falls as you level and floors at 1. |
| `durabilityCostBase` | `3` | Sword durability spent per cyclone at level 0 percent. |
| `durabilityCostFactor` | `1.5` | Durability removed from the cost at max level. The cost falls as you level and floors at 1. |
| `cooldownTicksBase` | `320` | Cooldown in ticks at level 0 percent (20 ticks = 1 second). |
| `cooldownTicksFactor` | `160` | Cooldown ticks removed at max level. Floors at 40 ticks. |
| `xpPerTargetHit` | `10` | Skill XP per target hit by the cyclone. |
| `maxCandidatesPerActivation` | `16` | Maximum living entities inspected per activation. Hard cap 32. |
| `maxAffectedPerActivation` | `12` | Maximum targets damaged per activation, including the primary. Hard cap 16. |
| `maxTargetFxPerActivation` | `9` | Maximum targets that get individual spark effects. Hard cap 12. |

### Lunge Strike

| Property | Value |
|----------|-------|
| Class | `SwordsLungeStrike` |
| Icon | `IRON_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-lunge-strike.toml` |

Menu stat lines: Lunge Force; Bonus Reach.

Listened events:

- `EntityDamageByEntityEvent` (`HIGHEST`, ignore cancelled) - fires the lunge on a sprinting sword hit

Requires `isSprinting()`. The horizontal surge is `lungeForce + (bonusReach * reachVelocityFactor)` capped at `maxSurge`, applied on top of current velocity with `verticalBoost` as the Y component. Bonus reach is applied as an `ENTITY_INTERACTION_RANGE` modifier under the `reach` slot for the reach window, which is floored at 5 ticks.

Milestones: `challenge_swords_lunge_250` on `swords.lunge-strike.lunges` at 250 (reward 400); `challenge_swords_lunge_2500` at 2500 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `forceBase` | `0.35` | Forward velocity magnitude, before level scaling. |
| `forceFactor` | `0.45` | Extra forward velocity gained at max level. |
| `reachBase` | `0.8` | Bonus entity interaction range in blocks, before level scaling. |
| `reachFactor` | `1.8` | Extra bonus reach gained at max level. |
| `reachVelocityFactor` | `0.12` | How much of the bonus reach is folded back into the lunge velocity. |
| `verticalBoost` | `0.18` | Vertical velocity component of the lunge. |
| `reachWindowTicks` | `12` | Ticks the bonus reach modifier lasts. Floored at 5. |
| `maxSurge` | `1.1` | Hard cap on total horizontal lunge velocity. |
| `cooldownMillis` | `350` | Minimum milliseconds between lunges. |
| `xpPerLunge` | `6` | Skill XP per lunge. |

### Blade Flow

| Property | Value |
|----------|-------|
| Class | `SwordsBladeFlow` |
| Icon | `GOLDEN_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.62 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-blade-flow.toml` |

Menu stat lines: Max Flow Stacks; Attack Speed / Stack.

Listened events:

- `EntityDamageByEntityEvent` (`HIGHEST`, ignore cancelled) - adds a stack
- `EntityDamageEvent` (`MONITOR`, ignore cancelled) - clears all stacks when the learner takes damage with final damage above 0

Each stack is worth a fixed `0.10` of attack speed, applied as an `ADD_SCALAR` modifier on `ATTACK_SPEED` under the `flow` slot with a duration matching the flow window, floored at 20 ticks. Stacks are counted per player and reset to zero if the window has already lapsed when the next hit lands. Reaching the stack cap grants the `challenge_swords_flow_max` advancement, which has no stat milestone.

Milestones: `challenge_swords_flow_1k` on `swords.blade-flow.stacks-built` at 1000 (reward 400); `challenge_swords_flow_10k` at 10000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `stackCapBase` | `1.5` | Maximum flow stacks, before level scaling. Rounded, minimum 1. |
| `stackCapFactor` | `4.5` | Extra stack cap gained at max level. |
| `windowMillis` | `4000` | Milliseconds a stack survives without a new sword hit. |
| `xpPerStack` | `3` | Skill XP per stack gained. |

### Duelist's Focus

| Property | Value |
|----------|-------|
| Class | `SwordsDuelistsFocus` |
| Icon | `SHIELD` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.68 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-duelists-focus.toml` |

Menu stat lines: Bonus Damage; Damage Reduction.

Listened events:

- `EntityDamageByEntityEvent` (`HIGHEST`, ignore cancelled) - handles both the offensive bonus and the defensive reduction

The engaged count includes `Monster` instances and players inside `engageRadius`, excluding the learner, and both halves require the count to be exactly 1. The defensive half additionally requires a sword in the learner's main hand. The glow is a `GLOWING` potion effect on the attacker, or on a projectile's shooter, clamped to a maximum of 100 ticks and only applied when the target does not already have a longer glow.

Milestones: `challenge_swords_duelist_200` on `swords.duelists-focus.focused-hits` at 200 (reward 400); `challenge_swords_duelist_2500` at 2500 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bonusDamageBase` | `0.10` | Bonus damage as a fraction of base damage, before level scaling. |
| `bonusDamageFactor` | `0.35` | Extra damage fraction gained at max level. |
| `reductionBase` | `0.08` | Incoming damage reduction fraction, before level scaling. |
| `reductionFactor` | `0.30` | Extra reduction fraction gained at max level. |
| `maxReduction` | `0.40` | Hard cap on the reduction fraction, 0-1. |
| `engageRadius` | `7` | Radius in blocks searched for engaged monsters and players. |
| `threatGlowTicks` | `30` | Ticks the current threat glows after it hits you. Clamped to 1-100. |
| `xpPerFocusedHit` | `4` | Skill XP per focused hit. |

### Whetstone Ritual

| Property | Value |
|----------|-------|
| Class | `SwordsWhetstoneRitual` |
| Icon | `GRINDSTONE` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.7 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-whetstone-ritual.toml` |

Menu stat lines: Sharpness Level; Buff Duration.

Listened events:

- `PlayerInteractEvent` (`HIGHEST`, ignore cancelled, also receives cancelled events) - runs the ritual on a sneaking main-hand right-click on a `GRINDSTONE`

The buff is an `ATTACK_DAMAGE` modifier under the `sharp` slot worth `3.0 * (amplifier + 1)` health points, applied for the buff duration. It is not the vanilla Sharpness enchantment and not the Strength potion. If the block interaction is already denied by another plugin, the event is only cancelled to swallow a spam click while the ritual cooldown is running. Running out of XP levels cancels the click and plays a fail effect; a durability cost that would break the sword aborts silently.

Milestones: `challenge_swords_whetstone_100` on `swords.whetstone-ritual.rituals` at 100 (reward 400); `challenge_swords_whetstone_1000` at 1000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `strengthBase` | `0` | Buff amplifier before level scaling. Amplifier 0 is one tier. |
| `strengthFactor` | `2` | Extra amplifier tiers gained at max level. |
| `durationTicksBase` | `200` | Buff duration in ticks, before level scaling. Floored at 40 ticks. |
| `durationTicksFactor` | `400` | Extra buff ticks gained at max level. |
| `durabilityCost` | `15` | Durability taken from the sword per ritual. |
| `xpCost` | `2` | Vanilla experience levels spent per ritual. |
| `cooldownMillis` | `60000` | Minimum milliseconds between rituals. |
| `skillXpOnRitual` | `14` | Skill XP per ritual. |

### Crescent Guard

| Property | Value |
|----------|-------|
| Class | `SwordsCrescentGuard` |
| Icon | `GOLDEN_APPLE` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.66 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-crescent-guard.toml` |

Menu stat lines: Absorption Hearts; Guard Duration.

Listened events:

- `EntityDeathEvent` (`MONITOR`) - grants the guard when the killer holds a sword

Applies `ABSORPTION` at the computed amplifier. An existing Absorption effect is never downgraded: the higher amplifier and the longer duration win, and an infinite effect stays infinite. Absorption points granted are `4 * (amplifier + 1)`, clamped to the player's max absorption attribute, and the player's absorption amount is only raised, never lowered.

Milestones: `challenge_swords_crescent_200` on `swords.crescent-guard.guarded-kills` at 200 (reward 400); `challenge_swords_crescent_2500` at 2500 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `amplifierBase` | `0` | Absorption amplifier, before level scaling. Amplifier 0 grants 4 absorption points, which is 2 hearts. |
| `amplifierFactor` | `2` | Extra amplifier tiers gained at max level. |
| `durationTicksBase` | `120` | Guard duration in ticks, before level scaling. Floored at 20 ticks. |
| `durationTicksFactor` | `180` | Extra guard ticks gained at max level. |
| `xpPerGuard` | `8` | Skill XP per guarded kill. |

### Hamstring

| Property | Value |
|----------|-------|
| Class | `SwordsHamstring` |
| Icon | `LEAD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-hamstring.toml` |

Menu stat lines: Slowness Tier; Slow Duration.

Listened events:

- `EntityDamageByEntityEvent` (`HIGHEST`, ignore cancelled) - applies the slow

A target counts as fleeing when it is a sprinting player, or when its horizontal velocity is at or above `fleeSpeedThreshold`. The slow is a `MOVEMENT_SPEED` modifier under the `slow` slot with a `MULTIPLY_SCALAR_1` value of `-0.15 * (tier + 1)`, clamped to -1, so it is not the Slowness potion effect and does not appear in the effect list. Player targets also have `setSprinting(false)` called on them.

Milestones: `challenge_swords_hamstring_200` on `swords.hamstring.hamstrings` at 200 (reward 400); `challenge_swords_hamstring_2500` at 2500 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `slowTierBase` | `0` | Slow tier, before level scaling. Tier 0 is a 15 percent movement speed cut. |
| `slowTierFactor` | `2` | Extra slow tiers gained at max level. Each tier adds another 15 percent. |
| `durationTicksBase` | `40` | Slow duration in ticks, before level scaling. |
| `durationTicksFactor` | `80` | Extra slow ticks gained at max level. |
| `fleeSpeedThreshold` | `0.14` | Horizontal velocity at or above which a non-sprinting target counts as fleeing. |
| `xpPerHamstring` | `5` | Skill XP per hamstring. |

### Heirloom Edge

| Property | Value |
|----------|-------|
| Class | `SwordsHeirloomEdge` |
| Icon | `NETHERITE_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 6 |
| Cost factor | 0.72 |
| Config file | `plugins/Adapt/adapt/adaptations/sword-heirloom-edge.toml` |

Menu stat lines: Damage Per Bank; Kills Per Bank; Banked Damage Cap.

Listened events:

- `PrepareAnvilEvent` (`HIGH`) - stamps the renamed sword as an heirloom
- `EntityDeathEvent` (`MONITOR`) - banks kill progress onto the held heirloom

Stamping requires a non-blank rename text and a sword result. The item carries five persistent keys: `heirloom_edge` (the flag), `heirloom_edge_kills`, `heirloom_edge_bonus`, `heirloom_edge_damage` (the attribute modifier key), and `heirloom_edge_lore`. The banked bonus is an `ATTACK_DAMAGE` `ADD_NUMBER` modifier on the mainhand slot of the item itself, so it travels with the sword rather than with the player. When the item has no explicit attribute modifiers yet, the material's vanilla defaults are copied on first so the heirloom bonus adds to them instead of replacing them. Once the bonus reaches the cap, banked kills stop accumulating and the kill counter parks at the per-bank threshold.

Milestones: `challenge_swords_heirloom_10` on `swords.heirloom-edge.banks` at 10 (reward 400); `challenge_swords_heirloom_100` at 100 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `growthBase` | `0.15` | Attack damage added per bank, before level scaling. |
| `growthFactor` | `0.6` | Extra damage per bank gained at max level. |
| `capBase` | `1.0` | Ceiling on the total banked attack damage, before level scaling. |
| `capFactor` | `4.0` | Extra cap gained at max level. |
| `killsPerBank` | `5` | Kills with the heirloom in hand required to bank one growth step. Minimum 1. |
| `xpPerBank` | `12` | Skill XP per banked step. |

### Support classes (not player adaptations)

- `DamagingBleedEffect` runs the bleed visual and applies each scheduled damage pulse on the target entity's owning thread. It backs the Poisoned Blade fallback for poison-immune mobs and the Crimson Cyclone bleed.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
