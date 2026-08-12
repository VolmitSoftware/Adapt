# Skill: Seaborne

Seaborne is the water skill. You level it by living in the ocean: swimming with your air bar draining, mining underwater, fishing, throwing tridents, and killing drowned and guardians. It has 14 adaptations and shows up in the menu as a blue `TRIDENT`.

The early adaptations are about staying down there. Organic Oxygen Tank stretches your air, Turtle's Vision lights up the water, Turtle Miner speeds up underwater mining, and Dolphin's Grace makes you actually fast. Once you can survive a dive, Pressure Diver and Brine Skin keep you alive under damage, and Ink Veil gives you an escape when a guardian locks on.

The rest is about doing something useful down there. Deep Salvager marks sunken chests and pays out treasure the first time you crack one open. Coral Gardener lets you keep coral alive out of water and farm it with bone meal. Fish Whisperer herds fish toward you and turns dolphins and axolotls into bodyguards. Trident Mastery makes tridents hit harder and fly home on their own. Two more are pure movement: Tidecaller surges you forward through water or rain, and Hydro Jet burns hunger for a hard burst while you are sprint-swimming.

## Adaptations

Everything below needs the same four things before it does anything: the adaptation learned at level 1 or higher, the skill and the adaptation both enabled in config, the matching `adapt.use.*` permission, and any protection or region plugin allowing the action. Those preconditions are not repeated per adaptation.

### Organic Oxygen Tank (`seaborne-oxygen`)

Raises your maximum air underwater by adding an oxygen bonus attribute. At high levels you can stay under for a very long time before the bar starts moving. It works on its own once learned, no gesture needed.

### Dolphin's Grace (`seaborne-speed`)

Gives you a water movement efficiency bonus that scales with level, so you cut through water faster. When you are actually sprint-swimming, it also applies the real Dolphin's Grace potion effect, which lingers longer as the level climbs.

It refuses to run if your boots have Depth Strider. Take the enchantment off if you want this adaptation to work. This is deliberate, and the menu lore says so.

It works on its own once learned. Just get in the water.

### Fisher's Fantasy (`seaborne-fishers-fantasy`)

Every time you reel in a fish, the adaptation flips one coin per level. Each success drops an extra random fishing item at your feet, spawns a vanilla XP orb, and pays skill XP on top. At level 7 that is seven chances at a bonus drop per catch.

Works on its own once learned. Just fish.

### Turtle's Vision (`seaborne-turtles-vision`)

Gives you Night Vision the whole time you are in water, and takes it away when you surface. It only removes the effect if it was the one that applied it, so a Night Vision potion you drank yourself is left alone.

Passive. Learn it and swim.

### Turtle Miner (`seaborne-turtles-mining-speed`)

Adds a flat 40 percent to your submerged mining speed attribute, which is what makes mining underwater feel less like chewing rock. It stacks on top of Aqua Affinity rather than replacing it.

Passive, single level. The menu lore says the effect waits for your Water Breathing to wear off; the code does not check that, so it is always on.

### Tidecaller (`seaborne-tidecaller`)

A dash. You point where you want to go and surge, leaving a splash trail behind. Default settings put you on a velocity burst rather than a teleport, so walls stop you instead of letting you blink through them.

Out of the box you can trigger it two ways, and it works in water or in the rain. The dash is on a cooldown that shows on the Heart of the Sea item cooldown, and it shortens as you level.

1. Learn Tidecaller in the Adapt menu.
2. Get into water, or stand out in the open during a storm.
3. Look where you want to go.
4. Tap sneak, or swing your arm (left click) while in water.
5. Wait out the cooldown before the next one. A fizzle sound means a wall is in front of you, or the cooldown is still running.

Operators can turn either trigger off, require sneak for the swing trigger, restrict triggers to water only, or switch back to the old teleport dash.

### Pressure Diver (`seaborne-pressure-diver`)

Rewards going deep. Once your eyes are far enough below sea level, you get Resistance and Water Breathing on a rolling refresh, and incoming damage is scaled down on top of that. Go deeper still and the Resistance steps up a tier. The required depth shrinks as you level, so higher levels get the buff nearer the surface.

It also fights mining fatigue. While the buff is up it adds a submerged mining speed modifier sized to partly cancel whatever Mining Fatigue amplifier you are carrying, which is what makes elder guardian territory workable.

Passive. It arms itself when you dive and clears when you surface.

### Coral Gardener (`seaborne-coral-gardener`)

Coral you place stops dying the moment it leaves water. The adaptation remembers the blocks you placed and cancels their fade for a set time, minutes at low level and much longer at high level. Placing coral or other reef blocks (prismarine, sea lanterns, sponge) also pays skill XP.

The second half is bone meal farming. Right-click live coral with bone meal and it may grow a new random coral block into an adjacent water cell.

