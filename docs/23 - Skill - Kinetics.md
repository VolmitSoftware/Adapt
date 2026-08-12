# Skill: Kinetics

Kinetics is the momentum skill. It pays attention to force: mace smashes, spear charges, knockback in both directions, bounces off slime and beds, launches off pistons, levitation, big falls you survive, and anvils you drop on things. Do any of that and you earn `kinetics` XP.

The tree splits into three families. The movement adaptations change how you jump, land, slide, and fall, so a Kinetics player moves differently from everyone else on the server. The mace adaptations hang off the vanilla smash attack: dive faster into it, shred armor with it, blow the room apart with it, and land braced and springy afterward. The spear adaptations are about spacing: extra reach, damage that scales with how fast you are moving, a slowness pin at the right distance, better lunges, mounted charges, and a shove that clears anyone crowding your point.

Most of it is Paper-only. The smash, lunge, and knockback hooks come from Paper events, and the whole combat listener stays unregistered if those classes are missing.

Anvils get special treatment. Kinetics keeps a ledger of who placed which anvil, follows that anvil through piston pushes and falls, and pays the owner when it lands on something. Nearby players get a cut. Drop one from high enough for a kill and it counts toward the skill's only challenge.

## Adaptations

Everything below needs the same conditions: the adaptation learned at level 1 or higher, the Kinetics skill and that adaptation both enabled in config, the `adapt.use.*` permission (or the matching per-adaptation node), and any protection plugin on your server allowing the action where you are standing. Several of these lean on modern attributes (gravity, bounciness, friction, air drag, scale) and silently do nothing if the running server version does not have them.

"Spear" means any of the seven spear items, wooden through netherite. "Mace" means the vanilla mace.

### Moon Jump (`kinetics-moon-jump`)

Every jump gets higher, half a block per level, and it stays applied as long as you have the adaptation. Sneak-jumping adds a short extra hop with reduced gravity on top, which turns the peak of the jump into a slow float. Good for getting around, and it pairs with the mace adaptations because height is what a smash attack needs.

**How to use it**

1. Learn Moon Jump in the Adapt menu.
2. Jump normally for the passive height.
3. Hold sneak and jump for the floaty low-gravity hop.

### Rubber Soul (`kinetics-rubber-soul`)

Your boots stay springy all the time, so every landing keeps more of your momentum. Landing on a slime block, honey block, or bed adds a bigger springload bonus for a couple of seconds on top. Works on its own once learned.

### Soft Catch (`kinetics-soft-catch`)

Landing on something soft (slime, honey, a bed, hay, powder snow, sponge) cuts most of the fall damage, and you get Kinetics XP for the damage you avoided. Bouncing off a springy block also opens a short grace window, so the second landing after a bounce is protected even if you come down on stone. Works on its own once learned.

### Surface Skate (`kinetics-surface-skate`)

Sprint and the ground goes slick, so you carry speed through turns and slide when you stop steering. Sneak and it goes the other way, gripping harder than normal ground, which is what you want on a ledge. Both modes turn off the moment you stop sprinting or sneaking.

**How to use it**

1. Learn Surface Skate in the Adapt menu.
2. Sprint to slide.
3. Sneak to grip.

### Terminal Toggle (`kinetics-terminal-toggle`)

While falling, sneaking flips you between two midair modes: dive, which cuts air drag and increases gravity to get you down fast, and hang, which does the opposite and turns the fall into a drift. Each sneak press swaps modes. You need to have been airborne for a moment before the toggle arms, and landing clears the mode.

**How to use it**

1. Learn Terminal Toggle in the Adapt menu.
2. Get airborne and wait a fraction of a second.
3. Tap sneak to enter dive. Tap it again to switch to hang, and again to go back to dive.

### Heavy Frame (`kinetics-heavy-frame`)

Sneak while holding a mace or a spear and you plant your feet: heavy knockback resistance, blast resistance, and a movement speed penalty for as long as you hold it. It is a stance for holding a doorway or eating a creeper. Stand up or switch to a different item and it drops immediately.

**How to use it**

