# Skill: Ranged

Skill id `ranged`. Earn XP by landing projectile hits. Ranged has 12 registered adaptations and uses the `CROSSBOW` icon.

**XP sources:** landing player-fired projectile hits and kills.

**Milestones / challenges** (stat keys):

- `challenge_ranged_100` tracking `ranged.shotsfired`
- `challenge_ranged_1k` tracking `ranged.shotsfired`
- `challenge_ranged_10k` tracking `ranged.shotsfired`
- `challenge_ranged_dmg_1k` tracking `ranged.damage`
- `challenge_ranged_dmg_10k` tracking `ranged.damage`
- `challenge_ranged_dist_5k` tracking `ranged.distance`
- `challenge_ranged_dist_50k` tracking `ranged.distance`
- `challenge_ranged_kills_50` tracking `ranged.kills`
- `challenge_ranged_kills_500` tracking `ranged.kills`
- `challenge_longshot_25` tracking `ranged.longshots`
- `challenge_longshot_250` tracking `ranged.longshots`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `ranged` |
| Class | `SkillRanged` |
| Icon | `CROSSBOW` |
| Color | `DARK_GREEN` |
| Interval (ms) | `3044` |
| Skill config | `plugins/Adapt/adapt/skills/ranged.toml` |
| Adaptation count | 12 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/ranged.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&2"` | Legacy ampersand color code used for this skill in menus and text. |
| `shootXP` | `5` | XP awarded for shoot. |
| `cooldownDelay` | `1250` | Minimum delay between passive skill XP awards, in milliseconds. |
| `hitDamageXPMultiplier` | `1.75` | Unitless multiplier applied to XP from hit damage multiplier. |
| `hitDistanceXPMultiplier` | `1.2` | Unitless multiplier applied to XP from hit distance multiplier. |
| `challengeRangedReward` | `500` | Reward for the ranged challenge. |
| `challengeRangedDmgReward` | `500` | Reward for the ranged damage challenge. |
| `challengeRangedDistReward` | `500` | Reward for the ranged dist challenge. |
| `challengeRangedKillsReward` | `500` | Reward for the ranged kills challenge. |
| `challengeRangedLongshotReward` | `500` | Reward for the ranged longshot challenge. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Force Shot (`ranged-force`)

Shoot projectiles further, faster.

**Runtime entry points:** on melee/projectile hit (damage); when launching a projectile; periodic evaluation every 4900 ms.

