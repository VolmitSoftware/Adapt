# Skill: Rift

Rift is the ender skill. Its id is `rift`, it uses an ender eye icon, and it carries 13 adaptations. You level it by teleporting, throwing ender pearls and eyes, and fighting End creatures, so it grows naturally from the way most players already use pearls.

What you get back is mobility and storage. Rift Blink turns a double jump into a short teleport with no pearl cost. Rift Gate is a crafted recall stone: bind a spot, then channel back to it later. Pearl Rebound makes a thrown pearl bounce once so you can bank it around a corner. Void Skin catches a killing blow and drops you somewhere safe at the price of one pearl.

The storage side is quietly the strongest part of the skill. Easy Enderchest opens your ender chest from your hand. Inflated Pocket Dimension treats that chest as a bottomless building supply. Void Magnet sucks item drops into it while you sneak. Remote Access binds a portkey to any container so you can open it from across the world, and Rift Conduit links two containers so items flow from one to the other.

The rest is control and safety. Ender Taglock lets you pearl someone else instead of yourself. Rift Visage stops endermen from aggroing while you carry pearls. Anti-Levitation cancels a shulker hit and saves you the fall. Rift Resistance hands you Resistance every time you use an ender item.

## Earning XP

Every teleport counts toward the `rift.teleports` stat, and grants XP on a long cooldown so repeat pearling does not farm the skill. Throwing an ender pearl or an eye of ender pays out immediately with no cooldown, and pearls are the biggest single source in the skill.

Damaging endermen, endermites, and the ender dragon pays XP scaled by the damage dealt, capped at the target's base health so one huge hit cannot overpay. Destroying an end crystal pays a large flat amount. Killing endermen and damaging the dragon feed their own challenge chains.

## Adaptations

Everything below only runs when you have learned the adaptation (level 1 or higher), the skill and the adaptation are both enabled, you are not in a blacklisted world or a blocked game mode, you hold the `adapt.use.<adaptation>` permission, and the protection plugins and region policy allow the action. See `08 - Protection & Region Policy.md` and `04 - Commands & Permissions.md`. Learn and level everything from the Adapt menu (`/adapt`).

Anti-Levitation, Rift Visage, and Inflated Pocket Dimension are marked permanent: the menu asks for a confirmation click before you learn them, and after that they cannot be unlearned or refunded.

### Rift Resistance (`rift-resist`)

Using an ender item gives you a short burst of Resistance, which covers the moment right after a pearl lands when you are usually most exposed.

1. Learn it and hold an ender pearl or an eye of ender in your main hand.
2. Right-click air. You get Resistance II for four seconds and a little XP.
3. Wait out the short activation throttle before it can trigger again.

Easy Enderchest also grants a brief, stronger Resistance pulse when you open your chest from hand, if you have learned this adaptation too.

### Remote Access (`rift-access`)

Remote Access gives you a crafted portkey bound to one container. After that you can open that container from anywhere, so a base chest is always one right-click away.

1. Learn it, then craft an ender pearl with a compass to get a Reliquary Portkey.
2. Sneak-left-click the container you want to bind. Left-clicking air binds the container you are looking at within 5 blocks, except on Folia.
3. Right-click the portkey anywhere to open that container remotely.

Binding and every remote open run the full container permission checks, including both halves of a double chest, so it never opens something you could not open by hand. On Folia the player and every physical container block must be in the same region, so a remote open fails when the target is elsewhere.

### Easy Enderchest (`rift-enderchest`)

Hold an ender chest and click to open it without placing the block. That is the whole feature, and it saves a placement and a pickup every single time.

1. Learn it and hold an ender chest in your main hand.
2. Right-click air, left-click air, or left-click a block.
3. Your ender chest opens. The item goes on a five second cooldown afterward.

### Rift Gate (`rift-gate`)

Rift Gate is a recall stone. Bind a location to a crafted eye, then use it later to channel back there. The channel is slow and blinds you on purpose: you float in place, visible and vulnerable, and if something kills you during it you die normally.

1. Learn it and craft an emerald, an amethyst shard, and an ender pearl into a recall gate eye.
2. Sneak-left-click a block to bind your current location to the eye.
3. Right-click the eye to start the channel. After a bit over four seconds you teleport.
4. Sneak-left-click air with a bound eye to unbind it.

