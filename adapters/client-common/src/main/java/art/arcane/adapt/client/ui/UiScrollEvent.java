package art.arcane.adapt.client.ui;

/** Scroll amounts are in wheel notches; positive vertical scrolls up. */
public record UiScrollEvent(double x, double y, double horizontal, double vertical) {
}
