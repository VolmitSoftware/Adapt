package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.kit.Snackbar;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.UiRamp;
import art.arcane.adapt.client.ui.kit.UiTheme;

/** Colours the Adapt screens share on top of the kit theme, all ink that reads on parchment. */
final class Palette {
    static final int GOOD = Snackbar.SUCCESS_COLOR;
    static final int BAD = Snackbar.FAILURE_COLOR;
    static final int LOCK = UiRamp.GOLD[0];
    static final int MUTED = UiTheme.DEFAULT.textMuted();
    /** The darkest paper a skill's colour is written on; skill inks keep text contrast against it. */
    static final int PAPER = UiTheme.DEFAULT.surface();
    /** Contrast on {@link #PAPER} of the ink for a mid-light source colour; light sources get less, dark ones more. */
    private static final double MID_CONTRAST = 6.1;
    private static final double CONTRAST_SPREAD = 1.4;
    /** Degrees a light colour's hue leans toward yellow and a dark one's toward blue, so a pair of one hue stays apart. */
    private static final float HUE_LEAN = 14.0F;
    private static final float GREY = 0.15F;
    private static final float MIN_SATURATION = 0.45F;
    private static final float MAX_SATURATION = 0.62F;

    private Palette() {
    }

    /**
     * An opaque ink for a skill's 0xRRGGBB colour: its hue at an ink's muted saturation, darkened until it reads on
     * {@link #PAPER}. A light colour becomes a lighter, warmer ink than the dark colour of the same hue, golds turn to
     * rust so they stay apart from yellows, and greys become taupe, slate or umber by how light they were.
     */
    static int accent(int rgb) {
        float red = (rgb >> 16 & 0xFF) / 255.0F;
        float green = (rgb >> 8 & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float lightness = (max + min) / 2.0F;
        float spread = max - min;
        float saturation = spread == 0.0F ? 0.0F : spread / (1.0F - Math.abs(2.0F * lightness - 1.0F));
        float bias = Math.max(-1.0F, Math.min(1.0F, (lightness - 0.5F) * 6.0F));
        double target = MID_CONTRAST - CONTRAST_SPREAD * bias;
        float hue;
        if (saturation < GREY) {
            hue = lightness > 0.9F ? 34.0F : lightness > 0.5F ? 210.0F : 28.0F;
            saturation = lightness > 0.9F ? 0.30F : lightness > 0.5F ? 0.22F : 0.18F;
            target = lightness > 0.9F ? 4.8 : lightness > 0.5F ? 5.6 : lightness > 0.1F ? 7.0 : 10.0;
        } else {
            hue = hue(red, green, blue, max, spread);
            float goal = bias > 0.0F ? 60.0F : 240.0F;
            float turn = (goal - hue + 540.0F) % 360.0F - 180.0F;
            hue = (hue + Math.max(-HUE_LEAN, Math.min(HUE_LEAN, turn)) * Math.abs(bias) + 360.0F) % 360.0F;
            if (hue >= 30.0F && hue <= 48.0F) {
                hue -= 18.0F;
            }
            saturation = Math.max(MIN_SATURATION, Math.min(MAX_SATURATION, saturation));
        }
        float low = 0.0F;
        float high = 0.6F;
        for (int step = 0; step < 24; step++) {
            float middle = (low + high) / 2.0F;
            if (UiContrast.ratio(hsl(hue, saturation, middle), PAPER) >= target) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return hsl(hue, saturation, low);
    }

    private static float hue(float red, float green, float blue, float max, float spread) {
        float sector = max == red ? (green - blue) / spread % 6.0F : max == green ? (blue - red) / spread + 2.0F : (red - green) / spread + 4.0F;
        return (sector * 60.0F + 360.0F) % 360.0F;
    }

    private static int hsl(float hue, float saturation, float lightness) {
        float chroma = (1.0F - Math.abs(2.0F * lightness - 1.0F)) * saturation;
        float sector = hue / 60.0F;
        float second = chroma * (1.0F - Math.abs(sector % 2.0F - 1.0F));
        float red = sector < 1.0F || sector >= 5.0F ? chroma : sector < 2.0F || sector >= 4.0F ? second : 0.0F;
        float green = sector < 1.0F ? second : sector < 3.0F ? chroma : sector < 4.0F ? second : 0.0F;
        float blue = sector < 2.0F ? 0.0F : sector < 3.0F ? second : sector < 5.0F ? chroma : second;
        float lift = lightness - chroma / 2.0F;
        return 0xFF000000 | channel(red + lift) << 16 | channel(green + lift) << 8 | channel(blue + lift);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }
}
