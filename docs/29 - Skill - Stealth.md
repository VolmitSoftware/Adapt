# Skill: Stealth

Stealth is the rogue skill. You level it by sneaking around and by hurting or killing things while crouched, and it pays best when you open a fight from a shadow instead of walking into it. It has 14 adaptations and shows up in the menu as a dark gray `WITHER_ROSE`.

The one that matters most is Stealth itself. It runs a concealment session while you sneak, watches every nearby mob and player to work out whether anyone can actually see you, and multiplies your melee damage when nobody can. Cutpurse and Assassinate both hang off that same undetected check, so learning the core first is the whole point of the tree.

Around that sit the utility picks. Sneak Speed makes crouching bearable, Item Snatch vacuums drops off the floor, Ghost's Armor charges a free hit absorber while you avoid damage, Stealth Vision lights up the dark and reveals invisible players, Enderveil stops endermen aggroing on you, Trap Sense outlines traps and stops sculk hearing your footsteps, and Umbral Recovery feeds you and stretches your invisibility on every sneaking kill.

Then there are the escapes. Shadow Decoy leaves a copy of you behind and turns you invisible, Decoy Swap teleports you to that copy, Shadowmeld makes you actually invisible for staying still and unseen, and Smoke Pellet throws an aimed cloud that blinds everything in it and wipes aggro out to 64 blocks.

## Adaptations

Everything below needs the same four things before it does anything: the adaptation learned at level 1 or higher, the skill and the adaptation both enabled in config, the matching `adapt.use.*` permission, and any protection or region plugin allowing the action. Those preconditions are not repeated per adaptation.

### Stealth (`stealth-silent-step`)

The core of the tree. Sneaking opens a concealment session. Every quarter second the plugin scans nearby mobs and players, checks whether each one is looking roughly at you and has line of sight, and decides whether you are detected. Being invisible, having an active Shadow Decoy, or sitting in a Smoke Pellet cloud all count as undetected no matter who is looking.

While a session is open, mobs that had you as their target let go of you, targeting events aimed at you are cancelled, and you take no fall damage. While you are actually undetected your screen dims, and nearby threats are outlined for you alone: red for anyone who can see you, gray for anyone about to.

Attacking while undetected multiplies your damage. Mobs take a bigger bonus than players. Land five backstabs inside ten seconds and you get the Unseen Blade advancement.

1. Learn Stealth in the Adapt menu.
2. Sneak. The dim and the outlines tell you the session is live.
3. Stay out of the red outlines. Break line of sight or get behind them.
4. Melee an observer that has not spotted you. The hit lands with the backstab multiplier.

Four dangerous mobs ignore the targeting suppression by default: warden, wither, phantom, and ender dragon. That list is a config knob.

### Sneak Speed (`stealth-speed`)

Crouching stops being punishing. Each level adds sneaking speed, and at max level with default settings you sneak at full walk speed, which is the vanilla cap. Crawling on land gets a small extra multiplier.

It also gives you auto-stepping while active: extra step height so you walk up one-block ledges without jumping, and an auto-step-down so you drop off one-block edges while moving instead of stopping at the lip.

It runs while you sneak or crawl, on the ground, in survival or adventure mode. Riding, flying, gliding, and being in water all switch it off.

Passive. Learn it and sneak.

### Item Snatch (`stealth-snatch`)

Sneak and dropped items within range fly into your inventory. It keeps pulling on a repeating pulse for as long as you stay crouched, so you can walk a mob-farm floor without clicking anything.

Every item goes through the normal Bukkit pickup event with your real remaining inventory space, so a plugin that cancels the pickup leaves the item on the ground untouched, and a full inventory is skipped rather than eaten.

1. Learn Item Snatch.
2. Stand near dropped items.
3. Hold sneak. Items pull in on each pulse until you stand up.

### Ghost's Armor (`stealth-ghost-armor`)

A recharging armor buffer. It ticks upward while you are alive and not being hit, adding armor points on top of whatever you are wearing. The next hit that armor would normally apply to eats the entire buffer at once and the buffer starts refilling from zero.

Damage that ignores armor in vanilla also ignores this, so it will not save you from the void or from starving.

Passive. Learn it and let it charge.

### Stealth Vision (`stealth-vision`)

Three things while you sneak: you get Night Vision, incoming Blindness is refused outright, and any invisible player near you gets a private outline that only you can see. Stand up and all three go away, including the Night Vision the adaptation applied.

It only cleans up its own Night Vision. A potion you drank yourself is left alone.

Passive, single level. Learn it and sneak.

### Enderveil (`stealth-enderveil`)

Endermen stop caring about you. At level 1 the protection applies while you are sneaking; at level 2 it applies always, so you can stare at them across an End highlands with no pumpkin on your head. At level 2 a slow portal particle orbits your head whenever an enderman is nearby.

Passive.

### Shadow Decoy (`stealth-shadow-decoy`)

Stop sneaking and you leave a copy of yourself behind, wearing your skin and your gear. Nearby mobs that were hunting you retarget onto the decoy. You go invisible for as long as the decoy lives, with your equipment hidden and a thin smoke trail marking where you actually are.

The decoy cannot be killed. Damage to it is cancelled, but it does react: hits knock it around and it plays a hurt sound, so an attacker keeps swinging.

1. Learn Shadow Decoy.
2. Sneak.
3. Stand up. The decoy spawns where you were standing.
4. Walk away while it holds aggro. It expires on its own, sooner at low level.
5. Wait out the cooldown, which shrinks as you level.

### Shadowmeld (`stealth-shadowmeld`)

