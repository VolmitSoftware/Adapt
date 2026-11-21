package art.arcane.adapt.client.ui.kit;

/**
 * The tome's whole palette as opaque ARGB ramps. Every opaque pixel of the {@code kit/} textures and every colour in
 * {@link UiTheme#DEFAULT} and {@link UiTome} comes from here, so the book reads as drawn by one hand.
 */
public final class UiRamp {
    /** Cover leather, dark to light; index 1 is the outline the icons use. */
    public static final int[] LEATHER = {0xFF24140C, 0xFF3A2414, 0xFF56341E, 0xFF74482A, 0xFF8F5F3A, 0xFFAE7E4E};
    /** Paper, light to dark: slip, page, toned, surface, pressed, edge, rule, line between sheets. */
    public static final int[] PAGE = {0xFFF6EDD8, 0xFFEDE1C6, 0xFFE6D7B4, 0xFFDCC9A0, 0xFFD2BC92, 0xFFB79C6E, 0xFF896C48, 0xFF6E5436};
    /** Text, muted text, disabled text. */
    public static final int[] INK = {0xFF2E2216, 0xFF5A4430, 0xFF8A7860};
    /** Gold, dark to light; the last is gilt lettering. */
    public static final int[] GOLD = {0xFF6B440C, 0xFF9C7420, 0xFFC99A32, 0xFFE2B648, 0xFFF2D98A};
    /** Moss: ink, mid, light, then three paper washes. */
    public static final int[] MOSS = {0xFF2E5619, 0xFF4E7A32, 0xFF86A85E, 0xFFB9C990, 0xFFD2DCAE, 0xFFE2EAC8};
    /** Wine: outline, shadow, ink, light, then three paper washes. */
    public static final int[] WINE = {0xFF4A1410, 0xFF6E1E1C, 0xFF8D261D, 0xFFB2523F, 0xFFD9A893, 0xFFEBCBB9, 0xFFF4E0D2};
    /** Dusty blue: ink, mid, light, wash. */
    public static final int[] BLUE = {0xFF23406E, 0xFF3E5F80, 0xFF7C98B4, 0xFFB9CBE2};
    /** Locked metal, dark to light. */
    public static final int[] PEWTER = {0xFF5E5548, 0xFF857A6A, 0xFFA69A88, 0xFFC6BCAA};

    private UiRamp() {
    }

    /** Every ramp, for checks that art stays inside the palette. */
    public static int[][] all() {
        return new int[][] {LEATHER, PAGE, INK, GOLD, MOSS, WINE, BLUE, PEWTER};
    }

    /** {@code color} with its alpha replaced by {@code alpha} (0 to 255). */
    public static int alpha(int color, int alpha) {
        return alpha << 24 | color & 0xFFFFFF;
    }
}
