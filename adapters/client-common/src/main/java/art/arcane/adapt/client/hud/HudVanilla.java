package art.arcane.adapt.client.hud;

/**
 * Vanilla HUD areas in scaled units that Adapt widgets keep clear of: the hotbar with its status rows, held item name
 * and action bar; chat left of the hotbar; two boss bars; vanilla toasts and effect icons in the top right; a 13-line
 * scoreboard at the middle right. The crosshair is covered by the protected view zone.
 */
final class HudVanilla {
    static final double HOTBAR_HALF_WIDTH = 91.0;
    static final double HOTBAR_HEIGHT = 72.0;
    static final double CHAT_RISE = 130.0;
    static final double BOSS_BARS = 50.0;
    static final double SCOREBOARD_WIDTH = 110.0;
    static final double SCOREBOARD_HALF_HEIGHT = 60.0;

    private HudVanilla() {
    }

    /** Whether the rectangle touches any vanilla area of a {@code width} x {@code height} screen. */
    static boolean overlaps(double x, double y, double w, double h, double width, double height) {
        double half = width / 2.0;
        return hit(x, y, w, h, half - HOTBAR_HALF_WIDTH, height - HOTBAR_HEIGHT, HOTBAR_HALF_WIDTH * 2, HOTBAR_HEIGHT)
                || hit(x, y, w, h, 0, height - CHAT_RISE, Math.min(330, half - HOTBAR_HALF_WIDTH), 90)
                || hit(x, y, w, h, half - HOTBAR_HALF_WIDTH, 0, HOTBAR_HALF_WIDTH * 2, BOSS_BARS)
                || hit(x, y, w, h, width - 160, 0, 160, 52)
                || hit(x, y, w, h, width - SCOREBOARD_WIDTH, height / 2.0 - SCOREBOARD_HALF_HEIGHT, SCOREBOARD_WIDTH,
                SCOREBOARD_HALF_HEIGHT * 2);
    }

    static boolean hit(double x, double y, double w, double h, double ox, double oy, double ow, double oh) {
        return x < ox + ow && ox < x + w && y < oy + oh && oy < y + h;
    }
}
