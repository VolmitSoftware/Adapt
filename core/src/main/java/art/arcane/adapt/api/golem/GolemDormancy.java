package art.arcane.adapt.api.golem;

import java.util.function.BooleanSupplier;

public final class GolemDormancy {
  private GolemDormancy() {
  }

  public static DormancyReason reason(boolean ownerOnline, boolean ownerDataKnown, boolean offlineGuard, BooleanSupplier claimRoom) {
    if (ownerOnline && !ownerDataKnown) {
      return DormancyReason.OWNER_UNKNOWN;
    }
    if (!ownerOnline && !offlineGuard) {
      return DormancyReason.OWNER_OFFLINE;
    }
    return claimRoom.getAsBoolean() ? DormancyReason.NONE : DormancyReason.SERVER_CAP;
  }

  public static Transition transition(boolean awake, DormancyReason next) {
    if (next == DormancyReason.NONE) {
      return awake ? Transition.NONE : Transition.WAKE;
    }
    return awake ? Transition.SLEEP : Transition.NONE;
  }

  public static boolean offlineGuard(boolean guardWhileOwnerOffline, GolemCoreType core) {
    return guardWhileOwnerOffline && core == GolemCoreType.GUARDIAN;
  }

  public enum Transition {
    WAKE,
    SLEEP,
    NONE
  }
}
