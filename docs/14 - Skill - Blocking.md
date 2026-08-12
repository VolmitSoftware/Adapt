# Skill: Blocking

Blocking is the shield skill. You level it by taking hits with your shield up, and it pays out whether the hit was an arrow, a sword, or a creeper. If you play the tank in a group, or you just get shot at a lot, this is your tree.

The defensive adaptations turn a raised shield from a flat damage sponge into something with timing and positioning behind it. Perfect Guard rewards raising the shield at the last instant with a full negate and a stagger. Bastion Stance rewards planting your feet. Mirror Block throws arrows back where they came from. Counter Guard builds up charges while you eat hits and spends them to hurt whoever is hitting you. Tempered Guard quietly patches your gear while you soak.

There is a support side too. Shield Wall shelters allies standing behind you from projectiles, Interpose lets you eat part of a hurt ally's damage on your own shield, and Shieldbearer's Resolve gets your shield back fast when an axe knocks it out of your hands.

The rest are crafting utilities that came along for the ride: chainmail armor, saddles, horse armor, an alternate shield recipe with a netherite upgrade, and Multi-Armor, which merges an elytra into your chestplate so it swaps itself when you jump off a cliff.

## Earning XP

Every hit you take while blocking pays a flat XP award, on a shared cooldown so a burst of arrows does not pay ten times. The same event records four stats: total blocked hits, total blocked damage, whether the hit was a projectile or melee, and whether it was a heavy hit, which means more than 5 damage in one blow.

There is a second, off-by-default source. If you set `passiveXpForUsingShield` above zero, you also earn a trickle every skill tick just for having a shield in either hand. It is scaled by how much time actually elapsed, and it is awarded silently, so it never spams your screen.

## Adaptations

All of these need the same things before they do anything: the adaptation learned at level 1 or higher, the Blocking skill and the adaptation both enabled, the player holding the matching `adapt.use.` permission (or the `adapt.use.*` wildcard), and any protection or region plugin on the server allowing the action. Anything that hurts another entity also runs the normal PvP and PvE checks first.

### Multi-Armor (`blocking-multiarmor`)

Merge an elytra into a chestplate and get one item that switches between the two by itself. On the ground it is your chestplate. Jump off something and once you have fallen more than four blocks it becomes an elytra. It is a travel adaptation more than a combat one.

How to use it:

1. Learn Multi-Armor.
2. Open your inventory, pick up the elytra on your cursor, and left-click it onto the chestplate (or the other way round). One of the two has to be an elytra.
3. Wear the merged item. It swaps itself as you move.
4. To take it apart, sneak and drop it. The parts come back out with their names, enchantments and damage intact.

Swaps are throttled so it does not flicker. The merged item keeps a MultiArmor lore tag, which is how Adapt recognizes it. Destroying the merged item destroys everything inside it.

### Chains of Mephistopheles (`blocking-chainarmorer`)

Adds the four chainmail armor recipes, which vanilla does not give you. The shapes are the normal armor shapes, made from iron nuggets instead of ingots.

How to use it:

1. Learn Chains of Mephistopheles.
2. Lay iron nuggets out in a crafting table in the usual helmet, chestplate, leggings or boots shape.

This one ships with `permanent` set to `true`, so learning it is one-way.

### Craftable Saddle (`blocking-saddlecrafter`)

Adds a saddle recipe so you are not waiting on a dungeon chest or a fishing rod. Five leather in an upside-down U.

How to use it:

1. Learn Craftable Saddle.
2. Place leather in a crafting table as two in the top corners and three across the middle row.

Also `permanent` by default.

### Craftable Horse Armor (`blocking-horsearmorer`)

Adds leather, iron, gold and diamond horse armor recipes. Surround a saddle with eight of whichever material you want.

How to use it:

1. Learn Craftable Horse Armor.
2. Put a saddle in the center of a crafting table.
3. Fill the other eight slots with leather, iron ingots, gold ingots or diamonds.

Also `permanent` by default.

### Counter Guard (`blocking-counter-guard`)

