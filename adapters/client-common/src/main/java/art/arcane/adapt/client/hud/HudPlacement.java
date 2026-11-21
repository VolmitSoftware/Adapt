package art.arcane.adapt.client.hud;

import java.util.Objects;

/**
 * Where a widget sits: its own {@code anchor} point is placed on the same screen point, moved by an offset in scaled
 * GUI units. {@code scale} multiplies the widget's natural size (and the profile's global scale multiplies that).
 */
public record HudPlacement(HudAnchor anchor, double offsetX, double offsetY, double scale, double opacity,
                           HudOrientation orientation, boolean visible) {
    public static final double MIN_SCALE = 0.25;
    public static final double MAX_SCALE = 4.0;
    public static final double MIN_OPACITY = 0.1;

    public HudPlacement {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(orientation, "orientation");
        offsetX = finite(offsetX, 0.0);
        offsetY = finite(offsetY, 0.0);
        scale = clampScale(scale);
        opacity = Math.max(MIN_OPACITY, Math.min(1.0, finite(opacity, 1.0)));
    }

    public static double clampScale(double scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, finite(scale, 1.0)));
    }

    public HudPlacement withAnchor(HudAnchor newAnchor, double newOffsetX, double newOffsetY) {
        return new HudPlacement(newAnchor, newOffsetX, newOffsetY, scale, opacity, orientation, visible);
    }

    public HudPlacement withScale(double newScale) {
        return new HudPlacement(anchor, offsetX, offsetY, newScale, opacity, orientation, visible);
    }

    public HudPlacement withOpacity(double newOpacity) {
        return new HudPlacement(anchor, offsetX, offsetY, scale, newOpacity, orientation, visible);
    }

    public HudPlacement withOrientation(HudOrientation newOrientation) {
        return new HudPlacement(anchor, offsetX, offsetY, scale, opacity, newOrientation, visible);
    }

    public HudPlacement withVisible(boolean newVisible) {
        return new HudPlacement(anchor, offsetX, offsetY, scale, opacity, orientation, newVisible);
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