1. Learn Heavy Frame in the Adapt menu.
2. Hold a mace or spear in your main hand.
3. Hold sneak. The stance stays up until you stop sneaking or change item.

### Mass Shift (`kinetics-mass-shift`)

Three persistent body forms you switch between with a gesture. Titan makes you bigger, adds 20 percent to your attack damage and max health, gives you a taller step height and a pulled-back camera, and saddles you with Slowness I. Pocket makes you smaller, takes 20 percent off damage and health, and gives you Speed I. Normal is normal. The form survives until you change it, and it resets on death or logout.

**How to use it**

1. Learn Mass Shift in the Adapt menu.
2. Hold sneak.
3. Look up and press the swap-hands key (F by default) for Titan, look down for Pocket, or look level to go back to Normal. The offhand swap itself is cancelled while you do this.

### Meteor Cadence (`kinetics-meteor-cadence`)

Hold sneak while falling with a mace and you drop like a rock: extra gravity, less air drag, and a hard downward push added every tick up to a terminal speed. Fall distance is what powers a mace smash, so this is the setup move for everything else in the mace family. Releasing sneak or touching ground ends it.

**How to use it**

1. Learn Meteor Cadence in the Adapt menu.
2. Get airborne with a mace in your main hand.
3. Hold sneak while you are moving downward. Aim at what you want to hit.

### Breachwright (`kinetics-breachwright`)

Landing a mace smash strips armor points and armor toughness off the target for several seconds, so your follow-up swings land much harder. One target can only be shredded once every few seconds. Works on its own once learned.

### Windburst (`kinetics-windburst`)

A smash landed after a big enough fall sets off a shockwave that throws every nearby living thing away from you. Your own pets and mobs protected as friendly are skipped. You also get a moment of full explosion knockback resistance so the burst does not throw you. Higher levels widen the radius, add force, and lower the fall distance needed. Works on its own once learned, though you have to be falling to trigger it.

### Quake Guard (`kinetics-quake-guard`)

Every smash you land braces you for a couple of seconds: knockback resistance, extra armor toughness, and extra safe fall distance. It is the adaptation that lets you smash into a crowd without immediately being knocked out of it. Works on its own once learned.

### Rebound Anvil (`kinetics-rebound-anvil`)

After a smash, your legs stay coiled for a short window: your bounciness goes way up and fall damage is cut. Land inside that window and you spring back up, ready to line up the next dive. Works on its own once learned.

### Phalanx Reach (`kinetics-phalanx-reach`)

While a spear is in your main hand, your entity interaction range grows, so you hit things from farther away than the person swinging back at you. Drop the spear and the reach goes away. Works on its own once learned.

### Charge Lance (`kinetics-charge-lance`)

Spear hits scale with how fast you are actually moving. Below a minimum speed there is no bonus at all, so this rewards hitting at the end of a sprint or a lunge rather than standing still and poking. Does not apply while you are riding something; that is what Mounted Shock is for.

**How to use it**

1. Learn Charge Lance in the Adapt menu.
2. Hold a spear.
3. Hit the target while sprinting or right out of a lunge.

### Impale Pin (`kinetics-impale-pin`)

Land a spear hit in the sweet band, not point-blank and not at the edge of your reach, and the target gets hit with heavy Slowness. Higher levels widen the band, raise the slowness tier, and hold the pin longer. The same target cannot be re-pinned for a couple of seconds.

**How to use it**

1. Learn Impale Pin in the Adapt menu.
2. Hold a spear and keep the target a few blocks away.
3. Hit them from inside the sweet band shown in the menu.

### Lunge Conductor (`kinetics-lunge-conductor`)

Your spear lunges hit with more power and carry you farther forward. The forward assist is applied when the lunge resolves, with the upward part capped so it does not turn into a launch. There is a cooldown, so this improves deliberate lunges rather than spam.

### Mounted Shock (`kinetics-mounted-shock`)

Spear hits from the saddle scale with your mount's speed rather than your own. A galloping horse turns a jab into a real charge. This stacks with the Taming skill's mounted damage, which is why the bonus is capped. Works on its own once learned.

### Dead Zone (`kinetics-dead-zone`)