Every hit you block while holding a shield adds a counter stack, up to a cap. Each incoming hit then rolls a chance to spend a stack and slam damage back into whoever hit you. Reflect damage scales with how many stacks you are sitting on, so a long defensive fight hits harder than a single block. Projectile attacks reflect onto the shooter, not the arrow.

Works on its own once learned. Keep your shield up and stacks build themselves.

### Bastion Stance (`blocking-bastion-stance`)

Sneak while actively blocking with a shield and you plant yourself. You gain knockback resistance and explosion knockback resistance while the stance holds, incoming projectile damage is cut, and each projectile also rolls a chance to be blocked outright for zero damage.

How to use it:

1. Learn Bastion Stance.
2. Hold a shield in either hand and raise it.
3. Hold sneak.

The stance re-checks itself on a short timer and drops the moment you stop sneaking, stop blocking, lose the shield, or leave survival or adventure mode.

### Mirror Block (`blocking-mirror-block`)

While you are blocking with a shield, an incoming projectile can be sent back at whoever fired it instead of hitting you. The reflected shot carries a fraction of the original damage and flies at a fraction of the original speed. Each reflect starts a cooldown, and a projectile that was already reflected cannot be reflected again.

Works on its own once learned. Just block projectiles.

### Bulwark Bash (`blocking-bulwark-bash`)

Sprint, jump, and hit something on the way down while a shield is in your off hand. The target takes bonus damage and gets thrown back, and everything else in range is knocked away and slowed. It is the shield player's opener and gap-closer at the same time.

How to use it:

1. Learn Bulwark Bash.
2. Put a shield in your off hand, and make sure it is not on cooldown.
3. Sprint.
4. Jump.
5. Hit an enemy while you are still falling.

Only the entity you actually hit takes the extra damage; the rest of the shockwave is knockback and stun. Each bash puts your shield on cooldown.

### Shield Wall (`blocking-shield-wall`)

Stand in front of your team with your shield up and facing the incoming fire, and projectiles that hit players behind you land softer. Adapt looks at every blocking player near the target, keeps only the ones inside range, inside the protection arc, and actually facing into the shot, and applies the strongest reduction it finds.

How to use it:

1. Learn Shield Wall.
2. Raise a shield.
3. Stand between your allies and whatever is shooting at them, facing the shooter.

Only players are shielded this way. The XP goes to the blocker, not the ally.

### Perfect Guard (`blocking-perfect-guard`)

Raise your shield in the last fraction of a second before a hit lands and the hit is cancelled outright, and the attacker eats a stagger. It is a parry, not a block, and it takes real timing: the window is short and there is a cooldown between successful guards.

How to use it:

1. Learn Perfect Guard.
2. Hold a shield.
3. Right-click to raise it just as the attack is about to land.
4. Face the attacker. Perfect Guard checks that the hit came from in front of you.

It negates melee and projectiles alike. If the source is a living attacker you are allowed to hurt, they get slowed and shoved back.

### Tempered Guard (`blocking-tempered-guard`)

Every hit you block with a shield rolls a chance to repair gear. It tries the shield first, then the first damaged armor piece it finds. It will not carry you through a long fight, but over a session it noticeably slows how fast your kit wears out.

Works on its own once learned.

### Shieldbearer's Resolve (`blocking-shieldbearers-resolve`)

Getting your shield axed is normally a death sentence. With this, the moment an axe disables your shield you get Resistance and the shield cooldown is cut down, so you are back behind cover much sooner.

Works on its own once learned. It only fires when the attacker was actually swinging an axe and your shield really went on cooldown.

### Phalanx Crafter (`blocking-phalanx-crafter`)

Two levels, two recipes. Level 1 adds an alternate shield recipe built from white wool, oak planks and one iron ingot. Level 2 lets you wrap an existing shield in netherite for a shield with far more durability and a gold name.

How to use it:

1. Learn Phalanx Crafter.
2. For the shield, place three white wool across the top, oak planks either side of an iron ingot in the middle row, and one oak plank below the center.
3. For the netherite version, reach level 2, then put a shield in the center of a crafting table with a netherite ingot above, below, left and right of it.

Crafting the netherite recipe below level 2 is cancelled with a deny sound. The level 1 recipe produces a plain shield, not a banner-faced one, despite what the menu lore says.

