# Skill: Agility

Agility is the movement line. You level it by moving, and the game does the counting for you: sprinting, swimming, hanging in the air, and climbing each pay out on a slow pulse, and every block you travel pays a small trickle on top. It tends to level itself while you play.

What you get back is parkour. Sprint long enough and you outrun your own walk speed. Latch a wall and climb a shaft with nothing but shift. Dash mid-air, slide under a one-block gap, and roll out of a fall that would otherwise have cost you half your hearts.

Not all of it is flashy. Several adaptations are quiet insurance: less hunger burned per sprint, a chance to sidestep an incoming arrow, farmland you stop trampling and pressure plates you stop setting off.

Agility uses the `FEATHER` icon, shows green in menus, and runs its passive XP pulse every 975 ms. It has 13 adaptations, and most of them are cheap to unlock.

## How you earn Agility XP

Two paths, both automatic.

1. Every movement you make credits distance to the `move` stat and pays `moveXpPassive` for each block travelled. The same distance is also credited to exactly one of `move.sneak`, `move.fly`, `move.swim`, or `move.sprint`, checked in that order. Those four stats are what the challenge milestones count.
2. Every 975 ms a pulse looks at what you are doing and pays for each thing that applies: sprinting pays `sprintXpPassive`, swimming pays `swimXpPassive`, being off the ground pays `jumpXpPassive`, and climbing pays `climbXpPassive`. Sneaking or flying blocks all four. Sprinting and swimming exclude each other.

The pulse scales its payout by how much real time actually elapsed, so a laggy tick does not shortchange you.

## Adaptations

All of these need the same four things before they do anything: you have learned the adaptation to level 1 or higher in the Adapt menu, both the Agility skill and that adaptation are enabled in config, you hold the matching `adapt.use` permission, and protection plugins or region policy allow the action where you are standing. Most also require Survival or Adventure mode. That list is not repeated below.

### Wind Up (`agility-wind-up`)

Hold a sprint and you keep accelerating. The speed builds over a fixed number of ticks and settles well above vanilla sprint speed, easing in rather than snapping on. Break the sprint, sneak, start flying, start gliding, mount or dismount anything, or leave Survival and Adventure mode, and the buildup resets to zero. It works on its own once learned. Just run.

### Wall Jump (`agility-wall-jump`)

Turns any flat wall into a ladder you can chain. Good for shafts, cliffs, and getting out of holes you dug yourself into.

1. Get airborne next to a wall.
2. Hold shift. You latch onto the wall and stop falling.
3. Release shift to launch off it.
4. Repeat until you run out of air jumps. Touching the ground refills them.

If you are steering away from the wall as you release, you get an extra push backward off it. Levels raise both the launch strength and how many latches you get per airtime.

### Super Jump (`agility-super-jump`)

A charged standing jump for crossing gaps and reaching ledges.

1. Hold shift. The jump-strength bonus applies while you sneak and is stripped the moment you release.
2. Jump.

Four levels scale the apex from 1.5 blocks up to 2.5 blocks.

### Armor-Up (`agility-armor-up`)

Sprinting plates you in temporary armor. The plating builds while you run and drains back off after you stop, so it rewards committing to a charge instead of poking. Sneaking, swimming, flying, or gliding all stop it building. It works on its own once learned.

### Ladder Slide (`agility-ladder-slide`)

Turns ladders and vines into express lanes. Look up to climb fast, look down to drop fast, and level your view back toward the horizon to hand control back to vanilla. Sneaking stops directional movement outright, and the first and last two climbable blocks of a column always use normal control so you do not overshoot the top or slam into the floor. Scaffolding is deliberately excluded. Fall damage caused directly by a fast descent is cancelled while `safeLanding` is on.

### Roll Landing (`agility-roll-landing`)

A timed crouch that turns a bad landing into a hungry one.

1. While falling, tap or hold shift shortly before you hit the ground. A soft click confirms the input is armed.
2. Land. Part of the fall damage is absorbed and paid for in food points instead.

The absorbed damage scales with level and is capped. You go briefly prone on the landing, and a cooldown is stamped on the hay-block item slot so you cannot chain rolls. Rolling out of a fall of 30 blocks or more grants a hidden challenge.

### Slipstream Slide (`agility-slipstream-slide`)

