# Mutations Overview

Mutations are an experimental trait system that sits beside skill adaptations. A player wears up to two traits, each one a package with a real upside and a real downside, and lives with that pair until they are allowed to change it. The feature ships off, so none of it exists on your server until you set `enabled = true` in `plugins/Adapt/adapt/mutations.toml`.

Where adaptations are things you learn and spend knowledge on, a Mutation is a body you commit to. Gale Lung makes you fast and dangerous while moving, and also makes every hit you take shove you further. Bastion Spine turns standing still into a weapon, and takes your sprint away while you brace. All fifteen types work this way: a benefit, a burden, and one clear thing you do to trigger it. At master level 200 the burdens stop applying, which is the reward for sticking with a pair.

The commitment is enforced with time. Slots unlock at master level 25 and 50, changing one needs a trip to an Adapt bookshelf, and each change puts a ten-minute cooldown on that slot. Because Mutations touch combat, movement, blocks, and other players, nearly everything is tunable per type and per world: conflict lists, a PvP switch, particle and sound switches, and world blacklists.

## Turning it on

1. Start the server once with Adapt installed so `plugins/Adapt/adapt/mutations.toml` is written.
2. Set `enabled = true` and save. The config watcher picks it up and reconciles every online player, and `/adapt mutations reload` does the same on demand.
3. Grant `adapt.mutations` to players, plus `adapt.use.mutation.<id>` for each type you want available. Give `adapt.mutations.admin` to staff.

Adapt logs which way the switch is set on boot, so check the console if you are unsure.

## How a player gets a Mutation

Qualification is the first gate. Every type belongs to two of the six domains (Body, Hunt, Industry, Wild, Craft, Anomaly), and each domain is a list of skills. To qualify you need at least one learned adaptation from a skill in each of the type's two domains, at `minimumAdaptationLevel` or higher, with the skill and adaptation both enabled and both use permissions held. A player who has never touched Hunt skills cannot wear Gale Lung at any level. Slots come next: slot one at master level 25, slot two at 50, each announced with a title and a sound.

To equip, the player right-clicks the Adapt activator block (a bookshelf by default), which opens the Adapt menu and authorizes Mutation editing for the next minute. `/adapt mutations menu` then shows a page of cards, one per type, with its benefit, burden, state, and the reason for that state. Clicking a card equips it.

A non-admin equip or clear puts a ten-minute cooldown on that slot, and dealing or taking damage blocks slot changes for ten seconds. Admin commands skip those gates along with the permission, world, level, and qualification checks, but still refuse duplicates and configured conflicts. Mutation effects themselves only run in survival and adventure mode; creative and spectator are ignored by the runtime.

## Perfect adaptation and discovery

At master level 200 an active Mutation keeps its benefit and drops its burden. Each catalog entry says what changes. Admins can force it either way with `/adapt mutations perfect-test on|off|clear`, but that override lives in memory only and is dropped when the player leaves or the config reloads.

Discovery is a per-player record of which types a player has actually worn. Equipping marks a type discovered and the menu labels each card Discovered or Undiscovered. It gates nothing. Admins can set it with `/adapt mutations discover <id> <true|false> [player]`.

## States

Every type gets a state and a reason string, and the menu prints that reason on the card, so "why can I not use this" is answerable from the menu alone. The state that trips people up is `DORMANT`: the type is slotted but stopped, usually by a locked slot, a blacklisted world, a lost permission, or a qualification the player no longer meets. All seven states are listed in Reference.

## Cooperative effects

Some Mutations reach other players. Packmind builds Tempo when allies help on your marked target, and Mycelial Nerve spreads your own good potion effects to people near you. Neither ever touches a player who has not opted in with the menu toggle or `/adapt mutations cooperative on|off|toggle`.

On top of that opt-in, `cooperativeConsentMode` decides which opted-in players count. `EXPLICIT`, the default, accepts any of them. `PARTY` also requires the recipient to share the initiator's scoreboard and team name, which is what Adapt reads as a party. `FRIEND` has no friendship provider behind it, so it rejects everyone and behaves like `DISABLED`.

## Slot pairs that overlap