### Interpose (`blocking-interpose`)

Sneak-block near a hurt ally and part of the damage they take gets pulled onto your shield instead. It only kicks in once the ally is below the low-health threshold, so it saves people who are actually about to die rather than leaking your durability all fight.

How to use it:

1. Learn Interpose.
2. Hold a shield and raise it.
3. Hold sneak.
4. Stay within range of the ally.

The redirected damage does not hit your health. It costs your shield durability and adds exhaustion, so you get hungry doing it. If several blockers qualify, the closest one takes the hit.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `blocking` |
| Class | `SkillBlocking` |
| Icon | `SHIELD` |
| Color | `DARK_GRAY` |
| Interval (ms) | `5000` |
| Skill config | `plugins/Adapt/adapt/skills/blocking.toml` |
| Adaptation count | 14 |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/blocking.toml` on first load.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `enabled` | `true` | Turns the whole Blocking skill off when false. |
| `skillColor` | `"&8"` | Legacy ampersand color code used for this skill in menus and text. |
| `xpOnBlockedAttack` | `25` | Flat XP paid for each hit taken while blocking, subject to the cooldown below. |
| `challengeBlock1kReward` | `500` | XP paid by the smaller tier of each Blocking milestone pair, and by `challenge_block_5k`. |
| `challengeBlock5kReward` | `2000` | XP paid by the larger tier of each Blocking milestone pair. |
| `cooldownDelay` | `1500` | Milliseconds between blocked-hit XP awards. |
| `passiveXpForUsingShield` | `0` | XP per skill interval just for holding a shield in either hand. 0 turns it off. Awarded silently under the `blocking:shield-hold` source tag. |

### Milestones and stat keys

| Milestone key | Stat key | Threshold |
|---------------|----------|-----------|
| `challenge_block_1k` | `blocked.hits` | 1000 |
| `challenge_block_5k` | `blocked.hits` | 5000 |
| `challenge_block_50k` | `blocked.hits` | 50000 |
| `challenge_block_dmg_1k` | `blocked.damage` | 1000 |
| `challenge_block_dmg_10k` | `blocked.damage` | 10000 |
| `challenge_block_proj_100` | `blocked.projectiles` | 100 |
| `challenge_block_proj_1k` | `blocked.projectiles` | 1000 |
| `challenge_block_melee_500` | `blocked.melee` | 500 |
| `challenge_block_melee_5k` | `blocked.melee` | 5000 |
| `challenge_block_heavy_50` | `blocked.heavy` | 50 |
| `challenge_block_heavy_500` | `blocked.heavy` | 500 |
| `challenge_blocking_multi_200` | `blocking.multi-armor.swaps` | 200 |
| `challenge_blocking_multi_5k` | `blocking.multi-armor.swaps` | 5000 |
| `challenge_blocking_chain_25` | `blocking.chain-armorer.pieces-crafted` | 25 |
| `challenge_blocking_saddle_25` | `blocking.saddlecrafter.saddles-crafted` | 25 |
| `challenge_blocking_horse_armor_10` | `blocking.horse-armorer.armor-crafted` | 10 |
| `challenge_blocking_counter_500` | `blocking.counter-guard.damage-reflected` | 500 |
| `challenge_blocking_bastion_500` | `blocking.bastion-stance.projectiles-softened` | 500 |
| `challenge_blocking_mirror_100` | `blocking.mirror-block.projectiles-reflected` | 100 |
| `challenge_blocking_bulwark_500` | `blocking.bulwark-bash.mobs-bashed` | 500 |
| `challenge_blocking_shieldwall_500` | `blocking.shield-wall.damage-shielded` | 500 |
| `challenge_blocking_shieldwall_5k` | `blocking.shield-wall.damage-shielded` | 5000 |
| `challenge_blocking_perfect_100` | `blocking.perfect-guard.hits-negated` | 100 |
| `challenge_blocking_perfect_1k` | `blocking.perfect-guard.hits-negated` | 1000 |
| `challenge_blocking_tempered_500` | `blocking.tempered-guard.durability-repaired` | 500 |
| `challenge_blocking_tempered_5k` | `blocking.tempered-guard.durability-repaired` | 5000 |
| `challenge_blocking_resolve_100` | `blocking.shieldbearers-resolve.recoveries` | 100 |
| `challenge_blocking_resolve_1k` | `blocking.shieldbearers-resolve.recoveries` | 1000 |
| `challenge_blocking_phalanx_25` | `blocking.phalanx-crafter.items-crafted` | 25 |
| `challenge_blocking_interpose_250` | `blocking.interpose.damage-redirected` | 250 |
| `challenge_blocking_interpose_2k` | `blocking.interpose.damage-redirected` | 2000 |

Five advancements are granted directly instead of by a stat threshold: `challenge_blocking_bastion_10` after ten projectiles in one stance session, `challenge_blocking_counter_max` on a Counter Guard reflect fired at full stacks, `challenge_blocking_mirror_3in5` on three Mirror Block reflects inside one window, `challenge_blocking_bulwark_4` when one bash affects four or more targets, and `challenge_blocking_phalanx_netherite` on the first netherite shield crafted.

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

The tick interval below is the adaptation's background tick rate. Only Bastion Stance does work on that tick, clearing stale stance sessions; for every other Blocking adaptation the interval is idle bookkeeping.

### Multi-Armor

| Property | Value |
|----------|-------|
| Class | `BlockingMultiArmor` |
| Icon | `ELYTRA` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 1 |
| Cost factor | 1 |
| Tick interval (ms) | 20202 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-multiarmor.toml` |

