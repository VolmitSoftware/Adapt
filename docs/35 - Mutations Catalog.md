# Mutations Catalog

Experimental Mutations are opt-in passive traits expressed in two equipment slots. Each type has two domains, a material icon, a benefit, a drawback, a perfect-adaptation softening of the drawback, a short how-to, and a `pvpRelevant` catalog flag used by the GUI.

| Id | Display | Domains | Icon | PvP relevant |
|----|---------|---------|------|--------------|
| `gale-lung` | Gale Lung | BODY + HUNT | `FEATHER` | true |
| `bastion-spine` | Bastion Spine | BODY + INDUSTRY | `DEEPSLATE_BRICKS` | true |
| `verdant-molt` | Verdant Molt | BODY + WILD | `MOSS_BLOCK` | false |
| `temperbound` | Temperbound | BODY + CRAFT | `ANVIL` | false |
| `paradox-scar` | Paradox Scar | BODY + ANOMALY | `RECOVERY_COMPASS` | true |
| `arsenal-cortex` | Arsenal Cortex | HUNT + INDUSTRY | `SMITHING_TABLE` | true |
| `packmind` | Packmind | HUNT + WILD | `LEAD` | true |
| `trophy-crucible` | Trophy Crucible | HUNT + CRAFT | `SKELETON_SKULL` | true |
| `umbral-echo` | Umbral Echo | HUNT + ANOMALY | `ECHO_SHARD` | true |
| `living-lattice` | Living Lattice | INDUSTRY + WILD | `MANGROVE_ROOTS` | false |
| `masterwork-bond` | Masterwork Bond | INDUSTRY + CRAFT | `NETHERITE_PICKAXE` | false |
| `deepblood` | Deepblood | INDUSTRY + ANOMALY | `DEEPSLATE_DIAMOND_ORE` | false |
| `mycelial-nerve` | Mycelial Nerve | WILD + CRAFT | `SPORE_BLOSSOM` | false |
| `gravebloom` | Gravebloom | WILD + ANOMALY | `WITHER_ROSE` | false |
| `resonant-formula` | Resonant Formula | CRAFT + ANOMALY | `ENCHANTED_BOOK` | true |

### Gale Lung (`gale-lung`)

Enum `GALE_LUNG`. Domains `BODY` and `HUNT`. Icon `FEATHER`. PvP relevant: `true`.

- **Benefit:** Moving fills Momentum. At full Momentum, your next hit pushes or repositions the target.
- **Drawback:** Enemies push you harder while Momentum is full. Blocking empties it.
- **Perfect Adaptation:** Enemies no longer push you harder. Blocking still empties Momentum.
- **FX cue:** Teal wind gathers around your feet and weapon.
- **How to use:** Keep moving to fill Momentum, then land a hit.
- **Permission:** `adapt.use.mutation.gale-lung`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `maximumMomentum` | `100` | 1–100 |
| `sprintMomentumPerBlock` | `8` | 0–`maximumMomentum` |
| `airborneMomentumPerBlock` | `4` | 0–`maximumMomentum` |
| `stationaryVentMillis` | `1250` | 100–31,536,000,000 ms |
| `burdenKnockbackMultiplier` | `1.35` | 1–2 |
| `meleeFlankDistance` | `1.5` | 0–3 blocks |
| `projectileDisplacement` | `0.45` | 0–1.5 |

### Bastion Spine (`bastion-spine`)

Enum `BASTION_SPINE`. Domains `BODY` and `INDUSTRY`. Icon `DEEPSLATE_BRICKS`. PvP relevant: `true`.

- **Benefit:** Standing still builds Stability. Your next shield, axe, or unarmed hit sends out a short push.
- **Drawback:** While braced, you cannot sprint or jump. A hit from behind breaks the stance.
- **Perfect Adaptation:** You can sprint and jump while braced. Hits from behind can still break it.
- **FX cue:** Stone ribs and bright cracks show your stored force.
- **How to use:** Stand still on the ground, then strike with a shield, axe, or empty hand.
- **Permission:** `adapt.use.mutation.bastion-spine`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `anchorChargeMillis` | `1500` | 250–31,536,000,000 ms |
| `maximumStability` | `8` | 1–8 |
| `stabilityPerDamage` | `0.5` | 0.01–4 |
| `waveRange` | `5` | 1–12 blocks |
| `waveAngleDegrees` | `90` | 15–180 degrees |
| `maximumVelocity` | `0.85` | 0.1–1.5 |
| `maximumTargets` | `12` | 1–12 |

