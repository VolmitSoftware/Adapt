# GUI Customization

Adapt menu size, icons, and ordering are configured in `plugins/Adapt/adapt/adapt.toml`. Every key on this
page hot-reloads; a successful save re-reads the config and refreshes open Adapt windows.

```toml
guiShowAllSkills = false

[gui]
skillsGuiRows = 0
skillOrder = []

[gui.skillIcons]

[gui.adaptationIcons]

[gui.adaptationOrder]
```

---

## Window size

```
gui.skillsGuiRows = 0
```

Applies to the **main skills menu only**. Adaptation lists and adaptation level pickers are 9-wide and
size themselves from their own content (`GuiLayout.plan`); they reserve a navigation row when
`guiBackButton` is on.

| Value | Behaviour |
|---|---|
| `0` | Auto-size to the page's contents |
| `2` … `6` | Fixed viewport height, always that many rows |
| `1` | Raised to `2` with a warning — the navigation row needs a row of its own |
| `< 0` | Treated as `0` (auto) with a warning |
| `> 6` | Clamped to `6` with a warning |

Each distinct bad value warns once; the warning set clears when a reload replaces the config object.

The skills menu is a 5-wide card grid with one navigation row at the bottom, so the usable content rows are
always `rows - 1`.

**Fixed (`skillsGuiRows = n`, `n >= 2`)** — `contentRows = n - 1`, `itemsPerPage = 5 * contentRows`, and the
window is `n` rows on every page regardless of how many cards that page holds. Anything beyond
`itemsPerPage` pages.

| `skillsGuiRows` | Content rows | Skills per page |
|---|---|---|
| `2` | 1 | 5 |
| `3` | 2 | 10 |
| `4` | 3 | 15 |
| `5` | 4 | 20 |
| `6` | 5 | 25 |

**Auto (`skillsGuiRows = 0`)** — `contentRows = min(5, ceil(visibleSkills / 5))`, so the page holds up to 25
cards and pages beyond that. The *viewport* is then re-derived per page from the cards actually on it:
`max(2, min(6, ceil(pageItems / 5) + 1))`. A last page with three cards renders as a 2-row window even
though earlier pages were taller.

The checkerboard decorator reads the live viewport height. Below 3 rows the top gradient row is dropped and
below 4 rows the black separator row is dropped, so short windows keep a clean alternating background
instead of a truncated pattern.

---

## Icon overrides

```toml
[gui.skillIcons]
stealth = "PHANTOM_MEMBRANE"
"axes" = "netherite axe"

[gui.adaptationIcons]
stealth-shadowmeld = "minecraft:black_dye"
```

- **Key** is the skill or adaptation **registry name** (`getName()`), not the display name. Lookup is exact
  first, then case-insensitive.
- **Value** is a Bukkit `Material` name. Parsing trims whitespace, drops everything up to and including the
  first `:` (so `minecraft:diamond` works), uppercases, and converts spaces to underscores. `netherite axe`,
  `NETHERITE_AXE` and `minecraft:netherite_axe` are all accepted.
- `AIR` and legacy materials are rejected.
- An unusable value warns **once per key** and keeps the built-in icon. The warning names the section, the
  key, the value, and the icon it kept.

The same resolver serves all three surfaces: the skills menu, the adaptation list inside a skill, and the
per-level buttons in an adaptation's window.

### Precedence with models.toml

```
models.toml (when it actually overrides, and customModels = true)
  > gui.skillIcons / gui.adaptationIcons
  > the hardcoded icon in the skill/adaptation class
```

The configured material is folded into the `CustomModel` **before** it becomes an item, so a real
`models.toml` entry still wins. "Actually overrides" is a precise test:

- no configured material, or it equals the hardcoded default → the model is returned untouched
- the model has a non-zero `model` number, **or** a `material` different from the hardcoded default →
  `models.toml` wins and the configured material is ignored
- otherwise the configured material replaces the model's material, keeping its `modelKey`

That middle case matters because `models.toml` is self-populating: the first lookup for a path writes an
entry holding the hardcoded default and `model = 0`. Such a placeholder is not an override, so
`gui.skillIcons` still applies over it.

With `customModels = false`, `CustomModel.get` returns the hardcoded fallback with `model = 0` and no key,
which is never an override — so the `gui.*Icons` material always applies.

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

Paths in use:

| Path | Used for |
|---|---|
| `skill.<name>` | The skill card in the skills menu |
| `adaptation.<name>.icon` | The adaptation entry in a skill's list |
| `adaptation.<name>.level-<n>` | The level-`n` button in an adaptation's window |
| `snippets.gui.level.<n>` | Fallback level button when the adaptation defines none |

`model` is a custom model data number (`0` = none); `modelKey` is an item-model namespaced key. Missing
paths are created with defaults on first read and the file is rewritten asynchronously. A legacy
`models.json` is migrated to `models.toml` on startup.

---

## Ordering

```toml
[gui]
skillOrder = ["stealth", "axes", "hunter"]

[gui.adaptationOrder]
stealth = ["stealth-shadowmeld", "stealth-cutpurse"]
```

- `skillOrder` orders the skills menu; `adaptationOrder.<skill>` orders the adaptation list inside one
  skill. The skill key of `adaptationOrder` is matched case-insensitively; several differently-cased keys
  for the same skill are concatenated.
- Listed names come first, in list order. Everything else follows in the normal order: display name, then
  registry name as a case-insensitive tiebreak.
- Matching is case-insensitive on the registry name. Blank entries are skipped and a repeated name keeps
  its first position.
- A name that matches no registered skill or adaptation warns **once** and is ignored. Disabled skills and
  adaptations are still "known", so ordering them does not warn — they simply are not drawn.

Ordering does not make anything visible. A skill still has to pass the visibility rules below, and an
adaptation still has to be enabled and permitted.

---

## `guiShowAllSkills`

```
guiShowAllSkills = false
```

Off (the default), the skills menu lists only skills the player has touched: xp above zero, knowledge above
zero, or at least one learned adaptation level.

On, every enabled skill the player has use permission for is listed. Skills with no stored data are drawn
from a transient in-memory skill line, so browsing does not write empty lines into player saves.

Use permission is enforced either way — the toggle changes what is *shown*, not what is usable.
`/adapt debug mode` implies this toggle and additionally bypasses use permissions and the progress check.

## See also

- `01 - Installation & Configuration.md`
- `00 - Overview.md`
