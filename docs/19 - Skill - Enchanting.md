# Skill: Enchanting

Enchanting is the skill you level by using an enchanting table. Every enchant you apply awards skill XP scaled by the total power of the enchantments that landed, and the fourteen adaptations turn the table, the anvil, and the grindstone into a workshop that gives back more than vanilla does.

The early adaptations are refunds. Lapis comes back, spent experience comes back as an orb, and the anvil stops charging you full price for repairs and renames. None of that needs a gesture. You enchant like normal and keep more of what you paid.

The later ones change how you enchant at all. Rune Sight prints the hidden offers on your actionbar before you commit. Offer Reroll lets you burn lapis to reshuffle a bad table. Bookshelf Attunement fakes extra bookshelf power so the table offers better tiers than the room deserves. Quick-Click Enchant skips the anvil entirely for books that fit. Tome Rebinding and Infusion Transfer move enchantments between items, and Soul Link keeps one favorite item out of your death pile.

Big enchants get noticed. Anything costing 30 levels or more counts toward the high-level challenges and sets off a beacon chime and a dome of particles over the table.

## Adaptations

Everything below needs the same four things: the adaptation learned at level 1 or higher in the Adapt menu, the Enchanting skill and that adaptation both enabled in config, an `adapt.use.` permission that has not been revoked for you, and any protection or region plugin allowing the block or entity you are acting on. Those are not repeated per entry.

### Quick-Click Enchant (`enchanting-quick-enchant`)

Applies an enchanted book straight onto an item without an anvil, with no level cost and no prior-work penalty. It only moves enchantments the target can actually accept, and it refuses if the item would end up with more combined enchantment levels than your adaptation level allows.

How to use it:

1. Open any inventory screen.
2. Pick up an enchanted book so it sits on your cursor. It has to be a single book.
3. Left-click it onto a single item in a container slot, an armor slot, or your hotbar. The target cannot be a book or another enchanted book.
4. Compatible enchantments move onto the item. Anything that conflicts with what is already there, or cannot go on that item type, stays on the book.
5. If the book gave up everything it had it disappears. Otherwise it stays on your cursor holding the leftovers.

If the combined power would exceed your cap, nothing happens and your actionbar tells you the limit.

### Lapis Return (`enchanting-lapis-return`)

Enchanting at a table sometimes drops lapis back at your feet. The amount dropped equals your adaptation level, so higher levels both refund more often and refund more. It works on its own once learned.

There is a 20 second window between refunds. The chance is rolled first and the cooldown is checked second, so a lucky roll inside that window is wasted.

### XP Return (`enchanting-xp-return`)

Every enchant can spit an experience orb back out at you, worth `xpReturn * level * level` experience points. Same 20 second spacing as Lapis Return. It works on its own once learned.

### Anvil Savant (`enchanting-anvil-savant`)

Cuts the level cost the anvil quotes you for combining, repairing, and renaming, down to a floor of `minimumCost`. Your actionbar shows how many levels you just saved while you are shuffling items in the anvil. It works on its own once learned.

The saved-levels stat only records once you actually take the result out of the output slot.

### Offer Reroll (`enchanting-offer-reroll`)

Rerolls your personal enchantment seed, which reshuffles every offer the table is showing you. Useful when all three slots are junk and you do not want to burn levels on a bad one.

How to use it:

1. Learn it, then stand near an enchanting table.
2. Sneak and right-click the table with your main hand. Sneak-right-clicking air also works if you are looking at a table within 5 blocks.
3. Lapis and XP levels come out of your inventory, the offers reshuffle, and the table goes on cooldown.

Higher levels cost less lapis and shorten the cooldown. If you cannot pay, or the server build does not expose the seed setter, the attempt fizzles and everything is refunded.

### Bookshelf Attunement (`enchanting-bookshelf-attunement`)