### Verdant Molt (`verdant-molt`)

Enum `VERDANT_MOLT`. Domains `BODY` and `WILD`. Icon `MOSS_BLOCK`. PvP relevant: `false`.

- **Benefit:** Sneak without moving on natural ground to clear harmful effects.
- **Drawback:** It also clears helpful effects, costs hunger, and briefly blocks new effects.
- **Perfect Adaptation:** Helpful effects and hunger are no longer lost. The short effect block stays.
- **FX cue:** Leaves, scales, or spores burst away from you.
- **How to use:** Hold sneak and stay still on natural ground.
- **Permission:** `adapt.use.mutation.verdant-molt`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `chargeTicks` | `50` | 10–72,000 ticks |
| `cooldownMillis` | `90000` | 0–31,536,000,000 ms |
| `saturationCost` | `6` | 0–20 |
| `recoveryTicks` | `40` | 1–72,000 ticks |
| `maximumEffects` | `32` | 1–32 |

### Temperbound (`temperbound`)

Enum `TEMPERBOUND`. Domains `BODY` and `CRAFT`. Icon `ANVIL`. PvP relevant: `false`.

- **Benefit:** Link one armor set so its pieces share durability. A piece that would break becomes Cracked instead.
- **Drawback:** Removing or swapping a linked piece disables the set for a short time.
- **Perfect Adaptation:** Swapping armor no longer disables the set. Only one set can stay linked.
- **FX cue:** Glowing lines connect the linked armor pieces.
- **How to use:** At an Adapt bookshelf, wear four crafted armor pieces and choose Link Current Armor.
- **Permission:** `adapt.use.mutation.temperbound`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `rejectionMillis` | `30000` | 0–31,536,000,000 ms |

### Paradox Scar (`paradox-scar`)

Enum `PARADOX_SCAR`. Domains `BODY` and `ANOMALY`. Icon `RECOVERY_COMPASS`. PvP relevant: `true`.

- **Benefit:** A long move or teleport leaves a return point. You can jump back to it once.
- **Drawback:** The return point is visible, blocks another one from forming, and enemies can break it.
- **Perfect Adaptation:** It no longer blocks other Mutation return effects. Enemies can still break it.
- **FX cue:** A bright afterimage marks the return point.
- **How to use:** After a long move or teleport, sneak and swap hands to return.
- **Permission:** `adapt.use.mutation.paradox-scar`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `minimumDistance` | `8` | 1–64 blocks |
| `echoLifetimeMillis` | `12000` | 1,000–31,536,000,000 ms |
| `maximumReturnDistance` | `64` | `minimumDistance`–128 blocks |
| `hostileCollapseTicks` | `60` | 1–72,000 ticks |

### Arsenal Cortex (`arsenal-cortex`)

Enum `ARSENAL_CORTEX`. Domains `HUNT` and `INDUSTRY`. Icon `SMITHING_TABLE`. PvP relevant: `true`.

- **Benefit:** Changing weapon or tool types between hits carries one control effect into the next hit.
- **Drawback:** Using the same type twice locks the combo for a short time.
- **Perfect Adaptation:** Repeating a type no longer locks the combo. Switching types is still needed to build it.
- **FX cue:** A changing symbol spins around the held item.
- **How to use:** Land hits with different weapon or tool types.
- **Permission:** `adapt.use.mutation.arsenal-cortex`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `chainTimeoutMillis` | `5000` | 250–31,536,000,000 ms |
| `maximumChain` | `4` | 2–4 steps |
| `dullnessMillis` | `3000` | 0–31,536,000,000 ms |

### Packmind (`packmind`)

Enum `PACKMIND`. Domains `HUNT` and `WILD`. Icon `LEAD`. PvP relevant: `true`.

- **Benefit:** Your first hit marks a target. Pets and opted-in allies build Tempo by hitting it too.
- **Drawback:** Your damage is lower until someone else joins in. Solo Tempo quickly fades.
- **Perfect Adaptation:** Your damage is no longer lowered while waiting. Tempo still needs another participant.
- **FX cue:** Amber lines connect the group to the marked target.
- **How to use:** Hit a target, then have a pet or opted-in ally attack it too.
- **Permission:** `adapt.use.mutation.packmind`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `quarryMillis` | `20000` | 1,000–31,536,000,000 ms |
| `participationRange` | `16` | 2–32 blocks |
| `maximumTempo` | `6` | 1–6 |
| `maximumMembers` | `8` | 1–8 |
| `waitingDamageFactor` | `0.8` | 0.1–1 |

