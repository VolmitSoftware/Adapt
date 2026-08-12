# Skill: TragOul

TragOul is the blood skill. You level it by getting hurt and living through it, and the adaptations pay that back: damage reflected at whoever hit you, life stolen out of your enemies, corpses that explode or launch seeking lances, and a pack of skeletons raised out of a stack of bones. Fourteen adaptations, and most of them get better the more dangerous your fights are.

It plays as a high-risk kit. Several adaptations charge you for their power. Corpse Lances take a bite out of your own health each time a lance connects. Skeletal Servant permanently lowers your max health while its pack is alive. Marrow Armor eats a bone off your belt every time it soaks a hit. In exchange you get lifesteal, an emergency save that refuses a killing blow, and crowd damage that clears rooms.

It also has a death penalty, off by default. Turn `takeAwaySkillsOnDeath` on and dying costs you TragOul XP and knocks every TragOul adaptation down one level.

If you are new to it, Will of Pain and Soul Siphon are the safest starting picks, because they only ever give you health back. Corpse Lances and Skeletal Servant are where the skill gets loud.

## How you earn TragOul XP

XP comes from being damaged by something. When an entity hits you, the skill adds 1 to `trag.hitsrecieved` and adds the raw damage to `trag.damage`, then pays `damageReceivedXpMultiplier` times that damage. XP awards are on a `cooldownDelay` cooldown, so the stat counters keep climbing during a long fight while the payouts drip.

Surviving low pays extra. If the hit leaves you alive but at 4 hearts or less, you get `lowHealthSurvivalXP` on top, with a short red-to-cyan ring effect around you.

Nothing is credited if you are already dead, invulnerable, or blocking with a shield when the hit lands.

Death behavior depends on config. If Adapt's global hardcore reset is on, dying wipes all your skill data. Otherwise, if `takeAwaySkillsOnDeath` is on, you lose up to `deathXpLoss` TragOul XP (never below zero) and every learned TragOul adaptation drops one level.

## Adaptations

Everything below needs the same four things before it does anything: the adaptation learned at level 1 or higher, the TragOul skill and that adaptation both enabled in config, the `adapt.use` permission for it, and any protection or region plugin allowing the action on that target. Learn adaptations from the Adapt menu (`/adapt`), under TragOul.

### Thorns (`tragoul-thorns`)

Whoever hits you takes a flat chunk of damage back. Projectiles count, and the reflected damage goes to the shooter, not the arrow. Fires at most once every 1.5 seconds, so a swarm will not shred itself instantly. Killing something with the reflection earns you a one-off advancement.

### Globe of Pain (`tragoul-globe`)

Your melee hit stops being single target. The damage is split evenly across the mob you hit and the other valid mobs nearby, and each of them also takes a per-level bonus on top. Against a crowd, each mob takes less than your normal hit, but everything gets hit at once.

### Will of Pain (`tragoul-healing`)

Anything that damages you loses a small fixed amount of life, and you are healed by whatever it actually lost. Steady, passive, and does not care what hit you as long as the source resolves to a living attacker.

### Corpse Lances (`tragoul-lance`)

Kill something and a lance launches from the corpse at the nearest valid target. It never picks you. If the lance kills its target, it can chain from that corpse to the next one at half damage, up to one hop per level and a hard maximum of 6. Damage is based on the killing blow that started it, tripled by default while you wear no armor at all.

Each connecting lance costs you real health, mitigated by armor and effects like any other hit, and the cost drops as you level. There is a 5 second cooldown per player and only one chain running at a time.

### Blood Pact (`tragoul-blood-pact`)

Take a big enough hit and you might be rewarded for it. On a proc you get a handful of random potion effects drawn from speed, regeneration, resistance, fire resistance, absorption, jump boost, and night vision. Larger hits and higher levels give you more of them at once.

### Bone Harvest (`tragoul-bone-harvest`)

Kills can drop a globe on the ground: a red blood globe or a white bone globe, coin flip. Walk over it to collect. Blood globes give regeneration; bone globes give a random handful of buffs. Globes expire on their own and hoppers cannot take them.

### Corpse Explosion (`tragoul-corpse-explosion`)

