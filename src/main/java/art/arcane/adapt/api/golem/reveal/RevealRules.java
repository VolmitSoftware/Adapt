package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.node.NodeVisibility;
import art.arcane.adapt.util.common.format.C;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;

import java.util.List;

public final class RevealRules {
  public static final double LEAVE_MARGIN = 2D;
  private static final long MILLIS_PER_SECOND = 1_000L;

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

  public static ChatColor glowColor(int channels) {
    List<DyeColor> colors = ChannelMask.colors(channels);
    return colors.isEmpty() ? ChatColor.WHITE : ChatColor.getByChar(C.dyeToChat(colors.getFirst()).getChar());
  }

  public static long secondsLeft(long expiresAtMillis, long nowMillis) {
    long remaining = expiresAtMillis - nowMillis;
    return remaining <= 0L ? 0L : (remaining + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND;
  }

  public static boolean nodeVisible(boolean armed, boolean owner, NodeVisibility visibility, int nodeWeavingLevel) {
    return armed && (owner || (visibility == NodeVisibility.ANY_GOLEMANCER && nodeWeavingLevel >= 1));
  }
}
