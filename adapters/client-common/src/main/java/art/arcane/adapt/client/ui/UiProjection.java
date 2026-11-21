package art.arcane.adapt.client.ui;

/** Projects world points onto a GUI-sized screen with the game's perspective, so HUD elements can follow entities. */
public final class UiProjection {
    public static final double NEAR = 0.05D;

    private UiProjection() {
    }

    /** The point on a {@code width} x {@code height} screen, or null when it is behind the camera. */
    public static UiScreenPoint project(UiCamera camera, double x, double y, double z, int width, int height) {
        double yaw = Math.toRadians(camera.yaw());
        double pitch = Math.toRadians(camera.pitch());
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);
        double dx = x - camera.x();
        double dy = y - camera.y();
        double dz = z - camera.z();
        double depth = -sinYaw * cosPitch * dx - sinPitch * dy + cosYaw * cosPitch * dz;
        if (depth < NEAR) {
            return null;
        }
        double right = -cosYaw * dx - sinYaw * dz;
        double up = -sinYaw * sinPitch * dx + cosPitch * dy + cosYaw * sinPitch * dz;
        double scale = pixelsPerBlock(camera, depth, height);
        return new UiScreenPoint(width / 2.0D + right * scale, height / 2.0D - up * scale, depth);
    }

    /** GUI units one block spans at {@code depth} blocks in front of the camera. */
    public static double pixelsPerBlock(UiCamera camera, double depth, int height) {
        double halfFov = Math.toRadians(Math.max(1.0D, Math.min(179.0D, camera.fov())) / 2.0D);
        return height / (2.0D * Math.max(NEAR, depth) * Math.tan(halfFov));
    }
}
