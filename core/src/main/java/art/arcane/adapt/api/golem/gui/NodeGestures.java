package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.node.NodeEdits;
import art.arcane.adapt.platform.value.Id;

public final class NodeGestures {
  private NodeGestures() {
  }

  public static Action route(Click click, boolean sneaking, boolean crookInMainHand, boolean crookInOffHand, Id mainHand) {
    if (crookInMainHand) {
      return switch (click) {
        case RIGHT -> sneaking ? Action.CYCLE_PRIORITY : Action.OPEN_GUI;
        case LEFT -> sneaking ? Action.REMOVE : Action.NEXT_CHANNEL;
      };
    }
    if (!crookInOffHand || click != Click.RIGHT || mainHand == null || mainHand.isAir()) {
      return Action.NONE;
    }
    return NodeEdits.dyeChannel(mainHand).isPresent() ? Action.SET_CHANNEL : Action.TOGGLE_FILTER;
  }

  public enum Click {
    LEFT,
    RIGHT
  }

  public enum Action {
    NONE,
    OPEN_GUI,
    NEXT_CHANNEL,
    SET_CHANNEL,
    TOGGLE_FILTER,
    CYCLE_PRIORITY,
    REMOVE
  }
}