Every mob you kill detonates in a blood nova that damages hostile mobs around the corpse. Nova damage is a flat amount plus a share of the dead mob's max health, so killing a tanky mob in a crowd hurts. A mob damaged by a nova cannot start a new one for a few seconds, which is what stops the chain reaction from running away.

Servant kills detonate too, tinted bone white instead of crimson.

### Soul Siphon (`tragoul-soul-siphon`)

Lifesteal on everything you are credited for. Melee, arrows, TNT you lit, lingering clouds you threw, evoker fangs, all of it heals you for part of the final damage. There is a healing cap per second so multi-target hits cannot fully restore you in one swing, and you get a small puff of smoke when you hit that cap.

### Skeletal Servant (`tragoul-skeletal-servant`)

Raise a pack of skeletons that fight for you. Servants spawn with random gear scaled to your level, do not burn in daylight, drop no loot, cannot damage you, and expire on a timer. They inherit your other TragOul perks, so a servant's hits can siphon, curse, and spread plague for you.

How to use it:

1. Learn Skeletal Servant in the Adapt menu.
2. Hold bones in your main hand. The summon costs several bones, fewer as you level.
3. Sneak and right-click.
4. A servant rises at your feet and takes your current mark, which is whatever you last hit or whatever last hit you.

You can keep one living servant per level. Summoning at the cap recycles the oldest one by default. While servants are alive your maximum health is reduced by `healthCostPerMinion` for each of them, down to a floor, so a full pack is a real trade.

### Marrow Armor (`tragoul-marrow-armor`)

A big enough hit shatters one bone from your inventory and a share of the damage goes with it. Chip damage is ignored so you do not burn the whole stack, and there is an internal cooldown that shortens as you level. With no bones on you it just makes a dull rattle and does nothing.

### Curse of Frailty (`tragoul-curse-of-frailty`)

Anything that hits you gets Weakness, and Slowness on top once you are far enough up the level track. Each attacker has its own cooldown, so a crowd all get cursed but no single one is re-cursed over and over.

### Death Sense (`tragoul-death-sense`)

Wounded creatures and players near you glow through walls, visible only to you. The outline color tracks how close to death they are, from yellow down to dark red. Higher levels raise both the radius and the health threshold, so eventually you can see anything that is even slightly hurt.

### Plague Bearer (`tragoul-plague-bearer`)

If you poison or wither a mob and it dies with that effect still on it, the affliction jumps to nearby mobs at a higher amplifier. The spread can chain for a few generations before it burns out.

How to use it:

1. Learn Plague Bearer in the Adapt menu.
2. Poison or wither a mob and hit it (a splash potion plus a hit works, since the mark is applied on your damage).
3. Kill it while the effect is still running.
4. Nearby mobs catch the same effect, one amplifier stronger.

### Last Rites (`tragoul-last-rites`)

A hit that would kill you is refused. You drop to 1 HP, go invisible with heavy resistance for a few seconds, and every hostile mob within range that was targeting you forgets about you. The cooldown is long, measured in minutes, and drops as you level. This is your escape button, not a rotation.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `tragoul` |
| Class | `SkillTragOul` |
| Icon | `CRIMSON_ROOTS` |
| Color | `AQUA` |
| Interval (ms) | `2755` |
| Skill config | `plugins/Adapt/adapt/skills/tragoul.toml` |
| Adaptation count | 14 |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/tragoul.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `deathXpLoss` | `250` | TragOul XP removed on death when `takeAwaySkillsOnDeath` is true, clamped so XP never goes below zero. |
| `takeAwaySkillsOnDeath` | `false` | True makes death cost TragOul XP and drop every learned TragOul adaptation by one level. |
| `enabled` | `true` | Turns the whole TragOul skill on or off. |
| `skillColor` | `"&b"` | Legacy ampersand color code used for TragOul in menus and text. |
| `showParticles` | `true` | Emits the skill's own damage and death particle effects. Sounds still play. |
| `cooldownDelay` | `450` | Milliseconds between XP awards for taking damage. |
| `damageReceivedXpMultiplier` | `4.8` | Skill XP per point of damage you take. |
| `lowHealthSurvivalXP` | `28` | Extra skill XP when a survived hit leaves you at 8 health (4 hearts) or less. |
| `challengeTragReward` | `500` | Knowledge paid by the TragOul challenges. |