**Menu displays:** Projectile Speed.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RangedForce` |
| Icon | `TIPPED_ARROW` |
| Max level | 7 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 2 |
| Cost factor | 0.225 |
| Tick interval (ms) | 4900 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-force.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `ProjectileLaunchEvent` (`on`) — when launching a projectile

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `challengeRewardLongShotReward` | `2000` | Reward for the reward long shot challenge. |
| `speedFactor` | `1.135` | Speed factor. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Arrow Piercing (`ranged-piercing`)

Adds piercing so projectiles can pass through targets.

**Runtime entry points:** when launching a projectile; on melee/projectile hit (damage); periodic evaluation every 4791 ms.

**Menu displays:** Pierce Targets.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RangedPiercing` |
| Icon | `FLETCHING_TABLE` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Tick interval (ms) | 4791 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-piercing.toml` |

Listened events:

- `ProjectileLaunchEvent` (`on`) — when launching a projectile
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Arrow Recovery (`ranged-recovery`)

Recovers arrows after a projectile kill.

**Runtime entry points:** when shooting a bow/crossbow; when a projectile hits.

**Menu displays:** Arrow-recovery chance after a hit or kill.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RangedArrowRecovery` |
| Icon | `ARROW` |
| Max level | 8 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.78 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-recovery.toml` |

Listened events:

- `EntityShootBowEvent` (`onEntityShootBow`) — when shooting a bow/crossbow
- `ProjectileHitEvent` (`onProjectileHit`) — when a projectile hits

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `hitChance` | `[10, 20, 30, 40, 50, 60, 70, 80]` | Per-level hit-chance percentages. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Lunge Shot (`ranged-lunge-shot`)

While airborne, firing arrows kicks you backward, away from your aim.

**Runtime entry points:** when launching a projectile; periodic evaluation every 4859 ms.

**Menu displays:** Recoil Burst Speed.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RangedLungeShot` |
| Icon | `RABBIT_HIDE` |
| Max level | 3 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Tick interval (ms) | 4859 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-lunge-shot.toml` |

Listened events:

- `ProjectileLaunchEvent` (`on`) — when launching a projectile

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `factor` | `0.935` | Factor. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Web Snare (`ranged-webshot`)

Thrown web shots surround the hit target with cobwebs. Every cobweb target must pass a placement event at
commit; Folia rejects an impact whose placement footprint does not share the player's current region.

**Runtime entry points:** when a projectile hits; when launching a projectile; on `EntityRemoveEvent`; on `ChunkLoadEvent`; on `BlockPistonExtendEvent`; on `BlockPistonRetractEvent`; on `BlockExplodeEvent`; when breaking blocks.

**Menu displays:** Craft with eight cobwebs around a snowball, then throw it; approximate cage duration in seconds.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RangedWebBomb` |
| Icon | `COBWEB` |
| Max level | 5 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 5 |
| Cost factor | 0.9 |
| Tick interval (ms) | 4900 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-webshot.toml` |

Listened events:

- `ProjectileHitEvent` (`on`) — when a projectile hits
- `ProjectileLaunchEvent` (`on`) — when launching a projectile
- `EntityRemoveEvent` (`on`)
- `ChunkLoadEvent` (`on`)
- `BlockPistonExtendEvent` (`on`)
- `BlockPistonRetractEvent` (`on`)
- `BlockExplodeEvent` (`on`)
- `BlockBreakEvent` (`on`) — when breaking blocks
- `EntityExplodeEvent` (`on`)

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Trajectory Sight (`ranged-trajectory-sight`)

Sneak or draw a ranged weapon to preview projectile flight as a clear dotted line with a ringed impact marker. With a Heartseeker lock active, the preview shows the arrow's curved seeking path to the mark instead.

**Runtime entry points:** on world change; on player death; on drop item; on `PlayerStopUsingItemEvent`; on block/entity/air interact (click); on `PlayerItemHeldEvent`; on swap hands (F); on sneak toggle.

**Menu displays:** Prediction Range; Prediction Detail.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RangedTrajectorySight` |
| Icon | `SPYGLASS` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.75 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-trajectory-sight.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)
- `PlayerChangedWorldEvent` (`on`) — on world change
- `PlayerDeathEvent` (`on`) — on player death
- `PlayerDropItemEvent` (`on`) — on drop item
- `PlayerStopUsingItemEvent` (`on`)
- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerItemHeldEvent` (`on`)
- `PlayerSwapHandItemsEvent` (`on`) — on swap hands (F)
- `PlayerToggleSneakEvent` (`on`) — on sneak toggle
- `EntityShootBowEvent` (`on`) — when shooting a bow/crossbow
- `ProjectileLaunchEvent` (`on`) — when launching a projectile
- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `segmentsBase` | `18` | Base Segments. |
| `segmentsFactor` | `26` | Segments factor. Unitless multiplier. |
| `velocityBase` | `1.0` | Base Velocity. |
| `velocityFactor` | `0.18` | Velocity factor. Unitless multiplier. |
| `gravityStep` | `0.05` | Gravity step. |
| `dragFactor` | `0.99` | Drag factor. Unitless multiplier. |
| `lightProjectileDragFactor` | `0.99` | Drag factor used for lighter thrown projectiles (snowballs, eggs, pearls). |
| `heavyProjectileDragFactor` | `0.99` | Drag factor used for heavier thrown projectiles (potions, experience bottles). |
| `lightProjectileGravityStep` | `0.03` | Gravity step used for lighter thrown projectiles (snowballs, eggs, pearls). |
| `heavyProjectileGravityStep` | `0.05` | Gravity step used for heavier thrown projectiles (potions, experience bottles). |
| `crossbowVelocity` | `3.15` | Crossbow preview projectile velocity, in blocks per tick. |
| `tridentVelocity` | `2.5` | Launch velocity used for trident previews while sneaking. |
| `thrownProjectileVelocity` | `1.5` | Launch velocity used for light thrown projectile previews. |
| `thrownPotionVelocity` | `0.5` | Launch velocity used for potion and experience bottle previews. |
| `heavyProjectilePitchDrop` | `0.12` | Additional downward launch offset for heavy thrown projectile previews. |
| `fallbackVelocity` | `1.6` | Fallback preview projectile velocity, in blocks per tick. |
| `sneakPreviewChargeTicks` | `16` | Sneak preview charge ticks. Server ticks (20 ticks = 1 second). |
| `particleSize` | `0.18` | Particle size. |
| `particleSizePerBlock` | `0.008` | How much particle size grows per block of distance from the viewer. |
| `maxParticleSize` | `0.55` | Maximum particle size used for the trajectory preview. |
| `impactParticleCount` | `2` | Impact particle count. |
| `previewPointSpacing` | `0.7` | Distance in blocks between trajectory preview dots. |
| `impactRingRadius` | `0.35` | Radius of the ring marker drawn where the shot would land. |
| `minPreviewDistanceFromEye` | `1.6` | Minimum distance from the player's eye before preview particles are shown. |
| `previewStartOffset` | `0.55` | Offset forward from the eye where trajectory simulation begins. |
| `glowPredictedTarget` | `true` | Highlights the predicted hit target entity with per-player glow. |
| `previewRenderIntervalMillis` | `75` | Minimum milliseconds between preview renders for a player when aim and context have not changed. |
| `activeSessionIntervalMillis` | `100` | Milliseconds between owner-local active aiming refreshes, clamped between 75 and 100. |
| `previewYawDeltaDegrees` | `1.2` | Yaw delta in degrees required to force a preview recompute before the normal render interval. |
| `previewPitchDeltaDegrees` | `1.2` | Pitch delta in degrees required to force a preview recompute before the normal render interval. |
| `previewPositionDeltaSquared` | `0.0125` | Movement distance squared required to force a preview recompute before the normal render interval. |
| `minimumRenderedSegments` | `8` | Lowest number of simulation segments used when rendering a trajectory. |
| `maxRenderedSegments` | `36` | Hard cap on simulation segments used for rendering. |
| `previewHighLoadPercent` | `42` | Ticker load percentage at which trajectory segments are scaled down. |
| `previewHighLoadSegmentScale` | `0.7` | Segment scale applied once high-load shedding is active. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Floaters (`ranged-floaters`)

Projectiles have a chance to apply levitation and hold targets in the air.

**Runtime entry points:** when launching a projectile; on melee/projectile hit (damage); periodic evaluation every 2400 ms.

**Menu displays:** Levitation Chance; Levitation Duration; Levitation Strength.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RangedFloaters` |
| Icon | `SHULKER_SHELL` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.78 |
| Tick interval (ms) | 2400 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-floaters.toml` |

Listened events:

- `ProjectileLaunchEvent` (`on`) — when launching a projectile
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `chanceBase` | `0.12` | Proc chance for chance base. decimal probability. |
| `chanceFactor` | `0.58` | Proc chance for chance factor. decimal probability. |
| `maxChance` | `0.8` | Proc chance for max chance. decimal probability. |
| `durationTicksBase` | `26.0` | Base Duration ticks. Server ticks (20 ticks = 1 second). |
| `durationTicksFactor` | `110.0` | Duration ticks factor. Server ticks (20 ticks = 1 second). |
| `maxAmplifier` | `1.0` | Maximum amplifier. Level or effect-amplifier units. |
| `skillXpOnProc` | `8.0` | XP awarded for skill on proc. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Pinning Shot (`ranged-pinning-shot`)

Any player-fired projectile can pin targets with heavy slowness.

**Runtime entry points:** when launching a projectile; on melee/projectile hit (damage); periodic evaluation every 2200 ms.

**Menu displays:** Pin Chance; Pin Duration; Reapply Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RangedPinningShot` |
| Icon | `TRIPWIRE_HOOK` |
| Max level | 6 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.74 |
| Tick interval (ms) | 2200 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-pinning-shot.toml` |

Listened events:

- `ProjectileLaunchEvent` (`on`) — when launching a projectile
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `dampenVelocityOnProc` | `true` | Dampen velocity on proc. |
| `procChanceBase` | `0.12` | Proc chance for proc chance base. decimal probability. |
| `procChanceFactor` | `0.42` | Proc chance for proc chance factor. decimal probability. |
| `maxProcChance` | `0.65` | Maximum XP credited for max proc chance. |
| `durationTicksBase` | `30` | Base Duration ticks. Server ticks (20 ticks = 1 second). |
| `durationTicksFactor` | `90` | Duration ticks factor. Server ticks (20 ticks = 1 second). |
| `amplifierBase` | `1` | Base Amplifier. Level or effect-amplifier units. |
| `amplifierFactor` | `2` | Amplifier factor. Unitless multiplier. |
| `reapplyCooldownMillisBase` | `5000` | Base Reapply cooldown millis. Milliseconds. |
| `reapplyCooldownMillisFactor` | `2800` | Reapply cooldown millis factor. Milliseconds. |
| `horizontalVelocityFactor` | `0.15` | Horizontal velocity factor. Unitless multiplier. |
| `cleanupThreshold` | `128` | Cleanup threshold. |
| `entryTtlMillis` | `60000` | Entry ttl millis. Milliseconds. |
| `xpOnProc` | `12` | XP awarded for xp on proc. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Ricochet Bolt (`ranged-ricochet-bolt`)

Projectiles ricochet off solid blocks with chained bounces.

**Runtime entry points:** when launching a projectile; when a projectile hits; on melee/projectile hit (damage); on entity death / kill credit; periodic evaluation every 1400 ms.

**Menu displays:** Max Ricochets; Speed Bonus Per Ricochet; Bonus Damage Per Ricochet.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RangedRicochetBolt` |
| Icon | `SPECTRAL_ARROW` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.74 |
| Tick interval (ms) | 1400 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-ricochet-bolt.toml` |

Listened events:

- `ProjectileLaunchEvent` (`on`) — when launching a projectile
- `ProjectileHitEvent` (`on`) — when a projectile hits
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxRicochetsBase` | `1` | Base Maximum ricochets. |
| `maxRicochetsFactor` | `3` | Maximum ricochets factor. Unitless multiplier. |
| `speedBonusPerRicochetBase` | `0.08` | Base Speed bonus per ricochet. |
| `speedBonusPerRicochetFactor` | `0.27` | Speed bonus per ricochet factor. Unitless multiplier. |
| `maxSpeedBonusPerRicochet` | `0.4` | Maximum speed bonus per ricochet. |
| `damageBonusPerRicochetBase` | `0.55` | Base Damage bonus per ricochet. health points (2 points = 1 heart). |
| `damageBonusPerRicochetFactor` | `2.55` | Damage bonus per ricochet factor. Unitless multiplier. |
| `maxDamageBonusPerRicochet` | `3.65` | Maximum damage bonus per ricochet. health points (2 points = 1 heart). |
| `minRicochetVelocitySquared` | `0.09` | Lower bound or activation threshold for min ricochet velocity squared. |
| `minimumLiveVelocitySquared` | `0.0004` | Lower bound or activation threshold for minimum live velocity squared. |
| `minimumPostBounceSpeed` | `0.45` | Lower bound or activation threshold for minimum post bounce speed. |
| `spawnOffsetFromSurface` | `0.22` | Spawn offset from surface. |
| `spawnOffsetAlongDirection` | `0.14` | Spawn offset along direction. |
| `sparkParticleCount` | `18` | Spark particle count. |
| `sparkSpread` | `0.18` | Spark spread. |
| `critParticleCount` | `10` | Crit particle count. |
| `critSpread` | `0.14` | Crit spread. |
| `bouncePitchBase` | `1.35` | Base Bounce pitch. |
| `bouncePitchDropPerRicochet` | `0.08` | Bounce pitch drop per ricochet. |
| `sparkPitchBase` | `1.05` | Base Spark pitch. |
| `sparkPitchRaisePerRicochet` | `0.07` | Spark pitch raise per ricochet. |
| `xpPerRicochet` | `6` | XP awarded for xp per ricochet. |
| `xpPerRicochetStep` | `2` | XP awarded for xp per ricochet step. |
| `applyToAllProjectiles` | `true` | Allow ricochet behavior to apply to throwables (snowballs, eggs, pearls, potions, exp bottles) so all supported player projectiles can bounce. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Fetch Shot (`ranged-fetch-shot`)

