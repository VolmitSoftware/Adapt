package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.viewer.ViewerDisplayPort;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Keen Eye's search for chests and spawners a player can see. */
public final class KeenEyeScan {
  public static final int HARD_MAX_HIGHLIGHTS = 8;
  private static final Id CHEST = Id.minecraft("chest");
  private static final Id TRAPPED_CHEST = Id.minecraft("trapped_chest");
  private static final Id SPAWNER = Id.minecraft("spawner");

  private KeenEyeScan() {
  }

  public static boolean isTarget(Id type) {
    return CHEST.equals(type)
        || TRAPPED_CHEST.equals(type)
        || SPAWNER.equals(type)
        || DiscoveryKeenEye.TRIAL_SPAWNER.equals(type);
  }

  public static boolean isSpawner(Id type) {
    return SPAWNER.equals(type) || DiscoveryKeenEye.TRIAL_SPAWNER.equals(type);
  }

  /**
   * Chests and spawners accepted by {@code accepts} within {@code range} of the player's eye, inside the view cone
   * {@code viewConeCos} and in line of sight; at most {@code limit}, clamped to {@link #HARD_MAX_HIGHLIGHTS}.
   */
  public static List<BlockRef> scan(PlayerRef player, double range, double viewConeCos, int limit, Predicate<Id> accepts) {
    Loc eye = player.eyeLocation();
    Vec3 look = eye.direction();
    WorldRef world = player.world();
    double rangeSq = range * range;
    int cap = Math.max(1, Math.min(limit, HARD_MAX_HIGHLIGHTS));
    int chunkRadius = Math.max(1, (int) Math.ceil(range / 16D));
    int baseCx = eye.blockX() >> 4;
    int baseCz = eye.blockZ() >> 4;
    List<BlockRef> found = new ArrayList<>();
    for (int cx = -chunkRadius; cx <= chunkRadius && found.size() < cap; cx++) {
      for (int cz = -chunkRadius; cz <= chunkRadius && found.size() < cap; cz++) {
        int chunkX = baseCx + cx;
        int chunkZ = baseCz + cz;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          continue;
        }
        for (BlockRef state : targetsIn(world, chunkX, chunkZ, accepts)) {
          if (found.size() >= cap) {
            break;
          }
          double dx = (state.x() + 0.5D) - eye.x();
          double dy = (state.y() + 0.5D) - eye.y();
          double dz = (state.z() + 0.5D) - eye.z();
          double distSq = (dx * dx) + (dy * dy) + (dz * dz);
          if (distSq > rangeSq || distSq < 0.5D) {
            continue;
          }
          double dist = Math.sqrt(distSq);
          double dot = ((dx * look.x()) + (dy * look.y()) + (dz * look.z())) / dist;
          if (dot < viewConeCos) {
            continue;
          }
          if (!hasLineOfSight(world, eye, state, dx, dy, dz, dist)) {
            continue;
          }
          found.add(state);
        }
      }
    }
    return found;
  }

  /**
   * Chests and spawners accepted by {@code accepts} within {@code range} of {@code center} that the viewer's eye can see,
   * nearest to {@code center} first, at most {@code limit}. A container behind a wall never shows.
   */
  public static List<BlockRef> within(PlayerRef viewer, Loc center, double range, int limit, Predicate<Id> accepts) {
    WorldRef world = center.world();
    Loc eye = viewer.eyeLocation();
    double rangeSq = range * range;
    int chunkRadius = Math.max(1, (int) Math.ceil(range / 16D));
    List<BlockRef> found = new ArrayList<>();
    for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
      for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
        int chunkX = (center.blockX() >> 4) + cx;
        int chunkZ = (center.blockZ() >> 4) + cz;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          continue;
        }
        for (BlockRef block : targetsIn(world, chunkX, chunkZ, accepts)) {
          if (distanceSquared(center, block) <= rangeSq && seesBlock(eye, block)) {
            found.add(block);
          }
        }
      }
    }
    found.sort(Comparator.comparingDouble((BlockRef block) -> distanceSquared(center, block)));
    return found.size() > limit ? List.copyOf(found.subList(0, limit)) : found;
  }

  /** Keen Eye's glow on a chest or spawner that only {@code player} sees, on {@code owner}'s display channel. */
  public static void glimmer(Adaptation<?> owner, PlayerRef player, BlockRef state, Rgb color, int ticks) {
    Loc at = new Loc(state.world(), state.x(), state.y(), state.z());
    ViewerDisplayPort.current().showBlock(
        owner.getId().toString(),
        "target-" + state.x() + ":" + state.y() + ":" + state.z(),
        player,
        at,
        state.state(),
        color,
        Math.max(6, ticks)
    );
  }

  private static List<BlockRef> targetsIn(WorldRef world, int chunkX, int chunkZ, Predicate<Id> accepts) {
    List<BlockRef> tiles = new ArrayList<>();
    try {
      for (BlockRef block : world.blockEntities(chunkX, chunkZ)) {
        if (isTarget(block.type()) && accepts.test(block.type())) {
          tiles.add(block);
        }
      }
    } catch (Throwable t) {
      return List.of();
    }
    return tiles;
  }

  private static double distanceSquared(Loc center, BlockRef block) {
    double dx = (block.x() + 0.5D) - center.x();
    double dy = (block.y() + 0.5D) - center.y();
    double dz = (block.z() + 0.5D) - center.z();
    return (dx * dx) + (dy * dy) + (dz * dz);
  }

  /**
   * True when nothing solid stands between {@code eye} and {@code block}: a solid block must be the first one the ray
   * meets, and the ray to a passable block such as a crop must meet nothing on the way.
   */
  public static boolean seesBlock(Loc eye, BlockRef block) {
    double dx = (block.x() + 0.5D) - eye.x();
    double dy = (block.y() + 0.5D) - eye.y();
    double dz = (block.z() + 0.5D) - eye.z();
    double dist = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    if (!eye.sameWorld(block.location()) || dist < 0.5D) {
      return false;
    }
    if (block.isPassable()) {
      return block.world().rayTraceBlock(eye.position(), new Vec3(dx, dy, dz).normalize(), dist, false) == null;
    }
    return hasLineOfSight(block.world(), eye, block, dx, dy, dz, dist);
  }

  private static boolean hasLineOfSight(WorldRef world, Loc eye, BlockRef state, double dx, double dy, double dz, double dist) {
    Vec3 direction = new Vec3(dx, dy, dz).normalize();
    BlockRef hit = world.rayTraceBlock(eye.position(), direction, dist + 1.0D, false);
    if (hit == null) {
      return false;
    }
    return hit.x() == state.x()
        && hit.y() == state.y()
        && hit.z() == state.z();
  }
}