### Skill milestones

| Advancement key | Stat key | Threshold | Reward |
|-----------------|----------|-----------|--------|
| `challenge_trag_1k` | `trag.damage` | 1000 | `challengeTragReward` |
| `challenge_trag_10k` | `trag.damage` | 10000 | `challengeTragReward` x 2 |
| `challenge_trag_100k` | `trag.damage` | 100000 | `challengeTragReward` x 5 |
| `challenge_trag_hits_500` | `trag.hitsrecieved` | 500 | `challengeTragReward` |
| `challenge_trag_hits_5k` | `trag.hitsrecieved` | 5000 | `challengeTragReward` x 2 |

The stat key `trag.hitsrecieved` is spelled that way in code.

### Shared adaptation keys

Every adaptation TOML at `plugins/Adapt/adapt/adaptations/<id>.toml` also carries `enabled`, `permanent`, `showParticles`, `showSounds`, `baseCost`, `costFactor`, `maxLevel`, and `initialCost`.

"Level percent" below is the learned level divided by the adaptation's max level (0 to 1). Where a value is described as scaling from level 1 to max level, the code lerps on `(level - 1) / (maxLevel - 1)` instead.

Every adaptation carries a tick interval because every adaptation is registered with the scheduler, but only Bone Harvest, Curse of Frailty, Death Sense, and Skeletal Servant actually run work on that tick. The rest are event-driven and their interval is inert. Corpse Explosion drains its nova queue on its own one-tick schedule instead.

### Thorns

| Property | Default |
|----------|---------|
| Class | `TragoulThorns` |
| Icon | `CACTUS` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-thorns.toml` |

Listened events: `EntityDamageByEntityEvent` (you are the victim). Reflected damage runs inside the reactive-damage guard so it cannot re-trigger TragOul handlers.

Menu lore: "Damage retaliated when struck".

Stats and milestones: `tragoul.thorns.damage-reflected` at 500 (reward 400) and 5000 (reward 1500). One-off advancement `challenge_tragoul_thorns_kill` when a reflection kills the attacker.

Fixed in code: 1500 ms between reflections per player. Reflected damage is `damageMultiplierPerLevel` times the learned level, not level percent.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageMultiplierPerLevel` | `1.75` | Health points reflected per learned level. |

### Globe of Pain

| Property | Default |
|----------|---------|
| Class | `TragoulGlobe` |
| Icon | `CRYING_OBSIDIAN` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-globe.toml` |

Listened events: `EntityDamageByEntityEvent` at HIGHEST (you are the damager).

Menu lore: "The more enemies around you, the less damage you deal to each of them", plus range and added damage lines.

Stats and milestones: `tragoul.globe.mobs-shared-with` at 1000 (reward 400). One-off advancement `challenge_tragoul_globe_5` for sharing with 5 or more mobs at once.

Per hit, damage per entity is `originalDamage / (sharedTargets + 1)` plus the level bonus, and the original target takes that same share. Hard caps: range 24 blocks, 24 candidates examined, 8 shared targets per hit, 32 activations and 256 targets per 50 ms window.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldown` | `1` | Seconds between activations for one player, floored at 0.5 seconds. |
| `rangePerLevel` | `3.0` | Blocks of share radius added per learned level. |
| `initalRange` | `5.0` | Base share radius in blocks. The misspelling is the real key name. |
| `bonusDamagePerLevel` | `1` | Health points of extra damage per learned level, added to every share. |

### Will of Pain

| Property | Default |
|----------|---------|
| Class | `TragoulHealing` |
| Icon | `GLISTERING_MELON_SLICE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-healing.toml` |

Listened events: `EntityDamageEvent` (you are the victim; the attacker is resolved from the damage source or the projectile shooter).

Menu lore: "health drained from each attacker", "Actual life drained is restored to you".

Stats and milestones: `tragoul.healing.health-stolen` at 500 (reward 400) and 10000 (reward 1500).

Your own skeletal servants cannot be drained. Healing is capped by your missing health.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `drainDamageStart` | `0.5` | Health points drained from each attacker at level 1. |
| `drainDamageEnd` | `2.0` | Health points drained at max level; levels in between interpolate. |

