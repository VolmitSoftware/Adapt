package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.content.item.ChalkWandItem.Point;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


/** What Architect's build tools share: the open spots of a plan, the block a held stack lays, and the result line. */
public final class ArchitectBuilds {
  private ArchitectBuilds() {
  }

  /** The planned points that are inside the build height, in a loaded chunk, and empty or replaceable now. */
  public static List<BlockRef> openSpots(WorldRef world, List<Point> points) {
    List<BlockRef> open = new ArrayList<>(points.size());
    for (Point point : points) {
      if (point.y() < world.minHeight() || point.y() >= world.maxHeight()
          || !world.isChunkLoaded(point.x() >> 4, point.z() >> 4)) {
        continue;
      }
      BlockRef block = world.blockAt(point.x(), point.y(), point.z());
      if (block.isAir() || block.isReplaceable()) {
        open.add(block);
      }
    }
    return open;
  }

  /** The single block state the stack places, or null for an item that is not a block or places something else. */
  public static @Nullable BlockStateRef placedState(@Nullable ItemRef stack) {
    if (stack == null || stack.isEmpty() || Mutations.isTool(stack) || ChalkWandItem.read(stack) != null) {
      return null;
    }
    ItemOps items = AdaptApi.platform().host().items();
    Id type = stack.type();
    if (!items.isBlock(type)) {
      return null;
    }
    BlockStateRef state = items.blockState(type);
    return state == null || state.isAir() || !type.equals(state.placementItem()) ? null : state;
  }

  /** The action bar line for a finished build. */
  public static String report(BuildResult result) {
    return report(result.stop(), result.placed());
  }

  /** The status line for a build that ended with {@code stop} after placing {@code placed} blocks. */
  public static String report(BuildResult.Stop stop, int placed) {
    Text key = switch (stop) {
      case DONE -> Text.key("adapt.upgrade.architect.master_builder.built");
      case PROTECTED -> Text.key("adapt.upgrade.architect.master_builder.stopped_protected");
      case NO_ITEMS -> Text.key("adapt.upgrade.architect.master_builder.stopped_items");
      case LIMIT -> Text.key("adapt.upgrade.architect.master_builder.stopped_limit");
      case TOOL_WORN -> Text.key("adapt.upgrade.architect.master_builder.stopped_tool");
      case OUT_OF_REACH, CANCELLED -> Text.key("adapt.upgrade.architect.master_builder.stopped");
    };
    String color = stop == BuildResult.Stop.DONE ? TextFormat.AQUA.toString() : TextFormat.YELLOW.toString();
    return color + key.withMarkup("placed", String.valueOf(placed)).resolve();
  }

  /** The registered adaptation with this id and type, or null. */
  public static <A> @Nullable A find(Id id, Class<A> type) {
    Object adaptation = AdaptApi.isRunning() ? AdaptApi.content().adaptation(id).orElse(null) : null;
    return type.isInstance(adaptation) ? type.cast(adaptation) : null;
  }
}