Something attacks you at knife range while you hold a spear and it gets shoved out and slightly up, back to where your spear works. The shove also arms a short riposte window: your next spear hit inside that window does bonus damage. Works on its own once learned.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `kinetics` |
| Class | `SkillKinetics` |
| Icon | `MACE` |
| Color | `GOLD` |
| Interval (ms) | `1000` |
| Skill config | `plugins/Adapt/adapt/skills/kinetics.toml` |
| Adaptation count | 18 |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/kinetics.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Turns the whole Kinetics skill on or off. |
| `skillColor` | `"&6"` | Legacy ampersand color code used for Kinetics in menus and text. |
| `cooldownDelay` | `1000` | Milliseconds between combat XP awards (smash, mace hit, spear jab, charge, mounted charge share one cooldown). |
| `smashHitXp` | `12` | XP for a mace smash attack that lands. |
| `plainMaceHitXp` | `3` | XP for an ordinary mace hit with no smash. |
| `spearJabXp` | `6` | XP for a spear hit inside the sweet range band. |
| `spearChargeXp` | `12` | XP for a spear hit that counts as charged. |
| `mountedChargeXp` | `14` | XP for a spear hit landed while riding a vehicle. |
| `sweetRangeMin` | `3.0` | Minimum distance in blocks for a spear jab reward. |
| `sweetRangeMax` | `6.0` | Maximum distance in blocks for a spear jab reward. |
| `chargeMinSpeed` | `0.18` | Horizontal speed in blocks per tick, while sprinting, that makes a spear hit count as charged. |
| `lungeChargeWindowMs` | `1200` | Milliseconds after a lunge during which any spear hit counts as charged regardless of speed. |
| `breakFallXpPerBlock` | `1.2` | XP per block of a survived fall of 3 blocks or more. |
| `breakFallCap` | `25` | Maximum XP from one broken fall. |
| `bounceXp` | `4` | XP for bouncing off a slime block, honey block, or bed. |
| `bounceChainBonus` | `2` | Extra XP per additional bounce in the same chain. |
| `bounceChainWindowMs` | `4000` | Milliseconds allowed between bounces to stay in one chain. |
| `bounceCap` | `20` | Maximum XP from one bounce. |
| `launchXp` | `4` | XP for being launched by a slime block or piston. |
| `launchMinDeltaY` | `0.6` | Upward movement in blocks, from a standstill or a fall, needed to count as a launch. |
| `motionRewardCooldownMs` | `1000` | Milliseconds between bounce or launch rewards. |
| `motionRewardMinDistance` | `1.5` | Horizontal blocks you must cover between bounce or launch rewards, which is what stops a fixed bounce farm. |
| `kbDealtBaseXp` | `3` | XP for knockback you deal at vanilla base magnitude; scales with actual magnitude. |
| `kbTakenBaseXp` | `1.5` | XP for knockback you take at vanilla base magnitude; scales with actual magnitude. |
| `kbMinMagnitude` | `0.25` | Knockback vector length below which nothing is paid. |
| `kbXpCap` | `12` | Maximum XP from one knockback event. |
| `kbCooldownMs` | `750` | Milliseconds between knockback rewards, shared by dealt and taken. |
| `selfKnockbackFactor` | `0.35` | Multiplier applied when you knocked yourself back. |
| `levitationReceiveXp` | `5` | Base XP when Levitation is applied to you; scaled by amplifier and duration. |
| `levitationApplyXp` | `5` | Base XP per target when you apply Levitation with a splash potion or lingering cloud. |
| `levitationPulseXp` | `0.8` | XP per skill interval while you are levitating. |
| `levitationXpCap` | `15` | Maximum XP from one levitation award. |
| `levitationCooldownMs` | `1500` | Milliseconds between levitation rewards. |
| `anvilBaseXp` | `20` | Flat starting value of an anvil crush payout. |
| `anvilFallFactor` | `6` | XP added per block the anvil fell. |
| `anvilHealthFactor` | `0.6` | Scales the payout by the victim's max health (health x factor / 20 added as a multiplier). |
| `anvilKillBonusMultiplier` | `1.5` | Multiplier used when the anvil got the kill; a non-kill uses damage dealt over max health instead, floored at 0.1. |
| `anvilPerEventCap` | `250` | Maximum XP from one anvil crush. |
| `anvilCooldownMs` | `4000` | Milliseconds between anvil payouts for the same player, and between share payouts. |
| `anvilLocationCooldownMs` | `8000` | Milliseconds before the same block position can pay out again, which is what stops stacked-anvil farms. |
| `anvilShareRadius` | `8` | Blocks around the victim searched for players to share with; at most 8 recipients. |
| `anvilShareFactor` | `0.35` | Fraction of the owner's payout each nearby player receives. |
| `anvilLedgerTtlMs` | `120000` | Milliseconds a placed-anvil ownership record stays valid. |
| `anvilAdvancementMinFall` | `8` | Minimum anvil fall distance in blocks for a kill to count toward the challenge. |
| `anvilDropReward` | `500` | XP paid by the anvil-drop challenge. |

