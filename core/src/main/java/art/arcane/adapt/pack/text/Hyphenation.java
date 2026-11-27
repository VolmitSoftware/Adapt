package art.arcane.adapt.pack.text;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Where a word too wide for its line breaks with an added hyphen, by the native client's rule: at the last point that
 * fits with its hyphen where a syllable seems to end, {@link #EDGE} letters in on each side, else at the last point
 * that fits.
 */
final class Hyphenation {
  static final int EDGE = 4;
  static final String HYPHEN = "-";
  private static final String VOWELS = "aeiouy";
  /** The Cyrillic vowels, lower case, and the Turkish dotless i, by code point. */
  private static final int[] OTHER_VOWELS = {0x0131, 0x0430, 0x0435, 0x0438, 0x043E, 0x0443, 0x044B, 0x044D, 0x044E, 0x044F, 0x0456};
  /** Consonant pairs that open a syllable together, as in "gr" of "Carto|grapher", and are not split. */
  private static final List<String> ONSETS = List.of("bl", "br", "ch", "cl", "cr", "dr", "fl", "fr", "gl", "gr", "kl", "kr", "ph", "pl",
      "pr", "sh", "th", "tr", "wh");

  private Hyphenation() {
  }

  /** How many letters of {@code word} go before the hyphen on a line {@code width} wide; 0 when not even one fits. */
  static int breakAt(PackFontMetrics font, String word, int width) {
    int fallback = 0;
    int syllable = 0;
    for (int index = 1; index < word.length(); index++) {
      if (!font.fits(word.substring(0, index) + HYPHEN, width)) {
        break;
      }
      fallback = index;
      if (index >= EDGE && word.length() - index >= EDGE && syllable(word, index)) {
        syllable = index;
      }
    }
    return syllable > 0 ? syllable : fallback;
  }

  /**
   * A break before one consonant and its vowel, as in "Ma|son", before a pair that opens a syllable and its vowel, as in
   * "Carto|grapher", or between two consonants that do not open one together, as in "Conver|gence".
   */
  private static boolean syllable(String word, int index) {
    boolean vowelBefore = vowel(word.charAt(index - 1));
    boolean consonantAfter = !vowel(word.charAt(index)) && Character.isLetter(word.charAt(index));
    boolean vowelNext = index + 1 < word.length() && vowel(word.charAt(index + 1));
    boolean onsetNext = onset(word, index) && index + 2 < word.length() && vowel(word.charAt(index + 2));
    return vowelBefore && consonantAfter && (vowelNext || onsetNext) || pair(word, index);
  }

  private static boolean onset(String word, int index) {
    return index + 2 <= word.length() && ONSETS.contains(word.substring(index, index + 2).toLowerCase(Locale.ROOT));
  }

  private static boolean vowel(char letter) {
    char bare = Character.toLowerCase(Normalizer.normalize(String.valueOf(letter), Normalizer.Form.NFD).charAt(0));
    if (VOWELS.indexOf(bare) >= 0) {
      return true;
    }
    for (int other : OTHER_VOWELS) {
      if (bare == other) {
        return true;
      }
    }
    return false;
  }

  private static boolean pair(String word, int index) {
    char before = word.charAt(index - 1);
    char after = word.charAt(index);
    return Character.isLetter(before) && Character.isLetter(after) && !vowel(before) && !vowel(after) && !onset(word, index - 1)
        && hasVowel(word.substring(0, index)) && hasVowel(word.substring(index));
  }

  private static boolean hasVowel(String part) {
    for (int index = 0; index < part.length(); index++) {
      if (vowel(part.charAt(index))) {
        return true;
      }
    }
    return false;
  }
}
