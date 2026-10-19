package art.arcane.adapt.gameplay.demo;

import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemRune;
import org.bukkit.util.BoundingBox;

import java.util.List;

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

    public static void clearRoster(GolemRoster roster) {
        for (GolemRoster.GolemEntry entry : roster.golems()) {
            roster.remove(entry.id());
        }
        List<GolemRune> lattice = roster.lattice();
        for (int index = lattice.size() - 1; index >= 0; index--) {
            roster.removeLattice(index, lattice.get(index));
        }
        roster.setServantChannel(null);
    }
}
