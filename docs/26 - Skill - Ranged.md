# Skill: Ranged

Ranged is the bow and thrown-projectile skill. Its id is `ranged`, it uses a crossbow icon in the menu, and it carries 12 adaptations. You level it by firing arrows and by landing projectile hits, with longer shots paying more.

The adaptations turn a bow from a poking tool into a real weapon. Force Shot makes arrows fly faster and flatter. Heavy Draw trades speed for damage. Arrow Piercing pushes shots through a line of mobs. Arrow Recovery hands arrows back so you stop running dry. Ricochet Bolt bounces shots off walls and gets stronger with every bounce.

Some of it is utility rather than damage. Trajectory Sight draws a dotted arc showing exactly where your shot lands. Fetch Shot pulls dropped items to you by shooting them. Web Snare is a crafted snowball that cages whatever it hits. Floaters and Pinning Shot are crowd control: one lifts targets off the ground, the other cuts their movement speed to a crawl.

Heartseeker is the top of the tree. Right-click a creature with a bow to lock on, and your next arrow curves after it around corners. With Arrow Piercing or Ricochet Bolt learned, that arrow keeps chaining to new targets after each hit.

## Earning XP

Firing an arrow, spectral arrow, or trident awards a flat amount of XP and counts a shot. Landing a projectile hit on a valid target awards XP from the damage dealt plus the distance the shot traveled, so a 40 block headshot is worth far more than a point-blank poke. Snowballs and fishing hooks do not count as hits.

Both XP paths share one cooldown, so rapid-firing does not multiply your income. Hits over 30 blocks count as longshots and have their own challenge chain, and kills count when you are holding a bow or crossbow as the target dies.

Individual adaptations grant their own XP on top of that: Ricochet Bolt pays per bounce, Fetch Shot per item, Floaters and Pinning Shot per proc, Heartseeker per seeking shot and per hit.

## Adaptations

Everything below only runs when you have learned the adaptation (level 1 or higher), the skill and the adaptation are both enabled, you are not in a blacklisted world or a blocked game mode, you hold the `adapt.use.<adaptation>` permission, and the protection plugins and region policy allow the action against that target or block. See `08 - Protection & Region Policy.md` and `04 - Commands & Permissions.md`. Learn and level everything from the Adapt menu (`/adapt`).

Adaptations that modify a projectile skip Heartseeker's seeking arrows, which run their own flight and damage logic.

### Force Shot (`ranged-force`)

Every projectile you launch leaves at higher velocity, which means flatter arcs and less lead on moving targets. It works on its own once learned. Landing a hit gives a small XP kick, and your first hit from over 30 blocks away grants a one-time bonus and a Long Shot advancement.

### Arrow Piercing (`ranged-piercing`)

Your arrows get extra vanilla pierce levels equal to your adaptation level, so they punch through targets instead of stopping at the first one. Line up a corridor of mobs and one shot hits all of them. It works on its own once learned.

### Arrow Recovery (`ranged-recovery`)

When one of your arrows hits a living target, there is a chance to get an arrow back in your inventory. The chance is a flat per-level table, reaching 80% at level 8. Arrows fired from an Infinity bow are excluded, since those are free already. It works on its own once learned.

### Lunge Shot (`ranged-lunge-shot`)

Firing an arrow while airborne shoves you backward, opposite your aim. Look down and it launches you up, look at a wall and it kicks you off it. It is a mobility tool built out of recoil.

1. Learn it and hold a bow or crossbow.
2. Get off the ground: jump, or fire mid-fall.
3. Fire an arrow. The kick scales with your level.

### Web Snare (`ranged-webshot`)

Web Snare gives you a crafted throwable that cages what it hits. Cobwebs appear at the impact point and around it, hold for a few seconds, then clean themselves up. While they are active they cannot be broken, exploded, or pushed by pistons, so nobody can farm free cobwebs off it.

1. Learn the adaptation to unlock the recipe.
2. Craft eight cobwebs around one snowball to make a bound snowball.
3. Throw it at a target or a surface. The webs land just above the impact point and last about one second per adaptation level.

### Trajectory Sight (`ranged-trajectory-sight`)

Trajectory Sight draws your shot before you take it: a dotted line through the air and a ring where it would land. It reads the weapon you are holding, so it previews arrows, crossbow bolts, tridents, snowballs, eggs, pearls, potions, and experience bottles with the right arc for each. The predicted target entity glows so you know what you are about to hit. If Force Shot or Ricochet Bolt are learned, the preview accounts for them, and with a Heartseeker lock it shows the curved seeking path instead.

