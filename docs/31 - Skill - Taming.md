# Skill: Taming

Skill id `taming`. Earn XP by taming, breeding, and fighting alongside owned animals. Taming has 14 registered adaptations and uses the `LEAD` icon.

**XP sources:** taming, breeding, pet combat damage, and pet kill credit.

**Milestones / challenges** (stat keys):

- `challenge_taming_10` tracking `taming.bred`
- `challenge_taming_50` tracking `taming.bred`
- `challenge_taming_500` tracking `taming.bred`
- `challenge_pet_dmg_500` tracking `taming.pet.damage`
- `challenge_pet_dmg_5k` tracking `taming.pet.damage`
- `challenge_tamed_10` tracking `taming.tamed`
- `challenge_tamed_100` tracking `taming.tamed`
- `challenge_pet_kills_25` tracking `taming.pet.kills`
- `challenge_pet_kills_250` tracking `taming.pet.kills`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `taming` |
| Class | `SkillTaming` |
| Icon | `LEAD` |
| Color | `GOLD` |
| Interval (ms) | `3480` |
| Skill config | `plugins/Adapt/adapt/skills/taming.toml` |
| Adaptation count | 14 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/taming.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&6"` | Legacy ampersand color code used for this skill in menus and text. |
| `tameXpBase` | `65` | Base skill XP credited for tame base. |
| `cooldownDelay` | `1500` | Minimum delay between passive skill XP awards, in milliseconds. |
| `tameDamageXPMultiplier` | `8.0` | Unitless multiplier applied to XP from tame damage multiplier. |
| `tameSuccessXP` | `150` | XP awarded for tame success. |
| `petKillXP` | `25` | XP awarded for pet kill. |
| `challengeTamingReward` | `500` | Reward for the taming challenge. |
| `challengePetDmgReward` | `500` | Reward for the pet damage challenge. |
| `challengeTamedReward` | `500` | Reward for the tamed challenge. |
| `challengePetKillsReward` | `500` | Reward for the pet kills challenge. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Tame Health (`tame-health`)

Increase your tamed animal health.

**Runtime entry points:** on entity death / kill credit; on `EntitiesUnloadEvent`; periodic evaluation every 50 ms while its conditions hold.

**Menu displays:** Increased Health.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingHealthBoost` |
| Icon | `COOKED_BEEF` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 6 |
| Cost factor | 0.4 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-health.toml` |

Listened events:

- `EntityDeathEvent` (`on`) — on entity death / kill credit
- `EntitiesUnloadEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `healthBoostFactor` | `2.5` | Health boost factor. Unitless multiplier. |
| `healthBoostBase` | `0.57` | Base Health boost. health points (2 points = 1 heart). |
| `maxTameablesPerPass` | `128` | Maximum loaded tameables examined per scheduler pass. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Tame Damage (`tame-damage`)

Increase your tamed animal damage dealt.

**Runtime entry points:** on entity death / kill credit; on `EntitiesUnloadEvent`; periodic evaluation every 50 ms while its conditions hold.

**Menu displays:** Increased Damage.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingDamage` |
| Icon | `FLINT` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 6 |
| Cost factor | 0.4 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-damage.toml` |

Listened events:

- `EntityDeathEvent` (`on`) — on entity death / kill credit
- `EntitiesUnloadEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseDamage` | `0.08` | Base damage. health points (2 points = 1 heart). |
| `damageFactor` | `0.65` | Damage factor. Unitless multiplier. |
| `maxTameablesPerPass` | `128` | Maximum loaded tameables examined per scheduler pass. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Tame Regeneration (`tame-health-regeneration`)

Increase your tamed animal regeneration.

**Runtime entry points:** on melee/projectile hit (damage); on entity death / kill credit; periodic evaluation every 50 ms while its conditions hold.

**Menu displays:** HP/s.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingHealthRegeneration` |
| Icon | `GOLDEN_APPLE` |
| Max level | 3 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 7 |
| Cost factor | 0.4 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-health-regeneration.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `regenFactor` | `5` | Regen factor. Unitless multiplier. |
| `regenBase` | `1` | Base Regen. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Pack Leader Aura (`tame-pack-leader-aura`)

