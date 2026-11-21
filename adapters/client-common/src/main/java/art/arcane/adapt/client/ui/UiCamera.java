package art.arcane.adapt.client.ui;

/**
 * The game camera of one frame: eye position, Minecraft yaw and pitch in degrees (yaw 0 faces +Z, pitch 90 faces
 * down) and the vertical field of view in degrees, with the game's FOV effects applied.
 */
public record UiCamera(double x, double y, double z, float yaw, float pitch, double fov) {
}