Listened events: `PlayerMoveEvent` (auto swap), `PlayerDropItemEvent` (sneak-drop to unbind), `InventoryClickEvent` (left-click merge).

Swap cooldown is a hard-coded 3000 ms. The elytra form takes over once fall distance passes 4 blocks.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `startingSlots` | `1` | Items that can be merged into one MultiArmor before level is added. The cap is this plus your level. |

### Chains of Mephistopheles

| Property | Value |
|----------|-------|
| Class | `BlockingChainArmorer` |
| Icon | `CHAINMAIL_CHESTPLATE` |
| Max level | 1 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 1 |
| Cost factor | 0 |
| Tick interval (ms) | 17774 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-chainarmorer.toml` |

Listened events: `CraftItemEvent`.

Recipes: `blocking-chainarmorer-helmet`, `blocking-chainarmorer-chestplate`, `blocking-chainarmorer-leggings`, `blocking-chainarmorer-boots`, all from `IRON_NUGGET`. `permanent` defaults to `true`. No adaptation-specific config keys.

### Craftable Saddle

| Property | Value |
|----------|-------|
| Class | `BlockingSaddlecrafter` |
| Icon | `LEATHER_HORSE_ARMOR` |
| Max level | 1 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 5 |
| Cost factor | 0 |
| Tick interval (ms) | 17774 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-saddlecrafter.toml` |

Listened events: `CraftItemEvent`.

Recipe `blocking-saddlecrafter`: five `LEATHER` shaped as `I I` over `III`. `permanent` defaults to `true`. No adaptation-specific config keys.

### Craftable Horse Armor

| Property | Value |
|----------|-------|
| Class | `BlockingHorseArmorer` |
| Icon | `GOLDEN_HORSE_ARMOR` |
| Max level | 1 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 5 |
| Cost factor | 0 |
| Tick interval (ms) | 17774 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-horsearmorer.toml` |

Listened events: `CraftItemEvent`.

Recipes `blocking-horsearmorerleather`, `blocking-horsearmoreriron`, `blocking-horsearmorergold` and `blocking-horsearmorerdiamond`: a `SADDLE` in the center ringed by eight of `LEATHER`, `IRON_INGOT`, `GOLD_INGOT` or `DIAMOND`. `permanent` defaults to `true`. No adaptation-specific config keys.

### Counter Guard

| Property | Value |
|----------|-------|
| Class | `BlockingCounterGuard` |
| Icon | `IRON_BARS` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.75 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-counter-guard.toml` |