### Corpse Lances

| Property | Default |
|----------|---------|
| Class | `TragoulLance` |
| Icon | `TRIDENT` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 1000 (default; no tick work) |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-lance.toml` |

Listened events: `EntityDeathEvent` at LOWEST (starts a chain from a kill you caused), `PlayerQuitEvent` and `PlayerDeathEvent` (cancel any chain in flight).

Menu lore: "Killing blows launch seeking corpse lances, including lance chain kills", "Flat life cost falls from 3 hearts to 1 heart and uses normal damage mitigation", "Max Lances: 1 + level".

Stats and milestones: `tragoul.lance.lances-spawned` at 200 (reward 400), incremented per connecting lance; `tragoul.lance.lance-kills` at 100 (reward 1000).

Fixed in code: 5000 ms cooldown per player, one chain in flight per player, search radius `min(32, 5 + 4 x level)`, chain length `min(6, level)`, each hop deals half the previous damage, 24 candidates examined and 8 handed off per search, 32 searches per 50 ms window, chains abandoned after 30 seconds. Lance damage is the killing blow's final damage times `seekerDamageMultiplier`, times `unarmoredDamageMultiplier` when no armor is equipped.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `seekerDelay` | `12` | Ticks between launch and impact, clamped to 1 - 40. |
| `seekerDamageMultiplier` | `1.0` | Multiplier on the killing blow's damage, clamped to 0 - 4. |
| `selfDamageAtFirstLevel` | `6.0` | Health points you take per connecting lance at level 1. |
| `selfDamageAtMaxLevel` | `2.0` | Health points you take at max level; never higher than the level-1 value. |
| `unarmoredDamageMultiplier` | `3.0` | Extra multiplier while no armor is equipped, clamped to 1 - 10. |

### Blood Pact

| Property | Default |
|----------|---------|
| Class | `TragoulBloodPact` |
| Icon | `NETHER_WART` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.62 |
| Tick interval (ms) | 1000 (default; no tick work) |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-blood-pact.toml` |

Listened events: `EntityDamageEvent` (rolls the proc), `EntityDeathEvent` (counts kills made while an Absorption or Resistance effect is on you).

Menu lore: "Proc Chance", "Buff Duration", "Proc Cooldown".

Stats and milestones: `tragoul.blood-pact.health-sacrificed` at 200 (reward 400); `tragoul.blood-pact.empowered-kills` at 500 (reward 1000). One-off advancement `challenge_tragoul_pact_all_in` for an empowered kill after a proc that left you at 3 hearts or less.

Effect pool: Speed, Regeneration, Resistance, Fire Resistance, Absorption, Jump Boost, Night Vision. Speed and Jump Boost are applied as timed attribute modifiers rather than potion effects. Absorption runs 20 ticks shorter, floored at 40. Amplifier steps to 1 at level percent 0.85 for Absorption, Resistance, and Regeneration, and at 0.7 for the rest.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `minDamageTriggerHearts` | `2.0` | Hearts of final damage required to roll a proc; doubled internally into health points. |
| `procChanceBase` | `0.12` | Proc chance at level percent 0, 0-1. |
| `procChanceFactor` | `0.38` | Extra proc chance at full level percent. |
| `maxProcChance` | `0.5` | Ceiling on the proc chance, 0-1. |
| `procCooldownMillisBase` | `18000` | Milliseconds between procs at level percent 0. |
| `procCooldownMillisFactor` | `12000` | Milliseconds removed at full level percent, floored at 500 ms. |
| `effectDurationTicksBase` | `100` | Buff duration in ticks at level percent 0. |
| `effectDurationTicksFactor` | `150` | Extra duration in ticks at full level percent, floored at 40 ticks. |
| `buffCountBase` | `1` | Buffs granted at level percent 0. |
| `buffCountFactor` | `2` | Extra buffs at full level percent. One more is added when the hit was 1.6x the trigger threshold. |
| `bonusBuffChanceBase` | `0.08` | Chance of one extra buff at level percent 0, 0-1. |
| `bonusBuffChanceFactor` | `0.34` | Extra chance at full level percent, capped at 0.9. |
| `xpPerProc` | `24` | TragOul XP per proc. |

