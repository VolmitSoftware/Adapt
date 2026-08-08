# Skill: Enchanting

Skill id `enchanting`. Earn XP by enchanting items. Enchanting has 14 registered adaptations and uses the `KNOWLEDGE_BOOK` icon.

**XP sources:** enchanting items at an enchanting table.

**Milestones / challenges** (stat keys):

- `challenge_enchant_1k` tracking `enchanted.items`
- `challenge_enchant_5k` tracking `enchanted.items`
- `challenge_enchant_power_100` tracking `enchanted.power`
- `challenge_enchant_power_1k` tracking `enchanted.power`
- `challenge_enchant_high_25` tracking `enchanting.high.level`
- `challenge_enchant_high_250` tracking `enchanting.high.level`
- `challenge_enchant_total_500` tracking `enchanting.total.levels`
- `challenge_enchant_total_5k` tracking `enchanting.total.levels`

Adaptations run only when learned (level ≥ 1), skill and adaptation are enabled, use permissions allow it, and protectors/region policy permit it.

## Identity

| Property | Value |
|----------|-------|
| Skill id | `enchanting` |
| Class | `SkillEnchanting` |
| Icon | `KNOWLEDGE_BOOK` |
| Color | `LIGHT_PURPLE` |
| Interval (ms) | `3909` |
| Skill config | `plugins/Adapt/adapt/skills/enchanting.toml` |
| Adaptation count | 14 |

## Skill configuration defaults

These values are written to `plugins/Adapt/adapt/skills/enchanting.toml` on first load. They control this skill's XP awards, limits, cooldowns, and progression behavior.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Enables or disables this skill or adaptation. |
| `skillColor` | `"&d"` | Legacy ampersand color code used for this skill in menus and text. |
| `enchantPowerXPMultiplier` | `45` | Unitless multiplier applied to XP from enchant power multiplier. |
| `cooldownDelay` | `5250` | Minimum delay between passive skill XP awards, in milliseconds. |
| `challengeEnchantReward` | `2500` | Reward for the enchant challenge. |

## Adaptation usage reference

What each adaptation does and how a player activates it. TOML overrides live at `plugins/Adapt/adapt/adaptations/<id>.toml`.

### Quick-Click Enchant (`enchanting-quick-enchant`)

Enchant items by clicking enchant books directly on them.

**Runtime entry points:** on inventory click; periodic evaluation every 15100 ms.

**Menu displays:** Max Combined Levels; Cannot Enchant an item with more than ; power.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingQuickEnchant` |
| Icon | `WRITABLE_BOOK` |
| Max level | 7 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 6 |
| Cost factor | 0.9 |
| Tick interval (ms) | 15100 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-quick-enchant.toml` |

Listened events:

- `InventoryClickEvent` (`on`) — on inventory click

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxPowerBonusLimit` | `4` | Maximum XP credited for max power bonus limit. |
| `maxPowerBonus1PerLevels` | `3` | Maximum XP credited for max power bonus1 per levels. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Lapis Return (`enchanting-lapis-return`)

Enchanting at a table has a chance to refund lapis, more at higher levels.

**Runtime entry points:** when enchanting; periodic evaluation every 20999 ms.

**Menu displays:** Chance to drop free lapis when you enchant; the amount scales with your level.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingLapisReturn` |
| Icon | `LAPIS_LAZULI` |
| Max level | 3 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 5 |
| Cost factor | 0.9 |
| Tick interval (ms) | 20999 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-lapis-return.toml` |

Listened events:

- `EnchantItemEvent` (`on`) — when enchanting

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `refundChanceBase` | `0.1` | Base chance to refund lapis on an enchant. |
| `refundChanceFactor` | `0.2` | Additional refund chance granted as the adaptation levels up. |
| `maxRefundChance` | `0.4` | Upper bound on the lapis refund chance. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### XP Return (`enchanting-xp-return`)

Enchanting XP is returned to you when you enchant an item.

**Runtime entry points:** when enchanting; periodic evaluation every 13001 ms.

**Menu displays:** Experience spent has a chance to be refunded when you enchant an item; Experience per Enchant.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingXPReturn` |
| Icon | `EXPERIENCE_BOTTLE` |
| Max level | 7 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 1 |
| Cost factor | 0.9 |
| Tick interval (ms) | 13001 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-xp-return.toml` |

Listened events:

- `EnchantItemEvent` (`on`) — when enchanting

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `xpReturn` | `2` | XP awarded for xp return. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Anvil Savant (`enchanting-anvil-savant`)

Reduce anvil XP cost when combining, repairing, and renaming.

**Runtime entry points:** at anvil; on inventory click; periodic evaluation every 2200 ms.

**Menu displays:** Anvil Cost Reduction.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingAnvilSavant` |
| Icon | `ANVIL` |
| Max level | 4 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.8 |
| Tick interval (ms) | 2200 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-anvil-savant.toml` |

Listened events:

- `PrepareAnvilEvent` (`on`) — at anvil
- `InventoryClickEvent` (`on`) — on inventory click

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `reductionBase` | `0.08` | Base Reduction. |
| `reductionFactor` | `0.37` | Reduction factor. Unitless multiplier. |
| `maximumReduction` | `0.65` | Maximum reduction. |
| `minimumCost` | `1` | Lower bound or activation threshold for minimum cost. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Offer Reroll (`enchanting-offer-reroll`)

Sneak-right-click an enchanting table to reroll its offers. Each reroll costs lapis and XP levels.

**Runtime entry points:** on block/entity/air interact (click); periodic evaluation every 1800 ms.

**Menu displays:** Reroll Cooldown; Lapis Cost.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingOfferReroll` |
| Icon | `ENCHANTING_TABLE` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1800 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-offer-reroll.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `lapisCostBase` | `4` | Base Lapis cost. |
| `lapisCostFactor` | `2` | Lapis cost factor. Unitless multiplier. |
| `cooldownTicksBase` | `320` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksFactor` | `220` | Cooldown ticks factor. Server ticks (20 ticks = 1 second). |
| `xpLevelCost` | `1` | XP awarded for xp level cost. Level or effect-amplifier units. |
| `xpGainOnReroll` | `15` | XP awarded for xp gain on reroll. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Bookshelf Attunement (`enchanting-bookshelf-attunement`)

Gain virtual bookshelf power to improve enchanting table offer quality.

**Runtime entry points:** when enchanting; on `PrepareItemEnchantEvent`; periodic evaluation every 1400 ms.

**Menu displays:** Virtual Bookshelf Power.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingBookshelfAttunement` |
| Icon | `BOOKSHELF` |
| Max level | 4 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Tick interval (ms) | 1400 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-bookshelf-attunement.toml` |

Listened events:

- `EnchantItemEvent` (`on`) — when enchanting
- `PrepareItemEnchantEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `powerBase` | `1` | Base Power. |
| `powerFactor` | `5` | Power factor. Unitless multiplier. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Grindstone Recovery (`enchanting-grindstone-recovery`)