1. Learn it and hold a bow, crossbow, trident, snowball, egg, ender pearl, potion, or experience bottle.
2. Draw the bow, or sneak with the projectile in either hand.
3. Aim. The line updates as you move. Releasing the shot, changing item, dropping it, or standing up ends the preview.

Higher levels stretch the prediction further out and add detail to the line. Kills made with a previewed shot are tracked for a challenge.

### Floaters (`ranged-floaters`)

Your projectiles can hit with Levitation, lifting the target off the ground where it cannot chase or fight back well. The chance, duration, and strength all scale with level, and at max level the effect reaches Levitation II. It works on its own once learned, and never applies to a protected target or to your own tamed animals.

### Pinning Shot (`ranged-pinning-shot`)

A pinned target loses most of its movement speed and, by default, has its horizontal momentum cut immediately, so a charging mob stops dead. Each target has a reapply cooldown so you cannot chain-lock one victim forever, and higher levels shorten it. It works on its own once learned.

### Ricochet Bolt (`ranged-ricochet-bolt`)

Shots that hit a block bounce off instead of sticking, and every bounce makes the projectile faster and adds flat damage to its next hit. Bank a shot around a corner and it lands harder than the straight one would have. Arrows always bounce; snowballs and eggs bounce too unless you turn that off. It works on its own once learned. Bounce count, speed gain, and damage gain all scale with level, and each bounce pays XP.

### Fetch Shot (`ranged-fetch-shot`)

Shoot a pile of dropped items and they come to you. It is for the lava-edge drop, the item over a ravine, and the loot on the wrong side of a mob pack.

1. Learn it and hold any projectile weapon.
2. Shoot at or near the dropped items.
3. Whatever fits goes into your inventory. Anything you have no room for stays on the ground.

The pickup radius grows with level. Each impact inspects a limited number of item entities and transfers a limited number of them, so shooting into a huge item pile stays cheap.

### Heavy Draw (`ranged-heavy-draw`)

Heavy Draw slows your projectiles down and makes them hit much harder. At level 1 the trade is bad on purpose: half your speed for a small damage bump. By max level the speed penalty has mostly gone away and the damage bonus is large. It applies to arrows, snowballs, and eggs, and it works on its own once learned.

Because vanilla arrow damage already scales with speed, the code divides the bonus back out for arrows so slowing them down does not cancel the gain.

### Heartseeker (`ranged-heartseeker`)

Heartseeker is a manual lock-on. Point a bow at a creature and right-click to mark it: it glows red for you alone. Fire and the arrow leaves normally, then bends toward the mark, weaving around blocks in its way, and keeps chasing until it connects or runs out of flight time. Every seeking shot puts your bow on a cooldown that shrinks as you level.

1. Learn it and hold a bow that is not on cooldown.
2. Look at a creature within lock range and right-click to lock. It starts glowing red for you.
3. Fire within the lock timeout. The arrow whistles and curves to the target.

With Arrow Piercing learned, or with Ricochet Bolt bounce capacity left, the arrow chains: it punches through the target, exits the far side, and bends toward a fresh nearby target. Without a new target it keeps flying straight. Ricochet passes keep their reflection, speed, damage, and rewards when a seeking arrow strikes a block.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `ranged` |
| Class | `SkillRanged` |
| Icon | `CROSSBOW` |
| Color | `DARK_GREEN` |
| Interval (ms) | `3044` |
| Skill config | `plugins/Adapt/adapt/skills/ranged.toml` |
| Adaptation count | 12 |

Hit XP formula, from `SkillRanged`: `hitDamageXPMultiplier * damage + distance * hitDistanceXPMultiplier`, where distance is measured from the shooter to the target at the moment of impact. Launch XP is the flat `shootXP`, awarded only for `AbstractArrow` projectiles (arrows, spectral arrows, tridents).

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/ranged.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Turns the whole Ranged skill off when false. |
| `skillColor` | `"&2"` | Legacy ampersand color code used for this skill in menus and text. |
| `shootXP` | `5` | XP granted per arrow, spectral arrow, or trident launched. |
| `cooldownDelay` | `1250` | Milliseconds between XP awards from shots and hits. |
| `hitDamageXPMultiplier` | `1.75` | XP granted per point of projectile damage dealt. |
| `hitDistanceXPMultiplier` | `1.2` | XP granted per block of distance between shooter and target on a hit. |
| `challengeRangedReward` | `500` | Base XP reward for the shots-fired challenge chain. |
| `challengeRangedDmgReward` | `500` | Base XP reward for the projectile-damage challenge chain. |
| `challengeRangedDistReward` | `500` | Base XP reward for the hit-distance challenge chain. |
| `challengeRangedKillsReward` | `500` | Base XP reward for the ranged-kills challenge chain. |
| `challengeRangedLongshotReward` | `500` | Base XP reward for the longshot challenge chain. |