### Milestones and challenges

| Challenge key | Stat key | Threshold | Reward |
|---------------|----------|-----------|--------|
| `challenge_kinetics_anvil_drop` | `kinetics.anvil.deep-kills` | 1 | `anvilDropReward` |

Other stats recorded but not tied to a challenge: `kinetics.smash.hits`, `kinetics.smash.shreds`, `kinetics.windburst.bursts`, `kinetics.rebound.windows`, `kinetics.meteor.dives`, `kinetics.lance.charges`, `kinetics.mounted.charges`.

Skill-level events: `EntityDamageByEntityEvent` (melee XP and anvil crush detection), `EntityDamageEvent` (broken falls), `PlayerMoveEvent` (bounces and launches), `EntityPotionEffectEvent`, `PotionSplashEvent`, `AreaEffectCloudApplyEvent` (levitation), `BlockPlaceEvent`, `EntityChangeBlockEvent`, `BlockPistonExtendEvent`, `BlockPistonRetractEvent`, `BlockBreakEvent`, `BlockExplodeEvent`, `EntityExplodeEvent`, `EntityRemoveEvent`, `EntityDeathEvent` (anvil ledger and crush settlement). A separate Paper-only companion listener adds `EntityAttemptSmashAttackEvent`, `EntityLungeEvent`, `EntityPushedByEntityAttackEvent`, and `EntityKnockbackEvent`; it is skipped entirely when those Paper classes are absent.

### Shared adaptation keys

Every adaptation TOML carries `enabled`, `permanent`, `showParticles`, `showSounds`, `baseCost`, `costFactor`, `maxLevel`, and `initialCost` on top of its own knobs. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Moon Jump

| Property | Default |
|----------|---------|
| Class | `KineticsMoonJump` |
| Icon | `RABBIT_FOOT` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-moon-jump.toml` |

Listened events: `PlayerJumpEvent`. The tick is learner-bound and reapplies the passive jump strength modifier.

Passive jump height is vanilla height plus 0.5 blocks per level, converted to a jump strength modifier by `KineticsJumpPhysics`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `jumpBonusBase` | `0.06` | Extra jump strength on a sneak-jump at level 0. |
| `jumpBonusFactor` | `0.10` | Extra sneak-jump strength added across levels. |
| `gravityReductionBase` | `0.15` | Fraction of gravity removed during the float window at level 0, 0 to 1. |
| `gravityReductionFactor` | `0.30` | Extra gravity reduction across levels. |
| `floatWindowTicksBase` | `20` | Float window length in ticks at level 0. |
| `floatWindowTicksFactor` | `20` | Extra float window ticks across levels. |

### Rubber Soul

| Property | Default |
|----------|---------|
| Class | `KineticsRubberSoul` |
| Icon | `SLIME_BALL` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-rubber-soul.toml` |