Nearby tamed companions gain speed and regeneration near their owner.

**Runtime entry points:** periodic evaluation every 50 ms while its conditions hold.

**Menu displays:** Aura Radius; Aura Strength.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingPackLeaderAura` |
| Icon | `BONE` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.65 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-pack-leader-aura.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `8` | Base Radius. Blocks. |
| `radiusFactor` | `14` | Radius factor. Blocks. |
| `maxAmplifier` | `2` | Maximum amplifier. Level or effect-amplifier units. |
| `effectTicks` | `80` | Effect ticks. Server ticks (20 ticks = 1 second). |
| `maxOwnersPerPass` | `16` | Maximum owners processed per scheduler tick, capped internally at 16. |
| `maxTameablesPerPass` | `48` | Maximum indexed tameables examined per scheduler tick, capped internally at 48. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Beast Recall (`tame-beast-recall`)

Sneak-right-click with a lead to recall your nearest tamed companion to a safe nearby spot. Each recall costs hunger.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 2200 ms.

**Menu displays:** Recall Radius; Recall Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingBeastRecall` |
| Icon | `LEAD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 2200 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-beast-recall.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `20` | Base Radius. Blocks. |
| `radiusFactor` | `38` | Radius factor. Blocks. |
| `minRecallDistanceSquared` | `9.0` | Lower bound or activation threshold for min recall distance squared. Blocks. |
| `cooldownTicksBase` | `420` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksFactor` | `280` | Cooldown ticks factor. Server ticks (20 ticks = 1 second). |
| `xpOnRecall` | `26` | XP awarded for xp on recall. |
| `hungerCost` | `2` | Food points consumed per beast recall. |
| `maxCandidatesPerActivation` | `16` | Maximum nearby tameable candidates inspected by one recall. |
| `maxAffectedPerActivation` | `1` | Maximum pets recalled by one activation. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Shared Pain (`tame-shared-pain`)

Spread a portion of your incoming damage across nearby owned companions without reducing them below their health floor.

**Runtime entry points:** on taking damage; periodic evaluation every 1700 ms.

**Menu displays:** Shared Damage; Companion Health Floor.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingSharedPain` |
| Icon | `POPPY` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 1700 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-shared-pain.toml` |

Listened events:

- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `redirectPercentBase` | `0.2` | Base Redirect percent. |
| `redirectPercentFactor` | `0.35` | Redirect percent factor. Unitless multiplier. |
| `maxRedirectPercent` | `0.7` | Maximum redirect percent. |
| `petHealthFloorBase` | `1.0` | Minimum health preserved on each companion before it stops receiving Shared Pain damage. |
| `petHealthFloorFactor` | `1.0` | Additional companion health floor reached at maximum adaptation level. |
| `radiusBase` | `8.0` | Base radius searched for owned companions that can share incoming damage. |
| `radiusFactor` | `8.0` | Additional companion search radius reached at maximum adaptation level. |
| `maxPets` | `8` | Maximum companions included in one damage split, capped internally at 16. |
| `xpPerRedirectedDamage` | `2.0` | XP awarded for xp per redirected damage. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Mounted Tactics (`tame-mounted-tactics`)

Gain mount-specific combat and handling bonuses while riding.

**Runtime entry points:** while moving; on sprint toggle; on player death; on gamemode change; on entity death / kill credit; on melee/projectile hit (damage); periodic evaluation every 10 ms while its conditions hold.

