package art.arcane.adapt.api.golem.node;

import org.bukkit.DyeColor;
import org.bukkit.Material;

import java.util.Optional;

public final class NodeEdits {
  public static final int STEP = 1;
  public static final int SHIFT_STEP = 4;

  private static final DyeColor[] COLORS = DyeColor.values();
  private static final RedstoneMode[] REDSTONE_MODES = RedstoneMode.values();

  private NodeEdits() {
  }

  public static DyeColor nextChannel(DyeColor current, boolean forward) {
    int position = current == null ? COLORS.length : current.ordinal();
    int next = Math.floorMod(position + (forward ? 1 : -1), COLORS.length + 1);
    return next == COLORS.length ? null : COLORS[next];
  }

  public static Optional<DyeColor> dyeChannel(Material material) {
    if (material == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(switch (material) {
      case WHITE_DYE -> DyeColor.WHITE;
      case ORANGE_DYE -> DyeColor.ORANGE;
      case MAGENTA_DYE -> DyeColor.MAGENTA;
      case LIGHT_BLUE_DYE -> DyeColor.LIGHT_BLUE;
      case YELLOW_DYE -> DyeColor.YELLOW;
      case LIME_DYE -> DyeColor.LIME;
      case PINK_DYE -> DyeColor.PINK;
      case GRAY_DYE -> DyeColor.GRAY;
      case LIGHT_GRAY_DYE -> DyeColor.LIGHT_GRAY;
      case CYAN_DYE -> DyeColor.CYAN;
      case PURPLE_DYE -> DyeColor.PURPLE;
      case BLUE_DYE -> DyeColor.BLUE;
      case BROWN_DYE -> DyeColor.BROWN;
      case GREEN_DYE -> DyeColor.GREEN;
      case RED_DYE -> DyeColor.RED;
      case BLACK_DYE -> DyeColor.BLACK;
      default -> null;
    });
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
    int current = radius(record, axis);
    int next = delta > 0 ? Math.max(current, Math.min(current + delta, maxRadius)) : Math.max(0, current + delta);
    if (next == current) {
      return record;
    }
    return switch (axis) {
      case X -> record.toBuilder().radii(next, record.radiusY(), record.radiusZ()).build();
      case Y -> record.toBuilder().radii(record.radiusX(), next, record.radiusZ()).build();
      case Z -> record.toBuilder().radii(record.radiusX(), record.radiusY(), next).build();
    };
  }

  public static int radius(NodeRecord record, Axis axis) {
    return switch (axis) {
      case X -> record.radiusX();
      case Y -> record.radiusY();
      case Z -> record.radiusZ();
    };
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
