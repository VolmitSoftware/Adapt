package art.arcane.adapt.client.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A stretch of text with one legacy style, as {@link #parse} splits section-sign codes so a client can draw them in
 * its own colours. Colour 0 means the colour the text is drawn with. A colour code resets the formats, as in vanilla.
 */
public record LegacyRun(String text, int color, boolean bold, boolean italic, boolean underlined, boolean strikethrough,
                        boolean obfuscated) {
    public static final char SECTION = '§';
    private static final String COLOURS = "0123456789abcdef";

    /** Whether {@code argb} is light enough to keep the font's drop shadow. */
    public static boolean light(int argb) {
        return PngLuma.luminance(argb) > 0.35;
    }

    /** {@code text} split at its codes, coloured from {@code palette} (16 ARGB colours). */
    public static List<LegacyRun> parse(String text, int[] palette) {
        List<LegacyRun> runs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int color = 0;
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c != SECTION || index + 1 >= text.length()) {
                current.append(c);
                continue;
            }
            char code = Character.toLowerCase(text.charAt(++index));
            if (!current.isEmpty()) {
                runs.add(new LegacyRun(current.toString(), color, bold, italic, underlined, strikethrough, obfuscated));
                current.setLength(0);
            }
            int colour = COLOURS.indexOf(code);
            if (colour >= 0 || code == 'r') {
                color = colour >= 0 ? palette[colour] : 0;
                bold = false;
                italic = false;
                underlined = false;
                strikethrough = false;
                obfuscated = false;
            } else {
                switch (code) {
                    case 'k' -> obfuscated = true;
                    case 'l' -> bold = true;
                    case 'm' -> strikethrough = true;
                    case 'n' -> underlined = true;
                    case 'o' -> italic = true;
                    default -> {
                    }
                }
            }
        }
        if (!current.isEmpty()) {
            runs.add(new LegacyRun(current.toString(), color, bold, italic, underlined, strikethrough, obfuscated));
        }
        return runs;
    }
}
