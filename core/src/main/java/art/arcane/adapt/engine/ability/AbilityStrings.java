package art.arcane.adapt.engine.ability;

import java.util.Locale;

/** The text rules of the ability values: control characters blanked, at most 128 characters, ids lower case. */
final class AbilityStrings {
  private static final int MAX_LENGTH = 128;
  private static final char DELETE = '\u007F';

  private AbilityStrings() {
  }

  static String sanitize(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    String trimmed = value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    StringBuilder builder = new StringBuilder(trimmed.length());
    for (int index = 0; index < trimmed.length(); index++) {
      char character = trimmed.charAt(index);
      builder.append(character < ' ' || character == DELETE ? ' ' : character);
    }
    return builder.toString().strip();
  }

  static String requireId(String value, String field) {
    if (value == null) {
      throw new NullPointerException(field);
    }
    String id = sanitize(value).toLowerCase(Locale.ROOT);
    if (id.isEmpty()) {
      throw new IllegalArgumentException("an ability " + field + " must not be blank");
    }
    return id;
  }
}