A baseball slide that keeps its speed. Useful for shooting a one-block gap you would normally have to crouch-walk through.

1. Sprint.
2. Tap shift.

You drop into a prone pose, ground friction mostly disappears, and you carry your momentum until the slide runs out. Each slide costs hunger and puts you on a cooldown that shrinks as you level. At max level, mobs you slide through get slowed.

### Air Dash (`agility-air-dash`)

A mid-air correction for jumps you misjudged.

1. Sprint, then jump. That arms the dash.
2. While still in the air, left-click empty air.

You snap forward along your look direction with a small lift so you do not lose the airtime. Landing rearms it. Each dash costs hunger, and at max level you get two charges per sprint-jump. It does nothing while flying, gliding, swimming, climbing, riding, or already on the ground.

### Cat Reflexes (`agility-cat-reflexes`)

While you are sprinting, incoming projectiles have a chance to miss entirely. The hit is cancelled and you get a small sidestep nudge. It only fires while sprinting, and only against projectiles. It works on its own once learned.

### Featherfoot (`agility-featherfoot`)

Stops the ground from punishing you for running across it. Farmland unlocks first, then pressure plates, then sweet berry bushes, then powder snow, one per level. By default it only applies while you are sprinting, which server owners can turn off. It works on its own once learned.

### Vault (`agility-vault`)

Run at a fence and jump; you clear it instead of bouncing off. Adapt watches for a fence in your path while you are grounded and pre-arms the jump so the hop is high enough to land on top. One level, no scaling.

### Marathoner (`agility-marathoner`)

Cuts the saturation drain from sprinting and sprint-jumping. It does not make you faster, it just means you can keep running for a lot longer before hunger stops you. It works on its own once learned.

### Kip-Up (`agility-kip-up`)

Turns getting knocked around into momentum you keep.

1. Take a hit from another entity.
2. Jump within the recovery window.

You are re-launched in the direction you were steering, or where you were looking if you were not moving, and you get a short speed burst on top. There is a flat cooldown between recoveries.

### Paper-only jump detection

Super Jump and Vault both read Paper's `PlayerJumpEvent` through a companion listener that Adapt only registers when that class exists. On a server without it, the rest of each adaptation still runs, but the jump-moment work in that listener does not.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `agility` |
| Class | `SkillAgility` |
| Icon | `FEATHER` |
| Color | `GREEN` |
| Interval (ms) | `975` |
| Skill config | `plugins/Adapt/adapt/skills/agility.toml` |
| Adaptation count | 13 |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/agility.toml` on first load.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `enabled` | `true` | Turns the whole Agility line off when false. |
| `skillColor` | `"&a"` | Legacy ampersand color code used for Agility in menus and text. |
| `challengeMove1kReward` | `500` | Skill XP paid for completing `challenge_move_1k`. |
| `challengeSprint5kReward` | `2000` | Skill XP paid for `challenge_sprint_dist_5k`, the two swim challenges, the two fly challenges, and the two sneak challenges. The larger tier of each pair pays double this. |
| `challengeSprintMarathonReward` | `6500` | Skill XP paid for `challenge_sprint_marathon`. |
| `sprintXpPassive` | `0.35` | Skill XP per pulse while sprinting, scaled by elapsed time. |
| `swimXpPassive` | `0.4` | Skill XP per pulse while swimming, scaled by elapsed time. |
| `jumpXpPassive` | `0.15` | Skill XP per pulse while off the ground, scaled by elapsed time. |
| `climbXpPassive` | `0.4` | Skill XP per pulse while climbing, scaled by elapsed time. |
| `moveXpPassive` | `0.05` | Skill XP per block travelled, paid on every movement. |

### Skill milestones

| Advancement key | Stat tracked | Threshold | Reward source |
|-----------------|--------------|-----------|---------------|
| `challenge_move_1k` | `move` | 1000 | `challengeMove1kReward` |
| `challenge_sprint_marathon` | `move.sprint` | 42195 | `challengeSprintMarathonReward` |
| `challenge_sprint_dist_5k` | `move.sprint` | 5000 | `challengeSprint5kReward` |
| `challenge_sprint_dist_50k` | `move.sprint` | 50000 | `challengeSprint5kReward` x 2 |
| `challenge_agility_swim_1k` | `move.swim` | 1000 | `challengeSprint5kReward` |
| `challenge_agility_swim_10k` | `move.swim` | 10000 | `challengeSprint5kReward` x 2 |
| `challenge_fly_1k` | `move.fly` | 1000 | `challengeSprint5kReward` |
| `challenge_fly_10k` | `move.fly` | 10000 | `challengeSprint5kReward` x 2 |
| `challenge_agility_sneak_500` | `move.sneak` | 500 | `challengeSprint5kReward` |
| `challenge_agility_sneak_5k` | `move.sneak` | 5000 | `challengeSprint5kReward` x 2 |

### Shared adaptation keys

Every adaptation TOML at `plugins/Adapt/adapt/adaptations/<id>.toml` also carries `enabled`, `permanent`, `showParticles`, and `showSounds`, plus the learn-cost fields `baseCost`, `costFactor`, `maxLevel`, and `initialCost` listed per adaptation below.

### Wind Up

| Property | Default |
|----------|---------|
| Class | `AgilityWindUp` |
| Icon | `POWERED_RAIL` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 2 |
| Cost factor | 0.65 |
| Tick interval (ms) | 50 |
| Menu lines | Max Speed; Windup Time |
| Milestone | `challenge_agility_wind_up_10min` on `agility.wind-up.max-speed-ticks` at 12000, reward 400 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-wind-up.toml` |

