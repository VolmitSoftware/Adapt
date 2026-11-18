package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.Adapt;

/**
 * Installs every Bukkit implementation of the core ports. Call before {@code AdaptCore.startServices()} and
 * uninstall after {@code AdaptCore.stop()}. Ports backed by objects that only exist after a later plugin phase
 * resolve them on each call, so install order does not depend on those phases.
 */
public final class BukkitPorts {
  private BukkitPorts() {
  }

  public static void install(Adapt plugin) {
    BukkitTextPorts.install(plugin);
    BukkitWorldPorts.install(plugin);
    BukkitAdaptationPorts.install(plugin);
    BukkitCommandPorts.install();
  }

  public static void uninstall() {
    BukkitCommandPorts.uninstall();
    BukkitAdaptationPorts.uninstall();
    BukkitWorldPorts.uninstall();
    BukkitTextPorts.uninstall();
  }
}