### Skill milestones

| Advancement key | Stat key | Threshold | XP reward |
|-----------------|----------|-----------|-----------|
| `challenge_ranged_100` | `ranged.shotsfired` | 100 | `challengeRangedReward` |
| `challenge_ranged_1k` | `ranged.shotsfired` | 1000 | `challengeRangedReward` x 2 |
| `challenge_ranged_10k` | `ranged.shotsfired` | 10000 | `challengeRangedReward` x 5 |
| `challenge_ranged_dmg_1k` | `ranged.damage` | 1000 | `challengeRangedDmgReward` |
| `challenge_ranged_dmg_10k` | `ranged.damage` | 10000 | `challengeRangedDmgReward` x 3 |
| `challenge_ranged_dist_5k` | `ranged.distance` | 5000 | `challengeRangedDistReward` |
| `challenge_ranged_dist_50k` | `ranged.distance` | 50000 | `challengeRangedDistReward` x 3 |
| `challenge_ranged_kills_50` | `ranged.kills` | 50 | `challengeRangedKillsReward` |
| `challenge_ranged_kills_500` | `ranged.kills` | 500 | `challengeRangedKillsReward` x 3 |
| `challenge_longshot_25` | `ranged.longshots` | 25 | `challengeRangedLongshotReward` |
| `challenge_longshot_250` | `ranged.longshots` | 250 | `challengeRangedLongshotReward` x 3 |

Skill-level stats: `ranged.shotsfired` and `ranged.shotsfired.<projectile_type>` per launch, `ranged.damage` and `ranged.damage.<projectile_type>` per hit, `ranged.distance` and `ranged.distance.<projectile_type>` per hit, `ranged.longshots` for hits beyond 30 blocks, and `ranged.kills` for kills made while holding a `BOW` or `CROSSBOW`.

### Shared adaptation keys

Every adaptation TOML at `plugins/Adapt/adapt/adaptations/<id>.toml` carries `enabled`, `permanent`, `showParticles`, `showSounds`, plus the cost fields `baseCost`, `costFactor`, `maxLevel`, and `initialCost` listed per adaptation below.

### Force Shot

| Property | Value |
|----------|-------|
| Class | `RangedForce` |
| Icon | `TIPPED_ARROW` |
| Max level | 7 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 2 |
| Cost factor | 0.225 |
| Tick interval (ms) | 4900 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-force.toml` |
| Listened events | `ProjectileLaunchEvent` (`on`, NORMAL); `EntityDamageByEntityEvent` (`on`, NORMAL) |
| Stats | `ranged.force.long-range-hits` |
| Milestones | `challenge_ranged_force_500` at 500 long-range hits, 500 XP; `challenge_force_30` ("Long Shot") granted once on the first hit past 30 blocks |
| Menu lore | Projectile Speed |

Launch velocity is multiplied by `1 + (levelPercent * speedFactor)` for any projectile the player shoots. Each hit grants a flat 5 XP. A long-range hit is one where the horizontal distance squared exceeds 900, so more than 30 blocks of ground distance.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `speedFactor` | `1.135` | Extra launch speed at max level, as a fraction of the normal velocity. |
| `challengeRewardLongShotReward` | `2000` | One-time XP granted with the Long Shot advancement. |

### Arrow Piercing

| Property | Value |
|----------|-------|
| Class | `RangedPiercing` |
| Icon | `FLETCHING_TABLE` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Tick interval (ms) | 4791 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-piercing.toml` |
| Listened events | `ProjectileLaunchEvent` (`on`, NORMAL); `EntityDamageByEntityEvent` (`on`, HIGHEST) |
| Stats | `ranged.piercing.extra-hits` |
| Milestones | `challenge_ranged_piercing_500` at 500 extra hits, 400 XP; `challenge_ranged_piercing_4` granted once when a single arrow lands 4 hits |
| Menu lore | Pierce Targets |

The arrow's existing pierce level is increased by the adaptation level at launch, and the arrow is marked so the bonus is applied only once. Each launch grants a flat 5 XP. `ranged.piercing.extra-hits` counts only the second and later hits of an arrow. No adaptation-specific config knobs.

### Arrow Recovery

