package art.arcane.adapt.client.ui;

/** A world point on screen in GUI units; {@code depth} is its distance along the view direction in blocks. */
public record UiScreenPoint(double x, double y, double depth) {
}
