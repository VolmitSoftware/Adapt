package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.batch.BuildResult;
import art.arcane.adapt.api.mutation.tool.MutationTools;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.content.item.ChalkWandItem.Point;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.localization.TextKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

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
    if (stack == null || stack.isEmpty() || MutationTools.isTool(stack) || ChalkWandItem.read(stack) != null) {
      return null;
    }
    ItemOps items = AdaptCore.platform().host().items();
    Id type = stack.type();
    if (!items.isBlock(type)) {
      return null;
    }
    BlockStateRef state = items.blockState(type);
    return state == null || state.isAir() || !type.equals(state.placementItem()) ? null : state;
  }

  /** The action bar line for a finished build. */
  public static String report(BuildResult result) {
    TextKey key = switch (result.stop()) {
      case DONE -> ArchitectMessages.MASTER_BUILDER_BUILT;
      case PROTECTED -> ArchitectMessages.MASTER_BUILDER_STOPPED_PROTECTED;
      case NO_ITEMS -> ArchitectMessages.MASTER_BUILDER_STOPPED_ITEMS;
      case LIMIT -> ArchitectMessages.MASTER_BUILDER_STOPPED_LIMIT;
      case TOOL_WORN -> ArchitectMessages.MASTER_BUILDER_STOPPED_TOOL;
      case OUT_OF_REACH, CANCELLED -> ArchitectMessages.MASTER_BUILDER_STOPPED;
    };
    String color = result.stop() == BuildResult.Stop.DONE ? C.AQUA.toString() : C.YELLOW.toString();
    return color + AdaptLanguage.text(key, trusted("placed", String.valueOf(result.placed())));
  }

  /** The registered adaptation with this id and type, or null. */
  public static <A> @Nullable A find(String id, Class<A> type) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    SkillRegistry registry = server == null ? null : server.getSkillRegistry();
    Object adaptation = registry == null ? null : registry.getAdaptation(id);
    return type.isInstance(adaptation) ? type.cast(adaptation) : null;
  }
}
