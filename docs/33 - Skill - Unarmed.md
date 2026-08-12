# Skill: Unarmed

Unarmed is the bare-hands skill. You level it by fighting without a weapon, and the twelve adaptations turn punching from a joke into a real build: flat damage on every hit, sprint charges that launch mobs, combos that snowball, disarms that leave a skeleton holding nothing, and a clap that shoves a whole cone of enemies away.

The skill counts a hit as unarmed when your main hand is not a melee tool. Axes, pickaxes, hoes, shovels, swords, tridents, spears, and maces all disable it, but a torch, a block, or an empty fist all count. Individual adaptations are stricter: most also check your off hand, and Sucker Punch and Meditation want your hands genuinely empty.

Two of the adaptations reward going without armor as well. Glass Cannon multiplies your punches when you are wearing nothing at all, and Meditation slowly builds absorption hearts while you sit still, which is the closest thing this skill has to armor.

If you are starting out, Iron Fists and Unarmed Power are the plain damage picks. Combo Chain and Battering Charge are where it gets fun.

## How you earn Unarmed XP

Every hit you land with a non-melee main hand counts. The skill adds 1 to `unarmed.hits` and the raw damage to `unarmed.damage`, then pays `damageXPMultiplier` times that damage, subject to a `cooldownDelay` cooldown between payouts.

Two extra counters track style. A hit landed while falling (fall distance above zero and not on the ground) adds to `unarmed.critical`, and any hit above 6 damage adds to `unarmed.heavy`. Killing anything while not holding a melee tool adds to `unarmed.kills`, and killing a boss that way plays a small celebration.

Nothing is credited when the victim is already dead or invulnerable, or when you are invulnerable.

## Adaptations

Everything below needs the same four things before it does anything: the adaptation learned at level 1 or higher, the Unarmed skill and that adaptation both enabled in config, the `adapt.use` permission for it, and any protection or region plugin allowing the action on that target. Learn adaptations from the Adapt menu (`/adapt`), under Unarmed.

Where an adaptation is described as needing "bare hands", the code checks that neither hand holds a melee tool. Blocks and other junk items are fine.

### Sucker Punch (`unarmed-sucker-punch`)

A sprinting punch with a truly empty main hand multiplies your damage. Killing a full-health target in one such punch counts toward a knockout milestone, complete with a flash and a shockwave ring.

How to use it:

1. Learn Sucker Punch in the Adapt menu.
2. Empty your main hand completely. Any item at all disables it.
3. Sprint.
4. Punch.

### Unarmed Power (`unarmed-power`)

A flat percentage boost to your attack damage while neither hand holds a tool. The bonus is a timed attribute modifier that is reapplied whenever your hands change, so it comes and goes as you swap items. Nothing to press.

### Glass Cannon (`unarmed-glass-cannon`)

Punching hurts far more when you are naked. With zero armor equipped your damage is multiplied several times over. Wearing armor scales that bonus down toward nothing, and the result never drops below your normal damage, so it is purely upside with a large reward for going without.

### Battering Charge (`unarmed-battering-charge`)

Sprint into something and the hit lands as an impact: extra damage plus a shove in the direction you are looking. A shield in either hand works as well as bare fists, and while the charge is primed you leave a small dust trail so you can see it is ready.

How to use it:

1. Learn Battering Charge in the Adapt menu.
2. Have both hands empty, or hold a shield in either hand.
3. Sprint, and keep actually moving. Standing still while the sprint flag is on does not count.
4. Hit something.

Using a shield puts the cooldown on the shield itself as a visible item cooldown. With fists it is an internal cooldown instead. Riding anything disables the charge.

### Combo Chain (`unarmed-combo-chain`)

Consecutive punches stack up, and each stack adds damage to the next hit. Stacks reset if you go too long between hits, and swinging at nothing after the grace window drops the whole chain with a low note. Bigger chains have their own advancements at 10 and 25 stacks.