Hold a sneak while Stealth reports nobody can see you, and after a short delay you turn genuinely invisible and mobs stop being able to target you. The delay is three seconds at level 1 and drops to a quarter second at max level.

The meld breaks the moment you do anything: attack, get hurt, interact with a block or entity, get spotted, or stand up.

1. Learn Stealth and Shadowmeld.
2. Sneak somewhere nobody has line of sight on you.
3. Hold it. The meld fires with a smoke burst and a sculk click.
4. Move if you want, but do not act. Attacking, taking damage, or right-clicking ends it.

### Smoke Pellet (`stealth-smoke-pellet`)

Hold gunpowder and sneak. One gunpowder is spent and a smoke cloud is thrown along your aim, stopping at the first block or living entity it hits, up to a long range. The cloud pulses for several seconds.

Everything living inside the cloud goes blind. Players inside go invisible and get a concealment lease that lasts a couple of seconds past each pulse. Mobs inside drop their target, and while the lease holds, mobs within 64 blocks of the cloud cannot reacquire a concealed player. Even a warden angry at a concealed player has that anger cleared.

1. Learn Smoke Pellet.
2. Put gunpowder in your main hand or off hand.
3. Aim where you want the cloud.
4. Press sneak. One gunpowder is consumed and the cloud lands.
5. Walk out of the fight while everything in the cloud is blind.

### Cutpurse (`stealth-cutpurse`)

Hit a pillager, vindicator, piglin, or piglin brute while Stealth reports you undetected and you may roll its loot table and take a few stacks straight into your inventory. The mob lives. Each mob can only be picked once, ever, and the stolen items go to the ground if your inventory is full.

Passive on top of the core check. Get behind the mob, hit it, keep the loot.

### Trap Sense (`stealth-trap-sense`)

While you sneak, nearby trapped chests, tripwire, tripwire hooks, pressure plates, and sculk blocks are outlined for you alone. Sculk blocks glow teal, tripwire glows yellow, everything else glows red.

It also quiets your footsteps. Below max level there is a chance per movement vibration that a sculk sensor or shrieker does not hear you at all, and it only applies while sneaking. At max level every movement vibration you produce is suppressed, sneaking or not, and the block that would have heard you is outlined instead.

1. Learn Trap Sense.
2. Sneak as you enter an ancient city or a suspicious hallway.
3. Watch for the outlines and route around them.

### Assassinate (`stealth-assassinate`)

A finisher. Hit an eligible mob while Stealth reports you undetected and the damage is replaced with exactly the mob's current health, so it dies in one hit with no overkill number. It only works on mobs whose maximum health is under a level-scaled cap, and it is on a long cooldown that shortens as you level.

Players, anything implementing `Boss`, and the warden are excluded.

1. Learn Stealth and Assassinate.
2. Sneak up on a mob nobody has noticed you near.
3. Check that it is not a boss and not too tough for your level.
4. Melee it once.
5. Wait out the cooldown.

### Decoy Swap (`stealth-decoy-swap`)

Needs Shadow Decoy learned. While your decoy is alive and inside range, double-tap sneak and you and the decoy trade places. The escape and the reposition are the same button.

1. Learn Shadow Decoy and Decoy Swap.
2. Sneak and stand up to drop a decoy.
3. Run. The decoy stays where it was.
4. Tap sneak twice quickly. You swap into the decoy's position and it takes yours.
5. Wait out the cooldown before the next swap.

### Umbral Recovery (`stealth-umbral-recovery`)

Every kill you make while crouched feeds you and, if you are already invisible, extends that invisibility. It is what keeps a long stealth run going without eating or re-brewing. It does nothing if you are already at full hunger and not invisible.

Passive. Kill while sneaking.

## Reference

