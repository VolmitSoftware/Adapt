package art.arcane.adapt.client.hud;

/** Placement math in scaled GUI units. Pure; every result is fully on screen. */
public final class HudGeometry {
    private HudGeometry() {
    }

    /**
     * Places content of the given natural size. The drawn scale is placement scale times {@code globalScale}, reduced
     * when the widget would not fit the screen. Positions are rounded to whole physical pixels at {@code guiScale}.
     */
    public static void place(HudPlacement placement, double contentWidth, double contentHeight, double globalScale,
                             double screenWidth, double screenHeight, double guiScale, HudBox out) {
        double scale = placement.scale() * globalScale;
        double width = contentWidth * scale;
        double height = contentHeight * scale;
        if (width > screenWidth || height > screenHeight) {
            double fit = Math.min(width > 0.0 ? screenWidth / width : 1.0, height > 0.0 ? screenHeight / height : 1.0);
            scale *= fit;
            width = contentWidth * scale;
            height = contentHeight * scale;
        }
        HudAnchor anchor = placement.anchor();
        double x = screenWidth * anchor.fractionX() + placement.offsetX() - width * anchor.fractionX();
        double y = screenHeight * anchor.fractionY() + placement.offsetY() - height * anchor.fractionY();
        x = pixel(clamp(x, width, screenWidth), width, screenWidth, guiScale);
        y = pixel(clamp(y, height, screenHeight), height, screenHeight, guiScale);
        out.set(x, y, width, height, scale);
    }

    public static HudRect place(HudPlacement placement, double contentWidth, double contentHeight, double globalScale,
                                double screenWidth, double screenHeight) {
        HudBox box = new HudBox();
        place(placement, contentWidth, contentHeight, globalScale, screenWidth, screenHeight, 0.0, box);
        return box.rect();
    }

    /** Moves {@code rect} the least distance needed to lie fully on screen. */
    public static HudRect clamp(HudRect rect, double screenWidth, double screenHeight) {
        return rect.at(clamp(rect.x(), rect.width(), screenWidth), clamp(rect.y(), rect.height(), screenHeight));
    }

    /** The placement that puts the widget exactly at {@code rect}, keeping the current anchor. */
    public static HudPlacement moveTo(HudPlacement placement, HudRect rect, double screenWidth, double screenHeight) {
        return attach(placement, placement.anchor(), rect, screenWidth, screenHeight);
    }

    /** The placement that puts the widget exactly at {@code rect}, attached to the nearest of the nine points. */
    public static HudPlacement reanchor(HudPlacement placement, HudRect rect, double screenWidth, double screenHeight) {
        return attach(placement, HudAnchor.nearest(rect, screenWidth, screenHeight), rect, screenWidth, screenHeight);
    }

    private static HudPlacement attach(HudPlacement placement, HudAnchor anchor, HudRect rect, double screenWidth,
                                       double screenHeight) {
        double offsetX = rect.x() + rect.width() * anchor.fractionX() - screenWidth * anchor.fractionX();
        double offsetY = rect.y() + rect.height() * anchor.fractionY() - screenHeight * anchor.fractionY();
        return placement.withAnchor(anchor, round(offsetX), round(offsetY));
    }

    /**
     * Scale for a corner drag: {@code fixedX/fixedY} is the corner that stays put, {@code startX/startY} the dragged
     * corner when the drag began and {@code mouseX/mouseY} where it is now. Projects the mouse on the diagonal so the
     * widget keeps its proportions.
     */
    public static double cornerScale(double startScale, double fixedX, double fixedY, double startX, double startY,
                                     double mouseX, double mouseY) {
        double diagonalX = startX - fixedX;
        double diagonalY = startY - fixedY;
        double length = diagonalX * diagonalX + diagonalY * diagonalY;
        if (length <= 0.0) {
            return startScale;
        }
        double factor = ((mouseX - fixedX) * diagonalX + (mouseY - fixedY) * diagonalY) / length;
        return HudPlacement.clampScale(Math.round(startScale * factor * 20.0) / 20.0);
    }

    private static double clamp(double position, double size, double screen) {
        if (size >= screen) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(position, screen - size));
    }

    /** Rounds to a whole physical pixel without leaving the screen. */
    private static double pixel(double value, double size, double screen, double guiScale) {
        if (guiScale <= 0.0) {
            return value;
        }
        double rounded = Math.round(value * guiScale) / guiScale;
        if (rounded + size > screen) {
            rounded = Math.floor((screen - size) * guiScale) / guiScale;
        }
        return Math.max(0.0, rounded);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