1. Learn Coral Gardener.
2. Place coral or reef blocks anywhere. XP is paid on placement.
3. Hold bone meal in your main hand.
4. Right-click a coral block that has water next to it.
5. On a success a new coral block appears in that water cell and one bone meal is consumed.

Growth is authorized like a normal block place, so a region plugin that blocks you gets the last word. If placement is denied, no bone meal is spent; if the bone meal charge fails, the water cell is put back. On Folia the growth only happens in cells owned by the same region as the player.

### Deep Salvager (`seaborne-deep-salvager`)

While you are in water, the adaptation quietly scans nearby blocks and paints chests, trapped chests, and barrels with an aqua glow that only you can see. Up to six show at a time, within a capped radius, and only within three blocks of your own height.

Opening one of those containers while you are in water and the container is touching water pays out bonus treasure rolled from an ocean loot table (nautilus shells, prismarine, ingots, lapis, emeralds, ink sacs, tropical fish, heart of the sea). Each container pays once, ever, and the container is stamped so nobody double-dips.

1. Learn Deep Salvager.
2. Swim into a shipwreck, ruin, or any flooded structure.
3. Look for containers glowing aqua.
4. Open one while you are still in the water. The bonus items appear in the container.

### Ink Veil (`seaborne-ink-veil`)

When you take damage while in water, you burst an ink cloud. You get Invisibility, every hostile in the cloud gets Blindness, and drowned, guardians, and elder guardians that were targeting you drop their target. It fires on its own, so it works as a panic button you do not have to press. The cooldown shortens as you level, down to a floor of three seconds.

Passive. Learn it and take a hit underwater.

### Trident Mastery (`seaborne-trident-mastery`)

Tridents hit harder, both thrown and swung in melee. When you throw one, the trident is stamped with your level at launch, so it keeps the bonus even if you switch gear mid-flight.

It also brings the trident back. After a short flight grace the trident turns around and homes to you at a velocity that scales with level, and a trident stuck in a block frees itself and comes home too. Higher levels start the return sooner. Operators can turn the return off and keep only the damage.

Passive. Throw or swing a trident.

### Fish Whisperer (`seaborne-fish-whisperer`)

Three things at once. You carry a permanent Luck bonus equal to your level, which means better fishing results without an enchantment. While you are in water, nearby fish get nudged toward you, which makes them easy to bucket or spear. And when you hit a mob, nearby dolphins charge it and nearby axolotls retarget onto it.

Passive.

### Hydro Jet (`seaborne-hydro-jet`)

A charge-based burst for swimmers. Tap sneak while sprint-swimming and you launch in the direction you are looking. Charges refill over time, and each jet costs exhaustion, so it drains hunger if you spam it. Out of charges or out of food gives you a fizzle instead of a burst.

1. Learn Hydro Jet.
2. Sprint-swim so you are in the swimming pose, not just floating in water.
3. Look where you want to go.
4. Tap sneak.
5. Repeat until your charges run out, then wait for them to refill.

### Brine Skin (`seaborne-brine-skin`)

While you are wet you get Regeneration and take reduced damage. Wet means in water or swimming, or out in the open during a storm. Both effects hang around for a few seconds after you leave the water, which covers the moment you climb out of a fight.

The Regeneration tier climbs with level to a maximum of Regeneration III. Damage reduction is capped so it stays modest.

Passive.

## Reference

