package art.arcane.adapt.client.hud;

/** Mutable rectangle reused on the render path; {@code scale} is the factor the widget's content is drawn at. */
public final class HudBox {
    double x;
    double y;
    double width;
    double height;
    double scale;

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public double scale() {
        return scale;
    }

    public HudRect rect() {
        return new HudRect(x, y, width, height);
    }

    void set(double newX, double newY, double newWidth, double newHeight, double newScale) {
        x = newX;
        y = newY;
        width = newWidth;
        height = newHeight;
        scale = newScale;
    }
}