| Property | Value |
|----------|-------|
| Class | `RangedArrowRecovery` |
| Icon | `ARROW` |
| Max level | 8 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.78 |
| Tick interval (ms) | 1000 (framework default, never overridden) |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-recovery.toml` |
| Listened events | `EntityShootBowEvent` (`onEntityShootBow`, NORMAL); `ProjectileHitEvent` (`onProjectileHit`, NORMAL) |
| Stats | `ranged.arrow-recovery.arrows-recovered` |
| Milestones | `challenge_ranged_arrow_500` at 500 arrows, 300 XP; `challenge_ranged_arrow_10k` at 10000 arrows, 1000 XP |
| Menu lore | Chance to Recover Arrows on Hit/Kill; Chance: {chance} |

Only `Arrow` projectiles fired from a bow without Infinity are eligible, and the roll happens when the arrow hits an entity. The recovered arrow goes to the inventory, and drops at the player's feet when there is no room.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `hitChance` | `[10, 20, 30, 40, 50, 60, 70, 80]` | Recovery chance per level, in percent. Entry index is the level, clamped to the last entry. |

### Lunge Shot

| Property | Value |
|----------|-------|
| Class | `RangedLungeShot` |
| Icon | `RABBIT_HIDE` |
| Max level | 3 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Tick interval (ms) | 4859 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-lunge-shot.toml` |
| Listened events | `ProjectileLaunchEvent` (`on`, NORMAL) |
| Stats | `ranged.lunge-shot.lunges` |
| Milestones | `challenge_ranged_lunge_200` at 200 lunges, 300 XP; `challenge_ranged_lunge_2500` at 2500 lunges, 1000 XP |
| Menu lore | Recoil Burst Speed |

Only fires for `AbstractArrow` launches while the player is off the ground. The player's look direction times `levelPercent * factor` is subtracted from their velocity.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `factor` | `0.935` | Recoil speed at max level, in blocks per tick. |

### Web Snare

| Property | Value |
|----------|-------|
| Class | `RangedWebBomb` |
| Icon | `COBWEB` |
| Max level | 5 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 5 |
| Cost factor | 0.9 |
| Tick interval (ms) | 4900 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-webshot.toml` |
| Listened events | `ProjectileLaunchEvent` (`on`, MONITOR); `ProjectileHitEvent` (`on`, NORMAL); `EntityRemoveEvent` (`on`, MONITOR); `ChunkLoadEvent` (`on`, MONITOR); `BlockPistonExtendEvent` (`on`, HIGHEST); `BlockPistonRetractEvent` (`on`, HIGHEST); `BlockExplodeEvent` (`on`, HIGHEST); `BlockBreakEvent` (`on`, HIGHEST); `EntityExplodeEvent` (`on`, HIGHEST) |
| Stats | `ranged.web-bomb.mobs-trapped` |
| Milestone | `challenge_ranged_web_200` at 200 mobs trapped, 300 XP |
| Menu lore | 8 Cobwebs around a Snowball, and throw!; seconds of a cage, roughly. |
| Recipe | Shaped `ranged-web-bomb`: 8 `COBWEB` around 1 `SNOWBALL`, produces a bound snowball item |

Placement footprint is 7 blocks: the impact block one above the hit, plus its six direct neighbors. Cage lifetime is `level * 20` ticks. Every cobweb target must pass a block-place probe at commit, and on Folia the whole footprint must belong to the current region or the impact is dropped. Placed webs are journaled into chunk persistent data (up to 4096 per chunk) so they are still removed after a restart, with recovery processing at most 32 chunks per tick. Active webs cancel `BlockBreakEvent` and piston moves, and are stripped out of explosion block lists. No adaptation-specific config knobs.

### Trajectory Sight

| Property | Value |
|----------|-------|
| Class | `RangedTrajectorySight` |
| Icon | `SPYGLASS` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.75 |
| Tick interval (ms) | 1000 (framework default, never overridden) |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-trajectory-sight.toml` |
| Listened events | `PlayerQuitEvent`, `PlayerChangedWorldEvent`, `PlayerDeathEvent`, `PlayerDropItemEvent`, `PlayerInteractEvent`, `PlayerItemHeldEvent`, `PlayerSwapHandItemsEvent`, `PlayerToggleSneakEvent`, `EntityShootBowEvent`, `ProjectileLaunchEvent`, `EntityDeathEvent` (all `on`); `PlayerStopUsingItemEvent` via a companion listener registered only when the Paper class exists |
| Stats | `ranged.trajectory-sight.kills-while-aiming` |
| Milestone | `challenge_ranged_trajectory_100` at 100 kills while aiming, 400 XP |
| Menu lore | Prediction Range; Prediction Detail |

