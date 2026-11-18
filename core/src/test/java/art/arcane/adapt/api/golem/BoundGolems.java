package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.minion.MinionKind;

public final class BoundGolems {
  private static final MinionKind KIND = new BoundGolemKind();

  private BoundGolems() {
  }

  public static void register() {
    MinionIdentity.registerKind(KIND);
  }

  public static void unregister() {
    MinionIdentity.unregisterKind(KIND);
  }
}