**Menu displays:** Mounted Damage Bonus; Mounted Damage Reduction.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingMountedTactics` |
| Icon | `SADDLE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 10 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-mounted-tactics.toml` |

Listened events:

- `PlayerMoveEvent` (`on`) — while moving
- `PlayerToggleSprintEvent` (`on`) — on sprint toggle
- `EntityMountEvent` (`on`) — initializes mounted movement state
- `EntityDismountEvent` (`on`) — clears mounted movement state
- `PlayerQuitEvent` (`on`)
- `PlayerDeathEvent` (`on`) — on player death
- `PlayerGameModeChangeEvent` (`on`) — on gamemode change
- `EntityDeathEvent` (`on`) — on entity death / kill credit
- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `mountedDamageBonusBase` | `0.08` | Base Mounted damage bonus. health points (2 points = 1 heart). |
| `mountedDamageBonusFactor` | `0.22` | Mounted damage bonus factor. Unitless multiplier. |
| `maxMountedDamageBonus` | `0.35` | Maximum mounted damage bonus. health points (2 points = 1 heart). |
| `mountedDamageReductionBase` | `0.06` | Base Mounted damage reduction. health points (2 points = 1 heart). |
| `mountedDamageReductionFactor` | `0.2` | Mounted damage reduction factor. Unitless multiplier. |
| `maxMountedDamageReduction` | `0.28` | Maximum mounted damage reduction. health points (2 points = 1 heart). |
| `horseSpeedAmplifierBase` | `0` | Base Horse speed amplifier. Level or effect-amplifier units. |
| `horseSpeedAmplifierFactor` | `2` | Horse speed amplifier factor. Unitless multiplier. |
| `striderSpeedAmplifierBase` | `0` | Base Strider speed amplifier. Level or effect-amplifier units. |
| `striderSpeedAmplifierFactor` | `2` | Strider speed amplifier factor. Unitless multiplier. |
| `horseJumpStrengthBonusBase` | `0.1` | Base horse jump strength bonus as a percent of the mount's own jump strength. |
| `horseJumpStrengthBonusFactor` | `0.15` | Additional horse jump strength bonus scaling with level percent. |
| `pigResistanceAmplifierBase` | `0` | Base Pig resistance amplifier. Level or effect-amplifier units. |
| `pigResistanceAmplifierFactor` | `1` | Pig resistance amplifier factor. Unitless multiplier. |
| `horseBaseHorizontalSpeed` | `0.3` | Base horizontal speed target used for horse mounted speed scaling. |
| `striderBaseHorizontalSpeed` | `0.24` | Base horizontal speed target used for strider mounted speed scaling. |
| `mountMaxHorizontalSpeed` | `0.78` | Maximum horizontal speed this adaptation can force on mounts. |
| `horsePushBase` | `0.08` | Base Horse push. |
| `horsePushFactor` | `0.16` | Horse push factor. Unitless multiplier. |
| `pigPushBase` | `0.05` | Base Pig push. |
| `pigPushFactor` | `0.12` | Pig push factor. Unitless multiplier. |
| `xpPerMountedDamage` | `1.5` | XP awarded for xp per mounted damage. health points (2 points = 1 heart). |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Fetch (`tame-fetch`)

Your tamed wolves gather nearby dropped items and bring them straight to you.
The owner performs Bukkit's normal pickup-event sequence when a wolf reaches a drop; cancellation aborts that
fetch before the item entity is removed. On Folia wolf and item searches run only when their full footprint
belongs to the current region.

**Runtime entry points:** periodic evaluation every 1500 ms.

**Menu displays:** Fetch Range; Carry Chance.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingFetch` |
| Icon | `HOPPER` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.4 |
| Tick interval (ms) | 1500 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-fetch.toml` |

Listened events:

- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `fetchRangeBase` | `6.0` | Base Fetch range. Blocks. |
| `fetchRangeFactor` | `10.0` | Fetch range factor. Blocks. |
| `carryRateBase` | `0.35` | Base Carry rate. |
| `carryRateFactor` | `0.5` | Carry rate factor. Unitless multiplier. |
| `maxCarryRate` | `0.9` | Maximum carry rate. |
| `wolfSearchRadius` | `24.0` | Wolf search radius. Blocks. |
| `xpPerItemFetched` | `4` | Xp granted each time a wolf fetches a dropped item. |
| `maxWolves` | `6` | Maximum tamed wolves counted around the owner, capped internally at 12. |
| `maxCarryPerTick` | `4` | Maximum dropped items fetched per owner each pass, capped internally at 8. |
| `realFetch` | `true` | Sends a wolf walking to the drop and back instead of pulling the item straight to you. |
| `fetchWalkSpeed` | `1.15` | Movement speed multiplier used while a wolf walks a fetch, clamped to 0.1 - 4.0. |
| `pathfindRadius` | `9.0` | Maximum walked fetch distance in blocks, clamped internally at 11 by vanilla pet follow behavior. |
| `fetchDeadlineMillis` | `9000` | Milliseconds a walked fetch may run before it is given up, clamped to 1000 - 60000. |
| `maintenanceIntervalTicks` | `5` | Ticks between re-issuing a walking wolf its path, clamped to 1 - 20. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Alpha's Command (`tame-alphas-command`)

Sneak-left-click while holding a bone to raycast a target, mark it with a private red glow, and command nearby combat pets to focus it. Successful commands consume one bone.

**Runtime entry points:** on melee/projectile hit (damage); on block/entity/air interact (click).

**Menu displays:** Command Range; Focus Duration.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingAlphasCommand` |
| Icon | `BONE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.55 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-alphas-command.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)
- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `commandRangeBase` | `8.0` | Base Command range. Blocks. |
| `commandRangeFactor` | `12.0` | Command range factor. Blocks. |
| `focusTicksBase` | `60` | Base Focus ticks. Server ticks (20 ticks = 1 second). |
| `focusTicksFactor` | `120` | Focus ticks factor. Server ticks (20 ticks = 1 second). |
| `focusSpeedAmplifier` | `0` | Amplifier of the speed and strength focus buff applied to commanded pets. |
| `commandCooldownMillis` | `3000` | Cooldown in milliseconds between commands. |
| `xpPerCommand` | `12` | Xp granted each time the pack is commanded. |
| `maxPets` | `12` | Maximum pets commanded per activation, capped internally at 24. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Guardian Instinct (`tame-guardian-instinct`)

Nearby pets leap to intercept projectiles aimed at you, taking reduced damage themselves.

**Runtime entry points:** on melee/projectile hit (damage).

**Menu displays:** Intercept Chance; Pet Damage Reduction.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingGuardianInstinct` |
| Icon | `SHIELD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-guardian-instinct.toml` |

