package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.ui.PngLuma;

/** Colour arithmetic the demo used, on opaque ARGB. */
final class ThemeColor {
    private ThemeColor() {
    }

    /** The demo's {@code lerpHex}: each channel moved {@code t} of the way to {@code to}, rounded. */
    static int lerp(int from, int to, double t) {
        int r = (int) Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int g = (int) Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** {@code color} at {@code alpha} (0 to 1) of its opacity. */
    static int alpha(int color, double alpha) {
        return KitFrameCanvas.alphaByte(alpha) << 24 | color & 0xFFFFFF;
    }

    /** The WCAG contrast ratio between two colours. */
    static double contrast(int a, int b) {
        double la = PngLuma.luminance(a);
        double lb = PngLuma.luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }
}