Adds virtual bookshelf power to the enchanting table, so it offers better enchantments than the surrounding room should allow. Each offer gets its level requirement pushed up (capped at 30) and its enchantment level pushed up by a third of the virtual power, never past the enchantment's own maximum. It works on its own once learned.

Because it raises the required level as well as the reward, this is a quality upgrade, not a discount.

### Grindstone Recovery (`enchanting-grindstone-recovery`)

Disenchanting normally destroys everything you strip off. This gives you a chance to keep one of those enchantments as an enchanted book, plus a handful of vanilla XP on top of the usual grindstone payout. It works on its own once learned.

The recovered book carries one randomly chosen enchantment from whichever input was enchanted. Success puts the grindstone on a short cooldown for you. If you are sneaking, Curse Cleansing claims the click instead and this does not fire.

### Curse Cleansing (`enchanting-curse-cleansing`)

Strips Curse of Binding and Curse of Vanishing off an item while keeping everything else: the other enchantments, the name, the damage, the custom data. Vanilla grindstones cannot do this at all.

How to use it:

1. Put the cursed item in a grindstone. The other input slot can be empty.
2. Sneak.
3. Click the grindstone output slot.
4. One of that input item is consumed, and a cleaned copy lands in your inventory. Your actionbar reports how many curses came off.

You get Enchanting XP for each curse removed.

### Tome Rebinding (`enchanting-tome-rebinding`)

Splits an enchanted book carrying several enchantments into one book per enchantment. At low levels the split usually eats one of them at random. At max level the loss chance reaches zero and the split is clean.

How to use it:

1. Put a multi-enchant book (two or more stored enchantments) in the left slot of an anvil.
2. Make sure your cursor is empty.
3. Sneak and right-click that left slot.
4. XP levels are deducted, the original book is consumed, and the single-enchant books go to your inventory.

If you do not have the levels, the attempt fizzles and nothing is consumed.

### Soul Link (`enchanting-soul-link`)

Marks one enchanted item so it survives your next death. On death the marked item is pulled out of your drops, held server-side, and handed back to you when you respawn.

How to use it:

1. Hold an enchanted item, or an enchanted book, in your main hand.
2. Sneak and right-click an anvil block.
3. The item is stamped and your actionbar confirms the link. You cannot re-mark again until the cooldown expires.
4. When you die, the save costs XP levels. If you were keeping levels, they come off your kept total. If you were not, the cost comes out of the XP you drop, at 7 XP per level.
5. Respawn and the item is returned. If you logged out before it could be delivered, it arrives a second after your next join.

Only one item is linked at a time, marking a new one replaces the old link. If you do not have enough XP levels at the moment you die, the item drops normally.

### Arcane Siphon (`enchanting-arcane-siphon`)

Killing a mob that is wearing or holding enchanted gear pays Enchanting XP per distinct enchantment on it, and sometimes drops an enchanted book carrying one of those enchantments. It works on its own once learned.

Higher levels raise both the drop chance and the level the siphoned book rolls at, capped at the enchantment's vanilla maximum. Player kills do not count.

### Rune Sight (`enchanting-rune-sight`)

Shows you what the enchanting table is actually offering before you spend anything. The hidden offers are printed on your actionbar as enchantment name, level, and cost. It works on its own once learned.

Level 1 reveals the top offer only. Level 3 reveals all three.

### Infusion Transfer (`enchanting-infusion-transfer`)

Moves the strongest compatible enchantment from the sacrifice item onto the base item, without the anvil's combine rules or prior-work penalty. Good for pulling one enchantment off a book or a spare tool.

How to use it:

1. Put the item you want to improve in the left anvil slot. It cannot be a book.
2. Put the donor item or book in the right slot.
3. Empty your cursor.
4. Right-click the left slot. No sneaking needed.
5. XP levels are deducted and the enchantment lands on the base item.

The donor rolls to survive. At low levels it usually disappears; at max level it always survives, minus the enchantment that moved. A book stripped down to nothing becomes a plain book.

### Echo of Knowledge (`enchanting-echo-of-knowledge`)