By default the eye is consumed on use, so each gate is a one-shot ticket. Turn that off and the eye survives, with a cooldown between uses instead.

### Rift Blink (`rift-blink`)

Blink is a free short-range teleport on a double jump. Aim at the ground to land there, at a ledge to pull yourself onto it, or at open air to dash. It costs no pearl, but you take the normal pearl landing damage, which drops as you level. It only works in survival mode.

1. Learn it, then jump.
2. Press jump again in mid-air while looking where you want to go.
3. Hold sneak as you do it to phase straight through walls and land in the farthest open space in range.

### Anti-Levitation (`rift-descent`)

Shulker hits are annoying because the levitation lifts you and the fall afterward hurts. Tap sneak while levitating and Anti-Levitation strips the effect and shields you from fall damage for the next few seconds, so you come straight back down safely.

1. Learn it (it is permanent once learned).
2. While levitating, tap sneak.
3. Levitation ends and your fall damage is nullified for the duration of the cooldown.

The fall protection is an attribute change, not Slow Falling, so you drop at normal speed and take no damage from the landing.

### Rift Visage (`rift-visage`)

While you have at least one ender pearl anywhere in your inventory, endermen never take you as a target. Look at them all you like. It works on its own once learned, and it is permanent.

### Ender Taglock (`rift-ender-taglock`)

Taglock inverts the ender pearl. Instead of teleporting yourself, you bind a pearl to something else and throw it to move that thing. The tagging hit deals no damage.

1. Learn it and hold a plain ender pearl in your main hand.
2. Sneak and hit the entity you want to tag. The pearl becomes a Taglocked Ender Pearl showing its target.
3. Right-click to throw the pearl. Wherever it lands, the tagged target is teleported there. You are never teleported.

Level 1 tags passive and hostile mobs. Level 2 adds villagers and large targets. Level 3 tags anything, including players. By default the thrower eats the pearl teleport damage rather than the victim.

### Inflated Pocket Dimension (`rift-inflated-pocket-dimension`)

Your ender chest becomes a live building supply. It is the difference between one trip to build a bridge and six.

1. Learn it (it is permanent once learned).
2. With an empty main hand, right-click a block to pull a stack of that same block out of your ender chest.
3. Keep building. When a stack in your hand runs low, placing blocks refills it from the ender chest automatically.
4. Sneak and drop an item to send it into the ender chest instead of the ground.

### Void Magnet (`rift-void-magnet`)

Hold sneak and nearby item drops start flowing to you on a pulse, straight into your ender chest. It is built for mining, farming, and mob grinders where the drops are spread over a wide area. Leveling widens the radius, raises the items per pulse, and shortens the pulse delay.

1. Learn it.
2. Sneak and stay sneaking. The magnet pulses on a timer while you hold it.
3. Items land in your ender chest. By default anything that does not fit stays on the ground; a config switch lets the leftovers spill into your normal inventory.

### Void Skin (`rift-void-skin`)

Void Skin is a death save. Any hit that would kill you is cancelled and you are blinked to a nearby safe spot instead, with brief Resistance to survive whatever comes next. It costs one plain ender pearl from your inventory and has a long cooldown that shortens as you level.

It works on its own once learned. If no safe spot is found nearby it falls back to the current world's spawn. With no plain pearl on you, or with the cooldown still running, the hit lands normally.

### Pearl Rebound (`rift-pearl-rebound`)

A thrown pearl no longer commits at the first thing it hits. The first block it strikes bounces it off the surface, steered toward wherever you are looking, and the pearl teleports you at its next impact. That lets you bank pearls around corners and through gaps you cannot see through. Pearl landing damage is also reduced, and both the reduction and the steering improve with level. It works on its own once learned.

### Rift Conduit (`rift-conduit`)

Conduit links two containers so items move between them on their own. Dump loot into the chest by your farm and it appears in the sorting chest at your base.

1. Learn it and hold a plain ender pearl.
2. Sneak-right-click the source container. The pearl becomes a Rift Conduit Taglock.
3. Right-click a second container with the taglock to link the pair.
4. Put items in one container and close it. They flow to the partner.