Listened events: `PlayerMoveEvent` (detects the landing). The learner-bound tick maintains the passive bounciness modifier and needs the bounciness attribute to exist.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bouncinessBase` | `0.15` | Bounciness attribute added at all times, at level 0. |
| `bouncinessFactor` | `0.35` | Extra passive bounciness across levels. |
| `softBlockBonusBase` | `0.3` | Extra bounciness after landing on slime, honey, or a bed, at level 0. |
| `softBlockBonusFactor` | `0.5` | Extra bouncy-block bounciness across levels. |
| `bonusWindowTicks` | `40` | How long the bouncy-block bonus lasts, in ticks. |

### Soft Catch

| Property | Default |
|----------|---------|
| Class | `KineticsSoftCatch` |
| Icon | `WHITE_WOOL` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-soft-catch.toml` |

Listened events: `PlayerMoveEvent` (records bounces), `EntityDamageEvent` at `HIGHEST` (reduces `FALL` damage only).

Soft landing surfaces are slime, honey, any bed, hay bale, powder snow, sponge, and wet sponge.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `reductionBase` | `0.35` | Fraction of fall damage removed at level 0, 0 to 1. |
| `reductionFactor` | `0.45` | Extra fall damage reduction across levels. |
| `postBounceGraceTicks` | `30` | Ticks after a bouncy landing during which any fall still gets the reduction. |
| `xpPerDamagePrevented` | `1.5` | Kinetics XP per half-heart of fall damage removed. |
| `xpPerEventCap` | `50` | Maximum XP from one softened fall. |

### Surface Skate

| Property | Default |
|----------|---------|
| Class | `KineticsSurfaceSkate` |
| Icon | `PACKED_ICE` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-surface-skate.toml` |

Listened events: `PlayerToggleSprintEvent`, `PlayerToggleSneakEvent`. The learner-bound tick reconciles both modifiers against your current state. Both are `MULTIPLY_SCALAR_1` modifiers on the friction attribute, negative for slide and positive for grip.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `slideFrictionBase` | `0.15` | Fraction of ground friction removed while sprinting, at level 0. |
| `slideFrictionFactor` | `0.35` | Extra friction reduction across levels. |
| `gripFrictionBase` | `0.2` | Fraction of extra ground friction added while sneaking, at level 0. |
| `gripFrictionFactor` | `0.4` | Extra grip across levels. |

### Terminal Toggle

| Property | Default |
|----------|---------|
| Class | `KineticsTerminalToggle` |
| Icon | `PHANTOM_MEMBRANE` |
| Max level | 3 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-terminal-toggle.toml` |

Listened events: `PlayerToggleSneakEvent` (flips mode), `PlayerMoveEvent` (refreshes the modifiers every move while airborne and clears them on landing).

Dive applies negative air drag and positive gravity; hang applies the opposite. Both are refreshed in 10-tick slices while the mode is held.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `dragDeltaBase` | `0.2` | Air drag shift at level 0, as a scalar fraction. |
| `dragDeltaFactor` | `0.4` | Extra air drag shift across levels. |
| `gravityDeltaBase` | `0.2` | Gravity shift at level 0, as a scalar fraction. |
| `gravityDeltaFactor` | `0.4` | Extra gravity shift across levels. |
| `minAirTicks` | `6` | Ticks you must already be airborne before a sneak press can toggle a mode. |

### Heavy Frame

| Property | Default |
|----------|---------|
| Class | `KineticsHeavyFrame` |
| Icon | `NETHERITE_CHESTPLATE` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-heavy-frame.toml` |

Listened events: `PlayerToggleSneakEvent`, `PlayerItemHeldEvent`. The learner-bound tick reconciles the stance so it cannot get stuck on.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `kbResistBase` | `0.3` | Knockback resistance added while planted, at level 0. |
| `kbResistFactor` | `0.5` | Extra knockback resistance across levels. |
| `explosionResistBase` | `0.3` | Explosion knockback resistance added while planted, at level 0. |
| `explosionResistFactor` | `0.5` | Extra explosion knockback resistance across levels. |
| `speedPenaltyBase` | `0.15` | Fraction of movement speed lost while planted, at level 0. |
| `speedPenaltyFactor` | `0.15` | Extra speed penalty across levels. |

### Mass Shift

| Property | Default |
|----------|---------|
| Class | `KineticsMassShift` |
| Icon | `TOTEM_OF_UNDYING` |
| Max level | 3 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 6 |
| Cost factor | 0.45 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-mass-shift.toml` |

