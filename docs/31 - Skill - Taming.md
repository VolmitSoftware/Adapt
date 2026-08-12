# Skill: Taming

Taming is the pet skill. You level it by taming animals, breeding them, and letting your pets fight for you, and the adaptations turn a wolf pack or a horse from a novelty into real support. Fourteen adaptations cover the whole arc: tougher and stronger pets, a recall so you stop losing them, damage sharing, item retrieval, focus-fire commands, and a last-second save when a pet is about to die.

Early on the skill mostly rewards the things you already do. Tame a cat, breed some horses, let a wolf finish a skeleton, and XP comes in. Later the pack starts doing work you would otherwise do yourself: wolves walk over and pick up your drops, pets body-block arrows, and a bone in your hand becomes a "kill that" button.

Mounted play is part of the skill too. Mounted Tactics changes how horses, striders, and pigs handle and hit, so riding is a real combat style instead of just travel.

Most of the pack adaptations are passive and stack quietly. Only three ask for a gesture: Beast Recall (lead), Alpha's Command (bone), and Wild Empathy (the animal's normal taming food).

## How you earn Taming XP

- Taming an animal pays `tameSuccessXP` and counts toward `taming.tamed`.
- Breeding pays `tameXpBase` and counts toward `taming.bred`.
- A tamed pet damaging something pays damage times `tameDamageXPMultiplier` and adds the raw damage to `taming.pet.damage`.
- A mob killed by your pet pays `petKillXP` and counts toward `taming.pet.kills`. The credit only fires when no player is the mob's killer, and TragOul skeletal servants and Excavation grave mobs are excluded.

Breeding XP and pet damage XP share one cooldown of `cooldownDelay` milliseconds, so a long fight or a breeding spree pays on a steady drip rather than per event. Tame and pet-kill XP have no such cooldown.

## Adaptations

Everything below needs the same four things before it does anything: the adaptation learned at level 1 or higher, the Taming skill and that adaptation both enabled in config, the `adapt.use` permission for it, and any protection or region plugin allowing the action at that spot. Learn adaptations from the Adapt menu (`/adapt`), under Taming.

### Tame Health (`tame-health`)

Every animal you own gets a large percentage boost to its maximum health, applied as a transient attribute modifier that is refreshed while you are online and stripped when ownership ends. Good first pick, because a dead wolf does no damage.

### Tame Damage (`tame-damage`)

Your pets hit harder. Same idea as Tame Health, but on attack damage, and it pairs with anything that sends pets into a fight.

### Tame Regeneration (`tame-health-regeneration`)

When one of your pets takes damage, it heals a chunk back a moment later. Each pet has its own 8 second window between heals, so it takes the edge off sustained fights instead of making pets unkillable. Caps at level 3.

### Pack Leader Aura (`tame-pack-leader-aura`)

Pets near you get speed and regeneration for as long as they stay in range. The radius and the effect strength both grow with level. Purely passive: stay near the pack and it applies itself.

### Beast Recall (`tame-beast-recall`)

Pulls your nearest owned pet to a safe spot beside you. Handy when a wolf gets stuck on terrain or a horse wanders off during a fight.

How to use it:

1. Learn Beast Recall in the Adapt menu.
2. Hold a lead in your main hand.
3. Sneak and right-click.
4. The nearest owned pet inside the recall radius teleports next to you. One pet per use.

The recall needs a safe landing spot near you (open feet and head space over solid ground) and costs `hungerCost` food points. It puts a visible item cooldown on leads, which is what stops you from spamming it. Pets already closer than the minimum distance are ignored.

### Shared Pain (`tame-shared-pain`)

Some of the damage aimed at you is split across nearby pets instead. The split never takes a pet below its health floor, and whatever the pack absorbs is subtracted from your own hit. You get Taming XP for the damage they eat for you.

### Mounted Tactics (`tame-mounted-tactics`)

Riding gets better in several ways at once. You deal more damage and take less while mounted on a horse, strider, or pig. Horses gain speed and jump strength, striders gain speed and stop shivering over lava (and you get fire resistance while riding one), and pigs give you resistance. Sprinting on a horse or a pig also adds a forward shove, so the mount actually feels like it is charging.

How to use it:

1. Learn Mounted Tactics in the Adapt menu.
2. Ride a horse-type mount (the code accepts anything Bukkit calls an AbstractHorse, so donkeys, mules, and llamas count), a strider, or a pig.
3. Fight from the saddle for the damage bonus and reduction.
4. Sprint while mounted on a horse or pig for the extra push.

