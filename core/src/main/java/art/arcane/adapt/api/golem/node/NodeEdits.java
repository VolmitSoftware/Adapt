package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;

import java.util.Locale;

import java.util.Optional;

public final class NodeEdits {
  public static final int STEP = 1;
  public static final int SHIFT_STEP = 4;

  private static final DyeColor[] COLORS = DyeColor.values();
  private static final RedstoneMode[] REDSTONE_MODES = RedstoneMode.values();
  private static final String DYE_SUFFIX = "_dye";

  private NodeEdits() {
  }

  public static DyeColor nextChannel(DyeColor current, boolean forward) {
    int position = current == null ? COLORS.length : current.ordinal();
    int next = Math.floorMod(position + (forward ? 1 : -1), COLORS.length + 1);
    return next == COLORS.length ? null : COLORS[next];
  }

  public static Optional<DyeColor> dyeChannel(Id material) {
    if (material == null || !material.isMinecraft() || !material.path().endsWith(DYE_SUFFIX)) {
      return Optional.empty();
    }
    String color = material.path().substring(0, material.path().length() - DYE_SUFFIX.length()).toUpperCase(Locale.ROOT);
    for (DyeColor dye : COLORS) {
      if (dye.name().equals(color)) {
        return Optional.of(dye);
      }
    }
    return Optional.empty();
  }

  public static Optional<NodeRecord> cyclePriority(NodeRecord record, int nodeWeavingLevel, boolean forward) {
    if (!NodeRules.allowsPriority(nodeWeavingLevel)) {
      return Optional.empty();
    }
    return Optional.of(record.toBuilder().priority(nextPriority(record.priority(), forward)).build());
  }

  public static int nextPriority(int current, boolean forward) {
    int span = NodeRecord.MAX_PRIORITY - NodeRecord.MIN_PRIORITY + 1;
    return NodeRecord.MIN_PRIORITY + Math.floorMod(current - NodeRecord.MIN_PRIORITY + (forward ? 1 : -1), span);
  }

  public static NodeRecord resize(NodeRecord record, Axis axis, int delta, int maxRadius) {
    NodeCuboid next = resizedArea(record.area(), axis, delta, maxRadius);
    return next.equals(record.area()) ? record : record.toBuilder().area(next).build();
  }

  public static NodeCuboid resizedArea(NodeCuboid area, Axis axis, int delta, int maxRadius) {
    int low = area.min(axis);
    int high = area.max(axis);
    int steps = Math.abs(delta);
    for (int index = 0; index < steps; index++) {
      if (delta > 0) {
        if (high - low + 1 >= NodeCuboid.MAX_SPAN) {
          break;
        }
        if (-low < high && -low < maxRadius || high >= maxRadius && -low < maxRadius) {
          low--;
        } else if (high < maxRadius) {
          high++;
        }
      } else if (-low > high && low < 0) {
        low++;
      } else if (high > 0) {
        high--;
      } else if (low < 0) {
        low++;
      }
    }
    return area.withAxis(axis, low, high);
  }

  public static RedstoneMode nextRedstone(RedstoneMode mode, boolean forward) {
    return REDSTONE_MODES[Math.floorMod(mode.ordinal() + (forward ? 1 : -1), REDSTONE_MODES.length)];
  }

  public enum Axis {
    X,
    Y,
    Z
  }
}