Listened events: `PlayerSwapHandItemsEvent` (cancelled when it triggers a form change), `PlayerQuitEvent`, `PlayerDeathEvent` (both reset to Normal). The tick only runs while at least one player holds a form, and it refreshes the movement effect and reapplies modifiers after a level change.

Fixed values not exposed in config: the combat scalar is 0.2 up for Titan and 0.2 down for Pocket, applied to both attack damage and max health as `MULTIPLY_SCALAR_1`; Titan also gets step height `+1.0` and camera distance `+2.0`; the look threshold is 25 degrees of pitch; the form movement effect is Slowness I for Titan and Speed I for Pocket, refreshed in 60-tick slices. Health is clamped to the new maximum on every form change.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `titanScaleBase` | `0.25` | Scale attribute added in Titan form, at level 0. |
| `titanScaleFactor` | `0.35` | Extra Titan scale across levels. |
| `pocketScaleBase` | `0.25` | Scale attribute subtracted in Pocket form, at level 0. |
| `pocketScaleFactor` | `0.25` | Extra Pocket shrink across levels. |

### Meteor Cadence

| Property | Default |
|----------|---------|
| Class | `KineticsMeteorCadence` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-meteor-cadence.toml` |

Listened events: `PlayerToggleSneakEvent`, `PlayerMoveEvent`. Requires you to be airborne, moving downward, sneaking, and holding a mace. Modifiers are refreshed in 8-tick slices and the velocity push is applied at most once per game tick.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `gravityBoostBase` | `0.3` | Gravity increase while diving, at level 0, as a scalar fraction. |
| `gravityBoostFactor` | `0.6` | Extra gravity increase across levels. |
| `dragCutBase` | `0.2` | Air drag reduction while diving, at level 0. |
| `dragCutFactor` | `0.4` | Extra drag reduction across levels. |
| `downwardAccelerationBase` | `0.2` | Downward velocity added per tick while diving, at level 0; clamped to 2.0. |
| `downwardAccelerationFactor` | `0.3` | Extra per-tick downward push across levels. |
| `terminalFallSpeed` | `3.5` | Fastest downward speed this dive will push you to, in blocks per tick; clamped to 10. |

### Breachwright

| Property | Default |
|----------|---------|
| Class | `KineticsBreachwright` |
| Icon | `NETHERITE_SCRAP` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-breachwright.toml` |

Listened events: `EntityAttemptSmashAttackEvent`. PvP or PvE policy is checked against the target's location before the shred lands. The tick only clears expired per-target cooldowns.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `armorShredBase` | `2` | Armor points removed at level 0. |
| `armorShredFactor` | `4` | Extra armor points removed across levels. |
| `toughnessShredBase` | `1` | Armor toughness removed at level 0. |
| `toughnessShredFactor` | `3` | Extra toughness removed across levels. |
| `shredTicksBase` | `80` | Shred duration in ticks at level 0. |
| `shredTicksFactor` | `60` | Extra shred duration in ticks across levels. |
| `targetCooldownMs` | `3000` | Milliseconds before the same target can be shredded again. |

### Windburst

| Property | Default |
|----------|---------|
| Class | `KineticsWindburst` |
| Icon | `WIND_CHARGE` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-windburst.toml` |

Listened events: `EntityAttemptSmashAttackEvent`.

Hard limits not exposed in config: at most 32 candidate entities scanned, 16 actually thrown, and 12 given per-target particles. The caster gets `+1.0` explosion knockback resistance for 20 ticks. Tamed pets you own and mobs protected as friendly are skipped, and PvP or PvE policy is checked per target.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `2.5` | Shockwave radius in blocks at level 0. |
| `radiusFactor` | `2.5` | Extra radius in blocks across levels. |
| `forceBase` | `0.6` | Outward velocity applied to each target at level 0. |
| `forceFactor` | `0.8` | Extra outward velocity across levels. |
| `minFallDistanceBase` | `3` | Fall distance in blocks needed to trigger a burst at level 0. |
| `minFallDistanceFactor` | `-1` | Change to that requirement across levels; negative means higher levels need less height. |
| `cooldownMs` | `4000` | Milliseconds between bursts. |
| `xpPerBurst` | `8` | Kinetics XP per burst. |

### Quake Guard

| Property | Default |
|----------|---------|
| Class | `KineticsQuakeGuard` |
| Icon | `POLISHED_DEEPSLATE` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-quake-guard.toml` |