Everything below is exact code truth. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`. Every adaptation TOML also carries the shared keys `enabled`, `permanent`, `showParticles`, and `showSounds`, which are not repeated per adaptation.

### Identity

| Property | Value |
|----------|-------|
| Skill id | `stealth` |
| Class | `SkillStealth` |
| Icon | `WITHER_ROSE` |
| Color | `DARK_GRAY` |
| Interval (ms) | `1412` |
| Skill config | `plugins/Adapt/adapt/skills/stealth.toml` |
| Adaptation count | 14 |

### Skill XP sources

| Trigger | Award | Notes |
|---------|-------|-------|
| Passive sneak pulse (every `1412` ms) | `sneakXP` scaled by elapsed time over the interval | Requires sneaking and not swimming, sprinting, flying, or gliding, in survival or adventure mode. |
| Damaging any valid living entity while sneaking | Damage dealt times `sneakCombatXPMultiplier` | Rate-limited by `sneakCombatXpCooldown`. The `stealth.damage.sneaking` stat is added before the rate limit, so the stat always counts. Parrots and the invalid-damageable entity listing are excluded. |
| Killing while sneaking | `sneakKillXP` | Tragoul skeletal servants and Excavation grave mobs are excluded. |
| Launching a projectile while sneaking | No XP | Adds `stealth.arrows.sneaking` only. |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/stealth.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Set to false to disable the whole skill. |
| `skillColor` | `"&8"` | Legacy ampersand color code used for this skill in menus and text. |
| `challengeSneak1kReward` | `1750` | XP paid for `challenge_sneak_1k`. |
| `challengeSneak5kReward` | `3500` | XP paid for `challenge_sneak_5k`. |
| `challengeSneak20kReward` | `8750` | XP paid for `challenge_sneak_20k`. |
| `sneakXP` | `0.4` | Base passive sneak XP per interval, before cadence scaling. |
| `sneakCombatXPMultiplier` | `3.0` | Multiplier applied to damage dealt while sneaking when converting it to XP. |
| `sneakCombatXpCooldown` | `1250` | Milliseconds between XP awards for sneaking combat damage. |
| `sneakKillXP` | `15` | XP for a kill made while sneaking. |
| `challengeStealthDmg500Reward` | `1500` | XP paid for `challenge_stealth_dmg_500`. |
| `challengeStealthDmg5kReward` | `5000` | XP paid for `challenge_stealth_dmg_5k`. |
| `challengeStealthKills10Reward` | `1000` | XP paid for `challenge_stealth_kills_10`. |
| `challengeStealthKills100Reward` | `5000` | XP paid for `challenge_stealth_kills_100`. |
| `challengeStealthArrows50Reward` | `1250` | XP paid for `challenge_stealth_arrows_50`. |
| `challengeStealthArrows500Reward` | `5000` | XP paid for `challenge_stealth_arrows_500`. |

### Skill milestones

| Challenge key | Stat key | Threshold | Reward |
|---------------|----------|-----------|--------|
| `challenge_sneak_1k` | `move.sneak` | 1000 | `challengeSneak1kReward` |
| `challenge_sneak_5k` | `move.sneak` | 5000 | `challengeSneak5kReward` |
| `challenge_sneak_20k` | `move.sneak` | 20000 | `challengeSneak20kReward` |
| `challenge_stealth_dmg_500` | `stealth.damage.sneaking` | 500 | `challengeStealthDmg500Reward` |
| `challenge_stealth_dmg_5k` | `stealth.damage.sneaking` | 5000 | `challengeStealthDmg5kReward` |
| `challenge_stealth_kills_10` | `stealth.kills.sneaking` | 10 | `challengeStealthKills10Reward` |
| `challenge_stealth_kills_100` | `stealth.kills.sneaking` | 100 | `challengeStealthKills100Reward` |
| `challenge_stealth_arrows_50` | `stealth.arrows.sneaking` | 50 | `challengeStealthArrows50Reward` |
| `challenge_stealth_arrows_500` | `stealth.arrows.sneaking` | 500 | `challengeStealthArrows500Reward` |

`move.sneak` is written by the Agility movement tracker, not by Stealth. The other stat keys are written by `SkillStealth` itself.

### Stealth

| Property | Value |
|----------|-------|
| Class | `StealthCore` |
| Icon | `WHITE_WOOL` |
| Max level | 2 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 2 |
| Cost factor | 0.325 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-silent-step.toml` |

Menu stat lines: Mob Detection Suppression Radius; Mob Backstab Damage Bonus; Player Backstab Damage Bonus.

Listened events:

- `PlayerToggleSneakEvent` (`MONITOR`) - starts or stops the concealment session
- `PlayerMoveEvent` (`MONITOR`, `@RunsWithoutLearnedAdaptation`) - restarts a session after a state change and clears leftover concealment
- `EntityTargetLivingEntityEvent` (`HIGHEST`, ignore cancelled) - cancels targeting on concealed players and nulls existing targets
- `EntityDamageByEntityEvent` (`HIGHEST`) - applies the backstab multiplier
- `PlayerQuitEvent` (`MONITOR`) - clears the session

Detection uses a look-dot check plus a line-of-sight check against the player's eye location. An observer at or above `detectionLookDotThreshold` with line of sight can detect you; between the threshold minus `almostLookDotMargin` and the threshold it is an almost-detect. Forced concealment (invisible, an Invisibility effect, an active Shadow Decoy, or a Smoke Pellet lease) short-circuits detection entirely. While a session is open the adaptation applies `SAFE_FALL_DISTANCE` of `1024` under the `fall` slot, so you take no fall damage while concealed. While undetected it applies `DARKNESS` to the concealing player at `dimAmplifier`. Backstab XP is `xpPerBonusDamage` times the final damage after the multiplier; target drops pay `xpPerTargetDrop` per mob that let go of you in a scan.

Hard caps not exposed in config: stealth radius 16 blocks, player detection radius 24 blocks, scan completion delay 4 ticks, dim duration floor 160 ticks, 16 scan dispatches per tick, 16 scan completions per tick, 128 owner refreshes per tick.