Everything below is exact code truth. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`. Every adaptation TOML also carries the shared keys `enabled`, `permanent`, `showParticles`, and `showSounds`, which are not repeated per adaptation.

### Identity

| Property | Value |
|----------|-------|
| Skill id | `seaborne` |
| Class | `SkillSeaborne` |
| Icon | `TRIDENT` |
| Color | `BLUE` |
| Interval (ms) | `2120` |
| Skill config | `plugins/Adapt/adapt/skills/seaborne.toml` |
| Adaptation count | 14 |

### Skill XP sources

| Trigger | Award | Notes |
|---------|-------|-------|
| Passive swim pulse (every `2120` ms) | `swimXP` scaled by elapsed time over the interval | With Water Breathing or Conduit Power you must have moved in water within the last `2500` ms and the award is multiplied by `1 + waterBreathingSwimXpBonusMultiplier`. Without either effect you must be in water or swimming with air below maximum. |
| `CAUGHT_FISH` | `fishCaughtXp` | Rate-limited by `fishXpCooldown`. The `seaborne.fish.caught` stat is added before the rate limit, so the stat always counts. |
| `CAUGHT_ENTITY` | `entityCaughtXp` | Rate-limited by `fishXpCooldown`. |
| Breaking a block while in water or swimming | `10` for a sea pickle broken while swimming with air below maximum, otherwise `3` | Rate-limited by `seaPickleCooldown`, which gates all underwater block XP, not only sea pickles. |
| Damaging a drowned | `damagedrownxpmultiplier` times the damage dealt, capped at the victim's base max health | Rate-limited by `drownedDamageXpCooldown`. |
| Damaging with a trident, thrown or held in the main hand | `tridentxpmultiplier` times the damage dealt, capped at the victim's base max health | Rate-limited by `tridentDamageXpCooldown`. |
| Killing a drowned, guardian, or elder guardian | No XP | Adds the kill stat and plays effects only. |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/seaborne.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `seaPickleCooldown` | `60000` | Milliseconds between underwater block-break XP awards and `seaborne.underwater.blocks` stat increments, per player. |
| `drownedDamageXpCooldown` | `1500` | Milliseconds between XP awards for damaging drowned. |
| `tridentDamageXpCooldown` | `1500` | Milliseconds between XP awards for trident damage. |
| `fishCaughtXp` | `250` | XP for reeling in a fish. |
| `entityCaughtXp` | `10` | XP for reeling in an entity instead of a fish. |
| `fishXpCooldown` | `5000` | Milliseconds between fishing XP awards. |
| `tridentxpmultiplier` | `4.0` | Multiplier applied to trident damage when converting it to XP. |
| `damagedrownxpmultiplier` | `3` | Multiplier applied to damage dealt to drowned when converting it to XP. |
| `enabled` | `true` | Set to false to disable the whole skill. |
| `skillColor` | `"&9"` | Legacy ampersand color code used for this skill in menus and text. |
| `challengeSwim1nmReward` | `750` | XP paid for `challenge_swim_1nm`, and also for the first tier of the fish, drowned, guardian, and underwater-block challenges. Their second tiers pay double this value. |
| `challengeSwim5kReward` | `1500` | XP paid for `challenge_swim_5k`. |
| `challengeSwim20kReward` | `3750` | XP paid for `challenge_swim_20k`. |
| `swimXP` | `0.4` | Base passive swim XP per interval, before cadence scaling and the Water Breathing bonus. |
| `waterBreathingSwimXpBonusMultiplier` | `1.0` | Extra passive swim XP multiplier while moving in water with Water Breathing or Conduit Power. `1.0` doubles the award, `0` disables the bonus. |

### Skill milestones

| Challenge key | Stat key | Threshold | Reward |
|---------------|----------|-----------|--------|
| `challenge_swim_1nm` | `move.swim` | 1852 | `challengeSwim1nmReward` |
| `challenge_swim_5k` | `move.swim` | 5000 | `challengeSwim5kReward` |
| `challenge_swim_20k` | `move.swim` | 20000 | `challengeSwim20kReward` |
| `challenge_fish_25` | `seaborne.fish.caught` | 25 | `challengeSwim1nmReward` |
| `challenge_fish_250` | `seaborne.fish.caught` | 250 | `challengeSwim1nmReward` x2 |
| `challenge_drowned_25` | `seaborne.drowned.kills` | 25 | `challengeSwim1nmReward` |
| `challenge_drowned_250` | `seaborne.drowned.kills` | 250 | `challengeSwim1nmReward` x2 |
| `challenge_guardian_10` | `seaborne.guardian.kills` | 10 | `challengeSwim1nmReward` |
| `challenge_guardian_100` | `seaborne.guardian.kills` | 100 | `challengeSwim1nmReward` x2 |
| `challenge_underwater_blocks_100` | `seaborne.underwater.blocks` | 100 | `challengeSwim1nmReward` |
| `challenge_underwater_blocks_1k` | `seaborne.underwater.blocks` | 1000 | `challengeSwim1nmReward` x2 |

`move.swim` is written by the Agility movement tracker, not by Seaborne. The other stat keys are written by `SkillSeaborne` itself.

### Organic Oxygen Tank

| Property | Value |
|----------|-------|
| Class | `SeaborneOxygen` |
| Icon | `GLASS_PANE` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 3 |
| Cost factor | 0.525 |
| Tick interval (ms) | 3750 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-oxygen.toml` |

Menu stat line: Oxygen Capacity Increase.

Applies the `OXYGEN_BONUS` attribute with a `160` tick timed modifier under the `oxygen` slot. The bonus is derived from the saved-air fraction `level * airPerLevelTics / 75`, clamped to 1; a fraction of 1 grants the hard maximum bonus of `1024`.

Milestone: `challenge_seaborne_oxygen_12k` on `seaborne.oxygen.bonus-air-ticks` at 12000, reward 300.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `airPerLevelTics` | `15` | Air ticks saved per level out of a 75 tick drowning pulse. The resulting fraction becomes the oxygen bonus attribute. |

### Dolphin's Grace

