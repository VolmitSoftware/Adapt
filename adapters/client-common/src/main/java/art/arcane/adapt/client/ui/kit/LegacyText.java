package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;

/**
 * Helpers for text that carries legacy section-sign colour codes, as Adapt's chest menus send it. The Minecraft font
 * draws the codes and leaves them out of {@link UiDraw#textWidth}, so these helpers only cut text without breaking a
 * code apart.
 */
public final class LegacyText {
    public static final char SECTION = '§';
    public static final String ELLIPSIS = "...";

    private LegacyText() {
    }

    /** The text without colour and format codes. */
    public static String strip(String text) {
        if (text == null || text.indexOf(SECTION) < 0) {
            return text == null ? "" : text;
        }
        StringBuilder plain = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c == SECTION) {
                index++;
            } else {
                plain.append(c);
            }
        }
        return plain.toString();
    }

    public static boolean isBlank(String text) {
        return strip(text).isBlank();
    }

    /** The text cut to {@code maxWidth} with an ellipsis when it does not fit; never splits a code or a surrogate pair. */
    public static String truncate(UiDraw metrics, String text, int maxWidth) {
        if (metrics.textWidth(text) <= maxWidth) {
            return text;
        }
        int budget = maxWidth - metrics.textWidth(ELLIPSIS);
        if (budget <= 0) {
            return "";
        }
        int end = 0;
        int index = 0;
        while (index < text.length()) {
            int next = advance(text, index);
            if (metrics.textWidth(text.substring(0, next)) > budget) {
                break;
            }
            end = next;
            index = next;
        }
        return text.substring(0, end) + ELLIPSIS;
    }

    private static int advance(String text, int index) {
        if (text.charAt(index) == SECTION) {
            return Math.min(text.length(), index + 2);
        }
        return text.offsetByCodePoints(index, 1);
    }
}
