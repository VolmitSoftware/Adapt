package art.arcane.adapt.localization;

import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Map;

/**
 * The keys of the built-in namespace. Every built-in message is {@code adapt.<key>}, the same as an addon's
 * {@code <namespace>.<key>}; engine code names them by {@code <key>} and these factories add the namespace.
 */
public final class AdaptKeys {
  public static final String PREFIX = TextBundle.BUILT_IN + ".";

  private AdaptKeys() {
  }

  /** {@code adapt.<key>} */
  public static String id(String key) {
    return PREFIX + key;
  }

  public static TextKey text(String key, String english) {
    return TextKey.of(id(key), english);
  }

  public static PluralKey plural(String key, String selectorArgument, Map<String, String> english) {
    return PluralKey.of(id(key), selectorArgument, english);
  }

  /** {@code key}, a library's message, as the built-in namespace declares it. */
  static MessageKey adopted(MessageKey key) {
    return switch (key) {
      case TextKey text -> new TextKey(id(text.id()), text.english(), text.optionalPlaceholders());
      case LinesKey lines -> LinesKey.of(id(lines.id()), lines.english());
      case PluralKey plural -> PluralKey.of(id(plural.id()), plural.selectorArgument(), plural.english());
    };
  }
}