| Property | Value |
|----------|-------|
| Class | `SeaborneSpeed` |
| Icon | `PRISMARINE_CRYSTALS` |
| Max level | 7 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 3 |
| Cost factor | 0.525 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-speed.toml` |

Listened events:

- `PlayerMoveEvent` (`MONITOR`, ignore cancelled) - starts, refreshes, and ends the swim session
- `PlayerTeleportEvent` (`MONITOR`, ignore cancelled) - ends the swim session
- `PlayerQuitEvent` (`LOWEST`) - ends the swim session

Applies `WATER_MOVEMENT_EFFICIENCY` under the `swim` slot at `level / maxLevel`, capped at 1. Applies `DOLPHINS_GRACE` at amplifier 0 for `20 + round(levelPercent * 60)` ticks while sprint-swimming. Eligibility is re-checked at most every `500` ms, and resolves to level 0 (fully inactive) when the player's boots carry Depth Strider. Swim distance is tracked in `4` block chunks and flushed to the stat every `4` blocks.

Milestones: `challenge_seaborne_speed_10k` on `seaborne.speed.blocks-swum` at 10000 (reward 300); `challenge_seaborne_speed_100k` at 100000 (reward 1500).

No adaptation-specific config knobs.

### Fisher's Fantasy

| Property | Value |
|----------|-------|
| Class | `SeaborneFishersFantasy` |
| Icon | `FISHING_ROD` |
| Max level | 7 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 5 |
| Cost factor | 0.9 |
| Tick interval (ms) | 8080 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-fishers-fantasy.toml` |

Menu stat line: For each level there is a chance to get more XP and Fish.

Listened events:

- `PlayerFishEvent` - on `CAUGHT_FISH` only

On a catch, rolls one 50 percent coin flip per level. Each success drops one random fishing drop at the player's location, spawns an experience orb worth `level * 2`, and adds skill XP of `15` per success at the end.

Milestones: `challenge_seaborne_fish_500` on `seaborne.fishers-fantasy.fish-caught` at 500 (reward 300); `challenge_seaborne_fish_5k` at 5000 (reward 1000).

No adaptation-specific config knobs.

### Turtle's Vision

| Property | Value |
|----------|-------|
| Class | `SeaborneTurtlesVision` |
| Icon | `DIAMOND_HORSE_ARMOR` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 5 |
| Cost factor | 1 |
| Tick interval (ms) | 3000 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-turtles-vision.toml` |

Menu stat line: Gain Night Vision while underwater after your water breathing effect wears off. No Water Breathing check exists in the code, so the menu line does not match runtime behavior.

Listened events:

- `EntityPotionEffectEvent` (`MONITOR`, ignore cancelled) - drops ownership when something else changes the player's Night Vision
- `PlayerQuitEvent` (`MONITOR`) - clears managed Night Vision and state

Applies `NIGHT_VISION` at amplifier 0 for `600` ticks, refreshed once the remaining duration drops to `500` ticks or below. Only a non-ambient, particle-free amplifier 0 effect is treated as adaptation-owned and eligible for removal. Underwater time is credited to the stat at up to `160` ticks per sample.

Milestone: `challenge_seaborne_vision_72k` on `seaborne.turtles-vision.time-underwater` at 72000, reward 400.

No adaptation-specific config knobs.

### Turtle Miner

| Property | Value |
|----------|-------|
| Class | `SeaborneTurtlesMiningSpeed` |
| Icon | `PRISMARINE_SHARD` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 15 |
| Cost factor | 1 |
| Tick interval (ms) | 3000 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-turtles-mining-speed.toml` |

Menu stat line: Haste 3 is applied underwater while mining (stacks with AquaAffinity) after your water breathing effect wears off.

Listened events:

- `BlockBreakEvent` (`MONITOR`, ignore cancelled) - counts blocks broken while in water and plays effects

Applies `SUBMERGED_MINING_SPEED` under the `mining` slot as a `MULTIPLY_SCALAR_1` modifier of `0.4` for `160` ticks, refreshed on every tick pulse. The modifier is applied regardless of whether the player is currently in water, and no Haste potion effect and no Water Breathing check exist in the code, so the menu stat line above does not match runtime behavior.

Milestones: `challenge_seaborne_mining_2500` on `seaborne.turtles-mining.blocks-underwater` at 2500 (reward 300); `challenge_seaborne_mining_25k` at 25000 (reward 1000).

No adaptation-specific config knobs.

### Tidecaller

