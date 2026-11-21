package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.kit.Snackbar;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiTheme;

/** Colours the Adapt screens share on top of the kit theme, all ink that reads on parchment. */
final class Palette {
    static final int GOOD = Snackbar.SUCCESS_COLOR;
    static final int BAD = Snackbar.FAILURE_COLOR;
    static final int LOCK = 0xFF6B440C;
    static final int MUTED = UiTheme.DEFAULT.textMuted();
    static final int TRACK = 0xFF85694A;
    /** The darkest paper text sits on, a hovered or pressed tile; skill colours are darkened until they read on it. */
    static final int PAPER = 0xFFD2BC92;
    private static final int UMBER = 0xFF6A5A48;

    private Palette() {
    }

    /** An opaque accent for a skill's 0xRRGGBB colour, muted toward umber and darkened until it reads as ink on paper. */
    static int accent(int rgb) {
        int muted = UiEase.mix(0xFF000000 | rgb & 0xFFFFFF, UMBER, 0.25F);
        return UiContrast.darkenFor(muted, PAPER, UiContrast.TEXT);
    }
}