### Disarm (`unarmed-disarm`)

Bare-hand hits can knock a target's held item to the ground. It takes the main-hand item, or an off-hand shield if the main hand is empty. Mobs can also lose one worn armor piece on the same disarm. The dropped item gets a pickup delay so the victim cannot instantly snatch it back, and each target has its own cooldown to stop chain-disarming. Skeletal servants are never disarmed.

### Pressure Point (`unarmed-pressure-point`)

Bare-hand hits apply Slowness that stacks up one amplifier at a time toward a level-based cap. Past a certain level, Weakness stacks on the same way. Good for softening something you cannot outrun.

### Shockwave Clap (`unarmed-shockwave-clap`)

Clap your hands and everything in a cone in front of you gets thrown backward and upward. No damage, just displacement, which makes it an escape tool and a way to break up a pile of mobs.

How to use it:

1. Learn Shockwave Clap in the Adapt menu.
2. Keep both hands free of tools.
3. Sneak.
4. Left-click the air or a block.

Each clap costs hunger and fails with a dull cue if you are on cooldown or too hungry. Your own pets are never thrown.

### Iron Fists (`unarmed-iron-fists`)

Flat extra damage on every bare-hand hit, and punching soft blocks (dirt, sand, leaves, anything under the hardness threshold) gives you a short mining-speed buff. Nothing to activate.

### Grapple (`unarmed-grapple`)

Grab something with a sneak-punch, then throw it where you are looking. Works on players too, subject to PVP policy, but bosses cannot be grabbed.

How to use it:

1. Learn Grapple in the Adapt menu.
2. Keep both hands free of tools.
3. Sneak and punch a target. A line of particles connects you to it.
4. Punch again, or release sneak, to hurl it.

The grab expires on its own after a few seconds. Each throw adds exhaustion, so grappling a crowd will make you hungry, and the target must still be within throwing range when the hurl resolves.

### Second Wind (`unarmed-second-wind`)

Killing a mob bare-handed gives back some hunger and saturation and starts a short regeneration burst. It has its own cooldown so a fast kill chain does not turn into infinite food.

### Meditation (`unarmed-meditation`)

Sit still and build absorption hearts. Slow, but it stacks up to a real buffer over a minute or two of downtime, and it pairs well with Glass Cannon since absorption is not armor.

How to use it:

1. Learn Meditation in the Adapt menu.
2. Empty both hands completely.
3. Stay out of combat for the lockout window.
4. Sneak and stand still. Absorption ticks up once per second until you hit the cap.

Moving, unsneaking, picking anything up, or taking or dealing a hit ends the session immediately.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `unarmed` |
| Class | `SkillUnarmed` |
| Icon | `FIRE_CHARGE` |
| Color | `YELLOW` |
| Interval (ms) | `2579` |
| Skill config | `plugins/Adapt/adapt/skills/unarmed.toml` |
| Adaptation count | 12 |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/unarmed.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Turns the whole Unarmed skill on or off. |
| `skillColor` | `"&e"` | Legacy ampersand color code used for Unarmed in menus and text. |
| `damageXPMultiplier` | `4.5` | Skill XP per point of damage you deal without a melee tool. |
| `cooldownDelay` | `1250` | Milliseconds between XP awards for unarmed damage. |
| `challengeUnarmedReward` | `500` | Knowledge paid by the hit-count challenges. |
| `challengeUnarmedDmgReward` | `500` | Knowledge paid by the damage challenges. |
| `challengeUnarmedKillsReward` | `750` | Knowledge paid by the kill challenges. |
| `challengeUnarmedCritReward` | `750` | Knowledge paid by the falling-hit challenges. |
| `challengeUnarmedHeavyReward` | `750` | Knowledge paid by the heavy-hit challenges. |

### Skill milestones