| Property | Value |
|----------|-------|
| Class | `SeaborneTidecaller` |
| Icon | `HEART_OF_THE_SEA` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 1600 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-tidecaller.toml` |

Menu stat lines: Surge Distance; Surge Cooldown; plus a generated Trigger line per enabled trigger and an Environment line.

Listened events:

- `PlayerToggleSneakEvent` (`HIGHEST`, ignore cancelled) - sneak trigger
- `PlayerAnimationEvent` (`HIGHEST`, ignore cancelled) - arm swing trigger
- `PlayerQuitEvent` (`MONITOR`) - clears pending teleport dash state

The cooldown is stored as a vanilla item cooldown on `HEART_OF_THE_SEA` and floors at `20` ticks. A ready ping plays when it expires. Water state counts as in water, swimming, or standing with feet or eyes in liquid. Rain state requires a storm and a position at or above the highest block minus one.

Milestones: `challenge_seaborne_tidecaller_200` on `seaborne.tidecaller.dashes` at 200 (reward 300); `challenge_seaborne_tidecaller_5k` at 5000 (reward 1000).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `dashDistanceBase` | `6` | Dash distance in blocks before level scaling. |
| `dashDistanceFactor` | `8` | Extra dash blocks added at max level. |
| `cooldownTicksBase` | `140` | Cooldown in server ticks before level scaling (20 ticks = 1 second). |
| `cooldownTicksFactor` | `80` | Cooldown ticks removed at max level. The result floors at 20 ticks. |
| `xpPerBurst` | `11` | Skill XP granted per successful dash. |
| `allowRainTrigger` | `true` | Allows dashing while exposed to a storm. |
| `allowWaterTrigger` | `true` | Allows dashing while in a water state. |
| `enableSneakTrigger` | `true` | Enables the sneak trigger. |
| `enableAttackTrigger` | `true` | Enables the arm swing trigger, with any item or empty hand. |
| `attackTriggerRequiresSneak` | `false` | When true the arm swing trigger only fires while sneaking. |
| `attackTriggerWaterOnly` | `true` | When true the arm swing trigger only fires in a water state, even if rain triggers are allowed. |
| `useVelocityDash` | `true` | True applies a velocity burst. False teleports to the farthest safe point along the look vector. |
| `flattenVelocityDashDirection` | `false` | True zeroes the pitch so the dash stays horizontal. |
| `velocityStrengthBase` | `1.05` | Forward velocity magnitude before level scaling. |
| `velocityStrengthFactor` | `0.85` | Extra forward velocity magnitude at max level. |
| `velocityVerticalBase` | `0.01` | Vertical velocity added before level scaling. |
| `velocityVerticalFactor` | `0.05` | Extra vertical velocity added at max level. |
| `velocityAdditive` | `true` | True adds the dash on top of current velocity. False sets a fresh vector. |
| `maxResultingVelocity` | `2.25` | Hard cap on the resulting velocity magnitude. |
| `blockDashWhenWallAhead` | `true` | Cancels the dash and fizzles when a ray trace finds a solid block ahead. |
| `wallCheckDistance` | `1.2` | Ray trace distance in blocks used for the wall check. |
| `applyForwardMomentumAfterDash` | `true` | Applies momentum after a teleport dash. Ignored when `useVelocityDash` is true. |
| `forwardMomentum` | `1.05` | Horizontal velocity magnitude applied after a teleport dash. |
| `verticalMomentum` | `0.02` | Vertical velocity added, or set, after a teleport dash. |
| `replaceVerticalMomentum` | `false` | True replaces current vertical velocity with `verticalMomentum` instead of adding it. |
| `preserveSwimmingAfterDash` | `true` | Re-applies the swimming pose after a dash when the player was swimming and is still in water. |

### Pressure Diver

| Property | Value |
|----------|-------|
| Class | `SeabornePressureDiver` |
| Icon | `NAUTILUS_SHELL` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 20 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-pressure-diver.toml` |

Menu stat lines: Minimum Depth Requirement; Depth Damage Reduction; Mining Fatigue Reduction Chance.

Listened events:

- `EntityDamageEvent` (`HIGHEST`, ignore cancelled) - scales incoming damage down while deep enough
- `PlayerMoveEvent` (`MONITOR`, ignore cancelled, `@RunsWithoutLearnedAdaptation`) - arms and disarms depth tracking
- `BlockBreakEvent` (`MONITOR`, ignore cancelled) - counts blocks mined while the deep state is active
- `PlayerQuitEvent` - clears depth state

Depth is measured as world sea level minus eye Y. Meeting the depth threshold applies `RESISTANCE` at amplifier 0 and `WATER_BREATHING` at amplifier 0 for `effectTicks`; passing the deep threshold raises Resistance to amplifier 1. The depth threshold floors at 2 blocks and the deep threshold at 4 blocks. Refresh cadence is clamped to 250 ms to 750 ms, with a 250 ms entry check while shallow, and refresh work is batched at 128 players per tick.