### Bone Harvest

| Property | Default |
|----------|---------|
| Class | `TragoulBoneHarvest` |
| Icon | `BONE_BLOCK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 10000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-bone-harvest.toml` |

Listened events: `EntityDeathEvent` (rolls a globe on your kill), `EntityPickupItemEvent` (consumes a globe and applies its buff), `InventoryPickupItemEvent` (blocks hoppers from eating globes). The tick pass only prunes expired globe tracking.

Menu lore: "Globe Spawn Chance", "Globe Lifetime".

Stats and milestones: `tragoul.bone-harvest.orbs-collected` at 500 (reward 300) and 5000 (reward 1000).

Globes are real dropped items: `MAGMA_CREAM` for blood, `SNOWBALL` for bone, owner-locked to you with a 10 tick pickup delay, tagged with `adapt:tragoul-globe`, and not pickable by mobs. Which type spawns is a coin flip.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `globeChanceBase` | `0.16` | Chance per kill of a globe at level percent 0, 0-1. |
| `globeChanceFactor` | `0.42` | Extra chance at full level percent. |
| `maxGlobeChance` | `0.7` | Ceiling on the globe chance, 0-1. |
| `globeLifetimeTicksBase` | `120` | Ticks a globe survives at level percent 0. |
| `globeLifetimeTicksFactor` | `220` | Extra ticks at full level percent, floored at 20 ticks. |
| `bloodBuffTicks` | `80` | Regeneration duration in ticks from a blood globe. |
| `bloodBuffAmplifier` | `1` | Regeneration amplifier from a blood globe. |
| `boneBuffTicks` | `100` | Duration in ticks of every buff from a bone globe. |
| `boneBuffAmplifier` | `0` | Amplifier of bone globe buffs; Absorption gets one more at level percent 0.75 and up. |
| `boneBuffCountBase` | `1` | Buffs from a bone globe at level percent 0. |
| `boneBuffCountFactor` | `2` | Extra buffs at full level percent, drawn from the same seven-effect pool as Blood Pact. |
| `xpPerGlobeSpawned` | `8` | TragOul XP paid when a globe spawns. |

### Corpse Explosion

| Property | Default |
|----------|---------|
| Class | `TragoulCorpseExplosion` |
| Icon | `WITHER_ROSE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-corpse-explosion.toml` |

Listened events: `EntityDeathEvent` (any mob you killed). Skeletal Servant also calls into this adaptation for servant kills.

Menu lore: "Kills always display a corpse nova and damage nearby hostile mobs", "Nova Radius", "of the victim's max health added as nova damage".

Stats and milestones: `tragoul.corpse-explosion.mobs-detonated` at 500 (reward 400) and 5000 (reward 1500).

Only entities implementing Bukkit's `Enemy` interface are damaged. Each victim is stamped with `adapt:tragoul_nova_stamp` so it cannot start a nova of its own inside the suppression window. Hard caps: radius 16 blocks, 16 targets, 32 candidates, 8 novas per tick, 256 queued.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `3.0` | Nova radius in blocks at level percent 0. |
| `radiusFactor` | `3.5` | Extra radius in blocks at full level percent. |
| `baseDamage` | `3.0` | Flat health points of nova damage to every hostile mob hit. |
| `victimHealthFractionBase` | `0.10` | Share of the dead mob's max health added to nova damage at level percent 0. |
| `victimHealthFractionFactor` | `0.40` | Extra share at full level percent. |
| `maxDamage` | `24.0` | Ceiling on nova damage per mob, in health points. |
| `maxTargets` | `12` | Hostile mobs damaged per nova, hard-capped at 16. |
| `chainSuppressionMillis` | `5000` | Milliseconds a nova-damaged mob is barred from starting its own nova. |
| `xpPerMobHit` | `6` | TragOul XP per mob the nova actually damaged. |

### Soul Siphon