Turns ordinary XP pickup into book upgrades. Hold an enchanted book while collecting experience and it soaks up charge; when it is full, one of its enchantments goes up a level.

How to use it:

1. Hold an enchanted book in your main hand.
2. Collect experience however you like: mining, mobs, furnaces.
3. Each pickup adds charge to that specific book, stored on the book itself.
4. When the charge passes the threshold, one enchantment below its vanilla maximum gains a level and the threshold is subtracted from the charge.

The book stops charging once every enchantment on it is at its vanilla cap. Higher levels convert experience to charge faster.

## Reference

### Identity

| Property | Value |
|----------|-------|
| Skill id | `enchanting` |
| Class | `SkillEnchanting` |
| Icon | `KNOWLEDGE_BOOK` |
| Color | `LIGHT_PURPLE` |
| Interval (ms) | `3909` |
| Skill config | `plugins/Adapt/adapt/skills/enchanting.toml` |
| Adaptation count | 14 |

### Skill XP and stats

XP comes from `EnchantItemEvent` at MONITOR priority. Award is `enchantPowerXPMultiplier * power`, where `power` is the sum of the enchantment levels applied. Awards are spaced by `cooldownDelay`; stats are recorded on every enchant regardless of the cooldown.

| Stat key | Recorded |
|----------|----------|
| `enchanted.items` | 1 per enchant performed |
| `enchanted.power` | Sum of applied enchantment levels |
| `enchanting.high.level` | 1 per enchant costing 30 or more levels |
| `enchanting.total.levels` | Level cost of the enchant |

### Skill configuration defaults

Written to `plugins/Adapt/adapt/skills/enchanting.toml` on first load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `enabled` | `true` | Turns the whole skill and its adaptations off when false. |
| `skillColor` | `"&d"` | Legacy ampersand color code used for this skill in menus and text. |
| `enchantPowerXPMultiplier` | `45` | Skill XP granted per point of applied enchantment power. |
| `cooldownDelay` | `5250` | Minimum milliseconds between XP awards from enchanting. |
| `challengeEnchantReward` | `2500` | Base knowledge reward for the Enchanting milestones. |

### Skill milestones

| Milestone key | Stat key | Threshold | Reward |
|---------------|----------|-----------|--------|
| `challenge_enchant_1k` | `enchanted.items` | 1000 | `challengeEnchantReward` |
| `challenge_enchant_5k` | `enchanted.items` | 5000 | `challengeEnchantReward` |
| `challenge_enchant_power_100` | `enchanted.power` | 100 | `challengeEnchantReward` |
| `challenge_enchant_power_1k` | `enchanted.power` | 1000 | `challengeEnchantReward` * 2 |
| `challenge_enchant_high_25` | `enchanting.high.level` | 25 | `challengeEnchantReward` |
| `challenge_enchant_high_250` | `enchanting.high.level` | 250 | `challengeEnchantReward` * 2 |
| `challenge_enchant_total_500` | `enchanting.total.levels` | 500 | `challengeEnchantReward` |
| `challenge_enchant_total_5k` | `enchanting.total.levels` | 5000 | `challengeEnchantReward` * 2 |

### Shared adaptation keys

Every adaptation TOML at `plugins/Adapt/adapt/adaptations/<id>.toml` carries these in addition to the knobs listed below.

| Key | Behavior |
|-----|----------|
| `enabled` | Turns this adaptation off when false. |
| `permanent` | Treats the adaptation as always learned, bypassing learn and unlearn. |
| `showParticles` | Plays this adaptation's particle effects. |
| `showSounds` | Plays this adaptation's sound effects. |
| `baseCost`, `costFactor`, `maxLevel`, `initialCost` | Knowledge cost curve and level cap. Defaults per adaptation below. |

In the formulas below, `levelPercent` is the learned level divided by `maxLevel`, clamped to 0 through 1.

### Quick-Click Enchant

