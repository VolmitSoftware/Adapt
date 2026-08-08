# Concepts

Adapt progression is skill XP → skill level → knowledge, plus master XP → master level → ability power. Players spend knowledge and power to learn adaptation levels; adaptations only run when learned, enabled, permitted, and not blocked by protectors or ability policies.

## Skills

A **skill** is a named line (`agility`, `pickaxe`, …) implemented as `SimpleSkill`. It:

- Awards XP from its event handlers and owner pulses
- Tracks milestones/advancements
- Registers zero or more adaptations
- Has an enable flag and config under `plugins/Adapt/adapt/skills/<id>.toml`

Skill level is derived from skill XP using the global `xpCurve` (see `05 - Configuration Math.md`). Level is clamped by `experienceMaxLevel`.

## Adaptations

An **adaptation** is a purchasable ability under a skill (`SimpleAdaptation`). It has:

- Kebab-case id (e.g. `agility-air-dash`)
- Levels from 0 (unlearned) to `maxLevel`
- Knowledge cost for level `L`: `max(1, baseCost + baseCost * L * costFactor)`, plus `initialCost` when `L = 1`; multi-level purchases sum each step
- Ability **power** cost per level while held
- Optional tick interval, cooldowns, hunger/item/durability costs on use
- Config under `plugins/Adapt/adapt/adaptations/<id>.toml`
- Shared flags: `enabled`, `permanent`, `showParticles`, `showSounds`

Learning and unlearning run through `AdaptationLearningTransaction` (GUI or admin commands). Permanent adaptations skip normal unlearn flow once learned.

## Knowledge

**Knowledge** is the skill-scoped currency spent to buy adaptation levels. It is earned alongside skill progression (level-ups and skill-specific grants). Knowledge orbs can inject knowledge via admin items (`adapt.cheatitem`).

## Master XP, master level, power

When a skill levels up, master XP is granted:

`masterXpGain = playerXpPerSkillLevelUpBase + previousSkillLevel * playerXpPerSkillLevelUpLevelMultiplier`

Master level uses the same `xpCurve` on master XP. **Ability power** max is:

`floor(masterLevel * powerPerLevel)`

plus the current region power bonus, with the final result floored at zero. The built-in power cost is one point per learned adaptation level; region-granted levels are excluded. Power remaining must cover every newly purchased level before learning succeeds.

## Wisdom

When a skill line would exceed the hard level cap, excess handling grants **wisdom** (see `experienceMaxLevel` docs in config math). Wisdom is tracked on player data and can be cleared by admin clear commands.

## Ability pipeline

On use, an adaptation typically:

1. Confirms the player has the adaptation learned and skill enabled
2. Checks `adapt.use.<id-without-hyphens>` (and skill node)
3. Consults protectors / region policy
4. Runs `AbilityUsePolicy` providers (external plugins may deny)
5. Reserves and settles costs via `AbilityCostProvider` and default costs
6. Fires `AdaptAbilityActivateEvent` / `AdaptAbilityActivatedEvent` (and legacy content events where used)
7. Applies gameplay effect

External integrations cannot grant unlearned adaptations; they can only deny, price, or observe. See `41`–`45`.

## Mutations

Mutations are a separate experimental track: dual slots, domain pairing, combat lock, discovery, and optional perfect adaptation. They are not adaptations and do not use knowledge costs the same way. See `34`–`35`.

## Player data

Per-player state (`PlayerData` / `AdaptPlayer`) holds skill lines, adaptations, discoveries, stats, mutation data, effect preferences, and multipliers. Storage is local JSON or SQL (`sql.enabled`).

## See also

- `03 - Player Usage.md`
- `05 - Configuration Math.md`
- `10 - Skills Catalog.md`
- `34 - Mutations Overview.md`