Listened events: `PlayerMoveEvent`, `PlayerToggleSprintEvent`, `PlayerToggleSneakEvent`, `PlayerToggleFlightEvent`, `EntityToggleGlideEvent`, `EntityMountEvent`, `EntityDismountEvent`, `PlayerGameModeChangeEvent`, `PlayerDeathEvent`, `PlayerQuitEvent`.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `windupTicksSlowest` | `180` | Ticks of unbroken sprinting needed to reach top speed at the lowest level. 20 ticks = 1 second. |
| `windupTicksFastest` | `60` | Ticks needed to reach top speed at max level. |
| `windupSpeedBase` | `0.22` | Speed target reached at the lowest level, before the per-level bonus. |
| `windupSpeedLevelMultiplier` | `0.225` | Extra speed target added at max level. |
| `walkSpeedBonusScalar` | `0.75` | Fraction of the speed target converted into the relative movement-speed modifier. |
| `walkSpeedLerpPerTick` | `0.45` | How quickly the applied modifier eases toward its target each tick, 0-1. |
| `maxWalkSpeed` | `0.35` | Walk-speed ceiling measured against the 0.2 vanilla base; the relative bonus is capped at maxWalkSpeed / 0.2 - 1. |
| `movementVelocityThreshold` | `0.015` | Minimum horizontal speed before top-speed ticks count toward the milestone stat. |

### Wall Jump

| Property | Default |
|----------|---------|
| Class | `AgilityWallJump` |
| Icon | `VINE` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 2 |
| Cost factor | 0.65 |
| Tick interval (ms) | 50 |
| Menu lines | Max Jumps; Jump Height |
| Milestone | `challenge_agility_wall_jump_500` on `agility.wall-jump.air-jumps` at 500, reward 500. A hidden `challenge_agility_parkour_master` advancement is also registered. |
| Config file | `plugins/Adapt/adapt/adaptations/agility-wall-jump.toml` |

Listened events: `PlayerMoveEvent`, `PlayerToggleSneakEvent`, `PlayerGameModeChangeEvent`, `PlayerDeathEvent`.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `maxJumpsLevelBonusDivisor` | `2` | Latches per airtime are level plus level divided by this number. Lower values give more latches. |
| `jumpHeightBase` | `0.625` | Launch strength off the wall at the lowest level. |
| `jumpHeightBonusLevelMultiplier` | `0.225` | Extra launch strength added at max level. |
| `backwardPushSpeed` | `0.22` | Horizontal speed pushing you away from the wall when you release while steering backward. |
| `backwardIntentDotThreshold` | `0.35` | How directly your steering must oppose your facing, as a dot product, to count as a backward release. |
| `inputMovementThreshold` | `0.0025` | Minimum horizontal movement in one move event before it is recorded as a steering input. |
| `inputWindowMs` | `450` | How long a recorded steering input stays valid, in milliseconds. |

### Super Jump