Disenchanting can recover one removed enchantment onto a book with bonus XP.

**Runtime entry points:** on inventory click; periodic evaluation every 1700 ms.

**Menu displays:** Recovery Chance; Bonus XP; Recovery Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingGrindstoneRecovery` |
| Icon | `GRINDSTONE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.74 |
| Tick interval (ms) | 1700 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-grindstone-recovery.toml` |

Listened events:

- `InventoryClickEvent` (`on`) — on inventory click

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `recoverChanceBase` | `0.15` | Proc chance for recover chance base. decimal probability. |
| `recoverChanceFactor` | `0.45` | Proc chance for recover chance factor. decimal probability. |
| `maxRecoverChance` | `0.7` | Proc chance for max recover chance. decimal probability. |
| `bonusXpBase` | `2` | Base skill XP credited for bonus base. |
| `bonusXpFactor` | `8` | Unitless multiplier applied to XP from bonus factor. |
| `cooldownTicksBase` | `120` | Base Cooldown ticks. Server ticks (20 ticks = 1 second). |
| `cooldownTicksFactor` | `70` | Cooldown ticks factor. Server ticks (20 ticks = 1 second). |
| `skillXpOnRecovery` | `13` | XP awarded for skill on recovery. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Curse Cleansing (`enchanting-curse-cleansing`)

Sneak while taking a grindstone result to remove curses from the original item first, preserve every other property, and gain Enchanting XP.

**Runtime entry points:** on inventory click; periodic evaluation every 1900 ms.

**Menu displays:** Enchanting XP per Curse.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingCurseCleansing` |
| Icon | `GRINDSTONE` |
| Max level | 4 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.8 |
| Tick interval (ms) | 1900 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-curse-cleansing.toml` |

Listened events:

- `InventoryClickEvent` (`on`) — on inventory click

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `skillXpPerCurse` | `30` | Enchanting skill XP granted for each curse removed. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Tome Rebinding (`enchanting-tome-rebinding`)

Sneak-right-click a multi-enchant book in an anvil to split it into single-enchant books. Lossy at low levels, lossless at max.

**Runtime entry points:** on inventory click; periodic evaluation every 2100 ms.

**Menu displays:** Enchant Loss Chance; XP Level Cost.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingTomeRebinding` |
| Icon | `WRITABLE_BOOK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.7 |
| Tick interval (ms) | 2100 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-tome-rebinding.toml` |

Listened events:

- `InventoryClickEvent` (`on`) — on inventory click

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `lossChanceBase` | `0.9` | Proc chance for loss chance base. decimal probability. |
| `lossChanceFactor` | `1.0` | Proc chance for loss chance factor. decimal probability. |
| `xpCostBase` | `5` | Base skill XP credited for xp cost base. |
| `xpCostFactor` | `3` | Unitless multiplier applied to XP from xp cost factor. |
| `minXpCost` | `2` | XP awarded for min cost. |
| `skillXpOnSplit` | `14` | XP awarded for skill on split. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Soul Link (`enchanting-soul-link`)

Sneak-right-click an anvil to soul-link an enchanted item so it survives death, gated by an XP level buffer.

**Runtime entry points:** on block/entity/air interact (click); on player death; on `PlayerRespawnEvent`; periodic evaluation every 2400 ms.