### Trophy Crucible (`trophy-crucible`)

Enum `TROPHY_CRUCIBLE`. Domains `HUNT` and `CRAFT`. Icon `SKELETON_SKULL`. PvP relevant: `true`.

- **Benefit:** Natural mobs can drop trophies. Use one to prepare a control effect against that mob type.
- **Drawback:** That mob type spots you more easily and sees through Mutation stealth.
- **Perfect Adaptation:** The extra detection is removed. You can still store only one trophy effect.
- **FX cue:** A mask showing the stored mob type appears behind you.
- **How to use:** Sneak-right-click a crafting table while holding a natural trophy.
- **Permission:** `adapt.use.mutation.trophy-crucible`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `imprintLifetimeMillis` | `1800000` | 1,000–31,536,000,000 ms |
| `recognitionRange` | `16` | 2–32 blocks |

### Umbral Echo (`umbral-echo`)

Enum `UMBRAL_ECHO`. Domains `HUNT` and `ANOMALY`. Icon `ECHO_SHARD`. PvP relevant: `true`.

- **Benefit:** Attack from a new angle or with a new weapon type to repeat a weaker control effect after a delay.
- **Drawback:** Repeating the same approach reveals you and briefly shuts down stealth.
- **Perfect Adaptation:** Repeating an approach no longer reveals you. New angles or weapon types are still required.
- **FX cue:** A dark purple afterimage repeats the control effect.
- **How to use:** Change your attack angle or weapon type between hits.
- **Permission:** `adapt.use.mutation.umbral-echo`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `angleBucketDegrees` | `45` | 15–180 degrees |
| `techniqueMemoryMillis` | `5000` | 250–31,536,000,000 ms |
| `echoDelayTicks` | `8` | 1–72,000 ticks |
| `exposureTicks` | `60` | 1–72,000 ticks |
| `maximumTargetMemories` | `8` | 1–8 |

### Living Lattice (`living-lattice`)

Enum `LIVING_LATTICE`. Domains `INDUSTRY` and `WILD`. Icon `MANGROVE_ROOTS`. PvP relevant: `false`.

- **Benefit:** Harvesting and replanting earns Root Charge. Spend it to grow a short temporary path.
- **Drawback:** Fire and lava can wipe your charge, and a forced collapse costs hunger.
- **Perfect Adaptation:** Fire and lava no longer wipe charge. The paths still disappear.
- **FX cue:** Green roots turn brown before the path disappears.
- **How to use:** Harvest and replant, then sneak-right-click while holding a sapling.
- **Permission:** `adapt.use.mutation.living-lattice`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `maximumRootCharge` | `12` | 1–12 |
| `pathLength` | `5` | 1–8 blocks |
| `blockLifetimeMillis` | `15000` | 1,000–31,536,000,000 ms |
| `collapseLockMillis` | `4000` | 0–31,536,000,000 ms |
| `maximumBlocks` | `16` | 1–16 |
| `maximumStructures` | `3` | 1–3 |

### Masterwork Bond (`masterwork-bond`)

Enum `MASTERWORK_BOND`. Domains `INDUSTRY` and `CRAFT`. Icon `NETHERITE_PICKAXE`. PvP relevant: `false`.

- **Benefit:** Bind one tool you crafted. It stops at one durability instead of breaking and must be repaired before use.
- **Drawback:** Only the bound tool gets protection. If it is lost, you must wait before binding another.
- **Perfect Adaptation:** Other tools work normally with Mutation effects. Only the Masterwork avoids breaking.
- **FX cue:** Runes on the tool crack as it nears breaking.
- **How to use:** At an Adapt bookshelf, hold a tool you crafted and choose Bind Held Tool.
- **Permission:** `adapt.use.mutation.masterwork-bond`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `abandonCooldownMillis` | `86400000` | 0–31,536,000,000 ms |

### Deepblood (`deepblood`)

Enum `DEEPBLOOD`. Domains `INDUSTRY` and `ANOMALY`. Icon `DEEPSLATE_DIAMOND_ORE`. PvP relevant: `false`.

