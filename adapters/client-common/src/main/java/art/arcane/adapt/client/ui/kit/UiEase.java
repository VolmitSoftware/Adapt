package art.arcane.adapt.client.ui.kit;

/** Easing curves over 0..1 and colour blending. */
public final class UiEase {
    private UiEase() {
    }

    public static float clamp(float t) {
        return Math.max(0.0F, Math.min(1.0F, t));
    }

    public static float outCubic(float t) {
        float inverse = 1.0F - clamp(t);
        return 1.0F - inverse * inverse * inverse;
    }

    public static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /** Blends each ARGB channel from {@code from} to {@code to}. */
    public static int mix(int from, int to, float t) {
        float k = clamp(t);
        int a = Math.round(lerp(from >>> 24, to >>> 24, k));
        int r = Math.round(lerp(from >> 16 & 0xFF, to >> 16 & 0xFF, k));
        int g = Math.round(lerp(from >> 8 & 0xFF, to >> 8 & 0xFF, k));
        int b = Math.round(lerp(from & 0xFF, to & 0xFF, k));
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** {@code color} with its alpha multiplied by {@code factor}. */
    public static int fade(int color, float factor) {
        int alpha = Math.round((color >>> 24) * clamp(factor));
        return alpha << 24 | color & 0xFFFFFF;
    }
}