| Property | Value |
|----------|-------|
| Class | `EnchantingQuickEnchant` |
| Icon | `WRITABLE_BOOK` |
| Max level | 7 |
| Initial knowledge cost | 8 |
| Base knowledge cost | 6 |
| Cost factor | 0.9 |
| Tick interval (ms) | 15100 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-quick-enchant.toml` |
| Listened events | `InventoryClickEvent` (HIGHEST) |
| Menu stat line | Max Combined Levels |
| Stat key | `enchanting.quick-enchant.books-applied` |
| Milestones | `challenge_enchanting_quick_100` (100, reward 300), `challenge_enchanting_quick_1k` (1000, reward 1000) |

Power cap is `level + (level / maxPowerBonus1PerLevels)` once `level` exceeds `maxPowerBonusLimit`, otherwise just `level`. A successful application awards 50 skill XP plus 320 per enchantment level moved.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxPowerBonusLimit` | `4` | Adaptation level above which the bonus power term starts applying. |
| `maxPowerBonus1PerLevels` | `3` | Adaptation levels per extra point of allowed combined power. |

### Lapis Return

| Property | Value |
|----------|-------|
| Class | `EnchantingLapisReturn` |
| Icon | `LAPIS_LAZULI` |
| Max level | 3 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 5 |
| Cost factor | 0.9 |
| Tick interval (ms) | 20999 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-lapis-return.toml` |
| Listened events | `EnchantItemEvent` (HIGH) |
| Menu stat line | Chance to drop free lapis when you enchant; the amount scales with your level |
| Stat key | `enchanting.lapis-return.lapis-saved` |
| Milestones | `challenge_enchanting_lapis_100` (100, reward 300), `challenge_enchanting_lapis_2500` (2500, reward 1000) |

Refund chance is `min(maxRefundChance, refundChanceBase + levelPercent * refundChanceFactor)`. Dropped stack size equals the adaptation level. Hardcoded 20000 ms cooldown between refunds, checked after the roll.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `refundChanceBase` | `0.1` | Refund chance at level 0 progress, 0-1. |
| `refundChanceFactor` | `0.2` | Extra refund chance added at full level, 0-1. |
| `maxRefundChance` | `0.4` | Hard ceiling on refund chance, 0-1. |

### XP Return

| Property | Value |
|----------|-------|
| Class | `EnchantingXPReturn` |
| Icon | `EXPERIENCE_BOTTLE` |
| Max level | 7 |
| Initial knowledge cost | 2 |
| Base knowledge cost | 1 |
| Cost factor | 0.9 |
| Tick interval (ms) | 13001 |
| Localization key | `enchanting.return` |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-xp-return.toml` |
| Listened events | `EnchantItemEvent` (HIGHEST) |
| Menu stat lines | Experience spent has a chance to be refunded when you enchant an item; Experience per Enchant |
| Stat key | `enchanting.xp-return.levels-saved` (counts experience points, not levels) |
| Milestones | `challenge_enchanting_xp_100` (100, reward 400) |

Orb value is `xpReturn * level * level` experience points. Hardcoded 20000 ms cooldown between refunds.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `xpReturn` | `2` | Experience points per squared adaptation level in the refunded orb. |

### Anvil Savant

