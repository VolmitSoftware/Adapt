# Concepts

Adapt runs on two currencies. Playing a skill earns skill XP, which raises that skill's level and pays out knowledge you can spend only inside that skill. Levelling any skill also feeds a single account-wide master level, and master level sets your ability power, the budget that limits how many adaptation levels you can hold at once across every skill.

Knowledge answers "can I afford this?". Power answers "can I carry it?". A player with lots of knowledge in one line and no spare power has to unlearn something before buying anything new, which is what stops a long-lived character from owning every ability at once.

Adaptations only do anything when they are learned, enabled, allowed in the current world and game mode, cleared by the protection plugins, and not blocked by another plugin through the ability API. All of those checks run every time an adaptation would fire, not once at purchase.

## Skills

A skill is a named line such as `agility`, `pickaxe`, or `chronos`. It listens for its own gameplay events and pays XP for them, ticks every 50ms so it can also pay for ongoing states like sprinting, tracks stats and advancements, and owns a list of adaptations. Every skill has an enable flag and a config file at `plugins/Adapt/adapt/skills/<id>.toml`.

Skill level comes from skill XP through the global `xpCurve`. The default curve, `ADAPT_BALANCED`, needs `100 * L^2 + 1200 * L` XP to reach level `L`, so early levels come quickly and later ones stretch out. Level lookups are clamped at `experienceMaxLevel`.

## Adaptations

An adaptation is a purchasable ability under a skill. Its id is kebab-case and starts with the skill name, like `agility-air-dash`. Level 0 means unlearned; the top is the adaptation's `maxLevel`.

One level costs `max(1, baseCost + baseCost * level * costFactor)` knowledge, and level 1 additionally costs `initialCost`. Buying several levels at once sums each individual step, so jumping straight from level 0 to level 4 costs the same as buying 1, 2, 3, and 4 one at a time. Selling back works the same way in reverse.

Power is simpler: holding an adaptation at level `L` costs `L` power, and moving from level `m` to level `L` costs `L - m` more. Region-granted adaptation levels are excluded from that total, so a region can hand out an ability without eating the player's budget.

Each adaptation gets its own file at `plugins/Adapt/adapt/adaptations/<id>.toml` with its own knobs plus the shared `enabled`, `permanent`, `showParticles`, and `showSounds` flags. Some adaptations also carry a tick interval, cooldowns, or per-use hunger, item, and durability costs.

Learning and unlearning both run through `AdaptationLearningTransaction`, whether the click came from the menu or from an admin command. If a step fails partway (Vault takes the money but the level cannot be applied) the transaction rolls the level, the knowledge, and the money back. An adaptation marked `permanent` refuses to unlearn at all unless an admin forces it.

## Knowledge

Knowledge is per skill line. You get it automatically when the line levels up: crossing level `i` pays `(i / 13) + 1` knowledge, so the payout is 1 per level through level 12, 2 through level 25, and so on. Skills may also grant knowledge directly, and an admin with `adapt.cheatitem` can hand out knowledge orbs that inject a fixed amount into a chosen line.

## Master level and power

Every skill level a player crosses also grants master XP, worth `playerXpPerSkillLevelUpBase + previousSkillLevel * playerXpPerSkillLevelUpLevelMultiplier`. Master XP feeds master level through the same `xpCurve` used for skills.

Max power is `floor(masterLevel * powerPerLevel)` plus any region power bonus currently applied, never below zero. Used power is the sum of every learned adaptation level that was not granted by a region. A purchase fails if the new levels would push used power past max power.

## Wisdom

Skill lines stop at `experienceMaxLevel`. When a line's XP would carry it past that cap, Adapt grants 1 wisdom, drops the line's XP back to the XP value of one level below the cap, and logs a warning. Wisdom is stored on player data and is cleared by the admin clear commands.

## What happens when an adaptation fires

Adapt resolves an "active level" for the adaptation before running any effect. If any check fails the active level is 0 and the effect never runs. The checks happen in this order:

1. The player has the adaptation learned at level 1 or higher.
2. The player's world is not in `blacklistedWorlds`.
3. The game mode is allowed. Spectator is always refused; creative is refused unless `allowAdaptationsInCreative` is on.
4. Every registered protector allows action at the player's location.
5. The player passes the adaptation's `adapt.use` node.
6. The player does not hold a conflicting adaptation from `adaptationUsageConflicts`.
7. The legacy `AdaptAdaptationUseEvent` is not cancelled, and no registered `AbilityUsePolicy` denies the use.

