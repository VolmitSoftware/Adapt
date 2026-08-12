# GUI Customization

Adapt's menus can be reshaped without touching code. You choose how tall the skills menu is, which item represents each skill and adaptation, and what order they appear in. Everything lives in `plugins/Adapt/adapt/adapt.toml` under the `gui` table, plus the one top-level toggle `guiShowAllSkills`.

Every setting on this page hot-reloads. A file watcher polls the Adapt config files twice a second, and a save that parses cleanly re-reads the config and reopens any Adapt window a player currently has on screen, so you see the change without a restart.

There are three menu surfaces. The skills menu is a 5-wide card grid with a navigation row at the bottom, and it is the only one whose height you set directly. The adaptation list inside a skill and the level buttons inside an adaptation are 9-wide and size themselves from their own contents.

Icons resolve through the same lookup on all three surfaces, so one key can change a skill's card, its entry in a list, and its level buttons. If you also use a resource pack, `models.toml` sits above these icon overrides and stays in charge.

## Setting the skills menu height

1. Open `plugins/Adapt/adapt/adapt.toml`.
2. Set `gui.skillsGuiRows` under the `[gui]` table.
3. Save. The watcher picks it up and any open menu reopens at the new size.

Leave it at `0` to let each page size itself to its contents. Set `2` through `6` to pin the window to that many rows on every page. One of those rows is always the navigation row, so a value of `4` gives you three rows of cards, fifteen skills per page.

Auto-sizing behaves differently from a fixed height in one visible way: the viewport is recomputed per page from the cards actually on that page. A last page holding three cards renders as a 2-row window even though the earlier pages were taller.

Bad values are corrected rather than rejected, with one console warning per distinct bad value. The warning set clears when a config reload replaces the config object, so a fresh mistake warns again.

`skillsGuiRows` is one of the fields you can edit from `/adapt configure` in game. The icon and order tables below are maps and lists, which that editor shows read-only, so those have to be edited in the file.

## Changing an icon

1. Find the registry name of the skill or adaptation. Adaptation names look like `stealth-shadowmeld`; skill names are plain, like `stealth`.
2. Add the name as a key under `[gui.skillIcons]` or `[gui.adaptationIcons]`.
3. Set the value to a Bukkit material name.
4. Save.

The value parser is forgiving. It trims whitespace, drops everything up to and including the first `:`, uppercases, and turns spaces into underscores, so `netherite axe`, `NETHERITE_AXE` and `minecraft:netherite_axe` all land on the same material. `AIR` and legacy materials are refused.

Keys are matched exactly first, then case-insensitively. A value Adapt cannot turn into a usable material warns once per key and keeps the built-in icon; the warning names the section, the key, the value you wrote, and the icon it kept.

### When models.toml wins

`plugins/Adapt/adapt/models.toml` is the resource-pack side of icons, and it outranks `gui.skillIcons` and `gui.adaptationIcons` whenever it actually overrides something. Your configured material is folded into the model before the model becomes an item, and the test for "actually overrides" is narrow:

- No configured material, or one equal to the class's hardcoded default, leaves the model untouched.
- A model with a non-zero `model` number, or with a `material` different from the hardcoded default, wins outright and your configured material is ignored.
- Anything else takes your configured material and keeps the model's `modelKey`.

That middle case matters because `models.toml` populates itself. The first lookup for a path writes an entry holding the hardcoded default and `model = 0`. A placeholder like that is not an override, so `gui.skillIcons` still applies over it.

With `customModels = false`, model lookups return the hardcoded fallback with no model number and no key, which is never an override, so the `gui.*Icons` material always applies.

Missing `models.toml` paths are created with defaults on first read and the file is rewritten on a background thread. A legacy `models.json` is migrated to `models.toml` on startup.

## Reordering menus

1. List the skills you want pinned to the front, in order, in `gui.skillOrder`.
2. For adaptations, add a key per skill under `[gui.adaptationOrder]` holding that skill's ordered adaptation names.
3. Save.

Listed names come first, in the order you wrote them. Everything else follows in the normal order: display name with color codes and leading punctuation stripped, then registry name as a case-insensitive tiebreak.

Matching is case-insensitive on the registry name. Blank entries are skipped, and a name repeated in the list keeps its first position. The skill key of `adaptationOrder` is also matched case-insensitively, and if you somehow end up with several differently-cased keys for the same skill their lists are concatenated.

A name that matches no registered skill or adaptation warns once and is then ignored. Disabled skills and adaptations still count as known, so ordering them does not warn, they just are not drawn.

Ordering never makes anything visible. A skill still has to pass the visibility rules below, and an adaptation still has to be enabled and permitted.

## Showing every skill

`guiShowAllSkills` decides whether the skills menu is a progress list or a catalog.

Left off, which is the default, the menu lists only skills the player has touched: xp above zero, knowledge above zero, or at least one learned adaptation level.

Turned on, it lists every enabled skill the player has use permission for. Skills with no stored data are drawn from a throwaway in-memory skill line, so browsing does not write empty lines into player saves.

Use permission is enforced either way. The toggle changes what is shown, not what is usable. `/adapt debug mode` implies the toggle, and on top of that it makes every skill and adaptation report use permission as granted and skips the progress check.

## Reference

