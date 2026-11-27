package art.arcane.adapt.api.text;

import art.arcane.adapt.api.asset.JarResources;
import org.jetbrains.annotations.ApiStatus;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An addon's messages, in every language its author supplies.
 *
 * <p>A bundle belongs to the namespace of the addon that registers it, and its keys are relative to that namespace:
 * the entry {@code skill.alchemy.name} registered by the {@code alchemist} addon is the message
 * {@code alchemist.skill.alchemy.name}. A bundle cannot define a message outside its addon's namespace.
 * {@link #DEFAULT_LOCALE} is required; a message missing from another locale falls back to it.
 *
 * <p>Config field text needs no default-locale entry: Adapt declares the English of every config field of the
 * addon's registered content from the field name and its {@code @ConfigDoc} (see {@link TextKeys#skillConfigField}),
 * so a bundle can ship translated config labels without restating the English ones. A default-locale entry for such a
 * key replaces the derived English.
 *
 * <p>An admin overrides any message in {@code <data>/languages/<namespace>/<locale>.toml}.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface MessageBundle {
  /** The locale every bundle must provide and every lookup falls back to. */
  String DEFAULT_LOCALE = "en_US";

  /** The locales this bundle has messages for, {@link #DEFAULT_LOCALE} among them. */
  List<String> locales();

  /** Feeds one locale's messages to the engine. Called again whenever the message catalog is rebuilt. */
  void load(String locale, MessageSink sink);

  /**
   * Reads {@code <root>/<locale>.toml} files from the jar that holds {@code owner}.
   *
   * @param owner any class of the addon, used to find its jar
   * @param root  resource folder of the locale files, such as {@code adapt/lang}
   */
  static MessageBundle resources(Class<?> owner, String root) {
    Objects.requireNonNull(owner, "owner");
    String normalizedRoot = JarResources.normalizeRoot(root);
    List<String> locales = new ArrayList<>();
    for (String file : JarResources.list(owner, normalizedRoot)) {
      if (file.endsWith(".toml") && file.indexOf('/') < 0) {
        locales.add(file.substring(0, file.length() - ".toml".length()));
      }
    }
    if (!locales.contains(DEFAULT_LOCALE)) {
      throw new IllegalArgumentException("No " + DEFAULT_LOCALE + ".toml under " + normalizedRoot + " in the jar of "
          + owner.getName());
    }
    List<String> found = List.copyOf(locales);
    return new MessageBundle() {
      @Override
      public List<String> locales() {
        return found;
      }

      @Override
      public void load(String locale, MessageSink sink) {
        String file = normalizedRoot + "/" + locale + ".toml";
        sink.toml(file, new String(JarResources.read(owner, file), StandardCharsets.UTF_8));
      }
    };
  }

  /** Starts a bundle whose messages are written in code. */
  static Builder builder() {
    return new Builder();
  }

  /** Collects messages written in code; {@link #build()} freezes them. */
  final class Builder {
    private final Map<String, Map<String, String>> messages = new LinkedHashMap<>();

    private Builder() {
    }

    /** Adds one message; {@code key} is relative to the addon's namespace. */
    public Builder message(String locale, String key, String text) {
      Objects.requireNonNull(locale, "locale");
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(text, "text");
      String previous = messages.computeIfAbsent(locale, (String added) -> new LinkedHashMap<>()).put(key, text);
      if (previous != null) {
        throw new IllegalArgumentException("Message '" + key + "' is defined twice for " + locale);
      }
      return this;
    }

    /** Adds one {@link MessageBundle#DEFAULT_LOCALE} message. */
    public Builder message(String key, String text) {
      return message(DEFAULT_LOCALE, key, text);
    }

    /**
     * The finished bundle.
     *
     * @throws IllegalStateException when no {@link MessageBundle#DEFAULT_LOCALE} message was added
     */
    public MessageBundle build() {
      if (!messages.containsKey(DEFAULT_LOCALE)) {
        throw new IllegalStateException("A message bundle needs " + DEFAULT_LOCALE + " messages");
      }
      Map<String, Map<String, String>> frozen = new LinkedHashMap<>();
      for (Map.Entry<String, Map<String, String>> locale : messages.entrySet()) {
        frozen.put(locale.getKey(), Map.copyOf(locale.getValue()));
      }
      List<String> locales = List.copyOf(frozen.keySet());
      return new MessageBundle() {
        @Override
        public List<String> locales() {
          return locales;
        }

        @Override
        public void load(String locale, MessageSink sink) {
          Map<String, String> entries = frozen.get(locale);
          if (entries == null) {
            throw new IllegalArgumentException("This message bundle has no locale " + locale);
          }
          for (Map.Entry<String, String> entry : entries.entrySet()) {
            sink.message(entry.getKey(), entry.getValue());
          }
        }
      };
    }
  }
}