Two different types can always be worn together unless a profile's `conflicts` list rejects the pair. Seven pairs do compete for the same runtime resource, though, because both want to move you, both want to save your item from breaking, or both want to place temporary blocks. Each of those has a fixed resolution rule, listed in Reference.

## GUI and commands

`/adapt mutations menu` opens the card GUI and needs `adapt.mutations` plus the feature enabled. `view` and `cooperative` are player-facing; `equip`, `clear`, `discover`, `cooldown`, `refresh`, `slot-override`, `reset`, `perfect-test`, and `reload` all require `adapt.mutations.admin`. `slot-override` forces a slot open or shut for one player regardless of their level, and unlike `perfect-test` it is saved with their data. Full syntax is in `04 - Commands & Permissions.md`, and placeholders are in `47 - API - PlaceholderAPI.md`.

## Reference

### Requirements

| Requirement | Detail |
|-------------|--------|
| Config | `adapt/mutations.toml`, `enabled = true` |
| Player permission | `adapt.mutations`, plus `adapt.use.mutation.<id>` per type |
| Admin permission | `adapt.mutations.admin` for equip, clear, discover, cooldown, refresh, slot-override, reset, perfect-test, reload, and viewing another player |
| Editing gate | Non-admin slot changes need a bookshelf authorization token, taken from the last Adapt activator block click, held for `bookshelfTokenMillis` within `bookshelfMaximumDistance` in the same world |
| Game mode | Runtime effects skip creative and spectator |

### Core config defaults (`MutationConfig`)

| Key | Default | What it does |
|-----|---------|--------------|
| `enabled` | `false` | Master switch. Saved slot choices stay on file while it is off. |
| `slotOneUnlockLevel` | `25` | Master level needed before slot 1 can hold a Mutation |
| `slotTwoUnlockLevel` | `50` | Master level needed before slot 2 can hold a Mutation |
| `perfectAdaptationLevel` | `200` | Master level at which burdens stop applying |
| `perfectAdaptationEnabled` | `true` | Whether reaching that level grants perfect adaptation at all |
| `minimumAdaptationLevel` | `1` | Level a learned adaptation must reach to count toward a domain |
| `switchCooldownMillis` | `600000` | Wait on a slot after a player equips or clears it, in milliseconds |
| `combatLockMillis` | `10000` | How long after dealing or taking damage slot changes are refused, in milliseconds |
| `switchingEnabled` | `true` | Whether players may change slots at all. `false` leaves admin commands as the only route. |
| `permanentSelection` | `false` | When true, a filled slot can never be changed by the player again |
| `pvpEnabled` | `true` | Global switch for Mutation control effects between players |
| `cooperativeEffectsEnabled` | `true` | Global switch for effects that reach other players and pets |
| `cooperativeConsentMode` | `EXPLICIT` | Which opted-in recipients count: `EXPLICIT`, `PARTY`, `FRIEND`, `DISABLED` |
| `bookshelfTokenMillis` | `60000` | How long one activator-block click keeps slot editing open, in milliseconds |
| `bookshelfMaximumDistance` | `8` | Blocks the player may move from that block while editing |
| `particlesEnabled` | `true` | Global particle switch for Mutation effects |
| `soundsEnabled` | `true` | Global sound switch for Mutation effects |
| `worldBlacklist` | `[]` | World keys where no Mutation works |
| `domainMembership` | Table below | Skill ids assigned to each domain |

Normalization runs on load and after every reload. It enforces `slotOneUnlockLevel >= 0`, `slotTwoUnlockLevel >= slotOneUnlockLevel`, `perfectAdaptationLevel >= slotTwoUnlockLevel`, and `minimumAdaptationLevel >= 1`. Switch and combat durations clamp to 0 through 31,536,000,000 ms, the bookshelf token to 1,000 through 300,000 ms, and bookshelf distance to 2 through 32 blocks. World lists keep at most 256 normalized world keys, and each domain list keeps at most 64 unique lowercase skill ids.

### Per-type profile keys

Every type has these keys under its camel-case TOML section, such as `galeLung` or `resonantFormula`. Type-specific keys and their clamps are listed per entry in `35 - Mutations Catalog.md`.

