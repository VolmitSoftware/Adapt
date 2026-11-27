package art.arcane.adapt.content.golem;

import art.arcane.adapt.engine.minion.MinionIdentity;
import art.arcane.adapt.api.ability.MinionKind;

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