Preview triggers: drawing a bow, or sneaking with `BOW`, `CROSSBOW`, `TRIDENT`, `SNOWBALL`, `EGG`, `ENDER_PEARL`, `SPLASH_POTION`, `LINGERING_POTION`, or `EXPERIENCE_BOTTLE` in either hand. Bow previews use the actual draw charge; when the hand is not raised and the player is sneaking, `sneakPreviewChargeTicks` is assumed instead.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `segmentsBase` | `18` | Simulation steps in the previewed path before the level bonus. |
| `segmentsFactor` | `26` | Simulation steps added at max level. |
| `velocityBase` | `1.0` | Multiplier on the simulated launch speed before the level bonus. |
| `velocityFactor` | `0.18` | Extra launch speed multiplier at max level. |
| `gravityStep` | `0.05` | Downward speed added per simulation step for arrows and tridents, in blocks per tick. |
| `dragFactor` | `0.99` | Fraction of speed kept per simulation step for arrows and tridents. |
| `lightProjectileDragFactor` | `0.99` | Speed kept per step for snowballs, eggs, and pearls. |
| `heavyProjectileDragFactor` | `0.99` | Speed kept per step for potions and experience bottles. |
| `lightProjectileGravityStep` | `0.03` | Downward speed added per step for snowballs, eggs, and pearls. |
| `heavyProjectileGravityStep` | `0.05` | Downward speed added per step for potions and experience bottles. |
| `crossbowVelocity` | `3.15` | Simulated crossbow launch speed, in blocks per tick. |
| `tridentVelocity` | `2.5` | Simulated trident launch speed, in blocks per tick. |
| `thrownProjectileVelocity` | `1.5` | Simulated launch speed for snowballs, eggs, and pearls. |
| `thrownPotionVelocity` | `0.5` | Simulated launch speed for potions and experience bottles. |
| `heavyProjectilePitchDrop` | `0.12` | Extra downward aim offset applied to heavy thrown previews. |
| `fallbackVelocity` | `1.6` | Simulated launch speed for anything not matched above. |
| `sneakPreviewChargeTicks` | `16` | Bow charge assumed when previewing while sneaking without drawing, in ticks. |
| `particleSize` | `0.18` | Dust size of the preview dots at the viewer. |
| `particleSizePerBlock` | `0.008` | Dust size added per block of distance from the viewer. |
| `maxParticleSize` | `0.55` | Cap on preview dot size. |
| `impactParticleCount` | `2` | Particles drawn at the predicted impact point. |
| `previewPointSpacing` | `0.7` | Distance between preview dots, in blocks. |
| `impactRingRadius` | `0.35` | Radius of the ring drawn where the shot would land, in blocks. |
| `minPreviewDistanceFromEye` | `1.6` | Distance from the eye before preview dots start drawing, in blocks. |
| `previewStartOffset` | `0.55` | Distance forward from the eye where the simulation starts, in blocks. |
| `glowPredictedTarget` | `true` | Highlights the predicted hit entity with a glow only the aiming player sees. |
| `previewRenderIntervalMillis` | `75` | Minimum milliseconds between renders when aim and context have not changed. |
| `activeSessionIntervalMillis` | `100` | Milliseconds between aiming-session refreshes, clamped to 75-100. |
| `previewYawDeltaDegrees` | `1.2` | Yaw change that forces an early recompute, in degrees. |
| `previewPitchDeltaDegrees` | `1.2` | Pitch change that forces an early recompute, in degrees. |
| `previewPositionDeltaSquared` | `0.0125` | Squared movement distance that forces an early recompute. |
| `minimumRenderedSegments` | `8` | Floor on rendered simulation segments. |
| `maxRenderedSegments` | `36` | Cap on rendered simulation segments. |
| `previewHighLoadPercent` | `42` | Ticker load percentage above which segment count is scaled down. |
| `previewHighLoadSegmentScale` | `0.7` | Segment multiplier applied while high-load shedding is active. |

### Floaters

