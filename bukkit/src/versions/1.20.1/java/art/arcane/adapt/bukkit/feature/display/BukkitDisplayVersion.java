package art.arcane.adapt.bukkit.feature.display;

import org.bukkit.entity.Display;

/** Displays on Paper 1.20.1 have no teleport interpolation; they jump to each new position. */
final class BukkitDisplayVersion {
  private BukkitDisplayVersion() {
  }

  static int teleportDuration(Display display) {
    return 0;
  }

  static void setTeleportDuration(Display display, int ticks) {
  }
}
