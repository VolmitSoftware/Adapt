package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.engine.fx.TpsPort;
import art.arcane.adapt.util.common.compat.PaperApi;

/** Paper's recent TPS; Spigot has no TPS reading and always reports a healthy 20. */
final class BukkitTpsPort implements TpsPort {
  @Override
  public double recentTps() {
    return PaperApi.recentTps();
  }
}
