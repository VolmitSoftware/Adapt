package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.volmlib.util.localization.TextKey;
import net.kyori.adventure.text.format.NamedTextColor;

public enum NodeStatus {
  IDLE,
  WORKING,
  BLOCKED,
  FULL,
  NO_CONTAINER;

  public TextKey displayName() {
    return switch (this) {
      case IDLE -> GolemancyMessages.NODE_STATUS_IDLE;
      case WORKING -> GolemancyMessages.NODE_STATUS_WORKING;
      case BLOCKED -> GolemancyMessages.NODE_STATUS_BLOCKED;
      case FULL -> GolemancyMessages.NODE_STATUS_FULL;
      case NO_CONTAINER -> GolemancyMessages.NODE_STATUS_NO_CONTAINER;
    };
  }

  public NamedTextColor color() {
    return switch (this) {
      case IDLE -> NamedTextColor.GRAY;
      case WORKING -> NamedTextColor.GREEN;
      case BLOCKED -> NamedTextColor.RED;
      case FULL -> NamedTextColor.GOLD;
      case NO_CONTAINER -> NamedTextColor.LIGHT_PURPLE;
    };
  }
}