Milestones: `challenge_stealth_silent_200` on `stealth.silent-step.backstabs` at 200, reward 400. The `challenge_stealth_silent_5in10` advancement is granted directly in code after 5 backstabs within 10 seconds and has no stat milestone.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `6` | Mob scan radius in blocks, before level scaling. Clamped to 1-16. |
| `radiusFactor` | `8` | Extra mob scan radius gained at max level. |
| `playerDetectionRadiusBase` | `10` | Player scan radius in blocks, before level scaling. Clamped to 1-24. |
| `playerDetectionRadiusFactor` | `14` | Extra player scan radius gained at max level. |
| `dimDurationTicksBase` | `20` | Darkness duration in ticks, before level scaling. The result is floored at a hard minimum of 160 ticks, so this default has no effect. |
| `dimDurationTicksFactor` | `20` | Extra Darkness ticks gained at max level. Also floored out by the 160 tick minimum at these defaults. |
| `dimAmplifier` | `0` | Amplifier of the Darkness effect applied while undetected. |
| `mobBackstabBase` | `1.5` | Damage multiplier against mobs, before level scaling. |
| `mobBackstabFactor` | `0.5` | Extra damage multiplier against mobs at max level. |
| `playerBackstabBase` | `1.25` | Damage multiplier against players, before level scaling. |
| `playerBackstabFactor` | `0.35` | Extra damage multiplier against players at max level. |
| `xpPerTargetDrop` | `2` | Skill XP per mob that dropped you as its target during a scan. |
| `xpPerBonusDamage` | `3.0` | Skill XP per point of final backstab damage. |
| `showThreatGlows` | `true` | Shows per-viewer glowing on nearby threats while sneaking. Red means can detect, gray means almost. |
| `almostLookDotMargin` | `0.2` | How far below the detection threshold still counts as an almost-detect. Clamped to 0-2. |
| `detectionLookDotThreshold` | `0.2` | Dot product of the observer's look vector toward you at or above which the observer detects you. Clamped to -1 to 1. |
| `allMobsAffectStealthVisibility` | `true` | True lets every nearby mob, passive included, break your hidden state with line of sight. False restricts that to blacklisted types. |
| `targetingBlacklistTypes` | `["WARDEN", "WITHER", "PHANTOM", "ENDER_DRAGON"]` | Entity types exempt from targeting suppression. These keep hunting you. |
| `threatScanIntervalMillis` | `250` | Milliseconds between threat-awareness scans while sneaking. |
| `maxTargetDropEntitiesPerScan` | `32` | Maximum mobs inspected by each target-drop scan. |
| `maxThreatEntitiesPerScan` | `32` | Maximum mobs and players inspected by each threat-awareness scan. |
| `threatScanCompletionDelayTicks` | `2` | Ticks a scan waits for per-entity checks before applying a partial result. Clamped to 1-4. |

### Sneak Speed

| Property | Value |
|----------|-------|
| Class | `StealthSpeed` |
| Icon | `MUSHROOM_STEW` |
| Max level | 3 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Tick interval (ms) | `setInterval`, default 50 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-speed.toml` |

Menu stat line: Sneaking Speed.

Listened events:

- `PlayerToggleSneakEvent` - starts a session on sneak, ends it on stand unless crawling
- `PlayerMoveEvent` - starts a session for a sneaking or crawling player that has none
- `PlayerDeathEvent` - clears state
- `PlayerQuitEvent` - clears state

Applies `SNEAKING_SPEED` as a `MULTIPLY_SCALAR_1` modifier under the `sneak` slot, and `STEP_HEIGHT` under the `step` slot when auto-step-up is on. Eligibility requires sneaking or crawling on land, survival or adventure mode, not dead, not in a vehicle, not flying, not gliding, and grounded unless `requireGrounded` is false. Water disables it unless `allowWhileInWater` is true. Milestone: `challenge_stealth_speed_5k` on `stealth.speed.blocks-sneak-sprinted` at 5000, reward 400.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `setInterval` | `50` | Milliseconds between refresh passes. Also the adaptation's registered tick interval. |
| `baselineWalkSpeed` | `0.2` | Walk speed used as the reference when the player's live walk speed is effectively zero. |
| `maxSpeedBonus` | `0.4666666666666667` | Walk-speed-equivalent bonus at max level. The default lands max level exactly on the vanilla sneak-speed cap. |
| `crawlBonusMultiplier` | `1.15` | Multiplier applied to the bonus while crawling on land. |
| `minWalkSpeed` | `-1` | Lower clamp on the walk speed used in the scalar math. |
| `maxWalkSpeed` | `1` | Upper clamp on the walk speed used in the scalar math. |
| `enableAutoStep` | `true` | Master switch for both auto-step behaviors. |
| `enableAutoStepUp` | `true` | Applies the step height modifier so one-block ledges are walked up. |
| `stepHeightBonus` | `0.4` | Extra step height in blocks applied while active. |
| `enableAutoStepDown` | `true` | Allows stepping down one block while moving. |
| `autoStepProbeDistance` | `0.45` | Forward probe distance in blocks for the step-down check. |
| `autoStepForwardPush` | `0.36` | Horizontal push applied during each step-down teleport. |
| `autoStepUseInput` | `true` | Uses raw movement input for step-down direction when the server exposes it. |
| `autoStepVelocityThreshold` | `0.01` | Minimum horizontal velocity before step-down runs. |
| `autoStepCooldownMs` | `90` | Minimum milliseconds between step-down teleports. |
| `doubleHeadroomHeightThreshold` | `1.7` | Bounding box height above which a step-down destination needs two blocks of headroom. |
| `crawlHeightMax` | `0.61` | Bounding box height at or below which the player counts as crawling on land. |
| `requireGrounded` | `true` | Requires the player to be on the ground for the boost to run. |
| `allowWhileInWater` | `false` | Allows the boost while in water or swimming. |
| `movementVelocityThreshold` | `0.005` | Minimum horizontal velocity to count as moving for particles and stat credit. |
| `showSoulParticles` | `true` | Shows a soul or ash particle at the player's feet while active. |
| `soulParticleChance` | `0.3` | Chance per refresh pass to spawn that particle, 0-1. |
| `soulParticleYOffset` | `0.02` | Vertical offset in blocks for the particle. |
| `activationSoundVolume` | `1.6` | Volume of the activation sound. |
| `activationSoundPitch` | `0.9` | Pitch of the activation sound. |
| `activationSoundCooldownMs` | `250` | Minimum milliseconds between activation sounds. |
| `statIntervalMs` | `200` | Minimum milliseconds between stat increments while moving. |

### Item Snatch

| Property | Value |
|----------|-------|
| Class | `StealthSnatch` |
| Icon | `CHEST_MINECART` |
| Max level | 3 |
| Initial knowledge cost | 12 |
| Base knowledge cost | 4 |
| Cost factor | 0.125 |
| Tick interval (ms) | 50 while a snatch session is open, 1000 when idle |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-snatch.toml` |