Listened events: `EntityAttemptSmashAttackEvent`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `kbResistBase` | `0.3` | Knockback resistance granted after a smash, at level 0. |
| `kbResistFactor` | `0.5` | Extra knockback resistance across levels. |
| `toughnessBase` | `2` | Armor toughness granted after a smash, at level 0. |
| `toughnessFactor` | `4` | Extra toughness across levels. |
| `safeFallBase` | `2` | Safe fall distance in blocks granted after a smash, at level 0. |
| `safeFallFactor` | `4` | Extra safe fall distance across levels. |
| `braceTicksBase` | `40` | Brace duration in ticks at level 0. |
| `braceTicksFactor` | `40` | Extra brace duration in ticks across levels. |

### Rebound Anvil

| Property | Default |
|----------|---------|
| Class | `KineticsReboundAnvil` |
| Icon | `SLIME_BLOCK` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.55 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-rebound-anvil.toml` |

Listened events: `EntityAttemptSmashAttackEvent`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `bouncinessBase` | `0.5` | Bounciness added during the window, at level 0. |
| `bouncinessFactor` | `0.6` | Extra bounciness across levels. |
| `fallReliefBase` | `0.4` | Fraction of the fall damage multiplier removed during the window, at level 0. |
| `fallReliefFactor` | `0.4` | Extra fall relief across levels. |
| `windowTicksBase` | `40` | Rebound window length in ticks at level 0. |
| `windowTicksFactor` | `30` | Extra window length in ticks across levels. |

### Phalanx Reach

| Property | Default |
|----------|---------|
| Class | `KineticsPhalanxReach` |
| Icon | `COPPER_SPEAR` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-phalanx-reach.toml` |

Listened events: `PlayerItemHeldEvent`. The learner-bound tick reconciles the modifier against whatever is currently in your main hand.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `reachBase` | `0.5` | Blocks of extra entity interaction range while holding a spear, at level 0. |
| `reachFactor` | `1.25` | Extra reach in blocks across levels. |

### Charge Lance

| Property | Default |
|----------|---------|
| Class | `KineticsChargeLance` |
| Icon | `IRON_SPEAR` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-charge-lance.toml` |

Listened events: `EntityDamageByEntityEvent`, resolved as a melee hit with a spear in the main hand. Skipped while the attacker is in a vehicle. Bonus is `min(cap, horizontalSpeed * factor)` applied as a damage multiplier.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `speedDamageFactorBase` | `0.8` | Bonus damage fraction per block-per-tick of horizontal speed, at level 0. |
| `speedDamageFactorFactor` | `1.2` | Extra speed-to-damage conversion across levels. |
| `minSpeed` | `0.18` | Horizontal speed in blocks per tick below which no bonus applies. |
| `bonusCapBase` | `0.5` | Ceiling on the bonus damage fraction at level 0. |
| `bonusCapFactor` | `0.75` | Extra ceiling across levels. |
| `cooldownMs` | `1500` | Milliseconds between charge bonuses. |

### Impale Pin

| Property | Default |
|----------|---------|
| Class | `KineticsImpalePin` |
| Icon | `COBWEB` |
| Max level | 5 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 4 |
| Cost factor | 0.45 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-impale-pin.toml` |

Listened events: `EntityDamageByEntityEvent`, resolved as a melee hit with a spear in the main hand. The tick only clears expired per-target cooldowns.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `sweetMin` | `3.0` | Minimum distance in blocks for a hit to pin. |
| `sweetMaxBase` | `5.0` | Maximum distance in blocks at level 0. |
| `sweetMaxFactor` | `1.5` | Extra maximum distance across levels. |
| `slowTierBase` | `0` | Slowness amplifier at level 0 (0 is Slowness I). |
| `slowTierFactor` | `2` | Extra amplifier across levels, rounded to a whole number. |
| `durationTicksBase` | `40` | Slowness duration in ticks at level 0; the result is floored at 10. |
| `durationTicksFactor` | `50` | Extra slowness duration in ticks across levels. |
| `targetCooldownMs` | `2500` | Milliseconds before the same target can be pinned again. |