Listened events: `EntityDamageByEntityEvent`.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `baseStacks` | `2` | Stack cap at level 1. |
| `stackFactor` | `8` | Extra stack cap added across the full level range. |
| `reflectChanceBase` | `0.08` | Chance per incoming hit to reflect at level 1, 0 to 1. |
| `reflectChanceFactor` | `0.27` | Extra reflect chance added across the full level range. |
| `maxReflectChance` | `0.6` | Ceiling on reflect chance no matter the level. |
| `baseReflectDamage` | `1` | Health points reflected at level 1 before the per-stack bonus. |
| `reflectDamageFactor` | `3.5` | Extra reflected health points added across the full level range. |
| `damagePerStack` | `0.28` | Extra reflected health points per stack you currently hold. |
| `stackCostOnReflect` | `1` | Stacks spent per reflect. |
| `xpPerReflectedDamage` | `5.0` | Skill XP per health point reflected. |

### Bastion Stance

| Property | Value |
|----------|-------|
| Class | `BlockingBastionStance` |
| Icon | `SHIELD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.68 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-bastion-stance.toml` |

Listened events: `EntityDamageByEntityEvent` (projectile softening), `PlayerVelocityEvent` (impact effect only), `PlayerToggleSneakEvent`, `PlayerMoveEvent` and `PlayerGameModeChangeEvent` (stance start and stop).

Knockback resistance is applied as `KNOCKBACK_RESISTANCE` and `EXPLOSION_KNOCKBACK_RESISTANCE` attribute modifiers while the stance is held.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `knockbackReductionBase` | `0.18` | Knockback resistance at level 1, 0 to 1. |
| `knockbackReductionFactor` | `0.52` | Extra knockback resistance added across the full level range. |
| `maxKnockbackReduction` | `0.75` | Ceiling on knockback resistance. |
| `projectileReductionBase` | `0.12` | Fraction of projectile damage removed at level 1. |
| `projectileReductionFactor` | `0.5` | Extra projectile damage reduction added across the full level range. |
| `maxProjectileReduction` | `0.7` | Ceiling on projectile damage reduction. |
| `projectileNegateChanceBase` | `0.05` | Chance to cancel a projectile hit outright at level 1, 0 to 1. |
| `projectileNegateChanceFactor` | `0.22` | Extra negate chance added across the full level range. |
| `maxProjectileNegateChance` | `0.35` | Ceiling on negate chance. |
| `xpPerMitigatedDamage` | `2.5` | Skill XP per health point of projectile damage removed. |
| `xpOnNegate` | `8.0` | Skill XP for a full projectile negate. |

### Mirror Block

| Property | Value |
|----------|-------|
| Class | `BlockingMirrorBlock` |
| Icon | `LIGHT_WEIGHTED_PRESSURE_PLATE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1200 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-mirror-block.toml` |

Listened events: `EntityDamageByEntityEvent`.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `reflectChanceBase` | `0.1` | Chance to reflect an incoming projectile at level 1, 0 to 1. |
| `reflectChanceFactor` | `0.35` | Extra reflect chance added across the full level range. |
| `maxReflectChance` | `0.7` | Ceiling on reflect chance. |
| `reflectedDamageFactorBase` | `0.45` | Fraction of the original damage the reflected shot carries at level 1. |
| `reflectedDamageFactorIncrease` | `0.35` | Extra damage fraction added across the full level range. |
| `maxReflectedDamageFactor` | `0.95` | Ceiling on reflected damage fraction. |
| `reflectVelocityFactorBase` | `0.42` | Fraction of the original speed the reflected shot flies at, at level 1. |
| `reflectVelocityFactor` | `0.45` | Extra speed fraction added across the full level range. |
| `maxReflectVelocityFactor` | `1.1` | Ceiling on reflected speed fraction. |
| `cooldownMillisBase` | `2000` | Milliseconds between reflects at level 1. |
| `cooldownMillisFactor` | `1200` | Milliseconds of that cooldown removed at max level. |
| `minReflectedVelocitySquared` | `0.08` | Squared speed below which the incoming shot is too slow to bounce meaningfully. |
| `fallbackReflectedSpeed` | `0.95` | Speed given to a reflected shot when the incoming velocity was under that threshold. |
| `xpOnReflect` | `8` | Skill XP per projectile reflected. |

### Bulwark Bash