### Fetch (`tame-fetch`)

Your tamed wolves collect dropped items around you and bring them back. With `realFetch` on, a wolf inside the walk radius physically trots to the drop, picks it up, and walks it to you. Drops farther out (or when a walk cannot start) are pulled to you directly instead.

How to use it:

1. Learn Fetch in the Adapt menu.
2. Keep tamed wolves near you. Sitting, leashed, and riding wolves are skipped.
3. Drop items or walk near loose drops.
4. Wolves work automatically on their own pass, subject to the carry chance roll.

A fetched item goes through the normal pickup event as if you had walked over it, so a protection plugin that would block your pickup blocks the fetch too. On Folia, wolf and item scans only run when the area belongs to the current region.

### Alpha's Command (`tame-alphas-command`)

Marks a target and sends every nearby combat pet at it. Only wolves, cats, and llamas answer the call. Commanded pets are stood up if they were sitting, given a short attack damage and movement speed buff, and kept on the target until the focus runs out, the target dies, or the target stops being a legal thing for you to hit.

How to use it:

1. Learn Alpha's Command in the Adapt menu.
2. Hold a bone in your main hand.
3. Sneak and left-click at what you want dead. You can also sneak and melee the target directly.
4. The target glows red for you alone while your pack focuses it.

Each successful command eats one bone (not in creative) and has its own cooldown. Your own pets, NPCs, invulnerable entities, and TragOul servants are never valid targets.

### Guardian Instinct (`tame-guardian-instinct`)

An arrow headed for you can be intercepted by a nearby pet, which leaps at you and eats the shot at reduced damage while your hit is cancelled outright. It rolls per projectile, and a short cooldown stops one pet from soaking an entire barrage.

### Stable Hand (`tame-stable-hand`)

Animals you tame or breed keep a permanent bias toward better movement speed, jump strength, max health, and safe fall distance. The modifiers stay on the animal, so breeding programs compound over time. Tame or breed as usual and the bias applies itself, with a short chime to confirm.

### Wild Empathy (`tame-wild-empathy`)

Two effects. Taming can succeed instantly on a roll instead of grinding through vanilla's odds, and neutral mobs frequently give up on being angry at you.

How to use it:

1. Learn Wild Empathy in the Adapt menu.
2. Hold the animal's normal taming food: bone for wolves, cod or salmon for cats and ocelots, any of the seeds for parrots.
3. Right-click the untamed animal.
4. On a successful roll the animal is tamed immediately and one food item is consumed.

The anger half applies to wolves, bees, polar bears, llamas, pandas, and goats and works on its own with no gesture.

### Battle Bond (`tame-battle-bond`)

When one of your pets lands a kill, you and every owned pet nearby get speed, regeneration, and strength for a few seconds, and the bonded pets briefly glow. It turns a pack fight into a snowball as long as kills keep coming.

### Last Breath (`tame-last-breath`)

A killing blow on a pet is refused. The pet is set to 1 HP, made immune for a short window, and teleported to a safe spot next to you. Each pet has its own long cooldown, so it is a rescue, not a health bar.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `taming` |
| Class | `SkillTaming` |
| Icon | `LEAD` |
| Color | `GOLD` |
| Interval (ms) | `3480` |
| Skill config | `plugins/Adapt/adapt/skills/taming.toml` |
| Adaptation count | 14 |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/taming.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Turns the whole Taming skill on or off. |
| `skillColor` | `"&6"` | Legacy ampersand color code used for Taming in menus and text. |
| `tameXpBase` | `65` | Skill XP paid when you breed an animal. |
| `cooldownDelay` | `1500` | Milliseconds between breeding and pet-damage XP awards for one player. |
| `tameDamageXPMultiplier` | `8.0` | Skill XP per point of damage your pets deal. |
| `tameSuccessXP` | `150` | Skill XP paid when you tame an animal. |
| `petKillXP` | `25` | Skill XP paid when one of your pets kills a mob. |
| `challengeTamingReward` | `500` | Knowledge paid by the breeding challenges. |
| `challengePetDmgReward` | `500` | Knowledge paid by the pet damage challenges. |
| `challengeTamedReward` | `500` | Knowledge paid by the tamed-animal challenges. |
| `challengePetKillsReward` | `500` | Knowledge paid by the pet kill challenges. |

