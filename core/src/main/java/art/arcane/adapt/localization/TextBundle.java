package art.arcane.adapt.localization;

import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.api.text.MessageSink;
import art.arcane.volmlib.util.localization.LanguageDirectory;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.VolmitLocales;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.stream.Stream;

/**
 * The messages one namespace contributes: every key with its English default, plus the translations it ships. Admins
 * override a bundle in {@code languages/<namespace>/<locale>.toml}; the built-in {@code adapt} bundle keeps
 * {@code languages/<locale>.toml}.
 */
public final class TextBundle {
  public static final String BUILT_IN = "adapt";
  private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");

  private final String namespace;
  private final MessageCatalog catalog;
  private final Map<String, LocaleOverlay> translations;
  private final List<String> problems;

  private TextBundle(String namespace, MessageCatalog catalog, Map<String, LocaleOverlay> translations, List<String> problems) {
    this.namespace = namespace;
    this.catalog = catalog;
    this.translations = Collections.unmodifiableMap(new LinkedHashMap<>(translations));
    this.problems = List.copyOf(problems);
  }

  /** A bundle declared in code. Translation values the keys do not declare are left out and listed in {@link #problems}. */
  public static TextBundle declared(String namespace, Collection<? extends MessageKey> keys, Collection<LocaleOverlay> translations) {
    String required = requireNamespace(namespace);
    MessageCatalog catalog = MessageCatalog.builder(VolmitLocales.ENGLISH).addAll(keys).build();
    Map<String, LocaleOverlay> accepted = new LinkedHashMap<>();
    List<String> problems = new ArrayList<>();
    for (LocaleOverlay translation : translations) {
      LocaleOverlay valid = LocalizationValidator.validValues(catalog, translation);
      for (String key : translation.values().keySet()) {
        if (!valid.values().containsKey(key)) {
          problems.add(translation.source() + " [" + key + "]: not a declared key with a matching shape and placeholders");
        }
      }
      accepted.put(translation.locale(), valid);
    }
    return new TextBundle(required, catalog, accepted, problems);
  }

  /**
   * A bundle read from {@code <root>/<locale>.toml} files on {@code loader}. {@code en_US.toml} is required and declares
   * the keys; a broken translation file is left out and listed in {@link #problems}.
   */
  public static TextBundle resources(String namespace, ClassLoader loader, String root) {
    String required = requireNamespace(namespace);
    Objects.requireNonNull(loader, "loader");
    String folder = trimSlashes(Objects.requireNonNull(root, "root"));
    String englishPath = resource(folder, VolmitLocales.ENGLISH);
    String english = read(loader, englishPath)
        .orElseThrow(() -> new IllegalArgumentException("Message bundle " + required + " has no " + englishPath));
    List<String> problems = new ArrayList<>();
    LanguageToml.Declared declared = LanguageToml.keys(required + ":" + englishPath, english);
    for (String key : declared.rejected()) {
      problems.add(required + ":" + englishPath + " [" + key + "]: not a text or lines value");
    }
    MessageCatalog catalog = MessageCatalog.builder(VolmitLocales.ENGLISH).addAll(declared.keys()).build();
    Map<String, LocaleOverlay> translations = new LinkedHashMap<>();
    for (String locale : locales(loader, folder)) {
      if (LanguageDirectory.sameLocale(locale, VolmitLocales.ENGLISH)) {
        continue;
      }
      String path = resource(folder, locale);
      String source = required + ":" + path;
      try {
        Optional<String> raw = read(loader, path);
        if (raw.isEmpty()) {
          continue;
        }
        LanguageToml.Read parsed = LanguageToml.overlay(source, locale, raw.get(), catalog);
        for (String key : parsed.rejected()) {
          problems.add(source + " [" + key + "]: not a declared key with a matching shape and placeholders");
        }
        translations.put(locale, parsed.overlay());
      } catch (RuntimeException broken) {
        problems.add(source + ": " + broken.getMessage());
      }
    }
    return new TextBundle(required, catalog, translations, problems);
  }