Menu stat line: Snatch Radius.

Listened events:

- `PlayerToggleSneakEvent` (`MONITOR`, ignore cancelled) - snatches immediately and opens a repeating session
- `PlayerQuitEvent` (`MONITOR`) - closes the session

Each pulse inspects at most `128` nearby entities and takes at most `32` items, searching a box of radius by radius over 1.5 by radius around the player. Every candidate must pass a chest-access check, an inventory space check, and Bukkit's normal pickup event sequence; a cancelled pickup leaves the item entity untouched. Items are held for up to `5000` ms to avoid double pulls. On Folia the scan runs only when the whole footprint belongs to the current region. Milestones: `challenge_stealth_snatch_2500` on `stealth.snatch.items-snatched` at 2500 (reward 400); `challenge_stealth_snatch_25k` at 25000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `snatchRate` | `250` | Milliseconds between repeat snatch pulses while the player stays sneaking. |
| `radiusFactor` | `5.55` | Blocks of snatch radius gained at max level. Radius is `levelPercent * radiusFactor + 1`, clamped to 1-8 blocks. |

### Ghost's Armor

| Property | Value |
|----------|-------|
| Class | `StealthGhostArmor` |
| Icon | `CHAINMAIL_HELMET` |
| Max level | 7 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 3 |
| Cost factor | 0.335 |
| Tick interval (ms) | 5353 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-ghost-armor.toml` |

Menu stat lines: Max Ghost Armor; Speed.

Listened events:

- `EntityDamageEvent` (`HIGHEST`, ignore cancelled) - consumes the whole buffer on an armor-respecting hit
- `PlayerJoinEvent` (`MONITOR`) - starts the charge session
- `PlayerRespawnEvent` (`MONITOR`) - restarts the charge session
- `PlayerMoveEvent` (`MONITOR`) - starts a session if none exists, re-checked at most every 5000 ms
- `PlayerDeathEvent` (`MONITOR`) - clears the session and the armor modifier
- `PlayerQuitEvent` (`MONITOR`) - clears the session and the armor modifier

Applies the `ARMOR` attribute under the `armor` slot as an additive modifier that grows by the per-tick amount every refresh. Both the ceiling and the per-refresh gain are interpolated between the min and max knob by level percent, and both are clamped to 0-20. XP on consumption is `min(10, 2.5 * incoming damage)`. Damage tagged as bypassing armor does not consume the buffer. Milestones: `challenge_stealth_ghost_100` on `stealth.ghost-armor.armor-consumed` at 100 (reward 300); `challenge_stealth_ghost_500` at 500 (reward 1000).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxArmor` | `16` | Armor points the buffer holds at max level. |
| `minArmor` | `2` | Armor points the buffer holds at level 1. |
| `maxArmorPerTick` | `3` | Armor points added per refresh at max level. |
| `minArmorPerTick` | `1` | Armor points added per refresh at level 1. |

### Stealth Vision

| Property | Value |
|----------|-------|
| Class | `StealthSight` |
| Icon | `POTION` |
| Max level | 1 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 2 |
| Cost factor | 0.6 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-vision.toml` |

Menu stat lines: Gain a burst of night vision while sneaking; Blindness immunity while sneaking; Invisible players glow while sneaking.

Listened events:

- `PlayerToggleSneakEvent` - begins or ends the sight session
- `EntityPotionEffectEvent` (`LOWEST`, ignore cancelled, `onBlindness`) - cancels Blindness being added or changed on a sneaking learner
- `EntityPotionEffectEvent` (`MONITOR`, ignore cancelled) - drops ownership when something else changes the player's Night Vision
- `PlayerQuitEvent` (`LOWEST`) - clears tracked state and owned outlines

Tracking runs at most every `500` ms per player, at most `16` players per tick. Invisible-player outlines are private viewer glows on a `1500` ms lease, refreshed while both players stay in range. Search range is the server view distance in blocks, floored at 16 and capped at `160`, and at most `128` players are inspected per pass. Milestone: `challenge_stealth_sight_sneak_1h` on `stealth.sight.sneaking-ticks` at 72000, reward 400.

No adaptation-specific config knobs.

### Enderveil

| Property | Value |
|----------|-------|
| Class | `StealthEnderVeil` |
| Icon | `CARVED_PUMPKIN` |
| Max level | 2 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 6 |
| Cost factor | 1.0 |
| Tick interval (ms) | 9182 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-enderveil.toml` |

Menu stat line: Prevent enderman attacks while sneaking at level 1, Prevent all enderman attacks at level 2.

Listened events:

- `EntityTargetLivingEntityEvent` (`LOWEST`, ignore cancelled, `onTarget`) - cancels enderman targeting
- `EndermanAttackPlayerEvent` (`LOWEST`, ignore cancelled, reflective handler, `onTarget`) - cancels direct enderman aggression
- `PlayerQuitEvent` (`MONITOR`) - stops the ambient particle session

At level 1 suppression requires sneaking; at level 2 it always applies. At level 2 a portal particle orbits the player's eye position on a 4 second cycle while an enderman is within 16 blocks. The suppression puff effect is throttled to once every `2000` ms. Milestone: `challenge_stealth_ender_veil_200` on `stealth.ender-veil.stares-survived` at 200, reward 300.

No adaptation-specific config knobs.

### Shadow Decoy

