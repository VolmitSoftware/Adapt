package art.arcane.adapt.client.ui.kit;

/** An axis-aligned rectangle in scaled GUI units. Width and height are never negative. */
public record UiRect(int x, int y, int width, int height) {
    public static final UiRect EMPTY = new UiRect(0, 0, 0, 0);

    public UiRect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    public static UiRect of(int left, int top, int right, int bottom) {
        return new UiRect(left, top, right - left, bottom - top);
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    public boolean contains(double px, double py) {
        return px >= x && py >= y && px < right() && py < bottom();
    }

    public boolean intersects(UiRect other) {
        return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
    }

    public UiRect intersection(UiRect other) {
        return of(Math.max(x, other.x), Math.max(y, other.y), Math.min(right(), other.right()), Math.min(bottom(), other.bottom()));
    }

    public UiRect inset(int amount) {
        return inset(amount, amount, amount, amount);
    }

    public UiRect inset(int left, int top, int right, int bottom) {
        return new UiRect(x + left, y + top, width - left - right, height - top - bottom);
    }

    public UiRect offset(int dx, int dy) {
        return new UiRect(x + dx, y + dy, width, height);
    }

    /** The top {@code amount} units. */
    public UiRect top(int amount) {
        return new UiRect(x, y, width, Math.min(height, amount));
    }

    /** The bottom {@code amount} units. */
    public UiRect bottomStrip(int amount) {
        int strip = Math.min(height, amount);
        return new UiRect(x, bottom() - strip, width, strip);
    }

    /** Everything below the top {@code amount} units. */
    public UiRect belowTop(int amount) {
        return inset(0, Math.min(height, amount), 0, 0);
    }
}