  /**
   * The bundle of an addon's {@link MessageBundle}s. Their keys are relative to {@code namespace}; the default locale
   * declares them. Throws when one key is given twice in a locale or the default locale is missing.
   */
  public static TextBundle messages(String namespace, List<MessageBundle> bundles) {
    return messages(namespace, bundles, List.of());
  }

  /**
   * As {@link #messages(String, List)}, with {@code defaults} declared too: full keys with English the engine derives,
   * such as config field text. A default the bundles declare in the default locale is theirs; translations may cover
   * either. Without bundles the defaults alone make the bundle.
   */
  public static TextBundle messages(String namespace, List<MessageBundle> bundles, Collection<? extends MessageKey> defaults) {
    String required = requireNamespace(namespace);
    Set<String> locales = new LinkedHashSet<>();
    for (MessageBundle bundle : bundles) {
      locales.addAll(bundle.locales());
    }
    if (!bundles.isEmpty() && !locales.contains(MessageBundle.DEFAULT_LOCALE)) {
      throw new IllegalArgumentException("Messages of " + required + " have no " + MessageBundle.DEFAULT_LOCALE
          + " locale");
    }
    JsonObject english = flatten(required, MessageBundle.DEFAULT_LOCALE, bundles);
    List<String> problems = new ArrayList<>();
    LanguageToml.Declared declared = LanguageToml.keys(english);
    for (String key : declared.rejected()) {
      problems.add(required + ":" + MessageBundle.DEFAULT_LOCALE + " [" + key + "]: not a text or lines value");
    }
    MessageCatalog.Builder keys = MessageCatalog.builder(VolmitLocales.ENGLISH).addAll(declared.keys());
    for (MessageKey key : defaults) {
      if (!english.has(key.id())) {
        keys.add(key);
      }
    }
    MessageCatalog catalog = keys.build();
    Map<String, LocaleOverlay> translations = new LinkedHashMap<>();
    for (String locale : locales) {
      if (LanguageDirectory.sameLocale(locale, VolmitLocales.ENGLISH)) {
        continue;
      }
      String source = required + ":" + locale;
      try {
        LanguageToml.Read parsed = LanguageToml.overlay(source, locale, flatten(required, locale, bundles), catalog);
        for (String key : parsed.rejected()) {
          problems.add(source + " [" + key + "]: not a declared key with a matching shape and placeholders");
        }
        translations.put(locale, parsed.overlay());
      } catch (RuntimeException broken) {
        problems.add(source + ": " + broken.getMessage());
      }
    }
    return new TextBundle(required, catalog, translations, problems);
  }

  private static JsonObject flatten(String namespace, String locale, List<MessageBundle> bundles) {
    JsonObject flat = new JsonObject();
    MessageSink sink = new MessageSink() {
      @Override
      public void message(String key, String text) {
        put(flat, joined(namespace,Objects.requireNonNull(key, "key")), new JsonPrimitive(text), namespace + ":" + locale);
      }

      @Override
      public void toml(String document) {
        read(namespace + ":" + locale, document);
      }

      @Override
      public void toml(String file, String document) {
        read(namespace + ":" + locale + " (" + Objects.requireNonNull(file, "file") + ")", document);
      }

      private void read(String source, String document) {
        flattenInto(flat, namespace, LanguageToml.parse(source, document), source);
      }
    };
    for (MessageBundle bundle : bundles) {
      if (bundle.locales().contains(locale)) {
        bundle.load(locale, sink);
      }
    }
    return flat;
  }

  private static void flattenInto(JsonObject flat, String prefix, JsonObject table, String source) {
    for (Map.Entry<String, JsonElement> entry : table.entrySet()) {
      String key = joined(prefix, entry.getKey());
      JsonElement value = entry.getValue();
      if (value.isJsonObject() && !LanguageToml.isPlural(value.getAsJsonObject())) {
        flattenInto(flat, key, value.getAsJsonObject(), source);
      } else {
        put(flat, key, value, source);
      }
    }
  }

  private static String joined(String prefix, String key) {
    return prefix + "." + key;
  }