| Property | Value |
|----------|-------|
| Class | `StealthShadowDecoy` |
| Icon | `PLAYER_HEAD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 5 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-shadow-decoy.toml` |

Menu stat lines: Decoy Duration; Decoy Attraction Radius; Decoy Cooldown.

Listened events:

- `PlayerToggleSneakEvent` (`HIGHEST`, ignore cancelled) - spawns the decoy when sneaking ends
- `EntityDamageEvent` (`HIGHEST`) - cancels all damage to the decoy anchor and plays hit feedback
- `PlayerAnimationEvent` (`HIGHEST`, ignore cancelled) - ray traces attack swings against the decoy
- `PlayerQuitEvent` (`MONITOR`) - removes active decoys and cooldown state

The decoy is an invisible, non-persistent armor stand anchor plus a packet-only fake player wearing the owner's skin and equipment. If the packet decoy cannot be created and `legacyFallbackEnabled` is true, the armor stand is made visible instead. Spawning is skipped when the player carries the `adapt-mutation-exposed` metadata. Aggro redirection skips mobs protected as friendly to the owner, including tamed pets. Cooldown floors at 1000 ms and duration floors at 20 ticks. Milestones: `challenge_stealth_decoy_100` on `stealth.shadow-decoy.decoys-spawned` at 100 (reward 300); `challenge_stealth_decoy_distract_500` on `stealth.shadow-decoy.mobs-distracted` at 500 (reward 1000).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldownMillisBase` | `18000` | Milliseconds between decoys, before level scaling. |
| `cooldownMillisFactor` | `12000` | Milliseconds removed from the cooldown at max level. Floors at 1000 ms. |
| `decoyTicksBase` | `60` | Decoy lifetime in ticks, before level scaling. |
| `decoyTicksFactor` | `80` | Extra lifetime ticks gained at max level. Floors at 20 ticks. |
| `decoyRadiusBase` | `8` | Aggro redirect radius in blocks, before level scaling. |
| `decoyRadiusFactor` | `10` | Extra redirect radius gained at max level. |
| `decoyEyeHeight` | `1.62` | Eye height used when facing the fake player at viewers. |
| `tabListRemoveDelayTicks` | `40` | Ticks before the skinned fake player is pulled from the tab list. |
| `legacyFallbackEnabled` | `true` | Falls back to a visible armor stand when packet NPC creation fails. |
| `ownerInvisibilityRefreshTicks` | `30` | Duration in ticks of each owner Invisibility refresh. |
| `ownerInvisibilityAmplifier` | `0` | Amplifier of the owner Invisibility effect. |
| `ownerTrailParticles` | `5` | Smoke particles spawned around the invisible owner per burst. |
| `ownerTrailHorizontalSpread` | `0.18` | Horizontal spread in blocks of the owner smoke trail. |
| `ownerTrailVerticalSpread` | `0.05` | Vertical spread in blocks of the owner smoke trail. |
| `ownerTrailYOffset` | `0.1` | Vertical offset in blocks of the trail spawn point. |
| `ownerTrailSpeed` | `0.01` | Particle speed of the owner smoke trail. |
| `ownerTrailIntervalMillis` | `75` | Milliseconds between owner trail bursts. Floors at 25 ms. |
| `ownerEquipmentHideResendMillis` | `250` | Milliseconds between resends of the owner equipment-hide packets. |
| `aggroRedirectIntervalMillis` | `150` | Milliseconds between aggro redirect scans. Floors at 25 ms. |
| `maxAggroEntitiesPerScan` | `32` | Maximum mobs dispatched by each redirect scan. |
| `maxPacketViewers` | `64` | Maximum players sent decoy and equipment packets for one decoy. |
| `maxViewerAddsPerRefresh` | `8` | Maximum newly tracked viewers initialized per refresh. |
| `maxViewerLookUpdatesPerRefresh` | `16` | Maximum viewer-facing rotations sent per refresh. |
| `decoyHitKnockback` | `0.28` | Horizontal knockback applied to the decoy when hit. |
| `decoyHitLift` | `0.08` | Vertical lift applied to the decoy when hit. |
| `decoySwingDetectionReach` | `4.5` | Ray distance in blocks used to detect swings at the decoy. |
| `decoySkinLayerMask` | `127` | Bitmask of visible skin layers on the fake player. |
| `xpOnDecoy` | `18` | Skill XP granted per decoy spawned. |

### Shadowmeld

| Property | Value |
|----------|-------|
| Class | `StealthShadowmeld` |
| Icon | `SCULK` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.4 |
| Tick interval (ms) | 250 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-shadowmeld.toml` |

Menu stat line: Undetected Sneak Delay.

Listened events:

- `PlayerToggleSneakEvent` (`MONITOR`) - opens the meld session on sneak, ends it on stand
- `EntityTargetLivingEntityEvent` (`LOWEST`, ignore cancelled) - cancels targeting on a melded player
- `EntityDamageByEntityEvent` (`MONITOR`, ignore cancelled, `onAct`) - breaks the meld when the player attacks
- `EntityDamageEvent` (`MONITOR`, ignore cancelled, `onHurt`) - breaks the meld on any damage with final damage above 0
- `PlayerInteractEvent` (`onInteract`) - breaks the meld on any interaction
- `PlayerQuitEvent` (`LOWEST`) - ends the session

Eligibility is sneaking plus either a Smoke Pellet lease or Stealth reporting you currently undetected. The meld applies `INVISIBILITY` at amplifier 0 in `40` tick refreshes and removes it on break unless a Smoke Pellet lease still covers you. Meld delay interpolates linearly from `meldDelayStartMillis` at level 1 to `meldDelayEndMillis` at max level. At most `2048` sessions are tracked and `64` visited per tick. Milestones: `challenge_stealth_shadowmeld_100` on `stealth.shadowmeld.melds` at 100 (reward 350); `challenge_stealth_shadowmeld_1k` at 1000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `meldDelayStartMillis` | `3000` | Milliseconds of unbroken eligible sneaking before melding, at level 1. |
| `meldDelayEndMillis` | `250` | Milliseconds of unbroken eligible sneaking before melding, at max level. |
| `xpOnMeld` | `6` | Skill XP granted the moment you meld. |

### Smoke Pellet

| Property | Value |
|----------|-------|
| Class | `StealthSmokePellet` |
| Icon | `GUNPOWDER` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-smoke-pellet.toml` |

