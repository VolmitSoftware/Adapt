# Localization

Adapt's English text lives in typed Java catalogs, not in a file you can edit. Seventeen translations ship inside the jar. Everything on disk under `plugins/Adapt/languages/` is either a generated reference you read, or an override you write.

Pick a language with one key in `adapt.toml`. If you want to change wording, put only the keys you care about in an override file. Adapt resolves each key on its own, so an override falls back to the bundled translation, and a key missing from the bundle falls back to English.

Reloads are all-or-nothing and validated before anything goes live. A bad override never reaches players; Adapt logs what was wrong and keeps running on the last good language. Nothing that reads text ever touches disk at runtime, so a reload cannot stall gameplay.

```
plugins/Adapt/
  adapt/adapt.toml            language = "en_US"
  languages/
    en_US.toml                generated reference, always present, never read back
    <locale>.toml             generated copy of the active bundled locale, never read back
    overrides/
      <locale>.toml           yours, sparse, the only file Adapt reads
```

## Picking a language

1. Open `plugins/Adapt/adapt/adapt.toml`.
2. Set `language` to one of the bundled names in the Reference below, matching the spelling exactly. `ja-JP` uses a hyphen; the other sixteen use an underscore.
3. Save. The hotload watcher sees the `adapt.toml` change, reloads the config, and reloads the language with it.

Setting `language` to a name with no bundled translation is not an error. Adapt warns `No bundled locale exists for <locale>; code-owned English will be used.` and runs on English, with your overrides for that name still applied on top.

A name that is not made of letters, digits, underscores and hyphens is rejected outright and the previously loaded language stays live.

## Reading the generated reference

`languages/en_US.toml` is written first thing in every reload, before the locale is even resolved. It is a dump of the code-owned English catalog in the same TOML shape as the bundled files, and it is the authoritative list of every key that exists. Start here when you want to know what a key is called.

`languages/<locale>.toml` appears after a successful reload when `language` is not `en_US`, holding the bundled translation for that locale exactly as it ships. Only the active locale is extracted; the other sixteen stay in the jar. Set `language = "de_DE"` and you get `languages/de_DE.toml` on the next boot. A rejected reload leaves the previous extraction in place.

Both files are regenerated on every boot and every language reload, through a temp file plus an atomic move. Editing them accomplishes nothing: your edits are overwritten and were never read in the first place. Each carries a four-line header saying so. A failed write warns and is otherwise ignored, it never blocks language loading.

## Overriding text

1. Find the key you want in `languages/en_US.toml`.
2. Create `plugins/Adapt/languages/overrides/<locale>.toml`, where `<locale>` matches your configured `language` exactly. The folder is created on startup if it is missing.
3. Copy in just that key, keeping its table path and its value shape.
4. Keep every placeholder the original has. Placeholders are `{name}` and the reload validates them before publishing.
5. Save. The watcher notices any `.toml` change in the overrides folder and reloads the language.

```toml
# languages/overrides/de_DE.toml
[gui.skills]
title = "&5Stufe {level} &7({used}/{maximum} Leistung)"
```

Overrides are sparse by design. Copy in only the keys you want to change; everything absent resolves through the fallback chain.

Colors use `&` codes and are translated when the message renders. Runtime values substituted into a message are either trusted, meaning they render as markup, or untrusted, meaning colors are stripped and `&` becomes a full-width `＆`. The code that raises the message decides which, and an override cannot promote an untrusted value.

## Watching a reload

A successful reload logs `Loaded locale <name> with N fallback entries.` `N` is the validator's warning count, one per key that an overlay does not define. Overlays are sparse on purpose, so a large `N` is normal and is not a problem signal.

A rejected reload logs `Rejected locale reload for <locale>; continuing with <active>.` followed by up to twelve specific issues formatted as `source [key]: detail`, then a count of any omitted issues. The last good language stays live.

An override reload triggered by the watcher also re-synchronizes advancement titles, so new text reaches the advancement tree.

## Reference

### Bundled locales

Seventeen translations ship inside the jar. English is not one of them, it comes from code.

`de_DE` `es_ES` `fi_FI` `fr_FR` `he_IL` `it_IT` `ja-JP` `ko_KR` `lt_LT` `nl_NL` `pl_PL` `pt_PT` `ru_RU` `tr_TR` `vi_VI` `zh_CN` `zh_TW`

### Config keys

| Key | Default | What it does |
|---|---|---|
| `language` | `"en_US"` | Locale name to load. Must match `[A-Za-z0-9_-]+`; an invalid name rejects the reload |
| `automaticGradients` | `false` | Applies a gradient pass over rendered markup after `&` codes are translated |

### Value shapes

The key's declaration in the catalog dictates the shape. Use the shape you see in `languages/en_US.toml`.

| Shape | TOML |
|---|---|
| Text | `key = "one line"` |
| Lines | `key = ["first", "second"]` |
| Plural | `[path.key]` table with one entry per plural form |

### Precedence

```
languages/overrides/<locale>.toml   >   bundled <locale>.toml   >   code-owned English
```

Resolution is per key, not per file.

### What rejects a reload

Any one of these fails validation and keeps the previous language:

| Condition | Reported as |
|---|---|
| Key not declared by the message catalog | `Locale overlay key is not declared by the message catalog` |
| Wrong value shape for the key | `Expected <shape> but found <shape>` |
| Lines value with a different line count than English | `Expected N lines but found M` |
| Plural table whose form keys differ from English | `Expected plural forms [...] but found [...]` |
| Placeholder set differs from the key's declaration | `Expected {...} but found {...}` |
| A `null` value anywhere in the file | `Locale value cannot be null: <key>` |
| File is not valid TOML | `Locale source is not valid TOML: <source>` |
| Override file larger than 2 MiB | `Locale override is too large: <path>` |
| `language` value not matching `[A-Za-z0-9_-]+` | `Invalid locale name: <value>` |

Missing keys are warnings, not errors. Only errors block a reload.

### Limits and behaviour

| Item | Value |
|---|---|
| Maximum override file size | 2 MiB |
| Issues logged per rejected reload | 12, then a count of the remainder |
| Override watcher poll interval | 500 ms |
| Watched for language reloads | `languages/overrides/*.toml`; `adapt/adapt.toml` also reloads the language as part of its config reload |
| Not watched | `languages/en_US.toml`, `languages/<locale>.toml` |
| Generated file write | Temp file plus atomic move, falling back to a plain move |

### Catalog layout

Message keys are declared in `art.arcane.adapt.localization.catalog`, one class per area: `GuiMessages`, `CommandMessages`, `RuntimeMessages`, `SnippetsMessages`, `AdvancementMessages`, `MutationMessages`, `ItemsMessages`, `ConfigMessages`, plus one per skill (`AgilityMessages`, `AxeMessages`, and so on). Key ids are dotted paths, which become the TOML table structure in the generated files.

## See also

- `01 - Installation & Configuration.md`
- `06 - GUI Customization.md`
- `00 - Overview.md`