| Advancement key | Stat key | Threshold | Reward |
|-----------------|----------|-----------|--------|
| `challenge_unarmed_100` | `unarmed.hits` | 100 | `challengeUnarmedReward` |
| `challenge_unarmed_1k` | `unarmed.hits` | 1000 | `challengeUnarmedReward` x 2 |
| `challenge_unarmed_10k` | `unarmed.hits` | 10000 | `challengeUnarmedReward` x 5 |
| `challenge_unarmed_dmg_1k` | `unarmed.damage` | 1000 | `challengeUnarmedDmgReward` |
| `challenge_unarmed_dmg_10k` | `unarmed.damage` | 10000 | `challengeUnarmedDmgReward` x 3 |
| `challenge_unarmed_kills_25` | `unarmed.kills` | 25 | `challengeUnarmedKillsReward` |
| `challenge_unarmed_kills_250` | `unarmed.kills` | 250 | `challengeUnarmedKillsReward` x 3 |
| `challenge_unarmed_crit_25` | `unarmed.critical` | 25 | `challengeUnarmedCritReward` |
| `challenge_unarmed_crit_250` | `unarmed.critical` | 250 | `challengeUnarmedCritReward` x 3 |
| `challenge_unarmed_heavy_25` | `unarmed.heavy` | 25 | `challengeUnarmedHeavyReward` |
| `challenge_unarmed_heavy_250` | `unarmed.heavy` | 250 | `challengeUnarmedHeavyReward` x 3 |

`unarmed.critical` counts hits landed with fall distance above zero while not on the ground. `unarmed.heavy` counts hits above 6 damage.

### Shared adaptation keys

Every adaptation TOML at `plugins/Adapt/adapt/adaptations/<id>.toml` also carries `enabled`, `permanent`, `showParticles`, `showSounds`, `baseCost`, `costFactor`, `maxLevel`, and `initialCost`.

"Level percent" below is the learned level divided by the adaptation's max level (0 to 1).

Every adaptation carries a tick interval because every adaptation is registered with the scheduler, but only Unarmed Power, Disarm, and Meditation actually run work on that tick. The rest are event-driven and their interval is inert.

### Sucker Punch

| Property | Default |
|----------|---------|
| Class | `UnarmedSuckerPunch` |
| Icon | `OBSIDIAN` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 2 |
| Cost factor | 0.225 |
| Tick interval (ms) | 4944 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-sucker-punch.toml` |

Listened events: `EntityDamageByEntityEvent` (applies the multiplier), `EntityDeathEvent` (counts one-punch kills).

Menu lore: "Damage", "Requires an empty main hand while sprinting".

Stats and milestones: `unarmed.sucker-punch.sucker-punches` at 500 (reward 400); `unarmed.sucker-punch.one-punch-kills` at 50 (reward 1000), credited when the killing blow's final damage was at least the victim's max health.

XP is hardcoded: 6.221 times the resulting damage per punch, plus 0.42 times the damage again when the punch exceeds 5. The main hand must be `AIR` exactly.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseDamage` | `0.2` | Fraction added to your damage at level percent 0, applied as a multiplier of 1 plus this value. |
| `damageFactor` | `0.55` | Extra fraction at full level percent. |

### Unarmed Power

| Property | Default |
|----------|---------|
| Class | `UnarmedPower` |
| Icon | `IRON_INGOT` |
| Max level | 7 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 3 |
| Cost factor | 0.425 |
| Tick interval (ms) | 4444 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-power.toml` |

Listened events: `PlayerItemHeldEvent`, `PlayerSwapHandItemsEvent`, `InventoryCloseEvent`, `PlayerDropItemEvent`, `EntityPickupItemEvent`, `PlayerItemBreakEvent`, `PlayerRespawnEvent`, and `PlayerChangedWorldEvent` all reapply or strip the modifier; `EntityDamageByEntityEvent` pays XP and reapplies; `EntityDeathEvent` counts bare-hand kills. The tick pass also reconciles learners.

Menu lore: "Damage".

Stats and milestones: `unarmed.power.unarmed-kills` at 500 (reward 400) and 5000 (reward 1500).

The bonus is an attack-damage modifier of level percent times `damageFactor`, applied with MULTIPLY_SCALAR_1 for 180 ticks and refreshed whenever your hands change. XP per hit is hardcoded at 0.321 times level percent times damage.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageFactor` | `2.57` | Attack-damage multiplier reached at full level percent. |

