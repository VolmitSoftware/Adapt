package art.arcane.adapt.api.text;

import org.jetbrains.annotations.ApiStatus;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Number and duration formatting for stat lines and HUD text. Output does not depend on the server's locale and
 * matches what built-in content printed before this class existed, with one exception: whole numbers above
 * 16,777,216 passed to {@code f(x, places)} or {@code duration} print exactly, where the old helper rounded them.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class Format {
  private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);
  private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
  private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};

  private Format() {
  }

  /** A whole number with thousands separators: {@code 12,500}. */
  public static String f(long value) {
    return NumberFormat.getInstance(Locale.US).format(value);
  }

  /** {@link #f(double, int)} with one decimal: {@code f(2.25)} is {@code 2.2}, {@code f(3.0)} is {@code 3}. */
  public static String f(double value) {
    return f(value, 1);
  }

  /**
   * A number with at most {@code places} decimals, rounded half to even, with no trailing zeros and no thousands
   * separators: {@code f(2.50, 2)} is {@code 2.5}, {@code f(12345.678, 0)} is {@code 12346}.
   */
  public static String f(double value, int places) {
    StringBuilder pattern = new StringBuilder("#");
    if (places > 0) {
      pattern.append('.');
      for (int index = 0; index < places; index++) {
        pattern.append('#');
      }
    }
    return new DecimalFormat(pattern.toString(), SYMBOLS).format(value);
  }

  /** A fraction as a percentage: {@code pc(0.125, 1)} is {@code 12.5%}. */
  public static String pc(double fraction, int places) {
    return f(fraction * 100.0D, places) + "%";
  }

  /** A fraction as a whole percentage: {@code pc(0.125)} is {@code 12%}. */
  public static String pc(double fraction) {
    return pc(fraction, 0);
  }

  /**
   * Milliseconds in short units, the form adaptation descriptions use: {@code 850ms}, {@code 4.5s}, {@code 3m},
   * then {@code 2 hours}. From one day the value printed is the number of days times 60, and from seven days it is
   * the milliseconds again ({@code 604800000ms}); descriptions never reach either.
   */
  public static String duration(double millis, int places) {
    if (millis < 1000.0D) {
      return f(millis, places) + "ms";
    }
    double seconds = millis / 1000.0D;
    if (seconds < 60.0D) {
      return f(seconds, places) + "s";
    }
    if (seconds / 60.0D < 60.0D) {
      return f(seconds / 60.0D, places) + "m";
    }
    if (seconds / 60.0D / 60.0D < 24.0D) {
      return f(seconds / 60.0D / 60.0D, places) + " hours";
    }
    if (seconds / 60.0D / 60.0D / 24.0D < 7.0D) {
      return f(seconds / 60.0D / 24.0D, places) + " days";
    }
    return f(millis, places) + "ms";
  }

  /**
   * Milliseconds in long units: {@code 850ms}, {@code 4.5 seconds}, {@code 3 minutes}, {@code 2 hours},
   * {@code 3 days}; from seven days the milliseconds again ({@code 604800000ms}).
   */
  public static String duration(long millis, int places) {
    if (millis < 1000L) {
      return f(millis, places) + "ms";
    }
    double seconds = millis / 1000.0D;
    if (seconds < 60.0D) {
      return f(seconds, places) + " seconds";
    }
    if (seconds / 60.0D < 60.0D) {
      return f(seconds / 60.0D, places) + " minutes";
    }
    if (seconds / 60.0D / 60.0D < 24.0D) {
      return f(seconds / 60.0D / 60.0D, places) + " hours";
    }
    if (seconds / 60.0D / 60.0D / 24.0D < 7.0D) {
      return f(seconds / 60.0D / 60.0D / 24.0D, places) + " days";
    }
    return f(millis, places) + "ms";
  }

  /** {@link #duration(long, int)} with no decimals: {@code 4500} is {@code 4 seconds}. */
  public static String duration(long millis) {
    return duration(millis, 0);
  }

  /**
   * Trims {@code text}, then upper-cases the first letter of every space-separated word and trims each word:
   * {@code "  padded  words "} gives {@code "Padded  Words"}.
   */
  public static String capitalizeWords(String text) {
    StringBuilder result = new StringBuilder(text.length());
    for (String word : text.trim().split(" ")) {
      String trimmed = word.trim();
      result.append(' ');
      if (!trimmed.isEmpty()) {
        result.append(Character.toUpperCase(trimmed.charAt(0))).append(trimmed, 1, trimmed.length());
      }
    }
    return result.substring(1);
  }

  /** A level as a roman numeral; values below 1 give an empty string. */
  public static String toRoman(int value) {
    StringBuilder roman = new StringBuilder();
    int remaining = value;
    for (int index = 0; index < ROMAN_VALUES.length; index++) {
      while (remaining >= ROMAN_VALUES[index]) {
        roman.append(ROMAN_SYMBOLS[index]);
        remaining -= ROMAN_VALUES[index];
      }
    }
    return roman.toString();
  }
}