Menu stat lines: Cloud Radius; Cloud Duration.

Listened events:

- `PlayerToggleSneakEvent` (`NORMAL`, ignore cancelled) - consumes gunpowder and casts the cloud
- `EntityTargetLivingEntityEvent` (`HIGHEST`, ignore cancelled) - cancels targeting on a concealed player and clears the mob's target
- `PlayerQuitEvent` (`MONITOR`) - clears concealment state

The cloud pulses every `10` ticks. Each pulse blinds living entities inside for `40` ticks, renews the concealment lease and Invisibility of players inside for `40` ticks, and clears mob targets. Aggro clearing also sweeps mobs within `64` blocks of the cloud center, including a warden's anger at a concealed player. At most `24` targets are handed off per tick. Ray range is clamped to 2-64 blocks. The gunpowder cost goes through the normal item-cost path, so a cancelled cost aborts the throw. Milestones: `challenge_stealth_smoke_100` on `stealth.smoke-pellet.thrown` at 100 (reward 400); `challenge_stealth_smoke_1k` at 1000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `2.5` | Cloud radius in blocks, before level scaling. |
| `radiusFactor` | `2.5` | Extra cloud radius gained at max level. |
| `radiusMax` | `6.0` | Hard cap on the cloud radius after scaling. |
| `pulsesBase` | `8` | Number of 10-tick cloud pulses, before level scaling. |
| `pulsesFactor` | `10` | Extra pulses gained at max level. Minimum 1. |
| `raycastRange` | `24.0` | Maximum throw distance in blocks. Clamped to 2-64. |
| `cooldownMillis` | `1500` | Milliseconds between throws. |
| `xpOnThrow` | `10` | Skill XP granted per pellet thrown. |

### Cutpurse

| Property | Value |
|----------|-------|
| Class | `StealthCutpurse` |
| Icon | `SHEARS` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.4 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-cutpurse.toml` |

Menu stat lines: Steal Chance; Loot Stacks.

Listened events:

- `EntityDamageByEntityEvent` (`HIGHEST`, ignore cancelled) - rolls the steal

Eligible targets are `PILLAGER`, `VINDICATOR`, `PIGLIN`, and `PIGLIN_BRUTE`. The mob must not already carry the `cutpurse_picked` persistent key, and Stealth must report you undetected. On a success the mob's own loot table is rolled with `lootQuality` as luck, the first non-empty stacks up to the stack cap are taken, and the mob is stamped so it can never be picked again. Items that do not fit in your inventory drop at the mob's location. Milestones: `challenge_stealth_cutpurse_100` on `stealth.cutpurse.pockets-picked` at 100 (reward 400); `challenge_stealth_cutpurse_1k` at 1000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `stealChanceBase` | `0.25` | Chance per qualifying hit that a steal is attempted, 0-1, before level scaling. |
| `stealChanceFactor` | `0.4` | Extra steal chance gained at max level. |
| `stealChanceMax` | `0.9` | Hard cap on the steal chance, 0-1. |
| `lootQualityBase` | `0.0` | Luck value passed to the loot table roll, before level scaling. |
| `lootQualityFactor` | `2.0` | Extra luck gained at max level. |
| `lootStacksBase` | `1` | Item stacks taken per successful steal, before level scaling. |
| `lootStacksFactor` | `2` | Extra stacks gained at max level. Minimum 1. |
| `xpOnSteal` | `15` | Skill XP granted per successful steal. |

### Trap Sense

| Property | Value |
|----------|-------|
| Class | `StealthTrapSense` |
| Icon | `TRIPWIRE_HOOK` |
| Max level | 4 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.35 |
| Tick interval (ms) | 400 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-trap-sense.toml` |

Menu stat lines: Detection Range; Sculk Movement Suppression.

Listened events:

- `PlayerToggleSneakEvent` (`MONITOR`) - opens or closes the scan session and caches the sculk suppression state
- `BlockReceiveGameEvent` (`HIGHEST`, ignore cancelled) - suppresses movement vibrations reaching sculk blocks
- `PlayerQuitEvent` (`MONITOR`) - cancels scans and clears the viewer's block displays