Listened events:

- `EntityDamageByEntityEvent` (`on`) — on melee/projectile hit (damage)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `interceptChanceBase` | `0.35` | Proc chance for intercept chance base. decimal probability. |
| `interceptChanceFactor` | `0.45` | Proc chance for intercept chance factor. decimal probability. |
| `maxInterceptChance` | `0.8` | Proc chance for max intercept chance. decimal probability. |
| `petReductionBase` | `0.4` | Base Pet reduction. |
| `petReductionFactor` | `0.35` | Pet reduction factor. Unitless multiplier. |
| `maxPetReduction` | `0.7` | Maximum XP credited for max pet reduction. |
| `radiusBase` | `8.0` | Base Radius. Blocks. |
| `radiusFactor` | `8.0` | Radius factor. Blocks. |
| `leapStrength` | `0.8` | Leap velocity applied to a pet as it intercepts a projectile. |
| `cooldownMillis` | `1200` | Cooldown in milliseconds between projectile intercepts. |
| `xpPerDamageIntercepted` | `2.0` | Xp granted per point of intercepted damage. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Stable Hand (`tame-stable-hand`)

Animals you tame or breed keep a permanent bias toward better speed, jump, health, and safe fall distance.

**Runtime entry points:** when taming; when breeding.

**Menu displays:** Attribute Bias; Safe Fall Blocks.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingStableHand` |
| Icon | `SADDLE` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 5 |
| Cost factor | 0.5 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-stable-hand.toml` |

