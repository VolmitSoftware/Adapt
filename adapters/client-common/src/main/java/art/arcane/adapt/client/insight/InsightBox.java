package art.arcane.adapt.client.insight;

/** A plate's place on screen in GUI units. */
public record InsightBox(double x, double y, double width, double height) {
    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public boolean intersects(double left, double top, double right, double bottom) {
        return x < right && right() > left && y < bottom && bottom() > top;
    }
}
