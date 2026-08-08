# Localization

Adapt's canonical English lives in typed Java catalogs, not in a resource file. Everything on disk under
`plugins/Adapt/languages/` is either a generated reference you read, or an override you write.

```
plugins/Adapt/
  adapt/adapt.toml            language = "en_US"
  languages/
    en_US.toml                generated reference, always present, never read back
    <locale>.toml             generated copy of the active bundled locale, never read back
    overrides/
      <locale>.toml           yours; sparse; this is the only file Adapt reads
```

---

## Picking a language

```toml
# adapt/adapt.toml
language = "en_US"
```

The name must match `[A-Za-z0-9_-]+`. Anything else is rejected and the previous language stays loaded.

### Bundled locales

Seventeen translations ship inside the jar. English is not one of them — it comes from code.

`de_DE` · `es_ES` · `fi_FI` · `fr_FR` · `he_IL` · `it_IT` · `ja-JP` · `ko_KR` · `lt_LT` · `nl_NL` · `pl_PL` ·
`pt_PT` · `ru_RU` · `tr_TR` · `vi_VI` · `zh_CN` · `zh_TW`

`ja-JP` uses a hyphen; the other sixteen use an underscore. Use the name exactly as listed.

Setting `language` to something with no bundled translation is not an error: Adapt warns
`No bundled locale exists for <locale>; code-owned English will be used.` and runs on English, with your
overrides for that name still applied on top.

---

## Generated reference files

Both files below are regenerated on **every boot and every language reload**, through a temp file plus an
atomic move. Editing them accomplishes nothing — your edits are overwritten and were never read in the
first place. Each carries a four-line header saying so.

**`languages/en_US.toml`** — written first thing in every reload, before the locale is even resolved. A dump
of the code-owned English catalog rendered in the same TOML shape as the bundled files. This is the
authoritative list of every key that exists.

**`languages/<locale>.toml`** — written after a reload succeeds, when `language` is not `en_US`, holding the
bundled translation for that locale exactly as it ships. Only the active locale is extracted; the other
sixteen stay in the jar. Set `language = "de_DE"` and you get `languages/de_DE.toml` on the next boot. A
rejected reload leaves the previous extraction in place.

A failed write warns and is otherwise ignored — it never blocks language loading.

---

## Overriding text

Create `plugins/Adapt/languages/overrides/<locale>.toml`, where `<locale>` matches your configured
`language` exactly. The folder is created on startup if missing.

An override file larger than 2 MiB is rejected.

Overrides are **sparse**. Copy in only the keys you want to change; everything absent resolves through the
chain below.

```toml
# languages/overrides/de_DE.toml
[gui.skills]
title = "&5Stufe {level} &7({used}/{maximum} Leistung)"
```

### Precedence

```
languages/overrides/<locale>.toml   >   bundled <locale>.toml   >   code-owned English
```

Resolution is per key, not per file. A key you did not override falls back to the bundled translation; a key
missing from the bundle falls back to English.

### Value shapes

Three shapes, dictated by the key's declaration in the catalog:

| Shape | TOML |
|---|---|
| Text | `key = "one line"` |
| Lines | `key = ["first", "second"]` |
| Plural | `[path.key]` table with one entry per plural form |

An override that uses the wrong shape for a key, or a value that is `null`, rejects the whole reload.

### Placeholders and formatting

Placeholders are `{name}`. Keep every placeholder the original has; the reload validates them before
publishing.

Colors use `&` codes and are translated when the message renders. Runtime values substituted into a message
are either trusted (rendered as markup) or untrusted (colors stripped, `&` replaced with a full-width `＆`),
decided by the code that raises the message — an override cannot promote an untrusted value.
`automaticGradients` in `adapt.toml` applies a gradient pass over rendered markup.

---

## Reloading

The config hotload watcher polls `languages/overrides/` and reloads the language when any `.toml` in that
folder changes. It does not watch the generated reference files.

A reload is all-or-nothing. Keys, value shapes and placeholders are validated first; only a clean candidate
replaces the live snapshot. On failure Adapt logs
`Rejected locale reload for <locale>; continuing with <active>.` followed by up to 12 specific issues
(`source [key]: detail`) and keeps the last good language. Nothing that reads text ever touches disk, so a
reload cannot stall gameplay.

A successful reload logs `Loaded locale <name> with N fallback entries.` — `N` is the validator's warning
count, one per key each overlay does not define. Overlays are sparse by design, so a large `N` is normal and
is not a problem signal; the errors are what block a reload.

An override reload triggered by the watcher also re-synchronizes advancement titles so the new text reaches
the advancement tree.

## See also

- `01 - Installation & Configuration.md`
- `00 - Overview.md`
