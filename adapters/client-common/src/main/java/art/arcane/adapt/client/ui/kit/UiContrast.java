package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.PngLuma;

/** WCAG contrast arithmetic for ARGB colours, used to keep text and icons readable on the kit's parchment. */
public final class UiContrast {
    /** WCAG AA for normal text. */
    public static final double TEXT = 4.5;
    /** WCAG AA for graphics such as icons and control edges. */
    public static final double GRAPHIC = 3.0;
    /** The paper shade a pale icon's plate starts from before it is darkened. */
    private static final int SHADE = 0xFFC9B48C;

    private UiContrast() {
    }

    public static double luminance(int color) {
        return PngLuma.luminance(color);
    }

    public static double ratio(double first, double second) {
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    public static double ratio(int first, int second) {
        return ratio(luminance(first), luminance(second));
    }

    /** {@code top} blended over the opaque {@code bottom}, as the screen shows it; the result is opaque. */
    public static int over(int top, int bottom) {
        float alpha = (top >>> 24) / 255.0F;
        return UiEase.mix(bottom | 0xFF000000, top | 0xFF000000, alpha);
    }

    /** The opaque neutral grey with relative luminance {@code target} (0 to 1). */
    public static int grey(double target) {
        double clamped = Math.max(0.0, Math.min(1.0, target));
        double channel = clamped <= 0.0031308 ? clamped * 12.92 : 1.055 * Math.pow(clamped, 1.0 / 2.4) - 0.055;
        int value = (int) Math.round(channel * 255.0);
        return 0xFF000000 | value << 16 | value << 8 | value;
    }

    /**
     * A plate to put behind an icon of mean luminance {@code icon} drawn on {@code backdrop}, or 0 when it needs none: art
     * paler than the paper gets a warm shade dark enough to reach {@link #GRAPHIC} against it. Art darker than the paper
     * reads on it through its own outline and gets none, as does unknown luminance (below 0).
     */
    public static int iconPlate(double icon, int backdrop) {
        if (icon < 0.0 || icon <= luminance(backdrop) || ratio(icon, luminance(backdrop)) >= GRAPHIC) {
            return 0;
        }
        return darkenFor(SHADE, grey(icon), GRAPHIC);
    }

    /** {@code color} darkened toward black until {@code text} reaches {@code minimum} on it; opaque. */
    public static int darkenFor(int color, int text, double minimum) {
        int shade = color | 0xFF000000;
        for (int step = 0; step < 20 && ratio(shade, text) < minimum; step++) {
            shade = UiEase.mix(shade, 0xFF000000, 0.12F);
        }
        return shade;
    }
}