| Property | Value |
|----------|-------|
| Class | `RangedFloaters` |
| Icon | `SHULKER_SHELL` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.78 |
| Tick interval (ms) | 2400 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-floaters.toml` |
| Listened events | `ProjectileLaunchEvent` (`on`, MONITOR); `EntityDamageByEntityEvent` (`on`, MONITOR) |
| Stats | `ranged.floaters.targets-levitated` |
| Milestone | `challenge_ranged_floaters_200` at 200 targets, 300 XP |
| Menu lore | Levitation Chance; Levitation Duration; Levitation Strength |

The level and owner are stamped onto the projectile's persistent data at launch, so the effect follows that shot even if the shooter changes level or logs out. Protected targets and the shooter's own tamed animals are skipped.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `chanceBase` | `0.12` | Levitation chance before the level bonus, 0-1. |
| `chanceFactor` | `0.58` | Levitation chance added at max level, 0-1. |
| `maxChance` | `0.8` | Cap on the levitation chance, 0-1. |
| `durationTicksBase` | `26.0` | Levitation duration before the level bonus, in ticks. |
| `durationTicksFactor` | `110.0` | Levitation ticks added at max level. Applied duration is at least 20 ticks. |
| `maxAmplifier` | `1.0` | Highest Levitation amplifier. Amplifier is `floor(levelPercent * maxAmplifier)`, so Levitation I below max level and Levitation II at max level. |
| `skillXpOnProc` | `8.0` | Ranged XP granted to the shooter each time levitation lands. |

### Pinning Shot

| Property | Value |
|----------|-------|
| Class | `RangedPinningShot` |
| Icon | `TRIPWIRE_HOOK` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.74 |
| Tick interval (ms) | 2200 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-pinning-shot.toml` |
| Listened events | `ProjectileLaunchEvent` (`on`, MONITOR); `EntityDamageByEntityEvent` (`on`, MONITOR) |
| Stats | `ranged.pinning-shot.targets-pinned` |
| Milestone | `challenge_ranged_pinning_300` at 300 targets, 400 XP |
| Menu lore | Pin Chance; Pin Duration; Reapply Cooldown |

The pin is a timed negative `MOVEMENT_SPEED` modifier, not a Slowness potion effect. The scalar is `-min(1.0, 0.15 * (amplifier + 1))`, so -30% at level 1 and -60% at level 6 with the defaults. Level and owner are stamped onto the projectile at launch. Protected targets and the shooter's own tamed animals are skipped.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `dampenVelocityOnProc` | `true` | When true, the target's horizontal velocity is cut the moment the pin lands. |
| `procChanceBase` | `0.12` | Pin chance before the level bonus, 0-1. |
| `procChanceFactor` | `0.42` | Pin chance added at max level, 0-1. |
| `maxProcChance` | `0.65` | Cap on the pin chance, 0-1. |
| `durationTicksBase` | `30` | Pin duration before the level bonus, in ticks. |
| `durationTicksFactor` | `90` | Pin ticks added at max level. Applied duration is at least 20 ticks. |
| `amplifierBase` | `1` | Slow amplifier before the level bonus. |
| `amplifierFactor` | `2` | Slow amplifier added at max level. |
| `reapplyCooldownMillisBase` | `5000` | Milliseconds before the same target can be pinned again, before the level reduction. |
| `reapplyCooldownMillisFactor` | `2800` | Milliseconds removed from the reapply cooldown at max level. Floor is 1000. |
| `horizontalVelocityFactor` | `0.15` | Multiplier applied to the target's X and Z velocity on the proc. |
| `cleanupThreshold` | `128` | Tracked targets before expired pin timestamps are swept. |
| `entryTtlMillis` | `60000` | Age at which a tracked pin timestamp is dropped during a sweep, in milliseconds. |
| `xpOnProc` | `12` | Ranged XP granted to the shooter each time a pin lands. |

### Ricochet Bolt

