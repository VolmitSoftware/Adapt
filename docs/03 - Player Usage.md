# Player Usage

Players open Adapt by interacting with the activator block (default bookshelf) or `/adapt gui` when permitted. From the skills GUI they open a skill, pick an adaptation, learn levels with knowledge and power, then use each adaptation through normal Minecraft actions described in that skill’s usage reference.

## Opening the GUI

### Activator block

- Right-click `adaptActivatorBlock` (default `BOOKSHELF`) without sneaking.
- Use a side face. Top and bottom faces work only when `adaptActivatorAllowVerticalFaces = true`.
- Do not hold a placeable block in either hand; non-block items are accepted.
- The interaction must be allowed by every protector enabled through `protectorSupport.*`.
- `adaptActivatorBlockName` changes only the display text used in messages.

The activator GUI itself is available in a blacklisted world. `blacklistedWorlds` gates Adapt gameplay, XP, and adaptation runtime behavior after the menu is open.

### Command

`/adapt gui [target] [player] [force]` requires `adapt.gui`. `target` may be `main`, `skill:<id>`, or `adaptation:<id>`. `force=true` bypasses the target skill or adaptation's `adapt.use.*` check; it does not enable a disabled target.

### Effects toggle

`/adapt effects` (`adapt.effects`, default true) toggles whether this player sees Adapt particles/sounds subject to global and per-adaptation effect flags.

## Skills GUI

- Lists skills the player can see. With `guiShowAllSkills`, enabled skills appear even at zero progress.
- Ordering and icons: `06 - GUI Customization.md`.
- Escape behavior: `escClosesAllGuis` and `guiBackButton`.
- Learn/unlearn clicks debounce with `learnUnlearnButtonDelayTicks`.

## How to use skills (all lines)

1. Open Adapt (bookshelf or `/adapt gui`).
2. Select a skill line (for example Agility, Pickaxes, Chronos). Skill ids are stable (`agility`, `pickaxe`, `chronos`, …) — see `10 - Skills Catalog.md`.
3. Play that skill’s activities to earn **skill XP** and **knowledge**. Each skill doc opens with its XP sources and milestones.
4. Open an adaptation under that skill. The menu shows level-scaled metrics (GUI lore).
5. Learn the next level if you have enough knowledge and free ability power (and Vault money if learning economy is on).
6. Use the adaptation in the world as documented under **How it activates** in that skill doc.

Admin shortcuts without the economy path: `/adapt claim-skill`, `/adapt claim-adaptation`, `/adapt determine` (`adapt.determine`).

## Learning an adaptation

1. Earn skill XP until the skill has knowledge available.
2. Open the adaptation and purchase the next level if knowledge and free power allow.
3. Optional Vault charge when `learningEconomy.enabled` and economy is present.
4. If `hardcoreNoRefunds` is true, unlearn may not refund knowledge.

## Using adaptations

Every registered adaptation has a dedicated section in docs `11`–`33` with:

- What it does (English catalog description)
- **How it activates** (player actions / events: sneak, jump, break blocks, craft, combat, ticks, …)
- GUI metrics, default costs/levels, listened events, config knobs

Common gates for any adaptation:

- Adaptation level ≥ 1 and `enabled`
- Skill enabled
- `adapt.use.*` permission tree
- Not creative unless `allowAdaptationsInCreative`
- Current world not listed in `blacklistedWorlds`
- Protectors and WorldGuard `use-adaptations`
- External ability policies

Adaptation-created items such as Backpacks, Chalk Wands, time items, and bound Rift objects require their owning adaptation for special behavior. Administrator-created experience and knowledge orbs are different: throw the snowball item to apply its stored reward, with no learned adaptation requirement. See `36 - Items, Orbs & Bound Objects.md`.

### Where to look up a specific ability

| Need | Doc |
|------|-----|
| Index of all skills + full adaptation usage table | `10 - Skills Catalog.md` |
| One skill’s XP path + every adaptation | `11`–`33` (`NN - Skill - <Name>.md`) |
| XP / knowledge / power model | `02 - Concepts.md` |
| Numbers and curves | `05 - Configuration Math.md` |

## Mutations menu

When mutations are enabled and the player may use them: `/adapt mutations menu` or GUI entry. Mutations are separate from skill adaptations. See `34 - Mutations Overview.md` and `35 - Mutations Catalog.md`.

## See also

- `02 - Concepts.md`
- `04 - Commands & Permissions.md`
- `06 - GUI Customization.md`
- `10 - Skills Catalog.md`
- Skill docs `11`–`33`
