package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.fx.TpsPort;
import org.bukkit.Bukkit;

final class BukkitTpsPort implements TpsPort {
  private volatile boolean unavailable;

  @Override
  public double recentTps() {
    if (unavailable) {
      return 20.0D;
    }
    try {
      double[] tps = Bukkit.getTPS();
      if (tps == null || tps.length == 0) {
        return 20.0D;
      }
      return tps[0];
    } catch (Throwable ignored) {
      unavailable = true;
      return 20.0D;
    }
  }
}
