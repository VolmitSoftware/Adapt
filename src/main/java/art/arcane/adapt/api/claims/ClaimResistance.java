package art.arcane.adapt.api.claims;

import java.util.Objects;

public final class ClaimResistance {
  private final int onlineBreaks;
  private final int offlineBreaks;
  private final int onlinePlacements;
  private final int offlinePlacements;

  public ClaimResistance(int onlineBreaks, int offlineBreaks, int onlinePlacements, int offlinePlacements) {
    this.onlineBreaks = onlineBreaks;
    this.offlineBreaks = offlineBreaks;
    this.onlinePlacements = onlinePlacements;
    this.offlinePlacements = offlinePlacements;
    validate();
  }

  public void validate() {
    if (onlineBreaks < 1 || offlineBreaks < 1 || onlinePlacements < 1 || offlinePlacements < 1) {
      throw new IllegalArgumentException("Resistance values must be positive");
    }
  }

  public int onlineBreaks() {
    return onlineBreaks;
  }

  public int offlineBreaks() {
    return offlineBreaks;
  }

  public int onlinePlacements() {
    return onlinePlacements;
  }

  public int offlinePlacements() {
    return offlinePlacements;
  }

  public int required(ClaimAction action, boolean online) {
    return switch (action) {
      case BREAK -> online ? onlineBreaks : offlineBreaks;
      case PLACE -> online ? onlinePlacements : offlinePlacements;
    };
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ClaimResistance resistance && onlineBreaks == resistance.onlineBreaks
        && offlineBreaks == resistance.offlineBreaks && onlinePlacements == resistance.onlinePlacements
        && offlinePlacements == resistance.offlinePlacements;
  }

  @Override
  public int hashCode() {
    return Objects.hash(onlineBreaks, offlineBreaks, onlinePlacements, offlinePlacements);
  }
}