Shoot dropped items with projectiles to pull them straight into your inventory.
Each candidate must pass Bukkit's normal pickup-event sequence with its actual remaining inventory capacity;
cancellation leaves the item entity unchanged. On Folia the fetch scan runs only when its full footprint belongs
to the current region.

**Runtime entry points:** when a projectile hits; periodic evaluation every 2751 ms.

**Menu displays:** Fetch Radius.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RangedFetchShot` |
| Icon | `FISHING_ROD` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.3 |
| Tick interval (ms) | 2751 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-fetch-shot.toml` |

Listened events:

- `ProjectileHitEvent` (`on`) — when a projectile hits

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `1.6` | Base Radius. Blocks. |
| `radiusFactor` | `2.4` | Radius factor. Blocks. |
| `xpPerItemFetched` | `3` | XP awarded for xp per item fetched. |
| `maxCandidatesPerActivation` | `16` | Maximum dropped-item candidates inspected by one projectile impact. |
| `maxAffectedPerActivation` | `8` | Maximum dropped-item entities transferred by one projectile impact. |
| `maxTargetFxPerActivation` | `3` | Maximum successful fetches that receive individual trail effects. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Heavy Draw (`ranged-heavy-draw`)

Heavier projectiles fly slower but hit far harder.

**Runtime entry points:** when launching a projectile; on melee/projectile hit (damage); periodic evaluation every 3277 ms.