| Property | Default |
|----------|---------|
| Class | `AgilitySuperJump` |
| Icon | `LEATHER_BOOTS` |
| Max level | 4 (locked in code, config overrides are reset on reload) |
| Initial knowledge cost | 5 |
| Base knowledge cost | 2 |
| Cost factor | 0.55 |
| Tick interval (ms) | 9999 |
| Menu lines | Jump apex (blocks); Sneak + Jump to Super Jump! |
| Milestones | `challenge_agility_super_jump_100` on `agility.super-jump.jumps` at 100, reward 300; `challenge_agility_super_jump_5k` at 5000, reward 1500 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-super-jump.toml` |

Listened events: `PlayerToggleSneakEvent`, `PlayerGameModeChangeEvent`, `PlayerChangedWorldEvent`, and `PlayerJumpEvent` through a Paper-only companion listener.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `minimumJumpHeight` | `1.5` | Jump apex in blocks at level 1. Values below the vanilla jump height are clamped up. |
| `maximumJumpHeight` | `2.5` | Jump apex in blocks at level 4. |

### Armor-Up

| Property | Default |
|----------|---------|
| Class | `AgilityArmorUp` |
| Icon | `IRON_CHESTPLATE` |
| Max level | 5 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 2 |
| Cost factor | 0.65 |
| Tick interval (ms) | 50 |
| Menu lines | Max Armor; Armor-Up Time; Armor Decay Time |
| Milestone | `challenge_agility_armor_up_30min` on `agility.armor-up.ticks-armored` at 36000, reward 500 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-armor-up.toml` |

Listened events: `PlayerMoveEvent`, `PlayerToggleSprintEvent`, `PlayerToggleSneakEvent`, `PlayerJoinEvent`, `PlayerDeathEvent`, `PlayerQuitEvent`.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `windupTicksSlowest` | `180` | Ticks of unbroken sprinting needed for full plating at the lowest level. |
| `windupTicksFastest` | `60` | Ticks needed for full plating at max level. |
| `windupArmorBase` | `0.22` | Plating target at the lowest level. Multiplied by 10 to get armor points, so this alone is 2.2 armor. |
| `windupArmorLevelMultiplier` | `0.525` | Extra plating target added at max level, also multiplied by 10 for armor points. |
| `decaySecondsBase` | `5.0` | Seconds for full plating to drain away at the lowest level. |
| `decaySecondsMaxLevelBonus` | `5.0` | Extra drain seconds added at max level, so the plating lingers longer. |

### Ladder Slide