**Menu displays:** XP Save Cost; Re-Mark Cooldown.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingSoulLink` |
| Icon | `TOTEM_OF_UNDYING` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 6 |
| Cost factor | 0.85 |
| Tick interval (ms) | 2400 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-soul-link.toml` |

Listened events:

- `PlayerInteractEvent` (`on`) — on block/entity/air interact (click)
- `PlayerDeathEvent` (`on`) — on player death
- `PlayerRespawnEvent` (`on`)
- `PlayerJoinEvent` (`on`)
- `PlayerQuitEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `saveCostBase` | `8` | Base Save cost. |
| `saveCostFactor` | `5` | Save cost factor. Unitless multiplier. |
| `minSaveCost` | `2` | Lower bound or activation threshold for min save cost. |
| `remarkCooldownBase` | `60000` | Base Remark cooldown. |
| `remarkCooldownFactor` | `45000` | Remark cooldown factor. Unitless multiplier. |
| `minRemarkCooldown` | `8000` | Minimum remark cooldown. |
| `skillXpOnSave` | `40` | XP awarded for skill on save. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Arcane Siphon (`enchanting-arcane-siphon`)

Killing mobs in enchanted gear grants bonus XP and can siphon a book of their enchantments.

**Runtime entry points:** on entity death / kill credit; periodic evaluation every 2600 ms.

**Menu displays:** Book Drop Chance; Enchant Quality Bonus.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingArcaneSiphon` |
| Icon | `SOUL_LANTERN` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Tick interval (ms) | 2600 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-arcane-siphon.toml` |

Listened events:

- `EntityDeathEvent` (`on`) — on entity death / kill credit

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `dropChanceBase` | `0.12` | Proc chance for drop chance base. decimal probability. |
| `dropChanceFactor` | `0.4` | Proc chance for drop chance factor. decimal probability. |
| `maxDropChance` | `0.5` | Proc chance for max drop chance. decimal probability. |
| `qualityFactor` | `2` | Quality factor. Unitless multiplier. |
| `bonusXpPerEnchant` | `12` | XP awarded for bonus per enchant. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Rune Sight (`enchanting-rune-sight`)

Reveal the enchantments behind enchanting-table offers before you commit. One at first, the full list at max.

**Runtime entry points:** on `PrepareItemEnchantEvent`; periodic evaluation every 1600 ms.

**Menu displays:** Offers Revealed.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingRuneSight` |
| Icon | `SPYGLASS` |
| Max level | 3 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Tick interval (ms) | 1600 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-rune-sight.toml` |

Listened events:

- `PrepareItemEnchantEvent` (`on`)

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxRevealDepth` | `3` | Maximum reveal depth. |
| `revealThrottleMs` | `400` | Reveal throttle ms. Milliseconds. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Infusion Transfer (`enchanting-infusion-transfer`)

Right-click the base item in an anvil to move one enchantment onto it from the sacrifice item.

**Runtime entry points:** on inventory click; periodic evaluation every 2300 ms.

**Menu displays:** Source Survival Chance; XP Level Cost.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingInfusionTransfer` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 6 |
| Cost factor | 0.8 |
| Tick interval (ms) | 2300 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-infusion-transfer.toml` |

Listened events:

- `InventoryClickEvent` (`on`) — on inventory click

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `survivalBase` | `0.1` | Base Survival. |
| `survivalFactor` | `0.9` | Survival factor. Unitless multiplier. |
| `maxSurvival` | `1.0` | Maximum survival. |
| `xpCostBase` | `6` | Base skill XP credited for xp cost base. |
| `xpCostFactor` | `3` | Unitless multiplier applied to XP from xp cost factor. |
| `minXpCost` | `2` | XP awarded for min cost. |
| `skillXpOnTransfer` | `20` | XP awarded for skill on transfer. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

### Echo of Knowledge (`enchanting-echo-of-knowledge`)

Hold an enchanted book while collecting XP to charge it and upgrade an enchantment within vanilla caps.

**Runtime entry points:** on vanilla XP change; periodic evaluation every 1500 ms.

**Menu displays:** XP Charge Rate.

Requires level ≥ 1, enabled skill/adaptation, `adapt.use.*`, and protector allowance.

| Property | Default |
|----------|---------|
| Class | `EnchantingEchoOfKnowledge` |
| Icon | `KNOWLEDGE_BOOK` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1500 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-echo-of-knowledge.toml` |

Listened events:

- `PlayerExpChangeEvent` (`on`) — on vanilla XP change

Config knobs (code defaults):

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `chargeRateBase` | `1` | Base Charge rate. |
| `chargeRateFactor` | `3` | Charge rate factor. Unitless multiplier. |
| `chargeThreshold` | `120` | Charge threshold. |
| `skillXpOnUpgrade` | `35` | XP awarded for skill on upgrade. |

Shared keys: `enabled`, `permanent`, `showParticles`, `showSounds`.

## See also

- `02 - Concepts.md`
- `03 - Player Usage.md`
- `10 - Skills Catalog.md`
- `04 - Commands & Permissions.md`
