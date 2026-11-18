package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.node.NodeVisibility;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.util.common.format.C;

import java.util.List;

public final class RevealRules {
  public static final double LEAVE_MARGIN = 2D;
  private static final long MILLIS_PER_SECOND = 1_000L;
  private static final String CHAT_CODES = "0123456789abcdef";

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

  public static boolean holdsCrook(GolemItemKind mainHand, GolemItemKind offHand) {
    return mainHand == GolemItemKind.CROOK || offHand == GolemItemKind.CROOK;
  }

  public static boolean withinRange(double distanceSquared, double range) {
    return distanceSquared <= range * range;
  }

  public static boolean inRevealRange(double distanceSquared, double range, boolean shown) {
    return withinRange(distanceSquared, shown ? range + LEAVE_MARGIN : range);
  }

  public static GlowColor glowColor(int channels) {
    List<DyeColor> colors = ChannelMask.colors(channels);
    if (colors.isEmpty()) {
      return GlowColor.WHITE;
    }
    int code = CHAT_CODES.indexOf(C.dyeToChat(colors.getFirst().name()).getChar());
    return code < 0 ? GlowColor.WHITE : GlowColor.values()[code];
  }

  public static long secondsLeft(long expiresAtMillis, long nowMillis) {
    long remaining = expiresAtMillis - nowMillis;
    return remaining <= 0L ? 0L : (remaining + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND;
  }

  public static boolean nodeVisible(boolean armed, boolean owner, NodeVisibility visibility, int nodeWeavingLevel) {
    return armed && (owner || (visibility == NodeVisibility.ANY_GOLEMANCER && nodeWeavingLevel >= 1));
  }
}
