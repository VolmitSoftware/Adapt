package art.arcane.adapt.api.golem;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemWorkMessages;
import art.arcane.volmlib.util.localization.TextKey;

public enum GolemWorkStatus {
  WAITING(GolemWorkMessages.WAITING),
  MOVING(GolemWorkMessages.MOVING),
  WORKING(GolemWorkMessages.WORKING),
  DELIVERING(GolemWorkMessages.DELIVERING),
  FETCHING_TOOL(GolemWorkMessages.FETCHING_TOOL),
  NEEDS_TOOL(GolemWorkMessages.NEEDS_TOOL),
  NO_OUTPUT(GolemWorkMessages.NO_OUTPUT),
  STUCK_PAUSE(GolemWorkMessages.STUCK_PAUSE),
  CORE_UNLEARNED(GolemWorkMessages.CORE_UNLEARNED),
  NO_REACHABLE_WORK(GolemWorkMessages.NO_REACHABLE_WORK),
  NEEDS_SEEDS(GolemWorkMessages.NEEDS_SEEDS);

  private final TextKey displayName;

  GolemWorkStatus(TextKey displayName) {
    this.displayName = displayName;
  }

  public TextKey displayName() {
    return displayName;
  }

  public String describe(GolemToolNote note) {
    return note != null && (this == NEEDS_TOOL || this == FETCHING_TOOL) ? note.text() : text();
  }

  public String text() {
    return AdaptLanguage.text(displayName);
  }
}
