package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.LegacyText;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A name set on a few short lines: words wrap at spaces and after their own hyphens, a word too wide for a line breaks
 * with a hyphen where a vowel meets a consonant, {@link CardName#SHORT} letters in on each side where it can, and only
 * text past the last line is cut short.
 */
final class NameLines {
    private static final String VOWELS = "aeiouy";
    /** The Cyrillic vowels, lower case, and the Turkish dotless i, by code point. */
    /** Consonant pairs that open a syllable together, as in "gr" of "Carto|grapher", and are not split. */
    private static final List<String> ONSETS = List.of("bl", "br", "ch", "cl", "cr", "dr", "fl", "fr", "gl", "gr", "kl", "kr", "ph", "pl",
            "pr", "sh", "th", "tr", "wh");
    private static final int[] OTHER_VOWELS = {0x0131, 0x0430, 0x0435, 0x0438, 0x043E, 0x0443, 0x044B, 0x044D, 0x044E, 0x044F, 0x0456};

    /** What joins a line to the next in the name: a space, the line's own hyphen, or a hyphen added to break a word. */
    private enum Join {
        SPACE,
        HYPHEN,
        BROKEN
    }

    private NameLines() {
    }

    static List<String> fit(UiDraw metrics, String name, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        List<Join> joins = new ArrayList<>();
        String line = "";
        for (String word : name.trim().split("\\s+")) {
            String[] parts = word.split("(?<=-)(?=.)");
            for (int index = 0; index < parts.length; index++) {
                Join join = index == 0 ? Join.SPACE : Join.HYPHEN;
                String rest = parts[index];
                while (!rest.isEmpty()) {
                    String joined = line.isEmpty() ? rest : line + (join == Join.SPACE ? " " : "") + rest;
                    if (metrics.textWidth(joined) <= width) {
                        line = joined;
                        rest = "";
                    } else if (!line.isEmpty()) {
                        lines.add(line);
                        joins.add(join);
                        line = "";
                    } else {
                        int cut = breakAt(metrics, rest, width);
                        lines.add(rest.substring(0, cut) + "-");
                        joins.add(Join.BROKEN);
                        rest = rest.substring(cut);
                    }
                }
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        if (lines.size() <= maxLines) {
            return lines;
        }
        List<String> kept = new ArrayList<>(lines.subList(0, maxLines - 1));
        StringBuilder tail = new StringBuilder();
        for (int index = maxLines - 1; index < lines.size(); index++) {
            String part = lines.get(index);
            Join join = index < joins.size() ? joins.get(index) : null;
            tail.append(join == Join.BROKEN ? part.substring(0, part.length() - 1) : part);
            if (join == Join.SPACE) {
                tail.append(' ');
            }
        }
        kept.add(LegacyText.truncate(metrics, tail.toString().trim(), width));
        return kept;
    }

    /** Where a word too wide for {@code width} breaks: the last syllable-like point that fits with its hyphen. */
    static int breakAt(UiDraw metrics, String word, int width) {
        int fallback = 1;
        int syllable = -1;
        for (int index = 1; index < word.length(); index++) {
            if (metrics.textWidth(word.substring(0, index) + "-") > width) {
                break;
            }
            fallback = index;
            if (index >= CardName.SHORT && word.length() - index >= CardName.SHORT && syllable(word, index)) {
                syllable = index;
            }
        }
        return syllable > 0 ? syllable : fallback;
    }

    /** Where {@code word} may break inside with an added hyphen: where a syllable seems to end, {@code edge} letters in at least. */
    static List<Integer> syllables(String word, int edge) {
        List<Integer> points = new ArrayList<>();
        for (int index = edge; index <= word.length() - edge; index++) {
            if (syllable(word, index)) {
                points.add(index);
            }
        }
        return points;
    }

    /** Whether {@code word} is written in a script whose characters stand alone, such as Japanese, Chinese or Korean. */
    static boolean ideographic(String word) {
        for (int index = 0; index < word.length(); index++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(word.charAt(index));
            if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL) {
                return true;
            }
        }
        return false;
    }

    /**
     * A break before one consonant and its vowel, as in "Ma|son", before a pair that opens a syllable and its vowel, as in
     * "Carto|grapher", or between two consonants ({@link #isPair}).
     */
    private static boolean syllable(String word, int index) {
        boolean vowelBefore = vowel(word.charAt(index - 1));
        boolean consonantAfter = !vowel(word.charAt(index)) && Character.isLetter(word.charAt(index));
        boolean vowelNext = index + 1 < word.length() && vowel(word.charAt(index + 1));
        boolean onsetNext = onset(word, index) && index + 2 < word.length() && vowel(word.charAt(index + 2));
        return vowelBefore && consonantAfter && (vowelNext || onsetNext) || isPair(word, index);
    }

    /** Whether the two letters from {@code index} open a syllable together. */
    private static boolean onset(String word, int index) {
        return index + 2 <= word.length() && ONSETS.contains(word.substring(index, index + 2).toLowerCase(Locale.ROOT));
    }

    /** Whether {@code letter} is a vowel, its accents aside, in the Latin or Cyrillic alphabet. */
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

    /** A break between two consonants that do not open a syllable together, as in "Tide|caller": both sides keep a vowel. */
    private static boolean isPair(String word, int index) {
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
