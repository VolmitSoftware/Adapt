package art.arcane.adapt.bukkit.feature.pack;

import java.util.List;
import java.util.Locale;

/** Parsing of typed prompt answers. */
final class PromptValues {
  static final String CANCEL = "cancel";

  private PromptValues() {
  }

  /** The number in {@code text} clamped to the bounds, or null when it is not a number. */
  static Double number(String text, Double min, Double max) {
    double value;
    try {
      value = Double.parseDouble(text.trim());
    } catch (NumberFormatException invalid) {
      return null;
    }
    if (!Double.isFinite(value)) {
      return null;
    }
    if (min != null) {
      value = Math.max(min, value);
    }
    if (max != null) {
      value = Math.min(max, value);
    }
    return value;
  }

  /** The option {@code text} names, by its 1-based number or its text, or -1. */
  static int choice(String text, List<String> options) {
    String typed = text.trim();
    try {
      int number = Integer.parseInt(typed);
      return number >= 1 && number <= options.size() ? number - 1 : -1;
    } catch (NumberFormatException notNumber) {
      String lower = typed.toLowerCase(Locale.ROOT);
      for (int index = 0; index < options.size(); index++) {
        if (options.get(index).toLowerCase(Locale.ROOT).equals(lower)) {
          return index;
        }
      }
      return -1;
    }
  }

  static String clip(String text, int maxLength) {
    return maxLength > 0 && text.length() > maxLength ? text.substring(0, maxLength) : text;
  }

  static boolean cancels(String text) {
    return text.trim().equalsIgnoreCase(CANCEL);
  }
}