| Property | Value |
|----------|-------|
| Class | `EnchantingAnvilSavant` |
| Icon | `ANVIL` |
| Max level | 4 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.8 |
| Tick interval (ms) | 2200 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-anvil-savant.toml` |
| Listened events | `PrepareAnvilEvent` (HIGHEST), `InventoryClickEvent` (MONITOR) |
| Menu stat line | Anvil Cost Reduction |
| Stat key | `enchanting.anvil-savant.levels-saved` |
| Milestones | `challenge_enchanting_anvil_200` (200, reward 400), `challenge_enchanting_anvil_5k` (5000, reward 1500) |

Reduction is `min(maximumReduction, reductionBase + levelPercent * reductionFactor)`; the new cost is `max(minimumCost, ceil(oldCost * (1 - reduction)))`. The savings actionbar is throttled to one message per 350 ms.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `reductionBase` | `0.08` | Cost reduction at level 0 progress, 0-1. |
| `reductionFactor` | `0.37` | Extra cost reduction added at full level, 0-1. |
| `maximumReduction` | `0.65` | Hard ceiling on cost reduction, 0-1. |
| `minimumCost` | `1` | Lowest anvil level cost the reduction may produce. |

### Offer Reroll

| Property | Value |
|----------|-------|
| Class | `EnchantingOfferReroll` |
| Icon | `ENCHANTING_TABLE` |
| Max level | 4 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1800 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-offer-reroll.toml` |
| Listened events | `PlayerInteractEvent` (HIGHEST) |
| Menu stat lines | Reroll Cooldown; Lapis Cost; XP Level Cost |
| Stat key | `enchanting.offer-reroll.rerolls` |
| Milestones | `challenge_enchanting_reroll_100` (100, reward 300), `challenge_enchanting_reroll_1k` (1000, reward 1000) |

Lapis cost is `max(1, round(lapisCostBase - levelPercent * lapisCostFactor))`. Cooldown is `max(20, round(cooldownTicksBase - levelPercent * cooldownTicksFactor))` ticks, applied as a vanilla item cooldown on `ENCHANTING_TABLE`. The seed change is done reflectively through `setEnchantmentSeed`; failure refunds the lapis and XP levels.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `lapisCostBase` | `4` | Lapis consumed per reroll at level 0 progress. |
| `lapisCostFactor` | `2` | Lapis removed from that cost at full level. |
| `cooldownTicksBase` | `320` | Reroll cooldown at level 0 progress, in server ticks (20 = 1 second). |
| `cooldownTicksFactor` | `220` | Ticks removed from the cooldown at full level. |
| `xpLevelCost` | `1` | Vanilla XP levels charged per reroll, flat at all levels. |
| `xpGainOnReroll` | `15` | Enchanting skill XP granted per successful reroll. |

### Bookshelf Attunement

| Property | Value |
|----------|-------|
| Class | `EnchantingBookshelfAttunement` |
| Icon | `BOOKSHELF` |
| Max level | 4 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Tick interval (ms) | 1400 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-bookshelf-attunement.toml` |
| Listened events | `PrepareItemEnchantEvent` (HIGHEST), `EnchantItemEvent` (MONITOR, effects only) |
| Menu stat line | Virtual Bookshelf Power |
| Stat key | `enchanting.bookshelf-attunement.enchants-boosted` |
| Milestones | `challenge_enchanting_bookshelf_100` (100, reward 400) |

Virtual power is `max(1, round(powerBase + levelPercent * powerFactor))`. Each offer's cost becomes `min(30, cost + power)` and its enchantment level becomes `min(enchantMax, level + power / 3)`, never below 1. The shimmer effect is throttled to once per 1000 ms.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `powerBase` | `1` | Virtual bookshelf power at level 0 progress. |
| `powerFactor` | `5` | Virtual bookshelf power added at full level. |

### Grindstone Recovery

| Property | Value |
|----------|-------|
| Class | `EnchantingGrindstoneRecovery` |
| Icon | `GRINDSTONE` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.74 |
| Tick interval (ms) | 1700 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-grindstone-recovery.toml` |
| Listened events | `InventoryClickEvent` (HIGHEST, cancelled events ignored) |
| Menu stat lines | Recovery Chance; Bonus XP; Recovery Cooldown |
| Stat key | `enchanting.grindstone-recovery.enchants-recovered` |
| Milestones | `challenge_enchanting_grindstone_50` (50, reward 300), `challenge_enchanting_grindstone_500` (500, reward 1000) |

