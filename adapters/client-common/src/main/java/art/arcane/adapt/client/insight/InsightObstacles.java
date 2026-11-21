package art.arcane.adapt.client.insight;

/** Boxes on screen the target plate keeps clear of, such as HUD toasts and notices, in GUI units. */
@FunctionalInterface
public interface InsightObstacles {
    InsightObstacles NONE = (double[] boxes) -> 0;

    /** Writes up to {@code boxes.length / 4} boxes as x, y, width, height and returns how many it wrote. */
    int collect(double[] boxes);
}
