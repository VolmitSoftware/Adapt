package art.arcane.adapt.api.claims;

public final class ClaimException extends IllegalStateException {
  private final Reason reason;

  public ClaimException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }

  public enum Reason {
    FOUNDING_REQUIRED,
    FORTIFICATION_REQUIRED,
    HOMESTEAD_REQUIRED,
    ALREADY_MEMBER,
    CITY_NOT_FOUND,
    NOT_FOUNDER,
    WORLD_DENIED,
    EXCLUDED_AREA,
    LAND_OCCUPIED,
    NOT_ADJACENT,
    CAPACITY_REACHED,
    MEMBER_CAPACITY_REACHED,
    ALREADY_HAS_PLOT,
    PLOT_OUTSIDE_CITY,
    PLOT_OVERLAP,
    MAIN_ANCHOR_OVERLAP,
    INVITATION_MISSING,
    INVITATION_EXPIRED,
    NOT_MEMBER,
    FOUNDER_CANNOT_LEAVE,
    INVALID_NAME
  }
}
