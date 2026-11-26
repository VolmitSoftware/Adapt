package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.LegacyText;

/**
 * Wrapped HUD text measured with the game's own line breaking ({@link UiDraw#wrappedHeight}), so lines break where the
 * font breaks them on every version, including languages written without spaces and right to left.
 */
final class HudWrap {
    private HudWrap() {
    }

    /** Lines {@code text} takes at {@code width}; zero for blank text. */
    static int lines(UiDraw draw, String text, int width) {
        if (LegacyText.isBlank(text)) {
            return 0;
        }
        return Math.max(1, draw.wrappedHeight(text, Math.max(1, width)) / Math.max(1, draw.lineHeight()));
    }

    /**
     * {@code text} when it wraps to at most {@code maxLines} at {@code width}; otherwise its longest start that does with
     * an ellipsis, cut before a space, or between characters when no space allows a fit (languages written without
     * spaces). Never splits a colour code or a surrogate pair.
     */
    static String clip(UiDraw draw, String text, int width, int maxLines) {
        if (lines(draw, text, width) <= maxLines) {
            return text;
        }
        String cut = longest(draw, text, width, maxLines, true);
        if (cut.isEmpty()) {
            cut = longest(draw, text, width, maxLines, false);
        }
        return cut.isEmpty() ? HudPaint.ELLIPSIS : cut;
    }

    private static String longest(UiDraw draw, String text, int width, int maxLines, boolean words) {
        int[] cuts = new int[text.length()];
        int count = 0;
        for (int index = advance(text, 0); index < text.length(); index = advance(text, index)) {
            if (!words || text.charAt(index) == ' ') {
                cuts[count++] = index;
            }
        }
        int low = 0;
        int high = count - 1;
        String best = "";
        while (low <= high) {
            int middle = (low + high) >>> 1;
            String candidate = text.substring(0, cuts[middle]).stripTrailing() + HudPaint.ELLIPSIS;
            if (lines(draw, candidate, width) <= maxLines) {
                best = candidate;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return best;
    }

    private static int advance(String text, int index) {
        if (text.charAt(index) == LegacyText.SECTION) {
            return Math.min(text.length(), index + 2);
        }
        return text.offsetByCodePoints(index, 1);
    }
}