### Skill milestones

| Advancement key | Stat key | Threshold | Reward |
|-----------------|----------|-----------|--------|
| `challenge_taming_10` | `taming.bred` | 10 | `challengeTamingReward` |
| `challenge_taming_50` | `taming.bred` | 50 | `challengeTamingReward` x 2 |
| `challenge_taming_500` | `taming.bred` | 500 | `challengeTamingReward` x 5 |
| `challenge_pet_dmg_500` | `taming.pet.damage` | 500 | `challengePetDmgReward` |
| `challenge_pet_dmg_5k` | `taming.pet.damage` | 5000 | `challengePetDmgReward` x 5 |
| `challenge_tamed_10` | `taming.tamed` | 10 | `challengeTamedReward` |
| `challenge_tamed_100` | `taming.tamed` | 100 | `challengeTamedReward` x 5 |
| `challenge_pet_kills_25` | `taming.pet.kills` | 25 | `challengePetKillsReward` |
| `challenge_pet_kills_250` | `taming.pet.kills` | 250 | `challengePetKillsReward` x 5 |

### Shared adaptation keys

Every adaptation TOML at `plugins/Adapt/adapt/adaptations/<id>.toml` also carries `enabled`, `permanent`, `showParticles`, `showSounds`, `baseCost`, `costFactor`, `maxLevel`, and `initialCost`.

Level scaling below uses "level percent", which is the learned level divided by the adaptation's max level (0 to 1).

Every adaptation carries a tick interval because every adaptation is registered with the scheduler, but only Tame Health, Tame Damage, Tame Regeneration, Pack Leader Aura, Mounted Tactics, and Fetch actually run work on that tick. The rest are event-driven and their interval is inert.

### Tame Health

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

Listened events: `EntityDeathEvent`, `EntitiesUnloadEvent` (both only clear cached state for the dead or unloaded entity).

Menu lore: "Increased Health".

Milestone: `challenge_taming_health_boost_72k` on `taming.health-boost.ticks-active` at 72000, reward 400.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `healthBoostFactor` | `2.5` | Extra max-health multiplier added at full level percent. |
| `healthBoostBase` | `0.57` | Max-health multiplier applied at level percent 0. Total is applied as a scalar to the pet's max health. |
| `maxTameablesPerPass` | `128` | Loaded tameables examined per scheduler pass. |

### Tame Damage

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

Listened events: `EntityDeathEvent` (credits `taming.damage.pet-kills` when your pet dealt the killing blow, and clears cached state), `EntitiesUnloadEvent` (clears cached state).

Menu lore: "Increased Damage".

Milestones: `challenge_taming_damage_500` and `challenge_taming_damage_5k` on `taming.damage.pet-kills` at 500 (reward 400) and 5000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `baseDamage` | `0.08` | Attack-damage multiplier applied at level percent 0. |
| `damageFactor` | `0.65` | Extra attack-damage multiplier added at full level percent. Total is applied as a scalar to the pet's attack damage. |
| `maxTameablesPerPass` | `128` | Loaded tameables examined per scheduler pass. |

### Tame Regeneration

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