### Glass Cannon

| Property | Default |
|----------|---------|
| Class | `UnarmedGlassCannon` |
| Icon | `POINTED_DRIPSTONE` |
| Max level | 7 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 3 |
| Cost factor | 0.425 |
| Tick interval (ms) | 4544 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-glass-cannon.toml` |

Listened events: `EntityDamageByEntityEvent` (adjusts the damage), `EntityDeathEvent` (counts kills made with zero armor).

Menu lore: "x Damage at 0 armor", "PerLevel Bonus Damage".

Stats and milestones: `unarmed.glass-cannon.naked-kills` at 100 (reward 300) and 500 (reward 1000).

Armor value here is Adapt's own fraction, summed across the four slots (for example a leather helmet is 0.04, an iron helmet 0.08, a diamond helmet 0.12). With zero armor the damage becomes `damage x (maxDamageFactor + level x maxDamagePerLevelMultiplier)` plus the flat bonus. With any armor it becomes `damage - (damage x armor)` plus the flat bonus. Both branches take the higher of the result and your original damage, so this never reduces a hit.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `perLevelBonusMultiplier` | `0.25` | Flat health points of bonus damage per learned level, added in both branches. |
| `maxDamageFactor` | `4.0` | Damage multiplier at zero armor before the per-level term. |
| `maxDamagePerLevelMultiplier` | `0.15` | Extra zero-armor multiplier per learned level. |

### Battering Charge

| Property | Default |
|----------|---------|
| Class | `UnarmedBatteringCharge` |
| Icon | `BLAZE_ROD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1000 (default; no tick work) |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-battering-charge.toml` |

Listened events: `EntityDamageByEntityEvent` (applies the impact), `EntityDeathEvent` (counts kills within 2 seconds of a charge hit), `PlayerMoveEvent` (samples horizontal movement and updates the primed trail), `PlayerToggleSprintEvent` (primes or drops the charge).

Menu lore: "Impact Damage Bonus", "Impact Knockback", "Charge Cooldown".

Stats and milestones: `unarmed.battering-charge.charges` at 300 (reward 400); `unarmed.battering-charge.charge-kills` at 100 (reward 1000).

The movement sample must be under 750 ms old to count. Riding a vehicle blocks the charge. Cooldown ticks are floored at 10.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageBase` | `0.5` | Flat health points added to the impact at level percent 0. |
| `damageFactor` | `4.2` | Extra flat damage at full level percent. |
| `knockbackBase` | `0.5` | Velocity added to the target along your look direction at level percent 0. |
| `knockbackFactor` | `1.2` | Extra knockback velocity at full level percent. |
| `cooldownTicksBase` | `80` | Ticks between charges at level percent 0. |
| `cooldownTicksFactor` | `50` | Ticks removed at full level percent, floored at 10. |
| `minimumVelocitySquared` | `0.05` | Squared horizontal blocks per tick you must actually be moving; sprinting is roughly 0.08. |
| `xpPerDamage` | `3.3` | Unarmed XP per point of the resulting hit damage. |
| `primedTrailIntervalMillis` | `120` | Milliseconds between dust puffs while the charge is primed. |

### Combo Chain