### Lunge Conductor

| Property | Default |
|----------|---------|
| Class | `KineticsLungeConductor` |
| Icon | `FEATHER` |
| Max level | 3 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-lunge-conductor.toml` |

Listened events: `EntityLungeEvent` twice, once at `HIGHEST` (`on`, raises lunge power) and once at `MONITOR` (`finalizeLunge`, marks the cooldown and applies the forward velocity assist). The upward component of the assist is capped at 0.4.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `powerBonusBase` | `1` | Lunge power added at level 0, rounded to a whole number. |
| `powerBonusFactor` | `2` | Extra lunge power across levels. |
| `dashBoostBase` | `0.2` | Forward velocity added when the lunge resolves, at level 0. |
| `dashBoostFactor` | `0.3` | Extra forward velocity across levels. |
| `cooldownMs` | `2500` | Milliseconds between boosted lunges. |

### Mounted Shock

| Property | Default |
|----------|---------|
| Class | `KineticsMountedShock` |
| Icon | `SADDLE` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.55 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-mounted-shock.toml` |

Listened events: `EntityDamageByEntityEvent`, resolved as a melee hit with a spear in the main hand while riding a vehicle. Bonus is `min(cap, mountSpeed * factor)` applied as a damage multiplier. Stacks with Taming's mounted damage bonus.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `mountSpeedFactorBase` | `1.0` | Bonus damage fraction per block-per-tick of mount speed, at level 0. |
| `mountSpeedFactorFactor` | `1.5` | Extra speed-to-damage conversion across levels. |
| `bonusCapBase` | `0.4` | Ceiling on the bonus damage fraction at level 0. |
| `bonusCapFactor` | `0.6` | Extra ceiling across levels. |
| `cooldownMs` | `2000` | Milliseconds between boosted mounted charges. |

### Dead Zone

| Property | Default |
|----------|---------|
| Class | `KineticsDeadZone` |
| Icon | `ARMOR_STAND` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.6 |
| Tick interval (ms) | 9999 |
| Config file | `plugins/Adapt/adapt/adaptations/kinetics-dead-zone.toml` |

Listened events: `EntityDamageByEntityEvent`, handling both halves. The shove half fires when you are the victim, holding a spear, and the attacker is a living entity inside the dead zone; PvP or PvE policy is checked first. The riposte half fires on your next spear melee hit while the window is open and is consumed by that hit. Shoved attackers keep 20 percent of their velocity plus the shove, with a fixed 0.25 upward lift.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `deadZoneRangeBase` | `2.0` | Radius in blocks inside which attackers get shoved, at level 0. |
| `deadZoneRangeFactor` | `1.0` | Extra radius in blocks across levels. |
| `shoveForceBase` | `0.5` | Outward velocity applied to the attacker at level 0. |
| `shoveForceFactor` | `0.6` | Extra shove force across levels. |
| `riposteWindowTicks` | `30` | Ticks the boosted counterattack stays armed after a shove. |
| `riposteBonusBase` | `0.2` | Bonus damage fraction on the riposte at level 0. |
| `riposteBonusFactor` | `0.4` | Extra riposte bonus across levels. |
| `cooldownMs` | `3000` | Milliseconds between shoves. |

### Support classes (not player adaptations)

- `KineticsJumpPhysics` converts between jump strength, jump height, and the extra velocity needed for a target height.
- `KineticsAnvils` tracks player-placed anvils through falls and piston moves, then calculates the bounded crush and share payouts.
- `KineticsKnockback` validates knockback magnitude and calculates dealt, taken, and self-caused XP.
- `KineticsLevitation` calculates bounded levitation apply, receive, multi-target, and airtime-pulse XP.
- `KineticsMotion` classifies bouncy and soft surfaces and calculates broken-fall, launch, and bounce-chain rewards.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
