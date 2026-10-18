package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.node.NodeVisibility;

public final class RevealRules {
  public static final double LEAVE_MARGIN = 2D;

  private RevealRules() {
  }

  public static boolean reveals(GolemItemKind kind) {
    return switch (kind) {
      case CROOK, NODE, BLANK_CORE, CORE, DORMANT_GOLEM -> true;
      case BLANK_NODE, BLANK_RUNE, RUNE, SHATTERED_CORE, MASTERWORK_HEART -> false;
    };
  }

  public static boolean eligible(GolemItemKind mainHand, GolemItemKind offHand) {
    return (mainHand != null && reveals(mainHand)) || (offHand != null && reveals(offHand));
  }

  public static boolean withinRange(double distanceSquared, double range) {
    return distanceSquared <= range * range;
  }

  public static boolean inRevealRange(double distanceSquared, double range, boolean shown) {
    return withinRange(distanceSquared, shown ? range + LEAVE_MARGIN : range);
  }

  public static boolean nodeVisible(boolean armed, boolean owner, NodeVisibility visibility, int nodeWeavingLevel) {
    return armed && (owner || (visibility == NodeVisibility.ANY_GOLEMANCER && nodeWeavingLevel >= 1));
  }
}