Recovery chance is `min(maxRecoverChance, recoverChanceBase + levelPercent * recoverChanceFactor)`. Vanilla XP granted is `round(bonusXpBase + levelPercent * bonusXpFactor)`. Cooldown is `max(10, round(cooldownTicksBase - levelPercent * cooldownTicksFactor))` ticks on `GRINDSTONE`. Recovered book level is clamped to the enchantment's maximum.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `recoverChanceBase` | `0.15` | Recovery chance at level 0 progress, 0-1. |
| `recoverChanceFactor` | `0.45` | Extra recovery chance added at full level, 0-1. |
| `maxRecoverChance` | `0.7` | Hard ceiling on recovery chance, 0-1. |
| `bonusXpBase` | `2` | Vanilla experience points granted at level 0 progress. |
| `bonusXpFactor` | `8` | Extra vanilla experience points granted at full level. |
| `cooldownTicksBase` | `120` | Recovery cooldown at level 0 progress, in server ticks. |
| `cooldownTicksFactor` | `70` | Ticks removed from the cooldown at full level. |
| `skillXpOnRecovery` | `13` | Enchanting skill XP granted per recovery. |

### Curse Cleansing

| Property | Value |
|----------|-------|
| Class | `EnchantingCurseCleansing` |
| Icon | `GRINDSTONE` |
| Max level | 4 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.8 |
| Tick interval (ms) | 1900 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-curse-cleansing.toml` |
| Listened events | `InventoryClickEvent` (LOWEST, cancelled events ignored) |
| Menu stat line | Enchanting XP per Curse |
| Stat key | `enchanting.curse-cleansing.curses-removed` |
| Milestones | `challenge_enchanting_cleanse_10` (10, reward 300), `challenge_enchanting_cleanse_100` (100, reward 1000) |

Curses recognized are `BINDING_CURSE` and `VANISHING_CURSE`, on both direct enchantments and stored book enchantments. The cleaned output is always a stack of 1. Skill XP is `skillXpPerCurse * cursesRemoved`. This adaptation canonicalizes its config file on load.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `skillXpPerCurse` | `30` | Enchanting skill XP granted for each curse removed. |

### Tome Rebinding

| Property | Value |
|----------|-------|
| Class | `EnchantingTomeRebinding` |
| Icon | `WRITABLE_BOOK` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 5 |
| Cost factor | 0.7 |
| Tick interval (ms) | 2100 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-tome-rebinding.toml` |
| Listened events | `InventoryClickEvent` (HIGHEST, cancelled events ignored) |
| Menu stat lines | Enchant Loss Chance; XP Level Cost |
| Stat key | `enchanting.tome-rebinding.books-split` |
| Milestones | `challenge_enchanting_rebind_50` (50, reward 400), `challenge_enchanting_rebind_500` (500, reward 1200) |

Loss chance is `max(0, lossChanceBase - levelPercent * lossChanceFactor)`, which reaches 0 at max level with the shipped defaults. A loss only happens when more than one enchantment remains. XP level cost is `max(minXpCost, round(xpCostBase - levelPercent * xpCostFactor))`. Skill XP is `skillXpOnSplit` per book produced.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `lossChanceBase` | `0.9` | Chance to lose one enchantment at level 0 progress, 0-1. |
| `lossChanceFactor` | `1.0` | Loss chance subtracted at full level, 0-1. |
| `xpCostBase` | `5` | Vanilla XP levels charged at level 0 progress. |
| `xpCostFactor` | `3` | XP levels removed from that cost at full level. |
| `minXpCost` | `2` | Lowest XP level cost the scaling may produce. |
| `skillXpOnSplit` | `14` | Enchanting skill XP granted per book produced. |

### Soul Link