Binding range grows a long way with level, and at max level the two containers can sit in different dimensions. Both ends re-check container permissions on every flow, and anything the partner cannot accept comes straight back to the source. On Folia the player and the endpoint have to share a region, so cross-region and cross-dimension links do not work there.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `rift` |
| Class | `SkillRift` |
| Icon | `ENDER_EYE` |
| Color | `DARK_PURPLE` |
| Interval (ms) | `1154` |
| Skill config | `plugins/Adapt/adapt/skills/rift.toml` |
| Adaptation count | 13 |

End-creature XP is `multiplier * min(damage, target base max health)`, so overkill damage does not pay extra. Teleport XP is granted silently (no floating text) and is rate limited by `teleportXPCooldown`; teleports suppressed by Chronos Instant Recall are ignored entirely.

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/rift.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Turns the whole Rift skill off when false. |
| `skillColor` | `"&5"` | Legacy ampersand color code used for this skill in menus and text. |
| `throwEnderpearlXP` | `65` | XP granted per ender pearl thrown. |
| `throwEnderEyeXP` | `30` | XP granted per eye of ender thrown. |
| `teleportXP` | `15` | XP granted per teleport, subject to the teleport cooldown. |
| `teleportXPCooldown` | `60000` | Milliseconds between teleport XP awards. The stat still counts every teleport. |
| `destroyEndCrystalXP` | `250` | XP granted for destroying an end crystal. |
| `damageEndermanXPMultiplier` | `4` | XP per point of damage dealt to endermen. |
| `damageEndermiteXPMultiplier` | `2` | XP per point of damage dealt to endermites. |
| `damageEnderdragonXPMultiplier` | `8` | XP per point of damage dealt to the ender dragon. |
| `challengeRiftReward` | `500` | Base XP reward for every Rift challenge chain. |

### Skill milestones

| Advancement key | Stat key | Threshold | XP reward |
|-----------------|----------|-----------|-----------|
| `challenge_rift_50` | `rift.teleports` | 50 | `challengeRiftReward` |
| `challenge_rift_500` | `rift.teleports` | 500 | `challengeRiftReward` x 2 |
| `challenge_rift_5k` | `rift.teleports` | 5000 | `challengeRiftReward` x 5 |
| `challenge_rift_pearls_50` | `rift.ender.pearls` | 50 | `challengeRiftReward` |
| `challenge_rift_pearls_500` | `rift.ender.pearls` | 500 | `challengeRiftReward` x 2 |
| `challenge_rift_enderman_50` | `rift.enderman.kills` | 50 | `challengeRiftReward` |
| `challenge_rift_enderman_500` | `rift.enderman.kills` | 500 | `challengeRiftReward` x 2 |
| `challenge_rift_dragon_500` | `rift.dragon.damage` | 500 | `challengeRiftReward` |
| `challenge_rift_dragon_5k` | `rift.dragon.damage` | 5000 | `challengeRiftReward` x 2 |
| `challenge_rift_crystal_10` | `rift.crystals.destroyed` | 10 | `challengeRiftReward` |
| `challenge_rift_crystal_100` | `rift.crystals.destroyed` | 100 | `challengeRiftReward` x 2 |

`rift.enderman.kills` counts kills where the enderman's killer is the player. `rift.teleports` is also incremented by Rift Blink and Void Skin.

### Shared adaptation keys

Every adaptation TOML at `plugins/Adapt/adapt/adaptations/<id>.toml` carries `enabled`, `permanent`, `showParticles`, `showSounds`, plus the cost fields `baseCost`, `costFactor`, `maxLevel`, and `initialCost` listed per adaptation below.

### Rift Resistance