| Property | Default |
|----------|---------|
| Class | `TragoulSoulSiphon` |
| Icon | `SOUL_LANTERN` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-soul-siphon.toml` |

Listened events: `EntityDamageEvent` (any damage credited to you). Credit resolves through the damage source, projectile shooters, primed TNT, area effect clouds, and evoker fangs.

Menu lore: "of all attributed damage returned as health", "max health restored per second".

Stats and milestones: `tragoul.soul-siphon.health-siphoned` at 500 (reward 400) and 10000 (reward 1500).

Healing is the smallest of the damage share, the remaining per-second cap, and your missing health. Damage beyond the victim's remaining health plus absorption does not count.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `healPercentBase` | `0.05` | Share of the final damage returned as health at level percent 0, 0-1. |
| `healPercentFactor` | `0.32` | Extra share at full level percent. |
| `healCapPerSecondBase` | `2.0` | Health points per second you may heal at level percent 0. |
| `healCapPerSecondFactor` | `6.5` | Extra per-second cap at full level percent, floored at 0.5. |
| `xpPerHeal` | `3` | TragOul XP per siphon heal. |

### Skeletal Servant

| Property | Default |
|----------|---------|
| Class | `TragoulSkeletalServant` |
| Icon | `SKELETON_SKULL` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.75 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-skeletal-servant.toml` |

Listened events:

- `PlayerInteractEvent`: sneak plus right-click with a bone summons.
- `EntityTargetLivingEntityEvent`: keeps servants on your mark.
- `EntityDamageByEntityEvent` at LOWEST: cancels servant damage aimed at you or at anything that is not your mark.
- `EntityDamageByEntityEvent` as `onCombatPerks`: runs inherited TragOul perks for servant hits and updates the pack's mark from your own combat.
- `EntityDeathEvent`: a dying servant drops nothing and grants no XP.
- `EntityDeathEvent` as `onServantKill`: routes servant kills into Corpse Explosion.
- `EntitiesUnloadEvent`, `PlayerQuitEvent`, `PlayerDeathEvent`: release the pack.

Menu lore: "Sneak + Right-Click with bones in hand to summon a servant", "Servant Lifetime", "Bones consumed per summon", "Summon Cooldown", "Max living servants", "Servants gear up with your level, inherit your Tragoul perks, and hunt whatever you strike or whatever strikes you", "Max health lost per living servant".

Stats and milestones: `tragoul.skeletal-servant.servants-summoned` at 50 (reward 400) and 500 (reward 1500).

Servants are `Skeleton` entities tagged with `adapt:tragoul_servant_owner`, excluded from mob stacking, non-persistent, kept loaded, and set not to burn in daylight. Gear is drawn from three tiers of leather, chainmail, iron, and diamond, plus a sword or bow. Creative mode skips the bone cost. Hard cap of 16 living servants per owner.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `boneCostBase` | `8` | Bones per summon at level percent 0. |
| `boneCostReduction` | `5` | Bones removed from that cost at full level percent, floored at 1. |
| `durationTicksBase` | `400` | Servant lifetime in ticks at level percent 0. |
| `durationTicksFactor` | `800` | Extra lifetime ticks at full level percent, floored at 100. |
| `cooldownMillisBase` | `10000` | Milliseconds between summons at level percent 0. |
| `cooldownMillisFactor` | `9000` | Milliseconds removed at full level percent, floored at 1000. |
| `servantCapPerLevel` | `1.0` | Living servants allowed per learned level, hard-capped at 16 per owner. |
| `replaceOldestAtCap` | `true` | True recycles your oldest servant when summoning at the cap; false refuses the summon. |
| `playerThreatWindowMillis` | `5000` | Milliseconds the last thing you hit or that hit you stays the pack's mark. |
| `gearChancePerPiece` | `0.55` | Chance per armor slot that a new servant spawns wearing something, 0-1. |
| `enchantChanceBase` | `0.0` | Chance an equipped piece is enchanted at level percent 0, 0-1. |
| `enchantChanceFactor` | `0.45` | Extra enchant chance at full level percent. |
| `bowChance` | `0.3` | Chance a servant spawns with a bow instead of a sword, 0-1. |
| `healthBonusPerLevel` | `3.0` | Health points of max health added to a servant per learned level. |
| `attackBonusPerLevel` | `1.0` | Health points of attack damage added to a servant per learned level. |
| `retargetIntervalTicks` | `20` | Ticks between retarget pulses, floored at 10. |
| `targetSearchRadius` | `12` | Blocks a servant scans for hostile mobs, capped at 24. |
| `xpPerSummon` | `30` | TragOul XP per summon. |
| `healthCostEnabled` | `true` | True applies the owner max-health upkeep while servants live. |
| `healthCostPerMinion` | `2.0` | Health points removed from your max health per living servant. |
| `minimumOwnerMaxHealth` | `4.0` | Lowest max health the upkeep can push you to. |

