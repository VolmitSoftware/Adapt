package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.api.golem.item.GolemItemKind;

public final class RevealRules {
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
}