| Property | Default |
|----------|---------|
| Class | `UnarmedComboChain` |
| Icon | `CHAINMAIL_BOOTS` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.6 |
| Tick interval (ms) | 1800 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-combo-chain.toml` |

Listened events: `EntityDamageByEntityEvent` (builds the combo, or drops it if the main hand holds a melee tool), `PlayerInteractEvent` (a left-click that lands on nothing after the grace window drops the combo).

Menu lore: "Max Combo Stacks", "Damage Per Stack", "Combo Window".

Stats and milestones: `unarmed.combo-chain.total-combo-hits` at 5000 (reward 400). One-off advancements `challenge_unarmed_combo_10` and `challenge_unarmed_combo_25` at 10 and 25 stacks.

Only the main hand is checked here. Dropping a combo below 3 stacks plays no effect.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxStacksBase` | `2` | Stack ceiling at level percent 0. |
| `maxStacksFactor` | `8` | Extra stack ceiling at full level percent. |
| `damagePerStackBase` | `0.2` | Flat health points of bonus damage per stack at level percent 0. |
| `damagePerStackFactor` | `0.85` | Extra damage per stack at full level percent. |
| `comboWindowMillisBase` | `1300` | Milliseconds allowed between hits at level percent 0. |
| `comboWindowMillisFactor` | `1400` | Extra window milliseconds at full level percent, floored at 250. |
| `missResetGraceMillis` | `280` | Milliseconds after a hit during which a whiffed swing does not break the chain. |
| `xpPerBonusDamage` | `4.1` | Unarmed XP per point of combo bonus damage dealt. |

### Disarm

| Property | Default |
|----------|---------|
| Class | `UnarmedDisarm` |
| Icon | `STICK` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.55 |
| Tick interval (ms) | 5125 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-disarm.toml` |

Listened events: `EntityDamageByEntityEvent`. The tick pass only expires per-target cooldowns.

Menu lore: "Disarm Chance", "Per-Target Cooldown", "chance a disarmed mob also drops a worn armor piece".

Stats and milestones: `unarmed.disarm.disarms` at 100 (reward 400) and 1000 (reward 1500).

The main-hand item is taken first; an off-hand shield is only taken when the main hand is empty. Players never lose armor. TragOul skeletal servants are skipped.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `allowDisarmPlayers` | `true` | False limits disarms to mobs. |
| `mobArmorDropChance` | `0.5` | Chance a successful disarm on a mob also knocks off one worn armor piece, 0-1. |
| `chanceBase` | `0.04` | Disarm chance per hit at level percent 0, 0-1. |
| `chanceFactor` | `0.18` | Extra chance at full level percent, capped at 1. |
| `pickupDelayTicks` | `60` | Ticks before anyone can pick the knocked item back up. |
| `targetCooldownMillis` | `8000` | Milliseconds before the same target can be disarmed again. |
| `xpPerDisarm` | `28` | Unarmed XP per successful disarm. |

### Pressure Point

| Property | Default |
|----------|---------|
| Class | `UnarmedPressurePoint` |
| Icon | `TRIPWIRE_HOOK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Tick interval (ms) | 4733 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-pressure-point.toml` |

Listened events: `EntityDamageByEntityEvent`.

Menu lore: "Max Slowness Stacks", "Max Weakness Stacks", and "Weakness unlocks at higher levels" while it is still locked.

Stats and milestones: `unarmed.pressure-point.pressure-strikes` at 500 (reward 400) and 5000 (reward 1500).

Each hit raises the existing amplifier by one, up to the cap, and refreshes the duration.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxSlownessAmplifierBase` | `0` | Highest Slowness amplifier at level percent 0. |
| `maxSlownessAmplifierFactor` | `2` | Extra amplifier headroom at full level percent. |
| `slownessDurationTicks` | `60` | Ticks of Slowness applied per strike. |
| `weaknessUnlockPercent` | `0.6` | Level percent at which Weakness starts being applied too. |
| `maxWeaknessAmplifier` | `1` | Highest Weakness amplifier once unlocked. |
| `weaknessDurationTicks` | `50` | Ticks of Weakness applied per strike. |
| `xpPerStrike` | `3.1` | Unarmed XP per strike. |

### Shockwave Clap