### Marrow Armor

| Property | Default |
|----------|---------|
| Class | `TragoulMarrowArmor` |
| Icon | `BONE_MEAL` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-marrow-armor.toml` |

Listened events: `EntityDamageEvent` at HIGHEST (you are the victim).

Menu lore: "Consumes 1 bone to absorb part of a hit", "of the hit absorbed per bone", "Internal Cooldown".

Stats and milestones: `tragoul.marrow-armor.damage-absorbed` at 500 (reward 400) and 5000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `minDamageToTrigger` | `2.0` | Health points of final damage required before a bone is spent. |
| `absorbPercentBase` | `0.20` | Share of the hit removed at level percent 0, 0-1. |
| `absorbPercentFactor` | `0.30` | Extra share at full level percent. |
| `maxAbsorbPercent` | `0.6` | Ceiling on the absorbed share, 0-1. |
| `internalCooldownMillisBase` | `4000` | Milliseconds between absorbs at level percent 0. |
| `internalCooldownMillisFactor` | `2000` | Milliseconds removed at full level percent, floored at 500. |
| `xpPerAbsorb` | `8` | TragOul XP per absorbed hit. |

### Curse of Frailty

| Property | Default |
|----------|---------|
| Class | `TragoulCurseOfFrailty` |
| Icon | `FERMENTED_SPIDER_EYE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 5000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-curse-of-frailty.toml` |

Listened events: `EntityDamageByEntityEvent` (you are the victim; projectile shooters are cursed rather than the projectile). The tick pass only expires attacker cooldowns.

Menu lore: "Attackers are cursed with Weakness", "Curse Duration", "Attackers are also cursed with Slowness" (the third line only appears once slowness is unlocked).

Stats and milestones: `tragoul.curse-of-frailty.curses-applied` at 100 (reward 400) and 1000 (reward 1500).

Weakness amplifier steps to 1 at level percent 0.8. Your own pets, marker armor stands, invulnerable entities, NPCs, and skeletal servants are never cursed. At most 16384 attacker cooldowns are tracked at once.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `curseDurationTicksBase` | `60` | Curse duration in ticks at level percent 0. |
| `curseDurationTicksFactor` | `100` | Extra duration in ticks at full level percent, floored at 40. |
| `slownessUnlockPercent` | `0.6` | Level percent at which Slowness is added to the curse. |
| `slownessAmplifier` | `0` | Amplifier of the Slowness component, clamped to 0 - 4. |
| `perAttackerCooldownMillis` | `4000` | Milliseconds before the same attacker can be cursed again, floored at 250. |
| `xpPerCurse` | `5` | TragOul XP per curse applied. |

### Death Sense

| Property | Default |
|----------|---------|
| Class | `TragoulDeathSense` |
| Icon | `SPIDER_EYE` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.6 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-death-sense.toml` |

