package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.api.text.Text;

public enum NodeStatus {
  IDLE,
  WAITING,
  PAUSED,
  WORKING,
  BLOCKED,
  FULL,
  NO_CONTAINER;

  public Text displayName() {
    return switch (this) {
      case IDLE -> Text.key("adapt.golemancy.node_status.idle");
      case WAITING -> Text.key("adapt.golemancy.node_status.waiting");
      case PAUSED -> Text.key("adapt.golemancy.node_status.paused");
      case WORKING -> Text.key("adapt.golemancy.node_status.working");
      case BLOCKED -> Text.key("adapt.golemancy.node_status.blocked");
      case FULL -> Text.key("adapt.golemancy.node_status.full");
      case NO_CONTAINER -> Text.key("adapt.golemancy.node_status.no_container");
    };
  }

  /** MiniMessage colour name of the status text. */
  public String color() {
    return switch (this) {
      case IDLE, WAITING -> "gray";
      case PAUSED -> "yellow";
      case WORKING -> "green";
      case BLOCKED -> "red";
      case FULL -> "gold";
      case NO_CONTAINER -> "light_purple";
    };
  }
}
