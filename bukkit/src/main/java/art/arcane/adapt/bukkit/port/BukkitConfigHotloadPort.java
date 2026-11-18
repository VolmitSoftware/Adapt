package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.protection.ProtectorRegistry;
import art.arcane.adapt.util.config.ConfigHotloadPort;

final class BukkitConfigHotloadPort implements ConfigHotloadPort {
  private final Adapt plugin;

  BukkitConfigHotloadPort(Adapt plugin) {
    this.plugin = plugin;
  }

  @Override
  public void coreConfigReloaded() {
    ProtectorRegistry protectorRegistry = plugin.getProtectorRegistry();
    if (protectorRegistry != null) {
      protectorRegistry.refreshDefaultProtectors();
    }
  }
}
