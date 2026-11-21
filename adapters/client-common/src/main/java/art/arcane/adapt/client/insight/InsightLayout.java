package art.arcane.adapt.client.insight;

/**
 * Places a plate above its anchor: centred on it, lifted clear of the crosshair, clamped inside the screen edges, then
 * moved the shortest way off any HUD box it touches (keeping {@link #HUD_GAP} free around them).
 */
public final class InsightLayout {
    public static final double GAP = 2D;
    public static final double HUD_GAP = 3D;
    public static final double MARGIN = 4D;
    public static final double CROSSHAIR = 9D;

    private InsightLayout() {
    }

    /**
     * Obstacles are the first {@code count} boxes of {@code obstacles} (x, y, width, height each). A plate touching one
     * stays where it is when no spot clear of every box, the crosshair and the screen edges exists.
     */
    public static InsightBox place(double anchorX, double anchorY, double width, double height, int screenWidth,
                                   int screenHeight, double[] obstacles, int count) {
        double centerX = screenWidth / 2D;
        double centerY = screenHeight / 2D;
        double x = anchorX - width / 2D;
        double y = anchorY - GAP - height;
        if (overCrosshair(x, y, width, height, centerX, centerY)) {
            y = centerY - CROSSHAIR - height;
        }
        x = clamp(x, MARGIN, screenWidth - MARGIN - width);
        y = clamp(y, MARGIN, screenHeight - MARGIN - height);
        if (!touches(x, y, width, height, obstacles, count)) {
            return new InsightBox(x, y, width, height);
        }
        double bestX = x;
        double bestY = y;
        double best = Double.MAX_VALUE;
        for (int index = 0; index < count; index++) {
            double left = obstacles[index * 4] - HUD_GAP;
            double top = obstacles[index * 4 + 1] - HUD_GAP;
            double right = obstacles[index * 4] + obstacles[index * 4 + 2] + HUD_GAP;
            double bottom = obstacles[index * 4 + 1] + obstacles[index * 4 + 3] + HUD_GAP;
            for (int side = 0; side < 4; side++) {
                double candidateX = side == 2 ? left - width : side == 3 ? right : x;
                double candidateY = side == 0 ? bottom : side == 1 ? top - height : y;
                double distance = Math.abs(candidateX - x) + Math.abs(candidateY - y);
                if (distance < best && fits(candidateX, candidateY, width, height, screenWidth, screenHeight, obstacles, count)) {
                    best = distance;
                    bestX = candidateX;
                    bestY = candidateY;
                }
            }
        }
        return new InsightBox(bestX, bestY, width, height);
    }

    private static boolean fits(double x, double y, double width, double height, int screenWidth, int screenHeight,
                                double[] obstacles, int count) {
        return x >= MARGIN && y >= MARGIN && x + width <= screenWidth - MARGIN && y + height <= screenHeight - MARGIN
                && !overCrosshair(x, y, width, height, screenWidth / 2D, screenHeight / 2D)
                && !touches(x, y, width, height, obstacles, count);
    }

    private static boolean touches(double x, double y, double width, double height, double[] obstacles, int count) {
        for (int index = 0; index < count; index++) {
            double left = obstacles[index * 4] - HUD_GAP;
            double top = obstacles[index * 4 + 1] - HUD_GAP;
            if (x < left + obstacles[index * 4 + 2] + HUD_GAP * 2D && x + width > left
                    && y < top + obstacles[index * 4 + 3] + HUD_GAP * 2D && y + height > top) {
                return true;
            }
        }
        return false;
    }

    static boolean overCrosshair(double x, double y, double width, double height, double centerX, double centerY) {
        return new InsightBox(x, y, width, height).intersects(centerX - CROSSHAIR, centerY - CROSSHAIR,
                centerX + CROSSHAIR, centerY + CROSSHAIR);
    }

    /** Opacity by distance: full up to 20 blocks, fading to 35% at 40. */
    public static double fade(double distance) {
        return clamp(1D - (distance - 20D) / 20D, 0.35D, 1D);
    }

    /**
     * The plate scale for a GUI scale, rounded so one plate pixel is a whole number of screen pixels and text stays
     * sharp.
     */
    public static double scale(double setting, double guiScale) {
        if (!(guiScale > 0D)) {
            return setting;
        }
        double pixels = Math.max(1D, Math.round(setting * guiScale));
        return pixels / guiScale;
    }

    private static double clamp(double value, double min, double max) {
        return max < min ? min : Math.max(min, Math.min(max, value));
    }
}
