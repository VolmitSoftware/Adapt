package art.arcane.adapt.bukkit.feature.display;

import org.bukkit.entity.Display;

final class BukkitDisplayVersion {
  private BukkitDisplayVersion() {
  }

  static int teleportDuration(Display display) {
    return display.getTeleportDuration();
  }

  static void setTeleportDuration(Display display, int ticks) {
    display.setTeleportDuration(ticks);
  }
}