| Property | Value |
|----------|-------|
| Class | `RangedRicochetBolt` |
| Icon | `SPECTRAL_ARROW` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.74 |
| Tick interval (ms) | 1400 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-ricochet-bolt.toml` |
| Listened events | `ProjectileLaunchEvent` (`on`, MONITOR); `ProjectileHitEvent` (`on`, HIGHEST); `EntityDamageByEntityEvent` (`on`, HIGHEST); `EntityDeathEvent` (`on`, NORMAL) |
| Stats | `ranged.ricochet-bolt.total-ricochets`, `ranged.ricochet-bolt.ricochet-kills` |
| Milestones | `challenge_ranged_ricochet_kills_50` at 50 kills, 500 XP; `challenge_ranged_ricochet_kills_500` at 500 kills, 2000 XP |
| Menu lore | Max Ricochets; Speed Bonus Per Ricochet; Bonus Damage Per Ricochet |

Bounces are capped at 12 regardless of config. A bounce replaces the projectile with a new one carrying the accumulated count, speed, and bonus damage; Heartseeker's seeking arrows are excluded. XP per bounce is `xpPerRicochet + (count * xpPerRicochetStep)`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxRicochetsBase` | `1` | Bounces allowed before the level bonus. |
| `maxRicochetsFactor` | `3` | Bounces added at max level. Hard cap is 12. |
| `speedBonusPerRicochetBase` | `0.08` | Speed added per bounce before the level bonus, as a fraction. |
| `speedBonusPerRicochetFactor` | `0.27` | Speed per bounce added at max level, as a fraction. |
| `maxSpeedBonusPerRicochet` | `0.4` | Cap on the speed gained per bounce, as a fraction. |
| `damageBonusPerRicochetBase` | `0.55` | Damage added per bounce before the level bonus, in health points (2 = 1 heart). |
| `damageBonusPerRicochetFactor` | `2.55` | Damage per bounce added at max level, in health points. |
| `maxDamageBonusPerRicochet` | `3.65` | Cap on the damage gained per bounce, in health points. |
| `minRicochetVelocitySquared` | `0.09` | Squared impact speed below which a projectile no longer bounces. |
| `minimumLiveVelocitySquared` | `0.0004` | Squared speed below which a bounced projectile is treated as dead. |
| `minimumPostBounceSpeed` | `0.45` | Floor applied to speed after a bounce, in blocks per tick. |
| `spawnOffsetFromSurface` | `0.22` | Distance off the struck face where the bounced projectile respawns, in blocks. |
| `spawnOffsetAlongDirection` | `0.14` | Extra distance along the new heading where it respawns, in blocks. |
| `sparkParticleCount` | `18` | Spark particles emitted at a bounce. |
| `sparkSpread` | `0.18` | Spread of the bounce spark particles, in blocks. |
| `critParticleCount` | `10` | Crit particles emitted at a bounce. |
| `critSpread` | `0.14` | Spread of the bounce crit particles, in blocks. |
| `bouncePitchBase` | `1.35` | Pitch of the anvil bounce sound on the first bounce. |
| `bouncePitchDropPerRicochet` | `0.08` | Pitch removed from the bounce sound per accumulated bounce. |
| `sparkPitchBase` | `1.05` | Pitch of the spark sound on the first bounce. |
| `sparkPitchRaisePerRicochet` | `0.07` | Pitch added to the spark sound per accumulated bounce. |
| `xpPerRicochet` | `6` | Ranged XP granted per bounce. |
| `xpPerRicochetStep` | `2` | Extra XP per bounce already made by that projectile. |
| `applyToAllProjectiles` | `true` | When true, snowballs and eggs bounce as well. Arrows always bounce. |

### Fetch Shot

| Property | Value |
|----------|-------|
| Class | `RangedFetchShot` |
| Icon | `FISHING_ROD` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.3 |
| Tick interval (ms) | 2751 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-fetch-shot.toml` |
| Listened events | `ProjectileHitEvent` (`on`, MONITOR) |
| Stats | `ranged.fetch-shot.items-fetched` |
| Milestones | `challenge_ranged_fetch_500` at 500 items, 400 XP; `challenge_ranged_fetch_5k` at 5000 items, 1500 XP |
| Menu lore | Fetch Radius |

Fish hooks and Heartseeker arrows never fetch. Each candidate must pass the normal pickup event sequence with the player's real remaining capacity; a cancelled pickup leaves the item entity alone. On Folia the scan runs only when the whole footprint belongs to the current region. Radius is `radiusBase + (levelPercent * radiusFactor)` blocks.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `1.6` | Fetch radius before the level bonus, in blocks. |
| `radiusFactor` | `2.4` | Fetch radius added at max level, in blocks. |
| `xpPerItemFetched` | `3` | Ranged XP granted per item entity pulled in. |
| `maxCandidatesPerActivation` | `16` | Item entities inspected per impact. Hard cap 32. |
| `maxAffectedPerActivation` | `8` | Item entities transferred per impact. Hard cap 16, and never above the candidate limit. |
| `maxTargetFxPerActivation` | `3` | Successful fetches that get their own trail effect. Hard cap 8. |

### Heavy Draw

| Property | Value |
|----------|-------|
| Class | `RangedHeavyDraw` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Tick interval (ms) | 3277 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-heavy-draw.toml` |
| Listened events | `ProjectileLaunchEvent` (`on`, HIGH); `EntityDamageByEntityEvent` (`on`, HIGHEST) |
| Stats | `ranged.heavy-draw.heavy-hits` |
| Milestones | `challenge_ranged_heavy_hits_250` at 250 hits, 500 XP; `challenge_ranged_heavy_hits_2500` at 2500 hits, 2000 XP |
| Menu lore | Bonus Damage; Projectile Speed |

Applies to `AbstractArrow`, `Snowball`, and `Egg` launches. Both the speed penalty and the damage bonus interpolate linearly from the level 1 value to the max level value. Damage multiplier is `1 + damageBonus`, divided by the velocity factor for arrows other than tridents so the vanilla speed-scaled damage does not eat the bonus.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `velocityPenaltyStart` | `0.5` | Fraction of launch speed removed at level 1. |
| `velocityPenaltyEnd` | `0.1` | Fraction of launch speed removed at max level. |
| `damageBonusStart` | `0.1` | Damage bonus at level 1, as a fraction of base damage. |
| `damageBonusEnd` | `1.5` | Damage bonus at max level, as a fraction of base damage. |
| `xpPerHeavyHit` | `4` | Ranged XP granted per heavy hit landed. |

