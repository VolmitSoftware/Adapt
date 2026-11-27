package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.HostKey;

/**
 * Holds the host key for every Adapt host in this class loader. The first {@link #key()} claims it from
 * {@link Engine#claimHost()}; the Bukkit plugin and the mod bootstrap call it before any addon code runs, and the test
 * kits share it. When other code claimed the key first, this throws and Adapt does not start.
 */
public final class HostKeys {
  private static HostKey key;

  private HostKeys() {
  }

  public static synchronized HostKey key() {
    if (key == null) {
      key = Engine.claimHost();
    }
    return key;
  }
}