Milestone: `challenge_seaborne_pressure_1k` on `seaborne.pressure-diver.deep-blocks-mined` at 1000, reward 400.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `depthThresholdBase` | `10` | Blocks below sea level required for the buff, before level scaling. |
| `depthThresholdFactor` | `6` | Blocks removed from the depth requirement at max level. Floors at 2. |
| `deepThresholdBase` | `18` | Blocks below sea level required for the stronger Resistance tier, before level scaling. |
| `deepThresholdFactor` | `8` | Blocks removed from the deep tier requirement at max level. Floors at 4. |
| `damageReductionBase` | `0.12` | Damage reduction while deep, as a fraction 0-1, before level scaling. |
| `damageReductionFactor` | `0.26` | Extra damage reduction fraction gained at max level. |
| `maxDamageReduction` | `0.45` | Hard cap on the damage reduction fraction, 0-1. |
| `fatigueTrimChanceBase` | `0.2` | Chance term, 0-1, feeding the fatigue cancellation math, before level scaling. |
| `fatigueTrimChanceFactor` | `0.45` | Extra chance term gained at max level. Total is clamped to 1. |
| `fatigueTrimAmountBase` | `1` | Mining Fatigue amplifier levels cancelled per second, before level scaling. |
| `fatigueTrimAmountFactor` | `1` | Extra amplifier levels cancelled at max level. |
| `effectTicks` | `60` | Duration in ticks of each Resistance and Water Breathing refresh, and the basis for the refresh cadence. |
| `fatigueReplaceTicks` | `80` | Maximum ticks the mining speed counter-modifier lasts, clamped to the remaining Mining Fatigue duration and a floor of 20. |
| `xpPerDepthPulse` | `6` | Skill XP granted per depth pulse. |
| `xpPulseCooldownMillis` | `3000` | Milliseconds between depth XP pulses. |

### Coral Gardener

| Property | Value |
|----------|-------|
| Class | `SeaborneCoralGardener` |
| Icon | `BRAIN_CORAL_BLOCK` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.55 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-coral-gardener.toml` |

Menu stat lines: Coral Survival Time; Bonemeal Growth Chance.

Listened events:

- `BlockPlaceEvent` (`MONITOR`, ignore cancelled) - pays reef XP and starts the fade timer for coral
- `BlockFadeEvent` (`MONITOR`, ignore cancelled) - cancels the fade while the timer is still running
- `PlayerInteractEvent` (`MONITOR`, ignore cancelled) - bone meal growth on right-click

Reef blocks are anything tagged `CORAL_BLOCKS`, `CORALS`, or `WALL_CORALS`, plus `PRISMARINE`, `PRISMARINE_BRICKS`, `DARK_PRISMARINE`, `SEA_LANTERN`, `SPONGE`, and `WET_SPONGE`. Only tagged coral gets fade protection and counts toward the stat. Growth picks a random block from `TUBE_CORAL_BLOCK`, `BRAIN_CORAL_BLOCK`, `BUBBLE_CORAL_BLOCK`, `FIRE_CORAL_BLOCK`, and `HORN_CORAL_BLOCK` and searches the six adjacent faces for a water cell. Growth passes the normal block place authorization and a protection event probe before changing the cell. A denied placement consumes no bone meal, and a failed bone meal charge restores the water cell. Creative mode skips the bone meal cost. On Folia both player and target must be owned by the current region. At most `8192` protected coral blocks are tracked, with expired entries purged first.

Milestones: `challenge_seaborne_coral_500` on `seaborne.coral-gardener.coral-placed` at 500 (reward 300); `challenge_seaborne_coral_5k` at 5000 (reward 1000).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `survivalSecondsBase` | `60` | Seconds placed coral is protected from fading, before level scaling. |
| `survivalSecondsFactor` | `240` | Extra protected seconds gained at max level. |
| `growthChanceBase` | `0.35` | Chance per bone meal click that growth is attempted, 0-1, before level scaling. |
| `growthChanceFactor` | `0.5` | Extra growth chance gained at max level. Total is clamped to 1. |
| `reefPlaceXp` | `8` | Skill XP granted per reef block placed. |
| `growthXp` | `14` | Skill XP granted per coral block grown with bone meal. |

### Deep Salvager

| Property | Value |
|----------|-------|
| Class | `SeaborneDeepSalvager` |
| Icon | `CHEST` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Tick interval (ms) | 3000 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-deep-salvager.toml` |

Menu stat lines: Detection Range (blocks); Bonus Treasure Rolls.

Listened events:

- `InventoryOpenEvent` (`MONITOR`) - bonus treasure roll
- `PlayerQuitEvent` (`MONITOR`) - cancels scans and clears the viewer's block displays

Shimmer scans run only while the player is in water. The effective radius is capped at `9` blocks, vertical reach at 3 blocks either side of the player, samples at `3200` blocks per scan, and results at `6` shimmering containers. Shimmers are private per-viewer block displays tinted RGB `70, 230, 235`, lasting between 20 and 200 ticks based on the scan cooldown. Container types are `CHEST`, `TRAPPED_CHEST`, and `BARREL`.