| Property | Default |
|----------|---------|
| Class | `AgilityLadderSlide` |
| Icon | `LADDER` |
| Max level | 1 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 1 |
| Cost factor | 0.12 |
| Tick interval (ms) | 50 |
| Menu lines | Ladder descent speed (blocks/sec); Ladder climb speed (blocks/sec); Look activation / release angles |
| Milestones | `challenge_agility_ladder_500` on `agility.ladder-slide.blocks-climbed` at 500, reward 300; `challenge_agility_ladder_10k` at 10000, reward 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-ladder-slide.toml` |

Listened events: `PlayerMoveEvent`, `PlayerToggleSneakEvent`, `PlayerGameModeChangeEvent`, `PlayerChangedWorldEvent`, `PlayerTeleportEvent`, `PlayerDeathEvent`, `PlayerQuitEvent`, `EntityDamageEvent`, `ServerResourcesReloadedEvent`.

Controlled climbables are everything in the vanilla `CLIMBABLE` tag except `SCAFFOLDING`. The two blocks at each end of a climbable column always use normal control.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `descentSpeedBase` | `0.30` | Downward speed in blocks per tick before per-level scaling. |
| `descentSpeedPerLevel` | `0.30` | Extra downward speed granted at max level. |
| `climbAssistBase` | `0.28` | Upward speed in blocks per tick before per-level scaling. |
| `climbAssistPerLevel` | `0.22` | Extra upward speed granted at max level. |
| `lookActivationDegrees` | `30.0` | Degrees above or below the horizon at which gaze-directed movement switches on. |
| `lookReleaseDegrees` | `15.0` | Degrees at which an active gaze direction hands control back to vanilla. |
| `safeLanding` | `true` | Cancels fall damage that came directly out of a fast ladder descent. |

### Roll Landing

| Property | Default |
|----------|---------|
| Class | `AgilityRollLanding` |
| Icon | `HAY_BLOCK` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.62 |
| Tick interval (ms) | 1200 |
| Menu lines | Fall Damage Conversion; Input Timing Window; Roll Cooldown |
| Milestones | `challenge_agility_roll_100` on `agility.roll-landing.damage-prevented` at 100, reward 300; `challenge_agility_roll_1000` at 1000, reward 1000. A hidden `challenge_agility_fearless` advancement is granted for rolling a fall of 30 blocks or more. |
| Config file | `plugins/Adapt/adapt/adaptations/agility-roll-landing.toml` |

Listened events: `PlayerToggleSneakEvent`, `PlayerMoveEvent`, `EntityDamageEvent`.

The roll cooldown is stamped on the player's `HAY_BLOCK` item cooldown slot.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `reductionBase` | `0.22` | Fraction of fall damage absorbed at the lowest level. |
| `reductionFactor` | `0.43` | Extra fraction absorbed at max level. |
| `maxReduction` | `0.8` | Hard cap on the absorbed fraction regardless of level. |
| `inputWindowMillisBase` | `450` | How long a crouch input stays armed before landing, in milliseconds, before level scaling. |
| `inputWindowMillisFactor` | `350` | Extra armed window in milliseconds granted at max level. |
| `hungerPerDamageBase` | `1.4` | Food points charged per point of damage absorbed, at the lowest level. |
| `hungerPerDamageReduction` | `0.75` | How much of that food cost is removed across the level range. |
| `cooldownTicksBase` | `22` | Ticks between rolls at the lowest level. 20 ticks = 1 second. |
| `cooldownTicksFactor` | `12` | Ticks removed from the roll cooldown at max level. |
| `maxVerticalVelocityForRollInput` | `-0.08` | You must be falling at least this fast for a crouch to arm a roll. |
| `proneTicksBase` | `4` | Ticks you stay prone after a roll at the lowest level. |
| `proneTicksFactor` | `5` | Extra prone ticks at max level. |
| `xpPerDamagePrevented` | `4.2` | Skill XP paid per point of fall damage absorbed. |

### Slipstream Slide

| Property | Default |
|----------|---------|
| Class | `AgilitySlipstreamSlide` |
| Icon | `ICE` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.55 |
| Tick interval (ms) | 1000 (framework default) |
| Menu lines | Slide speed (blocks/sec); Slide cooldown; Max level: mobs you slide through are slowed |
| Milestones | `challenge_agility_slipstream_500` on `agility.slipstream-slide.slides` at 500, reward 400; `challenge_agility_slipstream_5k` at 5000, reward 1500 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-slipstream-slide.toml` |

Listened events: `PlayerMoveEvent`, `PlayerToggleSneakEvent`, `PlayerTeleportEvent`, `PlayerDeathEvent`, `PlayerQuitEvent`.

A sprint that ended within the last 350 ms still counts as sprinting for the purpose of starting a slide.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `slideForceBase` | `0.5` | Horizontal slide velocity in blocks per tick before level scaling. |
| `slideForceFactor` | `0.45` | Extra slide velocity in blocks per tick at max level. |
| `cooldownMillisBase` | `4000` | Milliseconds between slides before level scaling. |
| `cooldownMillisReduction` | `2500` | Milliseconds removed from that cooldown at max level. |
| `cooldownMillisFloor` | `1300` | Shortest cooldown allowed after all reductions, in milliseconds. |
| `slideTicksBase` | `14` | Ticks the prone slide pose lasts before level scaling. |
| `slideTicksFactor` | `10` | Extra prone ticks granted at max level. |
| `slideFrictionReduction` | `0.9` | Fraction of ground friction removed while sliding, 0-1. |
| `hungerCost` | `1.8` | Saturation, then food points, charged per slide. |
| `slowAmplifier` | `1` | Slowness amplifier applied to mobs you slide through at max level. |
| `slowDurationTicks` | `40` | Duration in ticks of that max-level slow. |
| `xpPerSlide` | `3` | Skill XP paid per successful slide. |

### Air Dash

