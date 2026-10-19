package art.arcane.adapt.gameplay.demo;

import org.bukkit.util.BoundingBox;

public final class DemoScope {
    private static final String OWNER_PREFIX = "AQA";

    private DemoScope() {
    }

    public static boolean ownsDemoState(String playerName) {
        return playerName != null && playerName.startsWith(OWNER_PREFIX);
    }

    public static boolean contains(BoundingBox box, int blockX, int blockY, int blockZ) {
        return box.contains(blockX + 0.5D, blockY + 0.5D, blockZ + 0.5D);
    }

    public static double searchRadius(BoundingBox box) {
        double halfX = box.getWidthX() / 2D;
        double halfY = box.getHeight() / 2D;
        double halfZ = box.getWidthZ() / 2D;
        return Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);
    }
}
