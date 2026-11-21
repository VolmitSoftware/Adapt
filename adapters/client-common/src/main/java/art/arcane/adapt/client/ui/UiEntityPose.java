package art.arcane.adapt.client.ui;

/** A tracked entity at the frame's partial tick: feet position and bounding box size, in blocks. */
public record UiEntityPose(double x, double y, double z, double width, double height) {
}
