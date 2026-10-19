package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.golem.node.NodeKey;

import java.util.List;

final class GolemDeliveryRoute {
  private static final Route HOLD = new Route(Kind.HOLD, null, null);

  private GolemDeliveryRoute() {
  }

  static Route choose(List<DepositOption> deposits, HomeOption home) {
    DepositOption best = null;
    for (DepositOption option : deposits) {
      if (option.usable() && (best == null || better(option, best))) {
        best = option;
      }
    }
    if (best != null) {
      return new Route(Kind.DEPOSIT, best.key(), null);
    }
    return home.usable() ? new Route(Kind.HOME_CHEST, null, home.chest()) : HOLD;
  }

  private static boolean better(DepositOption candidate, DepositOption current) {
    if (candidate.priority() != current.priority()) {
      return candidate.priority() > current.priority();
    }
    return candidate.distanceSquared() < current.distanceSquared();
  }

  enum Kind {
    DEPOSIT,
    HOME_CHEST,
    HOLD
  }

  record DepositOption(NodeKey key, int priority, double distanceSquared, boolean active, boolean accepts, boolean room, boolean accessible) {
    boolean usable() {
      return active && accepts && room && accessible;
    }
  }

  record HomeOption(GolemState.Anchor chest, boolean room, boolean accessible) {
    static final HomeOption NONE = new HomeOption(null, false, false);

    boolean usable() {
      return chest != null && room && accessible;
    }
  }

  record Route(Kind kind, NodeKey deposit, GolemState.Anchor chest) {
  }
}