Revealed blocks are `TRAPPED_CHEST`, `TRIPWIRE`, `TRIPWIRE_HOOK`, `SCULK_SENSOR`, `CALIBRATED_SCULK_SENSOR`, `SCULK_SHRIEKER`, and any material whose name ends in `_PRESSURE_PLATE`. Markers are private viewer block displays, colored RGB `40, 220, 210` for sculk, `255, 220, 45` for tripwire and hooks, and `255, 70, 70` for everything else. Each scan samples at most `4096` blocks and shows at most `96` markers, and the stat counts only newly revealed blocks. Suppressed game events are `STEP`, `SWIM`, `FLAP`, `HIT_GROUND`, `ELYTRA_GLIDE`, `SPLASH`, `BOUNCE` where present, `TELEPORT`, `ENTITY_MOUNT`, and `ENTITY_DISMOUNT`. At max level suppression is unconditional and does not require sneaking. Below max level the effective chance is `mercyMaxChance * (level / maxLevel)` and it only applies while sneaking. The cached suppression state expires `1500` ms after its last refresh. Milestones: `challenge_stealth_trap_500` on `stealth.trap-sense.traps-revealed` at 500 (reward 400); `challenge_stealth_trap_5k` at 5000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `rangeBase` | `4.0` | Trap reveal radius in blocks, before level scaling. |
| `rangeFactor` | `4.0` | Extra reveal radius gained at max level. The result is clamped to 3-8 blocks. |
| `mercyMaxChance` | `0.7` | Suppression chance at max level, used as a ceiling. Effective chance below max level is this value scaled by `level / maxLevel`. |
| `scanIntervalMillis` | `500` | Milliseconds between trap scans while sneaking. Floors at 200 ms. |

### Assassinate

| Property | Value |
|----------|-------|
| Class | `StealthAssassinate` |
| Icon | `NETHERITE_SWORD` |
| Max level | 4 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 6 |
| Cost factor | 0.85 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-assassinate.toml` |

Menu stat lines: Executable Health Cap; Cooldown.

Listened events:

- `EntityDamageByEntityEvent` (`HIGHEST`, ignore cancelled) - replaces the damage with an execution

Excludes players, anything implementing `Boss`, and `WARDEN`. Eligibility compares the target's maximum health against the level-scaled cap; the replaced damage is the target's current health at the moment of the hit. Milestones: `challenge_stealth_assassinate_50` on `stealth.assassinate.executions` at 50 (reward 500); `challenge_stealth_assassinate_500` at 500 (reward 2000).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `healthCapBase` | `22.0` | Maximum health a target may have to be executable, before level scaling. |
| `healthCapFactor` | `38.0` | Extra executable health gained at max level. |
| `cooldownBase` | `40000` | Milliseconds between executions, before level scaling. |
| `cooldownFactor` | `20000` | Milliseconds removed from the cooldown at max level. Floors at 8000 ms. |
| `xpOnExecution` | `45` | Skill XP granted per execution. |

### Decoy Swap

| Property | Value |
|----------|-------|
| Class | `StealthDecoySwap` |
| Icon | `ENDER_PEARL` |
| Max level | 3 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.5 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-decoy-swap.toml` |

Menu stat lines: Swap Range; Cooldown.

Listened events:

- `PlayerToggleSneakEvent` (`MONITOR`) - detects the double tap and starts the swap
- `PlayerQuitEvent` (`MONITOR`) - clears double-tap and cooldown state

Requires Shadow Decoy learned and an active decoy. Both teleports run asynchronously; if the second leg fails the decoy is rolled back to where it started and no cooldown or XP is charged. The swap is refused when the decoy is in a different world or outside the swap range. Milestones: `challenge_stealth_decoy_swap_100` on `stealth.decoy-swap.swaps` at 100 (reward 400); `challenge_stealth_decoy_swap_1k` at 1000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `swapRangeBase` | `10.0` | Maximum distance in blocks to your decoy, before level scaling. |
| `swapRangeFactor` | `20.0` | Extra swap range gained at max level. |
| `cooldownBase` | `12000` | Milliseconds between swaps, before level scaling. |
| `cooldownFactor` | `8000` | Milliseconds removed from the cooldown at max level. Floors at 2000 ms. |
| `doubleTapWindowMillis` | `400` | Maximum milliseconds between the two sneak presses to count as a double tap. |
| `xpOnSwap` | `12` | Skill XP granted per successful swap. |

### Umbral Recovery

| Property | Value |
|----------|-------|
| Class | `StealthUmbralRecovery` |
| Icon | `COOKED_BEEF` |
| Max level | 4 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.35 |
| Config file | `plugins/Adapt/adapt/adaptations/stealth-umbral-recovery.toml` |

Menu stat lines: Hunger Refund; Invisibility Extension.

Listened events:

- `EntityDeathEvent` (`MONITOR`) - runs the recovery when the killer was sneaking

Hunger is only refunded if the player is below 20 food, and saturation is raised by the same amount but never above the new food level. Invisibility is only extended if an Invisibility effect is already active and the extension would actually raise its duration. If neither applies, no stat and no XP are granted. Milestones: `challenge_stealth_umbral_200` on `stealth.umbral-recovery.recoveries` at 200 (reward 400); `challenge_stealth_umbral_2k` at 2000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `refundBase` | `2` | Food points restored per sneaking kill, before level scaling. |
| `refundFactor` | `4` | Extra food points restored at max level. Minimum 1. |
| `extensionTicksBase` | `40` | Ticks added to an active Invisibility per sneaking kill, before level scaling. |
| `extensionTicksFactor` | `120` | Extra extension ticks gained at max level. |
| `maxInvisibilityTicks` | `1200` | Ceiling on the Invisibility duration reachable through extension. |
| `xpOnRecovery` | `8` | Skill XP granted per recovery. |

### Support classes (not player adaptations)

- `StealthShadowDecoyPackets` creates, updates, and removes the fake-player packets used by Shadow Decoy, including bounded viewer refreshes.
- `EntityListing` defines the hostile entity types used by legacy Stealth aggression checks.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