| Property | Default |
|----------|---------|
| Class | `UnarmedShockwaveClap` |
| Icon | `NOTE_BLOCK` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 5 |
| Cost factor | 0.7 |
| Tick interval (ms) | 5230 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-shockwave-clap.toml` |

Listened events: `PlayerInteractEvent` (sneak plus left-click air or block).

Menu lore: "Shockwave Range", "Knockback Force", "Clap Cooldown", "Hunger Cost".

Stats and milestones: `unarmed.shockwave-clap.mobs-clapped` at 250 (reward 400) and 2500 (reward 1500). Every activation also increments `unarmed.shockwave-clap.claps`, which has no milestone.

Hunger is spent on activation, before targets are resolved. Cooldown is floored at 1000 ms.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rangeBase` | `3.5` | Cone reach in blocks at level percent 0. |
| `rangeFactor` | `3` | Extra reach in blocks at full level percent. |
| `forceBase` | `0.8` | Outward velocity applied to each target at level percent 0. |
| `forceFactor` | `1.2` | Extra outward velocity at full level percent. |
| `upwardForceBase` | `0.25` | Upward velocity component at level percent 0. |
| `upwardForceFactor` | `0.2` | Extra upward velocity at full level percent. |
| `coneDotThreshold` | `0.45` | Dot product against your look direction a target must exceed; lower widens the cone. |
| `cooldownMillisBase` | `10000` | Milliseconds between claps at level percent 0. |
| `cooldownMillisFactor` | `6000` | Milliseconds removed at full level percent, floored at 1000. |
| `hungerCost` | `2` | Food points spent per clap; the clap fails if you have less. |
| `xpPerTargetHit` | `14` | Unarmed XP per target actually knocked back. |
| `maxCandidatesPerActivation` | `16` | Nearby living entities inspected per clap, hard-capped at 32. |
| `maxAffectedPerActivation` | `12` | Targets knocked back per clap, hard-capped at 16 and by the candidate limit. |
| `maxTargetFxPerActivation` | `8` | Knocked-back targets that get their own cloud particles, hard-capped at 12. |

### Iron Fists

| Property | Default |
|----------|---------|
| Class | `UnarmedIronFists` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.45 |
| Tick interval (ms) | 4622 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-iron-fists.toml` |

Listened events: `EntityDamageByEntityEvent` (flat damage bonus), `BlockDamageEvent` (soft-block mining buff, which requires an empty main hand rather than just no tool).

Menu lore: "Flat Punch Damage", "Soft Block Punch Haste".

Stats and milestones: `unarmed.iron-fists.iron-hits` at 1000 (reward 400) and 10000 (reward 1500).

The mining buff is a block-break-speed modifier of 0.2 x (amplifier + 1), not a Haste potion effect.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageBase` | `0.5` | Flat health points added per hit at level percent 0. |
| `damageFactor` | `2.5` | Extra flat damage at full level percent. |
| `softBlockMaxHardness` | `0.8` | Highest block hardness that still counts as soft. |
| `hasteDurationTicks` | `25` | Ticks the mining buff lasts after each punch; 0 disables it. |
| `hasteAmplifierFactor` | `2` | Amplifier reached at full level percent. |
| `xpPerHit` | `2.4` | Unarmed XP per bare-hand hit. |

### Grapple