| Property | Value |
|----------|-------|
| Class | `BlockingBulwarkBash` |
| Icon | `BELL` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-bulwark-bash.toml` |

Listened events: `PlayerToggleSprintEvent` (records the sprint timestamp), `EntityDamageByEntityEvent` (fires the bash).

| Key | Code default | What it does |
|-----|--------------|--------------|
| `baseDamage` | `1.0` | Health points added to the primary hit before the level bonus. |
| `damageBonusBase` | `0.3` | Extra health points on the primary hit at level 1. |
| `damageBonusFactor` | `2.2` | Extra health points added across the full level range. |
| `rangeBase` | `2.4` | Shockwave radius in blocks at level 1. |
| `rangeFactor` | `1.8` | Extra radius in blocks added across the full level range. |
| `knockbackBase` | `0.6` | Horizontal launch strength at level 1. |
| `knockbackFactor` | `0.6` | Extra horizontal launch added across the full level range. |
| `upwardKnockbackBase` | `0.18` | Vertical launch strength at level 1. |
| `upwardKnockbackFactor` | `0.14` | Extra vertical launch added across the full level range. |
| `stunTicksBase` | `18` | Slowness duration in ticks at level 1. Never less than 10. |
| `stunTicksFactor` | `24` | Extra slowness ticks added across the full level range. |
| `stunAmplifierBase` | `2` | Slowness amplifier at level 1. |
| `stunAmplifierFactor` | `1` | Extra amplifier added across the full level range. |
| `cooldownTicksBase` | `220` | Shield cooldown in ticks applied at level 1. Never less than 20. |
| `cooldownTicksFactor` | `120` | Ticks of that cooldown removed at max level. |
| `minFallDistanceForCrit` | `0.08` | Blocks you must have fallen for the hit to count as a jump crit. |
| `recentSprintWindowMillis` | `900` | How long after you stop sprinting the bash still counts you as sprinting. |
| `xpPerTargetHit` | `8` | Skill XP per target affected by the bash. |
| `maxCandidatesPerActivation` | `16` | Nearby living entities inspected per bash. |
| `maxAffectedPerActivation` | `12` | Targets actually affected per bash, primary included. |
| `maxTargetFxPerActivation` | `6` | Affected targets that get their own impact particles. |

### Shield Wall

| Property | Value |
|----------|-------|
| Class | `BlockingShieldWall` |
| Icon | `SHIELD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.65 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-shield-wall.toml` |

Listened events: `EntityDamageByEntityEvent`. Only fires when the entity taking the projectile is a player.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `rangeBase` | `3.0` | Blocks between blocker and ally allowed at level 1. |
| `rangeFactor` | `4.0` | Extra range in blocks added across the full level range. |
| `arcDegreesBase` | `60` | Width of the protection cone in degrees at level 1. |
| `arcDegreesFactor` | `90` | Extra cone degrees added across the full level range. |
| `damageReductionBase` | `0.18` | Fraction of the projectile's damage removed at level 1. |
| `damageReductionFactor` | `0.5` | Extra damage reduction added across the full level range. |
| `maxDamageReduction` | `0.6` | Ceiling on damage reduction. |
| `minFacingAlignment` | `0.1` | How squarely the blocker must face into the incoming shot to count. |
| `xpPerDamageShielded` | `3.0` | Skill XP per health point of damage taken off the ally. |

### Perfect Guard

| Property | Value |
|----------|-------|
| Class | `BlockingPerfectGuard` |
| Icon | `SHIELD` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.78 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-perfect-guard.toml` |

Listened events: `PlayerInteractEvent` (records the shield raise), `EntityDamageByEntityEvent` (negates and staggers).

| Key | Code default | What it does |
|-----|--------------|--------------|
| `windowMillisBase` | `120` | Milliseconds after raising the shield in which a hit is parried, at level 1. |
| `windowMillisFactor` | `240` | Extra window milliseconds added across the full level range. |
| `staggerTicksBase` | `20` | Slowness duration in ticks put on the attacker at level 1. |
| `staggerTicksFactor` | `40` | Extra stagger ticks added across the full level range. |
| `staggerAmplifierBase` | `1` | Slowness amplifier at level 1. |
| `staggerAmplifierFactor` | `2` | Extra amplifier added across the full level range. |
| `staggerKnockback` | `0.55` | Shove strength applied to the staggered attacker. |
| `minFacingAlignment` | `0.15` | How squarely you must face the attacker for the parry to count. |
| `cooldownMillis` | `1500` | Milliseconds between successful parries. |
| `xpOnNegate` | `14` | Skill XP per hit negated. |