| Key | Default | Normalization |
|-----|---------|---------------|
| `enabled` | `true` | Boolean. Turns this one type off while the feature stays on. |
| `pvpEnabled` | `true` | Boolean. Also requires global `pvpEnabled`. |
| `particlesEnabled` | `true` | Boolean. Also requires global `particlesEnabled`. |
| `soundsEnabled` | `true` | Boolean. Also requires global `soundsEnabled`. |
| `worldBlacklist` | `[]` | At most 256 normalized world keys |
| `conflicts` | `[]` | At most 15 unique lowercase Mutation ids that cannot share a loadout with this one |

### Domain membership defaults

| Domain | Default skill membership |
|--------|--------------------------|
| BODY | agility, blocking, unarmed, kinetics |
| HUNT | swords, ranged, hunter, stealth |
| INDUSTRY | architect, axes, excavation, pickaxe |
| WILD | herbalism, taming, seaborne |
| CRAFT | crafting, brewing, enchanting, discovery |
| ANOMALY | nether, rift, chronos, tragoul |

At most 64 candidate adaptations per domain are scanned per player.

### Mutation states

| State | Meaning |
|-------|---------|
| `LOCKED` | Not selected, and slot one is not unlocked yet |
| `AVAILABLE` | Qualified and permitted, ready to equip |
| `EXPRESSED` | Selected and running |
| `DORMANT` | Selected but not running: feature off, type off, slot locked, missing permission, blocked world, or no longer qualified |
| `DISABLED` | Not selected, and either the feature or this type is off |
| `RESTRICTED` | Not selected, and blocked by permission, world, or qualification |
| `CONFLICT` | The same id sits in both slots, or the pair is rejected by a `conflicts` list |

### Shared-resource pairs

| Pair | Exclusive claims | Resolution |
|------|------------------|------------|
| Umbral Echo + Resonant Formula | utility echo | Only the first legal utility echo is scheduled |
| Temperbound + Masterwork Bond | item preservation | Only one preservation result applies to a durability event |
| Packmind + Mycelial Nerve | cooperative link | Each recipient consents independently, and propagation does not chain |
| Living Lattice + Gravebloom | world state | Each temporary structure stays separately owned and bounded |
| Gale Lung + Bastion Spine | movement, posture | The most recent deliberate movement or posture action owns the result |
| Deepblood + Gravebloom | recovery | Recovery evaluates once in deterministic slot order |
| Paradox Scar + Umbral Echo | movement, utility echo | Movement resolves before control echoes |

### Runtime support classes

Internal implementation, not public API.

| Class | Role |
|-------|------|
| `MutationRuntimeRouter` | Registers Mutation event handlers and routes them to the specialized runtimes |
| `MutationRuntimeAccess` | Provides config, player data, consent, PvP, FX, and eligibility access |
| `MutationRuntimeStore` | Holds bounded transient Mutation state |
| `MutationCombatRuntime` | Combat Mutations and combat-linked state |
| `MutationMovementRuntime` | Movement, posture, and return-point behavior |
| `MutationEquipmentRuntime` | Linked armor and bound-tool behavior |
| `MutationEffectRuntime` | Cleanse, copied-effect, and cooperative-effect behavior |
| `MutationFormulaRuntime` | Resonant Formula preparation and echoes |
| `MutationWorldRuntime` | World-state Mutations, natural-block checks, and lifecycle cleanup |
| `MutationBlockProvenance` | Distinguishes natural blocks from player-placed blocks |
| `MutationEntityResolver` | Resolves players, owners, pets, projectiles, and credited attackers |
| `MutationProtectionAccess` | Applies Adapt's protection policy to Mutation world changes |
| `MutationItemIdentity` | Marks and resolves Mutation-owned or bound items |
| `MutationRuntimePolicy` | Centralizes bounded values and shared runtime policy decisions |
| `MutationUtilityTag` | Identifies utility-effect categories used by pairing policy |
| `MutationWeaponFamily` | Classifies held weapons and tools for combo behavior |

Manager and public types are documented in `48 - API - Mutations.md`.

## See also

- `35 - Mutations Catalog.md`
- `04 - Commands & Permissions.md`
- `48 - API - Mutations.md`