| Property | Default |
|----------|---------|
| Class | `AgilityAirDash` |
| Icon | `PHANTOM_MEMBRANE` |
| Max level | 4 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 3 |
| Cost factor | 0.55 |
| Tick interval (ms) | 1000 (framework default) |
| Menu lines | Dash speed (blocks/sec); Mid-air dash charges |
| Milestones | `challenge_agility_air_dash_500` on `agility.air-dash.dashes` at 500, reward 400; `challenge_agility_air_dash_5k` at 5000, reward 1500 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-air-dash.toml` |

Listened events: `PlayerMoveEvent`, `PlayerInteractEvent` (left-click air only), `PlayerQuitEvent`.

A dash is refused when you are on the ground, flying, gliding, swimming, climbing, riding a vehicle, at zero food, or outside Survival and Adventure mode.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `dashForceBase` | `0.85` | Dash velocity in blocks per tick before level scaling. |
| `dashForceFactor` | `0.6` | Extra dash velocity in blocks per tick at max level. |
| `upwardLift` | `0.12` | Upward velocity added on a dash so you keep airtime. |
| `maxLevelCharges` | `2` | Dashes available per sprint-jump at max level. |
| `debounceMillis` | `250` | Minimum milliseconds between dash inputs, to swallow double clicks. |
| `hungerCost` | `2` | Saturation, then food points, charged per dash. |
| `xpPerDash` | `3` | Skill XP paid per successful dash. |

### Cat Reflexes

| Property | Default |
|----------|---------|
| Class | `AgilityCatReflexes` |
| Icon | `RABBIT_HIDE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Tick interval (ms) | 1000 (framework default) |
| Menu lines | Projectile dodge chance |
| Milestones | `challenge_agility_cat_reflexes_100` on `agility.cat-reflexes.dodges` at 100, reward 300; `challenge_agility_cat_reflexes_1k` at 1000, reward 1200 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-cat-reflexes.toml` |

Listened events: `EntityDamageByEntityEvent`.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `dodgeChanceBase` | `0.08` | Chance to dodge a projectile while sprinting before level scaling, 0-1. |
| `dodgeChanceFactor` | `0.3` | Extra dodge chance granted at max level. |
| `maxDodgeChance` | `0.35` | Hard cap on dodge chance regardless of level. |
| `xpPerDodge` | `4` | Skill XP paid per dodged projectile. |

### Featherfoot

| Property | Default |
|----------|---------|
| Class | `AgilityFeatherfoot` |
| Icon | `RABBIT_FOOT` |
| Max level | 4 (recomputed on save as the highest enabled unlock level) |
| Initial knowledge cost | 1 |
| Base knowledge cost | 1 |
| Cost factor | 0.2 |
| Tick interval (ms) | 1000 (framework default) |
| Menu lines | Surfaces ignored while sprinting; Farmland > pressure plates > sweet berries > powder snow |
| Milestones | `challenge_agility_featherfoot_500` on `agility.featherfoot.surfaces-ignored` at 500, reward 300; `challenge_agility_featherfoot_5k` at 5000, reward 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-featherfoot.toml` |

Listened events: `PlayerMoveEvent`, `PlayerInteractEvent`, `EntityInsideBlockEvent`, `PlayerInputEvent`, `PlayerToggleSprintEvent`, `PlayerQuitEvent`.

Disabling a surface group or moving its minimum level changes the adaptation's own max level, because max level is set to the highest enabled unlock level whenever the config is normalized.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `requireSprint` | `true` | When true, none of the protections apply unless you are sprinting. |
| `farmlandEnabled` | `true` | Turns the farmland protection off entirely when false. |
| `farmlandMinLevel` | `1` | Level at which farmland stops being trampled. |
| `farmlandMaterials` | `["FARMLAND"]` | Blocks covered by the trample protection. |
| `pressurePlateEnabled` | `true` | Turns the pressure-plate protection off entirely when false. |
| `pressurePlateMinLevel` | `2` | Level at which pressure plates stop triggering under you. |
| `pressurePlateUseVanillaTag` | `true` | Also covers every block in the vanilla pressure plate tag. |
| `pressurePlateMaterials` | `[]` | Extra blocks treated as pressure plates. |
| `berryBushEnabled` | `true` | Turns the sweet-berry protection off entirely when false. |
| `berryBushMinLevel` | `3` | Level at which sweet-berry slowdown and contact damage are ignored. |
| `berryBushMaterials` | `["SWEET_BERRY_BUSH"]` | Blocks whose slowdown and contact damage are ignored. |
| `powderSnowEnabled` | `true` | Turns the powder-snow protection off entirely when false. |
| `powderSnowMinLevel` | `4` | Level at which powder-snow freezing is cleared on contact. |
| `powderSnowMaterials` | `["POWDER_SNOW"]` | Blocks whose freezing effect is cleared. |