| Property | Default |
|----------|---------|
| Class | `UnarmedGrapple` |
| Icon | `LEAD` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 5 |
| Cost factor | 0.65 |
| Tick interval (ms) | 1000 (default; no tick work) |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-grapple.toml` |

Listened events: `EntityDamageByEntityEvent` (grabs while sneaking, or hurls if a grab is already held), `PlayerToggleSneakEvent` (releasing sneak hurls).

Menu lore: "Hurl Force", "Grapple Cooldown", "Hit again or release sneak to hurl", "Exhaustion per Throw".

Stats and milestones: `unarmed.grapple.hurled-mobs` at 100 (reward 400) and 1000 (reward 1500).

Bosses cannot be grabbed. The cooldown is marked on the hurl, not the grab, and is floored at 1000 ms. One hurl per second per player.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `forceBase` | `0.9` | Throw velocity along your look direction at level percent 0. |
| `forceFactor` | `1.4` | Extra throw velocity at full level percent. |
| `upwardBoost` | `0.2` | Upward velocity component at level percent 0. |
| `upwardBoostFactor` | `0.25` | Extra upward component at full level percent. |
| `maxHurlRange` | `6` | Blocks the target may be from you when the hurl resolves, or it is cancelled. |
| `grabTimeoutMillis` | `5000` | Milliseconds an unused grab stays held. |
| `cooldownMillisBase` | `9000` | Milliseconds between grapples at level percent 0. |
| `cooldownMillisFactor` | `5000` | Milliseconds removed at full level percent, floored at 1000. |
| `exhaustionPerThrow` | `2.0` | Exhaustion added to you per throw; 0 disables the cost. |
| `xpPerHurl` | `32` | Unarmed XP per throw. |

### Second Wind

| Property | Default |
|----------|---------|
| Class | `UnarmedSecondWind` |
| Icon | `COOKED_BEEF` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Tick interval (ms) | 4960 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-second-wind.toml` |

Listened events: `EntityDeathEvent` (a non-player mob you killed bare-handed).

Menu lore: "Hunger Restored", "Regeneration Duration".

Stats and milestones: `unarmed.second-wind.second-winds` at 100 (reward 400) and 1000 (reward 1500).

Friendly targets, including your own pets, are skipped. Food is clamped to 20 and saturation to your current food level.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `foodRestoreBase` | `1` | Food points restored per kill at level percent 0. |
| `foodRestoreFactor` | `4` | Extra food points at full level percent. |
| `saturationRestore` | `1.5` | Saturation restored per kill. |
| `regenDurationTicksBase` | `40` | Ticks of Regeneration at level percent 0. |
| `regenDurationTicksFactor` | `80` | Extra ticks at full level percent, floored at 20. |
| `regenAmplifier` | `0` | Amplifier of the Regeneration burst. |
| `cooldownMillis` | `3000` | Milliseconds between triggers. |
| `xpPerSecondWind` | `18` | Unarmed XP per trigger. |

### Meditation

| Property | Default |
|----------|---------|
| Class | `UnarmedMeditation` |
| Icon | `AMETHYST_CLUSTER` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.55 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/unarmed-meditation.toml` |

Listened events: `EntityDamageByEntityEvent` (any combat, as attacker or victim, starts the lockout and ends the session), `PlayerToggleSneakEvent` (starts and stops sessions), `PlayerQuitEvent` (cleans up the absorption capacity modifier).

Menu lore: "Max Absorption", "Absorption Per Pulse", "Combat Lockout".

Stats and milestones: `unarmed.meditation.absorption-gained` at 500 (reward 400) and 5000 (reward 1500).

Pulses run once per second while sneaking. Both hands must be completely empty, not merely free of tools. XP from pulses is silent, meaning it does not show the usual XP popup.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `absorptionCapBase` | `2` | Absorption health points you can hold at level percent 0. |
| `absorptionCapFactor` | `10` | Extra absorption cap at full level percent. |
| `gainPerPulse` | `0.5` | Absorption health points gained each second while meditating. |
| `combatLockoutMillis` | `8000` | Milliseconds after any combat before meditation can resume. |
| `stationaryEpsilonSquared` | `0.01` | Squared blocks of drift per pulse still treated as standing still. |
| `xpPerPulse` | `1.2` | Silent Unarmed XP per pulse. |

## See also

- `02 - Concepts.md` for levels, knowledge, and how adaptations are learned.
- `03 - Player Usage.md` for the Adapt menu and general play.
- `10 - Skills Catalog.md` for the full skill list.
- `04 - Commands & Permissions.md` for the `adapt.use` nodes.