Salvage requires the player to be in water and the container to have water on at least one of its six faces. The container is stamped with the `seaborne_salvaged` persistent key so it pays out once. Treasure is rolled from `NAUTILUS_SHELL` (x2 weight), `PRISMARINE_SHARD` (x3), `PRISMARINE_CRYSTALS` (x2), `GOLD_INGOT`, `IRON_INGOT`, `LAPIS_LAZULI`, `EMERALD`, `INK_SAC`, `GLOW_INK_SAC`, `TROPICAL_FISH`, and `HEART_OF_THE_SEA`, in stacks of 1 to 3 except Heart of the Sea which is always 1. XP is `salvageXp` per item that actually fit in the container.

Milestones: `challenge_seaborne_salvage_100` on `seaborne.deep-salvager.containers-salvaged` at 100 (reward 400); `challenge_seaborne_salvage_1k` at 1000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `detectionRangeBase` | `4` | Shimmer scan radius in blocks, before level scaling. |
| `detectionRangeFactor` | `5` | Extra scan radius gained at max level. The result is capped at 9 blocks. |
| `bonusRollsBase` | `1` | Bonus treasure rolls per container, before level scaling. |
| `bonusRollsFactor` | `3` | Extra treasure rolls gained at max level. Minimum is 1 roll. |
| `salvageXp` | `12` | Skill XP granted per bonus item that fit in the container. |
| `shimmerScanCooldownMillis` | `3000` | Milliseconds between shimmer scans per player, and the basis for how long a shimmer stays visible. |
| `enableShimmer` | `true` | Set to false to disable shimmer scanning while keeping the bonus loot. |

### Ink Veil

| Property | Value |
|----------|-------|
| Class | `SeaborneInkVeil` |
| Icon | `INK_SAC` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.55 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-ink-veil.toml` |

Menu stat lines: Ink Cloud Size (blocks); Ink Burst Cooldown.

Listened events:

- `EntityDamageEvent` (`MONITOR`, ignore cancelled) - fires the burst when the damaged player is in water

Applies `INVISIBILITY` to the player and `BLINDNESS` to up to `24` nearby `Monster` entities. Drowned, guardians, and elder guardians targeting the player have their target cleared. The burst does not cancel or reduce the incoming damage.

Milestones: `challenge_seaborne_ink_100` on `seaborne.ink-veil.clouds-burst` at 100 (reward 300); `challenge_seaborne_ink_1k` at 1000 (reward 1000).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cloudSizeBase` | `4` | Cloud radius in blocks, before level scaling. |
| `cloudSizeFactor` | `4` | Extra cloud radius gained at max level. |
| `cooldownMillisBase` | `12000` | Milliseconds between bursts, before level scaling. |
| `cooldownMillisReduction` | `8000` | Milliseconds removed from the cooldown at max level. The result floors at 3000 ms. |
| `invisTicksBase` | `40` | Invisibility duration in ticks, before level scaling. |
| `invisTicksFactor` | `40` | Extra invisibility ticks gained at max level. |
| `blindTicksBase` | `60` | Blindness duration in ticks applied to hostiles, before level scaling. |
| `blindTicksFactor` | `60` | Extra blindness ticks gained at max level. |
| `burstXp` | `10` | Skill XP granted per burst. |

### Trident Mastery

| Property | Value |
|----------|-------|
| Class | `SeaborneTridentMastery` |
| Icon | `TRIDENT` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-trident-mastery.toml` |

Menu stat lines: Bonus Trident Damage; Recall Speed.

Listened events:

- `EntityDamageByEntityEvent` (`HIGHEST`, ignore cancelled) - applies the damage bonus
- `ProjectileLaunchEvent` (`MONITOR`, ignore cancelled) - stamps the trident and starts recall

Thrown tridents carry the thrower's level in the `seaborne_trident_mastery_level` persistent key, and the damage bonus uses that stamped level. Melee hits use the wielder's current level and require a `TRIDENT` in the main hand. Recall re-evaluates every tick up to `120` ticks of tracked life, stops once the trident is within `1.6` blocks of the player, and teleports a stuck trident free before homing it.

Milestones: `challenge_seaborne_trident_250` on `seaborne.trident-mastery.trident-hits` at 250 (reward 400); `challenge_seaborne_trident_2500` at 2500 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageBonusBase` | `0.15` | Bonus trident damage as a fraction of base damage, before level scaling. |
| `damageBonusFactor` | `0.45` | Extra damage fraction gained at max level. |
| `recallSpeedBase` | `0.8` | Velocity magnitude applied to a homing trident, before level scaling. |
| `recallSpeedFactor` | `1.2` | Extra homing velocity gained at max level. |
| `flightGraceTicksBase` | `50` | Ticks a trident flies freely before recall takes over, before level scaling. |
| `flightGraceTicksReduction` | `30` | Grace ticks removed at max level. The result floors at 10 ticks. |
| `recallDelayTicks` | `5` | Ticks after launch before the first recall evaluation. Minimum 2. |
| `enableRecall` | `true` | Set to false to keep the damage bonus without the homing return. |