**Menu displays:** Bonus Damage; Projectile Speed.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RangedHeavyDraw` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Tick interval (ms) | 3277 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-heavy-draw.toml` |

Listened events:

- `ProjectileLaunchEvent` (`on`) — when launching a projectile
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `velocityPenaltyStart` | `0.5` | Fraction of projectile speed removed at level 1 for the Ranged Heavy Draw adaptation. |
| `velocityPenaltyEnd` | `0.1` | Fraction of projectile speed removed at max level for the Ranged Heavy Draw adaptation. |
| `damageBonusStart` | `0.1` | Bonus damage fraction at level 1 for the Ranged Heavy Draw adaptation. |
| `damageBonusEnd` | `1.5` | Bonus damage fraction at max level for the Ranged Heavy Draw adaptation. |
| `xpPerHeavyHit` | `4` | XP awarded for xp per heavy hit. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Heartseeker (`ranged-heartseeker`)

Draw a bow while looking at a creature to lock on - it glows red for you, and your arrow whistles and curves through the air to find it no matter where you aim, weaving around obstacles. Piercing levels and Ricochet Bolt's available bounce capacity add seeking passes: the arrow punches through its target, exits the far side, then bends toward a fresh nearby target; without a new target it keeps flying forward. Ricochet passes preserve their reflection, speed, damage, and rewards when seeking arrows strike blocks. Every seeking shot puts your bow on a short cooldown.

