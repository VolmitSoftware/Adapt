package art.arcane.adapt.api.golem;

public final class GolemValidation {
  private GolemValidation() {
  }

  public static Verdict verdict(boolean duplicate, boolean ownerDataKnown, GolemRoster.Status status, boolean stale) {
    if (duplicate) {
      return Verdict.REVERT;
    }
    if (!ownerDataKnown) {
      return Verdict.WAIT;
    }
    if (status == null || stale) {
      return Verdict.REVERT;
    }
    return switch (status) {
      case ACTIVE -> Verdict.KEEP;
      case MISSING -> Verdict.RESTORE;
      case PACKED, SHATTERED -> Verdict.REVERT;
    };
  }

  public enum Verdict {
    KEEP,
    RESTORE,
    REVERT,
    WAIT
  }
}
