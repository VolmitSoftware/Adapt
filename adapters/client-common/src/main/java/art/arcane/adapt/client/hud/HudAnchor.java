package art.arcane.adapt.client.hud;

import java.util.Locale;

/**
 * One of the nine screen points a widget attaches to. The same fraction picks the matching point on the widget, so a
 * widget anchored bottom-right grows up and to the left.
 */
public enum HudAnchor {
    TOP_LEFT(0, 0),
    TOP_CENTER(1, 0),
    TOP_RIGHT(2, 0),
    MIDDLE_LEFT(0, 1),
    MIDDLE_CENTER(1, 1),
    MIDDLE_RIGHT(2, 1),
    BOTTOM_LEFT(0, 2),
    BOTTOM_CENTER(1, 2),
    BOTTOM_RIGHT(2, 2);

    private static final HudAnchor[] GRID = values();

    private final int column;
    private final int row;

    HudAnchor(int column, int row) {
        this.column = column;
        this.row = row;
    }

    public double fractionX() {
        return column * 0.5;
    }

    public double fractionY() {
        return row * 0.5;
    }

    public int column() {
        return column;
    }

    public int row() {
        return row;
    }

    public boolean bottom() {
        return row == 2;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static HudAnchor of(int column, int row) {
        return GRID[row * 3 + column];
    }

    /** The anchor whose third of the screen holds the centre of {@code rect}. */
    public static HudAnchor nearest(HudRect rect, double screenWidth, double screenHeight) {
        return of(third(rect.centerX(), screenWidth), third(rect.centerY(), screenHeight));
    }

    public static HudAnchor parse(String id, HudAnchor fallback) {
        if (id != null) {
            for (HudAnchor anchor : GRID) {
                if (anchor.id().equals(id)) {
                    return anchor;
                }
            }
        }
        return fallback;
    }

    private static int third(double position, double size) {
        if (size <= 0.0) {
            return 0;
        }
        double fraction = position / size;
        if (fraction < 1.0 / 3.0) {
            return 0;
        }
        return fraction < 2.0 / 3.0 ? 1 : 2;
    }
}