When the adaptation actually charges something, the cost pipeline fires `AdaptAbilityActivateEvent` (cancellable), collects quotes from any registered `AbilityCostProvider`, reserves and settles the cost, and then fires `AdaptAbilityActivatedEvent` with the outcome.

Other plugins can deny an ability, reprice it, or watch it. None of them can grant an adaptation a player has not learned. See `41` through `45`.

## Mutations

Mutations are a separate, opt-in track: two slots, paired domains, a combat lock, discovery, and an end-game perfect adaptation state. They are not adaptations, they do not spend knowledge, and they are disabled until an operator turns them on. See `34 - Mutations Overview.md`.

## Player data

`PlayerData` and `AdaptPlayer` hold everything per player: skill lines, learned adaptations, discoveries, stats, mutation state, effect preferences, and XP multipliers. Storage is one JSON file per player under `data/players/` unless `sql.enabled` is on, in which case it lives in the `ADAPT_DATA` table.

## Reference

### Progression formulas

| Quantity | Formula | Notes |
|---|---|---|
| Skill XP for level `L` | `100 * L^2 + 1200 * L` | Default `ADAPT_BALANCED` curve; other curves listed in `05 - Configuration Math.md` |
| Skill level for XP | `(sqrt(1440000 + 400 * xp) - 1200) / 200` | Inverse of the default curve |
| Knowledge per skill level crossed | `(previousLevel / 13) + 1` | Integer division |
| Master XP per skill level crossed | `playerXpPerSkillLevelUpBase + previousLevel * playerXpPerSkillLevelUpLevelMultiplier` | |
| Master level | Same `xpCurve` applied to master XP | |
| Max power | `floor(masterLevel * powerPerLevel) + regionPowerBonus`, floored at 0 | |
| Used power | Sum of learned adaptation levels, region-granted excluded | |
| Knowledge cost for level `L` | `max(1, baseCost + baseCost * L * costFactor)` plus `initialCost` when `L = 1` | Result floored at 1 and capped at `Integer.MAX_VALUE` |
| Knowledge cost for a multi-level jump | Sum of the per-level cost of every step | Refunds use the same sum |
| Power cost for a jump | `targetLevel - currentLevel` | |

### Progression config defaults

| Key | Default | What it does |
|---|---|---|
| `xpCurve` | `ADAPT_BALANCED` | Curve family mapping XP to level for both skill lines and master level |
| `experienceMaxLevel` | `1000` | Hard level cap per skill line; passing it grants wisdom and rolls XP back |
| `playerXpPerSkillLevelUpBase` | `489` | Flat master XP paid per skill level gained |
| `playerXpPerSkillLevelUpLevelMultiplier` | `44` | Extra master XP per skill level already reached, so late levels pay more |
| `powerPerLevel` | `0.65` | Ability power granted per master level, before flooring |
| `hardcoreNoRefunds` | `false` | When true, unlearning returns no knowledge and no money |
| `allowAdaptationsInCreative` | `false` | When true, adaptations still run for creative-mode players |
| `blacklistedWorlds` | 2 example entries | Worlds where adaptation effects and XP are suppressed |
| `adaptationUsageConflicts` | empty | Adaptation id to the list of adaptation ids that block it when learned |
| `sql.enabled` | `false` | Store player data in MySQL instead of per-player JSON files |

### Adaptation config flags shared by every adaptation

| Key | Meaning |
|---|---|
| `enabled` | Adaptation is registered and usable |
| `permanent` | Cannot be unlearned once learned, unless an admin forces it |
| `showParticles` | Adaptation may spawn its particle effects |
| `showSounds` | Adaptation may play its sounds |

### Types worth knowing

| Type | Role |
|---|---|
| `SimpleSkill` / `SimpleAdaptation` | Base classes for skill lines and adaptations |
| `AdaptationLearningTransaction` | Single path for learn and unlearn, including the Vault charge and rollback |
| `AbilityUsePolicy` / `AbilityCostProvider` | Third-party allow-deny decisions and third-party cost quoting |
| `AdaptAbilityActivateEvent` / `AdaptAbilityActivatedEvent` | Cancellable event before costs are collected, and the result event after settlement |
| `PlayerData` / `AdaptPlayer` | Persistent and runtime player state |

## See also

- `03 - Player Usage.md`
- `05 - Configuration Math.md`
- `10 - Skills Catalog.md`
- `34 - Mutations Overview.md`
