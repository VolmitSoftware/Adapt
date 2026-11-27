package art.arcane.adapt.localization;

import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads language TOML: nested tables are key paths, strings are text, arrays are lines, tables of plural forms are plurals. */
final class LanguageToml {
  private static final Pattern PARSER_LINE = Pattern.compile("on line (\\d+)");
  private static final Set<String> PLURAL_CATEGORIES = Set.of("zero", "one", "two", "few", "many", "other");

  private LanguageToml() {
  }

  /** The values of {@code raw} that {@code catalog} declares with a matching shape and placeholders. */
  static Read overlay(String source, String locale, String raw, MessageCatalog catalog) {
    return overlay(source, locale, raw == null || raw.isBlank() ? new JsonObject() : parse(source, raw), catalog);
  }

  /** As {@link #overlay(String, String, String, MessageCatalog)} for an already parsed document. */
  static Read overlay(String source, String locale, JsonObject document, MessageCatalog catalog) {
    LocaleOverlay.Builder builder = LocaleOverlay.builder(source, locale);
    List<String> rejected = new ArrayList<>();
    appendOverlay(builder, document, "", catalog, rejected);
    LocaleOverlay parsed = builder.build();
    LocaleOverlay accepted = LocalizationValidator.validValues(catalog, parsed);
    for (String key : parsed.values().keySet()) {
      if (!accepted.values().containsKey(key)) {
        rejected.add(key);
      }
    }
    return new Read(accepted, List.copyOf(rejected));
  }

  /** Declares every text and lines entry of {@code raw} as a key with that entry as its English value. */
  static Declared keys(String source, String raw) {
    return keys(parse(source, raw));
  }

  /** As {@link #keys(String, String)} for an already parsed document. */
  static Declared keys(JsonObject document) {
    Map<String, MessageKey> keys = new LinkedHashMap<>();
    List<String> rejected = new ArrayList<>();
    appendKeys(keys, document, "", rejected);
    return new Declared(List.copyOf(keys.values()), List.copyOf(rejected));
  }

  static JsonObject parse(String source, String raw) {
    JsonElement parsed;
    try {
      parsed = TomlCodec.toJsonElement(raw);
    } catch (IOException invalid) {
      throw new IllegalArgumentException("Locale source is not valid TOML: " + source + problem(invalid, raw), invalid);
    }
    if (parsed == null || !parsed.isJsonObject()) {
      throw new IllegalArgumentException("Locale source is not valid TOML: " + source);
    }
    return parsed.getAsJsonObject();
  }

  /** What the parser objected to, and the line it names as written in {@code raw}; empty when it names nothing. */
  private static String problem(Throwable failure, String raw) {
    Throwable cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    if (cause == failure || cause.getMessage() == null) {
      return "";
    }
    String detail = ": " + cause.getMessage();
    Matcher line = PARSER_LINE.matcher(cause.getMessage());
    if (!line.find()) {
      return detail;
    }
    int number = Integer.parseInt(line.group(1));
    String[] lines = raw.split("\\R", -1);
    return number >= 1 && number <= lines.length
        ? detail + "; line " + number + " reads " + lines[number - 1].strip()
        : detail;
  }

  private static void appendOverlay(LocaleOverlay.Builder builder, JsonObject object, String prefix,
                                    MessageCatalog catalog, List<String> rejected) {
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
      JsonElement value = entry.getValue();
      if (value == null || value.isJsonNull()) {
        continue;
      }
      try {
        if (value.isJsonObject() && catalog.key(key) instanceof PluralKey) {
          builder.plural(key, readPlural(key, value.getAsJsonObject()));
        } else if (value.isJsonObject()) {
          appendOverlay(builder, value.getAsJsonObject(), key, catalog, rejected);
        } else if (value.isJsonArray()) {
          builder.lines(key, readLines(key, value.getAsJsonArray()));
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
          builder.text(key, value.getAsString());
        } else {
          rejected.add(key);
        }
      } catch (IllegalArgumentException invalid) {
        rejected.add(key);
      }
    }
  }

  private static void appendKeys(Map<String, MessageKey> keys, JsonObject object, String prefix, List<String> rejected) {
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
      JsonElement value = entry.getValue();
      if (value == null || value.isJsonNull()) {
        continue;
      }
      try {
        if (value.isJsonObject() && isPlural(value.getAsJsonObject())) {
          Map<String, String> forms = readPlural(key, value.getAsJsonObject());
          keys.put(key, PluralKey.of(key, selector(forms), forms));
        } else if (value.isJsonObject()) {
          appendKeys(keys, value.getAsJsonObject(), key, rejected);
        } else if (value.isJsonArray()) {
          keys.put(key, LinesKey.of(key, readLines(key, value.getAsJsonArray())));
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
          keys.put(key, TextKey.of(key, value.getAsString()));
        } else {
          rejected.add(key);
        }
      } catch (IllegalArgumentException invalid) {
        rejected.add(key);
      }
    }
  }

  /**
   * Whether {@code table} is a plural message: two or more text forms named by plural categories, {@code other} among
   * them, that use one placeholder between them, the number the form is picked by.
   */
  static boolean isPlural(JsonObject table) {
    if (table.size() < 2 || !table.has("other")) {
      return false;
    }
    Map<String, String> forms = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> entry : table.entrySet()) {
      JsonElement value = entry.getValue();
      if (!PLURAL_CATEGORIES.contains(entry.getKey()) || value == null || !value.isJsonPrimitive()
          || !value.getAsJsonPrimitive().isString()) {
        return false;
      }
      forms.put(entry.getKey(), value.getAsString());
    }
    return selector(forms) != null;
  }

  /** The one placeholder the forms use, or null when they use none or several. */
  private static String selector(Map<String, String> forms) {
    Set<String> placeholders = new LinkedHashSet<>();
    for (String form : forms.values()) {
      placeholders.addAll(new TextValue(form).placeholders());
    }
    return placeholders.size() == 1 ? placeholders.iterator().next() : null;
  }

  private static List<String> readLines(String key, JsonArray array) {
    List<String> lines = new ArrayList<>(array.size());
    for (JsonElement value : array) {
      if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("Locale line must be text: " + key);
      }
      lines.add(value.getAsString());
    }
    return lines;
  }

  private static Map<String, String> readPlural(String key, JsonObject object) {
    Map<String, String> forms = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      JsonElement value = entry.getValue();
      if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("Locale plural form must be text: " + key + "." + entry.getKey());
      }
      forms.put(entry.getKey(), value.getAsString());
    }
    return forms;
  }

  /** An overlay of the accepted values and the keys left out of it. */
  record Read(LocaleOverlay overlay, List<String> rejected) {
  }

  /** Keys declared by an English file and the entries that could not become keys. */
  record Declared(List<MessageKey> keys, List<String> rejected) {
  }
}