**Runtime entry points:** on block/entity/air interact (click); when shooting a bow/crossbow; on `EntityAddToWorldEvent`; when a projectile hits; on melee/projectile hit (damage); on `EntityRemoveEvent`; periodic evaluation every 50 ms while its conditions hold.

**Menu displays:** Draw a bow while looking at a creature to lock on; Bow cooldown after a seeking shot; Piercing and Ricochet Bolt bounce capacity add seeking passes.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `RangedHeartseeker` |
| Icon | `TARGET` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 6 |
| Cost factor | 0.6 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/ranged-heartseeker.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `EntityShootBowEvent` (`on`) — when shooting a bow/crossbow
- `EntityAddToWorldEvent` (`on`)
- `ProjectileHitEvent` (`on`) — when a projectile hits
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityRemoveEvent` (`on`)
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `lockRange` | `32` | Maximum distance at which drawing a bow can lock onto a creature. |
| `lockTimeoutMillis` | `6000` | Milliseconds a lock stays valid after acquiring it before the shot. |
| `turnDegreesPerTick` | `10` | Maximum degrees the arrow turns toward its target per tick. |
| `lungeTurnDegreesPerTick` | `18` | Maximum degrees the arrow turns per tick during its final approach. |
| `initialArcControlDistance` | `8` | Distance ahead of the shooter used as the control point for the initial launch arc. |
| `initialArcDistance` | `12` | Distance traveled before the initial launch arc hands full control to target homing. |
| `lungeRadius` | `2.5` | Distance at which the arrow commits to a direct lunge at the target. |
| `maxFlightTicksPerPass` | `160` | Maximum ticks a single seeking pass may fly before giving up. |
| `reseekRadius` | `12` | Radius searched for the next target after a target is lost or hit. |
| `stuckTicks` | `25` | Ticks without closing distance before a seeking arrow retargets or gives up. |
| `trailSpacing` | `0.35` | Distance in blocks between trail particles along the arrow's flight path. |
| `trailCoreSize` | `1.6` | Dust size of the seeking arrow's core trail particles. |
| `maxTrailPointsPerUpdate` | `20` | Maximum trail points emitted by one steering update. |
| `rayLookahead` | `10` | Maximum distance checked ahead for block avoidance. |
| `maxAvoidanceRays` | `4` | Maximum alternate avoidance rays tested after the direct path is blocked. |
| `avoidanceStrength` | `1.15` | Width of the avoidance cone used when the seeking path meets a block. |
| `avoidanceHoldUpdates` | `4` | Steering updates that retain the selected route around an obstacle. |
| `avoidanceClearChecks` | `2` | Consecutive clear path checks required before releasing a remembered avoidance route. |
| `targetRefreshMillis` | `50` | Milliseconds between target-owner position snapshots. |
| `maxCandidatesPerReseek` | `24` | Maximum nearby entities inspected during one reseek. |
| `maxCandidateHandoffsPerReseek` | `8` | Maximum candidate-owner snapshot handoffs during one reseek. |
| `maxChainPasses` | `8` | Maximum chained seeking passes inherited from Piercing and Ricochet Bolt. |
| `continuationExitDistance` | `8` | Minimum distance a chained arrow travels beyond a struck target before it may turn toward the next mark. |
| `continuationExitOffset` | `0.35` | Distance beyond the struck target's collision box where a chained arrow reappears. |
| `cooldownTicksStart` | `60` | Bow item cooldown in ticks applied after a seeking shot at level 1. |
| `cooldownTicksEnd` | `10` | Bow item cooldown in ticks applied after a seeking shot at max level. |
| `xpPerSeek` | `8` | XP granted when a seeking shot is admitted. |
| `xpPerHit` | `4` | XP granted per seeking arrow connection. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## Support classes (not player adaptations)

- `HeartseekerRuntime` — coordinates queued arrows, per-owner and global work budgets, chain passes, and chunk traversal for Heartseeker.
- `RicochetRuntime` — calculates ricochet transitions and preserves projectile state across projectile replacement and Folia region handoff.
- `TrajectorySightRuntime` — limits concurrent trajectory previews and invalidates stopped or replaced preview sessions.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