- **Benefit:** Mining natural blocks deep underground builds Deep Charge. It powers healing and saves one bound tool.
- **Drawback:** Underground healing spends Deep Charge, and the charge slowly drains above ground.
- **Perfect Adaptation:** Underground healing works at zero charge. Saving the bound tool still costs charge.
- **FX cue:** Red cracks spread across you and the bound tool as charge builds.
- **How to use:** Mine natural deep blocks and bind one tool at an Adapt bookshelf.
- **Permission:** `adapt.use.mutation.deepblood`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `maximumDepthY` | `16` | -2,048–2,048 |
| `ichorPerBlock` | `1` | 0–100 |
| `maximumIchor` | `100` | 1–100 |
| `regenerationCost` | `4` | 0–`maximumIchor` |
| `toolPreservationCost` | `25` | 0–`maximumIchor` |
| `aboveGroundHalfLifeMillis` | `300000` | 1,000–31,536,000,000 ms |

### Mycelial Nerve (`mycelial-nerve`)

Enum `MYCELIAL_NERVE`. Domains `WILD` and `CRAFT`. Icon `SPORE_BLOSSOM`. PvP relevant: `false`.

- **Benefit:** Helpful effects you give yourself spread in weaker form to nearby opted-in players and pets.
- **Drawback:** Your own effect lasts less time, and taking fire damage breaks the link.
- **Perfect Adaptation:** Your effect keeps its full time and fire no longer breaks the link.
- **FX cue:** Spore trails carry copied effects to nearby allies.
- **How to use:** Give yourself a helpful effect near opted-in players or owned pets.
- **Permission:** `adapt.use.mutation.mycelial-nerve`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `range` | `16` | 2–32 blocks |
| `copiedDurationFactor` | `0.5` | 0.05–1 |
| `rootDurationFactor` | `0.75` | 0.05–1 |
| `maximumRecipients` | `8` | 1–8 |
| `reconnectLockMillis` | `5000` | 0–31,536,000,000 ms |

### Gravebloom (`gravebloom`)

Enum `GRAVEBLOOM`. Domains `WILD` and `ANOMALY`. Icon `WITHER_ROSE`. PvP relevant: `false`.

- **Benefit:** Killing a natural hostile mob can create a short-lived bloom that grows crops and heals your pets.
- **Drawback:** Active blooms weaken your natural healing, and older blooms attract hostile mobs.
- **Perfect Adaptation:** Blooms no longer weaken your healing. They still disappear after a short time.
- **FX cue:** Pale flowers and soul particles rise from the death spot.
- **How to use:** Kill a natural hostile mob on natural ground.
- **Permission:** `adapt.use.mutation.gravebloom`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `lifetimeMillis` | `20000` | 1,000–31,536,000,000 ms |
| `radius` | `6` | 1–12 blocks |
| `maximumBlooms` | `3` | 1–3 |
| `regenerationFactor` | `0.5` | 0–1 |
| `pulseTicks` | `20` | 5–72,000 ticks |
| `maximumCrops` | `16` | 1–16 |
| `maximumAnimals` | `8` | 1–8 |

### Resonant Formula (`resonant-formula`)

Enum `RESONANT_FORMULA`. Domains `CRAFT` and `ANOMALY`. Icon `ENCHANTED_BOOK`. PvP relevant: `true`.

- **Benefit:** Craft, brew, and enchant once each to prepare a combo. Your next non-damaging Anomaly effect repeats at half strength.
- **Drawback:** Repeating the same prep step breaks the combo and removes your oldest helpful effect.
- **Perfect Adaptation:** Repeating a step no longer breaks the combo. You still need all three different steps.
- **FX cue:** Three symbols join together when the combo is ready.
- **How to use:** Craft, brew, and enchant once each, in any order.
- **Permission:** `adapt.use.mutation.resonant-formula`

Type-specific config:

| Key | Default | Normalized range |
|-----|---------|------------------|
| `sigilLifetimeMillis` | `600000` | 1,000–31,536,000,000 ms |
| `collapseLockMillis` | `30000` | 0–31,536,000,000 ms |
| `echoFactor` | `0.5` | 0.05–1 |
| `echoDelayTicks` | `10` | 1–72,000 ticks |

## See also

- `34 - Mutations Overview.md`
- `04 - Commands & Permissions.md`
- `48 - API - Mutations.md`
