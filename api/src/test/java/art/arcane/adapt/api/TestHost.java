package art.arcane.adapt.api;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.HostKey;

/** The host key for every test of this module, claimed once as Adapt's hosts claim theirs. */
public final class TestHost {
  private static HostKey key;

  private TestHost() {
  }

  public static synchronized HostKey key() {
    if (key == null) {
      key = Engine.claimHost();
    }
    return key;
  }
}