  private static void put(JsonObject flat, String key, JsonElement value, String source) {
    if (flat.has(key)) {
      throw new IllegalArgumentException("Message " + key + " is defined twice; " + source + " defines it again");
    }
    flat.add(key, value);
  }

  static TextBundle builtIn(MessageCatalog catalog) {
    return new TextBundle(BUILT_IN, catalog, Map.of(), List.of());
  }

  /**
   * The built-in bundle with the messages the addon of the built-in namespace registers added to the engine's own:
   * its keys, its translations and what was left out reading them. The caller checks that no key is declared twice.
   */
  static TextBundle builtIn(MessageCatalog engine, TextBundle addon) {
    MessageCatalog catalog = MessageCatalog.builder(VolmitLocales.ENGLISH).addAll(engine.keys()).addAll(addon.keys())
        .build();
    return new TextBundle(BUILT_IN, catalog, addon.translations, addon.problems);
  }

  public String namespace() {
    return namespace;
  }

  public List<MessageKey> keys() {
    return catalog.keys();
  }

  /** Shipped translations by locale, each holding only values valid for this bundle's keys. */
  public Map<String, LocaleOverlay> translations() {
    return translations;
  }

  /** What was left out while reading this bundle, one line per file or key. */
  public List<String> problems() {
    return problems;
  }

  MessageCatalog catalog() {
    return catalog;
  }

  Optional<LocaleOverlay> translation(String locale) {
    for (Map.Entry<String, LocaleOverlay> entry : translations.entrySet()) {
      if (LanguageDirectory.sameLocale(entry.getKey(), locale)) {
        return Optional.of(entry.getValue());
      }
    }
    return Optional.empty();
  }

  static String requireNamespace(String namespace) {
    if (namespace == null || !NAMESPACE.matcher(namespace).matches()) {
      throw new IllegalArgumentException("Invalid message bundle namespace: " + namespace);
    }
    return namespace;
  }

  private static String trimSlashes(String root) {
    String trimmed = root.strip();
    while (trimmed.startsWith("/")) {
      trimmed = trimmed.substring(1);
    }
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  private static String resource(String folder, String locale) {
    String file = locale + LanguageDirectory.EXTENSION;
    return folder.isEmpty() ? file : folder + "/" + file;
  }

  private static Optional<String> read(ClassLoader loader, String path) {
    try (InputStream stream = loader.getResourceAsStream(path)) {
      if (stream == null) {
        return Optional.empty();
      }
      byte[] bytes = stream.readNBytes((int) LanguageDirectory.MAXIMUM_BYTES + 1);
      if (bytes.length > LanguageDirectory.MAXIMUM_BYTES) {
        throw new IllegalArgumentException("Language resource is too large: " + path);
      }
      return Optional.of(new String(bytes, StandardCharsets.UTF_8));
    } catch (IOException failure) {
      throw new IllegalArgumentException("Could not read language resource " + path, failure);
    }
  }

  /** Locales beside the English file, listed from a directory or jar when the loader allows it, else probed by name. */
  private static Set<String> locales(ClassLoader loader, String folder) {
    Set<String> locales = new LinkedHashSet<>(VolmitLocales.all());
    URL english = loader.getResource(resource(folder, VolmitLocales.ENGLISH));
    if (english == null) {
      return locales;
    }
    try {
      URLConnection connection = english.openConnection();
      if (connection instanceof JarURLConnection jar) {
        connection.setUseCaches(false);
        try (JarFile file = jar.getJarFile()) {
          String prefix = folder.isEmpty() ? "" : folder + "/";
          Enumeration<JarEntry> entries = file.entries();
          while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.startsWith(prefix) && name.indexOf('/', prefix.length()) < 0) {
              LanguageDirectory.localeFromName(name.substring(prefix.length())).ifPresent(locales::add);
            }
          }
        }
        return locales;
      }
      URI uri = english.toURI();
      Path parent = Path.of(uri).getParent();
      try (Stream<Path> files = Files.list(parent)) {
        files.forEach(path -> LanguageDirectory.localeFromName(path.getFileName().toString()).ifPresent(locales::add));
      }
    } catch (Exception unlisted) {
      return locales;
    }
    return locales;
  }
}