### Fish Whisperer

| Property | Value |
|----------|-------|
| Class | `SeaborneFishWhisperer` |
| Icon | `TROPICAL_FISH` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Tick interval (ms) | 4000 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-fish-whisperer.toml` |

Menu stat lines: Luck of the Sea Tier; Creature Affinity Range (blocks).

Listened events:

- `EntityDamageByEntityEvent` (`MONITOR`, ignore cancelled) - recruits dolphins and axolotls onto the victim

Applies the `LUCK` attribute under the `luck` slot at `level`, refreshed on every tick pulse for `400` ticks. Fish schooling only runs while the player is in water or swimming, nudges at most `12` fish per pulse, ignores fish already within 1 block, and blends the fish's current velocity at half weight with the pull vector. The charmed stat counts a fish once per `12000` ms session. Assist recruitment affects at most `8` mobs per hit; dolphins get a velocity charge toward the victim, axolotls get retargeted.

Milestones: `challenge_seaborne_charm_2k` on `seaborne.fish-whisperer.charmed` at 2000 (reward 300); `challenge_seaborne_charm_20k` at 20000 (reward 1000).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `schoolRangeBase` | `6` | Radius in blocks that fish are pulled from, before level scaling. |
| `schoolRangeFactor` | `8` | Extra schooling radius gained at max level. |
| `schoolPullBase` | `0.12` | Velocity magnitude of the pull applied to each fish, before level scaling. |
| `schoolPullFactor` | `0.18` | Extra pull magnitude gained at max level. |
| `assistRangeBase` | `8` | Radius in blocks around the victim searched for dolphins and axolotls, before level scaling. |
| `assistRangeFactor` | `8` | Extra assist radius gained at max level. |
| `dolphinChargeStrength` | `0.9` | Velocity magnitude dolphins use to charge the victim. 0 disables the charge motion. |

### Hydro Jet

| Property | Value |
|----------|-------|
| Class | `SeaborneHydroJet` |
| Icon | `PRISMARINE_CRYSTALS` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-hydro-jet.toml` |

Menu stat lines: Burst Force; Jet Charges.

Listened events:

- `PlayerToggleSneakEvent` (`HIGHEST`, ignore cancelled) - fires the jet when the player starts sneaking while swimming
- `PlayerQuitEvent` - clears stored charges

Requires `isSwimming()`, so floating in water is not enough. A food level of 0 fizzles without spending a charge. The jet blends 40 percent of current velocity with the burst vector and caps the result at a magnitude of `2.6`. Charges refill continuously from the last use, in fractions of a charge.

Milestones: `challenge_seaborne_hydro_200` on `seaborne.hydro-jet.jets` at 200 (reward 300); `challenge_seaborne_hydro_5k` at 5000 (reward 1000).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `burstForceBase` | `0.9` | Velocity magnitude of the burst, before level scaling. |
| `burstForceFactor` | `0.9` | Extra burst magnitude gained at max level. |
| `maxChargesBase` | `2` | Stored charges, before level scaling. |
| `maxChargesFactor` | `3` | Extra stored charges gained at max level. Minimum is 1. |
| `chargeRegenMillis` | `2500` | Milliseconds to regenerate one charge. 0 or less refills instantly. |
| `hungerCost` | `2.0` | Exhaustion added per jet. |
| `jetXp` | `6` | Skill XP granted per jet. |

### Brine Skin

| Property | Value |
|----------|-------|
| Class | `SeaborneBrineSkin` |
| Icon | `KELP` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.55 |
| Tick interval (ms) | 2000 |
| Config file | `plugins/Adapt/adapt/adaptations/seaborne-brine-skin.toml` |

Menu stat lines: Brine Regeneration Tier; Damage Reduction While Wet.

Listened events:

- `EntityDamageEvent` (`HIGHEST`, ignore cancelled) - applies the damage reduction

Wet means in water, swimming, or exposed to a storm at or above the highest block minus one. Regeneration is applied for `100` ticks at amplifier `floor(levelPercent * 3)` clamped to a maximum of 2, and is refreshed once the remaining duration drops to `40` ticks or below. Damage reduction also applies during the linger window after leaving water. Wet time is credited to the stat at `interval / 50` ticks per pulse.

Milestone: `challenge_seaborne_brine_72k` on `seaborne.brine-skin.wet-ticks` at 72000, reward 400.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageReductionBase` | `0.06` | Damage reduction while wet as a fraction 0-1, before level scaling. |
| `damageReductionFactor` | `0.14` | Extra damage reduction fraction gained at max level. |
| `maxDamageReduction` | `0.25` | Hard cap on the damage reduction fraction, 0-1. |
| `lingerSecondsBase` | `3` | Seconds the wet state persists after leaving water, before level scaling. |
| `lingerSecondsFactor` | `4` | Extra linger seconds gained at max level. |

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
