package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.text.Text;

public enum GolemWorkStatus {
  WAITING(Text.key("adapt.golemancy.work.waiting")),
  MOVING(Text.key("adapt.golemancy.work.moving")),
  WORKING(Text.key("adapt.golemancy.work.working")),
  DELIVERING(Text.key("adapt.golemancy.work.delivering")),
  FETCHING_TOOL(Text.key("adapt.golemancy.work.fetching_tool")),
  NEEDS_TOOL(Text.key("adapt.golemancy.work.needs_tool")),
  NO_OUTPUT(Text.key("adapt.golemancy.work.no_output")),
  STUCK_PAUSE(Text.key("adapt.golemancy.work.stuck_pause")),
  CORE_UNLEARNED(Text.key("adapt.golemancy.work.core_unlearned")),
  NO_REACHABLE_WORK(Text.key("adapt.golemancy.work.no_reachable_work")),
  NEEDS_SEEDS(Text.key("adapt.golemancy.work.needs_seeds"));

  private final Text displayName;

  GolemWorkStatus(Text displayName) {
    this.displayName = displayName;
  }

  public Text displayName() {
    return displayName;
  }

  public String describe(GolemToolNote note) {
    return note != null && (this == NEEDS_TOOL || this == FETCHING_TOOL) ? note.text() : text();
  }

  public String text() {
    return displayName.resolve();
  }
}