Listened events: `EntityDamageByEntityEvent` (queues a heal for the damaged pet), `EntityDeathEvent` (drops the pet's heal cooldown).

Menu lore: "HP/s".

Milestone: `challenge_taming_regen_1k` on `taming.health-regen.health-regened` at 1000, reward 400.

Per-pet heal cooldown is fixed at 8000 ms in code. Heal amount is `regenBase` plus level percent squared times `regenFactor`, capped by missing health.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `regenFactor` | `5` | Health points added to the heal at full level percent. |
| `regenBase` | `1` | Health points healed at level percent 0. |

### Pack Leader Aura

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

Listened events: `PlayerQuitEvent` (drops the owner's aura snapshot).

Menu lore: "Aura Radius", "Aura Strength".

Milestone: `challenge_taming_pack_72k` on `taming.pack-leader.buffed-ticks` at 72000, reward 400.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `8` | Aura radius in blocks at level percent 0. |
| `radiusFactor` | `14` | Extra aura radius in blocks at full level percent. |
| `maxAmplifier` | `2` | Highest potion amplifier the aura can reach; the applied amplifier is level percent times this, rounded down. |
| `effectTicks` | `80` | Duration in ticks of each speed and regeneration reapplication. |
| `maxOwnersPerPass` | `16` | Owners refreshed per scheduler tick, hard-capped at 16. |
| `maxTameablesPerPass` | `48` | Indexed tameables examined per scheduler tick, hard-capped at 48. |

### Beast Recall

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

Listened events: `PlayerInteractEvent` (sneak plus right-click with a lead in the main hand; the handler also receives cancelled events).

Menu lore: "Recall Radius", "Recall Cooldown", and "Hunger cost per recall" when `hungerCost` is above 0.

Milestones: `challenge_taming_recall_100` and `challenge_taming_recall_1k` on `taming.beast-recall.recalls` at 100 (reward 300) and 1000 (reward 1000).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `radiusBase` | `20` | Search radius in blocks at level percent 0. |
| `radiusFactor` | `38` | Extra search radius in blocks at full level percent. |
| `minRecallDistanceSquared` | `9.0` | Squared block distance a pet must exceed to be worth recalling (9.0 is 3 blocks). |
| `cooldownTicksBase` | `420` | Lead item cooldown in ticks at level percent 0. |
| `cooldownTicksFactor` | `280` | Ticks removed from that cooldown at full level percent, with a floor of 40 ticks. |
| `xpOnRecall` | `26` | Taming XP paid per successful recall. |
| `hungerCost` | `2` | Food points consumed per recall; 0 disables the cost. |
| `maxCandidatesPerActivation` | `16` | Nearby tameables inspected per recall, hard-capped at 32. |
| `maxAffectedPerActivation` | `1` | Pets recalled per activation, hard-capped at 1; 0 disables the effect. |

### Shared Pain

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

Listened events: `EntityDamageEvent` (any damage to the owner).

Menu lore: "Shared Damage", "Companion Health Floor".

Milestones: `challenge_taming_shared_500` and `challenge_taming_shared_5k` on `taming.shared-pain.damage-taken` at 500 (reward 400) and 5000 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `redirectPercentBase` | `0.2` | Fraction of incoming damage redirected at level percent 0, 0-1. |
| `redirectPercentFactor` | `0.35` | Extra redirected fraction at full level percent. |
| `maxRedirectPercent` | `0.7` | Hard ceiling on the redirected fraction, 0-1. |
| `petHealthFloorBase` | `1.0` | Health each pet keeps before it stops absorbing, at level percent 0. |
| `petHealthFloorFactor` | `1.0` | Extra health floor at full level percent. |
| `radiusBase` | `8.0` | Pet search radius in blocks at level percent 0. |
| `radiusFactor` | `8.0` | Extra search radius in blocks at full level percent. |
| `maxPets` | `8` | Pets included in one damage split, hard-capped at 16. |
| `xpPerRedirectedDamage` | `2.0` | Taming XP per point of damage the pack actually absorbed. |

### Mounted Tactics

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

- `PlayerMoveEvent` and `PlayerToggleSprintEvent` refresh the mounted state.
- `EntityMountEvent` and `EntityDismountEvent` (reflective handlers) start and clear it.
- `PlayerQuitEvent`, `PlayerDeathEvent`, and `PlayerGameModeChangeEvent` strip mount buffs.
- `EntityDeathEvent` counts mounted kills.
- `EntityDamageByEntityEvent` applies the damage bonus when you attack and the reduction when you are hit.

Menu lore: "Mounted Damage Bonus", "Mounted Damage Reduction".

Milestones: `challenge_taming_mounted_200` on `taming.mounted-tactics.mounted-kills` at 200 (reward 400), `challenge_taming_mounted_50k` on `taming.mounted-tactics.distance` at 50000 (reward 1000).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `mountedDamageBonusBase` | `0.08` | Fraction added to your mounted melee damage at level percent 0. |
| `mountedDamageBonusFactor` | `0.22` | Extra fraction at full level percent. |
| `maxMountedDamageBonus` | `0.35` | Ceiling on the mounted damage bonus, 0-1. |
| `mountedDamageReductionBase` | `0.06` | Fraction of incoming damage removed while mounted, at level percent 0. |
| `mountedDamageReductionFactor` | `0.2` | Extra fraction at full level percent. |
| `maxMountedDamageReduction` | `0.28` | Ceiling on the mounted damage reduction, 0-1. |
| `horseSpeedAmplifierBase` | `0` | Speed-effect amplifier the horse speed bonus is derived from, at level percent 0. |
| `horseSpeedAmplifierFactor` | `2` | Amplifier added at full level percent. |
| `striderSpeedAmplifierBase` | `0` | Same amplifier basis for striders, at level percent 0. |
| `striderSpeedAmplifierFactor` | `2` | Amplifier added at full level percent. |
| `horseJumpStrengthBonusBase` | `0.1` | Fraction added to horse jump strength at level percent 0. |
| `horseJumpStrengthBonusFactor` | `0.15` | Extra fraction at full level percent. |
| `pigResistanceAmplifierBase` | `0` | Resistance amplifier given to a pig rider at level percent 0. |
| `pigResistanceAmplifierFactor` | `1` | Amplifier added at full level percent. |
| `horseBaseHorizontalSpeed` | `0.3` | Reference horse speed in blocks per tick used to convert the amplifier into a movement-speed scalar. |
| `striderBaseHorizontalSpeed` | `0.24` | Same reference for striders. |
| `mountMaxHorizontalSpeed` | `0.78` | Hard ceiling in blocks per tick that the speed scalar is clamped against. |
| `horsePushBase` | `0.08` | Forward velocity added per sprinting move on a horse, at level percent 0. |
| `horsePushFactor` | `0.16` | Extra forward velocity at full level percent. |
| `pigPushBase` | `0.05` | Forward velocity added per sprinting move on a pig, at level percent 0. |
| `pigPushFactor` | `0.12` | Extra forward velocity at full level percent. |
| `xpPerMountedDamage` | `1.5` | Taming XP per point of damage you deal while mounted. |

### Fetch

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

Listened events: `PlayerQuitEvent` (aborts that owner's walking fetches).

Menu lore: "Fetch Range", "Carry Chance".

Milestones: `challenge_taming_fetch_1k` and `challenge_taming_fetch_10k` on `taming.fetch.items-fetched` at 1000 (reward 400) and 10000 (reward 1500).

Hard limits in code: pickup range 1.5 blocks, delivery range 2 blocks, and a walked fetch is abandoned if the wolf ends up more than 11 blocks from you, because vanilla yanks pets back at that distance.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `fetchRangeBase` | `6.0` | Item search radius in blocks at level percent 0. |
| `fetchRangeFactor` | `10.0` | Extra search radius in blocks at full level percent. |
| `carryRateBase` | `0.35` | Chance per eligible drop that it gets fetched this pass, at level percent 0, 0-1. |
| `carryRateFactor` | `0.5` | Extra chance at full level percent. |
| `maxCarryRate` | `0.9` | Ceiling on that chance, 0-1. |
| `wolfSearchRadius` | `24.0` | Radius in blocks searched for your tamed wolves. |
| `xpPerItemFetched` | `4` | Taming XP per delivered item. |
| `maxWolves` | `6` | Tamed wolves counted around the owner, hard-capped at 12. |
| `maxCarryPerTick` | `4` | Drops handled per owner per pass, hard-capped at 8. |
| `realFetch` | `true` | True walks a wolf to the drop and back; false pulls every drop straight to you. |
| `fetchWalkSpeed` | `1.15` | Pathfinding speed multiplier while walking a fetch, clamped to 0.1 - 4.0. |
| `pathfindRadius` | `9.0` | Farthest drop a wolf will walk to, in blocks, clamped internally to 11. |
| `fetchDeadlineMillis` | `9000` | Milliseconds a walked fetch may run before it is abandoned, clamped to 1000 - 60000. |
| `maintenanceIntervalTicks` | `5` | Ticks between re-issuing the wolf its path, clamped to 1 - 20. |

### Alpha's Command

| Property | Default |
|----------|---------|
| Class | `TamingAlphasCommand` |
| Icon | `BONE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.55 |
| Tick interval (ms) | 1000 (default; no tick work) |
| Config file | `plugins/Adapt/adapt/adaptations/tame-alphas-command.toml` |

Listened events: `PlayerInteractEvent` (sneak plus left-click with a bone, raycast for the target), `EntityDamageByEntityEvent` (sneak melee with a bone, which cancels the hit and commands instead), `PlayerQuitEvent` (clears glow and focus state).

Menu lore: "Command Range", "Focus Duration".

Milestones: `challenge_taming_command_250` and `challenge_taming_command_2500` on `taming.alphas-command.commands` at 250 (reward 400) and 2500 (reward 1500).

Focus is re-asserted every 10 ticks and revalidated against PVP/PVE policy each time. Focus buffs are attack damage of 3.0 x (amplifier + 1) and a movement speed scalar of 0.2 x (amplifier + 1).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `commandRangeBase` | `8.0` | Raycast and pet-gather radius in blocks at level percent 0. |
| `commandRangeFactor` | `12.0` | Extra radius in blocks at full level percent. |
| `focusTicksBase` | `60` | Focus duration in ticks at level percent 0. |
| `focusTicksFactor` | `120` | Extra focus ticks at full level percent, with a floor of 20 ticks. |
| `focusSpeedAmplifier` | `0` | Amplifier for the attack-damage and movement-speed buff given to commanded pets. |
| `commandCooldownMillis` | `3000` | Milliseconds between commands for one player. |
| `xpPerCommand` | `12` | Taming XP per successful command. |
| `maxPets` | `12` | Pets commanded per activation, hard-capped at 24. |

### Guardian Instinct

| Property | Default |
|----------|---------|
| Class | `TamingGuardianInstinct` |
| Icon | `SHIELD` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1000 (default; no tick work) |
| Config file | `plugins/Adapt/adapt/adaptations/tame-guardian-instinct.toml` |

Listened events: `EntityDamageByEntityEvent` (projectile damage to the owner only).

Menu lore: "Intercept Chance", "Pet Damage Reduction".

Milestones: `challenge_taming_guardian_250` and `challenge_taming_guardian_2500` on `taming.guardian-instinct.intercepts` at 250 (reward 400) and 2500 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `interceptChanceBase` | `0.35` | Chance per incoming projectile that a pet intercepts, at level percent 0, 0-1. |
| `interceptChanceFactor` | `0.45` | Extra chance at full level percent. |
| `maxInterceptChance` | `0.8` | Ceiling on the intercept chance, 0-1. |
| `petReductionBase` | `0.4` | Fraction of the intercepted damage removed before the pet takes it, at level percent 0. |
| `petReductionFactor` | `0.35` | Extra fraction at full level percent. |
| `maxPetReduction` | `0.7` | Ceiling on that reduction, 0-1. |
| `radiusBase` | `8.0` | Radius in blocks searched for an intercepting pet, at level percent 0. |
| `radiusFactor` | `8.0` | Extra radius in blocks at full level percent. |
| `leapStrength` | `0.8` | Velocity applied to the pet as it lunges toward you. |
| `cooldownMillis` | `1200` | Milliseconds between intercepts for one player. |
| `xpPerDamageIntercepted` | `2.0` | Taming XP per point of the original incoming damage. |

### Stable Hand

| Property | Default |
|----------|---------|
| Class | `TamingStableHand` |
| Icon | `SADDLE` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 5 |
| Cost factor | 0.5 |
| Tick interval (ms) | 1000 (default; no tick work) |
| Config file | `plugins/Adapt/adapt/adaptations/tame-stable-hand.toml` |

Listened events: `EntityTameEvent` (applies the bias to the tamed animal), `EntityBreedEvent` (applies it to the offspring one tick later).

Menu lore: "Attribute Bias", "Safe Fall Blocks".

Milestones: `challenge_taming_stable_100` and `challenge_taming_stable_1k` on `taming.stable-hand.animals-shaped` at 100 (reward 400) and 1000 (reward 1500).

The bias is applied as a scalar to movement speed, jump strength, and max health, and as a flat block bonus to safe fall distance equal to bias x 10.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `biasBase` | `0.1` | Attribute bias at level percent 0, as a fraction. |
| `biasFactor` | `0.2` | Extra bias at full level percent. |
| `maxBias` | `0.3` | Ceiling on the bias, 0-1. |
| `xpPerAnimal` | `20` | Taming XP per animal that receives the bias. |

### Wild Empathy

| Property | Default |
|----------|---------|
| Class | `TamingWildEmpathy` |
| Icon | `DANDELION` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Tick interval (ms) | 1000 (default; no tick work) |
| Config file | `plugins/Adapt/adapt/adaptations/tame-wild-empathy.toml` |

Listened events: `PlayerInteractEntityEvent` (right-click an untamed tameable with its taming food), `EntityTargetLivingEntityEvent` (a neutral mob targeting you).

Menu lore: "Extra Taming Odds", "Anger Resistance".

Milestones: `challenge_taming_empathy_100` and `challenge_taming_empathy_1k` on `taming.wild-empathy.tames` at 100 (reward 400) and 1000 (reward 1500). Resisted anger is also counted on `taming.wild-empathy.angers-resisted`, which has no milestone.

Taming foods in code: `BONE` for wolves; `COD` and `SALMON` for cats and ocelots; `WHEAT_SEEDS`, `MELON_SEEDS`, `PUMPKIN_SEEDS`, `BEETROOT_SEEDS`, `TORCHFLOWER_SEEDS`, and `PITCHER_POD` for parrots. Pacifiable neutrals: wolves, bees, polar bears, llamas, pandas, goats, and only while untamed.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `tamingOddsBase` | `0.25` | Chance per feed that the animal is tamed outright, at level percent 0, 0-1. |
| `tamingOddsFactor` | `0.4` | Extra chance at full level percent. |
| `maxTamingOdds` | `0.6` | Ceiling on the taming chance, 0-1. |
| `angerResistanceBase` | `0.3` | Chance per targeting attempt that the mob is calmed, at level percent 0, 0-1. |
| `angerResistanceFactor` | `0.45` | Extra chance at full level percent. |
| `maxAngerResistance` | `0.75` | Ceiling on the anger resistance, 0-1. |
| `xpPerTame` | `60` | Taming XP per forced tame. |

### Battle Bond

| Property | Default |
|----------|---------|
| Class | `TamingBattleBond` |
| Icon | `DIAMOND_SWORD` |
| Max level | 5 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Tick interval (ms) | 1000 (default; no tick work) |
| Config file | `plugins/Adapt/adapt/adaptations/tame-battle-bond.toml` |

Listened events: `EntityDeathEvent` (the killing blow came from a tameable).

Menu lore: "Buff Tier", "Buff Duration".

Milestones: `challenge_taming_bond_250` and `challenge_taming_bond_2500` on `taming.battle-bond.kills` at 250 (reward 400) and 2500 (reward 1500).

Buffs applied are Speed, Regeneration, and the strength effect where the server exposes it. The lore line shows tier as amplifier + 1, so the displayed tier 1 is potion amplifier 0. Candidate scan stops after 96 nearby entities.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxBuffTier` | `1` | Highest potion amplifier reachable; the applied amplifier is level percent times (this + 1), rounded down and clamped here. |
| `buffTicksBase` | `80` | Buff duration in ticks at level percent 0. |
| `buffTicksFactor` | `120` | Extra duration in ticks at full level percent, with a floor of 20 ticks. |
| `packRadius` | `16` | Radius in blocks searched for pack members to buff. |
| `xpPerKill` | `10` | Taming XP per Battle Bond trigger. |
| `maxPack` | `12` | Pack members buffed per kill, hard-capped at 24. |
| `glowTicks` | `30` | Ticks bonded pets glow, clamped to 10 - 60. |

### Last Breath

| Property | Default |
|----------|---------|
| Class | `TamingLastBreath` |
| Icon | `TOTEM_OF_UNDYING` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1000 (default; no tick work) |
| Config file | `plugins/Adapt/adapt/adaptations/tame-last-breath.toml` |

Listened events: `EntityDamageEvent` twice. `onProtectedWindow` runs at LOWEST and cancels all damage to a pet inside its invulnerability window; `on` runs at HIGHEST and performs the save when the hit would be lethal.

Menu lore: "Per-Pet Cooldown", "Invulnerability".

Milestones: `challenge_taming_lastbreath_50` and `challenge_taming_lastbreath_500` on `taming.last-breath.saves` at 50 (reward 400) and 500 (reward 1500).

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `cooldownMillisBase` | `300000` | Per-pet cooldown in milliseconds at level percent 0. |
| `cooldownMillisFactor` | `180000` | Milliseconds removed from that cooldown at full level percent. |
| `minCooldownMillis` | `60000` | Floor on the per-pet cooldown in milliseconds. |
| `invulnTicks` | `60` | Ticks of invulnerability after a save, with a floor of 10. |
| `xpPerSave` | `40` | Taming XP per save. |

### Support classes (not player adaptations)

- `TameableOwnershipIndex` tracks loaded tameable entities, ownership changes, lifecycle generations, and bounded Folia discovery passes. Tame Health, Tame Damage, and Pack Leader Aura all read from it.

## See also

- `02 - Concepts.md` for levels, knowledge, and how adaptations are learned.
- `03 - Player Usage.md` for the Adapt menu and general play.
- `10 - Skills Catalog.md` for the full skill list.
- `04 - Commands & Permissions.md` for the `adapt.use` nodes.