| Property | Value |
|----------|-------|
| Class | `RiftResist` |
| Icon | `SCULK_VEIN` |
| Max level | 1 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 3 |
| Cost factor | 1 |
| Tick interval (ms) | 10288 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-resist.toml` |
| Listened events | `PlayerInteractEvent` (`on`, HIGHEST) |
| Stats | `rift.resist.activations` |
| Milestone | `challenge_rift_resist_200` at 200 activations, 300 XP |
| Menu lore | Passive: Provides resistance when you use rift abilities, or Ender Items; NOT Including Portable Enderchest, only things you can Consume |

Triggers only on right-click air with `ENDER_EYE` or `ENDER_PEARL` in the main hand, granting Resistance at amplifier `amplitude` for `duration` ticks plus 3 XP. Despite the menu lore, `RiftEnderchest` also calls into this adaptation and grants a 10 tick, amplifier 2 Resistance pulse when Easy Enderchest opens a chest and Rift Resistance is learned.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `amplitude` | `1` | Resistance amplifier granted, so 1 means Resistance II. |
| `duration` | `80` | Resistance duration in ticks. |
| `activationCooldownMillis` | `4000` | Milliseconds between right-click-air activations and the XP they grant. |

### Remote Access

| Property | Value |
|----------|-------|
| Class | `RiftAccess` |
| Icon | `NETHER_STAR` |
| Max level | 1 |
| Initial knowledge cost | 15 |
| Base knowledge cost | 3 |
| Cost factor | 0.2 |
| Tick interval (ms) | 1000 (framework default, never overridden) |
| Config file | `plugins/Adapt/adapt/adaptations/rift-access.toml` |
| Listened events | `PlayerInteractEvent`, `BlockBurnEvent`, `BlockPistonRetractEvent`, `BlockPistonExtendEvent`, `BlockExplodeEvent`, `EntityExplodeEvent`, `BlockBreakEvent`, `InventoryCloseEvent`, `PlayerQuitEvent`, `ChunkUnloadEvent` (all `on`) |
| Stats | `rift.access.remote-opens` |
| Milestones | `challenge_rift_access_100` at 100 remote opens, 300 XP; `challenge_rift_access_2500` at 2500 remote opens, 1000 XP |
| Menu lore | Ender Pearl + Compass = Reliquary Portkey; This item allows you to access containers remotely; Once crafted look at item to see usage |
| Recipe | Shapeless `rift-remote-access`: 1 `ENDER_PEARL` + 1 `COMPASS`, produces a bound ender pearl (Reliquary Portkey) |

Binding and every remote open must pass native container protectors plus Bukkit right-click-block events for every physical container block, so either half can deny a double chest. A remote double-chest session indexes both blocks and every unique chunk, holding those chunk tickets only until the view closes or the attempt fails. On Folia the player and every physical container block must share the current owning region, and binding requires a direct block click. Block break, burn, piston, and explosion handlers invalidate open sessions when the container is destroyed. No adaptation-specific config knobs.

### Easy Enderchest

| Property | Value |
|----------|-------|
| Class | `RiftEnderchest` |
| Icon | `ENDER_CHEST` |
| Max level | 1 |
| Initial knowledge cost | 10 |
| Base knowledge cost | 0 |
| Cost factor | 0.0 |
| Tick interval (ms) | 9248 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-enderchest.toml` |
| Listened events | `PlayerInteractEvent` (`on`, NORMAL) |
| Stats | `rift.enderchest.opens` |
| Milestone | `challenge_rift_enderchest_200` at 200 opens, 300 XP |
| Menu lore | Click an Ender Chest in your hand to open it (just don't place it) |

Triggers on right-click air, left-click air, or left-click block with `ENDER_CHEST` in the main hand. A successful use sets a 100 tick cooldown on the ender chest item; clicking during the cooldown cancels the interaction. If `rift-resist` is learned, a 10 tick amplifier 2 Resistance pulse is applied. No adaptation-specific config knobs.

### Rift Gate

| Property | Value |
|----------|-------|
| Class | `RiftGate` |
| Icon | `RESPAWN_ANCHOR` |
| Max level | 1 |
| Initial knowledge cost | 30 |
| Base knowledge cost | 0 |
| Cost factor | 0.0 |
| Tick interval (ms) | 1322 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-gate.toml` |
| Listened events | `PlayerInteractEvent` (`on`); `PlayerQuitEvent` (`on`); `PlayerJoinEvent` (`on`) |
| Stats | `rift.gate.teleports`, `rift.gate.total-distance` |
| Milestones | `challenge_rift_gate_100` at 100 gate teleports, 400 XP; `challenge_rift_gate_50k_dist` at 50000 blocks travelled, 1500 XP |
| Menu lore | CRAFTING: Emerald + Amethyst shard + Ender Pearl; Read before using!; 5s delay, you can die while you are in this animation |
| Recipe | Shapeless `rift-recall-gate`: 1 `ENDER_PEARL` + 1 `AMETHYST_SHARD` + 1 `EMERALD`, produces a bound eye of ender. Registered only when `requireCraftedEye` is true |

Channel length is 85 ticks. During the channel the player gets Blindness for 100 ticks and Levitation for 85 ticks. The eye reservation and the cooldown are both committed when the channel starts, so stowing or dropping the eye mid-channel does not refund it. The gate's cooldown lives in the bound eye's own cooldown group so a plain eye of ender can still be thrown to locate a stronghold. Cooldown when `consumeOnUse` is false is 150 ticks.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `consumeOnUse` | `true` | When true the bound eye is consumed by a completed teleport. When false the eye survives and a 150 tick cooldown gates reuse. |
| `requireCraftedEye` | `true` | When true only the crafted bound eye works and the recipe is registered. When false any eye of ender can be bound. |

### Rift Blink

| Property | Value |
|----------|-------|
| Class | `RiftBlink` |
| Icon | `FEATHER` |
| Max level | 5 |
| Initial knowledge cost | 1 |
| Base knowledge cost | 7 |
| Cost factor | 0.12 |
| Tick interval (ms) | 9288 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-blink.toml` |
| Listened events | `PlayerMoveEvent` (`on`, MONITOR) |
| Stats | `rift.blink.blinks`, `rift.blink.distance-blinked`, `rift.teleports` |
| Milestones | `challenge_rift_blink_500` at 500 blinks, 400 XP; `challenge_rift_blink_5k` at 5000 blocks blinked, 1500 XP |
| Menu lore | Blink Range; Self-Damage (hearts); Sneak while blinking to phase through walls |

Requires survival game mode. The gesture is a double jump detected from movement. Distance is `baseDistance + (levelPercent * distanceFactor)`. Self damage is `pearlDamageBase - ((level - 1) * pearlDamageReductionPerLevel)`, floored at `minimumPearlDamage`. The teleport fires an `AdaptAdaptationTeleportEvent` that other plugins can cancel.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldownMillis` | `2000` | Milliseconds between successful blinks. |
| `pearlDamageBase` | `5.0` | Self damage at level 1, in health points (2 = 1 heart). |
| `pearlDamageReductionPerLevel` | `1.0` | Self damage removed per level past the first. |
| `minimumPearlDamage` | `1.0` | Floor on blink self damage. |
| `baseDistance` | `12` | Blink distance in blocks before the level bonus. |
| `distanceFactor` | `20` | Blink distance in blocks added at max level. |
| `groundSnapDepth` | `5` | Blocks searched downward from the aimed point to prefer solid ground. |
| `momentumCarry` | `0.35` | Velocity carried along your look direction after landing, in blocks per tick. |
| `minBlinkDistance` | `1.5` | Shortest distance that still counts as a blink, in blocks. |
| `phaseWhileSneaking` | `true` | Lets a blink started while sneaking pass through walls and land in the farthest open space in range. |

### Anti-Levitation

| Property | Value |
|----------|-------|
| Class | `RiftDescent` |
| Icon | `SHULKER_BOX` |
| Max level | 1 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 1 |
| Cost factor | 0.95 |
| Tick interval (ms) | 9544 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-descent.toml` |
| Listened events | `PlayerToggleSneakEvent` (`on`, HIGHEST) |
| Stats | `rift.descent.levitation-cancelled` |
| Milestones | `challenge_rift_descent_100` at 100 cancels, 300 XP; `challenge_rift_descent_1k` at 1000 cancels, 1000 XP |
| Menu lore | Just Sneak to descend, and you will fall at a less than normal rate!; Cooldown: {duration} |
| Permanent | `permanent = true` by default, so it cannot be unlearned once learned |

Removes the Levitation effect and applies a `FALL_DAMAGE_MULTIPLIER` modifier of -1.0 for `cooldown * 20` ticks. That nullifies fall damage for the cooldown window; it does not apply Slow Falling and does not change your fall speed, despite what the adaptation description says.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldown` | `5.0` | Seconds between uses, and also the length of the fall damage protection. |

### Rift Visage

| Property | Value |
|----------|-------|
| Class | `RiftVisage` |
| Icon | `POPPED_CHORUS_FRUIT` |
| Max level | 1 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 8 |
| Cost factor | 0 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-visage.toml` |
| Listened events | `EntityTargetEvent` (`onEntityTarget`, NORMAL) |
| Stats | `rift.visage.stares-survived` |
| Milestones | `challenge_rift_visage_100` at 100 stares, 300 XP; `challenge_rift_visage_1k` at 1000 stares, 1000 XP |
| Menu lore | Endermen will not become aggressive if you have Ender Pearls in your inventory. |
| Permanent | `permanent = true` by default, so it cannot be unlearned once learned |

Cancels the target event whenever an enderman tries to target a player carrying at least one `ENDER_PEARL`. The stat is credited at most once per enderman per 10 seconds, and the effect emits an interruption utility signal to the mutation runtime. No adaptation-specific config knobs.

### Ender Taglock

| Property | Value |
|----------|-------|
| Class | `RiftEnderTaglock` |
| Icon | `ENDER_PEARL` |
| Max level | 3 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 7 |
| Cost factor | 0.95 |
| Tick interval (ms) | 1200 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-ender-taglock.toml` |
| Listened events | `EntityDamageByEntityEvent` (`on`, HIGHEST); `PlayerInteractEvent` (`on`, HIGHEST, receives cancelled events); `PlayerTeleportEvent` (`on`); `ProjectileHitEvent` (`on`); `PlayerQuitEvent` (`on`) |
| Stats | `rift.ender-taglock.entities-tagged`, `rift.ender-taglock.taglocked-teleports` |
| Milestones | `challenge_rift_taglock_100` at 100 tags, 400 XP; `challenge_rift_taglock_500` at 500 taglocked teleports, 1000 XP |
| Menu lore | Level 1: Passive and hostile mobs; Level 2: Villagers and large targets; Level 3: Any target, including players; Tagged Pearl Throw Cooldown |

Tagging requires a sneaking melee hit with a plain `ENDER_PEARL` in the main hand; the damage event is cancelled so the tag deals no damage. Target eligibility by level: 1 covers passive and hostile mobs, 2 adds villagers and targets above the large size thresholds, 3 covers everything including players. Throw cooldown is `throwCooldownTicksBase - (levelPercent * throwCooldownTicksFactor)` with a floor of 4 ticks. The thrower's own vanilla pearl teleport is suppressed briefly after a taglocked pearl lands.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `throwCooldownTicksBase` | `30` | Cooldown between tagged pearl throws before the level reduction, in ticks. |
| `throwCooldownTicksFactor` | `14` | Cooldown ticks removed at max level. |
| `suppressPearlTeleportWindowMillis` | `250` | How long the thrower's own vanilla pearl teleport stays suppressed after a taglocked pearl lands. |
| `largeWidthThreshold` | `1.3` | Hitbox width in blocks at or above which a target counts as large for level 2. |
| `largeHeightThreshold` | `2.35` | Hitbox height in blocks at or above which a target counts as large for level 2. |
| `xpOnTag` | `8` | Rift XP granted for tagging an entity. |
| `xpOnThrow` | `5` | Rift XP granted for throwing a tagged pearl. |
| `xpOnTeleport` | `14` | Rift XP granted when a tagged target is relocated. |
| `damageSender` | `true` | When true the thrower takes the pearl teleport damage. When false the teleported target takes it instead. |

### Inflated Pocket Dimension

| Property | Value |
|----------|-------|
| Class | `RiftInflatedPocketDimension` |
| Icon | `ENDER_EYE` |
| Max level | 1 |
| Initial knowledge cost | 7 |
| Base knowledge cost | 7 |
| Cost factor | 1 |
| Tick interval (ms) | 600 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-inflated-pocket-dimension.toml` |
| Listened events | `PlayerInteractEvent` (`on`); `BlockPlaceEvent` (`on`); `PlayerDropItemEvent` (`on`) |
| Stats | `rift.inflated-pocket.items-pulled`, `rift.inflated-pocket.items-stored` |
| Milestones | `challenge_rift_pocket_5k` at 5000 items pulled, 400 XP; `challenge_rift_pocket_store_10k` at 10000 items stored, 1000 XP |
| Menu lore | Right-click block to pull stack; Building auto-refill from ender chest; Sneak-drop stores item in ender chest |
| Permanent | `permanent = true` by default, so it cannot be unlearned once learned |

The pull requires an empty main hand and works on right-click block, right-click air, or left-click air, using the block you are looking at within 5 blocks for the air variants. Build refill tops the held stack back up to `buildRefillAmount` or the material's max stack size, whichever is smaller. Storing requires sneaking while dropping.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `buildRefillAmount` | `64` | Items pulled from the ender chest to top up the held stack while building. |
| `rightClickPullAmount` | `64` | Items pulled per right-click on a block. |
| `xpPerTransferredItem` | `0.08` | Rift XP granted per item stored into the ender chest by a sneak-drop. Pulls and build refills award no XP. |

### Void Magnet

| Property | Value |
|----------|-------|
| Class | `RiftVoidMagnet` |
| Icon | `HOPPER_MINECART` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.72 |
| Tick interval (ms) | 50 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-void-magnet.toml` |
| Listened events | `PlayerToggleSneakEvent` (`on`); `PlayerQuitEvent` (`on`) |
| Stats | `rift.void-magnet.items-pulled` |
| Milestones | `challenge_rift_void_magnet_5k` at 5000 items, 400 XP; `challenge_rift_void_magnet_50k` at 50000 items, 1500 XP |
| Menu lore | Magnet Radius; Max Items Per Pulse; Pulse Delay |

Hard caps in code: radius 16 blocks, 32 items per pulse, 1024 active sessions, 64 candidate inspections per scan, and per-window budgets for session visits, scans, and item handoffs. Pulse delay has a floor of 2 ticks. Each candidate must pass the normal pickup event sequence using the combined remaining capacity of the ender chest plus any permitted inventory overflow; a cancelled pickup leaves the item entity alone. On Folia a pulse scans only when its whole footprint belongs to the current region.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `allowEnderChestOverflow` | `false` | When true, items that do not fit in the ender chest go to your normal inventory. When false they stay on the ground. |
| `radiusBase` | `5` | Magnet radius in blocks before the level bonus. |
| `radiusFactor` | `9` | Magnet radius in blocks added at max level. |
| `maxItemsBase` | `10` | Item drops pulled per pulse before the level bonus. |
| `maxItemsFactor` | `22` | Item drops per pulse added at max level. |
| `pulseTicksBase` | `20` | Ticks between pulses before the level reduction. |
| `pulseTicksFactor` | `12` | Ticks removed from the pulse delay at max level. |
| `xpPerMovedItem` | `0.7` | Rift XP granted per item moved. |

### Void Skin

| Property | Value |
|----------|-------|
| Class | `RiftVoidSkin` |
| Icon | `ECHO_SHARD` |
| Max level | 4 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 8 |
| Cost factor | 0.4 |
| Tick interval (ms) | 1000 (framework default, never overridden) |
| Config file | `plugins/Adapt/adapt/adaptations/rift-void-skin.toml` |
| Listened events | `EntityDamageEvent` (`on`, HIGHEST); `PlayerQuitEvent` (`on`); `PlayerJoinEvent` (`on`) |
| Stats | `rift.void-skin.escapes`, `rift.teleports` |
| Milestones | `challenge_rift_void_skin_50` at 50 escapes, 400 XP; `challenge_rift_void_skin_500` at 500 escapes, 1500 XP |
| Menu lore | Any lethal damage triggers the escape; Escape Cooldown; Costs an Ender Pearl |

Triggers when the final damage would exceed current health plus absorption. Requires a plain ender pearl in the inventory, which is reserved and consumed. The safe spot search radius is clamped to 3-16 blocks; with no safe spot found it falls back to the current world's spawn, and with no usable world spawn the escape is skipped and the damage lands.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldownBaseMillis` | `120000` | Milliseconds between escapes at level 1. |
| `cooldownReductionPerLevelMillis` | `18000` | Cooldown milliseconds removed per level past the first. |
| `minimumCooldownMillis` | `45000` | Floor on the escape cooldown, in milliseconds. |
| `resistanceTicksBase` | `60` | Resistance duration after an escape before the level bonus, in ticks. |
| `resistanceTicksPerLevel` | `20` | Resistance ticks added per level. |
| `resistanceAmplifier` | `2` | Resistance amplifier applied after an escape, so 2 means Resistance III. |
| `searchRadius` | `9` | Horizontal search radius for a safe blink spot, in blocks. |
| `minRadius` | `4` | Shortest horizontal blink distance, in blocks. |
| `xpOnEscape` | `40` | Rift XP granted when an escape triggers. |

### Pearl Rebound

| Property | Value |
|----------|-------|
| Class | `RiftPearlRebound` |
| Icon | `SLIME_BALL` |
| Max level | 4 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 5 |
| Cost factor | 0.35 |
| Tick interval (ms) | 1000 (framework default, never overridden) |
| Config file | `plugins/Adapt/adapt/adaptations/rift-pearl-rebound.toml` |
| Listened events | `ProjectileLaunchEvent` (`on`); `ProjectileHitEvent` (`on`, HIGH); `EntityDamageEvent` (`on`, LOWEST) |
| Stats | `rift.pearl-rebound.rebounds` |
| Milestones | `challenge_rift_rebound_100` at 100 rebounds, 400 XP; `challenge_rift_rebound_1k` at 1000 rebounds, 1500 XP |
| Menu lore | Pearl Damage Reduction; Aim Control |

Only plain ender pearls rebound, and only once each: pearls already claimed by another Rift adaptation, or already rebounded, teleport normally. The bounce reflects the pearl off the struck block face, biases it toward the thrower's look direction, and relaunches it at `reboundSpeed`. Damage reduction and aim bias are both capped at 0.9 in code.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `damageReductionBase` | `0.3` | Fraction of pearl teleport damage removed at level 1, 0-1. |
| `damageReductionPerLevel` | `0.15` | Extra damage reduction fraction per level past the first. |
| `aimBiasBase` | `0.3` | Fraction the rebounded pearl steers toward your look direction at level 1, 0-1. |
| `aimBiasPerLevel` | `0.15` | Extra steering fraction per level past the first. |
| `reboundSpeed` | `1.5` | Launch speed of the rebounded pearl, in blocks per tick. Floor is 0.4. |
| `xpOnRebound` | `6` | Rift XP granted each time a pearl rebounds. |

### Rift Conduit

| Property | Value |
|----------|-------|
| Class | `RiftConduit` |
| Icon | `CONDUIT` |
| Max level | 4 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 8 |
| Cost factor | 0.6 |
| Tick interval (ms) | 1000 |
| Config file | `plugins/Adapt/adapt/adaptations/rift-conduit.toml` |
| Listened events | `PlayerInteractEvent` (`on`); `InventoryCloseEvent` (`on`, MONITOR); `PlayerQuitEvent` (`on`); `PlayerJoinEvent` (`on`) |
| Stats | `rift.conduit.links-formed`, `rift.conduit.items-flowed` |
| Milestones | `challenge_rift_conduit_10` at 10 links, 500 XP; `challenge_rift_conduit_10k` at 10000 items flowed, 1500 XP |
| Menu lore | Items Per Flow; Binding Range; Links across dimensions |

Gesture table: a taglock in hand binds when it clicks a container and prints a hint when it does not; a plain pearl captures only when sneaking on a container. Taglocks held by a player without the adaptation are cancelled rather than thrown. Throughput is clamped to 1-1152 items and binding range to at most 512 blocks. Capture and binding honor the original click denial and both physical halves of a double chest. Deferred bind writes and each flow source and destination reauthorize container use on their owning region; if the partner is denied, cannot load, or cannot accept a delivery, the items return to the source. On Folia a deferred bind or flow fails closed unless the player and endpoint share the current owning region, so cross-region and cross-dimension transfers are unavailable there.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `throughputBase` | `48` | Items moved per flow before the level bonus. |
| `throughputFactor` | `336` | Items per flow added at max level. |
| `rangeBase` | `24` | Binding range in blocks before the level bonus. |
| `rangeFactor` | `200` | Binding range in blocks added at max level. |
| `crossDimensionAtMax` | `true` | Allows linking containers in different worlds once the adaptation is at max level. |
| `xpOnLink` | `30` | Rift XP granted when a new link is formed. |
| `xpPerFlow` | `0.4` | Rift XP granted per item flowed between linked containers. |

### Support classes (not player adaptations)

- `RiftAccessViewRegistry` owns remote-container sessions and the block and chunk references held for every physical container part while each view stays open.
- `RiftPearls` distinguishes plain ender pearls from pearls already claimed by a Rift adaptation.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