| Property | Value |
|----------|-------|
| Class | `EnchantingSoulLink` |
| Icon | `TOTEM_OF_UNDYING` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 6 |
| Cost factor | 0.85 |
| Tick interval (ms) | 2400 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-soul-link.toml` |
| Listened events | `PlayerInteractEvent` (HIGHEST, also receives cancelled events), `PlayerDeathEvent` (HIGH), `PlayerRespawnEvent` (MONITOR), `PlayerJoinEvent` (MONITOR), `PlayerQuitEvent` (MONITOR) |
| Menu stat lines | XP Save Cost; Re-Mark Cooldown |
| Stat key | `enchanting.soul-link.items-saved` |
| Milestones | `challenge_enchanting_soul_10` (10, reward 400), `challenge_enchanting_soul_100` (100, reward 1200) |

The mark is a random token written to the item's persistent data under `adapt:soul-link-token`. Save cost is `max(minSaveCost, round(saveCostBase - levelPercent * saveCostFactor))` XP levels. Re-mark cooldown is `max(minRemarkCooldown, round(remarkCooldownBase - levelPercent * remarkCooldownFactor))` milliseconds. If the death keeps levels, the cost comes off the kept level total; otherwise dropped XP is reduced by 7 experience points per level of cost. The saved item is serialized to player storage and delivered on respawn, or 20 ticks after the next join. Deaths with keep-inventory on are skipped entirely.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `saveCostBase` | `8` | XP levels charged on save at level 0 progress. |
| `saveCostFactor` | `5` | XP levels removed from that cost at full level. |
| `minSaveCost` | `2` | Lowest XP level cost the scaling may produce. |
| `remarkCooldownBase` | `60000` | Milliseconds between marks at level 0 progress. |
| `remarkCooldownFactor` | `45000` | Milliseconds removed from that cooldown at full level. |
| `minRemarkCooldown` | `8000` | Shortest re-mark cooldown in milliseconds. |
| `skillXpOnSave` | `40` | Enchanting skill XP granted when a saved item is returned. |

### Arcane Siphon

| Property | Value |
|----------|-------|
| Class | `EnchantingArcaneSiphon` |
| Icon | `SOUL_LANTERN` |
| Max level | 5 |
| Initial knowledge cost | 4 |
| Base knowledge cost | 4 |
| Cost factor | 0.6 |
| Tick interval (ms) | 2600 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-arcane-siphon.toml` |
| Listened events | `EntityDeathEvent` (MONITOR, cancelled events ignored) |
| Menu stat lines | Book Drop Chance; Enchant Quality Bonus |
| Stat key | `enchanting.arcane-siphon.books-siphoned` |
| Milestones | `challenge_enchanting_siphon_25` (25, reward 400), `challenge_enchanting_siphon_250` (250, reward 1200) |

Gear scanned is helmet, chestplate, leggings, boots, main hand, and off hand; duplicate enchantments keep the highest level. Skill XP is `bonusXpPerEnchant * distinctEnchantCount` and is paid whether or not a book drops. Drop chance is `min(maxDropChance, dropChanceBase + levelPercent * dropChanceFactor)`. Book level is the source level plus `floor(levelPercent * qualityFactor)`, clamped to the enchantment maximum.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `dropChanceBase` | `0.12` | Book drop chance at level 0 progress, 0-1. |
| `dropChanceFactor` | `0.4` | Extra drop chance added at full level, 0-1. |
| `maxDropChance` | `0.5` | Hard ceiling on book drop chance, 0-1. |
| `qualityFactor` | `2` | Enchantment levels added to the siphoned book at full level. |
| `bonusXpPerEnchant` | `12` | Enchanting skill XP per distinct enchantment on the victim's gear. |

### Rune Sight