### Vault

| Property | Default |
|----------|---------|
| Class | `AgilityVault` |
| Icon | `OAK_FENCE` |
| Max level | 1 (locked in code, config overrides are reset on reload) |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0 (locked in code) |
| Tick interval (ms) | 1000 |
| Menu lines | Fence jump apex (blocks) |
| Milestones | `challenge_agility_vault_250` on `agility.vault.vaults` at 250, reward 300; `challenge_agility_vault_2500` at 2500, reward 1200 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-vault.toml` |

Listened events: `PlayerMoveEvent`, `PlayerTeleportEvent`, `PlayerGameModeChangeEvent`, `PlayerDeathEvent`, `PlayerQuitEvent`, and `PlayerJumpEvent` through a Paper-only companion listener.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `jumpHeight` | `1.75` | Jump apex in blocks when clearing a fence. Values below the built-in minimum are clamped up. |
| `xpPerVault` | `3` | Skill XP paid per successful vault. |

### Marathoner

| Property | Default |
|----------|---------|
| Class | `AgilityMarathoner` |
| Icon | `LEATHER_BOOTS` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 2 |
| Cost factor | 0.4 |
| Tick interval (ms) | 1000 (framework default) |
| Menu lines | Sprint saturation drain reduction |
| Milestones | `challenge_agility_marathoner_5k` on `agility.marathoner.saturation-saved` at 5000, reward 400; `challenge_agility_marathoner_50k` at 50000, reward 1500 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-marathoner.toml` |

Listened events: `EntityExhaustionEvent`, filtered to the `SPRINT` and `JUMP_SPRINT` reasons.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `drainReductionBase` | `0.15` | Fraction of sprint exhaustion removed before level scaling, 0-1. |
| `drainReductionFactor` | `0.45` | Extra fraction removed at max level. |
| `maxDrainReduction` | `0.6` | Hard cap on the removed fraction regardless of level. |
| `xpPerSaturationSaved` | `0.6` | Skill XP paid per unit of exhaustion saved. |

### Kip-Up

| Property | Default |
|----------|---------|
| Class | `AgilityKipUp` |
| Icon | `SHIELD` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 3 |
| Cost factor | 0.55 |
| Tick interval (ms) | 1000 (framework default) |
| Menu lines | Recovery window; Recovery speed boost tier |
| Milestones | `challenge_agility_kip_up_100` on `agility.kip-up.recoveries` at 100, reward 300; `challenge_agility_kip_up_1k` at 1000, reward 1200 |
| Config file | `plugins/Adapt/adapt/adaptations/agility-kip-up.toml` |

Listened events: `EntityDamageByEntityEvent`, `PlayerMoveEvent`, `PlayerQuitEvent`.

| Key | Code default | What it does |
|-----|--------------|--------------|
| `recoveryWindowMillisBase` | `350` | Milliseconds after a hit during which a jump counts as a recovery, before level scaling. |
| `recoveryWindowMillisFactor` | `550` | Extra milliseconds of recovery window at max level. |
| `speedAmplifierBase` | `0` | Speed effect amplifier granted on a recovery before level scaling. |
| `speedAmplifierFactor` | `1.6` | Extra amplifier granted at max level. |
| `speedDurationTicks` | `40` | Duration in ticks of the recovery speed burst. |
| `recoverySpeed` | `0.5` | Horizontal velocity applied toward your intended direction on recovery. |
| `jumpVelocityThreshold` | `0.2` | Minimum upward velocity treated as a jump when checking for a recovery. |
| `cooldownMillis` | `3000` | Milliseconds between recoveries. |
| `xpPerRecovery` | `5` | Skill XP paid per successful recovery. |

### Support classes

`AgilityJumpPhysics` is not a player adaptation. It converts between jump strength, jump height, and the extra velocity needed to reach a target height, and is used by Super Jump, Wall Jump, and Vault.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