### Keys

```toml
guiShowAllSkills = false

[gui]
skillsGuiRows = 0
skillOrder = []

[gui.skillIcons]

[gui.adaptationIcons]

[gui.adaptationOrder]
```

| Key | Default | What it does |
|---|---|---|
| `guiShowAllSkills` | `false` | `true` lists every enabled, permitted skill in the skills menu instead of only skills with progress |
| `gui.skillsGuiRows` | `0` | Skills menu height in rows; `0` auto-sizes per page |
| `gui.skillIcons` | empty | Skill registry name to Bukkit material name |
| `gui.adaptationIcons` | empty | Adaptation registry name to Bukkit material name |
| `gui.skillOrder` | empty | Skill registry names pinned to the front of the skills menu, in this order |
| `gui.adaptationOrder` | empty | Skill registry name to that skill's ordered adaptation registry names |

Two more keys affect these menus and are documented in `01 - Installation & Configuration.md`: `guiBackButton` (default `true`, reserves the navigation row in the 9-wide menus) and `customModels` (default `true`, enables `models.toml` lookups).

### skillsGuiRows values

| Value | Behaviour |
|---|---|
| `0` | Auto-size to the page's contents |
| `2` to `6` | Fixed viewport height, always that many rows |
| `1` | Raised to `2` with a warning, the navigation row needs a row of its own |
| `< 0` | Treated as `0` (auto) with a warning |
| `> 6` | Clamped to `6` with a warning |

### Skills menu layout math

Constants: grid width `5`, maximum window height `6`, one navigation row always reserved. Usable content rows are `rows - 1`.

Fixed height `n` (`n >= 2`): `contentRows = n - 1`, `itemsPerPage = 5 * contentRows`, and the window is `n` rows on every page regardless of how many cards that page holds. Anything past `itemsPerPage` moves to the next page.

| `skillsGuiRows` | Content rows | Skills per page |
|---|---|---|
| `2` | 1 | 5 |
| `3` | 2 | 10 |
| `4` | 3 | 15 |
| `5` | 4 | 20 |
| `6` | 5 | 25 |

Auto (`0`): `contentRows = min(5, ceil(visibleSkills / 5))`, so a page holds up to 25 cards and the rest page. The viewport is then re-derived per page from the cards on it as `max(2, min(6, ceil(pageItems / 5) + 1))`.

Navigation is left-click page step and right-click 5-page jump, with a 5-page jump distance in both the skills menu and the 9-wide menus.

### 9-wide menu layout

Adaptation lists and adaptation level pickers use `GuiLayout.plan` at width `9`. They reserve a navigation row when `guiBackButton` is on, and they force one on regardless once the content exceeds what the window can hold without it. Content rows are `ceil(items / 9)` capped at the available rows, and `itemsPerPage = contentRows * 9` with a floor of `9`.

### Background decorator

The checkerboard decorator reads the live viewport height:

| Row | Condition | Material |
|---|---|---|
| `0` | height >= 3 | Alternating `GRAY_STAINED_GLASS_PANE` / `LIGHT_GRAY_STAINED_GLASS_PANE` |
| `1` | height >= 4 | `BLACK_STAINED_GLASS_PANE` |
| any other | always | Alternating `BLACK_STAINED_GLASS_PANE` / `GRAY_STAINED_GLASS_PANE` |

Below 3 rows the top gradient row is dropped and below 4 rows the black separator row is dropped, so short windows keep a clean alternating background instead of a truncated pattern.

### Icon precedence

```
models.toml (when it actually overrides, and customModels = true)
  > gui.skillIcons / gui.adaptationIcons
  > the hardcoded icon in the skill/adaptation class
```

### models.toml format

`plugins/Adapt/adapt/models.toml`, dotted-path tables holding `material`, `model` and `modelKey`:

```toml
[skill.stealth]
material = "PHANTOM_MEMBRANE"
model = 0
modelKey = "minecraft:empty"

[adaptation.stealth-shadowmeld.icon]
material = "BLACK_DYE"
model = 14
modelKey = "minecraft:empty"

[adaptation.stealth-shadowmeld.level-2]
material = "BLACK_DYE"
model = 15
modelKey = "minecraft:empty"
```

| Path | Used for |
|---|---|
| `skill.<name>` | The skill card in the skills menu |
| `adaptation.<name>.icon` | The adaptation entry in a skill's list |
| `adaptation.<name>.level-<n>` | The level-`n` button in an adaptation's window |
| `snippets.gui.level.<n>` | Fallback level button when the adaptation defines none |

`model` is a custom model data number, `0` meaning none. `modelKey` is an item-model namespaced key, defaulting to `minecraft:empty`.

### Example

```toml
guiShowAllSkills = false

[gui]
skillsGuiRows = 4
skillOrder = ["stealth", "axes", "hunter"]

[gui.skillIcons]
stealth = "PHANTOM_MEMBRANE"
"axes" = "netherite axe"

[gui.adaptationIcons]
stealth-shadowmeld = "minecraft:black_dye"

[gui.adaptationOrder]
stealth = ["stealth-shadowmeld", "stealth-cutpurse"]
```

## See also

- `01 - Installation & Configuration.md`
- `04 - Commands & Permissions.md`
- `00 - Overview.md`