| Property | Value |
|----------|-------|
| Class | `EnchantingRuneSight` |
| Icon | `SPYGLASS` |
| Max level | 3 |
| Initial knowledge cost | 3 |
| Base knowledge cost | 3 |
| Cost factor | 0.5 |
| Tick interval (ms) | 1600 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-rune-sight.toml` |
| Listened events | `PrepareItemEnchantEvent` (MONITOR) |
| Menu stat line | Offers Revealed |
| Stat key | `enchanting.rune-sight.offers-revealed` |
| Milestones | `challenge_enchanting_rune_100` (100, reward 300), `challenge_enchanting_rune_1k` (1000, reward 1000) |

Reveal depth is `max(1, min(maxRevealDepth, 1 + floor(levelPercent * (maxRevealDepth - 1))))`. The actionbar line lists each revealed offer as enchantment name, level, and level cost.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `maxRevealDepth` | `3` | Offers revealed at full level. |
| `revealThrottleMs` | `400` | Minimum milliseconds between actionbar reveals. |

### Infusion Transfer

| Property | Value |
|----------|-------|
| Class | `EnchantingInfusionTransfer` |
| Icon | `ANVIL` |
| Max level | 5 |
| Initial knowledge cost | 6 |
| Base knowledge cost | 6 |
| Cost factor | 0.8 |
| Tick interval (ms) | 2300 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-infusion-transfer.toml` |
| Listened events | `InventoryClickEvent` (HIGHEST, cancelled events ignored) |
| Menu stat lines | Source Survival Chance; XP Level Cost |
| Stat key | `enchanting.infusion-transfer.transfers` |
| Milestones | `challenge_enchanting_infusion_25` (25, reward 400), `challenge_enchanting_infusion_250` (250, reward 1200) |

The transferred enchantment is the highest-level one on the donor that the base item does not already have and can legally hold. Survival chance is `min(maxSurvival, survivalBase + levelPercent * survivalFactor)`. XP level cost is `max(minXpCost, round(xpCostBase - levelPercent * xpCostFactor))`.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `survivalBase` | `0.1` | Chance the donor item survives at level 0 progress, 0-1. |
| `survivalFactor` | `0.9` | Extra survival chance added at full level, 0-1. |
| `maxSurvival` | `1.0` | Hard ceiling on donor survival chance, 0-1. |
| `xpCostBase` | `6` | Vanilla XP levels charged at level 0 progress. |
| `xpCostFactor` | `3` | XP levels removed from that cost at full level. |
| `minXpCost` | `2` | Lowest XP level cost the scaling may produce. |
| `skillXpOnTransfer` | `20` | Enchanting skill XP granted per transfer. |

### Echo of Knowledge

| Property | Value |
|----------|-------|
| Class | `EnchantingEchoOfKnowledge` |
| Icon | `KNOWLEDGE_BOOK` |
| Max level | 5 |
| Initial knowledge cost | 5 |
| Base knowledge cost | 5 |
| Cost factor | 0.7 |
| Tick interval (ms) | 1500 |
| Config file | `plugins/Adapt/adapt/adaptations/enchanting-echo-of-knowledge.toml` |
| Listened events | `PlayerExpChangeEvent` (MONITOR) |
| Menu stat line | XP Charge Rate |
| Stat key | `enchanting.echo-of-knowledge.levels-charged` |
| Milestones | `challenge_enchanting_echo_25` (25, reward 400), `challenge_enchanting_echo_250` (250, reward 1200) |

Charge gained per pickup is `max(1, round(xpAmount * (chargeRateBase + levelPercent * chargeRateFactor)))`, stored on the book under `adapt:echo-charge` and capped at the threshold while no upgrade is pending. Nothing is stored when every enchantment on the book is already at its vanilla maximum. Charge effects are throttled to once per 400 ms.

| Key | Code default | Behavior / units |
|-----|--------------|------------------|
| `chargeRateBase` | `1` | Charge gained per experience point at level 0 progress. |
| `chargeRateFactor` | `3` | Extra charge per experience point at full level. |
| `chargeThreshold` | `120` | Charge required to raise one enchantment by a level. |
| `skillXpOnUpgrade` | `35` | Enchanting skill XP granted per upgrade. |

## See also

- `02 - Concepts.md` for skills, adaptations, and knowledge
- `03 - Player Usage.md` for the Adapt menu and learning flow
- `10 - Skills Catalog.md` for the full skill list
- `04 - Commands & Permissions.md` for the `adapt.use` permission tree