Listened events: `EntityDamageEvent` (starts tracking a damaged entity), `EntityDeathEvent` and `EntitiesUnloadEvent` (stop tracking), `EntitiesLoadEvent` (picks up already-weakened entities), `PlayerQuitEvent` (drops the viewer's glows).

Menu lore: "Wounded damageable entities near you glow only for you", "health or lower marks an entity as dying prey", "Sense Radius".

Stats and milestones: `tragoul.death-sense.prey-sensed` at 1000 (reward 600).

Glow color by remaining health fraction: dark red at 0.25 and below, red at 0.5 and below, gold at 0.75 and below, yellow above that. The health threshold scales from `healthThresholdStart` at level 1 to `healthThresholdEnd` at max level. Glows are per-viewer and lease for 1 second at a time.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `8` | Sense radius in blocks at level percent 0. |
| `radiusFactor` | `8` | Extra radius in blocks at full level percent. |
| `maxRadius` | `32` | Ceiling on the sense radius, itself capped at 32 blocks. |
| `healthThresholdStart` | `0.5` | Health fraction at or below which a target is sensed, at level 1. |
| `healthThresholdEnd` | `0.9` | Same threshold at max level. |
| `maxOwnersPerTick` | `24` | Learned owners refreshed per scheduler tick, hard-capped at 24. |
| `maxTargetInspectionsPerTick` | `48` | Tracked targets inspected per scheduler tick, hard-capped at 48. |
| `maxMarksPerTick` | `12` | Per-owner glows refreshed per scheduler tick, hard-capped at 12. |

### Plague Bearer

| Property | Default |
|----------|---------|
| Class | `TragoulPlagueBearer` |
| Icon | `POISONOUS_POTATO` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-plague-bearer.toml` |

Listened events: `EntityDamageByEntityEvent` (marks a poisoned or withered mob you hit as yours), `EntityDeathEvent` (spreads the affliction if the mark is still fresh).

Menu lore: "Your poison and wither spread with increased potency on death", "Spread Radius", "Spread Effect Duration".

Stats and milestones: `tragoul.plague-bearer.mobs-infected` at 100 (reward 400) and 1000 (reward 1500).

Marks are stored on the mob as `adapt:tragoul_plague_owner`, `adapt:tragoul_plague_generation`, and `adapt:tragoul_plague_stamp`. Wither is preferred over Poison when both are present. Hard caps: radius 24 blocks, 4 generations, 8 spread targets, 600 tick effect duration, 60 second freshness, 24 candidates examined per spread.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `spreadRadiusStart` | `8` | Spread radius in blocks at level 1. |
| `spreadRadiusEnd` | `20` | Spread radius in blocks at max level. |
| `spreadDurationTicksBase` | `80` | Effect duration in ticks on infected mobs at level percent 0. |
| `spreadDurationTicksFactor` | `120` | Extra duration in ticks at full level percent, floored at 40. |
| `maxGenerations` | `3` | How many times one affliction may re-spread, hard-capped at 4. |
| `maxSpreadTargets` | `6` | Mobs infected per death, hard-capped at 8. |
| `amplifierBonus` | `1` | Amplifier levels added when the affliction jumps. |
| `afflictionFreshnessMillis` | `15000` | Milliseconds a mark stays valid, clamped to 1000 - 60000. |
| `xpPerInfection` | `6` | TragOul XP per infected mob. |

### Last Rites

| Property | Default |
|----------|---------|
| Class | `TragoulLastRites` |
| Icon | `TOTEM_OF_UNDYING` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 6 |
| Cost factor | 0.85 |
| Tick interval (ms) | 25000 |
| Config file | `plugins/Adapt/adapt/adaptations/tragoul-last-rites.toml` |

Listened events: `EntityDamageEvent` at HIGHEST (a hit whose final damage is at least your current health).

Menu lore: "Death is denied - you linger as a spirit at 1 HP", "Spirit Duration", "Cooldown".

Stats and milestones: `tragoul.last-rites.deaths-defied` at 5 (reward 500) and 50 (reward 2000).

The spirit state applies Invisibility and Resistance together for `spiritDurationTicks`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `spiritDurationTicks` | `60` | Ticks of Invisibility and Resistance after death is refused. |
| `resistanceAmplifier` | `3` | Amplifier of the Resistance effect during the spirit state. |
| `targetClearRadius` | `12` | Blocks searched for mobs targeting you, whose target is cleared. |
| `cooldownMillisBase` | `600000` | Milliseconds between saves at level percent 0. |
| `cooldownMillisFactor` | `300000` | Milliseconds removed at full level percent, floored at 30000. |
| `xpPerSave` | `120` | TragOul XP per death defied. |

### Support classes (not player adaptations)

- `TragoulReactiveDamage` marks reflected or reactive damage on the current thread so TragOul handlers do not recursively trigger themselves. Thorns and Will of Pain both check and set it.

## See also

- `02 - Concepts.md` for levels, knowledge, and how adaptations are learned.
- `03 - Player Usage.md` for the Adapt menu and general play.
- `10 - Skills Catalog.md` for the full skill list.
- `04 - Commands & Permissions.md` for the `adapt.use` nodes.