### Heartseeker

| Property | Value |
|----------|-------|
| Class | `RangedHeartseeker` |
| Icon | `TARGET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 6 |
| Cost factor | 0.6 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-heartseeker.toml` |
| Listened events | `PlayerInteractEvent` (`on`, MONITOR); `EntityShootBowEvent` (`on`, LOWEST); `EntityAddToWorldEvent` (`on`); `ProjectileHitEvent` (`on`); `EntityDamageByEntityEvent` (`on`); `EntityRemoveEvent` (`on`); `PlayerQuitEvent` (`on`) |
| Stats | `ranged.heartseeker.seeks`, `ranged.heartseeker.hits` |
| Milestones | `challenge_ranged_heartseeker_100` at 100 hits, 500 XP; `challenge_ranged_heartseeker_1k` at 1000 hits, 2000 XP |
| Menu lore | Draw a bow while looking at a creature to lock on; Bow cooldown after a seeking shot; Piercing and Ricochet Bolt bounce capacity add seeking passes |

Locking requires a `BOW` in hand that is not on cooldown. Tridents never seek. Ray traces, target snapshots, launches, and steering updates all run against per-owner and global work budgets, so heavy use degrades gracefully rather than stalling the server.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `lockRange` | `32` | Maximum distance at which drawing a bow can lock a creature, in blocks. |
| `lockTimeoutMillis` | `6000` | Milliseconds a lock stays valid before the shot. |
| `turnDegreesPerTick` | `10` | Maximum degrees the arrow turns toward its target per tick. |
| `lungeTurnDegreesPerTick` | `18` | Maximum degrees per tick during the final approach. |
| `initialArcControlDistance` | `8` | Distance ahead of the shooter used as the control point for the launch arc, in blocks. |
| `initialArcDistance` | `12` | Distance flown before the launch arc hands over to full homing, in blocks. |
| `lungeRadius` | `2.5` | Distance at which the arrow commits to a straight lunge, in blocks. |
| `maxFlightTicksPerPass` | `160` | Maximum ticks one seeking pass may fly before giving up. |
| `reseekRadius` | `12` | Radius searched for the next target after a target is lost or hit, in blocks. |
| `stuckTicks` | `25` | Ticks without closing distance before the arrow retargets or gives up. |
| `trailSpacing` | `0.35` | Distance between trail particles along the flight path, in blocks. |
| `trailCoreSize` | `1.6` | Dust size of the core trail particles. |
| `maxTrailPointsPerUpdate` | `20` | Trail points emitted per steering update. |
| `rayLookahead` | `10` | Distance checked ahead for block avoidance, in blocks. |
| `maxAvoidanceRays` | `4` | Alternate avoidance rays tested after the direct path is blocked. |
| `avoidanceStrength` | `1.15` | Width of the avoidance cone used when the path meets a block. |
| `avoidanceHoldUpdates` | `4` | Steering updates that keep the chosen route around an obstacle. |
| `avoidanceClearChecks` | `2` | Consecutive clear-path checks needed before dropping a remembered route. |
| `targetRefreshMillis` | `50` | Milliseconds between target position snapshots. |
| `maxCandidatesPerReseek` | `24` | Nearby entities inspected during one reseek. |
| `maxCandidateHandoffsPerReseek` | `8` | Candidate snapshot handoffs during one reseek. |
| `maxChainPasses` | `8` | Chained seeking passes inherited from Piercing and Ricochet Bolt. |
| `continuationExitDistance` | `8` | Distance a chained arrow flies past a struck target before it may turn, in blocks. |
| `continuationExitOffset` | `0.35` | Distance beyond the struck target's hitbox where a chained arrow reappears, in blocks. |
| `cooldownTicksStart` | `60` | Bow cooldown after a seeking shot at level 1, in ticks. |
| `cooldownTicksEnd` | `10` | Bow cooldown after a seeking shot at max level, in ticks. |
| `xpPerSeek` | `8` | Ranged XP granted when a seeking shot is admitted. |
| `xpPerHit` | `4` | Ranged XP granted per seeking arrow connection. |

### Support classes (not player adaptations)

- `HeartseekerRuntime` coordinates queued arrows, per-owner and global work budgets, chain passes, and chunk traversal for Heartseeker.
- `RicochetRuntime` calculates ricochet transitions and preserves projectile state across projectile replacement and Folia region handoff.
- `TrajectorySightRuntime` limits concurrent trajectory previews and invalidates stopped or replaced preview sessions.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
