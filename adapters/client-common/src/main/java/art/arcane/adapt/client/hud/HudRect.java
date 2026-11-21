package art.arcane.adapt.client.hud;

/** A rectangle in scaled GUI units. */
public record HudRect(double x, double y, double width, double height) {
    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public double centerX() {
        return x + width / 2.0;
    }

    public double centerY() {
        return y + height / 2.0;
    }

    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
    }

    public HudRect at(double newX, double newY) {
        return new HudRect(newX, newY, width, height);
    }

    public HudRect inflate(double amount) {
        return new HudRect(x - amount, y - amount, width + amount * 2.0, height + amount * 2.0);
    }
}