Listened events:

- `EntityTameEvent` (`on`) — when taming
- `EntityBreedEvent` (`on`) — when breeding

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `biasBase` | `0.1` | Base Bias. |
| `biasFactor` | `0.2` | Bias factor. Unitless multiplier. |
| `maxBias` | `0.3` | Maximum bias. |
| `xpPerAnimal` | `20` | Xp granted each time a tamed or bred animal receives the stable-hand bias. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Wild Empathy (`tame-wild-empathy`)

Taming succeeds more often, and neutral mobs are slower to anger at you.

**Runtime entry points:** on entity right-click; when mobs target.

**Menu displays:** Extra Taming Odds; Anger Resistance.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingWildEmpathy` |
| Icon | `DANDELION` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-wild-empathy.toml` |

Listened events:

- `PlayerInteractEntityEvent` (`on`) — on entity right-click
- `EntityTargetLivingEntityEvent` (`on`) — when mobs target

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `tamingOddsBase` | `0.25` | Base Taming odds. |
| `tamingOddsFactor` | `0.4` | Taming odds factor. Unitless multiplier. |
| `maxTamingOdds` | `0.6` | Maximum taming odds. |
| `angerResistanceBase` | `0.3` | Base Anger resistance. |
| `angerResistanceFactor` | `0.45` | Anger resistance factor. Unitless multiplier. |
| `maxAngerResistance` | `0.75` | Maximum anger resistance. |
| `xpPerTame` | `60` | Xp granted each time Wild Empathy forces a successful tame. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Battle Bond (`tame-battle-bond`)

When one of your pets lands a kill, you and the nearby pack gain brief strength, speed, and regeneration with a visible bond.

**Runtime entry points:** on entity death / kill credit.

**Menu displays:** Buff Tier; Buff Duration.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingBattleBond` |
| Icon | `DIAMOND_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-battle-bond.toml` |

Listened events:

- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxBuffTier` | `1` | Highest buff amplifier (tier) reachable at max level; level 1 grants tier 1. |
| `buffTicksBase` | `80` | Base Buff ticks. Server ticks (20 ticks = 1 second). |
| `buffTicksFactor` | `120` | Buff ticks factor. Server ticks (20 ticks = 1 second). |
| `packRadius` | `16` | Radius searched for pack members to buff on a pet kill. |
| `xpPerKill` | `10` | Xp granted each time Battle Bond triggers on a pet kill. |
| `maxPack` | `12` | Maximum pack members buffed per kill, capped internally at 24. |
| `glowTicks` | `30` | Number of ticks bonded pets glow, capped internally at 60. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Last Breath (`tame-last-breath`)

On a lethal hit a pet drops to 1 HP, is briefly invulnerable, and recalls to your side.

**Runtime entry points:** on taking damage.

**Menu displays:** Per-Pet Cooldown; Invulnerability.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `TamingLastBreath` |
| Icon | `TOTEM_OF_UNDYING` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Config file | `plugins/Adapt/adapt/adaptations/tame-last-breath.toml` |

Listened events:

- `EntityDamageEvent` (`onProtectedWindow`) — on taking damage
- `EntityDamageEvent` (`on`) — on taking damage

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldownMillisBase` | `300000` | Base per-pet cooldown in milliseconds before Last Breath can save the same pet again. |
| `cooldownMillisFactor` | `180000` | Cooldown milliseconds removed at max level. |
| `minCooldownMillis` | `60000` | Minimum per-pet cooldown in milliseconds after level reduction. |
| `invulnTicks` | `60` | Ticks of invulnerability granted to a pet saved by Last Breath. |
| `xpPerSave` | `40` | Xp granted each time Last Breath saves a pet. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## Support classes (not player adaptations)

- `TameableOwnershipIndex` — tracks loaded tameable entities, ownership changes, lifecycle generations, and bounded Folia discovery passes.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