### Tempered Guard

| Property | Value |
|----------|-------|
| Class | `BlockingTemperedGuard` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-tempered-guard.toml` |

Listened events: `EntityDamageByEntityEvent`.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `repairChanceBase` | `0.15` | Chance per blocked hit to repair something at level 1, 0 to 1. |
| `repairChanceFactor` | `0.4` | Extra repair chance added across the full level range. |
| `maxRepairChance` | `0.55` | Ceiling on repair chance. |
| `repairAmountBase` | `2` | Durability points restored per proc at level 1. |
| `repairAmountFactor` | `6` | Extra durability points added across the full level range. |
| `xpPerDurabilityRepaired` | `2.0` | Skill XP per durability point restored. |

### Shieldbearer's Resolve

| Property | Value |
|----------|-------|
| Class | `BlockingShieldbearersResolve` |
| Icon | `NETHERITE_AXE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-shieldbearers-resolve.toml` |

Listened events: `EntityDamageByEntityEvent`. The check runs one tick later so the shield cooldown has actually been set.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `recoverySpeedBase` | `0.2` | Fraction of the remaining shield cooldown removed at level 1. |
| `recoverySpeedFactor` | `0.45` | Extra fraction removed across the full level range. |
| `maxRecoverySpeed` | `0.7` | Ceiling on how much of the cooldown can be removed. |
| `resistanceAmplifierBase` | `0` | Resistance amplifier granted at level 1. |
| `resistanceAmplifierFactor` | `2.2` | Extra amplifier added across the full level range. |
| `minResistanceTicks` | `40` | Shortest Resistance duration in ticks, whatever the cooldown was. |
| `minCooldownTicks` | `20` | Shortest shield cooldown the recovery can leave behind. |
| `reprocessGuardMillis` | `500` | Milliseconds before another disable can be processed, so one axe hit does not fire twice. |
| `xpOnResolve` | `12` | Skill XP per recovery. |

### Phalanx Crafter

| Property | Value |
|----------|-------|
| Class | `BlockingPhalanxCrafter` |
| Icon | `SHIELD` |
| Max level | 2 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 0 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-phalanx-crafter.toml` |

Listened events: `CraftItemEvent`.

Recipes: `blocking-phalanx-field-shield` (`WHITE_WOOL` x3 on top, `OAK_PLANKS` / `IRON_INGOT` / `OAK_PLANKS` in the middle, one `OAK_PLANKS` below center, gives a plain `SHIELD`) and `blocking-phalanx-netherite-shield` (four `NETHERITE_INGOT` around a `SHIELD`, level 2 only, gives a shield with max durability 1200 named "Netherite-Reinforced Shield"). No adaptation-specific config keys.

### Interpose

| Property | Value |
|----------|-------|
| Class | `BlockingInterpose` |
| Icon | `SHIELD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/blocking-interpose.toml` |

Listened events: `EntityDamageEvent`. Only fires when the entity taking damage is a player.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `redirectShareBase` | `0.22` | Fraction of the ally's damage pulled onto your shield at level 1. |
| `redirectShareFactor` | `0.4` | Extra redirect fraction added across the full level range. |
| `maxRedirectShare` | `0.6` | Ceiling on the redirect fraction. |
| `rangeBase` | `3.5` | Blocks between you and the ally allowed at level 1. |
| `rangeFactor` | `4.5` | Extra range in blocks added across the full level range. |
| `lowHealthThreshold` | `0.4` | Fraction of max health the ally must be at or below before Interpose fires. |
| `durabilityPerDamage` | `1.0` | Shield durability spent per health point redirected, rounded up, minimum 1. |
| `exhaustionPerRedirect` | `1.0` | Exhaustion added to you per redirect. |
| `xpPerDamageRedirected` | `3.0` | Skill XP per health point redirected. |

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
